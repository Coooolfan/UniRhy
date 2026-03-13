<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { GridComponent, MarkLineComponent, TooltipComponent } from 'echarts/components'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import { useChartContainer } from '@/composables/useChartContainer'
import type {
    PlaybackSyncDiagnosticsEvent,
    PlaybackSyncNtpMeasurement,
} from '@/services/playbackSyncClient'
import { useAudioStore } from '@/stores/audio'
import { average } from '@/utils/math'
import { nowClientMs } from '@/utils/time'

use([CanvasRenderer, GridComponent, LineChart, MarkLineComponent, TooltipComponent])

type MeasurementPoint = {
    recordedAtMs: number
    offsetMs: number
    rttMs: number
}

type PipelinePoint = {
    recordedAtMs: number
    waitMs: number
    lateMs: number
}

type TimelineRow = {
    key: string
    direction: string
    type: string
    detail: string
    age: string
}

type TooltipDatum = {
    axisValueLabel?: string
    value?: number | string | (number | string)[]
    color?: string
    seriesName?: string
}

const PIPELINE_HISTORY_LIMIT = 50
const CHART_COLORS = {
    ink: '#1A1917',
    accent: '#C85A3C',
    border: '#DCD9D0',
    tooltipBackground: '#F2EFE9',
    axisLine: 'rgba(26, 25, 23, 0.14)',
    axisText: 'rgba(26, 25, 23, 0.42)',
    splitLine: 'rgba(26, 25, 23, 0.08)',
    zeroLine: 'rgba(26, 25, 23, 0.24)',
} as const
const clockFormatter = new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
})

const audioStore = useAudioStore()
const nowMs = ref(nowClientMs())
const pipelineHistory = ref<PipelinePoint[]>([])
const {
    viewportRef: ntpChartViewport,
    initOptions: ntpChartInitOptions,
    isReady: ntpChartReady,
} = useChartContainer()
const {
    viewportRef: pipelineChartViewport,
    initOptions: pipelineChartInitOptions,
    isReady: pipelineChartReady,
} = useChartContainer()

let tickTimer: number | null = null

const debugSnapshot = computed(() => audioStore.playbackSyncDebugSnapshot)

const reverseCopy = <T>(items: readonly T[]) =>
    Array.from(items, (_, index) => items[items.length - index - 1]!)

const formatNumber = (value: number | null | undefined, fractionDigits = 1) => {
    if (value === null || value === undefined || !Number.isFinite(value)) {
        return '-'
    }
    return value.toFixed(fractionDigits)
}

const formatSeconds = (value: number | null | undefined) => {
    if (value === null || value === undefined || !Number.isFinite(value)) {
        return '-'
    }
    return `${value.toFixed(2)}s`
}

const formatMilliseconds = (value: number | null | undefined, fractionDigits = 1) => {
    if (value === null || value === undefined || !Number.isFinite(value)) {
        return '-'
    }
    return `${value.toFixed(fractionDigits)}ms`
}

const formatClockTime = (value: number | null | undefined) => {
    if (value === null || value === undefined || !Number.isFinite(value)) {
        return '-'
    }

    return clockFormatter.format(new Date(value))
}

const formatAge = (value: number | null | undefined) => {
    if (value === null || value === undefined || !Number.isFinite(value)) {
        return '-'
    }

    const diffSeconds = Math.max(0, Math.round((nowMs.value - value) / 1_000))
    return `${diffSeconds}s ago`
}

const summarizeEventPayload = (payload: unknown) => {
    if (typeof payload !== 'object' || payload === null) {
        return '-'
    }

    const record = payload as Record<string, unknown>
    const commandId = typeof record.commandId === 'string' ? record.commandId : null
    const recordingId = typeof record.recordingId === 'number' ? record.recordingId : null
    const mediaFileId = typeof record.mediaFileId === 'number' ? record.mediaFileId : null
    const deviceId = typeof record.deviceId === 'string' ? record.deviceId : null
    const message = typeof record.message === 'string' ? record.message : null
    const maybeDevices = Array.isArray(record.devices) ? record.devices.length : null

    const parts: string[] = []
    if (commandId !== null) {
        parts.push(commandId)
    }
    if (recordingId !== null) {
        parts.push(`rec=${recordingId}`)
    }
    if (mediaFileId !== null) {
        parts.push(`media=${mediaFileId}`)
    }
    if (deviceId !== null) {
        parts.push(deviceId)
    }
    if (maybeDevices !== null) {
        parts.push(`${maybeDevices} devices`)
    }
    if (message !== null) {
        parts.push(message)
    }

    if (parts.length === 0) {
        return '-'
    }
    return parts.join(' · ')
}

