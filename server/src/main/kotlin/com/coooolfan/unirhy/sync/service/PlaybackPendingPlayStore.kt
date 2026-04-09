package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.model.PendingPlayState
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service

interface PlaybackPendingPlayStore {
    fun load(accountId: Long): PendingPlayState?

    fun save(accountId: Long, pendingPlay: PendingPlayState)

    fun clear(accountId: Long)

    fun markLoaded(
        accountId: Long,
        commandId: String,
        deviceId: String,
        createdAtMs: Long,
    )

    fun unmarkLoaded(
        accountId: Long,
        commandId: String,
        deviceId: String,
    )
}

class InMemoryPlaybackPendingPlayStore : PlaybackPendingPlayStore {
    private val states = linkedMapOf<Long, PendingPlayState>()

    override fun load(accountId: Long): PendingPlayState? = states[accountId]?.copy(
        clientsLoaded = states[accountId]?.clientsLoaded?.toMutableSet() ?: mutableSetOf(),
    )

    override fun save(
        accountId: Long,
        pendingPlay: PendingPlayState,
    ) {
        states[accountId] = pendingPlay.copy(clientsLoaded = pendingPlay.clientsLoaded.toMutableSet())
    }

    override fun clear(accountId: Long) {
        states.remove(accountId)
    }

    override fun markLoaded(
        accountId: Long,
        commandId: String,
        deviceId: String,
        createdAtMs: Long,
    ) {
        val state = states[accountId] ?: return
        if (state.commandId != commandId) {
            return
        }
        state.clientsLoaded += deviceId
    }

    override fun unmarkLoaded(
        accountId: Long,
        commandId: String,
        deviceId: String,
    ) {
        val state = states[accountId] ?: return
        if (state.commandId != commandId) {
            return
        }
        state.clientsLoaded.remove(deviceId)
    }
}

@Service
class JdbcPlaybackPendingPlayStore(
    private val jdbc: NamedParameterJdbcTemplate,
) : PlaybackPendingPlayStore {
    override fun load(accountId: Long): PendingPlayState? {
        val params = MapSqlParameterSource().addValue("accountId", accountId)
        val pending = jdbc.query(
            """
                SELECT command_id,
                       initiator_device_id,
                       recording_id,
                       position_seconds,
                       created_at_ms,
                       timeout_at_ms
                FROM public.playback_pending_play
                WHERE account_id = :accountId
            """.trimIndent(),
            params,
        ) { rs, _ ->
            PendingPlayState(
                commandId = rs.getString("command_id"),
                initiatorDeviceId = rs.getString("initiator_device_id"),
                recordingId = rs.getLong("recording_id"),
                positionSeconds = rs.getDouble("position_seconds"),
                clientsLoaded = mutableSetOf(),
                createdAtMs = rs.getLong("created_at_ms"),
                timeoutAtMs = rs.getLong("timeout_at_ms"),
            )
        }.singleOrNull() ?: return null

        pending.clientsLoaded += jdbc.query(
            """
                SELECT device_id
                FROM public.playback_pending_play_loaded_device
                WHERE account_id = :accountId
                  AND command_id = :commandId
                ORDER BY device_id
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("commandId", pending.commandId),
        ) { rs, _ -> rs.getString("device_id") }
        return pending
    }

    override fun save(
        accountId: Long,
        pendingPlay: PendingPlayState,
    ) {
        val params = MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("commandId", pendingPlay.commandId)
            .addValue("initiatorDeviceId", pendingPlay.initiatorDeviceId)
            .addValue("recordingId", pendingPlay.recordingId)
            .addValue("positionSeconds", pendingPlay.positionSeconds)
            .addValue("createdAtMs", pendingPlay.createdAtMs)
            .addValue("timeoutAtMs", pendingPlay.timeoutAtMs)
        jdbc.update(
            """
                INSERT INTO public.playback_pending_play (
                    account_id,
                    command_id,
                    initiator_device_id,
                    recording_id,
                    position_seconds,
                    created_at_ms,
                    timeout_at_ms
                ) VALUES (
                    :accountId,
                    :commandId,
                    :initiatorDeviceId,
                    :recordingId,
                    :positionSeconds,
                    :createdAtMs,
                    :timeoutAtMs
                )
                ON CONFLICT (account_id) DO UPDATE
                SET command_id = EXCLUDED.command_id,
                    initiator_device_id = EXCLUDED.initiator_device_id,
                    recording_id = EXCLUDED.recording_id,
                    position_seconds = EXCLUDED.position_seconds,
                    created_at_ms = EXCLUDED.created_at_ms,
                    timeout_at_ms = EXCLUDED.timeout_at_ms
            """.trimIndent(),
            params,
        )
        jdbc.update(
            "DELETE FROM public.playback_pending_play_loaded_device WHERE account_id = :accountId",
            MapSqlParameterSource().addValue("accountId", accountId),
        )
        pendingPlay.clientsLoaded.forEach { deviceId ->
            markLoaded(accountId, pendingPlay.commandId, deviceId, pendingPlay.createdAtMs)
        }
    }

    override fun clear(accountId: Long) {
        val params = MapSqlParameterSource().addValue("accountId", accountId)
        jdbc.update("DELETE FROM public.playback_pending_play WHERE account_id = :accountId", params)
    }

    override fun markLoaded(
        accountId: Long,
        commandId: String,
        deviceId: String,
        createdAtMs: Long,
    ) {
        val params = MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("commandId", commandId)
            .addValue("deviceId", deviceId)
            .addValue("createdAtMs", createdAtMs)
        jdbc.update(
            """
                INSERT INTO public.playback_pending_play_loaded_device (
                    account_id,
                    command_id,
                    device_id,
                    created_at_ms
                ) VALUES (
                    :accountId,
                    :commandId,
                    :deviceId,
                    :createdAtMs
                )
                ON CONFLICT DO NOTHING
            """.trimIndent(),
            params,
        )
    }

    override fun unmarkLoaded(
        accountId: Long,
        commandId: String,
        deviceId: String,
    ) {
        val params = MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("commandId", commandId)
            .addValue("deviceId", deviceId)
        jdbc.update(
            """
                DELETE FROM public.playback_pending_play_loaded_device
                WHERE account_id = :accountId
                  AND command_id = :commandId
                  AND device_id = :deviceId
            """.trimIndent(),
            params,
        )
    }
}
