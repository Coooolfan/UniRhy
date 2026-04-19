package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.model.*
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.*
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class AlbumService(private val sql: KSqlClient) {
    fun listAlbum(
        pageIndex: Int,
        pageSize: Int,
        fetcher: Fetcher<Album>,
        filterSingle: Boolean = false
    ): Page<Album> {
        return sql.createQuery(Album::class) {
            if (filterSingle) {
                where(
                    exists(
                        subQuery(AlbumRecording::class) {
                            where(table.albumId eq parentTable.id)
                            groupBy(table.albumId)
                            having(count(table.id) ne 1L)
                            select(table.albumId)
                        }
                    )
                )
            }

            orderBy(table.id)
            select(table.fetch(fetcher))
        }.fetchPage(pageIndex, pageSize)
    }

    fun getAlbum(id: Long, fetcher: Fetcher<Album>): Album {
        return sql.findOneById(fetcher, id)
    }

    fun getAlbumByName(name: String, fetcher: Fetcher<Album>): List<Album> {
        return sql.createQuery(Album::class) {
            where(table.title.ilike(name))
            select(table.fetch(fetcher))
        }.execute()

    }

    fun updateAlbum(input: Album, fetcher: Fetcher<Album>): Album {
        return sql.saveCommand(input, SaveMode.UPDATE_ONLY).execute(fetcher).modifiedEntity
    }

    @Transactional
    fun reorderAlbumRecordings(albumId: Long, recordingIds: List<Long>) {
        val requestedSet = recordingIds.toSet()
        if (requestedSet.size != recordingIds.size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "recordingIds contain duplicates")
        }

        val currentIds = sql.createQuery(AlbumRecording::class) {
            where(table.albumId eq albumId)
            select(table.recordingId)
        }.execute()

        if (currentIds.isEmpty())
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Album not found")

        if (requestedSet != currentIds.toSet()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "recordingIds do not match album recordings",
            )
        }

        val sortedRecordings = ArrayList<AlbumRecording>(recordingIds.size)

        recordingIds.forEachIndexed { index, recordingId ->
            sortedRecordings.add(
                AlbumRecording {
                    this.albumId = albumId
                    this.recordingId = recordingId
                    this.sortOrder = index
                }
            )
        }

        sql.saveEntities(sortedRecordings, SaveMode.UPDATE_ONLY)
    }
}
