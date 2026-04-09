package com.coooolfan.unirhy.sync.service

import org.postgresql.PGConnection
import org.springframework.jdbc.core.ConnectionCallback
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.sql.Connection
import javax.sql.DataSource

enum class PlaybackSyncEventType {
    LOAD_AUDIO_SOURCE,
    QUEUE_CHANGE,
    SCHEDULED_ACTION,
    DEVICE_CHANGE,
}

data class PlaybackSyncEvent(
    val id: Long,
    val accountId: Long,
    val eventType: PlaybackSyncEventType,
    val payload: String,
    val createdAtMs: Long,
)

interface PlaybackSyncEventStore {
    fun append(
        accountId: Long,
        eventType: PlaybackSyncEventType,
        dedupeKey: String,
        payload: String,
        createdAtMs: Long,
    ): Long?

    fun currentMaxId(): Long

    fun listAfterId(
        afterId: Long,
        limit: Int,
    ): List<PlaybackSyncEvent>
}

@Service
class JdbcPlaybackSyncEventStore(
    private val jdbc: NamedParameterJdbcTemplate,
) : PlaybackSyncEventStore {
    override fun append(
        accountId: Long,
        eventType: PlaybackSyncEventType,
        dedupeKey: String,
        payload: String,
        createdAtMs: Long,
    ): Long? {
        val params = MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("eventType", eventType.name)
            .addValue("dedupeKey", dedupeKey)
            .addValue("payload", payload)
            .addValue("createdAtMs", createdAtMs)
        return jdbc.query(
            """
                INSERT INTO public.playback_sync_event (
                    account_id,
                    event_type,
                    dedupe_key,
                    payload,
                    created_at_ms
                ) VALUES (
                    :accountId,
                    :eventType,
                    :dedupeKey,
                    CAST(:payload AS jsonb),
                    :createdAtMs
                )
                ON CONFLICT (dedupe_key) DO NOTHING
                RETURNING id
            """.trimIndent(),
            params,
        ) { rs, _ -> rs.getLong("id") }.singleOrNull()
    }

    override fun currentMaxId(): Long {
        return jdbc.jdbcOperations.queryForObject(
            "SELECT COALESCE(MAX(id), 0) FROM public.playback_sync_event",
            Long::class.java,
        ) ?: 0L
    }

    override fun listAfterId(
        afterId: Long,
        limit: Int,
    ): List<PlaybackSyncEvent> {
        val params = MapSqlParameterSource()
            .addValue("afterId", afterId)
            .addValue("limit", limit)
        return jdbc.query(
            """
                SELECT id,
                       account_id,
                       event_type,
                       payload::text AS payload,
                       created_at_ms
                FROM public.playback_sync_event
                WHERE id > :afterId
                ORDER BY id
                LIMIT :limit
            """.trimIndent(),
            params,
        ) { rs, _ ->
            PlaybackSyncEvent(
                id = rs.getLong("id"),
                accountId = rs.getLong("account_id"),
                eventType = PlaybackSyncEventType.valueOf(rs.getString("event_type")),
                payload = rs.getString("payload"),
                createdAtMs = rs.getLong("created_at_ms"),
            )
        }
    }
}

interface PlaybackSyncEventNotifier {
    fun notifyEvent(eventId: Long)
}

@Service
class PgPlaybackSyncEventNotifier(
    private val jdbc: NamedParameterJdbcTemplate,
) : PlaybackSyncEventNotifier {
    override fun notifyEvent(eventId: Long) {
        jdbc.jdbcOperations.execute(ConnectionCallback { connection ->
            connection.prepareStatement("SELECT pg_notify(?, ?)").use { statement ->
                statement.setString(1, CHANNEL)
                statement.setString(2, eventId.toString())
                statement.execute()
            }
        })
    }

    companion object {
        const val CHANNEL = "playback_sync_event"
    }
}

@Service
class PlaybackSyncEventSubscriber(
    private val dataSource: DataSource,
) {
    fun openConnection(): ListeningPgConnection {
        val connection = dataSource.connection
        connection.autoCommit = true
        connection.createStatement().use { it.execute("LISTEN ${PgPlaybackSyncEventNotifier.CHANNEL}") }
        return ListeningPgConnection(
            connection = connection,
            pgConnection = connection.unwrap(PGConnection::class.java),
        )
    }
}

data class ListeningPgConnection(
    val connection: Connection,
    val pgConnection: PGConnection,
) : AutoCloseable {
    override fun close() {
        connection.close()
    }
}
