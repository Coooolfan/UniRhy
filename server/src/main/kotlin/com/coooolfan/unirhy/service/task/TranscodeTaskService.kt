package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.model.*
import com.coooolfan.unirhy.model.storage.FileProviderFileSystem
import com.coooolfan.unirhy.model.storage.FileProviderType
import com.coooolfan.unirhy.service.task.common.AsyncTaskQueueStore
import com.coooolfan.unirhy.service.task.common.TaskStatus
import com.coooolfan.unirhy.service.task.common.TaskType
import com.coooolfan.unirhy.service.task.common.failureReason
import com.fasterxml.jackson.databind.ObjectMapper
import org.babyfish.jimmer.sql.ast.mutation.AssociatedSaveMode
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.io.File
import java.io.IOException
import java.util.*

@Service
class TranscodeTaskService(
    private val sql: KSqlClient,
    private val objectMapper: ObjectMapper,
    private val queueStore: AsyncTaskQueueStore,
    private val transactionTemplate: TransactionTemplate,
) {

    private val logger = LoggerFactory.getLogger(TranscodeTaskService::class.java)
    private val consumerEnabled = detectFfmpegAvailability()

    fun isConsumerEnabled(): Boolean = consumerEnabled

    fun submit(request: TranscodeTaskRequest) {
        val (srcProvider, _) = resolveAndValidateProviders(request)
        val audioFiles = sql.createQuery(Asset::class) {
            where(table.mediaFile.fsProviderId eq srcProvider.id)
            orderBy(table.recordingId, table.mediaFile.objectKey, table.id)
            select(table.recordingId, table.mediaFile.objectKey)
        }.execute()

        val recordingAssetMap = linkedMapOf<Long, String>()
        for ((recordingId, objectKey) in audioFiles) {
            recordingAssetMap.putIfAbsent(recordingId, objectKey)
        }

        val payloads = recordingAssetMap.entries.map { entry ->
            TranscodeTaskPayload(
                recordingId = entry.key,
                srcObjectKey = entry.value,
                srcProviderId = srcProvider.id,
                dstProviderId = request.dstProviderId,
                targetCodec = request.targetCodec,
            )
        }
        val paramsJsonList = payloads.map(objectMapper::writeValueAsString)
        queueStore.enqueueIgnoringConflicts(TaskType.TRANSCODE, paramsJsonList)
    }

    fun consumePendingTask() {
        if (!consumerEnabled) {
            return
        }
        try {
            transactionTemplate.executeWithoutResult {
                val claimedTasks = queueStore.claim(TaskType.TRANSCODE, TRANSCODE_CLAIM_LIMIT)
                if (claimedTasks.isEmpty()) {
                    return@executeWithoutResult
                }
                val completionUpdates = mutableListOf<AsyncTaskLog>()
                for (claimedTask in claimedTasks) {
                    try {
                        val payload = objectMapper.readValue(claimedTask.params, TranscodeTaskPayload::class.java)
                        execute(payload)
                        completionUpdates +=
                            AsyncTaskLog {
                                id = claimedTask.id
                                status = TaskStatus.COMPLETED
                                completedReason = "SUCCESS"
                            }
                    } catch (ex: Throwable) {
                        logger.error("Transcode task failed, logId={}", claimedTask.id, ex)
                        completionUpdates +=
                            AsyncTaskLog {
                                id = claimedTask.id
                                status = TaskStatus.FAILED
                                completedReason = failureReason(ex)
                            }
                    }
                }
                sql.saveEntities(completionUpdates, SaveMode.UPDATE_ONLY)
            }
        } catch (ex: Throwable) {
            logger.error("Failed to consume pending transcode task", ex)
        }
    }

    private fun detectFfmpegAvailability(): Boolean {
        val result = try {
            runProcess(listOf("ffmpeg", "-version"))
        } catch (ex: IOException) {
            logger.error("ffmpeg is unavailable or not in PATH", ex)
            return false
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.error("ffmpeg availability check interrupted", ex)
            return false
        }
        if (result.first != 0) {
            logger.error(
                "ffmpeg availability check failed, exitCode={}, output={}",
                result.first,
                result.second.ifBlank { "<empty>" },
            )
            return false
        }
        return true
    }

    private fun execute(payload: TranscodeTaskPayload) {
        if (payload.targetCodec != CodecType.OPUS) {
            error("Unsupported target codec: ${payload.targetCodec}")
        }
        val (srcProvider, dstProvider) = resolveProviders(payload.srcProviderId, payload.dstProviderId)
        val srcRoot = File(srcProvider.parentPath)
        val dstRoot = ensureDestinationRoot(dstProvider)
        val srcFile = File(srcRoot, payload.srcObjectKey)
        val outputFile = File(dstRoot, "${UUID.randomUUID()}.opus")

        executeFfmpegCommand(
            command = buildFfmpegCommand(srcFile, outputFile),
            srcFile = srcFile,
            outputFile = outputFile,
        )

        sql.saveCommand(Asset {
            recording = Recording { id = payload.recordingId }
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
            comment = "transcoded from ${payload.srcObjectKey} at ${srcProvider.name}"
        }) {
            setMode(SaveMode.INSERT_ONLY)
            setAssociatedMode(Asset::recording, AssociatedSaveMode.APPEND_IF_ABSENT)
            setAssociatedMode(MediaFile::fsProvider, AssociatedSaveMode.APPEND_IF_ABSENT)
        }.execute()
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

    private fun resolveProviders(
        srcProviderId: Long,
        dstProviderId: Long,
    ): Pair<FileProviderFileSystem, FileProviderFileSystem> {
        val srcDstProviders = sql.findByIds(FileProviderFileSystem::class, listOf(srcProviderId, dstProviderId))
        val srcProvider = srcDstProviders.find { it.id == srcProviderId } ?: error("Source provider not found")
        val dstProvider = srcDstProviders.find { it.id == dstProviderId } ?: error("Destination provider not found")
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
            "-nostdin", // 禁止从标准输入读取
            "-v", "error", // 只输出 error 级别日志
            "-i", srcFile.absolutePath,
            "-map", "0:a:0", // 只选择第一路音频流作为转码输入
            "-vn", // 丢弃所有视频流
            "-sn", // 丢弃所有字幕流
            "-dn", // 丢弃所有 data 流
            "-c:a", "libopus", // 音频编码器固定使用 libopus
            "-b:a", "128k", // 目标平均码率 128 kbps
            "-vbr", "on", // 启用可变码率
            "-application", "audio", // 按音乐/通用音频场景优化 Opus 编码
            "-compression_level", "10", // 固定 20ms 帧长，兼顾兼容性与压缩效率
            "-frame_duration", "20", // 去掉容器级元数据
            "-map_metadata", "-1", // 去掉流级元数据
            "-map_metadata:s", "-1", // 去掉章节信息
            "-map_chapters", "-1", // 指定输出文件路径
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
        private const val TRANSCODE_CLAIM_LIMIT = 1L
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

data class TranscodeTaskPayload(
    val recordingId: Long,
    val srcObjectKey: String,
    val srcProviderId: Long,
    val dstProviderId: Long,
    val targetCodec: CodecType = CodecType.OPUS,
)

enum class CodecType {
    MP3, OPUS, AAC,
}
