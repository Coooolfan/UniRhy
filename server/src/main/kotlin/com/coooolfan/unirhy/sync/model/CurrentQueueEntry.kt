package com.coooolfan.unirhy.sync.model

data class CurrentQueueEntry(
    val recordingId: Long,
    val workId: Long,
    val title: String,
    val artistLabel: String,
    val coverMediaFileId: Long?,
    val durationMs: Long,
)
