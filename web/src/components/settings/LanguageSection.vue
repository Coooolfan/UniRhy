<script setup lang="ts">
import { Languages } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { SUPPORTED_LOCALES, type SupportedLocale } from '@/i18n'
import { useClientPreferencesStore } from '@/stores/clientPreferences'

const { t } = useI18n()
const preferences = useClientPreferencesStore()

const selectLocale = (locale: SupportedLocale) => {
    preferences.setLocale(locale)
}
</script>

<template>
    <section class="animate-in fade-in mb-16 duration-500">
        <h2 class="mb-2 font-serif text-2xl text-[#2C2A28]">{{ t('settings.language.title') }}</h2>
        <div class="mb-6 h-px w-full bg-[#E8E4D9]"></div>

        <div
            class="flex items-center gap-4 rounded-sm border border-[#E0DCD0] bg-[#FCFBF9] p-5 sm:p-6"
        >
            <Languages :size="28" stroke-width="1.5" class="shrink-0 text-[#8A857B]" />
            <div class="flex-1">
                <p class="mb-3 text-sm text-[#66635C]">{{ t('settings.language.description') }}</p>
                <div class="flex gap-2">
                    <button
                        v-for="locale in SUPPORTED_LOCALES"
                        :key="locale"
                        type="button"
                        class="rounded-sm border px-4 py-1.5 text-sm transition-colors"
                        :class="
                            preferences.locale === locale
                                ? 'border-[#B87A5B] bg-[#B87A5B] text-white'
                                : 'border-[#E0DCD0] text-[#66635C] hover:border-[#B87A5B]'
                        "
                        @click="selectLocale(locale)"
                    >
                        {{ t(`settings.language.${locale}`) }}
                    </button>
                </div>
            </div>
        </div>
    </section>
</template>