const toLateDriftMs = (lateSeconds: number | null | undefined) => {
    if (lateSeconds === null || lateSeconds === undefined || !Number.isFinite(lateSeconds)) {
        return null
    }
    return lateSeconds * 1_000
}

const ntpMeasurements = computed<MeasurementPoint[]>(() => {
    const measurements = debugSnapshot.value.clientDiagnostics?.measurements ?? []
    return measurements.slice(-20).map((measurement) => ({
        recordedAtMs: measurement.recordedAtMs,
        offsetMs: measurement.offsetMs,
        rttMs: measurement.rttMs,
    }))
})

const recentProtocolEvents = computed<PlaybackSyncDiagnosticsEvent[]>(() => {
    const events = debugSnapshot.value.clientDiagnostics?.protocolEvents ?? []
    return reverseCopy(events).slice(0, 5)
})

const recentMeasurements = computed<PlaybackSyncNtpMeasurement[]>(() => {
    return reverseCopy(debugSnapshot.value.clientDiagnostics?.measurements ?? []).slice(0, 4)
})

const clockSyncUncertaintyMs = computed(() => {
    const rttMs = debugSnapshot.value.roundTripEstimateMs
    if (!Number.isFinite(rttMs) || rttMs < 0) {
        return null
    }
    return rttMs / 2
})

const offsetAverageMs = computed(() => average(ntpMeasurements.value.map((item) => item.offsetMs)))
const rttAverageMs = computed(() => average(ntpMeasurements.value.map((item) => item.rttMs)))

const currentTrackLabel = computed(() => {
    const track = debugSnapshot.value.currentTrack
    if (!track) {
        return 'No Active Track'
    }
    return `${track.title} · ${track.artist}`
})

const lastErrorMessage = computed(() => {
    return (
        debugSnapshot.value.error ??
        debugSnapshot.value.clientDiagnostics?.lastError?.message ??
        '无'
    )
})

const lastDeviceSummary = computed(() => {
    const devices = debugSnapshot.value.lastDeviceChange?.payload.devices ?? []
    if (devices.length === 0) {
        return '-'
    }
    return devices.map((device) => device.deviceId).join(', ')
})

const lastExecutionLateDriftMs = computed(() =>
    toLateDriftMs(debugSnapshot.value.lastLocalExecution?.lateSeconds),
)

const timelineRows = computed<TimelineRow[]>(() => {
    return recentProtocolEvents.value.map((event) => ({
        key: `${event.direction}-${event.type}-${event.atMs}`,
        direction: event.direction.toUpperCase(),
        type: event.type,
        detail: summarizeEventPayload(event.payload),
        age: formatAge(event.atMs),
    }))
})

const pushPipelineSample = () => {
    const execution = debugSnapshot.value.lastLocalExecution
    const nextPoint: PipelinePoint = {
        recordedAtMs: nowMs.value,
        waitMs: execution?.waitMs ?? 0,
        lateMs: toLateDriftMs(execution?.lateSeconds) ?? 0,
    }

    const nextHistory =
        pipelineHistory.value.length >= PIPELINE_HISTORY_LIMIT
            ? pipelineHistory.value.slice(1)
            : pipelineHistory.value.slice()

    nextHistory.push(nextPoint)
    pipelineHistory.value = nextHistory
}

const normalizeTooltipParams = (rawParams: unknown): TooltipDatum[] => {
    if (Array.isArray(rawParams)) {
        return rawParams as TooltipDatum[]
    }
    return rawParams ? [rawParams as TooltipDatum] : []
}

