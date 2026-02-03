package com.coooolfan.unirhy.service

import cn.dev33.satoken.stp.StpUtil
import com.coooolfan.unirhy.config.encodePassword
import com.coooolfan.unirhy.error.CommonException
import com.coooolfan.unirhy.error.SystemException
import com.coooolfan.unirhy.model.Account
import com.coooolfan.unirhy.model.dto.AccountCreate
import com.coooolfan.unirhy.model.email
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

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

        StpUtil.login(account.id)
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
        val encodedAccount = Account(account) {
            this.password = passwordEncoder.encodePassword(account.password)
        }
        return sql.saveCommand(encodedAccount, SaveMode.UPDATE_ONLY).execute(fetcher).modifiedEntity
    }

    fun delete(id: Long) {
        sql.deleteById(Account::class, id)
    }
}
