package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.model.dto.RecordingUpdate
import com.coooolfan.unirhy.service.RecordingService
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

}