package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.controller.MediaFileRoutes
import com.coooolfan.unirhy.sync.protocol.PlaybackStatus
import com.coooolfan.unirhy.sync.protocol.ScheduledActionType
import com.coooolfan.unirhy.sync.support.TestPlaybackSyncTimeProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackSessionServiceTest {
    private lateinit var timeProvider: TestPlaybackSyncTimeProvider
    private lateinit var service: PlaybackSessionService

    @BeforeEach
    fun setUp() {
        timeProvider = TestPlaybackSyncTimeProvider(1_000L)
        service = PlaybackSessionService(
            lockManager = PlaybackAccountLockManager(),
            timeProvider = timeProvider,
        )
    }

    @Test
    fun `complete pending play updates authority state and increments version`() {
        service.createPendingPlay(
            accountId = 42L,
            commandId = "cmd-play-001",
            initiatorDeviceId = "web-a",
            recordingId = 1001L,
            mediaFileId = 2001L,
            sourceUrl = MediaFileRoutes.mediaFilePath(2001L),
            positionSeconds = 12.5,
            nowMs = 1_000L,
            timeoutAtMs = 4_000L,
        )

        val scheduledAction = service.completePendingPlay(
            accountId = 42L,
            commandId = "cmd-play-001",
            nowMs = 1_500L,
            executeAtMs = 1_900L,
        )

        assertEquals(ScheduledActionType.PLAY, scheduledAction?.scheduledAction?.action)
        assertEquals(1L, scheduledAction?.scheduledAction?.version)
        assertEquals(12.5, scheduledAction?.scheduledAction?.positionSeconds)

        val state = service.getOrCreateState(42L)
        assertEquals(PlaybackStatus.PLAYING, state.status)
        assertEquals(1001L, state.recordingId)
        assertEquals(2001L, state.mediaFileId)
        assertEquals(12.5, state.positionSeconds)
        assertEquals(1_900L, state.serverTimeToExecuteMs)
        assertEquals(1L, state.version)
        assertNull(service.getPendingPlay(42L))
    }

    @Test
    fun `build sync action for playing state recalculates live position`() {
        service.createPendingPlay(
            accountId = 42L,
            commandId = "cmd-play-001",
            initiatorDeviceId = "web-a",
            recordingId = 1001L,
            mediaFileId = 2001L,
            sourceUrl = MediaFileRoutes.mediaFilePath(2001L),
            positionSeconds = 12.5,
            nowMs = 1_000L,
            timeoutAtMs = 4_000L,
        )
        service.completePendingPlay(
            accountId = 42L,
            commandId = "cmd-play-001",
            nowMs = 1_500L,
            executeAtMs = 2_000L,
        )
        timeProvider.setNowMs(3_500L)

        val scheduledAction = service.buildSyncAction(
            accountId = 42L,
            commandId = "sync-web-a-3500",
            nowMs = 3_500L,
            executeAtMs = 5_000L,
        )

        assertEquals(ScheduledActionType.PLAY, scheduledAction.scheduledAction.action)
        assertEquals(PlaybackStatus.PLAYING, scheduledAction.scheduledAction.status)
        assertEquals(14.0, scheduledAction.scheduledAction.positionSeconds)
        assertEquals(1L, scheduledAction.scheduledAction.version)
    }

    @Test
    fun `handle device disconnected completes pending play when remaining devices are loaded`() {
        service.createPendingPlay(
            accountId = 42L,
            commandId = "cmd-play-001",
            initiatorDeviceId = "web-a",
            recordingId = 1001L,
            mediaFileId = 2001L,
            sourceUrl = MediaFileRoutes.mediaFilePath(2001L),
            positionSeconds = 12.5,
            nowMs = 1_000L,
            timeoutAtMs = 4_000L,
        )
        service.markAudioSourceLoaded(
            accountId = 42L,
            commandId = "cmd-play-001",
            deviceId = "web-b",
            recordingId = 1001L,
            mediaFileId = 2001L,
        )

        val scheduledAction = service.handleDeviceDisconnected(
            accountId = 42L,
            deviceId = "web-a",
            remainingDeviceIds = listOf("web-b"),
            nowMs = 1_400L,
            executeAtMs = 1_800L,
        )

        assertEquals(ScheduledActionType.PLAY, scheduledAction?.scheduledAction?.action)
        assertNull(service.getPendingPlay(42L))
    }

    @Test
    fun `handle device disconnected clears pending play when no devices remain`() {
        service.createPendingPlay(
            accountId = 42L,
            commandId = "cmd-play-001",
            initiatorDeviceId = "web-a",
            recordingId = 1001L,
            mediaFileId = 2001L,
            sourceUrl = MediaFileRoutes.mediaFilePath(2001L),
            positionSeconds = 12.5,
            nowMs = 1_000L,
            timeoutAtMs = 4_000L,
        )

        val scheduledAction = service.handleDeviceDisconnected(
            accountId = 42L,
            deviceId = "web-a",
            remainingDeviceIds = emptyList(),
            nowMs = 1_400L,
            executeAtMs = 1_800L,
        )

        assertNull(scheduledAction)
        assertNull(service.getPendingPlay(42L))
    }

    @Test
    fun `schedule seek preserves paused status`() {
        service.schedulePause(
            accountId = 42L,
            commandId = "cmd-pause-001",
            recordingId = 1001L,
            mediaFileId = 2001L,
            sourceUrl = MediaFileRoutes.mediaFilePath(2001L),
            positionSeconds = 12.5,
            nowMs = 1_100L,
            executeAtMs = 1_500L,
        )

        val scheduledAction = service.scheduleSeek(
            accountId = 42L,
            commandId = "cmd-seek-001",
            recordingId = 1001L,
            mediaFileId = 2001L,
            sourceUrl = MediaFileRoutes.mediaFilePath(2001L),
            positionSeconds = 20.0,
            nowMs = 1_200L,
            executeAtMs = 1_600L,
        )

        assertEquals(ScheduledActionType.SEEK, scheduledAction.scheduledAction.action)
        assertEquals(PlaybackStatus.PAUSED, scheduledAction.scheduledAction.status)
        assertEquals(PlaybackStatus.PAUSED, service.getOrCreateState(42L).status)
    }
}
