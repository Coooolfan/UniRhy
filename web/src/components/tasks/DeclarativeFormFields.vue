<script setup lang="ts">
import { ChevronDown, RotateCcw, X } from 'lucide-vue-next'
import { isFieldValid, type SchemaField, type SchemaFormValues } from './schemaForm'

const props = withDefaults(
    defineProps<{
        fields: ReadonlyArray<SchemaField>
        modelValue: SchemaFormValues
        configuredSecretFields?: ReadonlySet<string>
        clearedSecretFields?: ReadonlySet<string>
        disabled?: boolean
        columns?: boolean
    }>(),
    {
        configuredSecretFields: () => new Set<string>(),
        clearedSecretFields: () => new Set<string>(),
        disabled: false,
        columns: false,
    },
)

const emit = defineEmits<{
    'update:modelValue': [value: SchemaFormValues]
    'clear-secret': [fieldName: string]
    'restore-secret': [fieldName: string]
}>()

const updateValue = (fieldName: string, value: string | boolean) => {
    emit('update:modelValue', { ...props.modelValue, [fieldName]: value })
}

const inputValue = (event: Event) => (event.target as HTMLInputElement).value
const checkedValue = (event: Event) => (event.target as HTMLInputElement).checked
</script>

<template>
    <div class="grid gap-6" :class="columns ? 'sm:grid-cols-2' : ''">
        <div v-for="field in fields" :key="field.name" class="block">
            <label>
                <span class="mb-2 block text-xs uppercase text-[#8A8A8A]">
                    {{ field.title }}
                    <span v-if="field.required" class="text-[#C27E46]">*</span>
                </span>

                <div v-if="field.type === 'boolean'">
                    <input
                        type="checkbox"
                        class="mt-1"
                        :checked="modelValue[field.name] === true"
                        :disabled="disabled"
                        @change="updateValue(field.name, checkedValue($event))"
                    />
                </div>

                <div v-else-if="field.enum" class="relative">
                    <select
                        class="w-full appearance-none border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 pr-10 text-sm text-[#2C2C2C] outline-none transition-colors focus:border-[#C27E46] disabled:opacity-60"
                        :value="modelValue[field.name]"
                        :disabled="disabled"
                        @change="updateValue(field.name, inputValue($event))"
                    >
                        <option v-if="!field.required" value="">-</option>
                        <option
                            v-for="candidate in field.enum"
                            :key="String(candidate)"
                            :value="String(candidate)"
                        >
                            {{ candidate }}
                        </option>
                    </select>
                    <ChevronDown
                        class="pointer-events-none absolute top-1/2 right-3 h-4 w-4 -translate-y-1/2 text-[#8A8A8A]"
                    />
                </div>

                <div v-else class="relative">
                    <input
                        :type="
                            field.writeOnly
                                ? 'password'
                                : field.type === 'string'
                                  ? 'text'
                                  : 'number'
                        "
                        :min="field.minimum"
                        :max="field.maximum"
                        :step="field.type === 'integer' ? 1 : field.multipleOf"
                        class="w-full border-b bg-[#F7F5F0] p-3 text-sm text-[#2C2C2C] outline-none transition-colors focus:border-[#C27E46] disabled:opacity-60"
                        :class="
                            isFieldValid(field, modelValue[field.name]) ||
                            (field.writeOnly &&
                                configuredSecretFields.has(field.name) &&
                                !clearedSecretFields.has(field.name))
                                ? 'border-[#D6D1C4]'
                                : 'border-rose-300'
                        "
                        :value="modelValue[field.name]"
                        :disabled="disabled"
                        @input="updateValue(field.name, inputValue($event))"
                    />
                    <button
                        v-if="
                            field.writeOnly &&
                            configuredSecretFields.has(field.name) &&
                            !clearedSecretFields.has(field.name)
                        "
                        type="button"
                        class="absolute top-1/2 right-2 -translate-y-1/2 p-1.5 text-[#9C968B] transition-colors hover:text-rose-500"
                        :disabled="disabled"
                        :title="$t('plugins.clearSecret')"
                        @click="emit('clear-secret', field.name)"
                    >
                        <X class="h-4 w-4" />
                    </button>
                    <button
                        v-else-if="field.writeOnly && clearedSecretFields.has(field.name)"
                        type="button"
                        class="absolute top-1/2 right-2 -translate-y-1/2 p-1.5 text-[#9C968B] transition-colors hover:text-[#C27E46]"
                        :disabled="disabled"
                        :title="$t('plugins.restoreSecret')"
                        @click="emit('restore-secret', field.name)"
                    >
                        <RotateCcw class="h-4 w-4" />
                    </button>
                </div>
            </label>

            <p v-if="field.description" class="mt-2 text-xs leading-relaxed text-[#9C968B]">
                {{ field.description }}
            </p>
            <p
                v-if="
                    field.writeOnly &&
                    configuredSecretFields.has(field.name) &&
                    !clearedSecretFields.has(field.name) &&
                    modelValue[field.name] === ''
                "
                class="mt-2 text-xs text-emerald-600"
            >
                {{ $t('plugins.secretConfigured') }}
            </p>
            <p
                v-else-if="field.writeOnly && clearedSecretFields.has(field.name)"
                class="mt-2 text-xs text-rose-600"
            >
                {{ $t('plugins.secretPendingClear') }}
            </p>
        </div>
    </div>
</template>
