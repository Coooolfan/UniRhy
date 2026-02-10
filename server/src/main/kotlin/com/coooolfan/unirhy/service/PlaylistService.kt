package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.model.Playlist
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.springframework.stereotype.Service

@Service
class PlaylistService(private val sql: KSqlClient) {
    fun getPlaylists(fetcher: Fetcher<Playlist>): List<Playlist> {
        return sql.findAll(fetcher)
    }

    fun getPlaylist(playlistId: Long, fetcher: Fetcher<Playlist>): Playlist {
        return sql.findOneById(fetcher, playlistId)
    }

    fun createPlaylist(create: Playlist, fetcher: Fetcher<Playlist>): Playlist {
        return sql.saveCommand(create, SaveMode.INSERT_ONLY).execute(fetcher).modifiedEntity
    }

    fun updatePlaylist(input: Playlist, fetcher: Fetcher<Playlist>): Playlist {
        return sql.saveCommand(input, SaveMode.UPDATE_ONLY).execute(fetcher).modifiedEntity
    }

    fun deletePlaylist(playlistId: Long) {
        sql.deleteById(Playlist::class, playlistId)
    }

}