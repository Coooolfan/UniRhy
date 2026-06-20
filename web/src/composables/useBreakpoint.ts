import { onMounted, onUnmounted, ref } from 'vue'

export type Breakpoint = 'base' | 'md' | 'lg' | 'xl'

const QUERIES: { breakpoint: Exclude<Breakpoint, 'base'>; query: string }[] = [
    { breakpoint: 'xl', query: '(min-width: 1280px)' },
    { breakpoint: 'lg', query: '(min-width: 1024px)' },
    { breakpoint: 'md', query: '(min-width: 768px)' },
]

const detect = (): Breakpoint => {
    if (typeof window === 'undefined') {
        return 'base'
    }
    if (!window.matchMedia) {
        return 'base'
    }

    for (const { breakpoint, query } of QUERIES) {
        if (window.matchMedia(query).matches) {
            return breakpoint
        }
    }
    return 'base'
}

export const useBreakpoint = () => {
    const breakpoint = ref<Breakpoint>(detect())
    const lists: MediaQueryList[] = []

    const sync = () => {
        breakpoint.value = detect()
    }

    onMounted(() => {
        if (!window.matchMedia) {
            return
        }

        QUERIES.forEach(({ query }) => {
            const list = window.matchMedia(query)
            list.addEventListener('change', sync)
            lists.push(list)
        })
        sync()
    })

    onUnmounted(() => {
        lists.forEach((list) => {
            list.removeEventListener('change', sync)
        })
    })

    return breakpoint
}
