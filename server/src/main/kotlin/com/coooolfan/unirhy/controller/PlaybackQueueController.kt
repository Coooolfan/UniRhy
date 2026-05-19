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

/**
 * 当前播放队列管理接口
 *
 * 提供当前播放队列的查询、替换、追加、重排、清空与导航能力，
 * 并在每次变更后通过同步通道广播给该账户的其他在线客户端
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
     * 此接口用于获取当前登录账户的播放队列快照
     * 需要用户登录认证才能访问
     *
     * @return CurrentQueueDto 返回当前队列
     *
     * @api GET /api/playback/current-queue
     * @permission 需要登录认证
     * @description 调用CurrentQueueService.getQueue()方法获取当前队列
     */
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    fun getCurrentQueue(): CurrentQueueDto {
        return currentQueueService.getQueue(StpUtil.getLoginIdAsLong())
    }

    /**
     * 替换当前播放队列
     *
     * 使用给定的录音列表整体替换当前队列，并指定新的当前索引；
     * 变更后会通过同步通道广播，并将暂停态播放进度对齐到新的当前项
     * 需要用户登录认证才能访问
     *
     * @param input 替换请求参数（录音列表、当前索引、期望版本号）
     * @return CurrentQueueDto 返回替换后的队列
     *
     * @api PUT /api/playback/current-queue
     * @permission 需要登录认证
     * @description 调用CurrentQueueService.replaceQueue()方法替换队列
     */
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

    /**
     * 向当前队列追加录音
     *
     * 在当前队列末尾追加一批录音
     * 需要用户登录认证才能访问
     *
     * @param input 追加请求参数（录音列表、期望版本号）
     * @return CurrentQueueDto 返回追加后的队列
     *
     * @api POST /api/playback/current-queue/items
     * @permission 需要登录认证
     * @description 调用CurrentQueueService.appendToQueue()方法追加录音
     */
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

    /**
     * 重排当前播放队列
     *
     * 按给定的录音 ID 顺序重排当前队列，并指定新的当前索引
     * 需要用户登录认证才能访问
     *
     * @param input 重排请求参数（新顺序下的录音列表、当前索引、期望版本号）
     * @return CurrentQueueDto 返回重排后的队列
     *
     * @api PUT /api/playback/current-queue/order
     * @permission 需要登录认证
     * @description 调用CurrentQueueService.reorderQueue()方法重排队列
     */
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

    /**
     * 设置当前播放索引
     *
     * 切换队列内当前播放项，并将暂停态播放进度对齐到该项
     * 需要用户登录认证才能访问
     *
     * @param input 设置当前索引的请求参数（当前索引、期望版本号）
     * @return CurrentQueueDto 返回更新后的队列
     *
     * @api PUT /api/playback/current-queue/current
     * @permission 需要登录认证
     * @description 调用CurrentQueueService.setCurrentIndex()方法切换当前项
     */
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

    /**
     * 更新当前队列的播放/停止策略
     *
     * 修改播放策略（顺序、随机等）与停止策略（单曲、列表等）
     * 需要用户登录认证才能访问
     *
     * @param input 策略更新参数（播放策略、停止策略、期望版本号）
     * @return CurrentQueueDto 返回更新后的队列
     *
     * @api PUT /api/playback/current-queue/strategy
     * @permission 需要登录认证
     * @description 调用CurrentQueueService.updateStrategies()方法更新播放策略
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
            expectedVersion = input.version,
        )
        broadcastQueueIfChanged(accountId, change)
        return change.queue
    }

    /**
     * 播放下一首
     *
     * 根据当前播放策略导航到下一首录音，并同步播放状态
     * 需要用户登录认证才能访问
     *
     * @param input 版本号请求参数（期望版本号）
     * @return CurrentQueueDto 返回更新后的队列
     *
     * @api POST /api/playback/current-queue/actions/next
     * @permission 需要登录认证
     * @description 调用CurrentQueueService.navigateToNext()方法导航到下一首
     */
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

    /**
     * 播放上一首
     *
     * 根据当前播放策略导航到上一首录音，并同步播放状态
     * 需要用户登录认证才能访问
     *
     * @param input 版本号请求参数（期望版本号）
     * @return CurrentQueueDto 返回更新后的队列
     *
     * @api POST /api/playback/current-queue/actions/previous
     * @permission 需要登录认证
     * @description 调用CurrentQueueService.navigateToPrevious()方法导航到上一首
     */
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

    /**
     * 从队列中移除指定索引项
     *
     * 移除当前队列中指定下标的录音；若移除的是当前播放项，则同步推进播放状态
     * 需要用户登录认证才能访问
     *
     * @param input 移除请求参数（待移除项下标、期望版本号）
     * @return CurrentQueueDto 返回移除后的队列
     *
     * @api POST /api/playback/current-queue/actions/remove
     * @permission 需要登录认证
     * @description 调用CurrentQueueService.removeAt()方法移除队列项
     */
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

    /**
     * 清空当前播放队列
     *
     * 移除当前队列中的全部录音，并同步清理播放状态
     * 需要用户登录认证才能访问
     *
     * @param input 版本号请求参数（期望版本号）
     * @return CurrentQueueDto 返回清空后的队列
     *
     * @api POST /api/playback/current-queue/actions/clear
     * @permission 需要登录认证
     * @description 调用CurrentQueueService.clearQueue()方法清空队列
     */
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
