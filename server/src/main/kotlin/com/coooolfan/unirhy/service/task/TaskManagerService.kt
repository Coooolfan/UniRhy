package com.coooolfan.unirhy.service.task

import org.springframework.stereotype.Service

@Service
class TaskManagerService(
    taskServices: List<TaskService<out TaskRequest>>,
) {
    private val taskServiceByType: Map<TaskType, TaskService<out TaskRequest>> =
        taskServices.associateBy { it.taskType }

    fun getTaskService(taskType: TaskType): TaskService<out TaskRequest> {
        return requireNotNull(taskServiceByType[taskType]) {
            "TaskService not found for type: $taskType"
        }
    }

    fun execute(request: TaskRequest): TaskInfo {
        val taskType = TaskRequestTypes.typeOf(request)
        val service = getTaskService(taskType)
        service.execute(request)
        return TaskInfo(taskType)
    }

    fun listTaskTypes(): List<TaskType> {
        val availableTypes = taskServiceByType.keys
        return TaskType.entries.filter { it in availableTypes }
    }
}

