const isTauri = () => typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window

type RuntimeFetch = typeof fetch

let runtimeFetchPromise: Promise<RuntimeFetch> | null = null

export const getRuntimeFetch = (): Promise<RuntimeFetch> => {
    runtimeFetchPromise ??= isTauri()
        ? import('@tauri-apps/plugin-http').then((plugin) => plugin.fetch as RuntimeFetch)
        : Promise.resolve(window.fetch.bind(window))

    return runtimeFetchPromise
}

export const runtimeFetch: RuntimeFetch = async (input, init) => {
    const fetchImpl = await getRuntimeFetch()
    return fetchImpl(input, init)
}
