import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import AppModalHost from '@/components/modals/AppModalHost.vue'
import PluginsSection from '@/components/settings/PluginsSection.vue'
import type { PluginInfoResponse } from '@/__generated/model/static'
import { useModalStore } from '@/stores/modal'

const plugin: PluginInfoResponse = {
    id: 'com.example.importer',
    name: 'Example Importer',
    version: '1.0.0',
    taskType: 'IMPORT',
    concurrency: 1,
    isAvailable: false,
    enabled: false,
    formDefinition: {
        schema: {
            type: 'object',
            properties: {
                sourceUrl: {
                    type: 'string',
                    title: 'Source URL',
                    description: 'Media source address',
                },
            },
            required: ['sourceUrl'],
            additionalProperties: false,
        },
        order: ['sourceUrl'],
    },
    configDefinition: {
        schema: {
            type: 'object',
            properties: {
                apiBaseUrl: {
                    type: 'string',
                    title: 'API Base URL',
                },
            },
            required: ['apiBaseUrl'],
            additionalProperties: false,
        },
        order: ['apiBaseUrl'],
    },
}

const mountSection = () => {
    const setEnabled = vi.fn<(_: string, __: boolean) => Promise<void>>().mockResolvedValue()
    const updateConcurrency = vi.fn<(_: string, __: number) => Promise<void>>().mockResolvedValue()
    const deletePlugin = vi.fn<(_: string) => Promise<void>>().mockResolvedValue()
    const loadConfiguration = vi.fn().mockResolvedValue({
        values: { apiBaseUrl: 'https://api.example.test' },
        configuredSecretFields: [],
    })
    const saveConfiguration = vi.fn().mockResolvedValue({
        values: { apiBaseUrl: 'https://api.example.test' },
        configuredSecretFields: [],
    })
    const pinia = createPinia()
    const Wrapper = {
        components: { AppModalHost, PluginsSection },
        template: `
            <div>
                <PluginsSection
                    :plugins="plugins"
                    :is-loading="false"
                    :is-uploading="false"
                    error=""
                    :on-upload="onUpload"
                    :on-set-enabled="setEnabled"
                    :on-update-concurrency="updateConcurrency"
                    :on-load-configuration="onLoadConfiguration"
                    :on-save-configuration="onSaveConfiguration"
                    :on-delete="onDelete"
                    :on-download="onDownload"
                    :can-manage="true"
                />
                <AppModalHost />
            </div>
        `,
        setup: () => ({
            plugins: [plugin],
            setEnabled,
            updateConcurrency,
            onUpload: vi.fn(),
            onLoadConfiguration: loadConfiguration,
            onSaveConfiguration: saveConfiguration,
            onDelete: deletePlugin,
            onDownload: vi.fn(),
        }),
    }

    return {
        wrapper: mount(Wrapper, {
            global: {
                plugins: [pinia],
                stubs: { teleport: true, transition: false },
            },
        }),
        modalStore: useModalStore(pinia),
        setEnabled,
        updateConcurrency,
        deletePlugin,
    }
}

describe('PluginsSection', () => {
    beforeEach(() => setActivePinia(createPinia()))

    it('moves plugin management and configuration into the details modal', async () => {
        const { wrapper, modalStore, setEnabled, updateConcurrency } = mountSection()
        expect(wrapper.text()).not.toContain('Source URL')
        expect(wrapper.text()).not.toContain('API Base URL')

        await wrapper.get('button[title="插件详情"]').trigger('click')
        await flushPromises()

        expect(modalStore.stack[0]?.size).toBe('xl')
        expect(wrapper.text()).toContain('API Base URL')
        expect(wrapper.get<HTMLInputElement>('input[type="text"]').element.value).toBe(
            'https://api.example.test',
        )
        expect(wrapper.text()).not.toContain('Source URL')
        expect(
            wrapper.get('[data-testid="plugin-form-params-toggle"]').attributes('aria-expanded'),
        ).toBe('false')

        await wrapper.get('[data-testid="plugin-form-params-toggle"]').trigger('click')
        expect(wrapper.text()).toContain('Source URL')
        expect(wrapper.text()).toContain('Media source address')

        await wrapper.get('[data-testid="plugin-enabled-toggle"]').setValue(true)
        await flushPromises()
        expect(setEnabled).toHaveBeenCalledWith(plugin.id, true)

        await wrapper.get('[data-testid="plugin-concurrency-input"]').setValue('3')
        await wrapper.get('[data-testid="plugin-concurrency-save"]').trigger('click')
        await flushPromises()
        expect(updateConcurrency).toHaveBeenCalledWith(plugin.id, 3)
    })

    it('requires confirmation before deleting a plugin', async () => {
        const { wrapper, deletePlugin } = mountSection()

        await wrapper.get('button[title="删除插件"]').trigger('click')
        await flushPromises()
        expect(deletePlugin).not.toHaveBeenCalled()
        expect(wrapper.text()).toContain('删除插件会同时清除其配置和持久化数据')

        const confirmButton = wrapper
            .findAll('button')
            .find((button) => button.text().includes('确认删除'))
        expect(confirmButton).toBeTruthy()
        await confirmButton!.trigger('click')
        await flushPromises()

        expect(deletePlugin).toHaveBeenCalledWith(plugin.id)
    })
})
