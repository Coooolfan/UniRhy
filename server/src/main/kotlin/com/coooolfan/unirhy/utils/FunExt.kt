package com.coooolfan.unirhy.utils

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(8192) // 8KB 缓冲区

    FileInputStream(this).use { fis ->
        var bytesRead: Int
        while (fis.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
    }

    return digest.digest().joinToString("") { "%02x".format(it) }
}