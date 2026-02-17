import { computed, ref, type Ref } from 'vue'

export type MergeOption = {
    id: number
    title: string
    subtitle: string
}

export type UseRecordingMergeStateOptions<T extends { id: number }> = {
    recordings: Ref<T[]>
    buildOption: (recording: T) => MergeOption
    mergeRequest: (options: { targetId: number; sourceIds: number[] }) => Promise<void>
    parseError?: (error: unknown) => string
    fallbackErrorMessage?: string
}

export const useRecordingMergeState = <T extends { id: number }>(
    options: UseRecordingMergeStateOptions<T>,
) => {
    const selectedIds = ref<Set<number>>(new Set())
    const lastSelectedId = ref<number | null>(null)
    const mergeModalOpen = ref(false)
    const mergeTargetId = ref<number | null>(null)
    const mergeModalError = ref('')
    const mergeSubmitting = ref(false)

    const selectedOptions = computed<MergeOption[]>(() =>
        options.recordings.value
            .filter((recording) => selectedIds.value.has(recording.id))
            .map((recording) => options.buildOption(recording)),
    )

    const hasEnoughSelectedItems = computed(() => selectedOptions.value.length >= 2)
    const canSubmitMerge = computed(
        () =>
            !mergeSubmitting.value &&
            selectedOptions.value.length >= 2 &&
            mergeTargetId.value !== null,
    )

    const resetState = () => {
        selectedIds.value = new Set()
        lastSelectedId.value = null
        mergeModalOpen.value = false
        mergeTargetId.value = null
        mergeModalError.value = ''
        mergeSubmitting.value = false
    }

    const isSelected = (recording: T) => selectedIds.value.has(recording.id)

    const toggleSelection = (recording: T, event?: MouseEvent) => {
        const nextSelected = new Set(selectedIds.value)

        if (event?.shiftKey && lastSelectedId.value !== null) {
            const lastIndex = options.recordings.value.findIndex(
                (r) => r.id === lastSelectedId.value,
            )
            const currentIndex = options.recordings.value.findIndex((r) => r.id === recording.id)

            if (lastIndex !== -1 && currentIndex !== -1) {
                const start = Math.min(lastIndex, currentIndex)
                const end = Math.max(lastIndex, currentIndex)

                for (let index = start; index <= end; index++) {
                    const current = options.recordings.value[index]
                    if (current) {
                        nextSelected.add(current.id)
                    }
                }
            }
        } else {
            if (nextSelected.has(recording.id)) {
                nextSelected.delete(recording.id)
            } else {
                nextSelected.add(recording.id)
            }
            lastSelectedId.value = recording.id
        }

        selectedIds.value = nextSelected
    }

    const openMergeModal = () => {
        if (!hasEnoughSelectedItems.value) {
            return
        }
        mergeModalError.value = ''
        mergeTargetId.value = selectedOptions.value[0]?.id ?? null
        mergeModalOpen.value = true
    }

    const closeMergeModal = () => {
        if (mergeSubmitting.value) {
            return
        }
        mergeModalOpen.value = false
        mergeModalError.value = ''
        mergeTargetId.value = null
    }

    const submitMerge = async () => {
        if (selectedOptions.value.length < 2) {
            mergeModalError.value = '至少选择 2 条录音后才能合并。'
            return
        }

        if (mergeTargetId.value === null) {
            mergeModalError.value = '请选择一个目标录音。'
            return
        }

        const sourceIds = selectedOptions.value
            .map((recording) => recording.id)
            .filter((id) => id !== mergeTargetId.value)

        if (sourceIds.length === 0) {
            mergeModalError.value = '请选择至少一条来源录音。'
            return
        }

        mergeSubmitting.value = true
        mergeModalError.value = ''

        try {
            await options.mergeRequest({
                targetId: mergeTargetId.value,
                sourceIds,
            })
            resetState()
        } catch (error) {
            const message = options.parseError?.(error)
            mergeModalError.value = message || options.fallbackErrorMessage || '合并录音失败'
        } finally {
            mergeSubmitting.value = false
        }
    }

    return {
        selectedIds,
        selectedOptions,
        hasEnoughSelectedItems,
        canSubmitMerge,
        mergeModalOpen,
        mergeTargetId,
        mergeModalError,
        mergeSubmitting,
        isSelected,
        toggleSelection,
        openMergeModal,
        closeMergeModal,
        submitMerge,
        resetState,
    }
}
