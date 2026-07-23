package com.coooolfan.unirhy.service.plugin.hostapi

import com.coooolfan.unirhy.model.AsyncTask
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.service.plugin.WasmExecutionContext
import com.coooolfan.unirhy.service.task.AsyncTaskService
import com.coooolfan.unirhy.service.task.TaskDefinitionService
import com.coooolfan.unirhy.service.task.TaskStatisticsService
import com.coooolfan.unirhy.service.task.common.AsyncTaskStore
import com.coooolfan.unirhy.service.task.common.TaskAction
import com.coooolfan.unirhy.service.task.common.TaskKey
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
private data class HostTasksEnqueued(val enqueued: Int)

private val HOST_TASK_FETCHER: Fetcher<AsyncTask> = newFetcher(AsyncTask::class).by {
    allScalarFields()
    parentId()
}

internal fun buildTaskHostFunctions(
    taskDefinitionService: TaskDefinitionService,
    asyncTaskService: AsyncTaskService,
    taskStatisticsService: TaskStatisticsService,
    asyncTaskStore: AsyncTaskStore,
    pluginId: String,
    executionContext: WasmExecutionContext?,
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
        support.jsonFunction("host_task_enqueue") { request ->
            val context = executionContext
                ?: conflict("Tasks can only be enqueued while a plugin task is running")
            val namespace = request.optionalText("namespace") ?: pluginId
            val taskType = request.optionalText("taskType") ?: context.taskType
            val key = TaskKey.ofOrNull(namespace, taskType)
                ?: invalidArgument("Invalid task key: $namespace:$taskType")
            taskDefinitionService.find(key) ?: notFound("Task definition not found: $key")
            val payloads = request.requiredObjectList("payloads")
            HostTasksEnqueued(
                asyncTaskStore.enqueueChildrenIgnoringConflicts(
                    parentId = context.taskId,
                    key = key,
                    payloadJsonList = payloads.map { it.toString() },
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
                actions = request.actionFilter(),
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

private fun ObjectNode.requiredObjectList(name: String): List<ObjectNode> {
    val value = requiredNode(name)
    if (!value.isArray) invalidArgument("Field '$name' must be an array")
    if (value.isEmpty) invalidArgument("Field '$name' must contain at least one item")
    if (value.size() > HOST_MAX_ENQUEUE_BATCH_SIZE) {
        invalidArgument("Field '$name' must contain at most $HOST_MAX_ENQUEUE_BATCH_SIZE items")
    }
    return value.mapIndexed { index, item ->
        item as? ObjectNode ?: invalidArgument("Field '$name[$index]' must be an object")
    }
}

private fun ObjectNode.statusFilter(): List<TaskStatus> = optionalEnumFilter("status", TaskStatus.entries)
private fun ObjectNode.actionFilter(): List<TaskAction> = optionalEnumFilter("action", TaskAction.entries)

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

private const val HOST_MAX_ENQUEUE_BATCH_SIZE = 1000
