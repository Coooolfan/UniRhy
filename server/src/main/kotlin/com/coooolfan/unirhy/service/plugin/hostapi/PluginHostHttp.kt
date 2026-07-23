package com.coooolfan.unirhy.service.plugin.hostapi

import com.coooolfan.unirhy.service.storage.StorageNodeObjectService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import run.endive.runtime.HostFunction
import run.endive.runtime.Instance
import run.endive.wasm.types.FunctionType
import run.endive.wasm.types.ValType
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64
import java.util.HexFormat
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private const val MAX_HTTP_RESPONSE_BYTES = 268_435_456L
private const val MAX_HTTP_REDIRECTS = 5
private const val HTTP_BUFFER_SIZE = 64 * 1024
private val HTTP_REQUEST_TIMEOUT: Duration = Duration.ofSeconds(30)
private val HTTP_BODY_IDLE_TIMEOUT: Duration = Duration.ofSeconds(30)

private val logger: Logger = LoggerFactory.getLogger("PluginHostFunctions")

private val sharedHttpClient: HttpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .followRedirects(HttpClient.Redirect.NEVER)
    .build()

internal data class HostHttpResponse(
    val status: Int,
    val headers: Map<String, List<String>>,
    val bodyBase64: String,
)

internal data class HostStorageNodeReference(
    val type: String,
    val id: Long,
)

internal data class HostDownloadDestination(
    val node: HostStorageNodeReference,
    val objectKey: String,
)

internal data class HostHttpDownloadResponse(
    val status: Int,
    val stored: Boolean,
    val bytesWritten: Long,
    val contentType: String?,
    val sha256: String?,
    val destination: HostDownloadDestination,
)

internal data class RequestHeader(
    val name: String,
    val value: String,
)

/** Network Host imports shared by every enabled plugin. */
internal fun buildDefaultHostFunctions(
    storageObjects: StorageNodeObjectService,
    objectMapper: ObjectMapper,
    instanceRef: () -> Instance,
    callExecutor: PluginHostCallExecutor = DIRECT_PLUGIN_HOST_CALL_EXECUTOR,
): List<HostFunction> {
    val support = PluginHostSupport(objectMapper, callExecutor, instanceRef)

    val hostLog = HostFunction(
        "env",
        "host_log",
        FunctionType.of(listOf(ValType.I32, ValType.I32, ValType.I32), emptyList()),
    ) { _: Instance, args: LongArray ->
        val level = args[0].toInt()
        val message = support.readBytes(args[1].toInt(), args[2].toInt()).toString(Charsets.UTF_8)
        when (level) {
            0 -> logger.debug("[wasm] {}", message)
            1 -> logger.info("[wasm] {}", message)
            2 -> logger.warn("[wasm] {}", message)
            else -> logger.error("[wasm] {}", message)
        }
        longArrayOf()
    }

    val hostHttpRequest = support.jsonFunction("host_http_request") { request ->
        executeHttpRequest(request)
    }

    val hostHttpDownloadToStorage = support.jsonFunction("host_http_download_to_storage") { request ->
        downloadToStorage(request, storageObjects)
    }

    return listOf(hostLog, hostHttpRequest, hostHttpDownloadToStorage)
}

internal fun executeHttpRequest(request: ObjectNode): HostHttpResponse {
    val uri = parseHttpUri(request.requiredText("url"))
    val method = parseMethod(request.requiredText("method"))
    val headers = request.requestHeaders()
    val body = request.bodyBytes()
    val response = send(uri, method, headers, body)

    response.body().use { input ->
        val contentLength = response.knownContentLength()
        if (contentLength != null && contentLength > MAX_HTTP_RESPONSE_BYTES) {
            responseTooLarge("HTTP response exceeds the 256 MiB limit")
        }

        val output = ByteArrayOutputStream()
        transferWithIdleTimeout(input) { buffer, count, bytesWritten ->
            if (bytesWritten > MAX_HTTP_RESPONSE_BYTES - count) {
                responseTooLarge("HTTP response exceeds the 256 MiB limit")
            }
            output.write(buffer, 0, count)
        }
        return HostHttpResponse(
            status = response.statusCode(),
            headers = response.headers().map(),
            bodyBase64 = Base64.getEncoder().encodeToString(output.toByteArray()),
        )
    }
}

