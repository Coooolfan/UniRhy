import { api } from '@/ApiInstance'
import {
    pickPlayableRecordingEntry,
    resolveArtistName,
    resolveCover,
    resolvePlayableAudio,
    type RecordingArtist,
    type RecordingAsset,
} from '@/composables/recordingMedia'
import type { AudioTrack } from '@/stores/audioShared'
import { useUserStore } from '@/stores/user'

export type RecordingPlaybackCandidate = {
    id: number
    title: string
    artist: string
    cover: string
    assets: readonly RecordingAsset[]
    defaultInWork?: boolean
    workId?: number
}

export type PlaybackTrackFallback = {
    title?: string
    artist?: string
    cover?: string
    workId?: number
}

export type PlaybackContainerType = 'album' | 'work'

type ContainerTrackCacheEntry = {
    recordingId: number | null
    track: AudioTrack | null
}

type ContainerTrackCacheState = {
    requests: Map<string, Promise<ContainerTrackCacheEntry>>
    results: Map<string, ContainerTrackCacheEntry>
    revisions: Map<string, number>
}

const audioSourceCache = new Map<string, ReturnType<typeof resolvePlayableAudio> | null>()
const albumTrackCache: ContainerTrackCacheState = {
    requests: new Map(),
    results: new Map(),
    revisions: new Map(),
}
const workTrackCache: ContainerTrackCacheState = {
    requests: new Map(),
    results: new Map(),
    revisions: new Map(),
}

const normalizePreferredAssetFormat = (preferredAssetFormat?: string) => {
    const normalized = preferredAssetFormat?.trim().toLowerCase()
    return normalized || null
}

const getPreferredAssetFormat = async (preferredAssetFormat?: string) => {
    const normalizedPreferredAssetFormat = normalizePreferredAssetFormat(preferredAssetFormat)
    if (normalizedPreferredAssetFormat) {
        return normalizedPreferredAssetFormat
    }
    return normalizePreferredAssetFormat(await useUserStore().getPreferredAssetFormat())
}

const getPreferredAssetFormatSync = (preferredAssetFormat?: string) => {
    const normalizedPreferredAssetFormat = normalizePreferredAssetFormat(preferredAssetFormat)
    if (normalizedPreferredAssetFormat) {
        return normalizedPreferredAssetFormat
    }
    return normalizePreferredAssetFormat(useUserStore().preferredAssetFormat)
}

const getAudioSourceCacheKey = (recordingId: number, preferredAssetFormat: string | null) => {
    return `${recordingId}:${preferredAssetFormat ?? 'default'}`
}

const getContainerTrackCacheKey = (id: number, preferredAssetFormat: string | null) => {
    return `${id}:${preferredAssetFormat ?? 'default'}`
}

const buildAudioTrack = (
    candidate: RecordingPlaybackCandidate,
    playableAudio: NonNullable<ReturnType<typeof resolvePlayableAudio>>,
): AudioTrack => {
    return {
        id: candidate.id,
        title: candidate.title,
        artist: candidate.artist,
        cover: candidate.cover,
        src: playableAudio.src,
        mediaFileId: playableAudio.mediaFileId,
        ...(candidate.workId === undefined ? {} : { workId: candidate.workId }),
    }
}

export const createRecordingPlaybackCandidate = (
    recording: {
        id: number
        title?: string
        comment?: string
        artists?: ReadonlyArray<RecordingArtist>
        cover?: { id?: number; url?: string } | null
        assets?: readonly RecordingAsset[] | null
        defaultInWork?: boolean
    },
    fallback: PlaybackTrackFallback = {},
): RecordingPlaybackCandidate => {
    return {
        id: recording.id,
        title: recording.title || recording.comment || fallback.title || 'Untitled Track',
        artist: resolveArtistName(recording.artists) || fallback.artist || 'Unknown Artist',
        cover: resolveCover(recording.cover) || fallback.cover || '',
        assets: recording.assets ?? [],
        defaultInWork: recording.defaultInWork,
        ...(fallback.workId === undefined ? {} : { workId: fallback.workId }),
    }
}

export const hasPlayableRecordingCandidate = (
    candidate: RecordingPlaybackCandidate,
    preferredAssetFormat?: string,
) => {
    const normalizedPreferredAssetFormat = getPreferredAssetFormatSync(preferredAssetFormat)
    return (
        resolvePlayableAudio(candidate.assets, normalizedPreferredAssetFormat ?? undefined) !==
        undefined
    )
}

