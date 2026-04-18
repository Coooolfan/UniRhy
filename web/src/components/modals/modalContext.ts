import { inject } from 'vue'

export type ModalContext<TResult = unknown> = {
    close: () => void
    resolve: (value: TResult) => void
    isTopmost: boolean
}

export const modalContextKey = Symbol('modal-context')

export const useModalContext = <TResult = unknown>() => {
    const context = inject<ModalContext<TResult> | null>(modalContextKey, null)

    if (!context) {
        throw new Error('useModalContext must be used inside a modal content component')
    }

    return context
}
