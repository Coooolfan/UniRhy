package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.model.Album
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.service.AlbumService
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 专辑管理接口
 *
 * 提供专辑列表查询能力
 */
@SaCheckLogin
@RestController
@RequestMapping("/api/album")
class AlbumController(private val service: AlbumService) {

    /**
     * 获取专辑列表
     *
     * 此接口用于获取系统中所有专辑信息
     * 需要用户登录认证才能访问
     *
     * @return List<Album> 返回专辑列表（默认 fetcher）
     *
     * @api GET /api/album
     * @permission 需要登录认证
     * @description 调用AlbumService.listAlbum()方法获取专辑列表
     */
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    fun listAlbums(): List<Album> {
        return service.listAlbum(DEFAULT_ALBUM_FETCHER, true)
    }

    companion object {
        private val DEFAULT_ALBUM_FETCHER = newFetcher(Album::class).by {
            allTableFields()
            recordings { allTableFields() }
            cover { allTableFields() }
        }
    }
}
