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
                    :is-editing="null"
                    :is-saving="false"
                    :edit-form="editForm"
                    :create-storage-node="createStorageNode"
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
            editForm: {
                name: '',
                parentPath: '',
                readonly: true,
            },
            createStorageNode,
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
