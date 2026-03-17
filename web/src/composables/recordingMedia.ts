import { buildApiUrl } from '@/runtime/platform'

export type RecordingAsset = {
    mediaFile: {
        id: number
        mimeType: string
        objectKey: string
        ossProvider?: { id: number }
        fsProvider?: { id: number }
    }
}

export type RecordingArtist = {
    id?: number
    displayName?: string
    name?: string
}

export type PlayableAudioSource = {
    src: string
    mediaFileId: number
}

export type NormalizedRecordingBase = {
    id: number
    title: string
    artist: string
    cover: string
    audioSrc?: string
    mediaFileId?: number
}

type NormalizableRecording = {
    id: number
    title?: string
    comment?: string
    artists?: ReadonlyArray<RecordingArtist>
    cover?: { id?: number } | null
    assets?: readonly RecordingAsset[] | null
    defaultInWork?: boolean
}

type InitialRecordingCandidate = {
    id: number
    audioSrc?: string
    isDefault?: boolean
}

type PlayableRecordingCandidate = {
    assets?: readonly RecordingAsset[] | null
    defaultInWork?: boolean
}

export type InitialRecordingStrategy = 'first-playable' | 'default-first'

type NormalizeRecordingsOptions<
    TRecording extends NormalizableRecording,
    TOutput extends NormalizedRecordingBase,
> = {
    fallbackArtist?: string
    fallbackCover?: string
    transform: (recording: TRecording, base: NormalizedRecordingBase) => TOutput
}

export const resolveCover = (coverId?: number) => {
    if (coverId !== undefined) {
        return buildApiUrl(`/api/media/${coverId}`)
    }
    return ''
}

export const resolvePlayableAudio = (
    assets: readonly RecordingAsset[],
): PlayableAudioSource | undefined => {
    const audioAsset = assets.find((asset) => asset.mediaFile.mimeType.startsWith('audio/'))
    if (audioAsset) {
        return {
            src: buildApiUrl(`/api/media/${audioAsset.mediaFile.id}`),
            mediaFileId: audioAsset.mediaFile.id,
        }
    }
    return undefined
}

type NormalizeRecordingsBaseOptions = {
    fallbackArtist?: string
    fallbackCover?: string
}

export const resolveArtistName = (artists?: ReadonlyArray<RecordingArtist>) => {
    const names =
        artists
            ?.map((artist) => artist.displayName || artist.name)
            .filter((name): name is string => typeof name === 'string' && name.length > 0) ?? []
    if (names.length > 0) {
        return names.join(', ')
    }
    return 'Unknown Artist'
}

export function normalizeRecordings(
    recordings: readonly NormalizableRecording[],
    options?: NormalizeRecordingsBaseOptions,
): NormalizedRecordingBase[]

export function normalizeRecordings<
    TRecording extends NormalizableRecording,
    TOutput extends NormalizedRecordingBase,
>(
    recordings: readonly TRecording[],
    options: NormalizeRecordingsBaseOptions & NormalizeRecordingsOptions<TRecording, TOutput>,
): TOutput[]

export function normalizeRecordings<
    TRecording extends NormalizableRecording,
    TOutput extends NormalizedRecordingBase,
>(
    recordings: readonly TRecording[],
    options:
        | NormalizeRecordingsBaseOptions
        | (NormalizeRecordingsBaseOptions & NormalizeRecordingsOptions<TRecording, TOutput>) = {},
) {
    return recordings.map((recording) => {
        const playableAudio = resolvePlayableAudio(recording.assets ?? [])
        const base: NormalizedRecordingBase = {
            id: recording.id,
            title: recording.title || recording.comment || 'Untitled Track',
            artist:
                resolveArtistName(recording.artists) || options.fallbackArtist || 'Unknown Artist',
            cover: resolveCover(recording.cover?.id) || options.fallbackCover || '',
            audioSrc: playableAudio?.src,
            mediaFileId: playableAudio?.mediaFileId,
        }

        if (!('transform' in options)) {
            return base
        }

        return options.transform(recording, base)
    })
}

export const pickInitialRecordingId = (
    recordings: readonly InitialRecordingCandidate[],
    strategy: InitialRecordingStrategy,
) => {
    if (recordings.length === 0) {
        return null
    }

    if (strategy === 'default-first') {
        return recordings.find((recording) => recording.isDefault)?.id ?? recordings[0]?.id ?? null
    }

    return recordings.find((recording) => recording.audioSrc)?.id ?? recordings[0]?.id ?? null
}

export const pickPlayableRecordingEntry = <TRecording extends PlayableRecordingCandidate>(
    recordings: readonly TRecording[],
):
    | {
          recording: TRecording
          playableAudio: PlayableAudioSource
      }
    | undefined => {
    let firstPlayableEntry:
        | {
              recording: TRecording
              playableAudio: PlayableAudioSource
          }
        | undefined

    for (const recording of recordings) {
        const playableAudio = resolvePlayableAudio(recording.assets ?? [])
        if (!playableAudio) {
            continue
        }

        const entry = {
            recording,
            playableAudio,
        }
        if (recording.defaultInWork) {
            return entry
        }

        firstPlayableEntry ??= entry
    }

    return firstPlayableEntry
}

export const formatYear = (releaseDate?: string) => {
    if (!releaseDate) {
        return ''
    }
    const date = new Date(releaseDate)
    if (Number.isNaN(date.getTime())) {
        return ''
    }
    return date.getFullYear().toString()
}

export const formatDurationMs = (durationMs?: number) => {
    if (durationMs === undefined || !Number.isFinite(durationMs) || durationMs < 0) {
        return ''
    }

    const totalSeconds = Math.floor(durationMs / 1000)
    const hours = Math.floor(totalSeconds / 3600)
    const minutes = Math.floor((totalSeconds % 3600) / 60)
    const seconds = totalSeconds % 60

    if (hours > 0) {
        return `${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`
    }

    return `${minutes}:${seconds.toString().padStart(2, '0')}`
}
