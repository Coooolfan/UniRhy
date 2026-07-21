package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.model.*
import com.coooolfan.unirhy.model.storage.FileProviderType
import com.coooolfan.unirhy.service.SystemConfigService.Companion.SYSTEM_CONFIG_ID
import com.coooolfan.unirhy.service.storage.StorageNode
import com.coooolfan.unirhy.service.storage.StorageNodeObjectService
import com.coooolfan.unirhy.service.storage.bindProvider
import com.coooolfan.unirhy.service.storage.resolveWriteableStorageNode
import com.coooolfan.unirhy.service.task.spi.AsyncTaskHandler
import com.coooolfan.unirhy.service.task.spi.TaskPlanner
import com.coooolfan.unirhy.service.task.common.TaskKey
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import org.babyfish.jimmer.sql.ast.mutation.AssociatedSaveMode
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.exception.SaveException
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.Artwork
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.io.File
import java.nio.file.Files
import java.time.LocalDate

/**
 * 内建元数据扫描的规划器：解析 submission 参数、遍历存储节点并产出扫描 payload。
 */
@Component
class ScanTaskPlanner(
    private val objectMapper: ObjectMapper,
    private val storageObjects: StorageNodeObjectService,
) : TaskPlanner {

    override val key: TaskKey = BuiltInTasks.METADATA_PARSE

    override fun plan(params: JsonNode): Sequence<JsonNode> {
        val request = objectMapper.treeToValue(params, ScanTaskRequest::class.java)
        val provider = storageObjects.resolve(request.providerType, request.providerId)
        return discoverScanFileTaskPayloads(provider).map { objectMapper.valueToTree(it) }
    }
}

/**
 * 内建元数据扫描的执行器：下载源文件、解析音频元数据并保存 Recording。
 */
@Component
class ScanTaskHandler(
    private val sql: KSqlClient,
    private val objectMapper: ObjectMapper,
    private val storageObjects: StorageNodeObjectService,
    private val jdbc: NamedParameterJdbcTemplate,
    transactionManager: PlatformTransactionManager,
) : AsyncTaskHandler {

    private val logger = LoggerFactory.getLogger(ScanTaskHandler::class.java)

    /** 嵌套事务（savepoint）用于保存重试：唯一键冲突后回滚到 savepoint 再重试 */
    private val nestedTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_NESTED
    }

    override val key: TaskKey = BuiltInTasks.METADATA_PARSE

    override fun run(payload: JsonNode) {
        val scanPayload = objectMapper.treeToValue(payload, ScanFileTaskPayload::class.java)
        val provider = storageObjects.resolve(scanPayload.providerType, scanPayload.providerId)
        val writeableProvider = sql.findOneById(SYSTEM_CONFIG_FETCHER, SYSTEM_CONFIG_ID)
            .resolveWriteableStorageNode(storageObjects)

        val stat = runCatching { storageObjects.stat(provider, scanPayload.objectKey) }.getOrNull()
        if (stat == null) {
            logger.info(
                "Skip scan task because source file is missing, providerId={}, objectKey={}",
                provider.providerId,
                scanPayload.objectKey,
            )
            return
        }

        if (audioMediaFileExists(provider.providerType, provider.providerId, scanPayload.objectKey)) {
            logger.info(
                "Skip scan task because media file already scanned, providerId={}, objectKey={}",
                provider.providerId,
                scanPayload.objectKey,
            )
            return
        }

        val scannedRecording = storageObjects.materializeTempFile(provider, scanPayload.objectKey).use { tempFile ->
            buildRecordingFromAudioFile(
                file = tempFile.file,
                objectKey = scanPayload.objectKey,
                provider = provider,
                writeableProvider = writeableProvider,
                storageObjects = storageObjects,
                size = stat.size,
            )
        }
        saveScannedRecordingWithRetry(scannedRecording, scanPayload.objectKey)
    }

    /**
     * 并发 Worker 可能同时按 key 创建相同的 Work / Album / Artist，而 Album / Artist
     * 没有数据库唯一约束，APPEND_IF_ABSENT 的查询-插入窗口会产生重复行。
     * 通过 advisory xact lock 串行化保存段（下载与元数据解析仍然并行），
     * 锁随任务事务提交释放，保证后来的保存能看到先提交的行。
     * Work 存在唯一键，与已提交事务的冲突通过回滚到 savepoint 后重试解决。
     */
    private fun saveScannedRecordingWithRetry(recording: Recording, objectKey: String) {
        jdbc.queryForObject(
            "SELECT pg_advisory_xact_lock(:key)",
            MapSqlParameterSource("key", SCAN_SAVE_ADVISORY_LOCK_KEY),
            Any::class.java,
        )
        var attempt = 1
        while (true) {
            try {
                nestedTransaction.executeWithoutResult { saveScannedRecording(recording) }
                return
            } catch (ex: SaveException.NotUnique) {
                if (attempt >= MAX_SAVE_ATTEMPTS) {
                    throw ex
                }
                logger.info(
                    "Retrying scan save after key conflict, objectKey={}, attempt={}: {}",
                    objectKey, attempt, ex.message,
                )
                attempt++
            }
        }
    }

    private fun saveScannedRecording(recording: Recording) {
        sql.saveEntities(listOf(recording)) {
            setMode(SaveMode.INSERT_ONLY)
            setAssociatedModeAll(AssociatedSaveMode.APPEND)
            setAssociatedMode(Recording::work, AssociatedSaveMode.APPEND_IF_ABSENT)
            setAssociatedMode(Asset::mediaFile, AssociatedSaveMode.APPEND_IF_ABSENT)
            setAssociatedMode(MediaFile::fsProvider, AssociatedSaveMode.APPEND_IF_ABSENT)
            setAssociatedMode(MediaFile::ossProvider, AssociatedSaveMode.APPEND_IF_ABSENT)
            setAssociatedMode(Album::cover, AssociatedSaveMode.APPEND_IF_ABSENT)
            setAssociatedMode(Recording::cover, AssociatedSaveMode.APPEND_IF_ABSENT)
            setAssociatedMode(Recording::artists, AssociatedSaveMode.APPEND_IF_ABSENT)
            setAssociatedMode(Recording::albumRecordings, AssociatedSaveMode.APPEND_IF_ABSENT)
            setAssociatedMode(AlbumRecording::album, AssociatedSaveMode.APPEND_IF_ABSENT)
        }
    }

    private fun audioMediaFileExists(providerType: FileProviderType, providerId: Long, objectKey: String): Boolean {
        return sql.createQuery(MediaFile::class) {
            where(table.objectKey eq objectKey)
            when (providerType) {
                FileProviderType.FILE_SYSTEM -> where(table.fsProviderId eq providerId)
                FileProviderType.OSS -> where(table.ossProviderId eq providerId)
            }
            selectCount()
        }.execute().first() > 0
    }

    private companion object {
        private const val MAX_SAVE_ATTEMPTS = 3

        /** 全局扫描保存锁的 advisory lock key（任意固定值，仅本用途使用） */
        private const val SCAN_SAVE_ADVISORY_LOCK_KEY = 7_235_923_001_764_291_913L
    }
}

