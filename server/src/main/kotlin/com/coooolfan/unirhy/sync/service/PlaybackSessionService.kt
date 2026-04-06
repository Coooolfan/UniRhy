package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.model.AccountPlaybackState
import com.coooolfan.unirhy.sync.model.PendingPlayState
import com.coooolfan.unirhy.sync.protocol.ScheduledActionPayload
import com.coooolfan.unirhy.sync.protocol.ScheduledActionType
import com.coooolfan.unirhy.sync.protocol.ScheduledPlaybackAction
import com.coooolfan.unirhy.sync.protocol.PlaybackStatus
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class PlaybackSessionService(
    private val lockManager: PlaybackAccountLockManager,
    private val timeProvider: PlaybackSyncTimeProvider,
) {
    private val states = ConcurrentHashMap<Long, AccountPlaybackState>()
    private val pendingPlays = ConcurrentHashMap<Long, PendingPlayState>()

    fun getOrCreateState(accountId: Long): AccountPlaybackState {
        return lockManager.withAccountLock(accountId) {
            getOrCreateStateLocked(accountId)
        }
    }

    fun createPendingPlay(
        accountId: Long,
        commandId: String,
        initiatorDeviceId: String?,
        recordingId: Long,
        positionSeconds: Double,
        nowMs: Long,
        timeoutAtMs: Long,
    ): PendingPlayCreationResult {
        return lockManager.withAccountLock(accountId) {
            val pendingPlay = PendingPlayState(
                commandId = commandId,
                initiatorDeviceId = initiatorDeviceId,
                recordingId = recordingId,
                positionSeconds = positionSeconds,
                clientsLoaded = initiatorDeviceId?.let { mutableSetOf(it) } ?: mutableSetOf(),
                createdAtMs = nowMs,
                timeoutAtMs = timeoutAtMs,
            )
            val replaced = pendingPlays.put(
                accountId,
                pendingPlay,
            ) != null
            PendingPlayCreationResult(
                pendingPlay = pendingPlay,
                replaced = replaced,
            )
        }
    }

    fun clearPendingPlay(accountId: Long): PendingPlayState? {
        return lockManager.withAccountLock(accountId) {
            pendingPlays.remove(accountId)
        }
    }

    fun getPendingPlay(accountId: Long): PendingPlayState? {
        return lockManager.withAccountLock(accountId) {
            pendingPlays[accountId]
        }
    }

    fun markAudioSourceLoaded(
        accountId: Long,
        commandId: String,
        deviceId: String,
        recordingId: Long,
    ): PendingPlayState? {
        return lockManager.withAccountLock(accountId) {
            val pendingPlay = pendingPlays[accountId] ?: return@withAccountLock null
            if (
                pendingPlay.commandId != commandId ||
                pendingPlay.recordingId != recordingId
            ) {
                return@withAccountLock null
            }
            pendingPlay.clientsLoaded += deviceId
            pendingPlay
        }
    }

    fun areAllDevicesLoaded(
        accountId: Long,
        deviceIds: Collection<String>,
    ): Boolean {
        return lockManager.withAccountLock(accountId) {
            val pendingPlay = pendingPlays[accountId] ?: return@withAccountLock false
            deviceIds.isNotEmpty() && deviceIds.all { it in pendingPlay.clientsLoaded }
        }
    }

    fun completePendingPlay(
        accountId: Long,
        commandId: String,
        nowMs: Long,
        executeAtMs: Long,
    ): ScheduledActionPayload? {
        return lockManager.withAccountLock(accountId) {
            val pendingPlay = pendingPlays[accountId] ?: return@withAccountLock null
            if (pendingPlay.commandId != commandId) {
                return@withAccountLock null
            }
            pendingPlays.remove(accountId)
            commitPendingPlayLocked(
                accountId = accountId,
                pendingPlay = pendingPlay,
                nowMs = nowMs,
                executeAtMs = executeAtMs,
            )
        }
    }

    fun schedulePause(
        accountId: Long,
        commandId: String,
        recordingId: Long?,
        positionSeconds: Double,
        nowMs: Long,
        executeAtMs: Long,
    ): ScheduledActionPayload {
        return lockManager.withAccountLock(accountId) {
            updateAndSchedule(
                currentState = getOrCreateStateLocked(accountId),
                accountId = accountId,
                commandId = commandId,
                action = ScheduledActionType.PAUSE,
                statusOverride = PlaybackStatus.PAUSED,
                recordingId = recordingId,
                positionSeconds = positionSeconds,
                nowMs = nowMs,
                executeAtMs = executeAtMs,
            )
        }
    }

    fun schedulePauseFromCurrentState(
        accountId: Long,
        commandId: String,
        nowMs: Long,
        executeAtMs: Long,
    ): ScheduledActionPayload {
        return lockManager.withAccountLock(accountId) {
            val currentState = getOrCreateStateLocked(accountId)
            updateAndSchedule(
                currentState = currentState,
                accountId = accountId,
                commandId = commandId,
                action = ScheduledActionType.PAUSE,
                statusOverride = PlaybackStatus.PAUSED,
                recordingId = currentState.recordingId,
                positionSeconds = currentState.recoverPositionSeconds(nowMs),
                nowMs = nowMs,
                executeAtMs = executeAtMs,
            )
        }
    }

    fun scheduleSeek(
        accountId: Long,
        commandId: String,
        recordingId: Long,
        positionSeconds: Double,
        nowMs: Long,
        executeAtMs: Long,
    ): ScheduledActionPayload {
        return lockManager.withAccountLock(accountId) {
            updateAndSchedule(
                currentState = getOrCreateStateLocked(accountId),
                accountId = accountId,
                commandId = commandId,
                action = ScheduledActionType.SEEK,
                statusOverride = null,
                recordingId = recordingId,
                positionSeconds = positionSeconds,
                nowMs = nowMs,
                executeAtMs = executeAtMs,
            )
        }
    }

    fun buildSyncAction(
        accountId: Long,
        commandId: String,
        nowMs: Long,
        executeAtMs: Long,
    ): ScheduledActionPayload {
        return lockManager.withAccountLock(accountId) {
            val currentState = getOrCreateStateLocked(accountId)
            val action = if (currentState.status == PlaybackStatus.PLAYING) {
                ScheduledActionType.PLAY
            } else {
                ScheduledActionType.PAUSE
            }
            val syncedState = if (currentState.status == PlaybackStatus.PLAYING) {
                currentState.copy(
                    positionSeconds = currentState.recoverPositionSeconds(nowMs),
                    serverTimeToExecuteMs = executeAtMs,
                )
            } else {
                currentState.copy(serverTimeToExecuteMs = executeAtMs)
            }
            syncedState.toScheduledActionPayload(
                commandId = commandId,
                action = action,
            )
        }
    }

    fun handleDeviceDisconnected(
        accountId: Long,
        deviceId: String,
        remainingDeviceIds: Collection<String>,
        nowMs: Long,
        executeAtMs: Long,
    ): ScheduledActionPayload? {
        return lockManager.withAccountLock(accountId) {
            val pendingPlay = pendingPlays[accountId] ?: return@withAccountLock null
            pendingPlay.clientsLoaded.remove(deviceId)
            if (remainingDeviceIds.isEmpty()) {
                pendingPlays.remove(accountId)
                return@withAccountLock null
            }
            if (!remainingDeviceIds.all { it in pendingPlay.clientsLoaded }) {
                return@withAccountLock null
            }
            pendingPlays.remove(accountId)
            commitPendingPlayLocked(
                accountId = accountId,
                pendingPlay = pendingPlay,
                nowMs = nowMs,
                executeAtMs = executeAtMs,
            )
        }
    }

    private fun AccountPlaybackState.toScheduledActionPayload(
        commandId: String,
        action: ScheduledActionType,
    ): ScheduledActionPayload {
        return ScheduledActionPayload(
            commandId = commandId,
            serverTimeToExecuteMs = serverTimeToExecuteMs,
            scheduledAction = ScheduledPlaybackAction(
                action = action,
                status = status,
                recordingId = recordingId,
                positionSeconds = positionSeconds,
                version = version,
            ),
        )
    }

    private fun AccountPlaybackState.recoverPositionSeconds(nowMs: Long): Double {
        if (status != PlaybackStatus.PLAYING) {
            return positionSeconds
        }
        return positionSeconds + (nowMs - serverTimeToExecuteMs).coerceAtLeast(0L) / 1_000.0
    }

    private fun updateAndSchedule(
        currentState: AccountPlaybackState,
        accountId: Long,
        commandId: String,
        action: ScheduledActionType,
        statusOverride: PlaybackStatus?,
        recordingId: Long?,
        positionSeconds: Double,
        nowMs: Long,
        executeAtMs: Long,
    ): ScheduledActionPayload {
        val nextState = currentState.copy(
            status = statusOverride ?: currentState.status,
            recordingId = recordingId,
            positionSeconds = positionSeconds,
            serverTimeToExecuteMs = executeAtMs,
            version = currentState.version + 1,
            updatedAtMs = nowMs,
        )
        states[accountId] = nextState
        return nextState.toScheduledActionPayload(
            commandId = commandId,
            action = action,
        )
    }

    private fun commitPendingPlayLocked(
        accountId: Long,
        pendingPlay: PendingPlayState,
        nowMs: Long,
        executeAtMs: Long,
    ): ScheduledActionPayload {
        val currentState = getOrCreateStateLocked(accountId)
        val nextState = currentState.copy(
            status = PlaybackStatus.PLAYING,
            recordingId = pendingPlay.recordingId,
            positionSeconds = pendingPlay.positionSeconds,
            serverTimeToExecuteMs = executeAtMs,
            version = currentState.version + 1,
            updatedAtMs = nowMs,
        )
        states[accountId] = nextState
        return nextState.toScheduledActionPayload(
            commandId = pendingPlay.commandId,
            action = ScheduledActionType.PLAY,
        )
    }

    private fun getOrCreateStateLocked(accountId: Long): AccountPlaybackState {
        return states.computeIfAbsent(accountId) {
            AccountPlaybackState.initial(
                accountId = accountId,
                nowMs = timeProvider.nowMs(),
            )
        }
    }
}

data class PendingPlayCreationResult(
    val pendingPlay: PendingPlayState,
    val replaced: Boolean,
)
