---
title: Plugin Development
description: Build, package, and validate a UniRhy plugin, starting with the manifest, WASM ABI, and task lifecycle.
---

UniRhy plugins are WebAssembly modules that run on the server. Each plugin provides one asynchronous task and can query or modify the music library, access storage nodes, make HTTP requests, and submit other tasks. This guide uses Rust to build a minimal plugin. UniRhy does not tie plugins to a particular programming language; any language capable of producing a compatible WebAssembly module can be used.

> Plugins have capabilities close to those of an administrator and run inside the server's trust boundary. In the current version, UniRhy does not assign per-plugin network allowlists, call deadlines, or memory caps. Install only plugins you trust and that target your current UniRhy version.

> The plugin system is still at an early stage. ABI stability and forward or backward compatibility are not currently guaranteed.

## Understand the execution model

A plugin task has a planning stage and an execution stage:

<figure class="diagram-figure plugin-flow-figure" role="img" aria-labelledby="plugin-flow-title-en" aria-describedby="plugin-flow-desc-en">
<span id="plugin-flow-title-en" class="sr-only">Flow from a plugin form submission to asynchronous execution</span>
<span id="plugin-flow-desc-en" class="sr-only">Form parameters are passed to plan as JSON. Plan returns a list of payloads. Each payload creates a separate asynchronous task, and each task invokes run independently.</span>
<svg class="plugin-flow-diagram" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 430" role="presentation" aria-hidden="true" focusable="false">
  <defs>
    <marker id="plugin-flow-arrow-en" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
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
  <text class="pf-label" x="80" y="210" text-anchor="middle">Form params</text>
  <text class="pf-sub" x="80" y="232" text-anchor="middle">params JSON</text>

  <rect class="pf-node" x="180" y="180" width="120" height="70" rx="3"/>
  <text class="pf-code" x="240" y="211" text-anchor="middle">plan(params)</text>
  <text class="pf-sub" x="240" y="232" text-anchor="middle">runs once</text>

  <rect class="pf-node" x="340" y="125" width="135" height="180" rx="3"/>
  <text class="pf-label" x="407.5" y="151" text-anchor="middle">Payload list</text>
  <rect class="pf-item" x="357" y="164" width="101" height="34" rx="3"/>
  <text class="pf-code" x="407.5" y="186" text-anchor="middle">payload 1</text>
  <rect class="pf-item" x="357" y="208" width="101" height="34" rx="3"/>
  <text class="pf-code" x="407.5" y="230" text-anchor="middle">payload 2</text>
  <rect class="pf-item" x="357" y="252" width="101" height="34" rx="3"/>
  <text class="pf-code" x="407.5" y="274" text-anchor="middle">payload n</text>

  <rect class="pf-node" x="535" y="40" width="115" height="70" rx="3"/>
  <text class="pf-label" x="592.5" y="70" text-anchor="middle">Async task 1</text>
  <text class="pf-sub" x="592.5" y="91" text-anchor="middle">scheduled alone</text>
  <rect class="pf-node" x="535" y="180" width="115" height="70" rx="3"/>
  <text class="pf-label" x="592.5" y="210" text-anchor="middle">Async task 2</text>
  <text class="pf-sub" x="592.5" y="231" text-anchor="middle">scheduled alone</text>
  <rect class="pf-node" x="535" y="320" width="115" height="70" rx="3"/>
  <text class="pf-label" x="592.5" y="350" text-anchor="middle">Async task n</text>
  <text class="pf-sub" x="592.5" y="371" text-anchor="middle">scheduled alone</text>

  <rect class="pf-node" x="670" y="40" width="110" height="70" rx="3"/>
  <text class="pf-code" x="725" y="70" text-anchor="middle">run</text>
  <text class="pf-code" x="725" y="89" text-anchor="middle">(payload 1)</text>
  <rect class="pf-node" x="670" y="180" width="110" height="70" rx="3"/>
  <text class="pf-code" x="725" y="210" text-anchor="middle">run</text>
  <text class="pf-code" x="725" y="229" text-anchor="middle">(payload 2)</text>
  <rect class="pf-node" x="670" y="320" width="110" height="70" rx="3"/>
  <text class="pf-code" x="725" y="350" text-anchor="middle">run</text>
  <text class="pf-code" x="725" y="369" text-anchor="middle">(payload n)</text>

  <path class="pf-line" d="M140,215 L180,215" marker-end="url(#plugin-flow-arrow-en)"/>
  <path class="pf-line" d="M300,215 L340,215" marker-end="url(#plugin-flow-arrow-en)"/>
  <path class="pf-line" d="M475,181 C 500,181 500,75 515,75 L535,75" marker-end="url(#plugin-flow-arrow-en)"/>
  <path class="pf-line" d="M475,215 L535,215" marker-end="url(#plugin-flow-arrow-en)"/>
  <path class="pf-line" d="M475,269 C 500,269 500,355 515,355 L535,355" marker-end="url(#plugin-flow-arrow-en)"/>
  <path class="pf-line" d="M650,75 L670,75" marker-end="url(#plugin-flow-arrow-en)"/>
  <path class="pf-line" d="M650,215 L670,215" marker-end="url(#plugin-flow-arrow-en)"/>
  <path class="pf-line" d="M650,355 L670,355" marker-end="url(#plugin-flow-arrow-en)"/>
