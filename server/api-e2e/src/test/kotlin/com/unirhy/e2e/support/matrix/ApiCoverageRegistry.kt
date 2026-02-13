package com.unirhy.e2e.support.matrix

object ApiCoverageRegistry {
    private const val SMOKE_CASE =
        "com.unirhy.e2e.SmokeTest#initialize login scan and stream media from real filesystem"
    private const val SYSTEM_AUTH_FLOW_CASE =
        "com.unirhy.e2e.SystemAuthE2eTest#initialize login get update logout should form closed session flow"

    val coverageByKey: Map<ApiEndpointKey, CoverageMark> = listOf(
        smoke("GET", "/api/system/config/status"),
        smoke("POST", "/api/system/config"),
        full("GET", "/api/system/config", testRef = SYSTEM_AUTH_FLOW_CASE),
        full("PUT", "/api/system/config", testRef = SYSTEM_AUTH_FLOW_CASE),
        smoke("POST", "/api/tokens"),
        full("DELETE", "/api/tokens/current", testRef = SYSTEM_AUTH_FLOW_CASE),
        smoke("POST", "/api/task/scan"),
        smoke("GET", "/api/task/running"),
        smoke("GET", "/api/works"),
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

    private fun full(
        method: String,
        path: String,
        condition: String = "",
        testRef: String,
        note: String = "",
    ): CoverageEntry {
        return CoverageEntry(
            key = ApiEndpointKey(method = method, path = path, condition = condition),
            mark = CoverageMark(level = CoverageLevel.FULL, testRef = testRef, note = note),
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
