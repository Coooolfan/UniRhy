# 后台播放治理方案（Android 为主）

跟踪"同步模式下 app 置于后台后播放中断"问题的根因结论、架构决策与实施路线。协议规格见 [server/README/PLAYBACK_SYNC.md](../server/README/PLAYBACK_SYNC.md)。

## 1. 问题现象

同步模式播放中将 Android app 置于后台：

- 当前曲目可以放完（已排期的 `AudioBufferSourceNode` 由原生音频线程驱动），但下一首不会开始；
- 回到前台后，对队列做下一首/上一首等操作全部无效、无提示；
- 杀掉 app 重进后恢复正常，且播放态 `version` 比后台前增长（如 n → n+2）。

## 2. 根因（已由服务端日志证实）

### 2.1 主链条

1. app 进入后台后，Chromium WebView renderer 对隐藏页面实施深度定时器节流，2500ms 一次的 NTP 心跳停摆；
2. 服务端以 `lastNtpResponseAtMs` 做活性判定（3750ms 阈值），将设备判为 stale 并踢出（日志 `connection_closed reason=stale_connection`）；
3. 该账号最后一台设备离线 → 服务端下发 `auto-disconnect-pause`，会话置为 `PAUSED`，**`PlaybackAutoAdvanceService` 的自动切歌定时器随之取消**；
4. 服务端不再切歌，客户端本地放完当前曲即静音。

后台的客户端会因断连事件反复重连（连接与 `HELLO` 是事件驱动的，可以成功），但心跳仍被节流，形成"连接 → 数秒后再被踢"的无限循环；每轮踢出附带一次 `auto-disconnect-pause`，即 `version` 增长的来源。

### 2.2 回前台后操作无效的链条

1. 客户端本地队列 `version` 落后于服务端；
2. 下一首/上一首走 HTTP 队列接口并携带旧 `version` → 409 `VERSION_CONFLICT`；
3. 前端捕获后仅调用 `requestSyncRecovery()` 并静默返回（`web/src/stores/audio.ts` 的 `executeRemoteQueueMutation`）；
4. `SYNC` 消息发往未恢复的 WS 连接被静默丢弃（`playbackSyncClient.ts` 在 `readyState !== OPEN` 时丢弃发送），且 `awaitingSyncRecovery` 置 true 后不再重发 → 恢复流程永久卡死。

### 2.3 关键辅助事实

- 前台服务并非缺失：app 已以 `mediaPlayback` 类型启动 `tauri-plugin-background-service`（vendored 0.7.1），manifest 权限与服务类型声明正确，并已接电池优化白名单。前台服务保住了 app 进程，但 WebView renderer 是 Chromium 管理的独立进程，不受其豁免。
- 日志显示后台期间设备曾成功处理过一次 `auto-next` 的音源预加载——WS 消息驱动的 JS 事件链在后台**可能**工作（audible 豁免），但曲目结束的静音瞬间恰好使豁免失效，不可依赖。
- Android WebView 的 `navigator.mediaSession` 不与系统媒体控件打通（Chromium 未实现，见 w3c/mediasession#337），媒体控件必须原生实现。
- iOS WKWebView 的 AudioContext 在后台约 27 秒被冻结且回前台不复活（WebKit 长期问题），Web 技术路线在 iOS 上不可行。

## 3. 架构决策

D2/D3/D4 共同构成一个目标架构：**线级协议只有一份（服务端权威），客户端按平台分为两种协议执行器**。D3 定义整体格局与边界，D2 与 D4 是原生执行器在两个移动平台上的先后落地。

```
服务端同步协议（唯一规格：PLAYBACK_SYNC.md，不改动）
├── TS 执行器（Web Audio，现状代码）→ 浏览器 / macOS / Windows
└── 原生执行器（新建）
    ├── Android：Media3 + 前台服务 + MediaSession   ← D2（本期）
    └── iOS：AVAudioEngine + 后台音频模式            ← D4（后置）
```

### D1：连接活性与 NTP 校时解耦（服务端，全平台受益）

活性判定改用 WebSocket 协议层 Ping/Pong：Pong 由客户端网络栈自动回复，不依赖 JS 运行，被节流的 WebView 也能响应。NTP 只负责校时，心跳停摆不再等于设备死亡。此改动独立于其他决策，优先落地。

