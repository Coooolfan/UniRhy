<script setup lang="ts" generic="T extends { id: number }">
const props = withDefaults(
    defineProps<{
        title: string
        summary?: string
        items: T[]
        activeId: number | null
        playingId: number | null
        playingRequiresActive?: boolean
        selectionStyle?: 'default' | 'ribbon'
        selectedIds?: Set<number>
    }>(),
    {
        playingRequiresActive: false,
        selectionStyle: 'default',
        selectedIds: () => new Set(),
    },
)

const emit = defineEmits<{
    (e: 'item-click', item: T): void
    (e: 'item-double-click', item: T): void
    (e: 'item-keydown', event: KeyboardEvent, item: T): void
    (e: 'item-edit', item: T): void
    (e: 'item-toggle-select', item: T, event: MouseEvent): void
}>()

defineSlots<{
    item(props: { item: T; index: number; isActive: boolean; isPlaying: boolean }): unknown
    empty(): unknown
    actions(): unknown
}>()

const isItemActive = (itemId: number) => {
    return props.activeId === itemId
}

const isItemPlaying = (itemId: number) => {
    if (props.playingId !== itemId) {
        return false
    }
    if (props.playingRequiresActive) {
        return props.activeId === itemId
    }
    return true
}
</script>

<template>
    <div class="bg-[#FDFBF7] rounded-sm shadow-sm p-8 md:p-12 relative">
        <div
            class="absolute -bottom-2 -right-2 w-full h-full bg-[#F5F1EA] rounded-sm -z-10 transform translate-x-1 translate-y-1"
        ></div>

        <div class="flex items-center justify-between mb-8 border-b border-[#EFEBE4] pb-4">
            <div class="flex items-end gap-4">
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
                tabindex="0"
                role="button"
                class="group flex items-center gap-6 py-4 px-4 rounded-sm transition-all duration-200 border-b border-transparent hover:bg-[#F2EFE9] relative overflow-hidden"
                :class="{
                    'bg-[#F2EFE9]': isItemActive(item.id),
                    'cursor-pointer': true,
                }"
            >
                <div
                    v-if="selectionStyle === 'ribbon' && selectedIds?.has(item.id)"
                    class="absolute top-0 left-0 w-0 h-0 border-t-[#FF0000] border-r-transparent pointer-events-none"
                    style="
                        border-top-width: 32px;
                        border-right-width: 32px;
                        border-top-style: solid;
                        border-right-style: solid;
                    "
                ></div>

                <div
                    class="w-6 text-center font-serif text-lg relative z-10 select-none"
                    :class="[
                        isItemActive(item.id)
                            ? 'text-[#C17D46]'
                            : 'text-[#DCD6CC] group-hover:text-[#8C857B]',
                        { 'cursor-pointer': selectionStyle === 'ribbon' },
                    ]"
                    @click.stop="
                        selectionStyle === 'ribbon'
                            ? emit('item-toggle-select', item, $event)
                            : emit('item-click', item)
                    "
                    @dblclick.stop="
                        selectionStyle === 'ribbon' ? undefined : emit('item-double-click', item)
                    "
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
                    :is-active="isItemActive(item.id)"
                    :is-playing="isItemPlaying(item.id)"
                />
            </div>
        </div>
    </div>
</template>
