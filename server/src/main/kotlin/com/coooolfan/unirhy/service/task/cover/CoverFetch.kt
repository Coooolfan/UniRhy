package com.coooolfan.unirhy.service.task.cover

import com.coooolfan.unirhy.model.MediaFile
import com.coooolfan.unirhy.model.storage.FileProviderFileSystem
import com.coooolfan.unirhy.model.storage.readonly
import com.coooolfan.unirhy.service.task.audio.ArtworkData
import com.coooolfan.unirhy.utils.sha256
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

private val COVER_EXTENSIONS = listOf("jpg", "jpeg", "png", "gif")

fun fetchCover(
    file: File,
    provider: FileProviderFileSystem,
    writeableProvider: FileProviderFileSystem,
    artwork: ArtworkData?
): MediaFile? {
    val sidecarFile = findSidecarCover(file, COVER_EXTENSIONS)
    if (sidecarFile != null) {
        return MediaFile {
            sha256 = sidecarFile.sha256()
            objectKey = sidecarFile.relativeTo(file.parentFile).path
            mimeType = Files.probeContentType(sidecarFile.toPath())
            size = sidecarFile.length()
            width = null
            height = null
            ossProvider = null
            fsProvider = provider
        }
    }

    val safeArtwork = artwork ?: return null
    val binaryData = safeArtwork.binaryData ?: return null
    if (binaryData.isEmpty()) {
        return null
    }

    val mimeType = safeArtwork.mimeType?.trim().takeIf { !it.isNullOrBlank() } ?: "image/jpeg"
    val extension = extensionFromMime(mimeType)
    val sha256 = sha256Bytes(binaryData)

    val targetProvider = if (provider.readonly) writeableProvider else provider
    val objectKey = "covers/$sha256.$extension"
    val coverFile = File(targetProvider.parentPath, objectKey)

    if (!coverFile.exists()) {
        coverFile.parentFile?.mkdirs()
        coverFile.writeBytes(binaryData)
    }

    return MediaFile {
        this.sha256 = sha256
        this.objectKey = objectKey
        this.mimeType = Files.probeContentType(coverFile.toPath()) ?: mimeType
        this.size = binaryData.size.toLong()
        this.width = safeArtwork.width
        this.height = safeArtwork.height
        this.ossProvider = null
        this.fsProvider = targetProvider
    }
}

private fun findSidecarCover(file: File, extensions: List<String>): File? {
    for (ext in extensions) {
        var coverFile = File(file.parentFile, "${file.nameWithoutExtension}.$ext")
        if (!coverFile.exists()) {
            coverFile = File(file.parentFile, "${file.nameWithoutExtension}.${ext.uppercase()}")
        }
        if (coverFile.exists()) {
            return coverFile
        }
    }
    return null
}

private fun extensionFromMime(mimeType: String): String =
    when (mimeType.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/bmp" -> "bmp"
        else -> "jpg"
    }

private fun sha256Bytes(binaryData: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(binaryData)
        .joinToString("") { "%02x".format(it) }
