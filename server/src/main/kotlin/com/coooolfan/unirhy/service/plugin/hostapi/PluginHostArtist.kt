package com.coooolfan.unirhy.service.plugin.hostapi

import com.coooolfan.unirhy.model.Artist
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.model.dto.ArtistMergeReq
import com.coooolfan.unirhy.service.ArtistService
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import run.endive.runtime.HostFunction
import run.endive.runtime.Instance
import tools.jackson.databind.ObjectMapper

private val HOST_ARTIST_FETCHER: Fetcher<Artist> = newFetcher(Artist::class).by {
    allScalarFields()
}

internal fun buildArtistHostFunctions(
    artistService: ArtistService,
    objectMapper: ObjectMapper,
    instanceRef: () -> Instance,
    callExecutor: PluginHostCallExecutor = DIRECT_PLUGIN_HOST_CALL_EXECUTOR,
): List<HostFunction> {
    val support = PluginHostSupport(objectMapper, callExecutor, instanceRef)

    val hostArtistList = support.jsonFunction("host_artist_list") { request ->
        val pageRequest = support.page(request)
        artistService.listArtist(pageRequest.pageIndex, pageRequest.pageSize, HOST_ARTIST_FETCHER).toHostPage()
    }

    val hostArtistGetByIds = support.jsonFunction("host_artist_get_by_ids") { request ->
        artistService.getArtistsByIds(request.requiredLongList("ids"), HOST_ARTIST_FETCHER)
    }

    val hostArtistSearch = support.jsonFunction("host_artist_search") { request ->
        artistService.getArtistByName(request.requiredText("name"), HOST_ARTIST_FETCHER)
    }

    val hostArtistCreate = support.jsonFunction("host_artist_create") { request ->
        val artist = Artist {
            displayName = request.requiredText("displayName")
            alias = if (request.has("alias")) request.requiredTextList("alias") else emptyList()
            comment = if (request.has("comment")) request.requiredText("comment") else ""
            avatarId = request.optionalLong("avatarId")
        }
        artistService.createArtist(
            input = artist,
            fetcher = HOST_ARTIST_FETCHER,
            copyAssociationsFrom = request.optionalLong("copyAssociationsFrom"),
        )
    }

    val hostArtistUpdate = support.jsonFunction("host_artist_update") { request ->
        val id = request.requiredLong("id")
        findArtist(artistService, id)
        val artist = Artist {
            this.id = id
            if (request.has("displayName")) displayName = request.requiredText("displayName")
            if (request.has("alias")) alias = request.requiredTextList("alias")
            if (request.has("comment")) comment = request.requiredText("comment")
            if (request.has("avatarId")) avatarId = request.optionalLong("avatarId")
        }
        artistService.updateArtist(artist, HOST_ARTIST_FETCHER)
    }

    val hostArtistMerge = support.jsonFunction("host_artist_merge") { request ->
        artistService.mergeArtists(
            ArtistMergeReq(
                targetId = request.requiredLong("targetId"),
                needMergeIds = request.requiredLongList("needMergeIds").toSet(),
            ),
        )
        null
    }

    val hostArtistSplit = support.jsonFunction("host_artist_split") { request ->
        artistService.splitArtist(
            sourceArtistId = request.requiredLong("sourceArtistId"),
            names = request.requiredTextList("names"),
        )
        null
    }

    return listOf(
        hostArtistList,
        hostArtistGetByIds,
        hostArtistSearch,
        hostArtistCreate,
        hostArtistUpdate,
        hostArtistMerge,
        hostArtistSplit,
    )
}

private fun findArtist(artistService: ArtistService, id: Long): Artist =
    artistService.getArtistsByIds(listOf(id), HOST_ARTIST_FETCHER).firstOrNull()
        ?: notFound("Artist not found: $id")
