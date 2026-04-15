package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.model.AccountPlaybackState
import com.coooolfan.unirhy.sync.model.PendingPlayState
import com.coooolfan.unirhy.sync.protocol.PlaybackStatus
import com.coooolfan.unirhy.sync.protocol.ScheduledActionPayload
import com.coooolfan.unirhy.sync.protocol.ScheduledActionType
import com.coooolfan.unirhy.sync.protocol.ScheduledPlaybackAction
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class PlaybackSessionService(
    private val lockManager: PlaybackAccountLockManager,
    private val currentQueueService: CurrentQueueService,
) {
    private val pendingPlays = ConcurrentHashMap<Long, PendingPlayState>()

    fun getOrCreateState(accountId: Long): AccountPlaybackState {
        return currentQueueService.getPlaybackState(accountId)
    }

    fun createPendingPlay(
        accountId: Long,
        commandId: String,
        initiatorDeviceId: String?,
        currentIndex: Int,
        recordingId: Long,
        positionSeconds: Double,
        nowMs: Long,
        timeoutAtMs: Long,
    ): PendingPlayCreationResult {
        return lockManager.withAccountLock(accountId) {
            val pendingPlay = PendingPlayState(
                commandId = commandId,
                initiatorDeviceId = initiatorDeviceId,
                currentIndex = currentIndex,
                recordingId = recordingId,
                positionSeconds = positionSeconds,
                clientsLoaded = initiatorDeviceId?.let { mutableSetOf(it) } ?: mutableSetOf(),
                createdAtMs = nowMs,
                timeoutAtMs = timeoutAtMs,
            )
            val replaced = pendingPlays.put(accountId, pendingPlay) != null
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
        currentIndex: Int,
    ): PendingPlayState? {
        return lockManager.withAccountLock(accountId) {
            val pendingPlay = pendingPlays[accountId] ?: return@withAccountLock null
            if (
                pendingPlay.commandId != commandId ||
                pendingPlay.currentIndex != currentIndex
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
            commitPendingPlayLocked(accountId, pendingPlay, nowMs, executeAtMs)
        }
    }

    fun schedulePause(
        accountId: Long,
        commandId: String,
        currentIndex: Int?,
        positionSeconds: Double,
        nowMs: Long,
        executeAtMs: Long,
        expectedVersion: Long? = null,
    ): ScheduledActionPayload {
        val nextState = currentQueueService.pausePlayback(
            accountId = accountId,
            currentIndex = currentIndex,
            positionMs = positionSeconds.toPositionMs(),
            executeAtMs = executeAtMs,
            expectedVersion = expectedVersion,
            nowMs = nowMs,
        )
        return nextState.toScheduledActionPayload(
            commandId = commandId,
            action = ScheduledActionType.PAUSE,
        )
    }

    fun schedulePauseFromCurrentState(
        accountId: Long,
        commandId: String,
        nowMs: Long,
        executeAtMs: Long,
    ): ScheduledActionPayload {
        val currentState = getOrCreateState(accountId)
        val recoveredPositionSeconds = currentState.recoverPositionSeconds(nowMs)
        return schedulePause(
            accountId = accountId,
            commandId = commandId,
            currentIndex = currentState.currentIndex,
            positionSeconds = recoveredPositionSeconds,
            nowMs = nowMs,
            executeAtMs = executeAtMs,
            expectedVersion = currentState.version,
        )
    }

    fun scheduleSeek(
        accountId: Long,
        commandId: String,
        currentIndex: Int,
        positionSeconds: Double,
        nowMs: Long,
        executeAtMs: Long,
        expectedVersion: Long,
    ): ScheduledActionPayload {
        val nextState = currentQueueService.seekPlayback(
            accountId = accountId,
            currentIndex = currentIndex,
            positionMs = positionSeconds.toPositionMs(),
            executeAtMs = executeAtMs,
            expectedVersion = expectedVersion,
            nowMs = nowMs,
        )
        return nextState.toScheduledActionPayload(
            commandId = commandId,
            action = ScheduledActionType.SEEK,
        )
    }

    fun buildSyncAction(
        accountId: Long,
        commandId: String,
        nowMs: Long,
        executeAtMs: Long,
    ): ScheduledActionPayload {
        val currentState = currentQueueService.buildSyncPlaybackState(
            accountId = accountId,
            executeAtMs = executeAtMs,
            nowMs = nowMs,
        )
        val action = if (currentState.status == PlaybackStatus.PLAYING) {
            ScheduledActionType.PLAY
        } else {
            ScheduledActionType.PAUSE
        }
        return currentState.toScheduledActionPayload(
            commandId = commandId,
            action = action,
        )
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
            commitPendingPlayLocked(accountId, pendingPlay, nowMs, executeAtMs)
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
                currentIndex = currentIndex,
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

    private fun commitPendingPlayLocked(
        accountId: Long,
        pendingPlay: PendingPlayState,
        nowMs: Long,
        executeAtMs: Long,
    ): ScheduledActionPayload {
        val currentState = currentQueueService.beginPlayback(
            accountId = accountId,
            currentIndex = pendingPlay.currentIndex,
            positionMs = pendingPlay.positionSeconds.toPositionMs(),
            executeAtMs = executeAtMs,
            expectedVersion = currentQueueService.getQueueVersion(accountId),
            nowMs = nowMs,
        )
        return currentState.toScheduledActionPayload(
            commandId = pendingPlay.commandId,
            action = ScheduledActionType.PLAY,
        )
    }

    private fun Double.toPositionMs(): Long {
        return (this * 1_000.0).toLong()
    }
}

data class PendingPlayCreationResult(
    val pendingPlay: PendingPlayState,
    val replaced: Boolean,
)
