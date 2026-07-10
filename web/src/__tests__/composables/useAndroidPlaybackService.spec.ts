import { beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, reactive } from 'vue'
import { useAndroidPlaybackService } from '@/composables/useAndroidPlaybackService'

const androidPlaybackMocks = vi.hoisted(() => ({
    isAndroid: true,
    getNotificationPermission: vi.fn<() => Promise<boolean>>(),
    requestNotificationPermission: vi.fn<() => Promise<NotificationPermission>>(),
    isServiceRunning: vi.fn<() => Promise<boolean>>(),
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
    isAndroidPlaybackServiceRunning: androidPlaybackMocks.isServiceRunning,
    listenForAndroidPlaybackStop: androidPlaybackMocks.listenForStop,
    startAndroidPlaybackService: androidPlaybackMocks.startService,
    stopAndroidPlaybackService: androidPlaybackMocks.stopService,
}))

type PlaybackProtectionGuard = (() => boolean | Promise<boolean>) | null

const createAudioStore = () => {
    const audioStore = reactive({
        currentTrack: null as { title: string } | null,
        isPlaying: false,
        playbackProtectionGuard: null as PlaybackProtectionGuard,
        pauseFromSystem: vi.fn(),
        pauseForPlaybackProtection: vi.fn(),
        recoverPlaybackAfterForeground: vi.fn(),
        setPlaybackProtectionGuard: vi.fn<(guard: PlaybackProtectionGuard) => void>(),
    })
    audioStore.setPlaybackProtectionGuard.mockImplementation((guard) => {
        audioStore.playbackProtectionGuard = guard
    })
    return audioStore
}

const flushController = async (
    controller: ReturnType<typeof useAndroidPlaybackService> | undefined,
) => {
    await nextTick()
    await controller?.whenSettled()
}

const setDocumentHidden = (hidden: boolean) => {
    Object.defineProperty(document, 'hidden', {
        configurable: true,
        value: hidden,
    })
}

describe('useAndroidPlaybackService', () => {
    beforeEach(() => {
        vi.restoreAllMocks()
        setDocumentHidden(false)
        androidPlaybackMocks.isAndroid = true
        androidPlaybackMocks.getNotificationPermission.mockReset().mockResolvedValue(true)
        androidPlaybackMocks.requestNotificationPermission.mockReset().mockResolvedValue('granted')
        androidPlaybackMocks.isServiceRunning.mockReset().mockResolvedValue(false)
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

    it('blocks a new background playback and requests recovery when visible again', async () => {
        const audioStore = createAudioStore()
        const scope = effectScope()
        const controller = scope.run(() => useAndroidPlaybackService(audioStore))
        await flushController(controller)
        androidPlaybackMocks.startService.mockClear()

        setDocumentHidden(true)
        await expect(audioStore.playbackProtectionGuard?.()).resolves.toBe(false)

        expect(androidPlaybackMocks.startService).not.toHaveBeenCalled()
        expect(audioStore.recoverPlaybackAfterForeground).not.toHaveBeenCalled()

        setDocumentHidden(false)
        document.dispatchEvent(new Event('visibilitychange'))

        expect(audioStore.recoverPlaybackAfterForeground).toHaveBeenCalledTimes(1)
        scope.stop()
    })

    it('resynchronizes a paused track when the app becomes visible', async () => {
        const audioStore = createAudioStore()
        audioStore.currentTrack = { title: 'Deferred Track' }
        const scope = effectScope()
        const controller = scope.run(() => useAndroidPlaybackService(audioStore))
        await flushController(controller)

        setDocumentHidden(true)
        document.dispatchEvent(new Event('visibilitychange'))
        setDocumentHidden(false)
        document.dispatchEvent(new Event('visibilitychange'))

        expect(audioStore.recoverPlaybackAfterForeground).toHaveBeenCalledTimes(1)
        scope.stop()
    })

    it('allows background continuation when the playback service is already running', async () => {
        androidPlaybackMocks.isServiceRunning.mockResolvedValue(true)
        setDocumentHidden(true)
        const audioStore = createAudioStore()
        audioStore.currentTrack = { title: 'Track Two' }
        audioStore.isPlaying = true
        const scope = effectScope()
        const controller = scope.run(() => useAndroidPlaybackService(audioStore))
        await flushController(controller)

        await expect(audioStore.playbackProtectionGuard?.()).resolves.toBe(true)

        expect(androidPlaybackMocks.startService).not.toHaveBeenCalled()
        setDocumentHidden(false)
        document.dispatchEvent(new Event('visibilitychange'))
        expect(audioStore.recoverPlaybackAfterForeground).not.toHaveBeenCalled()
        scope.stop()
    })

    it('rejects playback when foreground service startup fails', async () => {
        androidPlaybackMocks.startService.mockRejectedValue(new Error('background start denied'))
        const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined)
        const audioStore = createAudioStore()
        const scope = effectScope()
        const controller = scope.run(() => useAndroidPlaybackService(audioStore))
        await flushController(controller)

        await expect(audioStore.playbackProtectionGuard?.()).resolves.toBe(false)
        expect(consoleError).toHaveBeenCalledWith(
            'Failed to start Android playback service',
            expect.any(Error),
        )
        scope.stop()
    })

    it('pauses unprotected playback when fallback service startup fails', async () => {
        androidPlaybackMocks.startService.mockRejectedValue(new Error('start denied'))
        vi.spyOn(console, 'error').mockImplementation(() => undefined)
        const audioStore = createAudioStore()
        const scope = effectScope()
        const controller = scope.run(() => useAndroidPlaybackService(audioStore))
        await flushController(controller)

        audioStore.currentTrack = { title: 'Track Two' }
        audioStore.isPlaying = true
        await flushController(controller)

        expect(audioStore.pauseForPlaybackProtection).toHaveBeenCalledTimes(1)
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
        expect(audioStore.setPlaybackProtectionGuard).toHaveBeenLastCalledWith(null)
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
        audioStore.currentTrack = { title: 'Track Three' }
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
        audioStore.currentTrack = { title: 'Track Four' }
        audioStore.isPlaying = true

        const controller = useAndroidPlaybackService(audioStore)

        expect(controller).toBeNull()
        expect(audioStore.setPlaybackProtectionGuard).not.toHaveBeenCalled()
        expect(androidPlaybackMocks.startService).not.toHaveBeenCalled()
    })
})
