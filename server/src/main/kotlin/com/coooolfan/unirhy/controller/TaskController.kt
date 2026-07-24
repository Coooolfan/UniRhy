package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import cn.dev33.satoken.annotation.SaCheckRole
import com.coooolfan.unirhy.config.ROLE_ADMIN
import com.coooolfan.unirhy.error.CommonException
import com.coooolfan.unirhy.error.TaskException
import com.coooolfan.unirhy.model.AsyncTask
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.service.task.AsyncTaskService
import com.coooolfan.unirhy.service.task.TaskStatusCounts
import com.coooolfan.unirhy.service.task.common.TaskStatus
import jakarta.servlet.http.HttpServletResponse
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import tools.jackson.databind.JsonNode

data class TaskCreateRequest(
    val namespace: String,
    val taskType: String,
    val payload: JsonNode,
)

data class TaskCreatedResponse(val taskId: Long)

data class TaskStatusPatchRequest(val status: TaskStatus)

data class TaskStatusBatchPatchRequest(val ids: List<Long>, val status: TaskStatus)

data class TaskStatusTransitionRequest(
    val namespace: String,
    val taskType: String,
    val sourceStatuses: Set<TaskStatus>,
    val targetStatus: TaskStatus,
)

data class TaskStatusTransitionResponse(val transitioned: Int)

data class TaskDetailResponse(
    val task: @FetchBy("DEFAULT_TASK_FETCHER", ownerType = TaskController::class) AsyncTask,
    val childTaskCounts: TaskStatusCounts,
)

/** 统一任务资源管理接口。 */
@SaCheckLogin
@RestController
@RequestMapping("/api/tasks")
class TaskController(
    private val taskService: AsyncTaskService,
) {
    /** 创建一个根任务。 */
    @PostMapping
    @SaCheckRole(ROLE_ADMIN)
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun createTask(
        @RequestBody request: TaskCreateRequest,
        response: HttpServletResponse,
    ): TaskCreatedResponse {
        val id = taskService.create(request.namespace, request.taskType, request.payload)
        response.setHeader("Location", "/api/tasks/$id")
        return TaskCreatedResponse(id)
    }

    @GetMapping
    fun listTasks(
        @RequestParam(required = false) parentId: Long?,
        @RequestParam(required = false, defaultValue = "false") rootsOnly: Boolean,
        @RequestParam(required = false) namespace: String?,
        @RequestParam(required = false) taskType: String?,
        @RequestParam(required = false) statuses: List<TaskStatus>?,
        @RequestParam(required = false) pageIndex: Int?,
        @RequestParam(required = false) pageSize: Int?,
    ): Page<@FetchBy("DEFAULT_TASK_FETCHER") AsyncTask> =
        taskService.list(
            parentId = parentId,
            rootsOnly = rootsOnly,
            namespace = namespace,
            taskType = taskType,
            statuses = statuses ?: emptyList(),
            pageIndex = pageIndex ?: 0,
            pageSize = pageSize ?: 20,
            fetcher = DEFAULT_TASK_FETCHER,
        )

    @GetMapping("/{id}")
    fun getTask(@PathVariable id: Long): TaskDetailResponse = TaskDetailResponse(
        task = taskService.get(id, DEFAULT_TASK_FETCHER),
        childTaskCounts = TaskStatusCounts.from(taskService.childStatusCounts(id)),
    )

    @GetMapping("/{id}/tree")
    fun getTaskTree(@PathVariable id: Long): @FetchBy("TASK_TREE_FETCHER") AsyncTask =
        taskService.get(id, TASK_TREE_FETCHER)

    @PostMapping("/status-transitions")
    @SaCheckRole(ROLE_ADMIN)
    fun transitionTaskStatuses(
        @RequestBody request: TaskStatusTransitionRequest,
    ): TaskStatusTransitionResponse = TaskStatusTransitionResponse(
        taskService.transitionStatuses(
            namespace = request.namespace,
            taskType = request.taskType,
            sourceStatuses = request.sourceStatuses,
            targetStatus = request.targetStatus,
        ),
    )

    @PatchMapping("/{id}")
    @SaCheckRole(ROLE_ADMIN)
    fun patchTask(
        @PathVariable id: Long,
        @RequestBody request: TaskStatusPatchRequest,
    ): @FetchBy("DEFAULT_TASK_FETCHER") AsyncTask =
        taskService.patchStatus(id, request.status, DEFAULT_TASK_FETCHER)

    @PatchMapping
    @SaCheckRole(ROLE_ADMIN)
    fun patchTasks(@RequestBody request: TaskStatusBatchPatchRequest): Int =
        taskService.patchStatusBatch(request.ids, request.status)

    companion object {
        val DEFAULT_TASK_FETCHER = newFetcher(AsyncTask::class).by {
            allScalarFields()
            parentId()
        }

        val TASK_TREE_FETCHER = newFetcher(AsyncTask::class).by {
            allScalarFields()
            parentId()
            `childTasks*`()
        }
    }
}
