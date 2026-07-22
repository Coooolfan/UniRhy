package com.coooolfan.unirhy.service.plugin.hostapi

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
internal class PluginDataCipher(
    @Value("\${unirhy.plugin-data.encryption-key:}") secret: String,
) {
    private val key = SecretKeySpec(deriveKey(secret), "AES")
    private val random = SecureRandom()

    init {
        check(secret.isNotBlank()) {
            "Plugin data encryption key is not configured. " +
                "Set UNIRHY_PLUGIN_DATA_ENCRYPTION_KEY (or UNIRHY_SECRET_KEY) before starting the server."
        }
    }

    fun encrypt(pluginId: String, dataKey: String, plaintext: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad(pluginId, dataKey))
        val ciphertext = cipher.doFinal(plaintext)
        return ByteBuffer.allocate(1 + nonce.size + ciphertext.size)
            .put(FORMAT_VERSION)
            .put(nonce)
            .put(ciphertext)
            .array()
    }

    fun decrypt(pluginId: String, dataKey: String, encrypted: ByteArray): ByteArray {
        require(encrypted.size > 1 + NONCE_BYTES) { "Invalid encrypted plugin data" }
        val buffer = ByteBuffer.wrap(encrypted)
        require(buffer.get() == FORMAT_VERSION) { "Unsupported encrypted plugin data format" }
        val nonce = ByteArray(NONCE_BYTES).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad(pluginId, dataKey))
        return cipher.doFinal(ciphertext)
    }

    private fun aad(pluginId: String, dataKey: String): ByteArray {
        val pluginBytes = pluginId.toByteArray(StandardCharsets.UTF_8)
        val keyBytes = dataKey.toByteArray(StandardCharsets.UTF_8)
        return ByteBuffer.allocate(Int.SIZE_BYTES * 2 + pluginBytes.size + keyBytes.size)
            .putInt(pluginBytes.size)
            .put(pluginBytes)
            .putInt(keyBytes.size)
            .put(keyBytes)
            .array()
    }

    private fun deriveKey(secret: String): ByteArray = MessageDigest.getInstance("SHA-256").run {
        update(KEY_CONTEXT)
        update(0)
        digest(secret.toByteArray(StandardCharsets.UTF_8))
    }

    private companion object {
        const val CIPHER = "AES/GCM/NoPadding"
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val FORMAT_VERSION: Byte = 1
        val KEY_CONTEXT: ByteArray = "unirhy-plugin-data-v1".toByteArray(StandardCharsets.UTF_8)
    }
}
