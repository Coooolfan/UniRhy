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

    private emit(type: string, event: unknown) {
        for (const listener of this.listeners.get(type) ?? []) {
            listener(event)
        }
    }
}

describe('playbackSyncClient', () => {
    beforeEach(() => {
        MockWebSocket.instances = []
        window.localStorage.clear()
        vi.useFakeTimers()
        vi.stubGlobal('WebSocket', MockWebSocket)
        vi.spyOn(performance, 'now').mockReturnValue(1_000)
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

        firstClient.disconnect()

        const secondClient = new PlaybackSyncClient()
        secondClient.connect()
        const secondSocket = MockWebSocket.instances[1]
        secondSocket?.emitOpen()

        const secondHello = JSON.parse(secondSocket?.sentMessages[0] ?? 'null')
        expect(secondHello.payload.deviceId).toBe(firstHello.payload.deviceId)
    })

    it('starts calibration after snapshot and becomes ready after enough NTP samples', () => {
        const phases: string[] = []
        const client = new PlaybackSyncClient({
            onStateChange: (state) => {
                phases.push(state.phase)
            },
        })

        client.connect()
        const socket = MockWebSocket.instances[0]
        socket?.emitOpen()

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
                    serverNowMs: 2_000,
                },
            }),
        )

        const firstNtp = JSON.parse(socket?.sentMessages[1] ?? 'null')
        socket?.emitMessage(
            JSON.stringify({
                type: 'NTP_RESPONSE',
                payload: {
                    t0: firstNtp.payload.t0,
                    t1: firstNtp.payload.t0 + 10,
                    t2: firstNtp.payload.t0 + 12,
                },
            }),
        )

        vi.advanceTimersByTime(700)

        expect(client.getState().phase).toBe('ready')
        expect(client.getState().roundTripEstimateMs).toBeGreaterThanOrEqual(0)
        expect(phases).toContain('calibrating')
        expect(phases).toContain('ready')
    })

    it('reconnects with backoff after an unexpected close', () => {
        const client = new PlaybackSyncClient()
        client.connect()

        const socket = MockWebSocket.instances[0]
        socket?.emitClose()

        expect(client.getState().phase).toBe('reconnecting')
        expect(MockWebSocket.instances).toHaveLength(1)

        vi.advanceTimersByTime(999)
        expect(MockWebSocket.instances).toHaveLength(1)

        vi.advanceTimersByTime(1)
        expect(MockWebSocket.instances).toHaveLength(2)
    })
})
