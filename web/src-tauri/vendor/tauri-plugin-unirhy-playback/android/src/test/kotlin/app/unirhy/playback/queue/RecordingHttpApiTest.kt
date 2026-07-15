package app.unirhy.playback.queue

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingHttpApiTest {

    @Test
    fun `prefers configured audio format and falls back to first audio source`() {
        val sources = listOf(
            RecordingAudioSource(2000L, "video/mp4"),
            RecordingAudioSource(2001L, "audio/flac"),
            RecordingAudioSource(2002L, " Audio/Opus "),
        )

        assertEquals(2002L, selectPreferredAudioMediaFileId(sources, " AUDIO/OPUS "))
        assertEquals(2001L, selectPreferredAudioMediaFileId(sources, "audio/aac"))
    }
}
