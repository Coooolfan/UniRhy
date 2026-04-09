package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.model.DeviceRuntimeState
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import java.util.concurrent.ConcurrentHashMap

@Service
class DeviceRuntimeService(
    private val lockManager: PlaybackAccountScope,
    private val timeProvider: PlaybackSyncTimeProvider,
    private val runtimeStore: PlaybackRuntimeStore = InMemoryPlaybackRuntimeStore(),
    @Value("\${unirhy.sync.node-id:\${HOSTNAME:\${random.uuid}}}")
    private val nodeId: String = "test-node",
) {
    private val localContextsBySessionId = ConcurrentHashMap<String, PlaybackConnectionContext>()

    fun registerConnection(
        accountId: Long,
        tokenValue: String,
        sessionId: String,
        session: ConcurrentWebSocketSessionDecorator,
    ): PlaybackConnectionContext {
        return lockManager.withAccountLock(accountId) {
            val nowMs = timeProvider.nowMs()
            val context = PlaybackConnectionContext(
                accountId = accountId,
                tokenValue = tokenValue,
                sessionId = sessionId,
                session = session,
            )
            localContextsBySessionId[sessionId] = context
            runtimeStore.upsertConnection(
                StoredPlaybackConnection(
                    sessionId = sessionId,
                    accountId = accountId,
                    deviceId = null,
                    nodeId = nodeId,
                    clientVersion = null,
                    helloCompleted = false,
                    createdAtMs = nowMs,
                    updatedAtMs = nowMs,
                ),
            )
            context
        }
    }

    fun findConnectionContext(sessionId: String): PlaybackConnectionContext? = localContextsBySessionId[sessionId]

    fun registerHello(
        accountId: Long,
        sessionId: String,
        deviceId: String,
        clientVersion: String?,
    ): DeviceRegistrationResult {
        return lockManager.withAccountLock(accountId) {
            val nowMs = timeProvider.nowMs()
            val context = requireNotNull(localContextsBySessionId[sessionId]) {
                "Playback connection context not found for sessionId=$sessionId"
            }

            val previousSessionId = runtimeStore.findCurrentSessionId(accountId, deviceId)
            val replacedContext = if (previousSessionId != null && previousSessionId != sessionId) {
                removeConnectionLocked(accountId, previousSessionId)
            } else {
                null
            }

            context.deviceId = deviceId
            context.clientVersion = clientVersion
            context.helloCompleted = true

            runtimeStore.upsertConnection(
                StoredPlaybackConnection(
                    sessionId = sessionId,
                    accountId = accountId,
                    deviceId = deviceId,
                    nodeId = nodeId,
                    clientVersion = clientVersion,
                    helloCompleted = true,
                    createdAtMs = nowMs,
                    updatedAtMs = nowMs,
                ),
            )
            runtimeStore.upsertRuntimeState(
                state = DeviceRuntimeState(
                    deviceId = deviceId,
                    accountId = accountId,
                    lastSeenAtMs = nowMs,
                ),
                sessionId = sessionId,
                nodeId = nodeId,
            )

            DeviceRegistrationResult(
                context = context,
                replacedContext = replacedContext,
                deviceIds = runtimeStore.listHelloCompletedDeviceIds(accountId),
            )
        }
    }

    fun removeSession(sessionId: String): SessionRemovalResult? {
        val accountId = localContextsBySessionId[sessionId]?.accountId
            ?: runtimeStore.findConnection(sessionId)?.accountId
            ?: return null
        return lockManager.withAccountLock(accountId) {
            val removedContext = removeConnectionLocked(accountId, sessionId) ?: return@withAccountLock null
            SessionRemovalResult(
                context = removedContext,
                remainingDeviceIds = runtimeStore.listHelloCompletedDeviceIds(accountId),
                deviceListChanged = removedContext.deviceId != null,
            )
        }
    }

    fun listHelloCompletedConnections(accountId: Long): List<PlaybackConnectionContext> {
        return lockManager.withAccountLock(accountId) {
            runtimeStore.listHelloCompletedConnections(accountId, nodeId)
                .mapNotNull { connection -> localContextsBySessionId[connection.sessionId] }
                .sortedBy { it.deviceId.orEmpty() }
        }
    }

    fun getActiveRuntimeSnapshot(accountId: Long): PlaybackAccountRuntimeSnapshot {
        return lockManager.withAccountLock(accountId) {
            val runtimeStates = runtimeStore.listActiveRuntimeStates(accountId)
            PlaybackAccountRuntimeSnapshot(
                deviceIds = runtimeStore.listHelloCompletedDeviceIds(accountId),
                runtimeStates = runtimeStates.sortedBy(DeviceRuntimeState::deviceId),
            )
        }
    }

    fun listDeviceIds(accountId: Long): List<String> {
        return lockManager.withAccountLock(accountId) {
            runtimeStore.listHelloCompletedDeviceIds(accountId)
        }
    }

    fun listActiveRuntimeStates(accountId: Long): List<DeviceRuntimeState> {
        return getActiveRuntimeSnapshot(accountId).runtimeStates
    }

    fun recordNtpResponse(
        accountId: Long,
        deviceId: String,
        clientRttMs: Double?,
        nowMs: Long,
    ): DeviceRuntimeState {
        return lockManager.withAccountLock(accountId) {
            val currentSessionId = runtimeStore.findCurrentSessionId(accountId, deviceId)
                ?: error("Playback runtime session not found for accountId=$accountId, deviceId=$deviceId")
            val state = requireNotNull(runtimeStore.findRuntimeState(accountId, deviceId)) {
                "Playback runtime state not found for accountId=$accountId, deviceId=$deviceId"
            }
            state.lastSeenAtMs = nowMs
            state.lastNtpResponseAtMs = nowMs
            if (clientRttMs != null) {
                state.rttEmaMs = if (state.rttEmaMs == 0.0) {
                    clientRttMs
                } else {
                    state.rttEmaMs * (1 - EMA_ALPHA) + clientRttMs * EMA_ALPHA
                }
            }
            runtimeStore.upsertRuntimeState(state, currentSessionId, nodeId)
            state
        }
    }

    fun touchDevice(
        accountId: Long,
        deviceId: String,
        nowMs: Long,
    ) {
        lockManager.withAccountLock(accountId) {
            runtimeStore.touchRuntimeState(accountId, deviceId, nowMs)
        }
    }

    fun isSyncReady(
        accountId: Long,
        deviceId: String,
    ): Boolean {
        return lockManager.withAccountLock(accountId) {
            runtimeStore.findRuntimeState(accountId, deviceId)
                ?.lastNtpResponseAtMs
                ?.let { it > 0L }
                ?: false
        }
    }

    fun cleanupStaleConnections(
        nowMs: Long,
        staleThresholdMs: Long,
    ): List<SessionRemovalResult> {
        return runtimeStore.listStaleConnections(nowMs, staleThresholdMs).mapNotNull { connection ->
            lockManager.withAccountLock(connection.accountId) {
                val latest = runtimeStore.findConnection(connection.sessionId) ?: return@withAccountLock null
                val deviceId = latest.deviceId ?: return@withAccountLock null
                val runtimeState = runtimeStore.findRuntimeState(latest.accountId, deviceId) ?: return@withAccountLock null
                if (runtimeState.lastNtpResponseAtMs == 0L || nowMs - runtimeState.lastNtpResponseAtMs <= staleThresholdMs) {
                    return@withAccountLock null
                }
                val removedContext = removeConnectionLocked(latest.accountId, latest.sessionId) ?: return@withAccountLock null
                SessionRemovalResult(
                    context = removedContext,
                    remainingDeviceIds = runtimeStore.listHelloCompletedDeviceIds(latest.accountId),
                    deviceListChanged = removedContext.deviceId != null,
                )
            }
        }
    }

    fun hasConnection(sessionId: String): Boolean = localContextsBySessionId.containsKey(sessionId)

    fun isCurrentDeviceSession(
        accountId: Long,
        deviceId: String,
        sessionId: String,
    ): Boolean {
        return lockManager.withAccountLock(accountId) {
            runtimeStore.isCurrentDeviceSession(accountId, deviceId, sessionId)
        }
    }

    private fun removeConnectionLocked(
        accountId: Long,
        sessionId: String,
    ): PlaybackConnectionContext? {
        val removedStoreConnection = runtimeStore.removeConnection(sessionId) ?: return null
        val localContext = localContextsBySessionId.remove(sessionId)
        return localContext ?: PlaybackConnectionContext(
            accountId = accountId,
            tokenValue = "",
            sessionId = removedStoreConnection.sessionId,
            session = null,
            deviceId = removedStoreConnection.deviceId,
            clientVersion = removedStoreConnection.clientVersion,
            helloCompleted = removedStoreConnection.helloCompleted,
        )
    }
}

class PlaybackConnectionContext(
    val accountId: Long,
    val tokenValue: String,
    val sessionId: String,
    val session: ConcurrentWebSocketSessionDecorator?,
    var deviceId: String? = null,
    var clientVersion: String? = null,
    var helloCompleted: Boolean = false,
)

data class DeviceRegistrationResult(
    val context: PlaybackConnectionContext,
    val replacedContext: PlaybackConnectionContext?,
    val deviceIds: List<String>,
)

data class PlaybackAccountRuntimeSnapshot(
    val deviceIds: List<String>,
    val runtimeStates: List<DeviceRuntimeState>,
)

data class SessionRemovalResult(
    val context: PlaybackConnectionContext,
    val remainingDeviceIds: List<String>,
    val deviceListChanged: Boolean,
)

private const val EMA_ALPHA = 0.2
