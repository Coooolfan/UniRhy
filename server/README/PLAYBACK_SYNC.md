# 播放同步

同一已登录账号在多设备之间的播放听感同步规范，覆盖协议、状态模型、时序、调度与日志。

## 1. 目标与边界

同步同一账号下多设备的播放动作：

- 播放（Play）
- 暂停（Pause）
- 跳转（Seek）
- 切换曲目（Change Track）：语义等同于携带新 `recordingId` 的 `PLAY`，不设独立消息类型

边界：

- 仅同步同一 `accountId` 的设备，不做多用户房间同步。
- 单实例部署，不引入 Redis / MQ / 跨节点总线。
- 目标是“听感一致”，不追求采样级锁相。

非目标：多用户共享听歌房、多实例跨节点同步、协同播放列表编辑、设备优先级抢占。

## 2. 架构

### 2.1 通道与鉴权

- 通道：`/ws/playback-sync`
- 鉴权：复用 Sa-Token 登录态，凭证按以下顺序解析：
  1. 握手 HTTP 头 `unirhy-token`；
  2. 握手 Cookie 中的 `unirhy-token`；
  3. 首条 `HELLO` 消息的 `payload.token`（供无法自定义握手头的运行时使用，如浏览器 WebSocket）。
- 握手阶段携带了凭证但校验失败时，WebSocket 升级直接返回 `401`，不发送 `ERROR` 消息；未携带凭证时允许匿名完成握手，由 `HELLO` 的 `token` 补齐鉴权。
- 连接建立后 10 秒内必须收到合法且鉴权通过的 `HELLO`，否则服务端以 `POLICY_VIOLATION (1008)` 关闭连接。

### 2.2 服务端职责

- 权威维护账号播放会话（内存态）。
- 维护同账号设备连接集合并广播。
- 基于 NTP 风格测量计算调度窗口。
- 对播放动作做“未来时刻统一执行”的调度。

### 2.3 客户端职责

- 周期性发送 NTP 请求，估算 `clockOffset`。
- 收到调度指令后按 `serverTimeToExecuteMs` 计算本地等待时间。
- 使用 `AudioContext` + `AudioBuffer` 精确调度播放（`source.start(when, offset)`），并上报加载完成状态。
- 使用 `performance.timeOrigin + performance.now()` 作为高精度时间源。

## 3. 状态模型

### 3.1 权威稳定态（每账号一份）

```kotlin
enum class PlaybackStatus { PLAYING, PAUSED }

data class AccountPlaybackState(
    val accountId: Long,
    val status: PlaybackStatus,
    val currentIndex: Int?,
    val positionSeconds: Double,
    val serverTimeToExecuteMs: Long,
    val version: Long,
    val updatedAtMs: Long,
)
```

- `positionSeconds + serverTimeToExecuteMs` 构成播放锚点。
- `currentIndex` 指向当前队列下标，曲目身份由队列（`recordingIds[currentIndex]`）解析。
- `version` 单调递增，与队列版本共用同一计数（任何队列或播放态变更都会推进）；客户端仅接受更高版本。
- 内部状态不保存媒体地址，客户端根据队列项的 `mediaFileId` 自行构造音源 URL。
- 无播放状态用 `PAUSED + currentIndex=null` 表示，不设 `STOPPED` 状态。

### 3.2 执行中临时态（每账号可空）

```kotlin
data class PendingPlayState(
    val commandId: String,
    val initiatorDeviceId: String?,
    val currentIndex: Int,
    val recordingId: Long,
    val positionSeconds: Double,
    val clientsLoaded: MutableSet<String>,
    val createdAtMs: Long,
    val timeoutAtMs: Long,
)
```

