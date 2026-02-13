package com.unirhy.e2e.support.matrix

object ApiCoverageRegistry {
    private const val STATUS_AND_AUTH_REQUIRED_CASE =
        "com.unirhy.e2e.SystemAuthE2eTest#status and protected endpoints require authentication"
    private const val SYSTEM_AUTH_FLOW_CASE =
        "com.unirhy.e2e.SystemAuthE2eTest#initialize login get update logout should form closed session flow"
    private const val DUPLICATE_INIT_AND_WRONG_LOGIN_CASE =
        "com.unirhy.e2e.SystemAuthE2eTest#duplicate init and wrong login return stable business errors"

    val coverageByKey: Map<ApiEndpointKey, CoverageMark> = listOf(
        full("GET", "/api/system/config/status", testRef = STATUS_AND_AUTH_REQUIRED_CASE),
        full("POST", "/api/system/config", testRef = SYSTEM_AUTH_FLOW_CASE),
        full("GET", "/api/system/config", testRef = SYSTEM_AUTH_FLOW_CASE),
        full("PUT", "/api/system/config", testRef = SYSTEM_AUTH_FLOW_CASE),
        full("POST", "/api/tokens", testRef = DUPLICATE_INIT_AND_WRONG_LOGIN_CASE),
        full("DELETE", "/api/tokens/current", testRef = SYSTEM_AUTH_FLOW_CASE),
    ).associate { it.key to it.mark }

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
