package com.coooolfan.unirhy.service.plugin.hostapi

import com.coooolfan.unirhy.model.Account
import com.coooolfan.unirhy.model.Plugin
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.model.id
import com.coooolfan.unirhy.service.plugin.PluginTaskStore
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import run.endive.runtime.HostFunction
import run.endive.runtime.Instance
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

private data class HostPluginTaskMetadata(
    val taskType: String,
    val concurrency: Int,
    val userSubmittable: Boolean,
    val formDefinition: JsonNode,
)

private data class HostPluginMetadata(
    val id: String,
    val name: String?,
    val version: String,
    val isAvailable: Boolean,
    val enabled: Boolean,
    val tasks: List<HostPluginTaskMetadata>,
    val configDefinition: JsonNode,
)

private data class HostAccountMetadata(
    val id: Long,
    val email: String,
    val name: String,
    val admin: Boolean,
)

private val HOST_PLUGIN_METADATA_FETCHER: Fetcher<Plugin> = newFetcher(Plugin::class).by {
    name()
    version()
    enabled()
    configDefinition()
}

private val HOST_ACCOUNT_METADATA_FETCHER: Fetcher<Account> = newFetcher(Account::class).by {
    email()
    name()
    admin()
}

internal fun buildMetadataHostFunctions(
    sql: KSqlClient,
    pluginTaskStore: PluginTaskStore,
    isPluginLoaded: (String) -> Boolean,
    objectMapper: ObjectMapper,
    instanceRef: () -> Instance,
    callExecutor: PluginHostCallExecutor = DIRECT_PLUGIN_HOST_CALL_EXECUTOR,
): List<HostFunction> {
    val support = PluginHostSupport(objectMapper, callExecutor, instanceRef)

    fun tasksOf(pluginId: String): List<HostPluginTaskMetadata> =
        pluginTaskStore.findByPlugin(pluginId).map { task ->
            HostPluginTaskMetadata(
                taskType = task.taskType,
                concurrency = task.concurrency,
                userSubmittable = task.userSubmittable,
                formDefinition = objectMapper.readTree(task.formDefinitionJson),
            )
        }

    return listOf(
        support.jsonFunction("host_plugin_list") {
            sql.createQuery(Plugin::class) {
                orderBy(table.id)
                select(table.fetch(HOST_PLUGIN_METADATA_FETCHER))
            }.execute().map { it.toHostMetadata(isPluginLoaded, tasksOf(it.id)) }
        },
        support.jsonFunction("host_plugin_get") { request ->
            val plugin = sql.findById(HOST_PLUGIN_METADATA_FETCHER, request.requiredText("id"))
                ?: notFound("Plugin not found")
            plugin.toHostMetadata(isPluginLoaded, tasksOf(plugin.id))
        },
        support.jsonFunction("host_account_list") {
            sql.createQuery(Account::class) {
                orderBy(table.id)
                select(table.fetch(HOST_ACCOUNT_METADATA_FETCHER))
            }.execute().map(Account::toHostMetadata)
        },
        support.jsonFunction("host_account_get") { request ->
            val account = sql.findById(HOST_ACCOUNT_METADATA_FETCHER, request.requiredLong("id"))
                ?: notFound("Account not found")
            account.toHostMetadata()
        },
    )
}

private fun Plugin.toHostMetadata(
    isPluginLoaded: (String) -> Boolean,
    tasks: List<HostPluginTaskMetadata>,
): HostPluginMetadata = HostPluginMetadata(
    id = id,
    name = name,
    version = version,
    isAvailable = isPluginLoaded(id),
    enabled = enabled,
    tasks = tasks,
    configDefinition = configDefinition,
)

private fun Account.toHostMetadata(): HostAccountMetadata = HostAccountMetadata(
    id = id,
    email = email,
    name = name,
    admin = admin,
)
