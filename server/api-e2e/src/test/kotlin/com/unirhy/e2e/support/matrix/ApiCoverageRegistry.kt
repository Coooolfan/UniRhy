package com.unirhy.e2e.support.matrix

object ApiCoverageRegistry {
    private const val SMOKE_CASE =
        "com.unirhy.e2e.SmokeTest#initialize login scan and stream media from real filesystem"

    val coverageByKey: Map<ApiEndpointKey, CoverageMark> = listOf(
        smoke("GET", "/api/system/config/status"),
        smoke("POST", "/api/system/config"),
        smoke("GET", "/api/token"),
        smoke("POST", "/api/task/scan"),
        smoke("GET", "/api/task/running"),
        smoke("GET", "/api/work"),
        smoke("GET", "/api/media/{id}", condition = "Range"),
    ).associate { it.key to it.mark }

    private fun smoke(
        method: String,
        path: String,
        condition: String = "",
        testRef: String = SMOKE_CASE,
        note: String = "",
    ): CoverageEntry {
        return CoverageEntry(
            key = ApiEndpointKey(method = method, path = path, condition = condition),
            mark = CoverageMark(level = CoverageLevel.SMOKE, testRef = testRef, note = note),
        )
    }
}

enum class CoverageLevel {
    SMOKE,
    FULL,
    TODO,
}

data class CoverageMark(
    val level: CoverageLevel,
    val testRef: String = "",
    val note: String = "",
)

private data class CoverageEntry(
    val key: ApiEndpointKey,
    val mark: CoverageMark,
)
