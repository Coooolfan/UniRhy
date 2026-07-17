package app.unirhy.playback.queue

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 队列导航 HTTP 客户端：仅覆盖锁屏/耳机切歌所需的 next/prev 两个端点。
 * WebView 被节流时事件转发不可靠，因此这两个操作由原生直调；
 * 其余队列 mutation 只发生在 UI 前台，仍由 TS 层调用。
 */
class QueueHttpApi(
    private val okHttpClient: OkHttpClient,
) {
    sealed interface NavigationResult {
        data object Ok : NavigationResult
        data object VersionConflict : NavigationResult
        data class Failed(val message: String) : NavigationResult
    }

    fun navigateNext(apiBaseUrl: String, token: String?, version: Long): NavigationResult {
        return post(apiBaseUrl, token, "next-navigation-requests", version)
    }

    fun navigatePrevious(apiBaseUrl: String, token: String?, version: Long): NavigationResult {
        return post(apiBaseUrl, token, "previous-navigation-requests", version)
    }

    private fun post(
        apiBaseUrl: String,
        token: String?,
        path: String,
        version: Long,
    ): NavigationResult {
        val requestBuilder = Request.Builder()
            .url("${apiBaseUrl.trimEnd('/')}/api/playback-queues/current/$path")
            .post("""{"version":$version}""".toRequestBody(JSON_MEDIA_TYPE))
        token?.let { requestBuilder.header("unirhy-token", it) }

        return runCatching {
            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                when {
                    response.isSuccessful -> NavigationResult.Ok
                    response.code == 409 -> NavigationResult.VersionConflict
                    else -> NavigationResult.Failed("HTTP ${response.code}")
                }
            }
        }.getOrElse { throwable ->
            NavigationResult.Failed(throwable.message ?: "network error")
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
