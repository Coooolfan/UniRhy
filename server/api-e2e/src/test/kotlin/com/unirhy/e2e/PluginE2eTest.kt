package com.unirhy.e2e

import com.coooolfan.unirhy.UnirhyApplication
import com.unirhy.e2e.support.E2eAssert
import com.unirhy.e2e.support.E2eJson
import com.unirhy.e2e.support.E2eRuntime
import com.unirhy.e2e.support.bootstrapAdminSession
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
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

@SpringBootTest(
    classes = [UnirhyApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@Tag("full")
class PluginE2eTest {

    @LocalServerPort
    private var port: Int = 0

    @AfterAll
    fun cleanup() {
        E2eRuntime.cleanup()
    }

    @Test
    @Order(2)
    fun `plugin lifecycle should support upload list download enable submit disable and delete`() {
        val state = bootstrapAdminSession(baseUrl())
        val pluginId = "com.unirhy-e2e.${suffix()}"
        val pluginArchive = pluginArchive(pluginId)

        val uploadResponse = state.api.postMultipartFile(
            path = "/api/plugins",
            fieldName = "file",
            fileName = "$pluginId.up",
            fileBytes = pluginArchive,
        )
        E2eAssert.status(uploadResponse, 201, "[plugins] upload should succeed")

        val listAfterUploadResponse = state.api.get("/api/plugins")
        E2eAssert.status(listAfterUploadResponse, 200, "[plugins] list after upload should succeed")
        val uploaded = pluginNode(listAfterUploadResponse.body(), pluginId)
        assertEquals(pluginId, uploaded.path("id").asString(), "[plugins] list should contain uploaded plugin")
        assertEquals("0.0.1", uploaded.path("version").asString(), "[plugins] version should match manifest")
        assertEquals(TASK_TYPE, uploaded.path("taskType").asString(), "[plugins] task type should match manifest")
        assertEquals(1, uploaded.path("concurrency").intValue(), "[plugins] concurrency should match manifest")
        assertFalse(uploaded.path("enabled").asBoolean(), "[plugins] uploaded plugin should start disabled")
        assertFalse(uploaded.path("isAvailable").asBoolean(), "[plugins] disabled plugin should not be loaded")

        val downloadResponse = state.api.getBytes("/api/plugins/$pluginId/package")
        E2eAssert.status(downloadResponse, 200, "[plugins] download should succeed")
        assertTrue(
            downloadResponse.headers().firstValue("Content-Disposition").orElse("").contains("$pluginId-0.0.1.up"),
            "[plugins] download filename should include plugin id and version",
        )
        assertTrue(
            zipEntryNames(downloadResponse.body()).containsAll(listOf("plugin.yml", "plugin.wasm")),
            "[plugins] downloaded archive should contain manifest and wasm",
        )

        val invalidConcurrencyResponse = state.api.put(
            path = "/api/plugins/$pluginId/concurrency",
            query = mapOf("concurrency" to 0),
        )
        E2eAssert.status(invalidConcurrencyResponse, 400, "[plugins] non-positive concurrency should fail")

        val concurrencyResponse = state.api.put(
            path = "/api/plugins/$pluginId/concurrency",
            query = mapOf("concurrency" to 5),
        )
        E2eAssert.status(concurrencyResponse, 204, "[plugins] concurrency update should succeed")

        val enableResponse = state.api.put(
            path = "/api/plugins/$pluginId/enabled-state",
            query = mapOf("enabled" to true),
        )
        E2eAssert.status(enableResponse, 204, "[plugins] enable should succeed")

        val listAfterEnableResponse = state.api.get("/api/plugins")
        E2eAssert.status(listAfterEnableResponse, 200, "[plugins] list after enable should succeed")
        val enabled = pluginNode(listAfterEnableResponse.body(), pluginId)
        assertTrue(enabled.path("enabled").asBoolean(), "[plugins] enabled flag should be true")
        assertTrue(enabled.path("isAvailable").asBoolean(), "[plugins] enabled wasm should be loaded")
        assertEquals(5, enabled.path("concurrency").intValue(), "[plugins] concurrency update should persist")

        E2eAssert.status(
            state.api.delete("/api/plugins/$pluginId"),
            409,
            "[plugins] deleting enabled plugin should fail",
        )

        val submitResponse = state.api.post(
            path = "/api/task-submissions",
            json = mapOf(
                "namespace" to pluginId,
                "taskType" to TASK_TYPE,
                "params" to emptyMap<String, Any>(),
            ),
        )
        E2eAssert.status(submitResponse, 202, "[plugins] submit should accept loaded plugin task")
        val submissionId = E2eJson.mapper.readTree(submitResponse.body()).path("submissionId").longValue()
        assertTrue(submissionId > 0, "[plugins] submit should return submissionId")

        awaitSubmissionTerminal(state, submissionId)

        val invalidParamsResponse = state.api.post(
            path = "/api/task-submissions",
            json = mapOf(
                "namespace" to pluginId,
                "taskType" to TASK_TYPE,
                "params" to mapOf("unknownField" to true),
            ),
        )
        E2eAssert.status(invalidParamsResponse, 400, "[plugins] params outside schema should fail")

        val disableResponse = state.api.put(
            path = "/api/plugins/$pluginId/enabled-state",
            query = mapOf("enabled" to false),
        )
        E2eAssert.status(disableResponse, 204, "[plugins] disable should succeed")

        val submitAfterDisableResponse = state.api.post(
            path = "/api/task-submissions",
            json = mapOf(
                "namespace" to pluginId,
                "taskType" to TASK_TYPE,
                "params" to emptyMap<String, Any>(),
            ),
        )
        E2eAssert.status(submitAfterDisableResponse, 409, "[plugins] submit for disabled plugin should conflict")

        val deleteResponse = state.api.delete("/api/plugins/$pluginId")
        E2eAssert.status(deleteResponse, 204, "[plugins] delete should succeed")

        val listAfterDeleteResponse = state.api.get("/api/plugins")
        E2eAssert.status(listAfterDeleteResponse, 200, "[plugins] list after delete should succeed")
        assertFalse(
            E2eJson.mapper.readTree(listAfterDeleteResponse.body()).any { it.path("id").asString() == pluginId },
            "[plugins] deleted plugin should not remain in list",
        )

        E2eAssert.status(
            state.api.get("/api/plugins/$pluginId/package"),
            404,
            "[plugins] downloading deleted plugin should fail",
        )

        E2eAssert.status(
            state.api.put(
                path = "/api/plugins/$pluginId/enabled-state",
                query = mapOf("enabled" to true),
            ),
            404,
            "[plugins] enabling deleted plugin should fail",
        )
        E2eAssert.status(
            state.api.delete("/api/plugins/$pluginId"),
            404,
            "[plugins] deleting deleted plugin should fail",
        )
    }

    @Test
    @Order(3)
    fun `plugin upload should reject invalid archives`() {
        val state = bootstrapAdminSession(baseUrl())
        val response = state.api.postMultipartFile(
            path = "/api/plugins",
            fieldName = "file",
            fileName = "invalid.up",
            fileBytes = invalidPluginArchive(),
        )
        E2eAssert.status(response, 400, "[plugins] upload without wasm should fail")

        val reservedResponse = state.api.postMultipartFile(
            path = "/api/plugins",
            fieldName = "file",
            fileName = "reserved.up",
            fileBytes = pluginArchive("app.unirhy.evil"),
        )
        E2eAssert.status(reservedResponse, 400, "[plugins] reserved namespace should be rejected")
    }

    @Test
    @Order(4)
    fun `task submission should reject unknown task key`() {
        val state = bootstrapAdminSession(baseUrl())
        E2eAssert.status(
            state.api.post(
                path = "/api/task-submissions",
                json = mapOf(
                    "namespace" to "com.unirhy-e2e.not-installed",
                    "taskType" to "NOT_A_TASK",
                    "params" to emptyMap<String, Any>(),
                ),
            ),
            404,
            "[submissions] unknown task key should return 404",
        )
        E2eAssert.status(
            state.api.post(
                path = "/api/task-submissions",
                json = mapOf(
                    "namespace" to "INVALID NAMESPACE",
                    "taskType" to "lower",
                    "params" to emptyMap<String, Any>(),
                ),
            ),
            400,
            "[submissions] invalid task key format should return 400",
        )
    }

    private fun awaitSubmissionTerminal(state: com.unirhy.e2e.support.E2eAdminSession, submissionId: Long) {
        val deadline = System.currentTimeMillis() + SUBMISSION_WAIT_TIMEOUT_MILLIS
        var lastStatus = "<none>"
        while (System.currentTimeMillis() <= deadline) {
            val response = state.api.get("/api/task-submissions/$submissionId")
            E2eAssert.status(response, 200, "[plugins] submission detail should succeed")
            lastStatus = E2eJson.mapper.readTree(response.body()).path("submission").path("status").asString()
            if (lastStatus in setOf("COMPLETED", "FAILED", "CANCELLED")) {
                assertEquals("COMPLETED", lastStatus, "[plugins] plan() returning empty list should complete submission")
                return
            }
            Thread.sleep(200L)
        }
        fail("[plugins] submission $submissionId did not reach terminal state, last=$lastStatus")
    }

    private fun pluginArchive(pluginId: String): ByteArray {
        val manifest = """
            id: $pluginId
            name: E2E plugin
            version: 0.0.1
            runtime:
              type: wasm
              abi: unirhy-wasm-abi-v1
            task:
              type: $TASK_TYPE
              concurrency: 1
            form:
              schema:
                type: object
                properties:
                  dryRun:
                    type: boolean
                    title: Dry run
                required: []
                additionalProperties: false
              order:
                - dryRun
        """.trimIndent()
        return zip(
            "plugin.yml" to manifest.toByteArray(),
            "plugin.wasm" to minimalPlanningWasm(),
        )
    }

    private fun invalidPluginArchive(): ByteArray {
        return zip(
            "plugin.yml" to """
                id: com.unirhy-e2e.invalid
                version: 0.0.1
                runtime:
                  type: wasm
                  abi: unirhy-wasm-abi-v1
                task:
                  type: $TASK_TYPE
                  concurrency: 1
            """.trimIndent().toByteArray(),
        )
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun zipEntryNames(bytes: ByteArray): Set<String> {
        val names = mutableSetOf<String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                names += entry.name
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return names
    }

    private fun minimalPlanningWasm(): ByteArray {
        val bytes = intArrayOf(
            0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00,
            0x01, 0x16, 0x04,
            0x60, 0x01, 0x7F, 0x01, 0x7F,
            0x60, 0x02, 0x7F, 0x7F, 0x00,
            0x60, 0x02, 0x7F, 0x7F, 0x01, 0x7E,
            0x60, 0x02, 0x7F, 0x7F, 0x00,
            0x03, 0x05, 0x04, 0x00, 0x01, 0x02, 0x03,
            0x05, 0x03, 0x01, 0x00, 0x01,
            0x07, 0x29, 0x05,
            0x05, 0x61, 0x6C, 0x6C, 0x6F, 0x63, 0x00, 0x00,
            0x07, 0x64, 0x65, 0x61, 0x6C, 0x6C, 0x6F, 0x63, 0x00, 0x01,
            0x04, 0x70, 0x6C, 0x61, 0x6E, 0x00, 0x02,
            0x03, 0x72, 0x75, 0x6E, 0x00, 0x03,
            0x06, 0x6D, 0x65, 0x6D, 0x6F, 0x72, 0x79, 0x02, 0x00,
            0x0A, 0x17, 0x04,
            0x04, 0x00, 0x20, 0x00, 0x0B,
            0x02, 0x00, 0x0B,
            0x0A, 0x00, 0x42, 0x82, 0x80, 0x80, 0x80, 0x80, 0x80, 0x02, 0x0B,
            0x02, 0x00, 0x0B,
            0x0B, 0x09, 0x01, 0x00, 0x41, 0x80, 0x10, 0x0B, 0x02, 0x5B, 0x5D,
        )
        return bytes.map { it.toByte() }.toByteArray()
    }

    private fun pluginNode(responseBody: String, pluginId: String) =
        E2eJson.mapper.readTree(responseBody).first { it.path("id").asString() == pluginId }

    private fun suffix(): String = UUID.randomUUID().toString().replace("-", "").take(10)

    private fun baseUrl(): String = "http://127.0.0.1:$port"

    companion object {
        private const val TASK_TYPE = "E2E_TASK"
        private const val SUBMISSION_WAIT_TIMEOUT_MILLIS = 30_000L

        @JvmStatic
        @DynamicPropertySource
        fun registerDatasource(registry: DynamicPropertyRegistry) {
            E2eRuntime.registerDatasource(registry)
        }
    }
}
