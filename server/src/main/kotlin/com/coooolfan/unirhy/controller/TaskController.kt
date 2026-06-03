package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import cn.dev33.satoken.annotation.SaCheckRole
import com.coooolfan.unirhy.config.ROLE_ADMIN
import com.coooolfan.unirhy.service.task.*
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

/**
 * 任务管理接口
 *
 * 提供内置任务触发能力（扫描、转码）；插件任务通过 PluginController 提交
 */
@SaCheckLogin
@RestController
@RequestMapping("/api/tasks")
class TaskController(
    private val scanTaskService: ScanTaskService,
    private val transcodeTaskService: TranscodeTaskService,
    private val asyncTaskLogService: AsyncTaskLogService,
) {

    /**
     * 提交扫描任务
     *
     * 此接口用于异步触发媒体库扫描任务
     * 需要用户登录认证才能访问
     *
     * @param request 扫描任务请求参数
     *
     * @api POST /api/tasks/scans
     * @permission 需要登录认证
     * @description 调用ScanTaskService.submit()方法提交扫描任务
     */
    @PostMapping("/scans")
    @SaCheckRole(ROLE_ADMIN)
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun executeScanTask(@RequestBody request: ScanTaskRequest) {
        scanTaskService.submit(request)
    }

    /**
     * 提交转码任务
     *
     * 此接口用于异步触发音频转码任务
     * 需要用户登录认证才能访问
     *
     * @param request 转码任务请求参数
     *
     * @api POST /api/tasks/transcodes
     * @permission 需要登录认证
     * @description 调用TranscodeTaskService.submit()方法提交转码任务
     */
    @PostMapping("/transcodes")
    @SaCheckRole(ROLE_ADMIN)
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun executeTranscodeTask(@RequestBody request: TranscodeTaskRequest) {
        transcodeTaskService.submit(request)
    }

    /**
     * 获取任务日志计数列表
     *
     * 此接口用于按任务维度聚合查询异步任务日志计数
     * 需要用户登录认证才能访问
     *
     * @return List<AsyncTaskLogCountRow> 返回任务日志计数列表
     *
     * @api GET /api/tasks/log-counts
     * @permission 需要登录认证
     * @description 调用AsyncTaskLogService.listCounts()方法获取任务日志计数
     */
    @GetMapping("/log-counts")
    fun listTaskLogs(): List<AsyncTaskLogCountRow> {
        return asyncTaskLogService.listCounts()
    }
}
