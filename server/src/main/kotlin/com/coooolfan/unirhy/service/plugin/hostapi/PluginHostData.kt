package com.coooolfan.unirhy.service.plugin.hostapi

import run.endive.runtime.HostFunction
import run.endive.runtime.Instance
import tools.jackson.databind.ObjectMapper

internal fun buildPluginDataHostFunctions(
    pluginId: String,
    pluginDataService: PluginDataService,
    objectMapper: ObjectMapper,
    instanceRef: () -> Instance,
    callExecutor: PluginHostCallExecutor = DIRECT_PLUGIN_HOST_CALL_EXECUTOR,
): List<HostFunction> {
    val support = PluginHostSupport(objectMapper, callExecutor, instanceRef)

    return listOf(
        support.jsonFunction("host_plugin_config_get") {
            pluginDataService.getConfigurationForHost(pluginId)
        },
        support.jsonFunction("host_plugin_data_get") { request ->
            val key = request.requiredText("key")
            pluginDataService.getData(pluginId, key) ?: notFound("Plugin data key not found: $key")
        },
        support.jsonFunction("host_plugin_data_put") { request ->
            pluginDataService.putData(pluginId, request.requiredText("key"), request.requiredNode("value"))
            null
        },
        support.jsonFunction("host_plugin_data_list") { request ->
            val page = support.page(request)
            pluginDataService.listDataKeys(
                pluginId = pluginId,
                prefix = request.optionalText("prefix") ?: "",
                pageIndex = page.pageIndex,
                pageSize = page.pageSize,
            )
        },
    )
}
