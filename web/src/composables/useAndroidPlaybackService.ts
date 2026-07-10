import { onScopeDispose, watch } from 'vue'
import {
    getAndroidNotificationPermission,
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
}

export const useAndroidPlaybackService = (audioStore: AndroidPlaybackAudioStore) => {
    if (!isAndroidRuntime()) {
        return null
    }

    let desiredPlaybackActive = false
    let notificationPermissionRequested = false
    let disposed = false
    let stopNativeListener: (() => void) | null = null
    let operation = listenForAndroidPlaybackStop(() => {
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

    const reconcileService = () => {
        operation = operation
            .then(async () => {
                if (!desiredPlaybackActive) {
                    await stopAndroidPlaybackService()
                    return
                }

                await ensureNotificationPermission()
                if (!desiredPlaybackActive) {
                    await stopAndroidPlaybackService()
                    return
                }

                await startAndroidPlaybackService()
            })
            .catch((error: unknown) => {
                console.error('Failed to reconcile Android playback service', error)
            })
    }

    const stopWatcher = watch(
        () => [audioStore.isPlaying, audioStore.currentTrack] as const,
        ([isPlaying, track]) => {
            desiredPlaybackActive = isPlaying && track !== null
            reconcileService()
        },
        { immediate: true },
    )

    onScopeDispose(() => {
        disposed = true
        stopWatcher()
        stopNativeListener?.()
        stopNativeListener = null
        desiredPlaybackActive = false
        reconcileService()
    })

    return {
        whenSettled: () => operation,
    }
}
