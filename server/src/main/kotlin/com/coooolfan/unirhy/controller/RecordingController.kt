package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import cn.dev33.satoken.annotation.SaCheckRole
import com.coooolfan.unirhy.config.ROLE_ADMIN
import com.coooolfan.unirhy.error.CommonException
import com.coooolfan.unirhy.error.MediaFileException
import com.coooolfan.unirhy.error.RecordingException
import com.coooolfan.unirhy.model.Recording
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.model.dto.RecordingMergeReq
import com.coooolfan.unirhy.model.dto.RecordingUpdate
import com.coooolfan.unirhy.service.ArtworkService
import com.coooolfan.unirhy.service.RecordingService
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

/**
 * 录音管理接口
 *
 * 提供录音信息的增删改查能力
 */
@SaCheckLogin
@RestController
@RequestMapping("/api/recordings")
class RecordingController(
    private val recordingService: RecordingService,
    private val artworkService: ArtworkService,
) {

    /**
     * 分页查询指定艺术家的录音
     *
     * @param artistId 艺术家 ID
     * @param pageIndex 页码（从 0 开始）
     * @param pageSize 每页条数
     * @return Page<Recording> 返回录音分页列表
     *
     * @api GET /api/recordings
     * @permission 需要登录认证
     * @description 按艺术家 ID 筛选录音；艺术家不存在时返回空分页
     */
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    fun listRecordings(
        @RequestParam artistId: Long,
        @RequestParam(required = false) pageIndex: Int?,
        @RequestParam(required = false) pageSize: Int?,
    ): Page<@FetchBy("RECORDING_LIST_FETCHER") Recording> {
        return recordingService.listRecordings(
            pageIndex = pageIndex ?: 0,
            pageSize = pageSize ?: 10,
            ids = null,
            workId = null,
            artistId = artistId,
            fetcher = RECORDING_LIST_FETCHER,
        )
    }

    /**
     * 获取指定录音
     *
     * 此接口用于根据录音 ID 获取播放器展示所需的最小录音信息。
     * 需要用户登录认证才能访问
     *
     * @param id Recording ID
     * @return Recording 返回录音信息（使用 PLAYBACK_RECORDING_FETCHER）
     *
     * @api GET /api/recordings/{id}
     * @permission 需要登录认证
     * @description 调用RecordingService.getRecording()方法获取录音信息
     */
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @Throws(RecordingException.NotFound::class)
    fun getRecording(
        @PathVariable id: Long,
    ): @FetchBy("PLAYBACK_RECORDING_FETCHER") Recording {
        return recordingService.getRecording(id, PLAYBACK_RECORDING_FETCHER)
    }

    /**
     * 更新录音信息
     *
     * 此接口用于更新系统中已有的录音信息
     * 需要用户登录认证才能访问
     *
     * @param id Recording ID
     * @param input RecordingUpdate 更新参数
     *
     * @api PUT /api/recordings/{id}
     * @permission 需要登录认证
     * @description 调用RecordingService.updateRecording()方法更新录音信息
     */
    @PutMapping("/{id}")
    @SaCheckRole(ROLE_ADMIN)
    @ResponseStatus(HttpStatus.OK)
    @Throws(CommonException.Forbidden::class)
    fun updateRecording(
        @PathVariable id: Long,
        @RequestBody input: RecordingUpdate,
    ) {
        recordingService.updateRecording(input.toEntity { this.id = id })
    }

    @PutMapping("/{id}/cover", consumes = ["multipart/form-data"])
    @SaCheckRole(ROLE_ADMIN)
    @ResponseStatus(HttpStatus.OK)
    @Throws(
        CommonException.Forbidden::class,
        RecordingException.NotFound::class,
        MediaFileException.ImageTooLarge::class,
        MediaFileException.InvalidImage::class,
    )
    fun updateRecordingCover(
        @PathVariable id: Long,
        @RequestParam("file") file: MultipartFile,
    ): @FetchBy("PLAYBACK_RECORDING_FETCHER") Recording {
        return artworkService.updateRecordingCover(id, file, PLAYBACK_RECORDING_FETCHER)
    }

    @DeleteMapping("/{id}/cover")
    @SaCheckRole(ROLE_ADMIN)
    @ResponseStatus(HttpStatus.OK)
    @Throws(CommonException.Forbidden::class, RecordingException.NotFound::class)
    fun removeRecordingCover(
        @PathVariable id: Long,
    ): @FetchBy("PLAYBACK_RECORDING_FETCHER") Recording {
        return artworkService.removeRecordingCover(id, PLAYBACK_RECORDING_FETCHER)
    }


    /**
     * 录音合并接口
     *
     * 此接口用于将多个录音合并为一个录音
     * 需要用户登录认证才能访问
     *
     * @param input RecordingMergeReq 合并参数
     *
     * @api POST /api/recordings/merge-requests
     * @permission 需要登录认证
     * @description 调用RecordingService.mergeRecording()方法合并录音
     */
    @PostMapping("/merge-requests")
    @SaCheckRole(ROLE_ADMIN)
    @ResponseStatus(HttpStatus.OK)
    @Throws(
        CommonException.Forbidden::class,
        RecordingException.TargetNotFound::class,
        RecordingException.WorkMismatch::class,
    )
    fun mergeRecording(@RequestBody input: RecordingMergeReq) {
        recordingService.mergeRecording(input)
    }

    companion object {
        val RECORDING_LIST_FETCHER = newFetcher(Recording::class).by {
            allScalarFields()
            work {
                allScalarFields()
            }
            artists {
                allScalarFields()
            }
            cover {
                allScalarFields()
                url()
            }
            assets {
                allScalarFields()
                mediaFile {
                    allScalarFields()
                    url()
                }
            }
        }

        val PLAYBACK_RECORDING_FETCHER = newFetcher(Recording::class).by {
            allScalarFields()
            work {
                allScalarFields()
            }
            artists {
                allScalarFields()
            }
            cover {
                allScalarFields()
                url()
            }
            assets {
                allScalarFields()
                mediaFile {
                    allScalarFields()
                    url()
                }
            }
        }
    }
}
