package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.service.AccountService
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Controller
@RestController
@RequestMapping("/api/token")
class TokenController(private val service: AccountService) {

    @GetMapping
    fun login(email: String, password: String) {
        return service.checkChallenge(email, password)
    }

    @SaCheckLogin
    @DeleteMapping
    fun logout() {
        service.logout()
    }
}
