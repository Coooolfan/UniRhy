<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useModalContext } from '@/components/modals/modalContext'
import type { StorageNodeForm } from '@/composables/useStorageSettings'

const { t } = useI18n()

type SubmitStorageNodeForm = (payload: StorageNodeForm) => Promise<string | null>

const props = defineProps<{
    initialType: StorageNodeForm['type']
    initialName: string
    initialParentPath: string
    initialReadonly: boolean
    initialHost?: string
    initialBucket?: string
    initialAccessKey?: string
    submit: SubmitStorageNodeForm
}>()

const modal = useModalContext<undefined>()
const isSubmitting = ref(false)
const submitError = ref('')
const form = reactive<StorageNodeForm>({
    type: props.initialType,
    name: props.initialName,
    parentPath: props.initialParentPath,
    readonly: props.initialReadonly,
    host: props.initialHost ?? '',
    bucket: props.initialBucket ?? '',
    accessKey: props.initialAccessKey ?? '',
    secretKey: '',
})

const handleCancel = () => {
    if (isSubmitting.value) {
        return
    }

    modal.close()
}

const handleSubmit = async () => {
    if (isSubmitting.value) {
        return
    }

    isSubmitting.value = true
    submitError.value = ''

    try {
        const error = await props.submit({
            type: form.type,
            name: form.name,
            parentPath: form.parentPath,
            readonly: form.readonly,
            host: form.host,
            bucket: form.bucket,
            accessKey: form.accessKey,
            secretKey: form.secretKey,
        })

        if (error) {
            submitError.value = error
            return
        }

        modal.resolve(undefined)
    } finally {
        isSubmitting.value = false
    }
}
</script>

