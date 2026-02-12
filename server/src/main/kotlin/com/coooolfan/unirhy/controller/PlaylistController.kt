package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.model.Playlist
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.model.dto.PlaylistCreate
import com.coooolfan.unirhy.model.dto.PlaylistUpdate
import com.coooolfan.unirhy.service.PlaylistService
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*


@SaCheckLogin
@RestController
@RequestMapping("/api/playlist")
class PlaylistController(private val service: PlaylistService) {

    /**
     * 获取播放列表列表
     *
     * 此接口用于获取系统中所有播放列表信息
     * 需要用户登录认证才能访问
     *
     * @return List<Playlist> 返回播放列表集合（默认 fetcher）
     *
     * @api GET /api/playlist
     * @permission 需要登录认证
     * @description 调用PlaylistService.getPlaylists()方法获取播放列表列表
     */
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    fun listPlaylists(): List<@FetchBy("DEFAULT_PLAYLIST_FETCHER") Playlist> {
        return service.getPlaylists(DEFAULT_PLAYLIST_FETCHER)
    }

    /**
     * 获取播放列表详情
     *
     * 此接口用于根据播放列表 ID 查询播放列表详情
     * 需要用户登录认证才能访问
     *
     * @param id 播放列表 ID
     * @return Playlist 返回播放列表详情（详细 fetcher）
     *
     * @api GET /api/playlist/{id}
     * @permission 需要登录认证
     * @description 调用PlaylistService.getPlaylist()方法获取指定播放列表详情
     */
    @GetMapping("{id}")
    @ResponseStatus(HttpStatus.OK)
    fun getPlaylist(@PathVariable id: Long): @FetchBy("DETAIL_PLAYLIST_FETCHER") Playlist {
        return service.getPlaylist(id, DETAIL_PLAYLIST_FETCHER)
    }

    /**
     * 更新播放列表
     *
     * 此接口用于更新指定 ID 的播放列表信息
     * 需要用户登录认证才能访问
     *
     * @param id 播放列表 ID
     * @param input 播放列表更新参数
     * @return Playlist 返回更新后的播放列表（默认 fetcher）
     *
     * @api PUT /api/playlist/{id}
     * @permission 需要登录认证
     * @description 调用PlaylistService.updatePlaylist()方法更新播放列表
     */
    @PutMapping("{id}")
    @ResponseStatus(HttpStatus.OK)
    fun updatePlaylist(
        @PathVariable id: Long,
        @RequestBody input: PlaylistUpdate
    ): @FetchBy("DEFAULT_PLAYLIST_FETCHER") Playlist {
        return service.updatePlaylist(input.toEntity { this.id = id }, DEFAULT_PLAYLIST_FETCHER)
    }

    /**
     * 创建播放列表
     *
     * 此接口用于创建新的播放列表
     * 需要用户登录认证才能访问
     *
     * @param input 播放列表创建参数
     * @return Playlist 返回创建后的播放列表（默认 fetcher）
     *
     * @api POST /api/playlist
     * @permission 需要登录认证
     * @description 调用PlaylistService.createPlaylist()方法创建播放列表
     */
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    fun createPlaylist(
        @RequestBody input: PlaylistCreate
    ): @FetchBy("DEFAULT_PLAYLIST_FETCHER") Playlist {
        return service.createPlaylist(input.toEntity(), DEFAULT_PLAYLIST_FETCHER)
    }

    /**
     * 删除播放列表
     *
     * 此接口用于删除指定 ID 的播放列表
     * 需要用户登录认证才能访问
     *
     * @param id 播放列表 ID
     *
     * @api DELETE /api/playlist/{id}
     * @permission 需要登录认证
     * @description 调用PlaylistService.deletePlaylist()方法删除播放列表
     */
    @DeleteMapping("{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePlaylist(@PathVariable id: Long) {
        service.deletePlaylist(id)
    }

    /**
     * 向播放列表添加录音
     *
     * 此接口用于向指定播放列表追加一条录音记录
     * 重复添加时保持幂等，返回 added=false
     * 需要用户登录认证才能访问
     *
     * @param id 播放列表 ID
     * @param recordingId 录音 ID
     *
     * @api PUT /api/playlist/{id}/recordings/{recordingId}
     * @permission 需要登录认证
     */
    @PutMapping("{id}/recordings/{recordingId}")
    @ResponseStatus(HttpStatus.OK)
    fun addRecordingToPlaylist(
        @PathVariable id: Long,
        @PathVariable recordingId: Long,
    ) {
        service.addRecordingToPlaylist(id, recordingId)
    }

    /**
     * 从播放列表中移除录音
     *
     * 此接口用于从指定播放列表中移除一条录音记录
     * 需要用户登录认证才能访问
     *
     * @param id 播放列表 ID
     * @param recordingId 录音 ID
     *
     * @api DELETE /api/playlist/{id}/recordings/{recordingId}
     * @permission 需要登录认证
     * @description 调用PlaylistService.removeRecordingFromPlaylist()方法从播放列表中移除录音
     */
    @DeleteMapping("{id}/recordings/{recordingId}")
    @ResponseStatus(HttpStatus.OK)
    fun removeRecordingFromPlaylist(
        @PathVariable id: Long,
        @PathVariable recordingId: Long,
    ) {
        service.removeRecordingFromPlaylist(id, recordingId)
    }

    companion object {
        private val DEFAULT_PLAYLIST_FETCHER = newFetcher(Playlist::class).by {
            allScalarFields()
        }

        private val DETAIL_PLAYLIST_FETCHER = newFetcher(Playlist::class).by {
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
        }
    }
}
