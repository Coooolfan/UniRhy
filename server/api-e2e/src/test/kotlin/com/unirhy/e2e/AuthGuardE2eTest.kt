package com.unirhy.e2e

import com.coooolfan.unirhy.UnirhyApplication
import com.unirhy.e2e.support.E2eAssert
import com.unirhy.e2e.support.E2eHttpClient
import com.unirhy.e2e.support.E2eRuntime
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * 统一的鉴权闸门回归：对每个需登录的接口在未携带会话时发起请求，断言返回 401 AUTHENTICATION_FAILED。
 *
 * 鉴权由全局过滤器在进入 Controller 之前完成，对所有受保护接口行为一致，因此这里用数据驱动方式
 * 枚举受保护端点，避免在各业务测试中逐接口重复同一段断言。请求无需携带合法请求体，未登录请求
 * 会在参数绑定前被过滤器拦截。
 *
 * 与认证子系统自身强相关的用例（公开 status 接口、登录闭环、大小写绕过等）仍保留在 SystemAuthE2eTest。
 */
@SpringBootTest(
    classes = [UnirhyApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("full")
class AuthGuardE2eTest {

    @LocalServerPort
    private var port: Int = 0

    @AfterAll
    fun cleanup() {
        E2eRuntime.cleanup()
    }

    @ParameterizedTest(name = "{0} should require login")
    @MethodSource("protectedEndpoints")
    fun `protected endpoint should reject unauthenticated access`(endpoint: ProtectedEndpoint) {
        val api = E2eHttpClient(baseUrl())
        val response = when (endpoint.method) {
            "GET" -> api.get(endpoint.path, endpoint.headers)
            "POST" -> api.post(path = endpoint.path, headers = endpoint.headers)
            "PUT" -> api.put(path = endpoint.path, headers = endpoint.headers)
            "DELETE" -> api.delete(path = endpoint.path, headers = endpoint.headers)
            "HEAD" -> api.head(endpoint.path, endpoint.headers)
            else -> error("unsupported method ${endpoint.method}")
        }

        if (endpoint.method == "HEAD") {
            // HEAD 响应不含错误体，仅校验状态码。
            E2eAssert.status(response, 401, "[auth] $endpoint should require login")
        } else {
            E2eAssert.apiError(
                response = response,
                family = "COMMON",
                code = "AUTHENTICATION_FAILED",
                expectedStatus = 401,
                step = "[auth] $endpoint should require login",
            )
        }
    }

    private fun baseUrl(): String = "http://127.0.0.1:$port"

    data class ProtectedEndpoint(
        val method: String,
        val path: String,
        val headers: Map<String, String> = emptyMap(),
    ) {
        override fun toString(): String {
            val suffix = if (headers.containsKey("Range")) " (Range)" else ""
            return "$method $path$suffix"
        }
    }

    companion object {
        // 路径中的 id/占位段任取即可：未登录请求在路由命中前已被过滤器拦截。
        @JvmStatic
        fun protectedEndpoints(): List<ProtectedEndpoint> = listOf(
            // accounts
            ProtectedEndpoint("GET", "/api/accounts"),
            ProtectedEndpoint("POST", "/api/accounts"),
            ProtectedEndpoint("GET", "/api/accounts/me"),
            ProtectedEndpoint("DELETE", "/api/accounts/1"),
            ProtectedEndpoint("PUT", "/api/accounts/1"),
            // playlists
            ProtectedEndpoint("GET", "/api/playlists"),
            ProtectedEndpoint("POST", "/api/playlists"),
            ProtectedEndpoint("GET", "/api/playlists/1"),
            ProtectedEndpoint("PUT", "/api/playlists/1"),
            ProtectedEndpoint("DELETE", "/api/playlists/1"),
            ProtectedEndpoint("PUT", "/api/playlists/1/recordings/1"),
            ProtectedEndpoint("DELETE", "/api/playlists/1/recordings/1"),
            ProtectedEndpoint("PUT", "/api/playlists/1/recordings/reorder"),
            // artists
            ProtectedEndpoint("GET", "/api/artists"),
            ProtectedEndpoint("GET", "/api/artists/search"),
            ProtectedEndpoint("POST", "/api/artists"),
            ProtectedEndpoint("PUT", "/api/artists/1"),
            ProtectedEndpoint("POST", "/api/artists/merge"),
            // works
            ProtectedEndpoint("GET", "/api/works"),
            ProtectedEndpoint("GET", "/api/works/random"),
            ProtectedEndpoint("GET", "/api/works/search"),
            ProtectedEndpoint("GET", "/api/works/1"),
            ProtectedEndpoint("PUT", "/api/works/1"),
            ProtectedEndpoint("DELETE", "/api/works/1"),
            ProtectedEndpoint("POST", "/api/works/merge"),
            // albums
            ProtectedEndpoint("GET", "/api/albums"),
            ProtectedEndpoint("GET", "/api/albums/search"),
            ProtectedEndpoint("GET", "/api/albums/1"),
            ProtectedEndpoint("PUT", "/api/albums/1"),
            ProtectedEndpoint("PUT", "/api/albums/1/recordings/reorder"),
            // recordings
            ProtectedEndpoint("GET", "/api/recordings/1"),
            ProtectedEndpoint("PUT", "/api/recordings/1"),
            ProtectedEndpoint("PUT", "/api/recordings/merge"),
            // media
            ProtectedEndpoint("GET", "/api/media/1"),
            ProtectedEndpoint("GET", "/api/media/1", mapOf("Range" to "bytes=0-3")),
            ProtectedEndpoint("HEAD", "/api/media/1"),
            // playback queue
            ProtectedEndpoint("GET", "/api/playback/current-queue"),
            ProtectedEndpoint("PUT", "/api/playback/current-queue"),
            ProtectedEndpoint("POST", "/api/playback/current-queue/items"),
            ProtectedEndpoint("PUT", "/api/playback/current-queue/order"),
            ProtectedEndpoint("PUT", "/api/playback/current-queue/current"),
            ProtectedEndpoint("PUT", "/api/playback/current-queue/strategy"),
            ProtectedEndpoint("POST", "/api/playback/current-queue/actions/next"),
            ProtectedEndpoint("POST", "/api/playback/current-queue/actions/previous"),
            ProtectedEndpoint("POST", "/api/playback/current-queue/actions/remove"),
            ProtectedEndpoint("POST", "/api/playback/current-queue/actions/clear"),
            // tasks
            ProtectedEndpoint("GET", "/api/task/logs"),
            ProtectedEndpoint("POST", "/api/task/scan"),
            ProtectedEndpoint("POST", "/api/task/transcode"),
            // storage (fs)
            ProtectedEndpoint("GET", "/api/storage/fs"),
            ProtectedEndpoint("POST", "/api/storage/fs"),
            ProtectedEndpoint("GET", "/api/storage/fs/1"),
            ProtectedEndpoint("PUT", "/api/storage/fs/1"),
            ProtectedEndpoint("DELETE", "/api/storage/fs/1"),
            // storage (oss)
            ProtectedEndpoint("GET", "/api/storage/oss"),
            ProtectedEndpoint("POST", "/api/storage/oss"),
            ProtectedEndpoint("GET", "/api/storage/oss/1"),
            ProtectedEndpoint("PUT", "/api/storage/oss/1"),
            ProtectedEndpoint("DELETE", "/api/storage/oss/1"),
            // plugins
            ProtectedEndpoint("GET", "/api/plugins"),
            ProtectedEndpoint("POST", "/api/plugins/upload"),
            ProtectedEndpoint("PUT", "/api/plugins/1/enabled"),
            ProtectedEndpoint("DELETE", "/api/plugins/1"),
            ProtectedEndpoint("GET", "/api/plugins/1/download"),
            ProtectedEndpoint("POST", "/api/plugins/ARTIST_NORMALIZATION/submit"),
        )

        @JvmStatic
        @DynamicPropertySource
        fun registerDatasource(registry: DynamicPropertyRegistry) {
            E2eRuntime.registerDatasource(registry)
        }
    }
}
