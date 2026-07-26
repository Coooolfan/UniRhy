package com.coooolfan.unirhy.service.plugin.hostapi

import com.coooolfan.unirhy.model.Album
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.service.AlbumService
import org.babyfish.jimmer.sql.exception.EmptyResultException
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import run.endive.runtime.HostFunction
import run.endive.runtime.Instance
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Id-only fetcher for existence checks that discard the loaded entity. */
private val HOST_ALBUM_ID_FETCHER: Fetcher<Album> = newFetcher(Album::class).by { }

private val HOST_ALBUM_LIST_FETCHER: Fetcher<Album> = newFetcher(Album::class).by {
    allScalarFields()
    cover {
        allScalarFields()
    }
    recordings {
        allScalarFields()
        artists {
            allScalarFields()
        }
        cover {
            allScalarFields()
        }
    }
}

private val HOST_ALBUM_DETAIL_FETCHER: Fetcher<Album> = newFetcher(Album::class).by {
    allScalarFields()
    cover {
        allScalarFields()
    }
    recordings {
        allScalarFields()
        artists {
            allScalarFields()
        }
        cover {
            allScalarFields()
        }
        assets {
            allScalarFields()
            mediaFile {
                allScalarFields()
            }
        }
    }
}

internal fun buildAlbumHostFunctions(
    albumService: AlbumService,
    objectMapper: ObjectMapper,
    instanceRef: () -> Instance,
    callExecutor: PluginHostCallExecutor = DIRECT_PLUGIN_HOST_CALL_EXECUTOR,
): List<HostFunction> {
    val support = PluginHostSupport(objectMapper, callExecutor, instanceRef)

    val hostAlbumList = support.jsonFunction("host_album_list") { request ->
        val pageRequest = support.page(request)
        albumService.listAlbum(
            pageRequest.pageIndex,
            pageRequest.pageSize,
            HOST_ALBUM_LIST_FETCHER,
            filterSingle = true,
        ).toHostPage()
    }

    val hostAlbumGet = support.jsonFunction("host_album_get") { request ->
        findAlbum(albumService, request.requiredLong("id"), HOST_ALBUM_DETAIL_FETCHER)
    }

    val hostAlbumSearch = support.jsonFunction("host_album_search") { request ->
        albumService.getAlbumByName(request.requiredText("name"), HOST_ALBUM_LIST_FETCHER)
    }

    val hostAlbumUpdate = support.jsonFunction("host_album_update") { request ->
        val id = request.requiredLong("id")
        findAlbum(albumService, id, HOST_ALBUM_ID_FETCHER)
        albumService.updateAlbum(
            Album {
                this.id = id
                title = request.requiredText("title")
                releaseDate = request.requiredNullableLocalDate("releaseDate")
                comment = request.requiredText("comment")
            },
            HOST_ALBUM_LIST_FETCHER,
        )
    }

    val hostAlbumReorderRecordings = support.jsonFunction("host_album_reorder_recordings") { request ->
        albumService.reorderAlbumRecordings(
            albumId = request.requiredLong("id"),
            recordingIds = request.requiredLongList("recordingIds"),
        )
        null
    }

    return listOf(
        hostAlbumList,
        hostAlbumGet,
        hostAlbumSearch,
        hostAlbumUpdate,
        hostAlbumReorderRecordings,
    )
}

private fun findAlbum(albumService: AlbumService, id: Long, fetcher: Fetcher<Album>): Album =
    try {
        albumService.getAlbum(id, fetcher)
    } catch (_: EmptyResultException) {
        notFound("Album not found: $id")
    }

private fun tools.jackson.databind.node.ObjectNode.requiredNullableLocalDate(name: String): LocalDate? {
    val node = requiredNode(name)
    if (node.isNull) return null
    if (!node.isString) invalidArgument("Field '$name' must be an ISO-8601 date or null")
    val value = node.stringValue()
    return try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        invalidArgument("Field '$name' must be an ISO-8601 date")
    }
}
