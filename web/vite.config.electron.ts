import { builtinModules } from 'node:module'
import { fileURLToPath } from 'node:url'

import { defineConfig } from 'vite'

const resolveFromRoot = (relativePath: string) => {
    return fileURLToPath(new URL(relativePath, import.meta.url))
}

const externalModules = [
    'electron',
    ...builtinModules,
    ...builtinModules.map((module) => `node:${module}`),
]

const createElectronBuildConfig = ({
    emptyOutDir,
    entry,
    outputName,
}: {
    emptyOutDir: boolean
    entry: string
    outputName: string
}) => {
    return defineConfig({
        build: {
            emptyOutDir,
            lib: {
                entry: resolveFromRoot(entry),
                fileName: () => `${outputName}.cjs`,
                formats: ['cjs'],
            },
            minify: false,
            outDir: 'dist-electron',
            rollupOptions: {
                external: externalModules,
                output: {
                    codeSplitting: false,
                },
            },
            sourcemap: true,
            target: 'node20',
        },
    })
}

export default defineConfig(({ mode }) => {
    if (mode === 'electron-preload') {
        return createElectronBuildConfig({
            emptyOutDir: false,
            entry: './electron/preload.ts',
            outputName: 'preload',
        })
    }

    return createElectronBuildConfig({
        emptyOutDir: true,
        entry: './electron/main.ts',
        outputName: 'main',
    })
})
