package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
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
@RequestMapping("/api/task")
class TaskController(
    private val scanTaskService: ScanTaskService,
    private val transcodeTaskService: TranscodeTaskService,
    private val asyncTaskLogService: AsyncTaskLogService,
) {

    @PostMapping("/scan")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun executeScanTask(@RequestBody request: ScanTaskRequest) {
        scanTaskService.submit(request)
    }

    @PostMapping("/transcode")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun executeTranscodeTask(@RequestBody request: TranscodeTaskRequest) {
        transcodeTaskService.submit(request)
    }

    @GetMapping("/logs")
    fun listTaskLogs(): List<AsyncTaskLogCountRow> {
        return asyncTaskLogService.listCounts()
    }
}
