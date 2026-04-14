package com.coooolfan.unirhy.sync.service

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.coooolfan.unirhy.sync.model.AccountCurrentQueueState
import com.coooolfan.unirhy.sync.model.CurrentQueueEntry
import com.coooolfan.unirhy.sync.protocol.PlaybackStrategy
import com.coooolfan.unirhy.sync.protocol.StopStrategy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.core.namedparam.SqlParameterSource
import java.io.PrintWriter
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.util.logging.Logger as JulLogger
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JdbcCurrentQueueStateStoreTest {
    private lateinit var jdbc: FakeNamedParameterJdbcTemplate
    private lateinit var store: JdbcCurrentQueueStateStore
    private lateinit var logger: Logger
    private lateinit var appender: ListAppender<ILoggingEvent>
    private var originalLevel: Level? = null
    private var originalAdditive: Boolean = true

    @BeforeEach
    fun setUp() {
        jdbc = FakeNamedParameterJdbcTemplate()
        store = JdbcCurrentQueueStateStore(jdbc, jacksonObjectMapper())
        logger = LoggerFactory.getLogger(JdbcCurrentQueueStateStore::class.java) as Logger
        originalLevel = logger.level
        originalAdditive = logger.isAdditive
        appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.level = Level.WARN
        logger.isAdditive = false
        logger.addAppender(appender)
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(appender)
        logger.level = originalLevel
        logger.isAdditive = originalAdditive
    }

    @Test
    fun `upsert and load preserve current queue snapshot`() {
        val state = sampleState()

        store.upsert(state)
        val restored = store.load(42L)

        requireNotNull(restored)
        assertEquals(state.items, restored.items)
        assertEquals(state.currentEntryId, restored.currentEntryId)
        assertEquals(state.playbackStrategy, restored.playbackStrategy)
        assertEquals(state.stopStrategy, restored.stopStrategy)
        assertEquals(state.shuffleEntryIds, restored.shuffleEntryIds)
        assertEquals(state.nextEntryId, restored.nextEntryId)
        assertEquals(state.version, restored.version)
        assertEquals(state.updatedAtMs, restored.updatedAtMs)
    }

    @Test
    fun `upsert overwrites same account`() {
        store.upsert(sampleState())
        store.upsert(
            sampleState().copy(
                items = mutableListOf(
                    CurrentQueueEntry(
                        entryId = 9L,
                        recordingId = 2002L,
                        workId = 3002L,
                        title = "Track 2",
                        artistLabel = "Artist 2",
                        coverMediaFileId = null,
                        durationMs = 210_000L,
                    ),
                ),
                currentEntryId = 9L,
                playbackStrategy = PlaybackStrategy.SEQUENTIAL,
                stopStrategy = StopStrategy.TRACK,
                shuffleEntryIds = mutableListOf(),
                nextEntryId = 10L,
                version = 3L,
                updatedAtMs = 2_000L,
            ),
        )

        val restored = requireNotNull(store.load(42L))
        assertEquals(listOf(2002L), restored.items.map { it.recordingId })
        assertEquals(9L, restored.currentEntryId)
        assertEquals(3L, restored.version)
        assertEquals(2_000L, restored.updatedAtMs)
    }

    @Test
    fun `bad json returns null and logs warning`() {
        jdbc.seedRaw(
            accountId = 42L,
            stateJson = """{"items":[{"entryId":"bad"}]}""",
            version = 1L,
            updatedAtMs = 1_000L,
        )

        val restored = store.load(42L)

        assertNull(restored)
        assertTrue(appender.list.any { it.level == Level.WARN && it.formattedMessage.contains("accountId=42") })
    }

    private fun sampleState(): AccountCurrentQueueState {
        return AccountCurrentQueueState(
            accountId = 42L,
            items = mutableListOf(
                CurrentQueueEntry(
                    entryId = 1L,
                    recordingId = 1001L,
                    workId = 3001L,
                    title = "Track 1",
                    artistLabel = "Artist 1",
                    coverMediaFileId = 4001L,
                    durationMs = 180_000L,
                ),
                CurrentQueueEntry(
                    entryId = 2L,
                    recordingId = 1002L,
                    workId = 3002L,
                    title = "Track 2",
                    artistLabel = "Artist 2",
                    coverMediaFileId = null,
                    durationMs = 210_000L,
                ),
            ),
            currentEntryId = 2L,
            playbackStrategy = PlaybackStrategy.SHUFFLE,
            stopStrategy = StopStrategy.LIST,
            shuffleEntryIds = mutableListOf(2L, 1L),
            nextEntryId = 3L,
            version = 2L,
            updatedAtMs = 1_500L,
        )
    }
}

private class FakeNamedParameterJdbcTemplate : NamedParameterJdbcTemplate(StubDataSource()) {
    private val rows = linkedMapOf<Long, StoredRow>()

    override fun update(sql: String, paramSource: SqlParameterSource): Int {
        val accountId = requireNotNull(paramSource.getValue("accountId")) as Long
        rows[accountId] = StoredRow(
            state = requireNotNull(paramSource.getValue("state")) as String,
            version = requireNotNull(paramSource.getValue("version")) as Long,
            updatedAtMs = requireNotNull(paramSource.getValue("updatedAtMs")) as Long,
        )
        return 1
    }

    override fun <T : Any?> query(
        sql: String,
        paramSource: SqlParameterSource,
        rowMapper: RowMapper<T>,
    ): MutableList<T> {
        val accountId = requireNotNull(paramSource.getValue("accountId")) as Long
        val row = rows[accountId] ?: return mutableListOf()
        val resultSet = Proxy.newProxyInstance(
            ResultSet::class.java.classLoader,
            arrayOf(ResultSet::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getString" -> {
                    when (args[0]) {
                        "state" -> row.state
                        else -> null
                    }
                }

                "getLong" -> {
                    when (args[0]) {
                        "version" -> row.version
                        "updated_at_ms" -> row.updatedAtMs
                        else -> 0L
                    }
                }

                "wasNull" -> false
                else -> throw UnsupportedOperationException("Unsupported ResultSet method: ${method.name}")
            }
        } as ResultSet
        return mutableListOf(rowMapper.mapRow(resultSet, 0))
    }

    fun seedRaw(
        accountId: Long,
        stateJson: String,
        version: Long,
        updatedAtMs: Long,
    ) {
        rows[accountId] = StoredRow(stateJson, version, updatedAtMs)
    }
}

private data class StoredRow(
    val state: String,
    val version: Long,
    val updatedAtMs: Long,
)

private class StubDataSource : DataSource {
    override fun getConnection(): Connection {
        throw UnsupportedOperationException()
    }

    override fun getConnection(username: String?, password: String?): Connection {
        throw UnsupportedOperationException()
    }

    override fun <T : Any?> unwrap(iface: Class<T>?): T {
        throw SQLException("Unsupported")
    }

    override fun isWrapperFor(iface: Class<*>?): Boolean = false

    override fun getLogWriter(): PrintWriter? = null

    override fun setLogWriter(out: PrintWriter?) = Unit

    override fun setLoginTimeout(seconds: Int) = Unit

    override fun getLoginTimeout(): Int = 0

    override fun getParentLogger(): JulLogger = JulLogger.getAnonymousLogger()
}
