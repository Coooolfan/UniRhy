import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import vueInspectorCopy from 'vite-plugin-vue-inspector-copy'
import tailwindcss from '@tailwindcss/vite'

const isTauri = !!process.env.TAURI_ENV_PLATFORM
const inspectorToggleComboKey = process.platform === 'darwin' ? 'meta-shift' : 'control-shift'

const devServer = 'http://localhost:8654'
const devWebSocketServer = devServer.replace(/^http/iu, 'ws')

// https://vite.dev/config/
export default defineConfig({
    plugins: [
        vue(),
        vueDevTools({
            componentInspector: {
                toggleComboKey: inspectorToggleComboKey,
            },
        }),
        vueInspectorCopy(),
        tailwindcss(),
    ],
    envPrefix: ['VITE_', 'TAURI_ENV_'],
    resolve: {
        alias: {
            '@': fileURLToPath(new URL('src', import.meta.url)),
        },
    },
    server: {
        port: 8655,
        host: isTauri ? '0.0.0.0' : undefined,
        ...(!isTauri && {
            proxy: {
                '/api': {
                    target: devServer,
                },
                '/ws': {
                    target: devWebSocketServer,
                    ws: true,
                },
            },
        }),
    },
})
