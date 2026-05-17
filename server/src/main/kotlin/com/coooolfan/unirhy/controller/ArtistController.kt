package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.model.Artist
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.model.dto.ArtistMergeReq
import com.coooolfan.unirhy.model.dto.ArtistSplitReq
import com.coooolfan.unirhy.service.ArtistService
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/artists")
@SaCheckLogin
class ArtistController(private val service: ArtistService) {
    /**
     * 根据艺术家名称搜索
     *
     * @param name 艺术家名称
     * @return List<@FetchBy("DEFAULT_ARTIST_FETCHER") Artist> 返回艺术家列表
     *
     * @api GET /api/artists/search
     * @permission 需要登录认证
     * @description 调用ArtistService.getArtistByName()方法搜索艺术家
     */
    @GetMapping("/search")
    fun getArtistByName(@RequestParam name: String): List<@FetchBy("DEFAULT_ARTIST_FETCHER") Artist> {
        return service.getArtistByName(name, DEFAULT_ARTIST_FETCHER)
    }

    /**
     * 合并艺术家
     *
     * @param input 合并请求体
     *
     * @api POST /api/artists/merge
     * @permission 需要登录认证
     * @description 调用ArtistService.mergeArtists()方法合并艺术家
     */
    @PostMapping("/merge")
    @ResponseStatus(HttpStatus.OK)
    fun mergeArtists(@RequestBody input: ArtistMergeReq) {
        service.mergeArtists(input)
    }

    /**
     * 拆分艺术家
     *
     * @param input 拆分请求体
     * @return List<Artist> 返回新建艺术家列表
     *
     * @api POST /api/artists/split
     * @permission 需要登录认证
     * @description 调用ArtistService.splitArtist()方法拆分艺术家
     */
    @PostMapping("/split")
    @ResponseStatus(HttpStatus.OK)
    fun splitArtist(@RequestBody input: ArtistSplitReq): List<@FetchBy("DEFAULT_ARTIST_FETCHER") Artist> {
        return service.splitArtist(input)
    }

    companion object {
        val DEFAULT_ARTIST_FETCHER = newFetcher(Artist::class).by {
            allScalarFields()
        }

    }
}
