package com.coooolfan.unirhy.service.plugin.hostapi

import com.coooolfan.unirhy.service.storage.StorageNodeObjectService
import run.endive.runtime.HostFunction
import run.endive.runtime.Instance
import tools.jackson.databind.ObjectMapper

internal fun buildMediaHostFunctions(
    mediaService: PluginMediaService,
    storageObjects: StorageNodeObjectService,
    objectMapper: ObjectMapper,
    instanceRef: () -> Instance,
    callExecutor: PluginHostCallExecutor = DIRECT_PLUGIN_HOST_CALL_EXECUTOR,
): List<HostFunction> {
    val support = PluginHostSupport(objectMapper, callExecutor, instanceRef)
    return listOf(
        support.jsonFunction("host_media_file_get") { request ->
            mediaService.getMediaFile(request.requiredLong("id"))
        },
        support.jsonFunction("host_media_file_create") { request ->
            val node = resolveHostStorageNode(request.requiredObject("node"), storageObjects)
            mediaService.createMediaFile(
                node,
                request.requiredObjectKey("objectKey"),
                request.requiredNonBlankText("mimeType"),
            )
        },
        support.jsonFunction("host_media_file_delete") { request ->
            mediaService.deleteMediaFile(request.requiredLong("id"))
            null
        },
        support.jsonFunction("host_asset_list") { request ->
            mediaService.listAssets(request.requiredLong("recordingId"))
        },
        support.jsonFunction("host_asset_create") { request ->
            mediaService.createAsset(
                recordingId = request.requiredLong("recordingId"),
                mediaFileId = request.requiredLong("mediaFileId"),
                comment = request.optionalText("comment") ?: "",
            )
        },
        support.jsonFunction("host_asset_delete") { request ->
            mediaService.deleteAsset(request.requiredLong("id"))
            null
        },
    )
}
