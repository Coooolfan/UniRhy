package app.unirhy.playback

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import app.unirhy.playback.queue.QueueHttpApi
import app.unirhy.playback.queue.RecordingHttpApi
import app.unirhy.playback.queue.QueueState
import app.unirhy.playback.sync.CurrentQueueItemDto
import app.unirhy.playback.sync.NtpClock
import app.unirhy.playback.sync.PlaybackStateDto
import app.unirhy.playback.sync.PlaybackStatus
import app.unirhy.playback.sync.PlaybackSyncJson
import app.unirhy.playback.sync.ScheduledActionPayload
import app.unirhy.playback.sync.ScheduledActionType
import app.unirhy.playback.sync.ServerMessage
import app.unirhy.playback.sync.SyncProtocolClient
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import okhttp3.OkHttpClient

/**
 * 原生播放内核的进程级单例：持有同步协议客户端、校时器、权威队列态与 ExoPlayer 执行器。
 * 插件（UI 桥）与前台服务（PlaybackService）共享此实例，事件经 [eventSink] 回传 WebView。
 *
 * 事件面（type 字段）：sync-state / queue-changed / state-changed / position / auth-required，
 * 均携带单调递增 seq，供 TS 侧丢弃乱序的过期事件。
 *
 * 线程模型：协议与调度决策在单线程 executor；ExoPlayer 操作经 PlayerEngine 投递主线程。
 */
@UnstableApi
object PlaybackController {
    private const val TAG = "UnirhyPlayback"
    private const val POSITION_HEARTBEAT_MS = 1_000L
    private const val MODE_SYNC = "sync"
    private const val MODE_INDEPENDENT = "independent"

    data class SessionConfig(
        val apiBaseUrl: String,
        val token: String?,
        val deviceId: String,
        val clientVersion: String,
        val mode: String,
        val preferredAssetFormat: String?,
    )

    /** 事件出口：参数为事件 JSON 字符串（含 type 与 seq）。 */
    @Volatile
    var eventSink: ((String) -> Unit)? = null

    @Volatile
    var sessionConfig: SessionConfig? = null
        private set

    @Volatile
    private var appContext: Context? = null

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

    private val queueHttpApi by lazy { QueueHttpApi(okHttpClient) }
    private val recordingHttpApi by lazy { RecordingHttpApi(okHttpClient) }

    private val eventSeq = AtomicLong(0)
    private val lifecycleGeneration = AtomicLong(0)

    @Volatile
    private var syncClient: SyncProtocolClient? = null

    @Volatile
    private var playerEngine: PlayerEngine? = null

    // ---------- 播放态镜像（供事件与 getPlaybackState） ----------

    @Volatile
    var isPlaying: Boolean = false
        private set

    @Volatile
    var durationSeconds: Double = 0.0
        private set

    @Volatile
    var isLoading: Boolean = false
        private set

    @Volatile
    var lastError: String? = null
        private set

    /** 最近一次可用的播放态版本号（SNAPSHOT / SCHEDULED_ACTION / 队列事件的最大值）。 */
    @Volatile
    private var lastKnownVersion: Long = 0

    /**
     * 服务端会话播放状态镜像（SNAPSHOT / SCHEDULED_ACTION 驱动）。
     * 用于吸收 Media3 在焦点保持丢失期间对 (playWhenReady=false, AUDIO_FOCUS_LOSS)
     * 的重复回调：会话已 PAUSED 时不再上报系统暂停，否则每个调度 PAUSE 的执行
     * 都会再触发一次上报，形成 PAUSE 自激环。
     */
    @Volatile
    private var lastKnownStatus: PlaybackStatus? = null

    /** 校时未就绪时暂存的快照状态，READY 后统一应用。 */
    private var pendingSnapshotState: PlaybackStateDto? = null

    private var positionHeartbeatFuture: ScheduledFuture<*>? = null

    // ---------- 独立模式本地队列 ----------

    data class LocalQueueItem(
        val recordingId: Long,
        val mediaFileId: Long,
        val title: String,
        val artistLabel: String,
        val coverUrl: String?,
        val durationMs: Long,
    )

    @Volatile
    private var localQueue: List<LocalQueueItem> = emptyList()

    @Volatile
    var localCurrentIndex: Int = 0
        private set

    val syncPhase: SyncProtocolClient.Phase
        get() = syncClient?.phase ?: SyncProtocolClient.Phase.STOPPED

