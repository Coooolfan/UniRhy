import { computed, type Ref } from 'vue'
import { useAudioStore, type AudioTrack } from '@/stores/audio'

export type PlayableRecording = {
    id: number
    title: string
    artist: string
    cover: string
    audioSrc?: string
    mediaFileId?: number
}

export type UseRecordingPlaybackOptions<T extends PlayableRecording> = {
    recordings: Ref<T[]>
    currentRecordingId: Ref<number | null>
    fallbackCover: () => string
    trackExtra?: (recording: T) => Partial<AudioTrack>
}

export const useRecordingPlayback = <T extends PlayableRecording>(
    options: UseRecordingPlaybackOptions<T>,
) => {
    const audioStore = useAudioStore()

    const buildPlayableTracks = () => {
        return options.recordings.value.flatMap((recording) => {
            if (!recording.audioSrc || recording.mediaFileId === undefined) {
                return []
            }

            const trackExtra = options.trackExtra?.(recording)
            return [
                {
                    id: recording.id,
                    title: recording.title,
                    artist: recording.artist,
                    cover: recording.cover || options.fallbackCover(),
                    src: recording.audioSrc,
                    mediaFileId: recording.mediaFileId,
                    ...trackExtra,
                } satisfies AudioTrack,
            ]
        })
    }

    const hasPlayableRecording = computed(() =>
        options.recordings.value.some((recording) => !!recording.audioSrc),
    )

    const isCurrentRecordingPlaying = computed(() => {
        return (
            audioStore.isPlaying && audioStore.currentTrack?.id === options.currentRecordingId.value
        )
    })

    const playingId = computed(() => {
        if (!audioStore.isPlaying) {
            return null
        }
        return audioStore.currentTrack?.id ?? null
    })

    const handlePlay = (recording?: T) => {
        const targetRecordingId = recording?.id ?? options.currentRecordingId.value
        if (!targetRecordingId) {
            return
        }

        const targetRecording = options.recordings.value.find(
            (item) => item.id === targetRecordingId,
        )
        if (!targetRecording?.audioSrc || targetRecording.mediaFileId === undefined) {
            console.warn('No audio source for recording', targetRecordingId)
            return
        }

        options.currentRecordingId.value = targetRecording.id
        const playableTracks = buildPlayableTracks()
        const targetIndex = playableTracks.findIndex((track) => track.id === targetRecording.id)
        if (targetIndex < 0) {
            console.warn('No playable queue entry for recording', targetRecordingId)
            return
        }

        void audioStore.replaceQueueAndPlay(playableTracks, targetIndex)
    }

    const onRecordingClick = (recording: T) => {
        options.currentRecordingId.value = recording.id
    }

    const onRecordingDoubleClick = (recording: T) => {
        options.currentRecordingId.value = recording.id
        handlePlay(recording)
    }

    const onRecordingKeydown = (event: KeyboardEvent, recording: T) => {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault()
            onRecordingDoubleClick(recording)
        }
    }

    return {
        audioStore,
        hasPlayableRecording,
        isCurrentRecordingPlaying,
        playingId,
        handlePlay,
        onRecordingClick,
        onRecordingDoubleClick,
        onRecordingKeydown,
    }
}
