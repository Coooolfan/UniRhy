import { onScopeDispose, watch } from 'vue'
import {
    getAndroidNotificationPermission,
    isAndroidRuntime,
    requestAndroidNotificationPermission,
    startAndroidPlaybackService,
    stopAndroidPlaybackService,
} from '@/runtime/androidPlayback'

type AndroidPlaybackAudioStore = {
    currentTrack: {
        title: string
    } | null
    isPlaying: boolean
}

export const useAndroidPlaybackService = (audioStore: AndroidPlaybackAudioStore) => {
    if (!isAndroidRuntime()) {
        return null
    }

    let desiredPlaybackActive = false
    let notificationPermissionRequested = false
    let operation = Promise.resolve()

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
        stopWatcher()
        desiredPlaybackActive = false
        reconcileService()
    })

    return {
        whenSettled: () => operation,
    }
}
