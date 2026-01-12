package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.model.Account
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.model.dto.AccountCreate
import com.coooolfan.unirhy.model.dto.AccountUpdate
import com.coooolfan.unirhy.service.AccountService
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 账户管理接口
 *
 * 提供账户的增删改查与当前登录账户信息获取能力
 */
@RestController
@RequestMapping("/api/account")
class AccountController(private val service: AccountService) {
    /**
     * 获取账户列表
     *
     * 此接口用于获取系统中所有账户信息
     * 需要用户登录认证才能访问
     *
     * @return List<Account> 返回账户列表（默认 fetcher）
     *
     * @api GET /api/account
     * @permission 需要登录认证
     * @description 调用AccountService.list()方法获取账户列表
     */
    @SaCheckLogin
    @GetMapping
    fun list(): List<@FetchBy("DEFAULT_ACCOUNT_FETCHER") Account> {
        return service.list(DEFAULT_ACCOUNT_FETCHER)
    }

    /**
     * 获取当前登录账户信息
     *
     * 此接口用于获取当前会话对应的账户详情
     * 需要用户登录认证才能访问
     *
     * @return Account 返回当前账户信息（默认 fetcher）
     *
     * @api GET /api/account/me
     * @permission 需要登录认证
     * @description 调用AccountService.me()方法获取当前账户信息
     */
    @SaCheckLogin
    @GetMapping("/me")
    fun me(): @FetchBy("DEFAULT_ACCOUNT_FETCHER") Account {
        return service.me(DEFAULT_ACCOUNT_FETCHER)
    }

    /**
     * 删除指定账户
     *
     * 此接口用于删除指定ID的账户
     * 需要用户登录认证才能访问
     *
     * @param id 账户 ID
     *
     * @api DELETE /api/account/{id}
     * @permission 需要登录认证
     * @description 调用AccountService.delete()方法删除账户
     */
    @SaCheckLogin
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) {
        service.delete(id)
    }

    /**
     * 创建账户
     *
     * 此接口用于创建新账户
     * 需要用户登录认证才能访问
     *
     * @param create 创建参数
     * @return Account 返回创建后的账户（默认 fetcher）
     *
     * @api POST /api/account
     * @permission 需要登录认证
     * @description 调用AccountService.create()方法创建账户
     */
    @SaCheckLogin
    @PostMapping
    fun create(create: AccountCreate): @FetchBy("DEFAULT_ACCOUNT_FETCHER") Account {
        return service.create(create, DEFAULT_ACCOUNT_FETCHER)
    }

    /**
     * 初始化首个管理员账户
     *
     * 此接口用于系统首次初始化时创建管理员账户
     * 无需登录认证即可访问
     *
     * @param create 创建参数
     * @return Account 返回创建后的账户（默认 fetcher）
     *
     * @api POST /api/account/first
     * @permission 无需登录认证
     * @description 调用AccountService.initFirstAccount()方法初始化管理员账户
     */
    @PostMapping("/first")
    fun createFirst(create: AccountCreate): @FetchBy("DEFAULT_ACCOUNT_FETCHER") Account {
        return service.initFirstAccount(create.toEntity { this.admin = true }, DEFAULT_ACCOUNT_FETCHER)
    }

    /**
     * 更新指定账户
     *
     * 此接口用于更新指定ID的账户信息
     * 需要用户登录认证才能访问
     *
     * @param id 账户 ID
     * @param update 更新参数
     * @return Account 返回更新后的账户（默认 fetcher）
     *
     * @api PUT /api/account/{id}
     * @permission 需要登录认证
     * @description 调用AccountService.update()方法更新账户信息
     */
    @SaCheckLogin
    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, update: AccountUpdate): @FetchBy("DEFAULT_ACCOUNT_FETCHER") Account {
        return service.update(update.toEntity { this.id = id }, DEFAULT_ACCOUNT_FETCHER)
    }

    companion object {
        private val DEFAULT_ACCOUNT_FETCHER = newFetcher(Account::class).by {
            allScalarFields()
            password(false)
        }
    }
}
