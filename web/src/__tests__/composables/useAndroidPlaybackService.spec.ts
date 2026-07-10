import { beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, reactive } from 'vue'
import { useAndroidPlaybackService } from '@/composables/useAndroidPlaybackService'

const androidPlaybackMocks = vi.hoisted(() => ({
    isAndroid: true,
    getNotificationPermission: vi.fn<() => Promise<boolean>>(),
    requestNotificationPermission: vi.fn<() => Promise<NotificationPermission>>(),
    listenForStop: vi.fn<(handler: () => void) => Promise<() => void>>(),
    nativeStopHandler: undefined as (() => void) | undefined,
    stopNativeListener: vi.fn(),
    startService: vi.fn<() => Promise<void>>(),
    stopService: vi.fn<() => Promise<void>>(),
}))

vi.mock('@/runtime/androidPlayback', () => ({
    isAndroidRuntime: () => androidPlaybackMocks.isAndroid,
    getAndroidNotificationPermission: androidPlaybackMocks.getNotificationPermission,
    requestAndroidNotificationPermission: androidPlaybackMocks.requestNotificationPermission,
    listenForAndroidPlaybackStop: androidPlaybackMocks.listenForStop,
    startAndroidPlaybackService: androidPlaybackMocks.startService,
    stopAndroidPlaybackService: androidPlaybackMocks.stopService,
}))

const createAudioStore = () =>
    reactive({
        currentTrack: null as { title: string } | null,
        isPlaying: false,
        pauseFromSystem: vi.fn(),
    })

const flushController = async (
    controller: ReturnType<typeof useAndroidPlaybackService> | undefined,
) => {
    await nextTick()
    await controller?.whenSettled()
}

describe('useAndroidPlaybackService', () => {
    beforeEach(() => {
        vi.restoreAllMocks()
        androidPlaybackMocks.isAndroid = true
        androidPlaybackMocks.getNotificationPermission.mockReset().mockResolvedValue(true)
        androidPlaybackMocks.requestNotificationPermission.mockReset().mockResolvedValue('granted')
        androidPlaybackMocks.nativeStopHandler = undefined
        androidPlaybackMocks.stopNativeListener.mockReset()
        androidPlaybackMocks.listenForStop.mockReset().mockImplementation((handler) => {
            androidPlaybackMocks.nativeStopHandler = handler
            return Promise.resolve(() => {
                androidPlaybackMocks.stopNativeListener()
            })
        })
        androidPlaybackMocks.startService.mockReset().mockResolvedValue()
        androidPlaybackMocks.stopService.mockReset().mockResolvedValue()
    })

    it('starts the foreground service while playing and stops it when paused', async () => {
        const audioStore = createAudioStore()
        const scope = effectScope()
        const controller = scope.run(() => useAndroidPlaybackService(audioStore))
        await flushController(controller)
        androidPlaybackMocks.stopService.mockClear()

        audioStore.currentTrack = { title: 'Track One' }
        audioStore.isPlaying = true
        await flushController(controller)

        expect(androidPlaybackMocks.getNotificationPermission).toHaveBeenCalledTimes(1)
        expect(androidPlaybackMocks.requestNotificationPermission).not.toHaveBeenCalled()
        expect(androidPlaybackMocks.startService).toHaveBeenCalledWith()

        audioStore.isPlaying = false
        await flushController(controller)

        expect(androidPlaybackMocks.stopService).toHaveBeenCalledTimes(1)
        scope.stop()
    })

    it('pauses playback for the native notification stop action and removes the listener', async () => {
        const audioStore = createAudioStore()
        const scope = effectScope()
        const controller = scope.run(() => useAndroidPlaybackService(audioStore))
        await flushController(controller)

        androidPlaybackMocks.nativeStopHandler?.()

        expect(audioStore.pauseFromSystem).toHaveBeenCalledTimes(1)
        scope.stop()
        expect(androidPlaybackMocks.stopNativeListener).toHaveBeenCalledTimes(1)
    })

    it('does not start the service if playback stops during the permission request', async () => {
        let resolvePermission: ((permission: NotificationPermission) => void) | undefined
        androidPlaybackMocks.getNotificationPermission.mockResolvedValue(false)
        androidPlaybackMocks.requestNotificationPermission.mockImplementation(
            () =>
                new Promise((resolve) => {
                    resolvePermission = resolve
                }),
        )

        const audioStore = createAudioStore()
        audioStore.currentTrack = { title: 'Track Two' }
        audioStore.isPlaying = true
        const scope = effectScope()
        const controller = scope.run(() => useAndroidPlaybackService(audioStore))
        await nextTick()

        audioStore.isPlaying = false
        await nextTick()
        resolvePermission?.('granted')
        await controller?.whenSettled()

        expect(androidPlaybackMocks.startService).not.toHaveBeenCalled()
        expect(androidPlaybackMocks.stopService).toHaveBeenCalled()
        scope.stop()
    })

    it('does nothing outside the Android runtime', () => {
        androidPlaybackMocks.isAndroid = false
        const audioStore = createAudioStore()
        audioStore.currentTrack = { title: 'Track Three' }
        audioStore.isPlaying = true

        const controller = useAndroidPlaybackService(audioStore)

        expect(controller).toBeNull()
        expect(androidPlaybackMocks.startService).not.toHaveBeenCalled()
    })
})
