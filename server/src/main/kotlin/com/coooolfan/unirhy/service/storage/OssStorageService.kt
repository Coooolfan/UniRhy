package com.coooolfan.unirhy.service.storage

import com.coooolfan.unirhy.error.SystemException
import com.coooolfan.unirhy.model.SystemConfig
import com.coooolfan.unirhy.model.storage.FileProviderOss
import com.coooolfan.unirhy.model.storage.dto.FileProviderOssCreate
import com.coooolfan.unirhy.service.SystemConfigService.Companion.SYSTEM_CONFIG_ID
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.springframework.stereotype.Service

@Service
class OssStorageService(private val sql: KSqlClient) {

    fun list(fetcher: Fetcher<FileProviderOss>): List<FileProviderOss> {
        return sql.findAll(fetcher)
    }

    fun get(id: Long, fetcher: Fetcher<FileProviderOss>): FileProviderOss {
        return sql.findOneById(fetcher, id)
    }

    fun create(create: FileProviderOssCreate, fetcher: Fetcher<FileProviderOss>): FileProviderOss {
        val entity = create.toEntity()
        return sql.saveCommand(entity, SaveMode.INSERT_ONLY).execute(fetcher).modifiedEntity
    }

    fun update(oss: FileProviderOss, fetcher: Fetcher<FileProviderOss>): FileProviderOss {
        return sql.saveCommand(oss, SaveMode.UPDATE_ONLY).execute(fetcher).modifiedEntity
    }

    fun delete(id: Long) {
        if (id == sql.findOneById(SystemConfig::class, SYSTEM_CONFIG_ID).ossProviderId)
            throw SystemException.SystemStorageProviderCannotBeDeleted()

        sql.deleteById(FileProviderOss::class, id)
    }
}
