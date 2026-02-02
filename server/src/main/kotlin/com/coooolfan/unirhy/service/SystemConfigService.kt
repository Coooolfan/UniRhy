package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.model.Account
import com.coooolfan.unirhy.model.SystemConfig
import com.coooolfan.unirhy.model.admin
import com.coooolfan.unirhy.model.dto.SystemConfigCreate
import com.coooolfan.unirhy.model.dto.SystemConfigUpdate
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.springframework.stereotype.Service

@Service
class SystemConfigService(private val sql: KSqlClient) {

    fun get(fetcher: Fetcher<SystemConfig>): SystemConfig {
        return sql.findOneById(fetcher, SYSTEM_CONFIG_ID)
    }

    fun create(create: SystemConfigCreate, fetcher: Fetcher<SystemConfig>): SystemConfig {
        val entity = create.toEntity { id = SYSTEM_CONFIG_ID }
        return sql.saveCommand(entity, SaveMode.INSERT_ONLY).execute(fetcher).modifiedEntity
    }

    fun update(update: SystemConfigUpdate, fetcher: Fetcher<SystemConfig>): SystemConfig {
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
        private const val SYSTEM_CONFIG_ID = 0L
    }
}
