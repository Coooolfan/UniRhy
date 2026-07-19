# Android 采样级播放调度方案

评估将 Android 原生播放执行器升级到"采样级起播锚点"的技术路径。协议侧与后台治理见 [PLAYBACK_BACKGROUND_PLAN.md](./PLAYBACK_BACKGROUND_PLAN.md)。

## 1. 背景

### 1.1 实测偏差

同步模式下同账号两端协播，Web-Web 精准，Web-Android 出声显著偏晚。设备实测（Xiaomi 5G，有线/扬声器路径，2026-07-18 22:29 会话）关键时间线：

```
22:29:58.961  scheduled action=PLAY delayMs=344 position=0.0
22:29:58.964  state=2 (BUFFERING)     ← ① 同位置 seek 触发瞬时 BUFFERING（5ms 恢复）
22:29:58.964  playAt in=341ms pos=0ms
22:29:58.969  state=3 (READY)
22:29:59.305  playWhenReady=true      ← player.play() 精确命中锚点
22:29:59.309  AudioTrack: start(3302) ← +4ms 内 AudioTrack 起始
22:29:59.806  position correction driftMs=-236 pos=265ms  ← ② 起播 +500ms 时实际位置只有 265ms
22:29:59.808  AudioTrack teardown + recreate            ← ③ 校正 seek 导致 AudioTrack 销毁重建
```

结论：

- **主体延迟 ~235ms** 来自 AudioTrack primer + 输出本底，`player.play()` 到硬件首个可听样本之间的固定成本。
- `PlayerEngine.schedulePositionCorrection`（起播 +500ms 检测 driftMs>150 时 `seekTo(expected+drift/2)`）本身正在**制造** AudioTrack teardown/rebuild，起播 500ms 后一次可听裂声。
- Web 的 `AudioBufferSourceNode.start(when)` 是采样级排期，样本已排在渲染图上，出声即命中锚点。

### 1.2 Web 采样级为什么可行

Web Audio 的"采样级"由三条组成：

1. `AudioContext` 渲染线程持续以固定块长（通常 128 帧）向输出灌样本，从起会话开始就没停过；
2. `AudioBufferSourceNode.start(when)` 语义是"在 `when` 对应的渲染块上插入源节点"，不是启动任何东西；
3. 整段 PCM 已经在内存（`decodeAudioData` 产物 `AudioBuffer`），source 到 destination 无中间缓冲。

三条中缺一都不是"采样级"。Android 侧要复刻，等价于让本地音频输出**从 LOAD 阶段起就没停过**，并暴露一个"未来某个采样点上开始插入真实音频"的 API。

## 2. 目的

在保留 Media3 生态（MediaCodec 解码链、MediaSession 系统控件、AudioFocus、路由变更、gapless 队列）的前提下，把 Web-Android 相对出声偏差压到**感知阈值以下（<30ms，理想 <5ms）**，并顺手消除 `schedulePositionCorrection` 的可听裂声。

非目标：

- 不追求端到端"点击 → 出声"总时长最短（这条由协议层的 `PENDING_PLAY_TIMEOUT + 未来锚点` 决定，与本方案正交）；
- 不追求"同室裸放合奏"级的绝对采样对齐（那需要跨设备时钟同步做到 <1ms 层级，本方案不承诺）；
- iOS 侧不在本方案范围（见 D4）。

## 3. 方案对比

两条候选，本方案确定走 B：

| 方案 | 稳态偏差 | 抖动 | 工程量 | 生态代价 |
|---|---|---|---|---|
| **B. Media3 自定义 `AudioSink`（采用）** | <5ms | <2ms | 3–5 天 | AudioProcessor 链需适配 |
| C. 完全自研（绕过 ExoPlayer） | <1ms | <1ms | 1–2 周 | 解码/焦点/路由/MediaSession 全部自撸 |

排除的方向（供档案参考）：

- **纯 output latency 反馈补偿**（不动播放管线，`playAt(target - measuredLatency)` 提前触发 + 删 `schedulePositionCorrection`）：改动最小，但属于**开环预测**，AudioTrack primer 冷/热启动之间有几十毫秒差异，稳态误差 10–30ms 是天花板；不满足"消除可感知偏差"要求。
- **方案 C**：收益上界比方案 B 只多 <5ms 精度和 <2ms 抖动，与工程量完全不成正比。仅在 Media3 `AudioSink` 抽象封死了必要能力时才需要，本调研未发现此类阻塞。

### 3.1 方案 B：Media3 自定义 `AudioSink`