internal fun downloadToStorage(
    request: ObjectNode,
    storageObjects: StorageNodeObjectService,
): HostHttpDownloadResponse {
    val initialUri = parseHttpUri(request.requiredText("url"))
    val initialMethod = parseMethod(request.optionalText("method") ?: "GET")
    val headers = request.requestHeaders()
    val maxBytes = request.optionalPositiveLong("maxBytes")
    val destinationRequest = request.requiredObject("destination")
    val nodeRequest = destinationRequest.requiredObject("node")
    val node = resolveHostStorageNode(nodeRequest, storageObjects)
    if (node.readonly) conflict("Storage node is readonly")
    val nodeType = nodeRequest.requiredText("type")
    val nodeId = nodeRequest.requiredLong("id")
    val objectKey = destinationRequest.requiredObjectKey("objectKey")
    val overwrite = request.optionalBoolean("overwrite") ?: false
    val destination = HostDownloadDestination(
        node = HostStorageNodeReference(type = nodeType, id = nodeId),
        objectKey = objectKey,
    )

    val tempFile = Files.createTempFile("unirhy-plugin-download-", ".part")
    try {
        val response = sendFollowingRedirects(initialUri, initialMethod, headers)
        val contentType = response.headers().firstValue("Content-Type").orElse(null)
        if (response.statusCode() !in 200..299) {
            response.body().close()
            return HostHttpDownloadResponse(
                status = response.statusCode(),
                stored = false,
                bytesWritten = 0,
                contentType = contentType,
                sha256 = null,
                destination = destination,
            )
        }

        val digest = MessageDigest.getInstance("SHA-256")
        val bytesWritten = response.body().use { input ->
            val contentLength = response.knownContentLength()
            if (maxBytes != null && contentLength != null && contentLength > maxBytes) {
                responseTooLarge("HTTP response exceeds maxBytes ($maxBytes bytes)")
            }
            Files.newOutputStream(tempFile).use { output ->
                transferWithIdleTimeout(input) { buffer, count, bytesWritten ->
                    if (maxBytes != null && count.toLong() > maxBytes - bytesWritten) {
                        responseTooLarge("HTTP response exceeds maxBytes ($maxBytes bytes)")
                    }
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                }
            }
        }

        try {
            storageObjects.commitDownloadedFile(
                node = node,
                objectKey = objectKey,
                file = tempFile.toFile(),
                contentType = contentType ?: "application/octet-stream",
                overwrite = overwrite,
            )
        } catch (ex: FileAlreadyExistsException) {
            conflict("Storage object already exists: $objectKey")
        }

        return HostHttpDownloadResponse(
            status = response.statusCode(),
            stored = true,
            bytesWritten = bytesWritten,
            contentType = contentType,
            sha256 = HexFormat.of().formatHex(digest.digest()),
            destination = destination,
        )
    } finally {
        runCatching { Files.deleteIfExists(tempFile) }
            .onFailure { ex ->
                logger.warn(
                    "Failed to delete plugin HTTP download temporary file: path={}, error={}",
                    tempFile,
                    ex.message,
                )
            }
    }
}

internal fun sendFollowingRedirects(
    initialUri: URI,
    initialMethod: String,
    headers: List<RequestHeader>,
): HttpResponse<InputStream> {
    var uri = initialUri
    var method = initialMethod
    var requestHeaders = headers
    var redirectCount = 0

    while (true) {
        val response = send(uri, method, requestHeaders, body = null)
        val location = if (response.statusCode() in REDIRECT_STATUS_CODES) {
            response.headers().firstValue("Location").orElse(null)
        } else {
            null
        }
        if (location == null) return response

        response.body().close()
        if (redirectCount >= MAX_HTTP_REDIRECTS) {
            throw PluginHostException(
                PluginHostErrorCode.INTERNAL,
                "HTTP download exceeded the redirect limit of $MAX_HTTP_REDIRECTS",
            )
        }

        val nextUri = try {
            parseHttpUri(uri.resolve(location).toString())
        } catch (ex: PluginHostException) {
            throw PluginHostException(
                PluginHostErrorCode.INTERNAL,
                "HTTP redirect contains an invalid target URL",
                ex,
            )
        }
        if (uri.scheme.equals("https", ignoreCase = true) && nextUri.scheme.equals("http", ignoreCase = true)) {
            throw PluginHostException(
                PluginHostErrorCode.INTERNAL,
                "HTTPS to HTTP redirects are not allowed",
            )
        }

        if (!uri.hasSameHttpAuthority(nextUri)) {
            requestHeaders = requestHeaders.filterNot { header ->
                header.name.lowercase() in SENSITIVE_REDIRECT_HEADERS
            }
        }
        method = redirectedMethod(response.statusCode(), method)
        uri = nextUri
        redirectCount += 1
    }
}

private fun send(
    uri: URI,
    method: String,
    headers: List<RequestHeader>,
    body: ByteArray?,
): HttpResponse<InputStream> {
    val builder = HttpRequest.newBuilder(uri)
    for (header in headers) {
        builder.header(header.name, header.value)
    }
    val publisher = body?.let(HttpRequest.BodyPublishers::ofByteArray) ?: HttpRequest.BodyPublishers.noBody()
    val request = builder
        .timeout(HTTP_REQUEST_TIMEOUT)
        .method(method, publisher)
        .build()
    return try {
        sharedHttpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
    } catch (ex: InterruptedException) {
        Thread.currentThread().interrupt()
        throw PluginHostException(PluginHostErrorCode.INTERNAL, "HTTP request was interrupted", ex)
    } catch (ex: Exception) {
        throw PluginHostException(
            PluginHostErrorCode.INTERNAL,
            "HTTP request failed: ${ex.message ?: ex.javaClass.simpleName}",
            ex,
        )
    }
}

