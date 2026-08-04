package com.unirhy.e2e

import com.coooolfan.unirhy.UnirhyApplication
import com.unirhy.e2e.support.E2eAssert
import com.unirhy.e2e.support.E2eHttpClient
import com.unirhy.e2e.support.E2eJson
import com.unirhy.e2e.support.E2eRuntime
import com.unirhy.e2e.support.bootstrapAdminSession
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * 过期语义使用极短的有效期单独验证：查询读到真实终态，状态变更被 410 中断。
 */
@SpringBootTest(
    classes = [UnirhyApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("full")
class LoginTransferExpiryE2eTest {

    @LocalServerPort
    private var port: Int = 0

    @AfterAll
    fun cleanup() {
        E2eRuntime.cleanup()
    }

    @Test
    fun `expired login transfer should read as terminal but reject state changes`() {
        val source = bootstrapAdminSession(baseUrl())
        val phone = E2eHttpClient(baseUrl())

        val createResponse = source.api.post("/api/login-transfers")
        E2eAssert.status(createResponse, 201, "[create] source should create short-lived transfer")
        val created = E2eJson.mapper.readTree(createResponse.body())
        val transferId = created.path("id").asString()
        val secret = created.path("secret").asString()

        Thread.sleep(EXPIRY_WAIT_MILLIS)

        val expiredRead = source.api.get("/api/login-transfers/$transferId")
        E2eAssert.status(expiredRead, 200, "[poll] expired transfer should still be readable")
        E2eAssert.jsonAt(expiredRead.body(), "/status", "EXPIRED", "[poll] read should surface the real terminal state")

        val claimAfterExpiry = phone.post(
            path = "/api/login-transfers/claims",
            json = mapOf(
                "secret" to secret,
                "deviceName" to "E2E Phone",
                "platform" to "ANDROID",
            ),
        )
        E2eAssert.apiError(
            claimAfterExpiry,
            family = "LOGIN_TRANSFER",
            code = "EXPIRED",
            expectedStatus = 410,
            step = "[claim] expired transfer must reject state changes",
        )
    }

    private fun baseUrl(): String = "http://127.0.0.1:$port"

    companion object {
        private const val TRANSFER_TTL_SECONDS = 1L
        private const val EXPIRY_WAIT_MILLIS = 1_500L

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            E2eRuntime.registerDatasource(registry)
            registry.add("unirhy.login-transfer.ttl-seconds") { TRANSFER_TTL_SECONDS }
        }
    }
}
