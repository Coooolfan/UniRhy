package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.model.*
import com.coooolfan.unirhy.model.storage.FileProviderType
import com.coooolfan.unirhy.service.storage.StorageNode
import com.coooolfan.unirhy.service.storage.StorageNodeObjectService
import com.coooolfan.unirhy.service.storage.bindProvider
import com.coooolfan.unirhy.service.task.common.AsyncTaskQueueStore
import com.coooolfan.unirhy.service.task.common.TaskStatus
import com.coooolfan.unirhy.service.task.common.TaskType
import com.coooolfan.unirhy.service.task.common.failureReason
import tools.jackson.databind.ObjectMapper
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
    private val storageObjects: StorageNodeObjectService,
) {

    private val logger = LoggerFactory.getLogger(TranscodeTaskService::class.java)
    private val consumerEnabled = detectFfmpegAvailability()

    fun isConsumerEnabled(): Boolean = consumerEnabled

    fun submit(request: TranscodeTaskRequest) {
        val (srcProvider, dstProvider) = resolveAndValidateProviders(request)
        val audioFiles = sql.createQuery(Asset::class) {
            when (srcProvider.providerType) {
                FileProviderType.FILE_SYSTEM -> where(table.mediaFile.fsProviderId eq srcProvider.providerId)
                FileProviderType.OSS -> where(table.mediaFile.ossProviderId eq srcProvider.providerId)
            }
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
                srcProviderType = srcProvider.providerType,
                srcProviderId = srcProvider.providerId,
                dstProviderType = dstProvider.providerType,
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
                for (claimedTask in claimedTasks) {
                    val (status, reason) = try {
                        val payload = objectMapper.readValue(claimedTask.params, TranscodeTaskPayload::class.java)
                        execute(payload)
                        TaskStatus.COMPLETED to "SUCCESS"
                    } catch (ex: Throwable) {
                        logger.error("Transcode task failed, logId={}", claimedTask.id, ex)
                        TaskStatus.FAILED to failureReason(ex)
                    }
                    queueStore.completeTask(claimedTask.id, status, reason)
                }
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
        val (srcProvider, dstProvider) = resolveProviders(
            payload.srcProviderType,
            payload.srcProviderId,
            payload.dstProviderType,
            payload.dstProviderId,
        )
        val dstObjectKey = "${UUID.randomUUID()}.opus"
        val outputFile = createOutputFile()

        try {
            storageObjects.materializeTempFile(srcProvider, payload.srcObjectKey).use { srcTempFile ->
                executeFfmpegCommand(
                    command = buildFfmpegCommand(srcTempFile.file, outputFile),
                    srcFile = srcTempFile.file,
                    outputFile = outputFile,
                )
            }

            storageObjects.writeFile(dstProvider, dstObjectKey, outputFile, "audio/opus")

            sql.saveCommand(Asset {
                recording = Recording { id = payload.recordingId }
                mediaFile {
                    objectKey = dstObjectKey
                    mimeType = "audio/opus"
                    size = outputFile.length()
                    width = null
                    height = null
                    bindProvider(dstProvider)
                }
                comment = "transcoded from ${payload.srcObjectKey} at ${srcProvider.name}"
            }) {
                setMode(SaveMode.INSERT_ONLY)
                setAssociatedMode(Asset::recording, AssociatedSaveMode.APPEND_IF_ABSENT)
                setAssociatedMode(MediaFile::fsProvider, AssociatedSaveMode.APPEND_IF_ABSENT)
                setAssociatedMode(MediaFile::ossProvider, AssociatedSaveMode.APPEND_IF_ABSENT)
            }.execute()
        } finally {
            deleteOutputFileQuietly(outputFile)
        }
    }

    private fun createOutputFile(): File {
        val outputFile = File.createTempFile("unirhy-transcode-", ".opus")
        if (!outputFile.delete()) {
            error("Failed to prepare transcode output file: ${outputFile.absolutePath}")
        }
        return outputFile
    }

    private fun resolveAndValidateProviders(request: TranscodeTaskRequest): Pair<StorageNode, StorageNode> {
        if (request.targetCodec != CodecType.OPUS) {
            error("Unsupported target codec: ${request.targetCodec}")
        }

        val srcProvider = storageObjects.resolve(request.srcProviderType, request.srcProviderId)
        val dstProvider = storageObjects.resolve(request.dstProviderType, request.dstProviderId)

        if (dstProvider.readonly) {
            error("Destination provider is readonly")
        }
        return Pair(srcProvider, dstProvider)
    }

    private fun resolveProviders(
        srcProviderType: FileProviderType,
        srcProviderId: Long,
        dstProviderType: FileProviderType,
        dstProviderId: Long,
    ): Pair<StorageNode, StorageNode> {
        val srcProvider = storageObjects.resolve(srcProviderType, srcProviderId)
        val dstProvider = storageObjects.resolve(dstProviderType, dstProviderId)
        if (dstProvider.readonly) {
            error("Destination provider is readonly")
        }
        return Pair(srcProvider, dstProvider)
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
        if (result.first == 0 && outputFile.isFile && outputFile.length() > 0) {
            return
        }

        deleteOutputFileQuietly(outputFile)
        if (result.first == 0) {
            logger.error(
                "ffmpeg produced empty output, src={}, dst={}, output={}",
                srcFile.absolutePath,
                outputFile.absolutePath,
                result.second.ifBlank { "<empty>" },
            )
            error(
                "Failed to transcode src=${srcFile.absolutePath} dst=${outputFile.absolutePath} " +
                        "error=ffmpeg produced empty output"
            )
        }

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
    val srcProviderType: FileProviderType = FileProviderType.FILE_SYSTEM,
    val srcProviderId: Long,
    val dstProviderType: FileProviderType = FileProviderType.FILE_SYSTEM,
    val dstProviderId: Long,
    val targetCodec: CodecType = CodecType.OPUS,
)

enum class CodecType {
    MP3, OPUS, AAC,
}
