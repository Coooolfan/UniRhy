# Android 采样级播放调度

Android 原生播放执行器的"采样级起播锚点"机制：把 Web-Android 协播的相对出声偏差压到感知阈值以下。协议侧与后台治理见 [PLAYBACK_BACKGROUND_PLAN.md](./PLAYBACK_BACKGROUND_PLAN.md)，同步协议规范见 [server/README/PLAYBACK_SYNC.md](../server/README/PLAYBACK_SYNC.md)。

## 1. 背景

Web 端经 `AudioBufferSourceNode.start(when)` 做采样级排期：渲染线程从会话起持续向输出灌样本，源节点在指定渲染块上插入，出声即命中锚点。Media3 的默认路径则是 `player.play()` 触发式起播——命令到硬件首个可听样本之间存在数百毫秒的不定成本（MediaCodec 恢复供流、AudioTrack 创建与 buffer 填充），毫秒级 Handler 调度无法对齐。

Android 侧复刻采样级的等价条件：本地音频输出在起播前就处于活跃态，并暴露"未来某个采样点上开始插入真实音频"的能力。实现方式为自定义 `AudioSink`（装饰 `DefaultAudioSink`），在 PCM 输入端以静音帧填充目标时刻之前的时间轴——因为 AudioTrack 的 `framePosition → nanoTime` 是硬件级单调映射（CDD 要求 ±1–2ms），静音排水完毕的采样点即为精确的可听起播点。

保留 Media3 生态（MediaCodec 解码链、MediaSession 系统控件、AudioFocus、路由变更、gapless 队列）不动，替换点仅为 `DefaultRenderersFactory.buildAudioSink`。

## 2. 架构

### 2.1 `ScheduledAudioSink`

`tauri-plugin-unirhy-playback` 的 `ScheduledAudioSink` 继承 `ForwardingAudioSink`，一切能力委托底层 `DefaultAudioSink`，只干预 `handleBuffer`。三种工作态：

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

### 2.2 协议接线

- `PlaybackController.handleScheduledAction(PLAY)`：先 `armScheduledStart(executeAtElapsedMs)` 再预滚，READY 后 `playAt` 立即 play——起播时刻由 sink 静音注入决定，不再依赖上层定时触发；
- `LOAD_AUDIO_SOURCE` 预滚位置：同曲目续播沿用当前位置（seek 会 flush 解码链并重新拉流，代价数百 ms，只留给真正的位置变更）；新曲目从 0 起；
- 暂停驻留（调度 PAUSE、暂停态 SEEK、快照 PAUSED、LOAD 等待）一律进入 HOLD；
- `schedulePositionCorrection` 已移除：采样级精度下不需要事后 seek 校正，也消除了校正引发的 AudioTrack 重建与可听裂声。

### 2.3 服务端提前量

`PlaybackSchedulerService` 的调度窗口下限按会话内设备端型取最大值：默认 400ms，`clientVersion` 以 `android-native` 开头的设备为 1000ms（`ANDROID_NATIVE_MIN_SCHEDULE_DELAY_MS`）。Android 起播链路在 seek 后的首帧 PCM 到达时间波动大（几十 ms 至近秒，受流重拉与设备调度影响），窗口必须覆盖最坏值，注入路径才能全轮命中。代价是含 Android 设备的同步会话起播延迟约 1s。

### 2.4 出声测量（playout-advanced）

`AudioSink.Listener.onPositionAdvancing` 报告的是首帧（含注入静音）的播出时刻；sink 加上前置静音总时长换算真实音频出声时刻（注入未完成时暂存，commit 后以最终总量补发），经 `PlaybackController` 换算到服务端时钟后作为 `playout-advanced` 事件上抛，与 `serverTimeToExecuteMs` 一次比对得出 driftMs——测量集中在服务端时钟轴，只吃一次 NTP 误差。

## 3. 验收

Xiaomi 5G（扬声器路径）PAUSE→PLAY 循环实测：

- 稳态 driftMs **±1ms**（补偿收敛后），全轮命中注入路径；
- 起播全程无 AudioTrack 销毁重建、无位置校正 seek（旧路径两者均 100% 触发且伴随可听裂声）；
- logcat 观测：`adb logcat -s UnirhyScheduledSink:I UnirhyPlayerEngine:I UnirhyPlayback:I`，`playout advanced ... driftMs=` 直读。

已知限制与边界：

- driftMs 基于 sink 位置模型自洽口径；跨端绝对声学偏差的标定需要 Web 侧同样上报出声 echo（`AudioContext.getOutputTimestamp`）后在服务端比对，该部分未实施；
- 蓝牙路由不承诺采样级（链路自带百 ms 级不定延迟）；变速播放（`PlaybackParameters(speed != 1.0)`）未适配折算，UniRhy 当前不使用变速；
- 低端设备 `AudioTrack.getTimestamp` 精度不达 CDD 标称（已知 ±10ms）时，精度按该设备下限劣化；
- 息屏/后台被系统节流时首帧 PCM 延迟膨胀，超出窗口的轮次走迟到直通兜底。

回退：`ScheduledAudioSink` 与协议解耦，未排期时行为等价于 `DefaultAudioSink`；出问题可在 `DefaultRenderersFactory` 一行改回原生 sink，无协议兼容影响。

## 参考资料

- Media3 `AudioSink` 接口源码：[androidx/media/audio/AudioSink.java](https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioSink.java)
- Media3 customization 文档：[developer.android.com/media/media3/exoplayer/customization](https://developer.android.com/media/media3/exoplayer/customization)
- Android CDD 音频延迟要求：[CDD 5.6 audio-latency](https://android.googlesource.com/platform/compatibility/cdd/+/refs/heads/master/5_multimedia/5_6_audio-latency.md)
- `AudioTrack` 采样级调度实践：[Building a Sample-Accurate Metronome with AudioTrack](https://moshenskyi.medium.com/building-a-sample-accurate-metronome-with-audiotrack-in-android-7da27ac7dae1)
