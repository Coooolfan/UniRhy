import { watch, type Ref } from 'vue'
import type { CurrentQueueDto } from '@/services/playbackSyncProtocol'
import type { PlaybackSyncClientPhase } from '@/services/playbackSyncClient'
import type { AudioTrack } from '@/stores/audioShared'
import { getAuthToken } from '@/ApiInstance'
import { getPlatformRuntime } from '@/runtime/platform'
import {
    type NativePlaybackEvent,
    type NativePlaybackState,
    type NativeQueueItem,
    configureNativePlayback,
    connectNativeSync,
    disconnectNativeSync,
    getNativePlaybackState,
    listenForNativePlaybackEvents,
    nativeLocalPause,
    nativeLocalPlay,
    nativeLocalSeek,
    requestNativePause,
    requestNativePlay,
    requestNativeSeek,
    requestNativeSyncRecovery,
    setNativeLocalQueue,
    setNativeVolume,
} from '@/runtime/nativePlaybackBridge'

const DEVICE_ID_STORAGE_KEY = 'unirhy.playback-sync.device-id'
const NATIVE_CLIENT_VERSION = 'android-native@playback-sync'
const POSITION_INTERPOLATION_INTERVAL_MS = 250
/** 原生进程重启后事件 seq 会归零，低于该阈值的"回退"视为重启而非乱序 */
const SEQ_RESET_TOLERANCE = 5

type UseAudioNativeSessionOptions = {
    currentTrack: Ref<AudioTrack | null>
    currentQueue: Ref<CurrentQueueDto>
    isPlaying: Ref<boolean>
    currentTime: Ref<number>
    duration: Ref<number>
    isLoading: Ref<boolean>
    error: Ref<string | null>
    clientPhase: Ref<PlaybackSyncClientPhase>
    clockOffsetMs: Ref<number>
    roundTripEstimateMs: Ref<number>
    volume: Ref<number>
    isIndependentPlaybackMode: () => boolean
    applyQueueSnapshot: (queue: CurrentQueueDto) => void
    updateLocalQueueCurrentIndex: (currentIndex: number) => void
}

const getOrCreateNativeDeviceId = () => {
    const persisted = window.localStorage.getItem(DEVICE_ID_STORAGE_KEY)
    if (persisted?.startsWith('tauri-android-')) {
        return persisted
    }
    const suffix =
        typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
            ? crypto.randomUUID().slice(0, 8)
            : Math.random().toString(36).slice(2, 10)
    const deviceId = `tauri-android-${suffix}`
    window.localStorage.setItem(DEVICE_ID_STORAGE_KEY, deviceId)
    return deviceId
}

/**
 * Android 原生播放后端：对位 audioEngine + playbackSyncSession 之和。
 * 原生层（Media3 + Kotlin 协议客户端）为播放权威，本层将其事件写入 store refs、
 * 将用户操作转为插件命令；WebView 内不创建任何 AudioContext。
 */
