package com.coooolfan.unirhy.service.task.common

fun failureReason(ex: Throwable): String {
    val simpleName = ex::class.simpleName ?: ex.javaClass.name
    val message = ex.message?.trim().orEmpty()
    return if (message.isBlank()) {
        "FAILED: $simpleName"
    } else {
        "FAILED: $simpleName: $message"
    }
}
