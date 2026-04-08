package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.model.AccountPlaybackState
import com.coooolfan.unirhy.sync.protocol.PlaybackStatus
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service

interface PlaybackResumeStateStore {
    fun load(accountId: Long): AccountPlaybackState?

    fun upsert(state: AccountPlaybackState)
}

@Service
class JdbcPlaybackResumeStateStore(
    private val jdbc: NamedParameterJdbcTemplate,
) : PlaybackResumeStateStore {
    private val logger = LoggerFactory.getLogger(JdbcPlaybackResumeStateStore::class.java)

    override fun load(accountId: Long): AccountPlaybackState? {
        val params = MapSqlParameterSource().addValue("accountId", accountId)
        val row = jdbc.query(
            """
                SELECT status,
                       recording_id,
                       position_seconds,
                       server_time_to_execute_ms,
                       version,
                       updated_at_ms
                FROM public.account_playback_resume_state
                WHERE account_id = :accountId
            """.trimIndent(),
            params,
        ) { rs, _ ->
            StoredPlaybackResumeStateRow(
                status = rs.getString("status"),
                recordingId = (rs.getObject("recording_id") as Number?)?.toLong(),
                positionSeconds = rs.getDouble("position_seconds"),
                serverTimeToExecuteMs = rs.getLong("server_time_to_execute_ms"),
                version = rs.getLong("version"),
                updatedAtMs = rs.getLong("updated_at_ms"),
            )
        }.singleOrNull() ?: return null

        return try {
            row.toAccountPlaybackState(accountId)
        } catch (ex: Exception) {
            logger.warn("Failed to deserialize playback resume state for accountId={}", accountId, ex)
            null
        }
    }

    override fun upsert(state: AccountPlaybackState) {
        val params = MapSqlParameterSource()
            .addValue("accountId", state.accountId)
            .addValue("status", state.status.name)
            .addValue("recordingId", state.recordingId)
            .addValue("positionSeconds", state.positionSeconds)
            .addValue("serverTimeToExecuteMs", state.serverTimeToExecuteMs)
            .addValue("version", state.version)
            .addValue("updatedAtMs", state.updatedAtMs)
        jdbc.update(
            """
                INSERT INTO public.account_playback_resume_state (
                    account_id,
                    status,
                    recording_id,
                    position_seconds,
                    server_time_to_execute_ms,
                    version,
                    updated_at_ms
                ) VALUES (
                    :accountId,
                    :status,
                    :recordingId,
                    :positionSeconds,
                    :serverTimeToExecuteMs,
                    :version,
                    :updatedAtMs
                )
                ON CONFLICT (account_id) DO UPDATE
                SET status = EXCLUDED.status,
                    recording_id = EXCLUDED.recording_id,
                    position_seconds = EXCLUDED.position_seconds,
                    server_time_to_execute_ms = EXCLUDED.server_time_to_execute_ms,
                    version = EXCLUDED.version,
                    updated_at_ms = EXCLUDED.updated_at_ms
            """.trimIndent(),
            params,
        )
    }
}

private data class StoredPlaybackResumeStateRow(
    val status: String,
    val recordingId: Long?,
    val positionSeconds: Double,
    val serverTimeToExecuteMs: Long,
    val version: Long,
    val updatedAtMs: Long,
)

private fun StoredPlaybackResumeStateRow.toAccountPlaybackState(accountId: Long): AccountPlaybackState {
    return AccountPlaybackState(
        accountId = accountId,
        status = PlaybackStatus.valueOf(status),
        recordingId = recordingId,
        positionSeconds = positionSeconds,
        serverTimeToExecuteMs = serverTimeToExecuteMs,
        version = version,
        updatedAtMs = updatedAtMs,
    )
}
