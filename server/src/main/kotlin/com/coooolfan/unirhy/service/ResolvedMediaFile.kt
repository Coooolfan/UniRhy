package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.model.MediaFile
import java.io.InputStream
import java.time.Instant

data class ResolvedMediaFile(
    val mediaFile: MediaFile,
    val fileName: String,
    val size: Long,
    val lastModified: Instant,
    val openStream: (start: Long, endInclusive: Long) -> InputStream,
)
