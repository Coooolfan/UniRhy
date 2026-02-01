package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.service.MediaFileAccessService
import org.babyfish.jimmer.client.ApiIgnore
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 媒体文件访问接口
 *
 * 提供封面、音频等媒体文件的访问能力（当前仅支持本地文件系统存储）
 */
@SaCheckLogin
@RestController
@RequestMapping("/api/media")
class MediaFileController(
    private val service: MediaFileAccessService,
) {

    /**
     * 获取媒体文件内容
     *
     * 支持 Range 请求，用于音频流式播放
     *
     * @param id MediaFile ID
     *
     * @api GET /api/media/{id}
     * @permission 需要登录认证
     * @description 根据媒体文件ID返回对应的二进制内容
     */
    @GetMapping("/{id}")
    @ApiIgnore
    fun getMedia(
        @PathVariable id: Long,
        @RequestHeader headers: HttpHeaders,
    ): ResponseEntity<Any> {
        val resolved = service.loadLocalFile(id)
        val resource = FileSystemResource(resolved.file)
        val mediaType = parseMediaType(resolved.mediaFile.mimeType)

        if (headers.range.isEmpty()) {
            return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(resource.contentLength())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(resource)
        }

        val contentLength = resource.contentLength()
        val range = headers.range.first()
        val region = try {
            range.toResourceRegion(resource)
        } catch (_: IllegalArgumentException) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .header(HttpHeaders.CONTENT_RANGE, "bytes */$contentLength")
                .build()
        }

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
            .contentType(mediaType)
            .contentLength(region.count)
            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
            .body(region)
    }

    private fun parseMediaType(mimeType: String): MediaType {
        return try {
            MediaType.parseMediaType(mimeType)
        } catch (_: IllegalArgumentException) {
            MediaType.APPLICATION_OCTET_STREAM
        }
    }
}
