package com.coooolfan.unirhy.service.plugin.hostapi

import com.coooolfan.unirhy.model.AsyncTask
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.service.task.AsyncTaskService
import com.coooolfan.unirhy.service.task.TaskDefinitionService
import com.coooolfan.unirhy.service.task.TaskStatisticsService
import com.coooolfan.unirhy.service.task.common.TaskStatus
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import run.endive.runtime.HostFunction
import run.endive.runtime.Instance
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode

private data class HostPage<T>(val rows: List<T>, val totalRowCount: Long)
private data class HostTaskCreated(val taskId: Long)

private val HOST_TASK_FETCHER: Fetcher<AsyncTask> = newFetcher(AsyncTask::class).by {
    allScalarFields()
    parentId()
}

internal fun buildTaskHostFunctions(
    taskDefinitionService: TaskDefinitionService,
    asyncTaskService: AsyncTaskService,
    taskStatisticsService: TaskStatisticsService,
    objectMapper: ObjectMapper,
    instanceRef: () -> Instance,
    callExecutor: PluginHostCallExecutor = DIRECT_PLUGIN_HOST_CALL_EXECUTOR,
): List<HostFunction> {
    val support = PluginHostSupport(objectMapper, callExecutor, instanceRef)

    return listOf(
        support.jsonFunction("host_task_definition_list") { taskDefinitionService.list() },
        support.jsonFunction("host_task_definition_get") { request ->
            taskDefinitionService.get(request.requiredText("namespace"), request.requiredText("taskType"))
        },
        support.jsonFunction("host_task_create") { request ->
            HostTaskCreated(
                asyncTaskService.create(
                    namespace = request.requiredText("namespace"),
                    taskType = request.requiredText("taskType"),
                    payload = request.requiredObject("payload"),
                ),
            )
        },
        support.jsonFunction("host_task_list") { request ->
            val page = support.page(request)
            asyncTaskService.list(
                parentId = request.optionalLong("parentId"),
                rootsOnly = request.optionalBoolean("rootsOnly") ?: false,
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
                request.requiredLong("id"), request.requiredTaskStatus(), HOST_TASK_FETCHER,
            )
            null
        },
        support.jsonFunction("host_task_statistics") { request ->
            taskStatisticsService.statistics(request.optionalTextList("taskKeys"))
        },
    )
}

private fun ObjectNode.statusFilter(): List<TaskStatus> = optionalEnumFilter("status", TaskStatus.entries)

private fun <T : Enum<T>> ObjectNode.optionalEnumFilter(name: String, entries: List<T>): List<T> {
    val value = get(name) ?: return emptyList()
    if (value.isNull) return emptyList()
    if (!value.isString) invalidArgument("Field '$name' must be a string")
    return listOf(entries.firstOrNull { it.name == value.stringValue() }
        ?: invalidArgument("Unknown $name: ${value.stringValue()}"))
}

private fun ObjectNode.requiredTaskStatus(): TaskStatus =
    TaskStatus.entries.firstOrNull { it.name == requiredText("status") }
        ?: invalidArgument("Unknown task status")

private fun <T> Page<T>.toHostPage(): HostPage<T> = HostPage(rows, totalRowCount)
