package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.model.Album
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.service.AlbumService
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

/**
 * 专辑管理接口
 *
 * 提供专辑列表查询能力
 */
@SaCheckLogin
@RestController
@RequestMapping("/api/albums")
class AlbumController(private val service: AlbumService) {

    /**
     * 获取专辑列表
     *
     * 此接口用于获取系统中所有专辑信息
     * 需要用户登录认证才能访问
     *
     * @return Page<Album> 返回专辑分页列表（默认 fetcher）
     *
     * @api GET /api/albums
     * @permission 需要登录认证
     * @description 调用AlbumService.listAlbum()方法获取专辑列表
     */
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    fun listAlbums(
        @RequestParam(required = false) pageIndex: Int?,
        @RequestParam(required = false) pageSize: Int?
    ): Page<@FetchBy("DEFAULT_ALBUM_FETCHER") Album> {
        return service.listAlbum(pageIndex ?: 0, pageSize ?: 10, DEFAULT_ALBUM_FETCHER, true)
    }

    /**
     * 获取专辑详情
     *
     * 根据专辑 ID 获取完整专辑信息（包含曲目、资源、艺人和封面等）
     *
     * @param id 专辑 ID
     * @return Album 返回专辑详情（使用 DETAIL_ALBUM_FETCHER）
     *
     * @api GET /api/albums/{id}
     * @permission 需要登录认证
     * @description 调用AlbumService.getAlbum()方法获取专辑详情
     */
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun getAlbum(@PathVariable id: Long): @FetchBy("DETAIL_ALBUM_FETCHER") Album {
        return service.getAlbum(id, DETAIL_ALBUM_FETCHER)
    }

    /**
     * 根据专辑名称搜索
     *
     * @param name 专辑名称
     * @return List<Album> 返回专辑列表（默认 fetcher）
     *
     * @api GET /api/albums/search
     * @permission 需要登录认证
     * @description 调用AlbumService.getAlbumByName()方法搜索专辑
     */
    @GetMapping("/search")
    fun getAlbumByName(
        @RequestParam(required = true) name: String,
    ): List<@FetchBy("DEFAULT_ALBUM_FETCHER") Album> {
        return service.getAlbumByName(name, DEFAULT_ALBUM_FETCHER)
    }

    companion object {
        private val DEFAULT_ALBUM_FETCHER = newFetcher(Album::class).by {
            allScalarFields()
            recordings { label() }
            cover()
        }

        private val DETAIL_ALBUM_FETCHER = newFetcher(Album::class).by {
            allScalarFields()
            recordings {
                allScalarFields()
                assets {
                    allScalarFields()
                    mediaFile {
                        allScalarFields()
                        ossProvider()
                        fsProvider()
                        objectKey()
                    }
                }
                artists {
                    allScalarFields()
                }
                cover { allScalarFields() }
            }
            cover { allScalarFields() }
        }

    }
}
