package com.coooolfan.unirhy.service

internal val AUDIO_EXTENSIONS = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma")

fun isAudioObjectKey(objectKey: String): Boolean {
    return objectKey.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS
}
