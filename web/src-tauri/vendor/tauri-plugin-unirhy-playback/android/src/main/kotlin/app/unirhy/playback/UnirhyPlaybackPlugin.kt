package app.unirhy.playback

import android.app.Activity
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin

@InvokeArg
class ConfigureArgs {
    var apiBaseUrl: String = ""
    var token: String? = null
    var deviceId: String = ""
    var clientVersion: String = ""
    var mode: String = "sync"
}

@InvokeArg
class UpdateAuthArgs {
    var token: String? = null
}

@InvokeArg
class SetVolumeArgs {
    var volume: Double = 1.0
}

@InvokeArg
class RequestPlayArgs {
    var positionSeconds: Double? = null
    var currentIndex: Int? = null
    var version: Long? = null
}

@InvokeArg
class RequestPauseArgs {
    var positionSeconds: Double? = null
}

@InvokeArg
class RequestSeekArgs {
    var positionSeconds: Double = 0.0
}

@InvokeArg
class LocalQueueItemArg {
    var recordingId: Long = 0
    var mediaFileId: Long = 0
    var title: String = ""
    var artistLabel: String = ""
    var coverUrl: String? = null
    var durationMs: Long = 0
}

@InvokeArg
class LocalSetQueueArgs {
    var items: List<LocalQueueItemArg> = emptyList()
    var currentIndex: Int = 0
}

@InvokeArg
class LocalPlayArgs {
    var currentIndex: Int = 0
    var positionSeconds: Double = 0.0
}

@InvokeArg
class LocalSeekArgs {
    var positionSeconds: Double = 0.0
}

/**
 * UniRhy 原生播放内核的 Tauri 插件入口。
 *
 * 命令面与事件面见 web/src/runtime/nativePlaybackBridge.ts；
 * 协议客户端、校时与队列态由进程级单例 PlaybackController 承载，本类仅做桥接。
 */
@TauriPlugin
class UnirhyPlaybackPlugin(private val activity: Activity) : Plugin(activity) {

    override fun load(webView: android.webkit.WebView) {
        super.load(webView)
        PlaybackController.attachContext(activity.applicationContext)
        PlaybackController.eventSink = { eventJson ->
            trigger("playback-event", JSObject(eventJson))
        }
    }

    override fun onDestroy() {
        PlaybackController.eventSink = null
        super.onDestroy()
    }

    @Command
    fun configure(invoke: Invoke) {
        val args = invoke.parseArgs(ConfigureArgs::class.java)
        if (args.apiBaseUrl.isBlank() || args.deviceId.isBlank()) {
            invoke.reject("apiBaseUrl and deviceId are required")
            return
        }
        PlaybackController.configure(
            PlaybackController.SessionConfig(
                apiBaseUrl = args.apiBaseUrl.trimEnd('/'),
                token = args.token?.takeIf { it.isNotBlank() },
                deviceId = args.deviceId,
                clientVersion = args.clientVersion,
                mode = args.mode,
            ),
        )
        invoke.resolve()
    }

    @Command
    fun updateAuth(invoke: Invoke) {
        val args = invoke.parseArgs(UpdateAuthArgs::class.java)
        PlaybackController.updateToken(args.token?.takeIf { it.isNotBlank() })
        invoke.resolve()
    }

    @Command
    fun connectSync(invoke: Invoke) {
        if (!PlaybackController.connectSync()) {
            invoke.reject("configure must be called before connectSync")
            return
        }
        invoke.resolve()
    }

    @Command
    fun disconnectSync(invoke: Invoke) {
        PlaybackController.disconnectSync()
        invoke.resolve()
    }

    @Command
    fun getPlaybackState(invoke: Invoke) {
        val config = PlaybackController.sessionConfig
        val queue = PlaybackController.queueState.queue
        val state = JSObject()
        state.put("configured", config != null)
        state.put("mode", config?.mode ?: "sync")
        state.put("isPlaying", PlaybackController.isPlaying)
        state.put("currentIndex", PlaybackController.currentIndex)
        state.put("positionSeconds", PlaybackController.currentPositionSeconds)
        state.put("durationSeconds", PlaybackController.durationSeconds)
        state.put("isLoading", PlaybackController.isLoading)
        state.put("error", PlaybackController.lastError)
        state.put("syncPhase", PlaybackController.syncPhase.name.lowercase())
        state.put("clockOffsetMs", PlaybackController.clock.clockOffsetMs)
        state.put("roundTripEstimateMs", PlaybackController.clock.roundTripEstimateMs)
        PlaybackController.syncDiagnostics()?.let { diagnostics ->
            state.put(
                "syncDiagnostics",
                JSObject(
                    app.unirhy.playback.sync.PlaybackSyncJson.mapper.writeValueAsString(diagnostics),
                ),
            )
        }
        if (queue != null) {
            state.put(
                "queue",
                JSObject(app.unirhy.playback.sync.PlaybackSyncJson.mapper.writeValueAsString(queue)),
            )
            state.put("queueVersion", queue.version)
        } else {
            state.put("queueVersion", null)
        }
        invoke.resolve(state)
    }

    @Command
    fun setVolume(invoke: Invoke) {
        val args = invoke.parseArgs(SetVolumeArgs::class.java)
        PlaybackController.setVolume(args.volume)
        invoke.resolve()
    }

    @Command
    fun requestPlay(invoke: Invoke) {
        val args = invoke.parseArgs(RequestPlayArgs::class.java)
        PlaybackController.onUserPlay(
            positionSeconds = args.positionSeconds,
            currentIndexOverride = args.currentIndex,
            versionOverride = args.version,
        )
        invoke.resolve()
    }

    @Command
    fun requestPause(invoke: Invoke) {
        val args = invoke.parseArgs(RequestPauseArgs::class.java)
        PlaybackController.onUserPause(positionOverride = args.positionSeconds)
        invoke.resolve()
    }

    @Command
    fun requestSeek(invoke: Invoke) {
        val args = invoke.parseArgs(RequestSeekArgs::class.java)
        PlaybackController.onUserSeek(args.positionSeconds)
        invoke.resolve()
    }

    @Command
    fun requestSyncRecovery(invoke: Invoke) {
        PlaybackController.requestSyncRecovery()
        invoke.resolve()
    }

    @Command
    fun localSetQueue(invoke: Invoke) {
        val args = invoke.parseArgs(LocalSetQueueArgs::class.java)
        PlaybackController.localSetQueue(
            items = args.items.map { item ->
                PlaybackController.LocalQueueItem(
                    recordingId = item.recordingId,
                    mediaFileId = item.mediaFileId,
                    title = item.title,
                    artistLabel = item.artistLabel,
                    coverUrl = item.coverUrl,
                    durationMs = item.durationMs,
                )
            },
            currentIndex = args.currentIndex,
        )
        invoke.resolve()
    }

    @Command
    fun localPlay(invoke: Invoke) {
        val args = invoke.parseArgs(LocalPlayArgs::class.java)
        PlaybackController.localPlay(args.currentIndex, args.positionSeconds)
        invoke.resolve()
    }

    @Command
    fun localPause(invoke: Invoke) {
        PlaybackController.localPause()
        invoke.resolve()
    }

    @Command
    fun localSeek(invoke: Invoke) {
        val args = invoke.parseArgs(LocalSeekArgs::class.java)
        PlaybackController.localSeek(args.positionSeconds)
        invoke.resolve()
    }
}
