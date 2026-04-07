<script setup lang="ts" generic="T extends { id: number }">
import { ref } from 'vue'
import type { ReorderPosition } from '@/utils/recordingOrder'

const props = withDefaults(
    defineProps<{
        title: string
        summary?: string
        items: T[]
        playingId: number | null
        enableMultiSelect?: boolean
        selectedIds?: Set<number>
        enableReorder?: boolean
        reorderDisabled?: boolean
    }>(),
    {
        enableMultiSelect: false,
        selectedIds: () => new Set(),
        enableReorder: false,
        reorderDisabled: false,
    },
)

const emit = defineEmits<{
    (e: 'item-click', item: T): void
    (e: 'item-double-click', item: T): void
    (e: 'item-keydown', event: KeyboardEvent, item: T): void
    (e: 'item-edit', item: T): void
    (e: 'item-toggle-select', item: T, event?: MouseEvent): void
    (
        e: 'item-reorder',
        payload: {
            draggedId: number
            targetId: number
            position: ReorderPosition
        },
    ): void
}>()

defineSlots<{
    item(props: { item: T; index: number; isPlaying: boolean }): unknown
    empty(): unknown
    actions(): unknown
}>()

const isItemPlaying = (itemId: number) => {
    return props.playingId === itemId
}

const isItemSelected = (itemId: number) => {
    return props.selectedIds?.has(itemId) ?? false
}

const draggedItemId = ref<number | null>(null)
const dropTargetId = ref<number | null>(null)
const dropPosition = ref<ReorderPosition>('before')

const resetDragState = () => {
    draggedItemId.value = null
    dropTargetId.value = null
    dropPosition.value = 'before'
}

const handleDragStart = (itemId: number, event: DragEvent) => {
    if (!props.enableReorder || props.reorderDisabled) {
        return
    }

    draggedItemId.value = itemId
    if (event.dataTransfer) {
        event.dataTransfer.effectAllowed = 'move'
        event.dataTransfer.setData('text/plain', String(itemId))
        const currentTarget = event.currentTarget
        if (currentTarget instanceof HTMLElement) {
            const row = currentTarget.closest<HTMLElement>('[data-testid="media-list-row"]')
            if (row) {
                event.dataTransfer.setDragImage(row, 24, row.clientHeight / 2)
            }
        }
    }
}

const updateDropState = (itemId: number, event: DragEvent) => {
    if (!props.enableReorder || props.reorderDisabled || draggedItemId.value === null) {
        return
    }
    if (draggedItemId.value === itemId) {
        return
    }

    event.preventDefault()
    const currentTarget = event.currentTarget
    if (!(currentTarget instanceof HTMLElement)) {
        return
    }

    const rect = currentTarget.getBoundingClientRect()
    dropTargetId.value = itemId
    dropPosition.value = event.clientY - rect.top > rect.height / 2 ? 'after' : 'before'

    if (event.dataTransfer) {
        event.dataTransfer.dropEffect = 'move'
    }
}

const handleDrop = (itemId: number, event: DragEvent) => {
    if (!props.enableReorder || props.reorderDisabled || draggedItemId.value === null) {
        return
    }

    event.preventDefault()

    if (draggedItemId.value !== itemId) {
        emit('item-reorder', {
            draggedId: draggedItemId.value,
            targetId: itemId,
            position: dropPosition.value,
        })
    }

    resetDragState()
}
</script>

