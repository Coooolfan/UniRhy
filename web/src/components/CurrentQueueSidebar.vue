<script setup lang="ts">
import { computed } from 'vue'
import { formatDurationMs } from '@/composables/recordingMedia'
import { useAudioStore } from '@/stores/audio'
import {
    ArrowDown,
    ArrowUp,
    LoaderCircle,
    Play,
    Trash2,
    ChevronRight,
    ChevronDown,
} from 'lucide-vue-next'

const props = defineProps<{
    expanded: boolean
}>()

const emit = defineEmits<{
    'update:expanded': [value: boolean]
}>()

const audioStore = useAudioStore()

const strategyDisabled = computed(() => {
    return audioStore.queueEntries.length === 0 || !audioStore.canSendRealtimeControl
})

const playbackStrategyOptions = [
    { value: 'SEQUENTIAL', label: '顺序' },
    { value: 'SHUFFLE', label: '乱序' },
    { value: 'RADIO', label: '电台' },
] as const

const stopStrategyOptions = [
    { value: 'TRACK', label: '曲目' },
    { value: 'LIST', label: '列表' },
] as const

const isVisible = computed(
    () => props.expanded && audioStore.queueEntries.length > 0 && !audioStore.isPlayerHidden,
)

const close = () => {
    emit('update:expanded', false)
}

const playQueueEntry = (entryId: number) => {
    void audioStore.playQueueEntry(entryId)
}

const clearQueue = () => {
    void audioStore.clearQueue()
}

const moveQueueEntry = (entryId: number, direction: -1 | 1) => {
    const queueEntries = [...audioStore.queueEntries]
    const currentIndex = queueEntries.findIndex((entry) => entry.entryId === entryId)
    if (currentIndex < 0) {
        return
    }

    const targetIndex = currentIndex + direction
    if (targetIndex < 0 || targetIndex >= queueEntries.length) {
        return
    }

    const [movedEntry] = queueEntries.splice(currentIndex, 1)
    if (!movedEntry) {
        return
    }
    queueEntries.splice(targetIndex, 0, movedEntry)
    void audioStore.reorderQueue(queueEntries.map((entry) => entry.entryId))
}

const removeQueueEntry = (entryId: number) => {
    void audioStore.removeQueueEntry(entryId)
}

const updatePlaybackStrategy = (value: 'SEQUENTIAL' | 'SHUFFLE' | 'RADIO') => {
    void audioStore.updateQueueStrategies(value, audioStore.stopStrategy)
}

const updateStopStrategy = (value: 'TRACK' | 'LIST') => {
    void audioStore.updateQueueStrategies(audioStore.playbackStrategy, value)
}
</script>

