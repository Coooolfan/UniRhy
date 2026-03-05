# UniRhy 同账号多设备听感同步方案

参考 <https://github.com/freeman-jiang/beatsync>

## 1. 目标与边界

### 1.1 目标

为 UniRhy 增加”同一已登录用户在不同设备之间的播放听感同步”，覆盖以下动作：

- 播放（Play）
- 暂停（Pause）
- 跳转（Seek）
- 切换曲目（Change Track）：语义等同于”发一个带新 trackId 的 PLAY”，不设独立消息类型

### 1.2 边界

- 仅同步同一 `accountId` 的设备，不做多用户房间同步
- 仅支持单实例部署，不引入 Redis/MQ/跨节点总线
- 目标是“听感一致”，不追求采样级锁相

---

## 2. 从 Beatsync 提炼的实现模式

Beatsync 的核心经验不是“单一大状态对象”，而是分层状态管理：

- 稳定权威态：当前播放锚点、曲目、状态
- 执行中临时态：等待设备加载、超时、待调度动作
- 设备运行态：每设备 RTT/心跳活跃度
- 客户端执行态：本地 clock offset、waitTime、音频硬件时钟

UniRhy 采用同样分层，避免把所有语义塞进一个 `PlaybackSessionState`，降低竞态和状态污染。

---

## 3. 总体架构

### 3.1 服务端职责

- 权威维护账号播放会话（内存态）
- 维护同账号设备连接集合并广播
- 基于 NTP 风格测量计算调度窗口
- 对播放动作做“未来时刻统一执行”调度

### 3.2 客户端职责

- 周期性发送 NTP 请求，估算 `clockOffset`
- 收到调度指令后按 `executeAtServerMs` 计算本地等待时间
- 使用 `AudioContext` + `AudioBuffer` 执行精确调度播放（`source.start(when, offset)`），并上报加载完成状态
- 使用 `performance.timeOrigin + performance.now()` 作为高精度时间源

### 3.3 通道

- WebSocket：`/ws/playback-sync`
- 鉴权：复用 Sa-Token 登录态（cookie）

---

## 4. 状态模型（核心）

### 4.1 权威稳定态（每账号一份）

```kotlin
enum class PlaybackStatus { PLAYING, PAUSED }

data class AccountPlaybackState(
    val accountId: Long,
    val status: PlaybackStatus,
    val trackId: Long?,
    val mediaFileId: Long?,
    val sourceUrl: String?,
    val trackPositionSeconds: Double,
    val serverTimeToExecuteMs: Long,
    val version: Long,
    val updatedAtMs: Long,
)
```

说明：
- `trackPositionSeconds + serverTimeToExecuteMs` 是播放锚点
- `version` 单调递增，由服务端在每次更新 `AccountPlaybackState` 时自增，客户端仅接受更高版本
- `sourceUrl` 由服务端根据 `mediaFileId` 解析生成（可包含签名/CDN 路径），客户端不传 sourceUrl
- 无播放状态用 `PAUSED + trackId=null` 表示，不设 STOPPED 状态

### 4.2 执行中临时态（每账号可空）

```kotlin
data class PendingPlayState(
    val commandId: String,
    val initiatorDeviceId: String,
    val trackId: Long,
    val mediaFileId: Long,
    val sourceUrl: String,
    val clientsLoaded: MutableSet<String>,
    val createdAtMs: Long,
    val timeoutAtMs: Long,
)
```

说明：
- 对应 Beatsync 的 `pendingPlay`
- 只在”切歌播放需预加载”阶段存在
- `sourceUrl` 由服务端在创建 `PendingPlayState` 时根据 `mediaFileId` 解析填入
- 当 `executeScheduledPlay()` 执行时，将 pending 中的 `trackId`/`mediaFileId`/`sourceUrl` 写入 `AccountPlaybackState`
- 收到新 PLAY 时，先 `clearPendingPlay()`（取消超时定时器）再创建新的，即”后到覆盖”语义（与 Beatsync 一致）
- `clientsLoaded` 初始化时自动包含 `initiatorDeviceId`（发起者视为已加载，与 Beatsync 一致）

