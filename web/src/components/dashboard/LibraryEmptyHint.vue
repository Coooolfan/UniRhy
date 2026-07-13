<script setup lang="ts">
import { Disc3, Settings } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'

const { t, tm } = useI18n()

withDefaults(
    defineProps<{
        hasError?: boolean
        title?: string
        description?: string[]
        errorText?: string
        showSettingsButton?: boolean
    }>(),
    {
        title: undefined,
        description: undefined,
        errorText: undefined,
        showSettingsButton: true,
    },
)

const router = useRouter()
const userStore = useUserStore()

const navigateToSettings = () => {
    router.push({ name: 'settings' })
}
</script>

<template>
    <div class="px-2 py-2">
        <div class="w-full max-w-md mx-auto text-center">
            <div
                class="w-20 h-20 rounded-full bg-[#FCFBF9] shadow-sm border border-[#E5DFD3] flex items-center justify-center mx-auto mb-6 relative"
            >
                <Disc3 class="w-10 h-10 text-[#D6CEC0]" />
                <div class="absolute -bottom-1 -right-1 bg-[#F4F2EC] rounded-full p-1">
                    <Settings class="w-5 h-5 text-[#C27E46]" />
                </div>
            </div>

            <h3 class="text-lg font-medium text-[#3D3935] tracking-wider mb-2 font-serif">
                {{ title ?? t('libraryEmpty.albumEmptyTitle') }}
            </h3>
            <p class="text-sm text-[#8A847A] leading-relaxed mb-8">
                <template
                    v-for="(line, i) in description ?? [
                        t('libraryEmpty.albumExplanation1'),
                        t('libraryEmpty.albumExplanation2'),
                    ]"
                    :key="i"
                >
                    <br v-if="i > 0" />{{ line }}
                </template>
            </p>

            <p v-if="hasError" class="text-xs text-[#A17855] mb-4">
                {{ errorText ?? t('libraryEmpty.errorText') }}
            </p>

            <button
                v-if="showSettingsButton && userStore.isAdmin"
                type="button"
                class="px-8 py-3 border border-[#C27E46] text-[#C27E46] text-sm hover:bg-[#C27E46] hover:text-white transition-all duration-500 rounded-sm font-medium tracking-wide uppercase inline-flex items-center gap-2"
                @click="navigateToSettings"
            >
                <Settings :size="16" />
                <span>{{ t('libraryEmpty.goToSettings') }}</span>
            </button>
        </div>
    </div>
</template>
