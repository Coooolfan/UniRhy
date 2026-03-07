import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

type ChartInitOptions = {
    width: number
    height: number
}

const measureChartViewport = (element: HTMLElement | null) => {
    const width = element?.clientWidth ?? 0
    const height = element?.clientHeight ?? 0

    if (width <= 0 || height <= 0) {
        return null
    }

    return { width, height }
}

export const useChartContainer = () => {
    const viewportRef = ref<HTMLElement | null>(null)
    const initOptions = ref<ChartInitOptions | null>(null)
    let resizeObserver: ResizeObserver | null = null

    const disconnectResizeObserver = () => {
        resizeObserver?.disconnect()
        resizeObserver = null
    }

    const initializeChart = () => {
        const size = measureChartViewport(viewportRef.value)
        if (!size) {
            return false
        }

        initOptions.value = size
        disconnectResizeObserver()
        return true
    }

    const startObservingUntilReady = () => {
        if (!viewportRef.value || typeof ResizeObserver === 'undefined') {
            return
        }

        disconnectResizeObserver()
        resizeObserver = new ResizeObserver(() => {
            initializeChart()
        })
        resizeObserver.observe(viewportRef.value)
    }

    onMounted(async () => {
        await nextTick()

        if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
            await new Promise<void>((resolve) => {
                window.requestAnimationFrame(() => {
                    resolve()
                })
            })
        }

        if (!initializeChart()) {
            startObservingUntilReady()
        }
    })

    onBeforeUnmount(() => {
        disconnectResizeObserver()
    })

    return {
        viewportRef,
        initOptions,
        isReady: computed(() => initOptions.value !== null),
    }
}
