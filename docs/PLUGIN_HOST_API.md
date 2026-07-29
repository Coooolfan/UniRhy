# 插件 Host API

本文档说明 UniRhy WASM 插件可导入的 Host API，包括调用协议、错误处理、事务语义、权限边界和完整函数目录。插件运行时、任务模型和 Instance 生命周期见 [PLUGIN_API.md](./PLUGIN_API.md)。

服务端向每个已启用插件提供 66 个 Host imports，全部位于 `env` 模块。除 `host_log` 和二进制存储函数外，函数统一通过 JSON 请求与响应信封交换数据。

## ABI 与调用约定

插件使用 `unirhy-wasm-abi-v1`，并导出 `alloc`、`dealloc` 和 `execute`。Host 函数使用 `host_<domain>_<action>` 命名，例如 `host_artist_list` 和 `host_storage_object_read`。

Host 函数目录不属于 ABI 稳定性承诺。插件应当与安装它的 UniRhy 服务端版本匹配。

### JSON 调用

JSON Host 函数的签名为：

```text
(i32 reqPtr, i32 reqLen) -> i64
```

- 请求必须是 guest 线性内存中的 UTF-8 JSON Object；无参数函数传入 `{}`。
- Host 调用 guest 导出的 `alloc` 写入响应，并将返回地址和长度打包为 `(ptr << 32 | len)`。
- guest 读取响应后必须调用 `dealloc` 释放内存。
- 表格中的 `?` 表示可选字段；`分页`表示请求接受通用的 `pageIndex` 和 `pageSize` 字段。
- Artist、Work 等实体名称表示服务端固定的 JSON 输出形状。插件不能传入 Fetcher 或字段选择器。

成功响应：

```json
{"ok": true, "data": {}}
```

没有返回值的写操作使用 `null`：

```json
{"ok": true, "data": null}
```

业务错误通过信封返回，不会直接中断 WASM：

```json
{"ok": false, "error": {"code": "NOT_FOUND", "message": "..."}}
```

错误码包括：

| 错误码 | 含义 |
|---|---|
| `INVALID_ARGUMENT` | 缺少必填字段，或字段类型、格式、取值不合法 |
| `NOT_FOUND` | 请求的业务资源不存在 |
| `CONFLICT` | 数据约束、状态迁移、只读资源或已有对象造成冲突 |
| `RESPONSE_TOO_LARGE` | HTTP 响应超过允许的字节数 |
| `INTERNAL` | Host 内部、网络或存储操作失败 |

无效指针、越界长度、非法 JSON 或非 Object 根节点属于协议错误，会直接 trap，并按当前 `execute()` 调用失败处理。

### 分页

使用通用分页的函数接受：

```json
{"pageIndex": 0, "pageSize": 100}
```

- `pageIndex` 从 0 开始，默认值为 0。
- `pageSize` 默认值为 100，取值范围为 1 到 1000。
- 响应 `data` 为 `{"rows": [...], "totalRowCount": n}`。

`host_storage_object_list` 使用独立的 cursor 分页协议，详见存储章节。

### 日志

`host_log` 使用专用签名 `(i32 level, i32 ptr, i32 len) -> ()`。`ptr` 和 `len` 指向 UTF-8 日志文本，级别映射如下：

| level | 日志级别 |
|---|---|
| `0` | DEBUG |
| `1` | INFO |
| `2` | WARN |
| 其他值 | ERROR |

`host_log` 不返回 JSON 信封。

## 事务与执行语义

- 每次 `execute()` 调用都可以使用同一组 Host functions。
- 所有已启用插件获得相同的 Host functions，不配置插件级 capability。
- JSON Host 调用在当前 Worker 事务中建立嵌套 savepoint。业务失败会先回滚本次 Host 调用，再返回错误信封。
- Host 调用成功后释放 savepoint，其数据库写入仍随当前 `execute()` 的外层任务事务提交或回滚。
- HTTP、文件系统和对象存储操作属于外部副作用，不随数据库事务或 savepoint 回滚。
- Host 不提供隐式当前用户。创建歌单等需要属主的操作必须显式传入 `ownerId`，插件可以通过账号只读 API 查询账号 ID。

