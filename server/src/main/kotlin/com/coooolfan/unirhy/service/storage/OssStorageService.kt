package com.coooolfan.unirhy.service.storage

import com.coooolfan.unirhy.model.storage.FileProviderOss
import com.coooolfan.unirhy.model.storage.dto.FileProviderOssCreate
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
        sql.deleteById(FileProviderOss::class, id)
    }
}
