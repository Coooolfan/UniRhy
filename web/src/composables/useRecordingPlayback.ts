import { computed, type Ref } from 'vue'
import { useAudioStore, type AudioTrack } from '@/stores/audio'

export type PlayableRecording = {
    id: number
    title: string
    artist: string
    cover: string
    audioSrc?: string
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
        if (!targetRecording?.audioSrc) {
            console.warn('No audio source for recording', targetRecordingId)
            return
        }

        options.currentRecordingId.value = targetRecording.id
        const trackExtra = options.trackExtra?.(targetRecording)

        audioStore.play({
            id: targetRecording.id,
            title: targetRecording.title,
            artist: targetRecording.artist,
            cover: targetRecording.cover || options.fallbackCover(),
            src: targetRecording.audioSrc,
            ...trackExtra,
        })
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