- 只在“切歌播放需预加载”阶段存在。
- `executeScheduledPlay()` 执行时，将 pending 中的 `currentIndex` / `positionSeconds` 写入 `AccountPlaybackState`。
- 收到新 `PLAY` 时先 `clearPendingPlay()`（取消超时定时器）再创建新的，即“后到覆盖”语义。
- `clientsLoaded` 初始化即包含 `initiatorDeviceId`（发起者视为已加载）；服务端自动切歌等无发起设备的场景 `initiatorDeviceId=null`。

### 3.3 设备运行态（每账号下每设备一份）

```kotlin
data class DeviceRuntimeState(
    val deviceId: String,
    val accountId: Long,
    var rttEmaMs: Double,
    var lastNtpResponseAtMs: Long,
    var lastPongAtMs: Long,
    var lastSeenAtMs: Long,
)
```

- `rttEmaMs` 用于计算账号级调度窗口。
- `lastPongAtMs` 用于活跃性判断与断链清理：服务端每 `15s` 发送一次 WebSocket 协议层 Ping，超过 `60s` 未收到 Pong 判定为 stale。Pong 由客户端网络栈自动回复，不依赖页面 JS 运行，后台被节流的 WebView 也能响应。
- `lastNtpResponseAtMs` 仅用于校时诊断与 `SYNC` 就绪判断（`isSyncReady`），不参与活跃性判定。

### 3.4 连接注册态

- `accountId -> MutableSet<WebSocketSession>`
- `sessionId -> deviceId / accountId`

用于广播、单播与连接回收。

### 3.5 客户端本地执行态（前端）

- `clockOffsetMs`、`roundTripEstimateMs`、`ntpMeasurements[]`、`isSynced`（NTP 初始化是否完成）
- `audioContext`、`currentBuffer`、`sourceNode`、`gainNode`
- `playbackStartContextTime`（`audioContext.currentTime` 锚点）、`playbackOffsetSec`（播放起始偏移）

说明：

- 服务端不保存客户端 `clockOffset`，由客户端自行把本地时间映射到服务端时间。
- `currentTime` 由 `playbackOffsetSec + (audioContext.currentTime - playbackStartContextTime)` 计算得出。

## 4. 线级协议

### 4.1 基本约定

- 所有 WebSocket 消息统一封套：

```json
{
  "type": "PLAY",
  "payload": {}
}
```

- `type` 使用大写蛇形命名，`payload` 字段使用 camelCase。
- `recordingId` 指向后端 `Recording` 实体；UI 侧可继续展示为 Track / 曲目。
- `currentIndex` 指向当前队列（`CurrentQueueDto.items`）中的下标；播放控制与调度均以队列下标 + 队列版本号表达，不直接携带媒体地址。
- `positionSeconds` 表示播放偏移，单位秒，类型为 `Double`。
- 双方解析消息时必须忽略未知字段。

### 4.2 消息类型

| 方向 | 消息 |
| --- | --- |
| C2S | `HELLO`、`NTP_REQUEST`、`PLAY`、`PAUSE`、`SEEK`、`AUDIO_SOURCE_LOADED`、`SYNC` |
| S2C | `NTP_RESPONSE`、`SNAPSHOT`、`ROOM_EVENT_LOAD_AUDIO_SOURCE`、`ROOM_EVENT_QUEUE_CHANGE`、`SCHEDULED_ACTION`、`ROOM_EVENT_DEVICE_CHANGE`、`ERROR` |

播放状态枚举：`PLAYING`、`PAUSED`。调度动作类型：`PLAY`、`PAUSE`、`SEEK`。

### 4.3 C2S 消息

#### `HELLO`

```json
{
  "type": "HELLO",
  "payload": { "deviceId": "web-7c2f", "clientVersion": "web@0.1.0", "token": "..." }
}
```

- `deviceId: String`（非空白），`clientVersion: String?`，`token: String?`
- 连接建立后的首条业务消息必须是 `HELLO`，且同一连接只允许发送一次。
- 握手阶段未携带凭证时，`token` 必填且必须合法，否则服务端以 `POLICY_VIOLATION (1008)` 关闭连接；握手已鉴权时 `token` 被忽略。
- 同一 `accountId` 下，若新连接使用了已在线的 `deviceId`，服务端以新连接为准并关闭旧连接。

