package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.model.MediaFile
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.model.id
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

@Service
class MediaFileAccessService(private val sql: KSqlClient) {

    fun loadLocalFile(id: Long): ResolvedMediaFile {
        val mediaFile = findMediaFile(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Media file not found")

        val provider = mediaFile.fsProvider
            ?: throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Only file system provider is supported")

        val rootPath = Paths.get(provider.parentPath).toAbsolutePath().normalize()
        val targetPath = rootPath.resolve(mediaFile.objectKey).normalize()

        if (!targetPath.startsWith(rootPath)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid object key")
        }

        if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "File not found")
        }

        return ResolvedMediaFile(mediaFile, targetPath.toFile())
    }

    private fun findMediaFile(id: Long): MediaFile? {
        return sql.executeQuery(MediaFile::class) {
            where(table.id eq id)
            select(table.fetch(MEDIA_FILE_FETCHER))
        }.firstOrNull()
    }

    companion object {
        private val MEDIA_FILE_FETCHER: Fetcher<MediaFile> = newFetcher(MediaFile::class).by {
            allScalarFields()
            fsProvider {
                allScalarFields()
            }
        }
    }
}

data class ResolvedMediaFile(
    val mediaFile: MediaFile,
    val file: File,
)
