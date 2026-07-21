package com.coooolfan.unirhy.error

import cn.dev33.satoken.exception.NotLoginException
import cn.dev33.satoken.exception.NotRoleException
import cn.dev33.satoken.stp.StpUtil
import org.babyfish.jimmer.error.CodeBasedRuntimeException
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(CodeBasedRuntimeException::class)
    fun handle(ex: CodeBasedRuntimeException): ResponseEntity<Map<String, Any>> {
        val statusCode = when ("${ex.family}:${ex.code}") {
            "COMMON:NOT_FOUND",
            "ALBUM:NOT_FOUND",
            "ARTIST:TARGET_NOT_FOUND",
            "ARTIST:SOURCE_NOT_FOUND",
            "MEDIA_FILE:NOT_FOUND",
            "MEDIA_FILE:FILE_NOT_FOUND",
            "PLAYLIST:NOT_FOUND",
            "RECORDING:NOT_FOUND",
            "RECORDING:TARGET_NOT_FOUND",
            "PLAYBACK_QUEUE:INDEX_NOT_FOUND",
            "PLUGIN:NOT_FOUND",
            "TASK:SUBMISSION_NOT_FOUND",
            "TASK:TASK_NOT_FOUND",
            "TASK:DEFINITION_NOT_FOUND",
            -> 404
            "COMMON:AUTHENTICATION_FAILED" -> 401
            "COMMON:FORBIDDEN",
            "MEDIA_FILE:INVALID_STORAGE_PROVIDER",
            -> 403
            "PLUGIN:PACKAGE_TOO_LARGE",
            "PLUGIN:WASM_TOO_LARGE",
            -> 413
            "SYSTEM:SYSTEM_UNINITIALIZED",
            "SYSTEM:SYSTEM_ALREADY_INITIALIZED",
            "SYSTEM:SYSTEM_STORAGE_PROVIDER_CANNOT_BE_DELETED",
            "SYSTEM:SYSTEM_STORAGE_PROVIDER_CANNOT_BE_READONLY",
            "SYSTEM:SYSTEM_STORAGE_PROVIDER_MUST_BE_SINGLE",
            "PLAYBACK_QUEUE:RECORDING_NOT_FOUND",
            "PLAYBACK_QUEUE:RECORDING_NOT_PLAYABLE",
            "PLAYBACK_QUEUE:VERSION_CONFLICT",
            "PLUGIN:DELETE_CONFLICT",
            "TASK:PLUGIN_UNAVAILABLE",
            "TASK:STATUS_CONFLICT",
            "TASK:DELETE_CONFLICT",
            -> 409
            "COMMON:INVALID_REQUEST",
            "ACCOUNT:CREDENTIAL_UPDATE_REQUIRED",
            "ALBUM:RECORDING_IDS_CONTAIN_DUPLICATES",
            "ALBUM:RECORDING_IDS_MISMATCH",
            "PLAYLIST:RECORDING_IDS_CONTAIN_DUPLICATES",
            "PLAYLIST:RECORDING_IDS_MISMATCH",
            "RECORDING:WORK_MISMATCH",
            "WORK:INVALID_RANDOM_LENGTH",
            "PLAYBACK_QUEUE:RECORDING_IDS_EMPTY",
            "PLAYBACK_QUEUE:EMPTY_QUEUE_INDEX_INVALID",
            "PLAYBACK_QUEUE:CURRENT_INDEX_OUT_OF_RANGE",
            "PLUGIN:MANIFEST_MISSING",
            "PLUGIN:WASM_MISSING",
            "PLUGIN:INVALID_MANIFEST",
            "PLUGIN:UNSUPPORTED_RUNTIME",
            "PLUGIN:UNSUPPORTED_ABI",
            "PLUGIN:LOAD_FAILED",
            "PLUGIN:INVALID_CONCURRENCY",
            "TASK:INVALID_TASK_KEY",
            "TASK:INVALID_PARAMS",
            -> 400
            else -> {
                ex.printStackTrace()
                500 // Internal Server Error
            }
        }
        return ResponseEntity
            .status(statusCode)
            .body(resultMap(ex))
    }

    @ExceptionHandler(
        MethodArgumentTypeMismatchException::class,
        HttpMessageNotReadableException::class,
    )
    fun handleInvalidRequest(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity
            .status(400)
            .body(resultMap(CommonException.InvalidRequest()))
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNotFound(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity
            .status(404)
            .body(resultMap(CommonException.NotFound()))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<Map<String, Any>> {
        ex.printStackTrace()
        return ResponseEntity
            .status(500)
            .body(resultMap(CommonException.InternalError()))
    }

    @ExceptionHandler(NotLoginException::class)
    fun handleAuthenticationFailed(): ResponseEntity<Any>? {
        StpUtil.logout()
        return ResponseEntity
            .status(401)
            .body(resultMap(CommonException.AuthenticationFailed()))
    }

    @ExceptionHandler(NotRoleException::class)
    fun handleNotRole(): ResponseEntity<Any> {
        return ResponseEntity
            .status(403)
            .body(resultMap(CommonException.Forbidden()))
    }

    private fun resultMap(ex: CodeBasedRuntimeException): Map<String, Any> {
        val resultMap: MutableMap<String, Any> = LinkedHashMap()
        resultMap["family"] = ex.family
        resultMap["code"] = ex.code
        ex.fields.forEach { (key, value) -> if (value != null) resultMap[key] = value }
        return resultMap
    }

}
