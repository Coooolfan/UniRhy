import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import TaskSubmissionModal from '@/components/tasks/TaskSubmissionModal.vue'
import { modalContextKey, type ModalContext } from '@/components/modals/modalContext'
import type { TaskProviderOption } from '@/composables/useTaskManagement'
import type { TaskDefinitionView } from '@/__generated/model/static'

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

const BUILT_IN_NAMESPACE = 'app.unirhy.built-in'

const builtInDefinitions: TaskDefinitionView[] = [
    {
        namespace: BUILT_IN_NAMESPACE,
        taskType: 'METADATA_PARSE',
        name: '元数据解析',
        formDefinition: {
            schema: {
                type: 'object',
                properties: {},
                required: [],
                additionalProperties: false,
            },
            order: [],
        },
    },
    {
        namespace: BUILT_IN_NAMESPACE,
        taskType: 'TRANSCODE',
        name: '音频转码',
        formDefinition: {
            schema: {
                type: 'object',
                properties: {},
                required: [],
                additionalProperties: false,
            },
            order: [],
        },
    },
]

const pluginDefinition: TaskDefinitionView = {
    namespace: 'com.example.cover',
    taskType: 'FETCH_COVER',
    name: '封面抓取',
    formDefinition: {
        schema: {
            type: 'object',
            properties: {
                keyword: {
                    type: 'string',
                    title: '搜索关键字',
                    minLength: 1,
                },
                limit: {
                    type: 'integer',
                    title: '数量上限',
                    default: 10,
                    minimum: 1,
                },
                dryRun: {
                    type: 'boolean',
                    title: '试运行',
                    default: false,
                },
            },
            required: ['keyword'],
            additionalProperties: false,
        },
        order: ['keyword', 'limit', 'dryRun'],
    },
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

const submitTaskMock = vi.fn()
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
            definitions: builtInDefinitions,
            loadProviders: () => Promise.resolve(providerOptions),
            submitTask: submitTaskMock,
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
        submitTaskMock.mockReset()
        resolveMock.mockReset()
        closeMock.mockReset()
    })

    it('submits a metadata parse payload with the selected provider', async () => {
        const wrapper = await mountModal()

        expect(wrapper.text()).toContain('元数据解析')
        await wrapper.get('[data-test="task-submit-button"]').trigger('click')

        expect(submitTaskMock).toHaveBeenCalledWith(BUILT_IN_NAMESPACE, 'METADATA_PARSE', {
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

    it('limits transcode targets to writable providers and emits an OPUS payload', async () => {
        const wrapper = await mountModal()

        await wrapper.get('[data-test="task-type-transcode"]').trigger('click')

        const sourceOptions = getOptionTexts(wrapper, '[data-test="transcode-source-select"]')
        const destinationOptions = getOptionTexts(
            wrapper,
            '[data-test="transcode-destination-select"]',
        )

        expect(sourceOptions).toEqual(['[本地] Library A', '[本地] Archive B', '[OSS] Bucket C'])
        expect(destinationOptions).toEqual(['[本地] Library A', '[OSS] Bucket C'])

        await wrapper.get('[data-test="transcode-source-select"]').setValue('FILE_SYSTEM:2')
        await wrapper.get('[data-test="task-submit-button"]').trigger('click')

        expect(submitTaskMock).toHaveBeenCalledWith(BUILT_IN_NAMESPACE, 'TRANSCODE', {
            srcProviderType: 'FILE_SYSTEM',
            srcProviderId: 2,
            dstProviderType: 'OSS',
            dstProviderId: 3,
            targetCodec: 'OPUS',
        })
        expect(resolveMock).toHaveBeenCalledWith(true)
    })

    it('submits OSS providers for metadata parse and transcode', async () => {
        const wrapper = await mountModal()

        await wrapper.get('[data-test="metadata-parse-provider-select"]').setValue('OSS:3')
        await wrapper.get('[data-test="task-submit-button"]').trigger('click')

        expect(submitTaskMock).toHaveBeenCalledWith(BUILT_IN_NAMESPACE, 'METADATA_PARSE', {
            providerType: 'OSS',
            providerId: 3,
        })

        submitTaskMock.mockReset()
        resolveMock.mockReset()

        await wrapper.get('[data-test="task-type-transcode"]').trigger('click')
        await wrapper.get('[data-test="transcode-source-select"]').setValue('OSS:3')
        await wrapper.get('[data-test="transcode-destination-select"]').setValue('OSS:3')
        await wrapper.get('[data-test="task-submit-button"]').trigger('click')

        expect(submitTaskMock).toHaveBeenCalledWith(BUILT_IN_NAMESPACE, 'TRANSCODE', {
            srcProviderType: 'OSS',
            srcProviderId: 3,
            dstProviderType: 'OSS',
            dstProviderId: 3,
            targetCodec: 'OPUS',
        })
    })

    it('renders a schema-driven form for plugin tasks and submits typed params', async () => {
        const wrapper = await mountModal({
            definitions: [...builtInDefinitions, pluginDefinition],
        })

        await wrapper.get('[data-test="task-type-fetch_cover"]').trigger('click')
        expect(wrapper.text()).toContain('搜索关键字')
        expect(wrapper.text()).toContain('数量上限')

        // 缺少 required 字段时禁止提交
        const submitButton = wrapper.get('[data-test="task-submit-button"]')
        expect(submitButton.attributes('disabled')).toBeDefined()

        const keywordInput = wrapper.find('input[type="text"]')
        expect(keywordInput.exists()).toBe(true)
        await keywordInput.setValue('beethoven')
        await submitButton.trigger('click')

        expect(submitTaskMock).toHaveBeenCalledWith('com.example.cover', 'FETCH_COVER', {
            keyword: 'beethoven',
            limit: 10,
            dryRun: false,
        })
        expect(resolveMock).toHaveBeenCalledWith(true)
    })
})
