import type { AlbumDto, WorkDto } from '@/__generated/model/dto'
import { api } from '@/ApiInstance'
import {
    resolveAudio,
    resolveArtistName,
    resolveCover,
    type RecordingAsset,
} from '@/composables/recordingMedia'

type AlbumDetail = AlbumDto['AlbumController/DETAIL_ALBUM_FETCHER']
type WorkDetail = WorkDto['WorkController/DEFAULT_WORK_FETCHER']
type DetailRecording = AlbumDetail['recordings'][number] | WorkDetail['recordings'][number]

export type PlayableTrack = {
    id: number
    title: string
    artist: string
    cover: string
    src: string
    workId?: number
}

export type PlayableTrackFallback = {
    title?: string
    artist?: string
    cover?: string
}

const pickPlayableRecording = (recordings: readonly DetailRecording[]) => {
    const defaultTrack = recordings.find(
        (recording) =>
            recording.defaultInWork && resolveAudio(recording.assets as readonly RecordingAsset[]),
    )
    if (defaultTrack) {
        return defaultTrack
    }
    return recordings.find((recording) =>
        resolveAudio(recording.assets as readonly RecordingAsset[]),
    )
}

const resolveTrack = (
    recording: DetailRecording,
    detailTitle: string,
    fallback: PlayableTrackFallback,
): Omit<PlayableTrack, 'workId'> | undefined => {
    const src = resolveAudio(recording.assets as readonly RecordingAsset[])
    if (!src) {
        return undefined
    }

    return {
        id: recording.id,
        title:
            recording.title ||
            recording.comment ||
            detailTitle ||
            fallback.title ||
            'Untitled Track',
        artist: resolveArtistName(recording.artists) || fallback.artist || 'Unknown Artist',
        cover: recording.cover?.id ? resolveCover(recording.cover.id) : fallback.cover || '',
        src,
    }
}

export const resolveAlbumPlayableTrack = async (
    albumId: number,
    fallback: PlayableTrackFallback = {},
): Promise<PlayableTrack | undefined> => {
    const detail = await api.albumController.getAlbum({ id: albumId })
    const recording = pickPlayableRecording(detail.recordings)
    if (!recording) {
        return undefined
    }
    return resolveTrack(recording, detail.title, fallback)
}

export const resolveWorkPlayableTrack = async (
    workId: number,
    fallback: PlayableTrackFallback = {},
): Promise<PlayableTrack | undefined> => {
    const detail = await api.workController.getWorkById({ id: workId })
    const recording = pickPlayableRecording(detail.recordings)
    if (!recording) {
        return undefined
    }

    const track = resolveTrack(recording, detail.title, fallback)
    if (!track) {
        return undefined
    }

    return {
        ...track,
        workId: detail.id,
    }
}
