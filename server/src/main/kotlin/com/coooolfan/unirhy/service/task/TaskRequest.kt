package com.coooolfan.unirhy.service.task

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import kotlin.reflect.KClass

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "taskType",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ScanTaskRequest::class, name = "SCAN"),
)
sealed interface TaskRequest

object TaskRequestTypes {
    // 咋这么复杂？
    private val classToType: Map<KClass<out TaskRequest>, TaskType> = run {
        val annotation = TaskRequest::class.java.getAnnotation(JsonSubTypes::class.java)
            ?: error("JsonSubTypes annotation is required on TaskRequest")
        annotation.value.associate { type ->
            val taskType = TaskType.valueOf(type.name)
            @Suppress("UNCHECKED_CAST")
            (type.value as KClass<out TaskRequest>) to taskType
        }
    }

    fun typeOf(requestClass: KClass<out TaskRequest>): TaskType {
        return requireNotNull(classToType[requestClass]) {
            "TaskType not found for request class: ${requestClass.simpleName}"
        }
    }

    fun typeOf(request: TaskRequest): TaskType {
        return typeOf(request::class)
    }
}
