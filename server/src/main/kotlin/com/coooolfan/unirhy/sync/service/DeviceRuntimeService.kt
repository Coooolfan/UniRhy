package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.model.DeviceRuntimeState
import org.springframework.stereotype.Service
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import java.util.concurrent.ConcurrentHashMap

@Service
class DeviceRuntimeService(
    private val lockManager: PlaybackAccountLockManager,
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
                lastSeenAtMs = System.currentTimeMillis(),
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
            sessionIdsByAccountId[accountId]
                .orEmpty()
                .mapNotNull(connectionContextsBySessionId::get)
                .filter { it.helloCompleted }
                .sortedBy { it.deviceId.orEmpty() }
        }
    }

    fun listDeviceIds(accountId: Long): List<String> {
        return lockManager.withAccountLock(accountId) {
            listDeviceIdsLocked(accountId)
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

data class SessionRemovalResult(
    val context: PlaybackConnectionContext,
    val remainingDeviceIds: List<String>,
    val deviceListChanged: Boolean,
)

private data class PlaybackDeviceKey(
    val accountId: Long,
    val deviceId: String,
)
