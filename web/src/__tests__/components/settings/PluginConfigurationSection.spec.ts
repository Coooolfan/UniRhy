import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import PluginConfigurationSection from '@/components/settings/PluginConfigurationSection.vue'
import type { PluginInfoResponse } from '@/__generated/model/static'

const plugin = (required: boolean): PluginInfoResponse => ({
    id: 'com.example.configured',
    name: 'Configured Plugin',
    version: '1.0.0',
    isAvailable: false,
    enabled: false,
    tasks: [
        {
            taskType: 'IMPORT',
            concurrency: 1,
            userSubmittable: true,
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
    ],
    configDefinition: {
        schema: {
            type: 'object',
            properties: {
                apiKey: {
                    type: 'string',
                    title: 'API Key',
                    writeOnly: true,
                    minLength: 1,
                },
                batchSize: {
                    type: 'integer',
                    title: 'Batch size',
                    minimum: 1,
                },
            },
            required: required ? ['apiKey'] : [],
            additionalProperties: false,
        },
        order: ['apiKey', 'batchSize'],
    },
})

describe('PluginConfigurationSection', () => {
    it('does not allow saving before configuration is loaded', async () => {
        const wrapper = mount(PluginConfigurationSection, {
            props: {
                plugin: plugin(false),
                canManage: true,
                loadConfiguration: vi.fn().mockRejectedValue(new Error('load failed')),
                saveConfiguration: vi.fn(),
            },
        })
        await flushPromises()

        expect(
            wrapper.get<HTMLButtonElement>('[data-test="plugin-config-save"]').element.disabled,
        ).toBe(true)
    })

    it('loads configuration after management permission becomes available', async () => {
        const loadConfiguration = vi.fn().mockResolvedValue({
            values: { batchSize: 5 },
            configuredSecretFields: ['apiKey'],
        })
        const wrapper = mount(PluginConfigurationSection, {
            props: {
                plugin: plugin(true),
                canManage: false,
                loadConfiguration,
                saveConfiguration: vi.fn(),
            },
        })
        await flushPromises()
        expect(loadConfiguration).not.toHaveBeenCalled()

        await wrapper.setProps({ canManage: true })
        await flushPromises()

        expect(loadConfiguration).toHaveBeenCalledOnce()
        expect(wrapper.get<HTMLInputElement>('input[type="number"]').element.value).toBe('5')
        expect(wrapper.text()).toContain('已配置')
    })

    it('keeps configured secrets masked and submits replacements', async () => {
        const loadConfiguration = vi.fn().mockResolvedValue({
            values: { batchSize: 5 },
            configuredSecretFields: ['apiKey'],
        })
        const saveConfiguration = vi.fn().mockResolvedValue({
            values: { batchSize: 8 },
            configuredSecretFields: ['apiKey'],
        })
        const wrapper = mount(PluginConfigurationSection, {
            props: {
                plugin: plugin(true),
                canManage: true,
                loadConfiguration,
                saveConfiguration,
            },
        })
        await flushPromises()

        const password = wrapper.get<HTMLInputElement>('input[type="password"]')
        const batchSize = wrapper.get<HTMLInputElement>('input[type="number"]')
        expect(password.element.value).toBe('')
        expect(batchSize.element.value).toBe('5')
        expect(wrapper.text()).toContain('已配置')

        await batchSize.setValue('8')
        await password.setValue('replacement-secret')
        await wrapper.get('[data-test="plugin-config-save"]').trigger('click')
        await flushPromises()

        expect(saveConfiguration).toHaveBeenCalledWith(
            'com.example.configured',
            { apiKey: 'replacement-secret', batchSize: 8 },
            [],
        )
    })

    it('clears optional configured secrets explicitly', async () => {
        const loadConfiguration = vi.fn().mockResolvedValue({
            values: {},
            configuredSecretFields: ['apiKey'],
        })
        const saveConfiguration = vi.fn().mockResolvedValue({
            values: {},
            configuredSecretFields: [],
        })
        const wrapper = mount(PluginConfigurationSection, {
            props: {
                plugin: plugin(false),
                canManage: true,
                loadConfiguration,
                saveConfiguration,
            },
        })
        await flushPromises()

        await wrapper.get('button[title="清除敏感值"]').trigger('click')
        await wrapper.get('[data-test="plugin-config-save"]').trigger('click')
        await flushPromises()

        expect(saveConfiguration).toHaveBeenCalledWith('com.example.configured', {}, ['apiKey'])
    })
})
