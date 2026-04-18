import type { Component } from 'vue'
import ModalMessageContent from '@/components/modals/ModalMessageContent.vue'
import { useModalStore, type ModalTone, type OpenModalOptions } from '@/stores/modal'

export const useModal = () => {
    const modalStore = useModalStore()

    const open = <
        TResult = unknown,
        TProps extends Record<string, unknown> = Record<string, unknown>,
    >(
        component: Component,
        options: OpenModalOptions<TProps> = {},
    ) => modalStore.open<TResult, TProps>(component, options)

    const alert = async (options: {
        title: string
        content: string
        confirmText?: string
        tone?: ModalTone
    }) => {
        await open<undefined>(ModalMessageContent, {
            title: options.title,
            tone: options.tone ?? 'default',
            size: 'sm',
            props: {
                content: options.content,
                confirmText: options.confirmText ?? '确认',
                mode: 'alert',
            },
        })
    }

    const confirm = async (options: {
        title: string
        content: string
        confirmText?: string
        cancelText?: string
        tone?: ModalTone
    }) => {
        const result = await open<boolean>(ModalMessageContent, {
            title: options.title,
            tone: options.tone ?? 'default',
            size: 'sm',
            props: {
                content: options.content,
                confirmText: options.confirmText ?? '确认',
                cancelText: options.cancelText ?? '取消',
                mode: 'confirm',
            },
        })

        return result ?? false
    }

    return {
        open,
        alert,
        confirm,
    }
}
