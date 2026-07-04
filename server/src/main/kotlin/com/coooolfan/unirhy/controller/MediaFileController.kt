package com.coooolfan.unirhy.controller

import cn.dev33.satoken.stp.StpUtil
import com.coooolfan.unirhy.controller.support.MediaFileResponseBuilder
import com.coooolfan.unirhy.service.MediaFileAccessService
import com.coooolfan.unirhy.service.MediaUrlSigner
import org.babyfish.jimmer.client.ApiIgnore
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody

/**
 * 媒体文件访问接口
 *
 * 提供封面、音频等媒体文件的访问能力（当前仅支持本地文件系统存储）
 */
@RestController
@RequestMapping(MediaFileRoutes.MEDIA_API_BASE_PATH)
class MediaFileController(
    private val service: MediaFileAccessService,
    private val urlSigner: MediaUrlSigner,
    private val responses: MediaFileResponseBuilder,
) {

    private fun authenticateRequest(id: Long, sig: String?, exp: Long?) {
        if (sig != null && exp != null) {
            if (!urlSigner.verify(id, sig, exp)) {
                throw org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Invalid or expired signature"
                )
            }
            return
        }
        StpUtil.checkLogin()
    }

    /**
     * 获取媒体文件内容
     *
     * 支持 Range 请求，用于音频流式播放
     *
     * @param id MediaFile ID
     *
     * @api GET /api/media-files/{id}
     * @permission 需要登录认证或有效签名
     * @description 根据媒体文件ID返回对应的二进制内容
     */
    @GetMapping(MediaFileRoutes.MEDIA_FILE_PATH_PATTERN, headers = ["!Range"])
    @ApiIgnore
    fun getMedia(
        @PathVariable id: Long,
        @RequestHeader headers: HttpHeaders,
        @RequestParam("_sig", required = false) sig: String?,
        @RequestParam("_exp", required = false) exp: Long?,
    ): ResponseEntity<StreamingResponseBody> {
        authenticateRequest(id, sig, exp)
        val resolved = service.load(id)
        return responses.full(resolved, headers)
    }

    /**
     * 获取媒体文件元信息（HEAD）
     *
     * 仅返回响应头（Content-Type/Content-Length/Last-Modified 等），不返回响应体
     * 用于客户端预检文件大小、MIME 类型与缓存有效性
     *
     * @param id MediaFile ID
     *
     * @api HEAD /api/media-files/{id}
     * @permission 需要登录认证或有效签名
     * @description 根据媒体文件ID返回对应资源的响应头
     */
    @RequestMapping(MediaFileRoutes.MEDIA_FILE_PATH_PATTERN, method = [RequestMethod.HEAD], headers = ["!Range"])
    @ApiIgnore
    fun headMedia(
        @PathVariable id: Long,
        @RequestHeader headers: HttpHeaders,
        @RequestParam("_sig", required = false) sig: String?,
        @RequestParam("_exp", required = false) exp: Long?,
    ): ResponseEntity<Void> {
        authenticateRequest(id, sig, exp)
        val resolved = service.load(id)
        return responses.head(resolved, headers)
    }

    /**
     * 获取媒体文件区间内容（Range 请求）
     *
     * 处理带 Range 头的 GET 请求，返回 206 Partial Content；
     * 若 If-Range 不匹配则回退为完整资源响应；
     * Range 解析失败或越界时返回 416 Requested Range Not Satisfiable
     *
     * @param id MediaFile ID
     *
     * @api GET /api/media-files/{id} (Range)
     * @permission 需要登录认证或有效签名
     * @description 根据 Range 头返回媒体文件指定字节区间
     */
    @GetMapping(MediaFileRoutes.MEDIA_FILE_PATH_PATTERN, headers = ["Range"])
    @ApiIgnore
    fun getMediaWithRange(
        @PathVariable id: Long,
        @RequestHeader headers: HttpHeaders,
        @RequestParam("_sig", required = false) sig: String?,
        @RequestParam("_exp", required = false) exp: Long?,
    ): ResponseEntity<StreamingResponseBody> {
        authenticateRequest(id, sig, exp)
        val resolved = service.load(id)
        return responses.range(resolved, headers)
    }
}
