# 播放执行器架构

多设备听感同步的客户端播放架构：协议只有一份（服务端权威），客户端按平台分为两种协议执行器。线级协议规格见 [server/README/PLAYBACK_SYNC.md](../server/README/PLAYBACK_SYNC.md)。

```
服务端同步协议（唯一规格：PLAYBACK_SYNC.md）
├── TS 执行器（Web Audio）→ 浏览器 / macOS / Windows
└── 原生执行器
    ├── Android：Media3 + ScheduledAudioSink + 前台服务 + MediaSession（已落地）
    └── iOS：AVAudioEngine + 后台音频模式（后置立项）
```

## 1. 平台约束

移动端不能依赖 WebView renderer 产出后台声音：

- **Android WebView**：app 进入后台后 Chromium 对隐藏页面实施深度定时器节流，JS 驱动的心跳与切歌停摆；前台服务只保住 app 进程，WebView renderer 是 Chromium 管理的独立进程，不受其豁免。audible 状态的豁免在曲目结束的静音瞬间恰好失效，不可依赖。`navigator.mediaSession` 不与系统媒体控件打通（Chromium 未实现，w3c/mediasession#337），媒体控件必须原生实现。
- **iOS WKWebView**：AudioContext 在后台约 27 秒被冻结且回前台不复活（WebKit 长期问题），Web 技术路线不可行。
- **桌面端**：既有后台生存又有采样级排期（`AudioBufferSourceNode.start(when)`），原生化无收益，保持 TS 执行器。

各平台能力：

| | 排期精度 | 输出延迟可知性 | 后台生存 | 结论 |
|---|---|---|---|---|
| Android WebView | 采样级（`source.start`） | 差 | 不可行 | 弃用 |
| Android 原生（Media3 + ScheduledAudioSink） | 采样级（AudioTrack 帧钟） | 好（`getCurrentPositionUs`/`getTimestamp`） | 好 | 采用 |
| iOS WebView | 采样级 | — | 不可行 | 弃用 |
| iOS 原生（AVAudioEngine） | 采样级（`scheduleBuffer(at: hostTime)`） | 好 | 好 | 后置采用 |
| macOS WKWebView / Windows WebView2 | 采样级 | 中 | 无问题 | 保持现状 |

实际听感偏差由误差链决定：NTP 估计误差 → 时钟映射抖动 → 起播排期误差 → 输出延迟不确定性（蓝牙下几十至几百毫秒）。排期做到采样级后，前后两个环节成为主导项。

## 2. 服务端侧配套

- **连接活性与 NTP 校时解耦**：活性判定用 WebSocket 协议层 Ping/Pong（Pong 由客户端网络栈自动回复，不依赖 JS 运行，被节流的 WebView 与 Kotlin/OkHttp 客户端同样适用），Pong 超时 60s 判死；NTP 只负责校时。
- **按端型确定调度窗口下限**：默认 400ms；HELLO `clientVersion` 以 `android-native` 开头的设备为 1000ms——Android 起播链路在 seek 后的首帧 PCM 到达时间波动大（几十 ms 至近秒，受流重拉与设备调度影响），窗口必须覆盖最坏值，采样级注入路径才能全轮命中。代价是含 Android 设备的同步会话起播延迟约 1s。

## 3. Android 原生执行器

vendored 插件 `web/src-tauri/vendor/tauri-plugin-unirhy-playback/`，Android 上全时原生播放，WebView 降级为 UI 与遥控器：

- Kotlin 侧：OkHttp WS 协议客户端（五态状态机、NTP 单调钟校时、重连梯度与 TS 实现字段级一致）、ExoPlayer 播放执行器、Media3 MediaSessionService（mediaPlayback 前台服务与系统媒体控件）；桌面注册占位实现保证全平台编译；
- 系统控件回流：play/pause/seek 直发 WS 控制命令，next/prev 由 Kotlin 直调队列导航 HTTP 端点（不经 WebView，后台节流下依然可靠），409 冲突走 SYNC 恢复；
- TS 层（`audioNativeSession.ts`）：原生为播放权威，事件（带单调 seq）驱动 store，回前台 `getPlaybackState` 全量对齐；Android 上 Web Audio 引擎与 TS 同步客户端保持休眠、零 AudioContext；队列 mutation 仍由 TS 走 HTTP，经服务端广播回原生闭环；
- 独立模式同样走原生播放器：TS 全量下发本地队列，自然结束续播由原生按队列顺序驱动。