### 4.3 设备运行态（每账号下每设备一份）

```kotlin
data class DeviceRuntimeState(
    val deviceId: String,
    val accountId: Long,
    var rttEmaMs: Double,
    var lastNtpResponseAtMs: Long,
    var lastSeenAtMs: Long,
)
```

说明：
- `rttEmaMs` 用于计算账号级调度窗口
- `lastNtpResponseAtMs` 用于活跃性判断与断链清理，超过 `3750ms`（即 `1.5 * STEADY_STATE_INTERVAL_MS`）未响应判定为 stale

### 4.4 连接注册态

- `accountId -> MutableSet<WebSocketSession>`
- `sessionId -> deviceId/accountId`

用于广播、单播与连接回收。

### 4.5 客户端本地执行态（前端）

- `clockOffsetMs`
- `roundTripEstimateMs`
- `ntpMeasurements[]`
- `isSynced`（NTP 初始化是否完成）
- `audioContext`（Web Audio API 上下文）
- `currentBuffer`（当前曲目的 AudioBuffer）
- `sourceNode`（当前播放的 AudioBufferSourceNode）
- `gainNode`（音量控制节点）
- `playbackStartContextTime`（`audioContext.currentTime` 锚点）
- `playbackOffsetSec`（播放起始偏移）

说明：
- 服务端不保存客户端 `clockOffset`
- 客户端自行把”本地时间映射到服务端时间”
- 使用 `performance.timeOrigin + performance.now()` 获取高精度时间戳（微秒级），远优于 `Date.now()`
- `currentTime` 由 `playbackOffsetSec + (audioContext.currentTime - playbackStartContextTime)` 计算得出

---

## 5. 协议设计（MVP）

### 5.1 C2S

- `HELLO { deviceId, clientVersion }`
- `NTP_REQUEST { t0, clientRttMs? }`
- `PLAY { commandId, deviceId, trackId, mediaFileId, trackTimeSeconds }`
- `PAUSE { commandId, deviceId, trackTimeSeconds }`
- `SEEK { commandId, deviceId, trackTimeSeconds }`
- `AUDIO_SOURCE_LOADED { commandId, deviceId, trackId }`
- `SYNC { deviceId }`

说明：
- `PLAY` 不携带 `sourceUrl`，由服务端根据 `mediaFileId` 解析
- 切歌 = 发一个带新 `trackId` 的 `PLAY`，不设独立 CHANGE_TRACK 消息
- 无独立 STOP 消息；暂停并清空曲目由 `PAUSE + trackId=null` 语义覆盖
- `SYNC` 的前置条件：客户端必须先完成 NTP 初始化校时（`isSynced=true`）后才可发送

### 5.2 S2C

- `NTP_RESPONSE { t0, t1, t2 }`
- `SNAPSHOT { state, serverNowMs }`
- `ROOM_EVENT:LOAD_AUDIO_SOURCE { commandId, trackId, sourceUrl }`
- `SCHEDULED_ACTION { commandId, serverTimeToExecuteMs, scheduledAction }`
- `ROOM_EVENT:DEVICE_CHANGE { devices }`（可选）
- `ERROR { code, message }`

说明：
- `SNAPSHOT` 在以下时机发送：①HELLO 响应；②断线重连后服务端自动下发（无需客户端主动 SYNC）
- `LOAD_AUDIO_SOURCE` 中的 `sourceUrl` 由服务端解析生成

### 5.3 ACK 策略

- 不设计“通用 ACK 消息类型”（与 Beatsync 一致）
- 使用“业务型确认”代替：
  - `NTP_REQUEST -> NTP_RESPONSE`
  - `LOAD_AUDIO_SOURCE -> AUDIO_SOURCE_LOADED`

---

## 6. 核心时序

### 6.1 冷启动与校时

1. 设备连接 WS，发送 `HELLO`
2. 服务端返回 `SNAPSHOT`（包含当前 `AccountPlaybackState`）
3. 客户端快速发送 NTP 采样（20~40 次，每 30ms 一次，约 1.2 秒完成）
4. 达到采样数后标记 `isSynced=true`，进入稳态校时（每 2500ms 一次，兼作心跳）

