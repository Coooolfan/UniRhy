Tauri 2 发行可行性评估

基于对项目的全面分析，UniRhy 是一个 Vue 3 + Spring Boot 的音乐管理应用，前端使用原生 fetch + Jimmer 自动生成的 TS SDK，WebSocket
用于播放同步，Cookie 认证。以下是评估：

---
1. 网络请求：核心难点

现状： 前端所有请求通过 Vite 代理转发到 localhost:8654，使用浏览器原生 fetch + Cookie 认证。

Tauri 2 的影响：

- Tauri 2 的 WebView 可以直接发 HTTP 请求，不需要通过 Rust 后台转发。因为 UniRhy 的后端是独立部署的（不是嵌入式），WebView 直接请求后端 API 即可。
- 不需要 Tauri 侧转发代理。只需把 API base URL 从相对路径改为可配置的绝对路径（如 https://your-server.com/api）。
- Cookie 认证在 Tauri WebView 中可能有跨域问题。建议改为 Authorization: Bearer <token> header 认证，这对 Web 端同样兼容，且更通用。SA-Token
后端已支持 header 模式。
- WebSocket 同理，URL 需要从相对路径改为绝对路径，无本质障碍。

对 Web 发行的影响：几乎为零。 改为可配置 base URL + Bearer token 是向后兼容的改进，Web 端同样受益。

2. 代码结构变更方案

UniRhy/
├── web/                    # 保持不变，纯 Vue 3 SPA
│   ├── src/
│   │   ├── ApiInstance.ts  # 改造：base URL 可配置，认证改 header
│   │   ├── platform/       # 新增：平台抽象层（极薄）
│   │   │   ├── index.ts    # 统一导出
│   │   │   ├── web.ts      # Web 平台实现
│   │   │   └── tauri.ts    # Tauri 平台实现
│   │   └── ...
├── src-tauri/              # 新增：Tauri 2 壳
│   ├── Cargo.toml
│   ├── tauri.conf.json
│   ├── src/
│   │   └── main.rs         # 极简，基本只需窗口配置
│   └── capabilities/       # Tauri 2 权限声明
└── ...

3. 需要平台抽象的部分（很少）

│      功能       │       Web       │        Tauri         │            变更量            │
│ API Base URL    │ 相对路径 /api   │ 绝对路径（用户配置） │ ApiInstance.ts 改一处        │
│ 认证            │ Cookie → Header │ Header               │ ApiInstance.ts 改一处        │
│ WebSocket URL   │ /ws/...         │ 绝对 wss://...       │ playbackSyncClient.ts 改一处 │
│ 文件下载/缓存   │ 浏览器默认      │ 可选用 Tauri FS API  │ 可选增强，非必须             │
│ 系统托盘/媒体键 │ 无              │ Tauri 插件           │ 新增功能，不影响 Web         │
│ 窗口标题/状态   │ 无              │ Tauri Window API     │ 新增功能，不影响 Web         │

1. 代码复用率

预估 95%+ 的前端代码完全复用，无需修改。

需要改动的仅是：
- ApiInstance.ts：约 10 行，base URL + 认证方式可配置化
- playbackSyncClient.ts：约 3 行，WebSocket URL 可配置化
- 新增 src-tauri/ 目录：Tauri 壳，约 100-200 行 Rust 代码（窗口配置、基础能力声明）

5. 可行性结论

┌───────────────┬──────────────────────────────────────────────────────┐
│     维度      │                         评估                         │
├───────────────┼──────────────────────────────────────────────────────┤
│ 技术可行性    │ ✅ 高。Vue 3 + Vite 是 Tauri 2 一等公民支持          │
├───────────────┼──────────────────────────────────────────────────────┤
│ 网络请求改造  │ ✅ 简单。不需要 Rust 转发，直接 WebView 请求         │
├───────────────┼──────────────────────────────────────────────────────┤
│ 对 Web 端影响 │ ✅ 极小。base URL 配置化 + header 认证是通用改进     │
├───────────────┼──────────────────────────────────────────────────────┤
│ 代码复用      │ ✅ 95%+，几乎全部复用                                │
├───────────────┼──────────────────────────────────────────────────────┤
│ 主要工作量    │ Tauri 壳搭建 + 平台特有功能（托盘、媒体键等）        │
├───────────────┼──────────────────────────────────────────────────────┤
│ 风险点        │ Cookie → Header 认证迁移需后端配合确认 SA-Token 配置 │
└───────────────┴──────────────────────────────────────────────────────┘

总结： UniRhy 的架构非常适合加入 Tauri 2 发行。因为后端是独立部署的，前端只是一个 SPA，不需要 Tauri 侧做请求转发。核心改动是把 API/WS 的 URL
从硬编码相对路径改为可配置，认证从 Cookie 改为 Header（对两端都更好）。前端代码几乎 100% 复用。