#### `NTP_REQUEST`

```json
{ "type": "NTP_REQUEST", "payload": { "t0": 1730844000000, "clientRttMs": 18.5 } }
```

- `t0: Long`，`clientRttMs: Double?`

#### `PLAY` / `PAUSE` / `SEEK`

```json
{
  "type": "PLAY",
  "payload": {
    "commandId": "cmd-play-001",
    "deviceId": "web-7c2f",
    "currentIndex": 2,
    "positionSeconds": 12.5,
    "version": 8
  }
}
```

控制类字段：`commandId: String`、`deviceId: String`、`currentIndex: Int`、`positionSeconds: Double`、`version: Long`。

约束：

- `currentIndex` 必须落在当前队列范围内，否则返回 `ERROR(INVALID_MESSAGE)`。
- `version` 必须等于客户端已知的最新播放态版本号，不一致时返回 `ERROR(VERSION_CONFLICT)`，客户端应发送 `SYNC` 恢复。

#### `AUDIO_SOURCE_LOADED`

```json
{
  "type": "AUDIO_SOURCE_LOADED",
  "payload": { "commandId": "cmd-play-001", "deviceId": "web-85ab", "currentIndex": 2, "recordingId": 1001 }
}
```

- `commandId: String`、`deviceId: String`、`currentIndex: Int`、`recordingId: Long`

#### `SYNC`

```json
{ "type": "SYNC", "payload": { "deviceId": "web-85ab" } }
```

- `deviceId: String`
- 客户端必须在 NTP 初始化完成后（`isSynced=true`）再发送。

### 4.4 S2C 消息

#### `NTP_RESPONSE`

```json
{ "type": "NTP_RESPONSE", "payload": { "t0": 1730844000000, "t1": 1730844000012, "t2": 1730844000014 } }
```

- `t0: Long`、`t1: Long`、`t2: Long`

#### `SNAPSHOT`

```json
{
  "type": "SNAPSHOT",
  "payload": {
    "state": {
      "status": "PLAYING",
      "currentIndex": 2,
      "positionSeconds": 12.5,
      "serverTimeToExecuteMs": 1730844001500,
      "version": 8,
      "updatedAtMs": 1730844000100
    },
    "queue": { "items": [], "recordingIds": [], "currentIndex": 0, "playbackStrategy": "SEQUENTIAL", "stopStrategy": "LIST", "playbackStatus": "PAUSED", "positionMs": 0, "serverTimeToExecuteMs": 0, "version": 8, "updatedAtMs": 1730844000100 }, 
    "serverNowMs": 1730844000200
  }
}
```

- `state` 字段：`status: PlaybackStatus`、`currentIndex: Int?`、`positionSeconds: Double`、`serverTimeToExecuteMs: Long`、`version: Long`、`updatedAtMs: Long`。
- `queue: CurrentQueueDto`（结构见 `ROOM_EVENT_QUEUE_CHANGE`）。
- 发送时机：①`HELLO` 响应；②断线重连后服务端自动下发（无需客户端主动 `SYNC`）。
- 队列为空时使用 `status=PAUSED`、`currentIndex=null`、`positionSeconds=0.0`。

#### `ROOM_EVENT_LOAD_AUDIO_SOURCE`

```json
{
  "type": "ROOM_EVENT_LOAD_AUDIO_SOURCE",
  "payload": { "commandId": "cmd-play-001", "currentIndex": 2, "recordingId": 1001 }
}
```

- `commandId: String`、`currentIndex: Int`、`recordingId: Long`
- 消息不携带媒体地址：客户端根据队列项的 `mediaFileId` 自行构造 `GET /api/media-files/{mediaFileId}` 加载音源（登录态鉴权，或使用队列项中已带签名的资源路径）。

#### `ROOM_EVENT_QUEUE_CHANGE`

