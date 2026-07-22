---
title: 插件开发指南
description: 从 manifest、WASM ABI 与任务生命周期开始，构建、打包并验证一个 UniRhy 插件。
---

UniRhy 插件是运行在服务端的 WebAssembly 模块。一个插件提供一个异步任务，可以查询或修改音乐库、访问存储节点、发起 HTTP 请求，也可以提交其他任务。本指南以 Rust 为例完成最小插件，UniRhy 不绑定插件的编程语言，任何能生成兼容的 WebAssembly 模块的语言都可以使用。

> 插件拥有接近管理员的能力，并且与服务端运行在同一信任边界内。当前版本下，UniRhy 不为插件配置单独的网络白名单、调用超时或内存上限。只安装你信任且与当前 UniRhy 版本匹配的插件。

> 插件系统仍处于早期阶段，当前不承诺 ABI 稳定性及向前或向后兼容。

## 先理解执行模型

一次插件任务分为规划与执行两个阶段：

<figure class="diagram-figure plugin-flow-figure" role="img" aria-labelledby="plugin-flow-title-zh" aria-describedby="plugin-flow-desc-zh">
<span id="plugin-flow-title-zh" class="sr-only">插件任务从表单提交到异步执行的流程图</span>
<span id="plugin-flow-desc-zh" class="sr-only">表单参数以 JSON 传入 plan 函数；plan 返回包含多个 payload 的列表。每个 payload 分别生成一条异步任务，每条任务再独立调用一次 run 函数。</span>
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

  <rect class="pf-node" x="20" y="180" width="120" height="70" rx="3"/>
  <text class="pf-label" x="80" y="210" text-anchor="middle">表单参数</text>
  <text class="pf-sub" x="80" y="232" text-anchor="middle">params JSON</text>

  <rect class="pf-node" x="180" y="180" width="120" height="70" rx="3"/>
  <text class="pf-code" x="240" y="211" text-anchor="middle">plan(params)</text>
  <text class="pf-sub" x="240" y="232" text-anchor="middle">规划一次</text>

  <rect class="pf-node" x="340" y="125" width="135" height="180" rx="3"/>
  <text class="pf-label" x="407.5" y="151" text-anchor="middle">payload 列表</text>
  <rect class="pf-item" x="357" y="164" width="101" height="34" rx="3"/>
  <text class="pf-code" x="407.5" y="186" text-anchor="middle">payload 1</text>
  <rect class="pf-item" x="357" y="208" width="101" height="34" rx="3"/>
  <text class="pf-code" x="407.5" y="230" text-anchor="middle">payload 2</text>
  <rect class="pf-item" x="357" y="252" width="101" height="34" rx="3"/>
  <text class="pf-code" x="407.5" y="274" text-anchor="middle">payload n</text>

  <rect class="pf-node" x="535" y="40" width="115" height="70" rx="3"/>
  <text class="pf-label" x="592.5" y="70" text-anchor="middle">异步任务 1</text>
  <text class="pf-sub" x="592.5" y="91" text-anchor="middle">独立调度</text>
  <rect class="pf-node" x="535" y="180" width="115" height="70" rx="3"/>
  <text class="pf-label" x="592.5" y="210" text-anchor="middle">异步任务 2</text>
  <text class="pf-sub" x="592.5" y="231" text-anchor="middle">独立调度</text>
  <rect class="pf-node" x="535" y="320" width="115" height="70" rx="3"/>
  <text class="pf-label" x="592.5" y="350" text-anchor="middle">异步任务 n</text>
  <text class="pf-sub" x="592.5" y="371" text-anchor="middle">独立调度</text>

  <rect class="pf-node" x="670" y="40" width="110" height="70" rx="3"/>
  <text class="pf-code" x="725" y="70" text-anchor="middle">run</text>
  <text class="pf-code" x="725" y="89" text-anchor="middle">(payload 1)</text>
  <rect class="pf-node" x="670" y="180" width="110" height="70" rx="3"/>
  <text class="pf-code" x="725" y="210" text-anchor="middle">run</text>
  <text class="pf-code" x="725" y="229" text-anchor="middle">(payload 2)</text>
  <rect class="pf-node" x="670" y="320" width="110" height="70" rx="3"/>
  <text class="pf-code" x="725" y="350" text-anchor="middle">run</text>
  <text class="pf-code" x="725" y="369" text-anchor="middle">(payload n)</text>

  <path class="pf-line" d="M140,215 L180,215" marker-end="url(#plugin-flow-arrow-zh)"/>
  <path class="pf-line" d="M300,215 L340,215" marker-end="url(#plugin-flow-arrow-zh)"/>
  <path class="pf-line" d="M475,181 C 500,181 500,75 515,75 L535,75" marker-end="url(#plugin-flow-arrow-zh)"/>
  <path class="pf-line" d="M475,215 L535,215" marker-end="url(#plugin-flow-arrow-zh)"/>
  <path class="pf-line" d="M475,269 C 500,269 500,355 515,355 L535,355" marker-end="url(#plugin-flow-arrow-zh)"/>
  <path class="pf-line" d="M650,75 L670,75" marker-end="url(#plugin-flow-arrow-zh)"/>
  <path class="pf-line" d="M650,215 L670,215" marker-end="url(#plugin-flow-arrow-zh)"/>
  <path class="pf-line" d="M650,355 L670,355" marker-end="url(#plugin-flow-arrow-zh)"/>
