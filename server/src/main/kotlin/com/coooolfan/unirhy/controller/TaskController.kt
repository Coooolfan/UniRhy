package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import cn.dev33.satoken.annotation.SaCheckRole
import com.coooolfan.unirhy.config.ROLE_ADMIN
import com.coooolfan.unirhy.model.AsyncTaskLog
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.service.task.*
import com.coooolfan.unirhy.service.task.common.TaskStatus
import com.coooolfan.unirhy.service.task.common.TaskType
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
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

    /**
     * 按任务类型 + 状态集合分页查询任务日志明细
     *
     * @param taskType 任务类型
     * @param statuses 任务状态集合（允许多值）
     * @param pageIndex 页码（从 0 开始）
     * @param pageSize 每页条数
     * @return Page<AsyncTaskLog> 分页结果
     *
     * @api GET /api/tasks/logs
     * @permission 需要登录认证
     */
    @GetMapping("/logs")
    fun listTaskLogDetails(
        @RequestParam taskType: TaskType,
        @RequestParam(required = false) statuses: List<TaskStatus>?,
        @RequestParam(required = false) pageIndex: Int?,
        @RequestParam(required = false) pageSize: Int?,
    ): Page<@FetchBy("DEFAULT_TASK_LOG_FETCHER") AsyncTaskLog> {
        return asyncTaskLogService.listByTypeAndStatuses(
            taskType = taskType,
            statuses = statuses ?: emptyList(),
            pageIndex = pageIndex ?: 0,
            pageSize = pageSize ?: 20,
            fetcher = DEFAULT_TASK_LOG_FETCHER,
        )
    }

    /**
     * 将 FAILED / COMPLETED 状态的任务重置为 PENDING，让 worker 重新执行
     *
     * 按 ids / taskType / statuses 的交集选中记录：
     * - 三个参数均可选，但至少需要提供一个
     * - 无论传入 statuses 是什么，服务端只会真正重置 FAILED / COMPLETED 记录
     * - 前端约定：单条按 ids=1；一键按 taskType=X&statuses=FAILED
     *
     * @param ids 目标任务 ID 列表
     * @param taskType 任务类型过滤
     * @param statuses 状态过滤
     * @return 实际被重置的行数
     *
     * @api PATCH /api/tasks/logs
     * @permission 需要管理员权限
     */
    @PatchMapping("/logs")
    @SaCheckRole(ROLE_ADMIN)
    fun resetTaskLogs(
        @RequestParam(required = false) ids: List<Long>?,
        @RequestParam(required = false) taskType: TaskType?,
        @RequestParam(required = false) statuses: List<TaskStatus>?,
    ): Int {
        return asyncTaskLogService.resetToPending(
            ids = ids ?: emptyList(),
            taskType = taskType,
            statuses = statuses ?: emptyList(),
        )
    }

    companion object {
        val DEFAULT_TASK_LOG_FETCHER = newFetcher(AsyncTaskLog::class).by {
            allScalarFields()
        }
    }
}