```json
{
  "type": "ROOM_EVENT_QUEUE_CHANGE",
  "payload": {
    "queue": {
      "items": [
        {
          "recordingId": 1001,
          "title": "Track 1",
          "artistLabel": "Artist 1",
          "coverUrl": "/api/media-files/3001?_sig=...&_exp=...",
          "durationMs": 180000,
          "mediaFileId": 2001
        }
      ],
      "recordingIds": [1001],
      "currentIndex": 0,
      "playbackStrategy": "SEQUENTIAL",
      "stopStrategy": "LIST",
      "playbackStatus": "PAUSED",
      "positionMs": 0,
      "serverTimeToExecuteMs": 0,
      "version": 9,
      "updatedAtMs": 1730844000300
    }
  }
}
```

- `queue: CurrentQueueDto`：`items: CurrentQueueItemDto[]`、`recordingIds: Long[]`、`currentIndex: Int`、`playbackStrategy: PlaybackStrategy`、`stopStrategy: StopStrategy`、`playbackStatus: PlaybackStatus`、`positionMs: Long`、`serverTimeToExecuteMs: Long`、`version: Long`、`updatedAtMs: Long`。
- `CurrentQueueItemDto`：`recordingId: Long`、`title: String`、`artistLabel: String`、`coverUrl: String?`（带签名的封面路径）、`durationMs: Long`、`mediaFileId: Long?`（首个音频 asset 的媒体文件 ID，供客户端构造音源地址；录音无音频 asset 时为 `null`，客户端应跳过该曲目）。
- 广播时机：任何队列变更（替换/追加/删除/重排/切换索引/策略变更/服务端自动切歌）成功持久化后。

#### `SCHEDULED_ACTION`

```json
{
  "type": "SCHEDULED_ACTION",
  "payload": {
    "commandId": "cmd-play-001",
    "serverTimeToExecuteMs": 1730844001500,
    "scheduledAction": {
      "action": "PLAY",
      "status": "PLAYING",
      "currentIndex": 2,
      "positionSeconds": 12.5,
      "version": 8
    },
    "skipLateCompensation": false
  }
}
```

- `scheduledAction` 字段：`action: ScheduledActionType`、`status: PlaybackStatus`、`currentIndex: Int?`、`positionSeconds: Double`、`version: Long`。
- `skipLateCompensation: Boolean`（默认 `false`）：为 `true` 时，迟到执行的客户端不做“位置 + 迟到量”补偿，直接从 `positionSeconds` 起播。
- `PLAY` 与 `SEEK` 携带非空 `currentIndex`；`PAUSE` 允许 `currentIndex=null`（表示暂停且清空当前曲目）。
- `SEEK` 必须提供与当前权威态一致的 `status`，客户端按 `status` 决定 seek 后是否继续播放。

#### `ROOM_EVENT_DEVICE_CHANGE`

```json
{
  "type": "ROOM_EVENT_DEVICE_CHANGE",
  "payload": { "devices": [ { "deviceId": "web-7c2f" }, { "deviceId": "web-85ab" } ] }
}
```

- `devices: PlaybackSyncDevice[]`，`PlaybackSyncDevice.deviceId: String`。
- `devices` 仅包含已完成 `HELLO` 注册的在线设备。
- 广播时机：`HELLO` 成功后、已注册设备断开后、重复 `deviceId` 被新连接替换后。

#### `ERROR`

```json
{ "type": "ERROR", "payload": { "code": "RECORDING_NOT_PLAYABLE", "message": "Recording 1001 has no playable audio asset" } }
```

- `code: PlaybackSyncErrorCode`、`message: String`

错误码：`INVALID_MESSAGE`、`UNSUPPORTED_MESSAGE`、`VERSION_CONFLICT`、`RECORDING_NOT_FOUND`、`MEDIA_FILE_NOT_FOUND`、`RECORDING_NOT_PLAYABLE`、`SYNC_NOT_READY`、`INTERNAL_ERROR`。

