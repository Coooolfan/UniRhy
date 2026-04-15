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
    val recordingId: Long,
    val title: String,
    val artistLabel: String,
    val coverUrl: String? = null,
    val durationMs: Long,
)

data class CurrentQueueDto(
    val items: List<CurrentQueueItemDto>,
    val recordingIds: List<Long>,
    val currentIndex: Int,
    val playbackStrategy: PlaybackStrategy,
    val stopStrategy: StopStrategy,
    val playbackStatus: PlaybackStatus,
    val positionMs: Long,
    val serverTimeToExecuteMs: Long,
    val version: Long,
    val updatedAtMs: Long,
)

data class QueueChangePayload(
    val queue: CurrentQueueDto,
)
