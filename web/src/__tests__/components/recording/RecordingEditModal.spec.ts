import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import RecordingEditModal, {
    type RecordingEditForm,
    type RecordingPreview,
} from '@/components/recording/RecordingEditModal.vue'

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

describe('RecordingEditModal', () => {
    it('renders artists and assets when open', () => {
        const wrapper = mount(RecordingEditModal, {
            props: {
                open: true,
                recording: buildRecording(),
                form: buildForm(),
                error: '',
                isSaving: false,
                showDefaultToggle: false,
            },
            global: {
                stubs: {
                    teleport: true,
                    transition: false,
                },
            },
        })

        expect(wrapper.text()).toContain('Artists')
        expect(wrapper.text()).toContain('Artist A')
        expect(wrapper.text()).toContain('file(s) attached')
        expect(wrapper.text()).toContain('audio/file.mp3')
    })

    it('emits update:form when form fields change', async () => {
        const wrapper = mount(RecordingEditModal, {
            props: {
                open: true,
                recording: buildRecording(),
                form: buildForm(),
                error: '',
                isSaving: false,
                showDefaultToggle: true,
            },
            global: {
                stubs: {
                    teleport: true,
                    transition: false,
                },
            },
        })

        const titleInput = wrapper.find('input[placeholder="Recording Title"]')
        await titleInput.setValue('Updated Title')

        const events = wrapper.emitted('update:form')
        expect(events).toBeTruthy()
        expect(events?.[0]?.[0]).toMatchObject({ title: 'Updated Title' })
    })

    it('toggles default checkbox visibility by showDefaultToggle', async () => {
        const wrapper = mount(RecordingEditModal, {
            props: {
                open: true,
                recording: buildRecording(),
                form: buildForm(),
                error: '',
                isSaving: false,
                showDefaultToggle: false,
            },
            global: {
                stubs: {
                    teleport: true,
                    transition: false,
                },
            },
        })

        expect(wrapper.text()).not.toContain('默认版本 (Default Version)')

        await wrapper.setProps({ showDefaultToggle: true })
        expect(wrapper.text()).toContain('默认版本 (Default Version)')
    })

    it('disables actions during save and emits close/submit when enabled', async () => {
        const wrapper = mount(RecordingEditModal, {
            props: {
                open: true,
                recording: buildRecording(),
                form: buildForm(),
                error: '',
                isSaving: true,
                showDefaultToggle: true,
            },
            global: {
                stubs: {
                    teleport: true,
                    transition: false,
                },
            },
        })

        const buttonsWhenSaving = wrapper.findAll('button')
        expect(buttonsWhenSaving.length).toBeGreaterThan(0)
        buttonsWhenSaving.forEach((button) => {
            expect(button.attributes('disabled')).toBeDefined()
        })

        await wrapper.setProps({ isSaving: false })

        const buttons = wrapper.findAll('button')
        const cancelButton = buttons.find((button) => button.text().includes('取消'))
        const submitButton = buttons.find((button) => button.text().includes('保存更改'))

        expect(cancelButton).toBeTruthy()
        expect(submitButton).toBeTruthy()

        await cancelButton!.trigger('click')
        await submitButton!.trigger('click')

        expect(wrapper.emitted('close')).toHaveLength(1)
        expect(wrapper.emitted('submit')).toHaveLength(1)
    })
})
