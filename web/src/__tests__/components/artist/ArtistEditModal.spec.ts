import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ArtistEditModal from '@/components/artist/ArtistEditModal.vue'
import { modalContextKey, type ModalContext } from '@/components/modals/modalContext'

const resolveMock = vi.fn()
const closeMock = vi.fn()

const modalContext: ModalContext<undefined> = {
    close: () => {
        closeMock()
    },
    resolve: (value) => {
        resolveMock(value)
    },
    isTopmost: true,
}

const mountModal = (onSubmit = vi.fn()) =>
    mount(ArtistEditModal, {
        props: {
            initialForm: {
                displayName: 'Artist A',
                alias: ['Alias A'],
                comment: 'Comment A',
            },
            currentAvatar: '/avatar.jpg',
            onSubmit,
        },
        global: {
            provide: {
                [modalContextKey]: modalContext,
            },
        },
    })

describe('ArtistEditModal', () => {
    beforeEach(() => {
        resolveMock.mockReset()
        closeMock.mockReset()
    })

    it('submits metadata together with an avatar removal', async () => {
        const onSubmit = vi.fn()
        const wrapper = mountModal(onSubmit)

        const removeButton = wrapper
            .findAll('button')
            .find((button) => button.text() === '移除图片')
        expect(removeButton).toBeTruthy()
        await removeButton!.trigger('click')

        const submitButton = wrapper
            .findAll('button')
            .find((button) => button.text().includes('保存更改'))
        expect(submitButton).toBeTruthy()
        await submitButton!.trigger('click')

        expect(onSubmit).toHaveBeenCalledWith(
            {
                displayName: 'Artist A',
                alias: ['Alias A'],
                comment: 'Comment A',
            },
            { file: null, remove: true },
        )
        expect(resolveMock).toHaveBeenCalledWith(undefined)
    })
})
