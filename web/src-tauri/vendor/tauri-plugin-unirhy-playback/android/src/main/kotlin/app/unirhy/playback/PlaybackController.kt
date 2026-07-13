package app.unirhy.playback

import android.os.SystemClock
import app.unirhy.playback.queue.QueueState
import app.unirhy.playback.sync.NtpClock
import app.unirhy.playback.sync.PlaybackSyncJson
import app.unirhy.playback.sync.ServerMessage
import app.unirhy.playback.sync.SyncProtocolClient
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import okhttp3.OkHttpClient

/**
 * 原生播放内核的进程级单例：持有同步协议客户端、校时器与权威队列态。
 * 插件（UI 桥）与前台服务共享此实例，事件经 [eventSink] 回传 WebView。
 *
 * 事件面（type 字段）：sync-state / queue-changed / state-changed / position / auth-required，
 * 均携带单调递增 seq，供 TS 侧丢弃乱序的过期事件。
 */
object PlaybackController {
    data class SessionConfig(
        val apiBaseUrl: String,
        val token: String?,
        val deviceId: String,
        val clientVersion: String,
        val mode: String,
    )

    /** 事件出口：参数为事件 JSON 字符串（含 type 与 seq）。 */
    @Volatile
    var eventSink: ((String) -> Unit)? = null

    @Volatile
    var sessionConfig: SessionConfig? = null
        private set

    val queueState = QueueState()

    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "unirhy-playback").apply { isDaemon = true }
    }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    val clock = NtpClock(
        monotonicNowMs = { SystemClock.elapsedRealtime() },
        wallNowMs = { System.currentTimeMillis() },
    )

    private val eventSeq = AtomicLong(0)

    @Volatile
    private var syncClient: SyncProtocolClient? = null

    val syncPhase: SyncProtocolClient.Phase
        get() = syncClient?.phase ?: SyncProtocolClient.Phase.STOPPED

    fun configure(config: SessionConfig) {
        sessionConfig = config
        syncClient?.updateToken(config.token)
    }

    fun updateToken(token: String?) {
        sessionConfig = sessionConfig?.copy(token = token)
        syncClient?.updateToken(token)
    }

    fun connectSync(): Boolean {
        val config = sessionConfig ?: return false
        val client = syncClient ?: createSyncClient().also { syncClient = it }
        client.connect(
            SyncProtocolClient.Config(
                apiBaseUrl = config.apiBaseUrl,
                token = config.token,
                deviceId = config.deviceId,
                clientVersion = config.clientVersion,
            ),
        )
        return true
    }

    fun disconnectSync() {
        syncClient?.disconnect()
        queueState.clear()
    }

    fun requestSyncRecovery(): Boolean {
        return syncClient?.requestSync() ?: false
    }

    private fun createSyncClient(): SyncProtocolClient {
        return SyncProtocolClient(
            okHttpClient = okHttpClient,
            executor = executor,
            clock = clock,
            listener = object : SyncProtocolClient.Listener {
                override fun onSyncStateChanged(
                    phase: SyncProtocolClient.Phase,
                    clockOffsetMs: Double,
                    roundTripEstimateMs: Double,
                ) {
                    emitEvent(
                        mapOf(
                            "type" to "sync-state",
                            "syncPhase" to phase.name.lowercase(),
                            "clockOffsetMs" to clockOffsetMs,
                            "roundTripEstimateMs" to roundTripEstimateMs,
                        ),
                    )
                }

                override fun onServerMessage(message: ServerMessage) {
                    handleServerMessage(message)
                }
            },
        )
    }

    private fun handleServerMessage(message: ServerMessage) {
        when (message) {
            is ServerMessage.Snapshot -> {
                if (queueState.apply(message.payload.queue)) {
                    emitQueueChanged()
                }
            }
            is ServerMessage.QueueChange -> {
                if (queueState.apply(message.payload.queue)) {
                    emitQueueChanged()
                }
            }
            else -> {
                // LOAD_AUDIO_SOURCE / SCHEDULED_ACTION / ERROR 的播放执行分支由
                // PlaybackExecutor 接管（实施路线阶段 5）。
            }
        }
    }

    private fun emitQueueChanged() {
        emitEvent(
            mapOf(
                "type" to "queue-changed",
                "queue" to queueState.queue,
            ),
        )
    }

    fun emitEvent(fields: Map<String, Any?>) {
        val sink = eventSink ?: return
        val event = LinkedHashMap<String, Any?>(fields)
        event["seq"] = eventSeq.incrementAndGet()
        sink(PlaybackSyncJson.mapper.writeValueAsString(event))
    }
}
