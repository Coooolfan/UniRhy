import type {
    AudioSourceLoadedMessage,
    ClientPlaybackSyncMessage,
    NtpResponsePayload,
    PlaybackControlPayload,
    ServerPlaybackSyncMessage,
} from '@/services/playbackSyncProtocol'
import { getAuthToken } from '@/ApiInstance'
import { buildWebSocketUrl, getPlatformRuntime } from '@/runtime/platform'
import { nowClientMs } from '@/utils/time'
import { average } from '@/utils/math'

const DEVICE_ID_STORAGE_KEY = 'unirhy.playback-sync.device-id'
const INITIAL_SAMPLE_COUNT = 20
const INITIAL_SAMPLE_INTERVAL_MS = 30
const INITIAL_SAMPLE_SETTLE_MS = 60
const STEADY_STATE_INTERVAL_MS = 2_500
const RECONNECT_DELAYS_MS = [1_000, 2_000, 5_000] as const
const MAX_MEASUREMENT_COUNT = 20
const MAX_PROTOCOL_EVENT_COUNT = 30
const CLIENT_VERSION = 'web@playback-sync'
const SERVER_MESSAGE_TYPES = [
    'NTP_RESPONSE',
    'SNAPSHOT',
    'ROOM_EVENT_LOAD_AUDIO_SOURCE',
    'ROOM_EVENT_QUEUE_CHANGE',
    'SCHEDULED_ACTION',
    'ROOM_EVENT_DEVICE_CHANGE',
    'ERROR',
] as const

export type PlaybackSyncClientPhase =
    | 'stopped'
    | 'connecting'
    | 'calibrating'
    | 'ready'
    | 'reconnecting'
    | 'error'

export type PlaybackSyncSocketState = 'idle' | 'connecting' | 'open' | 'closing' | 'closed'

export type PlaybackSyncNtpMeasurement = {
    offsetMs: number
    rttMs: number
    recordedAtMs: number
}

export type PlaybackSyncDiagnosticsEvent = {
    direction: 'in' | 'out'
    type: string
    rawType: string | null
    atMs: number
    payload: unknown
}

export type PlaybackSyncClientDiagnosticsError = {
    atMs: number
    code: string | null
    message: string
    rawType: string | null
    rawMessage: string | null
}

export type PlaybackSyncClientDiagnosticsSnapshot = {
    deviceId: string
    phase: PlaybackSyncClientPhase
    clockOffsetMs: number
    roundTripEstimateMs: number
    socketState: PlaybackSyncSocketState
    reconnectAttempt: number
    snapshotReceived: boolean
    initialCalibration: {
        sampledCount: number
        requiredSampleCount: number
        settling: boolean
    }
    measurements: readonly PlaybackSyncNtpMeasurement[]
    lastNtpRequestAtMs: number | null
    lastNtpResponseAtMs: number | null
    lastInboundEvent: PlaybackSyncDiagnosticsEvent | null
    lastOutboundEvent: PlaybackSyncDiagnosticsEvent | null
    protocolEvents: readonly PlaybackSyncDiagnosticsEvent[]
    lastError: PlaybackSyncClientDiagnosticsError | null
}

export type PlaybackSyncClientState = {
    deviceId: string
    phase: PlaybackSyncClientPhase
    clockOffsetMs: number
    roundTripEstimateMs: number
}

export type PlaybackSyncClientCallbacks = {
    onMessage?: (message: ServerPlaybackSyncMessage) => void
    onStateChange?: (state: PlaybackSyncClientState) => void
    onDiagnosticsChange?: (snapshot: PlaybackSyncClientDiagnosticsSnapshot) => void
}

const summarizeMeasurements = (measurements: readonly PlaybackSyncNtpMeasurement[]) => {
    if (measurements.length === 0) {
        return null
    }

    const sortedByRtt = [...measurements].sort((left, right) => left.rttMs - right.rttMs)
    const selected = sortedByRtt.slice(0, Math.ceil(sortedByRtt.length / 2))
    const clockOffsetMs = average(selected.map((item) => item.offsetMs))
    const roundTripEstimateMs = average(selected.map((item) => item.rttMs))

    if (clockOffsetMs === null || roundTripEstimateMs === null) {
        return null
    }

    return {
        clockOffsetMs,
        roundTripEstimateMs,
    }
}

