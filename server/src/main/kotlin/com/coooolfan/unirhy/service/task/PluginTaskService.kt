package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.model.AsyncTaskLog
import com.coooolfan.unirhy.model.Plugin
import com.coooolfan.unirhy.model.enabled
import com.coooolfan.unirhy.service.ArtistService
import com.coooolfan.unirhy.service.plugin.PluginManifest
import com.coooolfan.unirhy.service.plugin.PluginNetworkPermission
import com.coooolfan.unirhy.service.plugin.PluginPermissions
import com.coooolfan.unirhy.service.plugin.PluginRuntime
import com.coooolfan.unirhy.service.plugin.PluginTaskBinding
import com.coooolfan.unirhy.service.plugin.WasmPlugin
import com.coooolfan.unirhy.service.plugin.buildArtistHostFunctions
import com.coooolfan.unirhy.service.plugin.buildDefaultHostFunctions
import com.coooolfan.unirhy.service.task.common.AsyncTaskQueueStore
import com.coooolfan.unirhy.service.task.common.TaskStatus
import com.coooolfan.unirhy.service.task.common.TaskType
import com.coooolfan.unirhy.service.task.common.failureReason
import com.fasterxml.jackson.databind.ObjectMapper
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Service
class PluginTaskService(
    private val sql: KSqlClient,
    private val objectMapper: ObjectMapper,
    private val queueStore: AsyncTaskQueueStore,
    private val transactionTemplate: TransactionTemplate,
    private val artistService: ArtistService,
) {
    private val logger = LoggerFactory.getLogger(PluginTaskService::class.java)
    private val lock = ReentrantReadWriteLock()
    private val plugins: MutableMap<TaskType, WasmPlugin> = loadFromDb().toMutableMap()

    fun isConsumerEnabled(): Boolean = lock.read { plugins.isNotEmpty() }

    fun getLoadedTaskTypes(): Set<TaskType> = lock.read { plugins.keys.toSet() }

    fun submit(taskType: TaskType, formParamsJson: ByteArray) {
        val plugin = lock.read { plugins[taskType] }
            ?: throw IllegalArgumentException("no plugin loaded for task type: $taskType")
        val payloads = plugin.plan(formParamsJson)
        if (payloads.isEmpty()) {
            logger.info("Plugin plan() returned no tasks for taskType={}", taskType)
            return
        }
        val inserted = queueStore.enqueueIgnoringConflicts(taskType, payloads)
        logger.info("Plugin submit: taskType={}, batches={}, inserted={}", taskType, payloads.size, inserted)
    }

    fun reloadPlugin(id: String) {
        val dbPlugin = sql.findById(Plugin::class, id) ?: run {
            logger.warn("Plugin {} not found in DB for reload", id)
            return
        }
        val taskType = runCatching { TaskType.valueOf(dbPlugin.taskType) }.getOrElse {
            logger.warn("Plugin {} has unknown taskType {}, skipping reload", id, dbPlugin.taskType)
            return
        }
        val manifest = dbPlugin.toManifest()
        val plugin = WasmPlugin.load(manifest, dbPlugin.wasm) { m, instanceRef ->
            buildDefaultHostFunctions(m, instanceRef) +
                buildArtistHostFunctions(artistService, objectMapper, instanceRef)
        } ?: run {
            logger.warn("Failed to instantiate wasm for plugin {}", id)
            return
        }
        lock.write {
            plugins[taskType] = plugin
        }
        logger.info("Plugin hot-reloaded: id={}, taskType={}", id, taskType)
    }

    fun unloadPlugin(taskType: TaskType) {
        lock.write { plugins.remove(taskType) }
        logger.info("Plugin unloaded: taskType={}", taskType)
    }

    fun consumePendingTasks() {
        val snapshot = lock.read { plugins.toMap() }
        for ((taskType, plugin) in snapshot) {
            consumeForType(taskType, plugin)
        }
    }

    private fun loadFromDb(): Map<TaskType, WasmPlugin> {
        val enabledPlugins = sql.createQuery(Plugin::class) {
            where(table.enabled eq true)
            select(table)
        }.execute()

        if (enabledPlugins.isEmpty()) {
            logger.info("No enabled plugins found in DB")
            return emptyMap()
        }

        val result = mutableMapOf<TaskType, WasmPlugin>()
        for (dbPlugin in enabledPlugins) {
            val taskType = runCatching { TaskType.valueOf(dbPlugin.taskType) }.getOrElse {
                logger.warn("Plugin {} has unknown taskType {}, skipping", dbPlugin.id, dbPlugin.taskType)
                continue
            }
            val manifest = dbPlugin.toManifest()
            val plugin = WasmPlugin.load(manifest, dbPlugin.wasm) { m, instanceRef ->
                buildDefaultHostFunctions(m, instanceRef) +
                    buildArtistHostFunctions(artistService, objectMapper, instanceRef)
            }
            if (plugin != null) {
                result[taskType] = plugin
                logger.info("Plugin loaded from DB: id={}, taskType={}", dbPlugin.id, taskType)
            }
        }
        return result
    }

    private fun consumeForType(taskType: TaskType, plugin: WasmPlugin) {
        val claimedTask = try {
            transactionTemplate.execute { queueStore.claim(taskType, 1).firstOrNull() }
        } catch (ex: Throwable) {
            logger.error("Failed to claim task for type={}", taskType, ex)
            return
        } ?: return

        val reason = try {
            plugin.run(claimedTask.params.toByteArray(Charsets.UTF_8))
            "SUCCESS"
        } catch (ex: Throwable) {
            logger.error("Plugin run failed, taskType={}, logId={}", taskType, claimedTask.id, ex)
            failureReason(ex)
        }

        try {
            transactionTemplate.executeWithoutResult {
                sql.saveEntities(
                    listOf(AsyncTaskLog {
                        id = claimedTask.id
                        status = if (reason == "SUCCESS") TaskStatus.COMPLETED else TaskStatus.FAILED
                        completedReason = reason
                    }),
                    SaveMode.UPDATE_ONLY,
                )
            }
        } catch (ex: Throwable) {
            logger.error("Failed to update task status, logId={}", claimedTask.id, ex)
        }
    }
}

private fun Plugin.toManifest(): PluginManifest = PluginManifest(
    id = id,
    name = name,
    version = version,
    runtime = PluginRuntime(type = "wasm", abi = abi),
    tasks = listOf(PluginTaskBinding(type = TaskType.valueOf(taskType), extension = extension)),
    permissions = PluginPermissions(network = PluginNetworkPermission(allow = networkAllow)),
)
