package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.error.PluginException
import com.coooolfan.unirhy.model.Plugin
import com.coooolfan.unirhy.model.id
import com.coooolfan.unirhy.service.plugin.PluginManifest
import com.coooolfan.unirhy.service.plugin.UNIRHY_WASM_ABI_V1
import com.coooolfan.unirhy.service.plugin.WasmPlugin
import com.coooolfan.unirhy.service.plugin.WasmPluginException
import com.coooolfan.unirhy.service.task.PluginTaskService
import tools.jackson.databind.ObjectMapper
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.module.kotlin.kotlinModule
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
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

@Service
class PluginService(
    private val sql: KSqlClient,
    private val objectMapper: ObjectMapper,
    private val jdbc: NamedParameterJdbcTemplate,
    private val pluginTaskService: PluginTaskService,
    private val transactionTemplate: TransactionTemplate,
) {
    private val logger = LoggerFactory.getLogger(PluginService::class.java)
    private val yamlMapper: ObjectMapper = YAMLMapper.builder().addModule(kotlinModule()).build()

    fun listPlugins(): List<Plugin> =
        sql.createQuery(Plugin::class) {
            orderBy(table.id)
            select(table)
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

        val existing = sql.findById(Plugin::class, manifest.id)
        if (existing != null && existing.taskType != manifest.task.type) {
            throw PluginException.invalidManifest(
                reason = "task type must stay ${existing.taskType} for plugin ${manifest.id}; " +
                    "incompatible task protocol changes require a new plugin id"
            )
        }

        val now = Instant.now()
        sql.saveCommand(Plugin {
            id = manifest.id
            name = manifest.name
            version = manifest.version
            abi = manifest.runtime.abi
            taskType = manifest.task.type
            concurrency = existing?.concurrency ?: manifest.task.concurrency
            formDefinition = manifest.formDefinition()
            this.wasm = wasm
            enabled = false
            createdAt = existing?.createdAt ?: now
            updatedAt = now
        }).execute()

        pluginTaskService.uninstall(manifest.id)
        logger.info("Plugin uploaded: id={}, version={}, taskType={}", manifest.id, manifest.version, manifest.task.type)
    }

    /**
     * 启用/禁用插件。启用前先完成 WASM 解析、实例化与导出函数校验，
     * 全部成功后再更新数据库状态并注册运行时。
     */
    fun setEnabled(id: String, enabled: Boolean) {
        val plugin = getPlugin(id)
        if (enabled) {
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
            pluginTaskService.install(getPlugin(id))
        } else {
            pluginTaskService.uninstall(id)
        }
    }

    /** 管理员直接读写当前并发值，修改后无需重启，各节点由 reconcile 生效 */
    fun updateConcurrency(id: String, concurrency: Int) {
        if (concurrency <= 0) {
            throw PluginException.invalidConcurrency()
        }
        getPlugin(id)
        sql.saveCommand(Plugin {
            this.id = id
            this.concurrency = concurrency
            this.updatedAt = Instant.now()
        }, SaveMode.UPDATE_ONLY).execute()
    }

    /**
     * 删除插件。只允许作用于已禁用的插件；存在活动 submission / task 时拒绝。
     * 删除事务锁定插件行后再次校验，与 submission 创建事务的共享锁互斥。
     */
    fun delete(id: String) {
        transactionTemplate.executeWithoutResult {
            val enabled = jdbc.query(
                "SELECT enabled FROM public.plugin WHERE id = :id FOR UPDATE",
                MapSqlParameterSource("id", id),
            ) { rs, _ -> rs.getBoolean(1) }.firstOrNull() ?: throw PluginException.notFound()
            if (enabled) {
                throw PluginException.deleteConflict()
            }
            val hasActive = jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM public.task_submission
                    WHERE namespace = :id AND status IN ('PENDING', 'RUNNING')
                ) OR EXISTS (
                    SELECT 1 FROM public.async_task
                    WHERE namespace = :id AND status IN ('PENDING', 'RUNNING')
                )
                """.trimIndent(),
                MapSqlParameterSource("id", id),
                Boolean::class.java,
            ) == true
            if (hasActive) {
                throw PluginException.deleteConflict()
            }
            sql.deleteById(Plugin::class, id)
        }
        pluginTaskService.uninstall(id)
    }

    /** 导出插件包；manifest 的 `task.concurrency` 写入当前并发值 */
    fun export(id: String): ByteArray {
        val plugin = getPlugin(id)
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("plugin.yml"))
            zos.write(reconstructManifestYaml(plugin).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("plugin.wasm"))
            zos.write(plugin.wasm)
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    private fun reconstructManifestYaml(plugin: Plugin): String {
        val data = mapOf(
            "id" to plugin.id,
            "name" to plugin.name,
            "version" to plugin.version,
            "runtime" to mapOf(
                "type" to "wasm",
                "abi" to plugin.abi,
            ),
            "task" to mapOf(
                "type" to plugin.taskType,
                "concurrency" to plugin.concurrency,
            ),
            "form" to mapOf(
                "schema" to plugin.formDefinition.get("schema"),
                "order" to plugin.formDefinition.get("order"),
            ),
        )
        return yamlMapper.writeValueAsString(data)
    }
}
