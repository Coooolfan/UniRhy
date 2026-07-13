package com.coooolfan.unirhy.error

import org.babyfish.jimmer.error.ErrorField
import org.babyfish.jimmer.error.ErrorFamily

@ErrorFamily
enum class CommonErrorCode {
    NOT_FOUND,
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
enum class PluginErrorCode {
    @ErrorField(name = "taskType", type = String::class)
    UNKNOWN_TASK_TYPE,
    INVALID_TASK_PARAMS,
}
