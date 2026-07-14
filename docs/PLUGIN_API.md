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

## 结论

*（随工作推进追加。每条结论应是最终决定，附带生效的代码位置。）*

### 任务身份：`(namespace, task_type)` 二元组

- 任务身份由封闭枚举改为二元组：`namespace`（反向域名，如 `com.coooolfan.fetch_cover`）+ `task_type`（全大写标识符，如 `FETCH_COVER`）。插件任务的 namespace 即插件 id。
- `app.unirhy` 开头的命名空间整体保留，插件上传校验禁止使用；内建任务归属 `app.unirhy.built-in`（`METADATA_PARSE` / `TRANSCODE`）。
- `async_task_log` 以**两列**存储二元组，各自做格式 CHECK（namespace：`^[a-z0-9_]+(\.[a-z0-9_]+)+$`；task_type：`^[A-Z][A-Z0-9_]*$`）；消费索引与去重索引建立在 `(namespace, task_type, ...)` 上。
- 组合串 `{namespace}:{TASK_TYPE}` 仅作为 API 边界与展示层的序列化形式，服务端内部使用值类型表示二元组，不落库组合串。
- 不做 `async_task_log` 与 `plugin` 表之间的数据库级引用约束：日志需在插件删除后保留，归属校验在应用层完成。

### 插件与任务的绑定关系

- 一个插件只允许声明一个任务（manifest 中 `tasks` 列表收敛为单项 `task` 对象），插件即任务的唯一提供者。

### `plugin` 表与 manifest 重做

- `id` 即任务命名空间（反向域名），加格式 CHECK；上传校验拒绝保留命名空间。
- `task_type` 存插件自有任务名段（全大写标识符），加格式 CHECK；`(id, task_type)` 即任务身份二元组。
- **删除 `extension` 字段**（manifest 中 `tasks[].extension` 一并移除），无逻辑消费。
- 其余字段（`name/version/abi/network_allow/form_fields/wasm/enabled/created_at`）保留，语义不变；迁移直接重建表。
- 任务提交入口按插件 id 提交（`POST /api/plugin-task-submissions/{pluginId}`），服务端由插件 id 派生任务二元组；前端任务 tab 以插件 id 为标识，消除与内建任务的 id 冲突。
- `PluginTaskService` 内存索引由 `TaskType` 枚举改为按插件 id，装载/卸载均以插件 id 定位。
- 内建消费者（`ScanTaskService` / `TranscodeTaskService`）改用 `app.unirhy.built-in` 命名空间下的任务常量。
- 投递去重通用化：`async_task_log` 新增 `params` 哈希列，以 `(namespace, task_type, params_hash)` 建部分唯一索引（`status IN ('PENDING','RUNNING')`），替代按内建类型硬编码的 jsonb 索引。
