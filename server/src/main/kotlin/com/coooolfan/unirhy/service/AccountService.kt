package com.coooolfan.unirhy.service

import cn.dev33.satoken.stp.parameter.SaLoginParameter
import cn.dev33.satoken.stp.StpUtil
import com.coooolfan.unirhy.config.ROLE_ADMIN
import com.coooolfan.unirhy.config.encodePassword
import com.coooolfan.unirhy.error.CommonException
import com.coooolfan.unirhy.model.Account
import com.coooolfan.unirhy.model.dto.AccountCreate
import com.coooolfan.unirhy.model.dto.AccountCredentialsUpdate
import com.coooolfan.unirhy.model.email
import org.babyfish.jimmer.kt.isLoaded
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class AccountService(
    private val sql: KSqlClient,
    private val passwordEncoder: PasswordEncoder,
) {

    fun checkChallenge(email: String, password: String) {
        val account = sql.executeQuery(Account::class) {
            where(table.email eq email)
            select(table)
        }.firstOrNull()
        if (account == null || !passwordEncoder.matches(password, account.password)) {
            throw CommonException.AuthenticationFailed()
        }

        StpUtil.login(account.id, SaLoginParameter().setExtra(ROLE_ADMIN, account.admin))
    }

    fun logout() = try {
        StpUtil.logout()
    } catch (_: Exception) {
    }

    fun list(fetcher: Fetcher<Account>): List<Account> {
        return sql.findAll(fetcher)
    }

    fun me(fetcher: Fetcher<Account>): Account {
        val accountId = StpUtil.getLoginIdAsLong()
        return sql.findOneById(fetcher, accountId)
    }

    fun create(create: AccountCreate, fetcher: Fetcher<Account>): Account {
        val rawPassword = create.password
        val entity = create.toEntity {
            password = passwordEncoder.encodePassword(rawPassword)
        }
        return sql.saveCommand(entity, SaveMode.INSERT_ONLY).execute(fetcher).modifiedEntity
    }

    fun update(account: Account, fetcher: Fetcher<Account>): Account {

        val currentLoginId = StpUtil.getLoginIdAsLong()

        val isAdmin = sql.findOneById(Account::class, currentLoginId).admin

        if (account.id != currentLoginId && !isAdmin) {
            throw CommonException.Forbidden()
        }

        if (isLoaded(account, Account::preferences) && account.id != currentLoginId) {
            throw CommonException.Forbidden()
        }

        return sql.saveCommand(account, SaveMode.UPDATE_ONLY).execute(fetcher).modifiedEntity
    }

    fun updateCredentials(id: Long, update: AccountCredentialsUpdate, fetcher: Fetcher<Account>): Account {
        if (update.password == null && update.email == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "password or email is required")
        }

        val currentLoginId = StpUtil.getLoginIdAsLong()
        val operator = sql.findOneById(Account::class, currentLoginId)

        if (id != currentLoginId && !operator.admin) {
            throw CommonException.Forbidden()
        }

        // 本人修改凭据必须先验证当前密码，防止被劫持的会话直接夺取账号
        if (id == currentLoginId &&
            !passwordEncoder.matches(update.currentPassword.orEmpty(), operator.password)
        ) {
            throw CommonException.AuthenticationFailed()
        }

        val entity = Account {
            this.id = id
            update.password?.let { this.password = passwordEncoder.encodePassword(it) }
            update.email?.let { this.email = it }
        }
        return sql.saveCommand(entity, SaveMode.UPDATE_ONLY).execute(fetcher).modifiedEntity
    }

    fun delete(id: Long) {
        sql.deleteById(Account::class, id)
    }
}
