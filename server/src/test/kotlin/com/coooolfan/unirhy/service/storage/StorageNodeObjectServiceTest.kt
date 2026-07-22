package com.coooolfan.unirhy.service.storage

import com.coooolfan.unirhy.model.storage.FileProviderFileSystem
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import java.util.Base64
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StorageNodeObjectServiceTest {
    @TempDir
    lateinit var tempDir: Path

    private val service = StorageNodeObjectService(mock(KSqlClient::class.java), jacksonObjectMapper())

    @Test
    fun `file system listing is sorted paged and cursor-bound`() {
        val node = node()
        service.write(node, "covers/z.jpg", byteArrayOf(3), "image/jpeg")
        service.write(node, "covers/a.jpg", byteArrayOf(1, 2), "image/jpeg")
        service.write(node, "audio/song.flac", byteArrayOf(4), "audio/flac")

        val first = service.list(node, "covers/", 1, null)
        assertEquals(listOf(StorageObjectListItem("covers/a.jpg", 2)), first.objects)
        val cursor = assertNotNull(first.nextCursor)

        val second = service.list(node, "covers/", 1, cursor)
        assertEquals(listOf(StorageObjectListItem("covers/z.jpg", 1)), second.objects)
        assertEquals(null, second.nextCursor)

        assertFailsWith<IllegalArgumentException> {
            service.list(node, "audio/", 1, cursor)
        }

        val cursorBytes = Base64.getUrlDecoder().decode(cursor)
        cursorBytes[cursorBytes.lastIndex] = (cursorBytes.last().toInt() xor 1).toByte()
        val tampered = Base64.getUrlEncoder().withoutPadding().encodeToString(cursorBytes)
        assertFailsWith<IllegalArgumentException> {
            service.list(node, "covers/", 1, tampered)
        }
    }

    @Test
    fun `download commit does not expose partial target and honors overwrite`() {
        val node = node()
        val source = createTempFile(tempDir, "download-", ".tmp")
        source.writeText("complete response")

        service.commitDownloadedFile(node, "imports/song.bin", source.toFile(), "application/octet-stream", false)
        assertEquals(
            "complete response",
            service.openStream(node, "imports/song.bin").bufferedReader().use { it.readText() },
        )

        assertFailsWith<FileAlreadyExistsException> {
            service.commitDownloadedFile(node, "imports/song.bin", source.toFile(), "application/octet-stream", false)
        }
        assertTrue(service.delete(node, "imports/song.bin"))
        assertFalse(service.delete(node, "imports/song.bin"))
    }

    private fun node(): FileSystemStorageNode = FileSystemStorageNode(
        FileProviderFileSystem {
            id = 1
            name = "test"
            parentPath = tempDir.toString()
            readonly = false
        },
    )
}
