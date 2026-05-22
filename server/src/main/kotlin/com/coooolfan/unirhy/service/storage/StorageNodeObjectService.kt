package com.coooolfan.unirhy.service.storage

import com.coooolfan.unirhy.model.storage.FileProviderFileSystem
import com.coooolfan.unirhy.model.storage.FileProviderOss
import com.coooolfan.unirhy.model.storage.FileProviderType
import com.coooolfan.unirhy.service.isAudioObjectKey
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.springframework.stereotype.Service
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.io.Closeable
import java.io.File
import java.net.URI
import java.time.Duration
import java.nio.file.Files
import java.time.Instant
import kotlin.io.path.Path

@Service
class StorageNodeObjectService(private val sql: KSqlClient) {

    fun resolve(providerType: FileProviderType, providerId: Long): StorageNode {
        return when (providerType) {
            FileProviderType.FILE_SYSTEM -> FileSystemStorageNode(sql.findOneById(FileProviderFileSystem::class, providerId))
            FileProviderType.OSS -> OssStorageNode(sql.findOneById(FileProviderOss::class, providerId))
        }
    }

    fun listAudioObjectKeys(node: StorageNode): Sequence<String> {
        return when (node) {
            is FileSystemStorageNode -> sequence {
                val rootDir = File(node.provider.parentPath)
                for (file in findAudioFilesRecursively(rootDir)) {
                    yield(file.relativeTo(rootDir).path)
                }
            }

            is OssStorageNode -> sequence {
                node.client().use { client ->
                    var continuationToken: String? = null
                    do {
                        val response = client.listObjectsV2(
                            ListObjectsV2Request.builder()
                                .bucket(node.provider.bucket)
                                .prefix(node.prefix)
                                .continuationToken(continuationToken)
                                .build()
                        )
                        for (item in response.contents()) {
                            val key = node.objectKeyFromStorageKey(item.key()) ?: continue
                            if (isAudioObjectKey(key)) {
                                yield(key)
                            }
                        }
                        continuationToken = response.nextContinuationToken()
                    } while (response.isTruncated == true)
                }
            }
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

            is OssStorageNode -> node.client().use { client ->
                val response = client.headObject(
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
            is OssStorageNode -> node.client().use { client ->
                client.getObjectAsBytes(
                    GetObjectRequest.builder()
                        .bucket(node.provider.bucket)
                        .key(node.storageKey(objectKey))
                        .build()
                ).asByteArray()
            }
        }
    }

    fun readRange(node: StorageNode, objectKey: String, start: Long, endInclusive: Long): ByteArray {
        return when (node) {
            is FileSystemStorageNode -> node.file(objectKey).inputStream().use { input ->
                input.skipNBytes(start)
                input.readNBytes((endInclusive - start + 1).toInt())
            }

            is OssStorageNode -> node.client().use { client ->
                client.getObjectAsBytes(
                    GetObjectRequest.builder()
                        .bucket(node.provider.bucket)
                        .key(node.storageKey(objectKey))
                        .range("bytes=$start-$endInclusive")
                        .build()
                ).asByteArray()
            }
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

            is OssStorageNode -> node.client().use { client ->
                client.putObject(
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

            is OssStorageNode -> node.client().use { client ->
                client.putObject(
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
    }

    fun directReadUrl(node: OssStorageNode, objectKey: String, ttlSeconds: Long): String {
        if (node.provider.secretKey.isBlank()) {
            return node.publicUrl(objectKey)
        }

        return node.presigner().use { presigner ->
            val objectRequest = GetObjectRequest.builder()
                .bucket(node.provider.bucket)
                .key(node.storageKey(objectKey))
                .build()
            val presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(ttlSeconds))
                .getObjectRequest(objectRequest)
                .build()
            presigner.presignGetObject(presignRequest).url().toString()
        }
    }
}

sealed interface StorageNode {
    val providerType: FileProviderType
    val providerId: Long
    val name: String
    val readonly: Boolean
}

data class FileSystemStorageNode(val provider: FileProviderFileSystem) : StorageNode {
    override val providerType: FileProviderType = FileProviderType.FILE_SYSTEM
    override val providerId: Long = provider.id
    override val name: String = provider.name
    override val readonly: Boolean = provider.readonly

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

    fun client(): S3Client {
        return S3Client.builder()
            .endpointOverride(URI.create(provider.host))
            .region(Region.AWS_GLOBAL)
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(provider.accessKey, provider.secretKey)
                )
            )
            .forcePathStyle(true)
            .build()
    }

    fun presigner(): S3Presigner {
        return S3Presigner.builder()
            .endpointOverride(URI.create(provider.host))
            .region(Region.AWS_GLOBAL)
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(provider.accessKey, provider.secretKey)
                )
            )
            .build()
    }

    fun publicUrl(objectKey: String): String {
        return provider.host.trimEnd('/') + "/" + encodePath(provider.bucket) + "/" + encodePath(storageKey(objectKey))
    }
}

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

private fun encodePath(path: String): String {
    return path.split('/').joinToString("/") { segment ->
        URI(null, null, segment, null).rawPath
    }
}
