package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.model.SystemConfig
import com.coooolfan.unirhy.model.dto.SystemConfigCreate
import com.coooolfan.unirhy.model.dto.SystemConfigUpdate
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
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

    companion object {
        private const val SYSTEM_CONFIG_ID = 0L
    }
}