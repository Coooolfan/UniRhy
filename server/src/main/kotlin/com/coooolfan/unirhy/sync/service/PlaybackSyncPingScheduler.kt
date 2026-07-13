package com.coooolfan.unirhy.sync.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.PingMessage
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import java.util.concurrent.ScheduledFuture

/**
 * 周期性向所有已完成 HELLO 的连接发送 WebSocket 协议层 Ping。
 *
 * Pong 由客户端网络栈自动回复，不依赖页面 JS 运行，
 * 因此可作为与 NTP 校时解耦的连接活性信号。
 */
@Component
class PlaybackSyncPingScheduler(
    private val deviceRuntimeService: DeviceRuntimeService,
    private val playbackSchedulerService: PlaybackSchedulerService,
) {
    private val logger = LoggerFactory.getLogger(PlaybackSyncPingScheduler::class.java)
    private var future: ScheduledFuture<*>? = null

    @PostConstruct
    fun start() {
        future = playbackSchedulerService.scheduleWithFixedDelay(
            initialDelayMs = PlaybackSchedulerService.PING_INTERVAL_MS,
            delayMs = PlaybackSchedulerService.PING_INTERVAL_MS,
            task = ::pingAllConnections,
        )
    }

    @PreDestroy
    fun stop() {
        future?.cancel(false)
    }

    fun pingAllConnections() {
        runCatching {
            deviceRuntimeService.listAllHelloCompletedConnections().forEach { context ->
                // 发送失败说明连接已不可用，交由 close 回调或 Pong 超时清理
                runCatching { context.session.sendMessage(PING_MESSAGE) }
            }
        }.onFailure { ex ->
            logger.warn("Failed to ping playback sync connections", ex)
        }
    }

    private companion object {
        private val PING_MESSAGE = PingMessage()
    }
}
