import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import tailwindcss from '@tailwindcss/vite'

const isTauri = !!process.env.TAURI_ENV_PLATFORM

const devServer = 'http://localhost:8654'
const devWebSocketServer = devServer.replace(/^http/i, 'ws')

// https://vite.dev/config/
export default defineConfig({
    plugins: [vue(), vueDevTools(), tailwindcss()],
    envPrefix: ['VITE_', 'TAURI_ENV_'],
    resolve: {
        alias: {
            '@': fileURLToPath(new URL('src', import.meta.url)),
        },
    },
    server: {
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
