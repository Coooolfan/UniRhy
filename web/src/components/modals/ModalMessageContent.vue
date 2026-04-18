<script setup lang="ts">
import { useModalContext } from '@/components/modals/modalContext'

const props = withDefaults(
    defineProps<{
        content: string
        confirmText?: string
        cancelText?: string
        mode?: 'alert' | 'confirm'
    }>(),
    {
        confirmText: '确认',
        cancelText: '取消',
        mode: 'alert',
    },
)

const modal = useModalContext<boolean | undefined>()

const confirm = () => {
    if (props.mode === 'confirm') {
        modal.resolve(true)
        return
    }

    modal.resolve(undefined)
}

const cancel = () => {
    modal.close()
}
</script>

<template>
    <div class="space-y-8">
        <p class="font-serif text-base leading-relaxed text-[#5A5A5A]">
            {{ content }}
        </p>

        <div class="flex items-center gap-4">
            <button
                v-if="mode === 'confirm'"
                type="button"
                class="flex-1 py-3 border border-[#8A8A8A] text-[#5A5A5A] hover:bg-[#EAE6DE] hover:text-[#2B221B] transition-all duration-300 font-serif text-sm tracking-[0.2em] uppercase active:scale-95"
                @click="cancel"
            >
                {{ cancelText }}
            </button>

            <button
                type="button"
                class="flex-1 py-3 border border-[#B95D5D] text-[#B95D5D] hover:bg-[#B95D5D] hover:text-[#FAF9F6] transition-all duration-300 font-serif text-sm tracking-[0.2em] uppercase active:scale-95"
                @click="confirm"
            >
                {{ confirmText }}
            </button>
        </div>
    </div>
</template>
