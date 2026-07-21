package com.coooolfan.unirhy.error

import org.babyfish.jimmer.error.ErrorField
import org.babyfish.jimmer.error.ErrorFamily

@ErrorFamily
enum class CommonErrorCode {
    NOT_FOUND,
    INVALID_REQUEST,
    AUTHENTICATION_FAILED, // 登录失败
    FORBIDDEN, // 没有权限
    INTERNAL_ERROR, // 未被业务异常覆盖的兜底错误
}

@ErrorFamily
enum class SystemErrorCode {
    SYSTEM_UNINITIALIZED,
    SYSTEM_ALREADY_INITIALIZED,
    SYSTEM_STORAGE_PROVIDER_CANNOT_BE_DELETED,
    SYSTEM_STORAGE_PROVIDER_CANNOT_BE_READONLY,
    SYSTEM_STORAGE_PROVIDER_MUST_BE_SINGLE,
}

@ErrorFamily
enum class AccountErrorCode {
    CREDENTIAL_UPDATE_REQUIRED,
}

@ErrorFamily
enum class AlbumErrorCode {
    NOT_FOUND,
    RECORDING_IDS_CONTAIN_DUPLICATES,
    RECORDING_IDS_MISMATCH,
}

@ErrorFamily
enum class ArtistErrorCode {
    TARGET_NOT_FOUND,
    SOURCE_NOT_FOUND,
}

@ErrorFamily
enum class MediaFileErrorCode {
    NOT_FOUND,
    INVALID_STORAGE_PROVIDER,
    FILE_NOT_FOUND,
}

@ErrorFamily
enum class PlaylistErrorCode {
    NOT_FOUND,
    RECORDING_IDS_CONTAIN_DUPLICATES,
    RECORDING_IDS_MISMATCH,
}

@ErrorFamily
enum class RecordingErrorCode {
    NOT_FOUND,
    TARGET_NOT_FOUND,
    WORK_MISMATCH,
}

@ErrorFamily
enum class WorkErrorCode {
    INVALID_RANDOM_LENGTH,
}

@ErrorFamily
enum class PlaybackQueueErrorCode {
    RECORDING_IDS_EMPTY,
    INDEX_NOT_FOUND,
    EMPTY_QUEUE_INDEX_INVALID,
    CURRENT_INDEX_OUT_OF_RANGE,

    @ErrorField(name = "recordingId", type = Long::class)
    RECORDING_NOT_FOUND,

    @ErrorField(name = "recordingId", type = Long::class)
    RECORDING_NOT_PLAYABLE,

    VERSION_CONFLICT,
}

@ErrorFamily
enum class PluginErrorCode {
    NOT_FOUND,
    PACKAGE_TOO_LARGE,
    WASM_TOO_LARGE,
    MANIFEST_MISSING,
    WASM_MISSING,

    @ErrorField(name = "reason", type = String::class)
    INVALID_MANIFEST,

    UNSUPPORTED_RUNTIME,
    UNSUPPORTED_ABI,

    @ErrorField(name = "reason", type = String::class)
    LOAD_FAILED,

    INVALID_CONCURRENCY,

    /** 删除前置条件不满足：插件仍启用，或存在活动 submission / task */
    DELETE_CONFLICT,
}

@ErrorFamily
enum class TaskErrorCode {
    SUBMISSION_NOT_FOUND,
    TASK_NOT_FOUND,
    DEFINITION_NOT_FOUND,

    @ErrorField(name = "reason", type = String::class)
    INVALID_TASK_KEY,

    @ErrorField(name = "reason", type = String::class)
    INVALID_PARAMS,

    /** 插件已安装但被禁用或当前不可用 */
    PLUGIN_UNAVAILABLE,

    /** 非法状态迁移，或资源正被 Worker 锁定 */
    STATUS_CONFLICT,

    /** submission 或其子任务存在非终态记录，拒绝删除 */
    DELETE_CONFLICT,
}
