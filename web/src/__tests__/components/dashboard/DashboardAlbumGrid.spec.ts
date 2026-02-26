import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'

const pushMock = vi.fn()

vi.mock('vue-router', () => ({
    useRouter: () => ({
        push: pushMock,
    }),
}))

vi.mock('@/ApiInstance', async (importOriginal) => {
    const actual = await importOriginal<typeof import('@/ApiInstance')>()
    return {
        ...actual,
        api: {
            albumController: {
                listAlbums: vi.fn(),
                getAlbum: vi.fn(),
            },
        },
    }
})

import { api } from '@/ApiInstance'
import DashboardAlbumGrid from '@/components/dashboard/DashboardAlbumGrid.vue'

const listAlbumsMock = vi.mocked(api.albumController.listAlbums)

const flushView = async () => {
    await Promise.resolve()
    await nextTick()
    await Promise.resolve()
    await nextTick()
}

const buildAlbumPage = () => ({
    rows: [
        {
            id: 101,
            title: 'Album A',
            kind: 'Album',
            comment: 'Test Album',
            recordings: [{ id: 301, label: 'Artist A' }],
            cover: { id: 401 },
        },
    ],
    totalPageCount: 1,
    totalRowCount: 1,
})

describe('DashboardAlbumGrid', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        listAlbumsMock.mockReset()
        pushMock.mockReset()
    })

    it('shows centered empty state and navigates to settings when album list is empty', async () => {
        listAlbumsMock.mockResolvedValueOnce({
            rows: [],
            totalPageCount: 0,
            totalRowCount: 0,
        })

        const wrapper = mount(DashboardAlbumGrid)
        await flushView()

        expect(wrapper.text()).toContain('唱片架空空如也')
        expect(wrapper.text()).toContain('前往设置')

        const settingsButton = wrapper
            .findAll('button')
            .find((button) => button.text().includes('前往设置'))
        expect(settingsButton).toBeTruthy()

        await settingsButton!.trigger('click')

        expect(pushMock).toHaveBeenCalledTimes(1)
        expect(pushMock).toHaveBeenCalledWith({ name: 'settings' })
    })

    it('shows centered empty state when album request fails', async () => {
        listAlbumsMock.mockRejectedValueOnce(new Error('request failed'))

        const wrapper = mount(DashboardAlbumGrid)
        await flushView()

        expect(wrapper.text()).toContain('唱片架空空如也')
        expect(wrapper.text()).toContain('数据加载失败，请检查配置后重试。')
    })

    it('renders album cards and hides empty note when data exists', async () => {
        listAlbumsMock.mockResolvedValueOnce(buildAlbumPage())

        const wrapper = mount(DashboardAlbumGrid)
        await flushView()

        expect(wrapper.text()).toContain('Album A')
        expect(wrapper.text()).toContain('Artist A')
        expect(wrapper.text()).not.toContain('唱片架空空如也')
        expect(listAlbumsMock).toHaveBeenCalledWith({ pageIndex: 0, pageSize: 10 })
    })
})
