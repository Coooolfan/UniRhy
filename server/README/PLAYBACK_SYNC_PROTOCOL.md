# UniRhy Playback Sync 协议规范

本文档是阶段 0 的协议权威来源。`PLAYBACK_SYNC_PLAN.md` 保留为路线图与架构说明；所有线级字段、消息形态与错误码以本文档为准。

## 1. 基本约定

- 通道：`/ws/playback-sync`
- 鉴权：复用 Sa-Token 登录态（cookie）
- 线形态：所有 WebSocket 消息统一为

```json
{
  "type": "PLAY",
  "payload": {}
}
```

- `type` 使用大写蛇形命名。
- `payload` 字段使用 camelCase。
- 协议中的 `recordingId` 指向后端 `Recording` 实体；UI 侧可继续展示为 Track / 曲目。
- `sourceUrl` 固定为相对路径 `/api/media/{mediaFileId}`，由服务端生成，客户端不得上传。
- `positionSeconds` 表示播放偏移，单位秒，类型为 `Double`。

## 2. 公共枚举

### 2.1 消息类型

- `HELLO`
- `NTP_REQUEST`
- `PLAY`
- `PAUSE`
- `SEEK`
- `AUDIO_SOURCE_LOADED`
- `SYNC`
- `NTP_RESPONSE`
- `SNAPSHOT`
- `ROOM_EVENT_LOAD_AUDIO_SOURCE`
- `SCHEDULED_ACTION`
- `ROOM_EVENT_DEVICE_CHANGE`
- `ERROR`

### 2.2 播放状态

- `PLAYING`
- `PAUSED`

### 2.3 调度动作类型

- `PLAY`
- `PAUSE`
- `SEEK`

## 3. C2S 消息

### `HELLO`

```json
{
  "type": "HELLO",
  "payload": {
    "deviceId": "web-7c2f",
    "clientVersion": "web@0.1.0"
  }
}
```

字段：

- `deviceId: String`
- `clientVersion: String?`

### `NTP_REQUEST`

```json
{
  "type": "NTP_REQUEST",
  "payload": {
    "t0": 1730844000000,
    "clientRttMs": 18.5
  }
}
```

字段：

- `t0: Long`
- `clientRttMs: Double?`

### `PLAY`

```json
{
  "type": "PLAY",
  "payload": {
    "commandId": "cmd-play-001",
    "deviceId": "web-7c2f",
    "recordingId": 1001,
    "mediaFileId": 2001,
    "positionSeconds": 12.5
  }
}
```

### `PAUSE`

```json
{
  "type": "PAUSE",
  "payload": {
    "commandId": "cmd-pause-001",
    "deviceId": "web-7c2f",
    "recordingId": 1001,
    "mediaFileId": 2001,
    "positionSeconds": 36.25
  }
}
```

### `SEEK`

```json
{
  "type": "SEEK",
  "payload": {
    "commandId": "cmd-seek-001",
    "deviceId": "web-7c2f",
    "recordingId": 1001,
    "mediaFileId": 2001,
    "positionSeconds": 91.0
  }
}
```

控制类字段：

- `commandId: String`
- `deviceId: String`
- `recordingId: Long?`
- `mediaFileId: Long?`
- `positionSeconds: Double`

约束：

- `PLAY` 必须提供非空 `recordingId` 与 `mediaFileId`。
- `SEEK` 必须提供非空 `recordingId` 与 `mediaFileId`。
- `PAUSE` 可携带当前录音上下文；当服务端需要表达“暂停且清空当前录音”时，允许 `recordingId=null` 且 `mediaFileId=null`。

### `AUDIO_SOURCE_LOADED`

```json
{
  "type": "AUDIO_SOURCE_LOADED",
  "payload": {
    "commandId": "cmd-play-001",
    "deviceId": "web-85ab",
    "recordingId": 1001,
    "mediaFileId": 2001
  }
}
```

字段：

- `commandId: String`
- `deviceId: String`
- `recordingId: Long`
- `mediaFileId: Long`

### `SYNC`

```json
{
  "type": "SYNC",
  "payload": {
    "deviceId": "web-85ab"
  }
}
```

字段：

- `deviceId: String`

约束：

- 客户端必须在 NTP 初始化完成后再发送 `SYNC`。