private val COVER_EXTENSIONS = hashSetOf("jpg", "jpeg", "png", "gif")
private val TAG_DATE_REGEX = Regex("""(\d{4})(?:[-./](\d{1,2})(?:[-./](\d{1,2}))?)?""")

data class ScanTaskRequest(
    val providerType: FileProviderType,
    val providerId: Long,
)

data class ScanFileTaskPayload(
    val providerType: FileProviderType,
    val providerId: Long,
    val objectKey: String,
)

fun discoverScanFileTaskPayloads(provider: StorageNode): Sequence<ScanFileTaskPayload> {
    return sequence {
        for (objectKey in provider.listAudioObjectKeys()) {
            yield(
                ScanFileTaskPayload(
                    providerType = provider.providerType,
                    providerId = provider.providerId,
                    objectKey = objectKey,
                )
            )
        }
    }
}

private fun buildRecordingFromAudioFile(
    file: File,
    objectKey: String,
    provider: StorageNode,
    writeableProvider: StorageNode,
    storageObjects: StorageNodeObjectService,
    size: Long,
): Recording {
    val audioTag = AudioFileIO.read(file)
    val tag = audioTag.tag
    val audioCover = fetchCover(objectKey, provider, writeableProvider, storageObjects, tag?.firstArtwork)
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
                this.size = size
                width = null
                height = null
                bindProvider(provider)
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
    sourceObjectKey: String,
    provider: StorageNode,
    writeableProvider: StorageNode,
    storageObjects: StorageNodeObjectService,
    artwork: Artwork?,
): MediaFile? {
    val sourceBaseName = sourceObjectKey.substringBeforeLast('.', sourceObjectKey)
    for (ext in COVER_EXTENSIONS) {
        val coverObjectKey = "$sourceBaseName.$ext"
        val stat = runCatching { storageObjects.stat(provider, coverObjectKey) }
            .recoverCatching { storageObjects.stat(provider, "$sourceBaseName.${ext.uppercase()}") }
            .getOrNull() ?: continue
        return MediaFile {
            objectKey = stat.objectKey
            mimeType = mimeFromExtension(ext)
            size = stat.size
            width = null
            height = null
            bindProvider(provider)
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
    val objectKey = embeddedCoverObjectKey(sourceObjectKey, provider, extension)

    if (runCatching { storageObjects.stat(targetProvider, objectKey) }.isFailure) {
        storageObjects.write(targetProvider, objectKey, binaryData, mimeType)
    }

    return MediaFile {
        this.objectKey = objectKey
        this.mimeType = mimeType
        this.size = binaryData.size.toLong()
        this.width = safeArtwork.width.takeIf { it > 0 }
        this.height = safeArtwork.height.takeIf { it > 0 }
        bindProvider(targetProvider)
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
    sourceObjectKey: String,
    provider: StorageNode,
    extension: String,
): String {
    val coverObjectKey = sourceObjectKey.substringBeforeLast('.', sourceObjectKey)
    return "covers/${provider.providerType.name.lowercase()}-${provider.providerId}/$coverObjectKey.$extension"
}

private fun mimeFromExtension(extension: String): String =
    when (extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        else -> "application/octet-stream"
    }

private val SYSTEM_CONFIG_FETCHER = newFetcher(SystemConfig::class).by {
    ossProvider {
        allScalarFields()
    }
    fsProvider {
        allScalarFields()
    }
}