### 4.5 ACK 规则

- 不定义通用 ACK 消息，改用业务型确认：
  - `NTP_REQUEST -> NTP_RESPONSE`
  - `ROOM_EVENT_LOAD_AUDIO_SOURCE -> AUDIO_SOURCE_LOADED`

## 5. 核心时序

### 5.1 冷启动与校时

1. 设备连接 WS，发送 `HELLO`。
2. 服务端返回 `SNAPSHOT`（包含当前 `AccountPlaybackState`）。
3. 客户端快速发送 NTP 采样（20~40 次，每 30ms 一次，约 1.2 秒完成）。
4. 达到采样数后标记 `isSynced=true`，进入稳态校时（每 2500ms 一次，仅用于校时，不承担活性检测）。

### 5.2 PLAY（切歌场景）

1. 设备 A 发 `PLAY { currentIndex, positionSeconds, version }`。
2. 服务端校验 `version` 与 `currentIndex` 合法性，并解析该下标对应的 `recordingId`。
3. 若已有 `PendingPlayState`，先 `clearPendingPlay()` 再创建新的（后到覆盖）。
4. 创建 `PendingPlayState`，`clientsLoaded` 初始化为 `Set([发起者 deviceId])`。
5. 广播 `ROOM_EVENT_LOAD_AUDIO_SOURCE { currentIndex, recordingId }`。
6. 各设备按队列项 `mediaFileId` 加载音源，完成后发 `AUDIO_SOURCE_LOADED`。
7. 全部加载完成或 3 秒超时后，服务端执行 `executeScheduledPlay()`：写入 `AccountPlaybackState`、计算 `serverTimeToExecuteMs`、广播 `SCHEDULED_ACTION(PLAY)`。
8. 客户端使用 `AudioBufferSourceNode.start(when, offset)` 精确调度：
   - `when = audioContext.currentTime + (serverTimeToExecuteMs - estimatedServerNowMs) / 1000`
   - `offset = positionSeconds`

### 5.3 PAUSE / SEEK

1. 发控制命令。
2. 服务端更新 `AccountPlaybackState`（`version` 自增）。
3. 计算未来执行时刻并广播 `SCHEDULED_ACTION`。
4. 各设备同一时刻执行。PAUSE 与 SEEK 为幂等操作，无需额外去重。

### 5.4 晚加入设备 SYNC

1. 新设备完成 NTP 校时后发送 `SYNC`。
2. 服务端根据当前 `AccountPlaybackState` 计算恢复位置。
3. 单播 `SCHEDULED_ACTION(PLAY)`，带额外缓冲（例如 `+1500ms`，用于音频 fetch + decode）。

断线重连的设备无需手动 `SYNC`，服务端在 `HELLO` 阶段自动下发 `SNAPSHOT`，客户端完成 NTP 校时后自行恢复。

### 5.5 活性检测与掉线

- 活性检测与 NTP 校时解耦：NTP 心跳停摆（如后台定时器节流）不构成掉线。
- 服务端：每 `15s` 向所有已完成 `HELLO` 的连接发送协议层 Ping，按 `lastPongAtMs` 检查，超过 `60s` 未收到 Pong 则关闭连接（close reason `stale_connection`），清扫周期 `10s`。
- 客户端：无活性看门狗，重连由 `close` 事件驱动；Pong 回复由网络栈/传输层自动完成，浏览器、Tauri（tungstenite）与原生客户端均无需实现。
- 设备断开后若存在 `PendingPlayState`，从 `clientsLoaded` 移除该设备，若剩余设备全部就绪则立即触发 `executeScheduledPlay()`。

## 6. 时间与调度策略

