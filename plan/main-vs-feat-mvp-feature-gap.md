# `main` vs `feat/mvp` 功能点差异梳理

## 基线说明

- 仓库当前不存在 `master` 分支，本次以默认分支 `main` 作为“master”对应基线。
- 对比范围为 `main...feat/mvp`。
- 结论按“可单独重构、审查、实现的功能点”组织，不把测试、生成代码、纯重构文件直接当作独立功能点。

## 总结

- `feat/mvp` 相比 `main` 主要多出 13 组提交，核心集中在两条主线：
- 播放链路：当前队列、跨会话恢复、离线自动暂停、系统媒体控制、调试增强。
- AI/任务链路：AI 模型配置、向量化、数据清洗、智能歌单生成，以及围绕这些任务的调度与前端入口。
- `main` 相比 `feat/mvp` 仅多出 2 组非核心功能提交：
- 全站语言状态管理重命名/统一。
- Cloudflare Workers 部署配置与 `@vue/tsconfig` 升级。

## 功能点清单

### 1. 当前播放队列由前端临时状态升级为服务端托管能力

- 新增服务端当前队列接口：获取、替换、追加、重排、切换当前曲目、修改播放策略、上一首、下一首、删除条目、清空队列。
- WebSocket 协议新增队列变更广播，前端音频 Store 从“单曲播放”升级为“队列 + 当前曲目 + 队列导航”模型。
- 播放器新增当前队列侧边栏、上一首/下一首按钮、队列展开入口。
- 这是后续“播放恢复”“自动推进”“断线暂停”的基础能力。
- 关键位置：
- `server/src/main/kotlin/com/coooolfan/unirhy/controller/PlaybackQueueController.kt`
- `server/src/main/kotlin/com/coooolfan/unirhy/sync/service/CurrentQueueService.kt`
- `server/src/main/kotlin/com/coooolfan/unirhy/sync/protocol/PlaybackSyncProtocol.kt`
- `web/src/stores/audio.ts`
- `web/src/components/CurrentQueueSidebar.vue`
- `web/src/components/AudioPlayer.vue`

### 2. 播放状态跨会话恢复，且最后设备离线时自动暂停

- 当前播放状态和当前位置被持久化到数据库，重新进入会话后可以从暂停状态恢复。
- 当前队列也被持久化，队列与播放状态不再依赖单个浏览器内存。
- 当最后一个在线设备离开同步房间时，系统会自动下发暂停，而不是让服务端继续保持“播放中”。
- 这部分是播放链路中的第二层能力，依赖功能点 1。
- 关键位置：
- `server/src/main/kotlin/com/coooolfan/unirhy/sync/service/PlaybackResumeStateStore.kt`
- `server/src/main/kotlin/com/coooolfan/unirhy/sync/service/PlaybackSessionService.kt`
- `server/src/main/kotlin/com/coooolfan/unirhy/sync/service/PlaybackSyncSessionRemovalCoordinator.kt`
- `server/src/main/resources/db/migration/V0.0.10__account_current_queue.sql`
- `server/src/main/resources/db/migration/V0.0.11__account_playback_resume_state.sql`

### 3. 播放偏好、系统媒体控制与调试页增强

- 账户新增播放偏好，允许围绕原始音频 / Opus / MP3 做播放资源选择。
- 前端新增 Media Session API 集成，支持系统级播放控制。
- 播放调试页不再只展示同步时序，还会展示当前音频文件格式、码率、采样率、声道数、文件大小等元数据。
- 这组能力依赖播放链路已稳定，但可以在功能点 1 和 2 之后独立落地。
- 关键位置：
- `server/src/main/kotlin/com/coooolfan/unirhy/model/AccountPreferences.kt`
- `server/src/main/kotlin/com/coooolfan/unirhy/model/PlaybackPreference.kt`
- `server/src/main/resources/db/migration/V0.0.8__account_preferences.sql`
- `web/src/stores/user.ts`
- `web/src/composables/useMediaSession.ts`
- `web/src/views/PlaybackSyncDebugView.vue`

