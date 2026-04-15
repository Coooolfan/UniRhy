package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.model.Recording
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.model.id
import com.coooolfan.unirhy.service.MediaUrlSigner
import com.coooolfan.unirhy.sync.model.AccountPlayQueueState
import com.coooolfan.unirhy.sync.model.AccountPlaybackState
import com.coooolfan.unirhy.sync.model.CurrentQueueEntry
import com.coooolfan.unirhy.sync.protocol.CurrentQueueDto
import com.coooolfan.unirhy.sync.protocol.CurrentQueueItemDto
import com.coooolfan.unirhy.sync.protocol.PlaybackStatus
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
    val previousCurrentIndex: Int?,
    val currentIndex: Int?,
    val previousRecordingId: Long?,
    val currentRecordingId: Long?,
    val removedIndex: Int? = null,
    val removedRecordingId: Long? = null,
    val changed: Boolean = true,
)

data class ResolvedQueueRecording(
    val recordingId: Long,
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
    private val states = ConcurrentHashMap<Long, AccountPlayQueueState>()

    private data class MutationContext(
        val currentState: AccountPlayQueueState,
        val nextState: AccountPlayQueueState,
        val previousCurrentIndex: Int?,
        val previousRecordingId: Long?,
    )

    fun getQueue(accountId: Long): CurrentQueueDto {
        return lockManager.withAccountLock(accountId) {
            buildQueueDto(getOrLoadStateLocked(accountId))
        }
    }

    fun getPlaybackState(accountId: Long): AccountPlaybackState {
        return lockManager.withAccountLock(accountId) {
            getOrLoadStateLocked(accountId).toPlaybackState()
        }
    }

    fun getCurrentEntry(accountId: Long): CurrentQueueEntry? {
        return lockManager.withAccountLock(accountId) {
            currentRecordingIdOf(getOrLoadStateLocked(accountId))?.let(::resolveCurrentEntry)
        }
    }

    fun getCurrentRecordingId(accountId: Long): Long? {
        return lockManager.withAccountLock(accountId) {
            currentRecordingIdOf(getOrLoadStateLocked(accountId))
        }
    }

    fun replaceQueue(
        accountId: Long,
        recordingIds: List<Long>,
        currentIndex: Int,
        expectedVersion: Long,
        nowMs: Long = timeProvider.nowMs(),
    ): CurrentQueueChangeResult {
        if (recordingIds.isEmpty()) {
            requireEmptyQueueIndex(currentIndex)
            return clearQueue(accountId, expectedVersion, nowMs)
        }
        requireValidCurrentIndex(recordingIds, currentIndex)

        return withMutationContext(accountId, expectedVersion) { context ->
            val nextState = context.nextState
            replaceQueueContents(nextState, recordingIds, currentIndex)
            if (recordingIds.isEmpty()) {
                forceEmptyStateLocked(nextState)
            } else {
                resetStrategiesLocked(nextState)
            }
            buildPersistedChangeResult(context, nowMs)
        }
    }

    fun appendToQueue(
        accountId: Long,
        recordingIds: List<Long>,
        expectedVersion: Long,
        nowMs: Long = timeProvider.nowMs(),
    ): CurrentQueueChangeResult {
        if (recordingIds.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "recordingIds must not be empty")
        }

        return withMutationContext(accountId, expectedVersion) { context ->
            val nextState = context.nextState
            val wasEmpty = nextState.recordingIds.isEmpty()
            nextState.recordingIds += recordingIds
            if (wasEmpty) {
                nextState.currentIndex = 0
            }
            rebuildShuffleOrderLocked(nextState)
            buildPersistedChangeResult(context, nowMs)
        }
    }

    fun reorderQueue(
        accountId: Long,
        recordingIds: List<Long>,
        currentIndex: Int,
        expectedVersion: Long,
        nowMs: Long = timeProvider.nowMs(),
    ): CurrentQueueChangeResult {
        if (recordingIds.isEmpty()) {
            requireEmptyQueueIndex(currentIndex)
            return clearQueue(accountId, expectedVersion, nowMs)
        }
        requireValidCurrentIndex(recordingIds, currentIndex)

        return withMutationContext(accountId, expectedVersion) { context ->
            val nextState = context.nextState
            replaceQueueContents(nextState, recordingIds, currentIndex)
            rebuildShuffleOrderLocked(nextState, anchorIndex = currentIndex)
            buildPersistedChangeResult(context, nowMs)
        }
    }

    fun setCurrentIndex(
        accountId: Long,
        currentIndex: Int,
        expectedVersion: Long,
        nowMs: Long = timeProvider.nowMs(),
    ): CurrentQueueChangeResult {
        return withMutationContext(accountId, expectedVersion) { context ->
            val currentState = context.currentState
            requireValidCurrentIndex(currentState.recordingIds, currentIndex)
            if (currentState.recordingIds.isNotEmpty() && currentState.currentIndex == currentIndex) {
                return@withMutationContext buildNoopChangeResult(currentState)
            }

            val nextState = context.nextState
            nextState.currentIndex = currentIndex
            clearPlaybackProgress(nextState)
            rebuildShuffleOrderLocked(nextState, anchorIndex = currentIndex)
            buildPersistedChangeResult(context, nowMs)
        }
    }

    fun updateStrategies(
        accountId: Long,
        playbackStrategy: PlaybackStrategy,
        stopStrategy: StopStrategy,
        expectedVersion: Long,
        nowMs: Long = timeProvider.nowMs(),
    ): CurrentQueueChangeResult {
        return withMutationContext(accountId, expectedVersion) { context ->
            val currentState = context.currentState
            if (
                currentState.playbackStrategy == playbackStrategy &&
                currentState.stopStrategy == stopStrategy
            ) {
                return@withMutationContext buildNoopChangeResult(currentState)
            }

            val nextState = context.nextState
            nextState.playbackStrategy = playbackStrategy
            nextState.stopStrategy = stopStrategy
            rebuildShuffleOrderLocked(nextState)
            buildPersistedChangeResult(context, nowMs)
        }
    }

    fun navigateToNext(
        accountId: Long,
        expectedVersion: Long? = null,
        nowMs: Long = timeProvider.nowMs(),
    ): CurrentQueueChangeResult {
        return withMutationContext(accountId, expectedVersion) { context ->
            val currentState = context.currentState
            if (currentState.recordingIds.isEmpty()) {
                return@withMutationContext buildNoopChangeResult(currentState)
            }

            val nextState = context.nextState
            val changed = when (nextState.playbackStrategy) {
                PlaybackStrategy.SEQUENTIAL -> moveSequentialLocked(nextState, 1)
                PlaybackStrategy.SHUFFLE -> moveShuffleLocked(nextState, 1)
                PlaybackStrategy.RADIO -> moveRadioNextLocked(nextState)
            }
            if (!changed) {
                return@withMutationContext buildNoopChangeResult(currentState)
            }

            clearPlaybackProgress(nextState)
            buildPersistedChangeResult(context, nowMs)
        }
    }

    fun navigateToPrevious(
        accountId: Long,
        expectedVersion: Long? = null,
        nowMs: Long = timeProvider.nowMs(),
    ): CurrentQueueChangeResult {
        return withMutationContext(accountId, expectedVersion) { context ->
            val currentState = context.currentState
            if (currentState.recordingIds.isEmpty()) {
                return@withMutationContext buildNoopChangeResult(currentState)
            }

            val nextState = context.nextState
            val changed = when (nextState.playbackStrategy) {
                PlaybackStrategy.SEQUENTIAL -> moveSequentialLocked(nextState, -1)
                PlaybackStrategy.SHUFFLE -> moveShuffleLocked(nextState, -1)
                PlaybackStrategy.RADIO -> moveSequentialLocked(nextState, -1)
            }
            if (!changed) {
                return@withMutationContext buildNoopChangeResult(currentState)
            }

            clearPlaybackProgress(nextState)
            buildPersistedChangeResult(context, nowMs)
        }
    }

    fun removeAt(
        accountId: Long,
        index: Int,
        expectedVersion: Long,
        nowMs: Long = timeProvider.nowMs(),
    ): CurrentQueueChangeResult {
        return withMutationContext(accountId, expectedVersion) { context ->
            val currentState = context.currentState
            if (index !in currentState.recordingIds.indices) {
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "Queue index not found")
            }

            val nextState = context.nextState
            val removedRecordingId = nextState.recordingIds.removeAt(index)

            when {
                nextState.recordingIds.isEmpty() -> forceEmptyStateLocked(nextState)
                index < currentState.currentIndex -> nextState.currentIndex -= 1
                index == currentState.currentIndex -> {
                    nextState.currentIndex = minOf(index, nextState.recordingIds.lastIndex)
                    clearPlaybackProgress(nextState)
                }
            }

            rebuildShuffleOrderLocked(nextState, anchorIndex = currentIndexOrNull(nextState))
            buildPersistedChangeResult(context, nowMs, index, removedRecordingId)
        }
    }

    fun clearQueue(
        accountId: Long,
        expectedVersion: Long,
        nowMs: Long = timeProvider.nowMs(),
    ): CurrentQueueChangeResult {
        return withMutationContext(accountId, expectedVersion) { context ->
            val nextState = context.nextState
            forceEmptyStateLocked(nextState)
            buildPersistedChangeResult(
                context,
                nowMs,
                context.previousCurrentIndex,
                context.previousRecordingId,
            )
        }
    }

    fun beginPlayback(
        accountId: Long,
        currentIndex: Int,
        positionMs: Long,
        executeAtMs: Long,
        expectedVersion: Long,
        nowMs: Long = timeProvider.nowMs(),
    ): AccountPlaybackState {
        return withMutationContext(accountId, expectedVersion) { context ->
            requirePlayableIndex(context.currentState, currentIndex)

            val nextState = context.nextState
            nextState.currentIndex = currentIndex
            nextState.playbackStatus = PlaybackStatus.PLAYING
            nextState.positionMs = positionMs
            nextState.serverTimeToExecuteMs = executeAtMs
            buildPersistedPlaybackState(context, nowMs)
        }
    }

    fun pausePlayback(
        accountId: Long,
        currentIndex: Int?,
        positionMs: Long,
        executeAtMs: Long,
        expectedVersion: Long? = null,
        nowMs: Long = timeProvider.nowMs(),
    ): AccountPlaybackState {
        return withMutationContext(accountId, expectedVersion) { context ->
            val nextState = context.nextState
            if (currentIndex == null || nextState.recordingIds.isEmpty()) {
                forceEmptyStateLocked(nextState)
            } else {
                requirePlayableIndex(nextState, currentIndex)
                nextState.currentIndex = currentIndex
                nextState.playbackStatus = PlaybackStatus.PAUSED
                nextState.positionMs = positionMs
                nextState.serverTimeToExecuteMs = executeAtMs
            }
            buildPersistedPlaybackState(context, nowMs)
        }
    }

    fun seekPlayback(
        accountId: Long,
        currentIndex: Int,
        positionMs: Long,
        executeAtMs: Long,
        expectedVersion: Long,
        nowMs: Long = timeProvider.nowMs(),
    ): AccountPlaybackState {
        return withMutationContext(accountId, expectedVersion) { context ->
            requirePlayableIndex(context.currentState, currentIndex)

            val nextState = context.nextState
            nextState.currentIndex = currentIndex
            nextState.positionMs = positionMs
            nextState.serverTimeToExecuteMs = executeAtMs
            buildPersistedPlaybackState(context, nowMs)
        }
    }

    fun buildSyncPlaybackState(
        accountId: Long,
        executeAtMs: Long,
        nowMs: Long,
    ): AccountPlaybackState {
        return lockManager.withAccountLock(accountId) {
            val state = getOrLoadStateLocked(accountId)
            val positionMs = if (state.playbackStatus == PlaybackStatus.PLAYING) {
                recoverPositionMs(state, nowMs)
            } else {
                state.positionMs
            }
            state.toPlaybackState(
                positionMs = positionMs,
                serverTimeToExecuteMs = executeAtMs,
            )
        }
    }

    fun getQueueVersion(accountId: Long): Long {
        return lockManager.withAccountLock(accountId) {
            getOrLoadStateLocked(accountId).version
        }
    }

    private fun requirePlayableIndex(
        state: AccountPlayQueueState,
        currentIndex: Int,
    ) {
        requireValidCurrentIndex(state.recordingIds, currentIndex)
    }

    private fun requireEmptyQueueIndex(currentIndex: Int) {
        if (currentIndex != 0) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "currentIndex must be 0 for an empty queue",
            )
        }
    }

    private fun requireValidCurrentIndex(
        recordingIds: List<Long>,
        currentIndex: Int,
    ) {
        if (recordingIds.isEmpty() || currentIndex !in recordingIds.indices) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "currentIndex out of range")
        }
    }

    private fun currentIndexOrNull(state: AccountPlayQueueState): Int? {
        return state.currentIndex.takeIf { state.recordingIds.isNotEmpty() }
    }

    private fun currentRecordingIdOf(state: AccountPlayQueueState): Long? {
        val index = currentIndexOrNull(state) ?: return null
        return state.recordingIds[index]
    }

    private fun replaceQueueContents(
        state: AccountPlayQueueState,
        recordingIds: List<Long>,
        currentIndex: Int,
    ) {
        state.recordingIds.clear()
        state.recordingIds += recordingIds
        state.currentIndex = currentIndex
        clearPlaybackProgress(state)
    }

    private fun clearPlaybackProgress(state: AccountPlayQueueState) {
        state.positionMs = 0L
        state.serverTimeToExecuteMs = 0L
    }

    private fun moveSequentialLocked(
        state: AccountPlayQueueState,
        step: Int,
    ): Boolean {
        val nextIndex = state.currentIndex + step
        if (nextIndex !in state.recordingIds.indices) {
            return false
        }
        state.currentIndex = nextIndex
        return true
    }

    private fun moveShuffleLocked(
        state: AccountPlayQueueState,
        step: Int,
    ): Boolean {
        rebuildShuffleOrderLocked(state)
        val currentPosition = state.shuffleIndices.indexOf(state.currentIndex)
        if (currentPosition < 0) {
            return false
        }
        val nextPosition = currentPosition + step
        if (nextPosition !in state.shuffleIndices.indices) {
            return false
        }
        state.currentIndex = state.shuffleIndices[nextPosition]
        return true
    }

    private fun moveRadioNextLocked(state: AccountPlayQueueState): Boolean {
        val realizedNextIndex = state.currentIndex + 1
        if (realizedNextIndex in state.recordingIds.indices) {
            state.currentIndex = realizedNextIndex
            return true
        }

        val currentRecordingId = currentRecordingIdOf(state) ?: return false
        val recentWindowSize = minOf(RADIO_RECENT_WINDOW_MAX, recordingCatalog.countWorks())
        val recentWorkIds = try {
            resolveEntriesOrThrow(
                state.recordingIds.takeLast(recentWindowSize),
            ).map(CurrentQueueEntry::workId).toSet()
        } catch (_: ResponseStatusException) {
            return false
        }

        val similarRecordingId = recordingCatalog.findFirstSimilarRecordingId(
            recordingId = currentRecordingId,
            excludedWorkIds = recentWorkIds,
        ) ?: return false

        state.recordingIds += similarRecordingId
        state.currentIndex = state.recordingIds.lastIndex
        return true
    }

    private fun resetStrategiesLocked(state: AccountPlayQueueState) {
        state.playbackStrategy = PlaybackStrategy.SEQUENTIAL
        state.stopStrategy = StopStrategy.LIST
        state.shuffleIndices.clear()
    }

    private fun forceEmptyStateLocked(state: AccountPlayQueueState) {
        state.recordingIds.clear()
        state.currentIndex = 0
        state.shuffleIndices.clear()
        state.playbackStrategy = PlaybackStrategy.SEQUENTIAL
        state.stopStrategy = StopStrategy.LIST
        state.playbackStatus = PlaybackStatus.PAUSED
        state.positionMs = 0L
        state.serverTimeToExecuteMs = 0L
    }

    private fun rebuildShuffleOrderLocked(
        state: AccountPlayQueueState,
        anchorIndex: Int? = currentIndexOrNull(state),
    ) {
        if (state.playbackStrategy != PlaybackStrategy.SHUFFLE) {
            state.shuffleIndices.clear()
            return
        }

        val indices = state.recordingIds.indices.toList()
        val anchor = anchorIndex?.takeIf { it in indices } ?: indices.firstOrNull() ?: run {
            state.shuffleIndices.clear()
            return
        }

        val existingOrdered = state.shuffleIndices
            .filter { it in indices }
            .distinct()
        if (existingOrdered.size == indices.size && anchor in existingOrdered) {
            state.shuffleIndices.clear()
            state.shuffleIndices += existingOrdered
            return
        }

        val missingIndices = indices
            .filterNot(existingOrdered::contains)
            .filterNot { it == anchor }
            .shuffled(Random.Default)
        val mergedIndices = (existingOrdered + missingIndices)
            .filterNot { it == anchor }

        state.shuffleIndices.clear()
        state.shuffleIndices += anchor
        state.shuffleIndices += mergedIndices
    }

    private fun resolveEntriesOrThrow(recordingIds: List<Long>): List<CurrentQueueEntry> {
        if (recordingIds.isEmpty()) {
            return emptyList()
        }

        val existingRecordingIds = recordingCatalog.getExistingRecordingIds(recordingIds.toSet())
        val resolvedByRecordingId = recordingCatalog.loadResolvedRecordings(recordingIds.toSet())
            .associateBy(ResolvedQueueRecording::recordingId)
        return recordingIds.map { recordingId ->
            when (recordingId) {
                !in existingRecordingIds -> throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Recording $recordingId not found",
                )

                !in resolvedByRecordingId -> throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Recording $recordingId is not playable",
                )

                else -> resolvedByRecordingId.getValue(recordingId).toCurrentQueueEntry()
            }
        }
    }

    private fun resolveCurrentEntry(recordingId: Long): CurrentQueueEntry {
        return resolveEntriesOrThrow(listOf(recordingId)).single()
    }

    private fun getOrLoadStateLocked(accountId: Long): AccountPlayQueueState {
        states[accountId]?.let { return it }
        val loaded = stateStore.load(accountId)?.toRecoveredState()
            ?: AccountPlayQueueState.initial(accountId, timeProvider.nowMs())
        states[accountId] = loaded
        return loaded
    }

    private inline fun <T> withMutationContext(
        accountId: Long,
        expectedVersion: Long? = null,
        crossinline block: (MutationContext) -> T,
    ): T {
        return lockManager.withAccountLock(accountId) {
            val currentState = getOrLoadStateLocked(accountId)
            if (expectedVersion != null) {
                requireExpectedVersion(currentState, expectedVersion)
            }
            val context = MutationContext(
                currentState = currentState,
                nextState = currentState.deepCopy(),
                previousCurrentIndex = currentIndexOrNull(currentState),
                previousRecordingId = currentRecordingIdOf(currentState),
            )
            block(context)
        }
    }

    private fun persistAndCacheState(
        previousVersion: Long,
        state: AccountPlayQueueState,
    ) {
        if (!stateStore.save(previousVersion, state)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, VERSION_CONFLICT_REASON)
        }
        states[state.accountId] = state
    }

    private fun touchState(
        state: AccountPlayQueueState,
        nowMs: Long,
    ) {
        state.version += 1
        state.updatedAtMs = nowMs
    }

    private fun persistMutation(
        context: MutationContext,
        nowMs: Long,
    ): AccountPlayQueueState {
        touchState(context.nextState, nowMs)
        persistAndCacheState(context.currentState.version, context.nextState)
        return context.nextState
    }

    private fun buildPersistedChangeResult(
        context: MutationContext,
        nowMs: Long,
        removedIndex: Int? = null,
        removedRecordingId: Long? = null,
    ): CurrentQueueChangeResult {
        val nextState = persistMutation(context, nowMs)
        return buildChangeResult(
            nextState,
            context.previousCurrentIndex,
            context.previousRecordingId,
            removedIndex,
            removedRecordingId,
        )
    }

    private fun buildPersistedPlaybackState(
        context: MutationContext,
        nowMs: Long,
    ): AccountPlaybackState {
        return persistMutation(context, nowMs).toPlaybackState()
    }

    private fun buildNoopChangeResult(state: AccountPlayQueueState): CurrentQueueChangeResult {
        return CurrentQueueChangeResult(
            queue = buildQueueDto(state),
            previousCurrentIndex = currentIndexOrNull(state),
            currentIndex = currentIndexOrNull(state),
            previousRecordingId = currentRecordingIdOf(state),
            currentRecordingId = currentRecordingIdOf(state),
            changed = false,
        )
    }

    private fun buildChangeResult(
        state: AccountPlayQueueState,
        previousCurrentIndex: Int?,
        previousRecordingId: Long?,
        removedIndex: Int?,
        removedRecordingId: Long?,
    ): CurrentQueueChangeResult {
        return CurrentQueueChangeResult(
            queue = buildQueueDto(state),
            previousCurrentIndex = previousCurrentIndex,
            currentIndex = currentIndexOrNull(state),
            previousRecordingId = previousRecordingId,
            currentRecordingId = currentRecordingIdOf(state),
            removedIndex = removedIndex,
            removedRecordingId = removedRecordingId,
            changed = true,
        )
    }

    private fun buildQueueDto(state: AccountPlayQueueState): CurrentQueueDto {
        val items = resolveEntriesOrThrow(state.recordingIds).map(::toQueueItemDto)
        return CurrentQueueDto(
            items = items,
            recordingIds = state.recordingIds.toList(),
            currentIndex = state.currentIndex,
            playbackStrategy = state.playbackStrategy,
            stopStrategy = state.stopStrategy,
            playbackStatus = state.playbackStatus,
            positionMs = state.positionMs,
            serverTimeToExecuteMs = state.serverTimeToExecuteMs,
            version = state.version,
            updatedAtMs = state.updatedAtMs,
        )
    }

    private fun recoverPositionMs(
        state: AccountPlayQueueState,
        nowMs: Long,
    ): Long {
        if (state.playbackStatus != PlaybackStatus.PLAYING) {
            return state.positionMs
        }
        return state.positionMs + (nowMs - state.serverTimeToExecuteMs).coerceAtLeast(0L)
    }

    private fun requireExpectedVersion(
        state: AccountPlayQueueState,
        expectedVersion: Long,
    ): AccountPlayQueueState {
        if (state.version != expectedVersion) {
            throw ResponseStatusException(HttpStatus.CONFLICT, VERSION_CONFLICT_REASON)
        }
        return state
    }

    private fun AccountPlayQueueState.toPlaybackState(
        positionMs: Long = this.positionMs,
        serverTimeToExecuteMs: Long = this.serverTimeToExecuteMs,
    ): AccountPlaybackState {
        return AccountPlaybackState(
            accountId = accountId,
            status = playbackStatus,
            currentIndex = currentIndexOrNull(this),
            positionSeconds = positionMs / 1_000.0,
            serverTimeToExecuteMs = serverTimeToExecuteMs,
            version = version,
            updatedAtMs = updatedAtMs,
        )
    }

    private fun AccountPlayQueueState.toRecoveredState(): AccountPlayQueueState {
        if (playbackStatus != PlaybackStatus.PLAYING) {
            return this
        }
        return copy(
            playbackStatus = PlaybackStatus.PAUSED,
            serverTimeToExecuteMs = 0L,
        )
    }

    private fun AccountPlayQueueState.deepCopy(): AccountPlayQueueState {
        return copy(
            recordingIds = recordingIds.toMutableList(),
            shuffleIndices = shuffleIndices.toMutableList(),
        )
    }

    private fun ResolvedQueueRecording.toCurrentQueueEntry(): CurrentQueueEntry {
        return CurrentQueueEntry(
            recordingId = recordingId,
            workId = workId,
            title = title,
            artistLabel = artistLabel,
            coverMediaFileId = coverMediaFileId,
            durationMs = durationMs,
        )
    }

    private fun toQueueItemDto(entry: CurrentQueueEntry): CurrentQueueItemDto {
        return CurrentQueueItemDto(
            recordingId = entry.recordingId,
            title = entry.title,
            artistLabel = entry.artistLabel,
            coverUrl = entry.coverMediaFileId?.let(urlSigner::generatePresignedPath),
            durationMs = entry.durationMs,
        )
    }

    companion object {
        const val VERSION_CONFLICT_REASON: String = "Queue version conflict"

        private const val RADIO_RECENT_WINDOW_MAX = 16
    }
}

interface CurrentQueueRecordingCatalog {
    fun getExistingRecordingIds(recordingIds: Set<Long>): Set<Long>

    fun countWorks(): Int

    fun loadResolvedRecordings(recordingIds: Set<Long>): List<ResolvedQueueRecording>

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
    ): List<ResolvedQueueRecording> {
        if (recordingIds.isEmpty()) {
            return emptyList()
        }

        val recordings = sql.createQuery(Recording::class) {
            where(table.id valueIn recordingIds)
            select(table.fetch(RECORDING_FETCHER))
        }.execute()

        return recordings.map { recording ->
            ResolvedQueueRecording(
                recordingId = recording.id,
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
