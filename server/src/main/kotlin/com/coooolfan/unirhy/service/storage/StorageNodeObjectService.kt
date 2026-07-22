package com.coooolfan.unirhy.service.storage

import com.coooolfan.unirhy.model.storage.FileProviderFileSystem
import com.coooolfan.unirhy.model.storage.FileProviderOss
import com.coooolfan.unirhy.model.storage.FileProviderType
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.CopyObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import tools.jackson.databind.ObjectMapper
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Service
class StorageNodeObjectService(
    private val sql: KSqlClient,
    private val objectMapper: ObjectMapper,
    @Value("\${unirhy.media.signing-key:}") configuredCursorKey: String = "",
) {
    private val logger = LoggerFactory.getLogger(StorageNodeObjectService::class.java)
    private val secureRandom = SecureRandom()
    private val cursorKey = if (configuredCursorKey.isBlank()) {
        ByteArray(CURSOR_KEY_BYTES).also {
            secureRandom.nextBytes(it)
            logger.warn("Storage cursor key is not configured; cursors will not survive a server restart")
        }
    } else {
        MessageDigest.getInstance("SHA-256").digest(configuredCursorKey.toByteArray(Charsets.UTF_8))
    }

    private data class ObjectCursor(
        val nodeType: String,
        val nodeId: Long,
        val prefix: String,
        val position: String,
    )

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
                    contentType = Files.probeContentType(file.toPath()),
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
                    contentType = response.contentType(),
                )
            }
        }
    }

    fun statOrNull(node: StorageNode, objectKey: String): StorageObjectStat? = try {
        stat(node, objectKey)
    } catch (ex: S3Exception) {
        if (ex.statusCode() == 404) null else throw ex
    } catch (_: java.io.FileNotFoundException) {
        null
    } catch (ex: IllegalStateException) {
        if (ex.message?.startsWith("Object not found:") == true) null else throw ex
    }

    fun list(
        node: StorageNode,
        prefix: String,
        pageSize: Int,
        cursor: String?,
    ): StorageObjectPage {
        require(pageSize > 0) { "pageSize must be positive" }
        val normalizedPrefix = normalizePrefix(prefix)
        val position = cursor?.let { decodeCursor(it, node, normalizedPrefix) }
        return when (node) {
            is FileSystemStorageNode -> listFileSystem(node, normalizedPrefix, pageSize, position)
            is OssStorageNode -> listOss(node, normalizedPrefix, pageSize, position)
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

    fun delete(node: StorageNode, objectKey: String): Boolean {
        if (node.readonly) error("Storage provider is readonly")
        return when (node) {
            is FileSystemStorageNode -> {
                val path = node.file(objectKey).toPath()
                Files.isRegularFile(path) && Files.deleteIfExists(path)
            }
            is OssStorageNode -> {
                val existed = statOrNull(node, objectKey) != null
                if (existed) {
                    node.client().deleteObject(
                        DeleteObjectRequest.builder()
                            .bucket(node.provider.bucket)
                            .key(node.storageKey(objectKey))
                            .build(),
                    )
                }
                existed
            }
        }
    }

    /**
     * Commits a completed local download through a temporary object key. The target is never
     * exposed until the full response has been received locally and the storage write succeeds.
     */
    fun commitDownloadedFile(
        node: StorageNode,
        objectKey: String,
        file: File,
        contentType: String,
        overwrite: Boolean,
    ) {
        if (node.readonly) error("Storage provider is readonly")
        when (node) {
            is FileSystemStorageNode -> commitFileSystem(node, objectKey, file, overwrite)
            is OssStorageNode -> commitOss(node, objectKey, file, contentType, overwrite)
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

    private fun listFileSystem(
        node: FileSystemStorageNode,
        prefix: String,
        pageSize: Int,
        lastObjectKey: String?,
    ): StorageObjectPage {
        val root = node.file("").toPath()
        if (!Files.exists(root)) return StorageObjectPage(emptyList(), null)
        val keys = Files.walk(root).use { paths ->
            paths.filter { Files.isRegularFile(it) }
                .map { root.relativize(it).toString().replace(File.separatorChar, '/') }
                .filter { it.startsWith(prefix) }
                .filter { lastObjectKey == null || it > lastObjectKey }
                .sorted()
                .limit(pageSize.toLong() + 1)
                .toList()
        }
        val hasMore = keys.size > pageSize
        val pageKeys = keys.take(pageSize)
        val objects = pageKeys.map { key ->
            StorageObjectListItem(objectKey = key, size = node.file(key).length())
        }
        val next = if (hasMore && pageKeys.isNotEmpty()) {
            encodeCursor(node, prefix, pageKeys.last())
        } else {
            null
        }
        return StorageObjectPage(objects, next)
    }

    private fun listOss(
        node: OssStorageNode,
        prefix: String,
        pageSize: Int,
        continuationToken: String?,
    ): StorageObjectPage {
        val response = node.client().listObjectsV2(
            ListObjectsV2Request.builder()
                .bucket(node.provider.bucket)
                .prefix(node.storageKey(prefix))
                .continuationToken(continuationToken)
                .maxKeys(pageSize)
                .build(),
        )
        val objects = response.contents().mapNotNull { item ->
            node.objectKeyFromStorageKey(item.key())?.let { key ->
                StorageObjectListItem(objectKey = key, size = item.size())
            }
        }.sortedBy { it.objectKey }
        val next = response.nextContinuationToken()?.let { encodeCursor(node, prefix, it) }
        return StorageObjectPage(objects, next)
    }

    private fun encodeCursor(node: StorageNode, prefix: String, position: String): String {
        val payload = ObjectCursor(node.providerType.name, node.providerId, prefix, position)
        val iv = ByteArray(CURSOR_IV_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(CURSOR_CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(cursorKey, "AES"), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(objectMapper.writeValueAsBytes(payload))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(iv + encrypted)
    }

    private fun decodeCursor(cursor: String, node: StorageNode, prefix: String): String {
        val payload = try {
            val encoded = Base64.getUrlDecoder().decode(cursor)
            require(encoded.size > CURSOR_IV_BYTES) { "Invalid storage cursor" }
            val cipher = Cipher.getInstance(CURSOR_CIPHER)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(cursorKey, "AES"),
                GCMParameterSpec(128, encoded.copyOfRange(0, CURSOR_IV_BYTES)),
            )
            val decrypted = cipher.doFinal(encoded.copyOfRange(CURSOR_IV_BYTES, encoded.size))
            objectMapper.readValue(decrypted, ObjectCursor::class.java)
        } catch (ex: Exception) {
            throw IllegalArgumentException("Invalid storage cursor", ex)
        }
        require(payload.nodeType == node.providerType.name && payload.nodeId == node.providerId && payload.prefix == prefix) {
            "Storage cursor does not match the requested node and prefix"
        }
        return payload.position
    }

    private fun normalizePrefix(prefix: String): String {
        val normalized = prefix.trimStart('/').replace('\\', '/')
        require(!normalized.split('/').any { it == ".." }) { "Invalid object prefix" }
        return normalized
    }

    private fun commitFileSystem(
        node: FileSystemStorageNode,
        objectKey: String,
        source: File,
        overwrite: Boolean,
    ) {
        val target = node.file(objectKey).toPath()
        target.parent?.let(Files::createDirectories)
        if (!overwrite && Files.exists(target)) {
            throw java.nio.file.FileAlreadyExistsException(objectKey)
        }
        val temporary = target.resolveSibling("${target.fileName}.unirhy-part-${UUID.randomUUID()}")
        try {
            Files.copy(source.toPath(), temporary, REPLACE_EXISTING)
            if (overwrite) {
                try {
                    Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
                } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                    Files.move(temporary, target, REPLACE_EXISTING)
                }
            } else {
                try {
                    Files.createLink(target, temporary)
                    Files.deleteIfExists(temporary)
                } catch (_: UnsupportedOperationException) {
                    Files.move(temporary, target)
                } catch (ex: IOException) {
                    if (ex is java.nio.file.FileAlreadyExistsException) throw ex
                    Files.move(temporary, target)
                }
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun commitOss(
        node: OssStorageNode,
        objectKey: String,
        source: File,
        contentType: String,
        overwrite: Boolean,
    ) {
        if (!overwrite && statOrNull(node, objectKey) != null) {
            throw java.nio.file.FileAlreadyExistsException(objectKey)
        }
        val temporaryObjectKey = "$objectKey.unirhy-part-${UUID.randomUUID()}"
        val client = node.client()
        try {
            client.putObject(
                PutObjectRequest.builder()
                    .bucket(node.provider.bucket)
                    .key(node.storageKey(temporaryObjectKey))
                    .contentType(contentType)
                    .contentLength(source.length())
                    .build(),
                RequestBody.fromFile(source),
            )
            val copyRequest = CopyObjectRequest.builder()
                .sourceBucket(node.provider.bucket)
                .sourceKey(node.storageKey(temporaryObjectKey))
                .destinationBucket(node.provider.bucket)
                .destinationKey(node.storageKey(objectKey))
                .contentType(contentType)
                .metadataDirective("REPLACE")
                .apply { if (!overwrite) ifNoneMatch("*") }
                .build()
            try {
                client.copyObject(copyRequest)
            } catch (ex: S3Exception) {
                if (!overwrite && ex.statusCode() == 412) {
                    throw java.nio.file.FileAlreadyExistsException(objectKey, null, ex.message)
                }
                throw ex
            }
        } finally {
            runCatching {
                client.deleteObject(
                    DeleteObjectRequest.builder()
                        .bucket(node.provider.bucket)
                        .key(node.storageKey(temporaryObjectKey))
                        .build(),
                )
            }
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

    companion object {
        private const val CURSOR_CIPHER = "AES/GCM/NoPadding"
        private const val CURSOR_KEY_BYTES = 32
        private const val CURSOR_IV_BYTES = 12
    }
}
