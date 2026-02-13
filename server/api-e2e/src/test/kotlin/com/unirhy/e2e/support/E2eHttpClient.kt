package com.unirhy.e2e.support

import java.lang.reflect.Array as ReflectArray
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

class E2eHttpClient(private val baseUrl: String) {
    private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
    private val httpClient = HttpClient.newBuilder()
        .cookieHandler(cookieManager)
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    fun get(path: String, headers: Map<String, String> = emptyMap()): HttpResponse<String> {
        return requestText(
            E2eRequest(
                method = HttpMethod.GET,
                path = path,
                headers = headers,
            ),
        )
    }

    fun head(path: String, headers: Map<String, String> = emptyMap()): HttpResponse<String> {
        return requestText(
            E2eRequest(
                method = HttpMethod.HEAD,
                path = path,
                headers = headers,
            ),
        )
    }

    fun getBytes(path: String, headers: Map<String, String> = emptyMap()): HttpResponse<ByteArray> {
        return requestBytes(
            E2eRequest(
                method = HttpMethod.GET,
                path = path,
                headers = headers,
            ),
        )
    }

    fun get(
        path: String,
        query: Map<String, Any?>,
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse<String> {
        return requestText(
            E2eRequest(
                method = HttpMethod.GET,
                path = path,
                query = query,
                headers = headers,
            ),
        )
    }

    fun getBytes(
        path: String,
        query: Map<String, Any?>,
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse<ByteArray> {
        return requestBytes(
            E2eRequest(
                method = HttpMethod.GET,
                path = path,
                query = query,
                headers = headers,
            ),
        )
    }

    fun postJson(path: String, body: Any, headers: Map<String, String> = emptyMap()): HttpResponse<String> {
        return post(path = path, headers = headers, json = body)
    }

    fun post(
        path: String,
        query: Map<String, Any?> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        json: Any? = null,
        form: Map<String, Any?>? = null,
    ): HttpResponse<String> {
        return requestText(
            E2eRequest(
                method = HttpMethod.POST,
                path = path,
                query = query,
                headers = headers,
                json = json,
                form = form,
            ),
        )
    }

    fun put(
        path: String,
        query: Map<String, Any?> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        json: Any? = null,
        form: Map<String, Any?>? = null,
    ): HttpResponse<String> {
        return requestText(
            E2eRequest(
                method = HttpMethod.PUT,
                path = path,
                query = query,
                headers = headers,
                json = json,
                form = form,
            ),
        )
    }

    fun delete(
        path: String,
        query: Map<String, Any?> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        json: Any? = null,
        form: Map<String, Any?>? = null,
    ): HttpResponse<String> {
        return requestText(
            E2eRequest(
                method = HttpMethod.DELETE,
                path = path,
                query = query,
                headers = headers,
                json = json,
                form = form,
            ),
        )
    }

    fun requestText(request: E2eRequest): HttpResponse<String> {
        return httpClient.send(buildHttpRequest(request), HttpResponse.BodyHandlers.ofString())
    }

    fun requestBytes(request: E2eRequest): HttpResponse<ByteArray> {
        return httpClient.send(buildHttpRequest(request), HttpResponse.BodyHandlers.ofByteArray())
    }

    private fun buildHttpRequest(request: E2eRequest): HttpRequest {
        require(!(request.json != null && request.form != null)) {
            "json and form cannot be provided at the same time"
        }

        val headers = linkedMapOf<String, String>()
        val bodyPublisher = when {
            request.json != null -> {
                headers["Content-Type"] = CONTENT_TYPE_JSON
                val payload = E2eJson.mapper.writeValueAsString(request.json)
                HttpRequest.BodyPublishers.ofString(payload)
            }

            request.form != null -> {
                headers["Content-Type"] = CONTENT_TYPE_FORM
                HttpRequest.BodyPublishers.ofString(encodeParams(request.form))
            }

            else -> HttpRequest.BodyPublishers.noBody()
        }
        headers.putAll(request.headers)

        val httpRequestBuilder = HttpRequest.newBuilder(uri(request.path, request.query))
        headers.forEach { (name, value) -> httpRequestBuilder.header(name, value) }
        return httpRequestBuilder
            .method(request.method.value, bodyPublisher)
            .build()
    }

    private fun uri(path: String, query: Map<String, Any?>): URI {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        val queryString = encodeParams(query)
        val url = if (queryString.isBlank()) {
            "$baseUrl$normalizedPath"
        } else {
            "$baseUrl$normalizedPath?$queryString"
        }
        return URI.create(url)
    }

    private fun encodeParams(params: Map<String, Any?>): String {
        return params.entries.asSequence()
            .flatMap { (key, rawValue) ->
                flattenValue(key, rawValue)
            }
            .joinToString("&") { (key, value) ->
                "${urlEncode(key)}=${urlEncode(value)}"
            }
    }

    private fun flattenValue(key: String, rawValue: Any?): Sequence<Pair<String, String>> {
        if (rawValue == null) {
            return emptySequence()
        }
        if (rawValue is Iterable<*>) {
            return rawValue.asSequence()
                .mapNotNull { value -> value?.toString()?.let { key to it } }
        }
        if (rawValue.javaClass.isArray) {
            val size = ReflectArray.getLength(rawValue)
            return (0 until size).asSequence()
                .mapNotNull { index ->
                    ReflectArray.get(rawValue, index)
                        ?.toString()
                        ?.let { key to it }
                }
        }
        return sequenceOf(key to rawValue.toString())
    }

    companion object {
        private const val CONTENT_TYPE_JSON = "application/json"
        private const val CONTENT_TYPE_FORM = "application/x-www-form-urlencoded"

        fun urlEncode(value: String): String {
            return URLEncoder.encode(value, StandardCharsets.UTF_8)
        }

        fun urlDecode(value: String): String {
            return URLDecoder.decode(value, StandardCharsets.UTF_8)
        }
    }
}

enum class HttpMethod(val value: String) {
    GET("GET"),
    HEAD("HEAD"),
    POST("POST"),
    PUT("PUT"),
    DELETE("DELETE"),
}

data class E2eRequest(
    val method: HttpMethod,
    val path: String,
    val query: Map<String, Any?> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val json: Any? = null,
    // null means no form body; non-null means send form body (empty map sends empty payload with form content type).
    val form: Map<String, Any?>? = null,
)
