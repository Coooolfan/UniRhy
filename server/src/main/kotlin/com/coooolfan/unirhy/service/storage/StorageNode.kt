package com.coooolfan.unirhy.service.storage

import com.coooolfan.unirhy.model.storage.FileProviderFileSystem
import com.coooolfan.unirhy.model.storage.FileProviderOss
import com.coooolfan.unirhy.model.storage.FileProviderType
import com.coooolfan.unirhy.service.isAudioObjectKey
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.io.File
import java.net.URI
import kotlin.io.path.Path

sealed interface StorageNode {
    val providerType: FileProviderType
    val providerId: Long
    val name: String
    val readonly: Boolean

    fun listAudioObjectKeys(): Sequence<String>
}

data class FileSystemStorageNode(val provider: FileProviderFileSystem) : StorageNode {
    override val providerType: FileProviderType = FileProviderType.FILE_SYSTEM
    override val providerId: Long = provider.id
    override val name: String = provider.name
    override val readonly: Boolean = provider.readonly

    override fun listAudioObjectKeys(): Sequence<String> = sequence {
        val rootDir = File(provider.parentPath)
        for (file in findAudioFilesRecursively(rootDir)) {
            yield(file.relativeTo(rootDir).path)
        }
    }

    fun file(objectKey: String): File {
        val rootPath = Path(provider.parentPath).toAbsolutePath().normalize()
        val targetPath = rootPath.resolve(objectKey).normalize()
        if (!targetPath.startsWith(rootPath)) {
            error("Invalid object key: $objectKey")
        }
        return targetPath.toFile()
    }
}

data class OssStorageNode(val provider: FileProviderOss) : StorageNode {
    override val providerType: FileProviderType = FileProviderType.OSS
    override val providerId: Long = provider.id
    override val name: String = provider.name
    override val readonly: Boolean = provider.readonly

    val prefix: String = provider.parentPath?.trim('/')?.takeIf { it.isNotBlank() }?.let { "$it/" }.orEmpty()

    override fun listAudioObjectKeys(): Sequence<String> = sequence {
        val client = client()
        var continuationToken: String? = null
        do {
            val response = client.listObjectsV2(
                ListObjectsV2Request.builder()
                    .bucket(provider.bucket)
                    .prefix(prefix)
                    .continuationToken(continuationToken)
                    .build()
            )
            for (item in response.contents()) {
                val key = objectKeyFromStorageKey(item.key()) ?: continue
                if (isAudioObjectKey(key)) {
                    yield(key)
                }
            }
            continuationToken = response.nextContinuationToken()
        } while (response.isTruncated == true)
    }

    fun storageKey(objectKey: String): String {
        val normalized = objectKey.trimStart('/')
        if (normalized.contains("..")) {
            error("Invalid object key: $objectKey")
        }
        return prefix + normalized
    }

    fun objectKeyFromStorageKey(storageKey: String): String? {
        if (!storageKey.startsWith(prefix)) {
            return null
        }
        return storageKey.removePrefix(prefix).takeIf { it.isNotBlank() && !it.endsWith("/") }
    }

    fun client(): S3Client = OssClientCache.client(provider)

    fun presigner(): S3Presigner = OssClientCache.presigner(provider)

    fun publicUrl(objectKey: String): String {
        return provider.host.trimEnd('/') + "/" + encodePath(provider.bucket) + "/" + encodePath(storageKey(objectKey))
    }
}
