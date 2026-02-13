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
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(
    classes = [UnirhyApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("full")
// Test cases are intentionally independent; execution order is not required.
class StorageConfigE2eTest {

    @LocalServerPort
    private var port: Int = 0

    @AfterAll
    fun cleanup() {
        E2eRuntime.cleanup()
    }

    @Test
    fun `all storage endpoints should reject unauthenticated access`() {
        val api = E2eHttpClient(baseUrl())
        val unknownId = 999_999_999L

        assertAuthenticationFailed(
            api.get("/api/storage/fs"),
            "[auth] get fs list should require login",
        )
        assertAuthenticationFailed(
            api.post(path = "/api/storage/fs", json = fsPayload("blocked-fs", "/tmp/blocked-fs", false)),
            "[auth] create fs provider should require login",
        )
        assertAuthenticationFailed(
            api.get("/api/storage/fs/$unknownId"),
            "[auth] get fs provider should require login",
        )
        assertAuthenticationFailed(
            api.put(path = "/api/storage/fs/$unknownId", json = fsPayload("blocked-fs", "/tmp/blocked-fs", true)),
            "[auth] update fs provider should require login",
        )
        assertAuthenticationFailed(
            api.delete("/api/storage/fs/$unknownId"),
            "[auth] delete fs provider should require login",
        )

        assertAuthenticationFailed(
            api.get("/api/storage/oss"),
            "[auth] get oss list should require login",
        )
        assertAuthenticationFailed(
            api.post(
                path = "/api/storage/oss",
                json = ossPayload(
                    name = "blocked-oss",
                    host = "https://oss.example.invalid",
                    bucket = "blocked-bucket",
                    accessKey = "blocked-ak",
                    secretKey = "blocked-sk",
                    parentPath = "/blocked",
                    readonly = false,
                ),
            ),
            "[auth] create oss provider should require login",
        )
        assertAuthenticationFailed(
            api.get("/api/storage/oss/$unknownId"),
            "[auth] get oss provider should require login",
        )
        assertAuthenticationFailed(
            api.put(
                path = "/api/storage/oss/$unknownId",
                json = ossPayload(
                    name = "blocked-oss",
                    host = "https://oss.example.invalid",
                    bucket = "blocked-bucket",
                    accessKey = "blocked-ak",
                    secretKey = "blocked-sk",
                    parentPath = "/blocked-updated",
                    readonly = true,
                ),
            ),
            "[auth] update oss provider should require login",
        )
        assertAuthenticationFailed(
            api.delete("/api/storage/oss/$unknownId"),
            "[auth] delete oss provider should require login",
        )
    }

    @Test
    fun `file system storage should support create get update list delete flow`() {
        val state = bootstrapAdminSession(baseUrl())
        var fsProviderId: Long? = null

        try {
            val createFsSuffix = suffix()
            val createPayload = fsPayload(
                name = "e2e-fs-$createFsSuffix",
                parentPath = state.runtime.workspace.resolve("storage-fs-$createFsSuffix").toAbsolutePath().toString(),
                readonly = false,
            )
            val createResponse = state.api.post(path = "/api/storage/fs", json = createPayload)
            E2eAssert.status(createResponse, 201, "[fs] create provider should succeed")
            E2eAssert.jsonAt(createResponse.body(), "/name", createPayload["name"], "[fs] created provider name should match")
            E2eAssert.jsonAt(
                createResponse.body(),
                "/parentPath",
                createPayload["parentPath"],
                "[fs] created provider parentPath should match",
            )
            E2eAssert.jsonAt(createResponse.body(), "/readonly", false, "[fs] created provider readonly should match")
            fsProviderId = readId(createResponse.body(), "[fs] created provider should return id")
            val createdFsProviderId = requireNotNull(fsProviderId)

            val getResponse = state.api.get("/api/storage/fs/$createdFsProviderId")
            E2eAssert.status(getResponse, 200, "[fs] get provider should succeed")
            E2eAssert.jsonAt(getResponse.body(), "/id", createdFsProviderId, "[fs] get provider id should match")
            E2eAssert.jsonAt(getResponse.body(), "/name", createPayload["name"], "[fs] get provider name should match")

            val updateFsSuffix = suffix()
            val updatePayload = fsPayload(
                name = "e2e-fs-updated-$updateFsSuffix",
                parentPath = state.runtime.workspace.resolve("storage-fs-updated-$updateFsSuffix").toAbsolutePath().toString(),
                readonly = true,
            )
            val updateResponse = state.api.put(path = "/api/storage/fs/$createdFsProviderId", json = updatePayload)
            E2eAssert.status(updateResponse, 200, "[fs] update provider should succeed")
            E2eAssert.jsonAt(updateResponse.body(), "/id", createdFsProviderId, "[fs] updated provider id should match")
            E2eAssert.jsonAt(updateResponse.body(), "/name", updatePayload["name"], "[fs] updated provider name should match")
            E2eAssert.jsonAt(
                updateResponse.body(),
                "/parentPath",
                updatePayload["parentPath"],
                "[fs] updated provider parentPath should match",
            )
            E2eAssert.jsonAt(updateResponse.body(), "/readonly", true, "[fs] updated provider readonly should match")

            val listResponse = state.api.get("/api/storage/fs")
            E2eAssert.status(listResponse, 200, "[fs] list providers should succeed")
            E2eAssert.jsonArrayContainsId(
                responseBody = listResponse.body(),
                createdFsProviderId,
                step = "[fs] list providers should contain created provider",
            )

            val deleteResponse = state.api.delete("/api/storage/fs/$createdFsProviderId")
            E2eAssert.status(deleteResponse, 204, "[fs] delete provider should succeed")
            fsProviderId = null

            val listAfterDeleteResponse = state.api.get("/api/storage/fs")
            E2eAssert.status(listAfterDeleteResponse, 200, "[fs] list providers after delete should succeed")
            E2eAssert.jsonArrayNotContainsId(
                responseBody = listAfterDeleteResponse.body(),
                createdFsProviderId,
                step = "[fs] deleted provider should not exist in list",
            )
        } finally {
            fsProviderId?.let { safeDelete(state.api, "/api/storage/fs/$it") }
        }
    }

    @Test
    fun `oss storage should support create get update list delete flow`() {
        val state = bootstrapAdminSession(baseUrl())
        var ossProviderId: Long? = null

        try {
            val createOssSuffix = suffix()
            val createPayload = ossPayload(
                name = "e2e-oss-$createOssSuffix",
                host = "https://oss-$createOssSuffix.example.invalid",
                bucket = "bucket-$createOssSuffix",
                accessKey = "access-$createOssSuffix",
                secretKey = "secret-$createOssSuffix",
                parentPath = "/root-$createOssSuffix",
                readonly = false,
            )
            val createResponse = state.api.post(path = "/api/storage/oss", json = createPayload)
            E2eAssert.status(createResponse, 201, "[oss] create provider should succeed")
            E2eAssert.jsonAt(createResponse.body(), "/name", createPayload["name"], "[oss] created provider name should match")
            E2eAssert.jsonAt(createResponse.body(), "/host", createPayload["host"], "[oss] created provider host should match")
            E2eAssert.jsonAt(createResponse.body(), "/bucket", createPayload["bucket"], "[oss] created provider bucket should match")
            E2eAssert.jsonAt(createResponse.body(), "/accessKey", createPayload["accessKey"], "[oss] created provider access key should match")
            E2eAssert.jsonAt(createResponse.body(), "/secretKey", createPayload["secretKey"], "[oss] created provider secret key should match")
            E2eAssert.jsonAt(
                createResponse.body(),
                "/parentPath",
                createPayload["parentPath"],
                "[oss] created provider parentPath should match",
            )
            E2eAssert.jsonAt(createResponse.body(), "/readonly", false, "[oss] created provider readonly should match")
            ossProviderId = readId(createResponse.body(), "[oss] created provider should return id")
            val createdOssProviderId = requireNotNull(ossProviderId)

            val getResponse = state.api.get("/api/storage/oss/$createdOssProviderId")
            E2eAssert.status(getResponse, 200, "[oss] get provider should succeed")
            E2eAssert.jsonAt(getResponse.body(), "/id", createdOssProviderId, "[oss] get provider id should match")
            E2eAssert.jsonAt(getResponse.body(), "/name", createPayload["name"], "[oss] get provider name should match")

            val updateOssSuffix = suffix()
            val updatePayload = ossPayload(
                name = "e2e-oss-updated-$updateOssSuffix",
                host = "https://oss-updated-$updateOssSuffix.example.invalid",
                bucket = "bucket-updated-$updateOssSuffix",
                accessKey = "access-updated-$updateOssSuffix",
                secretKey = "secret-updated-$updateOssSuffix",
                parentPath = "/root-updated-$updateOssSuffix",
                readonly = true,
            )
            val updateResponse = state.api.put(path = "/api/storage/oss/$createdOssProviderId", json = updatePayload)
            E2eAssert.status(updateResponse, 200, "[oss] update provider should succeed")
            E2eAssert.jsonAt(updateResponse.body(), "/id", createdOssProviderId, "[oss] updated provider id should match")
            E2eAssert.jsonAt(updateResponse.body(), "/name", updatePayload["name"], "[oss] updated provider name should match")
            E2eAssert.jsonAt(updateResponse.body(), "/host", updatePayload["host"], "[oss] updated provider host should match")
            E2eAssert.jsonAt(updateResponse.body(), "/accessKey", updatePayload["accessKey"], "[oss] updated provider access key should match")
            E2eAssert.jsonAt(updateResponse.body(), "/secretKey", updatePayload["secretKey"], "[oss] updated provider secret key should match")
            E2eAssert.jsonAt(
                updateResponse.body(),
                "/parentPath",
                updatePayload["parentPath"],
                "[oss] updated provider parentPath should match",
            )
            E2eAssert.jsonAt(updateResponse.body(), "/readonly", true, "[oss] updated provider readonly should match")

            val listResponse = state.api.get("/api/storage/oss")
            E2eAssert.status(listResponse, 200, "[oss] list providers should succeed")
            E2eAssert.jsonArrayContainsId(
                responseBody = listResponse.body(),
                createdOssProviderId,
                step = "[oss] list providers should contain created provider",
            )

            val deleteResponse = state.api.delete("/api/storage/oss/$createdOssProviderId")
            E2eAssert.status(deleteResponse, 204, "[oss] delete provider should succeed")
            ossProviderId = null

            val listAfterDeleteResponse = state.api.get("/api/storage/oss")
            E2eAssert.status(listAfterDeleteResponse, 200, "[oss] list providers after delete should succeed")
            E2eAssert.jsonArrayNotContainsId(
                responseBody = listAfterDeleteResponse.body(),
                createdOssProviderId,
                step = "[oss] deleted provider should not exist in list",
            )
        } finally {
            ossProviderId?.let { safeDelete(state.api, "/api/storage/oss/$it") }
        }
    }

    @Test
    fun `system config should enforce storage linkage constraints`() {
        val state = bootstrapAdminSession(baseUrl())
        var writableFsProviderId: Long? = null
        var readonlyFsProviderId: Long? = null
        var ossProviderId: Long? = null

        try {
            val writableFsSuffix = suffix()
            val writableFsResponse = state.api.post(
                path = "/api/storage/fs",
                json = fsPayload(
                    name = "e2e-fs-writable-$writableFsSuffix",
                    parentPath = state.runtime.workspace.resolve("storage-fs-writable-$writableFsSuffix").toAbsolutePath().toString(),
                    readonly = false,
                ),
            )
            E2eAssert.status(writableFsResponse, 201, "[linkage] create writable fs provider should succeed")
            writableFsProviderId = readId(writableFsResponse.body(), "[linkage] writable fs provider should return id")
            val writableFsId = requireNotNull(writableFsProviderId)

            val readonlyFsSuffix = suffix()
            val readonlyFsResponse = state.api.post(
                path = "/api/storage/fs",
                json = fsPayload(
                    name = "e2e-fs-readonly-$readonlyFsSuffix",
                    parentPath = state.runtime.workspace.resolve("storage-fs-readonly-$readonlyFsSuffix").toAbsolutePath().toString(),
                    readonly = true,
                ),
            )
            E2eAssert.status(readonlyFsResponse, 201, "[linkage] create readonly fs provider should succeed")
            readonlyFsProviderId = readId(readonlyFsResponse.body(), "[linkage] readonly fs provider should return id")
            val readonlyFsId = requireNotNull(readonlyFsProviderId)

            val linkageOssSuffix = suffix()
            val ossResponse = state.api.post(
                path = "/api/storage/oss",
                json = ossPayload(
                    name = "e2e-oss-linkage-$linkageOssSuffix",
                    host = "https://oss-linkage-$linkageOssSuffix.example.invalid",
                    bucket = "bucket-linkage-$linkageOssSuffix",
                    accessKey = "access-linkage-$linkageOssSuffix",
                    secretKey = "secret-linkage-$linkageOssSuffix",
                    parentPath = "/root-linkage-$linkageOssSuffix",
                    readonly = false,
                ),
            )
            E2eAssert.status(ossResponse, 201, "[linkage] create oss provider should succeed")
            ossProviderId = readId(ossResponse.body(), "[linkage] oss provider should return id")
            val ossId = requireNotNull(ossProviderId)

            val bindWritableResponse = state.api.put(
                path = "/api/system/config",
                json = mapOf("fsProviderId" to writableFsId),
            )
            E2eAssert.status(bindWritableResponse, 200, "[linkage] bind writable fs provider should succeed")
            E2eAssert.jsonAt(
                bindWritableResponse.body(),
                "/fsProviderId",
                writableFsId,
                "[linkage] system config fs provider should be writable provider",
            )

            val deleteBoundProviderResponse = state.api.delete("/api/storage/fs/$writableFsId")
            E2eAssert.apiError(
                deleteBoundProviderResponse,
                family = "SYSTEM",
                code = "SYSTEM_STORAGE_PROVIDER_CANNOT_BE_DELETED",
                expectedStatus = 500,
                step = "[linkage] deleting system fs provider should fail",
            )

            val bindReadonlyResponse = state.api.put(
                path = "/api/system/config",
                json = mapOf("fsProviderId" to readonlyFsId),
            )
            E2eAssert.apiError(
                bindReadonlyResponse,
                family = "SYSTEM",
                code = "SYSTEM_STORAGE_PROVIDER_CANNOT_BE_READONLY",
                expectedStatus = 500,
                step = "[linkage] readonly fs provider should not be bindable",
            )

            val bindRemoteResponse = state.api.put(
                path = "/api/system/config",
                json = mapOf<String, Any?>(
                    "fsProviderId" to null,
                    "ossProviderId" to ossId,
                ),
            )
            E2eAssert.apiError(
                bindRemoteResponse,
                family = "SYSTEM",
                code = "SYSTEM_STORAGE_PROVIDER_CANNOT_BE_REMOTE",
                expectedStatus = 500,
                step = "[linkage] remote provider should not be bindable as system storage",
            )

            val restoreResponse = state.api.put(
                path = "/api/system/config",
                json = mapOf("fsProviderId" to 0L),
            )
            E2eAssert.status(restoreResponse, 200, "[linkage] restore default fs provider should succeed")
            E2eAssert.jsonAt(
                restoreResponse.body(),
                "/fsProviderId",
                0,
                "[linkage] system config fs provider should be restored to default",
            )

            val deleteReadonlyFsResponse = state.api.delete("/api/storage/fs/$readonlyFsId")
            E2eAssert.status(deleteReadonlyFsResponse, 204, "[linkage] delete readonly fs provider should succeed")
            readonlyFsProviderId = null

            val deleteWritableFsResponse = state.api.delete("/api/storage/fs/$writableFsId")
            E2eAssert.status(deleteWritableFsResponse, 204, "[linkage] delete writable fs provider should succeed")
            writableFsProviderId = null

            val deleteOssResponse = state.api.delete("/api/storage/oss/$ossId")
            E2eAssert.status(deleteOssResponse, 204, "[linkage] delete oss provider should succeed")
            ossProviderId = null
        } finally {
            runCatching {
                state.api.put(
                    path = "/api/system/config",
                    json = mapOf("fsProviderId" to 0L),
                )
            }
            writableFsProviderId?.let { safeDelete(state.api, "/api/storage/fs/$it") }
            readonlyFsProviderId?.let { safeDelete(state.api, "/api/storage/fs/$it") }
            ossProviderId?.let { safeDelete(state.api, "/api/storage/oss/$it") }
        }
    }

    private fun baseUrl(): String = "http://127.0.0.1:$port"

    private fun assertAuthenticationFailed(response: HttpResponse<String>, step: String) {
        E2eAssert.apiError(
            response = response,
            family = "COMMON",
            code = "AUTHENTICATION_FAILED",
            expectedStatus = 401,
            step = step,
        )
    }

    private fun fsPayload(
        name: String,
        parentPath: String,
        readonly: Boolean,
    ): Map<String, Any> {
        return linkedMapOf(
            "name" to name,
            "parentPath" to parentPath,
            "readonly" to readonly,
        )
    }

    private fun ossPayload(
        name: String,
        host: String,
        bucket: String,
        accessKey: String,
        secretKey: String,
        parentPath: String,
        readonly: Boolean,
    ): Map<String, Any> {
        return linkedMapOf(
            "name" to name,
            "host" to host,
            "bucket" to bucket,
            "accessKey" to accessKey,
            "secretKey" to secretKey,
            "parentPath" to parentPath,
            "readonly" to readonly,
        )
    }

    private fun readId(responseBody: String, step: String): Long {
        val root = E2eJson.mapper.readTree(responseBody)
        val node = root.path("id")
        assertFalse(node.isMissingNode || node.isNull, "$step missing id")
        assertTrue(node.isIntegralNumber, "$step id should be an integer, got ${node.nodeType}")
        return node.longValue()
    }

    private fun safeDelete(api: E2eHttpClient, path: String) {
        runCatching { api.delete(path) }
    }

    private fun suffix(): String {
        return UUID.randomUUID().toString().replace("-", "").take(8)
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun registerDatasource(registry: DynamicPropertyRegistry) {
            E2eRuntime.registerDatasource(registry)
        }
    }
}