<template>
    <Transition
        enter-active-class="transition-all duration-220 ease-out"
        enter-from-class="translate-x-6 opacity-0"
        enter-to-class="translate-x-0 opacity-100"
        leave-active-class="transition-all duration-180 ease-in"
        leave-from-class="translate-x-0 opacity-100"
        leave-to-class="translate-x-6 opacity-0"
    >
        <aside
            v-if="isVisible"
            data-test="queue-sidebar"
            class="fixed bottom-[calc(max(6.25rem,env(safe-area-inset-bottom)+5rem))] right-4 top-24 z-[55] flex w-[23rem] flex-col border border-[#EAE6DE] bg-[#fffcf5] p-3 shadow-[0_8px_30px_rgba(0,0,0,0.12)] backdrop-blur max-md:left-3 max-md:right-3 max-md:top-auto max-md:max-h-[55vh] max-md:w-auto"
        >
            <div
                class="mb-3 flex items-start justify-between gap-3 border-b border-[#EAE6DE] px-1 pb-3"
            >
                <div>
                    <div class="text-[11px] uppercase tracking-[0.22em] text-[#8C857B]">
                        Current Queue
                    </div>
                    <div class="mt-1 text-sm font-medium text-[#3E322B]">
                        {{ audioStore.queueEntries.length }} tracks
                    </div>
                    <div class="mt-3 flex items-center gap-3 text-[11px] text-[#7C7367]">
                        <label class="flex min-w-0 items-center gap-2">
                            <span class="shrink-0 text-[#9A9287]">播放</span>
                            <div class="relative min-w-0">
                                <select
                                    data-test="playback-strategy-select"
                                    :value="audioStore.playbackStrategy"
                                    class="min-w-[5.5rem] appearance-none border-b border-[#D6D1C4] bg-[#F7F5F0] py-1 pr-6 text-[11px] text-[#2C2C2C] outline-none transition-colors focus:border-[#C27E46]"
                                    :disabled="strategyDisabled"
                                    @change="
                                        updatePlaybackStrategy(
                                            ($event.target as HTMLSelectElement).value as
                                                | 'SEQUENTIAL'
                                                | 'SHUFFLE'
                                                | 'RADIO',
                                        )
                                    "
                                >
                                    <option
                                        v-for="option in playbackStrategyOptions"
                                        :key="option.value"
                                        :value="option.value"
                                    >
                                        {{ option.label }}
                                    </option>
                                </select>
                                <ChevronDown
                                    :size="12"
                                    class="pointer-events-none absolute right-0.5 top-1/2 -translate-y-1/2 text-[#8C857B]"
                                />
                            </div>
                        </label>

                        <label class="flex min-w-0 items-center gap-2">
                            <span class="shrink-0 text-[#9A9287]">停止</span>
                            <div class="relative min-w-0">
                                <select
                                    data-test="stop-strategy-select"
                                    :value="audioStore.stopStrategy"
                                    class="min-w-[4.5rem] appearance-none border-b border-[#D6D1C4] bg-[#F7F5F0] py-1 pr-6 text-[11px] text-[#2C2C2C] outline-none transition-colors focus:border-[#C27E46]"
                                    :disabled="strategyDisabled"
                                    @change="
                                        updateStopStrategy(
                                            ($event.target as HTMLSelectElement).value as
                                                | 'TRACK'
                                                | 'LIST',
                                        )
                                    "
                                >
                                    <option
                                        v-for="option in stopStrategyOptions"
                                        :key="option.value"
                                        :value="option.value"
                                    >
                                        {{ option.label }}
                                    </option>
                                </select>
                                <ChevronDown
                                    :size="12"
                                    class="pointer-events-none absolute right-0.5 top-1/2 -translate-y-1/2 text-[#8C857B]"
                                />
                            </div>
                        </label>
                    </div>
                </div>

                <div class="flex items-center gap-1">
                    <button
                        type="button"
                        data-test="clear-queue-button"
                        class="px-2.5 py-1 text-xs text-[#8C857B] transition-colors hover:text-[#B55B4A]"
                        :disabled="!audioStore.canSendRealtimeControl"
                        @click="clearQueue"
                    >
                        清空
                    </button>
                    <button
                        type="button"
                        class="p-1.5 text-[#8C857B] transition-colors hover:text-[#B56C35]"
                        @click="close"
                    >
                        <ChevronRight :size="14" />
                    </button>
                </div>
            </div>

            <div class="min-h-0 space-y-1 overflow-y-auto pr-1">
                <div
                    v-for="(entry, index) in audioStore.queueEntries"
                    :key="entry.entryId"
                    class="group flex items-center gap-3 px-3 py-2 transition-colors"
                    :class="
                        audioStore.currentQueueEntry?.entryId === entry.entryId
                            ? 'bg-[#F8E8D5]'
                            : 'hover:bg-white'
                    "
                >
                    <button
                        type="button"
                        class="flex min-w-0 flex-1 items-center gap-3 text-left"
                        @click="playQueueEntry(entry.entryId)"
                    >
                        <div
                            class="flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-[#E8DED0]"
                        >
                            <img
                                v-if="entry.cover"
                                :src="entry.cover"
                                alt="Queue Cover"
                                class="h-full w-full object-cover"
                            />
                            <Play v-else :size="14" class="text-[#8C857B]" />
                        </div>

                        <div class="min-w-0 flex-1">
                            <div class="flex items-center gap-2">
                                <LoaderCircle
                                    v-if="
                                        audioStore.currentQueueEntry?.entryId === entry.entryId &&
                                        audioStore.isLoading
                                    "
                                    :size="13"
                                    class="animate-spin text-[#C17D46]"
                                />
                                <span
                                    class="truncate text-sm"
                                    :class="
                                        audioStore.currentQueueEntry?.entryId === entry.entryId
                                            ? 'font-semibold text-[#2C2420]'
                                            : 'font-medium text-[#473A32]'
                                    "
                                >
                                    {{ entry.title }}
                                </span>
                            </div>
                            <div class="mt-0.5 truncate text-xs text-[#8C857B]">
                                {{ entry.artist }}
                            </div>
                        </div>
                    </button>

                    <div class="text-xs text-[#8C857B]">
                        {{ formatDurationMs(entry.durationMs) }}
                    </div>

                    <div
                        class="flex items-center gap-1 opacity-100 transition-opacity md:opacity-0 md:group-hover:opacity-100"
                    >
                        <button
                            type="button"
                            class="p-1.5 text-[#8C857B] transition-colors hover:text-[#C17D46]"
                            :disabled="index === 0 || !audioStore.canSendRealtimeControl"
                            @click.stop="moveQueueEntry(entry.entryId, -1)"
                        >
                            <ArrowUp :size="14" />
                        </button>
                        <button
                            type="button"
                            class="p-1.5 text-[#8C857B] transition-colors hover:text-[#C17D46]"
                            :disabled="
                                index === audioStore.queueEntries.length - 1 ||
                                !audioStore.canSendRealtimeControl
                            "
                            @click.stop="moveQueueEntry(entry.entryId, 1)"
                        >
                            <ArrowDown :size="14" />
                        </button>
                        <button
                            type="button"
                            class="p-1.5 text-[#8C857B] transition-colors hover:text-[#B55B4A]"
                            :disabled="!audioStore.canSendRealtimeControl"
                            @click.stop="removeQueueEntry(entry.entryId)"
                        >
                            <Trash2 :size="14" />
                        </button>
                    </div>
                </div>
            </div>
        </aside>
    </Transition>
</template>
