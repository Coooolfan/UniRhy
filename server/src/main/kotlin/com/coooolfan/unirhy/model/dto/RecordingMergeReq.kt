package com.coooolfan.unirhy.model.dto

data class RecordingMergeReq(
    val targetId: Long,
    val needMergeIds: Set<Long>
)
