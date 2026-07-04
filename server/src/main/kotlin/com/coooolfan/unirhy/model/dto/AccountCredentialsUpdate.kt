package com.coooolfan.unirhy.model.dto

/**
 * 账户凭据更新请求
 *
 * 修改密码或邮箱等登录凭据时使用；
 * 本人操作必须提供 [currentPassword] 以验证身份，管理员重置他人凭据时无需提供
 */
data class AccountCredentialsUpdate(
    val currentPassword: String? = null,
    val password: String? = null,
    val email: String? = null,
)