const getDeviceIdPrefix = () => {
    if (typeof window === 'undefined') {
        return 'web'
    }

    const platform = getPlatformRuntime().platform

    switch (platform) {
        case 'web':
            return 'web'
        case 'macos':
            return 'tauri-darwin'
        case 'windows':
            return 'tauri-windows'
        case 'linux':
            return 'tauri-linux'
        case 'android':
            return 'tauri-android'
        case 'ios':
            return 'tauri-ios'
        default: {
            const exhaustiveCheck: never = platform
            return exhaustiveCheck
        }
    }
}

const createDeviceId = () => {
    const prefix = getDeviceIdPrefix()
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
        return `${prefix}-${crypto.randomUUID().slice(0, 8)}`
    }
    return `${prefix}-${Math.random().toString(36).slice(2, 10)}`
}

const isCurrentPlatformDeviceId = (deviceId: string) => {
    return deviceId.startsWith(`${getDeviceIdPrefix()}-`)
}

const getOrCreateDeviceId = () => {
    if (typeof window === 'undefined') {
        return createDeviceId()
    }

    const persisted = window.localStorage.getItem(DEVICE_ID_STORAGE_KEY)
    if (persisted && persisted.trim().length > 0 && isCurrentPlatformDeviceId(persisted)) {
        return persisted
    }

    const nextDeviceId = createDeviceId()
    window.localStorage.setItem(DEVICE_ID_STORAGE_KEY, nextDeviceId)
    return nextDeviceId
}

const isServerPlaybackSyncMessage = (value: unknown): value is ServerPlaybackSyncMessage => {
    if (typeof value !== 'object' || value === null) {
        return false
    }

    if (!('type' in value) || !('payload' in value)) {
        return false
    }

    return (
        typeof value.type === 'string' &&
        SERVER_MESSAGE_TYPES.some((messageType) => messageType === value.type)
    )
}

const cloneDiagnosticsPayload = (payload: unknown) => {
    if (payload === undefined) {
        return null
    }

    try {
        return structuredClone(payload)
    } catch {
        if (
            payload === null ||
            typeof payload === 'string' ||
            typeof payload === 'number' ||
            typeof payload === 'boolean'
        ) {
            return payload
        }

        return null
    }
}

export class PlaybackSyncClient {
    private readonly callbacks: PlaybackSyncClientCallbacks
    private readonly deviceId = getOrCreateDeviceId()

    private socket: WebSocket | null = null
    private explicitStop = false
    private reconnectAttempt = 0
    private phase: PlaybackSyncClientPhase = 'stopped'
    private socketState: PlaybackSyncSocketState = 'idle'
    private clockOffsetMs = 0
    private roundTripEstimateMs = 0
    private measurements: PlaybackSyncNtpMeasurement[] = []
    private initialMeasurements: PlaybackSyncNtpMeasurement[] = []
    private initialSampleCount = 0
    private snapshotReceived = false
    private protocolEvents: PlaybackSyncDiagnosticsEvent[] = []
    private lastInboundEvent: PlaybackSyncDiagnosticsEvent | null = null
    private lastOutboundEvent: PlaybackSyncDiagnosticsEvent | null = null
    private lastNtpRequestAtMs: number | null = null
    private lastNtpResponseAtMs: number | null = null
    private lastError: PlaybackSyncClientDiagnosticsError | null = null

    private reconnectTimer: number | null = null
    private initialSampleTimer: number | null = null
    private initialSettleTimer: number | null = null
    private heartbeatTimer: number | null = null

    constructor(callbacks: PlaybackSyncClientCallbacks = {}) {
        this.callbacks = callbacks
    }

    getState(): PlaybackSyncClientState {
        return {
            deviceId: this.deviceId,
            phase: this.phase,
            clockOffsetMs: this.clockOffsetMs,
            roundTripEstimateMs: this.roundTripEstimateMs,
        }
    }

