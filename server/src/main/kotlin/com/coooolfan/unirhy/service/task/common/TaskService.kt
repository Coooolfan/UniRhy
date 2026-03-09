package com.coooolfan.unirhy.service.task.common

abstract class TaskService<REQUEST : Any>(
    private val asyncTaskManager: AsyncTaskManager
) {
    abstract val type: TaskType

    fun submit(request: REQUEST) {
        asyncTaskManager.submit(type, request) {
            execute(request)
        }
    }

    abstract fun execute(request: REQUEST)

}