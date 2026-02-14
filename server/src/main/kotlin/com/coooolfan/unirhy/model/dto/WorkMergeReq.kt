package com.coooolfan.unirhy.model.dto

data class WorkMergeReq(
    val targetId: Long,
    val needMergeIds: Set<Long>
)
