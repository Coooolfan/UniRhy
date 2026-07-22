package com.coooolfan.unirhy.service.plugin.hostapi

import com.coooolfan.unirhy.model.Playlist
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.service.PlaylistService
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import run.endive.runtime.HostFunction
import run.endive.runtime.Instance
import tools.jackson.databind.ObjectMapper

private val PLAYLIST_SUMMARY_FETCHER: Fetcher<Playlist> = newFetcher(Playlist::class).by {
    allScalarFields()
}

private val PLAYLIST_DETAIL_FETCHER: Fetcher<Playlist> = newFetcher(Playlist::class).by {
    allScalarFields()
    recordings {
        allScalarFields()
        assets {
            allScalarFields()
            mediaFile {
                allScalarFields()
            }
        }
        artists {
            allScalarFields()
        }
        cover {
            allScalarFields()
        }
    }
}

internal fun buildPlaylistHostFunctions(
    playlistService: PlaylistService,
    objectMapper: ObjectMapper,
    instanceRef: () -> Instance,
    callExecutor: PluginHostCallExecutor = DIRECT_PLUGIN_HOST_CALL_EXECUTOR,
): List<HostFunction> {
    val support = PluginHostSupport(objectMapper, callExecutor, instanceRef)

    return listOf(
        support.jsonFunction("host_playlist_list") { request ->
            playlistService.getPlaylists(request.optionalLong("ownerId"), PLAYLIST_SUMMARY_FETCHER)
        },
        support.jsonFunction("host_playlist_get") { request ->
            playlistService.getPlaylist(request.requiredLong("id"), null, PLAYLIST_DETAIL_FETCHER)
        },
        support.jsonFunction("host_playlist_create") { request ->
            val ownerId = request.requiredLong("ownerId")
            val name = request.requiredText("name")
            val comment = request.optionalText("comment") ?: ""
            playlistService.createPlaylist(
                Playlist {
                    this.name = name
                    this.comment = comment
                },
                ownerId,
                PLAYLIST_SUMMARY_FETCHER,
            )
        },
        support.jsonFunction("host_playlist_update") { request ->
            val id = request.requiredLong("id")
            playlistService.updatePlaylist(
                Playlist {
                    this.id = id
                    if (request.has("name")) this.name = request.requiredText("name")
                    if (request.has("comment")) this.comment = request.requiredText("comment")
                },
                null,
                PLAYLIST_SUMMARY_FETCHER,
            )
        },
        support.jsonFunction("host_playlist_delete") { request ->
            playlistService.deletePlaylist(request.requiredLong("id"), null)
            null
        },
        support.jsonFunction("host_playlist_add_recording") { request ->
            playlistService.addRecordingToPlaylist(
                request.requiredLong("id"),
                request.requiredLong("recordingId"),
                null,
            )
            null
        },
        support.jsonFunction("host_playlist_remove_recording") { request ->
            playlistService.removeRecordingFromPlaylist(
                request.requiredLong("id"),
                request.requiredLong("recordingId"),
                null,
            )
            null
        },
        support.jsonFunction("host_playlist_reorder_recordings") { request ->
            playlistService.reorderPlaylistRecordings(
                request.requiredLong("id"),
                request.requiredLongList("recordingIds"),
                null,
            )
            null
        },
    )
}
