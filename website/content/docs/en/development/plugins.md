---
title: Plugin Development
description: Build, package, and validate a UniRhy plugin, starting with the manifest, WASM ABI, and task lifecycle.
---

UniRhy plugins are WebAssembly modules that run on the server. A plugin declares one or more asynchronous tasks and can query or modify the music library, access storage nodes, make HTTP requests, and create further tasks. This guide uses Rust to build a minimal plugin. UniRhy does not tie plugins to a particular programming language; any language capable of producing a compatible WebAssembly module can be used.

> Plugins have capabilities close to those of an administrator and run inside the server's trust boundary. In the current version, UniRhy does not assign per-plugin network allowlists, call deadlines, or memory caps. Install only plugins you trust and that target your current UniRhy version.

> The plugin system is still at an early stage. ABI stability and forward or backward compatibility are not currently guaranteed.

## Understand the execution model

Every task is the same kind of record and runs through the same exported function. A task receives its payload, does its work, and returns the successor tasks it wants to enqueue. Returning none makes it a leaf:

<figure class="diagram-figure plugin-flow-figure" role="img" aria-labelledby="plugin-flow-title-en" aria-describedby="plugin-flow-desc-en">
<span id="plugin-flow-title-en" class="sr-only">Flow from a plugin form submission to asynchronous execution</span>
<span id="plugin-flow-desc-en" class="sr-only">Form parameters become the payload of a root task. The server calls execute once for that task, and the call returns a list of successors. Each successor becomes a child task that is scheduled independently and calls execute again, which may in turn return further successors.</span>
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

  <rect class="pf-node" x="16" y="180" width="118" height="70" rx="3"/>
  <text class="pf-label" x="75" y="207" text-anchor="middle">Form params</text>
  <text class="pf-sub" x="75" y="229" text-anchor="middle">root payload</text>

  <rect class="pf-node" x="174" y="180" width="140" height="70" rx="3"/>
  <text class="pf-code" x="244" y="207" text-anchor="middle">execute()</text>
  <text class="pf-sub" x="244" y="229" text-anchor="middle">entry task</text>

  <rect class="pf-node" x="354" y="120" width="140" height="190" rx="3"/>
  <text class="pf-label" x="424" y="146" text-anchor="middle">successors</text>
  <rect class="pf-item" x="371" y="160" width="106" height="36" rx="3"/>
  <text class="pf-code" x="424" y="183" text-anchor="middle">successor 1</text>
  <rect class="pf-item" x="371" y="204" width="106" height="36" rx="3"/>
  <text class="pf-code" x="424" y="227" text-anchor="middle">successor 2</text>
  <rect class="pf-item" x="371" y="248" width="106" height="36" rx="3"/>
  <text class="pf-code" x="424" y="271" text-anchor="middle">successor n</text>

  <rect class="pf-node" x="534" y="36" width="180" height="70" rx="3"/>
  <text class="pf-code" x="624" y="63" text-anchor="middle">execute()</text>
  <text class="pf-sub" x="624" y="85" text-anchor="middle">child task, may return successors</text>
  <rect class="pf-node" x="534" y="180" width="180" height="70" rx="3"/>
  <text class="pf-code" x="624" y="207" text-anchor="middle">execute()</text>
  <text class="pf-sub" x="624" y="229" text-anchor="middle">child task, may return successors</text>
  <rect class="pf-node" x="534" y="324" width="180" height="70" rx="3"/>
  <text class="pf-code" x="624" y="351" text-anchor="middle">execute()</text>
  <text class="pf-sub" x="624" y="373" text-anchor="middle">child task, may return successors</text>

  <path class="pf-line" d="M134,215 L174,215" marker-end="url(#plugin-flow-arrow-en)"/>
  <path class="pf-line" d="M314,215 L354,215" marker-end="url(#plugin-flow-arrow-en)"/>
  <path class="pf-line" d="M494,178 C 514,178 514,71 524,71 L534,71" marker-end="url(#plugin-flow-arrow-en)"/>
  <path class="pf-line" d="M494,215 L534,215" marker-end="url(#plugin-flow-arrow-en)"/>
  <path class="pf-line" d="M494,265 C 514,265 514,359 524,359 L534,359" marker-end="url(#plugin-flow-arrow-en)"/>
