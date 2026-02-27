package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.model.Artist
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.service.ArtistService
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
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

    companion object {
        val DEFAULT_ARTIST_FETCHER = newFetcher(Artist::class).by {
            allScalarFields()
        }

    }
}