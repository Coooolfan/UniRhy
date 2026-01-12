package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.service.AccountService
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 登录与登出接口
 *
 * 提供账户登录与退出能力
 */
@Controller
@RestController
@RequestMapping("/api/token")
class TokenController(private val service: AccountService) {

    /**
     * 登录并创建会话
     *
     * 此接口用于校验账户凭据并创建登录会话
     * 无需登录认证即可访问
     *
     * @param email 登录邮箱
     * @param password 登录密码
     *
     * @api GET /api/token
     * @permission 无需登录认证
     * @description 调用AccountService.checkChallenge()方法进行登录校验
     */
    @GetMapping
    fun login(email: String, password: String) {
        return service.checkChallenge(email, password)
    }

    /**
     * 退出当前会话
     *
     * 此接口用于注销当前登录会话
     * 需要用户登录认证才能访问
     *
     * @api DELETE /api/token
     * @permission 需要登录认证
     * @description 调用AccountService.logout()方法退出登录
     */
    @SaCheckLogin
    @DeleteMapping
    fun logout() {
        service.logout()
    }
}
