package app.tauri.backgroundservice

import android.app.Service
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UniRhyLifecyclePatchTest {
    @Test
    fun restartDisabledUsesNotStickyAndDoesNotPersistRecoveryIntent() {
        val state = LifecycleService.buildStartState(
            label = "Playback",
            serviceType = "mediaPlayback",
            previous = DurableState(),
            restartOnProcessDeath = false,
        )

        assertEquals(Service.START_NOT_STICKY, LifecycleService.startMode(false))
        assertFalse(state.desiredRunning)
    }

    @Test
    fun restartEnabledUsesStickyAndPersistsRecoveryIntent() {
        val state = LifecycleService.buildStartState(
            label = "Playback",
            serviceType = "mediaPlayback",
            previous = DurableState(),
            restartOnProcessDeath = true,
        )

        assertEquals(Service.START_STICKY, LifecycleService.startMode(true))
        assertTrue(state.desiredRunning)
    }

    @Test
    fun applicationStopDoesNotEmitNotificationStopEvent() {
        assertNull(LifecycleService.nativeStopEventType(LifecycleService.ACTION_APP_STOP))
    }

    @Test
    fun notificationStopEmitsNativeLifecycleEvent() {
        assertEquals(
            "androidNotificationStop",
            LifecycleService.nativeStopEventType(LifecycleService.ACTION_NOTIFICATION_STOP),
        )
    }
}
