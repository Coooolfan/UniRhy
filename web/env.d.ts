/// <reference types="vite/client" />

declare module 'vue-echarts' {
    import type { DefineComponent } from 'vue'

    const VChart: DefineComponent<{
        option?: unknown
        autoresize?: boolean
        initOptions?: unknown
    }>

    export default VChart
}

declare module 'echarts/core' {
    export const use: (extensions: unknown[]) => void
}

declare module 'echarts/renderers' {
    export const CanvasRenderer: unknown
}

declare module 'echarts/charts' {
    export const LineChart: unknown
}

declare module 'echarts/components' {
    export const GridComponent: unknown
    export const MarkLineComponent: unknown
    export const TooltipComponent: unknown
}
