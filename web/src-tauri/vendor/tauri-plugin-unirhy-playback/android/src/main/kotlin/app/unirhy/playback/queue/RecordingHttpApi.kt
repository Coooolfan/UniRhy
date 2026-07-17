package app.unirhy.playback.queue

import app.unirhy.playback.sync.PlaybackSyncJson
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal data class RecordingAudioSource(
    val mediaFileId: Long,
    val mimeType: String,
)

internal fun selectPreferredAudioMediaFileId(
    sources: Iterable<RecordingAudioSource>,
    preferredAssetFormat: String?,
): Long? {
    val normalizedPreferredFormat = preferredAssetFormat
        ?.trim()
        ?.lowercase()
        ?.takeIf(String::isNotEmpty)
    var firstAudioMediaFileId: Long? = null

    for (source in sources) {
        val mimeType = source.mimeType.trim().lowercase()
        if (!mimeType.startsWith("audio/")) {
            continue
        }
        if (firstAudioMediaFileId == null) {
            firstAudioMediaFileId = source.mediaFileId
        }
        if (normalizedPreferredFormat != null && mimeType == normalizedPreferredFormat) {
            return source.mediaFileId
        }
    }

    return firstAudioMediaFileId
}

/** 拉取 Recording 播放详情，并由 Android 客户端按本地格式偏好解析音源。 */
internal class RecordingHttpApi(
    private val okHttpClient: OkHttpClient,
) {
    private val audioSourceCache = ConcurrentHashMap<Long, List<RecordingAudioSource>>()

    fun resolveAudioMediaFileId(
        apiBaseUrl: String,
        token: String?,
        recordingId: Long,
        preferredAssetFormat: String?,
        fallbackMediaFileId: Long?,
        callback: (Long?) -> Unit,
    ) {
        audioSourceCache[recordingId]?.let { sources ->
            callback(selectPreferredAudioMediaFileId(sources, preferredAssetFormat) ?: fallbackMediaFileId)
            return
        }

        val requestBuilder = Request.Builder()
            .url("${apiBaseUrl.trimEnd('/')}/api/recordings/$recordingId")
            .get()
        token?.let { requestBuilder.header(TOKEN_HEADER, it) }

        okHttpClient.newCall(requestBuilder.build()).enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    callback(fallbackMediaFileId)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            callback(fallbackMediaFileId)
                            return
                        }
                        val sources = runCatching {
                            parseAudioSources(response.body?.string().orEmpty())
                        }.getOrElse {
                            callback(fallbackMediaFileId)
                            return
                        }
                        audioSourceCache[recordingId] = sources
                        callback(
                            selectPreferredAudioMediaFileId(sources, preferredAssetFormat)
                                ?: fallbackMediaFileId,
                        )
                    }
                }
            },
        )
    }

    private fun parseAudioSources(body: String): List<RecordingAudioSource> {
        val assets = PlaybackSyncJson.mapper.readTree(body).path("assets")
        if (!assets.isArray) {
            return emptyList()
        }
        return assets.mapNotNull { asset ->
            val mediaFile = asset.path("mediaFile")
            val mediaFileId = mediaFile.path("id").takeIf { it.isIntegralNumber }?.longValue()
                ?: return@mapNotNull null
            val mimeType = mediaFile.path("mimeType").asText("")
            RecordingAudioSource(mediaFileId = mediaFileId, mimeType = mimeType)
        }
    }

    private companion object {
        const val TOKEN_HEADER = "unirhy-token"
    }
}
