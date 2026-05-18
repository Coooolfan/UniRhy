package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.model.*
import com.coooolfan.unirhy.model.storage.FileProviderFileSystem
import com.coooolfan.unirhy.model.storage.FileProviderType
import com.coooolfan.unirhy.service.SystemConfigService.Companion.SYSTEM_CONFIG_ID
import com.coooolfan.unirhy.service.task.common.*
import com.fasterxml.jackson.databind.ObjectMapper
import org.babyfish.jimmer.sql.ast.mutation.AssociatedSaveMode
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.Artwork
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlin.io.path.Path

@Service
class ScanTaskService(
    private val sql: KSqlClient,
    private val objectMapper: ObjectMapper,
    private val queueStore: AsyncTaskQueueStore,
    private val transactionTemplate: TransactionTemplate,
) {

    private val logger = LoggerFactory.getLogger(ScanTaskService::class.java)

    fun submit(request: ScanTaskRequest) {
        val provider = validateRequest(request)

        // 分批插入
        val paramsJsonBatch = mutableListOf<String>()
        for (payload in discoverScanFileTaskPayloads(
            rootDir = File(provider.parentPath),
            providerType = request.providerType,
            providerId = request.providerId,
        )) {
            paramsJsonBatch += objectMapper.writeValueAsString(payload)
            if (paramsJsonBatch.size >= SCAN_ENQUEUE_BATCH_SIZE) {
                queueStore.enqueueIgnoringConflicts(TaskType.METADATA_PARSE, paramsJsonBatch)
                paramsJsonBatch.clear()
            }
        }
        queueStore.enqueueIgnoringConflicts(TaskType.METADATA_PARSE, paramsJsonBatch)
    }

    fun consumePendingTask() {
        try {
            transactionTemplate.executeWithoutResult {
                val claimedTasks = queueStore.claim(TaskType.METADATA_PARSE, METADATA_PARSE_CLAIM_LIMIT)

                for (claimedTask in claimedTasks) {
                    val (status, reason) = try {
                        val payload = objectMapper.readValue(claimedTask.params, ScanFileTaskPayload::class.java)
                        TaskStatus.COMPLETED to execute(payload)
                    } catch (ex: Throwable) {
                        logger.error("Scan task failed, logId={}", claimedTask.id, ex)
                        TaskStatus.FAILED to failureReason(ex)
                    }
                    queueStore.completeTask(claimedTask.id, status, reason)
                }
            }
        } catch (ex: Throwable) {
            logger.error("Failed to consume pending scan task", ex)
        }
    }

    private fun validateRequest(request: ScanTaskRequest): FileProviderFileSystem {
        if (request.providerType != FileProviderType.FILE_SYSTEM) {
            error("Unsupported provider type: ${request.providerType}")
        }
        return sql.findOneById(FileProviderFileSystem::class, request.providerId)
    }

    private fun execute(payload: ScanFileTaskPayload): String {
        val provider = validateRequest(
            ScanTaskRequest(
                providerType = payload.providerType,
                providerId = payload.providerId,
            )
        )
        val writeableProvider = sql.executeQuery(SystemConfig::class) {
            where(table.id eq SYSTEM_CONFIG_ID)
            select(table.fsProvider)
        }.first()

        val rootDir = File(provider.parentPath)
        val file = File(rootDir, payload.objectKey)
        if (!file.exists() || !file.isFile) {
            logger.info(
                "Skip scan task because source file is missing, providerId={}, objectKey={}",
                provider.id,
                payload.objectKey
            )
            return "SKIPPED: source file missing"
        }

        if (audioMediaFileExists(provider.id, payload.objectKey)) {
            return "SKIPPED: media file already scanned"
        }

        val scannedRecording = buildRecordingFromAudioFile(
            file = file,
            objectKey = payload.objectKey,
            provider = provider,
            writeableProvider = writeableProvider,
        )

        sql.saveEntities(listOf(scannedRecording)) {
            setMode(SaveMode.INSERT_ONLY)
            setAssociatedModeAll(AssociatedSaveMode.APPEND)
            setAssociatedMode(Recording::work, AssociatedSaveMode.APPEND_IF_ABSENT)
            setAssociatedMode(Asset::mediaFile, AssociatedSaveMode.APPEND_IF_ABSENT)
            setAssociatedMode(MediaFile::fsProvider, AssociatedSaveMode.APPEND_IF_ABSENT)
            setAssociatedMode(Album::cover, AssociatedSaveMode.APPEND_IF_ABSENT)
            setAssociatedMode(Recording::cover, AssociatedSaveMode.APPEND_IF_ABSENT)
            setAssociatedMode(Recording::artists, AssociatedSaveMode.APPEND_IF_ABSENT)
            setAssociatedMode(Recording::albumRecordings, AssociatedSaveMode.APPEND_IF_ABSENT)
            setAssociatedMode(AlbumRecording::album, AssociatedSaveMode.APPEND_IF_ABSENT)
        }
        return "SUCCESS"
    }

    private fun audioMediaFileExists(providerId: Long, objectKey: String): Boolean {
        return sql.createQuery(MediaFile::class) {
            where(table.objectKey eq objectKey)
            where(table.fsProviderId eq providerId)
            selectCount()
        }.execute().first() > 0
    }

}

