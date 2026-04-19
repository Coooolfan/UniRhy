import type { AlbumDto, WorkDto } from '@/__generated/model/dto'
import { api } from '@/ApiInstance'
import {
    pickPlayableRecordingEntry,
    resolvePlayableAudio,
    resolveArtistName,
    resolveCover,
} from '@/composables/recordingMedia'
import { useUserStore } from '@/stores/user'

type AlbumDetail = AlbumDto['AlbumController/DETAIL_ALBUM_FETCHER']
type WorkDetail = WorkDto['WorkController/DEFAULT_WORK_FETCHER']
type DetailRecording = AlbumDetail['recordings'][number] | WorkDetail['recordings'][number]

export type PlayableTrack = {
    id: number
    title: string
    artist: string
    cover: string
    src: string
    mediaFileId: number
    workId?: number
}

export type PlayableTrackFallback = {
    title?: string
    artist?: string
    cover?: string
}

const resolveTrack = (
    recording: DetailRecording,
    options: {
        detailTitle: string
        fallback: PlayableTrackFallback
        preferredAssetFormat?: string
    },
): Omit<PlayableTrack, 'workId'> | undefined => {
    const playableAudio = resolvePlayableAudio(recording.assets, options.preferredAssetFormat)
    if (!playableAudio) {
        return undefined
    }

    return {
        id: recording.id,
        title:
            recording.title ||
            recording.comment ||
            options.detailTitle ||
            options.fallback.title ||
            'Untitled Track',
        artist: resolveArtistName(recording.artists) || options.fallback.artist || 'Unknown Artist',
        cover: recording.cover?.url ? resolveCover(recording.cover) : options.fallback.cover || '',
        src: playableAudio.src,
        mediaFileId: playableAudio.mediaFileId,
    }
}

export const resolveAlbumPlayableTrack = async (
    albumId: number,
    fallback: PlayableTrackFallback = {},
): Promise<PlayableTrack | undefined> => {
    const userStore = useUserStore()
    const preferredAssetFormat = await userStore.getPreferredAssetFormat()
    const detail = await api.albumController.getAlbum({ id: albumId })
    const playableEntry = pickPlayableRecordingEntry(detail.recordings, preferredAssetFormat)
    if (!playableEntry) {
        return undefined
    }
    return resolveTrack(playableEntry.recording, {
        detailTitle: detail.title,
        fallback,
        preferredAssetFormat,
    })
}

export const resolveWorkPlayableTrack = async (
    workId: number,
    fallback: PlayableTrackFallback = {},
): Promise<PlayableTrack | undefined> => {
    const userStore = useUserStore()
    const preferredAssetFormat = await userStore.getPreferredAssetFormat()
    const detail = await api.workController.getWorkById({ id: workId })
    const playableEntry = pickPlayableRecordingEntry(detail.recordings, preferredAssetFormat)
    if (!playableEntry) {
        return undefined
    }

    const track = resolveTrack(playableEntry.recording, {
        detailTitle: detail.title,
        fallback,
        preferredAssetFormat,
    })
    if (!track) {
        return undefined
    }

    return {
        ...track,
        workId: detail.id,
    }
}
