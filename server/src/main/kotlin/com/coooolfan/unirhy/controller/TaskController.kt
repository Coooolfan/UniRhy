package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.model.AsyncTaskLog
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.service.task.*
import com.coooolfan.unirhy.service.task.common.AsyncTaskLogStatus
import com.coooolfan.unirhy.service.task.common.TaskType
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

/**
 * 任务管理接口
 *
 * 提供任务触发能力（例如扫描任务）
 */
@SaCheckLogin
@RestController
@RequestMapping("/api/task")
class TaskController(
    private val scanTaskService: ScanTaskService,
    private val transcodeTaskService: TranscodeTaskService,
    private val asyncTaskLogService: AsyncTaskLogService,
) {

    /**
     * 触发扫描任务
     *
     * 此接口用于提交媒体扫描任务，对同一存储节点的请求是幂等的
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
     * 此接口用于提交媒体转码任务，非幂等操作
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
     * 分页查询任务日志
     *
     * @api GET /api/task/logs
     * @permission 需要登录认证
     */
    @GetMapping("/logs")
    fun listTaskLogs(
        @RequestParam(required = false) pageIndex: Int?,
        @RequestParam(required = false) pageSize: Int?,
        @RequestParam(required = false) taskType: TaskType?,
        @RequestParam(required = false) status: AsyncTaskLogStatus?,
    ): Page<@FetchBy("DEFAULT_ASYNC_TASK_LOG_FETCHER") AsyncTaskLog> {
        return asyncTaskLogService.listLogs(
            pageIndex = pageIndex ?: 0,
            pageSize = pageSize ?: 10,
            taskType = taskType,
            status = status,
            fetcher = DEFAULT_ASYNC_TASK_LOG_FETCHER,
        )
    }

    companion object {
        val DEFAULT_ASYNC_TASK_LOG_FETCHER = newFetcher(AsyncTaskLog::class).by {
            allScalarFields()
            running()
        }
    }
}
