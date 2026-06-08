import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { api } from '@/ApiInstance'
import SettingsView from '@/views/SettingsView.vue'

vi.mock('@/ApiInstance', async (importOriginal) => {
    const actual = await importOriginal<typeof import('@/ApiInstance')>()
    return {
        ...actual,
        api: {
            accountController: {
                me: vi.fn(),
                list: vi.fn(),
            },
            fileSystemStorageController: {
                list: vi.fn(),
            },
            ossStorageController: {
                list: vi.fn(),
            },
            pluginController: {
                listPlugins: vi.fn(),
            },
            systemConfigController: {
                get: vi.fn(),
                isInitialized: vi.fn(),
            },
        },
    }
})

const meMock = vi.mocked(api.accountController.me)
const listAccountsMock = vi.mocked(api.accountController.list)
const listFileSystemStorageMock = vi.mocked(api.fileSystemStorageController.list)
const listOssStorageMock = vi.mocked(api.ossStorageController.list)
const listPluginsMock = vi.mocked(api.pluginController.listPlugins)
const getSystemConfigMock = vi.mocked(api.systemConfigController.get)
const isInitializedMock = vi.mocked(api.systemConfigController.isInitialized)

const flushView = async () => {
    await flushPromises()
    await flushPromises()
}

const mountView = () =>
    mount(SettingsView, {
        global: {
            plugins: [createPinia()],
            stubs: {
                DashboardTopBar: true,
                StorageNodesSection: true,
                PluginsSection: true,
                AccountsSection: {
                    props: ['accounts'],
                    template:
                        '<section data-test="accounts-section">{{ accounts.length }}</section>',
                },
            },
        },
    })

describe('SettingsView', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        meMock.mockReset()
        listAccountsMock.mockReset()
        listFileSystemStorageMock.mockReset()
        listOssStorageMock.mockReset()
        listPluginsMock.mockReset()
        getSystemConfigMock.mockReset()
        isInitializedMock.mockReset()

        meMock.mockResolvedValue({
            id: 1,
            name: 'Admin',
            email: 'admin@example.com',
            admin: true,
            preferences: {
                preferredAssetFormat: 'audio/opus',
            },
        })
        listAccountsMock.mockResolvedValue([
            {
                id: 1,
                name: 'Admin',
                email: 'admin@example.com',
                admin: true,
                preferences: {
                    preferredAssetFormat: 'audio/opus',
                },
            },
        ])
        listFileSystemStorageMock.mockResolvedValue([])
        listOssStorageMock.mockResolvedValue([])
        listPluginsMock.mockResolvedValue([])
        getSystemConfigMock.mockResolvedValue({
            id: 0,
            fsProviderId: undefined,
            ossProviderId: undefined,
        })
        isInitializedMock.mockResolvedValue({ initialized: true })
    })

    it('loads account list after resolving the current admin user', async () => {
        const wrapper = mountView()

        await flushView()

        expect(meMock).toHaveBeenCalledTimes(1)
        expect(listAccountsMock).toHaveBeenCalledTimes(1)
        expect(wrapper.find('[data-test="accounts-section"]').exists()).toBe(true)
    })
})
