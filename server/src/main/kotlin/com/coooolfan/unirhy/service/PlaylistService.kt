package com.coooolfan.unirhy.service

import cn.dev33.satoken.stp.StpUtil
import com.coooolfan.unirhy.error.PlaylistException
import com.coooolfan.unirhy.model.*
import org.babyfish.jimmer.ImmutableObjects
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.max
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PlaylistService(private val sql: KSqlClient) {
    fun getPlaylists(fetcher: Fetcher<Playlist>): List<Playlist> =
        getPlaylists(StpUtil.getLoginIdAsLong(), fetcher)

    fun getPlaylists(ownerId: Long?, fetcher: Fetcher<Playlist>): List<Playlist> {
        return sql.createQuery(Playlist::class) {
            ownerId?.let { where(table.ownerId eq it) }
            orderBy(table.id)
            select(table.fetch(fetcher))
        }.execute()
    }

    fun getPlaylist(playlistId: Long, fetcher: Fetcher<Playlist>): Playlist =
        getPlaylist(playlistId, StpUtil.getLoginIdAsLong(), fetcher)

    fun getPlaylist(playlistId: Long, ownerId: Long?, fetcher: Fetcher<Playlist>): Playlist {
        return sql.createQuery(Playlist::class) {
            where(table.id eq playlistId)
            ownerId?.let { where(table.ownerId eq it) }
            select(table.fetch(fetcher))
        }.execute().firstOrNull()
            ?: throw PlaylistException.NotFound()
    }

    fun createPlaylist(create: Playlist, fetcher: Fetcher<Playlist>): Playlist =
        createPlaylist(create, StpUtil.getLoginIdAsLong(), fetcher)

    fun createPlaylist(create: Playlist, ownerId: Long, fetcher: Fetcher<Playlist>): Playlist {
        val entity = Playlist(create) {
            this.ownerId = ownerId
        }
        return sql.saveCommand(entity, SaveMode.INSERT_ONLY).execute(fetcher).modifiedEntity
    }

    fun updatePlaylist(input: Playlist, fetcher: Fetcher<Playlist>): Playlist =
        updatePlaylist(input, StpUtil.getLoginIdAsLong(), fetcher)

    fun updatePlaylist(input: Playlist, ownerId: Long?, fetcher: Fetcher<Playlist>): Playlist {
        val hasName = ImmutableObjects.isLoaded(input, "name")
        val hasComment = ImmutableObjects.isLoaded(input, "comment")

        if (!hasName && !hasComment) {
            return getPlaylist(input.id, ownerId, fetcher)
        }

        val affectedRows = sql.createUpdate(Playlist::class) {
            if (hasName) {
                set(table.name, input.name)
            }
            if (hasComment) {
                set(table.comment, input.comment)
            }
            where(table.id eq input.id)
            ownerId?.let { where(table.ownerId eq it) }
        }.execute()

        if (affectedRows == 0) {
            throw PlaylistException.NotFound()
        }

        return getPlaylist(input.id, ownerId, fetcher)
    }

    fun deletePlaylist(playlistId: Long) =
        deletePlaylist(playlistId, StpUtil.getLoginIdAsLong())

    fun deletePlaylist(playlistId: Long, ownerId: Long?) {
        val affectedRows = sql.createDelete(Playlist::class) {
            where(table.id eq playlistId)
            ownerId?.let { where(table.ownerId eq it) }
        }.execute()

        if (affectedRows == 0) {
            throw PlaylistException.NotFound()
        }
    }

    @Transactional
    fun addRecordingToPlaylist(playlistId: Long, recordingId: Long) =
        addRecordingToPlaylist(playlistId, recordingId, StpUtil.getLoginIdAsLong())

    @Transactional
    fun addRecordingToPlaylist(playlistId: Long, recordingId: Long, ownerId: Long?) {
        requirePlaylistAccessible(playlistId, ownerId)

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
    fun removeRecordingFromPlaylist(playlistId: Long, recordingId: Long) =
        removeRecordingFromPlaylist(playlistId, recordingId, StpUtil.getLoginIdAsLong())

    @Transactional
    fun removeRecordingFromPlaylist(playlistId: Long, recordingId: Long, ownerId: Long?) {
        requirePlaylistAccessible(playlistId, ownerId)

        sql.createDelete(PlaylistRecording::class) {
            where(table.playlistId eq playlistId)
            where(table.recordingId eq recordingId)
        }.execute()
    }

    private fun requirePlaylistAccessible(playlistId: Long, ownerId: Long?) {
        val exists = sql.executeQuery(Playlist::class) {
            where(table.id eq playlistId)
            ownerId?.let { where(table.ownerId eq it) }
            selectCount()
        }.first() > 0L
        if (!exists) throw PlaylistException.NotFound()
    }

    @Transactional
    fun reorderPlaylistRecordings(playlistId: Long, recordingIds: List<Long>) =
        reorderPlaylistRecordings(playlistId, recordingIds, StpUtil.getLoginIdAsLong())

    @Transactional
    fun reorderPlaylistRecordings(playlistId: Long, recordingIds: List<Long>, ownerId: Long?) {
        val requestedSet = recordingIds.toSet()
        if (requestedSet.size != recordingIds.size) {
            throw PlaylistException.RecordingIdsContainDuplicates()
        }

        if (ownerId == null) {
            requirePlaylistAccessible(playlistId, null)
        }

        val currentIds = sql.createQuery(PlaylistRecording::class) {
            where(table.playlistId eq playlistId)
            ownerId?.let { where(table.playlist.ownerId eq it) }
            select(table.recordingId)
        }.execute()

        if (requestedSet != currentIds.toSet()) {
            throw PlaylistException.RecordingIdsMismatch()
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
