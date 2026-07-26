package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.error.PluginException
import com.coooolfan.unirhy.model.Plugin
import com.coooolfan.unirhy.model.PluginTask
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.model.dto.PluginInfoView
import com.coooolfan.unirhy.model.enabled
import com.coooolfan.unirhy.model.id
import com.coooolfan.unirhy.service.plugin.PluginManifest
import com.coooolfan.unirhy.service.plugin.PluginTaskStore
import com.coooolfan.unirhy.service.plugin.UNIRHY_WASM_ABI_V1
import com.coooolfan.unirhy.service.plugin.WasmPlugin
import com.coooolfan.unirhy.service.plugin.WasmPluginException
import com.coooolfan.unirhy.service.plugin.hostapi.PluginDataService
import com.coooolfan.unirhy.service.task.PluginTaskService
import com.coooolfan.unirhy.service.task.common.AsyncTaskStore
import com.coooolfan.unirhy.service.task.common.TaskKey
import tools.jackson.databind.ObjectMapper
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.module.kotlin.kotlinModule
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.text.Charsets

private const val MAX_ZIP_BYTES = 10L * 1024 * 1024
private const val MAX_WASM_BYTES = 20L * 1024 * 1024

private val PLUGIN_SUMMARY_FETCHER: Fetcher<Plugin> = newFetcher(Plugin::class).by {
    allScalarFields()
    wasm(false)
}