    getDiagnosticsSnapshot(): PlaybackSyncClientDiagnosticsSnapshot {
        return {
            deviceId: this.deviceId,
            phase: this.phase,
            clockOffsetMs: this.clockOffsetMs,
            roundTripEstimateMs: this.roundTripEstimateMs,
            socketState: this.socketState,
            reconnectAttempt: this.reconnectAttempt,
            snapshotReceived: this.snapshotReceived,
            initialCalibration: {
                sampledCount: this.initialMeasurements.length,
                requiredSampleCount: INITIAL_SAMPLE_COUNT,
                settling: this.initialSettleTimer !== null,
            },
            measurements: [...this.measurements],
            lastNtpRequestAtMs: this.lastNtpRequestAtMs,
            lastNtpResponseAtMs: this.lastNtpResponseAtMs,
            lastInboundEvent: this.lastInboundEvent,
            lastOutboundEvent: this.lastOutboundEvent,
            protocolEvents: [...this.protocolEvents],
            lastError: this.lastError,
        }
    }

    connect() {
        if (
            this.socket &&
            (this.socket.readyState === WebSocket.OPEN ||
                this.socket.readyState === WebSocket.CONNECTING)
        ) {
            return
        }

        this.clearReconnectTimer()
        this.clearCalibrationTimers()
        this.clearHeartbeatTimer()

        this.explicitStop = false
        this.snapshotReceived = false
        this.initialMeasurements = []
        this.initialSampleCount = 0
        this.updateSocketState('connecting')

        this.setPhase(this.reconnectAttempt > 0 ? 'reconnecting' : 'connecting')

        const socket = new WebSocket(buildWebSocketUrl('/ws/playback-sync'))
        this.socket = socket
        socket.addEventListener('open', () => {
            if (this.socket !== socket) {
                return
            }
            this.updateSocketState('open')
            this.sendMessage({
                type: 'HELLO',
                payload: {
                    deviceId: this.deviceId,
                    clientVersion: CLIENT_VERSION,
                    token: getAuthToken() ?? undefined,
                },
            })
        })
        socket.addEventListener('message', (event) => {
            if (this.socket !== socket || typeof event.data !== 'string') {
                return
            }
            this.handleIncomingMessage(event.data)
        })
        socket.addEventListener('close', () => {
            if (this.socket !== socket) {
                return
            }
            this.socket = null
            this.clearCalibrationTimers()
            this.clearHeartbeatTimer()
            if (this.explicitStop) {
                this.updateSocketState('idle')
                this.setPhase('stopped')
                return
            }
            this.updateSocketState('closed')
            this.scheduleReconnect()
        })
        socket.addEventListener('error', () => {
            this.setLastError({
                atMs: nowClientMs(),
                code: 'SOCKET_ERROR',
                message: 'WebSocket transport error',
                rawType: null,
                rawMessage: null,
            })
            if (this.phase === 'connecting') {
                this.setPhase('error')
            }
        })
    }

    disconnect() {
        this.explicitStop = true
        this.reconnectAttempt = 0
        this.clearReconnectTimer()
        this.clearCalibrationTimers()
        this.clearHeartbeatTimer()
        if (this.socket && this.socket.readyState !== WebSocket.CLOSED) {
            this.updateSocketState('closing')
            this.socket.close()
        }
        this.socket = null
        this.updateSocketState('idle')
        this.setPhase('stopped')
    }

    requestSync() {
        return this.sendMessage({
            type: 'SYNC',
            payload: {
                deviceId: this.deviceId,
            },
        })
    }

    sendPlay(payload: Omit<PlaybackControlPayload, 'deviceId'>) {
        return this.sendMessage({
            type: 'PLAY',
            payload: {
                ...payload,
                deviceId: this.deviceId,
            },
        })
    }

    sendPause(payload: Omit<PlaybackControlPayload, 'deviceId'>) {
        return this.sendMessage({
            type: 'PAUSE',
            payload: {
                ...payload,
                deviceId: this.deviceId,
            },
        })
    }

    sendSeek(payload: Omit<PlaybackControlPayload, 'deviceId'>) {
        return this.sendMessage({
            type: 'SEEK',
            payload: {
                ...payload,
                deviceId: this.deviceId,
            },
        })
    }

    sendAudioSourceLoaded(payload: Omit<AudioSourceLoadedMessage['payload'], 'deviceId'>) {
        return this.sendMessage({
            type: 'AUDIO_SOURCE_LOADED',
            payload: {
                ...payload,
                deviceId: this.deviceId,
            },
        })
    }

