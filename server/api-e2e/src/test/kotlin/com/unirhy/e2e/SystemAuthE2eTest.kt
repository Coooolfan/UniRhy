package com.unirhy.e2e

import com.coooolfan.unirhy.UnirhyApplication
import com.unirhy.e2e.support.E2eAdminSession
import com.unirhy.e2e.support.E2eAssert
import com.unirhy.e2e.support.E2eRuntime
import com.unirhy.e2e.support.ensureSystemInitialized
import com.unirhy.e2e.support.loginAsAdmin
import com.unirhy.e2e.support.newAdminSession
import com.unirhy.e2e.support.systemInitRequest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.http.HttpResponse

@SpringBootTest(
    classes = [UnirhyApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@Tag("full")
class SystemAuthE2eTest {

    @LocalServerPort
    private var port: Int = 0

    @AfterAll
    fun cleanup() {
        E2eRuntime.cleanup()
    }

    @Test
    @Order(1)
    fun `status and protected endpoints require authentication`() {
        val state = prepareState()

        val statusResponse = state.api.get("/api/system/config/status")
        E2eAssert.status(statusResponse, 200, "[status] status endpoint should be reachable")
        E2eAssert.jsonAt(
            statusResponse.body(),
            "/initialized",
            false,
            "[status] new temporary db should not be initialized",
        )

        val getConfigResponse = state.api.get("/api/system/config")
        assertAuthenticationFailed(
            getConfigResponse,
            "[auth] get config should require login",
        )

        val updateConfigResponse = state.api.put(
            path = "/api/system/config",
            json = mapOf("fsProviderId" to 0L),
        )
        assertAuthenticationFailed(
            updateConfigResponse,
            "[auth] update config should require login",
        )

        val logoutResponse = state.api.delete("/api/tokens/current")
        assertAuthenticationFailed(
            logoutResponse,
            "[auth] logout should require login",
        )
    }

    @Test
    @Order(2)
    fun `initialize login get update logout should form closed session flow`() {
        val state = prepareState()

        val initResponse = state.api.post(
            path = "/api/system/config",
            json = systemInitRequest(state),
        )
        E2eAssert.status(initResponse, 201, "[flow] system initialization should succeed")

        val statusAfterInitResponse = state.api.get("/api/system/config/status")
        E2eAssert.status(statusAfterInitResponse, 200, "[flow] status endpoint should remain reachable")
        E2eAssert.jsonAt(
            statusAfterInitResponse.body(),
            "/initialized",
            true,
            "[flow] system should be initialized after create",
        )

        loginAsAdmin(state)

        val getConfigResponse = state.api.get("/api/system/config")
        E2eAssert.status(getConfigResponse, 200, "[flow] get system config should succeed after login")
        E2eAssert.jsonAt(getConfigResponse.body(), "/id", 0, "[flow] system config id should be singleton id")
        E2eAssert.jsonAt(getConfigResponse.body(), "/fsProviderId", 0, "[flow] system config fs provider id should be 0")
        E2eAssert.jsonAt(getConfigResponse.body(), "/ossProviderId", null, "[flow] system config oss provider should be null")

        val updateConfigResponse = state.api.put(
            path = "/api/system/config",
            json = mapOf("fsProviderId" to 0L),
        )
        E2eAssert.status(updateConfigResponse, 200, "[flow] update system config should succeed")
        E2eAssert.jsonAt(updateConfigResponse.body(), "/id", 0, "[flow] updated config id should be singleton id")
        E2eAssert.jsonAt(updateConfigResponse.body(), "/fsProviderId", 0, "[flow] updated fs provider id should stay 0")
        E2eAssert.jsonAt(updateConfigResponse.body(), "/ossProviderId", null, "[flow] updated oss provider should remain null")

        val logoutResponse = state.api.delete("/api/tokens/current")
        E2eAssert.status(logoutResponse, 204, "[flow] logout should succeed")

        val getAfterLogoutResponse = state.api.get("/api/system/config")
        assertAuthenticationFailed(
            getAfterLogoutResponse,
            "[flow] get system config after logout should require login",
        )
    }

    @Test
    @Order(3)
    fun `duplicate init and wrong login return stable business errors`() {
        val state = prepareState()
        ensureSystemInitialized(state)

        val duplicateInitResponse = state.api.post(
            path = "/api/system/config",
            json = systemInitRequest(state),
        )
        E2eAssert.apiError(
            duplicateInitResponse.body(),
            family = "SYSTEM",
            code = "SYSTEM_ALREADY_INITIALIZED",
            step = "[error] duplicate init should return system already initialized",
        )

        val wrongLoginResponse = state.api.post(
            path = "/api/tokens",
            json = mapOf(
                "email" to state.credentials.email,
                "password" to "${state.credentials.password}-wrong",
            ),
        )
        E2eAssert.apiError(
            wrongLoginResponse.body(),
            family = "COMMON",
            code = "AUTHENTICATION_FAILED",
            step = "[error] wrong login should return authentication failed",
        )
    }

    private fun prepareState(): E2eAdminSession {
        return newAdminSession(baseUrl())
    }

    private fun baseUrl(): String {
        return "http://127.0.0.1:$port"
    }

    private fun assertAuthenticationFailed(response: HttpResponse<String>, step: String) {
        E2eAssert.apiError(
            response = response,
            family = "COMMON",
            code = "AUTHENTICATION_FAILED",
            expectedStatus = 401,
            step = step,
        )
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun registerDatasource(registry: DynamicPropertyRegistry) {
            E2eRuntime.registerDatasource(registry)
        }
    }
}