### 4. 专辑 / 歌单录音关联改为可排序模型，支持顺序持久化

- `Album`、`Playlist` 与 `Recording` 的关系从简单 `ManyToMany` 升级为带 `sortOrder` 的中间实体。
- 歌单新增稳定的服务器端顺序写入逻辑，支持新增、删除、重排后保持顺序。
- 录音合并时，目标录音会继承原录音所在专辑/歌单的关联关系，并补齐排序值。
- 这是“录音拖拽排序”和“AI 歌单生成后按顺序写入”的数据层基础。
- 关键位置：
- `server/src/main/kotlin/com/coooolfan/unirhy/model/AlbumRecording.kt`
- `server/src/main/kotlin/com/coooolfan/unirhy/model/PlaylistRecording.kt`
- `server/src/main/kotlin/com/coooolfan/unirhy/model/Album.kt`
- `server/src/main/kotlin/com/coooolfan/unirhy/model/Playlist.kt`
- `server/src/main/kotlin/com/coooolfan/unirhy/service/PlaylistService.kt`
- `server/src/main/kotlin/com/coooolfan/unirhy/service/RecordingService.kt`
- `server/src/main/resources/db/migration/V0.0.9__sort_order_for_album_playlist_recording.sql`

### 5. 录音列表拖拽排序与录音编辑增强

- `AlbumDetailView`、`PlaylistDetailView`、`WorkDetailView` 都支持拖拽排序。
- Album / Work 侧目前是“当前设备本地顺序”体验，使用本地存储保存。
- Playlist 侧是“服务端顺序”体验，会真正改写歌单中的录音顺序。
- 录音编辑逻辑被抽到复用 composable，编辑弹窗新增“相似歌曲”折叠区。
- 其中“相似歌曲”依赖向量化能力，因此建议把它当成此功能点里的子项，而不是前置项。
- 关键位置：
- `web/src/utils/recordingOrder.ts`
- `web/src/views/AlbumDetailView.vue`
- `web/src/views/PlaylistDetailView.vue`
- `web/src/views/WorkDetailView.vue`
- `web/src/components/recording/RecordingEditModal.vue`
- `web/src/composables/useRecordingEditor.ts`

### 6. 系统设置新增 AI 模型配置中心

- 系统配置从“只配置存储节点”扩展为“存储节点 + Completion 模型 + Embedding 模型”。
- 可以分别配置 endpoint、model、api key、request format。
- 这是向量化、数据清洗、歌单生成三个 AI 任务的共同前置条件。
- 关键位置：
- `server/src/main/dto/SystemConfig.dto`
- `server/src/main/kotlin/com/coooolfan/unirhy/model/AiModelConfig.kt`
- `web/src/components/settings/AiModelConfigSection.vue`
- `web/src/composables/useStorageSettings.ts`
- `web/src/views/SettingsView.vue`

### 7. 向量化任务链路

- `Recording` 新增 `lyrics` 与 `embedding` 字段，后端可调用 Embedding API 为录音生成向量。
- 异步任务系统新增 `VECTORIZE`，支持 `ALL` / `PENDING_ONLY` 两种模式。
- 基于向量相似度新增“相似录音”查询接口，为后续推荐和歌单生成提供底层能力。
- 这是所有 AI 推荐能力的底座，建议优先于数据清洗和歌单生成落地。
- 关键位置：
- `server/src/main/kotlin/com/coooolfan/unirhy/model/Recording.kt`
- `server/src/main/kotlin/com/coooolfan/unirhy/model/Embedding.kt`
- `server/src/main/kotlin/com/coooolfan/unirhy/service/task/VectorizeTaskService.kt`
- `server/src/main/kotlin/com/coooolfan/unirhy/controller/TaskController.kt`
- `server/src/main/kotlin/com/coooolfan/unirhy/controller/RecordingController.kt`
- `web/src/components/tasks/TaskSubmissionVectorizeForm.vue`
- `web/src/views/TasksView.vue`

### 8. 数据清洗任务链路