    private handleIncomingMessage(rawMessage: string) {
        const receivedAtMs = nowClientMs()
        let parsedMessage: unknown
        try {
            parsedMessage = JSON.parse(rawMessage)
        } catch {
            this.recordProtocolEvent({
                direction: 'in',
                type: 'INVALID_JSON',
                rawType: null,
                atMs: receivedAtMs,
                payload: rawMessage,
            })
            this.setLastError({
                atMs: receivedAtMs,
                code: 'INVALID_JSON',
                message: 'Failed to parse playback sync message',
                rawType: null,
                rawMessage,
            })
            this.setPhase('error')
            return
        }

        const rawType =
            typeof parsedMessage === 'object' &&
            parsedMessage !== null &&
            'type' in parsedMessage &&
            typeof parsedMessage.type === 'string'
                ? parsedMessage.type
                : null

        if (!isServerPlaybackSyncMessage(parsedMessage)) {
            this.recordProtocolEvent({
                direction: 'in',
                type: 'INVALID_SERVER_MESSAGE',
                rawType,
                atMs: receivedAtMs,
                payload: parsedMessage,
            })
            this.setLastError({
                atMs: receivedAtMs,
                code: 'INVALID_SERVER_MESSAGE',
                message: 'Invalid playback sync message',
                rawType,
                rawMessage,
            })
            this.setPhase('error')
            return
        }

        const message = parsedMessage
        this.recordProtocolEvent({
            direction: 'in',
            type: message.type,
            rawType: message.type,
            atMs: receivedAtMs,
            payload: message.payload,
        })
        switch (message.type) {
            case 'NTP_RESPONSE':
                this.handleNtpResponse(message.payload)
                break
            case 'SNAPSHOT':
                this.snapshotReceived = true
                this.callbacks.onMessage?.(message)
                if (this.phase !== 'calibrating' && this.phase !== 'ready') {
                    this.startInitialCalibration()
                }
                break
            case 'ROOM_EVENT_LOAD_AUDIO_SOURCE':
            case 'ROOM_EVENT_QUEUE_CHANGE':
            case 'SCHEDULED_ACTION':
            case 'ROOM_EVENT_DEVICE_CHANGE':
            case 'ERROR':
                if (message.type === 'ERROR') {
                    this.setLastError({
                        atMs: receivedAtMs,
                        code: message.payload.code,
                        message: message.payload.message,
                        rawType: message.type,
                        rawMessage,
                    })
                }
                this.callbacks.onMessage?.(message)
                break
            default:
                break
        }
    }

    private handleNtpResponse(payload: NtpResponsePayload) {
        const t3 = nowClientMs()
        const offsetMs = (payload.t1 - payload.t0 + (payload.t2 - t3)) / 2
        const rttMs = Math.max(0, t3 - payload.t0 - (payload.t2 - payload.t1))
        const measurement = { offsetMs, rttMs, recordedAtMs: t3 }

        this.measurements = [...this.measurements, measurement].slice(-MAX_MEASUREMENT_COUNT)
        this.lastNtpResponseAtMs = t3
        if (this.phase === 'calibrating') {
            this.initialMeasurements = [...this.initialMeasurements, measurement]
        }

        if (this.phase === 'ready') {
            this.applyMeasurementSummary(this.measurements)
            return
        }

        this.emitDiagnostics()
    }

    private startInitialCalibration() {
        this.clearCalibrationTimers()
        this.initialMeasurements = []
        this.initialSampleCount = 0
        this.setPhase('calibrating')

        const sendSample = () => {
            if (this.initialSampleCount >= INITIAL_SAMPLE_COUNT) {
                this.clearInitialSampleTimer()
                this.initialSettleTimer = window.setTimeout(() => {
                    this.initialSettleTimer = null
                    this.finalizeInitialCalibration()
                }, INITIAL_SAMPLE_SETTLE_MS)
                return
            }

            this.initialSampleCount += 1
            this.sendNtpProbe()
        }

        sendSample()
        this.initialSampleTimer = window.setInterval(sendSample, INITIAL_SAMPLE_INTERVAL_MS)
    }

