package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.service.MediaFileResolver
import com.coooolfan.unirhy.service.ResolvedMediaFile
import org.babyfish.jimmer.client.ApiIgnore
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.core.io.support.ResourceRegion
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRange
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 媒体文件访问接口
 *
 * 提供封面、音频等媒体文件的访问能力（当前仅支持本地文件系统存储）
 */
@SaCheckLogin
@RestController
@RequestMapping("/api/media")
class MediaFileController(
    private val service: MediaFileResolver,
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
    @GetMapping("/{id}", headers = ["!Range"])
    @ApiIgnore
    fun getMedia(
        @PathVariable id: Long,
        @RequestHeader headers: HttpHeaders,
    ): ResponseEntity<Resource> {
        val resolved = service.loadLocalFile(id)
        val resource = FileSystemResource(resolved.file)
        val mediaType = parseMediaType(resolved.mediaFile.mimeType)
        val etag = toStrongEtag(resolved.mediaFile.sha256)
        val lastModified = normalizeToSeconds(resolved.file.lastModified())

        if (isNotModified(headers, etag, lastModified)) {
            return notModifiedResourceResponse(resolved, etag, lastModified)
        }

        return fullResponse(resolved, resource, mediaType, etag, lastModified)
    }

    @GetMapping("/{id}", headers = ["Range"])
    @ApiIgnore
    fun getMediaWithRange(
        @PathVariable id: Long,
        @RequestHeader headers: HttpHeaders,
    ): ResponseEntity<List<ResourceRegion>> {
        val resolved = service.loadLocalFile(id)
        val resource = FileSystemResource(resolved.file)
        val mediaType = parseMediaType(resolved.mediaFile.mimeType)
        val etag = toStrongEtag(resolved.mediaFile.sha256)
        val lastModified = normalizeToSeconds(resolved.file.lastModified())
        val total = resource.contentLength()

        if (isNotModified(headers, etag, lastModified)) {
            return notModifiedRangeResponse(resolved, etag, lastModified)
        }

        if (!ifRangeMatched(headers, etag, lastModified)) {
            // If-Range 不匹配时必须回退到完整资源响应
            return fullResponse(resolved, resource, mediaType, etag, lastModified).asRangeResponse()
        }

        val rawRange = headers.getFirst(HttpHeaders.RANGE)
            ?: return rangeNotSatisfiable(resolved, total, etag, lastModified)
        val ranges = try {
            HttpRange.parseRanges(rawRange)
        } catch (_: IllegalArgumentException) {
            return rangeNotSatisfiable(resolved, total, etag, lastModified)
        }
        if (ranges.isEmpty()) {
            return rangeNotSatisfiable(resolved, total, etag, lastModified)
        }

        val regions = try {
            ranges.map { it.toResourceRegion(resource) }
        } catch (_: IllegalArgumentException) {
            return rangeNotSatisfiable(resolved, total, etag, lastModified)
        }
        if (regions.isEmpty()) {
            return rangeNotSatisfiable(resolved, total, etag, lastModified)
        }

        val contentType = if (regions.size == 1) mediaType else null

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
            .headers(baseHeaders(resolved, etag, lastModified, contentType))
            .body(regions)
    }

    private fun fullResponse(
        resolved: ResolvedMediaFile,
        resource: Resource,
        mediaType: MediaType,
        etag: String,
        lastModified: Long,
    ): ResponseEntity<Resource> {
        val responseHeaders = baseHeaders(resolved, etag, lastModified, mediaType)
        responseHeaders.contentLength = resource.contentLength()
        return ResponseEntity.status(HttpStatus.OK)
            .headers(responseHeaders)
            .body(resource)
    }

    private fun notModifiedResourceResponse(
        resolved: ResolvedMediaFile,
        etag: String,
        lastModified: Long,
    ): ResponseEntity<Resource> {
        return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
            .headers(baseHeaders(resolved, etag, lastModified, null))
            .build()
    }

    private fun notModifiedRangeResponse(
        resolved: ResolvedMediaFile,
        etag: String,
        lastModified: Long,
    ): ResponseEntity<List<ResourceRegion>> {
        return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
            .headers(baseHeaders(resolved, etag, lastModified, null))
            .build()
    }

    private fun rangeNotSatisfiable(
        resolved: ResolvedMediaFile,
        total: Long,
        etag: String,
        lastModified: Long,
    ): ResponseEntity<List<ResourceRegion>> {
        val responseHeaders = baseHeaders(resolved, etag, lastModified, null)
        responseHeaders[HttpHeaders.CONTENT_RANGE] = "bytes */$total"
        return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
            .headers(responseHeaders)
            .build()
    }

    private fun baseHeaders(
        resolved: ResolvedMediaFile,
        etag: String,
        lastModified: Long,
        mediaType: MediaType?,
    ): HttpHeaders {
        return HttpHeaders().also { headers ->
            headers.set(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_VALUE)
            headers.set(HttpHeaders.ACCEPT_RANGES, "bytes")
            headers.set(HttpHeaders.ETAG, etag)
            headers.lastModified = lastModified
            headers.set(HttpHeaders.CONTENT_DISPOSITION, inlineContentDisposition(resolved.file.name))
            headers.set(X_CONTENT_TYPE_OPTIONS, "nosniff")
            if (mediaType != null) {
                headers.contentType = mediaType
            }
        }
    }

    private fun inlineContentDisposition(fileName: String): String {
        return ContentDisposition.inline()
            .filename(fileName, StandardCharsets.UTF_8)
            .build()
            .toString()
    }

    private fun ifRangeMatched(
        headers: HttpHeaders,
        etag: String,
        lastModified: Long,
    ): Boolean {
        val ifRange = headers.getFirst(HttpHeaders.IF_RANGE)?.trim() ?: return true
        if (ifRange.startsWith("W/")) {
            return false
        }
        if (ifRange.startsWith("\"")) {
            return ifRange == etag
        }

        val ifRangeDate = parseHttpDate(ifRange) ?: return false
        return normalizeToSeconds(lastModified) <= normalizeToSeconds(ifRangeDate)
    }

    private fun isNotModified(
        headers: HttpHeaders,
        etag: String,
        lastModified: Long,
    ): Boolean {
        if (headers.ifNoneMatch.isNotEmpty()) {
            return headers.ifNoneMatch.any { etagMatches(it, etag) }
        }

        val ifModifiedSince = headers.ifModifiedSince
        if (ifModifiedSince < 0) {
            return false
        }
        return normalizeToSeconds(lastModified) <= normalizeToSeconds(ifModifiedSince)
    }

    private fun etagMatches(headerValue: String, currentEtag: String): Boolean {
        val normalizedCurrent = normalizeEtag(currentEtag)
        return headerValue.split(",")
            .map { it.trim() }
            .any {
                it == "*" || normalizeEtag(it) == normalizedCurrent
            }
    }

    private fun normalizeEtag(etag: String): String {
        return etag.removePrefix("W/")
            .trim()
            .removeSurrounding("\"")
    }

    private fun parseHttpDate(headerValue: String): Long? {
        return try {
            ZonedDateTime.parse(headerValue, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant()
                .toEpochMilli()
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun toStrongEtag(sha256: String): String {
        return "\"$sha256\""
    }

    private fun normalizeToSeconds(value: Long): Long {
        if (value < 0) {
            return 0
        }
        return value / 1000 * 1000
    }

    @Suppress("UNCHECKED_CAST")
    private fun ResponseEntity<Resource>.asRangeResponse(): ResponseEntity<List<ResourceRegion>> {
        return this as ResponseEntity<List<ResourceRegion>>
    }

    private fun parseMediaType(mimeType: String): MediaType {
        return try {
            MediaType.parseMediaType(mimeType)
        } catch (_: IllegalArgumentException) {
            MediaType.APPLICATION_OCTET_STREAM
        }
    }

    companion object {
        private const val CACHE_CONTROL_VALUE = "private, max-age=31536000, immutable"
        private const val X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options"
    }
}
