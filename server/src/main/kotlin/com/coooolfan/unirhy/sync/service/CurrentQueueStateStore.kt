package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.model.AccountCurrentQueueState
import com.coooolfan.unirhy.sync.model.CurrentQueueEntry
import com.coooolfan.unirhy.sync.protocol.PlaybackStrategy
import com.coooolfan.unirhy.sync.protocol.StopStrategy
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service

interface CurrentQueueStateStore {
    fun load(accountId: Long): AccountCurrentQueueState?

    fun upsert(state: AccountCurrentQueueState)
}

@Service
class JdbcCurrentQueueStateStore(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
) : CurrentQueueStateStore {
    private val logger = LoggerFactory.getLogger(JdbcCurrentQueueStateStore::class.java)

    override fun load(accountId: Long): AccountCurrentQueueState? {
        val params = MapSqlParameterSource().addValue("accountId", accountId)
        val row = jdbc.query(
            """
                SELECT state::text AS state,
                       version,
                       updated_at_ms
                FROM public.account_current_queue
                WHERE account_id = :accountId
            """.trimIndent(),
            params,
        ) { rs, _ ->
            StoredCurrentQueueRow(
                state = rs.getString("state"),
                version = rs.getLong("version"),
                updatedAtMs = rs.getLong("updated_at_ms"),
            )
        }.singleOrNull() ?: return null

        return try {
            row.toAccountCurrentQueueState(accountId, objectMapper)
        } catch (ex: Exception) {
            logger.warn("Failed to deserialize current queue state for accountId={}", accountId, ex)
            null
        }
    }

    override fun upsert(state: AccountCurrentQueueState) {
        val params = MapSqlParameterSource()
            .addValue("accountId", state.accountId)
            .addValue("state", objectMapper.writeValueAsString(state.toSnapshot()))
            .addValue("version", state.version)
            .addValue("updatedAtMs", state.updatedAtMs)
        jdbc.update(
            """
                INSERT INTO public.account_current_queue (
                    account_id,
                    state,
                    version,
                    updated_at_ms
                ) VALUES (
                    :accountId,
                    CAST(:state AS jsonb),
                    :version,
                    :updatedAtMs
                )
                ON CONFLICT (account_id) DO UPDATE
                SET state = EXCLUDED.state,
                    version = EXCLUDED.version,
                    updated_at_ms = EXCLUDED.updated_at_ms
            """.trimIndent(),
            params,
        )
    }
}

internal data class CurrentQueueStateSnapshot(
    val items: List<CurrentQueueEntrySnapshot>,
    val currentEntryId: Long? = null,
    val playbackStrategy: PlaybackStrategy,
    val stopStrategy: StopStrategy,
    val shuffleEntryIds: List<Long>,
    val nextEntryId: Long,
)

internal data class CurrentQueueEntrySnapshot(
    val entryId: Long,
    val recordingId: Long,
    val workId: Long,
    val title: String,
    val artistLabel: String,
    val coverMediaFileId: Long? = null,
    val durationMs: Long,
)

private data class StoredCurrentQueueRow(
    val state: String,
    val version: Long,
    val updatedAtMs: Long,
)

internal fun AccountCurrentQueueState.toSnapshot(): CurrentQueueStateSnapshot {
    return CurrentQueueStateSnapshot(
        items = items.map { entry ->
            CurrentQueueEntrySnapshot(
                entryId = entry.entryId,
                recordingId = entry.recordingId,
                workId = entry.workId,
                title = entry.title,
                artistLabel = entry.artistLabel,
                coverMediaFileId = entry.coverMediaFileId,
                durationMs = entry.durationMs,
            )
        },
        currentEntryId = currentEntryId,
        playbackStrategy = playbackStrategy,
        stopStrategy = stopStrategy,
        shuffleEntryIds = shuffleEntryIds.toList(),
        nextEntryId = nextEntryId,
    )
}

private fun StoredCurrentQueueRow.toAccountCurrentQueueState(
    accountId: Long,
    objectMapper: ObjectMapper,
): AccountCurrentQueueState {
    val snapshot = objectMapper.readValue(state, CurrentQueueStateSnapshot::class.java)
    return AccountCurrentQueueState(
        accountId = accountId,
        items = snapshot.items.map { entry ->
            CurrentQueueEntry(
                entryId = entry.entryId,
                recordingId = entry.recordingId,
                workId = entry.workId,
                title = entry.title,
                artistLabel = entry.artistLabel,
                coverMediaFileId = entry.coverMediaFileId,
                durationMs = entry.durationMs,
            )
        }.toMutableList(),
        currentEntryId = snapshot.currentEntryId,
        playbackStrategy = snapshot.playbackStrategy,
        stopStrategy = snapshot.stopStrategy,
        shuffleEntryIds = snapshot.shuffleEntryIds.toMutableList(),
        nextEntryId = snapshot.nextEntryId,
        version = version,
        updatedAtMs = updatedAtMs,
    )
}
