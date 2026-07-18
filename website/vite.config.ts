import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import tailwindcss from '@tailwindcss/vite'
import blogPlugin from './plugins/blog'

const inspectorToggleComboKey = process.platform === 'darwin' ? 'meta-shift' : 'control-shift'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    blogPlugin(),
    vue(),
    vueDevTools({
      componentInspector: {
        toggleComboKey: inspectorToggleComboKey,
        launchEditor: 'zed',
      },
    }),
    // vueInspectorCopy(),
    tailwindcss(),
  ],
  server: {
    allowedHosts: ['mini.home.coooolfan.com'],
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('src', import.meta.url)),
    },
  },
  build: {
    manifest: true,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (
            id.includes('/three/build/three.webgpu.js') ||
            id.includes('/three/build/three.tsl.js')
          ) {
            return 'three-webgpu'
          }

          return id.includes('/three/') ? 'three' : undefined
        },
      },
    },
  },
})