- **客户端零改动**：Ping/Pong 帧在浏览器/WebView 的网络进程处理，JS 不可见；Tauri 端（tauri-plugin-websocket/tungstenite）与未来的 Kotlin 客户端（OkHttp）同样由传输层自动回复。
- 服务端 Pong 超时阈值放宽至数十秒级，仅用于清理真正的死连接；死连接关闭后客户端由 `close` 事件驱动自动重连，路径不变。
- 客户端重连纯由 `close` 事件驱动，实现中不存在"NTP 超时主动重连"的看门狗（`PLAYBACK_SYNC.md` §5.5 与实现不符，落地 D1 时一并修订该文档）；半死 socket 的客户端侧感知由实施路线第 2 步"回前台自愈"覆盖。

### D2：Android 治本 = 原生播放内核

后台声音的持续产出不能依赖 WebView renderer 调度。Android 采用平台标准架构：

> **Media3/ExoPlayer 播放内核 + 前台服务（mediaPlayback）+ MediaSession**，WebView 降级为 UI 与遥控器。

- Android 上**全时原生播放**，不做"前台 WebAudio / 后台 ExoPlayer"双引擎交接（交接位置精度与状态机复杂度不可控）；
- WS 协议客户端（连接、NTP、消息处理、播放执行）随播放内核下沉到 Kotlin；
- 媒体控件（通知栏/锁屏/耳机/车机）是 MediaSession 的自带属性，不再作为独立插件立项；
- 划掉任务、Doze、厂商杀后台等场景由该架构天然覆盖。

### D3：原生化的边界——协议不变，TS 执行器保留

划定 D2/D4 的爆炸半径：原生化范围只到"客户端执行器"，往上不碰协议，往旁不碰 TS 实现。

- 线级协议一行不改，`PLAYBACK_SYNC.md` 作为跨语言实现的规格书；
- TS 执行器继续服务浏览器与桌面端（保留 `AudioContext` 采样级调度，桌面无后台生存问题，原生化无收益，见第 4 节）；
- Kotlin 为第二份协议实现，以协议文档 + 服务端契约测试控制两份实现的漂移。

### D4：iOS 复用 D2 的执行器模式

iOS 后台音频在 WKWebView 上不可行（见 2.3），出路与 Android 同构：AVAudioEngine + `UIBackgroundModes: audio` + MPRemoteCommandCenter / MPNowPlayingInfoCenter，复用"原生播放执行器 + WebView 遥控"抽象与 Kotlin 客户端沉淀的协议实现经验。单独立项，不纳入本期。

### D5：明确不投入的方向

- gapless 预排下一首以维持 audible 豁免——续命一首歌，不治本；
- WebView 保活类 hack（`onPause` 补偿、`resumeTimers` 等）；
- 薄 MediaSession 桥接插件（被 D2 取代）；
- 桌面媒体控件优先验证 WebView2 / WKWebView 的 `navigator.mediaSession` 直通（零成本），不通再考虑 Rust 侧 souvlaki。

## 4. 播放精度取舍

### 4.1 误差链

设备间听感偏差由一条误差链决定，排期原语只是其中一环：

> NTP 估计误差（毫秒级）→ 时钟映射抖动 → **起播排期误差** → 输出延迟不确定性（蓝牙下几十至几百毫秒）

"采样级"指排期精度达到单个采样点（44.1kHz 下约 0.023ms），由音频渲染线程按帧定位实现，不经过 OS 线程调度。采样级排期不等于设备间采样级同步——实际听感偏差由误差链中更粗的环节主导。

### 4.2 各平台能力

| | 排期精度 | 输出延迟可知性 | 后台生存 | 结论 |
|---|---|---|---|---|
| Android WebView | 采样级（`source.start`） | 差（`outputLatency` 不可靠） | 不可行 | 弃用 |
| Android 原生 (Media3) | ±5~20ms + 校正环 | 好（`AudioTrack.getTimestamp()`） | 好 | D2 采用 |
| iOS WebView | 采样级 | — | 不可行 | 弃用 |
| iOS 原生 (AVAudioEngine) | 采样级（`scheduleBuffer(at: hostTime)`） | 好（`AVAudioSession.outputLatency`） | 好 | D4 采用 |
| macOS WKWebView | 采样级 | 中 | 无问题 | 保持现状 |
| Windows WebView2 | 采样级 | 中 | 无问题 | 保持现状 |

