<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Loader2, RotateCcw, Save } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import type { PluginConfigurationResponse, PluginInfoResponse } from '@/__generated/model/static'
import DeclarativeFormFields from '@/components/tasks/DeclarativeFormFields.vue'
import {
    initialFormValues,
    isFormValid,
    isRecord,
    parseFormDefinition,
    toSubmissionParams,
    type SchemaFormValues,
} from '@/components/tasks/schemaForm'
import { resolveErrorMessage } from '@/i18n/errors'

const props = withDefaults(
    defineProps<{
        plugin: PluginInfoResponse
        canManage: boolean
        embedded?: boolean
        loadConfiguration: (id: string) => Promise<PluginConfigurationResponse>
        saveConfiguration: (
            id: string,
            values: Record<string, unknown>,
            clearedSecretFields: ReadonlyArray<string>,
        ) => Promise<PluginConfigurationResponse>
    }>(),
    { embedded: false },
)

const { t } = useI18n()
const fields = computed(() => parseFormDefinition(props.plugin.configDefinition))
const values = ref<SchemaFormValues>({})
const configuredSecrets = ref<Set<string>>(new Set())
const clearedSecrets = ref<Set<string>>(new Set())
const baseline = ref('')
const isLoading = ref(false)
const isSaving = ref(false)
const hasSnapshot = ref(false)
const error = ref('')
/** 声明了默认值但服务端尚未存过的字段：视作待保存，使表单初始即为脏 */
const hasUnsavedDefaults = ref(false)

const visibleConfiguredSecrets = computed(
    () => new Set([...configuredSecrets.value].filter((name) => !clearedSecrets.value.has(name))),
)

const payload = computed(() => toSubmissionParams(fields.value, values.value))
const serializedState = computed(() =>
    JSON.stringify({
        values: payload.value,
        clearedSecretFields: [...clearedSecrets.value].sort(),
    }),
)
const isDirty = computed(() => hasUnsavedDefaults.value || serializedState.value !== baseline.value)
const isValid = computed(() =>
    isFormValid(fields.value, values.value, visibleConfiguredSecrets.value),
)
const canSave = computed(
    () =>
        props.canManage &&
        hasSnapshot.value &&
        isDirty.value &&
        isValid.value &&
        !isLoading.value &&
        !isSaving.value,
)

const applySnapshot = (snapshot: PluginConfigurationResponse) => {
    const source = isRecord(snapshot.values) ? snapshot.values : {}
    values.value = initialFormValues(fields.value, source)
    configuredSecrets.value = new Set(snapshot.configuredSecretFields)
    clearedSecrets.value = new Set()
    hasSnapshot.value = true
    baseline.value = JSON.stringify({
        values: toSubmissionParams(fields.value, values.value),
        clearedSecretFields: [],
    })
    hasUnsavedDefaults.value = fields.value.some(
        (field) => field.default !== undefined && !(field.name in source),
    )
}

const load = async () => {
    if (!props.canManage || fields.value.length === 0) return
    isLoading.value = true
    hasSnapshot.value = false
    error.value = ''
    try {
        applySnapshot(await props.loadConfiguration(props.plugin.id))
    } catch (e) {
        error.value = resolveErrorMessage(e)
    } finally {
        isLoading.value = false
    }
}

const save = async () => {
    if (!canSave.value) return
    isSaving.value = true
    error.value = ''
    try {
        const snapshot = await props.saveConfiguration(props.plugin.id, payload.value, [
            ...clearedSecrets.value,
        ])
        applySnapshot(snapshot)
    } catch (e) {
        error.value = resolveErrorMessage(e)
    } finally {
        isSaving.value = false
    }
}

const clearSecret = (fieldName: string) => {
    clearedSecrets.value = new Set(clearedSecrets.value).add(fieldName)
}

const restoreSecret = (fieldName: string) => {
    const next = new Set(clearedSecrets.value)
    next.delete(fieldName)
    clearedSecrets.value = next
}

watch(
    values,
    (nextValues) => {
        const nextCleared = new Set(clearedSecrets.value)
        let changed = false
        for (const field of fields.value) {
            if (
                field.writeOnly &&
                nextValues[field.name] !== '' &&
                nextCleared.delete(field.name)
            ) {
                changed = true
            }
        }
        if (changed) clearedSecrets.value = nextCleared
    },
    { deep: true },
)

watch(
    () => [props.canManage, props.plugin.version, props.plugin.configDefinition] as const,
    () => void load(),
    { immediate: true },
)
</script>

<template>
    <section
        v-if="fields.length > 0"
        :class="embedded ? '' : 'mt-4 border-t border-[#EBE6D9] pt-4'"
    >
        <div class="mb-4 flex items-center justify-between gap-3">
            <h3
                class="font-medium"
                :class="embedded ? 'text-sm text-[#2C2A28]' : 'text-xs text-[#6B635B]'"
            >
                {{ t('plugins.configuration') }}
            </h3>
            <div v-if="canManage" class="flex items-center gap-1">
                <button
                    type="button"
                    class="p-1.5 text-[#9C968B] transition-colors hover:text-[#C27E46] disabled:opacity-40"
                    :disabled="!isDirty || isLoading || isSaving"
                    :title="t('plugins.resetConfiguration')"
                    @click="void load()"
                >
                    <RotateCcw class="h-4 w-4" />
                </button>
                <button
                    data-test="plugin-config-save"
                    type="button"
                    class="inline-flex min-h-8 items-center gap-1.5 border border-[#C27E46] px-3 py-1.5 text-xs text-[#C27E46] transition-colors hover:bg-[#C27E46] hover:text-white disabled:cursor-not-allowed disabled:opacity-40"
                    :disabled="!canSave"
                    @click="void save()"
                >
                    <Loader2 v-if="isSaving" class="h-3.5 w-3.5 animate-spin" />
                    <Save v-else class="h-3.5 w-3.5" />
                    <span>{{ t('common.save') }}</span>
                </button>
            </div>
        </div>

        <div
            v-if="error"
            class="mb-4 border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700"
        >
            {{ error }}
        </div>

        <div
            v-if="isLoading"
            class="flex min-h-20 items-center justify-center text-xs text-[#6B635B]"
        >
            <Loader2 class="mr-2 h-4 w-4 animate-spin text-[#C27E46]" />
            {{ t('common.loading') }}
        </div>

        <DeclarativeFormFields
            v-else-if="canManage"
            v-model="values"
            :fields="fields"
            :configured-secret-fields="configuredSecrets"
            :cleared-secret-fields="clearedSecrets"
            :disabled="isSaving"
            :columns="embedded"
            @clear-secret="clearSecret"
            @restore-secret="restoreSecret"
        />

        <div v-else class="grid gap-2 sm:grid-cols-2">
            <div
                v-for="field in fields"
                :key="field.name"
                class="flex items-baseline gap-2 text-sm"
            >
                <span class="font-mono text-xs text-[#C27E46]">{{ field.type }}</span>
                <span class="text-[#2C2A28]">{{ field.title }}</span>
                <span v-if="field.writeOnly" class="text-xs text-[#9C968B]">
                    {{ t('plugins.sensitive') }}
                </span>
            </div>
        </div>
    </section>
</template>