private fun transferWithIdleTimeout(
    input: InputStream,
    consume: (buffer: ByteArray, count: Int, bytesWritten: Long) -> Unit,
): Long {
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    val buffer = ByteArray(HTTP_BUFFER_SIZE)
    var bytesWritten = 0L
    var lastReadAt = System.nanoTime()
    try {
        while (true) {
            val remainingNanos = HTTP_BODY_IDLE_TIMEOUT.toNanos() - (System.nanoTime() - lastReadAt)
            if (remainingNanos <= 0) bodyIdleTimeout()
            val future = executor.submit<Int> { input.read(buffer) }
            val count = try {
                future.get(remainingNanos, TimeUnit.NANOSECONDS)
            } catch (ex: TimeoutException) {
                runCatching { input.close() }
                future.cancel(true)
                bodyIdleTimeout(ex)
            } catch (ex: InterruptedException) {
                runCatching { input.close() }
                future.cancel(true)
                Thread.currentThread().interrupt()
                throw PluginHostException(PluginHostErrorCode.INTERNAL, "HTTP response reading was interrupted", ex)
            } catch (ex: ExecutionException) {
                throw PluginHostException(
                    PluginHostErrorCode.INTERNAL,
                    "Failed to read HTTP response body: ${ex.cause?.message ?: ex.message}",
                    ex.cause ?: ex,
                )
            }

            if (count < 0) break
            if (count == 0) continue
            lastReadAt = System.nanoTime()
            consume(buffer, count, bytesWritten)
            bytesWritten = try {
                Math.addExact(bytesWritten, count.toLong())
            } catch (ex: ArithmeticException) {
                throw PluginHostException(PluginHostErrorCode.INTERNAL, "HTTP response size overflow", ex)
            }
        }
        return bytesWritten
    } finally {
        executor.shutdownNow()
    }
}

private fun bodyIdleTimeout(cause: Throwable? = null): Nothing =
    throw PluginHostException(
        PluginHostErrorCode.INTERNAL,
        "HTTP response body was idle for ${HTTP_BODY_IDLE_TIMEOUT.seconds} seconds",
        cause,
    )

internal fun parseHttpUri(value: String): URI {
    val uri = try {
        URI.create(value)
    } catch (ex: Exception) {
        throw PluginHostException(PluginHostErrorCode.INVALID_ARGUMENT, "Invalid HTTP URL", ex)
    }
    if (uri.host == null || uri.scheme?.lowercase() !in HTTP_SCHEMES) {
        invalidArgument("URL must use http or https and include a host")
    }
    return uri
}

private fun parseMethod(value: String): String {
    val method = value.trim().uppercase()
    if (method.isEmpty()) invalidArgument("HTTP method must not be blank")
    return method
}

private fun ObjectNode.bodyBytes(): ByteArray? {
    val encoded = optionalText("bodyBase64") ?: return null
    return try {
        Base64.getDecoder().decode(encoded)
    } catch (ex: IllegalArgumentException) {
        throw PluginHostException(PluginHostErrorCode.INVALID_ARGUMENT, "bodyBase64 is not valid Base64", ex)
    }
}

private fun ObjectNode.requestHeaders(): List<RequestHeader> {
    val node = get("headers") ?: return emptyList()
    if (node.isNull) return emptyList()
    if (node !is ObjectNode) invalidArgument("Field 'headers' must be an object")

    return buildList {
        for ((name, value) in node.properties()) {
            if (name.isBlank()) invalidArgument("HTTP header name must not be blank")
            when {
                value.isString -> add(RequestHeader(name, value.stringValue()))
                value.isArray -> value.forEachIndexed { index, item ->
                    if (!item.isString) invalidArgument("Field 'headers.$name[$index]' must be a string")
                    add(RequestHeader(name, item.stringValue()))
                }
                else -> invalidArgument("Field 'headers.$name' must be a string or string array")
            }
        }
    }
}

private fun ObjectNode.optionalPositiveLong(name: String): Long? {
    val value = get(name) ?: return null
    if (value.isNull) return null
    if (!value.isIntegralNumber || !value.canConvertToLong()) {
        invalidArgument("Field '$name' must be a positive integer")
    }
    return value.longValue().also {
        if (it <= 0) invalidArgument("Field '$name' must be a positive integer")
    }
}

private fun HttpResponse<*>.knownContentLength(): Long? =
    headers().firstValue("Content-Length").orElse(null)
        ?.toLongOrNull()
        ?.takeIf { it >= 0 }

private fun redirectedMethod(status: Int, method: String): String = when (status) {
    303 -> if (method == "HEAD") "HEAD" else "GET"
    301, 302 -> if (method == "POST") "GET" else method
    else -> method
}

private fun URI.hasSameHttpAuthority(other: URI): Boolean =
    host.equals(other.host, ignoreCase = true) && effectiveHttpPort() == other.effectiveHttpPort()

private fun URI.effectiveHttpPort(): Int = when {
    port >= 0 -> port
    scheme.equals("http", ignoreCase = true) -> 80
    else -> 443
}

private val HTTP_SCHEMES = setOf("http", "https")
private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
private val SENSITIVE_REDIRECT_HEADERS = setOf(
    "authorization",
    "cookie",
    "cookie2",
    "proxy-authorization",
)
