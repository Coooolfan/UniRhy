package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.model.Plugin
import com.coooolfan.unirhy.model.enabled
import com.coooolfan.unirhy.model.id
import com.coooolfan.unirhy.service.plugin.PluginFormField
import com.coooolfan.unirhy.service.plugin.PluginManifest
import com.coooolfan.unirhy.service.plugin.UNIRHY_WASM_ABI_V1
import com.coooolfan.unirhy.service.task.PluginTaskService
import com.coooolfan.unirhy.service.task.common.TaskType
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.module.kotlin.kotlinModule
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
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
    private val pluginTaskService: PluginTaskService,
) {
    private val logger = LoggerFactory.getLogger(PluginService::class.java)
    private val yamlMapper: ObjectMapper = YAMLMapper.builder().addModule(kotlinModule()).build()
    private val formFieldsType = object : TypeReference<List<PluginFormField>>() {}

    fun listPlugins(): List<Plugin> =
        sql.createQuery(Plugin::class) {
            orderBy(table.id)
            select(table)
        }.execute()

    fun getPlugin(id: String): Plugin =
        sql.findById(Plugin::class, id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "plugin not found: $id")

    fun upload(file: MultipartFile) {
        if (file.size > MAX_ZIP_BYTES) {
            throw ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "plugin file exceeds 10MB limit")
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
                            throw ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "wasm exceeds 20MB limit")
                        }
                        wasmBytes = bytes
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val yaml = manifestYaml ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "plugin.yml missing from archive")
        val wasm = wasmBytes ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "plugin.wasm missing from archive")

        val manifest = try {
            yamlMapper.readValue(yaml, PluginManifest::class.java)
        } catch (ex: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid plugin.yml: ${ex.message}")
        }

        if (manifest.runtime.type != "wasm") {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported runtime type: ${manifest.runtime.type}")
        }
        if (manifest.runtime.abi != UNIRHY_WASM_ABI_V1) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported abi: ${manifest.runtime.abi}")
        }
        if (manifest.tasks.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "plugin declares no task bindings")
        }

        val task = manifest.tasks.first()
        val formFieldsJson = objectMapper.writeValueAsString(manifest.form.fields)

        sql.saveCommand(Plugin {
            id = manifest.id
            name = manifest.name
            version = manifest.version
            abi = manifest.runtime.abi
            taskType = task.type.name
            extension = task.extension
            networkAllow = manifest.networkAllowHosts().toList()
            formFields = formFieldsJson
            this.wasm = wasm
            enabled = false
            createdAt = Instant.now()
        }).execute()

        logger.info("Plugin uploaded: id={}, version={}, taskType={}", manifest.id, manifest.version, task.type)
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val plugin = getPlugin(id)
        sql.saveCommand(Plugin {
            this.id = id
            this.enabled = enabled
        }, SaveMode.UPDATE_ONLY).execute()
        val taskType = plugin.resolvedTaskType() ?: return
        if (enabled) {
            pluginTaskService.reloadPlugin(id)
        } else {
            pluginTaskService.unloadPlugin(taskType)
        }
    }

    fun delete(id: String) {
        val plugin = getPlugin(id)
        plugin.resolvedTaskType()?.let { pluginTaskService.unloadPlugin(it) }
        sql.deleteById(Plugin::class, id)
    }

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

    fun parseFormFields(plugin: Plugin): List<PluginFormField> =
        objectMapper.readValue(plugin.formFields, formFieldsType)

    companion object {
        fun Plugin.resolvedTaskType(): TaskType? =
            runCatching { TaskType.valueOf(taskType) }.getOrNull()
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
            "tasks" to listOf(mapOf(
                "type" to plugin.taskType,
                "extension" to plugin.extension,
            )),
            "permissions" to mapOf(
                "network" to mapOf("allow" to plugin.networkAllow),
            ),
            "form" to mapOf(
                "fields" to objectMapper.readValue(plugin.formFields, List::class.java),
            ),
        )
        return yamlMapper.writeValueAsString(data)
    }
}
