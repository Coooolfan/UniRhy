package com.coooolfan.unirhy.sync.log

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackSyncLogWriterTest {
    private val writer = PlaybackSyncLogWriter()
    private lateinit var logger: Logger
    private lateinit var appender: ListAppender<ILoggingEvent>
    private var originalLevel: Level? = null
    private var originalAdditive = true

    @BeforeEach
    fun setUp() {
        logger = LoggerFactory.getLogger(PlaybackSyncLogWriter::class.java) as Logger
        originalLevel = logger.level
        originalAdditive = logger.isAdditive
        appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.level = Level.DEBUG
        logger.isAdditive = false
        logger.addAppender(appender)
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(appender)
        logger.level = originalLevel
        logger.isAdditive = originalAdditive
        appender.stop()
    }

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

    @Test
    fun `debug logs structured message at debug level`() {
        writer.debug(
            "test",
            "message" to "hello world",
            "count" to 42,
        )

        val event = appender.list.single()
        assertEquals(Level.DEBUG, event.level)
        assertEquals("event=test message=hello_world count=42", event.formattedMessage)
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
