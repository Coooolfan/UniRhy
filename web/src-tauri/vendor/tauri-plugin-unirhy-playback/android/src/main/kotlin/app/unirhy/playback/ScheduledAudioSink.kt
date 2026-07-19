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
 * 时序约定（与 PlaybackController / PlayerEngine 配合）：
 * 1. PLAY 调度落地时先 `scheduleStartAt`，再做预滚 seek——seek 触发的 [flush] 不清除排期，
 *    这样重灌的第一批真实 PCM 就处于扣留态；
 * 2. 预滚 READY 后立即 `play()`，扣留解除，静音开始排水；起播前的等待窗口内
 *    持续预灌少量静音（[PREFEED_MAX_MS]）让 AudioTrack 提前进入活跃态，primer 摊到 LOAD 阶段；
 * 3. 目标时刻已过（迟到）或输入非 PCM（passthrough）时退化为直通，由上层兜底。
 *
 * 静音与真实 PCM 共用同一 presentationTimeUs：底层 sink 会检测到时间轴不连续并自行
 * 重锚 startMediaTimeUs（非致命，见方案 §4.3），位置估算在真实音频起播后自然对齐。
 *
 * 线程模型：`scheduleStartAt` / `cancelScheduledStart` 可从任意线程调用（volatile 覆盖写），
 * 其余状态只在 ExoPlayer 播放线程（handleBuffer/configure/flush/play 的调用线程）触碰。
 */
