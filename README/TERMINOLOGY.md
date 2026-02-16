# UniRhy 术语规范词典（中英双语）

## 目的与适用范围
- 本词典用于统一 UniRhy 项目的中英术语表达，避免同一概念在不同模块、文档、代码和界面中出现多种命名。
- 本词典适用于以下范围：`server`、`web`、`website`、根目录文档与协作沟通文档。
- 本词典是项目级标准源。子项目文档应引用本词典，不复制、不分叉维护。

## 使用规则
1. 同一概念必须只有一个标准词，中文与英文规范词需一一对应。
2. 中文标准词优先用于产品文案与中文文档；英文规范词优先用于代码标识、API 字段、英文文档。
3. 可接受别名仅用于补充说明，不作为新增内容默认写法。
4. 禁用词不得用于新增代码、文案、文档；历史内容应在修改时顺带归一。
5. 字段命名必须语义准确，不得使用与实际语义冲突的名称（例如使用 `username` 表示 `email`）。
6. 新增术语必须先补词典，再进入实现与评审流程。

## 词条模板
后续新增术语时，必须完整填写以下字段：

| 字段 | 说明 |
| --- | --- |
| `Term ID` | 稳定标识，如 `music.recording` |
| `中文标准词` | UI/文档首选中文 |
| `English Canonical` | 代码/API/英文文档首选词 |
| `可接受别名` | 可保留但不优先的写法 |
| `禁用词` | 不再使用的写法 |
| `替换建议` | 禁用词对应替换 |
| `定义` | 精确定义，限定边界 |
| `适用范围` | `UI` / `API` / `DB` / `文档` / `运营` |
| `示例` | 中英各 1 个简短示例 |

## 业务术语
### 品牌与导航
| Term ID | 中文标准词 | English Canonical | 可接受别名 | 禁用词 | 替换建议 | 定义 | 适用范围 | 示例 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `brand.unirhy` | 独一律 | UniRhy | UniRhy（中文文案中可保留） | Unirhy | 统一为 `UniRhy` | 项目品牌与产品名称。 | UI / 文档 / 运营 | 中：欢迎使用独一律。<br>EN: Welcome to UniRhy. |
| `nav.discovery` | 发现 | Discovery | Discover | - | - | 主页入口，展示推荐内容。 | UI / 文档 | 中：点击“发现”查看推荐。<br>EN: Open Discovery to view featured content. |
| `nav.library` | 资料库 | Library | - | 阅览室 | 统一替换为“资料库 / Library” | 浏览专辑与作品的主导航域。 | UI / 文档 | 中：在资料库浏览专辑与作品。<br>EN: Browse albums and works in Library. |
| `nav.task-management` | 任务管理 | Task Management | 任务中心 | - | - | 发起与查看后台任务的页面。 | UI / 文档 | 中：任务管理中可发起扫描。<br>EN: You can trigger scan tasks in Task Management. |
| `nav.system-settings` | 系统设置 | System Settings | 设置中心 | - | - | 系统配置与存储配置管理入口。 | UI / 文档 | 中：请在系统设置中管理存储节点。<br>EN: Manage storage nodes in System Settings. |

### 音乐实体
| Term ID | 中文标准词 | English Canonical | 可接受别名 | 禁用词 | 替换建议 | 定义 | 适用范围 | 示例 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `music.album` | 专辑 | Album | 唱片（非技术场景） | - | - | 专辑聚合实体，包含一组录音。 | UI / API / DB / 文档 | 中：该专辑收录 10 条录音。<br>EN: This album contains 10 recordings. |
| `music.work` | 作品 | Work | 乐曲（文案场景） | - | - | 音乐作品实体，可关联多个版本录音。 | UI / API / DB / 文档 | 中：此作品有多个演绎版本。<br>EN: This work has multiple recording versions. |
| `music.recording` | 录音 | Recording | 曲目（仅 UI 展示） | 歌曲（指代实体时） | 统一为“录音 / Recording” | 可播放实体，关联作品、艺人、媒体资产。 | UI / API / DB / 文档 | 中：请选择要编辑的录音。<br>EN: Select the recording to edit. |
| `music.playlist` | 歌单 | Playlist | 播放列表 | SongList（用于实体名） | 统一为“歌单 / Playlist” | 用户自定义的录音集合。 | UI / API / DB / 文档 | 中：将录音添加到歌单。<br>EN: Add the recording to a playlist. |
| `music.artist` | 艺术家 | Artist | 演奏者（说明性文案） | - | - | 参与录音演绎的实体。 | UI / API / DB / 文档 | 中：艺术家信息来自录音关联。<br>EN: Artist info is linked from recordings. |

