# UniRhy Playback Sync 日志规范

本文档是阶段 0 的日志权威来源。当前阶段继续使用 SLF4J 文本日志，不引入 MDC 或 JSON 结构日志。

## 1. 日志格式

- 基本格式：`event=<name> key=value key=value ...`
- 所有播放同步相关日志都必须先输出 `event=<name>`。
- 字段名只允许复用 `com.coooolfan.unirhy.sync.log` 中定义的常量，禁止手写分叉字段。

示例：

```text
event=playback_sync_play_request accountId=42 deviceId=web-7c2f commandId=cmd-play-001 recordingId=1001 mediaFileId=2001 positionSeconds=12.5 result=accepted
```

## 2. 公共字段

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

## 3. 事件名

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

## 4. 使用规则

- 字段缺失时可以省略对应 `key=value`，但不能改名。
- `reason` 用于描述拒绝原因或分支原因，例如 `sync_not_ready`、`unsupported_message_type`。
- `result` 建议统一使用小写英文值，避免同义词分叉。
- 事件名固定使用小写蛇形，且必须与代码常量完全一致。
- 高频校时链路默认使用 `debug`：`playback_sync_ntp_request_received`、`playback_sync_ntp_response_sent`，以及 `SYNC` 恢复路径产生的 `playback_sync_scheduled_action_sent`。
- 其他关键业务事件默认使用 `info`；事件名与字段契约保持不变。
