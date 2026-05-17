package com.coooolfan.unirhy.service.plugin

import com.coooolfan.unirhy.model.Artist
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.model.dto.ArtistMergeReq
import com.coooolfan.unirhy.service.ArtistService
import com.dylibso.chicory.runtime.HostFunction
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.wasm.types.FunctionType
import com.dylibso.chicory.wasm.types.ValType
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher

private val NORMALIZATION_FETCHER: Fetcher<Artist> = newFetcher(Artist::class).by {
    allScalarFields()
}

private val LONG_LIST_TYPE = object : TypeReference<List<Long>>() {}

private data class SplitArtistReq(
    val sourceArtistId: Long,
    val names: List<String>,
)

fun buildArtistHostFunctions(
    artistService: ArtistService,
    objectMapper: ObjectMapper,
    instanceRef: () -> Instance,
): List<HostFunction> {

    val hostListArtistIds = HostFunction(
        "env",
        "host_list_artist_ids",
        FunctionType.of(listOf(ValType.I32, ValType.I32), listOf(ValType.I64)),
    ) { _: Instance, args: LongArray ->
        val pageIndex = args[0].toInt()
        val pageSize = args[1].toInt()
        val page = artistService.listArtist(pageIndex, pageSize, NORMALIZATION_FETCHER)
        val ids = page.rows.map { it.id }
        writeJsonToPlugin(objectMapper, instanceRef(), ids)
    }

    val hostGetArtistsByIds = HostFunction(
        "env",
        "host_get_artists_by_ids",
        FunctionType.of(listOf(ValType.I32, ValType.I32), listOf(ValType.I64)),
    ) { _: Instance, args: LongArray ->
        val ptr = args[0].toInt()
        val len = args[1].toInt()
        val idsJson = instanceRef().memory().readBytes(ptr, len)
        val ids = objectMapper.readValue(idsJson, LONG_LIST_TYPE)
        val artists = artistService.getArtistsByIds(ids, NORMALIZATION_FETCHER)
        writeJsonToPlugin(objectMapper, instanceRef(), artists)
    }

    val hostMergeArtists = HostFunction(
        "env",
        "host_merge_artists",
        FunctionType.of(listOf(ValType.I32, ValType.I32), emptyList()),
    ) { _: Instance, args: LongArray ->
        val ptr = args[0].toInt()
        val len = args[1].toInt()
        val json = instanceRef().memory().readBytes(ptr, len)
        val req = objectMapper.readValue(json, ArtistMergeReq::class.java)
        artistService.mergeArtists(req)
        longArrayOf()
    }

    // { sourceArtistId, names: [name0, name1, ...] }
    // names[0] → 更新 source artist 的 displayName，清空 alias
    // names[1..] → 各新建艺术家，继承 source 的 work/recording 关联
    val hostSplitArtist = HostFunction(
        "env",
        "host_split_artist",
        FunctionType.of(listOf(ValType.I32, ValType.I32), emptyList()),
    ) { _: Instance, args: LongArray ->
        val ptr = args[0].toInt()
        val len = args[1].toInt()
        val json = instanceRef().memory().readBytes(ptr, len)
        val req = objectMapper.readValue(json, SplitArtistReq::class.java)
        if (req.names.size >= 2) {
            artistService.updateArtist(
                Artist {
                    id = req.sourceArtistId
                    displayName = req.names[0]
                    alias = emptyList()
                },
                NORMALIZATION_FETCHER,
            )
            for (name in req.names.drop(1)) {
                artistService.createArtist(
                    Artist {
                        displayName = name
                        alias = emptyList()
                        comment = ""
                    },
                    NORMALIZATION_FETCHER,
                    copyAssociationsFrom = req.sourceArtistId,
                )
            }
        }
        longArrayOf()
    }

    return listOf(hostListArtistIds, hostGetArtistsByIds, hostMergeArtists, hostSplitArtist)
}

private fun writeJsonToPlugin(objectMapper: ObjectMapper, instance: Instance, value: Any): LongArray {
    val json = objectMapper.writeValueAsBytes(value)
    if (json.isEmpty()) return longArrayOf(0L)
    val ptr = instance.export("alloc").apply(json.size.toLong())[0].toInt()
    instance.memory().write(ptr, json)
    return longArrayOf((ptr.toLong() shl 32) or json.size.toLong())
}
