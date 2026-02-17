<script setup lang="ts" generic="T extends { id: number }">
const props = withDefaults(
    defineProps<{
        title: string
        summary?: string
        items: T[]
        playingId: number | null
        enableMultiSelect?: boolean
        selectedIds?: Set<number>
    }>(),
    {
        enableMultiSelect: false,
        selectedIds: () => new Set(),
    },
)

const emit = defineEmits<{
    (e: 'item-click', item: T): void
    (e: 'item-double-click', item: T): void
    (e: 'item-keydown', event: KeyboardEvent, item: T): void
    (e: 'item-edit', item: T): void
    (e: 'item-toggle-select', item: T, event?: MouseEvent): void
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
                class="group flex items-center gap-6 py-4 px-4 rounded-sm transition-all duration-200 border-b border-transparent hover:bg-[#F2EFE9] relative overflow-visible"
                :class="{
                    'bg-[#F2EFE9]': enableMultiSelect && isItemSelected(item.id),
                    'cursor-pointer': true,
                }"
            >
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
                    class="absolute -top-1 left-3 z-20 w-5 drop-shadow-lg pointer-events-none"
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
                    class="w-6 text-center font-serif text-lg relative z-10 select-none text-[#DCD6CC] group-hover:text-[#8C857B]"
                    :class="{ 'cursor-pointer': enableMultiSelect }"
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
