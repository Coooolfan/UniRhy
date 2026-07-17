import type { CurrentQueueDto } from '@/services/playbackSyncProtocol'

const PLUGIN_NAME = 'unirhy-playback'

export type NativePlaybackMode = 'sync' | 'independent'

export type NativeSyncPhase =
    | 'stopped'
    | 'connecting'
    | 'calibrating'
    | 'ready'
    | 'reconnecting'
    | 'error'

export type NativePlaybackConfig = {
    apiBaseUrl: string
    token: string | null
    deviceId: string
    clientVersion: string
    mode: NativePlaybackMode
    preferredAssetFormat: string
}

export type NativeNtpMeasurement = {
    offsetMs: number
    rttMs: number
    recordedAtMs: number
}

/** 原生同步链路的诊断快照（附于 sync-state 事件与 getPlaybackState）。 */
export type NativeSyncDiagnostics = {
    socketState: 'idle' | 'connecting' | 'open' | 'closed'
    reconnectAttempt: number
    snapshotReceived: boolean
    lastNtpResponseAtMs: number | null
    measurements: NativeNtpMeasurement[]
}

export type NativePlaybackState = {
    configured: boolean
    mode: NativePlaybackMode
    isPlaying: boolean
    currentIndex: number | null
    positionSeconds: number
    durationSeconds: number
    isLoading: boolean
    error?: string | null
    syncPhase: NativeSyncPhase
    clockOffsetMs: number
    roundTripEstimateMs: number
    syncDiagnostics?: NativeSyncDiagnostics | null
    queue?: CurrentQueueDto | null
    queueVersion: number | null
}

export type NativeQueueItem = {
    recordingId: number
    mediaFileId: number
    title: string
    artistLabel: string
    coverUrl: string | null
    durationMs: number
}

export type NativePlaybackEvent =
    | ({ type: 'state-changed'; seq: number } & Partial<NativePlaybackState>)
    | { type: 'position'; seq: number; positionSeconds: number; isPlaying: boolean }
    | {
          type: 'sync-state'
          seq: number
          syncPhase: NativeSyncPhase
          clockOffsetMs: number
          roundTripEstimateMs: number
          diagnostics?: NativeSyncDiagnostics | null
      }
    | { type: 'queue-changed'; seq: number; queue: CurrentQueueDto }
    | {
          type: 'protocol-event'
          seq: number
          direction: 'in' | 'out'
          messageType: string
          payload: unknown
          atMs: number
      }
    | {
          type: 'local-execution'
          seq: number
          atMs: number
          action: 'PLAY' | 'PAUSE' | 'SEEK'
          commandId: string
          version: number
          estimatedServerNowMs: number
          executeAtServerMs: number
          waitMs: number
          lateSeconds: number
          scheduledOffset: number
          currentIndex: number | null
          mediaFileId: number | null
      }
    | { type: 'auth-required'; seq: number }

const rawInvokePlugin = async <T>(command: string, args?: Record<string, unknown>): Promise<T> => {
    const { invoke } = await import('@tauri-apps/api/core')
    return invoke<T>(`plugin:${PLUGIN_NAME}|${command}`, args)
}

/** 把前端消息写入原生 logcat（tag: UnirhyJs）。release 包 WebView console 不可见，仅此通道可用。 */
export const reportNativeJsLog = (message: string) => {
    rawInvokePlugin<unknown>('js_log', { request: { message } }).catch(() => {
        // 日志通道本身失败时无处上报，静默
    })
}

const invokePlugin = async <T>(command: string, args?: Record<string, unknown>): Promise<T> => {
    try {
        return await rawInvokePlugin<T>(command, args)
    } catch (invokeError: unknown) {
        reportNativeJsLog(`invoke ${command} failed: ${String(invokeError)}`)
        throw invokeError
    }
}

const invokeCommand = async (command: string, args?: Record<string, unknown>): Promise<void> => {
    await invokePlugin<unknown>(command, args)
}

/** 挂全局错误上报：未捕获异常与未处理 Promise 拒绝写入原生日志。 */
export const installNativeErrorReporting = () => {
    window.addEventListener('error', (event) => {
        reportNativeJsLog(`uncaught: ${event.message} @${event.filename}:${event.lineno}`)
    })
    window.addEventListener('unhandledrejection', (event) => {
        const reason: unknown = event.reason
        let detail: string
        if (reason instanceof Error) {
            detail = reason.stack ?? reason.message
        } else if (typeof reason === 'string') {
            detail = reason
        } else {
            detail = JSON.stringify(reason)
        }
        reportNativeJsLog(`unhandledrejection: ${detail}`)
    })
}

export const configureNativePlayback = (config: NativePlaybackConfig) =>
    invokeCommand('configure', { request: config })

export const updateNativePlaybackAuth = (token: string | null) =>
    invokeCommand('update_auth', { request: { token } })

export const connectNativeSync = () => invokeCommand('connect_sync')

export const disconnectNativeSync = () => invokeCommand('disconnect_sync')

export const getNativePlaybackState = () => invokePlugin<NativePlaybackState>('get_playback_state')

export const setNativeVolume = (volume: number) =>
    invokeCommand('set_volume', { request: { volume } })

export const requestNativePlay = (options?: {
    positionSeconds?: number
    currentIndex?: number
    version?: number
}) =>
    invokeCommand('request_play', {
        request: {
            positionSeconds: options?.positionSeconds ?? null,
            currentIndex: options?.currentIndex ?? null,
            version: options?.version ?? null,
        },
    })

export const requestNativePause = (positionSeconds?: number) =>
    invokeCommand('request_pause', { request: { positionSeconds: positionSeconds ?? null } })

export const requestNativeSeek = (positionSeconds: number) =>
    invokeCommand('request_seek', { request: { positionSeconds } })

export const requestNativeSyncRecovery = () => invokeCommand('request_sync_recovery')

export const setNativeLocalQueue = (items: NativeQueueItem[], currentIndex: number) =>
    invokeCommand('local_set_queue', { request: { items, currentIndex } })

export const nativeLocalPlay = (currentIndex: number, positionSeconds: number) =>
    invokeCommand('local_play', { request: { currentIndex, positionSeconds } })

export const nativeLocalPause = () => invokeCommand('local_pause')

export const nativeLocalSeek = (positionSeconds: number) =>
    invokeCommand('local_seek', { request: { positionSeconds } })

export const listenForNativePlaybackEvents = async (
    onEvent: (event: NativePlaybackEvent) => void,
) => {
    const { addPluginListener } = await import('@tauri-apps/api/core')
    let reportedEventCount = 0
    const listener = await addPluginListener<NativePlaybackEvent>(
        PLUGIN_NAME,
        'playback-event',
        (event) => {
            if (reportedEventCount < 10) {
                reportedEventCount += 1
                reportNativeJsLog(`event #${reportedEventCount}: ${event.type}`)
            }
            onEvent(event)
        },
    )
    reportNativeJsLog('playback-event listener attached')

    return () => {
        void listener.unregister()
    }
}
