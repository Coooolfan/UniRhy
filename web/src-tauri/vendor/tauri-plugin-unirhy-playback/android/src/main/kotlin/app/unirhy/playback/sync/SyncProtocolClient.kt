package app.unirhy.playback.sync

import android.util.Log
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.min
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * 播放同步协议的 Kotlin 客户端，状态机与校准/重连节奏复刻 TS 端
 * PlaybackSyncClient（playbackSyncClient.ts）：
 *
 * stopped → connecting →（收到 SNAPSHOT）→ calibrating →（20 采样 + 60ms settle）→ ready
 * close/失败 → reconnecting（1s/2s/5s 封顶）→ connecting → …
 *
 * 差异点：鉴权走握手 HTTP 头 `unirhy-token`（OkHttp 可自定义握手头，无需 HELLO 兜底）；
 * 协议层 Pong 由 OkHttp 自动回复，满足服务端 D1 活性检测。
 *
 * 线程模型：全部状态仅在单线程 [executor] 上变更，WS 回调与外部调用均投递进来。
 */
class SyncProtocolClient(
    private val okHttpClient: OkHttpClient,
    private val executor: ScheduledExecutorService,
    private val clock: NtpClock,
    private val listener: Listener,
) {
    data class Config(
        val apiBaseUrl: String,
        val token: String?,
        val deviceId: String,
        val clientVersion: String,
    )

    enum class Phase {
        STOPPED,
        CONNECTING,
        CALIBRATING,
        READY,
        RECONNECTING,
        ERROR,
    }

    interface Listener {
        fun onSyncStateChanged(phase: Phase, clockOffsetMs: Double, roundTripEstimateMs: Double)

        fun onServerMessage(message: ServerMessage)

        /** 非 NTP 协议消息的收发流水，仅用于诊断展示。 */
        fun onProtocolEvent(direction: String, type: String, payload: Any?, atMs: Long) {}
    }

    @Volatile
    var phase: Phase = Phase.STOPPED
        private set

    @Volatile
    var socketState: String = "idle"
        private set

    @Volatile
    var reconnectAttemptCount: Int = 0
        private set

    @Volatile
    var snapshotReceived: Boolean = false
        private set

    private var config: Config? = null
    private var webSocket: WebSocket? = null
    private var explicitStop = false
    private var reconnectAttempt = 0

    private var initialMeasurements = mutableListOf<NtpMeasurement>()
    private var initialSampleCount = 0

    private var reconnectFuture: ScheduledFuture<*>? = null
    private var initialSampleFuture: ScheduledFuture<*>? = null
    private var initialSettleFuture: ScheduledFuture<*>? = null
    private var heartbeatFuture: ScheduledFuture<*>? = null

    fun connect(config: Config) {
        executor.execute {
            this.config = config
            explicitStop = false
            connectLocked()
        }
    }

    fun updateToken(token: String?) {
        executor.execute {
            config = config?.copy(token = token)
        }
    }

    fun disconnect() {
        executor.execute {
            explicitStop = true
            reconnectAttempt = 0
            reconnectAttemptCount = 0
            cancelReconnect()
            cancelCalibration()
            cancelHeartbeat()
            webSocket?.close(NORMAL_CLOSE_CODE, null)
            webSocket = null
            socketState = "closed"
            setPhase(Phase.STOPPED)
        }
    }

    /** 序列化后发送；socket 未就绪时静默丢弃并返回 false（与 TS 端一致）。 */
    fun send(type: String, payload: Any): Boolean {
        val socket = webSocket ?: return false
        val sent = socket.send(encodeClientMessage(type, payload))
        if (sent && type != "NTP_REQUEST") {
            listener.onProtocolEvent("out", type, payload, clock.clientNowMs())
        }
        return sent
    }

    fun requestSync(): Boolean {
        val deviceId = config?.deviceId ?: return false
        return send("SYNC", SyncPayload(deviceId = deviceId))
    }

    fun sendControl(type: String, commandId: String, currentIndex: Int, positionSeconds: Double, version: Long): Boolean {
        val deviceId = config?.deviceId ?: return false
        return send(
            type,
            PlaybackControlPayload(
                commandId = commandId,
                deviceId = deviceId,
                currentIndex = currentIndex,
                positionSeconds = positionSeconds,
                version = version,
            ),
        )
    }

    fun sendAudioSourceLoaded(commandId: String, currentIndex: Int, recordingId: Long): Boolean {
        val deviceId = config?.deviceId ?: return false
        return send(
            "AUDIO_SOURCE_LOADED",
            AudioSourceLoadedPayload(
                commandId = commandId,
                deviceId = deviceId,
                currentIndex = currentIndex,
                recordingId = recordingId,
            ),
        )
    }

    // ---------- 内部：均在 executor 线程执行 ----------

    private fun connectLocked() {
        val config = config ?: return
        if (webSocket != null) {
            return
        }
        cancelReconnect()
        cancelCalibration()
        cancelHeartbeat()
        snapshotReceived = false
        initialMeasurements = mutableListOf()
        initialSampleCount = 0

        setPhase(if (reconnectAttempt > 0) Phase.RECONNECTING else Phase.CONNECTING)

        val requestBuilder = Request.Builder().url(buildWsUrl(config.apiBaseUrl))
        config.token?.let { requestBuilder.header(TOKEN_HEADER, it) }

        socketState = "connecting"
        val socket = okHttpClient.newWebSocket(requestBuilder.build(), createSocketListener())
        webSocket = socket
    }

    private fun createSocketListener() = object : WebSocketListener() {
        override fun onOpen(ws: WebSocket, response: Response) {
            executor.execute {
                if (webSocket !== ws) return@execute
                socketState = "open"
                val config = config ?: return@execute
                send(
                    "HELLO",
                    HelloPayload(
                        deviceId = config.deviceId,
                        clientVersion = config.clientVersion,
                    ),
                )
            }
        }

        override fun onMessage(ws: WebSocket, text: String) {
            executor.execute {
                if (webSocket !== ws) return@execute
                handleIncomingMessage(text)
            }
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            executor.execute { handleSocketGone(ws) }
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            executor.execute {
                Log.w(TAG, "playback sync socket failure: ${t.message}")
                handleSocketGone(ws)
            }
        }
    }

    private fun handleSocketGone(ws: WebSocket) {
        if (webSocket !== ws) {
            return
        }
        webSocket = null
        socketState = "closed"
        cancelCalibration()
        cancelHeartbeat()
        if (explicitStop) {
            setPhase(Phase.STOPPED)
            return
        }
        scheduleReconnect()
    }

    private fun handleIncomingMessage(raw: String) {
        val message = ServerMessage.parse(raw) ?: run {
            Log.w(TAG, "ignoring unparsable playback sync message")
            return
        }
        if (message !is ServerMessage.NtpResponse) {
            listener.onProtocolEvent("in", wireTypeOf(message), payloadOf(message), clock.clientNowMs())
        }
        when (message) {
            is ServerMessage.NtpResponse -> handleNtpResponse(message.payload)
            is ServerMessage.Snapshot -> {
                snapshotReceived = true
                listener.onServerMessage(message)
                if (phase != Phase.CALIBRATING && phase != Phase.READY) {
                    startInitialCalibration()
                }
            }
            else -> listener.onServerMessage(message)
        }
    }

    private fun handleNtpResponse(payload: NtpResponsePayload) {
        val measurement = clock.recordResponse(payload)
        if (phase == Phase.CALIBRATING) {
            initialMeasurements.add(measurement)
            // 校准期无相位变化，逐样本回发以驱动诊断图表实时更新
            emitSyncState()
        }
        if (phase == Phase.READY) {
            if (clock.applyRollingSummary() != null) {
                emitSyncState()
            }
        }
    }

    private fun startInitialCalibration() {
        cancelCalibration()
        initialMeasurements = mutableListOf()
        initialSampleCount = 0
        setPhase(Phase.CALIBRATING)

        sendCalibrationSample()
        initialSampleFuture = executor.scheduleWithFixedDelay(
            ::sendCalibrationSample,
            INITIAL_SAMPLE_INTERVAL_MS,
            INITIAL_SAMPLE_INTERVAL_MS,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun sendCalibrationSample() {
        if (initialSampleCount >= INITIAL_SAMPLE_COUNT) {
            initialSampleFuture?.cancel(false)
            initialSampleFuture = null
            if (initialSettleFuture == null) {
                initialSettleFuture = executor.schedule(
                    {
                        initialSettleFuture = null
                        finalizeInitialCalibration()
                    },
                    INITIAL_SAMPLE_SETTLE_MS,
                    TimeUnit.MILLISECONDS,
                )
            }
            return
        }
        initialSampleCount += 1
        sendNtpProbe()
    }

    private fun finalizeInitialCalibration() {
        if (!snapshotReceived) {
            setPhase(Phase.CONNECTING)
            return
        }
        if (clock.applySummary(initialMeasurements) == null) {
            setPhase(Phase.ERROR)
            return
        }
        reconnectAttempt = 0
        reconnectAttemptCount = 0
        setPhase(Phase.READY)
        startHeartbeat()
    }

    private fun startHeartbeat() {
        cancelHeartbeat()
        heartbeatFuture = executor.scheduleWithFixedDelay(
            ::sendNtpProbe,
            STEADY_STATE_INTERVAL_MS,
            STEADY_STATE_INTERVAL_MS,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun sendNtpProbe() {
        send(
            "NTP_REQUEST",
            NtpRequestPayload(
                t0 = clock.clientNowMs(),
                clientRttMs = clock.roundTripEstimateMs.takeIf { it > 0 },
            ),
        )
    }

    private fun scheduleReconnect() {
        cancelReconnect()
        val delayMs = reconnectDelayMs(reconnectAttempt)
        reconnectAttempt += 1
        reconnectAttemptCount = reconnectAttempt
        setPhase(Phase.RECONNECTING)
        reconnectFuture = executor.schedule(
            {
                reconnectFuture = null
                connectLocked()
            },
            delayMs,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun setPhase(next: Phase) {
        if (phase == next) {
            return
        }
        phase = next
        emitSyncState()
    }

    private fun emitSyncState() {
        listener.onSyncStateChanged(phase, clock.clockOffsetMs, clock.roundTripEstimateMs)
    }

    private fun cancelReconnect() {
        reconnectFuture?.cancel(false)
        reconnectFuture = null
    }

    private fun cancelCalibration() {
        initialSampleFuture?.cancel(false)
        initialSampleFuture = null
        initialSettleFuture?.cancel(false)
        initialSettleFuture = null
    }

    private fun cancelHeartbeat() {
        heartbeatFuture?.cancel(false)
        heartbeatFuture = null
    }

    companion object {
        private const val TAG = "UnirhyPlaybackSync"

        fun wireTypeOf(message: ServerMessage): String = when (message) {
            is ServerMessage.NtpResponse -> "NTP_RESPONSE"
            is ServerMessage.Snapshot -> "SNAPSHOT"
            is ServerMessage.LoadAudioSource -> "ROOM_EVENT_LOAD_AUDIO_SOURCE"
            is ServerMessage.QueueChange -> "ROOM_EVENT_QUEUE_CHANGE"
            is ServerMessage.ScheduledAction -> "SCHEDULED_ACTION"
            is ServerMessage.DeviceChange -> "ROOM_EVENT_DEVICE_CHANGE"
            is ServerMessage.ProtocolError -> "ERROR"
        }

        fun payloadOf(message: ServerMessage): Any = when (message) {
            is ServerMessage.NtpResponse -> message.payload
            is ServerMessage.Snapshot -> message.payload
            is ServerMessage.LoadAudioSource -> message.payload
            is ServerMessage.QueueChange -> message.payload
            is ServerMessage.ScheduledAction -> message.payload
            is ServerMessage.DeviceChange -> message.payload
            is ServerMessage.ProtocolError -> message.payload
        }
        private const val TOKEN_HEADER = "unirhy-token"
        private const val NORMAL_CLOSE_CODE = 1000
        const val INITIAL_SAMPLE_COUNT = 20
        const val INITIAL_SAMPLE_INTERVAL_MS = 30L
        const val INITIAL_SAMPLE_SETTLE_MS = 60L
        const val STEADY_STATE_INTERVAL_MS = 2_500L
        val RECONNECT_DELAYS_MS = longArrayOf(1_000L, 2_000L, 5_000L)

        fun reconnectDelayMs(attempt: Int): Long {
            return RECONNECT_DELAYS_MS[min(attempt, RECONNECT_DELAYS_MS.size - 1)]
        }

        fun buildWsUrl(apiBaseUrl: String): String {
            val base = apiBaseUrl.trimEnd('/')
            val wsBase = when {
                base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
                base.startsWith("http://") -> "ws://" + base.removePrefix("http://")
                else -> base
            }
            return "$wsBase/ws/playback-sync"
        }
    }
}
