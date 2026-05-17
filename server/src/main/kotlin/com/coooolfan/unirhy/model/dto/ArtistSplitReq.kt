package com.coooolfan.unirhy.model.dto

data class ArtistSplitReq(
    val sourceArtistId: Long,
    val artists: List<ArtistSplitCreate>,
)

data class ArtistSplitCreate(
    val displayName: String,
    val alias: List<String> = emptyList(),
    val comment: String = "",
)
