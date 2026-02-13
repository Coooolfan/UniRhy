package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.error.CommonException
import com.coooolfan.unirhy.model.dto.TokenLoginRequest
import com.coooolfan.unirhy.service.AccountService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import kotlin.jvm.Throws

/**
 * 登录与登出接口
 *
 * 提供账户登录与退出能力
 */
@RestController
@RequestMapping("/api/tokens")
class TokenController(private val service: AccountService) {

    /**
     * 登录并创建会话
     *
     * 此接口用于校验账户凭据并创建登录会话
     * 无需登录认证即可访问
     *
     * @param request 登录请求参数
     *
     * @api POST /api/tokens
     * @permission 无需登录认证
     * @description 调用AccountService.checkChallenge()方法进行登录校验
     */
    @PostMapping
    @Throws(CommonException.AuthenticationFailed::class)
    fun login(@RequestBody request: TokenLoginRequest) {
        service.checkChallenge(request.email, request.password)
    }

    /**
     * 退出当前会话
     *
     * 此接口用于注销当前登录会话
     * 需要用户登录认证才能访问
     *
     * @api DELETE /api/tokens/current
     * @permission 需要登录认证
     * @description 调用AccountService.logout()方法退出登录
     */
    @SaCheckLogin
    @DeleteMapping("/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout() {
        service.logout()
    }
}
