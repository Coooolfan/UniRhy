package com.coooolfan.unirhy.model.dto

data class ArtistMergeReq(
    val targetId: Long,
    val needMergeIds: Set<Long>,
)
