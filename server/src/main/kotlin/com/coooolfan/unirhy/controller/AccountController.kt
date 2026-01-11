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

@RestController
@RequestMapping("/api/account")
class AccountController(private val service: AccountService) {
    @SaCheckLogin
    @GetMapping
    fun list(): List<@FetchBy("DEFAULT_ACCOUNT_FETCHER") Account> {
        return service.list(DEFAULT_ACCOUNT_FETCHER)
    }

    @SaCheckLogin
    @GetMapping("/me")
    fun me(): @FetchBy("DEFAULT_ACCOUNT_FETCHER") Account {
        return service.me(DEFAULT_ACCOUNT_FETCHER)
    }

    @SaCheckLogin
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) {
        service.delete(id)
    }

    @SaCheckLogin
    @PostMapping
    fun create(create: AccountCreate): @FetchBy("DEFAULT_ACCOUNT_FETCHER") Account {
        return service.create(create, DEFAULT_ACCOUNT_FETCHER)
    }

    @PostMapping("/first")
    fun createFirst(create: AccountCreate): @FetchBy("DEFAULT_ACCOUNT_FETCHER") Account {
        return service.initFirstAccount(create.toEntity { this.admin = true }, DEFAULT_ACCOUNT_FETCHER)
    }

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
