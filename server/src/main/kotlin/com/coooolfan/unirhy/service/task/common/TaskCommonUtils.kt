package com.coooolfan.unirhy.service.task.common

import com.coooolfan.unirhy.service.isAudioObjectKey
import org.slf4j.LoggerFactory
import java.io.File

private val logger = LoggerFactory.getLogger("TaskCommonUtils")

fun findAudioFilesRecursively(rootDir: File): Sequence<File> {
    if (!rootDir.exists() || !rootDir.isDirectory) {
        logger.warn("Directory not found or is not a directory: ${rootDir.absolutePath}")
        return emptySequence()
    }

    return rootDir.walk()
        .filter { it.isFile }
        .filter { file -> isAudioObjectKey(file.name) }
}

fun failureReason(ex: Throwable): String {
    val simpleName = ex::class.simpleName ?: ex.javaClass.name
    val message = ex.message?.trim().orEmpty()
    return if (message.isBlank()) {
        "FAILED: $simpleName"
    } else {
        "FAILED: $simpleName: $message"
    }
}