### 账号认证
| Term ID | 中文标准词 | English Canonical | 可接受别名 | 禁用词 | 替换建议 | 定义 | 适用范围 | 示例 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `auth.account` | 账号 | Account | 账户 | - | - | 系统中的身份主体。 | UI / API / DB / 文档 | 中：账号可创建多个歌单。<br>EN: An account can create multiple playlists. |
| `auth.username` | 用户名 | Username | 账号名 | - | - | 账号显示名，不等同于邮箱。 | UI / API / 文档 | 中：用户名用于展示。<br>EN: Username is used for display. |
| `auth.email` | 邮箱 | Email | 电子邮箱 | `username`（表示邮箱时） | 字段改为 `email` | 登录与通知使用的邮箱字段。 | UI / API / 文档 | 中：请输入邮箱登录。<br>EN: Sign in with your email. |
| `auth.password` | 密码 | Password | 密钥（仅视觉文案） | Secure Key（作为字段名） | 字段统一为 `password` | 账号认证凭据。 | UI / API / 文档 | 中：请设置新密码。<br>EN: Set a new password. |
| `auth.admin-profile` | 管理员档案 | Admin Profile | 管理员账号信息 | - | - | 系统初始化阶段创建的管理员资料。 | UI / 文档 | 中：初始化时需要管理员档案。<br>EN: Admin Profile is required during initialization. |

### 任务系统
| Term ID | 中文标准词 | English Canonical | 可接受别名 | 禁用词 | 替换建议 | 定义 | 适用范围 | 示例 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `task.scan` | 媒体库扫描 | Scan Task | 扫描任务 | 媒体索引刷新（作任务名时） | 统一为“媒体库扫描 / Scan Task” | 从存储源扫描媒体并更新索引的后台任务。 | UI / API / 文档 | 中：媒体库扫描已提交。<br>EN: Scan task has been submitted. |
| `task.running-task` | 运行中任务 | Running Task | 活跃任务 | - | - | 当前状态为执行中的任务实例。 | UI / API / 文档 | 中：当前无运行中任务。<br>EN: There is no running task now. |

### 存储系统
| Term ID | 中文标准词 | English Canonical | 可接受别名 | 禁用词 | 替换建议 | 定义 | 适用范围 | 示例 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `storage.node` | 存储节点 | Storage Node | 存储提供方（抽象层） | 存储点（作实体名） | 统一为“存储节点 / Storage Node” | 系统可管理的具体存储配置单元。 | UI / API / 文档 | 中：请选择一个存储节点。<br>EN: Select a storage node. |
| `storage.fs-provider` | 文件系统存储提供方 | File System Provider | FS Provider | 本地盘（作类型名） | 统一为“文件系统存储提供方 / File System Provider” | 基于本地文件系统的存储类型。 | UI / API / DB / 文档 | 中：当前生效节点来自文件系统存储提供方。<br>EN: The active node uses File System Provider. |
| `storage.oss-provider` | OSS 存储提供方 | OSS Provider | 对象存储提供方 | 云盘（作类型名） | 统一为“OSS 存储提供方 / OSS Provider” | 基于对象存储服务的存储类型。 | UI / API / DB / 文档 | 中：扫描任务支持 OSS 存储提供方。<br>EN: Scan tasks support OSS Provider. |

