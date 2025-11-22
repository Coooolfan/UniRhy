package com.coooolfan.unirhy.controller

import com.coooolfan.unirhy.service.AccountService
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Controller
@RestController
@RequestMapping("/api/token")
class TokenController(private val service: AccountService) {

    @GetMapping
    fun login(email: String, password: String): Boolean {
        return service.checkChallenge(email, password)
    }
}