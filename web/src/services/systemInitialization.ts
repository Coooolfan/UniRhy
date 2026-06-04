import { api } from '@/ApiInstance'
import type { SystemStatus } from '@/__generated/model/static'

export type InitializationStatus = SystemStatus

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
