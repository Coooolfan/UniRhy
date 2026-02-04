package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.config.encodePassword
import com.coooolfan.unirhy.error.SystemException
import com.coooolfan.unirhy.model.Account
import com.coooolfan.unirhy.model.SystemConfig
import com.coooolfan.unirhy.model.admin
import com.coooolfan.unirhy.model.dto.SystemConfigUpdate
import com.coooolfan.unirhy.model.dto.SystemInitReq
import com.coooolfan.unirhy.model.storage.FileProviderFileSystem
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SystemConfigService(
    private val sql: KSqlClient,
    private val passwordEncoder: PasswordEncoder,
) {

    fun get(fetcher: Fetcher<SystemConfig>): SystemConfig {
        return sql.findOneById(fetcher, SYSTEM_CONFIG_ID)
    }

    @Transactional(rollbackFor = [Exception::class])
    fun create(create: SystemInitReq) {

        val adminAccount = Account {
            name = create.adminAccountName
            email = create.adminAccountEmail
            admin = true
            password = passwordEncoder.encodePassword(create.adminPassword)
        }
        val storageProvider = FileProviderFileSystem {
            id = SYSTEM_CONFIG_ID
            name = "Default"
            parentPath = create.storageProviderPath
            readonly = false
        }

        try {
            sql.saveCommand(adminAccount, SaveMode.INSERT_ONLY).execute()
            sql.saveCommand(storageProvider, SaveMode.INSERT_ONLY).execute()
            sql.saveCommand(SystemConfig {
                id = SYSTEM_CONFIG_ID
                ossProvider = null
                fsProvider = storageProvider
            }, SaveMode.INSERT_ONLY).execute()
        } catch (e: Exception) {
            throw SystemException.SystemAlreadyInitialized()
        }

    }

    fun update(update: SystemConfigUpdate, fetcher: Fetcher<SystemConfig>): SystemConfig {
        if(update.fsProviderId == null)
            throw SystemException.SystemStorageProviderCannotBeRemote()

        if(sql.findOneById(FileProviderFileSystem::class,update.fsProviderId).readonly)
            throw SystemException.SystemStorageProviderCannotBeReadonly()

        val entity = update.toEntity { id = SYSTEM_CONFIG_ID }
        return sql.saveCommand(entity, SaveMode.UPDATE_ONLY).execute(fetcher).modifiedEntity
    }

    fun initialized(): Boolean {
        return sql.executeQuery(Account::class) {
            where(table.admin eq true)
            selectCount()
        }.first() > 0L
    }

    companion object {
        const val SYSTEM_CONFIG_ID = 0L
    }
}