<template>
    <div class="space-y-6">
        <p class="text-xs text-[#8A8A8A] font-serif italic">
            {{ t('storageNodeEdit.scanHint') }}
        </p>

        <div class="space-y-6">
            <div>
                <label
                    class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                >
                    {{ t('storageNodeEdit.nodeType') }}
                </label>
                <select
                    v-model="form.type"
                    data-testid="storage-node-form-type"
                    disabled
                    class="w-full bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#8A8A8A] focus:outline-none font-serif disabled:cursor-not-allowed"
                >
                    <option value="FILE_SYSTEM">{{ t('storageNodeEdit.localFileSystem') }}</option>
                    <option value="OSS">{{ t('storageNodeEdit.objectStorage') }}</option>
                </select>
            </div>

            <div>
                <label
                    class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                >
                    {{ t('storageNodeEdit.nodeName') }}
                </label>
                <input
                    v-model="form.name"
                    data-testid="storage-node-form-name"
                    type="text"
                    :placeholder="t('storageNodeEdit.nodeNamePlaceholder')"
                    class="w-full bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE]"
                />
            </div>

            <div v-if="form.type === 'FILE_SYSTEM'" class="space-y-2">
                <div>
                    <label
                        class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                    >
                        {{ t('storageNodeEdit.storagePath') }}
                    </label>
                    <input
                        v-model="form.parentPath"
                        data-testid="storage-node-form-parent-path"
                        type="text"
                        placeholder="/path/to/dir"
                        class="w-full bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE]"
                    />
                </div>
                <p class="text-xs leading-relaxed text-[#B95D5D] font-serif">
                    {{ t('storageNodeEdit.pathRedirectHint') }}
                    <br />
                    {{ t('storageNodeEdit.pathRedirectWarning') }}
                </p>
            </div>

            <div v-else class="grid gap-5">
                <div>
                    <label
                        class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                    >
                        {{ t('storageNodeEdit.endpoint') }}
                    </label>
                    <input
                        v-model="form.host"
                        data-testid="storage-node-form-host"
                        type="text"
                        placeholder="https://s3.example.com"
                        class="w-full bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE]"
                    />
                </div>
                <div class="grid gap-5 sm:grid-cols-2">
                    <div>
                        <label
                            class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                        >
                            {{ t('storageNodeEdit.bucket') }}
                        </label>
                        <input
                            v-model="form.bucket"
                            data-testid="storage-node-form-bucket"
                            type="text"
                            placeholder="music-library"
                            class="w-full bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE]"
                        />
                    </div>
                    <div>
                        <label
                            class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                        >
                            {{ t('storageNodeEdit.rootPrefix') }}
                        </label>
                        <input
                            v-model="form.parentPath"
                            data-testid="storage-node-form-parent-path"
                            type="text"
                            placeholder="library"
                            class="w-full bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE]"
                        />
                    </div>
                </div>
                <div class="grid gap-5 sm:grid-cols-2">
                    <div>
                        <label
                            class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                        >
                            {{ t('storageNodeEdit.accessKey') }}
                        </label>
                        <input
                            v-model="form.accessKey"
                            data-testid="storage-node-form-access-key"
                            type="text"
                            autocomplete="off"
                            class="w-full bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE]"
                        />
                    </div>
                    <div>
                        <label
                            class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                        >
                            {{ t('storageNodeEdit.secretKey') }}
                        </label>
                        <input
                            v-model="form.secretKey"
                            data-testid="storage-node-form-secret-key"
                            type="password"
                            autocomplete="new-password"
                            :placeholder="t('storageNodeEdit.secretKeyPlaceholder')"
                            class="w-full bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE]"
                        />
                    </div>
                </div>
                <p class="text-xs leading-relaxed text-[#B95D5D] font-serif">
                    {{ t('storageNodeEdit.ossRedirectHint') }}
                    <br />{{ t('storageNodeEdit.ossRedirectWarning') }}
                </p>
            </div>

            <div class="flex flex-col gap-1.5">
                <label class="flex items-center gap-3 cursor-pointer group">
                    <div class="relative flex items-center">
                        <input
                            v-model="form.readonly"
                            data-testid="storage-node-form-readonly"
                            type="checkbox"
                            class="peer sr-only"
                        />
                        <div
                            class="w-9 h-5 bg-[#EAE6DE] peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-[#C67C4E]"
                        ></div>
                    </div>
                    <span
                        class="text-sm text-[#5A5A5A] group-hover:text-[#2B221B] transition-colors"
                    >
                        {{ t('storageNodeEdit.readonlyNode') }}
                    </span>
                </label>
                <p class="text-xs text-[#8A8A8A] leading-relaxed">
                    <span v-if="form.readonly">
                        {{ t('storageNodeEdit.readonlyHint') }}
                    </span>
                    <span v-else>
                        {{ t('storageNodeEdit.systemNodeHint') }}
                    </span>
                </p>
            </div>

            <p
                v-if="submitError"
                data-testid="storage-node-form-error"
                class="text-sm text-[#B95D5D]"
            >
                {{ submitError }}
            </p>

            <div class="flex gap-3 mt-8 pt-6 border-t border-[#EAE6DE]">
                <button
                    type="button"
                    data-testid="storage-node-form-cancel"
                    class="flex-1 px-4 py-2.5 border border-[#D6D1C4] text-[#8A8A8A] hover:bg-[#F7F5F0] hover:text-[#5A5A5A] transition-colors text-sm uppercase tracking-wide"
                    :disabled="isSubmitting"
                    @click="handleCancel"
                >
                    {{ t('common.cancel') }}
                </button>
                <button
                    type="button"
                    data-testid="storage-node-form-submit"
                    class="flex-1 px-4 py-2.5 bg-[#2B221B] text-[#F7F5F0] hover:bg-[#C67C4E] transition-colors text-sm uppercase tracking-wide shadow-md disabled:opacity-60 disabled:cursor-not-allowed"
                    :disabled="isSubmitting"
                    @click="handleSubmit"
                >
                    <span v-if="isSubmitting">{{ t('storageNodeEdit.saving') }}</span>
                    <span v-else>{{ t('common.saveChanges') }}</span>
                </button>
            </div>
        </div>
    </div>
</template>
