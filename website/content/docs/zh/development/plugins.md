---
title: 插件开发指南
description: 从 manifest、WASM ABI 与任务生命周期开始，构建、打包并验证一个 UniRhy 插件。
---

UniRhy 插件是运行在服务端的 WebAssembly 模块。一个插件声明一个或多个异步任务，可以查询或修改音乐库、访问存储节点、发起 HTTP 请求，也可以继续创建任务。本指南以 Rust 为例完成最小插件，UniRhy 不绑定插件的编程语言，任何能生成兼容的 WebAssembly 模块的语言都可以使用。

> 插件拥有接近管理员的能力，并且与服务端运行在同一信任边界内。当前版本下，UniRhy 不为插件配置单独的网络白名单、调用超时或内存上限。只安装你信任且与当前 UniRhy 版本匹配的插件。

> 插件系统仍处于早期阶段，当前不承诺 ABI 稳定性及向前或向后兼容。

## 先理解执行模型

所有任务都是同一种记录，并通过同一个导出函数执行。一条任务接收自己的 payload，完成工作，再返回它希望入队的后继任务；不返回后继即为叶子任务：

<figure class="diagram-figure plugin-flow-figure" role="img" aria-labelledby="plugin-flow-title-zh" aria-describedby="plugin-flow-desc-zh">
<span id="plugin-flow-title-zh" class="sr-only">插件任务从表单提交到异步执行的流程图</span>
<span id="plugin-flow-desc-zh" class="sr-only">表单参数成为根任务的 payload。服务端对该任务调用一次 execute，调用返回一组后继。每个后继成为一条独立调度的子任务并再次调用 execute，子任务同样可以继续返回后继。</span>
<svg class="plugin-flow-diagram" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 430" role="presentation" aria-hidden="true" focusable="false">
  <defs>
    <marker id="plugin-flow-arrow-zh" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
      <path d="M0,0 L10,5 L0,10 z" fill="#8a6a3a"/>
    </marker>
    <style>
      .pf-node { fill: #f5f0e6; stroke: #b8721b; stroke-width: 1.5; }
      .pf-item { fill: #fcfbf9; stroke: #d3b17e; stroke-width: 1.2; }
      .pf-label { fill: #2c2825; font-family: Georgia, 'Times New Roman', Times, serif; font-size: 16px; }
      .pf-code { fill: #2c2825; font-family: 'SFMono-Regular', Consolas, monospace; font-size: 14px; }
      .pf-sub { fill: #8a817c; font-family: Georgia, 'Times New Roman', Times, serif; font-size: 12px; }
      .pf-line { fill: none; stroke: #8a6a3a; stroke-width: 1.5; }
    </style>
  </defs>

  <rect class="pf-node" x="16" y="180" width="118" height="70" rx="3"/>
  <text class="pf-label" x="75" y="207" text-anchor="middle">表单参数</text>
  <text class="pf-sub" x="75" y="229" text-anchor="middle">根任务 payload</text>

  <rect class="pf-node" x="174" y="180" width="140" height="70" rx="3"/>
  <text class="pf-code" x="244" y="207" text-anchor="middle">execute()</text>
  <text class="pf-sub" x="244" y="229" text-anchor="middle">入口任务</text>

  <rect class="pf-node" x="354" y="120" width="140" height="190" rx="3"/>
  <text class="pf-label" x="424" y="146" text-anchor="middle">后继列表</text>
  <rect class="pf-item" x="371" y="160" width="106" height="36" rx="3"/>
  <text class="pf-code" x="424" y="183" text-anchor="middle">successor 1</text>
  <rect class="pf-item" x="371" y="204" width="106" height="36" rx="3"/>
  <text class="pf-code" x="424" y="227" text-anchor="middle">successor 2</text>
  <rect class="pf-item" x="371" y="248" width="106" height="36" rx="3"/>
  <text class="pf-code" x="424" y="271" text-anchor="middle">successor n</text>

  <rect class="pf-node" x="534" y="36" width="180" height="70" rx="3"/>
  <text class="pf-code" x="624" y="63" text-anchor="middle">execute()</text>
  <text class="pf-sub" x="624" y="85" text-anchor="middle">子任务，可继续返回后继</text>
  <rect class="pf-node" x="534" y="180" width="180" height="70" rx="3"/>
  <text class="pf-code" x="624" y="207" text-anchor="middle">execute()</text>
  <text class="pf-sub" x="624" y="229" text-anchor="middle">子任务，可继续返回后继</text>
  <rect class="pf-node" x="534" y="324" width="180" height="70" rx="3"/>
  <text class="pf-code" x="624" y="351" text-anchor="middle">execute()</text>
  <text class="pf-sub" x="624" y="373" text-anchor="middle">子任务，可继续返回后继</text>

  <path class="pf-line" d="M134,215 L174,215" marker-end="url(#plugin-flow-arrow-zh)"/>
  <path class="pf-line" d="M314,215 L354,215" marker-end="url(#plugin-flow-arrow-zh)"/>
  <path class="pf-line" d="M494,178 C 514,178 514,71 524,71 L534,71" marker-end="url(#plugin-flow-arrow-zh)"/>
  <path class="pf-line" d="M494,215 L534,215" marker-end="url(#plugin-flow-arrow-zh)"/>
  <path class="pf-line" d="M494,265 C 514,265 514,359 524,359 L534,359" marker-end="url(#plugin-flow-arrow-zh)"/>
</svg>
</figure>

- 服务端对每条任务调用一次 `execute()`，传入 `{taskId, taskType, payload}`。一个插件只导出一个 `execute()`，由插件自己按 `taskType` 分发。
- 返回的后继会作为当前任务的子任务入队，与当前任务被标记为 `COMPLETED` 处于同一个事务；随后每个子任务各自独立调度。
- 后继可以指向同一插件的另一种任务类型，也可以显式给出 `namespace`，把工作派给其他插件。
- 并发是「每种任务类型一份」的属性。负责展开工作的入口任务通常配置为并发 `1`，它产出的工作任务则使用更高的并发。
- 每次调用都会得到全新的 WASM Instance。线性内存与全局变量不在任务之间共享，需要传递的数据请放进 payload、插件数据或外部持久化存储。
- 后继在活动兄弟之间去重：若某个后继的父任务、命名空间、任务类型与 payload 都与一条 `PENDING` 或 `RUNNING` 的兄弟任务相同，则被静默丢弃。根任务不参与这项去重。
- 任务处于 `COMPLETED` 只表示这一次调用成功且其后继已入队，并不代表它下方的子树已经结束。

一般来说，入口任务应专注于发现工作并产出小而稳定的 payload，把网络下载与数据写入放在工作任务中。Host API 对所有任务都可用，但在展开阶段产生的外部副作用同样可能被重复执行。

## 准备 Rust 工程

安装 Rust 的裸 WASM target：

```sh
rustup target add wasm32-unknown-unknown
cargo new --lib artist-enricher
cd artist-enricher
```

将 crate 构建为动态库，并让 release 构建以体积为优化目标：

```toml
[package]
name = "artist_enricher"
version = "0.1.0"
edition = "2021"

[lib]
crate-type = ["cdylib"]

[dependencies]
serde = { version = "1", features = ["derive"] }
serde_json = "1"

[profile.release]
lto = true
opt-level = "z"
strip = true
panic = "abort"
codegen-units = 1
```

UniRhy 不提供 WASI。插件无法直接使用宿主文件系统、套接字或线程，这些能力都要通过 `env` 模块中的 Host API 获得。

## 编写 plugin.yml

包根目录必须包含 `plugin.yml` 与 `plugin.wasm`。下面的 manifest 声明了一个供用户投递的入口任务、一个只能由插件自己产出的工作任务，以及一项插件级配置：

```yaml
id: com.example.unirhy.artist-enricher
name: Artist Enricher
version: 0.1.0

runtime:
  type: wasm
  abi: unirhy-wasm-abi-v1

tasks:
  - type: SCAN_ARTISTS
    concurrency: 1
    userSubmittable: true
    form:
      schema:
        $schema: https://json-schema.org/draft/2020-12/schema
        type: object
        title: 扫描艺术家
        description: 将艺术家库切分为若干补全批次
        properties:
          batchSize:
            type: integer
            title: 每批数量
            minimum: 1
            default: 50
        required:
          - batchSize
        additionalProperties: false
      order:
        - batchSize

  - type: ENRICH_ARTIST
    concurrency: 4
    form:
      schema:
        $schema: https://json-schema.org/draft/2020-12/schema
        type: object
        title: 补全单个批次
        properties:
          offset:
            type: integer
            title: 偏移量
            minimum: 0
          limit:
            type: integer
            title: 数量
            minimum: 1
        required:
          - offset
          - limit
        additionalProperties: false
      order:
        - offset
        - limit

config:
  schema:
    $schema: https://json-schema.org/draft/2020-12/schema
    type: object
    title: 元数据来源
    properties:
      endpoint:
        type: string
        title: API 地址
        minLength: 1
      apiKey:
        type: string
        title: API 密钥
        writeOnly: true
        minLength: 1
    required:
      - endpoint
      - apiKey
    additionalProperties: false
  order:
    - endpoint
    - apiKey
```

其中的关键约束是：

- `id` 同时是任务命名空间，须为至少两段的小写反向域名；`app.unirhy` 前缀保留给内置任务。
- `tasks[].type` 必须是大写标识符，且在插件内唯一。`id` 与 `tasks[].type` 共同构成稳定的任务身份。
- 至少要有一个任务声明 `userSubmittable: true`。只有这类任务能从界面或 `host_task_create` 投递，其余任务只能作为后继出现。
- `tasks[].concurrency` 是该任务类型首次安装时的并发初始值。管理员之后可以调整，同 id 覆盖升级会保留当前值。
- `version` 仅用于展示，服务端不比较版本，也不做依赖解析。
- `form.schema` 使用 JSON Schema Draft 2020-12 的受支持子集。字段可以是 `string`、`integer`、`number`、`boolean` 标量，也可以是元素类型同质的 `string` / `integer` / `number` 数组。
- 根 Schema 必须声明 `type: object`、`properties`、`required` 与 `additionalProperties: false`，`form.order` 必须不重不漏地列出全部字段。
- 对可投递任务，`form` 会用于校验提交的参数；对其余任务，它只是声明 payload 契约，服务端不会再校验后继携带的 payload。
- `default` 只用于初始化前端表单。服务端不会注入默认值，required 字段仍须出现在提交的参数中。
- `config` 声明的是插件级配置，由管理员在插件页面填写，与具体任务无关。它沿用同一套 Schema 子集，但不允许数组，且 `string` 字段可以声明 `writeOnly: true`。
- `writeOnly` 字段会加密存储，且永远不会回传给管理界面，管理端只能看到该字段是否已配置。插件自身通过 `host_plugin_config_get` 读取完整值。
- 若 `config` 中的 required 字段尚未配置齐全，插件无法被启用。

如果某个任务不需要参数，省略它的 `form`，UniRhy 会提供一个不接受任何字段的空表单。插件不需要配置时，整段 `config` 可以省略。

## 实现 WASM ABI

ABI v1 要求提供线性内存与以下导出：

| 导出      | 签名                        | 用途                       |
| --------- | --------------------------- | -------------------------- |
| `alloc`   | `(i32 size) -> i32`         | 为宿主写入的数据分配内存   |
| `dealloc` | `(i32 ptr, i32 len)`        | 释放跨边界传输占用的内存   |
| `execute` | `(i32 ptr, i32 len) -> i64` | 执行一条任务并返回结果信封 |

`execute()` 接收一个 UTF-8 JSON 对象：

```json
{ "taskId": 42, "taskType": "SCAN_ARTISTS", "payload": { "batchSize": 50 } }
```

并返回一个信封。成功时 `successors` 可以省略或为空，表示叶子任务；`namespace` 缺省即插件自身：

```json
{
  "ok": true,
  "successors": [{ "taskType": "ENRICH_ARTIST", "payload": { "offset": 0, "limit": 50 } }]
}
```

失败时 `error` 必须是非空字符串，任务会以该信息记为 `FAILED`：

```json
{ "ok": false, "error": "metadata source returned 503" }
```

返回的 `i64` 高 32 位是输出指针，低 32 位是字节长度：

```text
(ptr << 32) | len
```

下面的最小实现把音乐库切分成若干批次，并在处理每个批次时写一条服务端日志：

```rust
use serde::Deserialize;
use serde_json::{json, Value};
use std::{mem, ptr, slice};

#[link(wasm_import_module = "env")]
extern "C" {
    fn host_log(level: i32, ptr: i32, len: i32);
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct Invocation {
    task_id: i64,
    task_type: String,
    payload: Value,
}

#[no_mangle]
pub extern "C" fn alloc(size: u32) -> u32 {
    let mut bytes = Vec::<u8>::with_capacity(size as usize);
    let pointer = bytes.as_mut_ptr();
    mem::forget(bytes);
    pointer as u32
}

#[no_mangle]
pub unsafe extern "C" fn dealloc(pointer: u32, size: u32) {
    if size > 0 {
        drop(Vec::from_raw_parts(pointer as *mut u8, 0, size as usize));
    }
}

fn return_json(value: &Value) -> u64 {
    let bytes = serde_json::to_vec(value).expect("failed to serialize JSON output");
    if bytes.is_empty() {
        return 0;
    }
    let size = bytes.len() as u32;
    let pointer = alloc(size);
    unsafe {
        ptr::copy_nonoverlapping(bytes.as_ptr(), pointer as *mut u8, bytes.len());
    }
    ((pointer as u64) << 32) | size as u64
}

#[no_mangle]
pub unsafe extern "C" fn execute(pointer: u32, size: u32) -> u64 {
    let bytes = slice::from_raw_parts(pointer as *const u8, size as usize);
    let outcome = match serde_json::from_slice::<Invocation>(bytes) {
        Ok(call) => dispatch(&call),
        Err(err) => Err(format!("invalid invocation: {err}")),
    };
    let envelope = match outcome {
        Ok(successors) => json!({ "ok": true, "successors": successors }),
        Err(message) => json!({ "ok": false, "error": message }),
    };
    return_json(&envelope)
}

fn dispatch(call: &Invocation) -> Result<Vec<Value>, String> {
    match call.task_type.as_str() {
        "SCAN_ARTISTS" => plan_batches(&call.payload),
        "ENRICH_ARTIST" => enrich(call.task_id, &call.payload).map(|()| Vec::new()),
        other => Err(format!("unknown task type: {other}")),
    }
}

fn plan_batches(payload: &Value) -> Result<Vec<Value>, String> {
    let batch_size = payload
        .get("batchSize")
        .and_then(Value::as_i64)
        .ok_or("batchSize is required")?;
    if batch_size <= 0 {
        return Err("batchSize must be positive".to_string());
    }
    let successors = (0..4)
        .map(|index| {
            json!({
                "taskType": "ENRICH_ARTIST",
                "payload": { "offset": index * batch_size, "limit": batch_size }
            })
        })
        .collect();
    Ok(successors)
}

fn enrich(task_id: i64, payload: &Value) -> Result<(), String> {
    let message = format!("artist-enricher: taskId={task_id}, payload={payload}");
    unsafe { host_log(1, message.as_ptr() as i32, message.len() as i32) };
    Ok(())
}
```

`host_log` 的级别为 `0 = debug`、`1 = info`、`2 = warn`，其余取值按 error 记录。

`execute()` 内部发生 panic 或 trap 同样会让任务失败，但显式返回 `{"ok": false, "error": ...}` 能给出有用得多的失败原因。

## 调用 Host API

Host imports 位于 `env` 模块。除日志与二进制存储操作外，它们共用同一签名：

```text
(i32 requestPtr, i32 requestLen) -> i64
```

插件把 UTF-8 的 JSON Object 写入自己的线性内存，调用 Host 函数，再按与自身 `execute()` 返回值完全相同的方式解包响应指针与长度。读取完响应后请调用自己的 `dealloc()` 释放内存。

例如一次 `host_artist_search` 的请求可以是：

```json
{ "name": "Miles Davis" }
```

JSON 类 Host API 始终返回信封：

```json
{ "ok": true, "data": [{ "id": 42, "displayName": "Miles Davis" }] }
```

```json
{
  "ok": false,
  "error": { "code": "NOT_FOUND", "message": "The requested resource was not found" }
}
```

读取 `data` 前先检查 `ok`。常见错误码为 `INVALID_ARGUMENT`、`NOT_FOUND`、`CONFLICT`、`RESPONSE_TOO_LARGE` 与 `INTERNAL`。非法指针、越界内存或无法解析的请求 JSON 属于 ABI 失败，会直接中止当前 WASM 调用。

当前 Host API 覆盖以下领域：

| 领域       | 能力                                               |
| ---------- | -------------------------------------------------- |
| 基础设施   | 分级日志、HTTP 请求，以及把 HTTP 响应流式写入存储  |
| 音乐元数据 | 查询与修改 Artist、Work、Recording、Album          |
| 媒体       | 查询、创建与删除 MediaFile 与 Asset                |
| 存储       | 查询 FS/OSS 节点；列举、stat、读取、写入与删除对象 |
| 播放列表   | 查询、创建、更新、删除与调整曲目顺序               |
| 任务       | 查询任务定义、创建与管理任务、读取统计             |
| 配置与数据 | 读取插件自身配置，读写与列举插件数据               |
| 只读元数据 | 查询插件与账号                                     |

分页调用默认返回 100 行，`pageIndex` 从 0 开始，`pageSize` 上限 1000。完整的函数名与请求/响应结构见 [Host API 参考](https://github.com/Coooolfan/UniRhy/blob/main/docs/PLUGIN_HOST_API.md)。Host 函数目录不构成长期兼容承诺，升级时请针对目标 UniRhy 版本重新构建并验证插件。

### 配置与插件数据

`host_plugin_config_get` 返回插件自身的配置，其中包含明文的 `writeOnly` 字段。其他插件无法读取它，每个插件的数据都以自身 id 为界。

`host_plugin_data_put`、`host_plugin_data_get` 与 `host_plugin_data_list` 提供一个键值存储，用于保存需要跨任务存活的状态，例如同步游标。若某个键同时也在 `config` 中声明，写入时会按该字段的 Schema 校验，并在字段为 `writeOnly` 时加密存储；其余键则原样保存任意 JSON 值。

### 二进制数据与大文件

有三个操作使用特殊约定：

- `host_storage_object_read(requestPtr, requestLen) -> i64` 返回原始字节，对象不存在时返回 `0`。调用前先 stat，并确认插件侧内存足够。
- `host_storage_object_write(metaPtr, metaLen, dataPtr, dataLen) -> i64` 把原始字节写入对象，返回 JSON 信封。
- `host_http_download_to_storage` 仍然接收 JSON，但服务端会把响应直接流式写入 FS/OSS，不经过插件内存。音频等大文件请使用它。

普通 `host_http_request` 的响应要经过 Base64 与插件内存，硬上限为 256 MiB。内容较大时优先使用流式下载。

## 事务、失败与幂等

通过 Host API 产生的数据库写入参与当前任务的事务。每次 JSON Host 调用还会建立一个嵌套保存点：若某次调用返回错误信封，它的数据库改动会被回滚，而插件仍可检查错误并继续执行。如果 `execute()` 随后失败，本次调用产生的全部数据库改动都会回滚，包括它本应入队的后继。

HTTP、文件系统与 OSS 的副作用不在 PostgreSQL 事务内。节点关闭、连接中断，或管理员重置失败任务，都可能让一条任务在外部写入已经完成之后再次执行。因此：

- 使用确定性的对象键，并在写入前检查目标；
- 用 `overwrite` 显式表达覆盖意图；
- 让后继 payload 保持确定，使重跑落入活动兄弟去重，而不是让子树翻倍；
- 让重复执行得到相同结果，或能够识别已完成的步骤；
- 不要把任务状态当作某个外部副作用恰好发生一次的证据。

普通执行错误不会自动重试，需要管理员把失败或已取消的任务重新置为 `PENDING`。若某次 HTTP 调用需要重试，请在单次调用内做有限次重试并自行实现退避。

## 构建与打包

编译模块：

```sh
cargo build --release --target wasm32-unknown-unknown
```

创建打包目录，并保证两个文件都位于 ZIP 根目录：

```sh
mkdir -p dist/plugin
cp plugin.yml dist/plugin/plugin.yml
cp target/wasm32-unknown-unknown/release/artist_enricher.wasm dist/plugin/plugin.wasm
(cd dist/plugin && zip -r ../artist-enricher-0.1.0.up plugin.yml plugin.wasm)
unzip -l dist/artist-enricher-0.1.0.up
```

`.up` 包就是 ZIP 文件。压缩包上限为 10 MiB，其中 `plugin.wasm` 条目上限为 20 MiB。

## 安装与验证

1. 以管理员身份登录，打开 _设置 → 插件_，上传 `.up` 文件。
2. 若 manifest 声明了 `config`，先填写插件配置；required 字段必须配置完整才能启用插件。
3. 上传后的插件保持禁用。启用时服务端会实例化 WASM，并校验 Host imports 与 `alloc`、`dealloc`、`execute` 导出，其中 `execute` 的签名必须是 `(i32, i32) -> i64`。
4. 打开 _任务管理_，选择插件中可投递的任务，填写由 Schema 生成的表单并提交。
5. 确认根任务已完成，再展开任务树查看它产出的后继。失败原因记录在各条任务上。
6. 使用 `host_log` 时带上有用的任务标识与任务类型，但绝不要记录凭据、Cookie 或完整媒体内容。

启用失败的常见原因包括：import 名称或签名不匹配、缺少必需导出、使用了 WASI、ABI 取值不正确、配置不完整，以及包内文件不在 ZIP 根目录。上传成功只能说明 manifest 与模块可以被解析，启用成功才能说明模块与当前 Host API 链接一致。

## 升级插件

上传同 `id` 的包即为就地升级，插件会重新变为禁用状态。新 manifest 必须继续声明旧版本已声明的全部任务类型（服务端会拒绝丢弃任务类型的上传），且每种任务都要能处理旧版本创建的 payload。已有的并发值会被保留，已存储的配置会按新声明调整加密表示。服务端不会在任务上记录插件版本，也不会为待执行的任务保留旧的 WASM。

如果 payload 协议无法保持兼容，请以新的插件 `id` 发布。发布前至少要验证：新的 `execute()` 能接受旧 payload、重复执行不会破坏外部数据、目标 UniRhy 版本提供了所需的全部 Host import。
