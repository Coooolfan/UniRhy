import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import TaskSubmissionModal from '@/components/tasks/TaskSubmissionModal.vue'
import type { TaskProviderOption } from '@/composables/useTaskManagement'

const providerOptions: TaskProviderOption[] = [
    {
        id: 1,
        name: '[本地] Library A',
        type: 'FILE_SYSTEM',
    },
    {
        id: 2,
        name: '[OSS] Archive B',
        type: 'OSS',
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
    it('emits a scan payload with the selected provider', async () => {
        const wrapper = mountModal()

        expect(wrapper.text()).toContain('媒体库扫描')

        await wrapper.get('[data-test="scan-provider-select"]').setValue('OSS:2')
        await wrapper.get('[data-test="task-submit-button"]').trigger('click')

        expect(wrapper.emitted('submit-scan')).toEqual([
            [
                {
                    providerType: 'OSS',
                    providerId: 2,
                },
            ],
        ])
    })

    it('switches to transcode mode and emits a transcode payload', async () => {
        const wrapper = mountModal()

        await wrapper.get('[data-test="task-type-transcode"]').trigger('click')
        await wrapper.get('[data-test="transcode-source-select"]').setValue('FILE_SYSTEM:1')
        await wrapper.get('[data-test="transcode-destination-select"]').setValue('OSS:2')
        await wrapper.get('[data-test="target-codec-select"]').setValue('AAC')
        await wrapper.get('[data-test="task-submit-button"]').trigger('click')

        expect(wrapper.emitted('submit-transcode')).toEqual([
            [
                {
                    srcProviderType: 'FILE_SYSTEM',
                    srcProviderId: 1,
                    dstProviderType: 'OSS',
                    dstProviderId: 2,
                    targetCodec: 'AAC',
                },
            ],
        ])
    })
})
