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

export const resolveCover = (coverId?: number) => {
    if (coverId !== undefined) {
        return `/api/media/${coverId}`
    }
    return ''
}

export const resolvePlayableAudio = (
    assets: readonly RecordingAsset[],
): PlayableAudioSource | undefined => {
    const audioAsset = assets.find((asset) => asset.mediaFile.mimeType.startsWith('audio/'))
    if (audioAsset) {
        return {
            src: `/api/media/${audioAsset.mediaFile.id}`,
            mediaFileId: audioAsset.mediaFile.id,
        }
    }
    return undefined
}

export const resolveAudio = (assets: readonly RecordingAsset[]) => {
    return resolvePlayableAudio(assets)?.src
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
