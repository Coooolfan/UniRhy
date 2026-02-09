<script setup lang="ts" generic="T extends { id: number }">
const props = withDefaults(
    defineProps<{
        title: string
        summary: string
        items: T[]
        activeId: number | null
        playingId: number | null
        playingRequiresActive?: boolean
    }>(),
    {
        playingRequiresActive: false,
    },
)

const emit = defineEmits<{
    (e: 'item-click', item: T): void
    (e: 'item-double-click', item: T): void
    (e: 'item-keydown', event: KeyboardEvent, item: T): void
}>()

defineSlots<{
    item(props: { item: T; index: number; isActive: boolean; isPlaying: boolean }): unknown
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
            <h3 class="font-serif text-2xl text-[#2C2420]">{{ title }}</h3>
            <div class="text-xs text-[#8C857B] uppercase tracking-widest">
                {{ summary }}
            </div>
        </div>

        <div class="flex flex-col">
            <div
                v-for="(item, index) in items"
                :key="item.id"
                @click="emit('item-click', item)"
                @dblclick="emit('item-double-click', item)"
                @keydown="(event) => emit('item-keydown', event, item)"
                tabindex="0"
                role="button"
                class="group flex items-center gap-6 py-4 px-4 rounded-sm transition-all duration-200 cursor-pointer border-b border-transparent hover:bg-[#F2EFE9]"
                :class="{ 'bg-[#F2EFE9]': isItemActive(item.id) }"
            >
                <div
                    class="w-6 text-center font-serif text-lg"
                    :class="
                        isItemActive(item.id)
                            ? 'text-[#C17D46]'
                            : 'text-[#DCD6CC] group-hover:text-[#8C857B]'
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