</svg>
</figure>

- The server calls `execute()` once per task and passes `{taskId, taskType, payload}`. One plugin exports exactly one `execute()`; the plugin dispatches on `taskType` itself.
- The returned successors are enqueued as children of the current task, in the same transaction that marks the current task `COMPLETED`. Each child is then scheduled on its own.
- A successor may target another task type of the same plugin, or set `namespace` explicitly to hand work to another plugin.
- Concurrency is a per-task-type property. An entry task that fans work out is usually configured with concurrency `1`, while the worker task it produces runs with a higher value.
- Every call gets a fresh WASM Instance. Linear memory and globals are not shared between tasks; pass data through the payload, plugin data, or external persistent storage.
- Successors are deduplicated among active siblings: a successor whose parent, namespace, task type, and payload match a `PENDING` or `RUNNING` sibling is silently dropped. Root tasks do not participate in this deduplication.
- A `COMPLETED` task only means that one call succeeded and its successors were enqueued. It does not mean that the subtree below it has finished.

As a rule, keep an entry task focused on discovering work and producing small, stable payloads, and put network downloads and mutations in the worker tasks. Host APIs are available to every task, but external side effects performed while fanning out can also be repeated.

## Prepare a Rust project

Install Rust's bare WASM target:

```sh
rustup target add wasm32-unknown-unknown
cargo new --lib artist-enricher
cd artist-enricher
```

Build the crate as a dynamic library and optimize release builds for size:

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

UniRhy does not provide WASI. A plugin cannot directly use a guest file system, sockets, or threads; obtain those capabilities through the Host APIs in the `env` module.

## Write plugin.yml

The package root must contain `plugin.yml` and `plugin.wasm`. This manifest declares an entry task that users submit, a worker task that only the plugin itself produces, and one plugin-level configuration field:

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
        title: Scan artists
        description: Split the artist library into enrichment batches
        properties:
          batchSize:
            type: integer
            title: Batch size
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
        title: Enrich one batch
        properties:
          offset:
            type: integer
            title: Offset
            minimum: 0
          limit:
            type: integer
            title: Limit
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
    title: Metadata source
    properties:
      endpoint:
        type: string
        title: API endpoint
        minLength: 1
      apiKey:
        type: string
        title: API key
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

The important constraints are:

- `id` is also the task namespace. Use a lowercase reverse-domain name with at least two segments; the `app.unirhy` prefix is reserved for built-in tasks.
- `tasks[].type` must be an uppercase identifier and unique within the plugin. Together, `id` and `tasks[].type` form the stable task identity.
- At least one task must set `userSubmittable: true`. Only such a task can be submitted from the UI or through `host_task_create`; every other task can only appear as a successor.
- `tasks[].concurrency` is the initial concurrency of that task type on first install. An administrator may change it later, and an upgrade with the same id preserves the current value.
- `version` is display-only. The server does not compare versions or resolve dependencies.
- `form.schema` uses a supported subset of JSON Schema Draft 2020-12. Fields may be scalar `string`, `integer`, `number`, or `boolean` values, or homogeneous `string` / `integer` / `number` arrays.
- The root schema must define `type: object`, `properties`, `required`, and `additionalProperties: false`. `form.order` must contain every field exactly once.
- On a user-submittable task, `form` is validated against the submitted parameters. On other tasks it documents the payload contract; the server does not re-validate payloads carried by successors.
- `default` only initializes the frontend form. The server does not inject defaults, so required fields must still be present in submitted parameters.
- `config` declares plugin-level settings that an administrator fills in from the plugin page, not per task. It follows the same schema subset, except that arrays are not allowed and a `string` field may declare `writeOnly: true`.
- A `writeOnly` field is stored encrypted and never returned to the management UI, which only reports whether the field has been set. The plugin itself reads the full value through `host_plugin_config_get`.
- A plugin whose `config` has unsatisfied required fields cannot be enabled.