export const useAudioNativeSession = (options: UseAudioNativeSessionOptions) => {
    let started = false
    let unlistenEvents: (() => void) | null = null
    let lastEventSeq = 0
    let positionBase: { positionSeconds: number; atMs: number } | null = null
    let interpolationTimer: number | null = null

    const currentMode = () => (options.isIndependentPlaybackMode() ? 'independent' : 'sync')

    const stopInterpolation = () => {
        if (interpolationTimer !== null) {
            window.clearInterval(interpolationTimer)
            interpolationTimer = null
        }
    }

    const startInterpolation = () => {
        if (interpolationTimer !== null) {
            return
        }
        interpolationTimer = window.setInterval(() => {
            const base = positionBase
            if (!base || !options.isPlaying.value) {
                return
            }
            options.currentTime.value =
                base.positionSeconds + (performance.now() - base.atMs) / 1_000
        }, POSITION_INTERPOLATION_INTERVAL_MS)
    }

    const applyNativeQueue = (queue: CurrentQueueDto) => {
        if (options.isIndependentPlaybackMode()) {
            // 独立模式下队列由 TS 本地维护，原生不下发权威队列
            return
        }
        options.applyQueueSnapshot(queue)
    }

    const applyNativeEvent = (event: NativePlaybackEvent) => {
        if (event.seq <= lastEventSeq && event.seq > SEQ_RESET_TOLERANCE) {
            return
        }
        lastEventSeq = event.seq

        switch (event.type) {
            case 'sync-state': {
                options.clientPhase.value = event.syncPhase
                options.clockOffsetMs.value = event.clockOffsetMs
                options.roundTripEstimateMs.value = event.roundTripEstimateMs
                break
            }
            case 'queue-changed': {
                applyNativeQueue(event.queue)
                break
            }
            case 'state-changed': {
                if (event.isPlaying !== undefined) {
                    options.isPlaying.value = event.isPlaying
                    if (event.isPlaying) {
                        startInterpolation()
                    } else {
                        stopInterpolation()
                    }
                }
                if (event.positionSeconds !== undefined) {
                    positionBase = {
                        positionSeconds: event.positionSeconds,
                        atMs: performance.now(),
                    }
                    options.currentTime.value = event.positionSeconds
                }
                if (event.durationSeconds !== undefined && event.durationSeconds > 0) {
                    options.duration.value = event.durationSeconds
                }
                if (event.isLoading !== undefined) {
                    options.isLoading.value = event.isLoading
                }
                if (event.error !== undefined) {
                    options.error.value = event.error ?? null
                }
                if (
                    event.currentIndex !== undefined &&
                    event.currentIndex !== null &&
                    options.isIndependentPlaybackMode() &&
                    event.currentIndex !== options.currentQueue.value.currentIndex
                ) {
                    // 独立模式自然结束续播由原生驱动，TS 队列指针跟随
                    options.updateLocalQueueCurrentIndex(event.currentIndex)
                }
                break
            }
            case 'position': {
                positionBase = { positionSeconds: event.positionSeconds, atMs: performance.now() }
                options.currentTime.value = event.positionSeconds
                options.isPlaying.value = event.isPlaying
                break
            }
            case 'auth-required': {
                options.error.value = '登录状态已失效，请重新登录'
                break
            }
            default:
                break
        }
    }

    const applyNativeState = (state: NativePlaybackState) => {
        options.isPlaying.value = state.isPlaying
        options.duration.value = state.durationSeconds
        options.isLoading.value = state.isLoading
        options.clientPhase.value = state.syncPhase
        options.clockOffsetMs.value = state.clockOffsetMs
        options.roundTripEstimateMs.value = state.roundTripEstimateMs
        positionBase = { positionSeconds: state.positionSeconds, atMs: performance.now() }
        options.currentTime.value = state.positionSeconds
        if (state.isPlaying) {
            startInterpolation()
        } else {
            stopInterpolation()
        }
        if (state.queue) {
            applyNativeQueue(state.queue)
        }
    }

    const configure = async () => {
        await configureNativePlayback({
            apiBaseUrl: getPlatformRuntime().apiBaseUrl,
            token: getAuthToken(),
            deviceId: getOrCreateNativeDeviceId(),
            clientVersion: NATIVE_CLIENT_VERSION,
            mode: currentMode(),
        })
    }

    /** 启动原生会话：配置、挂事件监听，同步模式下建立 WS 连接。 */
    const start = async () => {
        await configure()
        if (!started) {
            started = true
            unlistenEvents = await listenForNativePlaybackEvents(applyNativeEvent)
            await setNativeVolume(options.volume.value)
        }
        if (!options.isIndependentPlaybackMode()) {
            await connectNativeSync()
        }
    }

    const stopSync = async () => {
        stopInterpolation()
        await disconnectNativeSync()
    }

    const dispose = () => {
        stopInterpolation()
        unlistenEvents?.()
        unlistenEvents = null
        started = false
    }

    /** 回前台/初始化：从原生拉全量状态对齐（原生为权威）。 */
    const refreshFromNative = async () => {
        try {
            applyNativeState(await getNativePlaybackState())
        } catch {
            // 插件不可用（如桌面调试）时静默忽略
        }
    }

    /** 独立模式：把 TS 本地队列全量下发原生（供自然结束续播与系统控件切歌）。 */
    const pushLocalQueueToNative = async () => {
        const queue = options.currentQueue.value
        const items = queue.items.flatMap<NativeQueueItem>((item) =>
            item.mediaFileId === undefined || item.mediaFileId === null
                ? []
                : [
                      {
                          recordingId: item.recordingId,
                          mediaFileId: item.mediaFileId,
                          title: item.title,
                          artistLabel: item.artistLabel,
                          coverUrl: item.coverUrl ?? null,
                          durationMs: item.durationMs,
                      },
                  ],
        )
        await setNativeLocalQueue(items, Math.max(0, queue.currentIndex))
    }

    watch(
        () => options.volume.value,
        (nextVolume) => {
            void setNativeVolume(nextVolume)
        },
    )

    // 独立模式下本地队列任何变化全量下发原生
    watch(
        () => options.currentQueue.value,
        () => {
            if (!options.isIndependentPlaybackMode()) {
                return
            }
            void pushLocalQueueToNative()
        },
    )

    return {
        start,
        stopSync,
        dispose,
        configure,
        refreshFromNative,
        applyNativeEvent,
        pushLocalQueueToNative,
        // 命令透传（audio.ts 分支调用）
        requestPlay: requestNativePlay,
        requestPause: requestNativePause,
        requestSeek: requestNativeSeek,
        requestSyncRecovery: requestNativeSyncRecovery,
        localPlay: nativeLocalPlay,
        localPause: nativeLocalPause,
        localSeek: nativeLocalSeek,
    }
}

export type AudioNativeSession = ReturnType<typeof useAudioNativeSession>