媒体流经 `GET /api/media-files/{id}` + `unirhy-token` 头流式播放，队列项由服务端下发 `mediaFileId`。

### 3.1 采样级起播：`ScheduledAudioSink`

Media3 默认路径是 `player.play()` 触发式起播，命令到硬件首个可听样本之间存在数百毫秒的不定成本（MediaCodec 恢复供流、AudioTrack 创建与 buffer 填充），毫秒级 Handler 调度无法对齐。复刻 Web 采样级的等价条件是：本地音频输出在起播前就处于活跃态，并具备"未来某个采样点上开始插入真实音频"的能力。

实现为装饰 `DefaultAudioSink` 的 `ScheduledAudioSink`（替换点仅 `DefaultRenderersFactory.buildAudioSink`，解码链、MediaSession、AudioFocus、路由等生态不动）：在 PCM 输入端以静音帧填充目标时刻之前的时间轴——AudioTrack 的 `framePosition → nanoTime` 是硬件级单调映射（CDD 要求 ±1–2ms），静音排水完毕的采样点即为精确的可听起播点。

三种工作态：

- **直通**：未排期时透传，行为等价于原生 sink；
- **扣留（HOLD）**：暂停驻留期拒收真实 PCM，只预灌少量静音保活 AudioTrack，使 renderer 停在第一批真实数据上——保证后续 PLAY 的注入窗口就是完整的调度提前量；
- **注入**：`scheduleStartAt(executeAtElapsedMs)` 排期后，真实 PCM 被扣留，静音按目标单调钟时刻折算后先行写入，随后无缝切入真实 PCM。

注入分两段：

1. **大块**——按写入时刻模型（目标剩余时长 − 延迟补偿）折算，刻意留尾 60ms；
2. **精修**——等播放头运转、帧钟可读后，按「已写帧 − 已播帧 = 队列深度」精确补齐尾段。AudioTrack 要求 buffer 填到 start threshold（约 `getAudioTrackBufferSizeUs`）才开始出声，等待期间持续补灌静音踢过阈值，多灌的量由帧钟折算自动扣回；播放头始终未运转时超过宽限回退时钟模型。

延迟补偿（EMA）按帧钟轮的「留尾预算 − 实际精修量」误差收敛，职责是让大块欠写出精修空间；帧钟不可用时兼作写入时刻模型的固定成本估计，由实测出声偏差反馈。

关键约束（实现内注释均有对应）：

- **AudioSink 契约**：部分消费的 buffer 必须原样重送。静音写入统一走单块纪律；直通被反压后底层挂着外部 buffer 期间禁止写静音，flush 清场后恢复。
- **presentationTimeUs**：静音与真实 PCM 共用同一时间戳，底层 sink 检测到时间轴不连续后自行重锚（非致命），位置估算在真实音频起播后自然对齐。
- **flush 保留排期**：PLAY 落地顺序是先排期再预滚 seek（暂停预滚期间 renderer 已在向 sink 灌真实 PCM，事后排期无从插队），seek 触发的 flush 不得撤销排期；主动撤销走 `cancelScheduledStart` / `reset`。
- **HOLD 过渡**：pauseAt 在 pause() 落地前排扣留，此窗口内的在途真实 PCM 直通但不解除扣留。
- **非 PCM 输入 / 迟到目标 / 排期悬空**：退化为直通，由上层 postDelayed 兜底。offload/tunneling 不启用（无 PCM 通路则无注入点）。

### 3.2 协议接线

- `PlaybackController.handleScheduledAction(PLAY)`：先 `armScheduledStart(executeAtElapsedMs)` 再预滚，READY 后 `playAt` 立即 play——起播时刻由 sink 静音注入决定，不依赖上层定时触发；
- `LOAD_AUDIO_SOURCE` 预滚位置：同曲目续播沿用当前位置（seek 会 flush 解码链并重新拉流，代价数百 ms，只留给真正的位置变更）；新曲目从 0 起；
- 暂停驻留（调度 PAUSE、暂停态 SEEK、快照 PAUSED、LOAD 等待）一律进入 HOLD；
- 采样级精度下不做起播后位置校正 seek（校正本身会造成 AudioTrack 重建与可听裂声）。