private val COVER_EXTENSIONS = hashSetOf("jpg", "jpeg", "png", "gif")
private val TAG_DATE_REGEX = Regex("""(\d{4})(?:[-./](\d{1,2})(?:[-./](\d{1,2}))?)?""")
private const val SCAN_ENQUEUE_BATCH_SIZE = 512
private const val METADATA_PARSE_CLAIM_LIMIT = 10L

data class ScanTaskRequest(
    val providerType: FileProviderType,
    val providerId: Long,
)

data class ScanFileTaskPayload(
    val providerType: FileProviderType,
    val providerId: Long,
    val objectKey: String,
)

fun discoverScanFileTaskPayloads(
    rootDir: File,
    providerType: FileProviderType,
    providerId: Long,
): Sequence<ScanFileTaskPayload> {
    return sequence {
        for (file in findAudioFilesRecursively(rootDir)) {
            val objectKey = file.relativeTo(rootDir).path
            yield(
                ScanFileTaskPayload(
                    providerType = providerType,
                    providerId = providerId,
                    objectKey = objectKey,
                )
            )
        }
    }
}

private fun buildRecordingFromAudioFile(
    file: File,
    objectKey: String,
    provider: FileProviderFileSystem,
    writeableProvider: FileProviderFileSystem,
): Recording {
    val audioTag = AudioFileIO.read(file)
    val tag = audioTag.tag
    val audioCover = fetchCover(file, provider, writeableProvider, tag?.firstArtwork)
    val recordingTitle = tag?.getFirst(FieldKey.TITLE).orEmpty()
    val parsedTitle = parseRecordingTitleMetadata(recordingTitle)
    val labels = (tag?.getFirst(FieldKey.RECORD_LABEL).singleTagValueList() + parsedTitle.labels).distinct()

    return Recording {
        work {
            title = parsedTitle.workTitle
        }
        label = labels
        title = recordingTitle
        comment = tag?.getFirst(FieldKey.COMMENT).orEmpty()
        cover = audioCover
        durationMs = audioTag.audioHeader.preciseTrackLength.toLong() * 1000
        defaultInWork = false
        artists().addBy {
            displayName = tag?.getFirst(FieldKey.ARTIST).orEmpty()
            alias = listOf(tag?.getFirst(FieldKey.ARTIST).orEmpty())
            comment = ""
            avatar = null
        }
        albumRecordings().addBy {
            sortOrder = 0
            album().apply {
                title = tag?.getFirst(FieldKey.ALBUM).orEmpty()
                releaseDate = tag?.albumReleaseDate()
                comment = ""
                cover = audioCover
            }
        }
        assets().addBy {
            comment = ""
            mediaFile {
                this.objectKey = objectKey
                mimeType = Files.probeContentType(file.toPath()) ?: "application/octet-stream"
                size = file.length()
                width = null
                height = null
                ossProvider = null
                fsProvider = provider
            }
        }
    }
}

data class ParsedRecordingTitle(
    val workTitle: String,
    val labels: List<String>,
)

