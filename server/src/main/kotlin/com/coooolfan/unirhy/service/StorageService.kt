package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.model.storage.FileProviderFileSystem
import com.coooolfan.unirhy.model.storage.FileProviderOss
import com.coooolfan.unirhy.model.storage.dto.FileProviderFileSystemCreate
import com.coooolfan.unirhy.model.storage.dto.FileProviderOssCreate
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.springframework.stereotype.Service

@Service
class StorageService(private val sql: KSqlClient) {

    fun listOss(fetcher: Fetcher<FileProviderOss>): List<FileProviderOss> {
        return sql.findAll(fetcher)
    }

    fun getOss(id: Long, fetcher: Fetcher<FileProviderOss>): FileProviderOss {
        return sql.findOneById(fetcher, id)
    }

    fun createOss(create: FileProviderOssCreate, fetcher: Fetcher<FileProviderOss>): FileProviderOss {
        val entity = create.toEntity()
        return sql.saveCommand(entity, SaveMode.INSERT_ONLY).execute(fetcher).modifiedEntity
    }

    fun updateOss(oss: FileProviderOss, fetcher: Fetcher<FileProviderOss>): FileProviderOss {
        return sql.saveCommand(oss, SaveMode.UPDATE_ONLY).execute(fetcher).modifiedEntity
    }

    fun deleteOss(id: Long) {
        sql.deleteById(FileProviderOss::class, id)
    }

    fun listFileSystem(fetcher: Fetcher<FileProviderFileSystem>): List<FileProviderFileSystem> {
        return sql.findAll(fetcher)
    }

    fun getFileSystem(id: Long, fetcher: Fetcher<FileProviderFileSystem>): FileProviderFileSystem {
        return sql.findOneById(fetcher, id)
    }

    fun createFileSystem(
        create: FileProviderFileSystemCreate,
        fetcher: Fetcher<FileProviderFileSystem>,
    ): FileProviderFileSystem {
        val entity = create.toEntity()
        return sql.saveCommand(entity, SaveMode.INSERT_ONLY).execute(fetcher).modifiedEntity
    }

    fun updateFileSystem(fs: FileProviderFileSystem, fetcher: Fetcher<FileProviderFileSystem>): FileProviderFileSystem {
        return sql.saveCommand(fs, SaveMode.UPDATE_ONLY).execute(fetcher).modifiedEntity
    }

    fun deleteFileSystem(id: Long) {
        sql.deleteById(FileProviderFileSystem::class, id)
    }
}