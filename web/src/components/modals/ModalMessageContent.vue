<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useModalContext } from '@/components/modals/modalContext'
import type { ModalTone } from '@/stores/modal'

const { t } = useI18n()

const props = withDefaults(
    defineProps<{
        content: string
        confirmText?: string | undefined
        cancelText?: string | undefined
        mode?: 'alert' | 'confirm'
        tone?: ModalTone
    }>(),
    {
        confirmText: undefined,
        cancelText: undefined,
        mode: 'alert',
        tone: 'default',
    },
)

const modal = useModalContext<boolean | undefined>()

const confirmButtonClass = computed(() =>
    props.tone === 'danger'
        ? 'bg-[#B95D5D] text-[#FAF9F6] hover:bg-[#A84C4C]'
        : 'bg-[#C27E46] text-white hover:bg-[#B06D39]',
)

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
    <div class="space-y-6">
        <p class="font-serif text-base leading-relaxed text-[#5A5A5A]">
            {{ content }}
        </p>

        <div class="grid gap-3" :class="mode === 'confirm' ? 'grid-cols-2' : ''">
            <button
                v-if="mode === 'confirm'"
                type="button"
                class="border border-[#D6D1C4] px-4 py-2.5 text-sm tracking-wide text-[#8A8A8A] transition-colors hover:bg-[#F7F5F0] hover:text-[#5A5A5A]"
                @click="cancel"
            >
                {{ cancelText ?? t('common.cancel') }}
            </button>

            <button
                type="button"
                class="px-4 py-2.5 text-sm tracking-wide transition-colors"
                :class="confirmButtonClass"
                @click="confirm"
            >
                {{ confirmText ?? t('common.confirm') }}
            </button>
        </div>
    </div>
</template>
