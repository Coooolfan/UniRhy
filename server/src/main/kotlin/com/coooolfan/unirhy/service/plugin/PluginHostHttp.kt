package com.coooolfan.unirhy.service.plugin

import run.endive.runtime.HostFunction
import run.endive.runtime.Instance
import run.endive.wasm.types.FunctionType
import run.endive.wasm.types.ValType
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

/**
 * 所有已启用插件获得的默认 Host imports。
 *
 * 网络 Host API 只校验 URL 结构与自身支持的协议，不检查插件级 allowlist；
 * 部署者信任其安装插件发起的网络访问。
 */
fun buildDefaultHostFunctions(instanceRef: () -> Instance): List<HostFunction> {

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
        val status = checkUrl(url)
        longArrayOf(status.toLong())
    }

    return listOf(hostLog, hostHttpCheck)
}

private fun checkUrl(url: String): Int {
    val uri = try {
        URI.create(url)
    } catch (_: Exception) {
        return -1
    }
    if (uri.host == null || uri.scheme !in setOf("http", "https")) {
        return -1
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
