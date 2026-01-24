package com.coooolfan.unirhy.service.task

import kotlin.reflect.KClass
import kotlin.reflect.full.safeCast

interface TaskService<T : TaskRequest> {
    val requestClass: KClass<T>

    val taskType: TaskType
        get() = TaskRequestTypes.typeOf(requestClass)

    fun executeTask(request: T)

    fun execute(request: TaskRequest) {
        val typedRequest = requestClass.safeCast(request) ?: error(
            "Task request type mismatch for $taskType: expected ${requestClass.simpleName}, got ${request::class.simpleName}",
        )
        executeTask(typedRequest)
    }
}

data class TaskInfo(val taskType: TaskType)