Media3 显式支持替换 `DefaultAudioSink`——保留 MediaSourceFactory、MediaCodec 解码链、MediaSession，只重写"从 PCM buffer 到 AudioTrack"的输出层。

核心思路：

- 从 LOAD 阶段就把 AudioTrack 打开并持续写**静音 PCM**——AudioTrack 永远处于 STATE_ACTIVE，primer 摊到 LOAD 里；
- `handleBuffer(ByteBuffer, presentationTimeUs, ...)` 收到解码器送来的真实 PCM 时**不立刻写**，暂存到内部 ring buffer；
- 协议侧的 `executeAtElapsedMs` 转换为 AudioTrack 的目标 `startFrame`：
  ```
  ts = AudioTrack.getTimestamp()
  framesUntilTarget = (targetNanos - ts.nanoTime) * sampleRate / 1e9
  startFrame = ts.framePosition + framesUntilTarget
  ```
- 输出线程继续写静音直到 `writtenFrames == startFrame`，然后无缝切入 ring buffer 的真实 PCM。

因为 AudioTrack 的 `framePosition → nanoTime` 是硬件级单调映射（CDD 要求 ±1–2ms 精度），切入点是**采样级**的。

## 4. 方案 B 深入评估

### 4.1 接入方式

Media3 通过 `DefaultRenderersFactory.buildAudioSink` 暴露替换点，Kotlin 覆盖示例（[Media3 官方 customization 文档](https://developer.android.com/media/media3/exoplayer/customization)）：

```kotlin
val renderersFactory = object : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
        enableOffload: Boolean,
    ): AudioSink {
        return ScheduledAudioSink(
            delegate = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setOffloadMode(DefaultAudioSink.OFFLOAD_MODE_DISABLED)  // 采样级要求关闭 offload
                .build(),
            clock = playbackClock,
        )
    }
}
val player = ExoPlayer.Builder(context)
    .setRenderersFactory(renderersFactory)
    .build()
```

替换点只影响 audio renderer，其他一切（`MediaSourceFactory`、`MediaCodec` 池、MediaSession session）都不动。

### 4.2 `AudioSink` 接口面

从 [androidx/media 源码](https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioSink.java) 提取的抽象方法（Media3 1.5+）：

**核心数据面**：
- `configure(Format, int, int[])` — 输入格式声明，重配触发时 sink 内部会 flush
- `handleBuffer(ByteBuffer, long presentationTimeUs, int)` — 解码器送 PCM
- `handleDiscontinuity()` — 时间轴不连续
- `play()` / `pause()` / `flush()` / `reset()` / `release()` — 生命周期
- `playToEndOfStream()` / `isEnded()` / `hasPendingData()` — 尾部收敛

**控制/查询面**：
- `getCurrentPositionUs(boolean)` — 当前播放位置（由 `AudioTrack.getTimestamp` 支持）
- `setPlaybackParameters(PlaybackParameters)` / `getPlaybackParameters()` — 变速/变调
- `setVolume(float)` / `setAudioAttributes(...)` / `setAudioSessionId(int)`
- `setSkipSilenceEnabled(boolean)` — 静默跳过
- `getAudioTrackBufferSizeUs()` — 内部 buffer 深度

**能力查询面**：
- `supportsFormat(Format)` / `getFormatSupport(Format)` / `getFormatOffloadSupport(Format)`

**Listener 回调**（`AudioSink.Listener`）：
- `onPositionAdvancing(long playoutStartSystemTimeMs)` — 首次出声，可拿到实测起播时刻
- `onUnderrun(...)` — 欠载
- `onAudioTrackInitialized(AudioTrackConfig)` / `onAudioTrackReleased(...)`
- `onAudioSinkError(Exception)`

对本方案最关键的是 `onPositionAdvancing`——它给出"AudioTrack 首个真实样本被硬件播出的墙钟时刻"，可以用来**反馈校准 startFrame 的计算**。

### 4.3 实现骨架：`ScheduledAudioSink`

选型：**装饰 `DefaultAudioSink`**（而非从零实现整个接口）。

装饰模式收益：所有能力查询、格式支持、offload 支持、错误码、`Listener` 分发全部委托给 `DefaultAudioSink`，我们只在 `handleBuffer` 里做一件事——决定当前这批 PCM 是**直接下发**给底层 sink（正常播），还是**延迟到目标采样点后**再下发（调度起播）。

Media3 已经提供 `ForwardingAudioSink` 作为装饰基类（[androidx/media/audio/ForwardingAudioSink.java](https://github.com/androidx/media)），继承它只需覆盖需要的方法。

草图：

```kotlin
class ScheduledAudioSink(
    delegate: AudioSink,
    private val clock: PlaybackClock,
) : ForwardingAudioSink(delegate) {

    @Volatile private var scheduledStartElapsedNs: Long? = null
    private var currentFormat: Format? = null
    private val pendingBuffers = ArrayDeque<PcmChunk>()

    fun scheduleStartAt(executeAtElapsedMs: Long) {
        scheduledStartElapsedNs = executeAtElapsedMs * 1_000_000L
    }

    override fun configure(inputFormat: Format, bufferSize: Int, outputChannels: IntArray?) {
        currentFormat = inputFormat
        super.configure(inputFormat, bufferSize, outputChannels)
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int,
    ): Boolean {
        val scheduled = scheduledStartElapsedNs ?: return super.handleBuffer(
            buffer, presentationTimeUs, encodedAccessUnitCount,
        )
        val format = currentFormat ?: return super.handleBuffer(
            buffer, presentationTimeUs, encodedAccessUnitCount,
        )

        // 目标 startFrame 计算——用 delegate 里 AudioTrack 的 timestamp 做映射
        val nowElapsedNs = SystemClock.elapsedRealtimeNanos()
        val delayNs = scheduled - nowElapsedNs
        if (delayNs <= 0) {
            scheduledStartElapsedNs = null  // 迟到，直接放
            return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        }

        val silenceFrames = (delayNs * format.sampleRate / 1_000_000_000L).toInt()
        val silenceBuffer = obtainSilencePcm(format, silenceFrames)

        // 先下发静音，再下发真实 PCM
        val silenceConsumed = super.handleBuffer(silenceBuffer, presentationTimeUs, 0)
        if (!silenceConsumed) {
            recycleSilencePcm(silenceBuffer)
            return false  // sink 反压，等下一轮
        }
        scheduledStartElapsedNs = null
        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    override fun flush() {
        scheduledStartElapsedNs = null
        pendingBuffers.clear()
        super.flush()
    }
}
```

关键点：

- **静音注入不改变 `presentationTimeUs`**：Media3 用 presentationTimeUs 做 A/V 同步与位置估算，我们只是在时间轴上"提前灌 N 帧静音"，presentationTimeUs 保持真实值即可。位置估算走底层 `DefaultAudioSink` 的 `AudioTrack.getTimestamp` 自然对齐。
- **`silenceFrames` 精度由 `AudioTrack.getTimestamp` 保证**：CDD 声明支持的设备 ±2ms（[Android CDD 5.6 audio-latency](https://android.googlesource.com/platform/compatibility/cdd/+/refs/heads/master/5_multimedia/5_6_audio-latency.md)），实际测量常见 ±1ms。这是采样级方案的精度上限。
- **反压处理**：`super.handleBuffer` 返回 `false` 表示底层 sink buffer 满，需要下一轮 renderer tick 再喂。静音注入路径必须尊重这个协议。
- **flush 语义**：seek、pause、track change 时 renderer 调用 `flush`，调度状态一并清空。

### 4.4 与协议侧的接线

- `PlaybackController.handleScheduledAction(PLAY)` 里，preload 完成后调用 `scheduledAudioSink.scheduleStartAt(executeAtElapsedMs)`，然后 `engine.player.play()`；
- `PlayerEngine.playAt` 里的 `handler.postDelayed(...)` + spin 对齐**可以移除**——起播时刻由 sink 内的静音注入决定，不再依赖上层 Runnable 触发；
- 起播后由 `AudioSink.Listener.onPositionAdvancing(playoutStartSystemTimeMs)` 回调对齐"实际首次出声时刻"，用于验证 driftMs 是否收敛；
- `schedulePositionCorrection` **删除**——采样级精度下不需要事后 seek 校正，`driftMs` 应稳定在 ±2ms 内。

### 4.5 边界与副作用

**关闭 offload**：Media3 offload 模式下 AudioTrack 直接吃编码码流，没有 PCM 通路，`ScheduledAudioSink` 拦不到静音注入点。`buildAudioSink` 里显式传 `OFFLOAD_MODE_DISABLED`。带来的损失：低码率长曲目下 CPU/电量收益丢失，UniRhy 场景可接受。

**关闭 tunneling**：同理，tunneling 模式（视频+音频直通 HAL）跳过 PCM 通路。`enableTunnelingV21()` 不能被本 sink 转发下去，或者转发但打印 warning。

**AudioProcessor 链**：`DefaultAudioSink` 内部有一条 `AudioProcessor` 链（重采样、变速、音量渐变、silence skip 等）。静音注入发生在 sink 输入端，AudioProcessor 链在 sink 内部——静音会被 processor 链原样透传，不会有兼容问题。变速播放（`PlaybackParameters(speed != 1.0)`）会改变实际输出的 frame 速率，`silenceFrames` 计算需按 `speed` 倒推。UniRhy 目前不用变速，此项作为已知限制记录。

**AudioFocus / 路由切换**：全部走底层 `DefaultAudioSink`，行为不变。路由切换会触发 `flush`，我们的 `scheduledStartElapsedNs` 一并清空，符合语义。

**首次 configure**：`configure` 之后到第一次 `handleBuffer` 之间的静音写入由底层 `DefaultAudioSink` 自动处理（primer）。我们的调度起效于"scheduleStartAt 已经调用 + configure 已完成 + 有真实 PCM 到达"三条同时成立时。冷启动首播时机需要保证 `scheduleStartAt` 在 `handleBuffer` 之前调用——由 `PlaybackController` 的 `handleScheduledAction` 顺序保证。

### 4.6 已有生态位

Media3 内部本就在 500ms 间隔调用 `AudioTrack.getTimestamp()`（[ExoPlayer #3830](https://github.com/google/ExoPlayer/issues/3830) 讨论了这个频率是否合理）。方案 B 只是把这条**已经存在的时钟映射**扩展到"输入端调度"用途，不引入新的 API 依赖。

社区的采样级调度实践（[Metronome via AudioTrack 一文](https://moshenskyi.medium.com/building-a-sample-accurate-metronome-with-audiotrack-in-android-7da27ac7dae1)）验证了"预灌静音 + 按 frame 数下发真实 PCM"在裸 `AudioTrack` 上的可行性；本方案把这条思路从"节拍器场景"迁到"网络协议驱动的多设备协播场景"，并复用 Media3 的解码链。

## 5. 实施路线

**阶段 0——诊断基线**（0.5 天）
- `PlayerEngine.playAt` 增补日志：`player.play()` 调用时刻、`onIsPlayingChanged(true)` 时刻、`AudioSink.Listener.onPositionAdvancing` 时刻，形成"命令 → 承认播放 → 实际出声"三点时序基线；
- 目的：改造前后可对比同一台设备上的 driftMs，作为方案 B 的验收依据。

**阶段 1——`ScheduledAudioSink` 骨架**（1 天）
- 装饰 `DefaultAudioSink`；`scheduleStartAt` 接口；静音 PCM 池化；`flush` 清空；
- 单元测试：模拟 `handleBuffer` 时序，验证 `startFrame` 计算与静音注入长度；
- 边界单测：`scheduleStartAt` 未调用时行为等价于直通、目标时刻已过时的 late 分支、`configure` 前后调度。

**阶段 2——接线**（1 天）
- `DefaultRenderersFactory.buildAudioSink` 覆盖（关闭 offload/tunneling）；
- `PlaybackController.handleScheduledAction(PLAY)` 调用 `scheduleStartAt`，移除 `PlayerEngine.playAt` 里的 postDelayed + spin 调度；
- **删除 `PlayerEngine.schedulePositionCorrection`**——采样级精度下不需要事后 seek 校正，同时消除现有可听裂声；
- `AudioSink.Listener.onPositionAdvancing` 回填 `local-execution` 诊断事件。

**阶段 3——设备实测**（1–2 天）
- Xiaomi 5G（本次基线设备）：目标 driftMs <5ms，可听裂声消失；
- 其他形态：平板、低端机、有线耳机、蓝牙（作为已知劣化基线，不做为验收）；
- 边界回归：切歌 gapless、SEEK、PAUSE→PLAY、后台切前台、路由切换、AudioFocus 抢占。

## 6. 风险与回退

**风险 1**：`ForwardingAudioSink` 在 Media3 版本升级时接口变动。历史上 `configure` 参数已经 breaking-change 过一次（[相关讨论](https://github.com/androidx/media/issues/1728)），下次升级需要跟随。

**回退**：`ScheduledAudioSink` 与协议解耦，`scheduleStartAt` 未调用时行为等价于 `DefaultAudioSink`；出问题可以在 `DefaultRenderersFactory` 里一行改回原生 sink，无协议兼容影响。

**风险 2**：设备 `AudioTrack.getTimestamp` 精度不达 CDD 标称（部分低端设备已知 ±10ms）。

**回退**：在 `ScheduledAudioSink` 内加一层"多帧平滑"——起播前用连续多次 `getTimestamp` 采样，用中位数或滑动均值代替单次读数。CDD 声明支持的设备（`android.hardware.audio.output`）±2ms 精度是硬性要求，低端设备就是本方案的验收门槛外场景，接受降级。

**风险 3**：静音注入引入的**额外延迟窗口**（等待 `handleBuffer` 到达）与协议侧 `executeAtElapsedMs` 的时间关系错配——目标时刻已过但 `handleBuffer` 未到，静音注入无处发生。

**回退**：`handleScheduledAction` 里 `delayMs < 0` 分支已经有 late 补偿逻辑，扩展成"若 sink 未接到 PCM 时目标时刻已过，则跳过静音注入直接透传，并上报 lateSeconds"。

## 7. 交付判据

Xiaomi 5G 基线设备（有线/扬声器）实测。

### 7.1 前置改造

- 新增 `AudioSink.Listener.onPositionAdvancing` 钩子，把 `playoutStartSystemTimeMs` 换算到服务端时钟后作为一条 `playout-advanced` 事件上报（与 `local-execution` 同格式），并上抛到服务端形成 echo；
- Web 侧同样在 `AudioBufferSourceNode` 起播时用 `AudioContext.getOutputTimestamp()` 换算发一份 echo；
- 服务端拿两端 echo 与自己下发的 `serverTimeToExecuteMs` 做差——**测量点集中在服务端一次比对，只吃一次 NTP 误差**，避免设备本地时钟对齐叠加噪声。

### 7.2 自动化可验证部分

驱动与采集脚本化：`agent-browser` 触发 Web 端 PLAY / PAUSE→PLAY / 首曲切下一首各 20 次，`adb shell input` 触发 Android 端对称操作。

- **主指标**（服务端 echo 比对）：Android `playoutStartServerMs - serverTimeToExecuteMs` 中位数 <5ms，P95 <15ms；
- **副指标**：起播 500ms 内不再出现 `AudioTrack` 销毁重建日志（`grep '~AudioTrack\|AudioTrack: stop.*STATE_FLUSHED' logcat`，当前基线 100% 触发）；
- **自动化回归**：切歌 gapless、SEEK、PAUSE→PLAY、后台↔前台切换、其他 app（自带音乐播放器）抢占 AudioFocus——均不劣化。

### 7.3 需要人工介入验证部分

以下场景 shell 无权限或需要物理动作，纳入发版前手动 checklist：

- **有线路由切换**：物理拔插 3.5mm 耳机 / USB-C DAC，验证 `AUDIO_BECOMING_NOISY` 后 sink 状态正确、恢复播放后同步不失锁；`adb shell am broadcast HEADSET_PLUG` 已经被 Android 限制，做不到；
- **来电抢占 AudioFocus**：真机呼入或另一台设备打电话过来，验证挂断后播放恢复且不带位置漂移；`am broadcast PHONE_STATE` 需要 `MODIFY_PHONE_STATE` 权限，shell 拿不到；
- **蓝牙路由**：本方案不作为验收门槛，作为已知劣化基线记录一次即可（不承诺采样级）；
- **主观听感**：戴同一副有线耳机在 Web 与 Android 之间来回切换头戴，判断"卡拍"是否可辨——补充主指标测量之外的人耳复核。

落地后对 iOS 原生化（D4）有直接借鉴意义——AVAudioEngine 的 `scheduleBuffer(at:hostTime:)` 天然是采样级，方案 B 相当于把 iOS 已有的能力在 Android 上补齐。

## 参考资料

- Media3 `AudioSink` 接口源码：[androidx/media/audio/AudioSink.java](https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioSink.java)
- Media3 `DefaultRenderersFactory`：[androidx/media/DefaultRenderersFactory.java](https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/DefaultRenderersFactory.java)
- Media3 customization 文档：[developer.android.com/media/media3/exoplayer/customization](https://developer.android.com/media/media3/exoplayer/customization)
- Android CDD 音频延迟要求：[CDD 5.6 audio-latency](https://android.googlesource.com/platform/compatibility/cdd/+/refs/heads/master/5_multimedia/5_6_audio-latency.md)
- `AudioTrack` 采样级调度实践：[Building a Sample-Accurate Metronome with AudioTrack](https://moshenskyi.medium.com/building-a-sample-accurate-metronome-with-audiotrack-in-android-7da27ac7dae1)
- ExoPlayer `getTimestamp` 使用讨论：[ExoPlayer #3830](https://github.com/google/ExoPlayer/issues/3830)
- 自定义 AudioSink 早期讨论：[ExoPlayer #6180](https://github.com/google/ExoPlayer/issues/6180)
- 多渲染器同步（MixingAudioProcessor）：[ExoPlayer #11317](https://github.com/google/ExoPlayer/issues/11317)
