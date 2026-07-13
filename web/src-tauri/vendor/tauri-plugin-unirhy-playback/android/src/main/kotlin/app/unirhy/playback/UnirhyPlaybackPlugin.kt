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
        state.put("isPlaying", false)
        state.put("currentIndex", null)
        state.put("positionSeconds", 0.0)
        state.put("durationSeconds", 0.0)
        state.put("isLoading", false)
        state.put("syncPhase", PlaybackController.syncPhase.name.lowercase())
        state.put("clockOffsetMs", PlaybackController.clock.clockOffsetMs)
        state.put("roundTripEstimateMs", PlaybackController.clock.roundTripEstimateMs)
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
        invoke.parseArgs(SetVolumeArgs::class.java)
        invoke.resolve()
    }

    @Command
    fun requestPlay(invoke: Invoke) {
        invoke.parseArgs(RequestPlayArgs::class.java)
        invoke.reject("not implemented")
    }

    @Command
    fun requestPause(invoke: Invoke) {
        invoke.reject("not implemented")
    }

    @Command
    fun requestSeek(invoke: Invoke) {
        invoke.parseArgs(RequestSeekArgs::class.java)
        invoke.reject("not implemented")
    }

    @Command
    fun requestSyncRecovery(invoke: Invoke) {
        PlaybackController.requestSyncRecovery()
        invoke.resolve()
    }

    @Command
    fun localSetQueue(invoke: Invoke) {
        invoke.parseArgs(LocalSetQueueArgs::class.java)
        invoke.reject("not implemented")
    }

    @Command
    fun localPlay(invoke: Invoke) {
        invoke.parseArgs(LocalPlayArgs::class.java)
        invoke.reject("not implemented")
    }

    @Command
    fun localPause(invoke: Invoke) {
        invoke.reject("not implemented")
    }

    @Command
    fun localSeek(invoke: Invoke) {
        invoke.parseArgs(LocalSeekArgs::class.java)
        invoke.reject("not implemented")
    }
}