export const hasPlayableRecordingCandidates = (
    candidates: readonly RecordingPlaybackCandidate[],
    preferredAssetFormat?: string,
) => {
    return candidates.some((candidate) =>
        hasPlayableRecordingCandidate(candidate, preferredAssetFormat),
    )
}

export const pickInitialRecordingIdFromCandidates = (
    candidates: readonly RecordingPlaybackCandidate[],
    strategy: 'first-playable' | 'default-first',
    preferredAssetFormat?: string,
) => {
    if (candidates.length === 0) {
        return null
    }

    if (strategy === 'default-first') {
        return (
            candidates.find((candidate) => candidate.defaultInWork)?.id ?? candidates[0]?.id ?? null
        )
    }

    return (
        candidates.find((candidate) =>
            hasPlayableRecordingCandidate(candidate, preferredAssetFormat),
        )?.id ??
        candidates[0]?.id ??
        null
    )
}

export const resolvePlaybackTrackFromCandidate = async (
    candidate: RecordingPlaybackCandidate,
    preferredAssetFormat?: string,
): Promise<AudioTrack | null> => {
    const normalizedPreferredAssetFormat = await getPreferredAssetFormat(preferredAssetFormat)
    const cacheKey = getAudioSourceCacheKey(candidate.id, normalizedPreferredAssetFormat)
    let playableAudio: ReturnType<typeof resolvePlayableAudio> | null

    if (audioSourceCache.has(cacheKey)) {
        playableAudio = audioSourceCache.get(cacheKey) ?? null
    } else {
        playableAudio =
            resolvePlayableAudio(candidate.assets, normalizedPreferredAssetFormat ?? undefined) ??
            null
        audioSourceCache.set(cacheKey, playableAudio)
    }

    if (!playableAudio) {
        return null
    }

    return buildAudioTrack(candidate, playableAudio)
}

export const resolvePlaybackTracksFromCandidates = async (
    candidates: readonly RecordingPlaybackCandidate[],
    preferredAssetFormat?: string,
) => {
    const tracks = await Promise.all(
        candidates.map((candidate) =>
            resolvePlaybackTrackFromCandidate(candidate, preferredAssetFormat),
        ),
    )
    return tracks.filter((track): track is AudioTrack => track !== null)
}

const resolveContainerTrack = (
    cache: ContainerTrackCacheState,
    cacheKey: string,
    load: () => Promise<ContainerTrackCacheEntry>,
) => {
    if (cache.results.has(cacheKey)) {
        return cache.results.get(cacheKey)?.track ?? null
    }

    const inFlightRequest = cache.requests.get(cacheKey)
    if (inFlightRequest) {
        return inFlightRequest.then((entry) => entry.track)
    }

    const revision = cache.revisions.get(cacheKey) ?? 0
    const request = load()
        .then((entry) => {
            if ((cache.revisions.get(cacheKey) ?? 0) === revision) {
                cache.results.set(cacheKey, entry)
            }
            return entry
        })
        .finally(() => {
            cache.requests.delete(cacheKey)
        })

    cache.requests.set(cacheKey, request)
    return request.then((entry) => entry.track)
}

const peekContainerTrack = (
    cache: ContainerTrackCacheState,
    id: number,
    preferredAssetFormat?: string,
) => {
    const cacheKey = getContainerTrackCacheKey(
        id,
        getPreferredAssetFormatSync(preferredAssetFormat),
    )
    return cache.results.get(cacheKey)?.track ?? null
}

const invalidateContainerTrackCacheEntry = (cache: ContainerTrackCacheState, cacheKey: string) => {
    cache.results.delete(cacheKey)
    cache.requests.delete(cacheKey)
    cache.revisions.set(cacheKey, (cache.revisions.get(cacheKey) ?? 0) + 1)
}

