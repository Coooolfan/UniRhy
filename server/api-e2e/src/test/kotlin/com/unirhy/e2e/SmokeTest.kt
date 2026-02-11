package com.unirhy.e2e

import com.coooolfan.unirhy.UnirhyApplication
import com.unirhy.e2e.support.E2eAwait
import com.unirhy.e2e.support.E2eHttpClient
import com.unirhy.e2e.support.E2eJson
import com.unirhy.e2e.support.E2eRunContext
import com.unirhy.e2e.support.E2eRuntime
import com.unirhy.e2e.support.ScanSample
import com.unirhy.e2e.support.ScanSamplePreparer
import com.unirhy.e2e.support.findMediaIdByObjectKey
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
import kotlin.test.assertEquals
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
        val context = prepareContext()
        assertUninitialized(context)
        initializeSystem(context)
        login(context)
        triggerScan(context)
        awaitScanDone(context)
        assertMediaRangeReadable(context)
    }

    private fun prepareContext(): ScenarioContext {
        val runtime = E2eRuntime.context
        val scanSample = ScanSamplePreparer.prepare(runtime.scanWorkspace)
        val api = E2eHttpClient("http://127.0.0.1:$port")
        return ScenarioContext(runtime = runtime, scanSample = scanSample, api = api)
    }

    private fun assertUninitialized(context: ScenarioContext) {
        val statusBeforeInit = context.api.get("/api/system/config/status")
        assertEquals(200, statusBeforeInit.statusCode(), "[status] status endpoint should be reachable")
        assertEquals(
            false,
            E2eJson.mapper.readTree(statusBeforeInit.body()).path("initialized").asBoolean(),
            "[status] new temporary db should not be initialized",
        )
    }

    private fun initializeSystem(context: ScenarioContext) {
        val initResponse = context.api.postJson(
            "/api/system/config",
            mapOf(
                "adminAccountName" to context.runtime.admin.name,
                "adminPassword" to context.runtime.admin.password,
                "adminAccountEmail" to context.runtime.admin.email,
                "storageProviderPath" to context.runtime.scanWorkspace.toAbsolutePath().toString(),
            ),
        )
        assertEquals(201, initResponse.statusCode(), "[init] system initialization should succeed")

        val statusAfterInit = context.api.get("/api/system/config/status")
        assertEquals(200, statusAfterInit.statusCode(), "[init] status endpoint should remain reachable")
        assertEquals(
            true,
            E2eJson.mapper.readTree(statusAfterInit.body()).path("initialized").asBoolean(),
            "[init] system should be initialized after create",
        )
    }

    private fun login(context: ScenarioContext) {
        val loginResponse = context.api.get(
            "/api/token?email=${E2eHttpClient.urlEncode(context.runtime.admin.email)}" +
                "&password=${E2eHttpClient.urlEncode(context.runtime.admin.password)}",
        )
        assertEquals(200, loginResponse.statusCode(), "[login] admin login should succeed")
    }

    private fun triggerScan(context: ScenarioContext) {
        val scanResponse = context.api.postJson(
            "/api/task/scan",
            mapOf(
                "providerType" to "FILE_SYSTEM",
                "providerId" to 0,
            ),
        )
        assertEquals(202, scanResponse.statusCode(), "[scan] scan task should be accepted")
    }

    private fun awaitScanDone(context: ScenarioContext) {
        E2eAwait.until(timeout = Duration.ofMinutes(2), description = "[wait] scan task completion") {
            val runningResponse = context.api.get("/api/task/running")
            assertEquals(200, runningResponse.statusCode(), "[wait] running tasks endpoint should be reachable")
            val tasks = E2eJson.mapper.readTree(runningResponse.body())
            tasks.none { it.path("type").asText() == "SCAN" }
        }
    }

    private fun assertMediaRangeReadable(context: ScenarioContext) {
        val worksResponse = context.api.get("/api/work?pageIndex=0&pageSize=100")
        assertEquals(200, worksResponse.statusCode(), "[work] work list should be reachable")
        val workJson = E2eJson.mapper.readTree(worksResponse.body())
        val mediaId = workJson.findMediaIdByObjectKey(context.scanSample.relativeObjectKey)
        assertNotNull(
            mediaId,
            "[work] scan result should include media objectKey=${context.scanSample.relativeObjectKey}",
        )

        val rangeEnd = minOf(context.scanSample.size - 1, 63)
        val mediaRangeResponse = context.api.getBytes(
            "/api/media/$mediaId",
            headers = mapOf("Range" to "bytes=0-$rangeEnd"),
        )
        assertEquals(206, mediaRangeResponse.statusCode(), "[media] media endpoint should support range response")

        val expectedPrefix = context.scanSample.readPrefixBytes((rangeEnd + 1).toInt())
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

    private data class ScenarioContext(
        val runtime: E2eRunContext,
        val scanSample: ScanSample,
        val api: E2eHttpClient,
    )
}
