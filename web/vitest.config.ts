import { mergeConfig, defineConfig, configDefaults } from 'vitest/config'
import viteConfig from './vite.config'

export default mergeConfig(
    viteConfig,
    defineConfig({
        test: {
            environment: 'jsdom',
            // 部分用例通过 vi.resetModules() 重建模块图，worker 冷启动导入大图可能超过默认 5s
            testTimeout: 15000,
            exclude: [...configDefaults.exclude, 'e2e/**'],
            root: import.meta.dirname,
            setupFiles: ['./src/__tests__/setup.ts'],
        },
    }),
)