    val currentPositionSeconds: Double
        get() = playerEngine?.currentPositionSeconds ?: 0.0

    val currentIndex: Int?
        get() = if (isIndependentMode()) {
            localCurrentIndex.takeIf { localQueue.isNotEmpty() }
        } else {
            queueState.queue?.currentIndex?.takeIf { queueState.queue?.items?.isNotEmpty() == true }
        }

    private fun isIndependentMode() = sessionConfig?.mode == MODE_INDEPENDENT

    // ---------- 配置与生命周期 ----------

    fun attachContext(context: Context) {
        appContext = context.applicationContext
    }

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
        ensureServiceStarted()
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
        lifecycleGeneration.incrementAndGet()
        val client = syncClient
        client?.disconnect()
        queueState.clear()
        pendingSnapshotState = null
        lastKnownVersion = 0
        lastKnownStatus = null
        executor.execute {
            // 排在 SyncProtocolClient.disconnect() 之后执行，先吸收已经排队的旧账号消息，
            // 再次清空协议镜像，避免旧版本在断开后被迟到消息重新写回。
            if (syncClient !== client) {
                return@execute
            }
            queueState.clear()
            pendingSnapshotState = null
            lastKnownVersion = 0
            lastKnownStatus = null
        }
        playerEngine?.stop()
        stopService()
    }

    fun requestSyncRecovery(): Boolean {
        return syncClient?.requestSync() ?: false
    }

    fun setVolume(volume: Double) {
        playerEngine?.setVolume(volume)
    }

    /**
     * 由 PlaybackService.onCreate（主线程）或 executor 调用，惰性构建 ExoPlayer。
     *
     * 锁只在主线程内持有：后台线程仅 post 到主线程后等结果，避免
     * "后台持锁等主线程、主线程（Service.onCreate）等同一把锁"的互锁。
     */
    fun ensurePlayerEngine(context: Context): PlayerEngine {
        attachContext(context)
        playerEngine?.let { return it }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            synchronized(this) {
                playerEngine?.let { return it }
                val engine = createEngine(context.applicationContext)
                playerEngine = engine
                return engine
            }
        }
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            ensurePlayerEngine(context)
            latch.countDown()
        }
        latch.await(5, TimeUnit.SECONDS)
        return playerEngine ?: error("failed to create player engine on main thread")
    }

    private fun createEngine(context: Context): PlayerEngine {
        return PlayerEngine(
            context = context,
            okHttpClient = okHttpClient,
            tokenProvider = { sessionConfig?.token },
            listener = object : PlayerEngine.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    this@PlaybackController.isPlaying = isPlaying
                    if (isPlaying) {
                        // 已实际起播即视为加载结束；连续切歌时被替换的 preload 回调
                        // 可能丢失，避免 isLoading 卡死导致下游播控永久禁用
                        isLoading = false
                        startPositionHeartbeat()
                    } else {
                        stopPositionHeartbeat()
                    }
                    emitStateChanged()
                }

                override fun onDurationKnown(durationSeconds: Double) {
                    this@PlaybackController.durationSeconds = durationSeconds
                    emitStateChanged()
                }

                override fun onPlaybackEnded() {
                    executor.execute { handlePlaybackEnded() }
                }

                override fun onSystemInducedPause(positionSeconds: Double) {
                    executor.execute { handleSystemInducedPause(positionSeconds) }
                }

                override fun onPlayerError(message: String) {
                    lastError = message
                    emitStateChanged()
                }
            },
        )
    }

    private fun ensureServiceStarted() {
        val context = appContext ?: return
        runCatching {
            context.startService(Intent(context, PlaybackService::class.java))
        }.onFailure { Log.w(TAG, "failed to start playback service: ${it.message}") }
    }

    private fun stopService() {
        val context = appContext ?: return
        runCatching {
            context.stopService(Intent(context, PlaybackService::class.java))
        }
    }

    // ---------- 同步协议：消息路由与调度执行 ----------

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
                            "diagnostics" to syncDiagnostics(),
                        ),
                    )
                    if (phase == SyncProtocolClient.Phase.READY) {
                        executor.execute { applyPendingSnapshotState() }
                    }
                }

                override fun onServerMessage(message: ServerMessage) {
                    handleServerMessage(message)
                }

                override fun onProtocolEvent(
                    direction: String,
                    type: String,
                    payload: Any?,
                    atMs: Long,
                ) {
                    emitEvent(
                        mapOf(
                            "type" to "protocol-event",
                            "direction" to direction,
                            "messageType" to type,
                            "payload" to payload,
                            "atMs" to atMs,
                        ),
                    )
                }
            },
        )
    }

    private fun handleServerMessage(message: ServerMessage) {
        when (message) {
            is ServerMessage.Snapshot -> {
                lastKnownVersion = max(lastKnownVersion, message.payload.state.version)
                lastKnownStatus = message.payload.state.status
                if (queueState.apply(message.payload.queue)) {
                    emitQueueChanged()
                }
                pendingSnapshotState = message.payload.state
                if (syncPhase == SyncProtocolClient.Phase.READY) {
                    applyPendingSnapshotState()
                }
            }
            is ServerMessage.QueueChange -> {
                lastKnownVersion = max(lastKnownVersion, message.payload.queue.version)
                if (queueState.apply(message.payload.queue)) {
                    emitQueueChanged()
                }
            }
            is ServerMessage.LoadAudioSource -> handleLoadAudioSource(
                commandId = message.payload.commandId,
                currentIndex = message.payload.currentIndex,
                recordingId = message.payload.recordingId,
            )
            is ServerMessage.ScheduledAction -> handleScheduledAction(message.payload)
            is ServerMessage.ProtocolError -> handleProtocolError(
                code = message.payload.code,
                errorMessage = message.payload.message,
            )
            else -> Unit
        }
    }

    private fun handleLoadAudioSource(commandId: String, currentIndex: Int, recordingId: Long) {
        val item = queueState.itemAt(currentIndex)
        if (item == null) {
            Log.w(TAG, "load_audio_source: no playable media for index=$currentIndex")
            lastError = "曲目缺少可播放音源"
            emitStateChanged()
            return
        }
        isLoading = true
        emitStateChanged()
        preloadQueueItem(
            item = item,
            positionSeconds = 0.0,
            onUnavailable = {
                executor.execute {
                    isLoading = false
                    lastError = "曲目缺少可播放音源"
                    emitStateChanged()
                }
            },
        ) {
            executor.execute {
                isLoading = false
                emitStateChanged()
                syncClient?.sendAudioSourceLoaded(
                    commandId = commandId,
                    currentIndex = currentIndex,
                    recordingId = recordingId,
                )
            }
        }
    }

    private fun handleScheduledAction(payload: ScheduledActionPayload) {
        lastKnownVersion = max(lastKnownVersion, payload.scheduledAction.version)
        lastKnownStatus = payload.scheduledAction.status
        val engine = playerEngine ?: return
        val action = payload.scheduledAction
        val delayMs = payload.serverTimeToExecuteMs - clock.estimatedServerNowMs()
        Log.i(
            TAG,
            "scheduled action=${action.action} version=${action.version} delayMs=$delayMs " +
                "position=${action.positionSeconds} index=${action.currentIndex} commandId=${payload.commandId}",
        )
        val executeAtElapsedMs = SystemClock.elapsedRealtime() + delayMs
        emitLocalExecution(payload, delayMs)

        when (action.action) {
            ScheduledActionType.PLAY -> {
                val index = action.currentIndex ?: return
                val item = queueState.itemAt(index) ?: return
                var position = action.positionSeconds
                if (delayMs < 0 && !payload.skipLateCompensation) {
                    position += -delayMs / 1_000.0
                }
                preloadQueueItem(item, positionSeconds = position) {
                    engine.playAt(executeAtElapsedMs)
                }
            }
            ScheduledActionType.PAUSE -> {
                if (action.currentIndex == null) {
                    engine.stop()
                    isPlaying = false
                    emitStateChanged()
                } else {
                    engine.pauseAt(executeAtElapsedMs, action.positionSeconds)
                }
            }
            ScheduledActionType.SEEK -> {
                engine.seekAt(
                    executeAtElapsedMs,
                    action.positionSeconds,
                    resumePlaying = action.status == PlaybackStatus.PLAYING,
                )
            }
        }
    }

    /** 调度动作的本地执行画像（等待/迟到），仅供诊断页展示。 */
    private fun emitLocalExecution(payload: ScheduledActionPayload, delayMs: Long) {
        val action = payload.scheduledAction
        val lateSeconds =
            if (payload.skipLateCompensation) 0.0 else max(0.0, -delayMs / 1_000.0)
        emitEvent(
            mapOf(
                "type" to "local-execution",
                "atMs" to clock.clientNowMs(),
                "action" to action.action.name,
                "commandId" to payload.commandId,
                "version" to action.version,
                "estimatedServerNowMs" to clock.estimatedServerNowMs(),
                "executeAtServerMs" to payload.serverTimeToExecuteMs,
                "waitMs" to max(0L, delayMs),
                "lateSeconds" to lateSeconds,
                "scheduledOffset" to action.positionSeconds + lateSeconds,
                "currentIndex" to action.currentIndex,
                "mediaFileId" to playerEngine?.loadedSource?.mediaFileId,
            ),
        )
    }

    /** 同步链路诊断快照（附于 sync-state 事件与 getPlaybackState）。 */
    fun syncDiagnostics(): Map<String, Any?>? {
        val client = syncClient ?: return null
        return mapOf(
            "socketState" to client.socketState,
            "reconnectAttempt" to client.reconnectAttemptCount,
            "snapshotReceived" to client.snapshotReceived,
            "lastNtpResponseAtMs" to clock.lastResponseAtMs,
            "measurements" to clock.measurementsSnapshot(),
        )
    }

    /** 快照恢复：校时就绪后按权威态定位/续播（重连、晚加入与 SYNC 响应共用此路径）。 */
    private fun applyPendingSnapshotState() {
        val state = pendingSnapshotState ?: return
        pendingSnapshotState = null
        val engine = playerEngine ?: return
        Log.i(
            TAG,
            "apply snapshot status=${state.status} index=${state.currentIndex} " +
                "position=${state.positionSeconds} version=${state.version}",
        )

        val index = state.currentIndex
        if (index == null) {
            engine.stop()
            return
        }
        val item = queueState.itemAt(index) ?: return
        when (state.status) {
            PlaybackStatus.PLAYING -> {
                val elapsedSec =
                    max(0L, clock.estimatedServerNowMs() - state.serverTimeToExecuteMs) / 1_000.0
                preloadQueueItem(item, positionSeconds = state.positionSeconds + elapsedSec) {
                    engine.playAt(SystemClock.elapsedRealtime())
                }
            }
            PlaybackStatus.PAUSED -> {
                preloadQueueItem(item, positionSeconds = state.positionSeconds) {}
            }
        }
    }

    private fun handleProtocolError(code: String, errorMessage: String) {
        Log.w(TAG, "protocol error: $code $errorMessage")
        when (code) {
            "VERSION_CONFLICT" -> syncClient?.requestSync()
            else -> {
                lastError = errorMessage
                emitStateChanged()
            }
        }
    }

    private fun preloadQueueItem(
        item: CurrentQueueItemDto,
        positionSeconds: Double,
        onUnavailable: () -> Unit = {},
        onReady: () -> Unit,
    ) {
        val config = sessionConfig ?: run {
            Log.w(TAG, "preload skipped: not configured")
            return
        }
        // 服务启动是异步的，冷进程首次播放时 playerEngine 可能尚未由服务创建，
        // 这里主动同步创建，避免首次播放被静默丢弃
        val engine = playerEngine ?: appContext?.let { ensurePlayerEngine(it) } ?: run {
            Log.w(TAG, "preload skipped: player engine unavailable")
            return
        }
        val generation = lifecycleGeneration.get()
        recordingHttpApi.resolveAudioMediaFileId(
            apiBaseUrl = config.apiBaseUrl,
            token = config.token,
            recordingId = item.recordingId,
            preferredAssetFormat = config.preferredAssetFormat,
            fallbackMediaFileId = item.mediaFileId,
        ) { mediaFileId ->
            if (lifecycleGeneration.get() != generation) {
                return@resolveAudioMediaFileId
            }
            if (mediaFileId == null) {
                onUnavailable()
                return@resolveAudioMediaFileId
            }
            engine.preload(
                url = "${config.apiBaseUrl}/api/media-files/$mediaFileId",
                source = PlayerEngine.LoadedSource(
                    mediaFileId = mediaFileId,
                    recordingId = item.recordingId,
                ),
                positionSeconds = positionSeconds,
                metadata = buildMetadata(item),
                onReady = onReady,
            )
        }
    }

    private fun buildMetadata(item: CurrentQueueItemDto): MediaMetadata {
        val builder = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.artistLabel)
        val coverUrl = item.coverUrl
        val config = sessionConfig
        if (coverUrl != null && config != null) {
            val absolute = if (coverUrl.startsWith("http")) coverUrl else config.apiBaseUrl + coverUrl
            builder.setArtworkUri(android.net.Uri.parse(absolute))
        }
        return builder.build()
    }

    // ---------- 用户命令（MediaSession 系统控件 / TS 桥） ----------

    fun onUserPlay(
        positionSeconds: Double?,
        currentIndexOverride: Int? = null,
        versionOverride: Long? = null,
    ) {
        Log.i(TAG, "user play position=$positionSeconds index=$currentIndexOverride")
        executor.execute {
            if (isIndependentMode()) {
                playerEngine?.let { engine ->
                    engine.runOnPlayerThread { engine.player.play() }
                }
                return@execute
            }
            val index = currentIndexOverride ?: queueState.queue?.currentIndex ?: return@execute
            syncClient?.sendControl(
                type = "PLAY",
                commandId = newCommandId(),
                currentIndex = index,
                positionSeconds = positionSeconds ?: currentPositionSeconds,
                version = versionOverride ?: lastKnownVersion,
            )
        }
    }

    fun onUserPause(positionOverride: Double? = null) {
        Log.i(TAG, "user pause position=$positionOverride")
        executor.execute {
            if (isIndependentMode()) {
                playerEngine?.let { engine ->
                    engine.runOnPlayerThread { engine.player.pause() }
                }
                emitStateChanged()
                return@execute
            }
            val index = queueState.queue?.currentIndex ?: return@execute
            syncClient?.sendControl(
                type = "PAUSE",
                commandId = newCommandId(),
                currentIndex = index,
                positionSeconds = positionOverride ?: currentPositionSeconds,
                version = lastKnownVersion,
            )
        }
    }

    fun onUserSeek(positionSeconds: Double) {
        executor.execute {
            if (isIndependentMode()) {
                playerEngine?.let { engine ->
                    engine.runOnPlayerThread {
                        engine.player.seekTo((positionSeconds * 1_000).toLong())
                    }
                }
                return@execute
            }
            val index = queueState.queue?.currentIndex ?: return@execute
            syncClient?.sendControl(
                type = "SEEK",
                commandId = newCommandId(),
                currentIndex = index,
                positionSeconds = positionSeconds,
                version = lastKnownVersion,
            )
        }
    }

    fun onUserNext() {
        executor.execute {
            if (isIndependentMode()) {
                localNavigate(step = 1)
                return@execute
            }
            navigateQueue { apiBaseUrl, token, version ->
                queueHttpApi.navigateNext(apiBaseUrl, token, version)
            }
        }
    }

    fun onUserPrevious() {
        executor.execute {
            if (isIndependentMode()) {
                localNavigate(step = -1)
                return@execute
            }
            navigateQueue { apiBaseUrl, token, version ->
                queueHttpApi.navigatePrevious(apiBaseUrl, token, version)
            }
        }
    }

    private fun navigateQueue(
        call: (apiBaseUrl: String, token: String?, version: Long) -> QueueHttpApi.NavigationResult,
    ) {
        val config = sessionConfig ?: return
        // 服务端在队列切换后紧跟一次播放调度，会把权威版本再前进一步。
        // 队列广播只承载队列版本，调度版本经 scheduled action 回来落到 lastKnownVersion。
        // 手动切歌若只用队列版本会必然落后一步触发 409，取二者最大值。
        val queueVersion = queueState.version() ?: return
        val version = max(queueVersion, lastKnownVersion)
        when (val result = call(config.apiBaseUrl, config.token, version)) {
            is QueueHttpApi.NavigationResult.Ok -> Unit
            is QueueHttpApi.NavigationResult.VersionConflict -> {
                Log.w(TAG, "queue navigation version conflict version=$version, requesting sync")
                syncClient?.requestSync()
            }
            is QueueHttpApi.NavigationResult.Failed ->
                Log.w(TAG, "queue navigation failed: ${result.message}")
        }
    }

    private fun handleSystemInducedPause(positionSeconds: Double) {
        if (isIndependentMode()) {
            emitStateChanged()
            return
        }
        if (lastKnownStatus != PlaybackStatus.PLAYING) {
            Log.i(TAG, "system induced pause suppressed (session not playing) position=$positionSeconds")
            return
        }
        Log.i(TAG, "system induced pause position=$positionSeconds")
        val index = queueState.queue?.currentIndex ?: return
        syncClient?.sendControl(
            type = "PAUSE",
            commandId = newCommandId(),
            currentIndex = index,
            positionSeconds = positionSeconds,
            version = lastKnownVersion,
        )
    }

    private fun handlePlaybackEnded() {
        if (isIndependentMode()) {
            localNavigate(step = 1)
        }
        // 同步模式下由服务端 auto-advance 驱动切歌，客户端不主动动作
    }

    // ---------- 独立模式本地控制 ----------

    fun localSetQueue(items: List<LocalQueueItem>, currentIndex: Int) {
        executor.execute {
            localQueue = items
            localCurrentIndex = currentIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        }
    }

    fun localPlay(currentIndex: Int, positionSeconds: Double) {
        executor.execute {
            ensureServiceStarted()
            val item = localQueue.getOrNull(currentIndex) ?: return@execute
            localCurrentIndex = currentIndex
            preloadLocalItem(item, positionSeconds) {
                playerEngine?.playAt(SystemClock.elapsedRealtime())
            }
            emitStateChanged()
        }
    }

    fun localPause() {
        executor.execute {
            playerEngine?.let { engine ->
                engine.runOnPlayerThread { engine.player.pause() }
            }
        }
    }

    fun localSeek(positionSeconds: Double) {
        executor.execute {
            playerEngine?.let { engine ->
                engine.runOnPlayerThread {
                    engine.player.seekTo((positionSeconds * 1_000).toLong())
                }
            }
        }
    }

    private fun localNavigate(step: Int) {
        val nextIndex = localCurrentIndex + step
        val item = localQueue.getOrNull(nextIndex)
        if (item == null) {
            playerEngine?.stop()
            isPlaying = false
            emitStateChanged()
            return
        }
        localCurrentIndex = nextIndex
        preloadLocalItem(item, positionSeconds = 0.0) {
            playerEngine?.playAt(SystemClock.elapsedRealtime())
        }
        emitStateChanged()
    }

    private fun preloadLocalItem(
        item: LocalQueueItem,
        positionSeconds: Double,
        onReady: () -> Unit,
    ) {
        preloadQueueItem(
            CurrentQueueItemDto(
                recordingId = item.recordingId,
                title = item.title,
                artistLabel = item.artistLabel,
                coverUrl = item.coverUrl,
                durationMs = item.durationMs,
                mediaFileId = item.mediaFileId,
            ),
            positionSeconds = positionSeconds,
            onReady = onReady,
        )
    }

    // ---------- 事件回传 ----------

    private fun startPositionHeartbeat() {
        stopPositionHeartbeat()
        positionHeartbeatFuture = executor.scheduleWithFixedDelay(
            {
                emitEvent(
                    mapOf(
                        "type" to "position",
                        "positionSeconds" to currentPositionSeconds,
                        "isPlaying" to isPlaying,
                    ),
                )
            },
            POSITION_HEARTBEAT_MS,
            POSITION_HEARTBEAT_MS,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun stopPositionHeartbeat() {
        positionHeartbeatFuture?.cancel(false)
        positionHeartbeatFuture = null
    }

    private fun emitQueueChanged() {
        emitEvent(
            mapOf(
                "type" to "queue-changed",
                "queue" to queueState.queue,
            ),
        )
    }

    private fun emitStateChanged() {
        emitEvent(
            mapOf(
                "type" to "state-changed",
                "isPlaying" to isPlaying,
                "currentIndex" to currentIndex,
                "positionSeconds" to currentPositionSeconds,
                "durationSeconds" to durationSeconds,
                "isLoading" to isLoading,
                "error" to lastError,
            ),
        )
    }

    fun emitEvent(fields: Map<String, Any?>) {
        val sink = eventSink ?: return
        val event = LinkedHashMap<String, Any?>(fields)
        event["seq"] = eventSeq.incrementAndGet()
        sink(PlaybackSyncJson.mapper.writeValueAsString(event))
    }

    private fun newCommandId() = "android-${UUID.randomUUID().toString().take(8)}"
}