</svg>
</figure>

- `plan()` receives the submitted form parameters and returns a JSON array. Each item becomes a separate task; an empty array is also a successful plan.
- `run()` receives one payload at a time and performs the actual work.
- A plugin's `plan()` calls are single-flight on each node, while `run()` calls may execute in parallel up to the configured plugin concurrency.
- Every call gets a fresh WASM Instance. Linear memory and globals are not shared between `plan()` and `run()`; pass data through the payload or external persistent storage.
- A `COMPLETED` submission means planning and task insertion succeeded. It does not mean that every child task has completed.

As a rule, keep `plan()` focused on discovering work and producing small, stable payloads. Put network downloads and mutations in `run()`. Host APIs are available in both stages, but external side effects performed during planning can also be repeated.

## Prepare a Rust project

Install Rust's bare WASM target:

```sh
rustup target add wasm32-unknown-unknown
cargo new --lib metadata-enricher
cd metadata-enricher
```

Build the crate as a dynamic library and optimize release builds for size:

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

UniRhy does not provide WASI. A plugin cannot directly use a guest file system, sockets, or threads; obtain those capabilities through the Host APIs in the `env` module.

## Write plugin.yml

The package root must contain `plugin.yml` and `plugin.wasm`. This manifest declares a metadata enrichment task with two form fields:

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
    title: Enrich metadata
    description: Complete track information from an external source
    properties:
      query:
        type: string
        title: Search query
        minLength: 1
      dryRun:
        type: boolean
        title: Preview only
        default: true
    required:
      - query
      - dryRun
    additionalProperties: false
  order:
    - query
    - dryRun