const chartTooltipFormatter = (rawParams: unknown) => {
    const params = normalizeTooltipParams(rawParams)
    const firstItem = params[0]
    if (!firstItem) {
        return ''
    }

    const lines = [`<div>${firstItem.axisValueLabel ?? ''}</div>`]
    for (const item of params) {
        const value = Array.isArray(item.value) ? item.value[1] : item.value
        const numericValue = typeof value === 'number' ? value : Number(value)
        const color =
            typeof item.color === 'string' && item.color.length > 0 ? item.color : CHART_COLORS.ink

        lines.push(
            `<div><span style="display:inline-block;margin-right:6px;color:${color};">●</span>${item.seriesName ?? '-'}: ${Number.isFinite(numericValue) ? numericValue.toFixed(1) : '-'}ms</div>`,
        )
    }
    return lines.join('')
}

const baseChartSeriesStyle = {
    symbol: 'none',
    animation: false,
}

const createBaseGridConfig = () => ({
    top: 32,
    right: 12,
    bottom: 18,
    left: 44,
})

const createBaseTooltipConfig = () => ({
    trigger: 'axis',
    backgroundColor: CHART_COLORS.tooltipBackground,
    borderColor: CHART_COLORS.ink,
    borderWidth: 1,
    textStyle: {
        color: CHART_COLORS.ink,
        fontFamily: 'monospace',
        fontSize: 11,
    },
    formatter: chartTooltipFormatter,
})

const createBaseXAxisConfig = (data: string[]) => ({
    type: 'category',
    boundaryGap: false,
    data,
    axisLine: {
        lineStyle: {
            color: CHART_COLORS.axisLine,
        },
    },
    axisTick: {
        show: false,
    },
    axisLabel: {
        color: CHART_COLORS.axisText,
        fontFamily: 'monospace',
        fontSize: 9,
        interval: 'auto',
    },
})

const createBaseYAxisConfig = () => ({
    type: 'value',
    splitLine: {
        lineStyle: {
            color: CHART_COLORS.splitLine,
        },
    },
    axisLabel: {
        color: CHART_COLORS.axisText,
        fontFamily: 'monospace',
        fontSize: 9,
    },
})

const createChartOption = (xAxisData: string[], series: Record<string, unknown>[]) => ({
    grid: createBaseGridConfig(),
    tooltip: createBaseTooltipConfig(),
    xAxis: createBaseXAxisConfig(xAxisData),
    yAxis: createBaseYAxisConfig(),
    series,
})

const ntpChartOption = computed(() => {
    const xAxisData = ntpMeasurements.value.map((item) => formatClockTime(item.recordedAtMs))

    return createChartOption(xAxisData, [
        {
            ...baseChartSeriesStyle,
            name: 'Offset',
            type: 'line',
            step: 'end',
            data: ntpMeasurements.value.map((item) => item.offsetMs),
            lineStyle: {
                color: CHART_COLORS.ink,
                width: 1.5,
            },
            itemStyle: {
                color: CHART_COLORS.ink,
            },
            markLine: {
                symbol: 'none',
                label: {
                    show: false,
                },
                lineStyle: {
                    color: CHART_COLORS.zeroLine,
                    type: 'dashed',
                    width: 1,
                },
                data: [{ yAxis: 0 }],
            },
        },
        {
            ...baseChartSeriesStyle,
            name: 'RTT',
            type: 'line',
            step: 'end',
            data: ntpMeasurements.value.map((item) => item.rttMs),
            lineStyle: {
                color: CHART_COLORS.accent,
                width: 1,
                opacity: 0.8,
            },
            itemStyle: {
                color: CHART_COLORS.accent,
            },
        },
    ])
})

