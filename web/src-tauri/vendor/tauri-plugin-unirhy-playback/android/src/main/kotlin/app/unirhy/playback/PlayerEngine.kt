package app.unirhy.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import okhttp3.OkHttpClient

/**
 * ExoPlayer 封装：媒体流经 OkHttpDataSource 注入 `unirhy-token` 头，
 * 支持"预滚暂停在目标位置 → 按单调钟定时起播"的调度语义。
 *
 * 所有播放器操作在主线程执行（ExoPlayer 线程约束），外部线程调用会被投递。
 * 定时起播走采样级路径（docs/ANDROID_SAMPLE_ACCURATE_PLAYBACK.md）：调度方先
 * [armScheduledStart] 排期，[playAt] 立即 play，可听起播点由 [ScheduledAudioSink]
 * 的静音注入落在 AudioTrack 帧钟上；未排期或非 PCM 输入时退化为 postDelayed 兜底。
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

        /** 首个真实样本被硬件播出的近似墙钟时刻（播放线程回调，接收方自行换线程）。 */
        fun onPlayoutStarted(playoutStartSystemTimeMs: Long)
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

    @Volatile
    private var scheduledAudioSink: ScheduledAudioSink? = null

    val player: ExoPlayer = run {
        val dataSourceFactory = OkHttpDataSource.Factory { request ->
            val builder = request.newBuilder()
            tokenProvider()?.let { builder.header("unirhy-token", it) }
            okHttpClient.newCall(builder.build())
        }
        // 采样级调度要求 PCM 通路：不启用 offload/tunneling（本工厂也从不开启）
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink {
                return ScheduledAudioSink(
                    DefaultAudioSink.Builder(context)
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .build(),
                ).also { scheduledAudioSink = it }
            }
        }
        ExoPlayer.Builder(context, renderersFactory)
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
        scheduledAudioSink?.diagnosticsListener = ScheduledAudioSink.DiagnosticsListener { playoutStartSystemTimeMs ->
            Log.i(TAG, "playout advancing at=$playoutStartSystemTimeMs")
            listener.onPlayoutStarted(playoutStartSystemTimeMs)
        }
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.i(TAG, "player isPlaying=$isPlaying pos=${player.currentPosition}ms state=${player.playbackState}")
                listener.onIsPlayingChanged(isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.i(TAG, "player state=$playbackState pos=${player.currentPosition}ms")
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
                Log.i(TAG, "player playWhenReady=$playWhenReady reason=$reason pos=${player.currentPosition}ms")
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
                    listener.onSystemInducedPause(player.currentPosition / 1_000.0)
                }
            }

            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                Log.i(TAG, "player suppressionReason=$playbackSuppressionReason pos=${player.currentPosition}ms")
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.w(TAG, "player error: ${error.errorCodeName} ${error.message}")
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
     * AUDIO_SOURCE_LOADED）。相同 mediaFileId 已加载（READY 或 seek 引发的瞬时 BUFFERING）
     * 时只重新定位、不重建媒体源——LOAD 与紧随的 PLAY 调度会连续两次 preload，
     * 若第二次因 BUFFERING 走完整 prepare 会耗尽调度窗口，导致采样级起播必然迟到。
     */
    fun preload(
        url: String,
        source: LoadedSource,
        positionSeconds: Double,
        metadata: MediaMetadata?,
        onReady: () -> Unit,
    ) = runOnPlayerThread {
        cancelPendingStart()
        val reusableState = player.playbackState == Player.STATE_READY ||
            player.playbackState == Player.STATE_BUFFERING
        if (loadedSource?.mediaFileId == source.mediaFileId && reusableState) {
            player.pause()
            val targetMs = (positionSeconds * 1_000).toLong()
            // 同位置不重复 seek：seek 触发的 flush 会重启解码链并销毁 AudioTrack，
            // 使采样级起播的注入窗口被解码重启耗尽
            if (kotlin.math.abs(player.currentPosition - targetMs) > SEEK_EPSILON_MS) {
                player.seekTo(targetMs)
            }
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
     * 排期采样级起播：须在预滚 seek 之前调用（seek 触发的 sink flush 保留排期），
     * 之后 [playAt] 会立即 play，让静音注入落点决定可听起播时刻。
     */
    fun armScheduledStart(executeAtElapsedMs: Long) {
        scheduledAudioSink?.scheduleStartAt(executeAtElapsedMs)
    }

    /** 回填实测出声偏差（服务端时钟轴，正值=偏晚），驱动 sink 的延迟补偿收敛。 */
    fun reportPlayoutDrift(driftMs: Long) {
        scheduledAudioSink?.onPlayoutMeasured(driftMs)
    }

    /** 暂停驻留扣留：详见 [ScheduledAudioSink.scheduleHold]。 */
    fun armHold() {
        scheduledAudioSink?.scheduleHold()
    }

    /**
     * 定时起播：sink 已排期时立即 play（起播时刻由静音注入决定）；
     * 未排期（快照恢复 / 独立模式 / 非 PCM 退化）时 postDelayed 到目标时刻兜底。
     * 已迟到（目标时刻已过）时立即起播，由调用方预先补偿位置。
     */
    fun playAt(executeAtElapsedMs: Long) = runOnPlayerThread {
        val sink = scheduledAudioSink
        Log.i(
            TAG,
            "playAt in=${executeAtElapsedMs - SystemClock.elapsedRealtime()}ms " +
                "pos=${player.currentPosition}ms scheduled=${sink?.isScheduled == true}",
        )
        cancelPendingStart()
        if (sink != null && sink.isScheduled) {
            player.play()
            return@runOnPlayerThread
        }
        val start = Runnable {
            pendingStart = null
            player.play()
        }
        pendingStart = start
        val delayMs = executeAtElapsedMs - SystemClock.elapsedRealtime()
        if (delayMs <= 0) {
            start.run()
        } else {
            handler.postDelayed(start, delayMs)
        }
    }

    fun pauseAt(executeAtElapsedMs: Long, positionSeconds: Double) = runOnPlayerThread {
        Log.i(TAG, "pauseAt in=${executeAtElapsedMs - SystemClock.elapsedRealtime()}ms target=${positionSeconds}s")
        cancelPendingStart()
        scheduledAudioSink?.cancelScheduledStart()
        val delayMs = executeAtElapsedMs - SystemClock.elapsedRealtime()
        val action = Runnable {
            pendingStart = null
            // 扣留先于 seek-flush 生效：暂停驻留期间 sink 不积压真实 PCM，
            // 恢复 PLAY 时注入窗口完整
            scheduledAudioSink?.scheduleHold()
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
            scheduledAudioSink?.cancelScheduledStart()
            val delayMs = executeAtElapsedMs - SystemClock.elapsedRealtime()
            val action = Runnable {
                pendingStart = null
                if (!resumePlaying) {
                    scheduledAudioSink?.scheduleHold()
                    player.pause()
                }
                player.seekTo((positionSeconds * 1_000).toLong())
                if (resumePlaying) {
                    player.play()
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
        scheduledAudioSink?.cancelScheduledStart()
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
        private const val TAG = "UnirhyPlayerEngine"
        private const val POSITION_POLL_MS = 250L
        private const val SEEK_EPSILON_MS = 100L
    }
}