@UnstableApi
class ScheduledAudioSink(
    delegate: AudioSink,
    private val nanoClock: () -> Long = SystemClock::elapsedRealtimeNanos,
) : ForwardingAudioSink(delegate) {

    fun interface DiagnosticsListener {
        /** 首个真实样本被硬件播出的近似墙钟时刻（透传自底层 sink 的 onPositionAdvancing）。 */
        fun onPositionAdvancing(playoutStartSystemTimeMs: Long)
    }

    @Volatile
    var diagnosticsListener: DiagnosticsListener? = null

    @Volatile
    private var scheduledStartElapsedNanos: Long? = null

    @Volatile
    private var playRequested = false

    /**
     * 输出链路延迟补偿（写入折算时刻 → 硬件实际出声的固定成本），由实测 playout
     * drift 经 EMA 反馈收敛。仅注入路径的测量参与更新（late/直通轮次的偏差
     * 不反映注入精度）。
     */
    @Volatile
    private var latencyCompensationNanos = 0L

    /** 最近一次起播是否走完静音注入路径（供 [onPlayoutMeasured] 过滤样本）。 */
    @Volatile
    private var startedViaInjection = false

    // ---------- 以下状态仅在播放线程访问 ----------

    private var pcmSampleRate = 0
    private var pcmFrameSize = 0
    private var prefeedBuffer: ByteBuffer? = null
    private var prefedFrames = 0L
    private var pendingSilence: ByteBuffer? = null

    /** 排期起播：目标时刻为 SystemClock.elapsedRealtime() 毫秒轴。 */
    fun scheduleStartAt(executeAtElapsedMs: Long) {
        startedViaInjection = false
        scheduledStartElapsedNanos = executeAtElapsedMs * 1_000_000L
    }

    /**
     * 暂停驻留扣留：无目标时刻，但持续扣留真实 PCM 并预灌静音保活 AudioTrack，
     * 使 renderer 停在第一批真实数据上。后续 PLAY 调度以 [scheduleStartAt] 覆盖后，
     * 注入窗口即为完整的调度提前量；未经排期直接 play 则直通放行。
     */
    fun scheduleHold() {
        startedViaInjection = false
        scheduledStartElapsedNanos = HOLD_TARGET_NANOS
    }

    fun cancelScheduledStart() {
        scheduledStartElapsedNanos = null
    }

    val isScheduled: Boolean
        get() = scheduledStartElapsedNanos != null

    /** 回填实测出声偏差（正值=偏晚），更新延迟补偿。 */
    fun onPlayoutMeasured(driftMs: Long) {
        if (!startedViaInjection) {
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
        val target = scheduledStartElapsedNanos
            ?: return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        if (pcmSampleRate == 0) {
            Log.w(TAG, "scheduled start unsupported for non-PCM input, falling through")
            disarm()
            return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        }

        if (!playRequested) {
            if (target != HOLD_TARGET_NANOS && nanoClock() > target + STALE_SCHEDULE_GRACE_NANOS) {
                // 排期悬空（play 一直没来）：放弃调度，避免 renderer 永久拒收
                Log.w(TAG, "scheduled start stale without play(), disarming")
                disarm()
                return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
            }
            if (target == HOLD_TARGET_NANOS || nanoClock() < target) {
                // 目标时刻已过时不再预灌：迟到分支会直通真实 PCM，多灌的静音只会拖后出声
                prefeedSilence(presentationTimeUs)
            }
            return false
        }

        if (target == HOLD_TARGET_NANOS) {
            // 扣留期未经排期直接 play（快照恢复 / 独立模式）：直通放行
            Log.i(TAG, "hold released without scheduled start, passing through")
            disarm()
            return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        }

        var silence = pendingSilence
        if (silence == null) {
            val delayNanos = (target - nanoClock() - latencyCompensationNanos)
                .coerceAtMost(MAX_SCHEDULE_AHEAD_NANOS)
            val neededFrames = delayNanos * pcmSampleRate / 1_000_000_000L - prefedFrames
            if (neededFrames <= 0) {
                startedViaInjection = false
                Log.i(
                    TAG,
                    "scheduled start late by ${-delayNanos / 1_000_000}ms " +
                        "(prefed=${prefedFrames}f), passing through",
                )
                disarm()
                return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
            }
            silence = newSilenceBuffer(neededFrames)
            pendingSilence = silence
            Log.i(
                TAG,
                "injecting silence frames=$neededFrames (${neededFrames * 1_000 / pcmSampleRate}ms) " +
                    "prefed=${prefedFrames}f delayMs=${delayNanos / 1_000_000}",
            )
        }
        if (silence.hasRemaining()) {
            super.handleBuffer(silence, presentationTimeUs, 0)
            if (silence.hasRemaining()) {
                return false
            }
        }
        pendingSilence = null
        startedViaInjection = true
        disarm()
        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    /**
     * 起播前的等待窗口内向底层预灌少量静音：促使 AudioTrack 在 LOAD 阶段完成创建与
     * primer 填充。预灌帧数计入排期折算（从注入总量中扣除），不影响起播时刻。
     */
    private fun prefeedSilence(presentationTimeUs: Long) {
        var buf = prefeedBuffer
        if (buf == null) {
            buf = newSilenceBuffer(PREFEED_MAX_MS * pcmSampleRate / 1_000L)
            prefeedBuffer = buf
        }
        if (!buf.hasRemaining()) {
            return
        }
        val beforeBytes = buf.remaining()
        super.handleBuffer(buf, presentationTimeUs, 0)
        prefedFrames += (beforeBytes - buf.remaining()) / pcmFrameSize
    }

    private fun newSilenceBuffer(frames: Long): ByteBuffer {
        return ByteBuffer.allocateDirect((frames * pcmFrameSize).toInt())
            .order(ByteOrder.nativeOrder())
    }

    private fun disarm() {
        scheduledStartElapsedNanos = null
        pendingSilence = null
    }

    override fun play() {
        playRequested = true
        super.play()
    }

    override fun pause() {
        playRequested = false
        super.pause()
    }

    override fun flush() {
        // 排期保留：PLAY 落地顺序是先 scheduleStartAt 再预滚 seek，seek 触发的 flush
        // 不得撤销刚设置的排期；主动撤销走 cancelScheduledStart / reset
        prefeedBuffer = null
        prefedFrames = 0
        pendingSilence = null
        playRequested = false
        super.flush()
    }

    override fun reset() {
        disarm()
        prefeedBuffer = null
        prefedFrames = 0
        playRequested = false
        super.reset()
    }

    override fun setListener(listener: AudioSink.Listener) {
        super.setListener(object : AudioSink.Listener by listener {
            override fun onPositionAdvancing(playoutStartSystemTimeMs: Long) {
                diagnosticsListener?.onPositionAdvancing(playoutStartSystemTimeMs)
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

        /** 延迟补偿上限，防御异常测量把补偿推飞。 */
        private const val MAX_LATENCY_COMPENSATION_NANOS = 300_000_000L

        /** 排期折算的最大提前量，防御异常远的目标时刻导致超大静音分配。 */
        private const val MAX_SCHEDULE_AHEAD_NANOS = 5_000_000_000L

        /** play() 迟迟未到时的排期失效宽限。 */
        private const val STALE_SCHEDULE_GRACE_NANOS = 2_000_000_000L

        /** 暂停驻留扣留的哨兵目标（无限远，不参与折算与失效判定）。 */
        private const val HOLD_TARGET_NANOS = Long.MAX_VALUE
    }
}
