package com.coooolfan.unirhy.sync.protocol

enum class PlaybackStrategy {
    SEQUENTIAL,
    SHUFFLE,
    RADIO,
}

enum class StopStrategy {
    TRACK,
    LIST,
}

data class CurrentQueueItemDto(
    val entryId: Long,
    val recordingId: Long,
    val title: String,
    val artistLabel: String,
    val coverUrl: String? = null,
    val durationMs: Long,
)

data class CurrentQueueDto(
    val items: List<CurrentQueueItemDto>,
    val currentEntryId: Long? = null,
    val playbackStrategy: PlaybackStrategy,
    val stopStrategy: StopStrategy,
    val version: Long,
    val updatedAtMs: Long,
)

data class QueueChangePayload(
    val queue: CurrentQueueDto,
)
