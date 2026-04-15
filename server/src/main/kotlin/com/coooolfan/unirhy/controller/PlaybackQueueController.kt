package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import cn.dev33.satoken.stp.StpUtil
import com.coooolfan.unirhy.model.dto.CurrentQueueAppendRequest
import com.coooolfan.unirhy.model.dto.CurrentQueueRemoveRequest
import com.coooolfan.unirhy.model.dto.CurrentQueueReorderRequest
import com.coooolfan.unirhy.model.dto.CurrentQueueReplaceRequest
import com.coooolfan.unirhy.model.dto.CurrentQueueSetCurrentRequest
import com.coooolfan.unirhy.model.dto.CurrentQueueStrategyUpdateRequest
import com.coooolfan.unirhy.model.dto.CurrentQueueVersionRequest
import com.coooolfan.unirhy.sync.protocol.CurrentQueueDto
import com.coooolfan.unirhy.sync.service.CurrentQueueChangeResult
import com.coooolfan.unirhy.sync.service.CurrentQueueService
import com.coooolfan.unirhy.sync.service.PlaybackQueueMutationCoordinator
import com.coooolfan.unirhy.sync.service.PlaybackSyncMessageSender
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

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
        return currentQueueService.getQueue(StpUtil.getLoginIdAsLong())
    }

    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    fun replaceCurrentQueue(
        @RequestBody input: CurrentQueueReplaceRequest,
    ): CurrentQueueDto {
        val accountId = StpUtil.getLoginIdAsLong()
        val change = currentQueueService.replaceQueue(
            accountId = accountId,
            recordingIds = input.recordingIds,
            currentIndex = input.currentIndex,
            expectedVersion = input.version,
        )
        broadcastQueueIfChanged(accountId, change)
        queueMutationCoordinator.syncPausedPlaybackToCurrentQueue(
            accountId = accountId,
            currentIndex = change.currentIndex,
            nowMs = change.queue.updatedAtMs,
        )
        return change.queue
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.OK)
    fun appendToCurrentQueue(
        @RequestBody input: CurrentQueueAppendRequest,
    ): CurrentQueueDto {
        val accountId = StpUtil.getLoginIdAsLong()
        val change = currentQueueService.appendToQueue(
            accountId = accountId,
            recordingIds = input.recordingIds,
            expectedVersion = input.version,
        )
        broadcastQueueIfChanged(accountId, change)
        return change.queue
    }

    @PutMapping("/order")
    @ResponseStatus(HttpStatus.OK)
    fun reorderCurrentQueue(
        @RequestBody input: CurrentQueueReorderRequest,
    ): CurrentQueueDto {
        val accountId = StpUtil.getLoginIdAsLong()
        val change = currentQueueService.reorderQueue(
            accountId = accountId,
            recordingIds = input.recordingIds,
            currentIndex = input.currentIndex,
            expectedVersion = input.version,
        )
        broadcastQueueIfChanged(accountId, change)
        return change.queue
    }

    @PutMapping("/current")
    @ResponseStatus(HttpStatus.OK)
    fun setCurrentIndex(
        @RequestBody input: CurrentQueueSetCurrentRequest,
    ): CurrentQueueDto {
        val accountId = StpUtil.getLoginIdAsLong()
        val change = currentQueueService.setCurrentIndex(
            accountId = accountId,
            currentIndex = input.currentIndex,
            expectedVersion = input.version,
        )
        broadcastQueueIfChanged(accountId, change)
        queueMutationCoordinator.syncPausedPlaybackToCurrentQueue(
            accountId = accountId,
            currentIndex = change.currentIndex,
            nowMs = change.queue.updatedAtMs,
        )
        return change.queue
    }

    @PutMapping("/strategy")
    @ResponseStatus(HttpStatus.OK)
    fun updateCurrentQueueStrategy(
        @RequestBody input: CurrentQueueStrategyUpdateRequest,
    ): CurrentQueueDto {
        val accountId = StpUtil.getLoginIdAsLong()
        val change = currentQueueService.updateStrategies(
            accountId = accountId,
            playbackStrategy = input.playbackStrategy,
            stopStrategy = input.stopStrategy,
            expectedVersion = input.version,
        )
        broadcastQueueIfChanged(accountId, change)
        return change.queue
    }

    @PostMapping("/actions/next")
    @ResponseStatus(HttpStatus.OK)
    fun playNextInCurrentQueue(
        @RequestBody input: CurrentQueueVersionRequest,
    ): CurrentQueueDto {
        val accountId = StpUtil.getLoginIdAsLong()
        val change = currentQueueService.navigateToNext(
            accountId = accountId,
            expectedVersion = input.version,
        )
        broadcastQueueIfChanged(accountId, change)
        queueMutationCoordinator.handleQueueNavigation(accountId, change, change.queue.updatedAtMs)
        return change.queue
    }

    @PostMapping("/actions/previous")
    @ResponseStatus(HttpStatus.OK)
    fun playPreviousInCurrentQueue(
        @RequestBody input: CurrentQueueVersionRequest,
    ): CurrentQueueDto {
        val accountId = StpUtil.getLoginIdAsLong()
        val change = currentQueueService.navigateToPrevious(
            accountId = accountId,
            expectedVersion = input.version,
        )
        broadcastQueueIfChanged(accountId, change)
        queueMutationCoordinator.handleQueueNavigation(accountId, change, change.queue.updatedAtMs)
        return change.queue
    }

    @PostMapping("/actions/remove")
    @ResponseStatus(HttpStatus.OK)
    fun removeCurrentQueueEntry(
        @RequestBody input: CurrentQueueRemoveRequest,
    ): CurrentQueueDto {
        val accountId = StpUtil.getLoginIdAsLong()
        val change = currentQueueService.removeAt(
            accountId = accountId,
            index = input.index,
            expectedVersion = input.version,
        )
        broadcastQueueIfChanged(accountId, change)
        queueMutationCoordinator.handleCurrentEntryRemoved(
            accountId = accountId,
            change = change,
            nowMs = change.queue.updatedAtMs,
        )
        return change.queue
    }

    @PostMapping("/actions/clear")
    @ResponseStatus(HttpStatus.OK)
    fun clearCurrentQueue(
        @RequestBody input: CurrentQueueVersionRequest,
    ): CurrentQueueDto {
        val accountId = StpUtil.getLoginIdAsLong()
        val change = currentQueueService.clearQueue(
            accountId = accountId,
            expectedVersion = input.version,
        )
        broadcastQueueIfChanged(accountId, change)
        queueMutationCoordinator.handleQueueCleared(
            accountId = accountId,
            nowMs = change.queue.updatedAtMs,
        )
        return change.queue
    }

    private fun broadcastQueueIfChanged(
        accountId: Long,
        change: CurrentQueueChangeResult,
    ) {
        if (!change.changed) {
            return
        }
        messageSender.broadcastQueueChange(accountId, change.queue)
    }
}
