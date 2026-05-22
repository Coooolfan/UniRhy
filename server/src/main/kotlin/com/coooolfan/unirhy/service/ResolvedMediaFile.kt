package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.model.MediaFile
import java.time.Instant

data class ResolvedMediaFile(
    val mediaFile: MediaFile,
    val fileName: String,
    val size: Long,
    val lastModified: Instant,
    val readAll: () -> ByteArray,
    val readRange: (start: Long, endInclusive: Long) -> ByteArray,
)
