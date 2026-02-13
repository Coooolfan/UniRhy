package com.coooolfan.unirhy.error

import cn.dev33.satoken.exception.NotLoginException
import cn.dev33.satoken.stp.StpUtil
import org.babyfish.jimmer.error.CodeBasedRuntimeException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(CodeBasedRuntimeException::class)
    fun handle(ex: CodeBasedRuntimeException): ResponseEntity<Map<String, Any>> {
        val statusCode = when (ex.code) {
            SystemErrorCode.SYSTEM_UNINITIALIZED.name,
            SystemErrorCode.SYSTEM_ALREADY_INITIALIZED.name,
            SystemErrorCode.SYSTEM_STORAGE_PROVIDER_CANNOT_BE_DELETED.name,
            SystemErrorCode.SYSTEM_STORAGE_PROVIDER_CANNOT_BE_READONLY.name,
            SystemErrorCode.SYSTEM_STORAGE_PROVIDER_CANNOT_BE_REMOTE.name,
            -> 409
            CommonErrorCode.NOT_FOUND.name -> 404
            CommonErrorCode.AUTHENTICATION_FAILED.name -> 401
            CommonErrorCode.FORBIDDEN.name -> 403
            else -> {
                ex.printStackTrace()
                500 // Internal Server Error
            }
        }
        return ResponseEntity
            .status(statusCode)
            .body(resultMap(ex))
    }


    @ExceptionHandler(NotLoginException::class)
    fun handleAuthenticationFailed(): ResponseEntity<Any>? {
        StpUtil.logout()
        return ResponseEntity
            .status(401)
            .body(resultMap(CommonException.AuthenticationFailed()))
    }

    private fun resultMap(ex: CodeBasedRuntimeException): Map<String, Any> {
        val resultMap: MutableMap<String, Any> = LinkedHashMap()
        resultMap["family"] = ex.family
        resultMap["code"] = ex.code
        return resultMap
    }

}