    private finalizeInitialCalibration() {
        if (!this.snapshotReceived) {
            this.setPhase('connecting')
            return
        }

        const summary = summarizeMeasurements(this.initialMeasurements)
        if (!summary) {
            this.setPhase('error')
            return
        }

        this.clockOffsetMs = summary.clockOffsetMs
        this.roundTripEstimateMs = summary.roundTripEstimateMs
        this.reconnectAttempt = 0
        this.setPhase('ready')
        this.startHeartbeat()
    }

    private startHeartbeat() {
        this.clearHeartbeatTimer()
        this.heartbeatTimer = window.setInterval(() => {
            this.sendNtpProbe()
        }, STEADY_STATE_INTERVAL_MS)
    }

    private sendNtpProbe() {
        this.sendMessage({
            type: 'NTP_REQUEST',
            payload: {
                t0: nowClientMs(),
                clientRttMs: this.roundTripEstimateMs > 0 ? this.roundTripEstimateMs : undefined,
            },
        })
    }

    private sendMessage(message: ClientPlaybackSyncMessage) {
        if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
            return false
        }

        const sentAtMs = nowClientMs()
        if (message.type === 'NTP_REQUEST') {
            this.lastNtpRequestAtMs = sentAtMs
        }
        this.socket.send(JSON.stringify(message))
        this.recordProtocolEvent({
            direction: 'out',
            type: message.type,
            rawType: message.type,
            atMs: sentAtMs,
            payload: message.payload,
        })
        return true
    }

    private applyMeasurementSummary(measurements: readonly PlaybackSyncNtpMeasurement[]) {
        const summary = summarizeMeasurements(measurements)
        if (!summary) {
            return
        }

        this.clockOffsetMs = summary.clockOffsetMs
        this.roundTripEstimateMs = summary.roundTripEstimateMs
        this.emitState()
    }

    private scheduleReconnect() {
        this.clearReconnectTimer()
        const delayMs =
            RECONNECT_DELAYS_MS[Math.min(this.reconnectAttempt, RECONNECT_DELAYS_MS.length - 1)]
        this.reconnectAttempt += 1
        this.setPhase('reconnecting')
        this.reconnectTimer = window.setTimeout(() => {
            this.reconnectTimer = null
            this.connect()
        }, delayMs)
    }

    private setPhase(nextPhase: PlaybackSyncClientPhase) {
        if (this.phase === nextPhase) {
            return
        }
        this.phase = nextPhase
        this.emitState()
    }

    private emitState() {
        this.callbacks.onStateChange?.(this.getState())
        this.emitDiagnostics()
    }

    private emitDiagnostics() {
        this.callbacks.onDiagnosticsChange?.(this.getDiagnosticsSnapshot())
    }

    private updateSocketState(nextState: PlaybackSyncSocketState) {
        if (this.socketState === nextState) {
            return
        }
        this.socketState = nextState
        this.emitDiagnostics()
    }

    private setLastError(error: PlaybackSyncClientDiagnosticsError) {
        this.lastError = error
        this.emitDiagnostics()
    }

    private recordProtocolEvent(event: PlaybackSyncDiagnosticsEvent) {
        const normalizedEvent = {
            ...event,
            payload: cloneDiagnosticsPayload(event.payload),
        }
        this.protocolEvents = [...this.protocolEvents, normalizedEvent].slice(
            -MAX_PROTOCOL_EVENT_COUNT,
        )
        if (normalizedEvent.direction === 'in') {
            this.lastInboundEvent = normalizedEvent
        } else {
            this.lastOutboundEvent = normalizedEvent
        }
        this.emitDiagnostics()
    }

    private clearReconnectTimer() {
        if (this.reconnectTimer !== null) {
            window.clearTimeout(this.reconnectTimer)
            this.reconnectTimer = null
        }
    }

    private clearInitialSampleTimer() {
        if (this.initialSampleTimer !== null) {
            window.clearInterval(this.initialSampleTimer)
            this.initialSampleTimer = null
        }
    }

    private clearCalibrationTimers() {
        this.clearInitialSampleTimer()
        if (this.initialSettleTimer !== null) {
            window.clearTimeout(this.initialSettleTimer)
            this.initialSettleTimer = null
        }
    }

    private clearHeartbeatTimer() {
        if (this.heartbeatTimer !== null) {
            window.clearInterval(this.heartbeatTimer)
            this.heartbeatTimer = null
        }
    }
}
