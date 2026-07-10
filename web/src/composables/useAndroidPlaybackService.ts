import { onScopeDispose, watch } from 'vue'
import {
    getAndroidNotificationPermission,
    isAndroidPlaybackServiceRunning,
    isAndroidRuntime,
    listenForAndroidPlaybackStop,
    requestAndroidNotificationPermission,
    startAndroidPlaybackService,
    stopAndroidPlaybackService,
} from '@/runtime/androidPlayback'

type AndroidPlaybackAudioStore = {
    currentTrack: {
        title: string
    } | null
    isPlaying: boolean
    pauseFromSystem: () => void
    pauseForPlaybackProtection: () => void
    recoverPlaybackAfterForeground: () => void
    setPlaybackProtectionGuard: (guard: (() => boolean | Promise<boolean>) | null) => void
}

export const useAndroidPlaybackService = (audioStore: AndroidPlaybackAudioStore) => {
    if (!isAndroidRuntime()) {
        return null
    }

    let desiredPlaybackActive = false
    let notificationPermissionRequested = false
    let playbackRecoveryPending = false
    let serviceKnownActive = false
    let disposed = false
    let stopNativeListener: (() => void) | null = null
    let operation = listenForAndroidPlaybackStop(() => {
        serviceKnownActive = false
        audioStore.pauseFromSystem()
    })
        .then((stopListener) => {
            if (disposed) {
                stopListener()
                return
            }
            stopNativeListener = stopListener
        })
        .catch((error: unknown) => {
            console.error('Failed to listen for Android playback stop', error)
        })

    const enqueueServiceOperation = <T>(task: () => Promise<T>) => {
        const result = operation.then(task)
        operation = result.then(
            () => undefined,
            () => undefined,
        )
        return result
    }

    const ensureNotificationPermission = async () => {
        if (notificationPermissionRequested || (await getAndroidNotificationPermission())) {
            return
        }

        notificationPermissionRequested = true
        const permission = await requestAndroidNotificationPermission()
        if (permission !== 'granted') {
            console.warn('Notification permission was not granted for Android playback')
        }
    }

    const ensurePlaybackService = async (requireDesiredPlayback = false) => {
        try {
            return await enqueueServiceOperation(async () => {
                if (disposed || (requireDesiredPlayback && !desiredPlaybackActive)) {
                    return false
                }

                if (serviceKnownActive || (await isAndroidPlaybackServiceRunning())) {
                    serviceKnownActive = true
                    return true
                }

                if (document.hidden) {
                    playbackRecoveryPending = true
                    return false
                }

                await ensureNotificationPermission()
                if (disposed || (requireDesiredPlayback && !desiredPlaybackActive)) {
                    return false
                }
                if (document.hidden) {
                    playbackRecoveryPending = true
                    return false
                }

                await startAndroidPlaybackService()
                serviceKnownActive = true
                return true
            })
        } catch (error: unknown) {
            playbackRecoveryPending = true
            console.error('Failed to start Android playback service', error)
            return false
        }
    }

    const stopPlaybackService = () => {
        void enqueueServiceOperation(async () => {
            if (desiredPlaybackActive) {
                return
            }

            await stopAndroidPlaybackService()
            serviceKnownActive = false
        }).catch((error: unknown) => {
            serviceKnownActive = false
            console.error('Failed to stop Android playback service', error)
        })
    }

    const handleVisibilityChange = () => {
        if (
            document.hidden ||
            (!playbackRecoveryPending && (audioStore.isPlaying || audioStore.currentTrack === null))
        ) {
            return
        }

        playbackRecoveryPending = false
        audioStore.recoverPlaybackAfterForeground()
    }

    audioStore.setPlaybackProtectionGuard(() => ensurePlaybackService())
    document.addEventListener('visibilitychange', handleVisibilityChange)

    const stopWatcher = watch(
        () => audioStore.isPlaying,
        (isPlaying) => {
            desiredPlaybackActive = isPlaying && audioStore.currentTrack !== null
            if (!desiredPlaybackActive) {
                stopPlaybackService()
                return
            }

            void ensurePlaybackService(true).then((playbackProtected) => {
                if (!playbackProtected && desiredPlaybackActive) {
                    audioStore.pauseForPlaybackProtection()
                }
            })
        },
        { immediate: true },
    )

    onScopeDispose(() => {
        disposed = true
        stopWatcher()
        document.removeEventListener('visibilitychange', handleVisibilityChange)
        audioStore.setPlaybackProtectionGuard(null)
        stopNativeListener?.()
        stopNativeListener = null
        desiredPlaybackActive = false
        stopPlaybackService()
    })

    return {
        whenSettled: () => operation,
    }
}
