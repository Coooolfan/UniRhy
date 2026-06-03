package com.unirhy.e2e

import com.coooolfan.unirhy.UnirhyApplication
import com.unirhy.e2e.support.E2eAssert
import com.unirhy.e2e.support.E2eHttpClient
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
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

    @Autowired
    private lateinit var jdbc: NamedParameterJdbcTemplate

    @AfterAll
    fun cleanup() {
        E2eRuntime.cleanup()
    }

    @Test
    @Order(2)
    fun `plugin lifecycle should support upload list download enable submit disable and delete`() {
        val state = bootstrapAdminSession(baseUrl())
        val pluginId = "com.unirhy.e2e.${suffix()}"
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
        assertEquals(pluginId, uploaded.path("id").asText(), "[plugins] list should contain uploaded plugin")
        assertEquals("0.0.1", uploaded.path("version").asText(), "[plugins] version should match manifest")
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

        val submitResponse = state.api.post(
            path = "/api/plugin-task-submissions/ARTIST_NORMALIZATION",
            json = emptyMap<String, String>(),
        )
        E2eAssert.status(submitResponse, 202, "[plugins] submit should accept loaded plugin task")

        val disableResponse = state.api.put(
            path = "/api/plugins/$pluginId/enabled-state",
            query = mapOf("enabled" to false),
        )
        E2eAssert.status(disableResponse, 204, "[plugins] disable should succeed")

        val submitAfterDisableResponse = state.api.post(
            path = "/api/plugin-task-submissions/ARTIST_NORMALIZATION",
            json = emptyMap<String, String>(),
        )
        E2eAssert.status(submitAfterDisableResponse, 400, "[plugins] submit without loaded plugin should fail")

        val deleteResponse = state.api.delete("/api/plugins/$pluginId")
        E2eAssert.status(deleteResponse, 204, "[plugins] delete should succeed")

        val listAfterDeleteResponse = state.api.get("/api/plugins")
        E2eAssert.status(listAfterDeleteResponse, 200, "[plugins] list after delete should succeed")
        assertFalse(
            E2eJson.mapper.readTree(listAfterDeleteResponse.body()).any { it.path("id").asText() == pluginId },
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
    }

    @Test
    @Order(4)
    fun `plugin list should reject invalid stored form metadata`() {
        val state = bootstrapAdminSession(baseUrl())
        val pluginId = "com.unirhy.e2e.invalid-form.${suffix()}"
        jdbc.update(
            """
                INSERT INTO public.plugin (
                    id, name, version, abi, task_type, extension, network_allow, form_fields, wasm, enabled
                ) VALUES (
                    :id, :name, :version, :abi, :taskType, :extension, :networkAllow, :formFields, :wasm, false
                )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("id", pluginId)
                .addValue("name", "Invalid form metadata")
                .addValue("version", "0.0.1")
                .addValue("abi", "unirhy-wasm-abi-v1")
                .addValue("taskType", "ARTIST_NORMALIZATION")
                .addValue("extension", "metadata.artists_normalization@1")
                .addValue("networkAllow", emptyArray<String>())
                .addValue("formFields", "{not-json")
                .addValue("wasm", minimalPlanningWasm()),
        )

        try {
            E2eAssert.status(
                state.api.get("/api/plugins"),
                500,
                "[plugins] list should fail on invalid stored form metadata",
            )
        } finally {
            jdbc.update(
                "DELETE FROM public.plugin WHERE id = :id",
                mapOf("id" to pluginId),
            )
        }
    }

    @Test
    @Order(5)
    fun `plugin submit should reject invalid task type`() {
        val state = bootstrapAdminSession(baseUrl())
        E2eAssert.status(
            state.api.post(path = "/api/plugin-task-submissions/NOT_A_TASK", json = emptyMap<String, String>()),
            400,
            "[plugins] submit should reject unknown task type",
        )
    }

    private fun pluginArchive(pluginId: String): ByteArray {
        val manifest = """
            id: $pluginId
            name: E2E plugin
            version: 0.0.1
            runtime:
              type: wasm
              abi: unirhy-wasm-abi-v1
            tasks:
              - type: ARTIST_NORMALIZATION
                extension: metadata.artists_normalization@1
            form:
              fields:
                - name: dryRun
                  type: boolean
                  label: Dry run
        """.trimIndent()
        return zip(
            "plugin.yml" to manifest.toByteArray(),
            "plugin.wasm" to minimalPlanningWasm(),
        )
    }

    private fun invalidPluginArchive(): ByteArray {
        return zip(
            "plugin.yml" to """
                id: invalid
                version: 0.0.1
                runtime:
                  type: wasm
                  abi: unirhy-wasm-abi-v1
                tasks:
                  - type: ARTIST_NORMALIZATION
                    extension: metadata.artists_normalization@1
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
        E2eJson.mapper.readTree(responseBody).first { it.path("id").asText() == pluginId }

    private fun suffix(): String = UUID.randomUUID().toString().replace("-", "").take(10)

    private fun baseUrl(): String = "http://127.0.0.1:$port"

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun registerDatasource(registry: DynamicPropertyRegistry) {
            E2eRuntime.registerDatasource(registry)
        }
    }
}
