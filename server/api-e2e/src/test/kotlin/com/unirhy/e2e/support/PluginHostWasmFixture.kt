package com.unirhy.e2e.support

import java.io.ByteArrayOutputStream

/** Builds a small WASM plugin that links the complete Host API and exercises both JSON and binary calls. */
object PluginHostWasmFixture {
    private const val JSON_TYPE = 0
    private const val LOG_TYPE = 1
    private const val BINARY_WRITE_TYPE = 2
    private const val ALLOC_TYPE = 3
    private const val VOID_BINARY_TYPE = 4

    private const val PLAN_PTR = 1024
    private const val ARTIST_REQUEST_PTR = 2048
    private const val WORK_REQUEST_PTR = 3072
    private const val RECORDING_REQUEST_PTR = 4096
    private const val DEFAULT_NODE_REQUEST_PTR = 5120
    private const val STORAGE_META_PTR = 6144
    private const val MEDIA_CREATE_REQUEST_PTR = 7168
    private const val MEDIA_LOCATION_REQUEST_PTR = 8192
    private const val OSS_MEDIA_LOCATION_REQUEST_PTR = 9216
    private const val ASSET_BY_MEDIA_REQUEST_PTR = 10240
    private const val ASSET_AND_REQUEST_PTR = 11264
    private const val ASSET_MISMATCH_REQUEST_PTR = 12288
    private const val STORAGE_DATA_PTR = 13312
    private const val ALLOC_PTR = 16384
    private const val RESPONSE_OK_VALUE_OFFSET = 6
    private const val RESPONSE_DATA_ARRAY_ITEM_OFFSET = 19

    private val hostFunctionNames = listOf(
        "host_log",
        "host_http_request",
        "host_http_download_to_storage",
        "host_artist_list",
        "host_artist_get_by_ids",
        "host_artist_search",
        "host_artist_create",
        "host_artist_update",
        "host_artist_merge",
        "host_artist_split",
        "host_work_list",
        "host_work_get",
        "host_work_search",
        "host_work_create",
        "host_work_random",
        "host_work_update",
        "host_work_delete",
        "host_work_merge",
        "host_recording_get",
        "host_recording_list",
        "host_recording_create",
        "host_recording_update",
        "host_recording_merge",
        "host_album_list",
        "host_album_get",
        "host_album_search",
        "host_album_update",
        "host_album_reorder_recordings",
        "host_media_file_get",
        "host_media_file_get_by_location",
        "host_media_file_create",
        "host_media_file_delete",
        "host_asset_list",
        "host_asset_create",
        "host_asset_delete",
        "host_storage_fs_node_list",
        "host_storage_oss_node_list",
        "host_storage_default_write_node_get",
        "host_storage_object_list",
        "host_storage_object_stat",
        "host_storage_object_read",
        "host_storage_object_write",
        "host_storage_object_delete",
        "host_playlist_list",
        "host_playlist_get",
        "host_playlist_create",
        "host_playlist_update",
        "host_playlist_delete",
        "host_playlist_add_recording",
        "host_playlist_remove_recording",
        "host_playlist_reorder_recordings",
        "host_task_definition_list",
        "host_task_definition_get",
        "host_task_submission_create",
        "host_task_submission_list",
        "host_task_submission_get",
        "host_task_submission_tasks",
        "host_task_submission_patch",
        "host_task_submission_delete",
        "host_task_list",
        "host_task_get",
        "host_task_patch",
        "host_task_statistics",
        "host_plugin_list",
        "host_plugin_get",
        "host_account_list",
        "host_account_get",
    )

