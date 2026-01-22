package com.coooolfan.unirhy.service.storage

import com.coooolfan.unirhy.model.storage.FileProviderFileSystem
import com.coooolfan.unirhy.model.storage.dto.FileProviderFileSystemCreate
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.springframework.stereotype.Service

@Service
class FileSystemStorageService(private val sql: KSqlClient) {

    fun list(fetcher: Fetcher<FileProviderFileSystem>): List<FileProviderFileSystem> {
        return sql.findAll(fetcher)
    }

    fun get(id: Long, fetcher: Fetcher<FileProviderFileSystem>): FileProviderFileSystem {
        return sql.findOneById(fetcher, id)
    }

    fun create(
        create: FileProviderFileSystemCreate,
        fetcher: Fetcher<FileProviderFileSystem>,
    ): FileProviderFileSystem {
        val entity = create.toEntity()
        return sql.saveCommand(entity, SaveMode.INSERT_ONLY).execute(fetcher).modifiedEntity
    }

    fun update(fs: FileProviderFileSystem, fetcher: Fetcher<FileProviderFileSystem>): FileProviderFileSystem {
        return sql.saveCommand(fs, SaveMode.UPDATE_ONLY).execute(fetcher).modifiedEntity
    }

    fun delete(id: Long) {
        sql.deleteById(FileProviderFileSystem::class, id)
    }
}
