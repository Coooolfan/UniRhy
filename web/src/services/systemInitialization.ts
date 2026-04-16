import { api } from '@/ApiInstance'

export type InitializationStatus = {
    initialized: boolean
}

let initializationStatusPromise: Promise<InitializationStatus> | null = null

export const getInitializationStatus = () => {
    if (initializationStatusPromise === null) {
        initializationStatusPromise = api.systemConfigController.isInitialized()
    }

    return initializationStatusPromise
}

export const setInitializationStatus = (status: InitializationStatus) => {
    initializationStatusPromise = Promise.resolve(status)
}

export const resetInitializationStatus = () => {
    initializationStatusPromise = null
}
