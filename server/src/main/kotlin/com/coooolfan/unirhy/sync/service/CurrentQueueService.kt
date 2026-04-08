package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.model.Recording
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.model.id
import com.coooolfan.unirhy.service.MediaUrlSigner
import com.coooolfan.unirhy.sync.model.AccountCurrentQueueState
import com.coooolfan.unirhy.sync.model.CurrentQueueEntry
import com.coooolfan.unirhy.sync.protocol.CurrentQueueDto
import com.coooolfan.unirhy.sync.protocol.CurrentQueueItemDto
import com.coooolfan.unirhy.sync.protocol.PlaybackStrategy
import com.coooolfan.unirhy.sync.protocol.StopStrategy
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

data class CurrentQueueChangeResult(
    val queue: CurrentQueueDto,
    val previousCurrentEntry: CurrentQueueEntry?,
    val currentEntry: CurrentQueueEntry?,
    val removedEntry: CurrentQueueEntry? = null,
    val changed: Boolean = true,
)

data class ResolvedQueueRecording(
    val recordingId: Long,
    val mediaFileId: Long,
    val workId: Long,
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
    private val stateStore: CurrentQueueStateStore,
) {
    private val states = ConcurrentHashMap<Long, AccountCurrentQueueState>()

    fun getQueue(accountId: Long): CurrentQueueDto {
        return lockManager.withAccountLock(accountId) {
            buildQueueDto(getOrLoadStateLocked(accountId))
        }
    }

    fun getCurrentEntry(accountId: Long): CurrentQueueEntry? {
        return lockManager.withAccountLock(accountId) {
            currentEntryOf(getOrLoadStateLocked(accountId))
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
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "currentIndex must be 0 for an empty queue",
                )
            }
            return clearQueue(accountId, nowMs)
        }
        if (currentIndex !in recordings.indices) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "currentIndex out of range")
        }

        return lockManager.withAccountLock(accountId) {
            val currentState = getOrLoadStateLocked(accountId)
            val previousCurrent = currentEntryOf(currentState)
            val state = currentState.deepCopy()
            state.items.clear()
            recordings.forEach { recording ->
                state.items += recording.toQueueEntry(state.nextEntryId++)
            }
            state.currentEntryId = state.items[currentIndex].entryId
            resetStrategiesLocked(state)
            touchState(state, nowMs)
            persistAndCacheState(state)
            buildChangeResult(state, previousCurrent, removedEntry = null)
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
            val currentState = getOrLoadStateLocked(accountId)
            val previousCurrent = currentEntryOf(currentState)
            val state = currentState.deepCopy()
            recordings.forEach { recording ->
                state.items += recording.toQueueEntry(state.nextEntryId++)
            }
            if (state.currentEntryId == null) {
                state.currentEntryId = state.items.firstOrNull()?.entryId
            }
            rebuildShuffleOrderLocked(state)
            touchState(state, nowMs)
            persistAndCacheState(state)
            buildChangeResult(state, previousCurrent, removedEntry = null)
        }
    }

    fun reorderQueue(
        accountId: Long,
        entryIds: List<Long>,
        nowMs: Long = timeProvider.nowMs(),
    ): CurrentQueueChangeResult {
        return lockManager.withAccountLock(accountId) {
            val currentState = getOrLoadStateLocked(accountId)
            if (entryIds.size != currentState.items.size) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "entryIds size does not match queue size",
                )
            }

            val itemByEntryId = currentState.items.associateBy(CurrentQueueEntry::entryId)
            val reorderedItems = entryIds.map { entryId ->
                itemByEntryId[entryId]
                    ?: throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Queue entry $entryId not found",
                    )
            }
            if (itemByEntryId.size != entryIds.toSet().size) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "entryIds must be unique")
            }

            val previousCurrent = currentEntryOf(currentState)
            val state = currentState.deepCopy()
            state.items.clear()
            state.items += reorderedItems
            rebuildShuffleOrderLocked(state)
            touchState(state, nowMs)
            persistAndCacheState(state)
            buildChangeResult(state, previousCurrent, removedEntry = null)
        }
    }

    fun setCurrentEntry(
        accountId: Long,
        entryId: Long,
        nowMs: Long = timeProvider.nowMs(),
    ): CurrentQueueChangeResult {
        return lockManager.withAccountLock(accountId) {
            val currentState = getOrLoadStateLocked(accountId)
            val previousCurrent = currentEntryOf(currentState)
            val state = currentState.deepCopy()
            val nextCurrent = state.items.firstOrNull { it.entryId == entryId }
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Queue entry not found")
            if (previousCurrent?.entryId == nextCurrent.entryId) {
                return@withAccountLock buildNoopChangeResult(currentState, previousCurrent)
            }

            state.currentEntryId = nextCurrent.entryId
            rebuildShuffleOrderLocked(state, anchorEntryId = nextCurrent.entryId)
            touchState(state, nowMs)
            persistAndCacheState(state)
            buildChangeResult(state, previousCurrent, removedEntry = null)
        }
    }

    fun updateStrategies(
        accountId: Long,
        playbackStrategy: PlaybackStrategy,
        stopStrategy: StopStrategy,
        nowMs: Long = timeProvider.nowMs(),
    ): CurrentQueueChangeResult {
        return lockManager.withAccountLock(accountId) {
            val currentState = getOrLoadStateLocked(accountId)
            val previousCurrent = currentEntryOf(currentState)
            val changed =
                currentState.playbackStrategy != playbackStrategy || currentState.stopStrategy != stopStrategy
            if (!changed) {
                return@withAccountLock buildNoopChangeResult(currentState, previousCurrent)
            }

            val state = currentState.deepCopy()
            state.playbackStrategy = playbackStrategy
            state.stopStrategy = stopStrategy
            rebuildShuffleOrderLocked(state)
            touchState(state, nowMs)
            persistAndCacheState(state)
            buildChangeResult(state, previousCurrent, removedEntry = null)
        }
    }

    fun navigateToNext(
        accountId: Long,
        nowMs: Long = timeProvider.nowMs(),
    ): CurrentQueueChangeResult {
        return lockManager.withAccountLock(accountId) {
            val currentState = getOrLoadStateLocked(accountId)
            val previousCurrent = currentEntryOf(currentState)
            val state = currentState.deepCopy()
            if (state.items.isEmpty() || previousCurrent == null) {
                return@withAccountLock buildNoopChangeResult(currentState, previousCurrent)
            }

            val changed = when (state.playbackStrategy) {
                PlaybackStrategy.SEQUENTIAL -> moveSequentialLocked(state, previousCurrent, 1)
                PlaybackStrategy.SHUFFLE -> moveShuffleLocked(state, previousCurrent, 1)
                PlaybackStrategy.RADIO -> moveRadioNextLocked(state, previousCurrent)
            }
            if (!changed) {
                return@withAccountLock buildNoopChangeResult(currentState, previousCurrent)
            }

            touchState(state, nowMs)
            persistAndCacheState(state)
            buildChangeResult(state, previousCurrent, removedEntry = null)
        }
    }

    fun navigateToPrevious(
        accountId: Long,
        nowMs: Long = timeProvider.nowMs(),
    ): CurrentQueueChangeResult {
        return lockManager.withAccountLock(accountId) {
            val currentState = getOrLoadStateLocked(accountId)
            val previousCurrent = currentEntryOf(currentState)
            val state = currentState.deepCopy()
            if (state.items.isEmpty() || previousCurrent == null) {
                return@withAccountLock buildNoopChangeResult(currentState, previousCurrent)
            }

            val changed = when (state.playbackStrategy) {
                PlaybackStrategy.SEQUENTIAL -> moveSequentialLocked(state, previousCurrent, -1)
                PlaybackStrategy.SHUFFLE -> moveShuffleLocked(state, previousCurrent, -1)
                PlaybackStrategy.RADIO -> moveSequentialLocked(state, previousCurrent, -1)
            }
            if (!changed) {
                return@withAccountLock buildNoopChangeResult(currentState, previousCurrent)
            }

            touchState(state, nowMs)
            persistAndCacheState(state)
            buildChangeResult(state, previousCurrent, removedEntry = null)
        }
    }

    fun removeEntry(
        accountId: Long,
        entryId: Long,
        nowMs: Long = timeProvider.nowMs(),
    ): CurrentQueueChangeResult {
        return lockManager.withAccountLock(accountId) {
            val currentState = getOrLoadStateLocked(accountId)
            val state = currentState.deepCopy()
            val removeIndex = state.items.indexOfFirst { it.entryId == entryId }
            if (removeIndex < 0) {
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "Queue entry not found")
            }

            val previousCurrent = currentEntryOf(currentState)
            val removedEntry = state.items.removeAt(removeIndex)

            when {
                state.items.isEmpty() -> {
                    state.currentEntryId = null
                    resetStrategiesLocked(state)
                }

                previousCurrent?.entryId == removedEntry.entryId -> {
                    val nextIndex = if (removeIndex >= state.items.size) removeIndex - 1 else removeIndex
                    state.currentEntryId = state.items[nextIndex].entryId
                    rebuildShuffleOrderLocked(state)
                }

                else -> rebuildShuffleOrderLocked(state)
            }

            touchState(state, nowMs)
            persistAndCacheState(state)
            buildChangeResult(state, previousCurrent, removedEntry)
        }
    }

    fun clearQueue(
        accountId: Long,
        nowMs: Long = timeProvider.nowMs(),
    ): CurrentQueueChangeResult {
        return lockManager.withAccountLock(accountId) {
            val currentState = getOrLoadStateLocked(accountId)
            val previousCurrent = currentEntryOf(currentState)
            val state = currentState.deepCopy()
            val removedEntry = previousCurrent
            state.items.clear()
            state.currentEntryId = null
            resetStrategiesLocked(state)
            touchState(state, nowMs)
            persistAndCacheState(state)
            buildChangeResult(state, previousCurrent, removedEntry)
        }
    }

    fun syncTrackWithPlayback(
        accountId: Long,
        recording: ResolvedQueueRecording,
        nowMs: Long = timeProvider.nowMs(),
    ): CurrentQueueChangeResult? {
        return lockManager.withAccountLock(accountId) {
            val currentState = getOrLoadStateLocked(accountId)
            val previousCurrent = currentEntryOf(currentState)
            val state = currentState.deepCopy()
            val matchingEntry = state.items.firstOrNull { it.recordingId == recording.recordingId }

            when {
                matchingEntry == null -> {
                    state.items.clear()
                    state.items += recording.toQueueEntry(state.nextEntryId++)
                    state.currentEntryId = state.items.single().entryId
                    resetStrategiesLocked(state)
                }

                previousCurrent?.entryId == matchingEntry.entryId -> return@withAccountLock null

                else -> {
                    state.currentEntryId = matchingEntry.entryId
                    rebuildShuffleOrderLocked(state, anchorEntryId = matchingEntry.entryId)
                }
            }

            touchState(state, nowMs)
            persistAndCacheState(state)
            buildChangeResult(state, previousCurrent, removedEntry = null)
        }
    }

    fun resolvePlayableRecordings(recordingIds: List<Long>): List<ResolvedQueueRecording> {
        if (recordingIds.isEmpty()) {
            return emptyList()
        }

        val existingRecordingIds = recordingCatalog.getExistingRecordingIds(recordingIds.toSet())
        val resolvedByRecordingId =
            recordingCatalog.loadResolvedRecordings(recordingIds.toSet()).associateBy(
                ResolvedQueueRecording::recordingId,
            )
        return recordingIds.map { recordingId ->
            when {
                recordingId !in existingRecordingIds -> {
                    throw ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Recording $recordingId not found",
                    )
                }

                recordingId !in resolvedByRecordingId -> {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Recording $recordingId is not playable",
                    )
                }

                else -> resolvedByRecordingId.getValue(recordingId)
            }
        }
    }

    fun resolvePlayableRecording(
        recordingId: Long,
        requiredMediaFileId: Long? = null,
    ): ResolvedQueueRecording {
        return resolvePlayableRecordingUnchecked(recordingId, requiredMediaFileId)
            ?: when {
                recordingId !in recordingCatalog.getExistingRecordingIds(setOf(recordingId)) -> {
                    throw ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Recording $recordingId not found",
                    )
                }

                else -> {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Recording $recordingId is not playable",
                    )
                }
            }
    }

    private fun currentEntryOf(state: AccountCurrentQueueState): CurrentQueueEntry? {
        val currentId = state.currentEntryId ?: return null
        return state.items.firstOrNull { it.entryId == currentId }
    }

    private fun resolvePlayableRecordingUnchecked(
        recordingId: Long,
        requiredMediaFileId: Long? = null,
    ): ResolvedQueueRecording? {
        return recordingCatalog.loadResolvedRecordings(setOf(recordingId), requiredMediaFileId)
            .firstOrNull()
    }

    private fun moveSequentialLocked(
        state: AccountCurrentQueueState,
        currentEntry: CurrentQueueEntry,
        step: Int,
    ): Boolean {
        val currentIndex = state.items.indexOfFirst { it.entryId == currentEntry.entryId }
        if (currentIndex < 0) {
            return false
        }
        val nextIndex = currentIndex + step
        if (nextIndex !in state.items.indices) {
            return false
        }
        state.currentEntryId = state.items[nextIndex].entryId
        return true
    }

    private fun moveShuffleLocked(
        state: AccountCurrentQueueState,
        currentEntry: CurrentQueueEntry,
        step: Int,
    ): Boolean {
        rebuildShuffleOrderLocked(state)
        val currentIndex = state.shuffleEntryIds.indexOf(currentEntry.entryId)
        if (currentIndex < 0) {
            return false
        }
        val nextIndex = currentIndex + step
        if (nextIndex !in state.shuffleEntryIds.indices) {
            return false
        }
        state.currentEntryId = state.shuffleEntryIds[nextIndex]
        return true
    }

    private fun moveRadioNextLocked(
        state: AccountCurrentQueueState,
        currentEntry: CurrentQueueEntry,
    ): Boolean {
        val currentIndex = state.items.indexOfFirst { it.entryId == currentEntry.entryId }
        if (currentIndex < 0) {
            return false
        }

        val realizedNextIndex = currentIndex + 1
        if (realizedNextIndex in state.items.indices) {
            state.currentEntryId = state.items[realizedNextIndex].entryId
            return true
        }

        val recentWindowSize = minOf(RADIO_RECENT_WINDOW_MAX, recordingCatalog.countWorks())
        val recentWorkIds = state.items
            .takeLast(recentWindowSize)
            .map(CurrentQueueEntry::workId)
            .toSet()

        val similarRecordingId = recordingCatalog.findFirstSimilarRecordingId(
            recordingId = currentEntry.recordingId,
            excludedWorkIds = recentWorkIds,
        )
            ?: return false
        val nextRecording = try {
            resolvePlayableRecording(recordingId = similarRecordingId)
        } catch (_: ResponseStatusException) {
            return false
        }

        state.items += nextRecording.toQueueEntry(state.nextEntryId++)
        state.currentEntryId = state.items.last().entryId
        return true
    }

    private fun resetStrategiesLocked(state: AccountCurrentQueueState) {
        state.playbackStrategy = PlaybackStrategy.SEQUENTIAL
        state.stopStrategy = StopStrategy.LIST
        state.shuffleEntryIds.clear()
    }

    private fun rebuildShuffleOrderLocked(
        state: AccountCurrentQueueState,
        anchorEntryId: Long? = state.currentEntryId,
    ) {
        if (state.playbackStrategy != PlaybackStrategy.SHUFFLE) {
            state.shuffleEntryIds.clear()
            return
        }

        val anchor = anchorEntryId?.takeIf { entryId -> state.items.any { it.entryId == entryId } }
            ?: state.items.firstOrNull()?.entryId
            ?: run {
                state.shuffleEntryIds.clear()
                return
            }

        val remainingEntryIds = state.items
            .map(CurrentQueueEntry::entryId)
            .filterNot { it == anchor }
            .shuffled(Random.Default)

        state.shuffleEntryIds.clear()
        state.shuffleEntryIds += anchor
        state.shuffleEntryIds += remainingEntryIds
    }

    private fun getOrLoadStateLocked(accountId: Long): AccountCurrentQueueState {
        states[accountId]?.let { return it }
        val loaded = stateStore.load(accountId)
            ?: AccountCurrentQueueState.initial(accountId = accountId, nowMs = timeProvider.nowMs())
        states[accountId] = loaded
        return loaded
    }

    private fun persistAndCacheState(state: AccountCurrentQueueState) {
        stateStore.upsert(state)
        states[state.accountId] = state
    }

    private fun touchState(
        state: AccountCurrentQueueState,
        nowMs: Long,
    ) {
        state.version += 1
        state.updatedAtMs = nowMs
    }

    private fun buildNoopChangeResult(
        state: AccountCurrentQueueState,
        previousCurrent: CurrentQueueEntry?,
    ): CurrentQueueChangeResult {
        return CurrentQueueChangeResult(
            queue = buildQueueDto(state),
            previousCurrentEntry = previousCurrent,
            currentEntry = currentEntryOf(state),
            removedEntry = null,
            changed = false,
        )
    }

    private fun buildChangeResult(
        state: AccountCurrentQueueState,
        previousCurrent: CurrentQueueEntry?,
        removedEntry: CurrentQueueEntry?,
    ): CurrentQueueChangeResult {
        return CurrentQueueChangeResult(
            queue = buildQueueDto(state),
            previousCurrentEntry = previousCurrent,
            currentEntry = currentEntryOf(state),
            removedEntry = removedEntry,
            changed = true,
        )
    }

    private fun buildQueueDto(state: AccountCurrentQueueState): CurrentQueueDto {
        return CurrentQueueDto(
            items = state.items.map(::toQueueItemDto),
            currentEntryId = state.currentEntryId,
            playbackStrategy = state.playbackStrategy,
            stopStrategy = state.stopStrategy,
            version = state.version,
            updatedAtMs = state.updatedAtMs,
        )
    }

    private fun toQueueItemDto(entry: CurrentQueueEntry): CurrentQueueItemDto {
        return CurrentQueueItemDto(
            entryId = entry.entryId,
            recordingId = entry.recordingId,
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
            workId = workId,
            title = title,
            artistLabel = artistLabel,
            coverMediaFileId = coverMediaFileId,
            durationMs = durationMs,
        )
    }

    private fun AccountCurrentQueueState.deepCopy(): AccountCurrentQueueState {
        return copy(
            items = items.map { it.copy() }.toMutableList(),
            shuffleEntryIds = shuffleEntryIds.toMutableList(),
        )
    }

    private companion object {
        private const val RADIO_RECENT_WINDOW_MAX = 16
    }
}