## 技术术语
### 后端分层词汇
| Term ID | 中文标准词 | English Canonical | 可接受别名 | 禁用词 | 替换建议 | 定义 | 适用范围 | 示例 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `backend.controller` | 控制器层 | Controller | 接口层 | Handler（在本项目中指 Controller 时） | 统一为 `Controller` | 对外暴露 HTTP API 的入口层。 | API / 文档 | 中：新增接口应先定义 Controller。<br>EN: Add new endpoints in Controller first. |
| `backend.service` | 服务层 | Service | 业务层 | Manager（同义角色时） | 统一为 `Service` | 封装业务规则与流程编排。 | API / 文档 | 中：业务逻辑放在服务层。<br>EN: Business logic belongs to Service. |
| `backend.entity-model` | 实体模型 | Entity Model | Jimmer 实体 | 表对象（混用时） | 统一为 `Entity Model` | Jimmer `@Entity` 定义的核心领域模型。 | API / DB / 文档 | 中：实体模型变更需同步词典。<br>EN: Entity model changes must update glossary. |
| `backend.dto` | 数据传输对象 | DTO | 数据视图对象 | Model（泛指 DTO 时） | 统一为 `DTO` | API 输入输出承载的数据结构。 | API / 文档 | 中：接口返回 DTO。<br>EN: The endpoint returns a DTO. |

### 前端架构词汇
| Term ID | 中文标准词 | English Canonical | 可接受别名 | 禁用词 | 替换建议 | 定义 | 适用范围 | 示例 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `frontend.view` | 页面视图 | View | 页面 | Page（在 Vue 文件命名中） | 统一为 `View` | 路由级页面组件。 | UI / 文档 | 中：歌单详情是一个 View。<br>EN: Playlist detail is a View. |
| `frontend.component` | 组件 | Component | UI 组件 | Widget（作统一术语时） | 统一为 `Component` | 可复用界面单元。 | UI / 文档 | 中：播放器是全局组件。<br>EN: Audio player is a global component. |
| `frontend.store` | 状态存储 | Store | 状态仓库 | State Manager（冗余表述） | 统一为 `Store` | Pinia 管理的共享状态模块。 | UI / 文档 | 中：歌单列表由 Store 管理。<br>EN: Playlist list is managed by Store. |
| `frontend.route` | 路由 | Route | 路由项 | URL（指代路由对象时） | 统一为 `Route` | 页面导航规则与路径定义。 | UI / 文档 | 中：歌单详情路由是 `playlist/:id`。<br>EN: Playlist route is `playlist/:id`. |
| `frontend.composable` | 组合式函数 | Composable | 组合函数 | Hook（本项目 Vue 场景） | 统一为 `Composable` | Vue Composition API 复用逻辑模块。 | UI / 文档 | 中：存储管理逻辑在 Composable 中。<br>EN: Storage logic is implemented in a Composable. |

### 数据模型词汇
| Term ID | 中文标准词 | English Canonical | 可接受别名 | 禁用词 | 替换建议 | 定义 | 适用范围 | 示例 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `data.media-file` | 媒体文件 | MediaFile | 文件元数据 | ResourceFile（作实体名） | 统一为 `MediaFile` | 存储对象元信息，包含 `mimeType`、`size`、`objectKey`。 | API / DB / 文档 | 中：封面与音频都依赖 MediaFile。<br>EN: Cover and audio both rely on MediaFile. |
| `data.asset` | 资产关联 | Asset | 媒体资产 | Attachment（作实体名） | 统一为 `Asset` | `Recording` 与 `MediaFile` 的关联实体。 | API / DB / 文档 | 中：录音通过 Asset 关联音频文件。<br>EN: A recording links audio files via Asset. |
| `data.file-provider-type` | 存储提供方类型 | FileProviderType | Provider Type | StorageType（作代码类型名） | 统一为 `FileProviderType` | 存储类型枚举，值为 `FILE_SYSTEM` 与 `OSS`。 | API / DB / 文档 | 中：提交扫描任务需指定 FileProviderType。<br>EN: Scan request requires FileProviderType. |
| `data.task-type` | 任务类型 | TaskType | 任务枚举 | JobType（作代码类型名） | 统一为 `TaskType` | 后台任务类型枚举，当前主值为 `SCAN`。 | API / DB / 文档 | 中：当前 TaskType 为 `SCAN`。<br>EN: Current TaskType is `SCAN`. |

