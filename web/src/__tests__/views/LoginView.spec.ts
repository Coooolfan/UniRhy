import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'

const { alertMock, getBackendUrlMock, loginMock, pushMock, registerMock, setBackendUrlMock } =
    vi.hoisted(() => ({
        alertMock: vi.fn(),
        getBackendUrlMock: vi.fn(),
        loginMock: vi.fn(),
        pushMock: vi.fn(),
        registerMock: vi.fn(),
        setBackendUrlMock: vi.fn(),
    }))

vi.mock('vue-router', () => ({
    useRouter: () => ({
        push: pushMock,
    }),
}))

vi.mock('@/ApiInstance', () => ({
    api: {
        tokenController: {
            login: loginMock,
        },
        accountController: {
            create: registerMock,
        },
    },
    normalizeApiError: (error: unknown) => {
        if (error instanceof Error) {
            return { message: error.message }
        }

        return { message: '请求失败' }
    },
}))

import LoginView from '@/views/LoginView.vue'

const flushView = async () => {
    await Promise.resolve()
    await nextTick()
    await Promise.resolve()
    await nextTick()
}

describe('LoginView', () => {
    beforeEach(() => {
        pushMock.mockReset()
        loginMock.mockReset()
        registerMock.mockReset()
        getBackendUrlMock.mockReset()
        setBackendUrlMock.mockReset()
        alertMock.mockReset()

        vi.stubGlobal('alert', alertMock)
        delete window.__UNIRHY_ELECTRON__
        window.__UNIRHY_RUNTIME__ = {
            platform: 'web',
        }
    })

    it('hides backend endpoint configuration on web', async () => {
        const wrapper = mount(LoginView)
        await flushView()

        expect(wrapper.find('[data-testid="backend-endpoint-input"]').exists()).toBe(false)
    })

    it('shows backend endpoint configuration on desktop platforms and persists updates', async () => {
        window.__UNIRHY_RUNTIME__ = {
            platform: 'macos',
        }
        window.__UNIRHY_ELECTRON__ = {
            getBackendUrl: getBackendUrlMock,
            setBackendUrl: setBackendUrlMock,
        }
        getBackendUrlMock.mockResolvedValue('http://192.168.1.50:8654')
        setBackendUrlMock.mockResolvedValue('http://192.168.1.60:8654')

        const wrapper = mount(LoginView)
        await flushView()

        const input = wrapper.get('[data-testid="backend-endpoint-input"]')
        expect(input.element).toBeInstanceOf(HTMLInputElement)
        if (!(input.element instanceof HTMLInputElement)) {
            throw new Error('backend endpoint input is not an HTML input element')
        }

        expect(input.element.value).toBe('http://192.168.1.50:8654')

        await input.setValue('http://192.168.1.60:8654')
        await wrapper.get('[data-testid="backend-endpoint-save"]').trigger('click')
        await flushView()

        expect(setBackendUrlMock).toHaveBeenCalledWith('http://192.168.1.60:8654')
        expect(alertMock).toHaveBeenCalledWith('后端端点已保存')
    })
})
