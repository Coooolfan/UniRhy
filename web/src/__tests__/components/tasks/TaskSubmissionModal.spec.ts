import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import TaskSubmissionModal from '@/components/tasks/TaskSubmissionModal.vue'
import type { TaskProviderOption } from '@/composables/useTaskManagement'

const getOptionTexts = (wrapper: Readonly<ReturnType<typeof mountModal>>, dataTest: string) => {
    const options = wrapper.get(dataTest).findAll('option')
    return options.map((option: Readonly<{ text: () => string }>) => option.text())
}

const providerOptions: TaskProviderOption[] = [
    {
        id: 1,
        name: '[本地] Library A',
        type: 'FILE_SYSTEM',
        readonly: false,
    },
    {
        id: 2,
        name: '[本地] Archive B',
        type: 'FILE_SYSTEM',
        readonly: true,
    },
    {
        id: 3,
        name: '[OSS] Bucket C',
        type: 'OSS',
        readonly: false,
    },
]

const mountModal = (overrides: Partial<InstanceType<typeof TaskSubmissionModal>['$props']> = {}) =>
    mount(TaskSubmissionModal, {
        props: {
            open: true,
            providerOptions,
            isLoadingProviders: false,
            isSubmitting: false,
            submitError: '',
            ...overrides,
        },
        global: {
            stubs: {
                teleport: true,
                transition: false,
            },
        },
    })

describe('TaskSubmissionModal', () => {
    it('emits a metadata parse payload with the selected provider', async () => {
        const wrapper = mountModal()

        expect(wrapper.text()).toContain('元数据解析')
        expect(wrapper.text()).toContain('向量化')
        expect(wrapper.text()).toContain('数据清洗')
        expect(wrapper.text()).toContain('歌单生成')
        await wrapper.get('[data-test="task-submit-button"]').trigger('click')

        expect(wrapper.emitted('submit-metadata-parse')).toEqual([
            [
                {
                    providerType: 'FILE_SYSTEM',
                    providerId: 1,
                },
            ],
        ])
    })

    it('limits transcode targets to writable local providers and emits an OPUS payload', async () => {
        const wrapper = mountModal()

        await wrapper.get('[data-test="task-type-transcode"]').trigger('click')

        const sourceOptions = getOptionTexts(wrapper, '[data-test="transcode-source-select"]')
        const destinationOptions = getOptionTexts(
            wrapper,
            '[data-test="transcode-destination-select"]',
        )

        expect(sourceOptions).toEqual(['[本地] Library A', '[本地] Archive B'])
        expect(destinationOptions).toEqual(['[本地] Library A'])

        await wrapper.get('[data-test="transcode-source-select"]').setValue('FILE_SYSTEM:2')
        await wrapper.get('[data-test="task-submit-button"]').trigger('click')

        expect(wrapper.emitted('submit-transcode')).toEqual([
            [
                {
                    srcProviderType: 'FILE_SYSTEM',
                    srcProviderId: 2,
                    dstProviderType: 'FILE_SYSTEM',
                    dstProviderId: 1,
                    targetCodec: 'OPUS',
                },
            ],
        ])
    })

    it('emits a vectorize payload with the selected mode', async () => {
        const wrapper = mountModal()

        await wrapper.get('[data-test="task-type-vectorize"]').trigger('click')
        await wrapper.find('input[value="ALL"]').setValue(true)
        await wrapper.get('[data-test="task-submit-button"]').trigger('click')

        expect(wrapper.emitted('submit-vectorize')).toEqual([
            [
                {
                    mode: 'ALL',
                },
            ],
        ])
    })

    it('emits a data clean event without requiring additional form fields', async () => {
        const wrapper = mountModal()

        await wrapper.get('[data-test="task-type-data_clean"]').trigger('click')

        const submitButton = wrapper.get('[data-test="task-submit-button"]')
        expect(submitButton.attributes('disabled')).toBeUndefined()

        await submitButton.trigger('click')

        expect(wrapper.emitted('submit-data-clean')).toEqual([[]])
    })

    it('emits a playlist generate payload when the description is non-empty', async () => {
        const wrapper = mountModal()

        await wrapper.get('[data-test="task-type-playlist_generate"]').trigger('click')
        await wrapper
            .get('[data-test="playlist-generate-description-input"]')
            .setValue('适合深夜写代码时听的低饱和电子氛围')
        await wrapper.get('[data-test="task-submit-button"]').trigger('click')

        expect(wrapper.emitted('submit-playlist-generate')).toEqual([
            [
                {
                    description: '适合深夜写代码时听的低饱和电子氛围',
                },
            ],
        ])
    })

    it('does not emit playlist generate event when the description is blank', async () => {
        const wrapper = mountModal()

        await wrapper.get('[data-test="task-type-playlist_generate"]').trigger('click')
        await wrapper.get('[data-test="playlist-generate-description-input"]').setValue('   ')

        const submitButton = wrapper.get('[data-test="task-submit-button"]')
        expect(submitButton.attributes('disabled')).toBeDefined()

        await submitButton.trigger('click')

        expect(wrapper.emitted('submit-playlist-generate')).toBeUndefined()
    })
})
