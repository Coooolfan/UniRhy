import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'

const pushMock = vi.fn()

vi.mock('vue-router', () => ({
    useRoute: () => ({
        query: { tab: 'Works' },
    }),
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
            },
            workController: {
                listWork: vi.fn(),
            },
        },
    }
})

import { api } from '@/ApiInstance'
import AlbumListView from '@/views/AlbumListView.vue'

const listAlbumsMock = vi.mocked(api.albumController.listAlbums)
const listWorkMock = vi.mocked(api.workController.listWork)

const flushView = async () => {
    await Promise.resolve()
    await nextTick()
    await Promise.resolve()
    await nextTick()
}

const buildWorkPage = () => ({
    rows: [
        {
            id: 101,
            title: 'Moon Work',
            recordings: [
                {
                    id: 201,
                    kind: 'Studio',
                    comment: '',
                    durationMs: 180000,
                    defaultInWork: true,
                    assets: [],
                    artists: [{ id: 301, displayName: 'Artist A', alias: [], comment: '' }],
                    cover: undefined,
                },
            ],
        },
        {
            id: 102,
            title: 'Sun Work',
            recordings: [
                {
                    id: 202,
                    kind: 'Live',
                    comment: '',
                    durationMs: 200000,
                    defaultInWork: true,
                    assets: [],
                    artists: [{ id: 302, displayName: 'Artist B', alias: [], comment: '' }],
                    cover: undefined,
                },
            ],
        },
    ],
    totalPageCount: 1,
    totalRowCount: 2,
})

describe('AlbumListView', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        pushMock.mockReset()
        listAlbumsMock.mockReset()
        listWorkMock.mockReset()
    })

    it('does not filter works when typing in the dashboard top bar', async () => {
        listWorkMock.mockResolvedValueOnce(buildWorkPage())

        const wrapper = mount(AlbumListView, {
            global: {
                stubs: {
                    DashboardTopBar: {
                        template:
                            '<input data-testid="topbar-search" @input="$emit(\'update:modelValue\', $event.target.value)" />',
                    },
                    AlbumGridCard: {
                        template: '<div />',
                    },
                    WorkGridCard: {
                        props: ['title', 'subtitle'],
                        template:
                            '<div class="work-card">{{ title }}<span class="subtitle">{{ subtitle }}</span></div>',
                    },
                },
            },
        })

        await flushView()

        expect(wrapper.text()).toContain('Moon Work')
        expect(wrapper.text()).toContain('Sun Work')

        await wrapper.get('[data-testid="topbar-search"]').setValue('Moon')
        await flushView()

        expect(wrapper.text()).toContain('Moon Work')
        expect(wrapper.text()).toContain('Sun Work')
        expect(pushMock).not.toHaveBeenCalled()
    })
})
