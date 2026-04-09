package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.protocol.ServerPlaybackSyncMessage
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.sql.SQLException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@Component
class PlaybackSyncEventPump(
    private val eventStore: PlaybackSyncEventStore,
    private val subscriber: PlaybackSyncEventSubscriber,
    private val objectMapper: ObjectMapper,
    private val messageSender: PlaybackSyncMessageSender,
) {
    private val logger = LoggerFactory.getLogger(PlaybackSyncEventPump::class.java)
    private val running = AtomicBoolean(false)
    private val lastSeenId = AtomicLong(0L)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "playback-sync-event-pump")
    }

    @PostConstruct
    fun start() {
        lastSeenId.set(eventStore.currentMaxId())
        running.set(true)
        executor.execute(::runLoop)
    }

    @PreDestroy
    fun stop() {
        running.set(false)
        executor.shutdownNow()
    }

    private fun runLoop() {
        while (running.get()) {
            try {
                subscriber.openConnection().use { listener ->
                    while (running.get()) {
                        listener.pgConnection.getNotifications(1_000)
                        drainEvents()
                    }
                }
            } catch (ex: SQLException) {
                logger.warn("Playback sync LISTEN loop failed, falling back to retry", ex)
            } catch (ex: Exception) {
                logger.warn("Playback sync event pump loop failed", ex)
            }

            runCatching { drainEvents() }
            if (running.get()) {
                try {
                    TimeUnit.SECONDS.sleep(1)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
            }
        }
    }

    private fun drainEvents() {
        while (running.get()) {
            val events = eventStore.listAfterId(lastSeenId.get(), EVENT_BATCH_SIZE)
            if (events.isEmpty()) {
                return
            }
            events.forEach { event ->
                val message = objectMapper.readValue(event.payload, ServerPlaybackSyncMessage::class.java)
                messageSender.deliverLocalBroadcast(event.accountId, message)
                lastSeenId.set(event.id)
            }
        }
    }

    private companion object {
        private const val EVENT_BATCH_SIZE = 128
    }
}