## HTTP 与二进制传输

### 普通 HTTP 请求

`host_http_request` 用于插件需要直接读取响应体的 API、页面、歌词或小型媒体请求。请求 URL 仅允许 `http` 和 `https`，不限制目标 Host。

- `method` 为必填 HTTP 方法。
- `headers` 是 JSON Object；每个 Header 值可以是字符串或字符串数组。
- `bodyBase64` 是可选的 Base64 编码请求体。
- 响应包含 `status`、`headers` 和 `bodyBase64`。
- 响应体上限为 256 MiB（268,435,456 字节）。已知 `Content-Length` 超限时在读取前返回 `RESPONSE_TOO_LARGE`；未知长度时边读边检查，超限后中止且不返回截断内容。

### 流式下载

`host_http_download_to_storage` 将远程响应体直接写入 FS 或 OSS，不经过 WASM 线性内存，适合导入大型媒体文件。

- `method` 默认使用 `GET`，支持 `Range` 以及 `User-Agent`、`Referer`、`Cookie` 等自定义 Header。
- 跟随 `301`、`302`、`303`、`307` 和 `308` 重定向，最多 5 次；每一跳都重新校验 URL。
- 允许同协议跳转和 HTTP 升级到 HTTPS，禁止 HTTPS 降级到 HTTP。
- 跨 Host 重定向会移除 `Cookie`、`Authorization`、`Proxy-Authorization` 等敏感 Header；同 Host 重定向保留 Header。
- 连接超时为 10 秒，每次初始连接和重定向后的新连接分别计时。
- 响应体读取空闲超时为 30 秒，每次成功读取后重新计时；没有整体下载时长上限。
- `maxBytes` 是可选正整数。已知响应长度时在读取前检查，未知长度时边读边检查；未传入时不设置应用层下载字节上限。
- 只有最终 `2xx` 响应会提交目标对象。非 `2xx` 返回 `ok: true`、`stored: false` 和最终状态码，并且不会创建或覆盖目标对象。
- `overwrite` 默认为 `false`。目标对象已存在且不允许覆盖时返回 `CONFLICT`。
- 下载先写入临时文件，完整读取成功后再提交到目标对象。失败时清理临时文件。

成功写入时，响应 `data` 包含 `status`、`stored`、`bytesWritten`、`contentType`、`sha256` 和 `destination`。

### 存储对象二进制调用

存储节点使用以下引用格式：

```json
{"type": "FS", "id": 1}
```

`type` 只能是 `FS` 或 `OSS`。

| 函数 | 签名 | 行为 |
|---|---|---|
| `host_storage_object_read` | `(i32 reqPtr, i32 reqLen) -> i64` | 请求为 `{"node": {...}, "objectKey": "..."}`；返回原始字节的打包地址和长度，不使用 JSON 信封 |
| `host_storage_object_write` | `(i32 metaPtr, i32 metaLen, i32 dataPtr, i32 dataLen) -> i64` | meta 为 `{"node": {...}, "objectKey": "...", "contentType": "..."}`，data 为原始字节；返回 JSON 信封 |

读取前应当使用 `host_storage_object_stat` 检查对象是否存在和 guest 是否有足够内存。`host_storage_object_read` 在对象不存在或内容长度为 0 时返回 `0`；其他无效请求或存储故障会直接 trap。

## Host API 目录

共 66 个函数，以下表格中的响应均指成功信封的 `data` 字段，专用签名除外。

### 基础（3）

| 函数 | 请求 -> 响应 | 说明 |
|---|---|---|
| `host_log` | `(level, ptr, len) -> ()` | 写入插件日志，不使用 JSON 信封 |
| `host_http_request` | `{method, url, headers?, bodyBase64?}` -> `{status, headers, bodyBase64}` | 执行 HTTP 请求并将完整响应体编码为 Base64 |
| `host_http_download_to_storage` | `{url, method?, headers?, destination: {node, objectKey}, overwrite?, maxBytes?}` -> `{status, stored, bytesWritten, contentType?, sha256?, destination}` | 将最终 `2xx` 响应流式写入目标存储对象 |

