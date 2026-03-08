package com.coooolfan.unirhy.sync.log

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackSyncLogWriterTest {
    private val writer = PlaybackSyncLogWriter()

    @Test
    fun `sanitize replaces whitespace only for strings`() {
        assertEquals(
            "event=test message=hello_world count=42 ratio=1.5",
            buildMessage(
                "test",
                listOf(
                    "message" to "hello world",
                    "count" to 42,
                    "ratio" to 1.5,
                ),
            ),
        )
    }

    private fun buildMessage(
        event: String,
        fields: List<Pair<String, Any?>>,
    ): String {
        val method = PlaybackSyncLogWriter::class.java.getDeclaredMethod(
            "buildMessage",
            String::class.java,
            List::class.java,
        )
        method.isAccessible = true
        return method.invoke(writer, event, fields) as String
    }
}
