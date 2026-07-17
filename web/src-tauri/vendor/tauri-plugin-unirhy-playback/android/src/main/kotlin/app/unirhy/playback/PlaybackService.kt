package app.unirhy.playback

import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * 播放前台服务（mediaPlayback FGS 本体）。
 *
 * MediaSessionService 自动管理前台升降级与系统媒体通知（通知栏/锁屏/耳机/车机控件）。
 * 系统控件命令经 [CommandRoutingPlayer] 回流：同步模式下 play/pause/seek 直发 WS 控制命令、
 * next/prev 直调队列 HTTP 端点（不经 WebView，后台节流下依然可靠），播放器自身不直接响应。
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val engine = PlaybackController.ensurePlayerEngine(applicationContext)
        mediaSession = MediaSession.Builder(this, CommandRoutingPlayer(engine.player)).build().also(::addSession)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用户从最近任务明确划掉 App：结束播放、同步连接与媒体服务。
        // 部分系统会直接 force-stop 整包；此处为不会自动杀进程的实现补齐相同语义。
        PlaybackController.disconnectSync()
    }

    override fun onDestroy() {
        mediaSession?.let {
            removeSession(it)
            it.release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /**
     * 命令路由播放器：拦截系统媒体控件的用户意图并转交 PlaybackController，
     * 实际播放变更由服务端调度（SCHEDULED_ACTION）或独立模式本地逻辑驱动。
     */
    private class CommandRoutingPlayer(player: Player) : ForwardingPlayer(player) {
        override fun play() {
            PlaybackController.onUserPlay(null)
        }

        override fun pause() {
            PlaybackController.onUserPause()
        }

        override fun seekTo(positionMs: Long) {
            PlaybackController.onUserSeek(positionMs / 1_000.0)
        }

        override fun seekToNext() {
            PlaybackController.onUserNext()
        }

        override fun seekToNextMediaItem() {
            PlaybackController.onUserNext()
        }

        override fun seekToPrevious() {
            PlaybackController.onUserPrevious()
        }

        override fun seekToPreviousMediaItem() {
            PlaybackController.onUserPrevious()
        }

        override fun getAvailableCommands(): Player.Commands {
            return super.getAvailableCommands()
                .buildUpon()
                .addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                )
                .build()
        }

        override fun isCommandAvailable(command: Int): Boolean {
            return availableCommands.contains(command)
        }
    }
}