</svg>
</figure>

- `plan()` 接收用户提交的表单参数，返回 JSON 数组。数组中的每一项都会成为一条独立任务；返回空数组也属于规划成功。
- `run()` 每次接收一项 payload，完成真正的业务处理。
- 同一插件的 `plan()` 在单个节点上串行规划；`run()` 可以按插件并发值并行执行。
- 每次调用都会创建新的 WASM Instance。不要依赖 `plan()` 与 `run()` 之间的线性内存或全局变量；需要传递的数据必须进入 payload 或外部持久化存储。
- submission 的 `COMPLETED` 只表示规划与任务投递完成，不代表所有子任务都已执行成功。

通常应让 `plan()` 只负责发现工作并生成稳定、较小的 payload，把网络下载和数据写入放在 `run()` 中。Host API 在两个阶段都可用，但规划阶段产生的外部副作用同样可能被重复执行。

## 准备 Rust 工程

安装 Rust 的裸 WASM target：

```sh
rustup target add wasm32-unknown-unknown
cargo new --lib metadata-enricher
cd metadata-enricher
```

将 crate 构建为动态库，并为发行构建启用体积优化：

```toml
[package]
name = "metadata_enricher"
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

UniRhy 不提供 WASI。插件不能直接使用 guest 文件系统、socket 或线程；这些能力必须通过 `env` 模块下的 Host API 获得。

## 编写 plugin.yml

插件包根目录必须包含 `plugin.yml` 与 `plugin.wasm`。下面的 manifest 声明了一个带两个表单字段的元数据补全任务：

```yaml
id: com.example.unirhy.metadata-enricher
name: Metadata Enricher
version: 0.1.0

runtime:
  type: wasm
  abi: unirhy-wasm-abi-v1

task:
  type: ENRICH_METADATA
  concurrency: 4

form:
  schema:
    $schema: https://json-schema.org/draft/2020-12/schema
    type: object
    title: 补全元数据
    description: 从外部来源补全曲目信息
    properties:
      query:
        type: string
        title: 搜索词
        minLength: 1
      dryRun:
        type: boolean
        title: 仅预览
        default: true
    required:
      - query
      - dryRun
    additionalProperties: false
  order:
    - query
    - dryRun
