<script setup lang="ts">
import { computed, ref } from 'vue'
import { Loader2, Save } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import type { PluginInfoView } from '@/__generated/model/static/PluginInfoView'
import type { PluginConfigurationResponse } from '@/__generated/model/static/PluginConfigurationResponse'
import PluginConfigurationSection from '@/components/settings/PluginConfigurationSection.vue'
import { parseFormDefinition } from '@/components/tasks/schemaForm'
import { resolveErrorMessage } from '@/i18n/errors'

const props = defineProps<{
    plugin: PluginInfoView
    canManage: boolean
    setEnabled: (id: string, enabled: boolean) => Promise<void>
    updateConcurrency: (id: string, taskType: string, concurrency: number) => Promise<void>
    loadConfiguration: (id: string) => Promise<PluginConfigurationResponse>
    saveConfiguration: (
        id: string,
        values: Record<string, unknown>,
        clearedSecretFields: ReadonlyArray<string>,
    ) => Promise<PluginConfigurationResponse>
}>()

const { t } = useI18n()
const enabled = ref(props.plugin.enabled)
const isToggling = ref(false)
const error = ref('')

/** 并发配置按 taskType 索引 */
const concurrencyDrafts = ref<Record<string, string>>(
    Object.fromEntries(props.plugin.tasks.map((task) => [task.taskType, String(task.concurrency)])),
)
const savedConcurrency = ref<Record<string, number>>(
    Object.fromEntries(props.plugin.tasks.map((task) => [task.taskType, task.concurrency])),
)
const savingTaskType = ref<string | null>(null)

const hasConfiguration = computed(
    () => parseFormDefinition(props.plugin.configDefinition).length > 0,
)

const parsedConcurrency = (taskType: string) => Number(concurrencyDrafts.value[taskType])
const isConcurrencyValid = (taskType: string) => {
    const value = parsedConcurrency(taskType)
    return Number.isInteger(value) && value > 0
}
const canSaveConcurrency = (taskType: string) =>
    props.canManage &&
    isConcurrencyValid(taskType) &&
    parsedConcurrency(taskType) !== savedConcurrency.value[taskType] &&
    savingTaskType.value === null

const handleEnabledChange = async (event: Event) => {
    if (!props.canManage || isToggling.value) return
    const input = event.target as HTMLInputElement
    const previousEnabled = enabled.value
    const nextEnabled = input.checked
    isToggling.value = true
    error.value = ''
    try {
        await props.setEnabled(props.plugin.id, nextEnabled)
        enabled.value = nextEnabled
    } catch (e) {
        enabled.value = previousEnabled
        input.checked = previousEnabled
        error.value = resolveErrorMessage(e)
    } finally {
        isToggling.value = false
    }
}

const handleConcurrencySave = async (taskType: string) => {
    if (!canSaveConcurrency(taskType)) return
    const next = parsedConcurrency(taskType)
    savingTaskType.value = taskType
    error.value = ''
    try {
        await props.updateConcurrency(props.plugin.id, taskType, next)
        savedConcurrency.value = { ...savedConcurrency.value, [taskType]: next }
    } catch (e) {
        error.value = resolveErrorMessage(e)
    } finally {
        savingTaskType.value = null
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
                    <div class="mt-2 text-xs">
                        <span class="font-mono text-[#9C968B]">v{{ plugin.version }}</span>
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
                    </section>

                    <section class="border-b border-[#E8E4D9] pb-5">
                        <span class="mb-3 block text-xs text-[#8A8A8A]">
                            {{ t('plugins.tasks') }}
                        </span>
                        <div class="space-y-4">
                            <div v-for="task in plugin.tasks" :key="task.taskType" class="min-w-0">
                                <div
                                    class="mb-2 flex flex-wrap items-baseline gap-x-2 gap-y-1 text-xs"
                                >
                                    <span class="font-mono break-all text-[#2C2A28]">
                                        {{ task.taskType }}
                                    </span>
                                    <span
                                        :class="
                                            task.userSubmittable
                                                ? 'text-[#5F7350]'
                                                : 'text-[#9C968B]'
                                        "
                                    >
                                        {{
                                            task.userSubmittable
                                                ? t('plugins.entryTask')
                                                : t('plugins.workerTask')
                                        }}
                                    </span>
                                </div>
                                <label
                                    :for="`plugin-concurrency-${task.taskType}`"
                                    class="mb-2 block text-xs text-[#8A8A8A]"
                                >
                                    {{ t('plugins.concurrency') }}
                                </label>
                                <div class="flex items-center gap-2">
                                    <input
                                        :id="`plugin-concurrency-${task.taskType}`"
                                        v-model="concurrencyDrafts[task.taskType]"
                                        :data-testid="`plugin-concurrency-input-${task.taskType}`"
                                        type="number"
                                        min="1"
                                        step="1"
                                        class="min-w-0 flex-1 border-b border-[#D6D1C4] bg-[#F7F5F0] p-2.5 text-sm text-[#2C2A28] outline-none focus:border-[#C27E46] disabled:opacity-60"
                                        :disabled="!canManage || savingTaskType !== null"
                                    />
                                    <button
                                        v-if="canManage"
                                        :data-testid="`plugin-concurrency-save-${task.taskType}`"
                                        type="button"
                                        class="inline-flex min-h-9 shrink-0 items-center gap-1.5 border border-[#C27E46] px-3 py-2 text-xs text-[#C27E46] transition-colors hover:bg-[#C27E46] hover:text-white disabled:cursor-not-allowed disabled:opacity-40"
                                        :disabled="!canSaveConcurrency(task.taskType)"
                                        @click="handleConcurrencySave(task.taskType)"
                                    >
                                        <Loader2
                                            v-if="savingTaskType === task.taskType"
                                            class="h-3.5 w-3.5 animate-spin"
                                        />
                                        <Save v-else class="h-3.5 w-3.5" />
                                        <span>{{ t('common.save') }}</span>
                                    </button>
                                </div>
                            </div>
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
    </div>
</template>