    fun build(
        artistName: String,
        workTitle: String,
        recordingWorkId: Long,
        recordingTitle: String,
        ossNodeId: Long,
        ossObjectKey: String,
        assetRecordingId: Long,
        otherRecordingId: Long,
        assetMediaFileId: Long,
        objectKey: String,
        objectBytes: ByteArray,
    ): ByteArray {
        check(hostFunctionNames.size == 67 && hostFunctionNames.distinct().size == 67)

        val plan = "[{}]".toByteArray()
        val artistRequest = E2eJson.mapper.writeValueAsBytes(mapOf("displayName" to artistName))
        val workRequest = E2eJson.mapper.writeValueAsBytes(mapOf("title" to workTitle))
        val recordingRequest = E2eJson.mapper.writeValueAsBytes(
            mapOf(
                "workId" to recordingWorkId,
                "artistIds" to emptyList<Long>(),
                "label" to listOf("Fixture"),
                "title" to recordingTitle,
                "comment" to "Created by the real WASM Host API fixture",
                "durationMs" to 1234,
                "defaultInWork" to false,
                "coverId" to null,
            ),
        )
        val defaultNodeRequest = E2eJson.mapper.writeValueAsBytes(emptyMap<String, Any>())
        val node = mapOf("type" to "FS", "id" to 0)
        val storageMeta = E2eJson.mapper.writeValueAsBytes(
            mapOf(
                "node" to node,
                "objectKey" to objectKey,
                "contentType" to "application/octet-stream",
            ),
        )
        val mediaCreateRequest = E2eJson.mapper.writeValueAsBytes(
            mapOf(
                "node" to node,
                "objectKey" to objectKey,
                "mimeType" to "application/octet-stream",
            ),
        )
        val mediaLocationRequest = E2eJson.mapper.writeValueAsBytes(
            mapOf(
                "node" to node,
                "objectKey" to objectKey,
            ),
        )
        val ossMediaLocationRequest = E2eJson.mapper.writeValueAsBytes(
            mapOf(
                "node" to mapOf("type" to "OSS", "id" to ossNodeId),
                "objectKey" to ossObjectKey,
            ),
        )
        val assetByMediaRequest = E2eJson.mapper.writeValueAsBytes(
            mapOf("mediaFileId" to assetMediaFileId),
        )
        val assetAndRequest = E2eJson.mapper.writeValueAsBytes(
            mapOf(
                "recordingId" to assetRecordingId,
                "mediaFileId" to assetMediaFileId,
            ),
        )
        val assetMismatchRequest = E2eJson.mapper.writeValueAsBytes(
            mapOf(
                "recordingId" to otherRecordingId,
                "mediaFileId" to assetMediaFileId,
            ),
        )
        requireFits(PLAN_PTR, ARTIST_REQUEST_PTR, plan)
        requireFits(ARTIST_REQUEST_PTR, WORK_REQUEST_PTR, artistRequest)
        requireFits(WORK_REQUEST_PTR, RECORDING_REQUEST_PTR, workRequest)
        requireFits(RECORDING_REQUEST_PTR, DEFAULT_NODE_REQUEST_PTR, recordingRequest)
        requireFits(DEFAULT_NODE_REQUEST_PTR, STORAGE_META_PTR, defaultNodeRequest)
        requireFits(STORAGE_META_PTR, MEDIA_CREATE_REQUEST_PTR, storageMeta)
        requireFits(MEDIA_CREATE_REQUEST_PTR, MEDIA_LOCATION_REQUEST_PTR, mediaCreateRequest)
        requireFits(MEDIA_LOCATION_REQUEST_PTR, OSS_MEDIA_LOCATION_REQUEST_PTR, mediaLocationRequest)
        requireFits(OSS_MEDIA_LOCATION_REQUEST_PTR, ASSET_BY_MEDIA_REQUEST_PTR, ossMediaLocationRequest)
        requireFits(ASSET_BY_MEDIA_REQUEST_PTR, ASSET_AND_REQUEST_PTR, assetByMediaRequest)
        requireFits(ASSET_AND_REQUEST_PTR, ASSET_MISMATCH_REQUEST_PTR, assetAndRequest)
        requireFits(ASSET_MISMATCH_REQUEST_PTR, STORAGE_DATA_PTR, assetMismatchRequest)
        requireFits(STORAGE_DATA_PTR, ALLOC_PTR, objectBytes)

        val artistCreateIndex = hostFunctionNames.indexOf("host_artist_create")
        val workCreateIndex = hostFunctionNames.indexOf("host_work_create")
        val recordingCreateIndex = hostFunctionNames.indexOf("host_recording_create")
        val defaultNodeGetIndex = hostFunctionNames.indexOf("host_storage_default_write_node_get")
        val mediaFileCreateIndex = hostFunctionNames.indexOf("host_media_file_create")
        val mediaFileGetByLocationIndex = hostFunctionNames.indexOf("host_media_file_get_by_location")
        val assetListIndex = hostFunctionNames.indexOf("host_asset_list")
        val storageReadIndex = hostFunctionNames.indexOf("host_storage_object_read")
        val storageWriteIndex = hostFunctionNames.indexOf("host_storage_object_write")
        val firstDefinedFunctionIndex = hostFunctionNames.size

        return ByteArrayOutputStream().apply {
            write(byteArrayOf(0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00))
            writeSection(
                1,
                vector(
                    functionType(listOf(I32, I32), listOf(I64)),
                    functionType(listOf(I32, I32, I32), emptyList()),
                    functionType(listOf(I32, I32, I32, I32), listOf(I64)),
                    functionType(listOf(I32), listOf(I32)),
                    functionType(listOf(I32, I32), emptyList()),
                ),
            )
            writeSection(
                2,
                vector(
                    hostFunctionNames.map { name ->
                        val typeIndex = when (name) {
                            "host_log" -> LOG_TYPE
                            "host_storage_object_write" -> BINARY_WRITE_TYPE
                            else -> JSON_TYPE
                        }
                        bytes(name("env"), name(name), byteArrayOf(0x00), unsignedLeb128(typeIndex))
                    },
                ),
            )
            writeSection(
                3,
                vector(
                    unsignedLeb128(ALLOC_TYPE),
                    unsignedLeb128(VOID_BINARY_TYPE),
                    unsignedLeb128(JSON_TYPE),
                    unsignedLeb128(VOID_BINARY_TYPE),
                ),
            )
            writeSection(5, vector(bytes(byteArrayOf(0x00), unsignedLeb128(1))))
            writeSection(
                7,
                vector(
                    export("alloc", FUNCTION_EXPORT, firstDefinedFunctionIndex),
                    export("dealloc", FUNCTION_EXPORT, firstDefinedFunctionIndex + 1),
                    export("plan", FUNCTION_EXPORT, firstDefinedFunctionIndex + 2),
                    export("run", FUNCTION_EXPORT, firstDefinedFunctionIndex + 3),
                    export("memory", MEMORY_EXPORT, 0),
                ),
            )
            writeSection(
                10,
                vector(
                    functionBody(i32Const(ALLOC_PTR), END),
                    functionBody(END),
                    functionBody(i64Const(pack(PLAN_PTR, plan.size)), END),
                    functionBody(
                        callJsonAndAssertSuccess(ARTIST_REQUEST_PTR, artistRequest, artistCreateIndex),
                        callJsonAndAssertSuccess(WORK_REQUEST_PTR, workRequest, workCreateIndex),
                        callJsonAndAssertSuccess(RECORDING_REQUEST_PTR, recordingRequest, recordingCreateIndex),
                        callJsonAndAssertSuccess(
                            DEFAULT_NODE_REQUEST_PTR,
                            defaultNodeRequest,
                            defaultNodeGetIndex,
                        ),
                        i32Const(STORAGE_META_PTR),
                        i32Const(storageMeta.size),
                        i32Const(STORAGE_DATA_PTR),
                        i32Const(objectBytes.size),
                        call(storageWriteIndex),
                        assertNonZeroI64(),
                        assertMemoryByte(ALLOC_PTR + RESPONSE_OK_VALUE_OFFSET, 't'.code),
                        callJsonAndAssertSuccess(
                            MEDIA_CREATE_REQUEST_PTR,
                            mediaCreateRequest,
                            mediaFileCreateIndex,
                        ),
                        callJsonAndAssertSuccess(
                            MEDIA_LOCATION_REQUEST_PTR,
                            mediaLocationRequest,
                            mediaFileGetByLocationIndex,
                        ),
                        callJsonAndAssertSuccess(
                            OSS_MEDIA_LOCATION_REQUEST_PTR,
                            ossMediaLocationRequest,
                            mediaFileGetByLocationIndex,
                        ),
                        callJsonAndAssertArrayResult(
                            ASSET_BY_MEDIA_REQUEST_PTR,
                            assetByMediaRequest,
                            assetListIndex,
                            nonEmpty = true,
                        ),
                        callJsonAndAssertArrayResult(
                            ASSET_AND_REQUEST_PTR,
                            assetAndRequest,
                            assetListIndex,
                            nonEmpty = true,
                        ),
                        callJsonAndAssertArrayResult(
                            ASSET_MISMATCH_REQUEST_PTR,
                            assetMismatchRequest,
                            assetListIndex,
                            nonEmpty = false,
                        ),
                        i32Const(STORAGE_META_PTR),
                        i32Const(storageMeta.size),
                        call(storageReadIndex),
                        i64Const(pack(ALLOC_PTR, objectBytes.size)),
                        assertI64Equal(),
                        objectBytes.mapIndexed { index, byte ->
                            assertMemoryByte(ALLOC_PTR + index, byte.toInt() and 0xFF)
                        }.let(::bytes),
                        END,
                    ),
                ),
            )
            writeSection(
                11,
                vector(
                    dataSegment(PLAN_PTR, plan),
                    dataSegment(ARTIST_REQUEST_PTR, artistRequest),
                    dataSegment(WORK_REQUEST_PTR, workRequest),
                    dataSegment(RECORDING_REQUEST_PTR, recordingRequest),
                    dataSegment(DEFAULT_NODE_REQUEST_PTR, defaultNodeRequest),
                    dataSegment(STORAGE_META_PTR, storageMeta),
                    dataSegment(MEDIA_CREATE_REQUEST_PTR, mediaCreateRequest),
                    dataSegment(MEDIA_LOCATION_REQUEST_PTR, mediaLocationRequest),
                    dataSegment(OSS_MEDIA_LOCATION_REQUEST_PTR, ossMediaLocationRequest),
                    dataSegment(ASSET_BY_MEDIA_REQUEST_PTR, assetByMediaRequest),
                    dataSegment(ASSET_AND_REQUEST_PTR, assetAndRequest),
                    dataSegment(ASSET_MISMATCH_REQUEST_PTR, assetMismatchRequest),
                    dataSegment(STORAGE_DATA_PTR, objectBytes),
                ),
            )
        }.toByteArray()
    }

