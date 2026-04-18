import { defineStore } from 'pinia'
import { markRaw, ref, type Component } from 'vue'

export type ModalTone = 'default' | 'danger'
export type ModalSize = 'sm' | 'md' | 'lg' | 'xl'

export type OpenModalOptions<Props extends Record<string, unknown> = Record<string, unknown>> = {
    props?: Props
    title?: string
    tone?: ModalTone
    size?: ModalSize
    closable?: boolean
    closeOnBackdrop?: boolean
    closeOnEscape?: boolean
}

export type ModalEntry = {
    id: number
    component: Component
    props: Record<string, unknown>
    title: string
    tone: ModalTone
    size: ModalSize
    closable: boolean
    closeOnBackdrop: boolean
    closeOnEscape: boolean
    resolve: (value: unknown) => void
}

const DEFAULT_MODAL_OPTIONS = {
    title: '',
    tone: 'default',
    size: 'md',
    closable: true,
    closeOnBackdrop: true,
    closeOnEscape: true,
} satisfies Omit<Required<OpenModalOptions>, 'props'>

export const useModalStore = defineStore('modal', () => {
    const stack = ref<ModalEntry[]>([])
    let nextModalId = 1

    const open = <
        TResult = unknown,
        TProps extends Record<string, unknown> = Record<string, unknown>,
    >(
        component: Component,
        options: OpenModalOptions<TProps> = {},
    ) =>
        new Promise<TResult | null>((resolve) => {
            stack.value.push({
                id: nextModalId++,
                component: markRaw(component),
                props: options.props ?? {},
                title: options.title ?? DEFAULT_MODAL_OPTIONS.title,
                tone: options.tone ?? DEFAULT_MODAL_OPTIONS.tone,
                size: options.size ?? DEFAULT_MODAL_OPTIONS.size,
                closable: options.closable ?? DEFAULT_MODAL_OPTIONS.closable,
                closeOnBackdrop: options.closeOnBackdrop ?? DEFAULT_MODAL_OPTIONS.closeOnBackdrop,
                closeOnEscape: options.closeOnEscape ?? DEFAULT_MODAL_OPTIONS.closeOnEscape,
                resolve: (value) => {
                    // oxlint-disable-next-line typescript-eslint/no-unsafe-type-assertion
                    resolve(value as TResult | null)
                },
            })
        })

    const resolveById = (id: number, value: unknown) => {
        const entryIndex = stack.value.findIndex((entry) => entry.id === id)
        if (entryIndex === -1) {
            return
        }

        const [entry] = stack.value.splice(entryIndex, 1)
        entry?.resolve(value)
    }

    const closeById = (id: number) => {
        resolveById(id, null)
    }

    const closeTop = () => {
        // oxlint-disable-next-line unicorn/prefer-at
        const topEntry = stack.value.slice(-1)[0]
        if (!topEntry) {
            return
        }

        closeById(topEntry.id)
    }

    return {
        stack,
        open,
        resolveById,
        closeById,
        closeTop,
    }
})