## 4. S2C 消息

### `NTP_RESPONSE`

```json
{
  "type": "NTP_RESPONSE",
  "payload": {
    "t0": 1730844000000,
    "t1": 1730844000012,
    "t2": 1730844000014
  }
}
```

字段：

- `t0: Long`
- `t1: Long`
- `t2: Long`

### `SNAPSHOT`

```json
{
  "type": "SNAPSHOT",
  "payload": {
    "state": {
      "status": "PLAYING",
      "recordingId": 1001,
      "mediaFileId": 2001,
      "sourceUrl": "/api/media/2001",
      "positionSeconds": 12.5,
      "serverTimeToExecuteMs": 1730844001500,
      "version": 8,
      "updatedAtMs": 1730844000100
    },
    "serverNowMs": 1730844000200
  }
}
```

`state` 字段：

- `status: PlaybackStatus`
- `recordingId: Long?`
- `mediaFileId: Long?`
- `sourceUrl: String?`
- `positionSeconds: Double`
- `serverTimeToExecuteMs: Long`
- `version: Long`
- `updatedAtMs: Long`

约束：

- 无当前录音时，服务端使用 `status=PAUSED`、`recordingId=null`、`mediaFileId=null`、`sourceUrl=null`，并保留 `positionSeconds=0.0`。

### `ROOM_EVENT_LOAD_AUDIO_SOURCE`

```json
{
  "type": "ROOM_EVENT_LOAD_AUDIO_SOURCE",
  "payload": {
    "commandId": "cmd-play-001",
    "recordingId": 1001,
    "mediaFileId": 2001,
    "sourceUrl": "/api/media/2001"
  }
}
```

字段：

- `commandId: String`
- `recordingId: Long`
- `mediaFileId: Long`
- `sourceUrl: String`

### `SCHEDULED_ACTION`

```json
{
  "type": "SCHEDULED_ACTION",
  "payload": {
    "commandId": "cmd-play-001",
    "serverTimeToExecuteMs": 1730844001500,
    "scheduledAction": {
      "action": "PLAY",
      "status": "PLAYING",
      "recordingId": 1001,
      "mediaFileId": 2001,
      "sourceUrl": "/api/media/2001",
      "positionSeconds": 12.5,
      "version": 8
    }
  }
}
```

`scheduledAction` 字段：

- `action: ScheduledActionType`
- `status: PlaybackStatus`
- `recordingId: Long?`
- `mediaFileId: Long?`
- `sourceUrl: String?`
- `positionSeconds: Double`
- `version: Long`

约束：

- `PLAY` 必须提供非空 `recordingId`、`mediaFileId`、`sourceUrl`。
- `PAUSE` 允许 `recordingId`、`mediaFileId`、`sourceUrl` 为空。
- `SEEK` 必须提供与当前权威态一致的 `status`，客户端按 `status` 决定 seek 后是否继续播放。

### `ROOM_EVENT_DEVICE_CHANGE`

```json
{
  "type": "ROOM_EVENT_DEVICE_CHANGE",
  "payload": {
    "devices": [
      {
        "deviceId": "web-7c2f"
      },
      {
        "deviceId": "web-85ab"
      }
    ]
  }
}
```

字段：

- `devices: PlaybackSyncDevice[]`
- `PlaybackSyncDevice.deviceId: String`

### `ERROR`

```json
{
  "type": "ERROR",
  "payload": {
    "code": "RECORDING_NOT_PLAYABLE",
    "message": "Recording 1001 has no playable audio asset"
  }
}
```

字段：

- `code: PlaybackSyncErrorCode`
- `message: String`

## 5. 错误码

- `INVALID_MESSAGE`
- `UNSUPPORTED_MESSAGE`
- `RECORDING_NOT_FOUND`
- `MEDIA_FILE_NOT_FOUND`
- `RECORDING_NOT_PLAYABLE`
- `SYNC_NOT_READY`
- `INTERNAL_ERROR`

## 6. ACK 规则

- 不定义通用 ACK 消息。
- 业务确认链路固定为：
  - `NTP_REQUEST -> NTP_RESPONSE`
  - `ROOM_EVENT_LOAD_AUDIO_SOURCE -> AUDIO_SOURCE_LOADED`
