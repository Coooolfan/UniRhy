package com.unirhy.e2e.support

import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
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
        return sendText(path, "GET", headers)
    }

    fun getBytes(path: String, headers: Map<String, String> = emptyMap()): HttpResponse<ByteArray> {
        val request = requestBuilder(path, headers)
            .GET()
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
    }

    fun postJson(path: String, body: Any, headers: Map<String, String> = emptyMap()): HttpResponse<String> {
        val payload = E2eJson.mapper.writeValueAsString(body)
        val mergedHeaders = linkedMapOf("Content-Type" to "application/json").apply { putAll(headers) }
        val request = requestBuilder(path, mergedHeaders)
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun sendText(path: String, method: String, headers: Map<String, String>): HttpResponse<String> {
        val requestBuilder = requestBuilder(path, headers)
        if (method == "GET") {
            requestBuilder.GET()
        } else {
            error("Unsupported method: $method")
        }
        return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun requestBuilder(path: String, headers: Map<String, String>): HttpRequest.Builder {
        val builder = HttpRequest.newBuilder(uri(path))
        headers.forEach { (name, value) -> builder.header(name, value) }
        return builder
    }

    private fun uri(path: String): URI {
        return URI.create("$baseUrl$path")
    }

    companion object {
        fun urlEncode(value: String): String {
            return URLEncoder.encode(value, StandardCharsets.UTF_8)
        }
    }
}
