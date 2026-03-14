package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.model.storage.FileProviderType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

class ScanTaskDiscoveryTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `discovery yields every audio file under root`() {
        val root = prepareLibrary("existing.mp3", "new.mp3")

        val payloads = discover(root)

        assertEquals(listOf("existing.mp3", "new.mp3"), payloads.map { it.objectKey }.sorted())
    }

    @Test
    fun `discovery skips non audio files`() {
        val root = prepareLibrary("done.mp3", "fresh.m4a", "cover.jpg", "notes.txt")

        val payloads = discover(root)

        assertEquals(listOf("done.mp3", "fresh.m4a"), payloads.map { it.objectKey }.sorted())
    }

    @Test
    fun `relative object keys stay stable for nested files`() {
        val root = prepareLibrary("disc-1/track01.mp3", "disc-1/cover.jpg", "disc-2/track02.wav")

        val payloads = discover(root)

        assertEquals(
            listOf("disc-1/track01.mp3", "disc-2/track02.wav"),
            payloads.map { it.objectKey }.sorted(),
        )
    }

    private fun discover(
        rootDir: Path,
    ): List<ScanFileTaskPayload> {
        return discoverScanFileTaskPayloads(
            rootDir = rootDir.toFile(),
            providerType = FileProviderType.FILE_SYSTEM,
            providerId = 42L,
        ).toList()
    }

    private fun prepareLibrary(vararg relativePaths: String): Path {
        val root = tempDir.resolve("library-${relativePaths.size}")
        Files.createDirectories(root)
        for (relativePath in relativePaths) {
            val file = root.resolve(relativePath)
            Files.createDirectories(file.parent)
            Files.write(file, byteArrayOf(0x00))
        }
        return root
    }
}
