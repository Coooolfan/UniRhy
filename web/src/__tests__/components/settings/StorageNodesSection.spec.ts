import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import AppModalHost from '@/components/modals/AppModalHost.vue'
import StorageNodesSection from '@/components/settings/StorageNodesSection.vue'
import type { StorageNodeForm } from '@/composables/useStorageSettings'

const storageNodes = [
    {
        id: 1,
        name: 'Library',
        parentPath: '/music/library',
        readonly: true,
    },
]

const mountSection = ({
    createStorageNode = vi
        .fn<(_: StorageNodeForm) => Promise<string | null>>()
        .mockResolvedValue(null),
    updateStorageNode = vi
        .fn<(_: number, __: StorageNodeForm) => Promise<string | null>>()
        .mockResolvedValue(null),
    deleteStorageNode = vi.fn<(_: number) => Promise<string | null>>().mockResolvedValue(null),
} = {}) => {
    const Wrapper = {
        components: {
            AppModalHost,
            StorageNodesSection,
        },
        template: `
            <div>
                <StorageNodesSection
                    :storage-nodes="storageNodes"
                    :system-config="systemConfig"
                    :is-loading="false"
                    error=""
                    :is-saving="false"
                    :create-storage-node="createStorageNode"
                    :update-storage-node="updateStorageNode"
                    :delete-storage-node="deleteStorageNode"
                />
                <AppModalHost />
            </div>
        `,
        setup: () => ({
            storageNodes,
            systemConfig: {
                fsProviderId: null,
                ossProviderId: null,
            },
            createStorageNode,
            updateStorageNode,
            deleteStorageNode,
        }),
    }

    const wrapper = mount(Wrapper, {
        global: {
            plugins: [createPinia()],
            stubs: {
                teleport: true,
                transition: false,
            },
        },
    })

    return {
        wrapper,
        createStorageNode,
        updateStorageNode,
        deleteStorageNode,
    }
}

describe('StorageNodesSection', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    it('opens the create modal, submits the form, and forwards the payload', async () => {
        const { wrapper, createStorageNode } = mountSection()
        const createButton = wrapper
            .findAll('button')
            .find((button) => button.text().includes('新增节点'))

        expect(createButton).toBeTruthy()
        await createButton!.trigger('click')
        await flushPromises()

        await wrapper.get('[data-testid="storage-node-form-name"]').setValue('Archive')
        await wrapper
            .get('[data-testid="storage-node-form-parent-path"]')
            .setValue('/music/archive')
        await wrapper.get('[data-testid="storage-node-form-submit"]').trigger('click')
        await flushPromises()

        expect(createStorageNode).toHaveBeenCalledWith({
            name: 'Archive',
            parentPath: '/music/archive',
            readonly: true,
        })
        expect(wrapper.find('[data-testid="storage-node-form-name"]').exists()).toBe(false)
    })

    it('keeps the create modal open when create fails', async () => {
        const { wrapper, createStorageNode } = mountSection({
            createStorageNode: vi
                .fn<(_: StorageNodeForm) => Promise<string | null>>()
                .mockResolvedValue('创建失败'),
        })
        const createButton = wrapper
            .findAll('button')
            .find((button) => button.text().includes('新增节点'))

        expect(createButton).toBeTruthy()
        await createButton!.trigger('click')
        await flushPromises()
        await wrapper.get('[data-testid="storage-node-form-submit"]').trigger('click')
        await flushPromises()

        expect(createStorageNode).toHaveBeenCalled()
        expect(wrapper.get('[data-testid="storage-node-form-error"]').text()).toContain('创建失败')
    })

    it('opens the edit modal with initial node values and submits updates', async () => {
        const { wrapper, updateStorageNode } = mountSection()

        await wrapper.get('button[title="编辑"]').trigger('click')
        await flushPromises()

        const nameInputElement = wrapper.get('[data-testid="storage-node-form-name"]').element
        const parentPathInputElement = wrapper.get(
            '[data-testid="storage-node-form-parent-path"]',
        ).element

        expect(nameInputElement).toBeInstanceOf(HTMLInputElement)
        expect(parentPathInputElement).toBeInstanceOf(HTMLInputElement)

        if (!(nameInputElement instanceof HTMLInputElement)) {
            throw new TypeError('storage-node-form-name is not an input')
        }
        if (!(parentPathInputElement instanceof HTMLInputElement)) {
            throw new TypeError('storage-node-form-parent-path is not an input')
        }

        expect(nameInputElement.value).toBe('Library')
        expect(parentPathInputElement.value).toBe('/music/library')
        expect(wrapper.text()).toContain('修改存储路径根节点会导致此存储节点下的所有资产被重定向')

        await wrapper.get('[data-testid="storage-node-form-name"]').setValue('Updated Library')
        await wrapper
            .get('[data-testid="storage-node-form-parent-path"]')
            .setValue('/music/updated')
        await wrapper.get('[data-testid="storage-node-form-submit"]').trigger('click')
        await flushPromises()

        expect(updateStorageNode).toHaveBeenCalledWith(1, {
            name: 'Updated Library',
            parentPath: '/music/updated',
            readonly: true,
        })
        expect(wrapper.find('[data-testid="storage-node-form-name"]').exists()).toBe(false)
    })

    it('confirms deletion before calling deleteStorageNode', async () => {
        const { wrapper, deleteStorageNode } = mountSection()

        await wrapper.get('button[title="删除"]').trigger('click')
        await flushPromises()
        const confirmButton = wrapper
            .findAll('button')
            .find((button) => button.text().includes('确认移除'))

        expect(confirmButton).toBeTruthy()
        await confirmButton!.trigger('click')
        await flushPromises()

        expect(deleteStorageNode).toHaveBeenCalledWith(1)
    })
})