```

The important constraints are:

- `id` is also the task namespace. Use a lowercase reverse-domain name with at least two segments; the `app.unirhy` prefix is reserved for built-in tasks.
- `task.type` must be an uppercase identifier. Together, `id` and `task.type` form the stable task identity.
- `task.concurrency` is the initial `run()` concurrency on first install. An administrator may change it later, and an upgrade with the same id preserves the current value.
- `version` is display-only. The server does not compare versions or resolve dependencies.
- `form.schema` uses a supported subset of JSON Schema Draft 2020-12. Fields may only be scalar `string`, `integer`, `number`, or `boolean` values.
- The root schema must define `type: object`, `properties`, `required`, and `additionalProperties: false`. `form.order` must contain every field exactly once.
- `default` only initializes the frontend form. The server does not inject defaults, so required fields must still be present in submitted parameters.

If the task takes no parameters, omit `form`; UniRhy supplies an empty form that accepts no fields.

## Implement the WASM ABI

ABI v1 requires linear memory and the following exports:

| Export    | Signature                    | Purpose                                                 |
| --------- | ---------------------------- | ------------------------------------------------------- |
| `alloc`   | `(i32 size) -> i32`          | Allocate guest memory for data written by the host      |
| `dealloc` | `(i32 ptr, i32 len)`         | Release guest memory used for boundary transfers        |
| `plan`    | `(i32 ptr, i32 len) -> i64`  | Receive UTF-8 parameter JSON and return a payload array |
| `run`     | `(i32 ptr, i32 len) -> void` | Receive one UTF-8 payload and execute it                |

The `i64` returned by `plan()` stores the output pointer in the high 32 bits and its byte length in the low 32 bits:

```text
(ptr << 32) | len
```

This minimal implementation wraps the form parameters in one payload and writes a server log during execution:

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

`host_log` levels are `0 = debug`, `1 = info`, and `2 = warn`; every other value is logged as an error.

## Call Host APIs

Host imports live in the `env` module. Except for logging and binary storage operations, they share this signature:

```text
(i32 requestPtr, i32 requestLen) -> i64
```

The guest writes a UTF-8 JSON Object into its linear memory, calls the Host function, then unpacks the response pointer and length exactly as it does for `plan()`. Call your own `dealloc()` after reading the response.

For example, a request to `host_artist_search` can be:

```json
{ "name": "Miles Davis" }
```

JSON Host APIs always return an envelope:

```json
{ "ok": true, "data": [{ "id": 42, "displayName": "Miles Davis" }] }
```

```json
{
  "ok": false,
  "error": { "code": "NOT_FOUND", "message": "The requested resource was not found" }
}
```

Check `ok` before reading `data`. The common error codes are `INVALID_ARGUMENT`, `NOT_FOUND`, `CONFLICT`, `RESPONSE_TOO_LARGE`, and `INTERNAL`. Invalid pointers, out-of-bounds memory, or an unparseable request JSON are ABI failures and abort the current WASM call.

The current Host API covers these domains:

| Domain             | Capabilities                                                                |
| ------------------ | --------------------------------------------------------------------------- |
| Foundation         | Leveled logging, HTTP requests, and streaming HTTP responses into storage   |
| Music metadata     | Query and mutate Artists, Works, Recordings, and Albums                     |
| Media              | Query, create, and delete MediaFiles and Assets                             |
| Storage            | Query FS/OSS nodes; list, stat, read, write, and delete objects             |
| Playlists          | Query, create, update, delete, and manage track order                       |
| Tasks              | Query definitions, submit and manage submissions/tasks, and read statistics |
| Read-only metadata | Query plugins and accounts                                                  |

Paged calls default to 100 rows. `pageIndex` starts at 0, and `pageSize` is capped at 1000. See the [Host API reference](https://github.com/Coooolfan/UniRhy/blob/main/docs/PLUGIN_HOST_API.md) for every function name and request/response shape. The Host function catalog is not a long-term compatibility promise; rebuild and validate plugins against the target UniRhy version when upgrading.

### Binary data and large files

Three operations use special conventions:

- `host_storage_object_read(requestPtr, requestLen) -> i64` returns raw bytes and returns `0` when the object does not exist. Call stat first and ensure the guest has enough memory.
- `host_storage_object_write(metaPtr, metaLen, dataPtr, dataLen) -> i64` writes raw bytes to an object and returns a JSON envelope.
- `host_http_download_to_storage` still takes JSON, but the server streams the response directly into FS/OSS without passing through guest memory. Use it for audio and other large files.

A normal `host_http_request` response passes through Base64 and guest memory and has a hard 256 MiB limit. Prefer the streaming download call for large content.

## Transactions, failures, and idempotency

Database writes made through Host APIs participate in the current `plan()` or `run()` transaction. Each JSON Host call also creates a nested savepoint: if one call returns an error envelope, its database changes are rolled back while the plugin can inspect the error and continue. If the entire `plan()` or `run()` later fails, all database changes from that execution are rolled back.

HTTP, file system, and OSS effects are outside the PostgreSQL transaction. A node shutdown, broken connection, or manual reset of a failed task can cause `plan()` or `run()` to execute again after an external write already completed. Therefore:

- use deterministic object keys and inspect the destination before writing;
- express overwrite intent explicitly with `overwrite`;
- make repeated execution produce the same result, or detect completed steps;
- do not treat submission or task state as proof that an external effect happened exactly once.

Ordinary execution errors are not retried automatically. An administrator must reset a failed record to `PENDING`. If an HTTP call needs retries, perform a bounded number inside one invocation and implement the backoff yourself.

## Build and package

Compile the module:

```sh
cargo build --release --target wasm32-unknown-unknown
```

Create the package directory and keep both files at the ZIP root:

```sh
mkdir -p dist/plugin
cp plugin.yml dist/plugin/plugin.yml
cp target/wasm32-unknown-unknown/release/metadata_enricher.wasm dist/plugin/plugin.wasm
(cd dist/plugin && zip -r ../metadata-enricher-0.1.0.up plugin.yml plugin.wasm)
unzip -l dist/metadata-enricher-0.1.0.up
```

A `.up` package is a ZIP file. The compressed package limit is 10 MiB, and the `plugin.wasm` entry limit is 20 MiB.

## Install and verify

1. Sign in as an administrator, open _Settings → Plugins_, and upload the `.up` file.
2. Uploaded plugins remain disabled. When you enable one, the server instantiates its WASM and validates the Host imports plus the `alloc`, `dealloc`, `plan`, and `run` exports.
3. Open _Task Management_, choose the plugin task, complete the schema-generated form, and submit it.
4. Confirm that the submission planned successfully, then inspect its child tasks. Failure reasons are recorded on the corresponding resources.
5. Use `host_log` with a useful task identifier and stage, but never log credentials, cookies, or full media contents.

Common enable failures come from a mismatched import name or signature, a missing required export, use of WASI, an incorrect ABI value, or package files that are not at the ZIP root. A successful upload only proves that the manifest and module can be parsed; successful enablement proves that the module links against the current Host API.

## Upgrade a plugin

Uploading a package with the same `id` is an in-place upgrade, and the plugin becomes disabled again. An upgrade must keep `task.type` unchanged and remain able to process payloads created by the previous version. The server does not record the plugin version on tasks and does not retain the old WASM for pending work.

If the payload protocol cannot stay compatible, publish the change under a new plugin `id`. Before release, verify at minimum that the new `run()` accepts old payloads, repeated execution does not corrupt external data, and the target UniRhy version exposes every required Host import.