fun parseRecordingTitleMetadata(title: String): ParsedRecordingTitle {
    var remaining = title.trimEnd()
    val reversedGroups = mutableListOf<List<String>>()

    while (remaining.isNotEmpty()) {
        val closeChar = remaining.last()
        val openChar = when (closeChar) {
            ')' -> '('
            '）' -> '（'
            else -> break
        }
        val openIndex = remaining.lastIndexOf(openChar)
        if (openIndex < 0) {
            break
        }

        val content = remaining.substring(openIndex + 1, remaining.lastIndex).trim()
        val labels = content.split(Regex("""\s+"""))
            .map(String::trim)
            .filter(String::isNotBlank)
        if (labels.isNotEmpty()) {
            reversedGroups += labels
        }
        remaining = remaining.substring(0, openIndex).trimEnd()
    }

    return ParsedRecordingTitle(
        workTitle = remaining.trim(),
        labels = reversedGroups.asReversed().flatten(),
    )
}

private fun Tag.albumReleaseDate(): LocalDate? {
    val dateFields = listOf(
        FieldKey.ALBUM_YEAR,
        FieldKey.YEAR,
        FieldKey.ORIGINALRELEASEDATE,
        FieldKey.ORIGINAL_YEAR,
        FieldKey.RECORDINGDATE,
        FieldKey.RECORDINGSTARTDATE,
    )

    return dateFields.asSequence()
        .mapNotNull { getFirst(it).parseTagDate() }
        .firstOrNull()
}

private fun String.parseTagDate(): LocalDate? {
    val normalized = trim()
    if (normalized.isBlank()) {
        return null
    }

    val match = TAG_DATE_REGEX.find(normalized) ?: return null
    val year = match.groupValues[1].toIntOrNull() ?: return null
    val month = match.groupValues[2].toIntOrNull() ?: 1
    val day = match.groupValues[3].toIntOrNull() ?: 1

    return runCatching { LocalDate.of(year, month, day) }.getOrNull()
}

private fun String?.singleTagValueList(): List<String> {
    val value = this?.trim().orEmpty()
    if (value.isBlank()) {
        return emptyList()
    }
    return listOf(value)
}

fun fetchCover(
    file: File,
    provider: FileProviderFileSystem,
    writeableProvider: FileProviderFileSystem,
    artwork: Artwork?
): MediaFile? {
    for (ext in COVER_EXTENSIONS) {
        var coverFile = File(file.parentFile, "${file.nameWithoutExtension}.$ext")
        if (!coverFile.exists()) {
            coverFile = File(file.parentFile, "${file.nameWithoutExtension}.${ext.uppercase()}")
        }
        if (!coverFile.exists()) {
            continue
        }
        return MediaFile {
            objectKey = coverFile.relativeTo(Path(provider.parentPath).toFile()).path
            mimeType = Files.probeContentType(coverFile.toPath())
            size = coverFile.length()
            width = null
            height = null
            ossProvider = null
            fsProvider = provider
        }
    }

    val safeArtwork = artwork ?: return null
    val binaryData = safeArtwork.binaryData ?: return null
    if (binaryData.isEmpty()) {
        return null
    }

    val mimeType = safeArtwork.mimeType?.trim().takeIf { !it.isNullOrBlank() } ?: "image/jpeg"
    val extension = extensionFromMime(mimeType)

    val targetProvider = if (provider.readonly) writeableProvider else provider
    val objectKey = embeddedCoverObjectKey(file, provider, extension)
    val coverFile = File(targetProvider.parentPath, objectKey)

    if (!coverFile.exists()) {
        coverFile.parentFile?.mkdirs()
        coverFile.writeBytes(binaryData)
    }

    return MediaFile {
        this.objectKey = objectKey
        this.mimeType = Files.probeContentType(coverFile.toPath()) ?: mimeType
        this.size = binaryData.size.toLong()
        this.width = safeArtwork.width.takeIf { it > 0 }
        this.height = safeArtwork.height.takeIf { it > 0 }
        this.ossProvider = null
        this.fsProvider = targetProvider
    }
}

private fun extensionFromMime(mimeType: String): String =
    when (mimeType.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/bmp" -> "bmp"
        else -> "jpg"
    }

private fun embeddedCoverObjectKey(
    file: File,
    provider: FileProviderFileSystem,
    extension: String,
): String {
    val sourceObjectKey = file.relativeTo(Path(provider.parentPath).toFile()).path
    val coverObjectKey = sourceObjectKey.substringBeforeLast('.', sourceObjectKey)
    return "covers/${provider.id}/$coverObjectKey.$extension"
}
