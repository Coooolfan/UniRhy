import { computed, type Ref } from 'vue'
import { useAudioStore, type AudioTrack } from '@/stores/audio'
import {
    hasPlayableRecordingCandidate,
    hasPlayableRecordingCandidates,
    pickInitialRecordingIdFromCandidates,
    resolvePlaybackTrackFromCandidate,
    resolvePlaybackTracksFromCandidates,
    type RecordingPlaybackCandidate,
} from '@/services/recordingPlaybackResolver'
import { useUserStore } from '@/stores/user'

export type PlayableRecording = RecordingPlaybackCandidate

export type UseRecordingPlaybackOptions<T extends PlayableRecording> = {
    recordings: Ref<T[]>
    currentRecordingId: Ref<number | null>
    fallbackCover: () => string
    trackExtra?: (recording: T) => Partial<AudioTrack>
    initialStrategy?: 'first-playable' | 'default-first'
}

const hasPreferredPlayableAsset = (recording: PlayableRecording, preferredAssetFormat?: string) => {
    const normalizedPreferredAssetFormat = preferredAssetFormat?.trim().toLowerCase()
    if (!normalizedPreferredAssetFormat) {
        return false
    }

    return recording.assets.some((asset) => {
        const mimeType = asset.mediaFile.mimeType?.trim().toLowerCase()
        return mimeType === normalizedPreferredAssetFormat && !!asset.mediaFile.url
    })
}

export const useRecordingPlayback = <T extends PlayableRecording>(
    options: UseRecordingPlaybackOptions<T>,
) => {
    const audioStore = useAudioStore()
    const userStore = useUserStore()

    const pickPreferredRecordingId = (preferredAssetFormat?: string) => {
        if (!preferredAssetFormat) {
            return null
        }

        const initialStrategy = options.initialStrategy ?? 'first-playable'
        if (initialStrategy === 'default-first') {
            return (
                options.recordings.value.find(
                    (recording) =>
                        recording.defaultInWork &&
                        hasPreferredPlayableAsset(recording, preferredAssetFormat),
                )?.id ??
                options.recordings.value.find((recording) =>
                    hasPreferredPlayableAsset(recording, preferredAssetFormat),
                )?.id ??
                null
            )
        }

        return (
            options.recordings.value.find((recording) =>
                hasPreferredPlayableAsset(recording, preferredAssetFormat),
            )?.id ?? null
        )
    }

    const buildPlayableTracks = async (preferredAssetFormat?: string) => {
        const resolvedTracks = await resolvePlaybackTracksFromCandidates(
            options.recordings.value,
            preferredAssetFormat,
        )
        return resolvedTracks.map((track) => {
            const recording = options.recordings.value.find((item) => item.id === track.id)
            const trackExtra = recording ? options.trackExtra?.(recording) : undefined
            const nextTrack: AudioTrack = {
                id: track.id,
                title: track.title,
                artist: track.artist,
                cover: track.cover || options.fallbackCover(),
                src: track.src,
                mediaFileId: track.mediaFileId,
            }
            if (track.workId !== undefined) {
                nextTrack.workId = track.workId
            }

            if (trackExtra) {
                Object.assign(nextTrack, trackExtra)
            }

            return nextTrack
        })
    }

    const hasPlayableRecording = computed(() =>
        hasPlayableRecordingCandidates(options.recordings.value, userStore.preferredAssetFormat),
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

    const handlePlay = async (recording?: T) => {
        const preferredAssetFormat = await userStore.getPreferredAssetFormat()
        let targetRecordingId = recording?.id ?? options.currentRecordingId.value
        const preferredRecordingId = recording
            ? null
            : pickPreferredRecordingId(preferredAssetFormat)
        if (preferredRecordingId !== null) {
            const currentCandidate =
                targetRecordingId === null
                    ? undefined
                    : options.recordings.value.find((item) => item.id === targetRecordingId)
            if (
                targetRecordingId === null ||
                !currentCandidate ||
                !hasPreferredPlayableAsset(currentCandidate, preferredAssetFormat)
            ) {
                targetRecordingId = preferredRecordingId
            }
        }
        if (targetRecordingId !== null) {
            const currentCandidate = options.recordings.value.find(
                (item) => item.id === targetRecordingId,
            )
            if (
                !recording &&
                currentCandidate &&
                !hasPlayableRecordingCandidate(currentCandidate, preferredAssetFormat)
            ) {
                targetRecordingId = null
            }
        }
        targetRecordingId ??=
            pickInitialRecordingIdFromCandidates(
                options.recordings.value,
                options.initialStrategy ?? 'first-playable',
                preferredAssetFormat,
            ) ?? null
        if (!targetRecordingId) {
            return
        }

        let targetRecording = options.recordings.value.find((item) => item.id === targetRecordingId)
        if (!targetRecording) {
            console.warn('No audio source for recording', targetRecordingId)
            return
        }

        let resolvedTargetTrack = await resolvePlaybackTrackFromCandidate(
            targetRecording,
            preferredAssetFormat,
        )
        if (!resolvedTargetTrack && !recording) {
            const fallbackRecordingId = pickInitialRecordingIdFromCandidates(
                options.recordings.value,
                'first-playable',
                preferredAssetFormat,
            )
            if (fallbackRecordingId && fallbackRecordingId !== targetRecording.id) {
                const fallbackRecording = options.recordings.value.find(
                    (item) => item.id === fallbackRecordingId,
                )
                if (fallbackRecording) {
                    targetRecording = fallbackRecording
                    resolvedTargetTrack = await resolvePlaybackTrackFromCandidate(
                        targetRecording,
                        preferredAssetFormat,
                    )
                }
            }
        }
        if (!resolvedTargetTrack) {
            console.warn('No audio source for recording', targetRecordingId)
            return
        }

        options.currentRecordingId.value = targetRecording.id
        const playableTracks = await buildPlayableTracks(preferredAssetFormat)
        const targetIndex = playableTracks.findIndex((track) => track.id === targetRecording.id)
        if (targetIndex < 0) {
            console.warn('No playable queue entry for recording', targetRecordingId)
            return
        }

        await audioStore.replaceQueueAndPlay(playableTracks, targetIndex)
    }

    const onRecordingClick = (recording: T) => {
        options.currentRecordingId.value = recording.id
    }

    const onRecordingDoubleClick = (recording: T) => {
        options.currentRecordingId.value = recording.id
        void handlePlay(recording)
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
