<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import { useAudioStore } from '@/stores/audio'
import { nowClientMs } from '@/utils/time'

const audioStore = useAudioStore()
const nowMs = ref(nowClientMs())

let tickTimer: number | null = null

const debugSnapshot = computed(() => audioStore.playbackSyncDebugSnapshot)

const reverseCopy = <T,>(items: readonly T[]) => {
    const reversed: T[] = []
    for (let index = items.length - 1; index >= 0; index -= 1) {
        reversed.push(items[index]!)
    }
    return reversed
}

const recentProtocolEvents = computed(() => {
    const events = debugSnapshot.value.clientDiagnostics?.protocolEvents ?? []
    return reverseCopy(events).slice(0, 5)
})

const recentMeasurements = computed(() => {
    const measurements = debugSnapshot.value.clientDiagnostics?.measurements ?? []
    return reverseCopy(measurements).slice(0, 4)
})

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

    return new Intl.DateTimeFormat('zh-CN', {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false,
    }).format(new Date(value))
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

onMounted(() => {
    tickTimer = window.setInterval(() => {
        nowMs.value = nowClientMs()
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
    <div class="min-h-full bg-[#F7F3EC] text-[#3C342C]">
        <DashboardTopBar />

        <div class="mx-auto w-full max-w-7xl px-6 py-4 pb-40 md:pb-44">
            <header class="mb-4 flex items-center justify-between gap-4">
                <div>
                    <h1 class="font-serif text-2xl text-[#2B221B] tracking-tight">
                        Playback Sync DEBUG
                    </h1>
                    <p class="text-sm text-[#8C857B]">单屏查看当前浏览器的关键同步状态</p>
                </div>
                <div class="rounded-2xl border border-[#E6DED2] bg-white/80 px-4 py-2 shadow-sm">
                    <div class="text-[11px] uppercase tracking-[0.18em] text-[#A19483]">
                        Live Tick
                    </div>
                    <div class="mt-1 font-mono text-sm text-[#5B4D40]">
                        {{ formatClockTime(nowMs) }}
                    </div>
                </div>
            </header>

            <div class="grid gap-4 xl:grid-cols-2">
                <section
                    data-test="debug-panel-overview"
                    class="flex min-h-0 flex-col overflow-hidden rounded-[24px] border border-[#E8DED2] bg-white/90 p-4 shadow-[0_16px_36px_rgba(55,33,8,0.05)] xl:max-h-[23rem]"
                >
                    <div class="flex items-start justify-between gap-4">
                        <div>
                            <div class="text-[11px] uppercase tracking-[0.18em] text-[#A19483]">
                                连接与同步
                            </div>
                            <h2
                                data-test="debug-sync-status"
                                class="mt-1 font-serif text-[1.9rem] leading-none text-[#30251D]"
                            >
                                {{ debugSnapshot.syncStatusText }}
                            </h2>
                        </div>
                        <div
                            class="rounded-full border border-[#E6DED2] bg-[#F8F2EA] px-3 py-1 font-mono text-xs text-[#7C6F62]"
                        >
                            {{ debugSnapshot.syncState }}
                        </div>
                    </div>

                    <div class="mt-3 flex flex-1 flex-col gap-3">
                        <dl class="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
                            <div class="rounded-xl bg-[#FBF7F2] px-3 py-2.5">
                                <dt class="text-xs text-[#9A8E80]">Client Phase</dt>
                                <dd
                                    class="mt-0.5 font-mono text-[15px] leading-tight text-[#4E4034]"
                                >
                                    {{ debugSnapshot.clientDiagnostics?.phase ?? '-' }}
                                </dd>
                            </div>
                            <div class="rounded-xl bg-[#FBF7F2] px-3 py-2.5">
                                <dt class="text-xs text-[#9A8E80]">Socket</dt>
                                <dd
                                    class="mt-0.5 font-mono text-[15px] leading-tight text-[#4E4034]"
                                >
                                    {{ debugSnapshot.clientDiagnostics?.socketState ?? '-' }}
                                </dd>
                            </div>
                            <div class="rounded-xl bg-[#FBF7F2] px-3 py-2.5">
                                <dt class="text-xs text-[#9A8E80]">Device</dt>
                                <dd
                                    data-test="debug-device-id"
                                    class="mt-0.5 truncate font-mono text-[15px] leading-tight text-[#4E4034]"
                                >
                                    {{ debugSnapshot.clientDiagnostics?.deviceId ?? '-' }}
                                </dd>
                            </div>
                            <div class="rounded-xl bg-[#FBF7F2] px-3 py-2.5">
                                <dt class="text-xs text-[#9A8E80]">Realtime Control</dt>
                                <dd
                                    class="mt-0.5 font-mono text-[15px] leading-tight text-[#4E4034]"
                                >
                                    {{
                                        debugSnapshot.canSendRealtimeControl ? 'enabled' : 'waiting'
                                    }}
                                </dd>
                            </div>
                            <div class="rounded-xl bg-[#FBF7F2] px-3 py-2.5">
                                <dt class="text-xs text-[#9A8E80]">Reconnect</dt>
                                <dd
                                    class="mt-0.5 font-mono text-[15px] leading-tight text-[#4E4034]"
                                >
                                    {{ debugSnapshot.clientDiagnostics?.reconnectAttempt ?? 0 }}
                                </dd>
                            </div>
                            <div class="rounded-xl bg-[#FBF7F2] px-3 py-2.5">
                                <dt class="text-xs text-[#9A8E80]">Samples</dt>
                                <dd
                                    class="mt-0.5 font-mono text-[15px] leading-tight text-[#4E4034]"
                                >
                                    {{
                                        debugSnapshot.clientDiagnostics
                                            ? `${debugSnapshot.clientDiagnostics.initialCalibration.sampledCount}/${debugSnapshot.clientDiagnostics.initialCalibration.requiredSampleCount}`
                                            : '-'
                                    }}
                                </dd>
                            </div>
                        </dl>

                        <div
                            class="grid gap-2 rounded-[18px] border border-[#E7DED2] bg-[#F5EEE4] p-3 xl:grid-cols-3"
                        >
                            <div>
                                <div class="text-xs text-[#8F8376]">Last NTP Request</div>
                                <div class="mt-0.5 truncate font-mono text-[13px] text-[#4C4033]">
                                    {{
                                        formatClockTime(
                                            debugSnapshot.clientDiagnostics?.lastNtpRequestAtMs,
                                        )
                                    }}
                                    ·
                                    {{
                                        formatAge(
                                            debugSnapshot.clientDiagnostics?.lastNtpRequestAtMs,
                                        )
                                    }}
                                </div>
                            </div>
                            <div>
                                <div class="text-xs text-[#8F8376]">Last NTP Response</div>
                                <div
                                    data-test="debug-last-response-age"
                                    class="mt-0.5 truncate font-mono text-[13px] text-[#4C4033]"
                                >
                                    {{
                                        formatClockTime(
                                            debugSnapshot.clientDiagnostics?.lastNtpResponseAtMs,
                                        )
                                    }}
                                    ·
                                    {{
                                        formatAge(
                                            debugSnapshot.clientDiagnostics?.lastNtpResponseAtMs,
                                        )
                                    }}
                                </div>
                            </div>
                            <div class="min-w-0 xl:col-span-1">
                                <div class="text-xs text-[#8F8376]">Last Error</div>
                                <div class="mt-0.5 truncate font-mono text-[13px] text-[#8A4F43]">
                                    {{ lastErrorMessage }}
                                </div>
                            </div>
                        </div>
                    </div>
                </section>

                <section
                    data-test="debug-panel-ntp"
                    class="flex min-h-0 flex-col rounded-[24px] border border-[#E8DED2] bg-white/90 p-5 shadow-[0_16px_36px_rgba(55,33,8,0.05)] xl:max-h-[24rem]"
                >
                    <div class="flex items-start justify-between gap-4">
                        <div>
                            <div class="text-[11px] uppercase tracking-[0.18em] text-[#A19483]">
                                NTP / 时延
                            </div>
                            <h2 class="mt-2 font-serif text-2xl text-[#30251D]">Timing Window</h2>
                        </div>
                        <div
                            class="rounded-full border border-[#E4D8C9] bg-[#F8F2EA] px-3 py-1 font-mono text-xs text-[#7C6F62]"
                        >
                            {{ debugSnapshot.clientDiagnostics?.measurements.length ?? 0 }} samples
                        </div>
                    </div>

                    <div class="mt-4 flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto pr-1">
                        <div class="grid gap-3 sm:grid-cols-3">
                            <div class="rounded-2xl bg-[#FBF7F2] px-4 py-3">
                                <div class="text-xs text-[#9A8E80]">Offset</div>
                                <div class="mt-1 font-mono text-lg text-[#4E4034]">
                                    {{ formatMilliseconds(debugSnapshot.clockOffsetMs) }}
                                </div>
                            </div>
                            <div class="rounded-2xl bg-[#FBF7F2] px-4 py-3">
                                <div class="text-xs text-[#9A8E80]">RTT</div>
                                <div class="mt-1 font-mono text-lg text-[#4E4034]">
                                    {{ formatMilliseconds(debugSnapshot.roundTripEstimateMs) }}
                                </div>
                            </div>
                            <div class="rounded-2xl bg-[#FBF7F2] px-4 py-3">
                                <div class="text-xs text-[#9A8E80]">Last Sample</div>
                                <div class="mt-1 font-mono text-lg text-[#4E4034]">
                                    {{
                                        recentMeasurements.length > 0
                                            ? formatMilliseconds(recentMeasurements[0]?.rttMs)
                                            : '-'
                                    }}
                                </div>
                            </div>
                        </div>

                        <div
                            class="overflow-hidden rounded-[20px] border border-[#E7DED2] bg-[#F5EEE4]"
                        >
                            <div
                                class="grid grid-cols-[0.95fr_0.65fr_0.65fr_0.7fr] gap-3 border-b border-[#E3D7C9] px-4 py-2 text-[11px] uppercase tracking-[0.14em] text-[#9A8E80]"
                            >
                                <span>Time</span>
                                <span>Offset</span>
                                <span>RTT</span>
                                <span>Age</span>
                            </div>
                            <template v-if="recentMeasurements.length > 0">
                                <div
                                    v-for="measurement in recentMeasurements"
                                    :key="measurement.recordedAtMs"
                                    class="grid grid-cols-[0.95fr_0.65fr_0.65fr_0.7fr] gap-3 px-4 py-2 font-mono text-sm text-[#55483C] even:bg-white/35"
                                >
                                    <span>{{ formatClockTime(measurement.recordedAtMs) }}</span>
                                    <span>{{ formatMilliseconds(measurement.offsetMs) }}</span>
                                    <span>{{ formatMilliseconds(measurement.rttMs) }}</span>
                                    <span>{{ formatAge(measurement.recordedAtMs) }}</span>
                                </div>
                            </template>
                            <div v-else class="px-4 py-6 text-center text-sm text-[#9A8E80]">
                                暂无 NTP 采样
                            </div>
                        </div>
                    </div>
                </section>

                <section
                    data-test="debug-panel-events"
                    class="flex min-h-0 flex-col rounded-[24px] border border-[#E8DED2] bg-white/90 p-5 shadow-[0_16px_36px_rgba(55,33,8,0.05)]"
                >
                    <div class="flex items-start justify-between gap-4">
                        <div>
                            <div class="text-[11px] uppercase tracking-[0.18em] text-[#A19483]">
                                最近协议事件
                            </div>
                            <h2 class="mt-2 font-serif text-2xl text-[#30251D]">Recent Timeline</h2>
                        </div>
                        <div
                            class="rounded-full border border-[#E4D8C9] bg-[#F8F2EA] px-3 py-1 font-mono text-xs text-[#7C6F62]"
                        >
                            {{ recentProtocolEvents.length }} shown
                        </div>
                    </div>

                    <div class="mt-4 grid gap-2">
                        <template v-if="recentProtocolEvents.length > 0">
                            <article
                                v-for="event in recentProtocolEvents"
                                :key="`${event.direction}-${event.atMs}-${event.type}`"
                                class="grid grid-cols-[auto_1fr_auto] items-center gap-3 rounded-2xl border border-[#EEE5DA] bg-[#FFFCF8] px-4 py-3"
                            >
                                <span
                                    class="rounded-full px-2 py-0.5 text-[11px] font-semibold tracking-wide"
                                    :class="
                                        event.direction === 'in'
                                            ? 'bg-[#EAF2FF] text-[#466A9A]'
                                            : 'bg-[#FBEEDC] text-[#9B6436]'
                                    "
                                >
                                    {{ event.direction }}
                                </span>
                                <div class="min-w-0">
                                    <div class="truncate font-mono text-sm text-[#43372C]">
                                        {{ event.type }}
                                    </div>
                                    <div class="truncate text-xs text-[#8C857B]">
                                        {{ summarizeEventPayload(event.payload) }}
                                    </div>
                                </div>
                                <span class="font-mono text-xs text-[#8C857B]">
                                    {{ formatAge(event.atMs) }}
                                </span>
                            </article>
                        </template>
                        <div
                            v-else
                            class="rounded-2xl border border-dashed border-[#E0D5C7] bg-[#FCF8F3] px-4 py-6 text-center text-sm text-[#9A8E80]"
                        >
                            暂无协议事件
                        </div>
                    </div>
                </section>

                <section
                    data-test="debug-panel-audio"
                    class="flex min-h-0 flex-col rounded-[24px] border border-[#E8DED2] bg-white/90 p-5 shadow-[0_16px_36px_rgba(55,33,8,0.05)]"
                >
                    <div class="flex items-start justify-between gap-4">
                        <div>
                            <div class="text-[11px] uppercase tracking-[0.18em] text-[#A19483]">
                                本地音频执行
                            </div>
                            <h2 class="mt-2 truncate font-serif text-2xl text-[#30251D]">
                                {{ currentTrackLabel }}
                            </h2>
                        </div>
                        <div
                            class="rounded-full border border-[#E4D8C9] bg-[#F8F2EA] px-3 py-1 font-mono text-xs text-[#7C6F62]"
                        >
                            {{ debugSnapshot.isPlaying ? 'PLAYING' : 'PAUSED' }}
                        </div>
                    </div>

                    <dl class="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
                        <div class="rounded-2xl bg-[#FBF7F2] px-4 py-3">
                            <dt class="text-xs text-[#9A8E80]">Current</dt>
                            <dd class="mt-1 font-mono text-sm text-[#4E4034]">
                                {{ formatSeconds(debugSnapshot.currentTime) }}
                            </dd>
                        </div>
                        <div class="rounded-2xl bg-[#FBF7F2] px-4 py-3">
                            <dt class="text-xs text-[#9A8E80]">Duration</dt>
                            <dd class="mt-1 font-mono text-sm text-[#4E4034]">
                                {{ formatSeconds(debugSnapshot.duration) }}
                            </dd>
                        </div>
                        <div class="rounded-2xl bg-[#FBF7F2] px-4 py-3">
                            <dt class="text-xs text-[#9A8E80]">Buffer</dt>
                            <dd class="mt-1 font-mono text-sm text-[#4E4034]">
                                {{
                                    debugSnapshot.currentBuffer
                                        ? `media=${debugSnapshot.currentBuffer.mediaFileId}`
                                        : '-'
                                }}
                            </dd>
                        </div>
                        <div class="rounded-2xl bg-[#FBF7F2] px-4 py-3">
                            <dt class="text-xs text-[#9A8E80]">Queued Play</dt>
                            <dd class="mt-1 font-mono text-sm text-[#4E4034]">
                                {{
                                    debugSnapshot.queuedPlayIntent
                                        ? debugSnapshot.queuedPlayIntent.track.title
                                        : '-'
                                }}
                            </dd>
                        </div>
                        <div class="rounded-2xl bg-[#FBF7F2] px-4 py-3">
                            <dt class="text-xs text-[#9A8E80]">Sync Recovery</dt>
                            <dd class="mt-1 font-mono text-sm text-[#4E4034]">
                                {{ debugSnapshot.awaitingSyncRecovery ? 'pending' : 'idle' }}
                            </dd>
                        </div>
                        <div class="rounded-2xl bg-[#FBF7F2] px-4 py-3">
                            <dt class="text-xs text-[#9A8E80]">Audio Unlock</dt>
                            <dd class="mt-1 font-mono text-sm text-[#4E4034]">
                                {{ debugSnapshot.audioUnlockRequired ? 'required' : 'ready' }}
                            </dd>
                        </div>
                    </dl>

                    <div
                        data-test="debug-local-execution"
                        class="mt-4 grid gap-3 rounded-[20px] border border-[#E7DED2] bg-[#F5EEE4] p-4 sm:grid-cols-2"
                    >
                        <div>
                            <div class="text-xs text-[#8F8376]">Last Execution</div>
                            <div class="mt-1 font-mono text-sm text-[#4C4033]">
                                {{
                                    debugSnapshot.lastLocalExecution
                                        ? `${debugSnapshot.lastLocalExecution.action} · ${formatMilliseconds(debugSnapshot.lastLocalExecution.waitMs, 0)}`
                                        : '-'
                                }}
                            </div>
                        </div>
                        <div>
                            <div class="text-xs text-[#8F8376]">Late / Offset</div>
                            <div class="mt-1 font-mono text-sm text-[#4C4033]">
                                {{
                                    debugSnapshot.lastLocalExecution
                                        ? `${formatMilliseconds(debugSnapshot.lastLocalExecution.lateSeconds * 1_000)} · ${formatSeconds(debugSnapshot.lastLocalExecution.scheduledOffset)}`
                                        : '-'
                                }}
                            </div>
                        </div>
                        <div>
                            <div class="text-xs text-[#8F8376]">Snapshot / Action</div>
                            <div class="mt-1 truncate font-mono text-sm text-[#4C4033]">
                                {{
                                    debugSnapshot.latestSnapshot
                                        ? `v${debugSnapshot.latestSnapshot.version} · ${debugSnapshot.lastScheduledAction?.payload.scheduledAction.action ?? debugSnapshot.latestSnapshot.status}`
                                        : '-'
                                }}
                            </div>
                        </div>
                        <div>
                            <div class="text-xs text-[#8F8376]">Devices</div>
                            <div class="mt-1 truncate font-mono text-sm text-[#4C4033]">
                                {{ lastDeviceSummary }}
                            </div>
                        </div>
                    </div>
                </section>
            </div>
        </div>
    </div>
</template>
