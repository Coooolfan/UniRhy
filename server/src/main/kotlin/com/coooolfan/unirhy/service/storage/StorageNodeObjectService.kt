package com.coooolfan.unirhy.service.storage

import com.coooolfan.unirhy.model.storage.FileProviderFileSystem
import com.coooolfan.unirhy.model.storage.FileProviderOss
import com.coooolfan.unirhy.model.storage.FileProviderType
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.Instant

@Service
class StorageNodeObjectService(private val sql: KSqlClient) {

    fun resolve(providerType: FileProviderType, providerId: Long): StorageNode {
        return when (providerType) {
            FileProviderType.FILE_SYSTEM -> FileSystemStorageNode(sql.findOneById(FileProviderFileSystem::class, providerId))
            FileProviderType.OSS -> OssStorageNode(sql.findOneById(FileProviderOss::class, providerId))
        }
    }

    fun stat(node: StorageNode, objectKey: String): StorageObjectStat {
        return when (node) {
            is FileSystemStorageNode -> {
                val file = node.file(objectKey)
                if (!file.exists() || !file.isFile) {
                    error("Object not found: $objectKey")
                }
                StorageObjectStat(
                    objectKey = objectKey,
                    fileName = file.name,
                    size = file.length(),
                    lastModified = Instant.ofEpochMilli(file.lastModified()),
                )
            }

            is OssStorageNode -> {
                val response = node.client().headObject(
                    HeadObjectRequest.builder()
                        .bucket(node.provider.bucket)
                        .key(node.storageKey(objectKey))
                        .build()
                )
                StorageObjectStat(
                    objectKey = objectKey,
                    fileName = objectKey.substringAfterLast('/'),
                    size = response.contentLength(),
                    lastModified = response.lastModified(),
                )
            }
        }
    }

    fun readAll(node: StorageNode, objectKey: String): ByteArray {
        return when (node) {
            is FileSystemStorageNode -> node.file(objectKey).readBytes()
            is OssStorageNode -> node.client().getObjectAsBytes(
                GetObjectRequest.builder()
                    .bucket(node.provider.bucket)
                    .key(node.storageKey(objectKey))
                    .build()
            ).asByteArray()
        }
    }

    fun readRange(node: StorageNode, objectKey: String, start: Long, endInclusive: Long): ByteArray {
        return when (node) {
            is FileSystemStorageNode -> node.file(objectKey).inputStream().use { input ->
                input.skipNBytes(start)
                input.readNBytes((endInclusive - start + 1).toInt())
            }

            is OssStorageNode -> node.client().getObjectAsBytes(
                GetObjectRequest.builder()
                    .bucket(node.provider.bucket)
                    .key(node.storageKey(objectKey))
                    .range("bytes=$start-$endInclusive")
                    .build()
            ).asByteArray()
        }
    }

    fun materializeTempFile(node: StorageNode, objectKey: String): TemporaryStorageFile {
        val suffix = objectKey.substringAfterLast('/', objectKey).substringAfterLast('.', "")
            .takeIf { it.isNotBlank() }
            ?.let { ".$it" }
            ?: ".bin"
        val tempFile = Files.createTempFile("unirhy-storage-", suffix).toFile()
        tempFile.writeBytes(readAll(node, objectKey))
        return TemporaryStorageFile(tempFile)
    }

    fun write(node: StorageNode, objectKey: String, bytes: ByteArray, contentType: String) {
        if (node.readonly) {
            error("Storage provider is readonly")
        }
        when (node) {
            is FileSystemStorageNode -> {
                val file = node.file(objectKey)
                file.parentFile?.mkdirs()
                file.writeBytes(bytes)
            }

            is OssStorageNode -> node.client().putObject(
                PutObjectRequest.builder()
                    .bucket(node.provider.bucket)
                    .key(node.storageKey(objectKey))
                    .contentType(contentType)
                    .contentLength(bytes.size.toLong())
                    .build(),
                RequestBody.fromBytes(bytes),
            )
        }
    }

    fun writeFile(node: StorageNode, objectKey: String, file: File, contentType: String) {
        if (node.readonly) {
            error("Storage provider is readonly")
        }
        when (node) {
            is FileSystemStorageNode -> {
                val target = node.file(objectKey)
                target.parentFile?.mkdirs()
                file.copyTo(target, overwrite = false)
            }

            is OssStorageNode -> node.client().putObject(
                PutObjectRequest.builder()
                    .bucket(node.provider.bucket)
                    .key(node.storageKey(objectKey))
                    .contentType(contentType)
                    .contentLength(file.length())
                    .build(),
                RequestBody.fromFile(file),
            )
        }
    }

    fun directReadUrl(node: OssStorageNode, objectKey: String, ttlSeconds: Long): String {
        if (node.provider.secretKey.isBlank()) {
            return node.publicUrl(objectKey)
        }

        val objectRequest = GetObjectRequest.builder()
            .bucket(node.provider.bucket)
            .key(node.storageKey(objectKey))
            .build()
        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(ttlSeconds))
            .getObjectRequest(objectRequest)
            .build()
        return node.presigner().presignGetObject(presignRequest).url().toString()
    }
}
