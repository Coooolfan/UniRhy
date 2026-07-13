package app.unirhy.playback.sync

import kotlin.math.ceil
import kotlin.math.max

data class NtpMeasurement(
    val offsetMs: Double,
    val rttMs: Double,
    val recordedAtMs: Long,
)

data class NtpSummary(
    val clockOffsetMs: Double,
    val roundTripEstimateMs: Double,
)

/**
 * NTP 风格校时器，算法与 TS 端（playbackSyncClient.ts）逐项一致：
 * offset = ((t1 - t0) + (t2 - t3)) / 2，RTT 升序取最优半数求平均。
 *
 * 内部以单调钟为时间基准（Android 上为 SystemClock.elapsedRealtime），
 * 启动时锚定一次墙钟，之后所有 t0/t3 均由 "墙钟锚点 + 单调增量" 推导，
 * 免疫系统时间跳变；JVM 单测通过注入时钟函数驱动。
 */
class NtpClock(
    monotonicNowMs: () -> Long,
    wallNowMs: () -> Long,
) {
    private val monotonicNowMs = monotonicNowMs
    private val wallAnchorMs: Long = wallNowMs() - monotonicNowMs()

    private val measurements = ArrayDeque<NtpMeasurement>()

    @Volatile
    var clockOffsetMs: Double = 0.0
        private set

    @Volatile
    var roundTripEstimateMs: Double = 0.0
        private set

    fun clientNowMs(): Long = wallAnchorMs + monotonicNowMs()

    fun estimatedServerNowMs(): Long = clientNowMs() + clockOffsetMs.toLong()

    /** 记录一次 NTP_RESPONSE 采样，返回该次测量值。 */
    fun recordResponse(payload: NtpResponsePayload): NtpMeasurement {
        val t3 = clientNowMs()
        val offsetMs = ((payload.t1 - payload.t0) + (payload.t2 - t3)) / 2.0
        val rttMs = max(0.0, ((t3 - payload.t0) - (payload.t2 - payload.t1)).toDouble())
        val measurement = NtpMeasurement(offsetMs = offsetMs, rttMs = rttMs, recordedAtMs = t3)
        synchronized(measurements) {
            measurements.addLast(measurement)
            while (measurements.size > MAX_MEASUREMENT_COUNT) {
                measurements.removeFirst()
            }
        }
        return measurement
    }

    /** 用滚动窗口内的采样刷新 offset/RTT 估计（稳态心跳路径）。 */
    fun applyRollingSummary(): NtpSummary? {
        val snapshot = synchronized(measurements) { measurements.toList() }
        return applySummary(snapshot)
    }

    /** 用给定采样集合刷新 offset/RTT 估计（初始校准路径）。 */
    fun applySummary(samples: List<NtpMeasurement>): NtpSummary? {
        val summary = summarize(samples) ?: return null
        clockOffsetMs = summary.clockOffsetMs
        roundTripEstimateMs = summary.roundTripEstimateMs
        return summary
    }

    companion object {
        const val MAX_MEASUREMENT_COUNT = 20

        fun summarize(samples: List<NtpMeasurement>): NtpSummary? {
            if (samples.isEmpty()) {
                return null
            }
            val sortedByRtt = samples.sortedBy(NtpMeasurement::rttMs)
            val selected = sortedByRtt.take(ceil(sortedByRtt.size / 2.0).toInt())
            return NtpSummary(
                clockOffsetMs = selected.map(NtpMeasurement::offsetMs).average(),
                roundTripEstimateMs = selected.map(NtpMeasurement::rttMs).average(),
            )
        }
    }
}
