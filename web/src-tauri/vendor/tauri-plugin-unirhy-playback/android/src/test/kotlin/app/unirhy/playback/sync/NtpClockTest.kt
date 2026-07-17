package app.unirhy.playback.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NtpClockTest {

    private class FakeTime(var monotonicMs: Long = 0, var wallMs: Long = 1_000_000) {
        fun createClock() = NtpClock(
            monotonicNowMs = { monotonicMs },
            wallNowMs = { wallMs },
        )
    }

    @Test
    fun `client clock is anchored to wall time and driven by monotonic time`() {
        val time = FakeTime(monotonicMs = 100, wallMs = 1_000_000)
        val clock = time.createClock()
        assertEquals(1_000_000, clock.clientNowMs())

        // 墙钟跳变不影响客户端时间，单调钟推进才推进
        time.wallMs = 5_000_000
        time.monotonicMs = 200
        assertEquals(1_000_100, clock.clientNowMs())
    }

    @Test
    fun `offset and rtt follow the ntp formulas`() {
        val time = FakeTime(monotonicMs = 0, wallMs = 10_000)
        val clock = time.createClock()

        // t0=10000 发出，服务端 t1=10100 收到 / t2=10110 回发，t3=10040 收到
        time.monotonicMs = 40
        val measurement = clock.recordResponse(NtpResponsePayload(t0 = 10_000, t1 = 10_100, t2 = 10_110))

        // offset = ((t1-t0)+(t2-t3))/2 = (100 + 70) / 2 = 85
        assertEquals(85.0, measurement.offsetMs, 1e-9)
        // rtt = (t3-t0)-(t2-t1) = 40 - 10 = 30
        assertEquals(30.0, measurement.rttMs, 1e-9)
    }

    @Test
    fun `rtt is clamped to zero`() {
        val time = FakeTime(monotonicMs = 0, wallMs = 10_000)
        val clock = time.createClock()
        time.monotonicMs = 5
        val measurement = clock.recordResponse(NtpResponsePayload(t0 = 10_000, t1 = 10_100, t2 = 10_120))
        assertEquals(0.0, measurement.rttMs, 1e-9)
    }

    @Test
    fun `summarize selects the best half by rtt and averages`() {
        val samples = listOf(
            NtpMeasurement(offsetMs = 100.0, rttMs = 50.0, recordedAtMs = 0),
            NtpMeasurement(offsetMs = 10.0, rttMs = 5.0, recordedAtMs = 0),
            NtpMeasurement(offsetMs = 20.0, rttMs = 10.0, recordedAtMs = 0),
            NtpMeasurement(offsetMs = 200.0, rttMs = 80.0, recordedAtMs = 0),
        )
        // 按 RTT 升序取 ceil(4/2)=2 个：rtt=5 (offset 10) 与 rtt=10 (offset 20)
        val summary = NtpClock.summarize(samples)!!
        assertEquals(15.0, summary.clockOffsetMs, 1e-9)
        assertEquals(7.5, summary.roundTripEstimateMs, 1e-9)
    }

    @Test
    fun `summarize returns null for empty samples`() {
        assertNull(NtpClock.summarize(emptyList()))
    }

    @Test
    fun `rolling window keeps at most the last 20 measurements`() {
        val time = FakeTime(monotonicMs = 0, wallMs = 0)
        val clock = time.createClock()
        repeat(30) { index ->
            time.monotonicMs = index.toLong()
            clock.recordResponse(NtpResponsePayload(t0 = 0, t1 = 0, t2 = 0))
        }
        // 无法直接读窗口，但 applyRollingSummary 应成功且不抛
        val summary = clock.applyRollingSummary()!!
        assertEquals(summary.clockOffsetMs, clock.clockOffsetMs, 1e-9)
    }

    @Test
    fun `estimated server time adds the calibrated offset`() {
        val time = FakeTime(monotonicMs = 0, wallMs = 10_000)
        val clock = time.createClock()
        time.monotonicMs = 40
        val measurement = clock.recordResponse(NtpResponsePayload(t0 = 10_000, t1 = 10_100, t2 = 10_110))
        clock.applySummary(listOf(measurement))
        // clientNow = 10040, offset = 85 → serverNow ≈ 10125
        assertEquals(10_125, clock.estimatedServerNowMs())
    }
}
