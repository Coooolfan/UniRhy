package com.coooolfan.unirhy.controller.support

import com.coooolfan.unirhy.service.ResolvedMediaFile
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRange
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Component
class MediaFileResponseBuilder {

    fun full(
        resolved: ResolvedMediaFile,
        requestHeaders: HttpHeaders,
    ): ResponseEntity<ByteArray> {
        val mediaType = parseMediaType(resolved.mediaFile.mimeType)
        val lastModified = normalizeToSeconds(resolved.lastModified.toEpochMilli())

        if (isNotModified(requestHeaders, lastModified)) {
            return notModified(resolved, lastModified)
        }

        return fullBytesResponse(resolved, mediaType, lastModified)
    }

    fun head(
        resolved: ResolvedMediaFile,
        requestHeaders: HttpHeaders,
    ): ResponseEntity<Void> {
        val mediaType = parseMediaType(resolved.mediaFile.mimeType)
        val lastModified = normalizeToSeconds(resolved.lastModified.toEpochMilli())

        if (isNotModified(requestHeaders, lastModified)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .headers(baseHeaders(resolved, lastModified, null))
                .build()
        }

        val responseHeaders = baseHeaders(resolved, lastModified, mediaType)
        responseHeaders.contentLength = resolved.size
        return ResponseEntity.status(HttpStatus.OK)
            .headers(responseHeaders)
            .build()
    }

    fun range(
        resolved: ResolvedMediaFile,
        requestHeaders: HttpHeaders,
    ): ResponseEntity<ByteArray> {
        val mediaType = parseMediaType(resolved.mediaFile.mimeType)
        val lastModified = normalizeToSeconds(resolved.lastModified.toEpochMilli())
        val total = resolved.size

        if (isNotModified(requestHeaders, lastModified)) {
            return notModified(resolved, lastModified)
        }

        if (!ifRangeMatched(requestHeaders, lastModified)) {
            return fullBytesResponse(resolved, mediaType, lastModified)
        }

        val rawRange = requestHeaders.getFirst(HttpHeaders.RANGE)
            ?: return rangeNotSatisfiable(resolved, total, lastModified)
        val ranges = try {
            HttpRange.parseRanges(rawRange)
        } catch (_: IllegalArgumentException) {
            return rangeNotSatisfiable(resolved, total, lastModified)
        }
        if (ranges.isEmpty()) {
            return rangeNotSatisfiable(resolved, total, lastModified)
        }

        val normalizedRanges = try {
            ranges.map { it.getRangeStart(total) to it.getRangeEnd(total) }
        } catch (_: IllegalArgumentException) {
            return rangeNotSatisfiable(resolved, total, lastModified)
        }
        if (normalizedRanges.isEmpty() || normalizedRanges.any { (start, end) -> start > end || start >= total }) {
            return rangeNotSatisfiable(resolved, total, lastModified)
        }

        if (normalizedRanges.size == 1) {
            val (start, end) = normalizedRanges.first()
            val body = resolved.readRange(start, end)
            val responseHeaders = baseHeaders(resolved, lastModified, mediaType)
            responseHeaders[HttpHeaders.CONTENT_RANGE] = "bytes $start-$end/$total"
            responseHeaders.contentLength = body.size.toLong()
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .headers(responseHeaders)
                .body(body)
        }

        val boundary = "unirhy-${UUID.randomUUID()}"
        val body = buildMultipartByteRanges(resolved, mediaType, total, normalizedRanges, boundary)
        val responseHeaders = baseHeaders(resolved, lastModified, null)
        responseHeaders.contentType = MediaType.parseMediaType("multipart/byteranges; boundary=$boundary")
        responseHeaders.contentLength = body.size.toLong()
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
            .headers(responseHeaders)
            .body(body)
    }

    private fun fullBytesResponse(
        resolved: ResolvedMediaFile,
        mediaType: MediaType,
        lastModified: Long,
    ): ResponseEntity<ByteArray> {
        val body = resolved.readAll()
        val responseHeaders = baseHeaders(resolved, lastModified, mediaType)
        responseHeaders.contentLength = body.size.toLong()
        return ResponseEntity.status(HttpStatus.OK)
            .headers(responseHeaders)
            .body(body)
    }

    private fun notModified(
        resolved: ResolvedMediaFile,
        lastModified: Long,
    ): ResponseEntity<ByteArray> {
        return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
            .headers(baseHeaders(resolved, lastModified, null))
            .build()
    }

    private fun rangeNotSatisfiable(
        resolved: ResolvedMediaFile,
        total: Long,
        lastModified: Long,
    ): ResponseEntity<ByteArray> {
        val responseHeaders = baseHeaders(resolved, lastModified, null)
        responseHeaders[HttpHeaders.CONTENT_RANGE] = "bytes */$total"
        return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
            .headers(responseHeaders)
            .build()
    }

    private fun baseHeaders(
        resolved: ResolvedMediaFile,
        lastModified: Long,
        mediaType: MediaType?,
    ): HttpHeaders {
        return HttpHeaders().also { headers ->
            headers.set(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_VALUE)
            headers.set(HttpHeaders.ACCEPT_RANGES, "bytes")
            headers.lastModified = lastModified
            headers.set(HttpHeaders.CONTENT_DISPOSITION, inlineContentDisposition(resolved.fileName))
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
        lastModified: Long,
    ): Boolean {
        val ifRange = headers.getFirst(HttpHeaders.IF_RANGE)?.trim() ?: return true
        if (ifRange.startsWith("\"")) {
            return false
        }

        val ifRangeDate = parseHttpDate(ifRange) ?: return false
        return lastModified <= normalizeToSeconds(ifRangeDate)
    }

    private fun isNotModified(
        headers: HttpHeaders,
        lastModified: Long,
    ): Boolean {
        val ifModifiedSince = headers.ifModifiedSince
        if (ifModifiedSince < 0) {
            return false
        }
        return lastModified <= normalizeToSeconds(ifModifiedSince)
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

    private fun normalizeToSeconds(value: Long): Long {
        if (value < 0) {
            return 0
        }
        return value / 1000 * 1000
    }

    private fun parseMediaType(mimeType: String): MediaType {
        return try {
            MediaType.parseMediaType(mimeType)
        } catch (_: IllegalArgumentException) {
            MediaType.APPLICATION_OCTET_STREAM
        }
    }

    private fun buildMultipartByteRanges(
        resolved: ResolvedMediaFile,
        mediaType: MediaType,
        total: Long,
        ranges: List<Pair<Long, Long>>,
        boundary: String,
    ): ByteArray {
        val body = ByteArrayOutputStream()
        for ((start, end) in ranges) {
            body.writeUtf8("--$boundary\r\n")
            body.writeUtf8("Content-Type: $mediaType\r\n")
            body.writeUtf8("Content-Range: bytes $start-$end/$total\r\n\r\n")
            body.write(resolved.readRange(start, end))
            body.writeUtf8("\r\n")
        }
        body.writeUtf8("--$boundary--\r\n")
        return body.toByteArray()
    }

    private fun ByteArrayOutputStream.writeUtf8(value: String) {
        write(value.toByteArray(StandardCharsets.UTF_8))
    }

    companion object {
        private const val CACHE_CONTROL_VALUE = "private, max-age=31536000, immutable"
        private const val X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options"
    }
}
