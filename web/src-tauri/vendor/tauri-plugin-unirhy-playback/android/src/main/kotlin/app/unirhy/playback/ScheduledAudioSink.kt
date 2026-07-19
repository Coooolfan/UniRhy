package app.unirhy.playback

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 采样级起播调度的 `AudioSink` 装饰器（方案见 docs/ANDROID_SAMPLE_ACCURATE_PLAYBACK.md）。
 *
 * 解码/焦点/路由等一切能力委托给底层 `DefaultAudioSink`，本类只干预 `handleBuffer`：
 * `scheduleStartAt` 排期后，真实 PCM 被扣留，先向底层灌入按目标单调钟时刻折算的静音帧，
 * 静音排水完毕的那个采样点即为可听起播点——精度由 AudioTrack 的帧钟保证，
 * 不再依赖上层 Handler 的毫秒级唤醒。
 *
 * 注入分两段：
 * 1. 大块——按写入时刻模型（含延迟补偿）折算，刻意留尾 [TAIL_TRIM_MS]；
 * 2. 精修——等播放头运转、帧钟可读后，按「已写帧 - 已播帧 = 队列深度」精确补齐尾段。
 *    AudioTrack 要求 buffer 填到 start threshold 才开始出声，等待期间持续补灌静音踢过
 *    阈值；多灌的量由帧钟折算自动扣回。延迟补偿按「留尾预算 - 实际精修量」误差收敛。
 *
 * 时序约定（与 PlaybackController / PlayerEngine 配合）：
 * 1. PLAY 调度落地时先 `scheduleStartAt`，再做预滚 seek——seek 触发的 [flush] 不清除排期，
 *    这样重灌的第一批真实 PCM 就处于扣留态；
 * 2. 预滚 READY 后立即 `play()`，扣留解除，静音开始排水；起播前的等待窗口内
 *    持续预灌少量静音（[PREFEED_MAX_MS]）让 AudioTrack 提前进入活跃态；
 * 3. 暂停驻留经 [scheduleHold] 保持扣留，恢复 PLAY 时注入窗口完整；
 * 4. 目标时刻已过（迟到）或输入非 PCM（passthrough）时退化为直通，由上层兜底。
 *
 * AudioSink 契约：部分消费的 buffer 必须原样重送，因此所有静音写入统一走
 * [activeSilence] 单块纪律——上一块写完才允许换块，真实 PCM 递送前必先排空。
 *
 * 静音与真实 PCM 共用同一 presentationTimeUs：底层 sink 会检测到时间轴不连续并自行
 * 重锚 startMediaTimeUs（非致命，见方案 §4.3），位置估算在真实音频起播后自然对齐。
 *
 * 线程模型：`scheduleStartAt` / `scheduleHold` / `cancelScheduledStart` 可从任意线程调用
 * （volatile 覆盖写），其余状态只在 ExoPlayer 播放线程触碰。
 */