const invalidateContainerTrackCache = (
    cache: ContainerTrackCacheState,
    id: number,
    preferredAssetFormat?: string,
) => {
    const normalizedPreferredAssetFormat = normalizePreferredAssetFormat(preferredAssetFormat)
    if (normalizedPreferredAssetFormat) {
        invalidateContainerTrackCacheEntry(
            cache,
            getContainerTrackCacheKey(id, normalizedPreferredAssetFormat),
        )
        return
    }

    for (const cacheKey of new Set([...cache.results.keys(), ...cache.requests.keys()])) {
        if (cacheKey.startsWith(`${id}:`)) {
            invalidateContainerTrackCacheEntry(cache, cacheKey)
        }
    }
}

const invalidateContainerTrackCachesByRecording = (
    cache: ContainerTrackCacheState,
    recordingId: number,
) => {
    for (const [cacheKey, entry] of cache.results.entries()) {
        if (entry.recordingId === recordingId) {
            invalidateContainerTrackCacheEntry(cache, cacheKey)
        }
    }
}

export const peekResolvedPlayableTrack = (
    type: PlaybackContainerType,
    id: number,
    preferredAssetFormat?: string,
) => {
    return type === 'album'
        ? peekContainerTrack(albumTrackCache, id, preferredAssetFormat)
        : peekContainerTrack(workTrackCache, id, preferredAssetFormat)
}

export const resolveAlbumPlayableTrack = async (
    albumId: number,
    fallback: PlaybackTrackFallback = {},
): Promise<AudioTrack | null> => {
    const normalizedPreferredAssetFormat = await getPreferredAssetFormat()
    const cacheKey = getContainerTrackCacheKey(albumId, normalizedPreferredAssetFormat)

    return resolveContainerTrack(albumTrackCache, cacheKey, async () => {
        const detail = await api.albumController.getAlbum({ id: albumId })
        const playableEntry = pickPlayableRecordingEntry(
            detail.recordings,
            normalizedPreferredAssetFormat ?? undefined,
        )
        if (!playableEntry) {
            return {
                recordingId: null,
                track: null,
            }
        }

        const candidate = createRecordingPlaybackCandidate(playableEntry.recording, fallback)
        return {
            recordingId: playableEntry.recording.id,
            track: await resolvePlaybackTrackFromCandidate(
                candidate,
                normalizedPreferredAssetFormat ?? undefined,
            ),
        }
    })
}

export const resolveWorkPlayableTrack = async (
    workId: number,
    fallback: PlaybackTrackFallback = {},
): Promise<AudioTrack | null> => {
    const normalizedPreferredAssetFormat = await getPreferredAssetFormat()
    const cacheKey = getContainerTrackCacheKey(workId, normalizedPreferredAssetFormat)

    return resolveContainerTrack(workTrackCache, cacheKey, async () => {
        const detail = await api.workController.getWorkById({ id: workId })
        const playableEntry = pickPlayableRecordingEntry(
            detail.recordings,
            normalizedPreferredAssetFormat ?? undefined,
        )
        if (!playableEntry) {
            return {
                recordingId: null,
                track: null,
            }
        }

        const candidate = createRecordingPlaybackCandidate(playableEntry.recording, {
            ...fallback,
            workId: detail.id,
        })
        return {
            recordingId: playableEntry.recording.id,
            track: await resolvePlaybackTrackFromCandidate(
                candidate,
                normalizedPreferredAssetFormat ?? undefined,
            ),
        }
    })
}

export const invalidateResolvedPlayableTrack = (
    type: PlaybackContainerType,
    id: number,
    preferredAssetFormat?: string,
) => {
    if (type === 'album') {
        invalidateContainerTrackCache(albumTrackCache, id, preferredAssetFormat)
        return
    }

    invalidateContainerTrackCache(workTrackCache, id, preferredAssetFormat)
}

export const invalidateResolvedPlayableTracksByRecording = (recordingId: number) => {
    for (const cacheKey of audioSourceCache.keys()) {
        if (cacheKey.startsWith(`${recordingId}:`)) {
            audioSourceCache.delete(cacheKey)
        }
    }

    invalidateContainerTrackCachesByRecording(albumTrackCache, recordingId)
    invalidateContainerTrackCachesByRecording(workTrackCache, recordingId)
}

export const resetRecordingPlaybackResolverCaches = () => {
    audioSourceCache.clear()
    albumTrackCache.requests.clear()
    albumTrackCache.results.clear()
    albumTrackCache.revisions.clear()
    workTrackCache.requests.clear()
    workTrackCache.results.clear()
    workTrackCache.revisions.clear()
}
