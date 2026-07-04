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
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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

    fun openStream(node: StorageNode, objectKey: String): InputStream {
        return when (node) {
            is FileSystemStorageNode -> node.file(objectKey).inputStream()
            is OssStorageNode -> node.client().getObject(
                GetObjectRequest.builder()
                    .bucket(node.provider.bucket)
                    .key(node.storageKey(objectKey))
                    .build()
            )
        }
    }

    fun openStream(node: StorageNode, objectKey: String, start: Long, endInclusive: Long): InputStream {
        return when (node) {
            is FileSystemStorageNode -> {
                val input = node.file(objectKey).inputStream()
                try {
                    input.skipNBytes(start)
                } catch (ex: Exception) {
                    input.close()
                    throw ex
                }
                BoundedInputStream(input, endInclusive - start + 1)
            }

            is OssStorageNode -> node.client().getObject(
                GetObjectRequest.builder()
                    .bucket(node.provider.bucket)
                    .key(node.storageKey(objectKey))
                    .range("bytes=$start-$endInclusive")
                    .build()
            )
        }
    }

    fun materializeTempFile(node: StorageNode, objectKey: String): TemporaryStorageFile {
        val suffix = objectKey.substringAfterLast('/', objectKey).substringAfterLast('.', "")
            .takeIf { it.isNotBlank() }
            ?.let { ".$it" }
            ?: ".bin"
        val tempFile = Files.createTempFile("unirhy-storage-", suffix)
        try {
            openStream(node, objectKey).use { input ->
                Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (ex: Exception) {
            Files.deleteIfExists(tempFile)
            throw ex
        }
        return TemporaryStorageFile(tempFile.toFile())
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

    /** 将底层流限制为最多读取 [remaining] 字节，供文件系统的区间读取使用 */
    private class BoundedInputStream(
        private val delegate: InputStream,
        private var remaining: Long,
    ) : InputStream() {
        override fun read(): Int {
            if (remaining <= 0) {
                return -1
            }
            val byte = delegate.read()
            if (byte >= 0) {
                remaining -= 1
            }
            return byte
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) {
                return -1
            }
            val bytesRead = delegate.read(b, off, minOf(len.toLong(), remaining).toInt())
            if (bytesRead > 0) {
                remaining -= bytesRead
            }
            return bytesRead
        }

        override fun available(): Int = minOf(delegate.available().toLong(), remaining).toInt()

        override fun close() = delegate.close()
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