@UnstableApi
class ScheduledAudioSink(
    delegate: AudioSink,
    private val nanoClock: () -> Long = SystemClock::elapsedRealtimeNanos,
) : ForwardingAudioSink(delegate) {

    fun interface DiagnosticsListener {
        /** 首个真实样本被硬件播出的近似墙钟时刻（前置静音时长已折入）。 */
        fun onPositionAdvancing(playoutStartSystemTimeMs: Long)
    }

    @Volatile
    var diagnosticsListener: DiagnosticsListener? = null

    @Volatile
    private var scheduledStartElapsedNanos: Long? = null

    @Volatile
    private var playRequested = false

    /**
     * 输出链路延迟补偿：两段式模型下的职责是让大块欠写出精修空间，
     * 按帧钟轮的「留尾预算 - 实际精修量」误差收敛；帧钟不可用时兼作
     * 写入时刻模型的固定成本估计（由实测 playout drift 经 EMA 反馈）。
     */
    @Volatile
    private var latencyCompensationNanos = 0L

    /** 最近一次起播是否走完静音注入路径（供 [onPlayoutMeasured] 过滤样本）。 */
    @Volatile
    private var startedViaInjection = false

    /**
     * 最近一次注入的精修是否走了帧钟：帧钟精修的落点与写入时刻补偿解耦
     * （补偿变化会被精修抵消），其偏差不参与 EMA 学习。
     */
    @Volatile
    private var lastTrimUsedFrameClock = false

    /**
     * 当前起播前置静音总时长：onPositionAdvancing 报告的是首帧（静音）播出时刻，
     * 换算真实音频出声时刻须加上这段。随每次静音写入持续更新。
     */
    @Volatile
    private var leadingSilenceDurationMs = 0L

    // ---------- 以下状态仅在播放线程访问 ----------

    private var pcmSampleRate = 0
    private var pcmFrameSize = 0

    /** 当前未写完的静音块：AudioSink 契约要求部分消费的 buffer 原样重送。 */
    private var activeSilence: ByteBuffer? = null

    /**
     * 底层 sink 是否挂着未写完的真实 PCM buffer（直通被反压时留下）：
     * 此期间禁止写入任何静音——契约要求下一次递送必须是同一 buffer。
     * flush 会清空底层 pending，一并复位此标记。
     */
    private var delegateHoldsForeignBuffer = false

    /** 本流（自 flush 起）已写入底层的静音帧总数（预灌 + 注入 + 补灌）。 */
    private var silenceWrittenFrames = 0L

    private var bulkComputed = false
    private var trimComputed = false

    /** 阶段一是否真实写出了大块（窗口坍缩轮次的精修误差不参与补偿学习）。 */
    private var bulkHadRoom = false

    /**
     * 注入尚未完成时到达的 onPositionAdvancing（静音已开始播但精修未写入）：
     * 暂存首帧播出时刻，commit 后以最终静音总量折算真实出声时刻再上报。
     * onPositionAdvancing 与 handleBuffer 同在播放线程，无并发。
     */
    private var pendingPlayoutStartMs: Long? = null

    /** 排期起播：目标时刻为 SystemClock.elapsedRealtime() 毫秒轴。 */
    fun scheduleStartAt(executeAtElapsedMs: Long) {
        startedViaInjection = false
        lastTrimUsedFrameClock = false
        holdArmedWhilePlaying = false
        scheduledStartElapsedNanos = executeAtElapsedMs * 1_000_000L
    }

    /**
     * 暂停驻留扣留：无目标时刻，但持续扣留真实 PCM 并预灌静音保活 AudioTrack，
     * 使 renderer 停在第一批真实数据上。后续 PLAY 调度以 [scheduleStartAt] 覆盖后，
     * 注入窗口即为完整的调度提前量；未经排期直接 play 则直通放行。
     */
    fun scheduleHold() {
        startedViaInjection = false
        // pauseAt 在 pause() 落地前排扣留：此窗口内 renderer 仍在送在途真实 PCM，
        // 对其直通但不解除扣留，待 pause() 生效后扣留自然接管
        holdArmedWhilePlaying = playRequested
        scheduledStartElapsedNanos = HOLD_TARGET_NANOS
    }

    @Volatile
    private var holdArmedWhilePlaying = false

    fun cancelScheduledStart() {
        scheduledStartElapsedNanos = null
    }

    val isScheduled: Boolean
        get() = scheduledStartElapsedNanos != null

    /** 回填实测出声偏差（正值=偏晚），更新延迟补偿（仅时钟回退轮次参与）。 */
    fun onPlayoutMeasured(driftMs: Long) {
        if (!startedViaInjection || lastTrimUsedFrameClock) {
            return
        }
        val updated = latencyCompensationNanos + driftMs * 1_000_000L / 2
        latencyCompensationNanos = updated.coerceIn(0L, MAX_LATENCY_COMPENSATION_NANOS)
        Log.i(TAG, "latency compensation updated to ${latencyCompensationNanos / 1_000_000}ms (driftMs=$driftMs)")
    }

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        if (inputFormat.sampleMimeType == MimeTypes.AUDIO_RAW &&
            inputFormat.pcmEncoding != C.ENCODING_PCM_8BIT
        ) {
            // 8-bit PCM 的静音基线是 0x80 而非全零，不支持注入；实际解码链恒为 16-bit+
            pcmSampleRate = inputFormat.sampleRate
            pcmFrameSize = Util.getPcmFrameSize(inputFormat.pcmEncoding, inputFormat.channelCount)
        } else {
            pcmSampleRate = 0
            pcmFrameSize = 0
        }
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int,
    ): Boolean {
        // 未写完的静音块优先排空（契约：递送任何其他 buffer 前必须先送完它）
        if (pcmSampleRate > 0 && !drainActiveSilence(presentationTimeUs)) {
            return false
        }

        val target = scheduledStartElapsedNanos
            ?: return passThrough(buffer, presentationTimeUs, encodedAccessUnitCount)
        if (pcmSampleRate == 0) {
            Log.w(TAG, "scheduled start unsupported for non-PCM input, falling through")
            disarm()
            return passThrough(buffer, presentationTimeUs, encodedAccessUnitCount)
        }

        if (!playRequested) {
            if (target != HOLD_TARGET_NANOS && nanoClock() > target + STALE_SCHEDULE_GRACE_NANOS) {
                // 排期悬空（play 一直没来）：放弃调度，避免 renderer 永久拒收
                Log.w(TAG, "scheduled start stale without play(), disarming")
                disarm()
                return passThrough(buffer, presentationTimeUs, encodedAccessUnitCount)
            }
            if (delegateHoldsForeignBuffer) {
                // 底层还挂着直通期留下的真实 buffer，等 seek-flush 清场前不得写静音
                return false
            }
            if (target == HOLD_TARGET_NANOS || nanoClock() < target) {
                // 预灌少量静音让 AudioTrack 提前创建；目标已过时不再多灌
                val capFrames = PREFEED_MAX_MS * pcmSampleRate / 1_000L
                if (silenceWrittenFrames < capFrames) {
                    queueSilence(capFrames - silenceWrittenFrames)
                    drainActiveSilence(presentationTimeUs)
                }
            }
            return false
        }

        if (target == HOLD_TARGET_NANOS) {
            if (holdArmedWhilePlaying) {
                // 暂停尚未落地的在途播放：直通，不解除扣留
                return passThrough(buffer, presentationTimeUs, encodedAccessUnitCount)
            }
            // 扣留期未经排期直接 play（快照恢复 / 独立模式）：直通放行
            Log.i(TAG, "hold released without scheduled start, passing through")
            disarm()
            return passThrough(buffer, presentationTimeUs, encodedAccessUnitCount)
        }

        if (delegateHoldsForeignBuffer) {
            // 理论上 PLAY 前必有 flush 清场；防御性直通避免契约违例
            Log.w(TAG, "delegate still holds a foreign buffer at injection, passing through")
            disarm()
            return passThrough(buffer, presentationTimeUs, encodedAccessUnitCount)
        }

        // 阶段一：按写入时刻模型（含延迟补偿）折算大块静音，留尾给阶段二
        if (!bulkComputed) {
            val delayNanos = (target - nanoClock()).coerceAtMost(MAX_SCHEDULE_AHEAD_NANOS)
            if (delayNanos <= 0) {
                startedViaInjection = false
                Log.i(
                    TAG,
                    "scheduled start late by ${-delayNanos / 1_000_000}ms " +
                        "(written=${silenceWrittenFrames}f), passing through",
                )
                disarm()
                return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
            }
            bulkComputed = true
            val tailFrames = TAIL_TRIM_MS * pcmSampleRate / 1_000L
            val bulkFrames = (delayNanos - latencyCompensationNanos) * pcmSampleRate / 1_000_000_000L -
                silenceWrittenFrames - tailFrames
            Log.i(
                TAG,
                "injecting silence bulk=${bulkFrames.coerceAtLeast(0)}f written=${silenceWrittenFrames}f " +
                    "delayMs=${delayNanos / 1_000_000} compMs=${latencyCompensationNanos / 1_000_000}",
            )
            bulkHadRoom = bulkFrames > 0
            if (bulkFrames > 0) {
                queueSilence(bulkFrames)
                if (!drainActiveSilence(presentationTimeUs)) {
                    return false
                }
            }
        }

        // 阶段二：等播放头运转（帧钟可读）后按队列深度精修尾段
        if (!trimComputed) {
            val trim = computeTrimFrames(target, presentationTimeUs)
            if (!trim.usedFrameClock && nanoClock() < target + TRIM_FRAME_CLOCK_GRACE_NANOS) {
                // 帧钟未就绪=播放头尚未运转。AudioTrack 要求 buffer 填到 start threshold
                // 才开始出声，持续补灌静音踢过阈值；多灌的量在头运转后由帧钟折算扣回。
                // 超过宽限仍无帧钟才时钟回退，避免 renderer 永久滞留
                topUpStartThreshold(presentationTimeUs)
                return false
            }
            trimComputed = true
            lastTrimUsedFrameClock = trim.usedFrameClock
            if (trim.usedFrameClock && bulkHadRoom) {
                // comp 在两段式模型下的职责是让大块欠写出精修空间：按
                // 「留尾预算 - 实际精修量」的误差收敛。trim 为负（超写无法收回，
                // 落点必然偏晚）时误差为正，加大 comp；trim 远超预算则回缩。
                // 大块为 0 的轮次（窗口坍缩）误差不反映 comp 的作用，跳过
                val tailFrames = TAIL_TRIM_MS * pcmSampleRate / 1_000L
                val errorNanos = (tailFrames - trim.frames) * 1_000_000_000L / pcmSampleRate
                latencyCompensationNanos = (latencyCompensationNanos + errorNanos / 2)
                    .coerceIn(0L, MAX_LATENCY_COMPENSATION_NANOS)
            }
            val trimFrames = trim.frames
                .coerceAtMost(MAX_SCHEDULE_AHEAD_NANOS * pcmSampleRate / 1_000_000_000L)
            Log.i(
                TAG,
                "trim silence frames=${trimFrames.coerceAtLeast(0)}f frameClock=${trim.usedFrameClock} " +
                    "compMs=${latencyCompensationNanos / 1_000_000}",
            )
            if (trimFrames > 0) {
                queueSilence(trimFrames)
                if (!drainActiveSilence(presentationTimeUs)) {
                    return false
                }
            }
        }

        startedViaInjection = true
        disarm()
        pendingPlayoutStartMs?.let { playoutStartMs ->
            pendingPlayoutStartMs = null
            diagnosticsListener?.onPositionAdvancing(playoutStartMs + leadingSilenceDurationMs)
        }
        return passThrough(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    /** 递送真实 PCM 并跟踪底层是否留下未写完的外部 buffer。 */
    private fun passThrough(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int,
    ): Boolean {
        val handled = super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        delegateHoldsForeignBuffer = !handled
        return handled
    }

    private class TrimResult(val frames: Long, val usedFrameClock: Boolean)

    /**
     * 阶段二折算：优先用底层 sink 的播放位置（AudioTrack 帧钟支持）推得未播队列深度，
     * 目标剩余时长减队列深度即为待补静音；位置尚不可用时回退写入时刻模型。
     */
    private fun computeTrimFrames(target: Long, presentationTimeUs: Long): TrimResult {
        val remainingNanos = target - nanoClock()
        val positionUs = super.getCurrentPositionUs(false)
        return if (positionUs != AudioSink.CURRENT_POSITION_NOT_SET && positionUs > presentationTimeUs) {
            val audibleFrames = (positionUs - presentationTimeUs) * pcmSampleRate / 1_000_000L
            val queuedFrames = (silenceWrittenFrames - audibleFrames).coerceAtLeast(0)
            TrimResult(
                frames = remainingNanos * pcmSampleRate / 1_000_000_000L - queuedFrames,
                usedFrameClock = true,
            )
        } else {
            TrimResult(
                frames = (remainingNanos - latencyCompensationNanos) * pcmSampleRate / 1_000_000_000L -
                    silenceWrittenFrames,
                usedFrameClock = false,
            )
        }
    }

    /** 播放头未运转时向底层补灌静音，直到超过 AudioTrack 的 start threshold。 */
    private fun topUpStartThreshold(presentationTimeUs: Long) {
        val thresholdFrames = startThresholdFrames()
        if (silenceWrittenFrames >= thresholdFrames) {
            return
        }
        val chunkFrames = (thresholdFrames - silenceWrittenFrames)
            .coerceAtMost(TOP_UP_CHUNK_MS * pcmSampleRate / 1_000L)
        queueSilence(chunkFrames)
        drainActiveSilence(presentationTimeUs)
    }

    private fun startThresholdFrames(): Long {
        val bufferUs = super.getAudioTrackBufferSizeUs()
        val bufferFrames = if (bufferUs == C.TIME_UNSET) {
            DEFAULT_START_THRESHOLD_MS * pcmSampleRate / 1_000L
        } else {
            bufferUs * pcmSampleRate / 1_000_000L
        }
        return bufferFrames + START_THRESHOLD_MARGIN_MS * pcmSampleRate / 1_000L
    }

    /** 仅在 [activeSilence] 已排空时调用。 */
    private fun queueSilence(frames: Long) {
        activeSilence = ByteBuffer.allocateDirect((frames * pcmFrameSize).toInt())
            .order(ByteOrder.nativeOrder())
    }

    /** 向底层排水当前静音块；返回 true 表示已排空（或无待写块）。 */
    private fun drainActiveSilence(presentationTimeUs: Long): Boolean {
        val silence = activeSilence ?: return true
        if (silence.hasRemaining()) {
            val beforeBytes = silence.remaining()
            super.handleBuffer(silence, presentationTimeUs, 0)
            silenceWrittenFrames += (beforeBytes - silence.remaining()) / pcmFrameSize
            leadingSilenceDurationMs = silenceWrittenFrames * 1_000 / pcmSampleRate
            if (silence.hasRemaining()) {
                return false
            }
        }
        activeSilence = null
        return true
    }

    private fun disarm() {
        scheduledStartElapsedNanos = null
        bulkComputed = false
        trimComputed = false
    }

    override fun play() {
        playRequested = true
        super.play()
    }

    override fun pause() {
        playRequested = false
        holdArmedWhilePlaying = false
        super.pause()
    }

    override fun flush() {
        // 排期保留：PLAY 落地顺序是先 scheduleStartAt 再预滚 seek，seek 触发的 flush
        // 不得撤销刚设置的排期；主动撤销走 cancelScheduledStart / reset。
        // 底层 pending buffer 一并被 flush，activeSilence 可安全丢弃
        activeSilence = null
        silenceWrittenFrames = 0
        leadingSilenceDurationMs = 0
        bulkComputed = false
        trimComputed = false
        bulkHadRoom = false
        delegateHoldsForeignBuffer = false
        pendingPlayoutStartMs = null
        playRequested = false
        super.flush()
    }

    override fun reset() {
        disarm()
        activeSilence = null
        silenceWrittenFrames = 0
        leadingSilenceDurationMs = 0
        delegateHoldsForeignBuffer = false
        playRequested = false
        super.reset()
    }

    override fun setListener(listener: AudioSink.Listener) {
        super.setListener(object : AudioSink.Listener by listener {
            override fun onPositionAdvancing(playoutStartSystemTimeMs: Long) {
                // playoutStartSystemTimeMs 是首帧（含注入静音）的播出时刻，
                // 加上前置静音时长换算为真实音频的出声时刻；注入进行中
                // （精修静音还会追加）时暂存，commit 后以最终总量补发
                if (scheduledStartElapsedNanos != null) {
                    pendingPlayoutStartMs = playoutStartSystemTimeMs
                } else {
                    diagnosticsListener?.onPositionAdvancing(
                        playoutStartSystemTimeMs + leadingSilenceDurationMs,
                    )
                }
                listener.onPositionAdvancing(playoutStartSystemTimeMs)
            }
        })
    }

    companion object {
        private const val TAG = "UnirhyScheduledSink"

        /**
         * 等待 play() 期间的静音预灌上限：目的只是让 AudioTrack 提前完成创建与激活，
         * 不需要大量帧；预灌一旦超过起播剩余窗口，多出的静音会整体拖后出声。
         */
        private const val PREFEED_MAX_MS = 40L

        /** 排期折算的最大提前量，防御异常远的目标时刻导致超大静音分配。 */
        private const val MAX_SCHEDULE_AHEAD_NANOS = 5_000_000_000L

        /** play() 迟迟未到时的排期失效宽限。 */
        private const val STALE_SCHEDULE_GRACE_NANOS = 2_000_000_000L

        /** 暂停驻留扣留的哨兵目标（无限远，不参与折算与失效判定）。 */
        private const val HOLD_TARGET_NANOS = Long.MAX_VALUE

        /** 延迟补偿上限，防御异常测量把补偿推飞。 */
        private const val MAX_LATENCY_COMPENSATION_NANOS = 600_000_000L

        /**
         * 阶段一留尾时长：大块静音刻意欠写这段，等 AudioTrack 运转、帧钟可读后
         * 由阶段二按队列深度精修补齐，绕开写入时刻模型的 primer/延迟估计误差。
         */
        private const val TAIL_TRIM_MS = 60L

        /** 阶段二帧钟等待宽限：目标过后仍无帧钟（播放头始终未运转）才时钟回退提交。 */
        private const val TRIM_FRAME_CLOCK_GRACE_NANOS = 1_000_000_000L

        /** getAudioTrackBufferSizeUs 不可用时的 start threshold 兜底估计。 */
        private const val DEFAULT_START_THRESHOLD_MS = 400L

        /** start threshold 之上的安全余量。 */
        private const val START_THRESHOLD_MARGIN_MS = 10L

        /** 阈值补灌的单次块长（renderer 每次重试补一块）。 */
        private const val TOP_UP_CHUNK_MS = 50L
    }
}
