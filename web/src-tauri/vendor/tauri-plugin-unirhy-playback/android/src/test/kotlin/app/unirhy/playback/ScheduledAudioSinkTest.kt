package app.unirhy.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.audio.AudioSink
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledAudioSinkTest {

    /** 48kHz 16-bit 立体声：frameSize = 4 字节。 */
    private val pcmFormat = Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setPcmEncoding(C.ENCODING_PCM_16BIT)
        .setSampleRate(48_000)
        .setChannelCount(2)
        .build()

    private class Chunk(val bytes: Int, val presentationTimeUs: Long, val isRealBuffer: Boolean)

    /** 记录写入的最小 AudioSink 假身：可配置单次 handleBuffer 的最大吞吐以模拟反压。 */
    private class FakeAudioSink(var maxBytesPerWrite: Int = Int.MAX_VALUE) : AudioSink {
        val chunks = mutableListOf<Chunk>()
        var realBuffer: ByteBuffer? = null
        var playing = false
        var flushCount = 0

        override fun handleBuffer(
            buffer: ByteBuffer,
            presentationTimeUs: Long,
            encodedAccessUnitCount: Int,
        ): Boolean {
            val consumed = minOf(buffer.remaining(), maxBytesPerWrite)
            if (consumed > 0) {
                chunks.add(Chunk(consumed, presentationTimeUs, buffer === realBuffer))
                buffer.position(buffer.position() + consumed)
            }
            return !buffer.hasRemaining()
        }

        fun silenceBytes() = chunks.filter { !it.isRealBuffer }.sumOf { it.bytes }

        fun realBytes() = chunks.filter { it.isRealBuffer }.sumOf { it.bytes }

        override fun setListener(listener: AudioSink.Listener) = Unit
        override fun supportsFormat(format: Format) = true
        override fun getFormatSupport(format: Format) = AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        override fun getCurrentPositionUs(sourceEnded: Boolean) = 0L
        override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) = Unit
        override fun play() { playing = true }
        override fun handleDiscontinuity() = Unit
        override fun playToEndOfStream() = Unit
        override fun isEnded() = false
        override fun hasPendingData() = false
        override fun setPlaybackParameters(playbackParameters: PlaybackParameters) = Unit
        override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters.DEFAULT
        override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) = Unit
        override fun getSkipSilenceEnabled() = false
        override fun setAudioAttributes(audioAttributes: AudioAttributes) = Unit
        override fun getAudioAttributes(): AudioAttributes? = null
        override fun setAudioSessionId(audioSessionId: Int) = Unit
        override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) = Unit
        override fun getAudioTrackBufferSizeUs() = C.TIME_UNSET
        override fun enableTunnelingV21() = Unit
        override fun disableTunneling() = Unit
        override fun setVolume(volume: Float) = Unit
        override fun pause() { playing = false }
        override fun flush() { flushCount++ }
        override fun reset() = Unit
    }

    private class Harness {
        var nowNanos = 0L
        val fake = FakeAudioSink()
        val sink = ScheduledAudioSink(fake) { nowNanos }

        fun configurePcm(format: Format) = sink.configure(format, 0, null)

        fun realBuffer(bytes: Int = 4_096): ByteBuffer {
            val buffer = ByteBuffer.allocate(bytes)
            fake.realBuffer = buffer
            return buffer
        }
    }

    @Test
    fun `passthrough when not scheduled`() {
        val h = Harness()
        h.configurePcm(pcmFormat)
        val real = h.realBuffer()

        assertTrue(h.sink.handleBuffer(real, 0, 1))
        assertEquals(0, h.fake.silenceBytes())
        assertEquals(4_096, h.fake.realBytes())
    }

    @Test
    fun `waiting phase rejects real pcm and prefeeds bounded silence`() {
        val h = Harness()
        h.configurePcm(pcmFormat)
        h.sink.scheduleStartAt(executeAtElapsedMs = 300)
        val real = h.realBuffer()

        assertFalse(h.sink.handleBuffer(real, 0, 1))
        assertFalse(h.sink.handleBuffer(real, 0, 1))

        assertEquals(0, h.fake.realBytes())
        // 预灌上限 40ms：48000 * 0.04 * 4 字节
        assertEquals(7_680, h.fake.silenceBytes())
        assertTrue(h.sink.isScheduled)
    }

    @Test
    fun `injects silence matching remaining delay after play`() {
        val h = Harness()
        h.configurePcm(pcmFormat)
        h.sink.scheduleStartAt(executeAtElapsedMs = 300)
        h.sink.play()
        val real = h.realBuffer()

        assertTrue(h.sink.handleBuffer(real, 0, 1))
        // 300ms * 48000 帧 * 4 字节静音先行，随后真实 PCM
        assertEquals(57_600, h.fake.silenceBytes())
        assertEquals(4_096, h.fake.realBytes())
        assertFalse(h.sink.isScheduled)
        assertTrue(h.fake.chunks.last().isRealBuffer)
    }

    @Test
    fun `prefed frames are deducted from injected silence`() {
        val h = Harness()
        h.configurePcm(pcmFormat)
        h.sink.scheduleStartAt(executeAtElapsedMs = 300)
        val real = h.realBuffer()

        assertFalse(h.sink.handleBuffer(real, 0, 1)) // 预灌 40ms
        h.sink.play()
        assertTrue(h.sink.handleBuffer(real, 0, 1))

        // 总前置静音仍为 300ms：预灌 7680 + 注入 49920
        assertEquals(57_600, h.fake.silenceBytes())
        assertEquals(4_096, h.fake.realBytes())
    }

    @Test
    fun `late target passes real pcm through immediately`() {
        val h = Harness()
        h.configurePcm(pcmFormat)
        h.sink.scheduleStartAt(executeAtElapsedMs = 100)
        h.nowNanos = 150_000_000 // 已迟到 50ms
        h.sink.play()
        val real = h.realBuffer()

        assertTrue(h.sink.handleBuffer(real, 0, 1))
        assertEquals(0, h.fake.silenceBytes())
        assertEquals(4_096, h.fake.realBytes())
        assertFalse(h.sink.isScheduled)
    }

    @Test
    fun `flush keeps schedule but resets prefeed accounting`() {
        val h = Harness()
        h.configurePcm(pcmFormat)
        h.sink.scheduleStartAt(executeAtElapsedMs = 300)
        val real = h.realBuffer()
        assertFalse(h.sink.handleBuffer(real, 0, 1)) // 预灌 40ms

        h.sink.flush()
        assertTrue(h.sink.isScheduled)
        h.fake.chunks.clear()

        h.sink.play()
        assertTrue(h.sink.handleBuffer(real, 0, 1))
        // 预灌记账已清零，注入完整 300ms
        assertEquals(57_600, h.fake.silenceBytes())
    }

    @Test
    fun `reset clears schedule`() {
        val h = Harness()
        h.configurePcm(pcmFormat)
        h.sink.scheduleStartAt(executeAtElapsedMs = 300)
        h.sink.reset()
        assertFalse(h.sink.isScheduled)

        val real = h.realBuffer()
        assertTrue(h.sink.handleBuffer(real, 0, 1))
        assertEquals(0, h.fake.silenceBytes())
    }

    @Test
    fun `backpressure retains pending silence across calls`() {
        val h = Harness()
        h.configurePcm(pcmFormat)
        h.fake.maxBytesPerWrite = 16_000
        h.sink.scheduleStartAt(executeAtElapsedMs = 300)
        h.sink.play()
        val real = h.realBuffer()

        // 57600 字节静音按 16000/次排水：前三次都未完成
        assertFalse(h.sink.handleBuffer(real, 0, 1))
        assertFalse(h.sink.handleBuffer(real, 0, 1))
        assertFalse(h.sink.handleBuffer(real, 0, 1))
        assertEquals(48_000, h.fake.silenceBytes())
        assertEquals(0, h.fake.realBytes())

        h.fake.maxBytesPerWrite = Int.MAX_VALUE
        assertTrue(h.sink.handleBuffer(real, 0, 1))
        assertEquals(57_600, h.fake.silenceBytes())
        assertEquals(4_096, h.fake.realBytes())
    }

    @Test
    fun `stale schedule without play falls back to passthrough`() {
        val h = Harness()
        h.configurePcm(pcmFormat)
        h.sink.scheduleStartAt(executeAtElapsedMs = 100)
        h.nowNanos = 2_200_000_000 // 目标 +2s 宽限已过，play 一直未到
        val real = h.realBuffer()

        assertTrue(h.sink.handleBuffer(real, 0, 1))
        assertEquals(4_096, h.fake.realBytes())
        assertFalse(h.sink.isScheduled)
    }

    @Test
    fun `hold rejects real pcm without staleness until rescheduled`() {
        val h = Harness()
        h.configurePcm(pcmFormat)
        h.sink.scheduleHold()
        h.nowNanos = 10_000_000_000 // 远超失效宽限，扣留不受影响
        val real = h.realBuffer()

        assertFalse(h.sink.handleBuffer(real, 0, 1))
        assertEquals(7_680, h.fake.silenceBytes()) // 预灌保活 40ms
        assertTrue(h.sink.isScheduled)

        h.sink.scheduleStartAt(executeAtElapsedMs = 10_300)
        h.sink.play()
        assertTrue(h.sink.handleBuffer(real, 0, 1))
        // 预灌 1920 帧计入折算：注入 300ms - 40ms
        assertEquals(57_600, h.fake.silenceBytes())
        assertEquals(4_096, h.fake.realBytes())
    }

    @Test
    fun `hold released by play without schedule passes through`() {
        val h = Harness()
        h.configurePcm(pcmFormat)
        h.sink.scheduleHold()
        h.sink.play()
        val real = h.realBuffer()

        assertTrue(h.sink.handleBuffer(real, 0, 1))
        assertEquals(0, h.fake.silenceBytes())
        assertEquals(4_096, h.fake.realBytes())
        assertFalse(h.sink.isScheduled)
    }

    @Test
    fun `non raw input disarms and passes through`() {
        val h = Harness()
        val encoded = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()
        h.sink.configure(encoded, 0, null)
        h.sink.scheduleStartAt(executeAtElapsedMs = 300)
        h.sink.play()
        val real = h.realBuffer()

        assertTrue(h.sink.handleBuffer(real, 0, 1))
        assertEquals(0, h.fake.silenceBytes())
        assertFalse(h.sink.isScheduled)
    }
}
