package com.coooolfan.unirhy.service.task

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals

class TranscodeTaskServicePathTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `transcoded object key stays relative to destination root`() {
        val dstRoot = tempDir.resolve("data").toFile().apply { mkdirs() }
        val outputFile = File(dstRoot, "opus/result.opus")

        assertEquals("opus/result.opus", buildTranscodedObjectKey(outputFile, dstRoot))
    }
}
