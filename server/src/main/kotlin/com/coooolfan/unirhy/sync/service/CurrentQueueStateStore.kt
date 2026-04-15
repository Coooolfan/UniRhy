package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.model.AccountPlayQueueState
import com.coooolfan.unirhy.sync.protocol.PlaybackStatus
import com.coooolfan.unirhy.sync.protocol.PlaybackStrategy
import com.coooolfan.unirhy.sync.protocol.StopStrategy
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.Instant

interface CurrentQueueStateStore {
    fun load(accountId: Long): AccountPlayQueueState?

    fun save(
        expectedVersion: Long,
        state: AccountPlayQueueState,
    ): Boolean
}

@Service
class JdbcCurrentQueueStateStore(
    private val jdbc: NamedParameterJdbcTemplate,
) : CurrentQueueStateStore {
    override fun load(accountId: Long): AccountPlayQueueState? {
        val params = MapSqlParameterSource().addValue("accountId", accountId)
        return jdbc.query(
            """
                SELECT account_id,
                       recording_ids,
                       current_index,
                       shuffle_indices,
                       playback_strategy,
                       stop_strategy,
                       playback_status,
                       position_ms,
                       server_time_to_execute_ms,
                       updated_at,
                       version
                FROM public.play_queue
                WHERE account_id = :accountId
            """.trimIndent(),
            params,
        ) { rs, _ ->
            AccountPlayQueueState(
                accountId = rs.getLong("account_id"),
                recordingIds = readLongArray(rs.getArray("recording_ids")),
                currentIndex = rs.getInt("current_index"),
                shuffleIndices = readIntArray(rs.getArray("shuffle_indices")),
                playbackStrategy = PlaybackStrategy.valueOf(rs.getString("playback_strategy")),
                stopStrategy = StopStrategy.valueOf(rs.getString("stop_strategy")),
                playbackStatus = PlaybackStatus.valueOf(rs.getString("playback_status")),
                positionMs = rs.getLong("position_ms"),
                serverTimeToExecuteMs = rs.getLong("server_time_to_execute_ms"),
                version = rs.getLong("version"),
                updatedAtMs = rs.getTimestamp("updated_at").toInstant().toEpochMilli(),
            )
        }.singleOrNull()
    }

    override fun save(
        expectedVersion: Long,
        state: AccountPlayQueueState,
    ): Boolean {
        val params = MapSqlParameterSource()
            .addValue("accountId", state.accountId)
            .addValue("recordingIds", state.recordingIds.toTypedArray())
            .addValue("currentIndex", state.currentIndex)
            .addValue("shuffleIndices", state.shuffleIndices.toTypedArray())
            .addValue("playbackStrategy", state.playbackStrategy.name)
            .addValue("stopStrategy", state.stopStrategy.name)
            .addValue("playbackStatus", state.playbackStatus.name)
            .addValue("positionMs", state.positionMs)
            .addValue("serverTimeToExecuteMs", state.serverTimeToExecuteMs)
            .addValue("updatedAt", Timestamp.from(Instant.ofEpochMilli(state.updatedAtMs)))
            .addValue("nextVersion", state.version)
            .addValue("expectedVersion", expectedVersion)

        if (expectedVersion == 0L) {
            val inserted = jdbc.update(
                """
                    INSERT INTO public.play_queue (
                        account_id,
                        recording_ids,
                        current_index,
                        shuffle_indices,
                        playback_strategy,
                        stop_strategy,
                        playback_status,
                        position_ms,
                        server_time_to_execute_ms,
                        updated_at,
                        version
                    ) VALUES (
                        :accountId,
                        :recordingIds,
                        :currentIndex,
                        :shuffleIndices,
                        :playbackStrategy,
                        :stopStrategy,
                        :playbackStatus,
                        :positionMs,
                        :serverTimeToExecuteMs,
                        :updatedAt,
                        :nextVersion
                    )
                    ON CONFLICT (account_id) DO NOTHING
                """.trimIndent(),
                params,
            )
            if (inserted > 0) {
                return true
            }
        }

        return jdbc.update(
            """
                UPDATE public.play_queue
                SET recording_ids = :recordingIds,
                    current_index = :currentIndex,
                    shuffle_indices = :shuffleIndices,
                    playback_strategy = :playbackStrategy,
                    stop_strategy = :stopStrategy,
                    playback_status = :playbackStatus,
                    position_ms = :positionMs,
                    server_time_to_execute_ms = :serverTimeToExecuteMs,
                    updated_at = :updatedAt,
                    version = :nextVersion
                WHERE account_id = :accountId
                  AND version = :expectedVersion
            """.trimIndent(),
            params,
        ) > 0
    }

    private fun readLongArray(array: java.sql.Array?): MutableList<Long> {
        val values = array?.array as? Array<*> ?: return mutableListOf()
        return values.map { (it as Number).toLong() }.toMutableList()
    }

    private fun readIntArray(array: java.sql.Array?): MutableList<Int> {
        val values = array?.array as? Array<*> ?: return mutableListOf()
        return values.map { (it as Number).toInt() }.toMutableList()
    }
}
