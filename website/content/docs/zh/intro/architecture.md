---
title: 整体架构
description: UniRhy 后端、前端、桌面/移动端与插件的关系与数据流。
---

# 整体架构

UniRhy 以 monorepo 形式组织三个核心子项目，加上外部插件包。理解这张关系图有助于做部署决策与故障排查。

## 组成

### server（Spring Boot 后端）

- 技术栈：Spring Boot 4 · Kotlin · Gradle · JVM 25 · Jimmer ORM · PostgreSQL。
- 提供 REST API、WebSocket 同步通道、媒体文件签名访问。
- 持久化所有音乐元数据、账号、播放偏好、当前队列与播放恢复状态。
- 通过 Flyway 迁移管理数据库 schema。

### web（Vue 3 前端 / Tauri 2 桌面端）

- 技术栈：Vue 3 · TypeScript · Vite · Pinia · Tailwind CSS · Tauri 2。
- 浏览器中即是 Web 客户端；通过 Tauri 打包后变成 macOS / Windows / Android / iOS 桌面与移动客户端。
- API 客户端代码由 Jimmer 在编译时自动生成（位于 `/web/src/__generated`）。

### website（官网与文档）

- 技术栈：Vue 3 · Vite · SSR 预渲染。
- 你正在阅读的就是它。部署到 Cloudflare Workers。

### plugins（外部插件）

- 独立仓库（默认集合位于 `unirhy-plugins`），通过约定的接口由 server 加载。
- 用于元数据补全、艺术家名归一化、第三方音频处理等。

## 数据流

```
┌────────────┐        REST + WS         ┌─────────────┐
│   Client   │ ◀─────────────────────▶ │   Server    │
│ Web / Tauri│                          │ Spring Boot │
└────────────┘                          └──────┬──────┘
                                               │
                                  ┌────────────┼────────────┐
                                  ▼            ▼            ▼
                            ┌──────────┐  ┌─────────┐  ┌─────────┐
                            │PostgreSQL│  │ Storage │  │ Plugins │
                            │   (数据)  │  │ Local / │  │  JAR    │
                            └──────────┘  │   OSS   │  └─────────┘
                                          └─────────┘
```

- **REST**：所有 CRUD、列表、配置操作走 HTTP，使用 Sa-Token 维持 Cookie 登录态。
- **WebSocket**：播放同步专用通道 `/ws/playback-sync`，承载播放/暂停/Seek/队列变更广播。
- **Storage**：音频/封面文件存储节点可配置为本地目录或 OSS（S3 兼容）。
- **Plugins**：JAR 形式动态加载，通过约定接口处理元数据、归一化等任务。

## 部署形态

最简单的部署只需要两个进程：

- 一个 Spring Boot 进程（server）
- 一个 PostgreSQL 实例

前端构建产物（`web/dist`）由 server 作为静态资源直接服务，不需要单独的 nginx；如需 TLS 与多端口转发，可以前置反向代理。详见 [Docker 部署](/zh/docs/install/docker)。