If a task takes no parameters, omit its `form`; UniRhy supplies an empty form that accepts no fields. Omit `config` entirely when the plugin needs no settings.

## Implement the WASM ABI

ABI v1 requires linear memory and the following exports:

| Export    | Signature                   | Purpose                                            |
| --------- | --------------------------- | -------------------------------------------------- |
| `alloc`   | `(i32 size) -> i32`         | Allocate guest memory for data written by the host |
| `dealloc` | `(i32 ptr, i32 len)`        | Release guest memory used for boundary transfers   |
| `execute` | `(i32 ptr, i32 len) -> i64` | Run one task and return its result envelope        |

`execute()` receives a UTF-8 JSON object:

```json
{ "taskId": 42, "taskType": "SCAN_ARTISTS", "payload": { "batchSize": 50 } }
```

and returns an envelope. On success, `successors` may be omitted or empty for a leaf task; `namespace` defaults to the plugin's own id:

```json
{
  "ok": true,
  "successors": [{ "taskType": "ENRICH_ARTIST", "payload": { "offset": 0, "limit": 50 } }]
}
```

On failure, `error` must be a non-empty string. The task is recorded as `FAILED` with that message:

```json
{ "ok": false, "error": "metadata source returned 503" }
```

The returned `i64` stores the output pointer in the high 32 bits and its byte length in the low 32 bits:

```text
(ptr << 32) | len
```

This minimal implementation splits the library into batches and writes a server log for each batch:

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

`host_log` levels are `0 = debug`, `1 = info`, and `2 = warn`; every other value is logged as an error.

A panic or trap inside `execute()` also fails the task, but an explicit `{"ok": false, "error": ...}` produces a far more useful failure reason.

## Call Host APIs

Host imports live in the `env` module. Except for logging and binary storage operations, they share this signature:

```text
(i32 requestPtr, i32 requestLen) -> i64
```

The guest writes a UTF-8 JSON Object into its linear memory, calls the Host function, then unpacks the response pointer and length exactly as it does for its own `execute()` return value. Call your own `dealloc()` after reading the response.

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

| Domain                 | Capabilities                                                              |
| ---------------------- | ------------------------------------------------------------------------- |
| Foundation             | Leveled logging, HTTP requests, and streaming HTTP responses into storage |
| Music metadata         | Query and mutate Artists, Works, Recordings, and Albums                   |
| Media                  | Query, create, and delete MediaFiles and Assets                           |
| Storage                | Query FS/OSS nodes; list, stat, read, write, and delete objects           |
| Playlists              | Query, create, update, delete, and manage track order                     |
| Tasks                  | Query definitions, create and manage tasks, and read statistics           |
| Configuration and data | Read the plugin's own configuration, and read, write, and list its data   |
| Read-only metadata     | Query plugins and accounts                                                |

