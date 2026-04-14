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

/**
 * 当前播放队列管理接口
 *
 * 提供当前账号播放队列的查询、替换、追加、重排与导航能力，
 * 并在需要时同步触发播放状态广播。
 */
@SaCheckLogin
@RestController
@RequestMapping("/api/playback/current-queue")
class PlaybackQueueController(
    private val currentQueueService: CurrentQueueService,
    private val queueMutationCoordinator: PlaybackQueueMutationCoordinator,
    private val messageSender: PlaybackSyncMessageSender,
) {
    /**
     * 获取当前播放队列
     *
     * 此接口用于获取当前登录账号的播放队列快照
     * 需要用户登录认证才能访问
     *
     * @return CurrentQueueDto 返回当前播放队列
     *
     * @api GET /api/playback/current-queue
     * @permission 需要登录认证
     * @description 调用CurrentQueueService.getQueue()方法获取当前播放队列
     */
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    fun getCurrentQueue(): CurrentQueueDto {
        return currentQueueService.getQueue(StpUtil.getLoginIdAsLong())
    }

    /**
     * 替换当前播放队列
     *
     * 此接口用于使用指定录音列表整体替换当前播放队列，并设置当前项
     * 需要用户登录认证才能访问
     *
     * @param input CurrentQueueReplaceRequest 队列替换参数
     * @return CurrentQueueDto 返回替换后的当前播放队列
     *
     * @api PUT /api/playback/current-queue
     * @permission 需要登录认证
     * @description 调用CurrentQueueService.replaceQueue()方法替换当前播放队列
     */
    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    fun replaceCurrentQueue(
        @RequestBody input: CurrentQueueReplaceRequest,
    ): CurrentQueueDto {
        val accountId = StpUtil.getLoginIdAsLong()
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

    /**
     * 向当前播放队列追加录音
     *
     * 此接口用于向当前登录账号的播放队列末尾追加录音
     * 需要用户登录认证才能访问
     *
     * @param input CurrentQueueAppendRequest 队列追加参数
     * @return CurrentQueueDto 返回追加后的当前播放队列
     *
     * @api POST /api/playback/current-queue/items
     * @permission 需要登录认证
     * @description 调用CurrentQueueService.appendToQueue()方法向当前播放队列追加录音
     */
    @PostMapping("/items")
    @ResponseStatus(HttpStatus.OK)
    fun appendToCurrentQueue(
        @RequestBody input: CurrentQueueAppendRequest,
    ): CurrentQueueDto {
        val accountId = StpUtil.getLoginIdAsLong()
        val change = currentQueueService.appendToQueue(
            accountId = accountId,
            recordings = currentQueueService.resolvePlayableRecordings(input.recordingIds),
        )
        broadcastQueueIfChanged(accountId, change)
        return change.queue
    }

    /**
     * 重排当前播放队列
     *
     * 此接口用于按指定 entryId 顺序重排当前播放队列
     * 需要用户登录认证才能访问
     *
     * @param input CurrentQueueReorderRequest 队列重排参数
     * @return CurrentQueueDto 返回重排后的当前播放队列
     *
     * @api PUT /api/playback/current-queue/order
     * @permission 需要登录认证
     * @description 调用CurrentQueueService.reorderQueue()方法重排当前播放队列
     */
    @PutMapping("/order")
    @ResponseStatus(HttpStatus.OK)
    fun reorderCurrentQueue(
        @RequestBody input: CurrentQueueReorderRequest,
    ): CurrentQueueDto {
        val accountId = StpUtil.getLoginIdAsLong()
        val change = currentQueueService.reorderQueue(
            accountId = accountId,
            entryIds = input.entryIds,
        )
        broadcastQueueIfChanged(accountId, change)
        return change.queue
    }

    /**
     * 设置当前播放队列项
     *
     * 此接口用于切换当前播放队列中的当前项
     * 需要用户登录认证才能访问
     *
     * @param input CurrentQueueSetCurrentRequest 当前项设置参数
     * @return CurrentQueueDto 返回更新后的当前播放队列
     *
     * @api PUT /api/playback/current-queue/current
     * @permission 需要登录认证
     * @description 调用CurrentQueueService.setCurrentEntry()方法设置当前播放队列项
     */
    @PutMapping("/current")
    @ResponseStatus(HttpStatus.OK)
    fun setCurrentEntry(
        @RequestBody input: CurrentQueueSetCurrentRequest,
    ): CurrentQueueDto {
        val accountId = StpUtil.getLoginIdAsLong()
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

    /**
     * 更新当前播放队列策略
     *
     * 此接口用于更新当前播放队列的播放策略与停止策略
     * 需要用户登录认证才能访问
     *
     * @param input CurrentQueueStrategyUpdateRequest 队列策略更新参数
     * @return CurrentQueueDto 返回更新后的当前播放队列
     *
     * @api PUT /api/playback/current-queue/strategy
     * @permission 需要登录认证
     * @description 调用CurrentQueueService.updateStrategies()方法更新当前播放队列策略
     */
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
        )
        broadcastQueueIfChanged(accountId, change)
        return change.queue
    }

    /**
     * 执行下一首动作
     *
     * 此接口用于按当前播放队列策略跳转到下一首，并在需要时触发播放同步
     * 需要用户登录认证才能访问
     *
     * @return CurrentQueueDto 返回跳转后的当前播放队列
     *
     * @api POST /api/playback/current-queue/actions/next
     * @permission 需要登录认证
     * @description 调用CurrentQueueService.navigateToNext()方法执行下一首动作
     */
    @PostMapping("/actions/next")
    @ResponseStatus(HttpStatus.OK)
    fun playNextInCurrentQueue(): CurrentQueueDto {
        val accountId = StpUtil.getLoginIdAsLong()
        val change = currentQueueService.navigateToNext(accountId)
        broadcastQueueIfChanged(accountId, change)
        queueMutationCoordinator.handleQueueNavigation(accountId, change, change.queue.updatedAtMs)
        return change.queue
    }

    /**
     * 执行上一首动作
     *
     * 此接口用于按当前播放队列策略跳转到上一首，并在需要时触发播放同步
     * 需要用户登录认证才能访问
     *
     * @return CurrentQueueDto 返回跳转后的当前播放队列
     *
     * @api POST /api/playback/current-queue/actions/previous
     * @permission 需要登录认证
     * @description 调用CurrentQueueService.navigateToPrevious()方法执行上一首动作
     */
    @PostMapping("/actions/previous")
    @ResponseStatus(HttpStatus.OK)
    fun playPreviousInCurrentQueue(): CurrentQueueDto {
        val accountId = StpUtil.getLoginIdAsLong()
        val change = currentQueueService.navigateToPrevious(accountId)
        broadcastQueueIfChanged(accountId, change)
        queueMutationCoordinator.handleQueueNavigation(accountId, change, change.queue.updatedAtMs)
        return change.queue
    }

    /**
     * 删除当前播放队列项
     *
     * 此接口用于从当前播放队列中删除指定 entryId 对应的队列项
     * 需要用户登录认证才能访问
     *
     * @param entryId 队列项 ID
     * @return CurrentQueueDto 返回删除后的当前播放队列
     *
     * @api DELETE /api/playback/current-queue/items/{entryId}
     * @permission 需要登录认证
     * @description 调用CurrentQueueService.removeEntry()方法删除当前播放队列项
     */
    @DeleteMapping("/items/{entryId}")
    @ResponseStatus(HttpStatus.OK)
    fun removeCurrentQueueEntry(
        @PathVariable entryId: Long,
    ): CurrentQueueDto {
        val accountId = StpUtil.getLoginIdAsLong()
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

    /**
     * 清空当前播放队列
     *
     * 此接口用于清空当前登录账号的播放队列
     * 需要用户登录认证才能访问
     *
     * @return CurrentQueueDto 返回清空后的当前播放队列
     *
     * @api DELETE /api/playback/current-queue
     * @permission 需要登录认证
     * @description 调用CurrentQueueService.clearQueue()方法清空当前播放队列
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.OK)
    fun clearCurrentQueue(): CurrentQueueDto {
        val accountId = StpUtil.getLoginIdAsLong()
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
}
