package com.coooolfan.unirhy.service.task

import org.junit.jupiter.api.Test
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CoverExtractorTest {

    private val coverExtractor = CoverExtractor()

    @Test
    fun extractPrefersSidecarCover() {
        val sidecar = SidecarCoverCandidate(
            objectKey = "song.jpg",
            mimeType = "image/jpeg",
            size = 3,
            sha256 = "abc123"
        )
        val embedded = EmbeddedArtworkCandidate(
            bytes = byteArrayOf(0x01, 0x02),
            mimeType = "image/png",
            width = 1,
            height = 1
        )

        val payload = coverExtractor.extract(CoverCandidate(sidecar, embedded))

        assertNotNull(payload)
        assertTrue(payload is CoverPayload.Sidecar)
        payload as CoverPayload.Sidecar
        assertEquals("song.jpg", payload.objectKey)
        assertEquals("image/jpeg", payload.mimeType)
        assertEquals("abc123", payload.sha256)
    }

    @Test
    fun extractFromEmbeddedArtwork() {
        val artworkBytes = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
        val embedded = EmbeddedArtworkCandidate(
            bytes = artworkBytes,
            mimeType = "image/png",
            width = 1,
            height = 1
        )
        val payload = coverExtractor.extract(CoverCandidate(null, embedded))

        val expectedSha = sha256Bytes(artworkBytes)
        val expectedObjectKey = "covers/$expectedSha.png"

        assertNotNull(payload)
        assertTrue(payload is CoverPayload.Embedded)
        payload as CoverPayload.Embedded
        assertEquals(expectedObjectKey, payload.objectKey)
        assertEquals("image/png", payload.mimeType)
    }

    private fun sha256Bytes(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

}
