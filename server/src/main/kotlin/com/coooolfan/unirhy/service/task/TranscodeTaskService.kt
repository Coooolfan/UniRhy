package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.model.*
import com.coooolfan.unirhy.model.storage.FileProviderFileSystem
import com.coooolfan.unirhy.model.storage.FileProviderType
import com.coooolfan.unirhy.service.task.common.AsyncTaskManager
import com.coooolfan.unirhy.service.task.common.TaskService
import com.coooolfan.unirhy.service.task.common.TaskType
import org.babyfish.jimmer.sql.ast.mutation.AssociatedSaveMode
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.io.IOException
import java.util.*

@Service
class TranscodeTaskService(
    private val sql: KSqlClient,
    asyncTaskManager: AsyncTaskManager,
) : TaskService<TranscodeTaskRequest>(
    asyncTaskManager
) {
    override val type: TaskType = TaskType.TRANSCODE

    private val logger = LoggerFactory.getLogger(TranscodeTaskService::class.java)

    override fun execute(request: TranscodeTaskRequest) {
        val (srcProvider, dstProvider) = resolveAndValidateProviders(request)

        validateFfmpeg()

        val srcRoot = File(srcProvider.parentPath)
        val dstRoot = ensureDestinationRoot(dstProvider)
//        val audioFiles = findAudioFilesRecursively(srcRoot)
//        val audioFiles = sql.executeQuery(MediaFile::class) {
//            where(table.fsProvider eq srcProvider)
//            select(table.objectKey,table.)
//        }.map { File(srcRoot, it) }

        val audioFiles = sql.executeQuery(Asset::class) {
            where(table.mediaFile.fsProviderId eq srcProvider.id)
            select(table.recordingId, table.mediaFile.objectKey)
        }

        // TODO: 这里需要考虑单个 Recording 下在同一个 Provider 下有多个 Asset 的情况
        val recordingAssetMap = mutableMapOf<Long, String>()
        for ((recordingId, objectKey) in audioFiles) {
            recordingAssetMap[recordingId] = objectKey
        }

        if (recordingAssetMap.isEmpty()) {
            logger.info(
                "No audio files found for transcode task, srcProviderId={}, srcRoot={}, dstRoot={}",
                srcProvider.id,
                srcRoot.absolutePath,
                dstRoot.absolutePath,
            )
            return
        }

        // libopus 本身已经几乎可以把 CPU 占满，所以这里不需要并发执行 ffmpeg 命令
        for ((index, entry) in recordingAssetMap.entries.withIndex()) {
            val recordingId = entry.key
            val srcObjectKey = entry.value

            val file = File(srcRoot, srcObjectKey)
            val outputFile = File(dstRoot, "${UUID.randomUUID()}.opus")
            logger.info(
                "Transcoding file {}/{}: src={}, dst={}",
                index + 1,
                recordingAssetMap.size,
                file.relativeTo(srcRoot).path,
                outputFile.name,
            )
            executeFfmpegCommand(
                command = buildFfmpegCommand(file, outputFile),
                srcFile = file,
                outputFile = outputFile,
            )

            sql.saveCommand(Asset {
                recording = Recording { id = recordingId }
                mediaFile {
                    sha256 = "mocked-sha256"
                    objectKey = outputFile.toString()
                    mimeType = "audio/opus"
                    size = outputFile.length()
                    width = null
                    height = null
                    ossProvider = null
                    fsProvider = dstProvider
                }
                comment = "transcoded from ${srcObjectKey} at ${srcProvider.name}"
            }) {
                setMode(SaveMode.INSERT_ONLY)
                setAssociatedMode(Asset::recording, AssociatedSaveMode.APPEND_IF_ABSENT)
                setAssociatedMode(MediaFile::fsProvider, AssociatedSaveMode.APPEND_IF_ABSENT)
            }.execute()
        }

        logger.info(
            "Transcode task completed, srcProviderId={}, dstProviderId={}, totalFiles={}, dstRoot={}",
            srcProvider.id,
            dstProvider.id,
            recordingAssetMap.size,
            dstRoot.absolutePath,
        )
    }

    private fun validateFfmpeg() {
        val result = try {
            runProcess(listOf("ffmpeg", "-version"))
        } catch (ex: IOException) {
            throw IllegalStateException("ffmpeg is unavailable or not in PATH", ex)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("ffmpeg is unavailable or not in PATH", ex)
        }
        if (result.first != 0) {
            logger.error(
                "ffmpeg availability check failed, exitCode={}, output={}",
                result.first,
                result.second.ifBlank { "<empty>" },
            )
            error("ffmpeg is unavailable or not in PATH")
        }
    }

    private fun resolveAndValidateProviders(request: TranscodeTaskRequest): Pair<FileProviderFileSystem, FileProviderFileSystem> {
        if (request.targetCodec != CodecType.OPUS) {
            error("Unsupported target codec: ${request.targetCodec}")
        }
        if (request.srcProviderType != FileProviderType.FILE_SYSTEM) {
            error("Unsupported source provider type: ${request.srcProviderType}")
        }
        if (request.dstProviderType != FileProviderType.FILE_SYSTEM) {
            error("Unsupported destination provider type: ${request.dstProviderType}")
        }

        // 这里写俩条 sql 的话代码会简单点。但还是省一次 IO 吧
        val srcDstProviders =
            sql.findByIds(FileProviderFileSystem::class, listOf(request.srcProviderId, request.dstProviderId))
        val srcProvider = srcDstProviders.find { it.id == request.srcProviderId } ?: error("Source provider not found")
        val dstProvider =
            srcDstProviders.find { it.id == request.dstProviderId } ?: error("Destination provider not found")

        if (dstProvider.readonly) {
            error("Destination provider is readonly")
        }
        return Pair(srcProvider, dstProvider)
    }

    private fun ensureDestinationRoot(provider: FileProviderFileSystem): File {
        val dstRoot = File(provider.parentPath)
        if (dstRoot.exists() && !dstRoot.isDirectory) {
            error("Destination path is not a directory: ${dstRoot.absolutePath}")
        }
        if (!dstRoot.exists() && !dstRoot.mkdirs()) {
            error("Failed to create destination directory: ${dstRoot.absolutePath}")
        }
        if (!dstRoot.canWrite()) {
            error("Destination directory is not writable: ${dstRoot.absolutePath}")
        }
        return dstRoot
    }

    private fun buildFfmpegCommand(
        srcFile: File,
        outputFile: File,
    ): List<String> {
        return listOf(
            "ffmpeg",
            "-nostdin",
            "-v", "error",
            "-i", srcFile.absolutePath,
            "-map", "0:a:0",
            "-vn",
            "-sn",
            "-dn",
            "-c:a", "libopus",
            "-b:a", "128k",
            "-vbr", "on",
            "-application", "audio",
            "-compression_level", "10",
            "-frame_duration", "20",
            "-map_metadata", "-1",
            "-map_metadata:s", "-1",
            "-map_chapters", "-1",
            outputFile.absolutePath,
        )
    }

    private fun executeFfmpegCommand(
        command: List<String>,
        srcFile: File,
        outputFile: File,
    ) {
        val result = try {
            runProcess(command)
        } catch (ex: IOException) {
            deleteOutputFileQuietly(outputFile)
            throw IllegalStateException("ffmpeg is unavailable or not in PATH", ex)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            deleteOutputFileQuietly(outputFile)
            throw IllegalStateException(
                "Transcode interrupted: src=${srcFile.absolutePath} dst=${outputFile.absolutePath}",
                ex,
            )
        }
        if (result.first == 0) {
            return
        }

        deleteOutputFileQuietly(outputFile)
        logger.error(
            "ffmpeg command failed, src={}, dst={}, exitCode={}, output={}",
            srcFile.absolutePath,
            outputFile.absolutePath,
            result.first,
            result.second.ifBlank { "<empty>" },
        )
        error(
            "Failed to transcode src=${srcFile.absolutePath} dst=${outputFile.absolutePath} " +
                    "exitCode=${result.first} error=${summarizeProcessOutput(result.second)}"
        )
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun runProcess(command: List<String>): Pair<Int, String> {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        return Pair(exitCode, output)
    }

    private fun summarizeProcessOutput(output: String): String {
        val normalized = output.replace(WHITESPACE_REGEX, " ").trim()
        if (normalized.isBlank()) {
            return "ffmpeg exited without diagnostic output"
        }
        return if (normalized.length <= MAX_PROCESS_ERROR_SUMMARY_LENGTH) {
            normalized
        } else {
            normalized.take(MAX_PROCESS_ERROR_SUMMARY_LENGTH) + "..."
        }
    }

    private fun deleteOutputFileQuietly(outputFile: File) {
        if (!outputFile.delete() && outputFile.exists()) {
            logger.warn("Failed to delete incomplete output file: {}", outputFile.absolutePath)
        }
    }

    private companion object {
        private const val MAX_PROCESS_ERROR_SUMMARY_LENGTH = 300
        private val WHITESPACE_REGEX = "\\s+".toRegex()
    }

}

data class TranscodeTaskRequest(
    val srcProviderType: FileProviderType,
    val srcProviderId: Long,
    val dstProviderType: FileProviderType,
    val dstProviderId: Long,
    val targetCodec: CodecType = CodecType.OPUS,
)

enum class CodecType {
    MP3, OPUS, AAC,
}