### 6.2 PLAY（切歌场景）

1. 设备 A 发 `PLAY { trackId, mediaFileId, trackTimeSeconds }`
2. 服务端根据 `mediaFileId` 解析 `sourceUrl`
3. 若已有 `PendingPlayState`，先 `clearPendingPlay()`（取消旧超时），再创建新的（后到覆盖语义）
4. 创建 `PendingPlayState`，`clientsLoaded` 初始化为 `Set([发起者deviceId])`
5. 广播 `LOAD_AUDIO_SOURCE { commandId, trackId, sourceUrl }`
6. 各设备收到后执行 `fetch → decodeAudioData`，完成后发 `AUDIO_SOURCE_LOADED`
7. 全部加载完成或 3 秒超时后，服务端执行 `executeScheduledPlay()`：
   - 将 pending 中的 `trackId`/`mediaFileId`/`sourceUrl` 写入 `AccountPlaybackState`
   - 计算 `serverTimeToExecuteMs`
   - 广播 `SCHEDULED_ACTION(PLAY)`
8. 客户端使用 `AudioBufferSourceNode.start(when, offset)` 精确调度：
   - `when = audioContext.currentTime + (serverTimeToExecuteMs - estimatedServerNowMs) / 1000`
   - `offset = trackTimeSeconds`

### 6.3 PAUSE / SEEK

1. 发控制命令
2. 服务端更新 `AccountPlaybackState`（version 自增）
3. 计算未来执行时刻并广播 `SCHEDULED_ACTION`
4. 各设备同一时刻执行

说明：PAUSE 和 SEEK 是幂等操作（重复执行效果相同），无需额外去重机制。

### 6.4 晚加入设备 SYNC

1. 新设备完成 NTP 校时（`isSynced=true`）后发送 `SYNC`
2. 服务端根据当前 `AccountPlaybackState` 计算恢复位置
3. 单播 `SCHEDULED_ACTION(PLAY)`，带额外缓冲（例如 `+1500ms`，用于音频 fetch + decode）

说明：断线重连的设备无需手动 SYNC，服务端在 HELLO 阶段自动下发 SNAPSHOT，客户端完成 NTP 校时后自行恢复。

### 6.5 心跳与掉线

- 客户端：NTP 请求超过 `3750ms` 未收到响应，判定连接 stale，主动重连
- 服务端：按 `lastNtpResponseAtMs` 检查，超过 `3750ms`（`1.5 * 2500ms`）清理 stale 设备
- 设备断开后若存在 `PendingPlayState`，从 `clientsLoaded` 移除该设备，若剩余设备全部就绪则立即触发 `executeScheduledPlay()`

---

## 7. 时间与调度策略

### 7.1 NTP 公式

- `offset = ((t1 - t0) + (t2 - t3)) / 2`
- `rtt = (t3 - t0) - (t2 - t1)`

### 7.2 过滤与平滑

- 客户端：按 RTT 升序取最优半数计算 offset
- 服务端：对 `clientRttMs` 做 EMA（建议 `alpha=0.2`）

### 7.3 调度窗口

- `scheduleDelayMs = clamp(maxRttMs * 1.5 + 200, 400, 3000)`
- `executeAtServerMs = serverNowMs + scheduleDelayMs`

说明：
- `400ms` 为最小安全窗口，防止广播/调度过紧导致掉队
- `3000ms` 为上限，避免弱网下交互延迟过大

---

## 8. 幂等与一致性

### 8.1 对齐 Beatsync 风格

- `clientsLoaded` 使用 `Set<deviceId>` 去重
- 收到新 PLAY 时覆盖已有 `PendingPlayState`（后到覆盖），不做"同 track 去重"
- 客户端仅应用更高 `version` 的状态
- PAUSE / SEEK 天然幂等（重复执行效果相同）

### 8.2 commandId 去重（增强，建议后续迭代）