### Artist（7）

| 函数 | 请求 -> 响应 | 说明 |
|---|---|---|
| `host_artist_list` | 分页 -> `{rows: Artist[], totalRowCount}` | 分页列出艺术家标量信息 |
| `host_artist_get_by_ids` | `{ids: number[]}` -> `Artist[]` | 按 ID 批量查询艺术家 |
| `host_artist_search` | `{name}` -> `Artist[]` | 按名称搜索艺术家 |
| `host_artist_create` | `{displayName, alias?: string[], comment?, avatarId?, copyAssociationsFrom?}` -> `Artist` | 创建艺术家，可从另一艺术家复制关联关系 |
| `host_artist_update` | `{id, displayName?, alias?: string[], comment?, avatarId?}` -> `Artist` | 更新指定字段；`avatarId: null` 清除头像 |
| `host_artist_merge` | `{targetId, needMergeIds: number[]}` -> `null` | 将多个艺术家合并到目标艺术家 |
| `host_artist_split` | `{sourceArtistId, names: string[]}` -> `null` | 按名称拆分艺术家，`names` 至少包含两项 |

### Work（8）

| 函数 | 请求 -> 响应 | 说明 |
|---|---|---|
| `host_work_create` | `{title}` -> `Work` | 创建作品，标题不能为空 |
| `host_work_list` | 分页 -> `{rows: Work[], totalRowCount}` | 分页列出作品及录音摘要 |
| `host_work_get` | `{id}` -> `Work` | 查询作品详情 |
| `host_work_search` | `{name}` -> `Work[]` | 按名称搜索作品 |
| `host_work_random` | `{timestamp?, length?, offset?}` -> `Work` | 按随机选择参数获取一个作品，无可用作品时返回 `NOT_FOUND` |
| `host_work_update` | `{id, title?}` -> `Work` | 更新作品标题；未传标题时返回当前作品 |
| `host_work_delete` | `{id}` -> `null` | 删除作品 |
| `host_work_merge` | `{targetId, needMergeIds: number[]}` -> `null` | 将多个作品合并到目标作品 |

### Recording（5）

| 函数 | 请求 -> 响应 | 说明 |
|---|---|---|
| `host_recording_create` | `{workId, artistIds?: number[], label?: string[], title?, comment?, durationMs, defaultInWork?, coverId?}` -> `Recording` | 创建录音；`durationMs` 必须大于等于 0 |
| `host_recording_get` | `{id}` -> `Recording` | 查询录音及作品、艺术家、资源、封面和专辑详情 |
| `host_recording_list` | 分页 + `{ids?: number[], workId?}` -> `{rows: Recording[], totalRowCount}` | 按 ID 或作品筛选录音摘要 |
| `host_recording_update` | `{id, label: string[], title: string|null, comment, defaultInWork?}` -> `null` | 更新录音字段 |
| `host_recording_merge` | `{targetId, needMergeIds: number[]}` -> `null` | 将多个录音合并到目标录音 |

### Album（5）

| 函数 | 请求 -> 响应 | 说明 |
|---|---|---|
| `host_album_list` | 分页 -> `{rows: Album[], totalRowCount}` | 分页列出专辑及曲目摘要 |
| `host_album_get` | `{id}` -> `Album` | 查询专辑详情和曲目顺序 |
| `host_album_search` | `{name}` -> `Album[]` | 按名称搜索专辑 |
| `host_album_update` | `{id, title, releaseDate: string|null, comment}` -> `Album` | 更新专辑；`releaseDate` 使用 ISO-8601 日期 |
| `host_album_reorder_recordings` | `{id, recordingIds: number[]}` -> `null` | 设置专辑内完整曲目顺序 |

