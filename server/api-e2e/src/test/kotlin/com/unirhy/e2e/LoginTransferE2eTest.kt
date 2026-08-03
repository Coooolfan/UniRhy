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

@SpringBootTest(
    classes = [UnirhyApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("full")
class LoginTransferE2eTest {

    @LocalServerPort
    private var port: Int = 0

    @AfterAll
    fun cleanup() {
        E2eRuntime.cleanup()
    }

    @Test
    fun `login transfer should require approval and issue a one-time device token`() {
        val source = bootstrapAdminSession(baseUrl())
        val phone = E2eHttpClient(baseUrl())

        val createResponse = source.api.post("/api/login-transfers")
        E2eAssert.status(createResponse, 201, "[create] logged-in device should create transfer")
        val created = E2eJson.mapper.readTree(createResponse.body())
        val transferId = created.path("id").asString()
        val secret = created.path("secret").asString()
        E2eAssert.jsonAt(createResponse.body(), "/status", "WAITING", "[create] transfer should wait for claim")

        val badClaimResponse = phone.patch(
            path = "/api/login-transfers/$transferId",
            json = claimBody("invalid-secret"),
        )
        E2eAssert.apiError(
            badClaimResponse,
            family = "LOGIN_TRANSFER",
            code = "NOT_FOUND",
            expectedStatus = 404,
            step = "[claim] wrong QR secret should not reveal transfer",
        )

        val claimResponse = phone.patch(
            path = "/api/login-transfers/$transferId",
            json = claimBody(secret),
        )
        E2eAssert.status(claimResponse, 200, "[claim] phone should claim transfer")
        E2eAssert.jsonAt(claimResponse.body(), "/transfer/status", "CLAIMED", "[claim] transfer should await approval")
        E2eAssert.jsonMissing(
            claimResponse.body(),
            "/transfer/deviceName",
            "[claim] claim response must stay within the new-device projection",
        )
        val claimToken = E2eJson.mapper.readTree(claimResponse.body()).path("claimAccessToken").asString()

        val claimedResponse = source.api.get("/api/login-transfers/$transferId")
        E2eAssert.status(claimedResponse, 200, "[source] source should observe claim")
        E2eAssert.jsonAt(claimedResponse.body(), "/deviceName", "E2E Phone", "[source] source should see device name")
        E2eAssert.jsonMissing(claimedResponse.body(), "/qrSecretHash", "[source] response must not expose QR secret hash")
        E2eAssert.jsonMissing(claimedResponse.body(), "/claimTokenHash", "[source] response must not expose claim token hash")
        E2eAssert.jsonMissing(claimedResponse.body(), "/accountId", "[source] response must not expose account id")

        val prematureTokenResponse = phone.post(
            path = "/api/login-transfers/$transferId/tokens",
            headers = claimHeaders(claimToken),
        )
        E2eAssert.apiError(
            prematureTokenResponse,
            family = "LOGIN_TRANSFER",
            code = "STATUS_CONFLICT",
            expectedStatus = 409,
            step = "[token] phone must wait for source approval",
        )

        val authorizeResponse = source.api.patch(
            path = "/api/login-transfers/$transferId",
            json = mapOf("status" to "AUTHORIZED"),
        )
        E2eAssert.status(authorizeResponse, 200, "[approve] source should authorize phone")
        E2eAssert.jsonAt(authorizeResponse.body(), "/transfer/status", "AUTHORIZED", "[approve] transfer should be authorized")

        val phoneStatusResponse = phone.get(
            path = "/api/login-transfers/$transferId",
            headers = claimHeaders(claimToken),
        )
        E2eAssert.status(phoneStatusResponse, 200, "[poll] phone should read authorized state")
        E2eAssert.jsonAt(phoneStatusResponse.body(), "/status", "AUTHORIZED", "[poll] phone should observe authorization")
        E2eAssert.jsonMissing(phoneStatusResponse.body(), "/deviceName", "[poll] phone must not read device fields")
        E2eAssert.jsonMissing(phoneStatusResponse.body(), "/platform", "[poll] phone must not read device fields")
        E2eAssert.jsonMissing(phoneStatusResponse.body(), "/clientVersion", "[poll] phone must not read device fields")
        E2eAssert.jsonMissing(phoneStatusResponse.body(), "/claimedAt", "[poll] phone must not read device fields")

        val tokenResponse = phone.post(
            path = "/api/login-transfers/$transferId/tokens",
            headers = claimHeaders(claimToken),
        )
        E2eAssert.status(tokenResponse, 201, "[token] authorized phone should create token")
        val phoneToken = E2eJson.mapper.readTree(tokenResponse.body()).path("token").asString()
        check(phoneToken.isNotBlank()) { "[token] response should contain a JWT" }

        val replayResponse = phone.post(
            path = "/api/login-transfers/$transferId/tokens",
            headers = claimHeaders(claimToken),
        )
        E2eAssert.apiError(
            replayResponse,
            family = "LOGIN_TRANSFER",
            code = "STATUS_CONFLICT",
            expectedStatus = 409,
            step = "[token] transfer must be one-time",
        )

        phone.setAuthToken(phoneToken)
        val meResponse = phone.get("/api/accounts/me")
        E2eAssert.status(meResponse, 200, "[session] issued token should authenticate phone")
        E2eAssert.jsonAt(
            meResponse.body(),
            "/email",
            source.credentials.email,
            "[session] phone should log in as transfer owner",
        )

        val completedResponse = source.api.get("/api/login-transfers/$transferId")
        E2eAssert.status(completedResponse, 200, "[complete] source should observe completion")
        E2eAssert.jsonAt(completedResponse.body(), "/status", "COMPLETED", "[complete] transfer should be completed")

        val cancellableResponse = source.api.post("/api/login-transfers")
        E2eAssert.status(cancellableResponse, 201, "[cancel] source should create another transfer")
        val cancellableId = E2eJson.mapper.readTree(cancellableResponse.body()).path("id").asString()
        val cancelResponse = source.api.delete("/api/login-transfers/$cancellableId")
        E2eAssert.status(cancelResponse, 204, "[cancel] source should cancel active transfer")
        val repeatCancelResponse = source.api.delete("/api/login-transfers/$cancellableId")
        E2eAssert.status(repeatCancelResponse, 204, "[cancel] cancellation should be idempotent")
    }

    private fun claimBody(secret: String): Map<String, String> = mapOf(
        "status" to "CLAIMED",
        "secret" to secret,
        "deviceName" to "E2E Phone",
        "platform" to "ANDROID",
        "clientVersion" to "test",
    )

    private fun claimHeaders(token: String): Map<String, String> =
        mapOf("Authorization" to "Bearer $token")

    private fun baseUrl(): String = "http://127.0.0.1:$port"

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun registerDatasource(registry: DynamicPropertyRegistry) {
            E2eRuntime.registerDatasource(registry)
        }
    }
}