### 3.3 出声测量（playout-advanced）

`AudioSink.Listener.onPositionAdvancing` 报告的是首帧（含注入静音）的播出时刻；sink 加上前置静音总时长换算真实音频出声时刻（注入未完成时暂存，commit 后以最终总量补发），经 `PlaybackController` 换算到服务端时钟后作为 `playout-advanced` 事件上抛，与 `serverTimeToExecuteMs` 一次比对得出 driftMs——测量集中在服务端时钟轴，只吃一次 NTP 误差。

### 3.4 验收

Xiaomi 5G（扬声器路径）PAUSE→PLAY 循环实测：

- 稳态 driftMs **±1ms**（补偿收敛后），全轮命中注入路径；
- 起播全程无 AudioTrack 销毁重建、无位置校正 seek；
- logcat 观测：`adb logcat -s UnirhyScheduledSink:I UnirhyPlayerEngine:I UnirhyPlayback:I`，`playout advanced ... driftMs=` 直读。

已知限制与边界：

- driftMs 基于 sink 位置模型自洽口径；跨端绝对声学偏差的标定需要 Web 侧同样上报出声 echo（`AudioContext.getOutputTimestamp`）后在服务端比对，该部分未实施；
- 蓝牙路由不承诺采样级（链路自带百 ms 级不定延迟）；变速播放（`PlaybackParameters(speed != 1.0)`）未适配折算，UniRhy 当前不使用变速；
- 低端设备 `AudioTrack.getTimestamp` 精度不达 CDD 标称（已知 ±10ms）时，精度按该设备下限劣化；
- 息屏/后台被系统节流时首帧 PCM 延迟膨胀，超出窗口的轮次走迟到直通兜底。

回退：`ScheduledAudioSink` 与协议解耦，未排期时行为等价于 `DefaultAudioSink`；出问题可在 `DefaultRenderersFactory` 一行改回原生 sink，无协议兼容影响。

## 4. iOS（后置立项）

出路与 Android 同构：AVAudioEngine + `UIBackgroundModes: audio` + MPRemoteCommandCenter / MPNowPlayingInfoCenter，复用"原生播放执行器 + WebView 遥控"抽象与 Kotlin 沉淀的协议实现经验。`scheduleBuffer(at: hostTime)` 天然是采样级排期，无需静音注入层。

## 5. 明确不投入的方向

- gapless 预排下一首以维持 audible 豁免——续命一首歌，不治本；
- WebView 保活类 hack（`onPause` 补偿、`resumeTimers` 等）；
- 薄 MediaSession 桥接插件（被原生播放内核取代）；
- AudioTrack/Oboe 完全自研（绕过 ExoPlayer）：相对 `ScheduledAudioSink` 只多 <5ms 精度上界，成本等于自研完整音频引擎（解码管线、重采样、路由重建、焦点、underrun、gapless）；
- 桌面媒体控件优先验证 WebView2 / WKWebView 的 `navigator.mediaSession` 直通（零成本），不通再考虑 Rust 侧 souvlaki。

## 参考资料

- Media3 `AudioSink` 接口源码：[androidx/media/audio/AudioSink.java](https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioSink.java)
- Media3 customization 文档：[developer.android.com/media/media3/exoplayer/customization](https://developer.android.com/media/media3/exoplayer/customization)
- Android CDD 音频延迟要求：[CDD 5.6 audio-latency](https://android.googlesource.com/platform/compatibility/cdd/+/refs/heads/master/5_multimedia/5_6_audio-latency.md)
- `AudioTrack` 采样级调度实践：[Building a Sample-Accurate Metronome with AudioTrack](https://moshenskyi.medium.com/building-a-sample-accurate-metronome-with-audiotrack-in-android-7da27ac7dae1)
- iOS WKWebView 后台 AudioContext 冻结：WebKit 长期问题；`navigator.mediaSession` WebView 直通缺失：[w3c/mediasession#337](https://github.com/w3c/mediasession/issues/337)
