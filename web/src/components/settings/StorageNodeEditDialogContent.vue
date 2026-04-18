<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useModalContext } from '@/components/modals/modalContext'
import type { StorageNodeForm } from '@/composables/useStorageSettings'

type SubmitStorageNodeForm = (payload: StorageNodeForm) => Promise<string | null>

const props = defineProps<{
    initialName: string
    initialParentPath: string
    initialReadonly: boolean
    submit: SubmitStorageNodeForm
}>()

const modal = useModalContext<undefined>()
const isSubmitting = ref(false)
const submitError = ref('')
const form = reactive<StorageNodeForm>({
    name: props.initialName,
    parentPath: props.initialParentPath,
    readonly: props.initialReadonly,
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
            name: form.name,
            parentPath: form.parentPath,
            readonly: form.readonly,
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
            UniRhy 可以扫描其中的媒体文件并管理、索引其中的内容
        </p>

        <div class="space-y-6">
            <div>
                <label
                    class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                >
                    节点名称
                </label>
                <input
                    v-model="form.name"
                    data-testid="storage-node-form-name"
                    type="text"
                    placeholder="专辑刻录"
                    class="w-full bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE]"
                />
            </div>

            <div class="space-y-2">
                <div>
                    <label
                        class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                    >
                        存储节点根路径
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
                    修改存储路径根节点会导致此存储节点下的所有资产被重定向。
                    <br/>
                    除非您理解这意味着什么，否则不要更改此项
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
                        只读节点
                    </span>
                </label>
                <p class="text-xs text-[#8A8A8A] leading-relaxed">
                    <span v-if="form.readonly">
                        UniRhy 仅扫描此节点中的媒体文件，不会对其进行任何写入或修改操作
                    </span>
                    <span v-else>
                        将此节点配置为系统节点后，UniRhy 将会在此节点中写入缓存、元数据等文件
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
                    取消
                </button>
                <button
                    type="button"
                    data-testid="storage-node-form-submit"
                    class="flex-1 px-4 py-2.5 bg-[#2B221B] text-[#F7F5F0] hover:bg-[#C67C4E] transition-colors text-sm uppercase tracking-wide shadow-md disabled:opacity-60 disabled:cursor-not-allowed"
                    :disabled="isSubmitting"
                    @click="handleSubmit"
                >
                    <span v-if="isSubmitting">正在保存...</span>
                    <span v-else>保存更改</span>
                </button>
            </div>
        </div>
    </div>
</template>
