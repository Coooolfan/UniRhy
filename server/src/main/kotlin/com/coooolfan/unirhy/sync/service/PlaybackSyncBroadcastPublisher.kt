package com.coooolfan.unirhy.sync.service

import org.springframework.stereotype.Service

interface PlaybackSyncBroadcastPublisher {
    fun publish(
        accountId: Long,
        eventType: PlaybackSyncEventType,
        dedupeKey: String,
        payload: String,
    )
}

@Service
class OutboxPlaybackSyncBroadcastPublisher(
    private val eventStore: PlaybackSyncEventStore,
    private val notifier: PlaybackSyncEventNotifier,
    private val timeProvider: PlaybackSyncTimeProvider,
) : PlaybackSyncBroadcastPublisher {
    override fun publish(
        accountId: Long,
        eventType: PlaybackSyncEventType,
        dedupeKey: String,
        payload: String,
    ) {
        val eventId = eventStore.append(
            accountId = accountId,
            eventType = eventType,
            dedupeKey = dedupeKey,
            payload = payload,
            createdAtMs = timeProvider.nowMs(),
        ) ?: return
        notifier.notifyEvent(eventId)
    }
}
