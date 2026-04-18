import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useModal } from '@/composables/useModal'
import { useModalStore } from '@/stores/modal'

describe('useModal', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    it('returns a promise from open and resolves by modal id', async () => {
        const modal = useModal()
        const store = useModalStore()
        const pending = modal.open<{ saved: boolean }>(
            {
                name: 'InlineComponent',
                template: '<div />',
            },
            {
                title: '自定义弹窗',
            },
        )

        expect(store.stack).toHaveLength(1)
        expect(store.stack[0]?.title).toBe('自定义弹窗')

        store.resolveById(store.stack[0].id, { saved: true })

        await expect(pending).resolves.toEqual({ saved: true })
        expect(store.stack).toHaveLength(0)
    })

    it('keeps modal stack order and closes the top entry first', async () => {
        const store = useModalStore()

        const first = store.open(
            {
                name: 'FirstModal',
                template: '<div />',
            },
            { title: 'first' },
        )
        const second = store.open(
            {
                name: 'SecondModal',
                template: '<div />',
            },
            { title: 'second' },
        )

        expect(store.stack.map((entry) => entry.title)).toEqual(['first', 'second'])

        store.closeTop()
        await expect(second).resolves.toBeNull()
        expect(store.stack.map((entry) => entry.title)).toEqual(['first'])

        store.closeTop()
        await expect(first).resolves.toBeNull()
        expect(store.stack).toHaveLength(0)
    })

    it('maps confirm resolution to booleans', async () => {
        const modal = useModal()
        const store = useModalStore()

        const confirmedPromise = modal.confirm({
            title: '确认删除',
            content: '确认吗',
        })
        store.resolveById(store.stack[0].id, true)
        await expect(confirmedPromise).resolves.toBe(true)

        const dismissedPromise = modal.confirm({
            title: '确认删除',
            content: '确认吗',
        })
        store.closeById(store.stack[0].id)
        await expect(dismissedPromise).resolves.toBe(false)
    })
})
