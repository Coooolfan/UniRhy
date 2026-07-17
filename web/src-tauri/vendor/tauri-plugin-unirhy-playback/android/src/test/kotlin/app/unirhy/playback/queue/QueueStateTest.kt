package app.unirhy.playback.queue

import app.unirhy.playback.sync.CurrentQueueDto
import app.unirhy.playback.sync.CurrentQueueItemDto
import app.unirhy.playback.sync.PlaybackStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueStateTest {

    private fun queue(version: Long) = CurrentQueueDto(
        items = listOf(
            CurrentQueueItemDto(
                recordingId = 1001,
                title = "Track",
                artistLabel = "Artist",
                coverUrl = null,
                durationMs = 180_000,
                mediaFileId = 2001,
            ),
        ),
        recordingIds = listOf(1001),
        currentIndex = 0,
        playbackStrategy = "SEQUENTIAL",
        stopStrategy = "LIST",
        playbackStatus = PlaybackStatus.PAUSED,
        positionMs = 0,
        serverTimeToExecuteMs = 0,
        version = version,
        updatedAtMs = 0,
    )

    @Test
    fun `applies queues with increasing versions only`() {
        val state = QueueState()
        assertTrue(state.apply(queue(version = 5)))
        assertFalse(state.apply(queue(version = 5)))
        assertFalse(state.apply(queue(version = 3)))
        assertTrue(state.apply(queue(version = 6)))
        assertEquals(6L, state.version())
    }

    @Test
    fun `resolves items by index`() {
        val state = QueueState()
        state.apply(queue(version = 1))
        assertEquals(2001L, state.itemAt(0)?.mediaFileId)
        assertNull(state.itemAt(1))
    }

    @Test
    fun `clear resets the queue`() {
        val state = QueueState()
        state.apply(queue(version = 1))
        state.clear()
        assertNull(state.version())
    }
}
