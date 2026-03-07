import type {
    AudioSourceLoadedMessage,
    ClientPlaybackSyncMessage,
    NtpResponsePayload,
    PlaybackControlPayload,
    ServerPlaybackSyncMessage,
} from '@/services/playbackSyncProtocol'

const DEVICE_ID_STORAGE_KEY = 'unirhy.playback-sync.device-id'
const INITIAL_SAMPLE_COUNT = 20
const INITIAL_SAMPLE_INTERVAL_MS = 30
const INITIAL_SAMPLE_SETTLE_MS = 60
const STEADY_STATE_INTERVAL_MS = 2_500
const RECONNECT_DELAYS_MS = [1_000, 2_000, 5_000] as const
const MAX_MEASUREMENT_COUNT = 20
const CLIENT_VERSION = 'web@playback-sync'
const SERVER_MESSAGE_TYPES = [
    'NTP_RESPONSE',
    'SNAPSHOT',
    'ROOM_EVENT_LOAD_AUDIO_SOURCE',
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

type NtpMeasurement = {
    offsetMs: number
    rttMs: number
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
}

const nowClientMs = () => {
    if (typeof performance !== 'undefined') {
        return performance.timeOrigin + performance.now()
    }
    return Date.now()
}

const average = (values: number[]) => values.reduce((sum, value) => sum + value, 0) / values.length

const summarizeMeasurements = (measurements: readonly NtpMeasurement[]) => {
    if (measurements.length === 0) {
        return null
    }

    const sortedByRtt = [...measurements].sort((left, right) => left.rttMs - right.rttMs)
    const selected = sortedByRtt.slice(0, Math.ceil(sortedByRtt.length / 2))
    return {
        clockOffsetMs: average(selected.map((item) => item.offsetMs)),
        roundTripEstimateMs: average(selected.map((item) => item.rttMs)),
    }
}

const buildWebSocketUrl = () => {
    if (typeof window === 'undefined') {
        return ''
    }

    const { origin } = window.location
    return `${origin.replace(/^http/i, 'ws')}/ws/playback-sync`
}

const createDeviceId = () => {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
        return `web-${crypto.randomUUID().slice(0, 8)}`
    }
    return `web-${Math.random().toString(36).slice(2, 10)}`
}

const getOrCreateDeviceId = () => {
    if (typeof window === 'undefined') {
        return createDeviceId()
    }

    const persisted = window.localStorage.getItem(DEVICE_ID_STORAGE_KEY)
    if (persisted && persisted.trim().length > 0) {
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

export class PlaybackSyncClient {
    private readonly callbacks: PlaybackSyncClientCallbacks
    private readonly deviceId = getOrCreateDeviceId()

    private socket: WebSocket | null = null
    private explicitStop = false
    private reconnectAttempt = 0
    private phase: PlaybackSyncClientPhase = 'stopped'
    private clockOffsetMs = 0
    private roundTripEstimateMs = 0
    private measurements: NtpMeasurement[] = []
    private initialMeasurements: NtpMeasurement[] = []
    private initialSampleCount = 0
    private snapshotReceived = false

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

        this.setPhase(this.reconnectAttempt > 0 ? 'reconnecting' : 'connecting')

        const socket = new WebSocket(buildWebSocketUrl())
        this.socket = socket
        socket.addEventListener('open', () => {
            if (this.socket !== socket) {
                return
            }
            this.sendMessage({
                type: 'HELLO',
                payload: {
                    deviceId: this.deviceId,
                    clientVersion: CLIENT_VERSION,
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
                this.setPhase('stopped')
                return
            }
            this.scheduleReconnect()
        })
        socket.addEventListener('error', () => {
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
        this.socket?.close()
        this.socket = null
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
        let parsedMessage: unknown
        try {
            parsedMessage = JSON.parse(rawMessage)
        } catch {
            this.setPhase('error')
            return
        }

        if (!isServerPlaybackSyncMessage(parsedMessage)) {
            this.setPhase('error')
            return
        }

        const message = parsedMessage
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
            case 'SCHEDULED_ACTION':
            case 'ROOM_EVENT_DEVICE_CHANGE':
            case 'ERROR':
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
        const measurement = { offsetMs, rttMs }

        this.measurements = [...this.measurements, measurement].slice(-MAX_MEASUREMENT_COUNT)
        if (this.phase === 'calibrating') {
            this.initialMeasurements = [...this.initialMeasurements, measurement]
        }

        if (this.phase === 'ready') {
            this.applyMeasurementSummary(this.measurements)
        }
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
        this.emitState()
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
        this.socket.send(JSON.stringify(message))
        return true
    }

    private applyMeasurementSummary(measurements: readonly NtpMeasurement[]) {
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
