<script setup lang="ts">
import { computed, ref } from 'vue'
import { ChevronDown, Loader2, Save, XCircle } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import type { PluginInfoResponse } from '@/__generated/model/static/PluginInfoResponse'
import type { PluginConfigurationResponse } from '@/__generated/model/static/PluginConfigurationResponse'
import PluginConfigurationSection from '@/components/settings/PluginConfigurationSection.vue'
import { parseFormDefinition } from '@/components/tasks/schemaForm'
import { resolveErrorMessage } from '@/i18n/errors'

const props = defineProps<{
    plugin: PluginInfoResponse
    canManage: boolean
    setEnabled: (id: string, enabled: boolean) => Promise<void>
    updateConcurrency: (id: string, concurrency: number) => Promise<void>
    loadConfiguration: (id: string) => Promise<PluginConfigurationResponse>
    saveConfiguration: (
        id: string,
        values: Record<string, unknown>,
        clearedSecretFields: ReadonlyArray<string>,
    ) => Promise<PluginConfigurationResponse>
}>()

const { t } = useI18n()
const enabled = ref(props.plugin.enabled)
const concurrency = ref(String(props.plugin.concurrency))
const savedConcurrency = ref(props.plugin.concurrency)
const isToggling = ref(false)
const isSavingConcurrency = ref(false)
const isFormParamsExpanded = ref(false)
const error = ref('')

const formFields = computed(() => parseFormDefinition(props.plugin.formDefinition))
const hasConfiguration = computed(
    () => parseFormDefinition(props.plugin.configDefinition).length > 0,
)
const parsedConcurrency = computed(() => Number(concurrency.value))
const isConcurrencyValid = computed(
    () => Number.isInteger(parsedConcurrency.value) && parsedConcurrency.value > 0,
)
const canSaveConcurrency = computed(
    () =>
        props.canManage &&
        isConcurrencyValid.value &&
        parsedConcurrency.value !== savedConcurrency.value &&
        !isSavingConcurrency.value,
)

const handleEnabledChange = async (event: Event) => {
    if (!props.canManage || isToggling.value) return
    const nextEnabled = (event.target as HTMLInputElement).checked
    isToggling.value = true
    error.value = ''
    try {
        await props.setEnabled(props.plugin.id, nextEnabled)
        enabled.value = nextEnabled
    } catch (e) {
        error.value = resolveErrorMessage(e)
    } finally {
        isToggling.value = false
    }
}

const handleConcurrencySave = async () => {
    if (!canSaveConcurrency.value) return
    isSavingConcurrency.value = true
    error.value = ''
    try {
        await props.updateConcurrency(props.plugin.id, parsedConcurrency.value)
        savedConcurrency.value = parsedConcurrency.value
    } catch (e) {
        error.value = resolveErrorMessage(e)
    } finally {
        isSavingConcurrency.value = false
    }
}
</script>

