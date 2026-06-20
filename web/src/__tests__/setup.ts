import { vi } from 'vitest'

const createTestStorage = (): Storage => {
    const store = new Map<string, string>()

    return {
        get length() {
            return store.size
        },

        clear() {
            store.clear()
        },

        getItem(key: string) {
            return store.get(key) ?? null
        },

        key(index: number) {
            return Array.from(store.keys())[index] ?? null
        },

        removeItem(key: string) {
            store.delete(key)
        },

        setItem(key: string, value: string) {
            store.set(key, value)
        },
    }
}

class TestMediaQueryList extends EventTarget implements MediaQueryList {
    public onchange: ((this: MediaQueryList, event: MediaQueryListEvent) => unknown) | null = null
    public readonly matches = false
    public readonly media: string
    private readonly legacyListeners = new WeakMap<
        (this: MediaQueryList, event: MediaQueryListEvent) => unknown,
        EventListener
    >()

    public constructor(media: string) {
        super()
        this.media = media
    }

    public addListener(
        listener: ((this: MediaQueryList, event: MediaQueryListEvent) => unknown) | null,
    ) {
        if (!listener) {
            return
        }

        const wrappedListener: EventListener = (event) => {
            if (event instanceof MediaQueryListEvent) {
                listener.call(this, event)
            }
        }
        this.legacyListeners.set(listener, wrappedListener)
        this.addEventListener('change', wrappedListener)
    }

    public removeListener(
        listener: ((this: MediaQueryList, event: MediaQueryListEvent) => unknown) | null,
    ) {
        if (!listener) {
            return
        }

        const wrappedListener = this.legacyListeners.get(listener)
        if (!wrappedListener) {
            return
        }

        this.removeEventListener('change', wrappedListener)
        this.legacyListeners.delete(listener)
    }
}

Object.defineProperty(window, 'localStorage', {
    configurable: true,
    value: createTestStorage(),
})

if (!window.matchMedia) {
    Object.defineProperty(window, 'matchMedia', {
        configurable: true,
        value: vi.fn((query: string): MediaQueryList => new TestMediaQueryList(query)),
    })
}