- 异步任务系统新增 `DATA_CLEAN`，批量调用 Completion 模型对录音标题做规则化清洗。
- 数据清洗结果直接回写录音标题。
- 这项能力业务上独立，但技术上依赖功能点 6。
- 关键位置：
- `server/src/main/kotlin/com/coooolfan/unirhy/service/task/DataCleanTaskService.kt`
- `server/src/main/kotlin/com/coooolfan/unirhy/controller/TaskController.kt`
- `web/src/components/tasks/TaskSubmissionDataCleanForm.vue`
- `web/src/views/TasksView.vue`

### 9. 智能歌单生成

- 异步任务系统新增 `PLAYLIST_GENERATE`，用户提交自然语言描述后，系统会：
- 先把描述向量化。
- 再从已有 embedding 中做相似录音检索。
- 再调用 Completion 模型生成歌单名称/说明。
- 最后创建歌单并按顺序写入录音。
- 前端新增任务提交表单和完成后的歌单刷新监控。
- 这是面向用户最完整的 AI 功能，依赖功能点 4、6、7。
- 关键位置：
- `server/src/main/kotlin/com/coooolfan/unirhy/service/task/PlaylistGenerateTaskService.kt`
- `web/src/components/tasks/TaskSubmissionPlaylistGenerateForm.vue`
- `web/src/composables/usePlaylistGenerateMonitor.ts`
- `web/src/views/TasksView.vue`

### 10. 异步任务平台从 2 类任务扩展到 5 类任务，并引入指数退避调度

- 任务类型从 `METADATA_PARSE`、`TRANSCODE` 扩展到 `VECTORIZE`、`DATA_CLEAN`、`PLAYLIST_GENERATE`。
- 任务提交弹窗拆分成独立表单组件，任务看板和摘要文案同步扩展。
- 服务端调度器从固定频率轮询改为指数退避，空闲时自动降频，任务池线程数也随之扩容。
- 这部分更适合作为“支撑能力”实现，不建议先单独做，而应与 7、8、9 一起推进。
- 关键位置：
- `server/src/main/kotlin/com/coooolfan/unirhy/service/task/common/TaskType.kt`
- `server/src/main/kotlin/com/coooolfan/unirhy/config/ExponentialBackoffPollingTrigger.kt`
- `server/src/main/kotlin/com/coooolfan/unirhy/config/TaskSchedulingConfig.kt`
- `web/src/components/tasks/TaskSubmissionModal.vue`
- `web/src/components/tasks/taskSubmissionShared.ts`
- `web/src/views/TasksView.vue`

## 建议实施顺序

### 第一阶段：先把播放主链路补齐

- 1. 当前播放队列服务端化。
- 2. 播放状态恢复 + 最后设备离线自动暂停。
- 3. 播放偏好 + Media Session + 调试页增强。

### 第二阶段：补齐录音顺序与数据模型

- 4. 专辑 / 歌单可排序关联模型。
- 5. Album / Playlist / Work 的拖拽排序与录音编辑增强。

### 第三阶段：补齐 AI 基础设施

- 6. AI 模型配置中心。
- 7. 向量化任务链路。

### 第四阶段：在 AI 底座上实现业务能力

- 8. 数据清洗任务。
- 9. 智能歌单生成。
- 10. 与 7/8/9 同步完成任务平台扩容和指数退避调度。

## `main` 独有但不建议单列为功能点的差异

- 语言切换 composable 的命名和全站状态管理统一，更偏向内部整理。
- Cloudflare Workers 部署配置、`@vue/tsconfig` 升级，更偏向工程/部署能力。

## 建议的后续工作方式

- 每次只挑一个功能点开工，不把多个功能点混在同一轮重构里。
- 每个功能点都先补“数据模型 / 协议 / 页面入口 / 测试”四个检查项，避免只迁移 UI 或只迁移后端。
- 对于 AI 相关功能，优先把“配置入口 + 任务提交 + 失败反馈”作为同一批次完成，避免出现只能跑 happy path 的半成品。
