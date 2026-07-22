package com.coooolfan.unirhy.service.plugin.hostapi

import com.coooolfan.unirhy.error.PluginException
import com.coooolfan.unirhy.service.task.common.TaskFormSchema
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode

data class PluginConfigurationSnapshot(
    val values: ObjectNode,
    val configuredSecretFields: List<String>,
)

internal class InvalidPluginConfigurationException(
    val errors: List<String>,
) : RuntimeException(errors.joinToString("; "))

data class PluginDataKeyPage(
    val rows: List<String>,
    val totalRowCount: Long,
)

@Service
class PluginDataService internal constructor(
    private val store: PluginDataStore,
    private val cipher: PluginDataCipher,
    private val objectMapper: ObjectMapper,
) {
    fun getData(pluginId: String, key: String): JsonNode? {
        definition(pluginId)
        return store.find(pluginId, key)?.let { decode(pluginId, it) }
    }

    fun putData(pluginId: String, key: String, value: JsonNode) {
        val definition = definition(pluginId)
        val properties = properties(definition)
        if (properties.has(key)) {
            val errors = TaskFormSchema.validateField(definition, key, value)
            require(errors.isEmpty()) { errors.joinToString("; ") }
        }
        writeValue(pluginId, key, value, TaskFormSchema.isWriteOnly(definition, key))
    }

    fun listDataKeys(
        pluginId: String,
        prefix: String,
        pageIndex: Int,
        pageSize: Int,
    ): PluginDataKeyPage {
        definition(pluginId)
        val offset = Math.multiplyExact(pageIndex, pageSize)
        return PluginDataKeyPage(
            rows = store.listKeys(pluginId, prefix, offset, pageSize),
            totalRowCount = store.countKeys(pluginId, prefix),
        )
    }

    /** 返回包含敏感值的完整配置，仅供当前插件 Host 调用。 */
    fun getConfigurationForHost(pluginId: String): ObjectNode {
        val definition = definition(pluginId)
        val configuration = configurationValues(pluginId, definition)
        val errors = TaskFormSchema.validateParams(definition, configuration)
        if (errors.isNotEmpty()) throw InvalidPluginConfigurationException(errors)
        return configuration
    }

    /** 返回管理端可见配置，敏感字段只通过字段名表示已配置。 */
    fun getConfiguration(pluginId: String): PluginConfigurationSnapshot {
        val definition = definition(pluginId)
        val values = objectMapper.createObjectNode()
        val configuredSecrets = mutableListOf<String>()
        val stored = store.find(pluginId, propertyNames(definition))
        for (name in propertyNames(definition)) {
            val item = stored[name] ?: continue
            if (TaskFormSchema.isWriteOnly(definition, name)) {
                configuredSecrets += name
            } else {
                values.set(name, decode(pluginId, item))
            }
        }
        return PluginConfigurationSnapshot(values, configuredSecrets)
    }

    @Transactional
    fun updateConfiguration(
        pluginId: String,
        submittedValues: JsonNode,
        clearedSecretFields: Collection<String>,
    ): PluginConfigurationSnapshot {
        if (submittedValues !is ObjectNode) {
            throw PluginException.invalidConfiguration(reason = "values must be a JSON object")
        }
        val definition = definition(pluginId)
        val declaredNames = propertyNames(definition)
        val unknown = submittedValues.propertyNames().toSet() - declaredNames
        if (unknown.isNotEmpty()) {
            throw PluginException.invalidConfiguration(reason = "unknown configuration fields: $unknown")
        }

        val cleared = clearedSecretFields.toSet()
        val invalidClears = cleared.filter { it !in declaredNames || !TaskFormSchema.isWriteOnly(definition, it) }
        if (invalidClears.isNotEmpty()) {
            throw PluginException.invalidConfiguration(reason = "fields cannot be cleared as secrets: $invalidClears")
        }
        val setAndCleared = submittedValues.propertyNames().toSet() intersect cleared
        if (setAndCleared.isNotEmpty()) {
            throw PluginException.invalidConfiguration(reason = "fields cannot be set and cleared together: $setAndCleared")
        }

        val current = configurationValues(pluginId, definition)
        val effective = objectMapper.createObjectNode()
        for (name in declaredNames) {
            if (submittedValues.has(name)) {
                effective.set(name, submittedValues.get(name))
            } else if (TaskFormSchema.isWriteOnly(definition, name) && name !in cleared && current.has(name)) {
                effective.set(name, current.get(name))
            }
        }

        val errors = TaskFormSchema.validateParams(definition, effective)
        if (errors.isNotEmpty()) {
            throw PluginException.invalidConfiguration(reason = errors.joinToString("; "))
        }

        store.delete(pluginId, declaredNames)
        for (name in effective.propertyNames()) {
            writeValue(
                pluginId,
                name,
                effective.get(name),
                TaskFormSchema.isWriteOnly(definition, name),
            )
        }
        return getConfiguration(pluginId)
    }

    fun validateConfiguration(pluginId: String) {
        try {
            getConfigurationForHost(pluginId)
        } catch (ex: InvalidPluginConfigurationException) {
            throw PluginException.configurationRequired(reason = ex.errors.joinToString("; "))
        }
    }

    /** 插件升级时按新声明调整已有配置值的明文/密文表示。 */
    fun reconcileConfigEncryption(pluginId: String, configDefinition: JsonNode) {
        val stored = store.find(pluginId, propertyNames(configDefinition))
        for ((name, item) in stored) {
            val shouldEncrypt = TaskFormSchema.isWriteOnly(configDefinition, name)
            if (shouldEncrypt == (item.encrypted != null)) continue
            writeValue(pluginId, name, decode(pluginId, item), shouldEncrypt)
        }
    }

    private fun configurationValues(pluginId: String, definition: JsonNode): ObjectNode {
        val values = objectMapper.createObjectNode()
        for ((name, item) in store.find(pluginId, propertyNames(definition))) {
            values.set(name, decode(pluginId, item))
        }
        return values
    }

    private fun writeValue(pluginId: String, key: String, value: JsonNode, encrypted: Boolean) {
        val bytes = objectMapper.writeValueAsBytes(value)
        if (encrypted) {
            store.upsertEncrypted(pluginId, key, cipher.encrypt(pluginId, key, bytes))
        } else {
            store.upsertJson(pluginId, key, bytes.toString(Charsets.UTF_8))
        }
    }

    private fun decode(pluginId: String, stored: StoredPluginData): JsonNode = when {
        stored.encrypted != null -> objectMapper.readTree(cipher.decrypt(pluginId, stored.key, stored.encrypted))
        stored.json != null -> objectMapper.readTree(stored.json)
        else -> error("Plugin data row has neither a plain nor encrypted value")
    }

    private fun definition(pluginId: String): JsonNode {
        val json = store.configDefinition(pluginId) ?: throw PluginException.notFound()
        return objectMapper.readTree(json)
    }

    private fun properties(definition: JsonNode): JsonNode = definition.path("schema").path("properties")

    private fun propertyNames(definition: JsonNode): Set<String> = properties(definition).propertyNames().toSet()
}
