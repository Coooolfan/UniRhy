package com.coooolfan.unirhy.service.plugin

import com.dylibso.chicory.runtime.HostFunction
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.wasm.types.FunctionType
import com.dylibso.chicory.wasm.types.ValType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private val logger: Logger = LoggerFactory.getLogger("PluginHostFunctions")

private val sharedHttpClient: HttpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(5))
    .followRedirects(HttpClient.Redirect.NEVER)
    .build()

fun buildDefaultHostFunctions(manifest: PluginManifest, instanceRef: () -> Instance): List<HostFunction> {
    val allowedHosts = manifest.networkAllowHosts()

    val hostLog = HostFunction(
        "env",
        "host_log",
        FunctionType.of(listOf(ValType.I32, ValType.I32, ValType.I32), emptyList()),
    ) { _: Instance, args: LongArray ->
        val level = args[0].toInt()
        val ptr = args[1].toInt()
        val len = args[2].toInt()
        val message = instanceRef().memory().readString(ptr, len)
        when (level) {
            0 -> logger.debug("[wasm] {}", message)
            1 -> logger.info("[wasm] {}", message)
            2 -> logger.warn("[wasm] {}", message)
            else -> logger.error("[wasm] {}", message)
        }
        longArrayOf()
    }

    val hostHttpCheck = HostFunction(
        "env",
        "host_http_check",
        FunctionType.of(listOf(ValType.I32, ValType.I32), listOf(ValType.I32)),
    ) { _: Instance, args: LongArray ->
        val ptr = args[0].toInt()
        val len = args[1].toInt()
        val url = instanceRef().memory().readString(ptr, len)
        val status = checkUrl(url, allowedHosts)
        longArrayOf(status.toLong())
    }

    return listOf(hostLog, hostHttpCheck)
}

private fun checkUrl(url: String, allowedHosts: Set<String>): Int {
    val uri = try {
        URI.create(url)
    } catch (_: Exception) {
        return -1
    }
    val host = uri.host ?: return -1
    if (host !in allowedHosts) {
        logger.warn("Plugin network access denied: host={} not in allow list", host)
        return -2
    }
    return try {
        val request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build()
        val response = sharedHttpClient.send(request, HttpResponse.BodyHandlers.discarding())
        val status = response.statusCode()
        logger.info("Plugin network check: url={}, status={}", url, status)
        status
    } catch (ex: Exception) {
        logger.warn("Plugin network request failed: url={}, error={}", url, ex.message)
        -1
    }
}