```

关键约束如下：

- `id` 同时是任务 namespace，使用至少两段的小写反向域名格式；`app.unirhy` 前缀由内建任务保留。
- `task.type` 必须是大写标识符。`id` 与 `task.type` 一起构成稳定的任务身份。
- `task.concurrency` 是首次安装时的 `run()` 并发初始值。管理员之后可以修改它；同 id 升级不会覆盖当前值。
- `version` 只用于展示，服务端不比较版本号，也不解析依赖。
- `form.schema` 使用 JSON Schema Draft 2020-12 的白名单子集。字段仅支持 `string`、`integer`、`number` 与 `boolean` 标量。
- 根 schema 必须声明 `type: object`、`properties`、`required` 和 `additionalProperties: false`；`form.order` 必须恰好包含全部字段。
- `default` 只负责初始化前端表单。服务端不会补默认值，因此必填字段仍必须出现在提交参数中。

如果任务不需要参数，可以完全省略 `form`；UniRhy 会使用不接受任何字段的空表单。

## 实现 WASM ABI

ABI v1 要求模块提供线性内存，并导出以下函数：

| 导出      | 签名                         | 作用                                    |
| --------- | ---------------------------- | --------------------------------------- |
| `alloc`   | `(i32 size) -> i32`          | 为 host 写入数据分配 guest 内存         |
| `dealloc` | `(i32 ptr, i32 len)`         | 释放跨边界传输使用的 guest 内存         |
| `plan`    | `(i32 ptr, i32 len) -> i64`  | 接收 UTF-8 参数 JSON，返回 payload 数组 |
| `run`     | `(i32 ptr, i32 len) -> void` | 接收一项 UTF-8 payload JSON并执行任务   |

`plan()` 的 `i64` 返回值把输出指针放在高 32 位、字节长度放在低 32 位：

```text
(ptr << 32) | len
```

下面是可编译的最小实现。它把表单参数包装成一条 payload，并在执行阶段写入服务端日志：

```rust
use serde::de::DeserializeOwned;
use serde::{Deserialize, Serialize};
use std::{mem, ptr, slice};

#[link(wasm_import_module = "env")]
extern "C" {
    fn host_log(level: i32, ptr: i32, len: i32);
}

