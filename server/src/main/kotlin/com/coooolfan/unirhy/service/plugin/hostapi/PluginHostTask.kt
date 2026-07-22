package com.coooolfan.unirhy.service.plugin.hostapi

import com.coooolfan.unirhy.model.AsyncTask
import com.coooolfan.unirhy.model.TaskSubmission
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.service.task.AsyncTaskService
import com.coooolfan.unirhy.service.task.TaskDefinitionService
import com.coooolfan.unirhy.service.task.TaskStatisticsService
import com.coooolfan.unirhy.service.task.TaskStatusCounts
import com.coooolfan.unirhy.service.task.TaskSubmissionService
import com.coooolfan.unirhy.service.task.common.TaskStatus
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import run.endive.runtime.HostFunction
import run.endive.runtime.Instance
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode

private data class HostPage<T>(
    val rows: List<T>,
    val totalRowCount: Long,
)

private data class HostTaskSubmissionCreated(
    val submissionId: Long,
)

private data class HostTaskSubmissionDetail(
    val submission: TaskSubmission,
    val taskCounts: TaskStatusCounts,
)

private val HOST_SUBMISSION_FETCHER: Fetcher<TaskSubmission> = newFetcher(TaskSubmission::class).by {
    allScalarFields()
}

private val HOST_TASK_FETCHER: Fetcher<AsyncTask> = newFetcher(AsyncTask::class).by {
    allScalarFields()
    submissionId()
}

internal fun buildTaskHostFunctions(
    taskDefinitionService: TaskDefinitionService,
    taskSubmissionService: TaskSubmissionService,
    asyncTaskService: AsyncTaskService,
    taskStatisticsService: TaskStatisticsService,
    objectMapper: ObjectMapper,
    instanceRef: () -> Instance,
    callExecutor: PluginHostCallExecutor = DIRECT_PLUGIN_HOST_CALL_EXECUTOR,
): List<HostFunction> {
    val support = PluginHostSupport(objectMapper, callExecutor, instanceRef)

    return listOf(
        support.jsonFunction("host_task_definition_list") {
            taskDefinitionService.list()
        },
        support.jsonFunction("host_task_definition_get") { request ->
            taskDefinitionService.get(
                namespace = request.requiredText("namespace"),
                taskType = request.requiredText("taskType"),
            )
        },
        support.jsonFunction("host_task_submission_create") { request ->
            HostTaskSubmissionCreated(
                submissionId = taskSubmissionService.create(
                    namespace = request.requiredText("namespace"),
                    taskType = request.requiredText("taskType"),
                    params = request.requiredObject("params"),
                ),
            )
        },
        support.jsonFunction("host_task_submission_list") { request ->
            val page = support.page(request)
            taskSubmissionService.list(
                namespace = request.optionalText("namespace"),
                taskType = request.optionalText("taskType"),
                statuses = request.statusFilter(),
                pageIndex = page.pageIndex,
                pageSize = page.pageSize,
                fetcher = HOST_SUBMISSION_FETCHER,
            ).toHostPage()
        },
        support.jsonFunction("host_task_submission_get") { request ->
            val id = request.requiredLong("id")
            HostTaskSubmissionDetail(
                submission = taskSubmissionService.get(id, HOST_SUBMISSION_FETCHER),
                taskCounts = TaskStatusCounts.from(taskSubmissionService.taskStatusCounts(id)),
            )
        },
        support.jsonFunction("host_task_submission_tasks") { request ->
            val id = request.requiredLong("id")
            val page = support.page(request)
            taskSubmissionService.get(id, HOST_SUBMISSION_FETCHER)
            asyncTaskService.list(
                submissionId = id,
                namespace = null,
                taskType = null,
                statuses = request.statusFilter(),
                pageIndex = page.pageIndex,
                pageSize = page.pageSize,
                fetcher = HOST_TASK_FETCHER,
            ).toHostPage()
        },
        support.jsonFunction("host_task_submission_patch") { request ->
            taskSubmissionService.patchStatus(
                id = request.requiredLong("id"),
                target = request.requiredTaskStatus(),
                fetcher = HOST_SUBMISSION_FETCHER,
            )
            null
        },
        support.jsonFunction("host_task_submission_delete") { request ->
            taskSubmissionService.delete(request.requiredLong("id"))
            null
        },
        support.jsonFunction("host_task_list") { request ->
            val page = support.page(request)
            asyncTaskService.list(
                submissionId = request.optionalLong("submissionId"),
                namespace = request.optionalText("namespace"),
                taskType = request.optionalText("taskType"),
                statuses = request.statusFilter(),
                pageIndex = page.pageIndex,
                pageSize = page.pageSize,
                fetcher = HOST_TASK_FETCHER,
            ).toHostPage()
        },
        support.jsonFunction("host_task_get") { request ->
            asyncTaskService.get(request.requiredLong("id"), HOST_TASK_FETCHER)
        },
        support.jsonFunction("host_task_patch") { request ->
            asyncTaskService.patchStatus(
                id = request.requiredLong("id"),
                target = request.requiredTaskStatus(),
                fetcher = HOST_TASK_FETCHER,
            )
            null
        },
        support.jsonFunction("host_task_statistics") { request ->
            taskStatisticsService.statistics(request.optionalTextList("taskKeys"))
        },
    )
}

private fun ObjectNode.statusFilter(): List<TaskStatus> {
    val value = get("status") ?: return emptyList()
    if (value.isNull) return emptyList()
    if (!value.isString) invalidArgument("Field 'status' must be a string")
    return listOf(parseTaskStatus(value.stringValue()))
}

private fun ObjectNode.requiredTaskStatus(): TaskStatus = parseTaskStatus(requiredText("status"))

private fun parseTaskStatus(value: String): TaskStatus =
    TaskStatus.entries.firstOrNull { it.name == value }
        ?: invalidArgument("Unknown task status: $value")

private fun <T> Page<T>.toHostPage(): HostPage<T> = HostPage(
    rows = rows,
    totalRowCount = totalRowCount,
)
