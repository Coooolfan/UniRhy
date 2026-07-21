# 插件 API 扩容与异步任务优化

> 本文档跟踪 `feat/plugin-api` 分支的工作：记录目的、背景与已确定的结论。结论随工作推进持续追加。

## 目的

1. **插件 API 扩容**：扩展 WASM 插件可用的 Host API 面，使插件能够覆盖更多业务场景，而不仅限于当前的 Artist 操作。
2. **异步任务优化**：改造异步任务基础设施，使其适配插件任务的规模与形态（并发消费、吞吐、可观测性）。

## 背景（现状）

### 插件运行时

- 插件为 WASM 模块，由 [Chicory](https://github.com/dylibso/chicory) 在 JVM 内解释执行，ABI 固定为 `unirhy-wasm-abi-v1`。
- 插件包由 `plugin.yml`（manifest）与 `plugin.wasm` 组成，manifest 声明 `id/version/runtime/tasks/permissions/form`，上传后持久化在数据库（`Plugin` 实体），启动时从库加载、支持热重载（`PluginTaskService.reloadPlugin`）。
- 插件导出两个入口：
  - `plan(paramsJson) -> List<payloadJson>`：将一次表单提交拆分为若干任务载荷；
  - `run(payloadJson)`：执行单个任务。
- 内存交换协议：guest 导出 `alloc/dealloc`，host 写入参数、读取 `(ptr << 32 | len)` 打包返回值（`WasmPlugin.callJson`）。

### 现有 Host API

| 函数 | 说明 |
|---|---|
| `host_log` | 分级日志输出 |
| `host_http_check` | 对 manifest `permissions.network.allow` 白名单内的 host 发起 GET 并返回状态码（仅探活，不返回响应体） |
| `host_list_artist_ids` / `host_get_artists_by_ids` | Artist 查询 |
| `host_merge_artists` / `host_split_artist` | Artist 合并/拆分 |

### 异步任务基础设施

- 任务队列基于数据库表（`AsyncTaskLog`），`AsyncTaskQueueStore` 提供 `enqueue / claim / completeTask / resetRunningTasksToPending`。
- `TaskType` 目前仅有 `METADATA_PARSE` 与 `TRANSCODE`；插件按 manifest 绑定到某个 `TaskType`，**每个 TaskType 同时只有一个插件实例**（`PluginTaskService.plugins: Map<TaskType, WasmPlugin>`）。
- 消费由 `TaskSchedulingConfig` 定时轮询驱动，`consumePendingTasks` 每轮对每个 TaskType 只 claim 并执行 **1 个**任务，串行执行。
- 提交入口：`POST /api/plugin-task-submissions/{taskType}`，携带插件 form 定义的参数。

### 相关表结构

`plugin` 表（`V0.0.1__init.sql`，实体 `model/Plugin.kt`）：

| 字段 | 含义 |
|---|---|
| `id` | 插件 ID（manifest `id`），主键，同 id 上传即覆盖升级 |
| `name` | 展示名（manifest `name`，可空） |
| `version` | 插件版本（仅展示，无版本比较逻辑） |
| `abi` | ABI 标识，当前只接受 `unirhy-wasm-abi-v1` |
| `task_type` | 绑定的 `TaskType` 枚举名（取 manifest `tasks` 的**第一个**，多任务声明被丢弃） |
| `extension` | manifest `tasks[].extension` 的透传存储，**当前无任何逻辑消费**（仅入库、导出、API 回显） |
| `network_allow` | 网络白名单 host 列表（manifest `permissions.network.allow`） |
| `form_fields` | 前端表单字段定义 JSON（manifest `form.fields` 序列化） |
| `wasm` | WASM 模块二进制（BYTEA，上限 20MB） |
| `enabled` | 启用状态，上传后默认禁用 |
| `created_at` | 上传时间 |

`async_task_log` 表：`task_type`（实体侧为 `TaskType` 枚举类型）、`created_at/started_at/completed_at`、`params`（JSON 文本）、`completed_reason`、`status`。去重依赖按内建任务类型硬编码的 jsonb 部分唯一索引（`uq_async_task_log_metadata_parse_active` / `uq_async_task_log_transcode_active`），`enqueueIgnoringConflicts` 的幂等性完全由这些索引提供——插件任务没有命中任何索引，投递去重实际不生效。

### 现状的主要限制

- Host API 面过窄：插件无法读写 Artist 之外的领域数据，HTTP 能力仅有探活。
- 任务类型为封闭枚举，新插件场景需要改服务端代码才能引入新 TaskType。
- **插件无法注册独立的任务类型，「前端触发 → 投递 → 消费」链路在现状下不可用**：插件只能绑定 `METADATA_PARSE` / `TRANSCODE` 之一，而这两个类型各自有常驻的内建消费者（`ScanTaskService` / `TranscodeTaskService`），与 `PluginTaskService` 从同一队列分区（`AsyncTaskLog` 按 `taskType`）争抢任务，且两侧 payload 格式互不兼容——插件投递的任务会被内建消费者以错误格式消费，反之亦然。
- 前端存在同源冲突：`TaskSubmissionModal` 以 `taskType` 作为任务 tab 的 id，插件任务与内建任务 id 重复；且提交时 `activePlugin` 分支优先，加载了绑定 `METADATA_PARSE` 的插件后，内建「元数据解析」的提交也会被路由到插件提交接口。
- 消费模型为单线程、每轮单任务，吞吐不适合大批量插件任务。
- 单个 WASM `Instance` 被并发场景复用存在线程安全隐患（Chicory Instance 非线程安全）。

## 待决策清单

无。

## 结论

*（随工作推进追加。每条结论应是最终决定，附带生效的代码位置。）*

### WASM 运行时：Chicory → Endive

- 插件 WASM 运行时由 [Chicory](https://github.com/dylibso/chicory) 切换为 [Endive](https://github.com/bytecodealliance/endive)（Bytecode Alliance 出品的纯 JVM WebAssembly 运行时，零原生依赖、无 JNI，全 WASM 规范 + WASI + SIMD + GC 支持）。ABI 标识 `unirhy-wasm-abi-v1` 与 guest 侧内存交换协议（`alloc/dealloc` + `(ptr << 32 | len)` 打包返回）不变。
- 生效位置：`server/gradle/libs.versions.toml`（依赖坐标）、`server/build.gradle.kts`（依赖引入）、`WasmPlugin.kt`（模块解析、实例化、导出/Host 函数类型）、`PluginHostArtist.kt` / `PluginHostHttp.kt`（Host 函数定义）。

### 任务身份：`(namespace, task_type)` 二元组

- 任务身份由封闭枚举改为二元组：`namespace`（反向域名，如 `com.coooolfan.fetch_cover`）+ `task_type`（全大写标识符，如 `FETCH_COVER`）。插件任务的 namespace 即插件 id。
- `app.unirhy` 开头的命名空间整体保留，插件上传校验禁止使用；内建任务归属 `app.unirhy.built-in`（`METADATA_PARSE` / `TRANSCODE`）。
- `async_task` 以**两列**存储二元组，消费索引与去重索引建立在 `(namespace, task_type, ...)` 上。namespace / task type 的通用格式由服务端 `TaskKey` parser 校验，不在数据库重复设置格式 CHECK。
- 组合串 `{namespace}:{TASK_TYPE}` 仅作为前端稳定 id、日志、展示层及集合查询参数的紧凑序列化形式；创建 submission 的资源表示仍分别携带 `namespace` 与 `taskType`，服务端内部使用值类型表示二元组，不落库组合串。
- 不做 `async_task` 与 `plugin` 表之间的数据库级引用约束：任务记录需在插件删除后保留，归属校验在应用层完成。

### `async_task` 资源命名

- 目标数据库表使用 `async_task`，Jimmer 实体使用 `AsyncTask`。该记录是可排队、claim、完成、失败、取消和重新排队的任务资源，不再使用容易误解为不可变审计记录的 `async_task_log` / `AsyncTaskLog`。
- `task_submission` 与 `async_task` 是一对多关系，外键为 `async_task.submission_id -> task_submission.id`；终态任务仍保存在 `async_task` 中供查询和统计，不另建 task log 表。
- 数据访问组件统一命名为 `AsyncTaskStore`，覆盖 enqueue、discovery、claim、状态转换及管理查询；原 `AsyncTaskQueueStore` 不保留。管理服务统一命名为 `AsyncTaskService`，原 `AsyncTaskLogService` 不保留。
- 后端 DTO、Jimmer Fetcher、索引及方法名称全部移除 `Log`：`AsyncTaskLogCountRow` 由统一统计接口的 `TaskStatisticsResponse` / `TaskStatusCounts` 取代，`DEFAULT_TASK_LOG_FETCHER` 改为 `DEFAULT_TASK_FETCHER`，索引前缀由 `*_async_task_log_*` 改为 `*_async_task_*`。
- 外部资源统一使用 `/api/tasks`，不提供 `/logs` 子路径。前端生成类型使用 `AsyncTaskDto`，`TaskLogDrawerContent.vue` 改为 `TaskDrawerContent.vue`，`taskLog` i18n key 改为 `taskDetails`，`taskLogs` / `listTaskLogs` 等变量和方法改为 `tasks` / `listTasks`。
- 不新增 `async_task_attempt`。若管理员将 `FAILED` 任务重新排队，仍更新同一条 `async_task`；应用日志不作为数据库实体命名依据。
- 生效位置：数据库迁移与索引、`AsyncTask.kt`、`AsyncTaskStore.kt`、`AsyncTaskService.kt`、Task Controller / Fetcher / 统计 DTO、API 生成类型、任务管理前端组件与测试。

### 插件与任务的绑定关系

- 一个插件只允许声明一个任务（manifest 中 `tasks` 列表收敛为单项 `task` 对象），插件即任务的唯一提供者。
- `task.concurrency` 是必填正整数，作为该 TaskKey 首次安装时的任务执行并发值；安装后管理员可直接修改当前值。

### `plugin` 表与 manifest 重做

- `id` 即任务命名空间（反向域名）；上传时由服务端校验通用格式，数据库只通过 `ck_plugin_id_reserved` 阻止插件使用 `app.unirhy*` 保留命名空间。
- `task_type` 存插件自有任务名段（全大写标识符），格式由服务端校验；`(id, task_type)` 即任务身份二元组。
- `concurrency` 保存该插件 TaskKey 当前使用的正整数执行并发值。首次安装时由 manifest 的 `task.concurrency` 初始化；管理员后续直接修改该列。
- **删除 `extension` 字段**（manifest 中原有的 `tasks[].extension` 一并移除），无逻辑消费。
- `form_fields` 替换为 `form_definition`，保存 manifest 中完整的 `form.schema` 与 `form.order`。
- 其余字段（`name/version/abi/wasm/enabled/created_at`）保留，并增加 `updated_at` 作为多节点本地 Registry 的缓存失效标记；安装、覆盖上传、启禁用及并发修改时更新。该字段不表示可执行版本，不写入 submission / task，也不改变“不引入 `plugin_revision`”的契约；迁移直接重建表。
- 插件任务与内建任务共用 `POST /api/task-submissions`；插件请求表示中的二元组必须与数据库保存的 `(id, task_type)` 完全一致。前端任务 tab 使用完整二元组的紧凑序列化形式，消除插件与内建任务的 id 冲突。
- `PluginTaskService` 内存索引由 `TaskType` 枚举改为按插件 id，装载/卸载均以插件 id 定位。
- 内建消费者（`ScanTaskService` / `TranscodeTaskService`）改用 `app.unirhy.built-in` 命名空间下的任务常量。
- 投递去重通用化：`async_task` 不新增哈希列，以 `(namespace, task_type, sha256(jsonb_send(payload)))` 建部分唯一表达式索引（`status IN ('PENDING','RUNNING')`），替代按内建类型硬编码的 jsonb 索引。

### 目标数据库结构

以下 DDL 是插件任务架构的目标结构。其他章节描述与本节冲突时，以本节的表名、列名、类型、约束和索引为准。状态使用 `TEXT + CHECK`，不创建 PostgreSQL enum；结构化 JSON 使用 `JSONB`，不再以 `TEXT` 保存 JSON。

```sql
CREATE TABLE public.plugin
(
    id              TEXT        PRIMARY KEY,
    name            TEXT,
    version         TEXT        NOT NULL,
    abi             TEXT        NOT NULL,
    task_type       TEXT        NOT NULL,
    concurrency     INTEGER     NOT NULL,
    form_definition JSONB       NOT NULL,
    wasm            BYTEA       NOT NULL,
    enabled         BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_plugin_id_reserved
        CHECK (id NOT LIKE 'app.unirhy%'),
    CONSTRAINT ck_plugin_concurrency_positive
        CHECK (concurrency > 0),
    CONSTRAINT ck_plugin_form_definition
        CHECK (
            jsonb_typeof(form_definition) = 'object'
            AND form_definition ? 'schema'
            AND jsonb_typeof(form_definition -> 'schema') = 'object'
            AND form_definition ? 'order'
            AND jsonb_typeof(form_definition -> 'order') = 'array'
        ),
    CONSTRAINT ck_plugin_updated_at
        CHECK (updated_at >= created_at)
);

CREATE TABLE public.task_submission
(
    id               BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    namespace        TEXT        NOT NULL,
    task_type        TEXT        NOT NULL,
    params           JSONB       NOT NULL,
    status           TEXT        NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    completed_reason TEXT,

    CONSTRAINT ck_task_submission_params_object
        CHECK (jsonb_typeof(params) = 'object'),
    CONSTRAINT ck_task_submission_status
        CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_task_submission_discovery
    ON public.task_submission (status, namespace, task_type);

CREATE INDEX idx_task_submission_claim
    ON public.task_submission (namespace, task_type, status, created_at, id);

CREATE TABLE public.async_task
(
    id               BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    submission_id    BIGINT      NOT NULL
        REFERENCES public.task_submission (id) ON DELETE CASCADE,
    namespace        TEXT        NOT NULL,
    task_type        TEXT        NOT NULL,
    payload          JSONB       NOT NULL,
    status           TEXT        NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    completed_reason TEXT,

    CONSTRAINT ck_async_task_status
        CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_async_task_discovery
    ON public.async_task (status, namespace, task_type);

CREATE INDEX idx_async_task_claim
    ON public.async_task (namespace, task_type, status, created_at, id);

CREATE INDEX idx_async_task_submission
    ON public.async_task (submission_id, created_at, id)
    INCLUDE (status);

CREATE UNIQUE INDEX uq_async_task_active_payload
    ON public.async_task (
        namespace,
        task_type,
        sha256(jsonb_send(payload))
    )
    WHERE status IN ('PENDING', 'RUNNING');
```

| 当前结构 | 目标结构 |
|---|---|
| `plugin.extension` | 删除 |
| `plugin.network_allow` 与 manifest `permissions` | 删除 |
| `plugin.form_fields TEXT` | `plugin.form_definition JSONB` |
| 无插件执行并发与缓存失效字段 | 新增 `plugin.concurrency` / `plugin.updated_at` |
| `async_task_log` + `params TEXT` | 重建为 `async_task` + `payload JSONB` |
| 无提交资源 | 新增 `task_submission`，`async_task.submission_id` 必填外键关联 |
| 按内建 TaskType 拆分的活动索引 | 单一 TaskKey + payload hash 部分唯一索引 |

- `plugin.extension`、`plugin.network_allow` 与 `plugin.form_fields` 删除；`form_definition` 原子保存 `{schema, order}`，完整 JSON Schema 白名单仍由应用层校验。`plugin` 不保存权限、timeout、memory limit、revision 或 manifest default / override 副本。
- `task_submission.params` 只接受根 JSON Object，对应统一提交请求和 Planner 输入。`async_task.payload` 接受 Planner 产生的任意合法 JSON 值，对应 Handler / `run()` 输入；两者在 Jimmer 模型中均使用结构化 JSON 类型，不映射为 JSON 字符串。
- `async_task.namespace/task_type` 为执行和索引而反规范化保存，Task Planner 必须写入与父 submission 相同的 TaskKey；格式和父子一致性均由应用层在同一事务内保证。数据库不检查通用 TaskKey 字符格式，只保留插件 id 的保留 namespace CHECK。`task_submission` 与 `async_task` 都不外键关联 plugin，唯一的任务归属外键是 `async_task.submission_id`。
- 不新增 `task_definition`、通用 `task_concurrency`、`task_statistics`、attempt、lease 或 task log 表。内建定义留在服务端，插件定义与当前并发留在 `plugin`，统计实时聚合现有资源。
- submission / task 不增加插件版本或 revision、idempotency key、请求 fingerprint、payload hash、attempt count、max attempts、`available_at`、owner/token、heartbeat、lease timeout 或 `DEAD` 状态等列。
- 数据库只 CHECK 合法状态值，不约束状态与 `started_at/completed_at/completed_reason` 的组合。服务端仍按状态迁移维护时间字段，并在 `FAILED -> PENDING` 时清空三者；数据库保留人工修复和特殊故障处理空间。
- `completed_reason` 使用不截断的 `TEXT`；`params`、`payload` 与失败原因不增加数据库长度 CHECK 或应用层额度列。
- 活动 payload 唯一索引使用 `JSONB` 的规范化 binary send 表示计算 SHA-256；`jsonb_send(jsonb)` 与 `sha256(bytea)` 均为 PostgreSQL IMMUTABLE 内建函数，可直接用于表达式索引，不引入扩展或自定义函数。索引仅负责活动任务去重，哈希不作为表字段持久化。
- discovery、claim、TaskKey / 状态过滤、submission 子任务分页和级联删除使用上面的固定索引，不再保留任何 `*_async_task_log_*` 或按内建 TaskType 拆分的索引。
- 新 Flyway 迁移不得修改已发布的 `V0.0.1__init.sql`。迁移直接执行 `DROP TABLE public.async_task_log` 与 `DROP TABLE public.plugin`，再按目标 DDL 创建 `plugin`、`task_submission` 与 `async_task`；表的附属索引和序列随旧表删除，不编写旧任务或已安装插件的数据转换。迁移文件使用实施版本对应的下一个合法 Flyway 版本号。

### UniRhy 版本升级

- 本次架构变更是破坏性升级，不支持旧版与新版服务节点混合运行或滚动升级。部署时先停止全部旧版后端节点，使其 Worker、事务和数据库连接退出，再启动新版节点执行 Flyway migration。
- migration 直接删除全部旧任务记录和已安装插件；音乐、用户、Asset、播放等不属于上述两张旧表的业务数据不受影响。升级后由管理员使用新 manifest 格式重新安装所需插件，不自动转换或重新启用旧插件。
- 旧 manifest、旧任务 API 及其前端生成客户端不保留兼容分支、别名或转换逻辑。后端、Web 与 Tauri 客户端按同一版本契约升级；旧版服务或客户端不允许继续连接迁移后的部署。
- 不提供 down migration。需要回退时停止新版节点并恢复升级前的数据库备份，不允许旧版程序连接已经迁移的数据库。
- 生效位置：新 Flyway migration（直接 drop / create）、插件 manifest 解析与安装流程、统一任务 API 及 Web / Tauri 生成客户端、部署与对应版本 Release Notes。

### 插件升级与任务兼容性

- 插件任务不固定投递时的插件版本，`async_task` 不存储插件版本。
- manifest 的 `version` 仅用于展示，服务端不进行版本排序、比较或依赖解析。
- 同一插件 id 更新时必须保持 `(id, task_type)` 不变，并兼容历史版本已经生成的任务 payload；不兼容的任务协议变更必须使用新的插件 id。
- `PENDING` 任务及 `FAILED` 后被管理员重新排队的任务，始终由执行时当前加载的插件版本处理；`COMPLETED` / `CANCELLED` 不允许改回 `PENDING`。
- 已经开始执行的任务继续使用本次调用取得的运行时实例，不受插件更新影响。
- 生效位置：`PluginService.kt`（上传更新校验）、`PluginTaskService.kt`（任务执行时解析当前插件）、`AsyncTask.kt` 与数据库迁移（不引入插件版本字段）。

### 插件装载与 WASM Instance 生命周期

- 同 id 上传表示覆盖升级；上传后的插件保持禁用，并卸载此前加载的同 id 插件。
- 启用插件时先完成 WASM 解析、实例化和导出函数校验；全部成功后再更新 `enabled` 状态并注册运行时。加载失败时插件保持禁用，数据库启用状态与运行时可用状态必须一致。
- 每个已加载插件缓存解析后的 Module；每次 `plan()` 和 `run()` 调用创建独立 Instance。
- Instance 不跨调用共享，也不在 `plan()` 与 `run()` 之间共享；不使用 Instance 池。
- 插件更新只影响之后开始的调用，不强行中断正在执行的调用。
- 每个节点的固定轮询 tick 根据 `plugin.id/enabled/task_type/concurrency/updated_at` 与本地快照对齐 Registry：新增或变化的已启用插件加载最新 Module 并原子替换 Planner / Handler，禁用或删除的插件成对移除，单纯并发变化更新本地容量。其他节点不依赖处理管理请求的节点推送状态。
- 生效位置：`PluginService.kt`（上传、启用与卸载流程及 `updated_at`）、`PluginTaskService.kt`（运行时注册、原子替换与轮询对账）、`WasmPlugin.kt`（Module 缓存及按调用实例化）、统一 Dispatcher tick（各节点 Registry reconciliation）。

### 任务投递去重

- 去重哈希不落库为独立字段，由 PostgreSQL 唯一表达式索引通过 `sha256(jsonb_send(payload))` 计算。
- `async_task.payload` 使用 `JSONB`；其规范化 binary send 表示用于计算完整 payload 哈希，对象字段顺序和输入空白不影响结果，数组顺序保留。
- 相同 `(namespace, task_type, payload 哈希表达式)` 的 `PENDING` / `RUNNING` 任务只保留一条。
- 去重不区分插件版本；同 id 插件更新必须保持旧 payload 的业务语义兼容。
- `COMPLETED` / `FAILED` / `CANCELLED` 任务不阻止相同 payload 再次投递。
- 生效位置：`AsyncTaskStore.kt`（通过 `ON CONFLICT DO NOTHING` 使用唯一索引）、数据库迁移（活动任务部分唯一表达式索引）；`AsyncTask` 实体不增加哈希字段。

### 任务提交与异步规划

- 一次用户触发持久化为一条 `task_submission`，规划产生的 `async_task` 与 submission 为一对多关系。
- HTTP 提交接口只校验任务身份、当前可用性与请求结构，写入 `PENDING` submission 后返回 `202 Accepted`、`Location: /api/task-submissions/{id}` 及 `submissionId`；HTTP 请求线程不执行插件 `plan()`、存储对象遍历或全量 Asset 查询。
- `task_submission` 存储 `id/namespace/task_type/params/status/created_at/started_at/completed_at/completed_reason`，其中 `params` 为根 Object `JSONB`；`async_task` 存储对应 `submission_id` 与 Planner 产生的 `payload JSONB`。两者状态集合均为 `PENDING / RUNNING / COMPLETED / FAILED / CANCELLED`。
- Planner worker 在自己的虚拟线程中开启事务，以 `FOR UPDATE SKIP LOCKED` claim 一条 `PENDING` submission，并持有该行锁与数据库连接直到本次规划结束。
- Planner 在该事务中调用执行时当前注册的 `TaskPlanner`：插件任务执行 `plan()`，内建扫描与转码任务分别执行对应的 payload 发现逻辑；不对规划结果总字节数、任务数量或单任务 payload 大小设置应用层配额。
- 规划成功后，在同一事务中批量插入生成的异步任务并将 submission 标记为 `COMPLETED`；`plan()` 返回空任务列表属于成功。
- 可处理的规划异常在 savepoint 内回滚本次规划产生的 UniRhy 数据库写入，然后在外层事务中将 submission 标记为 `FAILED` 并提交；连接中断或进程退出则回滚整个事务，使 submission 保持 `PENDING` 并可由任意节点重新 claim。
- PostgreSQL 事务不覆盖网络、对象存储或文件系统等外部副作用；这些副作用在规划失败或节点断线后可能已经发生，插件负责重复调用语义。
- submission 不固定插件版本；插件更新必须兼容尚未规划的历史 submission 参数。
- submission 与异步任务使用独立状态，不在 `async_task` 中以 `PLAN` / `RUN` 类型混合存储。
- 生效位置：数据库迁移与 `TaskSubmission.kt`（submission 持久化模型）、任务提交 Controller（返回 `submissionId`）、`TaskSubmissionService.kt`（事务内 claim、规划、投递与状态流转）、`PluginTaskService.kt` / `ScanTaskService.kt` / `TranscodeTaskService.kt`（规划逻辑迁移）。

### 统一任务定义与提交 API

- `GET /api/task-definitions` 返回当前可提交的任务定义；`GET /api/task-definitions/{namespace}/{taskType}` 返回单项定义。每项包含 `namespace`、`taskType`、名称以及 `form.schema` / `form.order`；内建定义来自服务端静态定义，插件定义来自已启用插件的 `form_definition`。
- 所有内建任务与插件任务通过 collection resource `POST /api/task-submissions` 创建 submission。请求表示为 `{ "namespace": "...", "taskType": "...", "params": { ... } }`；成功时返回 `202 Accepted`、`Location: /api/task-submissions/{id}` 与 `{ "submissionId": <id> }`。
- 删除 `POST /api/tasks/scans`、`POST /api/tasks/transcodes` 与 `POST /api/plugin-task-submissions/{...}`，不保留别名或双写入口；HTTP 层不再以不同 Controller 路径区分内建任务和插件任务。
- 提交服务按完整 `TaskKey` 查找定义与 `TaskPlanner`。未知 TaskKey 返回 `404 Not Found`；已安装但禁用或当前不可用的插件返回 `409 Conflict`；`params` 不是 Object 或参数校验失败返回 `400 Bad Request`。
- 内建 `METADATA_PARSE` 与 `TRANSCODE` 同样提供 Draft 2020-12 白名单子集 Schema，并先按 Schema 校验参数，再转换为 `ScanTaskRequest` / `TranscodeTaskRequest` 执行已有领域校验；统一 JSON 边界不取消内建任务的强类型服务层模型。
- 存储节点候选项属于运行时领域数据，不编码为静态 Schema enum，也不扩展 Schema 以声明远程数据源或前端 action。前端按内建 TaskKey 使用专用存储节点选择器，插件任务完全根据 Schema 使用通用表单组件。
- 任务定义查询与提交都需要登录，创建 submission 继续要求管理员角色。统一入口不允许调用方注册 TaskKey，`params` 也不能覆盖同级的任务身份字段。
- 生效位置：新增统一 Task Definition / Submission Controller 与定义查询服务、`TaskSubmissionService.kt` 的 TaskKey 路由与参数校验、内建任务静态表单定义、`TaskSubmissionModal.vue`（按 TaskKey 选择专用或 Schema 表单）、删除 `TaskController.kt` / `PluginController.kt` 中原有的三个提交方法。

### 插件表单与参数 JSON Schema

- 插件表单的数据契约使用 JSON Schema Draft 2020-12 的服务端白名单子集；不引入 `json-render`、JSON Forms 或允许插件注册自定义前端组件与 action。
- manifest 中原有的 `form.fields` 替换为 `form.schema` 与 `form.order`。`form.schema` 定义参数类型和约束；`form.order` 必须与 `properties` 的字段集合完全一致，用于确定前端字段顺序。
- 统一提交请求中的 `params`、`task_submission.params` 与 `plan()` 输入均为保留 JSON 原生类型的 Object，不再使用 `Map<String, String>`，也不进行字符串与 number / boolean 之间的隐式转换。
- 根 Schema 必须声明 `type: object`、`properties`、`required` 与 `additionalProperties: false`；可选 `$schema` 只接受 Draft 2020-12，根节点可使用 `title` 与 `description`。
- 字段只支持 `string`、`integer`、`number` 与 `boolean` 标量类型，不支持 `null`、数组或嵌套对象。每个字段必须提供 `title`，可提供 `description`、类型匹配的 `default` 与 `enum`。
- `string` 字段可使用 `minLength` / `maxLength`；`integer` 与 `number` 字段可使用 `minimum` / `maximum` / `exclusiveMinimum` / `exclusiveMaximum` / `multipleOf`。
- 不支持 `$ref` / `$defs`、组合 Schema、条件 Schema、`patternProperties`、`pattern`、`format` 及其他未列入白名单的关键字；插件上传时遇到未知关键字直接拒绝，不静默忽略。
- `default` 保持 JSON Schema annotation 语义：前端用其初始化表单，服务端不向缺失字段自动写入默认值；`required` 字段即使声明 default 也必须由调用方提交。
- 前端使用 UniRhy 自有 Vue 组件根据 Schema 生成表单；服务端在插件上传时校验 Schema 自身，在创建 submission 前对参数执行权威校验。客户端校验只用于交互反馈，不能替代服务端校验。
- 未声明 `form` 时使用不接受任何字段的空表单：`type: object`、空 `properties` / `required` / `order` 与 `additionalProperties: false`。
- 生效位置：`PluginManifest.kt`（表单定义与白名单子集）、`PluginService.kt`（上传校验与存储）、统一任务定义 / 提交接口（类型化 JSON 参数、表单定义输出及服务端校验）、`TaskSubmissionModal.vue`（Schema 表单渲染）、`plugin.form_definition`（表单定义持久化）。

### Submission 请求语义

- 任务提交接口使用普通非幂等 `POST`，不支持 `Idempotency-Key`、客户端请求 token 或根据 submission 参数合并请求。
- 每个通过校验并被服务端受理的 HTTP 请求都创建新的 `task_submission`；即使任务身份与参数完全相同，也视为一次独立的用户触发。
- 客户端不得对结果不明的提交请求执行自动重试；连接中断后再次提交会创建新的 submission，调用方接受由此产生重复规划的可能性。
- 前端在单次请求完成前禁用重复提交，只用于避免界面重复操作，不构成服务端幂等保证。
- `async_task` 的活动 payload 唯一表达式索引只负责执行任务去重，不合并 submission。重复 submission 分别执行规划；规划结果中与现有 `PENDING` / `RUNNING` 任务冲突的 payload 由投递索引忽略。
- `task_submission` 不增加 idempotency key、请求 fingerprint 或相关唯一索引。
- 生效位置：任务提交 Controller（普通 `POST` 语义）、`TaskSubmissionService.kt`（每次受理均插入）、`TaskSubmissionModal.vue`（请求期间防止重复操作）、`task_submission` 数据库迁移（不引入请求幂等字段）。

### 私有部署与规划资源约束

- UniRhy 按私有部署应用设计，不采用面向多租户 SaaS 的任务配额、租户公平性或防滥用模型。
- 任务规划链路不额外限制 submission 参数大小、规划结果总字节数、任务数量、单任务 payload 大小或失败原因长度，也不允许插件在 manifest 中声明相关配额。
- WASM 返回指针与长度的数值合法性、线性内存边界、JSON 格式及 JSON Schema 等检查属于执行正确性与内存安全要求，必须保留，不视为业务配额。
- 超大规划可能消耗进程内存、产生大事务或导致部署不可用；该风险由部署者选择的插件与运行环境承担，不在应用层通过预防性额度处理。
- Endive 继续提供 WASM 线性内存边界安全，Host API 集合由服务端显式定义且对所有插件一致；UniRhy 不增加插件级 capability 配置，也不额外设置 WASM 调用 deadline 或 Host 线性内存 cap。
- 生效位置：`TaskSubmissionService.kt` / 各任务 Planner（不引入规划配额）、`WasmPlugin.kt`（仅保留数值、内存与格式安全检查）、任务失败记录（不截断业务失败原因）。

### 插件权限与 Host API 一致性

- manifest 不定义 `permissions`，数据库不持久化 `network_allow` 或其他插件权限字段，也不提供插件权限管理 API。`PluginPermissions`、`PluginNetworkPermission` 与 `networkAllowHosts()` 删除，插件导入和导出包均不读写权限段。
- 所有已启用插件获得同一组由服务端显式注册的 Host imports；不按插件 id、`plan()` / `run()` 阶段或 effect 类型区分能力。未注册的 JVM、WASI、文件系统或网络能力仍不可被 WASM 直接调用。
- 网络 Host API 不检查插件级 host allowlist。它只校验 URL 结构与自身支持的协议，并保留连接、请求及重定向等 API 级行为；部署者信任其安装插件发起的网络访问。
- `plan()` 与 `run()` 获得相同的 Host API 集合，不按调用阶段区分只读与写入能力。插件可以在 `plan()` 中查询或修改领域数据、访问存储以及调用网络 Host API；服务端不阻止 `plan()` 产生业务副作用。
- 使用 UniRhy 当前数据库连接的 Host API 写入参与 Planner worker 的事务，可随 savepoint 或整个事务回滚；网络、对象存储、文件系统及其他外部副作用不受 PostgreSQL 事务保护。
- submission 的 `COMPLETED` / `FAILED` 状态只描述数据库内的规划与任务投递结果，不证明 `plan()` 没有产生外部副作用；节点断线或人工重新规划时，插件负责外部操作的重复调用语义。
- 生效位置：`PluginManifest.kt` / `Plugin.kt` / `PluginService.kt`（删除权限模型、持久化及导入导出）、`PluginHostHttp.kt`（删除 allowlist 判断）、`WasmPlugin.kt`（两种导出使用相同 Host imports）、各 Plugin Host API 实现（不检查插件或调用阶段并沿用 worker 事务）。

### `TaskPlanner` / `AsyncTaskHandler` SPI

- 规划与执行使用两个独立的服务端内部 SPI：`TaskPlanner` 根据 submission 参数生成任务 payload，`AsyncTaskHandler` 执行单条 `async_task` payload；该 SPI 不属于 WASM ABI，也不开放 JVM 插件装载。
- 两个 SPI 均以 `TaskKey(namespace, taskType)` 标识，并分别注册到 `TaskPlannerRegistry` 与 `AsyncTaskHandlerRegistry`；重复 TaskKey 注册直接失败，不使用中央枚举或 `when` 分发。
- 内建元数据扫描与转码分别提供各自的 Planner 和 Handler，并以 `app.unirhy.built-in` 下的 TaskKey 注册；WASM 插件的 `plan()` / `run()` 分别适配为 Planner / Handler。
- 插件成功启用时成对注册 Planner 与 Handler；禁用、删除或覆盖升级时成对移除或原子替换，不能向系统暴露只有其中一侧的中间状态。
- Planner worker 只通过 `TaskPlannerRegistry` 解析 submission；任务 Dispatcher 只通过 `AsyncTaskHandlerRegistry` 解析执行任务。两套生命周期和调用入口保持独立。
- 任务执行引擎在 Worker 事务及 savepoint 内调用 Handler；使用当前数据库连接的领域写入参与该事务，最终任务状态由执行引擎统一写入，不在 SPI 注册关系中隐式处理。
- 生效位置：新增 `TaskPlanner.kt` / `TaskPlannerRegistry.kt`、`AsyncTaskHandler.kt` / `AsyncTaskHandlerRegistry.kt`；拆分 `ScanTaskService.kt`、`TranscodeTaskService.kt` 的规划与执行职责；`PluginTaskService.kt` 负责 WASM Planner / Handler 的成对注册与原子替换；`TaskSubmissionService.kt` 与 Dispatcher 分别消费对应注册表。

### Dispatcher Discovery 与 Worker Claim

- Task Dispatcher 每次唤醒只执行一次不加锁的跨 TaskKey discovery，按 `(namespace, task_type)` 统计当前事务快照中可见的 `PENDING` 数量，并与 `AsyncTaskHandlerRegistry` 快照及本节点剩余容量求交集。
- 每个 TaskKey 本轮启动的 Worker 数为 `min(可见 PENDING 数量, 本节点剩余容量)`；Dispatcher 先预留本地容量，再将 Worker 提交到虚拟线程 executor。TaskKey 之间不设置优先级、权重或全局先后顺序。
- Discovery 只用于减少空查询，不取得任务所有权。每个 Worker 在自己的事务与数据库连接中，通过 `ORDER BY created_at, id LIMIT 1 FOR UPDATE SKIP LOCKED` 真正 claim 一条指定 TaskKey 的任务。
- Worker 在同一事务内将任务更新为 `RUNNING`、执行对应 Handler，并写入 `COMPLETED` 或 `FAILED` 后提交；行锁和连接保持到 Handler 执行结束，任务不在 Worker 之间转交。
- 多节点可能同时 discovery 到相同的 `PENDING` 行；其他节点已锁定但尚未提交的行也可能继续对 discovery 可见。Worker 若因 `SKIP LOCKED` 未 claim 到任务，立即结束事务并释放本地容量；不增加 probe、cooldown 或退避状态机。
- Handler 被禁用或未注册时，其 TaskKey 不会启动 Worker，对应任务保持 `PENDING`。
- 数据库迁移为 discovery 与单条 claim 建立 `(status, namespace, task_type)` 及 `(namespace, task_type, status, created_at, id)` 索引；活动 payload 唯一表达式索引继续只负责投递去重。
- 生效位置：`AsyncTaskStore.kt`（`discoverPendingCounts()` 与事务内 `claimOne()`）、新增 Task Dispatcher（注册表快照、容量预留与 Worker 提交）、任务执行引擎（单条 claim-and-execute）、数据库迁移（通用 discovery / claim 索引）。

### 调度与执行线程模型

- `TaskSchedulingConfig` 使用单线程 scheduler 运行统一 tick；scheduler 回调完成插件 Registry reconciliation、两类 discovery、容量预留和 Worker 提交后返回。Registry reconciliation 只在插件元数据变化时加载或移除 Module 并更新本地容量；scheduler 不执行 claim、`plan()`、Handler 业务逻辑、存储/HTTP IO 或 ffmpeg 等待。
- Planner 与 Handler 分别使用独立、具名的 virtual-thread-per-task executor（`task-planner-*` 与 `async-task-worker-*`）；两者分开仅用于生命周期、日志与线程诊断，不代表不同资源配额。
- Spring 事务上下文不跨线程传播。每个 Worker 在自己的虚拟线程中取得一个数据库连接并开启事务，该事务覆盖单条 claim、`plan()` 或 Handler 执行以及最终状态写入；一个活动 Worker 在执行期间持续占用一条数据库连接。
- WASM、数据库、存储、HTTP、文件处理及 ffmpeg 等混合负载不再按工作类型拆分平台线程池；CPU 实际并行度仍由 JVM carrier threads 与 Dispatcher 本地并发容量约束，数据库并行度同时受连接池容量约束。
- 虚拟线程 executor 不提供任务队列背压。Dispatcher 在提交 Worker 前预留本地容量；Worker 未 claim 到任务、执行完成或失败时均在 `finally` 中释放容量，executor 在提交前拒绝任务时立即释放预留容量。
- 应用关闭时先停止新的 Dispatcher 唤醒，再关闭两个 executor，并等待正在执行的 Worker；未能完成的 Worker 在连接关闭后由 PostgreSQL 回滚其事务。
- 生效位置：`TaskSchedulingConfig.kt`（单线程唤醒调度）、新增 Planner / Task Dispatcher（discovery 与 Worker 提交）、新增 executor 配置（两个虚拟线程 executor）、任务执行引擎（Worker 内长事务、连接与容量生命周期）。

### Dispatcher 固定轮询

- 每个服务节点使用一个单线程 task scheduler 和一个统一 fixed-delay tick，间隔固定为 `500ms`，首次启动无初始延迟。一次 tick 依次执行插件 Registry reconciliation、Planner discovery 与 Task discovery，并完成相应容量预留和 Worker 提交。
- fixed delay 从上一次 tick 完成后开始计算；tick 不重入、不并发，也不在上一轮变慢时累计待执行轮次。Registry reconciliation、Planner discovery 与 Task discovery 分阶段捕获并记录异常，一个阶段失败不阻止其他阶段执行或下一轮 tick。
- 创建 submission、将资源 PATCH 为 `PENDING`、启用插件、提高并发值以及 Worker 释放容量时均不发送进程内唤醒信号，最迟由下一轮 tick 发现；接受正常情况下不超过一个轮询间隔的额外排队延迟。
- 不引入 condition / semaphore 等进程内通知、PostgreSQL `LISTEN/NOTIFY`、数据库 trigger、专用 listener 连接或通知重连状态。PostgreSQL 中可见的 `PENDING` 记录始终是唯一事实来源。
- 多节点分别执行相同轮询，允许同时 discovery 到相同记录；Worker 继续通过独立事务中的 `FOR UPDATE SKIP LOCKED` 仲裁所有权，空 claim 立即释放本地容量。
- 应用关闭时先停止 fixed-delay tick，不再创建 Worker，再按既定顺序关闭 Planner 与 Handler executor。轮询间隔使用服务端常量，不新增部署配置或动态管理 API。
- 生效位置：`TaskSchedulingConfig.kt`（原三个独立 fixed-delay consumer 与三线程 scheduler 收敛为单线程统一 tick）、`PluginTaskService.kt`（按插件元数据对账 Registry）、Planner / Task Dispatcher 的阶段错误隔离、提交与状态管理服务（不增加 after-commit signal）、数据库连接配置（不增加 listener 连接）。

### PostgreSQL 事务作为执行所有权

- 每条 submission 规划和每条异步任务执行分别使用一个独立 PostgreSQL 事务。`FOR UPDATE SKIP LOCKED` 取得的行锁、执行逻辑和最终状态写入始终位于同一 Worker 线程、同一数据库连接与同一事务内。
- 多个服务节点可共同消费同一数据库；行锁保证同一条记录同一时刻只由一个 Worker 执行，`SKIP LOCKED` 使其他节点继续领取其余记录。
- 节点退出或数据库连接中断并被 PostgreSQL 检测到时，未提交事务及其中的 UniRhy 数据库写入全部回滚，记录恢复为事务前的 `PENDING`，之后可由任意节点重新 claim。
- 不增加 lease owner/token、heartbeat、lease timeout 或 reaper，也不在应用启动时重置全部 `RUNNING`；删除 `StartRunner.kt` 中的 `resetRunningTasksToPending()` 调用及对应 Store 方法。
- 执行引擎在取得行锁后建立 savepoint。可处理的 `plan()` / Handler 异常回滚到 savepoint，再将记录标记为 `FAILED` 并提交外层事务；连接中断等无法完成失败记录的情况回滚整个事务并保留 `PENDING`。
- PostgreSQL 事务只回滚使用当前事务连接的数据库操作，不回滚已经发生的 HTTP、对象存储、文件系统或子进程副作用；节点故障后这些外部操作可能重复。
- `RUNNING` 和 `started_at` 在长事务提交前对其他数据库连接不可见；Worker 执行通常使管理查询看到记录从 `PENDING` 直接变为 `COMPLETED` / `FAILED`，管理员也可将尚未锁定的 `PENDING` 改为 `CANCELLED`。不为实时运行态另建事务外状态或 lease 记录。
- 每个活动 Worker 在整个规划或执行期间占用一条数据库连接；每节点实际并行度同时受 TaskKey 本地并发值和数据库连接池可用连接数约束。
- 生效位置：Planner / Task Worker 的 `TransactionTemplate` 边界、`AsyncTaskStore.kt` 与 Submission Store 的 `FOR UPDATE SKIP LOCKED` 单条 claim、任务执行引擎的 savepoint 与最终状态写入、`StartRunner.kt`（移除启动重置）。

### 失败与重试语义

- Task Planner 或 Handler 的普通执行异常不自动重试。执行引擎回滚到 savepoint，撤销本次调用使用当前事务连接产生的数据库写入，将 submission 或任务标记为 `FAILED` 并提交外层事务。
- 不增加 `attempt_count`、`max_attempts`、`available_at`、`DEAD` 状态、错误分类或指数退避；`FAILED` 是本次执行的终态。
- 管理员可以将 `FAILED` 记录手动重置为 `PENDING`。插件需要处理临时 HTTP 等错误时，可在单次 `plan()` / `run()` 调用内部自行重试。
- 节点退出、连接中断或其他使外层事务无法提交的故障不落为 `FAILED`：PostgreSQL 回滚后记录保持 `PENDING`，之后可由任意节点自然重新 claim。此类未提交执行不单独持久化 attempt 或失败原因。
- 节点故障后的自然重领和管理员手动重置都可能重复网络、对象存储、文件系统或子进程等外部副作用；任务系统不提供 exactly-once 或外部副作用去重。
- 生效位置：任务执行引擎与 Planner Worker（savepoint、`FAILED` 提交及事务故障回滚）、Submission / Task Store（不增加重试字段）、任务管理 API（保留 `FAILED` 到 `PENDING` 的管理员重置）。

### WASM 执行约束

- UniRhy 不为 `plan()` / `run()` 设置调用超时，不使用定时器中断 Worker，也不新增 manifest 或 `plugin` 的 timeout 配置字段。
- Instance 使用模块声明的线性内存 initial / maximum，不通过 Endive `withMemoryLimits` 施加 Host cap，也不新增内存配置字段；Endive 的 WASM 越界检查继续生效。
- 插件在 JVM 进程内由 Endive 执行，不引入独立 Worker 进程、Host API RPC 或进程级强制终止协议。
- Host API 可以保留自身操作所需的连接、请求或子进程超时，但这些超时不构成整个 `plan()` / `run()` 调用的 deadline。
- 永不返回的 WASM 或不响应中断的 Host API 会持续占用 Worker、TaskKey 本地容量、数据库连接与事务行锁，直到执行自行结束或节点停止并关闭连接；部署者对所安装插件承担该运行风险。
- Endive 的线程中断支持只用于 JVM/执行器正常中断语义，不作为任务系统承诺的超时或资源隔离机制。
- 生效位置：`WasmPlugin.kt`（不包装调用 deadline、不覆盖模块内存限制）、`PluginManifest.kt` / `Plugin`（不增加 timeout / memory limit 字段）、任务执行引擎（不增加 WASM deadline scheduler）、部署模型（不增加插件 Worker 进程）。

### 插件禁用与删除

- `plugin.enabled` 是所有节点判断插件可用性的数据库权威状态。禁用插件后拒绝新的 submission，并停止领取新的规划与执行记录；已有 `PENDING` submission / task 保持原状态，重新启用后继续处理。
- 禁用不取消或中断已经取得行锁的 `plan()` / `run()`。这些调用继续使用开始执行时取得的 Module / Handler 完成并提交；运行中的 `plan()` 在禁用后提交的新任务保持 `PENDING`。
- Planner / Handler Worker 的 claim 必须同时验证插件记录存在且 `enabled = true`，不能只依赖节点本地注册表；其他节点尚未移除的旧注册项不能在禁用后领取新记录。
- 删除只允许作用于已经禁用的插件。存在任意 `PENDING` / `RUNNING` submission 或 task 时返回 `409 Conflict`，不删除插件，也不自动取消、删除或改写这些记录。
- Worker 长事务内未提交的 `RUNNING` 对删除事务仍显示为此前已提交的 `PENDING`，因此相同的活动记录检查也会阻止删除正在执行的插件。
- 插件被禁用且存在 `PENDING` 记录时，管理员需要重新启用插件使其处理完成，或先通过任务管理操作处理这些记录，再执行删除。
- 删除事务锁定插件行并再次校验禁用状态及活动记录数量。submission 创建事务在检查启用状态时对插件行持有共享锁直到 `PENDING` submission 插入提交，防止删除遗漏尚未提交的新 submission。
- `COMPLETED` / `FAILED` / `CANCELLED` submission 与 task 不阻止删除；这些历史记录继续通过字符串形式的 `(namespace, task_type)` 保留，不依赖指向 `plugin` 的外键。插件不存在时，不允许将其历史失败记录重置为 `PENDING`。
- 生效位置：插件启禁用与删除事务、任务提交可用性检查和插件行共享锁、Planner / Handler claim 的插件启用谓词、两套 TaskKey 注册表的节点本地同步、Submission / Task 管理 API 的重置前置条件。

### Submission / Task 管理资源与取消

- submission 是规划资源，task 是单条执行资源；两者状态彼此独立。submission 的 `COMPLETED` 只表示规划和任务投递成功，不表示其子任务全部成功，也不增加 `PARTIAL_FAILED` 等聚合状态。
- `GET /api/task-submissions` 分页查询 submission，支持按 `namespace`、`taskType` 与 `status` 过滤；`GET /api/task-submissions/{id}` 返回单项及其子任务状态计数；`GET /api/task-submissions/{id}/tasks` 分页查询关联 task。
- `GET /api/tasks` 与 `GET /api/tasks/{id}` 查询执行资源，collection 支持按 `submissionId`、`namespace`、`taskType` 与 `status` 过滤。原 `/api/tasks/logs` 资源命名及基于封闭 `TaskType` 的查询接口删除；原 `GET /api/tasks/log-counts` 重命名并扩展为 `GET /api/task-statistics`。
- 单项状态变更使用 `PATCH /api/task-submissions/{id}` 与 `PATCH /api/tasks/{id}`，请求表示仅包含目标 `status`。批量状态变更使用对应 collection 的 `PATCH`，请求表示包含明确的 `ids` 集合与目标 `status`；不使用 `/cancel`、`/retry` 等动作路径，也不以 `DELETE` 表示取消。
- 管理 API 只接受 `PENDING -> CANCELLED` 与 `FAILED -> PENDING`。客户端不能写入 `RUNNING`、`COMPLETED` 或 `FAILED`；`COMPLETED` / `CANCELLED` 不允许重新排队，需要再次执行时创建新的 submission。
- 取消操作通过 `FOR UPDATE SKIP LOCKED` 只更新尚未被 Planner / Handler 锁定的 `PENDING` 记录，设置 `completed_at` 与取消原因；正在执行的记录被跳过且不中断。单项资源未能取消时返回 `409 Conflict`，批量请求返回实际更新数量，调用方随后刷新资源状态。
- 将 `FAILED` 重新排队前必须确认 TaskKey 当前存在且可用；成功后清空 `started_at`、`completed_at` 与 `completed_reason`。collection `POST` 创建 submission 仍是非幂等操作；状态型 `PATCH` 设置资源已经具有的目标状态时不重复执行转换，单项返回当前资源，批量请求的实际更新数量不包含这些记录。
- 非法状态迁移或单项资源正被 Worker 锁定时返回 `409 Conflict`。submission 列表与详情将规划状态和子任务状态计数分开展示；前端不把子任务汇总结果回写为 submission 状态。所有状态修改接口仅允许管理员调用。
- 生效位置：`TaskStatus` / submission 状态增加 `CANCELLED`、Submission / Task Store 的状态过滤与锁定更新、资源化的 Submission / Task Controller、任务管理前端的独立规划状态与子任务计数视图、插件删除活动记录检查。

### 可观测性与数据保留

- 不持久化 Planner / Handler attempt，不增加 `task_attempt`、`async_task_attempt` 或 attempt 计数。`FAILED -> PENDING` 会清除同一资源此前的执行时间与失败原因，数据库不保留这次失败的历史快照。
- 复用现有任务状态计数能力：`AsyncTaskQueueStore.listCounts()` / `AsyncTaskLogService.listCounts()` 随资源重命名迁移，并由原 `GET /api/tasks/log-counts` 扩展为 `GET /api/task-statistics`，不是新增统计持久化机制。
- `GET /api/task-statistics` 按 TaskKey 返回 `TaskStatisticsResponse`，其中 submission 与 async task 分别使用一组 `TaskStatusCounts(active, completed, failed, cancelled, total)`。可通过重复 query parameter 一次过滤多个 TaskKey，例如 `?taskKeys=app.unirhy.built-in:METADATA_PARSE&taskKeys=com.example.cover:FETCH_COVER`；不分别接收会产生配对歧义的 namespace 与 taskType 数组。
- 缺省 `taskKeys` 时返回全部当前定义或存在历史记录的 TaskKey。服务端使用统一 `TaskKey` parser 校验数组元素，任意格式错误返回 `400 Bad Request`；重复值按首次出现去重并保持请求顺序，合法但没有定义或历史记录的指定 TaskKey 返回全零统计。
- `TaskSubmissionStore` 与 `AsyncTaskStore` 分别在一次查询中对整个 TaskKey 集合执行过滤及 `GROUP BY namespace, task_type, status`，不得按 key 循环查询；`TaskStatisticsService` 合并两组结果。
- 长事务内的 `RUNNING` 对统计查询不可见，因此统计不提供不准确的集群 `pending` / `running` 拆分。`active` 统计查询快照中 `PENDING` / `RUNNING` 的合计，包含等待记录和无法从数据库区分的活动 Worker。
- Planner / Handler 以结构化应用日志记录 `submissionId`、`taskId`、TaskKey、耗时、结果与失败信息；这些日志由部署环境收集，不复制到新的数据库日志表。
- 不引入 Spring Boot Actuator、Micrometer、Prometheus 指标、每节点 Worker 指标 API 或跨节点指标聚合。
- submission 与 async task 默认一直保留，插件删除不触发清理；不增加自动保留天数、定时 TTL 清理或后台批量清理任务。
- `DELETE /api/task-submissions/{id}` 是显式删除资源，只允许 submission 及其全部 async task 都处于 `COMPLETED / FAILED / CANCELLED` 时执行，否则返回 `409 Conflict`。删除 submission 通过 `async_task.submission_id` 的 `ON DELETE CASCADE` 原子删除子任务。
- 不提供 `DELETE /api/tasks/{id}` 或批量删除 task，避免破坏 submission 的子任务集合与统计。查询与统计接口需要登录，删除只允许管理员调用。
- 生效位置：原任务计数 Controller / Service / Store 的资源化重命名、Task Statistics Controller / DTO / Service、`TaskSubmissionStore` 与 `AsyncTaskStore` 的聚合查询、Planner / Handler 结构化日志、Submission DELETE 与数据库级级联外键；构建依赖和调度配置不增加监控及清理组件。

### 按 TaskKey 配置执行并发

- 只按 `TaskKey(namespace, taskType)` 控制 Handler 执行并发，不设置全局并发上限；一个插件只有一个 TaskKey，因此不再增加独立的每插件并发层。
- 插件任务的并发值直接持久化为 `plugin.concurrency`，不新增通用 TaskKey 并发表，也不分别保存 default、override 与 effective 值。
- manifest 的 `task.concurrency` 是首次安装的初始值；管理员通过插件管理 API 直接读写 `plugin.concurrency`，修改后无需重启服务。
- 同 id 插件升级保留数据库中已有的 `plugin.concurrency`，不使用新 manifest 覆盖；导出插件包时将当前值写入 manifest 的 `task.concurrency`。禁用插件保留该值，删除插件则随插件记录一并删除。
- 内建 `METADATA_PARSE` 与 `TRANSCODE` 不创建伪插件记录，分别使用服务端定义的并发值 `10` 与 `1`。
- 每个节点按本节点加载的 TaskKey 并发值维护本地动态容量计数器，不限制整个集群的总并发；增加服务节点会相应提高集群吞吐。
- Dispatcher 根据 discovery 结果原子预留本轮本地可用容量；Worker 未 claim 到任务或执行结束时在 `finally` 中释放一份容量。
- 管理员降低并发数时不取消或中断已经执行的任务；当本节点当前占用数大于或等于新值时不再启动 Worker，直到任务完成后占用数降到新值以下。提高并发数在下一轮 Dispatcher 唤醒时生效。
- Planner 使用与 Handler 分离的每节点、每 TaskKey single-flight 容量，同一节点对同一 TaskKey 同时只规划一条 submission；`plugin.concurrency` 只控制 Handler 子任务执行，不控制 `plan()`。
- 生效位置：manifest `task.concurrency`、`plugin.concurrency` 字段及插件管理员 API、TaskKey 本地并发管理器、Task Dispatcher 的 discovery 后预留与 Worker `finally` 释放、Planner Dispatcher 的本地 single-flight、插件导出时读取当前并发值。