### MediaFile / Asset（7）

| 函数 | 请求 -> 响应 | 说明 |
|---|---|---|
| `host_media_file_get` | `{id}` -> `MediaFile` | 查询媒体文件位置、类型、大小、尺寸和节点归属 |
| `host_media_file_get_by_location` | `{node, objectKey}` -> `MediaFile` | 按存储节点和 objectKey 精确查询媒体文件 |
| `host_media_file_create` | `{node, objectKey, mimeType}` -> `MediaFile` | 将已存在的存储对象登记为媒体文件 |
| `host_media_file_delete` | `{id}` -> `null` | 删除无业务引用的媒体文件；存在引用时返回 `CONFLICT` |
| `host_asset_list` | `{recordingId?, mediaFileId?}` -> `Asset[]` | 至少传入一个筛选条件；两个条件同时存在时按 AND 查询 |
| `host_asset_create` | `{recordingId, mediaFileId, comment?}` -> `Asset` | 关联录音与媒体文件 |
| `host_asset_delete` | `{id}` -> `null` | 删除资源关联 |

### Storage（8）

| 函数 | 请求 -> 响应 | 说明 |
|---|---|---|
| `host_storage_default_write_node_get` | `{}` -> `{type, id, name}` | 获取系统默认可写节点，不返回节点凭据 |
| `host_storage_fs_node_list` | `{}` -> `FileSystemNode[]` | 列出文件系统节点及 `id`、`name`、`parentPath`、`readonly` |
| `host_storage_oss_node_list` | `{}` -> `OssNode[]` | 列出 OSS 节点及非敏感连接信息，不返回密钥 |
| `host_storage_object_list` | `{node, prefix?, pageSize?, cursor?}` -> `{objects: [{objectKey, size}], nextCursor?}` | 按前缀列举存储对象 |
| `host_storage_object_stat` | `{node, objectKey}` -> `{exists, size?, contentType?}` | 查询对象是否存在及其元数据 |
| `host_storage_object_read` | 二进制专用签名 -> 原始字节 | 将完整对象读入 guest 线性内存 |
| `host_storage_object_write` | 二进制专用签名 -> `null` | 将 guest 原始字节写入可写节点 |
| `host_storage_object_delete` | `{node, objectKey}` -> `null` | 从可写节点删除对象 |

`host_storage_object_list` 的 cursor 是不透明字符串：

- `prefix` 递归列举其下所有对象，返回按 objectKey 排序的扁平列表，不模拟目录层级。
- `pageSize` 默认值为 100，上限为 1000。
- cursor 只允许原样传回。服务端校验节点与 prefix 是否和生成 cursor 的请求一致。
- cursor 不能解析、修改、跨存储节点或跨 prefix 复用。
- 列举不提供跨页快照一致性。遍历期间发生对象增删时可能重复或跳过，插件应当按 objectKey 幂等处理。
- 没有后续对象时省略 `nextCursor`。

### Playlist（8）

| 函数 | 请求 -> 响应 | 说明 |
|---|---|---|
| `host_playlist_list` | `{ownerId?}` -> `Playlist[]` | 列出全部歌单或指定账号的歌单摘要 |
| `host_playlist_get` | `{id}` -> `Playlist` | 查询歌单详情和曲目顺序 |
| `host_playlist_create` | `{ownerId, name, comment?}` -> `Playlist` | 为指定账号创建歌单 |
| `host_playlist_update` | `{id, name?, comment?}` -> `Playlist` | 更新歌单字段 |
| `host_playlist_delete` | `{id}` -> `null` | 删除歌单 |
| `host_playlist_add_recording` | `{id, recordingId}` -> `null` | 向歌单添加录音 |
| `host_playlist_remove_recording` | `{id, recordingId}` -> `null` | 从歌单移除录音 |
| `host_playlist_reorder_recordings` | `{id, recordingIds: number[]}` -> `null` | 设置歌单内完整曲目顺序 |

### 任务系统（7）

