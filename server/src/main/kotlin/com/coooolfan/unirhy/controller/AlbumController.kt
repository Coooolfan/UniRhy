package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.model.Album
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.model.dto.AlbumUpdate
import com.coooolfan.unirhy.model.dto.RecordingReorderReq
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
     * 根据专辑 ID 获取完整专辑信息（包含录音、资源、艺人和封面等）
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

    /**
     * 更新专辑
     *
     * 按请求体内提供的字段更新指定专辑的标量信息
     *
     * @param id 专辑 ID
     * @param input 专辑更新参数
     * @return Album 返回更新后的专辑（默认 fetcher）
     *
     * @api PUT /api/albums/{id}
     * @permission 需要登录认证
     * @description 调用AlbumService.updateAlbum()方法更新专辑
     */
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun updateAlbum(
        @PathVariable id: Long,
        @RequestBody input: AlbumUpdate,
    ): @FetchBy("DEFAULT_ALBUM_FETCHER") Album {
        return service.updateAlbum(input.toEntity { this.id = id }, DEFAULT_ALBUM_FETCHER)
    }

    /**
     * 调整专辑内录音顺序
     *
     * 请求体需提供当前专辑中全部录音的 id 列表，按期望顺序排列。
     * 服务端严格校验集合一致性后，按下标重写映射表的 sortOrder。
     *
     * @param id 专辑 ID
     * @param input 新顺序下的录音 id 列表
     *
     * @api PUT /api/albums/{id}/recordings/reorder
     * @permission 需要登录认证
     */
    @PutMapping("/{id}/recordings/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reorderAlbumRecordings(
        @PathVariable id: Long,
        @RequestBody input: RecordingReorderReq,
    ) {
        service.reorderAlbumRecordings(id, input.recordingIds)
    }

    companion object {
        private val DEFAULT_ALBUM_FETCHER = newFetcher(Album::class).by {
            allScalarFields()
            recordings { label() }
            cover { url() }
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
                        url()
                    }
                }
                artists {
                    allScalarFields()
                }
                cover {
                    allScalarFields()
                    url()
                }
            }
            cover {
                allScalarFields()
                url()
            }
        }

    }
}
