package com.coooolfan.unirhy.sync.model

data class CurrentQueueEntry(
    val entryId: Long,
    val recordingId: Long,
    val workId: Long,
    val title: String,
    val artistLabel: String,
    val coverMediaFileId: Long?,
    val durationMs: Long,
)
