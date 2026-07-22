package com.coooolfan.unirhy.service.plugin.hostapi

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse

class PluginDataCipherTest {
    private val cipher = PluginDataCipher("test-plugin-data-secret")

    @Test
    fun `encrypts and decrypts with randomized ciphertext`() {
        val plaintext = "\"sensitive-value\"".toByteArray()
        val first = cipher.encrypt("com.example.plugin", "apiKey", plaintext)
        val second = cipher.encrypt("com.example.plugin", "apiKey", plaintext)

        assertFalse(first.contentEquals(second))
        assertContentEquals(plaintext, cipher.decrypt("com.example.plugin", "apiKey", first))
        assertContentEquals(plaintext, cipher.decrypt("com.example.plugin", "apiKey", second))
    }

    @Test
    fun `binds ciphertext to plugin and key`() {
        val encrypted = cipher.encrypt("com.example.plugin", "apiKey", "secret".toByteArray())

        assertFails { cipher.decrypt("com.example.other", "apiKey", encrypted) }
        assertFails { cipher.decrypt("com.example.plugin", "otherKey", encrypted) }
    }
}
