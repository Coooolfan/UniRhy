package com.coooolfan.unirhy.error

import org.babyfish.jimmer.error.ErrorFamily

@ErrorFamily
enum class CommonErrorCode {
    NOT_FOUND,
    AUTHENTICATION_FAILED, // 登录失败
    FORBIDDEN, // 没有权限
}

@ErrorFamily
enum class SystemErrorCode {
    SYSTEM_UNINITIALIZED,
    SYSTEM_ALREADY_INITIALIZED,
}