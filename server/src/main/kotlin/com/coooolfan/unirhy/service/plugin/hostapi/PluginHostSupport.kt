package com.coooolfan.unirhy.service.plugin.hostapi

import com.coooolfan.unirhy.service.plugin.WasmPluginException
import org.babyfish.jimmer.error.CodeBasedRuntimeException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import run.endive.runtime.HostFunction
import run.endive.runtime.Instance
import run.endive.wasm.types.FunctionType
import run.endive.wasm.types.ValType
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode

internal const val HOST_DEFAULT_PAGE_SIZE = 100
internal const val HOST_MAX_PAGE_SIZE = 1000
private val hostSupportLogger = LoggerFactory.getLogger(PluginHostSupport::class.java)

internal val PLUGIN_HOST_FUNCTION_NAMES: Set<String> = setOf(
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
    "host_work_random",
    "host_work_update",
    "host_work_delete",
    "host_work_merge",
    "host_recording_get",
    "host_recording_list",
    "host_recording_update",
    "host_recording_merge",
    "host_album_list",
    "host_album_get",
    "host_album_search",
    "host_album_update",
    "host_album_reorder_recordings",
    "host_media_file_get",
    "host_media_file_create",
    "host_media_file_delete",
    "host_asset_list",
    "host_asset_create",
    "host_asset_delete",
    "host_storage_fs_node_list",
    "host_storage_oss_node_list",
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

internal val HOST_JSON_FUNCTION_TYPE: FunctionType =
    FunctionType.of(listOf(ValType.I32, ValType.I32), listOf(ValType.I64))

internal val HOST_BINARY_WRITE_FUNCTION_TYPE: FunctionType =
    FunctionType.of(
        listOf(ValType.I32, ValType.I32, ValType.I32, ValType.I32),
        listOf(ValType.I64),
    )

internal fun validatePluginHostFunctions(functions: List<HostFunction>) {
    val names = functions.map { it.name() }
    require(names.size == names.distinct().size) { "Duplicate plugin Host function name" }
    require(names.toSet() == PLUGIN_HOST_FUNCTION_NAMES) {
        val missing = PLUGIN_HOST_FUNCTION_NAMES - names.toSet()
        val unexpected = names.toSet() - PLUGIN_HOST_FUNCTION_NAMES
        "Invalid plugin Host function catalog; missing=$missing, unexpected=$unexpected"
    }
    functions.forEach { function ->
        require(function.module() == "env") { "Host function ${function.name()} must use the env module" }
        val expectedParams = when (function.name()) {
            "host_log" -> listOf(ValType.I32, ValType.I32, ValType.I32)
            "host_storage_object_write" -> listOf(ValType.I32, ValType.I32, ValType.I32, ValType.I32)
            else -> listOf(ValType.I32, ValType.I32)
        }
        val expectedReturns = if (function.name() == "host_log") emptyList() else listOf(ValType.I64)
        require(function.paramTypes() == expectedParams && function.returnTypes() == expectedReturns) {
            "Invalid signature for plugin Host function ${function.name()}"
        }
    }
}

internal enum class PluginHostErrorCode {
    INVALID_ARGUMENT,
    NOT_FOUND,
    CONFLICT,
    RESPONSE_TOO_LARGE,
    INTERNAL,
}

internal class PluginHostException(
    val errorCode: PluginHostErrorCode,
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

internal data class HostPageRequest(
    val pageIndex: Int,
    val pageSize: Int,
)

internal interface PluginHostCallExecutor {
    fun <T> execute(block: () -> T): T
}

internal val DIRECT_PLUGIN_HOST_CALL_EXECUTOR = object : PluginHostCallExecutor {
    override fun <T> execute(block: () -> T): T = block()
}

internal class NestedPluginHostCallExecutor(
    transactionManager: PlatformTransactionManager,
) : PluginHostCallExecutor {
    private val transaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_NESTED
    }

    override fun <T> execute(block: () -> T): T = transaction.execute { block() }
}

/** Shared JSON, envelope, error, and guest-memory support for Host imports. */
internal class PluginHostSupport(
    private val objectMapper: ObjectMapper,
    private val callExecutor: PluginHostCallExecutor = DIRECT_PLUGIN_HOST_CALL_EXECUTOR,
    private val instanceRef: () -> Instance,
) {
    fun jsonFunction(
        name: String,
        handler: (ObjectNode) -> Any?,
    ): HostFunction = HostFunction("env", name, HOST_JSON_FUNCTION_TYPE) { _: Instance, args: LongArray ->
        val request = readRequest(args[0].toInt(), args[1].toInt())
        val response = try {
            callExecutor.execute { success(handler(request)) }
        } catch (ex: Exception) {
            val error = mapError(ex)
            logInternalError(name, error, ex)
            failure(error)
        }
        writeJson(response)
    }

    fun binaryWriteFunction(
        name: String,
        handler: (meta: ObjectNode, data: ByteArray) -> Any?,
    ): HostFunction = HostFunction("env", name, HOST_BINARY_WRITE_FUNCTION_TYPE) { _: Instance, args: LongArray ->
        val meta = readRequest(args[0].toInt(), args[1].toInt())
        val data = readBytes(args[2].toInt(), args[3].toInt())
        val response = try {
            callExecutor.execute { success(handler(meta, data)) }
        } catch (ex: Exception) {
            val error = mapError(ex)
            logInternalError(name, error, ex)
            failure(error)
        }
        writeJson(response)
    }

    fun rawReadFunction(
        name: String,
        handler: (ObjectNode) -> ByteArray?,
    ): HostFunction = HostFunction("env", name, HOST_JSON_FUNCTION_TYPE) { _: Instance, args: LongArray ->
        val request = readRequest(args[0].toInt(), args[1].toInt())
        val bytes = callExecutor.execute { handler(request) }
            ?: return@HostFunction longArrayOf(0L)
        writeBytes(bytes)
    }

    fun readRequest(ptr: Int, len: Int): ObjectNode {
        val bytes = readBytes(ptr, len)
        val node = try {
            objectMapper.readTree(bytes)
        } catch (ex: Exception) {
            throw WasmPluginException("Host request is not valid JSON", ex)
        }
        if (node !is ObjectNode) {
            throw WasmPluginException("Host request must be a JSON object")
        }
        return node
    }

    fun readBytes(ptr: Int, len: Int): ByteArray {
        if (ptr < 0 || len < 0) {
            throw WasmPluginException("Host request contains a negative pointer or length")
        }
        return instanceRef().memory().readBytes(ptr, len)
    }

    fun writeJson(value: Any): LongArray = writeBytes(objectMapper.writeValueAsBytes(value))

    fun writeBytes(bytes: ByteArray): LongArray {
        if (bytes.isEmpty()) return longArrayOf(0L)
        val instance = instanceRef()
        val ptr = instance.export("alloc").apply(bytes.size.toLong())[0].toInt()
        instance.memory().write(ptr, bytes)
        val packed = (ptr.toLong() shl 32) or (bytes.size.toLong() and 0xFFFF_FFFFL)
        return longArrayOf(packed)
    }

    fun page(request: ObjectNode): HostPageRequest {
        val pageIndex = request.optionalInt("pageIndex") ?: 0
        val pageSize = request.optionalInt("pageSize") ?: HOST_DEFAULT_PAGE_SIZE
        if (pageIndex < 0) invalidArgument("pageIndex must be greater than or equal to 0")
        if (pageSize !in 1..HOST_MAX_PAGE_SIZE) {
            invalidArgument("pageSize must be between 1 and $HOST_MAX_PAGE_SIZE")
        }
        return HostPageRequest(pageIndex, pageSize)
    }

    private fun success(data: Any?): ObjectNode = objectMapper.createObjectNode().apply {
        put("ok", true)
        if (data == null) {
            putNull("data")
        } else {
            set("data", objectMapper.valueToTree(data))
        }
    }

    private fun failure(error: PluginHostException): ObjectNode = objectMapper.createObjectNode().apply {
        put("ok", false)
        set("error", objectMapper.createObjectNode().apply {
            put("code", error.errorCode.name)
            put("message", error.message)
        })
    }

    private fun mapError(ex: Exception): PluginHostException {
        if (ex is PluginHostException) return ex
        if (ex is DataIntegrityViolationException) {
            return PluginHostException(PluginHostErrorCode.CONFLICT, "The operation conflicts with existing data", ex)
        }
        if (ex is java.nio.file.FileAlreadyExistsException) {
            return PluginHostException(PluginHostErrorCode.CONFLICT, "The storage object already exists", ex)
        }
        if (ex is java.io.FileNotFoundException || ex is java.nio.file.NoSuchFileException ||
            ex is NoSuchElementException
        ) {
            return PluginHostException(PluginHostErrorCode.NOT_FOUND, "The requested resource was not found", ex)
        }
        if (ex is CodeBasedRuntimeException) {
            val qualifiedCode = "${ex.family}:${ex.code}"
            val code = when {
                ex.code.contains("NOT_FOUND") -> PluginHostErrorCode.NOT_FOUND
                ex.code.contains("CONFLICT") || ex.code.contains("MISMATCH") ||
                    ex.code.contains("DUPLICATE") || ex.code.contains("NOT_UNIQUE") ||
                    ex.code.contains("OPTIMISTIC_LOCK") || qualifiedCode == "RECORDING:WORK_MISMATCH" ->
                    PluginHostErrorCode.CONFLICT
                qualifiedCode == "TASK:PLUGIN_UNAVAILABLE" -> PluginHostErrorCode.CONFLICT
                ex.code.contains("INVALID") || ex.code.contains("REQUIRED") ||
                    ex.code.contains("UNSUPPORTED") -> PluginHostErrorCode.INVALID_ARGUMENT
                else -> PluginHostErrorCode.INTERNAL
            }
            return PluginHostException(code, ex.message ?: qualifiedCode, ex)
        }
        if (ex is IllegalArgumentException) {
            return PluginHostException(
                PluginHostErrorCode.INVALID_ARGUMENT,
                ex.message ?: "Invalid argument",
                ex,
            )
        }
        return PluginHostException(PluginHostErrorCode.INTERNAL, "Internal Host API error", ex)
    }

    private fun logInternalError(name: String, error: PluginHostException, original: Exception) {
        if (error.errorCode == PluginHostErrorCode.INTERNAL) {
            hostSupportLogger.error("Plugin Host API call failed: function={}", name, original)
        }
    }
}

internal fun invalidArgument(message: String): Nothing =
    throw PluginHostException(PluginHostErrorCode.INVALID_ARGUMENT, message)

internal fun notFound(message: String): Nothing =
    throw PluginHostException(PluginHostErrorCode.NOT_FOUND, message)

internal fun conflict(message: String): Nothing =
    throw PluginHostException(PluginHostErrorCode.CONFLICT, message)

internal fun responseTooLarge(message: String): Nothing =
    throw PluginHostException(PluginHostErrorCode.RESPONSE_TOO_LARGE, message)

internal fun ObjectNode.requiredNode(name: String): JsonNode =
    get(name) ?: invalidArgument("Missing required field: $name")

internal fun ObjectNode.requiredObject(name: String): ObjectNode =
    requiredNode(name).takeIf { it is ObjectNode } as? ObjectNode
        ?: invalidArgument("Field '$name' must be an object")

internal fun ObjectNode.requiredText(name: String): String {
    val value = requiredNode(name)
    if (!value.isString) invalidArgument("Field '$name' must be a string")
    return value.stringValue()
}

internal fun ObjectNode.requiredNonBlankText(name: String): String =
    requiredText(name).also { if (it.isBlank()) invalidArgument("Field '$name' must not be blank") }

internal fun ObjectNode.requiredObjectKey(name: String): String {
    val value = requiredNonBlankText(name)
    val normalized = value.replace('\\', '/')
    if (normalized.startsWith('/') || normalized.endsWith('/') ||
        normalized.split('/').any { it == "." || it == ".." }
    ) {
        invalidArgument("Field '$name' is not a valid object key")
    }
    return normalized
}

internal fun ObjectNode.optionalText(name: String): String? {
    val value = get(name) ?: return null
    if (value.isNull) return null
    if (!value.isString) invalidArgument("Field '$name' must be a string")
    return value.stringValue()
}

internal fun ObjectNode.requiredNullableText(name: String): String? {
    val value = requiredNode(name)
    if (value.isNull) return null
    if (!value.isString) invalidArgument("Field '$name' must be a string or null")
    return value.stringValue()
}

internal fun ObjectNode.requiredLong(name: String): Long {
    val value = requiredNode(name)
    if (!value.isIntegralNumber) invalidArgument("Field '$name' must be an integer")
    return value.longValue()
}

internal fun ObjectNode.optionalLong(name: String): Long? {
    val value = get(name) ?: return null
    if (value.isNull) return null
    if (!value.isIntegralNumber) invalidArgument("Field '$name' must be an integer")
    return value.longValue()
}

internal fun ObjectNode.optionalInt(name: String): Int? {
    val value = optionalLong(name) ?: return null
    if (value !in Int.MIN_VALUE..Int.MAX_VALUE) invalidArgument("Field '$name' is outside the integer range")
    return value.toInt()
}

internal fun ObjectNode.optionalBoolean(name: String): Boolean? {
    val value = get(name) ?: return null
    if (value.isNull) return null
    if (!value.isBoolean) invalidArgument("Field '$name' must be a boolean")
    return value.booleanValue()
}

internal fun ObjectNode.requiredLongList(name: String): List<Long> {
    val value = requiredNode(name)
    if (!value.isArray) invalidArgument("Field '$name' must be an array")
    return value.mapIndexed { index, item ->
        if (!item.isIntegralNumber) invalidArgument("Field '$name[$index]' must be an integer")
        item.longValue()
    }
}

internal fun ObjectNode.optionalLongList(name: String): List<Long>? =
    if (!has(name) || get(name).isNull) null else requiredLongList(name)

internal fun ObjectNode.requiredTextList(name: String): List<String> {
    val value = requiredNode(name)
    if (!value.isArray) invalidArgument("Field '$name' must be an array")
    return value.mapIndexed { index, item ->
        if (!item.isString) invalidArgument("Field '$name[$index]' must be a string")
        item.stringValue()
    }
}

internal fun ObjectNode.optionalTextList(name: String): List<String>? =
    if (!has(name) || get(name).isNull) null else requiredTextList(name)