- 移动端原生化是为生存换精度：Android 走 Media3 时起播排期从采样级降至毫秒级，但换来更稳的时钟链路（原生单调时钟、校时不受 JS 节流影响）与首次可用的输出延迟闭环测量，蓝牙场景综合表现可能优于现状；iOS 原生排期原语（`atHostTime` 系 API）与 Web Audio 同级，无精度损失。
- 桌面端既有生存又有精度，原生化无收益：macOS 原生只能打平，Windows 原生（WASAPI）无高层定时起播原语反而更差。

### 4.3 Android 实现策略

- D2 采用 Media3/ExoPlayer 默认音频管线：预滚暂停在目标位置 → 定时起播 → `AudioTrack.getTimestamp()` 实测偏差 → `playbackParameters` 速度微调收敛。综合精度几十毫秒级，满足协议"听感一致"目标。
- **AudioTrack/Oboe 自研不立项**：其收益（毫秒级 → 采样级排期）被误差链中的 NTP 与输出延迟环节淹没，而成本等于自研完整音频引擎（解码管线、重采样、路由重建、焦点、underrun、gapless）。
- 记录在案的升级路径：Media3 支持仅替换 `AudioSink`（保留解码与 MediaSession 生态，只换输出层做精确控帧）。触发条件：实测同步误差超出感知阈值，且出现"同室多设备裸放合奏"级产品场景。

## 5. 实施路线

1. **服务端 Ping/Pong 活性检测**（D1，已实施）：服务端每 15s 发协议层 Ping，Pong 超时 60s 判死，替换基于 `lastNtpResponseAtMs` 的 stale 判定；`lastNtpResponseAtMs` 降级为校时诊断字段；
2. **回前台自愈修复**（已实施）：
   - `visibilitychange` 回前台时校验连接健康：socket 已死立即重建；声称打开则发 NTP 探测，5s 无响应强制重连（`PlaybackSyncClient.verifyConnectionHealth`）；
   - 修复 `awaitingSyncRecovery` 悬空卡死：`SYNC` 发送失败不置位，连接掉出 ready 时复位；
   - 409 `VERSION_CONFLICT` 恢复改为先走 HTTP 拉取队列快照，不依赖 WS 存活，播放态仍经 `SYNC` 恢复；
3. **Android 原生播放内核**（D2，已实施）：
   - 服务端队列项下发 `mediaFileId`（首个音频 asset），原生端以 `GET /api/media-files/{id}` + `unirhy-token` 头流式播放；PLAYBACK_SYNC.md 修订至与实现一致，作为 Kotlin 第二实现的规格依据；
   - 新增 vendored 插件 `web/src-tauri/vendor/tauri-plugin-unirhy-playback/`：Kotlin 侧含 OkHttp WS 协议客户端（五态状态机、NTP 单调钟校时、重连梯度复刻 TS 实现）、ExoPlayer 播放执行器（预滚 + 定时起播 + 迟到补偿 + 起播后一次性位置校正）、Media3 MediaSessionService（mediaPlayback 前台服务与系统媒体控件）；桌面注册占位实现保证全平台编译；
   - 系统控件回流：play/pause/seek 直发 WS 控制命令，next/prev 由 Kotlin 直调队列导航 HTTP 端点（不经 WebView，后台节流下依然可靠），409 冲突走 SYNC 恢复；
   - TS 层（`audioNativeSession.ts`）：原生为播放权威，事件（带单调 seq）驱动 store，回前台 `getPlaybackState` 全量对齐；Android 上 Web Audio 引擎与 TS 同步客户端保持休眠、零 AudioContext；队列 mutation 仍由 TS 走 HTTP，经服务端广播回原生闭环；
   - 独立模式同样走原生播放器：TS 全量下发本地队列，自然结束续播由原生按队列顺序驱动；
   - 旧 vendored `tauri-plugin-background-service` 及其空壳前台服务、`useAndroidPlaybackService` 已删除，电池优化插件保留；
4. **iOS 原生播放层立项**（D4，后置）。

## 6. 顺带发现的次要问题

- 单设备场景下 `PLAY` 的发起者被视为已加载、pending play 立即完成，客户端稍后的 `AUDIO_SOURCE_LOADED` 被拒并记录 `pending_play_not_found`，产生日志噪音；
- 队列操作 409 后 UI 无任何反馈（静默失败）。
