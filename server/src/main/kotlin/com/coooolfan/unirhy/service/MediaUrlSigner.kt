package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.controller.MediaFileRoutes
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class MediaUrlSigner(
    @Value("\${unirhy.media.signing-key:}")
    private val configuredKey: String,

    @Value("\${unirhy.media.url-ttl-seconds:7200}")
    private val ttlSeconds: Long,
) {
    private val logger = LoggerFactory.getLogger(MediaUrlSigner::class.java)

    private val secretKey: ByteArray = resolveKey(configuredKey)

    private fun resolveKey(key: String): ByteArray {
        if (key.isNotBlank()) {
            return key.toByteArray(Charsets.UTF_8)
        }
        logger.warn(
            "UNIRHY_MEDIA_SIGNING_KEY is not configured. " +
                "A random key has been generated — presigned URLs will not survive restarts."
        )
        val random = ByteArray(32)
        SecureRandom().nextBytes(random)
        return random
    }

    fun sign(id: Long, expiresAt: Long): String {
        val message = "GET\n${MediaFileRoutes.mediaFilePath(id)}\n$expiresAt"
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(secretKey, ALGORITHM))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8)).toHexString()
    }

    fun verify(id: Long, sig: String, expiresAt: Long): Boolean {
        if (System.currentTimeMillis() / 1000 > expiresAt) {
            return false
        }
        val expected = sign(id, expiresAt)
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            sig.toByteArray(Charsets.UTF_8),
        )
    }

    fun generatePresignedPath(id: Long): String {
        val expiresAt = System.currentTimeMillis() / 1000 + ttlSeconds
        val sig = sign(id, expiresAt)
        return "${MediaFileRoutes.mediaFilePath(id)}?_sig=$sig&_exp=$expiresAt"
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }

    companion object {
        private const val ALGORITHM = "HmacSHA256"
    }
}
