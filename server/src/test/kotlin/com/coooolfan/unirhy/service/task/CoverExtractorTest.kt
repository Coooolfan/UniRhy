package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.model.storage.FileProviderFileSystem
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.jaudiotagger.tag.images.ArtworkFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoverExtractorTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun fetchCoverPrefersSidecarCover() {
        val root = tempDir.resolve("library")
        Files.createDirectories(root)

        val audioFile = root.resolve("song.mp3").toFile().apply {
            writeBytes(byteArrayOf(0x00))
        }
        root.resolve("song.jpg").toFile().apply {
            writeBytes(byteArrayOf(0x01, 0x02, 0x03))
        }
        val embedded = ArtworkFactory.getNew().apply {
            binaryData = byteArrayOf(0x10, 0x11)
            mimeType = "image/png"
            width = 1
            height = 1
        }

        val provider = provider(id = 1L, root = root, readonly = false)

        val cover = fetchCover(
            file = audioFile,
            provider = provider,
            writeableProvider = provider,
            artwork = embedded,
        )

        assertNotNull(cover)
        assertEquals("song.jpg", cover.objectKey)
        assertEquals(provider.parentPath, cover.fsProvider?.parentPath)
    }

    @Test
    fun fetchCoverFromEmbeddedArtwork() {
        val readonlyRoot = tempDir.resolve("readonly")
        val writeableRoot = tempDir.resolve("writeable")
        Files.createDirectories(readonlyRoot)
        Files.createDirectories(writeableRoot)

        val audioFile = readonlyRoot.resolve("song.mp3").toFile().apply {
            writeBytes(byteArrayOf(0x00))
        }
        val artworkBytes = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
        val artwork = ArtworkFactory.getNew().apply {
            binaryData = artworkBytes
            mimeType = "image/png"
            width = 1
            height = 1
        }

        val provider = provider(id = 1L, root = readonlyRoot, readonly = true)
        val writeableProvider = provider(id = 2L, root = writeableRoot, readonly = false)
        val cover = fetchCover(
            file = audioFile,
            provider = provider,
            writeableProvider = writeableProvider,
            artwork = artwork,
        )

        val expectedObjectKey = "covers/${provider.id}/song.png"
        val expectedFile = writeableRoot.resolve(expectedObjectKey).toFile()

        assertNotNull(cover)
        assertEquals(expectedObjectKey, cover.objectKey)
        assertEquals("image/png", cover.mimeType)
        assertEquals(writeableProvider.parentPath, cover.fsProvider?.parentPath)
        assertTrue(expectedFile.exists())
        assertContentEquals(artworkBytes, expectedFile.readBytes())
    }

    @Test
    fun fetchCoverReturnsNullWhenNoCoverExists() {
        val root = tempDir.resolve("empty")
        Files.createDirectories(root)
        val audioFile = root.resolve("song.mp3").toFile().apply {
            writeBytes(byteArrayOf(0x00))
        }
        val provider = provider(id = 1L, root = root, readonly = false)
        val cover = fetchCover(
            file = audioFile,
            provider = provider,
            writeableProvider = provider,
            artwork = null,
        )
        assertNull(cover)
    }

    private fun provider(id: Long, root: Path, readonly: Boolean): FileProviderFileSystem {
        return FileProviderFileSystem {
            this.id = id
            name = "provider-$id"
            parentPath = root.toString()
            this.readonly = readonly
        }
    }
}
