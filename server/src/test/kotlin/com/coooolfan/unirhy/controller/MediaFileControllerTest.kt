package com.coooolfan.unirhy.controller

import com.coooolfan.unirhy.model.MediaFile
import com.coooolfan.unirhy.model.storage.FileProviderFileSystem
import com.coooolfan.unirhy.model.storage.FileProviderOss
import com.coooolfan.unirhy.service.MediaFileResolver
import com.coooolfan.unirhy.service.ResolvedMediaFile
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.server.ResponseStatusException
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MediaFileControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var resolver: StubMediaFileResolver
    private lateinit var tempDir: Path
    private lateinit var mediaFile: File
    private lateinit var mediaBytes: ByteArray

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("media-controller-test")
        mediaFile = tempDir.resolve("track.flac").toFile()
        mediaBytes = "0123456789".toByteArray()
        Files.write(mediaFile.toPath(), mediaBytes)
        mediaFile.setLastModified(System.currentTimeMillis() - 10_000)

        resolver = StubMediaFileResolver().apply {
            handler = { id ->
                ResolvedMediaFile(
                    mediaFile = media(
                        id = id,
                        sha256 = "abc123",
                        objectKey = mediaFile.name,
                        mimeType = "audio/flac",
                        size = mediaFile.length(),
                    ),
                    file = mediaFile,
                )
            }
        }

        val controller = MediaFileController(resolver)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @AfterEach
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `get full media returns 200 and standard headers`() {
        val response = mockMvc.perform(get("/api/media/183"))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, max-age=31536000, immutable"))
            .andExpect(header().string(HttpHeaders.ETAG, "\"abc123\""))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andReturn()
            .response

        assertEquals("audio/flac", response.contentType)
        assertNotNull(response.getHeader(HttpHeaders.LAST_MODIFIED))
        assertTrue(response.getHeader(HttpHeaders.CONTENT_DISPOSITION).orEmpty().startsWith("inline;"))
        assertTrue(response.contentAsByteArray.contentEquals(mediaBytes))
    }

    @Test
    fun `single range returns 206 with content range`() {
        val response = mockMvc.perform(
            get("/api/media/183")
                .header(HttpHeaders.RANGE, "bytes=0-3"),
        )
            .andExpect(status().isPartialContent)
            .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-3/10"))
            .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
            .andReturn()
            .response

        assertEquals("audio/flac", response.contentType)
        assertEquals("0123", response.contentAsString)
    }

    @Test
    fun `multi range returns multipart byteranges`() {
        val response = mockMvc.perform(
            get("/api/media/183")
                .header(HttpHeaders.RANGE, "bytes=0-1,4-5"),
        )
            .andExpect(status().isPartialContent)
            .andReturn()
            .response

        assertTrue(response.contentType.orEmpty().startsWith("multipart/byteranges"))
        val body = response.contentAsString
        assertTrue(body.contains("Content-Range: bytes 0-1/10"))
        assertTrue(body.contains("Content-Range: bytes 4-5/10"))
    }

    @Test
    fun `invalid range returns 416`() {
        mockMvc.perform(
            get("/api/media/183")
                .header(HttpHeaders.RANGE, "bytes=100-200"),
        )
            .andExpect(status().isRequestedRangeNotSatisfiable)
            .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */10"))
    }

    @Test
    fun `if none match hit returns 304`() {
        mockMvc.perform(
            get("/api/media/183")
                .header(HttpHeaders.IF_NONE_MATCH, "\"abc123\""),
        )
            .andExpect(status().isNotModified)
    }

    @Test
    fun `range request with if none match hit returns 304`() {
        mockMvc.perform(
            get("/api/media/183")
                .header(HttpHeaders.RANGE, "bytes=0-3")
                .header(HttpHeaders.IF_NONE_MATCH, "\"abc123\""),
        )
            .andExpect(status().isNotModified)
            .andExpect(header().doesNotExist(HttpHeaders.CONTENT_RANGE))
    }

    @Test
    fun `if modified since hit returns 304`() {
        val ifModifiedSince = DateTimeFormatter.RFC_1123_DATE_TIME.format(
            Instant.ofEpochMilli(mediaFile.lastModified() + 60_000).atZone(ZoneOffset.UTC),
        )
        mockMvc.perform(
            get("/api/media/183")
                .header(HttpHeaders.IF_MODIFIED_SINCE, ifModifiedSince),
        )
            .andExpect(status().isNotModified)
    }

    @Test
    fun `if range mismatch falls back to full 200`() {
        val response = mockMvc.perform(
            get("/api/media/183")
                .header(HttpHeaders.RANGE, "bytes=0-3")
                .header(HttpHeaders.IF_RANGE, "\"other-tag\""),
        )
            .andExpect(status().isOk)
            .andExpect(header().doesNotExist(HttpHeaders.CONTENT_RANGE))
            .andReturn()
            .response

        assertTrue(response.contentAsByteArray.contentEquals(mediaBytes))
    }

    @Test
    fun `weak if range falls back to full 200`() {
        val response = mockMvc.perform(
            get("/api/media/183")
                .header(HttpHeaders.RANGE, "bytes=0-3")
                .header(HttpHeaders.IF_RANGE, "W/\"abc123\""),
        )
            .andExpect(status().isOk)
            .andExpect(header().doesNotExist(HttpHeaders.CONTENT_RANGE))
            .andReturn()
            .response

        assertTrue(response.contentAsByteArray.contentEquals(mediaBytes))
    }

    @Test
    fun `head request returns headers without body`() {
        val response = mockMvc.perform(head("/api/media/183"))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
            .andReturn()
            .response

        assertEquals(0, response.contentAsByteArray.size)
        assertEquals(mediaBytes.size.toString(), response.getHeader(HttpHeaders.CONTENT_LENGTH))
    }

    @Test
    fun `missing file returns 404`() {
        resolver.handler = {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Media file not found")
        }

        mockMvc.perform(get("/api/media/404"))
            .andExpect(status().isNotFound)
    }

    private class StubMediaFileResolver : MediaFileResolver {
        var handler: (Long) -> ResolvedMediaFile = { error("handler not configured") }
        override fun loadLocalFile(id: Long): ResolvedMediaFile = handler(id)
    }

    private fun media(
        id: Long,
        sha256: String,
        objectKey: String,
        mimeType: String,
        size: Long,
    ): MediaFile {
        return object : MediaFile {
            override val id: Long = id
            override val sha256: String = sha256
            override val objectKey: String = objectKey
            override val mimeType: String = mimeType
            override val size: Long = size
            override val width: Int? = null
            override val height: Int? = null
            override val ossProvider: FileProviderOss? = null
            override val fsProvider: FileProviderFileSystem? = null
        }
    }
}
