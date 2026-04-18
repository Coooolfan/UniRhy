import { reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import AppModalHost from '@/components/modals/AppModalHost.vue'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'

const pushMock = vi.fn()
const toggleSidebarMock = vi.fn()
const fetchUserMock = vi.fn()
const userStoreMock = reactive({
    user: {
        id: 1,
        name: 'Test User',
        email: 'test@example.com',
    },
    fetchUser: fetchUserMock,
    updateUser: vi.fn(),
    logout: vi.fn(),
    clearUser: vi.fn(),
})

vi.mock('vue-router', () => ({
    useRouter: () => ({
        push: pushMock,
    }),
}))

vi.mock('@/composables/useDashboardLayout', () => ({
    useDashboardLayout: () => ({
        toggleSidebar: toggleSidebarMock,
    }),
}))

vi.mock('@/stores/user', () => ({
    useUserStore: () => userStoreMock,
}))

describe('DashboardTopBar', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        pushMock.mockReset()
        toggleSidebarMock.mockReset()
        fetchUserMock.mockReset()
        userStoreMock.user = {
            id: 1,
            name: 'Test User',
            email: 'test@example.com',
        }
    })

    it('opens the profile dialog from the avatar button', async () => {
        const wrapper = mount(
            {
                components: {
                    DashboardTopBar,
                    AppModalHost,
                },
                template: `
                    <div>
                        <DashboardTopBar />
                        <AppModalHost />
                    </div>
                `,
            },
            {
                global: {
                    plugins: [createPinia()],
                    stubs: {
                        teleport: true,
                        transition: false,
                    },
                },
            },
        )

        await wrapper.get('button[aria-label="个人中心"]').trigger('click')
        await flushPromises()

        expect(wrapper.text()).toContain('Test User')
        expect(wrapper.text()).toContain('编辑资料')
    })
})