    private fun requireFits(pointer: Int, nextPointer: Int, data: ByteArray) {
        require(pointer >= 0 && pointer + data.size <= nextPointer) { "WASM fixture data segments overlap" }
    }

    private fun ByteArrayOutputStream.writeSection(id: Int, payload: ByteArray) {
        write(id)
        write(unsignedLeb128(payload.size))
        write(payload)
    }

    private fun functionType(params: List<Int>, results: List<Int>): ByteArray =
        bytes(byteArrayOf(0x60), valueTypes(params), valueTypes(results))

    private fun valueTypes(types: List<Int>): ByteArray =
        bytes(unsignedLeb128(types.size), types.map { byteArrayOf(it.toByte()) })

    private fun export(name: String, kind: Int, index: Int): ByteArray =
        bytes(name(name), byteArrayOf(kind.toByte()), unsignedLeb128(index))

    private fun functionBody(vararg instructions: ByteArray): ByteArray {
        val body = bytes(byteArrayOf(0x00), instructions.toList())
        return bytes(unsignedLeb128(body.size), body)
    }

    private fun dataSegment(pointer: Int, data: ByteArray): ByteArray =
        bytes(byteArrayOf(0x00), i32Const(pointer), END, unsignedLeb128(data.size), data)

    private fun i32Const(value: Int): ByteArray = bytes(byteArrayOf(0x41), signedLeb128(value.toLong()))

