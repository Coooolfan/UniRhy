import { computed, type Ref, type ShallowRef } from 'vue'
import type {
    CurrentQueueDto,
    CurrentQueueItemDto,
    PlaybackSyncStatePayload,
} from '@/services/playbackSyncProtocol'
import type { AudioQueueEntry, AudioTrack } from '@/stores/audioShared'
import { nowClientMs } from '@/utils/time'

/**
 * API 返回的队列快照中 playbackStrategy 是宽泛字符串，需归一化为协议枚举。
 */
export type ApiCurrentQueueDto = Omit<CurrentQueueDto, 'playbackStrategy'> & {
    playbackStrategy: string
}

const KNOWN_PLAYBACK_STRATEGIES = ['SEQUENTIAL', 'SHUFFLE', 'SINGLE', 'RADIO'] as const

const normalizePlaybackStrategy = (value: string): CurrentQueueDto['playbackStrategy'] => {
    return KNOWN_PLAYBACK_STRATEGIES.find((strategy) => strategy === value) ?? 'SEQUENTIAL'
}

export const normalizeApiQueueSnapshot = (queue: ApiCurrentQueueDto): CurrentQueueDto => ({
    ...queue,
    playbackStrategy: normalizePlaybackStrategy(queue.playbackStrategy),
})

type UseAudioLocalQueueOptions = {
    currentQueue: Ref<CurrentQueueDto>
    currentTime: Ref<number>
    currentBuffer: ShallowRef<AudioBuffer | null>
    currentBufferTrack: ShallowRef<AudioTrack | null>
    latestSnapshot: Ref<PlaybackSyncStatePayload | null>
    createTrackFromQueueItem: (item: CurrentQueueItemDto) => AudioTrack
    applyQueueSnapshot: (queue: CurrentQueueDto) => void
    rememberHydratedTrack: (track: AudioTrack) => void
}

/**
 * 本地队列层：集中管理 currentQueue 的版本推进、本地队列项构造与快照应用，
 * 独立播放模式下的队列改动与服务端快照的落地都收敛于此。
 */
