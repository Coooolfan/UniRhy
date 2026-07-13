package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import cn.dev33.satoken.annotation.SaCheckRole
import com.coooolfan.unirhy.config.ROLE_ADMIN
import com.coooolfan.unirhy.error.CommonException
import com.coooolfan.unirhy.model.Artist
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.model.dto.ArtistCreate
import com.coooolfan.unirhy.model.dto.ArtistMergeReq
import com.coooolfan.unirhy.model.dto.ArtistUpdate
import com.coooolfan.unirhy.service.ArtistService
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 艺术家管理接口
 *
 * 提供艺术家的分页查询、按名搜索、创建、更新与合并能力
 */
@RestController
@RequestMapping("/api/artists")
@SaCheckLogin
class ArtistController(private val service: ArtistService) {
    /**
     * 获取艺术家分页列表
     *
     * @param pageIndex 页码（从 0 开始）
     * @param pageSize 每页条数
     * @return Page<Artist> 返回艺术家分页列表（默认 fetcher）
     *
     * @api GET /api/artists
     * @permission 需要登录认证
     * @description 调用ArtistService.listArtist()方法获取艺术家列表
     */
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    fun listArtists(
        @RequestParam(required = false) pageIndex: Int?,
        @RequestParam(required = false) pageSize: Int?,
    ): Page<@FetchBy("DEFAULT_ARTIST_FETCHER") Artist> {
        return service.listArtist(pageIndex ?: 0, pageSize ?: 10, DEFAULT_ARTIST_FETCHER)
    }

    /**
     * 根据艺术家名称搜索
     *
     * @param name 艺术家名称
     * @return List<@FetchBy("DEFAULT_ARTIST_FETCHER") Artist> 返回艺术家列表
     *
     * @api GET /api/artists/search-results
     * @permission 需要登录认证
     * @description 调用ArtistService.getArtistByName()方法搜索艺术家
     */
    @GetMapping("/search-results")
    fun getArtistByName(@RequestParam name: String): List<@FetchBy("DEFAULT_ARTIST_FETCHER") Artist> {
        return service.getArtistByName(name, DEFAULT_ARTIST_FETCHER)
    }

    /**
     * 创建艺术家
     *
     * @param input 艺术家创建参数
     * @return Artist 返回创建后的艺术家（默认 fetcher）
     *
     * @api POST /api/artists
     * @permission 需要登录认证
     * @description 调用ArtistService.createArtist()方法创建艺术家
     */
    @PostMapping
    @SaCheckRole(ROLE_ADMIN)
    @ResponseStatus(HttpStatus.CREATED)
    @Throws(CommonException.Forbidden::class)
    fun createArtist(
        @RequestBody input: ArtistCreate,
        @RequestParam(required = false) copyAssociationsFrom: Long?,
    ): @FetchBy("DEFAULT_ARTIST_FETCHER") Artist {
        return service.createArtist(input.toEntity(), DEFAULT_ARTIST_FETCHER, copyAssociationsFrom)
    }

    /**
     * 更新艺术家
     *
     * 按请求体内提供的字段更新指定艺术家的标量信息
     *
     * @param id 艺术家 ID
     * @param input 艺术家更新参数
     * @return Artist 返回更新后的艺术家（默认 fetcher）
     *
     * @api PUT /api/artists/{id}
     * @permission 需要登录认证
     * @description 调用ArtistService.updateArtist()方法更新艺术家
     */
    @PutMapping("/{id}")
    @SaCheckRole(ROLE_ADMIN)
    @ResponseStatus(HttpStatus.OK)
    @Throws(CommonException.Forbidden::class)
    fun updateArtist(
        @PathVariable id: Long,
        @RequestBody input: ArtistUpdate,
    ): @FetchBy("DEFAULT_ARTIST_FETCHER") Artist {
        return service.updateArtist(input.toEntity { this.id = id }, DEFAULT_ARTIST_FETCHER)
    }

    /**
     * 合并艺术家
     *
     * @param input 合并请求体
     *
     * @api POST /api/artists/merge-requests
     * @permission 需要登录认证
     * @description 调用ArtistService.mergeArtists()方法合并艺术家
     */
    @PostMapping("/merge-requests")
    @SaCheckRole(ROLE_ADMIN)
    @ResponseStatus(HttpStatus.OK)
    @Throws(CommonException.Forbidden::class)
    fun mergeArtists(@RequestBody input: ArtistMergeReq) {
        service.mergeArtists(input)
    }

    companion object {
        val DEFAULT_ARTIST_FETCHER = newFetcher(Artist::class).by {
            allScalarFields()
        }

    }
}
