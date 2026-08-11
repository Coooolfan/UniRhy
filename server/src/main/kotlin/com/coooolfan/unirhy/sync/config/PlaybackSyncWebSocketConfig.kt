package com.coooolfan.unirhy.sync.config

import com.coooolfan.unirhy.sync.ws.PlaybackSyncHandshakeInterceptor
import com.coooolfan.unirhy.sync.ws.PlaybackSyncWebSocketHandler
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class PlaybackSyncWebSocketConfig(
    private val playbackSyncWebSocketHandler: PlaybackSyncWebSocketHandler,
    private val playbackSyncHandshakeInterceptor: PlaybackSyncHandshakeInterceptor,
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(playbackSyncWebSocketHandler, PLAYBACK_SYNC_PATH)
            .addInterceptors(playbackSyncHandshakeInterceptor)
            // Origin 校验在 PlaybackSyncHandshakeInterceptor 中实施（白名单 + 同主机名）；
            // 此处不能用 Spring 默认的同源校验，它严格比较协议与端口，会在 TLS 终止代理后误拒。
            .setAllowedOriginPatterns("*")
    }

    companion object {
        const val PLAYBACK_SYNC_PATH = "/ws/playback-sync"
    }
}
