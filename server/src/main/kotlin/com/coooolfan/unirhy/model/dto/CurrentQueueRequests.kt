package com.coooolfan.unirhy.model.dto

import com.coooolfan.unirhy.sync.protocol.PlaybackStrategy
import com.coooolfan.unirhy.sync.protocol.StopStrategy

data class CurrentQueueReplaceRequest(
    val recordingIds: List<Long>,
    val currentIndex: Int,
    val version: Long,
)

data class CurrentQueueAppendRequest(
    val recordingIds: List<Long>,
    val version: Long,
)

data class CurrentQueueReorderRequest(
    val recordingIds: List<Long>,
    val currentIndex: Int,
    val version: Long,
)

data class CurrentQueueSetCurrentRequest(
    val currentIndex: Int,
    val version: Long,
)

data class CurrentQueueStrategyUpdateRequest(
    val playbackStrategy: PlaybackStrategy,
    val stopStrategy: StopStrategy,
    val version: Long,
)

data class CurrentQueueVersionRequest(
    val version: Long,
)

data class CurrentQueueRemoveRequest(
    val index: Int,
    val version: Long,
)
