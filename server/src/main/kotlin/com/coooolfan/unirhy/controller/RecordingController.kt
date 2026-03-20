package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.model.Recording
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.model.dto.RecordingMergeReq
import com.coooolfan.unirhy.model.dto.RecordingUpdate
import com.coooolfan.unirhy.service.RecordingService
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

/**
 * 录音管理接口
 *
 * 提供录音信息的增删改查能力
 */
@SaCheckLogin
@RestController
@RequestMapping("/api/recordings")
class RecordingController(private val service: RecordingService) {

    /**
     * 获取指定录音
     *
     * 此接口用于根据录音 ID 获取播放器展示所需的最小录音信息。
     * 需要用户登录认证才能访问
     *
     * @param id Recording ID
     * @return Recording 返回录音信息（使用 PLAYBACK_RECORDING_FETCHER）
     *
     * @api GET /api/recordings/{id}
     * @permission 需要登录认证
     * @description 调用RecordingService.getRecording()方法获取录音信息
     */
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun getRecording(
        @PathVariable id: Long,
    ): @FetchBy("PLAYBACK_RECORDING_FETCHER") Recording {
        return service.getRecording(id, PLAYBACK_RECORDING_FETCHER)
    }

    /**
     * 更新录音信息
     *
     * 此接口用于更新系统中已有的录音信息
     * 需要用户登录认证才能访问
     *
     * @param id Recording ID
     * @param input RecordingUpdate 更新参数
     *
     * @api PUT /api/recordings/{id}
     * @permission 需要登录认证
     * @description 调用RecordingService.updateRecording()方法更新录音信息
     */
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun updateRecording(
        @PathVariable id: Long,
        @RequestBody input: RecordingUpdate,
    ) {
        service.updateRecording(input.toEntity { this.id = id })
    }


    /**
     * 录音合并接口
     *
     * 此接口用于将多个录音合并为一个录音
     * 需要用户登录认证才能访问
     *
     * @param input RecordingMergeReq 合并参数
     *
     * @api PUT /api/recordings/merge
     * @permission 需要登录认证
     * @description 调用RecordingService.mergeRecording()方法合并录音
     */
    @PutMapping("/merge")
    @ResponseStatus(HttpStatus.OK)
    fun mergeRecording(@RequestBody input: RecordingMergeReq) {
        service.mergeRecording(input)
    }

    companion object {
        val PLAYBACK_RECORDING_FETCHER = newFetcher(Recording::class).by {
            allScalarFields()
            work {
                allScalarFields()
            }
            artists {
                allScalarFields()
            }
            cover {
                allScalarFields()
                url()
            }
        }
    }
}
