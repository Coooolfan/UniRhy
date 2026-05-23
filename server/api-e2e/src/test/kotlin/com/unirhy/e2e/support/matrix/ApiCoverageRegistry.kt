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
    private const val TASK_CONTENT_AUTH_REQUIRED_CASE =
        "com.unirhy.e2e.TaskContentReadE2eTest#task and content endpoints should reject unauthenticated access"
    private const val TASK_TRANSCODE_SUCCESS_CASE =
        "com.unirhy.e2e.TaskContentReadE2eTest#transcode task should complete successfully and write opus files"
    private const val TASK_SCAN_LIFECYCLE_CASE =
        "com.unirhy.e2e.TaskContentReadE2eTest#scan submission should report metadata parse stats and accept incremental duplicate submission"
    private const val WORK_ALBUM_READ_CASE =
        "com.unirhy.e2e.TaskContentReadE2eTest#works and albums should support read random and delete flow"
    private const val MEDIA_READ_CASE =
        "com.unirhy.e2e.TaskContentReadE2eTest#media endpoint should support full range head and error branches"
    private const val ACCOUNT_PLAYLIST_CONTENT_AUTH_REQUIRED_CASE =
        "com.unirhy.e2e.AccountPlaylistContentE2eTest#auth gate for account playlist and content mutation endpoints should reject unauthenticated access"
    private const val ACCOUNT_CRUD_CASE =
        "com.unirhy.e2e.AccountPlaylistContentE2eTest#accounts should support create list me update delete with permission boundary"
    private const val PLAYLIST_OWNER_SCOPE_CASE =
        "com.unirhy.e2e.AccountPlaylistContentE2eTest#playlists should support owner scoped crud and recording association"
    private const val CONTENT_SEARCH_UPDATE_MERGE_CASE =
        "com.unirhy.e2e.AccountPlaylistContentE2eTest#content endpoints should support search update recording update and merge flow"
    private const val ALBUM_UPDATE_CASE =
        "com.unirhy.e2e.AccountPlaylistContentE2eTest#album update should modify scalar fields and return updated detail"
    private const val ALBUM_REORDER_CASE =
        "com.unirhy.e2e.AccountPlaylistContentE2eTest#album reorder should update sort order and validate input"
    private const val PLAYLIST_REORDER_CASE =
        "com.unirhy.e2e.AccountPlaylistContentE2eTest#playlist reorder should update sort order and validate input"
    private const val ARTIST_FLOW_CASE =
        "com.unirhy.e2e.AccountPlaylistContentE2eTest#artists should support list search create update and merge flow"
    private const val PLUGIN_AUTH_REQUIRED_CASE =
        "com.unirhy.e2e.PluginE2eTest#plugin endpoints should reject unauthenticated access"
    private const val PLUGIN_LIFECYCLE_CASE =
        "com.unirhy.e2e.PluginE2eTest#plugin lifecycle should support upload list download enable submit disable and delete"
    private const val PLUGIN_UPLOAD_INVALID_CASE =
        "com.unirhy.e2e.PluginE2eTest#plugin upload should reject invalid archives"
    private const val PLAYBACK_QUEUE_AUTH_REQUIRED_CASE =
        "com.unirhy.e2e.PlaybackQueueE2eTest#all playback queue endpoints should reject unauthenticated access"
    private const val PLAYBACK_QUEUE_FLOW_CASE =
        "com.unirhy.e2e.PlaybackQueueE2eTest#playback queue should support full mutation flow and stable conflict branches"

    val coverageByKey: Map<ApiEndpointKey, CoverageMark> = listOf(
        full(
            "GET",
            "/api/accounts",
            testRef = ACCOUNT_CRUD_CASE,
            note = "auth: $ACCOUNT_PLAYLIST_CONTENT_AUTH_REQUIRED_CASE",
        ),
        full(
            "POST",
            "/api/accounts",
            testRef = ACCOUNT_CRUD_CASE,
            note = "auth: $ACCOUNT_PLAYLIST_CONTENT_AUTH_REQUIRED_CASE",
        ),
        full(
            "GET",
            "/api/accounts/me",
            testRef = ACCOUNT_CRUD_CASE,
            note = "auth: $ACCOUNT_PLAYLIST_CONTENT_AUTH_REQUIRED_CASE",
        ),
        full(
            "DELETE",
            "/api/accounts/{id}",
            testRef = ACCOUNT_CRUD_CASE,
            note = "auth: $ACCOUNT_PLAYLIST_CONTENT_AUTH_REQUIRED_CASE",
        ),
        full(
            "PUT",
            "/api/accounts/{id}",
            testRef = ACCOUNT_CRUD_CASE,
            note = "auth: $ACCOUNT_PLAYLIST_CONTENT_AUTH_REQUIRED_CASE; permission: non-admin cannot update admin",
        ),
        full(
            "GET",
            "/api/albums/search",
            testRef = CONTENT_SEARCH_UPDATE_MERGE_CASE,
            note = "auth: $ACCOUNT_PLAYLIST_CONTENT_AUTH_REQUIRED_CASE; validation: unknown keyword returns empty array",
        ),
        full(
            "PUT",
            "/api/albums/{id}/recordings/reorder",
            testRef = ALBUM_REORDER_CASE,
            note = "auth: $ACCOUNT_PLAYLIST_CONTENT_AUTH_REQUIRED_CASE; validation: duplicate missing extra recording ids and unknown album fail",
        ),
        full(
            "GET",
            "/api/artists",
            testRef = ARTIST_FLOW_CASE,
            note = "validation: malformed page index returns 400",
        ),
        full(
            "GET",
            "/api/artists/search",
            testRef = ARTIST_FLOW_CASE,
            note = "validation: unknown keyword returns empty array",
        ),
        full(
            "POST",
            "/api/artists",
            testRef = ARTIST_FLOW_CASE,
            note = "validation: missing display name returns 400",
        ),
        full(
            "PUT",
            "/api/artists/{id}",
            testRef = ARTIST_FLOW_CASE,
            note = "validation: malformed id returns 400",
        ),
        full(
            "POST",
            "/api/artists/merge",
            testRef = ARTIST_FLOW_CASE,
            note = "error: unknown target returns 404",
        ),
        full(
            "GET",
            "/api/playlists",
            testRef = PLAYLIST_OWNER_SCOPE_CASE,
            note = "auth: $ACCOUNT_PLAYLIST_CONTENT_AUTH_REQUIRED_CASE",
        ),
        full(
            "GET",
            "/api/playback/current-queue",
            testRef = PLAYBACK_QUEUE_FLOW_CASE,
            note = "auth: $PLAYBACK_QUEUE_AUTH_REQUIRED_CASE",
        ),
        full(
            "PUT",
            "/api/playback/current-queue",
            testRef = PLAYBACK_QUEUE_FLOW_CASE,
            note = "auth: $PLAYBACK_QUEUE_AUTH_REQUIRED_CASE; conflict: stale version and unknown recording return 409",
        ),
        full(
            "POST",
            "/api/playback/current-queue/items",
            testRef = PLAYBACK_QUEUE_FLOW_CASE,
            note = "auth: $PLAYBACK_QUEUE_AUTH_REQUIRED_CASE; conflict: stale version returns 409",
        ),
        full(
            "PUT",
            "/api/playback/current-queue/order",
            testRef = PLAYBACK_QUEUE_FLOW_CASE,
            note = "auth: $PLAYBACK_QUEUE_AUTH_REQUIRED_CASE",
        ),
        full(
            "PUT",
            "/api/playback/current-queue/current",
            testRef = PLAYBACK_QUEUE_FLOW_CASE,
            note = "auth: $PLAYBACK_QUEUE_AUTH_REQUIRED_CASE",
        ),
        full(
            "PUT",
            "/api/playback/current-queue/strategy",
            testRef = PLAYBACK_QUEUE_FLOW_CASE,
            note = "auth: $PLAYBACK_QUEUE_AUTH_REQUIRED_CASE",
        ),
        full(
            "POST",
            "/api/playback/current-queue/actions/next",
            testRef = PLAYBACK_QUEUE_FLOW_CASE,
            note = "auth: $PLAYBACK_QUEUE_AUTH_REQUIRED_CASE",
        ),
        full(
            "POST",
            "/api/playback/current-queue/actions/previous",
            testRef = PLAYBACK_QUEUE_FLOW_CASE,
            note = "auth: $PLAYBACK_QUEUE_AUTH_REQUIRED_CASE",
        ),
        full(
            "POST",
            "/api/playback/current-queue/actions/remove",
            testRef = PLAYBACK_QUEUE_FLOW_CASE,
            note = "auth: $PLAYBACK_QUEUE_AUTH_REQUIRED_CASE",
        ),
        full(
            "POST",
            "/api/playback/current-queue/actions/clear",
            testRef = PLAYBACK_QUEUE_FLOW_CASE,
            note = "auth: $PLAYBACK_QUEUE_AUTH_REQUIRED_CASE",
        ),
        full(
            "POST",
            "/api/playlists",
            testRef = PLAYLIST_OWNER_SCOPE_CASE,
            note = "auth: $ACCOUNT_PLAYLIST_CONTENT_AUTH_REQUIRED_CASE",
        ),
        full(
            "DELETE",
            "/api/playlists/{id}",
            testRef = PLAYLIST_OWNER_SCOPE_CASE,
            note = "auth: $ACCOUNT_PLAYLIST_CONTENT_AUTH_REQUIRED_CASE; ownership: non-owner returns 404",
        ),
        full(
            "GET",
            "/api/playlists/{id}",
            testRef = PLAYLIST_OWNER_SCOPE_CASE,
            note = "auth: $ACCOUNT_PLAYLIST_CONTENT_AUTH_REQUIRED_CASE; ownership: non-owner returns 404",
        ),
        full(
            "PUT",
            "/api/playlists/{id}",
            testRef = PLAYLIST_OWNER_SCOPE_CASE,
            note = "auth: $ACCOUNT_PLAYLIST_CONTENT_AUTH_REQUIRED_CASE; ownership: non-owner returns 404",
        ),
        full(
            "PUT",
            "/api/playlists/{id}/recordings/reorder",
            testRef = PLAYLIST_REORDER_CASE,
            note = "auth: $ACCOUNT_PLAYLIST_CONTENT_AUTH_REQUIRED_CASE; ownership and validation branches covered",
        ),
        full(
            "DELETE",
            "/api/playlists/{id}/recordings/{recordingId}",
            testRef = PLAYLIST_OWNER_SCOPE_CASE,
            note = "auth: $ACCOUNT_PLAYLIST_CONTENT_AUTH_REQUIRED_CASE; idempotent remove; ownership: non-owner returns 404",
        ),
        full(
            "PUT",
            "/api/playlists/{id}/recordings/{recordingId}",
            testRef = PLAYLIST_OWNER_SCOPE_CASE,
            note = "auth: $ACCOUNT_PLAYLIST_CONTENT_AUTH_REQUIRED_CASE; idempotent add; ownership: non-owner returns 404",
        ),
        full(
            "GET",
            "/api/recordings/{id}",
            testRef = CONTENT_SEARCH_UPDATE_MERGE_CASE,
            note = "auth: $ACCOUNT_PLAYLIST_CONTENT_AUTH_REQUIRED_CASE; missing resource returns 404",
        ),
        full(
            "PUT",
            "/api/recordings/{id}",
            testRef = CONTENT_SEARCH_UPDATE_MERGE_CASE,
            note = "auth: $ACCOUNT_PLAYLIST_CONTENT_AUTH_REQUIRED_CASE",
        ),
        full(
            "PUT",
            "/api/recordings/merge",
            testRef = CONTENT_SEARCH_UPDATE_MERGE_CASE,
            note = "auth: $ACCOUNT_PLAYLIST_CONTENT_AUTH_REQUIRED_CASE; merge: source recording removed and relations moved to target",
        ),
        full(
            "GET",
            "/api/plugins",
            testRef = PLUGIN_LIFECYCLE_CASE,
            note = "error: invalid stored form metadata returns 500",
        ),
        full(
            "POST",
            "/api/plugins/upload",
            testRef = PLUGIN_LIFECYCLE_CASE,
            note = "validation: $PLUGIN_UPLOAD_INVALID_CASE",
        ),
        full(
            "PUT",
            "/api/plugins/{id}/enabled",
            testRef = PLUGIN_LIFECYCLE_CASE,
            note = "error: missing plugin returns 404",
        ),
        full(
            "DELETE",
            "/api/plugins/{id}",
            testRef = PLUGIN_LIFECYCLE_CASE,
            note = "error: missing plugin returns 404",
        ),
        full(
            "GET",
            "/api/plugins/{id}/download",
            testRef = PLUGIN_LIFECYCLE_CASE,
            note = "error: deleted plugin returns 404",
        ),
        full(
            "POST",
            "/api/plugins/{taskType}/submit",
            testRef = PLUGIN_LIFECYCLE_CASE,
            note = "validation: unknown task type returns 400; error: disabled plugin task returns 400",
        ),
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
        full(
            "GET",
            "/api/task/logs",
            testRef = TASK_SCAN_LIFECYCLE_CASE,
            note = "auth: $TASK_CONTENT_AUTH_REQUIRED_CASE; stats: returns taskType/status counts without pagination",
        ),
        full(
            "POST",
            "/api/task/scan",
            testRef = TASK_SCAN_LIFECYCLE_CASE,
            note = "auth: $TASK_CONTENT_AUTH_REQUIRED_CASE; duplicate: repeated submission returns 202 and only supplements missing file tasks",
        ),
        full(
            "POST",
            "/api/task/transcode",
            testRef = TASK_TRANSCODE_SUCCESS_CASE,
            note = "auth: $TASK_CONTENT_AUTH_REQUIRED_CASE; stats: pending count drains into completed or failed; output: writes opus files for prepared fixture",
        ),
        full(
            "GET",
            "/api/works",
            testRef = WORK_ALBUM_READ_CASE,
            note = "auth: $TASK_CONTENT_AUTH_REQUIRED_CASE",
        ),
        full(
            "GET",
            "/api/works/{id}",
            testRef = WORK_ALBUM_READ_CASE,
            note = "auth: $TASK_CONTENT_AUTH_REQUIRED_CASE",
        ),
        full(
            "GET",
            "/api/works/random",
            testRef = WORK_ALBUM_READ_CASE,
            note = "auth: $TASK_CONTENT_AUTH_REQUIRED_CASE; validation: length<=0 returns 400",
        ),
        full(
            "DELETE",
            "/api/works/{id}",
            testRef = WORK_ALBUM_READ_CASE,
            note = "auth: $TASK_CONTENT_AUTH_REQUIRED_CASE",
        ),
        full(
            "PUT",
            "/api/works/{id}",
            testRef = CONTENT_SEARCH_UPDATE_MERGE_CASE,
            note = "auth: $ACCOUNT_PLAYLIST_CONTENT_AUTH_REQUIRED_CASE",
        ),
        full(
            "POST",
            "/api/works/merge",
            testRef = CONTENT_SEARCH_UPDATE_MERGE_CASE,
            note = "auth: $ACCOUNT_PLAYLIST_CONTENT_AUTH_REQUIRED_CASE; merge: source work removed and recordings moved to target",
        ),
        full(
            "GET",
            "/api/works/search",
            testRef = CONTENT_SEARCH_UPDATE_MERGE_CASE,
            note = "auth: $ACCOUNT_PLAYLIST_CONTENT_AUTH_REQUIRED_CASE; validation: unknown keyword returns empty array",
        ),
        full(
            "GET",
            "/api/albums",
            testRef = WORK_ALBUM_READ_CASE,
            note = "auth: $TASK_CONTENT_AUTH_REQUIRED_CASE",
        ),
        full(
            "GET",
            "/api/albums/{id}",
            testRef = WORK_ALBUM_READ_CASE,
            note = "auth: $TASK_CONTENT_AUTH_REQUIRED_CASE",
        ),
        full(
            "PUT",
            "/api/albums/{id}",
            testRef = ALBUM_UPDATE_CASE,
            note = "auth: $ACCOUNT_PLAYLIST_CONTENT_AUTH_REQUIRED_CASE",
        ),
        full(
            "GET",
            "/api/media/{id}",
            condition = "!Range",
            testRef = MEDIA_READ_CASE,
            note = "auth: $TASK_CONTENT_AUTH_REQUIRED_CASE; error: unknown id returns 404",
        ),
        full(
            "GET",
            "/api/media/{id}",
            condition = "Range",
            testRef = MEDIA_READ_CASE,
            note = "auth: $TASK_CONTENT_AUTH_REQUIRED_CASE; error: invalid range returns 416",
        ),
        full(
            "HEAD",
            "/api/media/{id}",
            condition = "!Range",
            testRef = MEDIA_READ_CASE,
            note = "auth: $TASK_CONTENT_AUTH_REQUIRED_CASE",
        ),
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
            mark = CoverageMark(level = CoverageLevel.FULL, testRef = testRef, note = businessNote(note)),
        )
    }

    private fun businessNote(note: String): String {
        return note
            .replace(Regex("^auth: [^;]+;\\s*"), "")
            .replace(Regex("^auth: [^;]+$"), "")
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
