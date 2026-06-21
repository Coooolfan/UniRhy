package com.coooolfan.unirhy.service.storage

import com.coooolfan.unirhy.service.isAudioObjectKey
import java.io.Closeable
import java.io.File
import java.net.URI
import java.time.Instant

data class StorageObjectStat(
    val objectKey: String,
    val fileName: String,
    val size: Long,
    val lastModified: Instant,
)

data class TemporaryStorageFile(val file: File) : Closeable {
    override fun close() {
        if (!file.delete() && file.exists()) {
            file.deleteOnExit()
        }
    }
}

fun findAudioFilesRecursively(rootDir: File): Sequence<File> {
    if (!rootDir.exists() || !rootDir.isDirectory) {
        return emptySequence()
    }
    return rootDir.walkTopDown()
        .filter { it.isFile && isAudioObjectKey(it.name) }
}

internal fun encodePath(path: String): String {
    return path.split('/').joinToString("/") { segment ->
        URI(null, null, segment, null).rawPath
    }
}
