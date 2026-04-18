import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import TaskSubmissionModal from '@/components/tasks/TaskSubmissionModal.vue'
import { modalContextKey, type ModalContext } from '@/components/modals/modalContext'
import type { TaskProviderOption } from '@/composables/useTaskManagement'

vi.mock('vue-router', () => ({
    useRouter: () => ({
        push: vi.fn(),
    }),
}))

const getOptionTexts = (
    selector: Readonly<Awaited<ReturnType<typeof mountModal>>>,
    dataTest: string,
) => {
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

const submitMetadataParseMock = vi.fn()
const submitTranscodeMock = vi.fn()
const resolveMock = vi.fn()
const closeMock = vi.fn()

const modalContext: ModalContext<boolean> = {
    close: () => {
        closeMock()
    },
    resolve: (value) => {
        resolveMock(value)
    },
    isTopmost: true,
}

const mountModal = async (
    overrides: Partial<InstanceType<typeof TaskSubmissionModal>['$props']> = {},
) => {
    const wrapper = mount(TaskSubmissionModal, {
        props: {
            loadProviders: () => Promise.resolve(providerOptions),
            submitMetadataParse: submitMetadataParseMock,
            submitTranscode: submitTranscodeMock,
            ...overrides,
        },
        global: {
            provide: {
                [modalContextKey]: modalContext,
            },
        },
    })

    await flushPromises()

    return wrapper
}

describe('TaskSubmissionModal', () => {
    beforeEach(() => {
        submitMetadataParseMock.mockReset()
        submitTranscodeMock.mockReset()
        resolveMock.mockReset()
        closeMock.mockReset()
    })

    it('submits a metadata parse payload with the selected provider', async () => {
        const wrapper = await mountModal()

        expect(wrapper.text()).toContain('元数据解析')
        await wrapper.get('[data-test="task-submit-button"]').trigger('click')

        expect(submitMetadataParseMock).toHaveBeenCalledWith({
            providerType: 'FILE_SYSTEM',
            providerId: 1,
        })
        expect(resolveMock).toHaveBeenCalledWith(true)
    })

    it('excludes system nodes from metadata parse provider options', async () => {
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
        const wrapper = await mountModal({
            loadProviders: () => Promise.resolve(optionsWithSystemNode),
        })

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
        const wrapper = await mountModal({
            loadProviders: () => Promise.resolve(optionsWithSystemNode),
        })

        await wrapper.get('[data-test="task-type-transcode"]').trigger('click')

        const sourceOptions = getOptionTexts(wrapper, '[data-test="transcode-source-select"]')
        expect(sourceOptions).toEqual(['[本地] Archive B'])
    })

    it('limits transcode targets to writable local providers and emits an OPUS payload', async () => {
        const wrapper = await mountModal()

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

        expect(submitTranscodeMock).toHaveBeenCalledWith({
            srcProviderType: 'FILE_SYSTEM',
            srcProviderId: 2,
            dstProviderType: 'FILE_SYSTEM',
            dstProviderId: 1,
            targetCodec: 'OPUS',
        })
        expect(resolveMock).toHaveBeenCalledWith(true)
    })
})
