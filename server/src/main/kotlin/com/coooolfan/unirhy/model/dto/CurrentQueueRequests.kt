package com.coooolfan.unirhy.model.dto

import com.coooolfan.unirhy.sync.protocol.PlaybackStrategy
import com.coooolfan.unirhy.sync.protocol.StopStrategy

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

data class CurrentQueueStrategyUpdateRequest(
    val playbackStrategy: PlaybackStrategy,
    val stopStrategy: StopStrategy,
)