### 工程流程词汇
| Term ID | 中文标准词 | English Canonical | 可接受别名 | 禁用词 | 替换建议 | 定义 | 适用范围 | 示例 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `process.verify` | 质量校验 | Verify | 校验流程 | 一键检查（非规范名） | 统一为 `Verify` | 项目约定的 lint、类型检查与格式检查集合。 | 文档 / 运营 | 中：提交前先执行质量校验。<br>EN: Run Verify before commit. |
| `process.migration` | 数据库迁移 | Migration | 迁移脚本 | SQL 补丁（流程名） | 统一为 `Migration` | 通过 Flyway 管理的数据库结构演进脚本。 | DB / 文档 | 中：新增表必须提供 Migration。<br>EN: New tables must include a Migration. |
| `process.coverage-matrix` | 覆盖矩阵 | Coverage Matrix | API 覆盖矩阵 | 覆盖表（非正式） | 统一为 `Coverage Matrix` | 端到端测试覆盖登记矩阵文档。 | 文档 / 运营 | 中：接口变更后更新覆盖矩阵。<br>EN: Update Coverage Matrix after API changes. |

## 同指向冲突映射
| 概念 | 非标写法 | 统一后标准词 | 处理规则 |
| --- | --- | --- | --- |
| Playlist 实体 | `SongList`、歌单、播放列表 | 歌单 / Playlist | 代码与 API 统一 `Playlist`，UI 中文统一“歌单”。 |
| Recording 实体 | 录音、曲目、歌曲 | 录音 / Recording | 实体、接口、文档统一“录音 / Recording”；“曲目”仅允许 UI 展示层。 |
| 存储配置单元 | 存储点、存储节点、Provider | 存储节点 / Storage Node | 具体配置单元统一“存储节点”，`Provider` 仅保留抽象类型语义。 |
| 内容浏览入口 | 资料库、阅览室、Albums | 资料库 / Library | 导航与标题统一“资料库 / Library”，`Albums` 仅用于内部路由片段。 |
| 认证字段 email | 邮箱、电子邮箱、`username` | 邮箱 / Email | 字段名统一 `email`，禁止以 `username` 承载邮箱语义。 |

## 禁用词清单
| 禁用词 | 替换为 | 说明 |
| --- | --- | --- |
| `SongList`（用于实体名） | 歌单 / Playlist | 统一实体命名，避免与历史页面名混淆。 |
| 歌曲（指代 `Recording` 实体） | 录音 / Recording | “歌曲”可用于用户向文案，不得用于模型术语。 |
| 存储点（作实体名） | 存储节点 / Storage Node | 统一配置单元命名。 |
| 阅览室（与“资料库”并列混用） | 资料库 / Library | 导航入口只能保留一个标准词。 |
| `username`（表示邮箱时） | `email` | 字段语义必须与真实含义一致。 |
| `Secure Key`（作为密码字段名） | Password / 密码 | 视觉文案可表达安全含义，字段名必须标准化。 |
| `StorageType`（作代码类型名） | FileProviderType | 与后端枚举名保持一致。 |
| `JobType`（作代码类型名） | TaskType | 与后端枚举名保持一致。 |

## 维护流程
1. 术语新增或调整时，先在本词典新增/修改词条，再提交代码或文案变更。
2. 评审时必须检查：是否存在同义词新分叉、是否误用禁用词、是否更新冲突映射。
3. 涉及模型、接口、路由、核心文案变更时，必须同步检查对应词条。
4. 词条变更应在 PR 描述中明确列出影响范围（UI、API、DB、文档、运营）。
5. 每次版本迭代结束前，执行一次全量术语巡检，处理遗留混用项。