interface CurrentQueueRecordingCatalog {
    fun getExistingRecordingIds(recordingIds: Set<Long>): Set<Long>

    fun countWorks(): Int

    fun loadResolvedRecordings(
        recordingIds: Set<Long>,
        requiredMediaFileId: Long? = null,
    ): List<ResolvedQueueRecording>

    fun findFirstSimilarRecordingId(
        recordingId: Long,
        excludedWorkIds: Set<Long>,
    ): Long?
}

@Component
class JimmerCurrentQueueRecordingCatalog(
    private val sql: KSqlClient,
    private val jdbc: NamedParameterJdbcTemplate,
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

    override fun countWorks(): Int {
        return jdbc.queryForObject(
            "SELECT COUNT(DISTINCT work_id) FROM public.recording",
            MapSqlParameterSource(),
            Int::class.java,
        ) ?: 0
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
                workId = recording.work.id,
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

    override fun findFirstSimilarRecordingId(
        recordingId: Long,
        excludedWorkIds: Set<Long>,
    ): Long? {
        val exclusionClause = if (excludedWorkIds.isEmpty()) {
            ""
        } else {
            "  AND candidate.work_id NOT IN (:excludedWorkIds)\n"
        }
        val sql = """
            SELECT candidate.id
            FROM public.recording source
            JOIN public.recording candidate
              ON candidate.id != source.id
             AND candidate.embedding IS NOT NULL
            WHERE source.id = :recordingId
              AND source.embedding IS NOT NULL
            $exclusionClause
            ORDER BY candidate.embedding <=> source.embedding
            LIMIT 1
        """.trimIndent()

        val params = MapSqlParameterSource().addValue("recordingId", recordingId)
        if (excludedWorkIds.isNotEmpty()) {
            params.addValue("excludedWorkIds", excludedWorkIds)
        }
        return jdbc.query(sql, params) { rs, _ -> rs.getLong("id") }.firstOrNull()
    }

    private companion object {
        private val RECORDING_FETCHER = newFetcher(Recording::class).by {
            allScalarFields()
            work {
                allScalarFields()
            }
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
