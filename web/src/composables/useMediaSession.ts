import { onScopeDispose, watch } from 'vue'

type MediaSessionAudioStore = {
    currentTrack: {
        title: string
        artist: string
        cover?: string
    } | null
    isPlaying: boolean
    currentTime: number
    duration: number
    canNavigateQueue: boolean
    resume: () => void | Promise<void>
    pause: () => void
    stop: () => void
    playPrevious: () => void | Promise<void>
    playNext: () => void | Promise<void>
    seek: (time: number) => void
}

type MediaSessionActionName =
    | 'play'
    | 'pause'
    | 'stop'
    | 'previoustrack'
    | 'nexttrack'
    | 'seekto'
    | 'seekbackward'
    | 'seekforward'

type MediaMetadataConstructor = new (init?: MediaMetadataInit) => MediaMetadata

const MEDIA_SESSION_ACTIONS: MediaSessionActionName[] = [
    'play',
    'pause',
    'stop',
    'previoustrack',
    'nexttrack',
    'seekto',
    'seekbackward',
    'seekforward',
]

const DEFAULT_SEEK_OFFSET_SECONDS = 10

const getMediaSession = () => {
    if (typeof navigator === 'undefined') {
        return null
    }

    return navigator.mediaSession ?? null
}

const setActionHandlerSafely = (
    mediaSession: MediaSession,
    action: MediaSessionActionName,
    handler: MediaSessionActionHandler | null,
) => {
    try {
        mediaSession.setActionHandler(action, handler)
    } catch {
        // Ignore unsupported media session actions.
    }
}

const clearActionHandlers = (mediaSession: MediaSession) => {
    MEDIA_SESSION_ACTIONS.forEach((action) => {
        setActionHandlerSafely(mediaSession, action, null)
    })
}

const updatePlaybackState = (mediaSession: MediaSession, isPlaying: boolean, hasTrack: boolean) => {
    if (!hasTrack) {
        mediaSession.playbackState = 'none'
        return
    }

    mediaSession.playbackState = isPlaying ? 'playing' : 'paused'
}

const updateMetadata = (
    mediaSession: MediaSession,
    track: MediaSessionAudioStore['currentTrack'],
) => {
    const MediaMetadataCtor = globalThis.MediaMetadata as MediaMetadataConstructor | undefined
    if (!track || !MediaMetadataCtor) {
        mediaSession.metadata = null
        return
    }

    mediaSession.metadata = new MediaMetadataCtor({
        title: track.title,
        artist: track.artist,
        artwork: track.cover ? [{ src: track.cover }] : [],
    })
}

const updatePositionState = (mediaSession: MediaSession, currentTime: number, duration: number) => {
    if (typeof mediaSession.setPositionState !== 'function') {
        return
    }

    if (!Number.isFinite(duration) || duration <= 0) {
        return
    }

    const normalizedPosition = Math.min(Math.max(0, currentTime), duration)
    try {
        mediaSession.setPositionState({
            duration,
            playbackRate: 1,
            position: normalizedPosition,
        })
    } catch {
        // Ignore invalid or unsupported position state updates.
    }
}

const resolveSeekOffset = (details: MediaSessionActionDetails) => {
    return Number.isFinite(details.seekOffset)
        ? Math.max(0, details.seekOffset!)
        : DEFAULT_SEEK_OFFSET_SECONDS
}

export const useMediaSession = (audioStore: MediaSessionAudioStore) => {
    const mediaSession = getMediaSession()
    if (!mediaSession) {
        return
    }

    const applyHandlers = () => {
        if (!audioStore.currentTrack) {
            clearActionHandlers(mediaSession)
            return
        }

        setActionHandlerSafely(mediaSession, 'play', () => {
            void audioStore.resume()
        })
        setActionHandlerSafely(mediaSession, 'pause', () => {
            audioStore.pause()
        })
        setActionHandlerSafely(mediaSession, 'stop', () => {
            audioStore.stop()
        })
        setActionHandlerSafely(mediaSession, 'seekto', (details) => {
            if (Number.isFinite(details.seekTime)) {
                audioStore.seek(details.seekTime!)
            }
        })
        setActionHandlerSafely(mediaSession, 'seekbackward', (details) => {
            audioStore.seek(audioStore.currentTime - resolveSeekOffset(details))
        })
        setActionHandlerSafely(mediaSession, 'seekforward', (details) => {
            audioStore.seek(audioStore.currentTime + resolveSeekOffset(details))
        })
        setActionHandlerSafely(
            mediaSession,
            'previoustrack',
            audioStore.canNavigateQueue
                ? () => {
                      void audioStore.playPrevious()
                  }
                : null,
        )
        setActionHandlerSafely(
            mediaSession,
            'nexttrack',
            audioStore.canNavigateQueue
                ? () => {
                      void audioStore.playNext()
                  }
                : null,
        )
    }

    const resetMediaSession = () => {
        mediaSession.metadata = null
        updatePlaybackState(mediaSession, false, false)
        clearActionHandlers(mediaSession)
    }

    watch(
        () => [audioStore.currentTrack, audioStore.isPlaying] as const,
        ([track, isPlaying]) => {
            updateMetadata(mediaSession, track)
            updatePlaybackState(mediaSession, isPlaying, track !== null)

            if (!track) {
                clearActionHandlers(mediaSession)
                return
            }

            applyHandlers()
        },
        { immediate: true },
    )

    watch(
        () => audioStore.canNavigateQueue,
        () => {
            if (!audioStore.currentTrack) {
                return
            }
            applyHandlers()
        },
        { immediate: true },
    )

    watch(
        () => [audioStore.currentTime, audioStore.duration] as const,
        ([currentTime, duration]) => {
            if (!audioStore.currentTrack) {
                return
            }
            updatePositionState(mediaSession, currentTime, duration)
        },
        { immediate: true },
    )

    onScopeDispose(() => {
        resetMediaSession()
    })
}
