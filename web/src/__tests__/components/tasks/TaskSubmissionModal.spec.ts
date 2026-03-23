import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import TaskSubmissionModal from '@/components/tasks/TaskSubmissionModal.vue'
import type { TaskProviderOption } from '@/composables/useTaskManagement'

const getOptionTexts = (selector: Readonly<ReturnType<typeof mountModal>>, dataTest: string) => {
    const options = selector.get(dataTest).findAll('option')
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

    it('emits a vectorize payload with local provider and required request fields', async () => {
        const wrapper = mountModal()

        await wrapper.get('[data-test="task-type-vectorize"]').trigger('click')

        const sourceOptions = getOptionTexts(wrapper, '[data-test="vectorize-source-select"]')
        expect(sourceOptions).toEqual(['[本地] Library A', '[本地] Archive B'])

        await wrapper.get('[data-test="vectorize-source-select"]').setValue('FILE_SYSTEM:2')
        await wrapper
            .get('[data-test="vectorize-api-endpoint-input"]')
            .setValue('https://api.example.com/v1/embeddings')
        await wrapper.get('[data-test="vectorize-api-key-input"]').setValue('secret-key')
        await wrapper.get('[data-test="vectorize-model-name-input"]').setValue('bge-m3')
        await wrapper.get('[data-test="task-submit-button"]').trigger('click')

        expect(wrapper.emitted('submit-vectorize')).toEqual([
            [
                {
                    srcProviderType: 'FILE_SYSTEM',
                    srcProviderId: 2,
                    apiEndpoint: 'https://api.example.com/v1/embeddings',
                    apiKey: 'secret-key',
                    modelName: 'bge-m3',
                },
            ],
        ])
    })

    it('does not emit vectorize event when any required request field is missing', async () => {
        const wrapper = mountModal()

        await wrapper.get('[data-test="task-type-vectorize"]').trigger('click')
        await wrapper
            .get('[data-test="vectorize-api-endpoint-input"]')
            .setValue('https://api.example.com/v1/embeddings')
        await wrapper.get('[data-test="vectorize-api-key-input"]').setValue('secret-key')

        const submitButton = wrapper.get('[data-test="task-submit-button"]')
        expect(submitButton.attributes('disabled')).toBeDefined()

        await submitButton.trigger('click')

        expect(wrapper.emitted('submit-vectorize')).toBeUndefined()
    })

    it('emits a data clean payload with local provider and required request fields', async () => {
        const wrapper = mountModal()

        await wrapper.get('[data-test="task-type-data_clean"]').trigger('click')

        const sourceOptions = getOptionTexts(wrapper, '[data-test="data-clean-source-select"]')
        expect(sourceOptions).toEqual(['[本地] Library A', '[本地] Archive B'])

        await wrapper.get('[data-test="data-clean-source-select"]').setValue('FILE_SYSTEM:2')
        await wrapper
            .get('[data-test="data-clean-api-endpoint-input"]')
            .setValue('https://api.example.com/v1/responses')
        await wrapper.get('[data-test="data-clean-api-key-input"]').setValue('secret-clean-key')
        await wrapper.get('[data-test="data-clean-model-name-input"]').setValue('gpt-5-mini')
        await wrapper.get('[data-test="task-submit-button"]').trigger('click')

        expect(wrapper.emitted('submit-data-clean')).toEqual([
            [
                {
                    srcProviderType: 'FILE_SYSTEM',
                    srcProviderId: 2,
                    apiEndpoint: 'https://api.example.com/v1/responses',
                    apiKey: 'secret-clean-key',
                    modelName: 'gpt-5-mini',
                },
            ],
        ])
    })

    it('does not emit data clean event when any required request field is missing', async () => {
        const wrapper = mountModal()

        await wrapper.get('[data-test="task-type-data_clean"]').trigger('click')
        await wrapper
            .get('[data-test="data-clean-api-endpoint-input"]')
            .setValue('https://api.example.com/v1/responses')
        await wrapper.get('[data-test="data-clean-api-key-input"]').setValue('secret-clean-key')

        const submitButton = wrapper.get('[data-test="task-submit-button"]')
        expect(submitButton.attributes('disabled')).toBeDefined()

        await submitButton.trigger('click')

        expect(wrapper.emitted('submit-data-clean')).toBeUndefined()
    })
})