    private fun i64Const(value: Long): ByteArray = bytes(byteArrayOf(0x42), signedLeb128(value))

    private fun call(index: Int): ByteArray = bytes(byteArrayOf(0x10), unsignedLeb128(index))

    private fun callJsonAndAssertSuccess(pointer: Int, request: ByteArray, functionIndex: Int): ByteArray = bytes(
        i32Const(pointer),
        i32Const(request.size),
        call(functionIndex),
        assertNonZeroI64(),
        assertMemoryByte(ALLOC_PTR + RESPONSE_OK_VALUE_OFFSET, 't'.code),
    )

    private fun callJsonAndAssertArrayResult(
        pointer: Int,
        request: ByteArray,
        functionIndex: Int,
        nonEmpty: Boolean,
    ): ByteArray = bytes(
        callJsonAndAssertSuccess(pointer, request, functionIndex),
        assertMemoryByte(ALLOC_PTR + RESPONSE_DATA_ARRAY_ITEM_OFFSET, if (nonEmpty) '{'.code else ']'.code),
    )

    private fun assertNonZeroI64(): ByteArray = bytes(I64_EQZ, TRAP_IF_TRUE)

    private fun assertI64Equal(): ByteArray = bytes(I64_NE, TRAP_IF_TRUE)

    private fun assertMemoryByte(pointer: Int, expected: Int): ByteArray = bytes(
        i32Const(pointer),
        I32_LOAD8_U,
        byteArrayOf(0x00, 0x00),
        i32Const(expected),
        I32_NE,
        TRAP_IF_TRUE,
    )

