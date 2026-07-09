package com.unirhy.e2e.support

import tools.jackson.databind.JsonNode
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.math.max
import kotlin.test.assertTrue
import kotlin.test.fail

data class PreparedPlaybackData(
    val recordingIds: List<Long>,
    val albumId: Long,
    val albumTitle: String,
)

private val prepareLock = Any()

fun ensurePreparedPlaybackData(
    state: E2eAdminSession,
    minRecordingCount: Int = 3,
): PreparedPlaybackData {
    require(minRecordingCount > 0) { "minRecordingCount must be positive" }

    synchronized(prepareLock) {
        recoverPreparedPlaybackData(state, minRecordingCount)?.let { return it }
        return executeScanAndPreparePlaybackData(state, minRecordingCount)
    }
}

private fun recoverPreparedPlaybackData(
    state: E2eAdminSession,
    minRecordingCount: Int,
): PreparedPlaybackData? {
    val works = listWorks(state, "[prepare] list works for playback recovery")
    val recordingIds = collectRecordingIds(works).distinct()
    if (recordingIds.size < minRecordingCount) {
        return null
    }

    val albumsResponse = state.api.get(
        path = "/api/albums",
        query = mapOf("pageIndex" to 0, "pageSize" to 200),
    )
    E2eAssert.status(albumsResponse, 200, "[prepare] list albums for playback recovery should succeed")
    val albums = pageRows(albumsResponse.body(), "[prepare] playback albums recovery")
    val album = albums.firstOrNull() ?: return null
    val albumId = album.path("id").takeIf(JsonNode::isIntegralNumber)?.longValue() ?: return null
    val albumTitle = album.path("title").asString().takeIf(String::isNotBlank) ?: return null

    return PreparedPlaybackData(
        recordingIds = recordingIds.take(minRecordingCount),
        albumId = albumId,
        albumTitle = albumTitle,
    )
}

private fun executeScanAndPreparePlaybackData(
    state: E2eAdminSession,
    minRecordingCount: Int,
): PreparedPlaybackData {
    val baselineStats = fetchTaskStats(state, "[prepare] playback baseline task stats")
    val baselinePending = taskCount(baselineStats, "METADATA_PARSE", "PENDING")
    val baselineCompleted = taskCount(baselineStats, "METADATA_PARSE", "COMPLETED")
    val baselineFailed = taskCount(baselineStats, "METADATA_PARSE", "FAILED")

    val fixture = preparePlaybackFixture(
        scanWorkspace = state.runtime.scanWorkspace,
        fileCount = max(minRecordingCount, 3),
    )

    val submitResponse = state.api.post(
        path = "/api/tasks/scans",
        json = mapOf(
            "providerType" to "FILE_SYSTEM",
            "providerId" to resolveSystemFsProviderId(state),
        ),
    )
    E2eAssert.status(submitResponse, 202, "[prepare] playback scan task should be accepted")

    val recordingIds = awaitPlayableRecordings(
        state = state,
        minRecordingCount = minRecordingCount,
        baselinePending = baselinePending,
        baselineCompleted = baselineCompleted,
        baselineFailed = baselineFailed,
    )

    val albumSearchResponse = state.api.get(
        path = "/api/albums/search-results",
        query = mapOf("name" to fixture.albumTitle),
    )
    E2eAssert.status(albumSearchResponse, 200, "[prepare] playback album search should succeed")
    val albums = E2eJson.mapper.readTree(albumSearchResponse.body())
    val firstAlbum = albums.firstOrNull() ?: fail("[prepare] expected scanned playback album to exist")
    val albumId = firstAlbum.path("id").takeIf(JsonNode::isIntegralNumber)?.longValue()
        ?: fail("[prepare] expected scanned playback album id")

    return PreparedPlaybackData(
        recordingIds = recordingIds.take(minRecordingCount),
        albumId = albumId,
        albumTitle = fixture.albumTitle,
    )
}

private fun listWorks(
    state: E2eAdminSession,
    step: String,
): List<JsonNode> {
    val worksResponse = state.api.get(
        path = "/api/works",
        query = mapOf("pageIndex" to 0, "pageSize" to 200),
    )
    E2eAssert.status(worksResponse, 200, "$step should succeed")
    return pageRows(worksResponse.body(), step)
}

private fun collectRecordingIds(works: List<JsonNode>): List<Long> {
    return works.flatMap { work ->
        work.path("recordings")
            .takeIf(JsonNode::isArray)
            ?.toList()
            .orEmpty()
            .mapNotNull { recording ->
                recording.path("id")
                    .takeIf(JsonNode::isIntegralNumber)
                    ?.longValue()
            }
    }
}

private fun fetchTaskStats(
    state: E2eAdminSession,
    step: String,
): List<JsonNode> {
    val response = state.api.get("/api/tasks/log-counts")
    E2eAssert.status(response, 200, "$step should succeed")
    val root = E2eJson.mapper.readTree(response.body())
    assertTrue(root.isArray, "$step expected root array")
    return root.toList()
}

private fun taskCount(
    rows: List<JsonNode>,
    taskType: String,
    status: String,
): Long {
    return rows.firstOrNull { row ->
        row.path("taskType").asString() == taskType && row.path("status").asString() == status
    }?.path("count")?.longValue() ?: 0L
}

