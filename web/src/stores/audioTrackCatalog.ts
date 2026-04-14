import type { Ref, ShallowRef } from 'vue'
import { api } from '@/ApiInstance'
import { resolveCover, resolvePlayableAudio } from '@/composables/recordingMedia'
import { buildApiUrl } from '@/runtime/platform'
import type { CurrentQueueDto, CurrentQueueItemDto } from '@/services/playbackSyncProtocol'
import {
    type AudioTrack,
    MAX_KNOWN_TRACKS,
    cloneQueue,
    cloneTrack,
    pickFirstNonBlank,
    resolveMetadataArtistLabel,
} from '@/stores/audioShared'

type PlaybackRecordingMetadata = Awaited<ReturnType<typeof api.recordingController.getRecording>>

type UseAudioTrackCatalogOptions = {
    currentTrack: Ref<AudioTrack | null>
    currentQueue: Ref<CurrentQueueDto>
    currentBuffer: ShallowRef<AudioBuffer | null>
    currentBufferTrack: ShallowRef<AudioTrack | null>
    duration: Ref<number>
}

export const useAudioTrackCatalog = (options: UseAudioTrackCatalogOptions) => {
    const knownTracks = new Map<number, AudioTrack>()
    const hydratedTrackIds = new Set<number>()
    const trackMetadataRequests = new Map<number, Promise<void>>()
    const recordingMetadataCache = new Map<number, PlaybackRecordingMetadata>()

    let trackMetadataGeneration = 0

    const cacheTrack = (track: AudioTrack) => {
        if (knownTracks.has(track.id)) {
            knownTracks.delete(track.id)
        }
        knownTracks.set(track.id, cloneTrack(track))
        if (knownTracks.size > MAX_KNOWN_TRACKS) {
            const oldestKey = knownTracks.keys().next().value
            if (oldestKey !== undefined) {
                knownTracks.delete(oldestKey)
            }
        }
    }

    const createTrackFromQueueItem = (item: CurrentQueueItemDto): AudioTrack => {
        const current =
            options.currentTrack.value?.id === item.recordingId ? options.currentTrack.value : null
        const cached = knownTracks.get(item.recordingId)
        let workId = current?.workId
        if (workId === undefined && cached?.workId !== undefined) {
            workId = cached.workId
        }

        const cover = item.coverUrl ? buildApiUrl(item.coverUrl) : ''
        const title =
            item.title || cached?.title || current?.title || `Recording #${item.recordingId}`
        const artist = item.artistLabel || cached?.artist || current?.artist || 'Unknown Artist'

        return {
            id: item.recordingId,
            title,
            artist,
            cover: cover || cached?.cover || current?.cover || '',
            src: current?.src || cached?.src,
            mediaFileId: current?.mediaFileId ?? cached?.mediaFileId,
            ...(workId === undefined ? {} : { workId }),
        }
    }

    const applyQueueSnapshot = (queue: CurrentQueueDto) => {
        options.currentQueue.value = cloneQueue(queue)
        options.currentQueue.value.items.forEach((item) => {
            cacheTrack(createTrackFromQueueItem(item))
        })

        const currentEntryId = options.currentQueue.value.currentEntryId
        if (currentEntryId === undefined) {
            return
        }

        const nextCurrentItem =
            options.currentQueue.value.items.find((item) => item.entryId === currentEntryId) ?? null
        if (!nextCurrentItem) {
            return
        }

        const nextTrack = createTrackFromQueueItem(nextCurrentItem)
        cacheTrack(nextTrack)
        options.currentTrack.value = cloneTrack(nextTrack)
        const hasMatchingBufferedTrack =
            nextTrack.mediaFileId !== undefined &&
            options.currentBufferTrack.value?.mediaFileId === nextTrack.mediaFileId &&
            options.currentBuffer.value !== null
        if (!hasMatchingBufferedTrack && nextCurrentItem.durationMs > 0) {
            options.duration.value = nextCurrentItem.durationMs / 1_000
        }
    }

    const queueRecordingIdsEqual = (recordingIds: number[]) => {
        if (recordingIds.length !== options.currentQueue.value.items.length) {
            return false
        }
        return options.currentQueue.value.items.every(
            (item, index) => item.recordingId === recordingIds[index],
        )
    }

    const rememberHydratedTrack = (track: AudioTrack) => {
        cacheTrack(track)
        hydratedTrackIds.add(track.id)
    }

    const applyRecordingMetadata = (
        track: AudioTrack,
        metadata: PlaybackRecordingMetadata,
    ): AudioTrack => {
        const title =
            pickFirstNonBlank(
                metadata.title,
                metadata.comment,
                metadata.work?.title,
                track.title,
            ) ?? `Recording #${track.id}`
        const artist =
            pickFirstNonBlank(resolveMetadataArtistLabel(metadata.artists), track.artist) ??
            'Unknown Artist'
        const cover = metadata.cover?.url ? resolveCover(metadata.cover) : (track.cover ?? '')
        let workId = track.workId
        if (metadata.work?.id !== undefined) {
            workId = metadata.work.id
        }

        return {
            ...track,
            title,
            artist,
            cover,
            ...(workId !== undefined ? { workId } : {}),
        }
    }

    const buildTrackWithResolvedSource = (
        track: AudioTrack,
        metadata: PlaybackRecordingMetadata,
    ): AudioTrack | null => {
        const enrichedTrack = applyRecordingMetadata(track, metadata)
        const playableAudio = resolvePlayableAudio(metadata.assets ?? [])
        if (!playableAudio) {
            return null
        }

        return {
            ...enrichedTrack,
            src: playableAudio.src,
            mediaFileId: playableAudio.mediaFileId,
        }
    }

    const fetchRecordingMetadata = async (recordingId: number) => {
        const cachedMetadata = recordingMetadataCache.get(recordingId)
        if (cachedMetadata) {
            return cachedMetadata
        }

        const existingRequest = trackMetadataRequests.get(recordingId)
        if (existingRequest) {
            await existingRequest
            return recordingMetadataCache.get(recordingId) ?? null
        }

        const generation = trackMetadataGeneration
        const request = api.recordingController
            .getRecording({ id: recordingId })
            .then((metadata) => {
                if (generation !== trackMetadataGeneration) {
                    return
                }
                recordingMetadataCache.set(recordingId, metadata)
            })
            .catch(() => {
                // Metadata enrichment is best-effort and must not interrupt sync playback.
            })
            .finally(() => {
                if (trackMetadataRequests.get(recordingId) === request) {
                    trackMetadataRequests.delete(recordingId)
                }
            })

        trackMetadataRequests.set(recordingId, request)
        await request
        return recordingMetadataCache.get(recordingId) ?? null
    }

    const hydrateTrackMetadata = (track: AudioTrack) => {
        if (hydratedTrackIds.has(track.id)) {
            return Promise.resolve()
        }

        const generation = trackMetadataGeneration
        return fetchRecordingMetadata(track.id)
            .then((metadata) => {
                if (!metadata || generation !== trackMetadataGeneration) {
                    return
                }

                const hydratedTrack =
                    buildTrackWithResolvedSource(track, metadata) ??
                    applyRecordingMetadata(track, metadata)
                rememberHydratedTrack(hydratedTrack)

                if (options.currentTrack.value?.id === track.id) {
                    options.currentTrack.value = cloneTrack({
                        ...options.currentTrack.value,
                        ...hydratedTrack,
                    })
                }

                if (options.currentBufferTrack.value?.id === track.id) {
                    options.currentBufferTrack.value = {
                        ...options.currentBufferTrack.value,
                        ...hydratedTrack,
                    }
                }
            })
            .catch(() => {
                // Metadata enrichment is best-effort and must not interrupt sync playback.
            })
    }

    const createTrackShell = (recordingId: number): AudioTrack => {
        const current =
            options.currentTrack.value?.id === recordingId ? options.currentTrack.value : null
        const cached = knownTracks.get(recordingId)
        const base = current ?? cached
        const track: AudioTrack = {
            id: recordingId,
            title: base?.title ?? `Recording #${recordingId}`,
            artist: base?.artist ?? 'Unknown Artist',
            cover: base?.cover ?? '',
            src: base?.src,
            mediaFileId: base?.mediaFileId,
            ...(base?.workId !== undefined ? { workId: base.workId } : {}),
        }
        cacheTrack(track)
        return track
    }

    const resolveTrackForPlayback = async (track: AudioTrack) => {
        if (track.src && track.mediaFileId !== undefined) {
            return track
        }

        const metadata = await fetchRecordingMetadata(track.id)
        if (!metadata) {
            return null
        }

        const resolvedTrack = buildTrackWithResolvedSource(track, metadata)
        if (!resolvedTrack) {
            rememberHydratedTrack(applyRecordingMetadata(track, metadata))
            return null
        }

        rememberHydratedTrack(resolvedTrack)
        if (options.currentTrack.value?.id === track.id) {
            options.currentTrack.value = cloneTrack(resolvedTrack)
        }
        return resolvedTrack
    }

    const resetTrackCatalog = () => {
        trackMetadataGeneration += 1
        trackMetadataRequests.clear()
        hydratedTrackIds.clear()
        knownTracks.clear()
        recordingMetadataCache.clear()
    }

    return {
        cacheTrack,
        createTrackFromQueueItem,
        applyQueueSnapshot,
        queueRecordingIdsEqual,
        rememberHydratedTrack,
        createTrackShell,
        resolveTrackForPlayback,
        hydrateTrackMetadata,
        resetTrackCatalog,
    }
}
