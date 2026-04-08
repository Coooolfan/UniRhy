package com.coooolfan.unirhy.sync.service

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.coooolfan.unirhy.sync.model.AccountPlaybackState
import com.coooolfan.unirhy.sync.protocol.PlaybackStatus
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

class JdbcPlaybackResumeStateStoreTest {
    private lateinit var jdbc: FakePlaybackResumeNamedParameterJdbcTemplate
    private lateinit var store: JdbcPlaybackResumeStateStore
    private lateinit var logger: Logger
    private lateinit var appender: ListAppender<ILoggingEvent>
    private var originalLevel: Level? = null
    private var originalAdditive: Boolean = true

    @BeforeEach
    fun setUp() {
        jdbc = FakePlaybackResumeNamedParameterJdbcTemplate()
        store = JdbcPlaybackResumeStateStore(jdbc)
        logger = LoggerFactory.getLogger(JdbcPlaybackResumeStateStore::class.java) as Logger
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
    fun `upsert and load preserve playback resume state`() {
        val state = sampleState()

        store.upsert(state)
        val restored = store.load(42L)

        assertEquals(state, restored)
    }

    @Test
    fun `upsert overwrites same account`() {
        store.upsert(sampleState())
        store.upsert(
            sampleState().copy(
                status = PlaybackStatus.PAUSED,
                recordingId = null,
                positionSeconds = 0.0,
                serverTimeToExecuteMs = 0L,
                version = 9L,
                updatedAtMs = 2_000L,
            ),
        )

        val restored = requireNotNull(store.load(42L))
        assertEquals(PlaybackStatus.PAUSED, restored.status)
        assertNull(restored.recordingId)
        assertEquals(0.0, restored.positionSeconds)
        assertEquals(9L, restored.version)
        assertEquals(2_000L, restored.updatedAtMs)
    }

    @Test
    fun `bad status returns null and logs warning`() {
        jdbc.seedRaw(
            accountId = 42L,
            status = "BROKEN",
            recordingId = 1001L,
            positionSeconds = 12.5,
            serverTimeToExecuteMs = 1_500L,
            version = 2L,
            updatedAtMs = 1_200L,
        )

        val restored = store.load(42L)

        assertNull(restored)
        assertTrue(appender.list.any { it.level == Level.WARN && it.formattedMessage.contains("accountId=42") })
    }

    private fun sampleState(): AccountPlaybackState {
        return AccountPlaybackState(
            accountId = 42L,
            status = PlaybackStatus.PLAYING,
            recordingId = 1001L,
            positionSeconds = 12.5,
            serverTimeToExecuteMs = 1_500L,
            version = 2L,
            updatedAtMs = 1_200L,
        )
    }
}

private class FakePlaybackResumeNamedParameterJdbcTemplate : NamedParameterJdbcTemplate(PlaybackResumeStubDataSource()) {
    private val rows = linkedMapOf<Long, PlaybackResumeStoredRow>()

    override fun update(sql: String, paramSource: SqlParameterSource): Int {
        val accountId = requireNotNull(paramSource.getValue("accountId")) as Long
        rows[accountId] = PlaybackResumeStoredRow(
            status = requireNotNull(paramSource.getValue("status")) as String,
            recordingId = paramSource.getValue("recordingId") as Long?,
            positionSeconds = requireNotNull(paramSource.getValue("positionSeconds")) as Double,
            serverTimeToExecuteMs = requireNotNull(paramSource.getValue("serverTimeToExecuteMs")) as Long,
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
                "getString" -> when (args[0]) {
                    "status" -> row.status
                    else -> null
                }

                "getLong" -> when (args[0]) {
                    "server_time_to_execute_ms" -> row.serverTimeToExecuteMs
                    "version" -> row.version
                    "updated_at_ms" -> row.updatedAtMs
                    else -> 0L
                }

                "getDouble" -> when (args[0]) {
                    "position_seconds" -> row.positionSeconds
                    else -> 0.0
                }

                "getObject" -> when (args[0]) {
                    "recording_id" -> row.recordingId
                    else -> null
                }

                "wasNull" -> false
                else -> throw UnsupportedOperationException("Unsupported ResultSet method: ${method.name}")
            }
        } as ResultSet
        return mutableListOf(rowMapper.mapRow(resultSet, 0))
    }

    fun seedRaw(
        accountId: Long,
        status: String,
        recordingId: Long?,
        positionSeconds: Double,
        serverTimeToExecuteMs: Long,
        version: Long,
        updatedAtMs: Long,
    ) {
        rows[accountId] = PlaybackResumeStoredRow(
            status = status,
            recordingId = recordingId,
            positionSeconds = positionSeconds,
            serverTimeToExecuteMs = serverTimeToExecuteMs,
            version = version,
            updatedAtMs = updatedAtMs,
        )
    }
}

private data class PlaybackResumeStoredRow(
    val status: String,
    val recordingId: Long?,
    val positionSeconds: Double,
    val serverTimeToExecuteMs: Long,
    val version: Long,
    val updatedAtMs: Long,
)

private class PlaybackResumeStubDataSource : DataSource {
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