export const useAudioLocalQueue = (options: UseAudioLocalQueueOptions) => {
    const {
        currentQueue,
        currentTime,
        currentBuffer,
        currentBufferTrack,
        latestSnapshot,
        createTrackFromQueueItem,
        applyQueueSnapshot,
        rememberHydratedTrack,
    } = options

    const currentQueueEntry = computed(() => {
        const { currentIndex, items } = currentQueue.value
        if (currentIndex < 0) {
            return null
        }
        return items[currentIndex] ?? null
    })

    const currentQueueIndex = computed(() => {
        const { currentIndex, items } = currentQueue.value
        if (currentIndex < 0 || currentIndex >= items.length) {
            return -1
        }
        return currentIndex
    })

    const queueEntries = computed<AudioQueueEntry[]>(() =>
        currentQueue.value.items.map((item, index) => {
            const track = createTrackFromQueueItem(item)

            return {
                queueIndex: index,
                recordingId: item.recordingId,
                mediaFileId: track.mediaFileId,
                title: track.title,
                artist: track.artist,
                cover: track.cover,
                durationMs: item.durationMs,
            }
        }),
    )

    const syncQueuePlaybackState = (state: PlaybackSyncStatePayload) => {
        currentQueue.value = {
            ...currentQueue.value,
            currentIndex: state.currentIndex ?? currentQueue.value.currentIndex,
            playbackStatus: state.status,
            positionMs: Math.max(0, Math.round(state.positionSeconds * 1_000)),
            serverTimeToExecuteMs: state.serverTimeToExecuteMs,
            version: state.version,
            updatedAtMs: state.updatedAtMs,
        }
    }

    const applyApiQueueSnapshot = (queue: ApiCurrentQueueDto) => {
        const normalizedQueue = normalizeApiQueueSnapshot(queue)
        applyQueueSnapshot(normalizedQueue)
        return normalizedQueue
    }

    const getCurrentQueueVersion = () => {
        return Math.max(currentQueue.value.version, latestSnapshot.value?.version ?? 0)
    }

    const nextLocalQueueVersion = () => getCurrentQueueVersion() + 1

    const updateLocalQueuePlaybackState = (
        playbackStatus = currentQueue.value.playbackStatus,
        positionSeconds = currentTime.value,
    ) => {
        currentQueue.value = {
            ...currentQueue.value,
            playbackStatus,
            positionMs: Math.max(0, Math.round(positionSeconds * 1_000)),
            serverTimeToExecuteMs: 0,
            version: nextLocalQueueVersion(),
            updatedAtMs: nowClientMs(),
        }
    }

    const getQueueRecordingId = (queueIndex: number | null | undefined) => {
        if (queueIndex === null || queueIndex === undefined || queueIndex < 0) {
            return null
        }
        return currentQueue.value.recordingIds[queueIndex] ?? null
    }

    const getCurrentQueueControlContext = () => {
        const currentIndex = currentQueueIndex.value
        if (currentIndex < 0) {
            return null
        }
        return {
            currentIndex,
            version: getCurrentQueueVersion(),
        }
    }

    const findExistingQueueItem = (recordingId: number) => {
        return currentQueue.value.items.find((item) => item.recordingId === recordingId) ?? null
    }

    const createLocalQueueItem = (track: AudioTrack): CurrentQueueItemDto => {
        const existingItem = findExistingQueueItem(track.id)
        const bufferedDurationMs =
            currentBufferTrack.value?.id === track.id && currentBuffer.value
                ? Math.round(currentBuffer.value.duration * 1_000)
                : null

        return {
            recordingId: track.id,
            title: track.title,
            artistLabel: track.artist,
            coverUrl: track.cover,
            durationMs: bufferedDurationMs ?? existingItem?.durationMs ?? 0,
        }
    }

    const updateLocalQueueCurrentIndex = (currentIndex: number) => {
        currentQueue.value = {
            ...currentQueue.value,
            currentIndex,
            version: nextLocalQueueVersion(),
            updatedAtMs: nowClientMs(),
        }
    }

    const applyLocalQueue = (tracks: AudioTrack[], currentIndex: number) => {
        tracks.forEach((track) => {
            rememberHydratedTrack(track)
        })

        currentQueue.value = {
            items: tracks.map((track) => createLocalQueueItem(track)),
            recordingIds: tracks.map((track) => track.id),
            currentIndex,
            playbackStrategy: currentQueue.value.playbackStrategy,
            stopStrategy: currentQueue.value.stopStrategy,
            playbackStatus: currentQueue.value.playbackStatus,
            positionMs: currentQueue.value.positionMs,
            serverTimeToExecuteMs: 0,
            version: nextLocalQueueVersion(),
            updatedAtMs: nowClientMs(),
        }
    }

    const updateLocalCurrentQueueItemDuration = (track: AudioTrack, buffer: AudioBuffer) => {
        const itemIndex = currentQueue.value.items.findIndex(
            (item) => item.recordingId === track.id,
        )
        if (itemIndex < 0) {
            return
        }

        const nextItems = currentQueue.value.items.map((item, index) =>
            index === itemIndex
                ? { ...item, durationMs: Math.round(buffer.duration * 1_000) }
                : item,
        )
        currentQueue.value = {
            ...currentQueue.value,
            items: nextItems,
            version: nextLocalQueueVersion(),
            updatedAtMs: nowClientMs(),
        }
    }

    return {
        currentQueueEntry,
        currentQueueIndex,
        queueEntries,
        syncQueuePlaybackState,
        applyApiQueueSnapshot,
        getCurrentQueueVersion,
        nextLocalQueueVersion,
        updateLocalQueuePlaybackState,
        getQueueRecordingId,
        getCurrentQueueControlContext,
        findExistingQueueItem,
        createLocalQueueItem,
        updateLocalQueueCurrentIndex,
        applyLocalQueue,
        updateLocalCurrentQueueItemDuration,
    }
}
