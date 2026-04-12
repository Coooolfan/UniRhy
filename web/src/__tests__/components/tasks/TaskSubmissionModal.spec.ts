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
        isSystemNode: false,
    },
    {
        id: 2,
        name: '[本地] Archive B',
        type: 'FILE_SYSTEM',
        readonly: true,
        isSystemNode: false,
    },
    {
        id: 3,
        name: '[OSS] Bucket C',
        type: 'OSS',
        readonly: false,
        isSystemNode: false,
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

    it('excludes system nodes from metadata parse provider options', () => {
        const optionsWithSystemNode: TaskProviderOption[] = [
            {
                id: 1,
                name: '[本地] Library A',
                type: 'FILE_SYSTEM',
                readonly: false,
                isSystemNode: true,
            },
            {
                id: 2,
                name: '[本地] Archive B',
                type: 'FILE_SYSTEM',
                readonly: true,
                isSystemNode: false,
            },
        ]
        const wrapper = mountModal({ providerOptions: optionsWithSystemNode })

        const options = getOptionTexts(wrapper, '[data-test="metadata-parse-provider-select"]')
        expect(options).toEqual(['[本地] Archive B'])
    })

    it('excludes system nodes from transcode source provider options', async () => {
        const optionsWithSystemNode: TaskProviderOption[] = [
            {
                id: 1,
                name: '[本地] Library A',
                type: 'FILE_SYSTEM',
                readonly: false,
                isSystemNode: true,
            },
            {
                id: 2,
                name: '[本地] Archive B',
                type: 'FILE_SYSTEM',
                readonly: false,
                isSystemNode: false,
            },
        ]
        const wrapper = mountModal({ providerOptions: optionsWithSystemNode })

        await wrapper.get('[data-test="task-type-transcode"]').trigger('click')

        const sourceOptions = getOptionTexts(wrapper, '[data-test="transcode-source-select"]')
        expect(sourceOptions).toEqual(['[本地] Archive B'])
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
})
