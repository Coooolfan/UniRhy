import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { PlaybackSyncClient } from '@/services/playbackSyncClient'

type MockListener = (event: unknown) => void

class MockWebSocket {
    static readonly CONNECTING = 0
    static readonly OPEN = 1
    static readonly CLOSING = 2
    static readonly CLOSED = 3
    static instances: MockWebSocket[] = []

    readonly url: string
    readyState = MockWebSocket.CONNECTING
    sentMessages: string[] = []
    private readonly listeners = new Map<string, MockListener[]>()

    constructor(url: string) {
        this.url = url
        MockWebSocket.instances.push(this)
    }

    addEventListener(type: string, listener: MockListener) {
        const current = this.listeners.get(type) ?? []
        current.push(listener)
        this.listeners.set(type, current)
    }

    close() {
        this.readyState = MockWebSocket.CLOSED
        this.emit('close', new Event('close'))
    }

    send(data: string) {
        this.sentMessages.push(data)
    }

    emitOpen() {
        this.readyState = MockWebSocket.OPEN
        this.emit('open', new Event('open'))
    }

    emitMessage(data: string) {
        this.emit('message', { data })
    }

    emitClose() {
        this.readyState = MockWebSocket.CLOSED
        this.emit('close', new Event('close'))
    }

    emitError() {
        this.emit('error', new Event('error'))
    }

    private emit(type: string, event: unknown) {
        for (const listener of this.listeners.get(type) ?? []) {
            listener(event)
        }
    }
}

const decodeSentMessages = (socket: MockWebSocket | undefined) => {
    return (socket?.sentMessages ?? []).map((message) => JSON.parse(message))
}

const emitSnapshot = (socket: MockWebSocket | undefined) => {
    socket?.emitMessage(
        JSON.stringify({
            type: 'SNAPSHOT',
            payload: {
                state: {
                    status: 'PAUSED',
                    recordingId: null,
                    mediaFileId: null,
                    sourceUrl: null,
                    positionSeconds: 0,
                    serverTimeToExecuteMs: 0,
                    version: 0,
                    updatedAtMs: 0,
                },
                serverNowMs: Date.now(),
            },
        }),
    )
}

const completeCalibration = (socket: MockWebSocket | undefined) => {
    emitSnapshot(socket)
    vi.advanceTimersByTime(600)

    const ntpRequests = decodeSentMessages(socket).filter(
        (message) => message.type === 'NTP_REQUEST',
    )
    ntpRequests.forEach((request, index) => {
        vi.advanceTimersByTime(7)
        socket?.emitMessage(
            JSON.stringify({
                type: 'NTP_RESPONSE',
                payload: {
                    t0: request.payload.t0,
                    t1: request.payload.t0 + 12 + (index % 5) * 3,
                    t2: request.payload.t0 + 15 + (index % 5) * 3,
                },
            }),
        )
    })
    vi.advanceTimersByTime(100)
}

