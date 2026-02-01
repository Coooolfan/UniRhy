package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.model.Album
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.service.AlbumService
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
    fun listAlbums(): List<@FetchBy("DEFAULT_ALBUM_FETCHER") Album> {
        return service.listAlbum(DEFAULT_ALBUM_FETCHER, true)
    }

    /**
     * 获取专辑详情
     *
     * 根据专辑 ID 获取完整专辑信息（包含曲目、资源、艺人和封面等）
     *
     * @param id 专辑 ID
     * @return Album 返回专辑详情（使用 DETAIL_ALBUM_FETCHER）
     *
     * @api GET /api/album/{id}
     * @permission 需要登录认证
     * @description 调用AlbumService.getAlbum()方法获取专辑详情
     */
    @GetMapping("{id}")
    @ResponseStatus(HttpStatus.OK)
    fun getAlbum(@PathVariable id: Long): @FetchBy("DETAIL_ALBUM_FETCHER") Album {
        return service.getAlbum(id, DETAIL_ALBUM_FETCHER)
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
                    mediaFile { allScalarFields() }
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
