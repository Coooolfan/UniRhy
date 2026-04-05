import { ref, type Ref } from 'vue'
import type { RecordingEditForm } from '@/components/recording/RecordingEditModal.vue'

export type EditableRecording = {
    id: number
    title: string
    label: string
    comment: string
    type: string
    isDefault: boolean
}

type UseRecordingEditorOptions<T extends EditableRecording> = {
    recordings: Ref<T[]>
    submitUpdate: (recordingId: number, form: RecordingEditForm) => Promise<void>
    applyLocalUpdate: (
        recordings: readonly T[],
        recordingId: number,
        form: RecordingEditForm,
    ) => T[]
    parseError?: (error: unknown) => string
    fallbackErrorMessage?: string
}

const createEmptyForm = (): RecordingEditForm => ({
    title: '',
    label: '',
    comment: '',
    type: '',
    isDefault: false,
})

const normalizeForm = (form: RecordingEditForm): RecordingEditForm => ({
    title: form.title.trim(),
    label: form.label?.trim() || '',
    comment: form.comment?.trim() || '',
    type: form.type.trim(),
    isDefault: form.isDefault,
})

export const useRecordingEditor = <T extends EditableRecording>(
    options: UseRecordingEditorOptions<T>,
) => {
    const isEditRecordingModalOpen = ref(false)
    const isEditingRecording = ref(false)
    const editingRecording = ref<T | null>(null)
    const editRecordingForm = ref<RecordingEditForm>(createEmptyForm())
    const editRecordingError = ref('')

    const resetEditorState = () => {
        isEditRecordingModalOpen.value = false
        editingRecording.value = null
        editRecordingForm.value = createEmptyForm()
        editRecordingError.value = ''
    }

    const openEditRecordingModal = (recording: T) => {
        if (isEditingRecording.value) {
            return
        }

        editingRecording.value = recording
        editRecordingForm.value = {
            title: recording.title,
            label: recording.label,
            comment: recording.comment,
            type: recording.type,
            isDefault: recording.isDefault,
        }
        editRecordingError.value = ''
        isEditRecordingModalOpen.value = true
    }

    const closeEditRecordingModal = () => {
        if (isEditingRecording.value) {
            return
        }

        resetEditorState()
    }

    const updateEditRecordingForm = (value: RecordingEditForm) => {
        editRecordingForm.value = value
    }

    const submitRecordingEdit = async () => {
        if (!editingRecording.value || isEditingRecording.value) {
            return
        }

        const normalizedForm = normalizeForm(editRecordingForm.value)
        if (!normalizedForm.title) {
            editRecordingError.value = '标题不能为空'
            return
        }

        isEditingRecording.value = true
        editRecordingError.value = ''

        try {
            await options.submitUpdate(editingRecording.value.id, normalizedForm)
            options.recordings.value = options.applyLocalUpdate(
                options.recordings.value,
                editingRecording.value.id,
                normalizedForm,
            )
            resetEditorState()
        } catch (error) {
            editRecordingError.value =
                options.parseError?.(error) || options.fallbackErrorMessage || '更新曲目失败'
        } finally {
            isEditingRecording.value = false
        }
    }

    return {
        isEditRecordingModalOpen,
        isEditingRecording,
        editingRecording,
        editRecordingForm,
        editRecordingError,
        openEditRecordingModal,
        closeEditRecordingModal,
        updateEditRecordingForm,
        submitRecordingEdit,
    }
}
