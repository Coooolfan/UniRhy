package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.model.DeviceRuntimeState
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service

data class StoredPlaybackConnection(
    val sessionId: String,
    val accountId: Long,
    val deviceId: String?,
    val nodeId: String,
    val clientVersion: String?,
    val helloCompleted: Boolean,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

interface PlaybackRuntimeStore {
    fun upsertConnection(connection: StoredPlaybackConnection)

    fun findConnection(sessionId: String): StoredPlaybackConnection?

    fun removeConnection(sessionId: String): StoredPlaybackConnection?

    fun listHelloCompletedConnections(
        accountId: Long,
        nodeId: String,
    ): List<StoredPlaybackConnection>

    fun listHelloCompletedDeviceIds(accountId: Long): List<String>

    fun findCurrentSessionId(
        accountId: Long,
        deviceId: String,
    ): String?

    fun listActiveRuntimeStates(accountId: Long): List<DeviceRuntimeState>

    fun upsertRuntimeState(state: DeviceRuntimeState, sessionId: String, nodeId: String)

    fun findRuntimeState(
        accountId: Long,
        deviceId: String,
    ): DeviceRuntimeState?

    fun touchRuntimeState(
        accountId: Long,
        deviceId: String,
        nowMs: Long,
    )

    fun removeRuntimeState(
        accountId: Long,
        deviceId: String,
    )

    fun listStaleConnections(
        nowMs: Long,
        staleThresholdMs: Long,
    ): List<StoredPlaybackConnection>

    fun isCurrentDeviceSession(
        accountId: Long,
        deviceId: String,
        sessionId: String,
    ): Boolean
}

class InMemoryPlaybackRuntimeStore : PlaybackRuntimeStore {
    private val connections = linkedMapOf<String, StoredPlaybackConnection>()
    private val runtimeStates = linkedMapOf<Pair<Long, String>, Pair<DeviceRuntimeState, String>>()

    override fun upsertConnection(connection: StoredPlaybackConnection) {
        connections[connection.sessionId] = connection
    }

    override fun findConnection(sessionId: String): StoredPlaybackConnection? = connections[sessionId]

    override fun removeConnection(sessionId: String): StoredPlaybackConnection? {
        val removed = connections.remove(sessionId) ?: return null
        removed.deviceId?.let { runtimeStates.remove(removed.accountId to it) }
        return removed
    }

    override fun listHelloCompletedConnections(
        accountId: Long,
        nodeId: String,
    ): List<StoredPlaybackConnection> {
        return connections.values
            .filter { it.accountId == accountId && it.nodeId == nodeId && it.helloCompleted }
            .sortedBy { it.deviceId.orEmpty() }
    }

    override fun listHelloCompletedDeviceIds(accountId: Long): List<String> {
        return connections.values
            .filter { it.accountId == accountId && it.helloCompleted }
            .mapNotNull { it.deviceId }
            .sorted()
    }

    override fun findCurrentSessionId(
        accountId: Long,
        deviceId: String,
    ): String? {
        return runtimeStates[accountId to deviceId]?.second
    }

    override fun listActiveRuntimeStates(accountId: Long): List<DeviceRuntimeState> {
        return runtimeStates.values
            .map { it.first }
            .filter { it.accountId == accountId }
            .sortedBy(DeviceRuntimeState::deviceId)
    }

    override fun upsertRuntimeState(
        state: DeviceRuntimeState,
        sessionId: String,
        nodeId: String,
    ) {
        runtimeStates[state.accountId to state.deviceId] = state.deepCopy() to sessionId
        val existing = connections[sessionId]
        if (existing != null) {
            connections[sessionId] = existing.copy(
                deviceId = state.deviceId,
                nodeId = nodeId,
                helloCompleted = true,
                updatedAtMs = state.lastSeenAtMs,
            )
        }
    }

    override fun findRuntimeState(
        accountId: Long,
        deviceId: String,
    ): DeviceRuntimeState? {
        return runtimeStates[accountId to deviceId]?.first?.deepCopy()
    }

    override fun touchRuntimeState(
        accountId: Long,
        deviceId: String,
        nowMs: Long,
    ) {
        val key = accountId to deviceId
        val existing = runtimeStates[key] ?: return
        runtimeStates[key] = existing.first.deepCopy().apply {
            lastSeenAtMs = nowMs
        } to existing.second
    }

    override fun removeRuntimeState(
        accountId: Long,
        deviceId: String,
    ) {
        runtimeStates.remove(accountId to deviceId)
    }

    override fun listStaleConnections(
        nowMs: Long,
        staleThresholdMs: Long,
    ): List<StoredPlaybackConnection> {
        return runtimeStates.values.mapNotNull { (state, sessionId) ->
            if (state.lastNtpResponseAtMs == 0L) {
                return@mapNotNull null
            }
            if (nowMs - state.lastNtpResponseAtMs <= staleThresholdMs) {
                return@mapNotNull null
            }
            connections[sessionId]
        }
    }

    override fun isCurrentDeviceSession(
        accountId: Long,
        deviceId: String,
        sessionId: String,
    ): Boolean {
        return runtimeStates[accountId to deviceId]?.second == sessionId
    }

    private fun DeviceRuntimeState.deepCopy(): DeviceRuntimeState {
        return DeviceRuntimeState(
            deviceId = deviceId,
            accountId = accountId,
            rttEmaMs = rttEmaMs,
            lastNtpResponseAtMs = lastNtpResponseAtMs,
            lastSeenAtMs = lastSeenAtMs,
        )
    }
}

@Service
class JdbcPlaybackRuntimeStore(
    private val jdbc: NamedParameterJdbcTemplate,
) : PlaybackRuntimeStore {
    override fun upsertConnection(connection: StoredPlaybackConnection) {
        val params = MapSqlParameterSource()
            .addValue("sessionId", connection.sessionId)
            .addValue("accountId", connection.accountId)
            .addValue("deviceId", connection.deviceId)
            .addValue("nodeId", connection.nodeId)
            .addValue("clientVersion", connection.clientVersion)
            .addValue("helloCompleted", connection.helloCompleted)
            .addValue("createdAtMs", connection.createdAtMs)
            .addValue("updatedAtMs", connection.updatedAtMs)
        jdbc.update(
            """
                INSERT INTO public.playback_connection (
                    session_id,
                    account_id,
                    device_id,
                    node_id,
                    client_version,
                    hello_completed,
                    created_at_ms,
                    updated_at_ms
                ) VALUES (
                    :sessionId,
                    :accountId,
                    :deviceId,
                    :nodeId,
                    :clientVersion,
                    :helloCompleted,
                    :createdAtMs,
                    :updatedAtMs
                )
                ON CONFLICT (session_id) DO UPDATE
                SET account_id = EXCLUDED.account_id,
                    device_id = EXCLUDED.device_id,
                    node_id = EXCLUDED.node_id,
                    client_version = EXCLUDED.client_version,
                    hello_completed = EXCLUDED.hello_completed,
                    updated_at_ms = EXCLUDED.updated_at_ms
            """.trimIndent(),
            params,
        )
    }

    override fun findConnection(sessionId: String): StoredPlaybackConnection? {
        val params = MapSqlParameterSource().addValue("sessionId", sessionId)
        return jdbc.query(
            """
                SELECT session_id,
                       account_id,
                       device_id,
                       node_id,
                       client_version,
                       hello_completed,
                       created_at_ms,
                       updated_at_ms
                FROM public.playback_connection
                WHERE session_id = :sessionId
            """.trimIndent(),
            params,
        ) { rs, _ ->
            StoredPlaybackConnection(
                sessionId = rs.getString("session_id"),
                accountId = rs.getLong("account_id"),
                deviceId = rs.getString("device_id"),
                nodeId = rs.getString("node_id"),
                clientVersion = rs.getString("client_version"),
                helloCompleted = rs.getBoolean("hello_completed"),
                createdAtMs = rs.getLong("created_at_ms"),
                updatedAtMs = rs.getLong("updated_at_ms"),
            )
        }.singleOrNull()
    }

    override fun removeConnection(sessionId: String): StoredPlaybackConnection? {
        val removed = findConnection(sessionId) ?: return null
        val params = MapSqlParameterSource().addValue("sessionId", sessionId)
        jdbc.update(
            "DELETE FROM public.playback_connection WHERE session_id = :sessionId",
            params,
        )
        return removed
    }

    override fun listHelloCompletedConnections(
        accountId: Long,
        nodeId: String,
    ): List<StoredPlaybackConnection> {
        val params = MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("nodeId", nodeId)
        return jdbc.query(
            """
                SELECT session_id,
                       account_id,
                       device_id,
                       node_id,
                       client_version,
                       hello_completed,
                       created_at_ms,
                       updated_at_ms
                FROM public.playback_connection
                WHERE account_id = :accountId
                  AND node_id = :nodeId
                  AND hello_completed = TRUE
                ORDER BY device_id
            """.trimIndent(),
            params,
        ) { rs, _ ->
            StoredPlaybackConnection(
                sessionId = rs.getString("session_id"),
                accountId = rs.getLong("account_id"),
                deviceId = rs.getString("device_id"),
                nodeId = rs.getString("node_id"),
                clientVersion = rs.getString("client_version"),
                helloCompleted = rs.getBoolean("hello_completed"),
                createdAtMs = rs.getLong("created_at_ms"),
                updatedAtMs = rs.getLong("updated_at_ms"),
            )
        }
    }

    override fun listHelloCompletedDeviceIds(accountId: Long): List<String> {
        val params = MapSqlParameterSource().addValue("accountId", accountId)
        return jdbc.query(
            """
                SELECT device_id
                FROM public.playback_connection
                WHERE account_id = :accountId
                  AND hello_completed = TRUE
                  AND device_id IS NOT NULL
                ORDER BY device_id
            """.trimIndent(),
            params,
        ) { rs, _ -> rs.getString("device_id") }
    }

    override fun findCurrentSessionId(
        accountId: Long,
        deviceId: String,
    ): String? {
        val params = MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("deviceId", deviceId)
        return jdbc.query(
            """
                SELECT session_id
                FROM public.playback_device_runtime
                WHERE account_id = :accountId
                  AND device_id = :deviceId
            """.trimIndent(),
            params,
        ) { rs, _ -> rs.getString("session_id") }.singleOrNull()
    }

    override fun listActiveRuntimeStates(accountId: Long): List<DeviceRuntimeState> {
        val params = MapSqlParameterSource().addValue("accountId", accountId)
        return jdbc.query(
            """
                SELECT device_id,
                       account_id,
                       rtt_ema_ms,
                       last_ntp_response_at_ms,
                       last_seen_at_ms
                FROM public.playback_device_runtime
                WHERE account_id = :accountId
                ORDER BY device_id
            """.trimIndent(),
            params,
        ) { rs, _ ->
            DeviceRuntimeState(
                deviceId = rs.getString("device_id"),
                accountId = rs.getLong("account_id"),
                rttEmaMs = rs.getDouble("rtt_ema_ms"),
                lastNtpResponseAtMs = rs.getLong("last_ntp_response_at_ms"),
                lastSeenAtMs = rs.getLong("last_seen_at_ms"),
            )
        }
    }

    override fun upsertRuntimeState(
        state: DeviceRuntimeState,
        sessionId: String,
        nodeId: String,
    ) {
        val params = MapSqlParameterSource()
            .addValue("accountId", state.accountId)
            .addValue("deviceId", state.deviceId)
            .addValue("sessionId", sessionId)
            .addValue("nodeId", nodeId)
            .addValue("rttEmaMs", state.rttEmaMs)
            .addValue("lastNtpResponseAtMs", state.lastNtpResponseAtMs)
            .addValue("lastSeenAtMs", state.lastSeenAtMs)
        jdbc.update(
            """
                INSERT INTO public.playback_device_runtime (
                    account_id,
                    device_id,
                    session_id,
                    node_id,
                    rtt_ema_ms,
                    last_ntp_response_at_ms,
                    last_seen_at_ms
                ) VALUES (
                    :accountId,
                    :deviceId,
                    :sessionId,
                    :nodeId,
                    :rttEmaMs,
                    :lastNtpResponseAtMs,
                    :lastSeenAtMs
                )
                ON CONFLICT (account_id, device_id) DO UPDATE
                SET session_id = EXCLUDED.session_id,
                    node_id = EXCLUDED.node_id,
                    rtt_ema_ms = EXCLUDED.rtt_ema_ms,
                    last_ntp_response_at_ms = EXCLUDED.last_ntp_response_at_ms,
                    last_seen_at_ms = EXCLUDED.last_seen_at_ms
            """.trimIndent(),
            params,
        )
    }

    override fun findRuntimeState(
        accountId: Long,
        deviceId: String,
    ): DeviceRuntimeState? {
        val params = MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("deviceId", deviceId)
        return jdbc.query(
            """
                SELECT device_id,
                       account_id,
                       rtt_ema_ms,
                       last_ntp_response_at_ms,
                       last_seen_at_ms
                FROM public.playback_device_runtime
                WHERE account_id = :accountId
                  AND device_id = :deviceId
            """.trimIndent(),
            params,
        ) { rs, _ ->
            DeviceRuntimeState(
                deviceId = rs.getString("device_id"),
                accountId = rs.getLong("account_id"),
                rttEmaMs = rs.getDouble("rtt_ema_ms"),
                lastNtpResponseAtMs = rs.getLong("last_ntp_response_at_ms"),
                lastSeenAtMs = rs.getLong("last_seen_at_ms"),
            )
        }.singleOrNull()
    }

    override fun touchRuntimeState(
        accountId: Long,
        deviceId: String,
        nowMs: Long,
    ) {
        val params = MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("deviceId", deviceId)
            .addValue("nowMs", nowMs)
        jdbc.update(
            """
                UPDATE public.playback_device_runtime
                SET last_seen_at_ms = :nowMs
                WHERE account_id = :accountId
                  AND device_id = :deviceId
            """.trimIndent(),
            params,
        )
    }

    override fun removeRuntimeState(
        accountId: Long,
        deviceId: String,
    ) {
        val params = MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("deviceId", deviceId)
        jdbc.update(
            """
                DELETE FROM public.playback_device_runtime
                WHERE account_id = :accountId
                  AND device_id = :deviceId
            """.trimIndent(),
            params,
        )
    }

    override fun listStaleConnections(
        nowMs: Long,
        staleThresholdMs: Long,
    ): List<StoredPlaybackConnection> {
        val params = MapSqlParameterSource()
            .addValue("nowMs", nowMs)
            .addValue("staleThresholdMs", staleThresholdMs)
        return jdbc.query(
            """
                SELECT connection.session_id,
                       connection.account_id,
                       connection.device_id,
                       connection.node_id,
                       connection.client_version,
                       connection.hello_completed,
                       connection.created_at_ms,
                       connection.updated_at_ms
                FROM public.playback_connection connection
                JOIN public.playback_device_runtime runtime
                  ON runtime.account_id = connection.account_id
                 AND runtime.device_id = connection.device_id
                WHERE connection.hello_completed = TRUE
                  AND runtime.last_ntp_response_at_ms > 0
                  AND :nowMs - runtime.last_ntp_response_at_ms > :staleThresholdMs
                ORDER BY runtime.last_ntp_response_at_ms
            """.trimIndent(),
            params,
        ) { rs, _ ->
            StoredPlaybackConnection(
                sessionId = rs.getString("session_id"),
                accountId = rs.getLong("account_id"),
                deviceId = rs.getString("device_id"),
                nodeId = rs.getString("node_id"),
                clientVersion = rs.getString("client_version"),
                helloCompleted = rs.getBoolean("hello_completed"),
                createdAtMs = rs.getLong("created_at_ms"),
                updatedAtMs = rs.getLong("updated_at_ms"),
            )
        }
    }

    override fun isCurrentDeviceSession(
        accountId: Long,
        deviceId: String,
        sessionId: String,
    ): Boolean {
        val params = MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("deviceId", deviceId)
            .addValue("sessionId", sessionId)
        return jdbc.query(
            """
                SELECT 1
                FROM public.playback_device_runtime
                WHERE account_id = :accountId
                  AND device_id = :deviceId
                  AND session_id = :sessionId
            """.trimIndent(),
            params,
        ) { _, _ -> true }.isNotEmpty()
    }
}
