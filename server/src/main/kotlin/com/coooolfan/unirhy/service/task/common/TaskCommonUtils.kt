package com.coooolfan.unirhy.service.task.common

import org.slf4j.LoggerFactory
import java.io.File

private val logger = LoggerFactory.getLogger("TaskCommonUtils")

private val ACCEPT_FILE_EXTENSIONS = setOf("mp3", "wav", "ogg", "flac", "aac", "wma", "m4a")

fun findAudioFilesRecursively(rootDir: File): List<File> {
    if (!rootDir.exists() || !rootDir.isDirectory) {
        logger.warn("Directory not found or is not a directory: ${rootDir.absolutePath}")
        return emptyList()
    }

    return rootDir.walk()
        .filter { it.isFile }
        .filter { file -> file.extension.lowercase() in ACCEPT_FILE_EXTENSIONS }
        .toList()
}
