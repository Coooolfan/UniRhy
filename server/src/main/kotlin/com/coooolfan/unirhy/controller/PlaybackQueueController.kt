package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import cn.dev33.satoken.stp.StpUtil
import com.coooolfan.unirhy.model.dto.*
import com.coooolfan.unirhy.sync.protocol.CurrentQueueDto
import com.coooolfan.unirhy.sync.service.CurrentQueueService
import com.coooolfan.unirhy.sync.service.PlaybackQueueMutationCoordinator
import com.coooolfan.unirhy.sync.service.PlaybackSyncMessageSender
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@SaCheckLogin
@RestController
@RequestMapping("/api/playback/current-queue")
class PlaybackQueueController(
    private val currentQueueService: CurrentQueueService,
    private val queueMutationCoordinator: PlaybackQueueMutationCoordinator,
    private val messageSender: PlaybackSyncMessageSender,
) {
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    fun getCurrentQueue(): CurrentQueueDto {
        return currentQueueService.getQueue(currentAccountId())
    }

    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    fun replaceCurrentQueue(
        @RequestBody input: CurrentQueueReplaceRequest,
    ): CurrentQueueDto {
        val accountId = currentAccountId()
        val change = currentQueueService.replaceQueue(
            accountId = accountId,
            recordings = currentQueueService.resolvePlayableRecordings(input.recordingIds),
            currentIndex = input.currentIndex,
        )
        broadcastQueueIfChanged(accountId, change)
        queueMutationCoordinator.syncPausedPlaybackToCurrentQueue(
            accountId = accountId,
            currentEntry = change.currentEntry,
            nowMs = change.queue.updatedAtMs,
        )
        return change.queue
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.OK)
    fun appendToCurrentQueue(
        @RequestBody input: CurrentQueueAppendRequest,
    ): CurrentQueueDto {
        val accountId = currentAccountId()
        val change = currentQueueService.appendToQueue(
            accountId = accountId,
            recordings = currentQueueService.resolvePlayableRecordings(input.recordingIds),
        )
        broadcastQueueIfChanged(accountId, change)
        return change.queue
    }

    @PutMapping("/order")
    @ResponseStatus(HttpStatus.OK)
    fun reorderCurrentQueue(
        @RequestBody input: CurrentQueueReorderRequest,
    ): CurrentQueueDto {
        val accountId = currentAccountId()
        val change = currentQueueService.reorderQueue(
            accountId = accountId,
            entryIds = input.entryIds,
        )
        broadcastQueueIfChanged(accountId, change)
        return change.queue
    }

    @PutMapping("/current")
    @ResponseStatus(HttpStatus.OK)
    fun setCurrentEntry(
        @RequestBody input: CurrentQueueSetCurrentRequest,
    ): CurrentQueueDto {
        val accountId = currentAccountId()
        val change = currentQueueService.setCurrentEntry(
            accountId = accountId,
            entryId = input.entryId,
        )
        broadcastQueueIfChanged(accountId, change)
        queueMutationCoordinator.syncPausedPlaybackToCurrentQueue(
            accountId = accountId,
            currentEntry = change.currentEntry,
            nowMs = change.queue.updatedAtMs,
        )
        return change.queue
    }

    @PutMapping("/strategy")
    @ResponseStatus(HttpStatus.OK)
    fun updateCurrentQueueStrategy(
        @RequestBody input: CurrentQueueStrategyUpdateRequest,
    ): CurrentQueueDto {
        val accountId = currentAccountId()
        val change = currentQueueService.updateStrategies(
            accountId = accountId,
            playbackStrategy = input.playbackStrategy,
            stopStrategy = input.stopStrategy,
        )
        broadcastQueueIfChanged(accountId, change)
        return change.queue
    }

    @PostMapping("/next")
    @ResponseStatus(HttpStatus.OK)
    fun playNextInCurrentQueue(): CurrentQueueDto {
        val accountId = currentAccountId()
        val change = currentQueueService.navigateToNext(accountId)
        broadcastQueueIfChanged(accountId, change)
        queueMutationCoordinator.handleQueueNavigation(accountId, change, change.queue.updatedAtMs)
        return change.queue
    }

    @PostMapping("/previous")
    @ResponseStatus(HttpStatus.OK)
    fun playPreviousInCurrentQueue(): CurrentQueueDto {
        val accountId = currentAccountId()
        val change = currentQueueService.navigateToPrevious(accountId)
        broadcastQueueIfChanged(accountId, change)
        queueMutationCoordinator.handleQueueNavigation(accountId, change, change.queue.updatedAtMs)
        return change.queue
    }

    @DeleteMapping("/items/{entryId}")
    @ResponseStatus(HttpStatus.OK)
    fun removeCurrentQueueEntry(
        @PathVariable entryId: Long,
    ): CurrentQueueDto {
        val accountId = currentAccountId()
        val change = currentQueueService.removeEntry(
            accountId = accountId,
            entryId = entryId,
        )
        broadcastQueueIfChanged(accountId, change)
        queueMutationCoordinator.handleCurrentEntryRemoved(
            accountId = accountId,
            change = change,
            nowMs = change.queue.updatedAtMs,
        )
        return change.queue
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.OK)
    fun clearCurrentQueue(): CurrentQueueDto {
        val accountId = currentAccountId()
        val change = currentQueueService.clearQueue(accountId)
        broadcastQueueIfChanged(accountId, change)
        queueMutationCoordinator.handleQueueCleared(
            accountId = accountId,
            nowMs = change.queue.updatedAtMs,
        )
        return change.queue
    }

    private fun broadcastQueueIfChanged(
        accountId: Long,
        change: com.coooolfan.unirhy.sync.service.CurrentQueueChangeResult,
    ) {
        if (!change.changed) {
            return
        }
        messageSender.broadcastQueueChange(accountId, change.queue)
    }

    private fun currentAccountId(): Long = StpUtil.getLoginIdAsLong()
}
