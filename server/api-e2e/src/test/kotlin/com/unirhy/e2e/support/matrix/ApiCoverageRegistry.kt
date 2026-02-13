package com.unirhy.e2e.support.matrix

object ApiCoverageRegistry {
    private const val STATUS_AND_AUTH_REQUIRED_CASE =
        "com.unirhy.e2e.SystemAuthE2eTest#status and protected endpoints require authentication"
    private const val SYSTEM_AUTH_FLOW_CASE =
        "com.unirhy.e2e.SystemAuthE2eTest#initialize login get update logout should form closed session flow"
    private const val DUPLICATE_INIT_AND_WRONG_LOGIN_CASE =
        "com.unirhy.e2e.SystemAuthE2eTest#duplicate init and wrong login return stable business errors"
    private const val STORAGE_AUTH_REQUIRED_CASE =
        "com.unirhy.e2e.StorageConfigE2eTest#all storage endpoints should reject unauthenticated access"
    private const val FILE_SYSTEM_STORAGE_CRUD_CASE =
        "com.unirhy.e2e.StorageConfigE2eTest#file system storage should support create get update list delete flow"
    private const val OSS_STORAGE_CRUD_CASE =
        "com.unirhy.e2e.StorageConfigE2eTest#oss storage should support create get update list delete flow"
    private const val STORAGE_LINKAGE_CONSTRAINT_CASE =
        "com.unirhy.e2e.StorageConfigE2eTest#system config should enforce storage linkage constraints"

    val coverageByKey: Map<ApiEndpointKey, CoverageMark> = listOf(
        full(
            "GET",
            "/api/storage/fs",
            testRef = FILE_SYSTEM_STORAGE_CRUD_CASE,
            note = "auth: $STORAGE_AUTH_REQUIRED_CASE",
        ),
        full(
            "POST",
            "/api/storage/fs",
            testRef = FILE_SYSTEM_STORAGE_CRUD_CASE,
            note = "auth: $STORAGE_AUTH_REQUIRED_CASE",
        ),
        full(
            "GET",
            "/api/storage/fs/{id}",
            testRef = FILE_SYSTEM_STORAGE_CRUD_CASE,
            note = "auth: $STORAGE_AUTH_REQUIRED_CASE",
        ),
        full(
            "PUT",
            "/api/storage/fs/{id}",
            testRef = FILE_SYSTEM_STORAGE_CRUD_CASE,
            note = "auth: $STORAGE_AUTH_REQUIRED_CASE",
        ),
        full(
            "DELETE",
            "/api/storage/fs/{id}",
            testRef = FILE_SYSTEM_STORAGE_CRUD_CASE,
            note = "auth: $STORAGE_AUTH_REQUIRED_CASE; linkage: $STORAGE_LINKAGE_CONSTRAINT_CASE",
        ),
        full(
            "GET",
            "/api/storage/oss",
            testRef = OSS_STORAGE_CRUD_CASE,
            note = "auth: $STORAGE_AUTH_REQUIRED_CASE",
        ),
        full(
            "POST",
            "/api/storage/oss",
            testRef = OSS_STORAGE_CRUD_CASE,
            note = "auth: $STORAGE_AUTH_REQUIRED_CASE",
        ),
        full(
            "GET",
            "/api/storage/oss/{id}",
            testRef = OSS_STORAGE_CRUD_CASE,
            note = "auth: $STORAGE_AUTH_REQUIRED_CASE",
        ),
        full(
            "PUT",
            "/api/storage/oss/{id}",
            testRef = OSS_STORAGE_CRUD_CASE,
            note = "auth: $STORAGE_AUTH_REQUIRED_CASE",
        ),
        full(
            "DELETE",
            "/api/storage/oss/{id}",
            testRef = OSS_STORAGE_CRUD_CASE,
            note = "auth: $STORAGE_AUTH_REQUIRED_CASE",
        ),
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