<template>
    <div class="font-serif">
        <div
            v-if="error"
            class="mb-6 border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700"
        >
            {{ error }}
        </div>

        <div
            class="grid gap-8"
            :class="hasConfiguration ? 'lg:grid-cols-[minmax(220px,0.7fr)_minmax(0,1.8fr)]' : ''"
        >
            <aside
                class="min-w-0"
                :class="hasConfiguration ? 'lg:border-r lg:border-[#E8E4D9] lg:pr-8' : ''"
            >
                <div class="mb-7 border-b border-[#E8E4D9] pb-5">
                    <p class="break-all font-mono text-xs text-[#8A8177]">
                        {{ plugin.id }}
                    </p>
                    <div class="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
                        <span class="font-mono text-[#9C968B]">v{{ plugin.version }}</span>
                        <span class="font-mono text-[#8A8177]">{{ plugin.taskType }}</span>
                    </div>
                </div>

                <div class="grid gap-6" :class="hasConfiguration ? '' : 'sm:grid-cols-2'">
                    <section class="border-b border-[#E8E4D9] pb-5">
                        <span class="mb-3 block text-xs text-[#8A8A8A]">
                            {{ t('plugins.enabledState') }}
                        </span>
                        <label
                            class="flex items-center gap-3"
                            :class="canManage ? 'cursor-pointer' : ''"
                        >
                            <span class="relative flex items-center">
                                <input
                                    data-testid="plugin-enabled-toggle"
                                    type="checkbox"
                                    class="peer sr-only"
                                    :checked="enabled"
                                    :disabled="!canManage || isToggling"
                                    @change="handleEnabledChange"
                                />
                                <span
                                    class="h-5 w-9 rounded-full bg-[#E0DCD2] transition-colors after:absolute after:top-0.5 after:left-0.5 after:h-4 after:w-4 after:rounded-full after:border after:border-[#D6D1C4] after:bg-white after:transition-transform after:content-[''] peer-checked:bg-[#C27E46] peer-checked:after:translate-x-4 peer-disabled:opacity-60"
                                ></span>
                            </span>
                            <Loader2
                                v-if="isToggling"
                                class="h-4 w-4 animate-spin text-[#C27E46]"
                            />
                            <span v-else class="text-sm text-[#2C2A28]">
                                {{ enabled ? t('plugins.enabled') : t('plugins.disabled') }}
                            </span>
                        </label>
                        <p
                            v-if="enabled && !plugin.isAvailable"
                            class="mt-3 flex items-center gap-1.5 text-xs text-[#B95D5D]"
                        >
                            <XCircle class="h-3.5 w-3.5 shrink-0" />
                            {{ t('plugins.notLoaded') }}
                        </p>
                    </section>

                    <section class="border-b border-[#E8E4D9] pb-5">
                        <label for="plugin-concurrency" class="mb-3 block text-xs text-[#8A8A8A]">
                            {{ t('plugins.concurrency') }}
                        </label>
                        <div class="flex items-center gap-2">
                            <input
                                id="plugin-concurrency"
                                v-model="concurrency"
                                data-testid="plugin-concurrency-input"
                                type="number"
                                min="1"
                                step="1"
                                class="min-w-0 flex-1 border-b border-[#D6D1C4] bg-[#F7F5F0] p-2.5 text-sm text-[#2C2A28] outline-none focus:border-[#C27E46] disabled:opacity-60"
                                :disabled="!canManage || isSavingConcurrency"
                            />
                            <button
                                v-if="canManage"
                                data-testid="plugin-concurrency-save"
                                type="button"
                                class="inline-flex min-h-9 shrink-0 items-center gap-1.5 border border-[#C27E46] px-3 py-2 text-xs text-[#C27E46] transition-colors hover:bg-[#C27E46] hover:text-white disabled:cursor-not-allowed disabled:opacity-40"
                                :disabled="!canSaveConcurrency"
                                @click="handleConcurrencySave"
                            >
                                <Loader2
                                    v-if="isSavingConcurrency"
                                    class="h-3.5 w-3.5 animate-spin"
                                />
                                <Save v-else class="h-3.5 w-3.5" />
                                <span>{{ t('common.save') }}</span>
                            </button>
                        </div>
                    </section>
                </div>
            </aside>

            <div v-if="hasConfiguration" class="min-w-0">
                <PluginConfigurationSection
                    embedded
                    :plugin="plugin"
                    :can-manage="canManage"
                    :load-configuration="loadConfiguration"
                    :save-configuration="saveConfiguration"
                />
            </div>
        </div>

        <section v-if="formFields.length > 0" class="mt-8 border-t border-[#E8E4D9] pt-1">
            <button
                data-testid="plugin-form-params-toggle"
                type="button"
                class="flex w-full items-center justify-between gap-3 py-3 text-left text-sm font-medium text-[#2C2A28] transition-colors hover:text-[#C27E46]"
                :aria-expanded="isFormParamsExpanded"
                @click="isFormParamsExpanded = !isFormParamsExpanded"
            >
                <span>{{ t('plugins.formParams') }}</span>
                <ChevronDown
                    class="h-4 w-4 shrink-0 transition-transform"
                    :class="isFormParamsExpanded ? 'rotate-180' : ''"
                />
            </button>
            <div v-if="isFormParamsExpanded" class="grid gap-x-6 gap-y-4 pt-2 sm:grid-cols-2">
                <div v-for="field in formFields" :key="field.name" class="min-w-0">
                    <div class="flex flex-wrap items-baseline gap-2 text-sm">
                        <span class="font-mono text-xs text-[#C27E46]">{{ field.type }}</span>
                        <span class="text-[#2C2A28]">
                            {{ field.title }}
                            <span v-if="field.required" class="text-[#C27E46]">*</span>
                        </span>
                        <span v-if="field.default !== undefined" class="text-xs text-[#9C968B]">
                            {{ t('plugins.default', { value: field.default }) }}
                        </span>
                    </div>
                    <p v-if="field.description" class="mt-1 text-xs leading-relaxed text-[#9C968B]">
                        {{ field.description }}
                    </p>
                </div>
            </div>
        </section>
    </div>
</template>