- NTP 公式：`offset = ((t1 - t0) + (t2 - t3)) / 2`，`rtt = (t3 - t0) - (t2 - t1)`。
- 过滤与平滑：客户端按 RTT 升序取最优半数计算 offset；服务端对 `clientRttMs` 做 EMA（`alpha=0.2`）。
- 调度窗口：`scheduleDelayMs = clamp(maxRttMs * 1.5 + 200, 400, 3000)`，`executeAtServerMs = serverNowMs + scheduleDelayMs`。`400ms` 为最小安全窗口，`3000ms` 为上限。

## 7. 幂等与一致性

- `clientsLoaded` 使用 `Set<deviceId>` 去重。
- 收到新 `PLAY` 时覆盖已有 `PendingPlayState`（后到覆盖），不做“同 recording 去重”。
- 客户端仅应用更高 `version` 的状态。
- PAUSE / SEEK 天然幂等。

## 8. 日志规范

当前使用 SLF4J 文本日志，不引入 MDC 或 JSON 结构日志。

### 8.1 格式

- 基本格式：`event=<name> key=value key=value ...`，每条日志都必须先输出 `event=<name>`。
- 字段名只允许复用 `com.coooolfan.unirhy.sync.log` 中定义的常量，禁止手写分叉字段。

```text
event=playback_sync_play_request accountId=42 deviceId=web-7c2f commandId=cmd-play-001 recordingId=1001 mediaFileId=2001 positionSeconds=12.5 result=accepted
```

### 8.2 公共字段

| 字段 | 含义 |
| --- | --- |
| `event` | 事件名 |
| `accountId` | 账号 ID |
| `deviceId` | 设备 ID |
| `sessionId` | WebSocket session ID |
| `commandId` | 控制命令 ID |
| `recordingId` | 录音 ID |
| `mediaFileId` | 媒体文件 ID |
| `version` | 权威状态版本号 |
| `positionSeconds` | 播放偏移，单位秒 |
| `serverNowMs` | 产生日志时的服务端时间戳 |
| `executeAtMs` | 计划执行时间戳 |
| `scheduleDelayMs` | 调度窗口时长 |
| `rttMs` | 单次 RTT 采样值 |
| `rttEmaMs` | RTT EMA 平滑值 |
| `result` | 结果，例如 `accepted`、`completed`、`rejected` |
| `reason` | 失败或分支原因 |

### 8.3 事件名

| 事件 | 触发时机 |
| --- | --- |
| `playback_sync_connection_opened` | 连接建立 |
| `playback_sync_connection_closed` | 连接关闭 |
| `playback_sync_hello_received` | 收到 `HELLO` |
| `playback_sync_ntp_request_received` | 收到 `NTP_REQUEST` |
| `playback_sync_ntp_response_sent` | 下发 `NTP_RESPONSE` |
| `playback_sync_play_request` | 收到 `PLAY` |
| `playback_sync_pause_request` | 收到 `PAUSE` |
| `playback_sync_seek_request` | 收到 `SEEK` |
| `playback_sync_pending_play_created` | 创建新的 pending play |
| `playback_sync_pending_play_replaced` | 新 `PLAY` 覆盖旧 pending play |
| `playback_sync_audio_source_loaded` | 收到 `AUDIO_SOURCE_LOADED` |
| `playback_sync_scheduled_action_sent` | 下发 `SCHEDULED_ACTION` |
| `playback_sync_snapshot_sent` | 下发 `SNAPSHOT` |
| `playback_sync_protocol_error` | 协议校验失败或反序列化失败 |

### 8.4 使用规则

- 字段缺失时可省略对应 `key=value`，但不能改名。
- `reason` 用于描述拒绝或分支原因，例如 `sync_not_ready`、`unsupported_message_type`。
- `result` 统一使用小写英文值，避免同义词分叉。
- 事件名固定使用小写蛇形，且必须与代码常量完全一致。
- 高频校时链路默认使用 `debug`：`playback_sync_ntp_request_received`、`playback_sync_ntp_response_sent`，以及 `SYNC` 恢复路径产生的 `playback_sync_scheduled_action_sent`；其他关键业务事件默认使用 `info`。
