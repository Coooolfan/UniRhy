import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import RecordingEditModal, {
    type RecordingEditForm,
    type RecordingPreview,
} from '@/components/recording/RecordingEditModal.vue'
import { modalContextKey, type ModalContext } from '@/components/modals/modalContext'

const buildForm = (): RecordingEditForm => ({
    title: 'Original Title',
    label: 'Original Label',
    comment: 'Original Comment',
    type: 'Studio',
    isDefault: false,
})

const buildRecording = (): RecordingPreview => ({
    cover: '/cover.jpg',
    rawArtists: [
        { id: 1, name: 'Artist A' },
        { id: 2, name: 'Artist B' },
    ],
    assets: [
        {
            mediaFile: {
                id: 100,
                mimeType: 'audio/mpeg',
                objectKey: 'audio/file.mp3',
                ossProvider: { id: 7 },
            },
        },
    ],
})

const resolveMock = vi.fn()
const closeMock = vi.fn()

const modalContext: ModalContext<undefined> = {
    close: () => {
        closeMock()
    },
    resolve: (value) => {
        resolveMock(value)
    },
    isTopmost: true,
}

const mountModal = (overrides: Partial<InstanceType<typeof RecordingEditModal>['$props']> = {}) =>
    mount(RecordingEditModal, {
        props: {
            recording: buildRecording(),
            initialForm: buildForm(),
            onSubmit: vi.fn(),
            showDefaultToggle: false,
            ...overrides,
        },
        global: {
            provide: {
                [modalContextKey]: modalContext,
            },
        },
    })

describe('RecordingEditModal', () => {
    beforeEach(() => {
        resolveMock.mockReset()
        closeMock.mockReset()
    })

    it('renders artists and assets', () => {
        const wrapper = mountModal()

        expect(wrapper.text()).toContain('Artists')
        expect(wrapper.text()).toContain('Artist A')
        expect(wrapper.text()).toContain('file(s) attached')
        expect(wrapper.text()).toContain('audio/file.mp3')
    })

    it('submits the updated form values', async () => {
        const onSubmit = vi.fn()
        const wrapper = mountModal({
            onSubmit,
            showDefaultToggle: true,
        })

        const titleInput = wrapper.find('input[placeholder="Track Title"]')
        await titleInput.setValue('Updated Title')

        const labelInput = wrapper.find('input[placeholder="Optional label"]')
        await labelInput.setValue('Updated Label')

        const commentInput = wrapper.find('textarea[placeholder="Add a comment..."]')
        await commentInput.setValue('Updated Comment')

        const submitButton = wrapper
            .findAll('button')
            .find((button) => button.text().includes('保存更改'))

        expect(submitButton).toBeTruthy()

        await submitButton!.trigger('click')

        expect(onSubmit).toHaveBeenCalledWith({
            title: 'Updated Title',
            label: 'Updated Label',
            comment: 'Updated Comment',
            type: 'Studio',
            isDefault: false,
        })
        expect(resolveMock).toHaveBeenCalledWith(undefined)
    })

    it('toggles default checkbox visibility by showDefaultToggle', async () => {
        const wrapper = mountModal({
            showDefaultToggle: false,
        })

        expect(wrapper.text()).not.toContain('默认版本 (Default Version)')

        await wrapper.setProps({ showDefaultToggle: true })
        expect(wrapper.text()).toContain('默认版本 (Default Version)')
    })

    it('disables actions while submit is in progress and closes when cancelled', async () => {
        const submitState: { resolve: null | (() => void) } = { resolve: null }
        const onSubmit = vi.fn(
            () =>
                new Promise<void>((resolve) => {
                    submitState.resolve = resolve
                }),
        )
        const wrapper = mountModal({
            onSubmit,
            showDefaultToggle: true,
        })

        const cancelButton = wrapper
            .findAll('button')
            .find((button) => button.text().includes('取消'))
        expect(cancelButton).toBeTruthy()

        await cancelButton!.trigger('click')
        expect(closeMock).toHaveBeenCalledTimes(1)

        const submitButton = wrapper
            .findAll('button')
            .find((button) => button.text().includes('保存更改'))
        expect(submitButton).toBeTruthy()

        await submitButton!.trigger('click')
        await flushPromises()

        const buttonsWhenSaving = wrapper.findAll('button')
        buttonsWhenSaving.forEach((button) => {
            expect(button.attributes('disabled')).toBeDefined()
        })

        const finishSubmit = submitState.resolve
        if (typeof finishSubmit !== 'function') {
            throw new TypeError('Expected submit promise resolver to be assigned')
        }
        finishSubmit()
        await flushPromises()
    })
})
