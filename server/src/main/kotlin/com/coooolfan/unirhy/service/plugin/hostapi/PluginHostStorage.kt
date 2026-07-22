package com.coooolfan.unirhy.service.plugin.hostapi

import com.coooolfan.unirhy.model.storage.FileProviderFileSystem
import com.coooolfan.unirhy.model.storage.FileProviderOss
import com.coooolfan.unirhy.model.storage.FileProviderType
import com.coooolfan.unirhy.model.storage.by
import com.coooolfan.unirhy.service.storage.FileSystemStorageService
import com.coooolfan.unirhy.service.storage.OssStorageService
import com.coooolfan.unirhy.service.storage.StorageNode
import com.coooolfan.unirhy.service.storage.StorageNodeObjectService
import org.babyfish.jimmer.sql.exception.EmptyResultException
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import run.endive.runtime.HostFunction
import run.endive.runtime.Instance
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode

internal fun buildStorageHostFunctions(
    fileSystemStorageService: FileSystemStorageService,
    ossStorageService: OssStorageService,
    storageObjects: StorageNodeObjectService,
    objectMapper: ObjectMapper,
    instanceRef: () -> Instance,
    callExecutor: PluginHostCallExecutor = DIRECT_PLUGIN_HOST_CALL_EXECUTOR,
): List<HostFunction> {
    val support = PluginHostSupport(objectMapper, callExecutor, instanceRef)
    return listOf(
        support.jsonFunction("host_storage_fs_node_list") {
            fileSystemStorageService.list(HOST_FS_NODE_FETCHER).map { node ->
                mapOf(
                    "id" to node.id,
                    "name" to node.name,
                    "parentPath" to node.parentPath,
                    "readonly" to node.readonly,
                )
            }
        },
        support.jsonFunction("host_storage_oss_node_list") {
            ossStorageService.list(HOST_OSS_NODE_FETCHER).map { node ->
                mapOf(
                    "id" to node.id,
                    "name" to node.name,
                    "host" to node.host,
                    "bucket" to node.bucket,
                    "parentPath" to node.parentPath,
                    "readonly" to node.readonly,
                )
            }
        },
        support.jsonFunction("host_storage_object_list") { request ->
            val node = resolveHostStorageNode(request.requiredObject("node"), storageObjects)
            val pageSize = request.optionalInt("pageSize") ?: HOST_DEFAULT_PAGE_SIZE
            if (pageSize !in 1..HOST_MAX_PAGE_SIZE) {
                invalidArgument("pageSize must be between 1 and $HOST_MAX_PAGE_SIZE")
            }
            val page = storageObjects.list(
                node = node,
                prefix = request.optionalText("prefix") ?: "",
                pageSize = pageSize,
                cursor = request.optionalText("cursor"),
            )
            linkedMapOf<String, Any>("objects" to page.objects).apply {
                page.nextCursor?.let { put("nextCursor", it) }
            }
        },
        support.jsonFunction("host_storage_object_stat") { request ->
            val node = resolveHostStorageNode(request.requiredObject("node"), storageObjects)
            val stat = storageObjects.statOrNull(node, request.requiredObjectKey("objectKey"))
                ?: return@jsonFunction mapOf("exists" to false)
            linkedMapOf<String, Any>(
                "exists" to true,
                "size" to stat.size,
            ).apply {
                stat.contentType?.let { put("contentType", it) }
            }
        },
        support.rawReadFunction("host_storage_object_read") { request ->
            val node = resolveHostStorageNode(request.requiredObject("node"), storageObjects)
            val objectKey = request.requiredObjectKey("objectKey")
            if (storageObjects.statOrNull(node, objectKey) == null) {
                null
            } else {
                storageObjects.openStream(node, objectKey).use { it.readAllBytes() }
            }
        },
        support.binaryWriteFunction("host_storage_object_write") { meta, data ->
            val node = resolveHostStorageNode(meta.requiredObject("node"), storageObjects)
            if (node.readonly) conflict("Storage node is readonly")
            storageObjects.write(
                node,
                meta.requiredObjectKey("objectKey"),
                data,
                meta.requiredText("contentType"),
            )
            null
        },
        support.jsonFunction("host_storage_object_delete") { request ->
            val node = resolveHostStorageNode(request.requiredObject("node"), storageObjects)
            if (node.readonly) conflict("Storage node is readonly")
            storageObjects.delete(node, request.requiredObjectKey("objectKey"))
            null
        },
    )
}

internal fun resolveHostStorageNode(
    request: ObjectNode,
    storageObjects: StorageNodeObjectService,
): StorageNode {
    val type = when (request.requiredText("type")) {
        "FS" -> FileProviderType.FILE_SYSTEM
        "OSS" -> FileProviderType.OSS
        else -> invalidArgument("Storage node type must be 'FS' or 'OSS'")
    }
    val id = request.requiredLong("id")
    return try {
        storageObjects.resolve(type, id)
    } catch (ex: EmptyResultException) {
        throw PluginHostException(PluginHostErrorCode.NOT_FOUND, "Storage node ${request.requiredText("type")}:$id was not found", ex)
    }
}

private val HOST_FS_NODE_FETCHER: Fetcher<FileProviderFileSystem> =
    newFetcher(FileProviderFileSystem::class).by {
        allScalarFields()
    }

private val HOST_OSS_NODE_FETCHER: Fetcher<FileProviderOss> =
    newFetcher(FileProviderOss::class).by {
        name()
        host()
        bucket()
        parentPath()
        readonly()
    }
