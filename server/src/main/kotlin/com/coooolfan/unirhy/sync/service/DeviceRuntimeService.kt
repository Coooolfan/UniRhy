package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.model.DeviceRuntimeState
import org.springframework.stereotype.Service
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import java.util.concurrent.ConcurrentHashMap

@Service
class DeviceRuntimeService(
    private val lockManager: PlaybackAccountLockManager,
    private val timeProvider: PlaybackSyncTimeProvider,
) {
    private val sessionIdsByAccountId = ConcurrentHashMap<Long, MutableSet<String>>()
    private val connectionContextsBySessionId = ConcurrentHashMap<String, PlaybackConnectionContext>()
    private val sessionIdByDeviceKey = ConcurrentHashMap<PlaybackDeviceKey, String>()
    private val runtimeStateByDeviceKey = ConcurrentHashMap<PlaybackDeviceKey, DeviceRuntimeState>()

    fun registerConnection(
        accountId: Long,
        tokenValue: String,
        sessionId: String,
        session: ConcurrentWebSocketSessionDecorator,
    ): PlaybackConnectionContext {
        return lockManager.withAccountLock(accountId) {
            val context = PlaybackConnectionContext(
                accountId = accountId,
                tokenValue = tokenValue,
                sessionId = sessionId,
                session = session,
            )
            connectionContextsBySessionId[sessionId] = context
            sessionIdsByAccountId.computeIfAbsent(accountId) { ConcurrentHashMap.newKeySet() }.add(sessionId)
            context
        }
    }

    fun findConnectionContext(sessionId: String): PlaybackConnectionContext? = connectionContextsBySessionId[sessionId]

    fun registerHello(
        accountId: Long,
        sessionId: String,
        deviceId: String,
        clientVersion: String?,
    ): DeviceRegistrationResult {
        return lockManager.withAccountLock(accountId) {
            val context = requireNotNull(connectionContextsBySessionId[sessionId]) {
                "Playback connection context not found for sessionId=$sessionId"
            }

            val deviceKey = PlaybackDeviceKey(accountId = accountId, deviceId = deviceId)
            val previousSessionId = sessionIdByDeviceKey.put(deviceKey, sessionId)
            val replacedContext = if (previousSessionId != null && previousSessionId != sessionId) {
                removeConnectionLocked(accountId, previousSessionId)
            } else {
                null
            }

            context.deviceId = deviceId
            context.clientVersion = clientVersion
            context.helloCompleted = true

            runtimeStateByDeviceKey[deviceKey] = DeviceRuntimeState(
                deviceId = deviceId,
                accountId = accountId,
                lastSeenAtMs = timeProvider.nowMs(),
            )

            DeviceRegistrationResult(
                context = context,
                replacedContext = replacedContext,
                deviceIds = listDeviceIdsLocked(accountId),
            )
        }
    }

    fun removeSession(sessionId: String): SessionRemovalResult? {
        val context = connectionContextsBySessionId[sessionId] ?: return null
        return lockManager.withAccountLock(context.accountId) {
            val removedContext = removeConnectionLocked(context.accountId, sessionId) ?: return@withAccountLock null
            SessionRemovalResult(
                context = removedContext,
                remainingDeviceIds = listDeviceIdsLocked(removedContext.accountId),
                deviceListChanged = removedContext.deviceId != null,
            )
        }
    }

    fun listHelloCompletedConnections(accountId: Long): List<PlaybackConnectionContext> {
        return lockManager.withAccountLock(accountId) {
            listHelloCompletedContextsLocked(accountId)
        }
    }

    fun getActiveRuntimeSnapshot(accountId: Long): PlaybackAccountRuntimeSnapshot {
        return lockManager.withAccountLock(accountId) {
            buildActiveRuntimeSnapshotLocked(accountId)
        }
    }

    fun listDeviceIds(accountId: Long): List<String> {
        return lockManager.withAccountLock(accountId) {
            listDeviceIdsLocked(accountId)
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
            val deviceKey = PlaybackDeviceKey(accountId = accountId, deviceId = deviceId)
            val state = requireNotNull(runtimeStateByDeviceKey[deviceKey]) {
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
            state
        }
    }

    fun touchDevice(
        accountId: Long,
        deviceId: String,
        nowMs: Long,
    ) {
        lockManager.withAccountLock(accountId) {
            runtimeStateByDeviceKey[PlaybackDeviceKey(accountId = accountId, deviceId = deviceId)]
                ?.lastSeenAtMs = nowMs
        }
    }

    fun isSyncReady(
        accountId: Long,
        deviceId: String,
    ): Boolean {
        return lockManager.withAccountLock(accountId) {
            runtimeStateByDeviceKey[PlaybackDeviceKey(accountId = accountId, deviceId = deviceId)]
                ?.lastNtpResponseAtMs
                ?.let { it > 0L }
                ?: false
        }
    }

    fun cleanupStaleConnections(
        nowMs: Long,
        staleThresholdMs: Long,
    ): List<SessionRemovalResult> {
        return sessionIdsByAccountId.keys.toList()
            .flatMap { accountId ->
                lockManager.withAccountLock(accountId) {
                    val sessionIds = sessionIdsByAccountId[accountId].orEmpty().toList()
                    sessionIds.mapNotNull { sessionId ->
                        val context = connectionContextsBySessionId[sessionId] ?: return@mapNotNull null
                        val deviceId = context.deviceId ?: return@mapNotNull null
                        val runtimeState = runtimeStateByDeviceKey[PlaybackDeviceKey(accountId = accountId, deviceId = deviceId)]
                            ?: return@mapNotNull null
                        if (runtimeState.lastNtpResponseAtMs == 0L) {
                            return@mapNotNull null
                        }
                        if (nowMs - runtimeState.lastNtpResponseAtMs <= staleThresholdMs) {
                            return@mapNotNull null
                        }

                        val removedContext = removeConnectionLocked(accountId, sessionId) ?: return@mapNotNull null
                        SessionRemovalResult(
                            context = removedContext,
                            remainingDeviceIds = listDeviceIdsLocked(accountId),
                            deviceListChanged = removedContext.deviceId != null,
                        )
                    }
                }
            }
    }

    fun hasConnection(sessionId: String): Boolean = connectionContextsBySessionId.containsKey(sessionId)

    private fun listDeviceIdsLocked(accountId: Long): List<String> {
        return sessionIdsByAccountId[accountId]
            .orEmpty()
            .mapNotNull(connectionContextsBySessionId::get)
            .mapNotNull { it.deviceId }
            .sorted()
    }

    private fun listHelloCompletedContextsLocked(accountId: Long): List<PlaybackConnectionContext> {
        return sessionIdsByAccountId[accountId]
            .orEmpty()
            .mapNotNull(connectionContextsBySessionId::get)
            .filter { it.helloCompleted }
            .sortedBy { it.deviceId.orEmpty() }
    }

    private fun buildActiveRuntimeSnapshotLocked(accountId: Long): PlaybackAccountRuntimeSnapshot {
        val contexts = listHelloCompletedContextsLocked(accountId)
        return PlaybackAccountRuntimeSnapshot(
            deviceIds = contexts.mapNotNull { it.deviceId },
            runtimeStates = contexts.mapNotNull { context ->
                context.deviceId?.let { deviceId ->
                    runtimeStateByDeviceKey[PlaybackDeviceKey(accountId = accountId, deviceId = deviceId)]
                }
            },
        )
    }

    private fun removeConnectionLocked(
        accountId: Long,
        sessionId: String,
    ): PlaybackConnectionContext? {
        val context = connectionContextsBySessionId.remove(sessionId) ?: return null

        sessionIdsByAccountId[accountId]?.remove(sessionId)
        if (sessionIdsByAccountId[accountId].isNullOrEmpty()) {
            sessionIdsByAccountId.remove(accountId)
        }

        context.deviceId?.let { deviceId ->
            val deviceKey = PlaybackDeviceKey(accountId = accountId, deviceId = deviceId)
            sessionIdByDeviceKey.remove(deviceKey, sessionId)
            runtimeStateByDeviceKey.remove(deviceKey)
        }

        return context
    }
}

class PlaybackConnectionContext(
    val accountId: Long,
    val tokenValue: String,
    val sessionId: String,
    val session: ConcurrentWebSocketSessionDecorator,
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

private data class PlaybackDeviceKey(
    val accountId: Long,
    val deviceId: String,
)

private const val EMA_ALPHA = 0.2
