package com.coooolfan.unirhy.sync.protocol

data class CurrentQueueItemDto(
    val entryId: Long,
    val recordingId: Long,
    val mediaFileId: Long,
    val title: String,
    val artistLabel: String,
    val coverUrl: String? = null,
    val durationMs: Long,
)

data class CurrentQueueDto(
    val items: List<CurrentQueueItemDto>,
    val currentEntryId: Long? = null,
    val version: Long,
    val updatedAtMs: Long,
)

data class QueueChangePayload(
    val queue: CurrentQueueDto,
)
