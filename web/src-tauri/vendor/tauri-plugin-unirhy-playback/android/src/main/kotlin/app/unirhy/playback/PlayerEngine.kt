package app.unirhy.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import okhttp3.OkHttpClient

/**
 * ExoPlayer 封装：媒体流经 OkHttpDataSource 注入 `unirhy-token` 头，
 * 支持"预滚暂停在目标位置 → 按单调钟定时起播"的调度语义。
 *
 * 所有播放器操作在主线程执行（ExoPlayer 线程约束），外部线程调用会被投递。
 * 定时起播：postDelayed 唤醒在目标前约 50ms，再短自旋对齐到目标 elapsedRealtime（±5ms 内）；
 * 起播 +500ms 做一次性位置校正，偏差 >150ms 时 seek 修正。速度微调收敛环不在本期范围
 * （升级路径见 docs/PLAYBACK_BACKGROUND_PLAN.md §4.3）。
 */
@UnstableApi
class PlayerEngine(
    context: Context,
    okHttpClient: OkHttpClient,
    tokenProvider: () -> String?,
    private val listener: Listener,
) {
    interface Listener {
        fun onIsPlayingChanged(isPlaying: Boolean)

        fun onDurationKnown(durationSeconds: Double)

        fun onPlaybackEnded()

        /** 系统侧强制暂停（音频焦点丢失 / 拔出耳机等），需要回流为同步 PAUSE。 */
        fun onSystemInducedPause(positionSeconds: Double)

        fun onPlayerError(message: String)
    }

    data class LoadedSource(
        val mediaFileId: Long,
        val recordingId: Long,
    )

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    var loadedSource: LoadedSource? = null
        private set

    /** 主线程轮询回填的最近位置（秒），供协议线程构造控制命令 payload。 */
    @Volatile
    var currentPositionSeconds: Double = 0.0
        private set

    private var pendingStart: Runnable? = null
    private var pendingOnReady: (() -> Unit)? = null

    val player: ExoPlayer = run {
        val dataSourceFactory = OkHttpDataSource.Factory { request ->
            val builder = request.newBuilder()
            tokenProvider()?.let { builder.header("unirhy-token", it) }
            okHttpClient.newCall(builder.build())
        }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                listener.onIsPlayingChanged(isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        val duration = player.duration
                        if (duration != C.TIME_UNSET) {
                            listener.onDurationKnown(duration / 1_000.0)
                        }
                        pendingOnReady?.let {
                            pendingOnReady = null
                            it()
                        }
                    }
                    Player.STATE_ENDED -> listener.onPlaybackEnded()
                    else -> Unit
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
                    listener.onSystemInducedPause(player.currentPosition / 1_000.0)
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                listener.onPlayerError(error.message ?: error.errorCodeName)
            }
        })
        handler.post(object : Runnable {
            override fun run() {
                currentPositionSeconds = player.currentPosition / 1_000.0
                handler.postDelayed(this, POSITION_POLL_MS)
            }
        })
    }

    fun runOnPlayerThread(action: () -> Unit) {
        if (Looper.myLooper() == handler.looper) {
            action()
        } else {
            handler.post(action)
        }
    }

    /**
     * 预滚：加载音源并暂停定位到 positionSeconds，STATE_READY 后回调 onReady（用于回报
     * AUDIO_SOURCE_LOADED）。相同 mediaFileId 已加载时直接定位并立即回调。
     */
    fun preload(
        url: String,
        source: LoadedSource,
        positionSeconds: Double,
        metadata: MediaMetadata?,
        onReady: () -> Unit,
    ) = runOnPlayerThread {
        cancelPendingStart()
        if (loadedSource?.mediaFileId == source.mediaFileId && player.playbackState == Player.STATE_READY) {
            player.pause()
            player.seekTo((positionSeconds * 1_000).toLong())
            onReady()
            return@runOnPlayerThread
        }
        loadedSource = source
        pendingOnReady = onReady
        val itemBuilder = MediaItem.Builder().setUri(Uri.parse(url))
        metadata?.let { itemBuilder.setMediaMetadata(it) }
        player.playWhenReady = false
        player.setMediaItem(itemBuilder.build(), (positionSeconds * 1_000).toLong())
        player.prepare()
    }

    /**
     * 定时起播：在单调钟到达 executeAtElapsedMs 时从当前预滚位置开始播放。
     * 已迟到（目标时刻已过）时立即起播，由调用方预先补偿位置。
     */
    fun playAt(executeAtElapsedMs: Long) = runOnPlayerThread {
        cancelPendingStart()
        val start = Runnable {
            pendingStart = null
            // 短自旋对齐目标时刻，postDelayed 唤醒误差通常在 ±10ms 级
            while (SystemClock.elapsedRealtime() < executeAtElapsedMs) {
                // 自旋窗口 ≤ 约 50ms
            }
            player.play()
            schedulePositionCorrection()
        }
        pendingStart = start
        val delayMs = executeAtElapsedMs - SystemClock.elapsedRealtime() - SPIN_WINDOW_MS
        if (delayMs <= 0) {
            start.run()
        } else {
            handler.postDelayed(start, delayMs)
        }
    }

    /** 起播后一次性位置校正：预期位置以起播时刻的实际位置为基准推算。 */
    private fun schedulePositionCorrection() {
        val startedAtElapsedMs = SystemClock.elapsedRealtime()
        val startedPositionMs = player.currentPosition
        handler.postDelayed(
            {
                if (!player.isPlaying) {
                    return@postDelayed
                }
                val expectedMs = startedPositionMs + (SystemClock.elapsedRealtime() - startedAtElapsedMs)
                val driftMs = player.currentPosition - expectedMs
                if (kotlin.math.abs(driftMs) > CORRECTION_THRESHOLD_MS) {
                    player.seekTo(expectedMs + (player.currentPosition - expectedMs) / 2)
                }
            },
            CORRECTION_DELAY_MS,
        )
    }

    fun pauseAt(executeAtElapsedMs: Long, positionSeconds: Double) = runOnPlayerThread {
        cancelPendingStart()
        val delayMs = executeAtElapsedMs - SystemClock.elapsedRealtime()
        val action = Runnable {
            pendingStart = null
            player.pause()
            player.seekTo((positionSeconds * 1_000).toLong())
        }
        pendingStart = action
        if (delayMs <= 0) {
            action.run()
        } else {
            handler.postDelayed(action, delayMs)
        }
    }

    fun seekAt(executeAtElapsedMs: Long, positionSeconds: Double, resumePlaying: Boolean) =
        runOnPlayerThread {
            cancelPendingStart()
            val delayMs = executeAtElapsedMs - SystemClock.elapsedRealtime()
            val action = Runnable {
                pendingStart = null
                player.seekTo((positionSeconds * 1_000).toLong())
                if (resumePlaying) {
                    player.play()
                } else {
                    player.pause()
                }
            }
            pendingStart = action
            if (delayMs <= 0) {
                action.run()
            } else {
                handler.postDelayed(action, delayMs)
            }
        }

    fun stop() = runOnPlayerThread {
        cancelPendingStart()
        loadedSource = null
        player.stop()
        player.clearMediaItems()
    }

    fun setVolume(volume: Double) = runOnPlayerThread {
        player.volume = volume.coerceIn(0.0, 1.0).toFloat()
    }

    fun release() = runOnPlayerThread {
        cancelPendingStart()
        player.release()
    }

    private fun cancelPendingStart() {
        pendingStart?.let(handler::removeCallbacks)
        pendingStart = null
        pendingOnReady = null
    }

    companion object {
        private const val SPIN_WINDOW_MS = 50L
        private const val CORRECTION_DELAY_MS = 500L
        private const val CORRECTION_THRESHOLD_MS = 150L
        private const val POSITION_POLL_MS = 250L
    }
}
