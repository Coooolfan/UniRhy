import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import BaseModalShell from '@/components/modals/BaseModalShell.vue'

const mountShell = (
    overrides: Partial<InstanceType<typeof BaseModalShell>['$props']> = {},
    slotOverrides: Record<string, string> = {},
) =>
    mount(BaseModalShell, {
        props: {
            title: '测试弹窗',
            ...overrides,
        },
        slots: {
            default: '<div>modal body</div>',
            ...slotOverrides,
        },
        global: {
            stubs: {
                teleport: true,
                transition: false,
            },
        },
    })

describe('BaseModalShell', () => {
    it('renders the shell and default tone styles', () => {
        const wrapper = mountShell()

        expect(wrapper.get('[data-testid="app-modal-root"]').text()).toContain('测试弹窗')
        expect(wrapper.html()).toContain('bg-[#2B221B]/50')
        expect(wrapper.html()).toContain('border-[#EAE6DE]')
    })

    it('renders danger tone styles', () => {
        const wrapper = mountShell({ tone: 'danger' })

        expect(wrapper.html()).toContain('bg-black/55')
        expect(wrapper.html()).toContain('border-[#E3C8C8]')
    })

    it('uses content-sized width constraints instead of filling the whole size tier', () => {
        const wrapper = mountShell({ size: 'sm' })
        const panel = wrapper.get('.pointer-events-auto')

        expect(panel.attributes('style')).toContain('width: fit-content;')
        expect(panel.attributes('style')).toContain('min-width: min(320px, calc(100vw - 2rem));')
        expect(panel.attributes('style')).toContain('max-width: min(420px, calc(100vw - 2rem));')
    })

    it('does not render a header when no title or header slot is provided', () => {
        const wrapper = mount(BaseModalShell, {
            slots: {
                default: '<div>modal body</div>',
            },
            global: {
                stubs: {
                    teleport: true,
                    transition: false,
                },
            },
        })

        expect(wrapper.find('[data-testid="app-modal-header"]').exists()).toBe(false)
        expect(wrapper.text()).toContain('modal body')
    })

    it('renders a custom header slot instead of the default title header', () => {
        const wrapper = mountShell(
            {},
            {
                header: '<div data-testid="custom-modal-header">custom header</div>',
            },
        )

        expect(wrapper.find('[data-testid="app-modal-header"]').exists()).toBe(true)
        expect(wrapper.get('[data-testid="custom-modal-header"]').text()).toBe('custom header')
        expect(wrapper.text()).not.toContain('测试弹窗')
    })

    it('emits close on escape when it is topmost and closable', async () => {
        const wrapper = mountShell()

        window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
        await wrapper.vm.$nextTick()

        expect(wrapper.emitted('close')).toHaveLength(1)
    })

    it('does not emit close on escape when it is not topmost', async () => {
        const wrapper = mountShell({ isTopmost: false })

        window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
        await wrapper.vm.$nextTick()

        expect(wrapper.emitted('close')).toBeUndefined()
    })

    it('respects closeOnBackdrop and closable settings', async () => {
        const wrapper = mountShell({
            closable: false,
            closeOnBackdrop: false,
        })

        expect(wrapper.find('[data-testid="app-modal-close"]').exists()).toBe(false)

        await wrapper.get('[data-testid="app-modal-backdrop"]').trigger('click')
        expect(wrapper.emitted('close')).toBeUndefined()
    })

    it('moves focus into the modal when opened', async () => {
        const outsideButton = document.createElement('button')
        outsideButton.type = 'button'
        outsideButton.textContent = 'outside'
        document.body.append(outsideButton)
        outsideButton.focus()

        const wrapper = mountShell(
            {},
            {
                default:
                    '<div><button type="button" data-testid="first-focusable">first</button><button type="button">second</button></div>',
            },
        )

        await nextTick()

        expect(document.activeElement).toBe(wrapper.get('[data-testid="first-focusable"]').element)

        wrapper.unmount()
        outsideButton.remove()
    })

    it('keeps Tab navigation cycling inside the modal', async () => {
        const wrapper = mountShell(
            {},
            {
                default:
                    '<div><button type="button" data-testid="first-focusable">first</button><button type="button" data-testid="last-focusable">last</button></div>',
            },
        )

        await nextTick()

        const firstButton = wrapper.get('[data-testid="first-focusable"]').element
        const lastButton = wrapper.get('[data-testid="last-focusable"]').element

        expect(firstButton).toBeInstanceOf(HTMLButtonElement)
        expect(lastButton).toBeInstanceOf(HTMLButtonElement)

        if (
            !(firstButton instanceof HTMLButtonElement) ||
            !(lastButton instanceof HTMLButtonElement)
        ) {
            throw new TypeError('Expected modal focus targets to be buttons')
        }

        lastButton.focus()
        window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab' }))
        expect(document.activeElement).toBe(firstButton)

        firstButton.focus()
        window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true }))
        expect(document.activeElement).toBe(lastButton)

        wrapper.unmount()
    })
})
