package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.model.Recording
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.model.id
import com.coooolfan.unirhy.service.MediaUrlSigner
import com.coooolfan.unirhy.sync.model.AccountCurrentQueueState
import com.coooolfan.unirhy.sync.model.CurrentQueueEntry
import com.coooolfan.unirhy.sync.protocol.CurrentQueueDto
import com.coooolfan.unirhy.sync.protocol.CurrentQueueItemDto
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.ConcurrentHashMap

data class CurrentQueueChangeResult(
    val queue: CurrentQueueDto,
    val previousCurrentEntry: CurrentQueueEntry?,
    val currentEntry: CurrentQueueEntry?,
    val removedEntry: CurrentQueueEntry? = null,
)

data class ResolvedQueueRecording(
    val recordingId: Long,
    val mediaFileId: Long,
    val title: String,
    val artistLabel: String,
    val coverMediaFileId: Long?,
    val durationMs: Long,
)

@Service
class CurrentQueueService(
    private val lockManager: PlaybackAccountLockManager,
    private val recordingCatalog: CurrentQueueRecordingCatalog,
    private val timeProvider: PlaybackSyncTimeProvider,
    private val urlSigner: MediaUrlSigner,
) {
    private val states = ConcurrentHashMap<Long, AccountCurrentQueueState>()

    fun getQueue(accountId: Long): CurrentQueueDto {
        return lockManager.withAccountLock(accountId) {
            buildQueueDto(getOrCreateStateLocked(accountId))
        }
    }

    fun getCurrentEntry(accountId: Long): CurrentQueueEntry? {
        return lockManager.withAccountLock(accountId) {
            val state = getOrCreateStateLocked(accountId)
            state.currentEntryId?.let { currentId -> state.items.firstOrNull { it.entryId == currentId } }
        }
    }

    fun replaceQueue(
        accountId: Long,
        recordings: List<ResolvedQueueRecording>,
        currentIndex: Int,
        nowMs: Long = timeProvider.nowMs(),
    ): CurrentQueueChangeResult {
        if (recordings.isEmpty()) {
            if (currentIndex != 0) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "currentIndex must be 0 for an empty queue")
            }
            return clearQueue(accountId, nowMs)
        }
        if (currentIndex !in recordings.indices) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "currentIndex out of range")
        }

        return lockManager.withAccountLock(accountId) {
            val state = getOrCreateStateLocked(accountId)
            val previousCurrent = state.currentEntryId?.let { currentId -> state.items.firstOrNull { it.entryId == currentId } }
            state.items.clear()
            recordings.forEach { recording ->
                state.items += recording.toQueueEntry(state.nextEntryId++)
            }
            state.currentEntryId = state.items[currentIndex].entryId
            touchState(state, nowMs)
            buildChangeResult(state, previousCurrent = previousCurrent, removedEntry = null)
        }
    }

    fun replaceWithSingleTrack(
        accountId: Long,
        recording: ResolvedQueueRecording,
        nowMs: Long = timeProvider.nowMs(),
    ): CurrentQueueChangeResult {
        return replaceQueue(
            accountId = accountId,
            recordings = listOf(recording),
            currentIndex = 0,
            nowMs = nowMs,
        )
    }

    fun appendToQueue(
        accountId: Long,
        recordings: List<ResolvedQueueRecording>,
        nowMs: Long = timeProvider.nowMs(),
    ): CurrentQueueChangeResult {
        if (recordings.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "recordingIds must not be empty")
        }

        return lockManager.withAccountLock(accountId) {
            val state = getOrCreateStateLocked(accountId)
            val previousCurrent = state.currentEntryId?.let { currentId -> state.items.firstOrNull { it.entryId == currentId } }
            recordings.forEach { recording ->
                state.items += recording.toQueueEntry(state.nextEntryId++)
            }
            if (state.currentEntryId == null) {
                state.currentEntryId = state.items.firstOrNull()?.entryId
            }
            touchState(state, nowMs)
            buildChangeResult(state, previousCurrent = previousCurrent, removedEntry = null)
        }
    }

    fun reorderQueue(
        accountId: Long,
        entryIds: List<Long>,
        nowMs: Long = timeProvider.nowMs(),
    ): CurrentQueueChangeResult {
        return lockManager.withAccountLock(accountId) {
            val state = getOrCreateStateLocked(accountId)
            if (entryIds.size != state.items.size) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "entryIds size does not match queue size")
            }

            val itemByEntryId = state.items.associateBy(CurrentQueueEntry::entryId)
            val reorderedItems = entryIds.map { entryId ->
                itemByEntryId[entryId]
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Queue entry $entryId not found")
            }
            if (itemByEntryId.size != entryIds.toSet().size) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "entryIds must be unique")
            }

            val previousCurrent = state.currentEntryId?.let { currentId -> state.items.firstOrNull { it.entryId == currentId } }
            state.items.clear()
            state.items += reorderedItems
            touchState(state, nowMs)
            buildChangeResult(state, previousCurrent = previousCurrent, removedEntry = null)
        }
    }

    fun setCurrentEntry(
        accountId: Long,
        entryId: Long,
        nowMs: Long = timeProvider.nowMs(),
    ): CurrentQueueChangeResult {
        return lockManager.withAccountLock(accountId) {
            val state = getOrCreateStateLocked(accountId)
            val previousCurrent = state.currentEntryId?.let { currentId -> state.items.firstOrNull { it.entryId == currentId } }
            val nextCurrent = state.items.firstOrNull { it.entryId == entryId }
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Queue entry not found")
            state.currentEntryId = nextCurrent.entryId
            touchState(state, nowMs)
            buildChangeResult(state, previousCurrent = previousCurrent, removedEntry = null)
        }
    }

    fun removeEntry(
        accountId: Long,
        entryId: Long,
        nowMs: Long = timeProvider.nowMs(),
    ): CurrentQueueChangeResult {
        return lockManager.withAccountLock(accountId) {
            val state = getOrCreateStateLocked(accountId)
            val removeIndex = state.items.indexOfFirst { it.entryId == entryId }
            if (removeIndex < 0) {
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "Queue entry not found")
            }

            val previousCurrent = state.currentEntryId?.let { currentId -> state.items.firstOrNull { it.entryId == currentId } }
            val removedEntry = state.items.removeAt(removeIndex)

            if (state.items.isEmpty()) {
                state.currentEntryId = null
            } else if (previousCurrent?.entryId == removedEntry.entryId) {
                val nextIndex = if (removeIndex >= state.items.size) 0 else removeIndex
                state.currentEntryId = state.items[nextIndex].entryId
            }

            touchState(state, nowMs)
            buildChangeResult(state, previousCurrent = previousCurrent, removedEntry = removedEntry)
        }
    }

    fun clearQueue(
        accountId: Long,
        nowMs: Long = timeProvider.nowMs(),
    ): CurrentQueueChangeResult {
        return lockManager.withAccountLock(accountId) {
            val state = getOrCreateStateLocked(accountId)
            val previousCurrent = state.currentEntryId?.let { currentId -> state.items.firstOrNull { it.entryId == currentId } }
            val removedEntry = previousCurrent
            state.items.clear()
            state.currentEntryId = null
            touchState(state, nowMs)
            buildChangeResult(state, previousCurrent = previousCurrent, removedEntry = removedEntry)
        }
    }

    fun syncTrackWithPlayback(
        accountId: Long,
        recording: ResolvedQueueRecording,
        nowMs: Long = timeProvider.nowMs(),
    ): CurrentQueueChangeResult? {
        return lockManager.withAccountLock(accountId) {
            val state = getOrCreateStateLocked(accountId)
            val previousCurrent = state.currentEntryId?.let { currentId -> state.items.firstOrNull { it.entryId == currentId } }
            val matchingEntry = state.items.firstOrNull {
                it.recordingId == recording.recordingId && it.mediaFileId == recording.mediaFileId
            }

            when {
                matchingEntry == null -> {
                    state.items.clear()
                    state.items += recording.toQueueEntry(state.nextEntryId++)
                    state.currentEntryId = state.items.single().entryId
                }

                state.currentEntryId == matchingEntry.entryId -> return@withAccountLock null

                else -> state.currentEntryId = matchingEntry.entryId
            }

            touchState(state, nowMs)
            buildChangeResult(state, previousCurrent = previousCurrent, removedEntry = null)
        }
    }

    fun advanceToNext(
        accountId: Long,
        nowMs: Long = timeProvider.nowMs(),
    ): CurrentQueueChangeResult? {
        return lockManager.withAccountLock(accountId) {
            val state = getOrCreateStateLocked(accountId)
            if (state.items.isEmpty()) {
                return@withAccountLock null
            }

            val previousCurrent = state.currentEntryId?.let { currentId -> state.items.firstOrNull { it.entryId == currentId } }
            val currentIndex = previousCurrent?.let { entry ->
                state.items.indexOfFirst { it.entryId == entry.entryId }
            } ?: 0
            val nextIndex = if (state.items.size == 1) 0 else (currentIndex + 1) % state.items.size
            state.currentEntryId = state.items[nextIndex].entryId
            touchState(state, nowMs)
            buildChangeResult(state, previousCurrent = previousCurrent, removedEntry = null)
        }
    }

    fun resolvePlayableRecordings(recordingIds: List<Long>): List<ResolvedQueueRecording> {
        if (recordingIds.isEmpty()) {
            return emptyList()
        }

        val existingRecordingIds = recordingCatalog.getExistingRecordingIds(recordingIds.toSet())
        val resolvedByRecordingId =
            recordingCatalog.loadResolvedRecordings(recordingIds.toSet()).associateBy(ResolvedQueueRecording::recordingId)
        return recordingIds.map { recordingId ->
            when {
                recordingId !in existingRecordingIds -> {
                    throw ResponseStatusException(HttpStatus.NOT_FOUND, "Recording $recordingId not found")
                }

                recordingId !in resolvedByRecordingId -> {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Recording $recordingId is not playable")
                }

                else -> resolvedByRecordingId.getValue(recordingId)
            }
        }
    }

    fun resolvePlayableRecording(
        recordingId: Long,
        mediaFileId: Long,
    ): ResolvedQueueRecording {
        return recordingCatalog.loadResolvedRecordings(setOf(recordingId), mediaFileId).firstOrNull()
            ?: when {
                recordingId !in recordingCatalog.getExistingRecordingIds(setOf(recordingId)) -> {
                    throw ResponseStatusException(HttpStatus.NOT_FOUND, "Recording $recordingId not found")
                }

                else -> {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Recording $recordingId has no playable audio asset for media file $mediaFileId",
                    )
                }
            }
    }

    private fun getOrCreateStateLocked(accountId: Long): AccountCurrentQueueState {
        return states.computeIfAbsent(accountId) {
            AccountCurrentQueueState.initial(accountId = accountId, nowMs = timeProvider.nowMs())
        }
    }

    private fun touchState(
        state: AccountCurrentQueueState,
        nowMs: Long,
    ) {
        state.version += 1
        state.updatedAtMs = nowMs
    }

    private fun buildChangeResult(
        state: AccountCurrentQueueState,
        previousCurrent: CurrentQueueEntry?,
        removedEntry: CurrentQueueEntry?,
    ): CurrentQueueChangeResult {
        val currentEntry = state.currentEntryId?.let { currentId -> state.items.firstOrNull { it.entryId == currentId } }
        return CurrentQueueChangeResult(
            queue = buildQueueDto(state),
            previousCurrentEntry = previousCurrent,
            currentEntry = currentEntry,
            removedEntry = removedEntry,
        )
    }

    private fun buildQueueDto(state: AccountCurrentQueueState): CurrentQueueDto {
        return CurrentQueueDto(
            items = state.items.map(::toQueueItemDto),
            currentEntryId = state.currentEntryId,
            version = state.version,
            updatedAtMs = state.updatedAtMs,
        )
    }

    private fun toQueueItemDto(entry: CurrentQueueEntry): CurrentQueueItemDto {
        return CurrentQueueItemDto(
            entryId = entry.entryId,
            recordingId = entry.recordingId,
            mediaFileId = entry.mediaFileId,
            title = entry.title,
            artistLabel = entry.artistLabel,
            coverUrl = entry.coverMediaFileId?.let(urlSigner::generatePresignedPath),
            durationMs = entry.durationMs,
        )
    }

    private fun ResolvedQueueRecording.toQueueEntry(entryId: Long): CurrentQueueEntry {
        return CurrentQueueEntry(
            entryId = entryId,
            recordingId = recordingId,
            mediaFileId = mediaFileId,
            title = title,
            artistLabel = artistLabel,
            coverMediaFileId = coverMediaFileId,
            durationMs = durationMs,
        )
    }
}

