export const ELECTRON_BRIDGE_CHANNELS = {
    getBackendUrl: 'unirhy:get-backend-url',
    setBackendUrl: 'unirhy:set-backend-url',
} as const

export type ElectronRuntimeBridge = {
    getBackendUrl: () => Promise<string>
    setBackendUrl: (backendUrl: string) => Promise<string>
}
