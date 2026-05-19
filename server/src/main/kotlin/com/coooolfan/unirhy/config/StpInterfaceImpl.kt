package com.coooolfan.unirhy.config

import cn.dev33.satoken.stp.StpInterface
import cn.dev33.satoken.stp.StpUtil
import org.springframework.stereotype.Component

const val ROLE_ADMIN = "admin"

@Component
class StpInterfaceImpl : StpInterface {
    override fun getRoleList(loginId: Any?, loginType: String?): List<String> {
        val isAdmin = StpUtil.getExtra(ROLE_ADMIN) as? Boolean ?: false
        return if (isAdmin) listOf(ROLE_ADMIN) else emptyList()
    }

    override fun getPermissionList(loginId: Any?, loginType: String?): List<String> = emptyList()
}
