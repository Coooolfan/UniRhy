package com.coooolfan.unirhy.service.storage

import com.coooolfan.unirhy.model.storage.FileProviderOss
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

private data class OssClientCacheKey(
    val providerId: Long,
    val host: String,
    val accessKey: String,
    val secretKey: String,
)

internal object OssClientCache {
    private val clients = ConcurrentHashMap<OssClientCacheKey, S3Client>()
    private val presigners = ConcurrentHashMap<OssClientCacheKey, S3Presigner>()

    fun client(provider: FileProviderOss): S3Client {
        return clients.compute(provider.cacheKey()) { _, existing ->
            existing ?: S3Client.builder()
                .endpointOverride(URI.create(provider.host))
                .region(Region.AWS_GLOBAL)
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(provider.accessKey, provider.secretKey)
                    )
                )
                .forcePathStyle(true)
                .build()
        }!!
    }

    fun presigner(provider: FileProviderOss): S3Presigner {
        return presigners.compute(provider.cacheKey()) { _, existing ->
            existing ?: S3Presigner.builder()
                .endpointOverride(URI.create(provider.host))
                .region(Region.AWS_GLOBAL)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(provider.accessKey, provider.secretKey)
                    )
                )
                .build()
        }!!
    }

    private fun FileProviderOss.cacheKey() = OssClientCacheKey(id, host, accessKey, secretKey)
}
