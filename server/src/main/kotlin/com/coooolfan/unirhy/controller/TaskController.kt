package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.service.task.AsyncTaskLogCountRow
import com.coooolfan.unirhy.service.task.AsyncTaskLogService
import com.coooolfan.unirhy.service.task.ScanTaskRequest
import com.coooolfan.unirhy.service.task.ScanTaskService
import com.coooolfan.unirhy.service.task.TranscodeTaskRequest
import com.coooolfan.unirhy.service.task.TranscodeTaskService
import com.coooolfan.unirhy.service.task.VectorizeTaskRequest
import com.coooolfan.unirhy.service.task.VectorizeTaskService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

/**
 * 任务管理接口
 *
 * 提供任务触发能力（例如扫描任务）
 */
// TODO
// @SaCheckLogin
@RestController
@RequestMapping("/api/task")
class TaskController(
    private val scanTaskService: ScanTaskService,
    private val transcodeTaskService: TranscodeTaskService,
    private val vectorizeTaskService: VectorizeTaskService,
    private val asyncTaskLogService: AsyncTaskLogService,
) {

    /**
     * 触发扫描任务
     *
     * 此接口用于提交媒体扫描任务，对同一存储节点的重复请求会增量补充缺失文件任务
     * 需要用户登录认证才能访问
     *
     * @param request 扫描任务请求参数
     *
     * @api POST /api/task/scan
     * @permission 需要登录认证
     * @description 调用ScanTaskService.submit()方法执行扫描任务
     */
    @PostMapping("/scan")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun executeScanTask(@RequestBody request: ScanTaskRequest) {
        scanTaskService.submit(request)
    }

    /**
     * 触发转码任务
     *
     * 此接口用于提交媒体转码任务，非幂等操作，但活跃中的相同转码任务会自动去重
     * 需要用户登录认证才能访问
     *
     * @param request 转码任务请求参数
     *
     * @api POST /api/task/transcode
     * @permission 需要登录认证
     * @description 调用TranscodeTaskService.submit()方法执行转码任务
     */
    @PostMapping("/transcode")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun executeTranscodeTask(@RequestBody request: TranscodeTaskRequest) {
        transcodeTaskService.submit(request)
    }

    /**
     * 查询任务状态统计
     *
     * @api GET /api/task/logs
     * @permission 需要登录认证
     */
    @PostMapping("/vectorize")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun executeVectorizeTask(@RequestBody request: VectorizeTaskRequest) {
        vectorizeTaskService.submit(request)
    }

    @GetMapping("/logs")
    fun listTaskLogs(): List<AsyncTaskLogCountRow> {
        return asyncTaskLogService.listCounts()
    }
}
