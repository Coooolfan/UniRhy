package com.unirhy.e2e.support

import com.fasterxml.jackson.databind.JsonNode
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
    val albumTitle = album.path("title").asText().takeIf(String::isNotBlank) ?: return null

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
        path = "/api/task/scan",
        json = mapOf(
            "providerType" to "FILE_SYSTEM",
            "providerId" to resolveSystemFsProviderId(state),
        ),
    )
    E2eAssert.status(submitResponse, 202, "[prepare] playback scan task should be accepted")

    awaitScanTaskFinished(
        state = state,
        baselinePending = baselinePending,
        baselineCompleted = baselineCompleted,
        baselineFailed = baselineFailed,
    )

    val works = listWorks(state, "[prepare] list works after playback scan")
    val recordingIds = collectRecordingIds(works).distinct()
    assertTrue(
        recordingIds.size >= minRecordingCount,
        "[prepare] expected at least $minRecordingCount playable recordings after scan",
    )

    val albumSearchResponse = state.api.get(
        path = "/api/albums/search",
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
    val response = state.api.get("/api/task/logs")
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
        row.path("taskType").asText() == taskType && row.path("status").asText() == status
    }?.path("count")?.longValue() ?: 0L
}

private fun awaitScanTaskFinished(
    state: E2eAdminSession,
    baselinePending: Long,
    baselineCompleted: Long,
    baselineFailed: Long,
) {
    val deadline = System.currentTimeMillis() + scanWaitTimeoutMillis()
    var observedPending = false

    while (System.currentTimeMillis() <= deadline) {
        val statsRows = fetchTaskStats(state, "[prepare] playback scan task stats")
        val pending = taskCount(statsRows, "METADATA_PARSE", "PENDING")
        val completed = taskCount(statsRows, "METADATA_PARSE", "COMPLETED")
        val failed = taskCount(statsRows, "METADATA_PARSE", "FAILED")

        if (pending > baselinePending) {
            observedPending = true
        }
        if (pending <= baselinePending && (completed > baselineCompleted || failed > baselineFailed)) {
            assertTrue(
                observedPending || completed > baselineCompleted,
                "[prepare] playback scan task stats should advance before finishing",
            )
            return
        }
        Thread.sleep(POLL_INTERVAL_MILLIS)
    }

    fail("[prepare] playback scan task did not finish within timeout ${scanWaitTimeoutMillis()} ms")
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
    val response = state.api.get("/api/system/config")
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