| 函数 | 请求 -> 响应 | 说明 |
|---|---|---|
| `host_task_definition_list` | `{}` -> `TaskDefinition[]` | 列出插件任务和内建任务定义 |
| `host_task_definition_get` | `{namespace, taskType}` -> `TaskDefinition` | 按 TaskKey 查询任务定义 |
| `host_task_create` | `{namespace, taskType, payload}` -> `{taskId}` | 创建根任务；`payload` 必须是 JSON Object |
| `host_task_list` | 分页 + `{parentId?, rootsOnly?, namespace?, taskType?, status?}` -> `{rows: AsyncTask[], totalRowCount}` | 筛选并分页列出任务 |
| `host_task_get` | `{id}` -> `AsyncTask` | 查询任务详情 |
| `host_task_patch` | `{id, status}` -> `null` | 允许 `PENDING -> CANCELLED`，以及 `FAILED` / `CANCELLED -> PENDING` |
| `host_task_statistics` | `{taskKeys?: string[]}` -> `TaskStatistics` | 查询全部或指定 TaskKey 的任务统计 |

插件通过 `execute()` 成功响应中的 `successors` 声明当前任务的子任务，不提供 Host 侧的子任务入队函数。

### 插件元数据（2，只读）

| 函数 | 请求 -> 响应 | 说明 |
|---|---|---|
| `host_plugin_list` | `{}` -> `PluginMetadata[]` | 列出插件元数据、任务声明和配置声明，不返回 WASM 字节 |
| `host_plugin_get` | `{id}` -> `PluginMetadata` | 查询指定插件元数据 |

插件元数据包含 `id`、`name`、`version`、`taskType`、`concurrency`、`isAvailable`、`enabled`、`formDefinition` 和 `configDefinition`。

### 当前插件配置与数据（4）

| 函数 | 请求 -> 响应 | 说明 |
|---|---|---|
| `host_plugin_config_get` | `{}` -> JSON Object | 返回当前插件的完整配置，包括已解密的 `writeOnly` 值 |
| `host_plugin_data_get` | `{key}` -> 任意 JSON 值 | 读取持久化数据；key 不存在时返回 `NOT_FOUND` |
| `host_plugin_data_put` | `{key, value}` -> `null` | 写入任意 JSON 值，包括 Object、Array、字符串、数字、布尔值和 null |
| `host_plugin_data_list` | 分页 + `{prefix?}` -> `{rows: string[], totalRowCount}` | 按 key 前缀分页列出持久化数据的 key |

插件 ID 由 Host 根据当前运行实例注入，插件不能读取或写入其他插件的数据。配置声明中的 key 使用 `configDefinition` Schema 校验，`writeOnly` 配置值加密存储；未声明的 key 作为普通插件数据存储。不提供删除 Host API。

### 账号（2，只读）

| 函数 | 请求 -> 响应 | 说明 |
|---|---|---|
| `host_account_list` | `{}` -> `AccountMetadata[]` | 列出账号的 `id`、`email`、`name` 和 `admin` |
| `host_account_get` | `{id}` -> `AccountMetadata` | 查询指定账号的非敏感信息 |

## 权限边界

插件具有管理音乐元数据、媒体对象、歌单和任务的能力，但以下能力不通过 Host API 提供：

| 能力 | 边界说明 |
|---|---|
| 账号创建、更新、删除和凭据修改 | 账号体系是部署信任根，只提供非敏感只读查询 |
| 登录、注销、Token 签发与撤销 | 认证能力不向插件开放 |
| 插件上传、启禁用、并发修改、删除、导出和 WASM 字节读取 | 插件不能管理自身或其他插件的生命周期 |
| 播放队列读写 | 播放队列属于客户端会话状态 |
| 系统配置读写、状态查询和系统初始化 | 系统配置属于部署级管理面 |
| 存储节点创建、修改、删除和凭据读取 | 插件只能使用已配置的存储节点 |
| MediaFile 签名播放 URL | 插件通过存储对象 API 读取媒体字节 |
