package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import cn.dev33.satoken.annotation.SaCheckRole
import com.coooolfan.unirhy.config.ROLE_ADMIN
import com.coooolfan.unirhy.error.CommonException
import com.coooolfan.unirhy.error.TaskException
import com.coooolfan.unirhy.model.AsyncTask
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.service.task.AsyncTaskService
import com.coooolfan.unirhy.service.task.common.TaskStatus
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.web.bind.annotation.*

/**
 * 任务执行资源管理接口
 *
 * 任务提交经由 `/api/task-submissions`；本接口只查询与管理单条执行任务
 */
@SaCheckLogin
@RestController
@RequestMapping("/api/tasks")
class TaskController(
    private val asyncTaskService: AsyncTaskService,
) {

    /**
     * 分页查询任务
     *
     * @api GET /api/tasks
     * @permission 需要登录认证
     */
    @GetMapping
    fun listTasks(
        @RequestParam(required = false) submissionId: Long?,
        @RequestParam(required = false) namespace: String?,
        @RequestParam(required = false) taskType: String?,
        @RequestParam(required = false) statuses: List<TaskStatus>?,
        @RequestParam(required = false) pageIndex: Int?,
        @RequestParam(required = false) pageSize: Int?,
    ): Page<@FetchBy("DEFAULT_TASK_FETCHER") AsyncTask> =
        asyncTaskService.list(
            submissionId = submissionId,
            namespace = namespace,
            taskType = taskType,
            statuses = statuses ?: emptyList(),
            pageIndex = pageIndex ?: 0,
            pageSize = pageSize ?: 20,
            fetcher = DEFAULT_TASK_FETCHER,
        )

    /**
     * 查询单条任务
     *
     * @api GET /api/tasks/{id}
     * @permission 需要登录认证
     */
    @GetMapping("/{id}")
    @Throws(TaskException.TaskNotFound::class)
    fun getTask(@PathVariable id: Long): @FetchBy("DEFAULT_TASK_FETCHER") AsyncTask =
        asyncTaskService.get(id, DEFAULT_TASK_FETCHER)

    /**
     * 单项状态变更；只接受 `PENDING -> CANCELLED` 与 `FAILED -> PENDING`
     *
     * @api PATCH /api/tasks/{id}
     * @permission 需要管理员权限
     */
    @PatchMapping("/{id}")
    @SaCheckRole(ROLE_ADMIN)
    @Throws(
        CommonException.Forbidden::class,
        TaskException.TaskNotFound::class,
        TaskException.StatusConflict::class,
        TaskException.PluginUnavailable::class,
    )
    fun patchTask(
        @PathVariable id: Long,
        @RequestBody request: TaskStatusPatchRequest,
    ): @FetchBy("DEFAULT_TASK_FETCHER") AsyncTask =
        asyncTaskService.patchStatus(id, request.status, DEFAULT_TASK_FETCHER)

    /**
     * 批量状态变更，返回实际更新数量
     *
     * @api PATCH /api/tasks
     * @permission 需要管理员权限
     */
    @PatchMapping
    @SaCheckRole(ROLE_ADMIN)
    @Throws(CommonException.Forbidden::class, TaskException.StatusConflict::class)
    fun patchTasks(@RequestBody request: TaskStatusBatchPatchRequest): Int =
        asyncTaskService.patchStatusBatch(request.ids, request.status)

    companion object {
        val DEFAULT_TASK_FETCHER = newFetcher(AsyncTask::class).by {
            allScalarFields()
            submissionId()
        }
    }
}
