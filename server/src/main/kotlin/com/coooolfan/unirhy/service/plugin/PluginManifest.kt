package com.coooolfan.unirhy.service.plugin

import com.coooolfan.unirhy.service.task.common.TaskType
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.databind.ObjectMapper
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.module.kotlin.kotlinModule
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

const val UNIRHY_WASM_ABI_V1 = "unirhy-wasm-abi-v1"

@JsonIgnoreProperties(ignoreUnknown = true)
data class PluginManifest(
    val id: String,
    val name: String? = null,
    val version: String,
    val runtime: PluginRuntime,
    val tasks: List<PluginTaskBinding>,
    val permissions: PluginPermissions = PluginPermissions(),
    val form: PluginForm = PluginForm(),
) {
    fun networkAllowHosts(): Set<String> = permissions.network.allow.toSet()
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class PluginForm(
    val fields: List<PluginFormField> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PluginFormField(
    val name: String,
    val type: String,
    val label: String,
    val description: String? = null,
    val default: String? = null,
    val min: Long? = null,
    val max: Long? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PluginRuntime(
    val type: String,
    val target: String? = null,
    val wasi: String? = null,
    val abi: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PluginTaskBinding(
    val type: TaskType,
    val extension: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PluginPermissions(
    val network: PluginNetworkPermission = PluginNetworkPermission(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PluginNetworkPermission(
    val allow: List<String> = emptyList(),
)

private val logger = LoggerFactory.getLogger(PluginManifest::class.java)
private val yamlMapper: ObjectMapper = YAMLMapper.builder().addModule(kotlinModule()).build()

fun loadPluginManifest(path: Path): PluginManifest? {
    if (!Files.isRegularFile(path)) return null
    val manifest = try {
        yamlMapper.readValue(path.toFile(), PluginManifest::class.java)
    } catch (ex: Exception) {
        logger.warn("Failed to parse plugin manifest at {}: {}", path, ex.message)
        return null
    }
    if (manifest.runtime.type != "wasm") {
        logger.warn("Plugin manifest at {} rejected: runtime.type={} (expected wasm)", path, manifest.runtime.type)
        return null
    }
    if (manifest.runtime.abi != UNIRHY_WASM_ABI_V1) {
        logger.warn("Plugin manifest at {} rejected: runtime.abi={} (expected {})", path, manifest.runtime.abi, UNIRHY_WASM_ABI_V1)
        return null
    }
    if (manifest.tasks.isEmpty()) {
        logger.warn("Plugin manifest at {} rejected: no task bindings", path)
        return null
    }
    return manifest
}
