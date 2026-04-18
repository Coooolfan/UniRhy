package com.coooolfan.unirhy.service

import cn.dev33.satoken.stp.StpUtil
import com.coooolfan.unirhy.model.*
import org.babyfish.jimmer.ImmutableObjects
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.max
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class PlaylistService(private val sql: KSqlClient) {
    fun getPlaylists(fetcher: Fetcher<Playlist>): List<Playlist> {
        val accountId = StpUtil.getLoginIdAsLong()
        return sql.createQuery(Playlist::class) {
            where(table.ownerId eq accountId)
            orderBy(table.id)
            select(table.fetch(fetcher))
        }.execute()
    }

    fun getPlaylist(playlistId: Long, fetcher: Fetcher<Playlist>): Playlist {
        val accountId = StpUtil.getLoginIdAsLong()
        return sql.createQuery(Playlist::class) {
            where(table.id eq playlistId)
            where(table.ownerId eq accountId)
            select(table.fetch(fetcher))
        }.execute().firstOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Playlist not found")
    }

    fun createPlaylist(create: Playlist, fetcher: Fetcher<Playlist>): Playlist {
        val entity = Playlist(create) {
            ownerId = StpUtil.getLoginIdAsLong()
        }
        return sql.saveCommand(entity, SaveMode.INSERT_ONLY).execute(fetcher).modifiedEntity
    }

    fun updatePlaylist(input: Playlist, fetcher: Fetcher<Playlist>): Playlist {
        val accountId = StpUtil.getLoginIdAsLong()
        val hasName = ImmutableObjects.isLoaded(input, "name")
        val hasComment = ImmutableObjects.isLoaded(input, "comment")

        if (!hasName && !hasComment) {
            return getPlaylist(input.id, fetcher)
        }

        val affectedRows = sql.createUpdate(Playlist::class) {
            if (hasName) {
                set(table.name, input.name)
            }
            if (hasComment) {
                set(table.comment, input.comment)
            }
            where(table.id eq input.id)
            where(table.ownerId eq accountId)
        }.execute()

        if (affectedRows == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Playlist not found")
        }

        return getPlaylist(input.id, fetcher)
    }

    fun deletePlaylist(playlistId: Long) {
        val accountId = StpUtil.getLoginIdAsLong()
        val affectedRows = sql.createDelete(Playlist::class) {
            where(table.id eq playlistId)
            where(table.ownerId eq accountId)
        }.execute()

        if (affectedRows == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Playlist not found")
        }
    }

    @Transactional
    fun addRecordingToPlaylist(playlistId: Long, recordingId: Long) {
        val playlist = sql.executeQuery(Playlist::class) {
            where(table.id eq playlistId)
            where(table.ownerId eq StpUtil.getLoginIdAsLong())
            selectCount()
        }.first() == 0L
        if (playlist) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Playlist not found")

        val alreadyExists = sql.createQuery(PlaylistRecording::class) {
            where(table.playlistId eq playlistId)
            where(table.recordingId eq recordingId)
            selectCount()
        }.execute().first() > 0L
        if (alreadyExists) return

        val maxOrder = sql.createQuery(PlaylistRecording::class) {
            where(table.playlistId eq playlistId)
            select(max(table.sortOrder))
        }.execute().first()
        val nextOrder = (maxOrder ?: -1) + 1

        sql.save(
            PlaylistRecording {
                this.playlistId = playlistId
                this.recordingId = recordingId
                this.sortOrder = nextOrder
            },
            SaveMode.INSERT_ONLY,
        )
    }

    @Transactional
    fun removeRecordingFromPlaylist(playlistId: Long, recordingId: Long) {
        val playlist = sql.executeQuery(Playlist::class) {
            where(table.id eq playlistId)
            where(table.ownerId eq StpUtil.getLoginIdAsLong())
            selectCount()
        }.first() == 0L
        if (playlist) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Playlist not found")

        sql.createDelete(PlaylistRecording::class) {
            where(table.playlistId eq playlistId)
            where(table.recordingId eq recordingId)
        }.execute()
    }

    @Transactional
    fun reorderPlaylistRecordings(playlistId: Long, recordingIds: List<Long>) {
        val requestedSet = recordingIds.toSet()
        if (requestedSet.size != recordingIds.size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "recordingIds contain duplicates")
        }

        val currentIds = sql.createQuery(PlaylistRecording::class) {
            where(table.playlistId eq playlistId)
            select(table.recordingId)
        }.execute()

        if (requestedSet != currentIds.toSet()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Playlist not found or recordingIds do not match playlist recordings",
            )
        }

        val sortedRecordings = ArrayList<PlaylistRecording>(recordingIds.size)

        recordingIds.forEachIndexed { index, recordingId ->
            sortedRecordings.add(
                PlaylistRecording {
                    this.playlistId = playlistId
                    this.recordingId = recordingId
                    this.sortOrder = index
                }
            )
        }

        sql.saveEntities(sortedRecordings, SaveMode.UPDATE_ONLY)
    }

}
