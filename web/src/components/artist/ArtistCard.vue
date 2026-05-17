<script setup lang="ts">
const props = withDefaults(
    defineProps<{
        id: number | string
        title: string
        subtitle: string
        selectable?: boolean
        selected?: boolean
    }>(),
    {
        selectable: false,
        selected: false,
    },
)

const emit = defineEmits<{
    (e: 'open'): void
    (e: 'toggle-select'): void
}>()
</script>

<template>
    <div class="group flex cursor-pointer flex-col items-center text-center" @click="emit('open')">
        <div class="relative mb-4 h-24 w-24 sm:h-32 sm:w-32 md:h-40 md:w-40">
            <div
                class="relative flex aspect-square h-full w-full items-center justify-center overflow-hidden rounded-full bg-[#EFEAE2] text-6xl shadow-md transition-all duration-300 group-hover:scale-105"
                :class="
                    selectable && selected
                        ? 'ring-4 ring-[#C27E46] ring-offset-2 ring-offset-[#F7F5F0]'
                        : ''
                "
            >
                <svg
                    xmlns="http://www.w3.org/2000/svg"
                    viewBox="0 0 400 400"
                    class="h-full w-full rounded-sm"
                >
                    <defs>
                        <path
                            :id="`vinyl-text-path-${id}`"
                            d="M 70, 200 A 130,130 0 1,1 330,200 A 130,130 0 1,1 70,200"
                        />
                    </defs>

                    <rect width="400" height="400" fill="#EAE7E0" />

                    <g stroke="#DFDCD6" stroke-width="1.2" fill="none">
                        <circle cx="200" cy="200" r="180" />
                        <circle cx="200" cy="200" r="155" />
                        <circle cx="200" cy="200" r="105" />
                        <circle cx="200" cy="200" r="80" />
                        <circle cx="200" cy="200" r="55" />
                    </g>

                    <g fill="#D1CECB">
                        <circle cx="200" cy="155" r="48" />
                        <path
                            d="M 80 400 C 80 280, 140 250, 200 250 C 260 250, 320 280, 320 400 Z"
                        />
                    </g>

                    <text
                        class="pointer-events-none select-none"
                        style="user-select: none"
                        font-family="ui-sans-serif, system-ui, -apple-system, sans-serif"
                        font-size="11"
                        font-weight="600"
                        fill="#A8A49C"
                        letter-spacing="6"
                    >
                        <textPath :href="`#vinyl-text-path-${id}`" startOffset="10">
                            UNKNOWN ARTIST • UNKNOWN ARTIST • UNKNOWN ARTIST • UNKNOWN ARTIST •
                            UNKNOWN ARTIST •
                        </textPath>
                    </text>

                    <path
                        d="M280 100 Q 280 115 295 115 Q 280 115 280 130 Q 280 115 265 115 Q 280 115 280 100 Z"
                        fill="#B56E46"
                    />
                    <circle cx="120" cy="280" r="3.5" fill="#B56E46" />

                    <rect
                        x="12"
                        y="12"
                        width="376"
                        height="376"
                        fill="none"
                        stroke="#DFDCD6"
                        stroke-width="1"
                        rx="2"
                    />
                </svg>
            </div>

            <button
                v-if="selectable"
                type="button"
                aria-label="选择艺术家"
                class="absolute left-0 top-0 z-30 h-1/2 w-1/2 cursor-pointer rounded-tl-full"
                @click.stop="emit('toggle-select')"
            ></button>
        </div>

        <h3
            class="mb-1 max-w-full truncate font-serif text-lg leading-tight text-[#1A1A1A] transition-colors group-hover:text-[#C27E46]"
        >
            {{ title }}
        </h3>
        <p class="max-w-full truncate text-xs uppercase tracking-wider text-[#8C857B]">
            {{ subtitle }}
        </p>
    </div>
</template>
