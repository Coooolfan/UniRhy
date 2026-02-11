package com.unirhy.e2e.support

import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.createDirectories

object ScanSamplePreparer {
    private const val ENV_SCAN_PATH = "E2E_SCAN_PATH"
    private const val DEFAULT_SCAN_PATH = "~/Music"
    private val ACCEPT_SCAN_EXTENSIONS = setOf("mp3", "wav", "ogg", "flac", "aac", "wma", "m4a")

    fun prepare(scanWorkspace: Path): ScanSample {
        val sourceRoot = resolveScanSourceRoot()
        val sourceAudio = findFirstAudioFile(sourceRoot)
        val extension = sourceAudio.fileName.toString().substringAfterLast('.', "").lowercase()
        val runId = UUID.randomUUID().toString().replace("-", "").take(12)
        val relativeObjectKey = "samples/$runId.$extension"
        val sampleFile = scanWorkspace.resolve(relativeObjectKey)
        sampleFile.parent?.createDirectories()
        Files.copy(sourceAudio, sampleFile)

        val sampleSize = Files.size(sampleFile)
        require(sampleSize > 0) {
            "Selected audio file is empty: ${sourceAudio.toAbsolutePath()}"
        }

        return ScanSample(
            file = sampleFile,
            relativeObjectKey = relativeObjectKey,
            size = sampleSize,
        )
    }

    private fun resolveScanSourceRoot(): Path {
        val rawPath = System.getenv(ENV_SCAN_PATH)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_SCAN_PATH
        val resolved = rawPath.expandHomePath().toAbsolutePath().normalize()
        require(Files.exists(resolved) && Files.isDirectory(resolved)) {
            "Scan source path does not exist or is not a directory: $resolved. " +
                    "Please set $ENV_SCAN_PATH to a valid local music directory."
        }
        return resolved
    }

    private fun findFirstAudioFile(root: Path): Path {
        Files.walk(root).use { paths ->
            val sample = paths
                .filter { Files.isRegularFile(it) }
                .filter { path ->
                    val ext = path.fileName.toString().substringAfterLast('.', "").lowercase()
                    ACCEPT_SCAN_EXTENSIONS.contains(ext)
                }
                .findFirst()
            require(sample.isPresent) {
                "No supported audio file found under $root. " +
                        "Supported: ${ACCEPT_SCAN_EXTENSIONS.joinToString(", ")}"
            }
            return sample.get()
        }
    }
}

data class ScanSample(
    val file: Path,
    val relativeObjectKey: String,
    val size: Long,
) {
    fun readPrefixBytes(size: Int): ByteArray {
        return Files.newInputStream(file).use { input ->
            input.readNBytes(size)
        }
    }
}
