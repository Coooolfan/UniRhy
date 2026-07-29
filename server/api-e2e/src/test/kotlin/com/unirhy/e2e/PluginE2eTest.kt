package com.unirhy.e2e

import com.coooolfan.unirhy.UnirhyApplication
import com.unirhy.e2e.support.E2eAssert
import com.unirhy.e2e.support.E2eJson
import com.unirhy.e2e.support.E2eRuntime
import com.unirhy.e2e.support.PluginHostWasmFixture
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
import java.nio.file.Files
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
        val uploadedTask = uploaded.path("tasks").single()
        assertEquals(TASK_TYPE, uploadedTask.path("taskType").asString(), "[plugins] task type should match manifest")
        assertEquals(1, uploadedTask.path("concurrency").intValue(), "[plugins] concurrency should match manifest")
        assertTrue(
            uploadedTask.path("userSubmittable").asBoolean(),
            "[plugins] entry task should be user-submittable",
        )
        assertFalse(uploaded.path("enabled").asBoolean(), "[plugins] uploaded plugin should start disabled")
        assertFalse(uploaded.path("isAvailable").asBoolean(), "[plugins] disabled plugin should not be loaded")
        assertEquals(
            0,
            uploaded.path("configDefinition").path("schema").path("properties").size(),
            "[plugins] plugin without config should expose an empty definition",
        )

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
            path = "/api/plugins/$pluginId/tasks/$TASK_TYPE/concurrency",
            query = mapOf("concurrency" to 0),
        )
        E2eAssert.status(invalidConcurrencyResponse, 400, "[plugins] non-positive concurrency should fail")

        val concurrencyResponse = state.api.put(
            path = "/api/plugins/$pluginId/tasks/$TASK_TYPE/concurrency",
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
        assertEquals(
            5,
            enabled.path("tasks").single().path("concurrency").intValue(),
            "[plugins] concurrency update should persist",
        )

        E2eAssert.status(
            state.api.delete("/api/plugins/$pluginId"),
            409,
            "[plugins] deleting enabled plugin should fail",
        )

        val submitResponse = state.api.post(
            path = "/api/tasks",
            json = mapOf(
                "namespace" to pluginId,
                "taskType" to TASK_TYPE,
                "payload" to mapOf("tags" to listOf("classical", "instrumental")),
            ),
        )
        E2eAssert.status(submitResponse, 202, "[plugins] submit should accept homogeneous array params")
        val taskId = E2eJson.mapper.readTree(submitResponse.body()).path("id").longValue()
        assertTrue(taskId > 0, "[plugins] submit should return task id")

        awaitRootTaskTerminal(state, taskId)

        val invalidParamsResponse = state.api.post(
            path = "/api/tasks",
            json = mapOf(
                "namespace" to pluginId,
                "taskType" to TASK_TYPE,
                "payload" to mapOf("unknownField" to true),
            ),
        )
        E2eAssert.status(invalidParamsResponse, 400, "[plugins] params outside schema should fail")

        val mixedArrayResponse = state.api.post(
            path = "/api/tasks",
            json = mapOf(
                "namespace" to pluginId,
                "taskType" to TASK_TYPE,
                "payload" to mapOf("tags" to listOf("classical", 2)),
            ),
        )
        E2eAssert.status(mixedArrayResponse, 400, "[plugins] mixed array params should fail")

        val disableResponse = state.api.put(
            path = "/api/plugins/$pluginId/enabled-state",
            query = mapOf("enabled" to false),
        )
        E2eAssert.status(disableResponse, 204, "[plugins] disable should succeed")

        val submitAfterDisableResponse = state.api.post(
            path = "/api/tasks",
            json = mapOf(
                "namespace" to pluginId,
                "taskType" to TASK_TYPE,
                "payload" to emptyMap<String, Any>(),
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
    fun `wasm plugin should link the complete host catalog and execute host calls`() {
        val state = bootstrapAdminSession(baseUrl())
        val fixtureSuffix = suffix()
        val pluginId = "com.unirhy-e2e.host-$fixtureSuffix"
        val artistName = "host-artist-$fixtureSuffix"
        val workTitle = "host-work-$fixtureSuffix"
        val recordingWorkId = insertWork("host-recording-work-$fixtureSuffix")
        val recordingTitle = "host-recording-$fixtureSuffix"
        val ossMediaFixture = insertOssMediaFixture(fixtureSuffix)
        val assetFixture = insertAssetFixture(recordingWorkId, fixtureSuffix)
        val objectKey = "plugin-host-e2e/$fixtureSuffix.bin"
        val objectBytes = "host-storage-$fixtureSuffix".toByteArray()
        val pluginDataKey = "e2e.state.$fixtureSuffix"
        val pluginDataValue = "persisted-$fixtureSuffix"
        val apiKey = "secret-$fixtureSuffix"
        val wasm = PluginHostWasmFixture.build(
            artistName = artistName,
            workTitle = workTitle,
            recordingWorkId = recordingWorkId,
            recordingTitle = recordingTitle,
            ossNodeId = ossMediaFixture.nodeId,
            ossObjectKey = ossMediaFixture.objectKey,
            assetRecordingId = assetFixture.recordingId,
            otherRecordingId = assetFixture.otherRecordingId,
            assetMediaFileId = assetFixture.mediaFileId,
            objectKey = objectKey,
            objectBytes = objectBytes,
            pluginDataKey = pluginDataKey,
            pluginDataValue = pluginDataValue,
        )

        val uploadResponse = state.api.postMultipartFile(
            path = "/api/plugins",
            fieldName = "file",
            fileName = "$pluginId.up",
            fileBytes = pluginArchive(pluginId, wasm, configured = true),
        )
        E2eAssert.status(uploadResponse, 201, "[plugin-host] complete Host import catalog should link")

        val emptyConfigResponse = state.api.get("/api/plugins/$pluginId/configuration")
        E2eAssert.status(emptyConfigResponse, 200, "[plugin-config] empty configuration should be readable")
        val emptyConfig = E2eJson.mapper.readTree(emptyConfigResponse.body())
        assertEquals(0, emptyConfig.path("values").size(), "[plugin-config] initial values should be empty")
        assertEquals(
            0,
            emptyConfig.path("configuredSecretFields").size(),
            "[plugin-config] initial secret state should be empty",
        )

        E2eAssert.apiError(
            response = state.api.put(
                path = "/api/plugins/$pluginId/enabled-state",
                query = mapOf("enabled" to true),
            ),
            family = "PLUGIN",
            code = "CONFIGURATION_REQUIRED",
            expectedStatus = 409,
            step = "[plugin-config] required configuration should block enable",
        )

        E2eAssert.apiError(
            response = state.api.put(
                path = "/api/plugins/$pluginId/configuration",
                json = mapOf(
                    "values" to mapOf("batchSize" to 7),
                    "clearedSecretFields" to emptyList<String>(),
                ),
            ),
            family = "PLUGIN",
            code = "INVALID_CONFIGURATION",
            expectedStatus = 400,
            step = "[plugin-config] missing required secret should be rejected",
        )

        val updateConfigResponse = state.api.put(
            path = "/api/plugins/$pluginId/configuration",
            json = mapOf(
                "values" to mapOf("apiKey" to apiKey, "batchSize" to 7),
                "clearedSecretFields" to emptyList<String>(),
            ),
        )
        E2eAssert.status(updateConfigResponse, 200, "[plugin-config] valid configuration should be saved")
        val updatedConfig = E2eJson.mapper.readTree(updateConfigResponse.body())
        assertFalse(updatedConfig.path("values").has("apiKey"), "[plugin-config] response should redact secret")
        assertEquals(7, updatedConfig.path("values").path("batchSize").intValue())
        assertEquals("apiKey", updatedConfig.path("configuredSecretFields")[0].asString())

        val encryptedRow = jdbc.query(
            """
                SELECT value::text, encrypted_value
                FROM plugin_data
                WHERE plugin_id = :pluginId AND key = 'apiKey'
            """.trimIndent(),
            MapSqlParameterSource("pluginId", pluginId),
        ) { rs, _ -> rs.getString(1) to rs.getBytes(2) }.single()
        assertNull(encryptedRow.first, "[plugin-config] writeOnly value must not be stored as JSONB")
        assertNotNull(encryptedRow.second, "[plugin-config] writeOnly value should have ciphertext")
        assertFalse(
            encryptedRow.second.toString(Charsets.UTF_8).contains(apiKey),
            "[plugin-config] ciphertext should not contain the secret plaintext",
        )

        E2eAssert.status(
            state.api.put(
                path = "/api/plugins/$pluginId/enabled-state",
                query = mapOf("enabled" to true),
            ),
            204,
            "[plugin-host] enable should succeed",
        )

        val submitResponse = state.api.post(
            path = "/api/tasks",
            json = mapOf(
                "namespace" to pluginId,
                "taskType" to TASK_TYPE,
                "payload" to emptyMap<String, Any>(),
            ),
        )
        E2eAssert.status(submitResponse, 202, "[plugin-host] root task should be accepted")
        val taskId = E2eJson.mapper.readTree(submitResponse.body()).path("id").longValue()

        awaitRootTaskTerminal(state, taskId)

        val artistResponse = state.api.get(
            path = "/api/artists/search-results",
            query = mapOf("name" to artistName),
        )
        E2eAssert.status(artistResponse, 200, "[plugin-host] artist search should succeed")
        assertTrue(
            E2eJson.mapper.readTree(artistResponse.body()).any { it.path("displayName").asString() == artistName },
            "[plugin-host] JSON Host call should create the artist",
        )

        val workResponse = state.api.get(
            path = "/api/works/search-results",
            query = mapOf("name" to workTitle),
        )
        E2eAssert.status(workResponse, 200, "[plugin-host] work search should succeed")
        assertTrue(
            E2eJson.mapper.readTree(workResponse.body()).any { it.path("title").asString() == workTitle },
            "[plugin-host] host_work_create should create the work",
        )

        val recordingCount = jdbc.queryForObject(
            """
                SELECT COUNT(*)
                FROM recording
                WHERE work_id = :workId
                  AND title = :title
                  AND duration_ms = 1234
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("workId", recordingWorkId)
                .addValue("title", recordingTitle),
            Long::class.java,
        )
        assertEquals(1L, recordingCount, "[plugin-host] host_recording_create should create the recording")

        val mediaFileCount = jdbc.queryForObject(
            """
                SELECT COUNT(*)
                FROM media_file
                WHERE fs_provider_id = 0
                  AND object_key = :objectKey
            """.trimIndent(),
            MapSqlParameterSource("objectKey", objectKey),
            Long::class.java,
        )
        assertEquals(1L, mediaFileCount, "[plugin-host] location lookup should find the registered media file")

        val storedObject = state.runtime.scanWorkspace.resolve(objectKey)
        assertTrue(Files.isRegularFile(storedObject), "[plugin-host] binary Host call should create the storage object")
        assertTrue(
            Files.readAllBytes(storedObject).contentEquals(objectBytes),
            "[plugin-host] stored bytes should match guest memory",
        )

        val persistedData = jdbc.queryForObject(
            """
                SELECT value #>> '{}'
                FROM plugin_data
                WHERE plugin_id = :pluginId AND key = :key
            """.trimIndent(),
            MapSqlParameterSource().addValue("pluginId", pluginId).addValue("key", pluginDataKey),
            String::class.java,
        )
        assertEquals(pluginDataValue, persistedData, "[plugin-host] guest data put should persist JSON value")

        E2eAssert.status(
            state.api.put(
                path = "/api/plugins/$pluginId/enabled-state",
                query = mapOf("enabled" to false),
            ),
            204,
            "[plugin-host] disable should succeed",
        )
        E2eAssert.status(
            state.api.postMultipartFile(
                path = "/api/plugins",
                fieldName = "file",
                fileName = "$pluginId.up",
                fileBytes = pluginArchive(pluginId, wasm, configured = true),
            ),
            201,
            "[plugin-config] same-id upgrade should succeed",
        )
        val configAfterUpgradeResponse = state.api.get("/api/plugins/$pluginId/configuration")
        E2eAssert.status(configAfterUpgradeResponse, 200, "[plugin-config] upgraded configuration should be readable")
        val configAfterUpgrade = E2eJson.mapper.readTree(configAfterUpgradeResponse.body())
        assertEquals(7, configAfterUpgrade.path("values").path("batchSize").intValue())
        assertEquals("apiKey", configAfterUpgrade.path("configuredSecretFields")[0].asString())
        assertEquals(
            pluginDataValue,
            jdbc.queryForObject(
                "SELECT value #>> '{}' FROM plugin_data WHERE plugin_id = :pluginId AND key = :key",
                MapSqlParameterSource().addValue("pluginId", pluginId).addValue("key", pluginDataKey),
                String::class.java,
            ),
            "[plugin-data] same-id upgrade should preserve arbitrary data",
        )

        E2eAssert.status(state.api.delete("/api/plugins/$pluginId"), 204, "[plugin-host] delete should succeed")
        assertEquals(
            0L,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM plugin_data WHERE plugin_id = :pluginId",
                MapSqlParameterSource("pluginId", pluginId),
                Long::class.java,
            ),
            "[plugin-data] plugin deletion should cascade to all data",
        )
    }

    @Test
    @Order(4)
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
    @Order(5)
    fun `task creation should reject unknown task key`() {
        val state = bootstrapAdminSession(baseUrl())
        E2eAssert.status(
            state.api.post(
                path = "/api/tasks",
                json = mapOf(
                    "namespace" to "com.unirhy-e2e.not-installed",
                    "taskType" to "NOT_A_TASK",
                    "payload" to emptyMap<String, Any>(),
                ),
            ),
            404,
            "[tasks] unknown task key should return 404",
        )
        E2eAssert.status(
            state.api.post(
                path = "/api/tasks",
                json = mapOf(
                    "namespace" to "INVALID NAMESPACE",
                    "taskType" to "lower",
                    "payload" to emptyMap<String, Any>(),
                ),
            ),
            400,
            "[tasks] invalid task key format should return 400",
        )
    }

    private fun awaitRootTaskTerminal(state: com.unirhy.e2e.support.E2eAdminSession, taskId: Long) {
        val deadline = System.currentTimeMillis() + SUBMISSION_WAIT_TIMEOUT_MILLIS
        var lastStatus = "<none>"
        while (System.currentTimeMillis() <= deadline) {
            val response = state.api.get("/api/tasks/$taskId")
            E2eAssert.status(response, 200, "[plugins] root task detail should succeed")
            lastStatus = E2eJson.mapper.readTree(response.body()).path("task").path("status").asString()
            if (lastStatus in setOf("COMPLETED", "FAILED", "CANCELLED")) {
                assertEquals("COMPLETED", lastStatus, "[plugins] plan() returning empty list should complete root task")
                return
            }
            Thread.sleep(200L)
        }
        fail("[plugins] root task $taskId did not reach terminal state, last=$lastStatus")
    }

    private fun pluginArchive(
        pluginId: String,
        wasm: ByteArray = minimalPlanningWasm(),
        configured: Boolean = false,
    ): ByteArray {
        val config = if (configured) {
            """
            config:
              schema:
                type: object
                properties:
                  apiKey:
                    type: string
                    title: API Key
                    writeOnly: true
                    minLength: 1
                  batchSize:
                    type: integer
                    title: Batch size
                    minimum: 1
                required:
                  - apiKey
                additionalProperties: false
              order:
                - apiKey
                - batchSize
            """.trimIndent()
        } else {
            ""
        }
        val manifest = """
            id: $pluginId
            name: E2E plugin
            version: 0.0.1
            runtime:
              type: wasm
              abi: unirhy-wasm-abi-v1
            tasks:
              - type: $TASK_TYPE
                concurrency: 1
                userSubmittable: true
                form:
                  schema:
                    type: object
                    properties:
                      dryRun:
                        type: boolean
                        title: Dry run
                      tags:
                        type: array
                        title: Tags
                        items:
                          type: string
                    required: []
                    additionalProperties: false
                  order:
                    - dryRun
                    - tags
        """.trimIndent() + if (config.isEmpty()) "" else "\n$config"
        return zip(
            "plugin.yml" to manifest.toByteArray(),
            "plugin.wasm" to wasm,
        )
    }

    private fun insertWork(title: String): Long = jdbc.queryForObject(
        "INSERT INTO work(title) VALUES (:title) RETURNING id",
        MapSqlParameterSource("title", title),
        Long::class.java,
    ) ?: fail("[plugin-host] failed to insert recording work fixture")

    private fun insertAssetFixture(workId: Long, suffix: String): HostAssetFixture {
        val recordingId = insertRecording(workId, "host-asset-recording-$suffix")
        val otherRecordingId = insertRecording(workId, "host-asset-other-recording-$suffix")
        val mediaFileId = jdbc.queryForObject(
            """
                INSERT INTO media_file(object_key, mime_type, size, fs_provider_id)
                VALUES (:objectKey, 'audio/mp4', 1, 0)
                RETURNING id
            """.trimIndent(),
            MapSqlParameterSource("objectKey", "plugin-host-asset/$suffix.m4a"),
            Long::class.java,
        ) ?: fail("[plugin-host] failed to insert asset media fixture")
        jdbc.update(
            "INSERT INTO asset(recording_id, media_file_id) VALUES (:recordingId, :mediaFileId)",
            MapSqlParameterSource()
                .addValue("recordingId", recordingId)
                .addValue("mediaFileId", mediaFileId),
        )
        return HostAssetFixture(recordingId, otherRecordingId, mediaFileId)
    }

    private fun insertRecording(workId: Long, title: String): Long = jdbc.queryForObject(
        """
            INSERT INTO recording(work_id, title, duration_ms)
            VALUES (:workId, :title, 1)
            RETURNING id
        """.trimIndent(),
        MapSqlParameterSource()
            .addValue("workId", workId)
            .addValue("title", title),
        Long::class.java,
    ) ?: fail("[plugin-host] failed to insert recording fixture")

    private fun insertOssMediaFixture(suffix: String): OssMediaFixture {
        val nodeId = jdbc.queryForObject(
            """
                INSERT INTO file_provider_oss(name, host, bucket, access_key, secret_key, readonly)
                VALUES (:name, 'https://oss.example.invalid', 'music', 'test-access', 'test-secret', false)
                RETURNING id
            """.trimIndent(),
            MapSqlParameterSource("name", "host-oss-$suffix"),
            Long::class.java,
        ) ?: fail("[plugin-host] failed to insert OSS node fixture")
        val objectKey = "plugin-host-oss/$suffix.m4a"
        jdbc.update(
            """
                INSERT INTO media_file(object_key, mime_type, size, oss_provider_id)
                VALUES (:objectKey, 'audio/mp4', 1, :nodeId)
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("objectKey", objectKey)
                .addValue("nodeId", nodeId),
        )
        return OssMediaFixture(nodeId, objectKey)
    }

    private data class HostAssetFixture(
        val recordingId: Long,
        val otherRecordingId: Long,
        val mediaFileId: Long,
    )

    private data class OssMediaFixture(
        val nodeId: Long,
        val objectKey: String,
    )

    private fun invalidPluginArchive(): ByteArray {
        return zip(
            "plugin.yml" to """
                id: com.unirhy-e2e.invalid
                version: 0.0.1
                runtime:
                  type: wasm
                  abi: unirhy-wasm-abi-v1
                tasks:
                  - type: $TASK_TYPE
                    concurrency: 1
                    userSubmittable: true
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
            0x03, 0x05, 0x04, 0x00, 0x01, 0x02, 0x02,
            0x05, 0x03, 0x01, 0x00, 0x01,
            0x07, 0x29, 0x05,
            0x05, 0x61, 0x6C, 0x6C, 0x6F, 0x63, 0x00, 0x00,
            0x07, 0x64, 0x65, 0x61, 0x6C, 0x6C, 0x6F, 0x63, 0x00, 0x01,
            0x04, 0x70, 0x6C, 0x61, 0x6E, 0x00, 0x02,
            0x03, 0x72, 0x75, 0x6E, 0x00, 0x03,
            0x06, 0x6D, 0x65, 0x6D, 0x6F, 0x72, 0x79, 0x02, 0x00,
            0x0A, 0x1F, 0x04,
            0x04, 0x00, 0x20, 0x00, 0x0B,
            0x02, 0x00, 0x0B,
            0x0A, 0x00, 0x42, 0x82, 0x80, 0x80, 0x80, 0x80, 0x80, 0x02, 0x0B,
            0x0A, 0x00, 0x42, 0x82, 0x80, 0x80, 0x80, 0x80, 0x80, 0x02, 0x0B,
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
