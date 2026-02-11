package com.unirhy.e2e.support

import java.nio.file.Files
import java.nio.file.Path

fun String.expandHomePath(): Path {
    if (!startsWith("~/")) {
        return Path.of(this)
    }
    return Path.of(System.getProperty("user.home"), removePrefix("~/"))
}

fun Path.deleteRecursivelyIfExists() {
    if (!Files.exists(this)) {
        return
    }
    Files.walk(this).use { paths ->
        paths.sorted(Comparator.reverseOrder())
            .forEach { Files.deleteIfExists(it) }
    }
}