    private fun pack(pointer: Int, length: Int): Long =
        (pointer.toLong() shl 32) or (length.toLong() and 0xFFFF_FFFFL)

    private fun name(value: String): ByteArray {
        val utf8 = value.toByteArray(Charsets.UTF_8)
        return bytes(unsignedLeb128(utf8.size), utf8)
    }

    private fun vector(vararg elements: ByteArray): ByteArray = vector(elements.toList())

    private fun vector(elements: List<ByteArray>): ByteArray =
        bytes(unsignedLeb128(elements.size), elements)

    private fun bytes(vararg chunks: ByteArray): ByteArray = bytes(chunks.toList())

    private fun bytes(prefix: ByteArray, chunks: List<ByteArray>): ByteArray =
        bytes(listOf(prefix) + chunks)

    private fun bytes(chunks: List<ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        chunks.forEach(output::write)
        return output.toByteArray()
    }

    private fun unsignedLeb128(value: Int): ByteArray {
        require(value >= 0)
        var remaining = value
        val output = ByteArrayOutputStream()
        do {
            var byte = remaining and 0x7F
            remaining = remaining ushr 7
            if (remaining != 0) byte = byte or 0x80
            output.write(byte)
        } while (remaining != 0)
        return output.toByteArray()
    }

    private fun signedLeb128(value: Long): ByteArray {
        var remaining = value
        val output = ByteArrayOutputStream()
        var more: Boolean
        do {
            var byte = (remaining and 0x7F).toInt()
            remaining = remaining shr 7
            val signBitSet = byte and 0x40 != 0
            more = !((remaining == 0L && !signBitSet) || (remaining == -1L && signBitSet))
            if (more) byte = byte or 0x80
            output.write(byte)
        } while (more)
        return output.toByteArray()
    }

    private const val I32 = 0x7F
    private const val I64 = 0x7E
    private const val FUNCTION_EXPORT = 0x00
    private const val MEMORY_EXPORT = 0x02
    private val I32_LOAD8_U = byteArrayOf(0x2D)
    private val I32_NE = byteArrayOf(0x47)
    private val I64_EQZ = byteArrayOf(0x50)
    private val I64_NE = byteArrayOf(0x52)
    private val TRAP_IF_TRUE = byteArrayOf(0x04, 0x40, 0x00, 0x0B)
    private val END = byteArrayOf(0x0B)
}
