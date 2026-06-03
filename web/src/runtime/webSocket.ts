import { buildWebSocketUrl } from '@/runtime/platform'

export const RUNTIME_WEB_SOCKET_CONNECTING = 0
export const RUNTIME_WEB_SOCKET_OPEN = 1
export const RUNTIME_WEB_SOCKET_CLOSING = 2
export const RUNTIME_WEB_SOCKET_CLOSED = 3

type RuntimeWebSocketEventType = 'open' | 'message' | 'close' | 'error'
type RuntimeWebSocketListener = (event: MessageEvent<string>) => void

export type RuntimeWebSocket = {
    readonly readyState: number
    addEventListener: (type: RuntimeWebSocketEventType, listener: RuntimeWebSocketListener) => void
    send: (data: string) => void
    close: () => void
}

const isTauri = () => typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window

const createRuntimeWebSocketEvent = (type: RuntimeWebSocketEventType) =>
    new MessageEvent(type, { data: '' })

const getStringEventData = (event: unknown) => {
    if (typeof event !== 'object' || event === null || !('data' in event)) {
        return null
    }
    return typeof event.data === 'string' ? event.data : null
}

class TauriRuntimeWebSocket implements RuntimeWebSocket {
    private pluginSocket: import('@tauri-apps/plugin-websocket').default | null = null
    private readonly listeners = new Map<RuntimeWebSocketEventType, Set<RuntimeWebSocketListener>>()
    private removePluginListener: (() => void) | null = null
    private state = RUNTIME_WEB_SOCKET_CONNECTING

    public constructor(url: string) {
        void this.connect(url)
    }

    public get readyState() {
        return this.state
    }

    public addEventListener(type: RuntimeWebSocketEventType, listener: RuntimeWebSocketListener) {
        const listeners = this.listeners.get(type) ?? new Set<RuntimeWebSocketListener>()
        listeners.add(listener)
        this.listeners.set(type, listeners)
    }

    public send(data: string) {
        if (this.state !== RUNTIME_WEB_SOCKET_OPEN || !this.pluginSocket) {
            return
        }

        void this.pluginSocket.send(data).catch(() => {
            this.emit('error', createRuntimeWebSocketEvent('error'))
        })
    }

    public close() {
        if (this.state === RUNTIME_WEB_SOCKET_CLOSED || this.state === RUNTIME_WEB_SOCKET_CLOSING) {
            return
        }

        this.state = RUNTIME_WEB_SOCKET_CLOSING
        this.removePluginListener?.()
        this.removePluginListener = null

        const socket = this.pluginSocket
        this.pluginSocket = null
        if (!socket) {
            this.markClosed()
            return
        }

        void socket
            .disconnect()
            .catch(() => {
                this.emit('error', createRuntimeWebSocketEvent('error'))
            })
            .finally(() => {
                this.markClosed()
            })
    }

    private async connect(url: string) {
        try {
            const { default: WebSocket } = await import('@tauri-apps/plugin-websocket')
            const socket = await WebSocket.connect(url)
            if (this.state !== RUNTIME_WEB_SOCKET_CONNECTING) {
                await socket.disconnect()
                return
            }

            this.pluginSocket = socket
            this.removePluginListener = socket.addListener((message) => {
                switch (message.type) {
                    case 'Text':
                        this.emit('message', new MessageEvent('message', { data: message.data }))
                        break
                    case 'Close':
                        this.markClosed()
                        break
                    case 'Binary':
                    case 'Ping':
                    case 'Pong':
                        break
                    default:
                        message satisfies never
                }
            })
            this.state = RUNTIME_WEB_SOCKET_OPEN
            this.emit('open', createRuntimeWebSocketEvent('open'))
        } catch {
            this.state = RUNTIME_WEB_SOCKET_CLOSED
            this.emit('error', createRuntimeWebSocketEvent('error'))
            this.emit('close', createRuntimeWebSocketEvent('close'))
        }
    }

    private markClosed() {
        if (this.state === RUNTIME_WEB_SOCKET_CLOSED) {
            return
        }
        this.state = RUNTIME_WEB_SOCKET_CLOSED
        this.emit('close', createRuntimeWebSocketEvent('close'))
    }

    private emit(type: RuntimeWebSocketEventType, event: MessageEvent<string>) {
        for (const listener of this.listeners.get(type) ?? []) {
            listener(event)
        }
    }
}

export const createRuntimeWebSocket = (path: string): RuntimeWebSocket => {
    const url = buildWebSocketUrl(path)
    if (isTauri()) {
        return new TauriRuntimeWebSocket(url)
    }

    const socket = new WebSocket(url)
    return {
        get readyState() {
            return socket.readyState
        },
        addEventListener(type, listener) {
            socket.addEventListener(type, (event) => {
                const data = getStringEventData(event)
                if (type === 'message' && data !== null) {
                    listener(new MessageEvent('message', { data }))
                    return
                }
                listener(createRuntimeWebSocketEvent(type))
            })
        },
        send(data) {
            socket.send(data)
        },
        close() {
            socket.close()
        },
    }
}
