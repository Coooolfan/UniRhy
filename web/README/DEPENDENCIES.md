# Web 依赖库清单

本文档列出了 UniRhy Web 子项目所使用的所有依赖库及其开源协议。

## 生产依赖 (dependencies)

| 依赖库            | 版本     | 开源协议 | 描述                   |
| ----------------- | -------- | -------- | ---------------------- |
| @tailwindcss/vite | ^4.1.18  | MIT      | Tailwind CSS Vite 插件 |
| lucide-vue-next   | ^0.562.0 | ISC      | 图标库 Vue 组件        |
| pinia             | ^3.0.4   | MIT      | Vue 状态管理库         |
| tailwindcss       | ^4.1.18  | MIT      | 原子化 CSS 框架        |
| vue               | ^3.5.26  | MIT      | Vue.js 框架            |
| vue-router        | ^4.6.4   | MIT      | Vue 官方路由库         |

## 开发依赖 (devDependencies)

| 依赖库                   | 版本     | 开源协议   | 描述                       |
| ------------------------ | -------- | ---------- | -------------------------- |
| @tsconfig/node24         | ^24.0.3  | MIT        | Node.js 24 TypeScript 配置 |
| @types/jsdom             | ^27.0.0  | MIT        | JSDOM 类型定义             |
| @types/node              | ^24.10.4 | MIT        | Node.js 类型定义           |
| @vitejs/plugin-vue       | ^6.0.3   | MIT        | Vite Vue 插件              |
| @vue/test-utils          | ^2.4.6   | MIT        | Vue 测试工具库             |
| @vue/tsconfig            | ^0.8.1   | MIT        | Vue TypeScript 配置        |
| adm-zip                  | ^0.5.16  | MIT        | ZIP 文件处理库             |
| fs-extra                 | ^11.3.3  | MIT        | 增强的文件系统操作库       |
| jsdom                    | ^27.4.0  | MIT        | DOM 环境模拟库             |
| npm-run-all2             | ^8.0.4   | MIT        | 并行/串行运行 npm 脚本     |
| oxlint                   | ~1.38.0  | MIT        | 高性能 Linter              |
| oxfmt                    | ^0.32.0  | MIT        | 高性能代码格式化工具       |
| oxlint-tsgolint          | ^0.10.0  | MIT        | Oxlint Type-Aware 支持工具 |
| temp-dir                 | ^3.0.0   | MIT        | 获取临时目录路径           |
| typescript               | ~5.9.3   | Apache-2.0 | TypeScript 编译器          |
| uuid                     | ^13.0.0  | MIT        | UUID 生成库                |
| vite                     | beta     | MIT        | 下一代前端构建工具         |
| vite-plugin-vue-devtools | ^8.0.5   | MIT        | Vue DevTools Vite 插件     |
| vitest                   | ^4.0.16  | MIT        | Vite 原生测试框架          |

## 协议说明

- **MIT**: MIT License - 允许商业使用、修改、分发，需保留版权声明
- **ISC**: ISC License - 与 MIT 类似，更简洁的许可协议
- **Apache-2.0**: Apache License 2.0 - 允许商业使用、修改、分发，需保留版权和许可声明