const pipelineChartOption = computed(() => {
    const xAxisData = pipelineHistory.value.map((item) => formatClockTime(item.recordedAtMs))

    return createChartOption(xAxisData, [
        {
            ...baseChartSeriesStyle,
            name: 'Schedule Wait',
            type: 'line',
            data: pipelineHistory.value.map((item) => item.waitMs),
            lineStyle: {
                color: CHART_COLORS.ink,
                width: 1,
            },
            areaStyle: {
                color: CHART_COLORS.splitLine,
            },
            itemStyle: {
                color: CHART_COLORS.ink,
            },
            markLine: {
                symbol: 'none',
                label: {
                    show: false,
                },
                lineStyle: {
                    color: CHART_COLORS.accent,
                    type: 'dashed',
                    width: 1,
                    opacity: 0.7,
                },
                data: [{ yAxis: 200 }],
            },
        },
        {
            ...baseChartSeriesStyle,
            name: 'Late Drift',
            type: 'line',
            data: pipelineHistory.value.map((item) => item.lateMs),
            lineStyle: {
                color: CHART_COLORS.accent,
                width: 1,
                opacity: 0.85,
            },
            itemStyle: {
                color: CHART_COLORS.accent,
            },
        },
    ])
})

onMounted(() => {
    pushPipelineSample()
    tickTimer = window.setInterval(() => {
        nowMs.value = nowClientMs()
        pushPipelineSample()
    }, 1_000)
})

onBeforeUnmount(() => {
    if (tickTimer !== null) {
        window.clearInterval(tickTimer)
        tickTimer = null
    }
})
</script>

