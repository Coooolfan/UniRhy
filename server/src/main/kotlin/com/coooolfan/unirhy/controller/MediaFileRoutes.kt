package com.coooolfan.unirhy.controller

object MediaFileRoutes {
    const val MEDIA_API_BASE_PATH = "/api/media"
    const val MEDIA_FILE_PATH_PATTERN = "/{id}"

    fun mediaFilePath(id: Long): String = "$MEDIA_API_BASE_PATH/$id"
}
