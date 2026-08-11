package com.coooolfan.unirhy.config

/**
 * `unirhy.cors.allowed-origins` 的统一定义，供 HTTP CORS 与 WebSocket 握手校验共用，
 * 保证两处的属性默认值与解析规则一致。
 */
object AllowedOrigins {
    const val VALUE_EXPRESSION =
        "\${unirhy.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}"

    fun parse(raw: String): Set<String> {
        return raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}