<template>
    <div class="relative rounded-sm bg-[#FDFBF7] p-5 shadow-sm sm:p-6 md:p-12">
        <div
            class="absolute -bottom-2 -right-2 w-full h-full bg-[#F5F1EA] rounded-sm -z-10 transform translate-x-1 translate-y-1"
        ></div>

        <div
            class="mb-6 flex flex-col gap-3 border-b border-[#EFEBE4] pb-4 sm:mb-8 sm:flex-row sm:items-center sm:justify-between"
        >
            <div class="flex flex-col gap-1 sm:flex-row sm:items-end sm:gap-4">
                <h3 class="font-serif text-2xl text-[#2C2420]">{{ title }}</h3>
                <div
                    v-if="summary"
                    class="text-xs text-[#8C857B] uppercase tracking-widest leading-none mb-1"
                >
                    {{ summary }}
                </div>
            </div>
            <div class="flex items-center gap-2">
                <slot name="actions"></slot>
            </div>
        </div>

        <div class="flex flex-col gap-1">
            <div
                v-if="items.length === 0"
                class="py-12 text-center text-[#8C857B] text-sm font-serif italic"
            >
                <slot name="empty">No items found</slot>
            </div>

            <div
                v-for="(item, index) in items"
                :key="item.id"
                @click="emit('item-click', item)"
                @dblclick="emit('item-double-click', item)"
                @keydown="(event) => emit('item-keydown', event, item)"
                @dragover="updateDropState(item.id, $event)"
                @drop="handleDrop(item.id, $event)"
                tabindex="0"
                role="button"
                data-testid="media-list-row"
                :data-item-id="item.id"
                class="group relative flex items-start gap-4 overflow-visible rounded-sm border-b border-transparent px-3 py-4 transition-all duration-200 hover:bg-[#F2EFE9] sm:px-4 md:items-center md:gap-6"
                :class="{
                    'bg-[#F2EFE9]': enableMultiSelect && isItemSelected(item.id),
                    'cursor-move': enableReorder && !reorderDisabled,
                    'cursor-pointer': !enableReorder || reorderDisabled,
                    'bg-[#F7F2EA]':
                        enableReorder && dropTargetId === item.id && draggedItemId !== item.id,
                    'opacity-75': draggedItemId === item.id,
                }"
            >
                <div
                    v-if="
                        enableReorder &&
                        dropTargetId === item.id &&
                        draggedItemId !== item.id &&
                        dropPosition === 'before'
                    "
                    class="pointer-events-none absolute left-6 right-6 top-0 h-1 -translate-y-1/2 rounded-full bg-[#C17D46]/70"
                ></div>
                <div
                    v-if="
                        enableReorder &&
                        dropTargetId === item.id &&
                        draggedItemId !== item.id &&
                        dropPosition === 'after'
                    "
                    class="pointer-events-none absolute bottom-0 left-6 right-6 h-1 translate-y-1/2 rounded-full bg-[#C17D46]/70"
                ></div>

                <input
                    v-if="enableMultiSelect"
                    type="checkbox"
                    data-testid="recording-select-checkbox"
                    class="sr-only"
                    :checked="isItemSelected(item.id)"
                    @change="emit('item-toggle-select', item)"
                />

                <div
                    v-if="enableMultiSelect && isItemSelected(item.id)"
                    class="pointer-events-none absolute -top-1 left-3 z-20 w-5 drop-shadow-lg"
                >
                    <svg
                        viewBox="0 0 32 64"
                        fill="none"
                        xmlns="http://www.w3.org/2000/svg"
                        class="w-full h-full drop-shadow-xl"
                    >
                        <path d="M0 0H20V36L12 30L0 36V0Z" fill="#d7b472" />
                        <path d="M20 0V6H26Z" fill="#979185" />
                    </svg>
                </div>

                <div
                    class="relative z-10 flex w-5 shrink-0 flex-col items-center justify-center select-none text-center font-serif text-base text-[#DCD6CC] group-hover:text-[#8C857B] sm:w-6 sm:text-lg"
                    :draggable="enableReorder && !reorderDisabled"
                    :class="{
                        'cursor-pointer': enableMultiSelect,
                        'cursor-move': enableReorder && !reorderDisabled,
                    }"
                    @dragstart="handleDragStart(item.id, $event)"
                    @dragend="resetDragState"
                    @click.stop="
                        enableMultiSelect
                            ? emit('item-toggle-select', item, $event)
                            : emit('item-click', item)
                    "
                    @dblclick.stop="enableMultiSelect ? undefined : emit('item-double-click', item)"
                >
                    <div
                        v-if="isItemPlaying(item.id)"
                        class="flex gap-0.5 justify-center h-4 items-end"
                    >
                        <div class="w-0.5 bg-[#C17D46] h-2 animate-pulse"></div>
                        <div class="w-0.5 bg-[#C17D46] h-4 animate-pulse delay-75"></div>
                        <div class="w-0.5 bg-[#C17D46] h-3 animate-pulse delay-150"></div>
                    </div>
                    <span v-else>{{ index + 1 }}</span>
                </div>

                <slot
                    name="item"
                    :item="item"
                    :index="index"
                    :is-playing="isItemPlaying(item.id)"
                />
            </div>
        </div>
    </div>
</template>
