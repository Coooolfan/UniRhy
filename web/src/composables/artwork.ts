import { api } from '@/ApiInstance'
import { resolveCover } from '@/composables/recordingMedia'

export type ArtworkEditValue = {
    file: File | null
    remove: boolean
}

export const emptyArtworkEdit = (): ArtworkEditValue => ({
    file: null,
    remove: false,
})

export const updateRecordingArtwork = async (
    recordingId: number,
    edit: ArtworkEditValue,
): Promise<string | undefined> => {
    if (edit.file) {
        const recording = await api.recordingController.updateRecordingCover({
            id: recordingId,
            body: { file: edit.file },
        })
        return resolveCover(recording.cover)
    }
    if (edit.remove) {
        const recording = await api.recordingController.removeRecordingCover({ id: recordingId })
        return resolveCover(recording.cover)
    }
    return undefined
}

export const updateArtistArtwork = async (
    artistId: number,
    edit: ArtworkEditValue,
): Promise<string | undefined> => {
    if (edit.file) {
        const artist = await api.artistController.updateArtistAvatar({
            id: artistId,
            body: { file: edit.file },
        })
        return resolveCover(artist.avatar)
    }
    if (edit.remove) {
        const artist = await api.artistController.removeArtistAvatar({ id: artistId })
        return resolveCover(artist.avatar)
    }
    return undefined
}