@Service
class PluginService(
    private val sql: KSqlClient,
    private val asyncTaskStore: AsyncTaskStore,
    private val pluginTaskService: PluginTaskService,
    private val pluginDataService: PluginDataService,
    private val pluginTaskStore: PluginTaskStore,
    private val transactionTemplate: TransactionTemplate,
) {
    private val logger = LoggerFactory.getLogger(PluginService::class.java)
    private val yamlMapper: ObjectMapper = YAMLMapper.builder().addModule(kotlinModule()).build()

    /** 列表视图不需要 WASM 字节码（每个插件最大 20MB），显式排除避免整库读入堆内存 */
    fun listPlugins(): List<PluginInfoView> =
        sql.createQuery(Plugin::class) {
            orderBy(table.id)
            select(table.fetch(PluginInfoView::class))
        }.execute()

    fun getPlugin(id: String): Plugin =
        sql.findById(Plugin::class, id)
            ?: throw PluginException.notFound()

    /**
     * 上传插件包。同 id 上传即覆盖升级：保留已有的 `concurrency` 与 `created_at`，
     * 上传后保持禁用并等待各节点 reconcile 卸载旧运行时。
     */
    fun upload(file: MultipartFile) {
        if (file.size > MAX_ZIP_BYTES) {
            throw PluginException.packageTooLarge()
        }

        var manifestYaml: String? = null
        var wasmBytes: ByteArray? = null

        ZipInputStream(ByteArrayInputStream(file.bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                when (entry.name) {
                    "plugin.yml" -> manifestYaml = zis.readBytes().toString(Charsets.UTF_8)
                    "plugin.wasm" -> {
                        val bytes = zis.readBytes()
                        if (bytes.size > MAX_WASM_BYTES) {
                            throw PluginException.wasmTooLarge()
                        }
                        wasmBytes = bytes
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val yaml = manifestYaml ?: throw PluginException.manifestMissing()
        val wasm = wasmBytes ?: throw PluginException.wasmMissing()

        val manifest = try {
            yamlMapper.readValue(yaml, PluginManifest::class.java)
        } catch (ex: Exception) {
            throw PluginException.invalidManifest(reason = ex.message ?: "manifest parse error", cause = ex)
        }

        if (manifest.runtime.type != "wasm") {
            throw PluginException.unsupportedRuntime()
        }
        if (manifest.runtime.abi != UNIRHY_WASM_ABI_V1) {
            throw PluginException.unsupportedAbi()
        }
        manifest.validate()?.let { reason ->
            throw PluginException.invalidManifest(reason = reason)
        }
        try {
            WasmPlugin.parseModule(wasm)
        } catch (ex: WasmPluginException) {
            throw PluginException.invalidManifest(reason = ex.message ?: "invalid wasm module", cause = ex)
        }

        val existingTasks = pluginTaskStore.findByPlugin(manifest.id)
        val existing = sql.findById(PLUGIN_SUMMARY_FETCHER, manifest.id)
        if (existing != null) {
            val declared = manifest.tasks.map { it.type }.toSet()
            val removed = existingTasks.map { it.taskType }.filterNot { it in declared }
            if (removed.isNotEmpty()) {
                throw PluginException.invalidManifest(
                    reason = "task types ${removed.sorted()} must stay declared for plugin ${manifest.id}; " +
                        "incompatible task protocol changes require a new plugin id"
                )
            }
        }

        val now = Instant.now()
        val configDefinition = manifest.configDefinition()
        // 覆盖升级保留管理员已调整过的并发值
        val existingConcurrency = existingTasks.associate { it.taskType to it.concurrency }
        transactionTemplate.executeWithoutResult {
            sql.saveCommand(Plugin {
                id = manifest.id
                name = manifest.name
                version = manifest.version
                abi = manifest.runtime.abi
                this.configDefinition = configDefinition
                this.wasm = wasm
                enabled = false
                createdAt = existing?.createdAt ?: now
                updatedAt = now
            }).execute()
            pluginTaskStore.replaceAll(
                manifest.id,
                manifest.tasks.map { task ->
                    PluginTask {
                        pluginId = manifest.id
                        taskType = task.type
                        name = task.name
                        concurrency = existingConcurrency[task.type] ?: task.concurrency
                        userSubmittable = task.userSubmittable
                        formDefinition = manifest.formDefinition(task)
                    }
                },
            )
            pluginDataService.reconcileConfigEncryption(manifest.id, configDefinition)
        }

        pluginTaskService.uninstall(manifest.id)
        logger.info(
            "Plugin uploaded: id={}, version={}, taskTypes={}",
            manifest.id, manifest.version, manifest.tasks.map { it.type },
        )
    }

    /**
     * 启用/禁用插件。启用前先完成 WASM 解析、实例化与导出函数校验，
     * 全部成功后再更新数据库状态并注册运行时。
     */
    fun setEnabled(id: String, enabled: Boolean) {
        val plugin = getPlugin(id)
        if (enabled) {
            pluginDataService.validateConfiguration(id)
            try {
                pluginTaskService.verifyLoadable(plugin.id, plugin.wasm)
            } catch (ex: Exception) {
                throw PluginException.loadFailed(reason = ex.message ?: "failed to load plugin", cause = ex)
            }
        }
        sql.saveCommand(Plugin {
            this.id = id
            this.enabled = enabled
            this.updatedAt = Instant.now()
        }, SaveMode.UPDATE_ONLY).execute()
        if (enabled) {
            pluginTaskService.install(plugin)
        } else {
            pluginTaskService.uninstall(id)
        }
    }

    /**
     * 管理员直接读写某个任务的当前并发值，修改后无需重启，各节点由 reconcile 生效。
     * 并发是"每任务一份"的属性：入口任务与工作任务各自独立。
     */
    fun updateConcurrency(id: String, taskType: String, concurrency: Int) {
        if (concurrency <= 0) {
            throw PluginException.invalidConcurrency()
        }
        getPlugin(id)
        val key = TaskKey.ofOrNull(id, taskType) ?: throw PluginException.notFound()
        transactionTemplate.executeWithoutResult {
            if (!pluginTaskStore.updateConcurrency(key, concurrency)) {
                throw PluginException.notFound()
            }
            sql.saveCommand(Plugin {
                this.id = id
                this.updatedAt = Instant.now()
            }, SaveMode.UPDATE_ONLY).execute()
        }
    }

    /**
     * 删除插件。只允许作用于已禁用的插件；存在活动任务时拒绝。
     * 删除事务锁定插件行后再次校验，与根任务创建事务的共享锁互斥。
     */
    fun delete(id: String) {
        transactionTemplate.executeWithoutResult {
            val enabled = sql.createQuery(Plugin::class) {
                where(table.id eq id)
                select(table.enabled)
            }.forUpdate().execute().firstOrNull() ?: throw PluginException.notFound()
            if (enabled) {
                throw PluginException.deleteConflict()
            }
            if (asyncTaskStore.hasActiveByNamespace(id)) {
                throw PluginException.deleteConflict()
            }
            sql.deleteById(Plugin::class, id)
        }
        pluginTaskService.uninstall(id)
    }

    /** 导出插件包；manifest 的 `tasks[].concurrency` 写入当前并发值 */
    fun export(id: String): ByteArray {
        val plugin = getPlugin(id)
        val tasks = pluginTaskStore.findByPlugin(id)
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("plugin.yml"))
            zos.write(reconstructManifestYaml(plugin, tasks).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("plugin.wasm"))
            zos.write(plugin.wasm)
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    private fun reconstructManifestYaml(plugin: Plugin, tasks: List<PluginTask>): String {
        val data = mapOf(
            "id" to plugin.id,
            "name" to plugin.name,
            "version" to plugin.version,
            "runtime" to mapOf(
                "type" to "wasm",
                "abi" to plugin.abi,
            ),
            "tasks" to tasks.map { task ->
                val formDefinition = task.formDefinition
                mapOf(
                    "type" to task.taskType,
                    "concurrency" to task.concurrency,
                    "userSubmittable" to task.userSubmittable,
                    "form" to mapOf(
                        "schema" to formDefinition.get("schema"),
                        "order" to formDefinition.get("order"),
                    ),
                )
            },
            "config" to mapOf(
                "schema" to plugin.configDefinition.get("schema"),
                "order" to plugin.configDefinition.get("order"),
            ),
        )
        return yamlMapper.writeValueAsString(data)
    }
}