<template>
    <div
        class="min-h-full bg-dashboard-main text-[#292723] selection:bg-[#C85A3C] selection:text-white"
    >
        <DashboardTopBar />

        <div class="px-6 pt-4 pb-40 md:px-10 xl:px-12">
            <section data-test="debug-panel-overview" class="border-b border-[#DCD9D0] pb-8">
                <div class="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
                    <div>
                        <div
                            class="mb-3 text-[12px] font-medium uppercase tracking-[0.24em] text-[#1A1917]/45"
                        >
                            Playback Sync DEBUG
                        </div>
                        <h1
                            class="font-serif text-[2rem] leading-none text-[#1A1917] md:text-[2.35rem]"
                        >
                            Timing Window Monitor
                        </h1>
                        <p class="mt-3 text-[13px] tracking-[0.14em] text-[#1A1917]/50">
                            STATE:
                            <span data-test="debug-sync-status" class="font-mono text-[#1A1917]">
                                {{ debugSnapshot.syncStatusText }}
                            </span>
                        </p>
                    </div>

                    <div class="flex flex-wrap gap-3 lg:justify-end">
                        <div class="border border-[#DCD9D0] bg-white px-4 py-2.5 text-right">
                            <div class="text-[9px] uppercase tracking-[0.2em] text-[#1A1917]/40">
                                Live Tick
                            </div>
                            <div class="mt-1 font-mono text-base tracking-tight text-[#1A1917]">
                                {{ formatClockTime(nowMs) }}
                            </div>
                        </div>
                        <div class="border border-[#DCD9D0] bg-white px-4 py-2.5 text-right">
                            <div class="text-[9px] uppercase tracking-[0.2em] text-[#1A1917]/40">
                                Device
                            </div>
                            <div
                                data-test="debug-device-id"
                                class="mt-1 max-w-[15rem] truncate font-mono text-base tracking-tight text-[#1A1917]"
                            >
                                {{ debugSnapshot.clientDiagnostics?.deviceId ?? '-' }}
                            </div>
                        </div>
                        <div class="border border-[#DCD9D0] bg-white px-4 py-2.5 text-right">
                            <div class="text-[9px] uppercase tracking-[0.2em] text-[#1A1917]/40">
                                Sync Mode
                            </div>
                            <div class="mt-1 font-mono text-base tracking-tight text-[#1A1917]">
                                {{ debugSnapshot.syncState.toUpperCase() }}
                            </div>
                        </div>
                    </div>
                </div>
            </section>

            <section class="mt-10 grid gap-px border border-[#DCD9D0] bg-[#DCD9D0] xl:grid-cols-2">
                <article class="bg-dashboard-main p-6 md:p-8">
                    <div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
                        <div>
                            <div
                                class="text-[10px] font-medium uppercase tracking-[0.12em] text-[#1A1917]/40"
                            >
                                NTP / 时延表现
                            </div>
                            <h2 class="mt-1 font-serif text-[1.65rem] text-[#1A1917]">
                                Timing Window
                            </h2>
                        </div>
                        <div
                            class="w-max border border-[#1A1917]/10 px-2 py-0.5 font-mono text-[10px] uppercase tracking-[0.18em] text-[#1A1917]/60"
                        >
                            {{ ntpMeasurements.length }} samples
                        </div>
                    </div>

                    <div
                        class="mt-8 grid gap-px border border-[#DCD9D0] bg-[#DCD9D0] md:grid-cols-3"
                    >
                        <div class="bg-white p-3">
                            <div class="text-[9px] uppercase tracking-[0.16em] text-[#1A1917]/40">
                                Offset Avg
                            </div>
                            <div class="mt-1 font-mono text-lg tracking-tighter text-[#1A1917]">
                                {{ formatNumber(offsetAverageMs) }}
                                <span class="ml-1 text-xs text-[#1A1917]/40">ms</span>
                            </div>
                        </div>
                        <div class="bg-white p-3">
                            <div class="text-[9px] uppercase tracking-[0.16em] text-[#1A1917]/40">
                                RTT Avg
                            </div>
                            <div class="mt-1 font-mono text-lg tracking-tighter text-[#1A1917]">
                                {{ formatNumber(rttAverageMs) }}
                                <span class="ml-1 text-xs text-[#1A1917]/40">ms</span>
                            </div>
                        </div>
                        <div class="bg-white p-3">
                            <div class="text-[9px] uppercase tracking-[0.16em] text-[#1A1917]/40">
                                Uncertainty
                            </div>
                            <div
                                data-test="debug-clock-sync-uncertainty"
                                class="mt-1 font-mono text-lg tracking-tighter text-[#1A1917]"
                            >
                                {{ formatMilliseconds(clockSyncUncertaintyMs) }}
                            </div>
                        </div>
                    </div>

                    <div class="relative mt-6 border border-[#DCD9D0] bg-white p-4">
                        <div
                            class="absolute top-3 left-4 z-10 text-[9px] uppercase tracking-[0.18em] text-[#1A1917]/40"
                        >
                            Offset & RTT Variation
                        </div>
                        <div ref="ntpChartViewport" class="h-[180px] w-full">
                            <VChart
                                v-if="ntpChartReady"
                                class="h-full w-full"
                                :option="ntpChartOption"
                                :init-options="ntpChartInitOptions"
                                autoresize
                            />
                        </div>
                    </div>

                    <div class="mt-6 grid gap-4 border-t border-[#DCD9D0] pt-4 md:grid-cols-3">
                        <div>
                            <div class="text-[9px] uppercase tracking-[0.12em] text-[#1A1917]/40">
                                Socket
                            </div>
                            <div class="mt-1 font-mono text-sm text-[#1A1917]">
                                {{ debugSnapshot.clientDiagnostics?.socketState ?? '-' }}
                            </div>
                        </div>
                        <div>
                            <div class="text-[9px] uppercase tracking-[0.12em] text-[#1A1917]/40">
                                Device
                            </div>
                            <div class="mt-1 font-mono text-sm text-[#1A1917]">
                                {{ debugSnapshot.clientDiagnostics?.deviceId ?? '-' }}
                            </div>
                        </div>
                        <div>
                            <div class="text-[9px] uppercase tracking-[0.12em] text-[#1A1917]/40">
                                Last Sync
                            </div>
                            <div
                                data-test="debug-last-response-age"
                                class="mt-1 font-mono text-sm text-[#1A1917]"
                            >
                                {{
                                    formatAge(debugSnapshot.clientDiagnostics?.lastNtpResponseAtMs)
                                }}
                            </div>
                        </div>
                    </div>
                </article>

                <article class="bg-dashboard-main p-6 md:p-8">
                    <div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
                        <div>
                            <div
                                class="text-[10px] font-medium uppercase tracking-[0.12em] text-[#1A1917]/40"
                            >
                                本地音频管线
                            </div>
                            <h2 class="mt-1 font-serif text-[1.65rem] text-[#1A1917]">
                                Pipeline Buffer
                            </h2>
                        </div>
                        <div
                            class="w-max border border-[#1A1917]/10 px-2 py-0.5 font-mono text-[10px] uppercase tracking-[0.18em] text-[#1A1917]/60"
                        >
                            {{ debugSnapshot.isPlaying ? 'PLAYING' : 'PAUSED' }}
                        </div>
                    </div>

                    <div
                        class="mt-8 grid gap-px border border-[#DCD9D0] bg-[#DCD9D0] md:grid-cols-2"
                    >
                        <div class="bg-white p-3">
                            <div class="text-[9px] uppercase tracking-[0.16em] text-[#1A1917]/40">
                                Schedule Wait
                            </div>
                            <div class="mt-1 font-mono text-lg tracking-tighter text-[#1A1917]">
                                {{
                                    formatMilliseconds(debugSnapshot.lastLocalExecution?.waitMs, 0)
                                }}
                            </div>
                        </div>
                        <div class="bg-white p-3">
                            <div class="text-[9px] uppercase tracking-[0.16em] text-[#1A1917]/40">
                                Late / Offset
                            </div>
                            <div class="mt-1 font-mono text-lg tracking-tighter text-[#1A1917]">
                                {{ formatMilliseconds(lastExecutionLateDriftMs) }}
                            </div>
                        </div>
                    </div>

                    <div class="relative mt-6 border border-[#DCD9D0] bg-white p-4">
                        <div
                            class="absolute top-3 left-4 z-10 text-[9px] uppercase tracking-[0.18em] text-[#1A1917]/40"
                        >
                            Execution Wait & Drift
                        </div>
                        <div ref="pipelineChartViewport" class="h-[180px] w-full">
                            <VChart
                                v-if="pipelineChartReady"
                                class="h-full w-full"
                                :option="pipelineChartOption"
                                :init-options="pipelineChartInitOptions"
                                autoresize
                            />
                        </div>
                    </div>

                    <div class="mt-6 grid gap-4 border-t border-[#DCD9D0] pt-4 md:grid-cols-2">
                        <div>
                            <div class="text-[9px] uppercase tracking-[0.12em] text-[#1A1917]/40">
                                Time Current
                            </div>
                            <div class="mt-1 font-mono text-sm text-[#1A1917]">
                                {{ formatSeconds(debugSnapshot.currentTime) }}
                            </div>
                        </div>
                        <div>
                            <div class="text-[9px] uppercase tracking-[0.12em] text-[#1A1917]/40">
                                Time Duration
                            </div>
                            <div class="mt-1 font-mono text-sm text-[#1A1917]">
                                {{ formatSeconds(debugSnapshot.duration) }}
                            </div>
                        </div>
                        <div>
                            <div class="text-[9px] uppercase tracking-[0.12em] text-[#1A1917]/40">
                                Sync Recovery
                            </div>
                            <div class="mt-1 font-mono text-sm text-[#1A1917]">
                                {{ debugSnapshot.awaitingSyncRecovery ? 'pending' : 'idle' }}
                            </div>
                        </div>
                        <div>
                            <div class="text-[9px] uppercase tracking-[0.12em] text-[#1A1917]/40">
                                Audio Unlock
                            </div>
                            <div class="mt-1 font-mono text-sm text-[#1A1917]">
                                {{ debugSnapshot.audioUnlockRequired ? 'required' : 'ready' }}
                            </div>
                        </div>
                    </div>
                </article>

                <article class="bg-dashboard-main p-6 md:p-8 xl:col-span-2">
                    <div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
                        <div>
                            <div
                                class="text-[10px] font-medium uppercase tracking-[0.12em] text-[#1A1917]/40"
                            >
                                协议事件日志
                            </div>
                            <h2 class="mt-1 font-serif text-[1.65rem] text-[#1A1917]">
                                Timeline Stream
                            </h2>
                        </div>
                        <div
                            class="w-max bg-[#1A1917] px-2 py-0.5 font-mono text-[10px] uppercase tracking-[0.18em] text-[#F6F5F2]"
                        >
                            LIVE
                        </div>
                    </div>

                    <div class="mt-6 border border-[#DCD9D0] bg-white">
                        <div
                            class="hidden grid-cols-12 border-b border-[#DCD9D0] bg-[#EBE9E4]/50 px-4 py-2 md:grid"
                        >
                            <div
                                class="col-span-1 text-[9px] font-medium uppercase tracking-[0.12em] text-[#1A1917]/50"
                            >
                                DIR
                            </div>
                            <div
                                class="col-span-3 text-[9px] font-medium uppercase tracking-[0.12em] text-[#1A1917]/50"
                            >
                                EVENT
                            </div>
                            <div
                                class="col-span-6 text-[9px] font-medium uppercase tracking-[0.12em] text-[#1A1917]/50"
                            >
                                PAYLOAD / DETAIL
                            </div>
                            <div
                                class="col-span-2 text-right text-[9px] font-medium uppercase tracking-[0.12em] text-[#1A1917]/50"
                            >
                                AGE
                            </div>
                        </div>

                        <div data-test="debug-panel-events" class="divide-y divide-[#DCD9D0]/60">
                            <template v-if="timelineRows.length > 0">
                                <div
                                    v-for="row in timelineRows"
                                    :key="row.key"
                                    class="grid gap-2 px-4 py-3 md:grid-cols-12 md:items-center"
                                >
                                    <div
                                        class="w-max border-b border-current pb-0.5 font-mono text-[10px] uppercase tracking-[0.1em]"
                                        :class="
                                            row.direction === 'IN'
                                                ? 'text-[#1A1917] md:col-span-1'
                                                : 'text-[#C85A3C] md:col-span-1'
                                        "
                                    >
                                        {{ row.direction }}
                                    </div>
                                    <div class="font-mono text-[12px] text-[#1A1917] md:col-span-3">
                                        {{ row.type }}
                                    </div>
                                    <div
                                        class="truncate pr-4 font-mono text-[11px] text-[#1A1917]/60 md:col-span-6"
                                    >
                                        {{ row.detail }}
                                    </div>
                                    <div
                                        class="font-mono text-[11px] text-[#1A1917]/40 md:col-span-2 md:text-right"
                                    >
                                        {{ row.age }}
                                    </div>
                                </div>
                            </template>
                            <div v-else class="px-4 py-8 text-center text-sm text-[#1A1917]/40">
                                暂无协议事件
                            </div>
                        </div>
                    </div>
                </article>
            </section>

            <section
                data-test="debug-local-execution"
                class="mt-10 grid gap-4 border border-[#DCD9D0] bg-white p-6 md:grid-cols-2 xl:grid-cols-4"
            >
                <div>
                    <div class="text-[9px] uppercase tracking-[0.16em] text-[#1A1917]/40">
                        Track
                    </div>
                    <div class="mt-2 font-serif text-lg text-[#1A1917]">
                        {{ currentTrackLabel }}
                    </div>
                </div>
                <div>
                    <div class="text-[9px] uppercase tracking-[0.16em] text-[#1A1917]/40">
                        Last Execution
                    </div>
                    <div class="mt-2 font-mono text-sm text-[#1A1917]">
                        {{
                            debugSnapshot.lastLocalExecution
                                ? `${debugSnapshot.lastLocalExecution.action} · ${formatMilliseconds(debugSnapshot.lastLocalExecution.waitMs, 0)}`
                                : '-'
                        }}
                    </div>
                </div>
                <div>
                    <div class="text-[9px] uppercase tracking-[0.16em] text-[#1A1917]/40">
                        Recent Samples
                    </div>
                    <div class="mt-2 font-mono text-sm text-[#1A1917]">
                        {{
                            recentMeasurements.length > 0
                                ? recentMeasurements
                                      .map((measurement) =>
                                          formatMilliseconds(measurement.offsetMs),
                                      )
                                      .join(' / ')
                                : '-'
                        }}
                    </div>
                </div>
                <div>
                    <div class="text-[9px] uppercase tracking-[0.16em] text-[#1A1917]/40">
                        Devices / Error
                    </div>
                    <div class="mt-2 truncate font-mono text-sm text-[#1A1917]">
                        {{ lastDeviceSummary }}
                    </div>
                    <div class="mt-1 truncate font-mono text-xs text-[#C85A3C]">
                        {{ lastErrorMessage }}
                    </div>
                </div>
            </section>
        </div>
    </div>
</template>
