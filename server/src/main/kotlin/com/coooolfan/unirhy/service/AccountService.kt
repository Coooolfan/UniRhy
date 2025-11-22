package com.coooolfan.unirhy.service

import cn.dev33.satoken.stp.StpUtil
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.springframework.stereotype.Service

@Service
class AccountService(private val sql: KSqlClient) {
    fun checkChallenge(email: String, password: String): Boolean {
        if (email == "admin" && password == "c") {
            StpUtil.login(1L)
            return true
        }
        return false
    }
}