describe('playbackSyncClient', () => {
    beforeEach(() => {
        MockWebSocket.instances = []
        window.localStorage.clear()
        delete window.__UNIRHY_RUNTIME__
        vi.useFakeTimers()
        vi.setSystemTime(new Date('2026-03-07T00:00:00Z'))
        vi.stubGlobal('WebSocket', MockWebSocket)
        Object.defineProperty(performance, 'timeOrigin', {
            configurable: true,
            value: 0,
        })
        vi.spyOn(performance, 'now').mockImplementation(() => Date.now())
        Object.defineProperty(window, 'location', {
            configurable: true,
            value: {
                origin: 'http://localhost:5173',
            },
        })
    })

    afterEach(() => {
        vi.useRealTimers()
        vi.unstubAllGlobals()
        vi.restoreAllMocks()
    })

    it('persists deviceId and sends HELLO with the same device across reconnects', () => {
        const firstClient = new PlaybackSyncClient()
        firstClient.connect()

        const firstSocket = MockWebSocket.instances[0]
        firstSocket?.emitOpen()

        const firstHello = JSON.parse(firstSocket?.sentMessages[0] ?? 'null')
        expect(firstHello.type).toBe('HELLO')
        expect(firstHello.payload.deviceId).toMatch(/^web-/)
        expect(firstClient.getDiagnosticsSnapshot().lastOutboundEvent?.type).toBe('HELLO')

        firstClient.disconnect()

        const secondClient = new PlaybackSyncClient()
        secondClient.connect()
        const secondSocket = MockWebSocket.instances[1]
        secondSocket?.emitOpen()

        const secondHello = JSON.parse(secondSocket?.sentMessages[0] ?? 'null')
        expect(secondHello.payload.deviceId).toBe(firstHello.payload.deviceId)
    })

    it('uses tauri-darwin prefix for macOS tauri runtime device ids', () => {
        window.__UNIRHY_RUNTIME__ = {
            apiBaseUrl: 'http://localhost:4000',
            platform: 'macos',
        }

        const client = new PlaybackSyncClient()
        client.connect()

        const socket = MockWebSocket.instances[0]
        socket?.emitOpen()

        const hello = JSON.parse(socket?.sentMessages[0] ?? 'null')
        expect(hello.type).toBe('HELLO')
        expect(hello.payload.deviceId).toMatch(/^tauri-darwin-/)
    })

    it('uses tauri-android prefix for Android tauri runtime device ids', () => {
        window.__UNIRHY_RUNTIME__ = {
            apiBaseUrl: 'http://localhost:4000',
            platform: 'android',
        }

        const client = new PlaybackSyncClient()
        client.connect()

        const socket = MockWebSocket.instances[0]
        socket?.emitOpen()

        const hello = JSON.parse(socket?.sentMessages[0] ?? 'null')
        expect(hello.type).toBe('HELLO')
        expect(hello.payload.deviceId).toMatch(/^tauri-android-/)
    })

    it('uses tauri-ios prefix for iOS tauri runtime device ids', () => {
        window.__UNIRHY_RUNTIME__ = {
            apiBaseUrl: 'http://localhost:4000',
            platform: 'ios',
        }

        const client = new PlaybackSyncClient()
        client.connect()

        const socket = MockWebSocket.instances[0]
        socket?.emitOpen()

        const hello = JSON.parse(socket?.sentMessages[0] ?? 'null')
        expect(hello.type).toBe('HELLO')
        expect(hello.payload.deviceId).toMatch(/^tauri-ios-/)
    })

    it('migrates persisted web device ids to tauri-darwin on macOS tauri runtime', () => {
        window.__UNIRHY_RUNTIME__ = {
            apiBaseUrl: 'http://localhost:4000',
            platform: 'macos',
        }
        window.localStorage.setItem('unirhy.playback-sync.device-id', 'web-legacy01')

        const client = new PlaybackSyncClient()
        client.connect()

        const socket = MockWebSocket.instances[0]
        socket?.emitOpen()

        const hello = JSON.parse(socket?.sentMessages[0] ?? 'null')
        expect(hello.type).toBe('HELLO')
        expect(hello.payload.deviceId).toMatch(/^tauri-darwin-/)
        expect(hello.payload.deviceId).not.toBe('web-legacy01')
        expect(window.localStorage.getItem('unirhy.playback-sync.device-id')).toBe(
            hello.payload.deviceId,
        )
    })

    it('sends HELLO with token from localStorage', () => {
        window.localStorage.setItem('unirhy.auth-token', 'my-test-token')
        const client = new PlaybackSyncClient()
        client.connect()

        const socket = MockWebSocket.instances[0]
        socket?.emitOpen()

        const hello = JSON.parse(socket?.sentMessages[0] ?? 'null')
        expect(hello.type).toBe('HELLO')
        expect(hello.payload.token).toBe('my-test-token')
    })

    it('sends HELLO without token when localStorage has no token', () => {
        const client = new PlaybackSyncClient()
        client.connect()

        const socket = MockWebSocket.instances[0]
        socket?.emitOpen()

        const hello = JSON.parse(socket?.sentMessages[0] ?? 'null')
        expect(hello.type).toBe('HELLO')
        expect(hello.payload.token).toBeUndefined()
    })

    it('connects with a clean WebSocket URL without query parameters', () => {
        const client = new PlaybackSyncClient()
        client.connect()

        const socket = MockWebSocket.instances[0]
        expect(socket?.url).toBe('ws://localhost:5173/ws/playback-sync')
        expect(socket?.url).not.toContain('?')
    })

    it('records jittery calibration measurements and becomes ready', () => {
        const phases: string[] = []
        const client = new PlaybackSyncClient({
            onStateChange: (state) => {
                phases.push(state.phase)
            },
        })

        client.connect()
        const socket = MockWebSocket.instances[0]
        socket?.emitOpen()

        completeCalibration(socket)

        expect(client.getState().phase).toBe('ready')
        expect(client.getState().roundTripEstimateMs).toBeGreaterThanOrEqual(0)
        expect(phases).toContain('calibrating')
        expect(phases).toContain('ready')

        const diagnostics = client.getDiagnosticsSnapshot()
        expect(diagnostics.measurements).toHaveLength(20)
        expect(diagnostics.snapshotReceived).toBe(true)
        expect(diagnostics.lastOutboundEvent?.type).toBe('NTP_REQUEST')
        expect(diagnostics.lastInboundEvent?.type).toBe('NTP_RESPONSE')
        expect(diagnostics.protocolEvents).toHaveLength(30)
        expect(diagnostics.protocolEvents.some((event) => event.type === 'NTP_REQUEST')).toBe(true)
    })

    it('exposes stale response age and updates again after the next heartbeat response', () => {
        const client = new PlaybackSyncClient()
        client.connect()

        const socket = MockWebSocket.instances[0]
        socket?.emitOpen()
        completeCalibration(socket)

        const previousResponseAtMs = client.getDiagnosticsSnapshot().lastNtpResponseAtMs
        expect(previousResponseAtMs).not.toBeNull()

        vi.advanceTimersByTime(6_000)

        expect(
            Date.now() - (client.getDiagnosticsSnapshot().lastNtpResponseAtMs ?? 0),
        ).toBeGreaterThanOrEqual(6_000)

        const ntpRequests = decodeSentMessages(socket).filter(
            (message) => message.type === 'NTP_REQUEST',
        )
        const [latestRequest] = ntpRequests.slice(-1)
        socket?.emitMessage(
            JSON.stringify({
                type: 'NTP_RESPONSE',
                payload: {
                    t0: latestRequest?.payload.t0,
                    t1: latestRequest?.payload.t0 + 20,
                    t2: latestRequest?.payload.t0 + 22,
                },
            }),
        )

        expect(client.getDiagnosticsSnapshot().lastNtpResponseAtMs).toBeGreaterThan(
            previousResponseAtMs ?? 0,
        )
    })

    it('records invalid inbound message details when the payload shape is unsupported', () => {
        const client = new PlaybackSyncClient()
        client.connect()

        const socket = MockWebSocket.instances[0]
        socket?.emitOpen()
        socket?.emitMessage(
            JSON.stringify({
                type: 'BOOM',
                payload: {
                    reason: 'unsupported',
                },
            }),
        )

        const diagnostics = client.getDiagnosticsSnapshot()
        expect(client.getState().phase).toBe('error')
        expect(diagnostics.lastInboundEvent?.type).toBe('INVALID_SERVER_MESSAGE')
        expect(diagnostics.lastInboundEvent?.rawType).toBe('BOOM')
        expect(diagnostics.lastError?.rawType).toBe('BOOM')
        expect(diagnostics.lastError?.message).toBe('Invalid playback sync message')
    })

    it('reconnects with backoff after an unexpected close and tracks retry diagnostics', () => {
        const client = new PlaybackSyncClient()
        client.connect()

        const socket = MockWebSocket.instances[0]
        socket?.emitClose()

        expect(client.getState().phase).toBe('reconnecting')
        expect(client.getDiagnosticsSnapshot().reconnectAttempt).toBe(1)
        expect(client.getDiagnosticsSnapshot().socketState).toBe('closed')
        expect(MockWebSocket.instances).toHaveLength(1)

        vi.advanceTimersByTime(999)
        expect(MockWebSocket.instances).toHaveLength(1)

        vi.advanceTimersByTime(1)
        expect(MockWebSocket.instances).toHaveLength(2)
    })
})