- 所有控制命令携带 `commandId`
- 服务端维护短 TTL 去重表（例如 30 秒）
- 重复 `commandId` 直接丢弃或返回最近结果

---

## 9. 服务端改造清单（UniRhy）

建议新增包：`server/src/main/kotlin/com/coooolfan/unirhy/sync`

- `config/PlaybackSyncWebSocketConfig.kt`
- `ws/PlaybackSyncWebSocketHandler.kt`
- `service/PlaybackSessionService.kt`
- `service/PlaybackSchedulerService.kt`
- `service/DeviceRuntimeService.kt`
- `model/AccountPlaybackState.kt`
- `model/PendingPlayState.kt`
- `model/DeviceRuntimeState.kt`
- `protocol/PlaybackSyncMessage.kt`

并复用现有：
- Sa-Token 登录态解析
- 录音/媒体查询能力（校验 track 与 source）

---

## 10. 前端改造清单（UniRhy Web）

### 10.1 音频引擎迁移（HTMLAudioElement → AudioContext + AudioBuffer）

改动收敛在两个文件，上层调用方（Dashboard、专辑详情、歌单等视图）零改动：

- 改造 `web/src/stores/audio.ts`：
  - 新增状态：`audioContext`、`currentBuffer`、`sourceNode`、`gainNode`
  - `play(track)` → `fetch(src) → decodeAudioData → 缓存 buffer`
  - `resume()` → `sourceNode.start(0, offset)`
  - `pause()` → `sourceNode.stop()` + 记录当前偏移
  - `seek(time)` → 重建 sourceNode + `start(0, time)`
  - `setVolume()` → `gainNode.gain.value = vol`
  - `currentTime` 由 `requestAnimationFrame` 轮询计算
- 改造 `web/src/components/AudioPlayer.vue`：
  - 移除 `<audio>` 标签及全部事件监听
  - 保留全部 UI（进度条、音量、封面动画等原封不动）

内存策略：仅缓存当前播放曲目的 AudioBuffer，切歌时释放前一个。

### 10.2 同步能力接入

- 新增 `web/src/services/playbackSyncClient.ts`
- 新增或扩展 `web/src/stores/audio.ts`：
  - `clockOffsetMs`、`roundTripEstimateMs`、`isSynced`
  - `applyScheduledAction()`：使用 `sourceNode.start(when, offset)` 精确调度
  - `emitControlCommand()`
  - `suppressEcho`
- 改造 `web/src/components/AudioPlayer.vue`：
  - 本地播放控制改为”发送控制命令 + 等待调度执行”
  - 处理 `LOAD_AUDIO_SOURCE`：`fetch → decodeAudioData → 发送 AUDIO_SOURCE_LOADED`
  - 处理 `SCHEDULED_ACTION`：计算 `audioContext.currentTime` 上的精确启动时刻

---

## 11. 验收指标

- 主观：双设备同账号播放同一音频，无可感知回声
- 客观：常见网络条件下，设备间执行偏差 `P95 <= 80ms`
- 稳定：连续 100 次 `play/pause/seek` 无状态分叉
- 自愈：断线重连后 3 秒内恢复，并在下一次调度动作重新对齐

---

## 12. 交付计划（单人估算）

### 阶段 0（0.5 周）

- 协议冻结与日志字段定义

### 阶段 0.5（0.5 周）

- 前端音频引擎迁移：HTMLAudioElement → AudioContext + AudioBuffer
- 改动 `audio.ts` + `AudioPlayer.vue`，上层视图零改动
- 验收：现有单设备播放功能不受影响

### 阶段 1（1 周）

- WS 鉴权、连接注册、会话状态骨架

### 阶段 2（1~1.5 周）

- NTP 校时链路、动态调度窗口、SCHEDULED_ACTION 执行

### 阶段 3（1 周）

- 前端播放器接入、双设备联调

### 阶段 4（0.5~1 周）

- 稳定性测试、弱网测试、指标采集

总计：**4.5~5.5 周**

---

## 13. 非目标

- 多用户共享听歌房
- 多实例跨节点同步
- 协同播放列表编辑
- 设备优先级抢占机制