/**
 * 轮询已入库的可播放 recording，直到数量达到阈值。
 *
 * 扫描会一次性 claim 多个 METADATA_PARSE 任务，PENDING 计数会瞬间回落到基线，
 * 且 RUNNING 因事务隔离总为 0，因此不能以“首个任务完成”或“pending 回落”作为结束信号
 * （此前的实现会在仅落库 1 条 recording 时提前返回，在 CI 全新库上间歇性失败）。
 *
 * 这里直接以 recording 数量为准：一旦达到阈值即返回；若终态计数（completed+failed）
 * 在 pending 回落后连续多次保持不变，说明调度器已停止推进，此时仍不足则快速失败。
 */
private fun awaitPlayableRecordings(
    state: E2eAdminSession,
    minRecordingCount: Int,
    baselinePending: Long,
    baselineCompleted: Long,
    baselineFailed: Long,
): List<Long> {
    val deadline = System.currentTimeMillis() + scanWaitTimeoutMillis()
    var observedPending = false
    var lastTerminal = -1L
    var stableCount = 0

    while (System.currentTimeMillis() <= deadline) {
        // 先读任务统计，再读 works。completeTask 与 saveScannedRecording 处于同一事务，
        // 先统计后 works 可保证已计入 completed 的 recording 一定可见。
        val statsRows = fetchTaskStats(state, "[prepare] playback scan task stats")
        val pending = taskCount(statsRows, "METADATA_PARSE", "PENDING")
        val terminal = taskCount(statsRows, "METADATA_PARSE", "COMPLETED") +
            taskCount(statsRows, "METADATA_PARSE", "FAILED")
        if (pending > baselinePending) {
            observedPending = true
        }

        val works = listWorks(state, "[prepare] list works during playback scan")
        val recordingIds = collectRecordingIds(works).distinct()
        if (recordingIds.size >= minRecordingCount) {
            return recordingIds
        }

        // 终态计数在 pending 回落后连续保持不变，视为调度器已排空。
        if (observedPending && pending <= baselinePending && terminal > baselineCompleted + baselineFailed) {
            stableCount = if (terminal == lastTerminal) stableCount + 1 else 0
            lastTerminal = terminal
            if (stableCount >= DRAIN_STABLE_POLLS) {
                fail(
                    "[prepare] scan drained with only ${recordingIds.size} playable recordings, " +
                        "expected at least $minRecordingCount " +
                        "(terminal delta=${terminal - baselineCompleted - baselineFailed})",
                )
            }
        }
        Thread.sleep(POLL_INTERVAL_MILLIS)
    }

    fail("[prepare] playback scan did not produce $minRecordingCount recordings within timeout ${scanWaitTimeoutMillis()} ms")
}

private fun preparePlaybackFixture(
    scanWorkspace: Path,
    fileCount: Int,
): PlaybackFixtureInfo {
    val suffix = UUID.randomUUID().toString().replace("-", "").take(10)
    val albumTitle = "e2e-playback-album-$suffix"
    val fixtureRoot = scanWorkspace.resolve("playback-sync-$suffix")
    Files.createDirectories(fixtureRoot)
    repeat(fileCount) { index ->
        SyntheticAudioFixture.generateOne(
            outputDir = fixtureRoot,
            fileName = "fixture-${index.toString().padStart(4, '0')}.mp3",
            metadata = AudioFixtureMetadata(
                title = "e2e-playback-track-$index-$suffix",
                artist = "e2e-playback-artist",
                album = albumTitle,
                comment = "e2e-playback-fixture-$suffix",
            ),
        )
    }
    return PlaybackFixtureInfo(albumTitle = albumTitle)
}

private fun resolveSystemFsProviderId(state: E2eAdminSession): Long {
    val response = state.api.get("/api/system-config")
    E2eAssert.status(response, 200, "[prepare] get system config for playback should succeed")
    val fsProviderIdNode = E2eJson.mapper.readTree(response.body()).path("fsProviderId")
    assertTrue(fsProviderIdNode.isIntegralNumber, "[prepare] playback fsProviderId should be integral")
    return fsProviderIdNode.longValue()
}

private fun pageRows(
    responseBody: String,
    step: String,
): List<JsonNode> {
    val root = E2eJson.mapper.readTree(responseBody)
    val rows = root.path("rows")
    assertTrue(rows.isArray, "$step expected rows array")
    return rows.toList()
}

private fun scanWaitTimeoutMillis(): Long {
    val raw = System.getenv(SCAN_WAIT_TIMEOUT_ENV)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return DEFAULT_SCAN_WAIT_TIMEOUT_MILLIS
    val value = raw.toLongOrNull()
    require(value != null && value > 0) {
        "[prepare] env $SCAN_WAIT_TIMEOUT_ENV must be positive integer milliseconds, actual=$raw"
    }
    return value
}

private data class PlaybackFixtureInfo(
    val albumTitle: String,
)

private const val SCAN_WAIT_TIMEOUT_ENV = "E2E_SCAN_WAIT_TIMEOUT_MILLIS"
private const val DEFAULT_SCAN_WAIT_TIMEOUT_MILLIS = 120_000L
private const val POLL_INTERVAL_MILLIS = 150L
private const val DRAIN_STABLE_POLLS = 5