interface CurrentQueueRecordingCatalog {
    fun getExistingRecordingIds(recordingIds: Set<Long>): Set<Long>

    fun loadResolvedRecordings(
        recordingIds: Set<Long>,
        requiredMediaFileId: Long? = null,
    ): List<ResolvedQueueRecording>
}

@Component
class JimmerCurrentQueueRecordingCatalog(
    private val sql: org.babyfish.jimmer.sql.kt.KSqlClient,
) : CurrentQueueRecordingCatalog {
    override fun getExistingRecordingIds(recordingIds: Set<Long>): Set<Long> {
        if (recordingIds.isEmpty()) {
            return emptySet()
        }
        return sql.createQuery(Recording::class) {
            where(table.id valueIn recordingIds)
            select(table.id)
        }.execute().toSet()
    }

    override fun loadResolvedRecordings(
        recordingIds: Set<Long>,
        requiredMediaFileId: Long?,
    ): List<ResolvedQueueRecording> {
        if (recordingIds.isEmpty()) {
            return emptyList()
        }

        val recordings = sql.createQuery(Recording::class) {
            where(table.id valueIn recordingIds)
            select(table.fetch(RECORDING_FETCHER))
        }.execute()

        return recordings.mapNotNull { recording ->
            val playableAsset = recording.assets.firstOrNull { asset ->
                asset.mediaFile.mimeType.startsWith("audio/") &&
                    (requiredMediaFileId == null || asset.mediaFile.id == requiredMediaFileId)
            } ?: return@mapNotNull null

            ResolvedQueueRecording(
                recordingId = recording.id,
                mediaFileId = playableAsset.mediaFile.id,
                title = recording.title?.takeIf(String::isNotBlank)
                    ?: recording.comment.takeIf(String::isNotBlank)
                    ?: "Untitled Track",
                artistLabel = recording.artists.mapNotNull { artist ->
                    artist.displayName.takeIf(String::isNotBlank)
                }.joinToString(", ").ifBlank { "Unknown Artist" },
                coverMediaFileId = recording.cover?.id,
                durationMs = recording.durationMs,
            )
        }
    }

    private companion object {
        private val RECORDING_FETCHER = newFetcher(Recording::class).by {
            allScalarFields()
            artists {
                allScalarFields()
            }
            cover {
                allScalarFields()
            }
            assets {
                allScalarFields()
                mediaFile {
                    allScalarFields()
                }
            }
        }
    }
}
