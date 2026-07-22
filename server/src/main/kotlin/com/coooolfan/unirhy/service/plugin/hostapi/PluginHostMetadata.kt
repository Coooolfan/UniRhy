package com.coooolfan.unirhy.service.plugin.hostapi

import com.coooolfan.unirhy.model.Account
import com.coooolfan.unirhy.model.Plugin
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.model.id
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import run.endive.runtime.HostFunction
import run.endive.runtime.Instance
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

private data class HostPluginMetadata(
    val id: String,
    val name: String?,
    val version: String,
    val taskType: String,
    val concurrency: Int,
    val isAvailable: Boolean,
    val enabled: Boolean,
    val formDefinition: JsonNode,
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
    taskType()
    concurrency()
    enabled()
    formDefinition()
    configDefinition()
}

private val HOST_ACCOUNT_METADATA_FETCHER: Fetcher<Account> = newFetcher(Account::class).by {
    email()
    name()
    admin()
}

internal fun buildMetadataHostFunctions(
    sql: KSqlClient,
    isPluginLoaded: (String) -> Boolean,
    objectMapper: ObjectMapper,
    instanceRef: () -> Instance,
    callExecutor: PluginHostCallExecutor = DIRECT_PLUGIN_HOST_CALL_EXECUTOR,
): List<HostFunction> {
    val support = PluginHostSupport(objectMapper, callExecutor, instanceRef)

    return listOf(
        support.jsonFunction("host_plugin_list") {
            sql.createQuery(Plugin::class) {
                orderBy(table.id)
                select(table.fetch(HOST_PLUGIN_METADATA_FETCHER))
            }.execute().map { it.toHostMetadata(isPluginLoaded) }
        },
        support.jsonFunction("host_plugin_get") { request ->
            val plugin = sql.findById(HOST_PLUGIN_METADATA_FETCHER, request.requiredText("id"))
                ?: notFound("Plugin not found")
            plugin.toHostMetadata(isPluginLoaded)
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

private fun Plugin.toHostMetadata(isPluginLoaded: (String) -> Boolean): HostPluginMetadata = HostPluginMetadata(
    id = id,
    name = name,
    version = version,
    taskType = taskType,
    concurrency = concurrency,
    isAvailable = isPluginLoaded(id),
    enabled = enabled,
    formDefinition = formDefinition,
    configDefinition = configDefinition,
)

private fun Account.toHostMetadata(): HostAccountMetadata = HostAccountMetadata(
    id = id,
    email = email,
    name = name,
    admin = admin,
)
