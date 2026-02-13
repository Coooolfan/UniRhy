package com.unirhy.e2e

import com.coooolfan.unirhy.UnirhyApplication
import com.unirhy.e2e.support.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Duration
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(
    classes = [UnirhyApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("smoke")
class SmokeTest {

    @LocalServerPort
    private var port: Int = 0

    @AfterAll
    fun cleanup() {
        E2eRuntime.cleanup()
    }

    @Test
    fun `initialize login scan and stream media from real filesystem`() {
        val state = prepareState()
        assertUninitialized(state)
        initializeSystem(state)
        login(state)
        triggerScan(state)
        awaitScanDone(state)
        assertMediaRangeReadable(state)
    }

    private fun prepareState(): E2eScenarioState {
        val runtime = E2eRuntime.context
        val scanSample = ScanSamplePreparer.prepare(runtime.scanWorkspace)
        val api = E2eHttpClient("http://127.0.0.1:$port")
        return E2eScenarioState(runtime = runtime, scanSample = scanSample, api = api)
    }

    private fun assertUninitialized(state: E2eScenarioState) {
        val statusBeforeInit = state.api.get("/api/system/config/status")
        E2eAssert.status(statusBeforeInit, 200, "[status] status endpoint should be reachable")
        E2eAssert.jsonAt(
            statusBeforeInit.body(),
            "/initialized",
            false,
            "[status] new temporary db should not be initialized",
        )
    }

    private fun initializeSystem(state: E2eScenarioState) {
        val initResponse = state.api.post(
            path = "/api/system/config",
            json = mapOf(
                "adminAccountName" to state.runtime.admin.name,
                "adminPassword" to state.runtime.admin.password,
                "adminAccountEmail" to state.runtime.admin.email,
                "storageProviderPath" to state.runtime.scanWorkspace.toAbsolutePath().toString(),
            ),
        )
        E2eAssert.status(initResponse, 201, "[init] system initialization should succeed")

        val statusAfterInit = state.api.get("/api/system/config/status")
        E2eAssert.status(statusAfterInit, 200, "[init] status endpoint should remain reachable")
        E2eAssert.jsonAt(
            statusAfterInit.body(),
            "/initialized",
            true,
            "[init] system should be initialized after create",
        )
    }

    private fun login(state: E2eScenarioState) {
        val loginResponse = state.api.post(
            path = "/api/tokens",
            json = mapOf(
                "email" to state.runtime.admin.email,
                "password" to state.runtime.admin.password,
            ),
        )
        E2eAssert.status(loginResponse, 200, "[login] admin login should succeed")
    }

    private fun triggerScan(state: E2eScenarioState) {
        val scanResponse = state.api.post(
            path = "/api/task/scan",
            json = mapOf(
                "providerType" to "FILE_SYSTEM",
                "providerId" to 0,
            ),
        )
        E2eAssert.status(scanResponse, 202, "[scan] scan task should be accepted")
    }

    private fun awaitScanDone(state: E2eScenarioState) {
        E2eAwait.until(timeout = Duration.ofMinutes(2), description = "[wait] scan task completion") {
            val runningResponse = state.api.get("/api/task/running")
            E2eAssert.status(runningResponse, 200, "[wait] running tasks endpoint should be reachable")
            val tasks = E2eJson.mapper.readTree(runningResponse.body())
            tasks.none { it.path("type").asText() == "SCAN" }
        }
    }

    private fun assertMediaRangeReadable(state: E2eScenarioState) {
        val worksResponse = state.api.get(
            path = "/api/works",
            query = mapOf(
                "pageIndex" to 0,
                "pageSize" to 100,
            ),
        )
        E2eAssert.status(worksResponse, 200, "[work] work list should be reachable")
        val workJson = E2eJson.mapper.readTree(worksResponse.body())
        state.putJson(E2eScenarioState.KEY_WORK_LIST, workJson)
        val mediaId = workJson.findMediaIdByObjectKey(state.scanSample.relativeObjectKey)
        assertNotNull(
            mediaId,
            "[work] scan result should include media objectKey=${state.scanSample.relativeObjectKey}",
        )
        state.putId(E2eScenarioState.KEY_MEDIA_ID, mediaId)

        val rangeEnd = minOf(state.scanSample.size - 1, 63)
        val mediaRangeResponse = state.api.getBytes(
            "/api/media/$mediaId",
            headers = mapOf("Range" to "bytes=0-$rangeEnd"),
        )
        E2eAssert.status(mediaRangeResponse, 206, "[media] media endpoint should support range response")

        val expectedPrefix = state.scanSample.readPrefixBytes((rangeEnd + 1).toInt())
        assertTrue(
            mediaRangeResponse.body().contentEquals(expectedPrefix),
            "[media] media payload should come from copied real audio sample",
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
