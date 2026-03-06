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
            .setAllowedOriginPatterns("*")
    }

    companion object {
        const val PLAYBACK_SYNC_PATH = "/ws/playback-sync"
    }
}