#[derive(Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
struct Payload {
    query: String,
    dry_run: bool,
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

unsafe fn read_json<T: DeserializeOwned>(pointer: u32, size: u32) -> T {
    let bytes = slice::from_raw_parts(pointer as *const u8, size as usize);
    serde_json::from_slice(bytes).expect("invalid JSON input")
}

fn return_json<T: Serialize>(value: &T) -> u64 {
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
pub unsafe extern "C" fn plan(pointer: u32, size: u32) -> u64 {
    let params: Payload = read_json(pointer, size);
    return_json(&vec![params])
}

#[no_mangle]
pub unsafe extern "C" fn run(pointer: u32, size: u32) {
    let payload: Payload = read_json(pointer, size);
    let message = format!(
        "metadata-enricher: query={}, dryRun={}",
        payload.query, payload.dry_run
    );
    host_log(1, message.as_ptr() as i32, message.len() as i32);
}
```

`host_log` 的日志级别为 `0 = debug`、`1 = info`、`2 = warn`，其他值按 error 记录。

## 调用 Host API

Host imports 位于 `env` 模块。除日志和二进制存储函数外，它们统一使用以下签名：

```text
(i32 requestPtr, i32 requestLen) -> i64
```

guest 将 UTF-8 JSON Object 写入自己的线性内存，调用 Host 函数，再按 `plan()` 相同的方式解包响应指针与长度。读取响应后必须调用自己的 `dealloc()`。

例如，调用 `host_artist_search` 时请求可以是：

```json
{ "name": "Miles Davis" }
```

JSON Host API 始终返回信封：

```json
{ "ok": true, "data": [{ "id": 42, "displayName": "Miles Davis" }] }
```

```json
{
  "ok": false,
  "error": { "code": "NOT_FOUND", "message": "The requested resource was not found" }
}
```

插件必须先检查 `ok`，再读取 `data`。当前通用错误码为 `INVALID_ARGUMENT`、`NOT_FOUND`、`CONFLICT`、`RESPONSE_TOO_LARGE` 与 `INTERNAL`。无效指针、越界内存或无法解析的请求 JSON 属于 ABI 错误，会直接使本次 WASM 调用失败。

当前 Host API 覆盖以下领域：

| 领域       | 能力                                               |
| ---------- | -------------------------------------------------- |
| 基础       | 分级日志、HTTP 请求、将 HTTP 响应流式下载到存储    |
| 音乐元数据 | Artist、Work、Recording、Album 的查询与变更        |
| 媒体       | MediaFile 与 Asset 的查询、创建和删除              |
| 存储       | FS/OSS 节点查询，对象列举、stat、读、写与删除      |
| 歌单       | 查询、创建、修改、删除和曲目顺序管理               |
| 任务       | 查询任务定义、提交与管理 submission/task、读取统计 |
| 只读元数据 | 插件与账号查询                                     |

分页接口默认每页 100 条，`pageIndex` 从 0 开始，`pageSize` 最大为 1000。完整的函数名称、请求与响应结构见 [Host API 参考](https://github.com/Coooolfan/UniRhy/blob/main/docs/PLUGIN_HOST_API.md)。Host 函数目录不属于长期稳定性承诺；升级 UniRhy 时，应使用目标版本重新构建并验证插件。

### 二进制与大文件

三个接口采用特殊约定：

- `host_storage_object_read(requestPtr, requestLen) -> i64` 直接返回原始字节；对象不存在时返回 `0`。读取前先调用 stat 并确保 guest 内存足够。
- `host_storage_object_write(metaPtr, metaLen, dataPtr, dataLen) -> i64` 将原始字节写入指定对象，返回 JSON 信封。
- `host_http_download_to_storage` 仍使用 JSON 请求，但由服务端直接把响应流写入 FS/OSS，不经过 guest 内存，适合音频等大文件。

普通 `host_http_request` 的响应体会经过 Base64 和 guest 内存，硬上限为 256 MiB。大文件始终优先使用流式下载接口。

## 事务、失败与幂等

数据库 Host API 写入参与当前 `plan()` 或 `run()` 的事务。每次 JSON Host 调用还会建立嵌套 savepoint：一次调用返回失败信封时，其数据库写入被回滚，但插件仍可处理错误并继续调用其他 API。随后如果整个 `plan()` 或 `run()` 失败，本次调用产生的数据库写入也会一并回滚。

HTTP、文件系统与 OSS 副作用不在 PostgreSQL 事务内。节点退出、连接中断或管理员手动重置失败任务时，`plan()` 或 `run()` 可能再次执行，而此前的外部写入可能已经完成。因此：

- 使用确定性的 object key，并在写入前检查目标状态；
- 用 `overwrite` 明确表达覆盖意图；
- 让重复执行得到相同结果，或能够识别已经完成的步骤；
- 不要把 submission 或 task 的状态当作外部副作用恰好执行一次的证明。

普通执行异常不会自动重试，失败记录需要管理员手动重置为 `PENDING`。如果 HTTP 请求需要重试，应在单次调用内设置有限次数并自行处理退避。

## 构建与打包

编译 WASM：

```sh
cargo build --release --target wasm32-unknown-unknown
```

创建包目录，并确保两个文件位于 ZIP 根目录：

```sh
mkdir -p dist/plugin
cp plugin.yml dist/plugin/plugin.yml
cp target/wasm32-unknown-unknown/release/metadata_enricher.wasm dist/plugin/plugin.wasm
(cd dist/plugin && zip -r ../metadata-enricher-0.1.0.up plugin.yml plugin.wasm)
unzip -l dist/metadata-enricher-0.1.0.up
```

`.up` 本质是 ZIP 文件。压缩包上限为 10 MiB，`plugin.wasm` 条目上限为 20 MiB。

## 安装与验证

1. 使用管理员账号打开“设置 → 插件”，上传 `.up` 文件。
2. 上传后的插件保持禁用。启用时，服务端会实例化 WASM，并校验 Host imports 与 `alloc`、`dealloc`、`plan`、`run` 导出。
3. 打开“任务管理”，选择插件任务，填写 schema 生成的表单并提交。
4. 先确认 submission 规划成功，再检查其子任务是否全部完成；失败原因会记录在对应资源上。
5. 使用 `host_log` 输出可定位的任务标识与关键阶段，但不要记录凭据、Cookie 或完整媒体内容。

常见启用失败通常来自导入名称或签名不匹配、缺少必需导出、使用 WASI、ABI 值错误，或打包后文件不在 ZIP 根目录。上传能证明 manifest 与 WASM 格式可解析；只有启用成功才证明模块能够与当前 Host API 完整链接。

## 升级插件

上传相同 `id` 的包表示覆盖升级，上传后插件会回到禁用状态。升级必须保持 `task.type` 不变，并能处理旧版本已经产生但尚未执行的 payload。服务端不会把插件版本写入任务，也不会为待执行任务保留旧 WASM。

如果 payload 协议无法向后兼容，请使用新的插件 `id`。发布前至少验证：旧 payload 可由新 `run()` 处理、重复执行不会破坏外部数据、目标 UniRhy 版本提供全部所需 Host imports。
