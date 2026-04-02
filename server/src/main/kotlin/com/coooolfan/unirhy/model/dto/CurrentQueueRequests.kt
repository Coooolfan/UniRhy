package com.coooolfan.unirhy.model.dto

data class CurrentQueueReplaceRequest(
    val recordingIds: List<Long>,
    val currentIndex: Int,
)

data class CurrentQueueAppendRequest(
    val recordingIds: List<Long>,
)

data class CurrentQueueReorderRequest(
    val entryIds: List<Long>,
)

data class CurrentQueueSetCurrentRequest(
    val entryId: Long,
)
