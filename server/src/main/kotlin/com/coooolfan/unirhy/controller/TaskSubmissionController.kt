package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import cn.dev33.satoken.annotation.SaCheckRole
import com.coooolfan.unirhy.config.ROLE_ADMIN
import com.coooolfan.unirhy.error.CommonException
import com.coooolfan.unirhy.error.TaskException
import com.coooolfan.unirhy.model.AsyncTask
import com.coooolfan.unirhy.model.TaskSubmission
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.service.task.AsyncTaskService
import com.coooolfan.unirhy.service.task.TaskStatusCounts
import com.coooolfan.unirhy.service.task.TaskSubmissionService
import com.coooolfan.unirhy.service.task.common.TaskStatus
import jakarta.servlet.http.HttpServletResponse
import tools.jackson.databind.JsonNode
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

data class TaskSubmissionCreateRequest(
    val namespace: String,
    val taskType: String,
    val params: JsonNode,
)

data class TaskSubmissionCreatedResponse(
    val submissionId: Long,
)

data class TaskStatusPatchRequest(
    val status: TaskStatus,
)

data class TaskStatusBatchPatchRequest(
    val ids: List<Long>,
    val status: TaskStatus,
)

data class TaskSubmissionDetailResponse(
    val submission: @FetchBy("DEFAULT_SUBMISSION_FETCHER", ownerType = TaskSubmissionController::class) TaskSubmission,
    val taskCounts: TaskStatusCounts,
)

/**
 * 任务提交管理接口
 *
 * 内建任务与插件任务共用的统一 submission 资源
 */
@SaCheckLogin
@RestController
@RequestMapping("/api/task-submissions")
class TaskSubmissionController(
    private val submissionService: TaskSubmissionService,
    private val asyncTaskService: AsyncTaskService,
) {

    /**
     * 创建 submission（触发一次任务规划）
     *
     * 普通非幂等 POST：每个通过校验并被受理的请求都创建新的 submission。
     * HTTP 线程只校验任务身份、可用性与请求结构，规划由 Planner worker 异步执行。
     *
     * @param request 任务身份二元组与根 Object 参数
     * @return 202 Accepted，`Location: /api/task-submissions/{id}` 与 submissionId
     *
     * @api POST /api/task-submissions
     * @permission 需要管理员权限
     */
    @PostMapping
    @SaCheckRole(ROLE_ADMIN)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Throws(
        CommonException.Forbidden::class,
        TaskException.InvalidTaskKey::class,
        TaskException.InvalidParams::class,
        TaskException.DefinitionNotFound::class,
        TaskException.PluginUnavailable::class,
    )
    fun createSubmission(
        @RequestBody request: TaskSubmissionCreateRequest,
        response: HttpServletResponse,
    ): TaskSubmissionCreatedResponse {
        val submissionId = submissionService.create(request.namespace, request.taskType, request.params)
        response.setHeader("Location", "/api/task-submissions/$submissionId")
        return TaskSubmissionCreatedResponse(submissionId = submissionId)
    }

    /**
     * 分页查询 submission
     *
     * @api GET /api/task-submissions
     * @permission 需要登录认证
     */
    @GetMapping
    fun listSubmissions(
        @RequestParam(required = false) namespace: String?,
        @RequestParam(required = false) taskType: String?,
        @RequestParam(required = false) statuses: List<TaskStatus>?,
        @RequestParam(required = false) pageIndex: Int?,
        @RequestParam(required = false) pageSize: Int?,
    ): Page<@FetchBy("DEFAULT_SUBMISSION_FETCHER") TaskSubmission> =
        submissionService.list(
            namespace = namespace,
            taskType = taskType,
            statuses = statuses ?: emptyList(),
            pageIndex = pageIndex ?: 0,
            pageSize = pageSize ?: 20,
            fetcher = DEFAULT_SUBMISSION_FETCHER,
        )

    /**
     * 查询单项 submission 及其子任务状态计数
     *
     * 规划状态与子任务状态彼此独立，前端不把子任务汇总结果回写为 submission 状态
     *
     * @api GET /api/task-submissions/{id}
     * @permission 需要登录认证
     */
    @GetMapping("/{id}")
    @Throws(TaskException.SubmissionNotFound::class)
    fun getSubmission(@PathVariable id: Long): TaskSubmissionDetailResponse {
        val submission = submissionService.get(id, DEFAULT_SUBMISSION_FETCHER)
        val counts = submissionService.taskStatusCounts(id)
        return TaskSubmissionDetailResponse(
            submission = submission,
            taskCounts = TaskStatusCounts.from(counts),
        )
    }

    /**
     * 分页查询 submission 的关联任务
     *
     * @api GET /api/task-submissions/{id}/tasks
     * @permission 需要登录认证
     */
    @GetMapping("/{id}/tasks")
    @Throws(TaskException.SubmissionNotFound::class)
    fun listSubmissionTasks(
        @PathVariable id: Long,
        @RequestParam(required = false) statuses: List<TaskStatus>?,
        @RequestParam(required = false) pageIndex: Int?,
        @RequestParam(required = false) pageSize: Int?,
    ): Page<@FetchBy("DEFAULT_TASK_FETCHER", ownerType = TaskController::class) AsyncTask> {
        submissionService.get(id, DEFAULT_SUBMISSION_FETCHER)
        return asyncTaskService.list(
            submissionId = id,
            namespace = null,
            taskType = null,
            statuses = statuses ?: emptyList(),
            pageIndex = pageIndex ?: 0,
            pageSize = pageSize ?: 20,
            fetcher = TaskController.DEFAULT_TASK_FETCHER,
        )
    }

    /**
     * 单项状态变更；只接受 `PENDING -> CANCELLED` 与 `FAILED -> PENDING`
     *
     * @api PATCH /api/task-submissions/{id}
     * @permission 需要管理员权限
     */
    @PatchMapping("/{id}")
    @SaCheckRole(ROLE_ADMIN)
    @Throws(
        CommonException.Forbidden::class,
        TaskException.SubmissionNotFound::class,
        TaskException.StatusConflict::class,
        TaskException.PluginUnavailable::class,
    )
    fun patchSubmission(
        @PathVariable id: Long,
        @RequestBody request: TaskStatusPatchRequest,
    ): @FetchBy("DEFAULT_SUBMISSION_FETCHER") TaskSubmission =
        submissionService.patchStatus(id, request.status, DEFAULT_SUBMISSION_FETCHER)

    /**
     * 批量状态变更，返回实际更新数量
     *
     * @api PATCH /api/task-submissions
     * @permission 需要管理员权限
     */
    @PatchMapping
    @SaCheckRole(ROLE_ADMIN)
    @Throws(CommonException.Forbidden::class, TaskException.StatusConflict::class)
    fun patchSubmissions(@RequestBody request: TaskStatusBatchPatchRequest): Int =
        submissionService.patchStatusBatch(request.ids, request.status)

    /**
     * 删除 submission 及其全部子任务（数据库级联）
     *
     * 仅当 submission 与全部子任务都处于终态时允许
     *
     * @api DELETE /api/task-submissions/{id}
     * @permission 需要管理员权限
     */
    @DeleteMapping("/{id}")
    @SaCheckRole(ROLE_ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Throws(
        CommonException.Forbidden::class,
        TaskException.SubmissionNotFound::class,
        TaskException.DeleteConflict::class,
    )
    fun deleteSubmission(@PathVariable id: Long) {
        submissionService.delete(id)
    }

    companion object {
        val DEFAULT_SUBMISSION_FETCHER = newFetcher(TaskSubmission::class).by {
            allScalarFields()
        }
    }
}
