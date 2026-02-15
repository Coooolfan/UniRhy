# Website 依赖库清单

本文档列出了 UniRhy Website 子项目所使用的所有依赖库及其开源协议。

## 生产依赖 (dependencies)

| 依赖库       | 版本     | 开源协议             | 描述               |
| ------------ | -------- | -------------------- | ------------------ |
| @types/three | ^0.182.0 | MIT                  | Three.js 类型定义  |
| three        | ^0.182.0 | MIT                  | 3D 图形库          |
| vue          | ^3.5.25  | MIT                  | Vue.js 框架        |
| vue-bits     | -        | MIT + Commons Clause | Vue 动画交互组件库 |

## 开发依赖 (devDependencies)

| 依赖库                   | 版本     | 开源协议   | 描述                       |
| ------------------------ | -------- | ---------- | -------------------------- |
| @tsconfig/node24         | ^24.0.3  | MIT        | Node.js 24 TypeScript 配置 |
| @types/node              | ^24.10.1 | MIT        | Node.js 类型定义           |
| @vitejs/plugin-vue       | ^6.0.2   | MIT        | Vite Vue 插件              |
| @vue/tsconfig            | ^0.8.1   | MIT        | Vue TypeScript 配置        |
| npm-run-all2             | ^8.0.4   | MIT        | 并行/串行运行 npm 脚本     |
| oxlint                   | ~1.38.0  | MIT        | 高性能 Linter              |
| oxfmt                    | ^0.32.0  | MIT        | 高性能代码格式化工具       |
| oxlint-tsgolint          | ^0.10.0  | MIT        | Oxlint Type-Aware 支持工具 |
| typescript               | ~5.9.0   | Apache-2.0 | TypeScript 编译器          |
| vite (rolldown-vite)     | latest   | MIT        | 下一代前端构建工具         |
| vite-plugin-vue-devtools | ^8.0.5   | MIT        | Vue DevTools Vite 插件     |

## 包管理器

| 工具 | 版本   | 开源协议     | 描述     |
| ---- | ------ | ------------ | -------- |
| Yarn | 4.12.0 | BSD-2-Clause | 包管理器 |

## 协议说明

- **MIT**: MIT License - 允许商业使用、修改、分发，需保留版权声明
- **MIT + Commons Clause**: MIT License 附加 Commons Clause - 允许非商业使用、修改、分发，禁止销售软件本身
- **Apache-2.0**: Apache License 2.0 - 允许商业使用、修改、分发，需保留版权和许可声明
- **BSD-2-Clause**: BSD 2-Clause License - 允许商业使用、修改、分发，需保留版权声明
