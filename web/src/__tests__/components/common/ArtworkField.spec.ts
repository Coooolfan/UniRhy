import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ArtworkField from '@/components/common/ArtworkField.vue'

const setInputFile = async (wrapper: ReturnType<typeof mount>, file: File) => {
    const input = wrapper.get('[data-test="artwork-input"]')
    Object.defineProperty(input.element, 'files', {
        configurable: true,
        value: [file],
    })
    await input.trigger('change')
}

const lastModelValue = (wrapper: ReturnType<typeof mount>) => {
    const events = wrapper.emitted('update:modelValue')
    // Test TypeScript target does not include Array.prototype.at.
    // oxlint-disable-next-line unicorn/prefer-at
    return events?.[events.length - 1]?.[0]
}

describe('ArtworkField', () => {
    it('selects a supported image and previews it', async () => {
        const createObjectUrl = vi
            .spyOn(URL, 'createObjectURL')
            .mockReturnValue('blob:artwork-preview')
        const revokeObjectUrl = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
        const wrapper = mount(ArtworkField, {
            props: {
                modelValue: { file: null, remove: false },
                currentUrl: '/current.jpg',
                label: '曲目封面',
            },
        })
        const file = new File(['image'], 'cover.png', { type: 'image/png' })

        await setInputFile(wrapper, file)
        await wrapper.setProps({ modelValue: { file, remove: false } })

        expect(lastModelValue(wrapper)).toEqual({
            file,
            remove: false,
        })
        expect(wrapper.get('img').attributes('src')).toBe('blob:artwork-preview')

        wrapper.unmount()
        expect(revokeObjectUrl).toHaveBeenCalledWith('blob:artwork-preview')
        createObjectUrl.mockRestore()
        revokeObjectUrl.mockRestore()
    })

    it('accepts arbitrary file types and rejects oversized files', async () => {
        const wrapper = mount(ArtworkField, {
            props: {
                modelValue: { file: null, remove: false },
                label: '艺术家头像',
            },
        })

        const avif = new File(['image'], 'avatar.avif', { type: 'image/avif' })
        await setInputFile(wrapper, avif)
        expect(lastModelValue(wrapper)).toEqual({ file: avif, remove: false })

        const oversized = new File([new Uint8Array(10 * 1024 * 1024 + 1)], 'avatar.png', {
            type: 'image/png',
        })
        await setInputFile(wrapper, oversized)
        expect(wrapper.get('[data-test="artwork-error"]').text()).toContain('10 MiB')
        expect(lastModelValue(wrapper)).toEqual({ file: avif, remove: false })
    })

    it('opens the picker from the image and does not expose image removal', async () => {
        const wrapper = mount(ArtworkField, {
            props: {
                modelValue: { file: null, remove: false },
                currentUrl: '/avatar.jpg',
                label: '艺术家头像',
            },
        })

        const input = wrapper.get<HTMLInputElement>('[data-test="artwork-input"]')
        const click = vi.spyOn(input.element, 'click')
        await wrapper.get('[data-test="artwork-picker"]').trigger('click')

        expect(click).toHaveBeenCalledOnce()
        expect(wrapper.text()).toContain('替换图片')
        expect(wrapper.text()).not.toContain('移除图片')
        expect(wrapper.text()).not.toContain('撤销图片更改')
    })
})
