package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.model.Plugin
import com.coooolfan.unirhy.model.concurrency
import com.coooolfan.unirhy.model.enabled
import com.coooolfan.unirhy.model.id
import com.coooolfan.unirhy.model.taskType
import com.coooolfan.unirhy.model.updatedAt
import com.coooolfan.unirhy.service.ArtistService
import com.coooolfan.unirhy.service.plugin.WasmPlugin
import com.coooolfan.unirhy.service.plugin.buildArtistHostFunctions
import com.coooolfan.unirhy.service.plugin.buildDefaultHostFunctions
import com.coooolfan.unirhy.service.task.common.TaskKey
import com.coooolfan.unirhy.service.task.dispatch.TaskCapacityManager
import com.coooolfan.unirhy.service.task.spi.AsyncTaskHandler
import com.coooolfan.unirhy.service.task.spi.AsyncTaskHandlerRegistry
import com.coooolfan.unirhy.service.task.spi.TaskPlanner
import com.coooolfan.unirhy.service.task.spi.TaskPlannerRegistry
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * WASM 插件运行时：按插件 id 维护本地已加载状态，将 `plan()` / `run()`
 * 适配为 [TaskPlanner] / [AsyncTaskHandler] 并成对注册。
 *
 * 各节点通过固定轮询 tick 调用 [reconcile]，根据
 * `plugin.id/enabled/task_type/concurrency/updated_at` 与本地快照对齐，
 * 不依赖处理管理请求的节点推送状态。
 */
@Service
class PluginTaskService(
    private val sql: KSqlClient,
    private val objectMapper: ObjectMapper,
    private val artistService: ArtistService,
    private val plannerRegistry: TaskPlannerRegistry,
    private val handlerRegistry: AsyncTaskHandlerRegistry,
    private val capacityManager: TaskCapacityManager,
) {
    private val logger = LoggerFactory.getLogger(PluginTaskService::class.java)

    private data class LoadedSnapshot(
        val key: TaskKey,
        val updatedAt: Instant,
    )

    /** 仅在单线程 tick 与管理操作中访问，用锁保证可见性 */
    private val loaded = mutableMapOf<String, LoadedSnapshot>()
    private val lock = Any()

    fun isLoaded(pluginId: String): Boolean = synchronized(lock) { pluginId in loaded }

    /** 装载插件并成对注册 Planner / Handler；失败抛出异常且不产生半注册状态 */
    fun install(plugin: Plugin) {
        val key = TaskKey(plugin.id, plugin.taskType)
        val wasmPlugin = loadWasmPlugin(plugin.id, plugin.wasm)
        synchronized(lock) {
            loaded[plugin.id]?.let { previous ->
                if (previous.key != key) {
                    plannerRegistry.unregister(previous.key)
                    handlerRegistry.unregister(previous.key)
                    capacityManager.removeHandler(previous.key)
                }
            }
            plannerRegistry.replace(WasmTaskPlanner(key, wasmPlugin, objectMapper))
            handlerRegistry.replace(WasmTaskHandler(key, wasmPlugin))
            capacityManager.setHandlerLimit(key, plugin.concurrency)
            loaded[plugin.id] = LoadedSnapshot(key = key, updatedAt = plugin.updatedAt)
        }
        logger.info("Plugin runtime installed: id={}, key={}, concurrency={}", plugin.id, key, plugin.concurrency)
    }

    /** 成对移除 Planner / Handler 与本地容量 */
    fun uninstall(pluginId: String) {
        synchronized(lock) {
            val snapshot = loaded.remove(pluginId) ?: return
            plannerRegistry.unregister(snapshot.key)
            handlerRegistry.unregister(snapshot.key)
            capacityManager.removeHandler(snapshot.key)
        }
        logger.info("Plugin runtime uninstalled: id={}", pluginId)
    }

    /** 每轮 tick 根据数据库插件元数据对齐本地 Registry */
    fun reconcile() {
        val rows = sql.createQuery(Plugin::class) {
            select(table.id, table.enabled, table.taskType, table.concurrency, table.updatedAt)
        }.execute()

        val enabledRows = rows.filter { it._2 }.associateBy { it._1 }

        val staleIds = synchronized(lock) { loaded.keys.filter { it !in enabledRows } }
        for (pluginId in staleIds) {
            uninstall(pluginId)
        }

        for ((pluginId, row) in enabledRows) {
            val snapshot = synchronized(lock) { loaded[pluginId] }
            if (snapshot != null && snapshot.updatedAt == row._5) {
                continue
            }
            val plugin = sql.findById(Plugin::class, pluginId) ?: continue
            if (!plugin.enabled) {
                continue
            }
            try {
                install(plugin)
            } catch (ex: Exception) {
                logger.error("Failed to load plugin runtime during reconcile: id={}", pluginId, ex)
            }
        }
    }

    private fun loadWasmPlugin(pluginId: String, wasmBytes: ByteArray): WasmPlugin =
        WasmPlugin.load(pluginId, wasmBytes) { instanceRef ->
            buildDefaultHostFunctions(instanceRef) +
                buildArtistHostFunctions(artistService, objectMapper, instanceRef)
        }

    /** 上传时的加载校验：完整执行解析、实例化与导出函数检查后即弃 */
    fun verifyLoadable(pluginId: String, wasmBytes: ByteArray) {
        loadWasmPlugin(pluginId, wasmBytes)
    }
}

private class WasmTaskPlanner(
    override val key: TaskKey,
    private val wasmPlugin: WasmPlugin,
    private val objectMapper: ObjectMapper,
) : TaskPlanner {
    override fun plan(params: JsonNode): Sequence<JsonNode> {
        val payloads = wasmPlugin.plan(params.toString().toByteArray(Charsets.UTF_8))
        return payloads.asSequence().map { objectMapper.readTree(it) }
    }
}

private class WasmTaskHandler(
    override val key: TaskKey,
    private val wasmPlugin: WasmPlugin,
) : AsyncTaskHandler {
    override fun run(payload: JsonNode) {
        wasmPlugin.run(payload.toString().toByteArray(Charsets.UTF_8))
    }
}