Paged calls default to 100 rows. `pageIndex` starts at 0, and `pageSize` is capped at 1000. See the [Host API reference](https://github.com/Coooolfan/UniRhy/blob/main/docs/PLUGIN_HOST_API.md) for every function name and request/response shape. The Host function catalog is not a long-term compatibility promise; rebuild and validate plugins against the target UniRhy version when upgrading.

### Configuration and plugin data

`host_plugin_config_get` returns the plugin's own configuration, including `writeOnly` fields in plaintext. Other plugins cannot read it, and each plugin's data is scoped to its own id.

`host_plugin_data_put`, `host_plugin_data_get`, and `host_plugin_data_list` provide a key-value store for state that must outlive a single task, such as a sync cursor. A key that is also declared in `config` is validated against that field's schema and, when the field is `writeOnly`, stored encrypted; any other key stores an arbitrary JSON value as-is.

### Binary data and large files

Three operations use special conventions:

- `host_storage_object_read(requestPtr, requestLen) -> i64` returns raw bytes and returns `0` when the object does not exist. Call stat first and ensure the guest has enough memory.
- `host_storage_object_write(metaPtr, metaLen, dataPtr, dataLen) -> i64` writes raw bytes to an object and returns a JSON envelope.
- `host_http_download_to_storage` still takes JSON, but the server streams the response directly into FS/OSS without passing through guest memory. Use it for audio and other large files.

A normal `host_http_request` response passes through Base64 and guest memory and has a hard 256 MiB limit. Prefer the streaming download call for large content.

## Transactions, failures, and idempotency

Database writes made through Host APIs participate in the transaction of the current task. Each JSON Host call also creates a nested savepoint: if one call returns an error envelope, its database changes are rolled back while the plugin can inspect the error and continue. If `execute()` later fails, every database change from that call is rolled back, including the successors it would have enqueued.

HTTP, file system, and OSS effects are outside the PostgreSQL transaction. A node shutdown, broken connection, or manual reset of a failed task can cause a task to execute again after an external write already completed. Therefore:

- use deterministic object keys and inspect the destination before writing;
- express overwrite intent explicitly with `overwrite`;
- keep successor payloads deterministic, so that a re-run collapses into the active-sibling deduplication instead of doubling the subtree;
- make repeated execution produce the same result, or detect completed steps;
- do not treat task state as proof that an external effect happened exactly once.

Ordinary execution errors are not retried automatically. An administrator must reset a failed or cancelled task to `PENDING`. If an HTTP call needs retries, perform a bounded number inside one invocation and implement the backoff yourself.

## Build and package

Compile the module:

```sh
cargo build --release --target wasm32-unknown-unknown
```

Create the package directory and keep both files at the ZIP root:

```sh
mkdir -p dist/plugin
cp plugin.yml dist/plugin/plugin.yml
cp target/wasm32-unknown-unknown/release/artist_enricher.wasm dist/plugin/plugin.wasm
(cd dist/plugin && zip -r ../artist-enricher-0.1.0.up plugin.yml plugin.wasm)
unzip -l dist/artist-enricher-0.1.0.up
```

A `.up` package is a ZIP file. The compressed package limit is 10 MiB, and the `plugin.wasm` entry limit is 20 MiB.

## Install and verify

1. Sign in as an administrator, open _Settings → Plugins_, and upload the `.up` file.
2. Fill in the plugin configuration if the manifest declares `config`. Required fields must be set before the plugin can be enabled.
3. Uploaded plugins remain disabled. When you enable one, the server instantiates its WASM and validates the Host imports plus the `alloc`, `dealloc`, and `execute` exports, including the `(i32, i32) -> i64` signature of `execute`.
4. Open _Task Management_, choose one of the plugin's user-submittable tasks, complete the schema-generated form, and submit it.
5. Confirm that the root task completed, then expand its task tree to inspect the successors it produced. Failure reasons are recorded on each task.
6. Use `host_log` with a useful task identifier and task type, but never log credentials, cookies, or full media contents.

Common enable failures come from a mismatched import name or signature, a missing required export, use of WASI, an incorrect ABI value, incomplete configuration, or package files that are not at the ZIP root. A successful upload only proves that the manifest and module can be parsed; successful enablement proves that the module links against the current Host API.

## Upgrade a plugin

Uploading a package with the same `id` is an in-place upgrade, and the plugin becomes disabled again. The new manifest must keep declaring every task type the previous version declared — the server rejects an upload that drops one — and each task must remain able to process payloads created by the previous version. Existing concurrency values are preserved, and stored configuration is re-encrypted to match the new declaration. The server does not record the plugin version on tasks and does not retain the old WASM for pending work.

If the payload protocol cannot stay compatible, publish the change under a new plugin `id`. Before release, verify at minimum that the new `execute()` accepts old payloads, repeated execution does not corrupt external data, and the target UniRhy version exposes every required Host import.
