package com.unirhy.e2e

import com.coooolfan.unirhy.UnirhyApplication
import com.fasterxml.jackson.databind.JsonNode
import com.unirhy.e2e.support.E2eAdminSession
import com.unirhy.e2e.support.E2eAssert
import com.unirhy.e2e.support.E2eHttpClient
import com.unirhy.e2e.support.E2eJson
import com.unirhy.e2e.support.E2eRuntime
import com.unirhy.e2e.support.bootstrapAdminSession
import com.unirhy.e2e.support.expandHomePath
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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
class AccountPlaylistContentE2eTest {

    @LocalServerPort
    private var port: Int = 0

    private val prepareLock = Any()
    private var preparedData: PreparedData? = null

    @AfterAll
    fun cleanup() {
        E2eRuntime.cleanup()
    }

    @Test
    @Order(1)
    fun `auth gate for account playlist and content mutation endpoints should reject unauthenticated access`() {
        val api = E2eHttpClient(baseUrl())

        assertAuthenticationFailed(
            api.get("/api/accounts"),
            "[auth] get accounts should require login",
        )
        assertAuthenticationFailed(
            api.post(
                path = "/api/accounts",
                json = accountCreatePayload(
                    name = "blocked-user",
                    email = "blocked-user@example.invalid",
                    password = "blocked-password",
                ),
            ),
            "[auth] create account should require login",
        )
        assertAuthenticationFailed(
            api.get("/api/accounts/me"),
            "[auth] get current account should require login",
        )
        assertAuthenticationFailed(
            api.delete("/api/accounts/1"),
            "[auth] delete account should require login",
        )
        assertAuthenticationFailed(
            api.put(
                path = "/api/accounts/1",
                json = mapOf("name" to "blocked"),
            ),
            "[auth] update account should require login",
        )

        assertAuthenticationFailed(
            api.get("/api/playlists"),
            "[auth] get playlist list should require login",
        )
        assertAuthenticationFailed(
            api.post(
                path = "/api/playlists",
                json = mapOf(
                    "name" to "blocked-playlist",
                    "comment" to "blocked-comment",
                ),
            ),
            "[auth] create playlist should require login",
        )
        assertAuthenticationFailed(
            api.get("/api/playlists/1"),
            "[auth] get playlist should require login",
        )
        assertAuthenticationFailed(
            api.put(
                path = "/api/playlists/1",
                json = mapOf(
                    "name" to "blocked-playlist-updated",
                    "comment" to "blocked-comment-updated",
                ),
            ),
            "[auth] update playlist should require login",
        )
        assertAuthenticationFailed(
            api.delete("/api/playlists/1"),
            "[auth] delete playlist should require login",
        )
        assertAuthenticationFailed(
            api.put("/api/playlists/1/recordings/1"),
            "[auth] add recording to playlist should require login",
        )
        assertAuthenticationFailed(
            api.delete("/api/playlists/1/recordings/1"),
            "[auth] remove recording from playlist should require login",
        )

        assertAuthenticationFailed(
            api.get(path = "/api/albums/search", query = mapOf("name" to "blocked")),
            "[auth] search albums should require login",
        )
        assertAuthenticationFailed(
            api.get(path = "/api/works/search", query = mapOf("name" to "blocked")),
            "[auth] search works should require login",
        )
        assertAuthenticationFailed(
            api.put(
                path = "/api/works/1",
                json = mapOf("title" to "blocked-work"),
            ),
            "[auth] update work should require login",
        )
        assertAuthenticationFailed(
            api.post(
                path = "/api/works/merge",
                json = mapOf(
                    "targetId" to 1L,
                    "needMergeIds" to listOf(1L, 2L),
                ),
            ),
            "[auth] merge works should require login",
        )
        assertAuthenticationFailed(
            api.put(
                path = "/api/recordings/1",
                json = mapOf(
                    "kind" to "CD",
                    "label" to "blocked-label",
                    "title" to "blocked-title",
                    "comment" to "blocked-comment",
                    "defaultInWork" to false,
                ),
            ),
            "[auth] update recording should require login",
        )
    }

    @Test
    @Order(2)
    fun `accounts should support create list me update delete with permission boundary`() {
        val state = bootstrapAdminSession(baseUrl())
        val suffix = suffix()

        val accountA = createAccountByAdmin(
            state = state,
            name = "acc-a-$suffix",
            email = "acc-a-$suffix@example.invalid",
            password = "acc-a-$suffix-password",
        )
        val accountB = createAccountByAdmin(
            state = state,
            name = "acc-b-$suffix",
            email = "acc-b-$suffix@example.invalid",
            password = "acc-b-$suffix-password",
        )

        val listResponse = state.api.get("/api/accounts")
        E2eAssert.status(listResponse, 200, "[accounts] list should succeed")
        E2eAssert.jsonArrayContainsId(
            responseBody = listResponse.body(),
            expectedId = accountA.id,
            step = "[accounts] list should contain account A",
        )
        E2eAssert.jsonArrayContainsId(
            responseBody = listResponse.body(),
            expectedId = accountB.id,
            step = "[accounts] list should contain account B",
        )

        val adminMeResponse = state.api.get("/api/accounts/me")
        E2eAssert.status(adminMeResponse, 200, "[accounts] admin me should succeed")
        val adminId = readIdFromObject(
            responseBody = adminMeResponse.body(),
            pointer = "/id",
            step = "[accounts] admin me should contain id",
        )

        val accountAApi = loginAsAccount(
            email = accountA.email,
            password = accountA.password,
        )

        val accountAMeResponse = accountAApi.get("/api/accounts/me")
        E2eAssert.status(accountAMeResponse, 200, "[accounts] account A me should succeed")
        E2eAssert.jsonAt(accountAMeResponse.body(), "/id", accountA.id, "[accounts] me id should match account A")
        E2eAssert.jsonAt(accountAMeResponse.body(), "/email", accountA.email, "[accounts] me email should match account A")
        E2eAssert.jsonMissing(accountAMeResponse.body(), "/password", "[accounts] me should not expose password")

        val updatedAName = "acc-a-updated-$suffix"
        val updateSelfResponse = accountAApi.put(
            path = "/api/accounts/${accountA.id}",
            json = mapOf("name" to updatedAName),
        )
        E2eAssert.status(updateSelfResponse, 200, "[accounts] account A self update should succeed")
        E2eAssert.jsonAt(updateSelfResponse.body(), "/id", accountA.id, "[accounts] updated account id should match")
        E2eAssert.jsonAt(updateSelfResponse.body(), "/name", updatedAName, "[accounts] updated account name should match")

        val forbiddenUpdateAdminResponse = accountAApi.put(
            path = "/api/accounts/$adminId",
            json = mapOf("name" to "forbidden-admin-update-$suffix"),
        )
        E2eAssert.apiError(
            response = forbiddenUpdateAdminResponse,
            family = "COMMON",
            code = "FORBIDDEN",
            expectedStatus = 403,
            step = "[accounts] non-admin should not update admin account",
        )

        val deleteBResponse = state.api.delete("/api/accounts/${accountB.id}")
        E2eAssert.status(deleteBResponse, 204, "[accounts] admin delete account B should succeed")

        val listAfterDeleteResponse = state.api.get("/api/accounts")
        E2eAssert.status(listAfterDeleteResponse, 200, "[accounts] list after delete should succeed")
        E2eAssert.jsonArrayNotContainsId(
            responseBody = listAfterDeleteResponse.body(),
            expectedId = accountB.id,
            step = "[accounts] deleted account B should not exist in list",
        )
    }

    @Test
    @Order(3)
    fun `playlists should support owner scoped crud and recording association`() {
        val state = bootstrapAdminSession(baseUrl())
        val data = ensurePreparedData(state)
        val suffix = suffix()

        val owner = createAccountByAdmin(
            state = state,
            name = "playlist-owner-$suffix",
            email = "playlist-owner-$suffix@example.invalid",
            password = "playlist-owner-$suffix-password",
        )
        val visitor = createAccountByAdmin(
            state = state,
            name = "playlist-visitor-$suffix",
            email = "playlist-visitor-$suffix@example.invalid",
            password = "playlist-visitor-$suffix-password",
        )

        val ownerApi = loginAsAccount(owner.email, owner.password)
        val visitorApi = loginAsAccount(visitor.email, visitor.password)

        val createPlaylistResponse = ownerApi.post(
            path = "/api/playlists",
            json = mapOf(
                "name" to "playlist-$suffix",
                "comment" to "playlist-comment-$suffix",
            ),
        )
        E2eAssert.status(createPlaylistResponse, 201, "[playlists] owner create should succeed")
        val playlistId = readIdFromObject(
            responseBody = createPlaylistResponse.body(),
            pointer = "/id",
            step = "[playlists] created playlist should contain id",
        )

        val ownerListResponse = ownerApi.get("/api/playlists")
        E2eAssert.status(ownerListResponse, 200, "[playlists] owner list should succeed")
        E2eAssert.jsonArrayContainsId(
            responseBody = ownerListResponse.body(),
            expectedId = playlistId,
            step = "[playlists] owner list should contain created playlist",
        )

        val ownerDetailResponse = ownerApi.get("/api/playlists/$playlistId")
        E2eAssert.status(ownerDetailResponse, 200, "[playlists] owner detail should succeed")
        E2eAssert.jsonAt(ownerDetailResponse.body(), "/id", playlistId, "[playlists] owner detail id should match")
        assertTrue(
            E2eJson.mapper.readTree(ownerDetailResponse.body()).path("recordings").isArray,
            "[playlists] owner detail should include recordings array",
        )

        val ownerUpdateResponse = ownerApi.put(
            path = "/api/playlists/$playlistId",
            json = mapOf(
                "name" to "playlist-updated-$suffix",
                "comment" to "playlist-updated-comment-$suffix",
            ),
        )
        E2eAssert.status(ownerUpdateResponse, 200, "[playlists] owner update should succeed")
        E2eAssert.jsonAt(
            ownerUpdateResponse.body(),
            "/name",
            "playlist-updated-$suffix",
            "[playlists] owner updated name should match",
        )

        val addRecordingResponse1 = ownerApi.put("/api/playlists/$playlistId/recordings/${data.recordingId}")
        E2eAssert.status(addRecordingResponse1, 204, "[playlists] first add recording should succeed")

        val addRecordingResponse2 = ownerApi.put("/api/playlists/$playlistId/recordings/${data.recordingId}")
        E2eAssert.status(addRecordingResponse2, 204, "[playlists] second add recording should stay idempotent")

        val ownerDetailAfterAddResponse = ownerApi.get("/api/playlists/$playlistId")
        E2eAssert.status(ownerDetailAfterAddResponse, 200, "[playlists] detail after add should succeed")
        assertTrue(
            detailContainsRecordingId(ownerDetailAfterAddResponse.body(), data.recordingId),
            "[playlists] playlist detail should contain added recording",
        )

        val removeRecordingResponse1 = ownerApi.delete("/api/playlists/$playlistId/recordings/${data.recordingId}")
        E2eAssert.status(removeRecordingResponse1, 204, "[playlists] first remove recording should succeed")

        val removeRecordingResponse2 = ownerApi.delete("/api/playlists/$playlistId/recordings/${data.recordingId}")
        E2eAssert.status(removeRecordingResponse2, 204, "[playlists] second remove recording should stay idempotent")

        val ownerDetailAfterRemoveResponse = ownerApi.get("/api/playlists/$playlistId")
        E2eAssert.status(ownerDetailAfterRemoveResponse, 200, "[playlists] detail after remove should succeed")
        assertFalse(
            detailContainsRecordingId(ownerDetailAfterRemoveResponse.body(), data.recordingId),
            "[playlists] playlist detail should not contain removed recording",
        )

        E2eAssert.status(
            visitorApi.get("/api/playlists/$playlistId"),
            404,
            "[playlists] non-owner get playlist should return not found",
        )
        E2eAssert.status(
            visitorApi.put(
                path = "/api/playlists/$playlistId",
                json = mapOf("name" to "visitor-updated-$suffix"),
            ),
            404,
            "[playlists] non-owner update playlist should return not found",
        )
        E2eAssert.status(
            visitorApi.delete("/api/playlists/$playlistId"),
            404,
            "[playlists] non-owner delete playlist should return not found",
        )
        E2eAssert.status(
            visitorApi.put("/api/playlists/$playlistId/recordings/${data.recordingId}"),
            404,
            "[playlists] non-owner add recording should return not found",
        )
        E2eAssert.status(
            visitorApi.delete("/api/playlists/$playlistId/recordings/${data.recordingId}"),
            404,
            "[playlists] non-owner remove recording should return not found",
        )

        val ownerDeleteResponse = ownerApi.delete("/api/playlists/$playlistId")
        E2eAssert.status(ownerDeleteResponse, 204, "[playlists] owner delete should succeed")

        val ownerListAfterDeleteResponse = ownerApi.get("/api/playlists")
        E2eAssert.status(ownerListAfterDeleteResponse, 200, "[playlists] owner list after delete should succeed")
        E2eAssert.jsonArrayNotContainsId(
            responseBody = ownerListAfterDeleteResponse.body(),
            expectedId = playlistId,
            step = "[playlists] deleted playlist should not remain in list",
        )
    }

    @Test
    @Order(4)
    fun `content endpoints should support search update recording update and merge flow`() {
        val state = bootstrapAdminSession(baseUrl())
        ensurePreparedData(state)
        val runSuffix = suffix()

        val worksListResponse = state.api.get(
            path = "/api/works",
            query = mapOf("pageIndex" to 0, "pageSize" to 200),
        )
        E2eAssert.status(worksListResponse, 200, "[content] work list should succeed")
        val works = pageRows(worksListResponse.body(), "[content] works list")
        assertTrue(works.size >= 2, "[content] merge flow requires at least two works")

        val targetWork = works[0]
        val sourceWork = works[1]
        val targetWorkId = readIdFromNode(targetWork.path("id"), "[content] target work id should be integral")
        val sourceWorkId = readIdFromNode(sourceWork.path("id"), "[content] source work id should be integral")
        assertTrue(targetWorkId != sourceWorkId, "[content] merge target and source must be different works")
        val sourceRecordingId = extractFirstRecordingId(sourceWork, "[content] source work should include recording")

        val updatedWorkTitle = "work-updated-$runSuffix"
        val updateWorkResponse = state.api.put(
            path = "/api/works/$targetWorkId",
            json = mapOf("title" to updatedWorkTitle),
        )
        E2eAssert.status(updateWorkResponse, 200, "[content] work update should succeed")
        E2eAssert.jsonAt(updateWorkResponse.body(), "/id", targetWorkId, "[content] updated work id should match")
        E2eAssert.jsonAt(updateWorkResponse.body(), "/title", updatedWorkTitle, "[content] updated work title should match")

        val workSearchResponse = state.api.get(
            path = "/api/works/search",
            query = mapOf("name" to updatedWorkTitle),
        )
        E2eAssert.status(workSearchResponse, 200, "[content] work search should succeed")
        assertArrayContainsId(
            responseBody = workSearchResponse.body(),
            expectedId = targetWorkId,
            step = "[content] work search should include updated work",
        )

        val workSearchUnknownResponse = state.api.get(
            path = "/api/works/search",
            query = mapOf("name" to "work-unknown-${UUID.randomUUID()}"),
        )
        E2eAssert.status(workSearchUnknownResponse, 200, "[content] unknown work search should succeed")
        assertArrayEmpty(
            responseBody = workSearchUnknownResponse.body(),
            step = "[content] unknown work search should return empty array",
        )

        val albumsListResponse = state.api.get(
            path = "/api/albums",
            query = mapOf("pageIndex" to 0, "pageSize" to 200),
        )
        E2eAssert.status(albumsListResponse, 200, "[content] album list should succeed")
        val albums = pageRows(albumsListResponse.body(), "[content] albums list")
        assertTrue(albums.isNotEmpty(), "[content] album search flow requires at least one album")
        val targetAlbum = albums.first()
        val targetAlbumId = readIdFromNode(targetAlbum.path("id"), "[content] album id should be integral")
        val targetAlbumTitle = targetAlbum.path("title").asText()

        val albumSearchResponse = state.api.get(
            path = "/api/albums/search",
            query = mapOf("name" to targetAlbumTitle),
        )
        E2eAssert.status(albumSearchResponse, 200, "[content] album search should succeed")
        assertArrayContainsId(
            responseBody = albumSearchResponse.body(),
            expectedId = targetAlbumId,
            step = "[content] album search should include target album",
        )

        val albumSearchUnknownResponse = state.api.get(
            path = "/api/albums/search",
            query = mapOf("name" to "album-unknown-${UUID.randomUUID()}"),
        )
        E2eAssert.status(albumSearchUnknownResponse, 200, "[content] unknown album search should succeed")
        assertArrayEmpty(
            responseBody = albumSearchUnknownResponse.body(),
            step = "[content] unknown album search should return empty array",
        )

        val recordingUpdatePayload = mapOf(
            "kind" to "VINYL",
            "label" to "label-$runSuffix",
            "title" to "recording-updated-$runSuffix",
            "comment" to "recording-comment-updated-$runSuffix",
            "defaultInWork" to false,
        )
        val updateRecordingResponse = state.api.put(
            path = "/api/recordings/$sourceRecordingId",
            json = recordingUpdatePayload,
        )
        E2eAssert.status(updateRecordingResponse, 200, "[content] recording update should succeed")

        val sourceDetailAfterRecordingUpdateResponse = state.api.get("/api/works/$sourceWorkId")
        E2eAssert.status(sourceDetailAfterRecordingUpdateResponse, 200, "[content] source work detail should succeed")
        val sourceRecordingNode = findRecordingNode(
            responseBody = sourceDetailAfterRecordingUpdateResponse.body(),
            recordingId = sourceRecordingId,
            step = "[content] source work detail should contain updated recording",
        )
        assertNotNull(sourceRecordingNode, "[content] updated recording should exist in source work detail")
        assertEquals(
            recordingUpdatePayload["kind"],
            sourceRecordingNode.path("kind").asText(),
            "[content] updated recording kind should match",
        )
        assertEquals(
            recordingUpdatePayload["label"],
            sourceRecordingNode.path("label").asText(),
            "[content] updated recording label should match",
        )
        assertEquals(
            recordingUpdatePayload["title"],
            sourceRecordingNode.path("title").asText(),
            "[content] updated recording title should match",
        )
        assertEquals(
            recordingUpdatePayload["comment"],
            sourceRecordingNode.path("comment").asText(),
            "[content] updated recording comment should match",
        )
        assertEquals(
            recordingUpdatePayload["defaultInWork"],
            sourceRecordingNode.path("defaultInWork").asBoolean(),
            "[content] updated recording defaultInWork should match",
        )

        val mergeResponse = state.api.post(
            path = "/api/works/merge",
            json = mapOf(
                "targetId" to targetWorkId,
                "needMergeIds" to listOf(targetWorkId, sourceWorkId),
            ),
        )
        E2eAssert.status(mergeResponse, 200, "[content] work merge should succeed")

        val worksAfterMergeResponse = state.api.get(
            path = "/api/works",
            query = mapOf("pageIndex" to 0, "pageSize" to 200),
        )
        E2eAssert.status(worksAfterMergeResponse, 200, "[content] work list after merge should succeed")
        assertFalse(
            pageContainsId(worksAfterMergeResponse.body(), sourceWorkId),
            "[content] merged source work should be removed from work list",
        )

        val targetAfterMergeResponse = state.api.get("/api/works/$targetWorkId")
        E2eAssert.status(targetAfterMergeResponse, 200, "[content] merged target work detail should succeed")
        assertTrue(
            detailContainsRecordingId(targetAfterMergeResponse.body(), sourceRecordingId),
            "[content] merged target work should contain source recording",
        )
    }

    private fun createAccountByAdmin(
        state: E2eAdminSession,
        name: String,
        email: String,
        password: String,
    ): CreatedAccount {
        val createResponse = state.api.post(
            path = "/api/accounts",
            json = accountCreatePayload(name = name, email = email, password = password),
        )
        E2eAssert.status(createResponse, 201, "[accounts] create account should succeed")
        E2eAssert.jsonAt(createResponse.body(), "/name", name, "[accounts] created account name should match")
        E2eAssert.jsonAt(createResponse.body(), "/email", email, "[accounts] created account email should match")
        E2eAssert.jsonMissing(createResponse.body(), "/password", "[accounts] created account should not expose password")
        val id = readIdFromObject(
            responseBody = createResponse.body(),
            pointer = "/id",
            step = "[accounts] created account should contain id",
        )
        return CreatedAccount(
            id = id,
            name = name,
            email = email,
            password = password,
        )
    }

    private fun loginAsAccount(email: String, password: String): E2eHttpClient {
        val api = E2eHttpClient(baseUrl())
        val loginResponse = api.post(
            path = "/api/tokens",
            json = mapOf(
                "email" to email,
                "password" to password,
            ),
        )
        E2eAssert.status(loginResponse, 200, "[accounts] account login should succeed")
        return api
    }

    private fun ensurePreparedData(state: E2eAdminSession): PreparedData {
        preparedData?.let { return it }

        synchronized(prepareLock) {
            preparedData?.let { return it }
            val recovered = recoverPreparedDataFromApi(state)
            if (recovered != null) {
                preparedData = recovered
                return recovered
            }
            val created = executeScanAndPrepareData(state)
            preparedData = created
            return created
        }
    }

    private fun recoverPreparedDataFromApi(state: E2eAdminSession): PreparedData? {
        val worksResponse = state.api.get(
            path = "/api/works",
            query = mapOf("pageIndex" to 0, "pageSize" to 200),
        )
        E2eAssert.status(worksResponse, 200, "[prepare] list works should succeed for recovery")
        val works = pageRows(worksResponse.body(), "[prepare] works recovery")
        if (works.isEmpty()) {
            return null
        }
        val firstRecordingId = runCatching {
            extractFirstRecordingId(works.first(), "[prepare] first work should include recording")
        }.getOrNull() ?: return null

        val albumsResponse = state.api.get(
            path = "/api/albums",
            query = mapOf("pageIndex" to 0, "pageSize" to 200),
        )
        E2eAssert.status(albumsResponse, 200, "[prepare] list albums should succeed for recovery")
        val albums = pageRows(albumsResponse.body(), "[prepare] albums recovery")
        if (albums.isEmpty()) {
            return null
        }
        val firstAlbumId = albums.first().path("id")
        if (!firstAlbumId.isIntegralNumber) {
            return null
        }

        return PreparedData(
            recordingId = firstRecordingId,
            albumId = firstAlbumId.longValue(),
        )
    }

    private fun executeScanAndPrepareData(state: E2eAdminSession): PreparedData {
        val requestBody = scanRequestBody(state)
        prepareScanFixture(state.runtime.scanWorkspace)

        val submitResponse = state.api.post(
            path = "/api/task/scan",
            json = requestBody,
        )
        E2eAssert.status(submitResponse, 202, "[prepare] submit scan task should return accepted")
        awaitScanTaskFinished(state)

        val worksResponse = state.api.get(
            path = "/api/works",
            query = mapOf("pageIndex" to 0, "pageSize" to 200),
        )
        E2eAssert.status(worksResponse, 200, "[prepare] list works after scan should succeed")
        val works = pageRows(worksResponse.body(), "[prepare] works after scan")
        assertTrue(works.isNotEmpty(), "[prepare] expected works after scan")
        val recordingId = extractFirstRecordingId(works.first(), "[prepare] first work after scan should include recording")

        val albumsResponse = state.api.get(
            path = "/api/albums",
            query = mapOf("pageIndex" to 0, "pageSize" to 200),
        )
        E2eAssert.status(albumsResponse, 200, "[prepare] list albums after scan should succeed")
        val albums = pageRows(albumsResponse.body(), "[prepare] albums after scan")
        assertTrue(albums.isNotEmpty(), "[prepare] expected albums after scan")
        val albumId = readIdFromNode(albums.first().path("id"), "[prepare] first album after scan should include id")

        return PreparedData(
            recordingId = recordingId,
            albumId = albumId,
        )
    }

    private fun awaitScanTaskFinished(state: E2eAdminSession) {
        val deadline = System.currentTimeMillis() + scanWaitTimeoutMillis()
        var observedRunning = false
        var emptyPolls = 0

        while (System.currentTimeMillis() <= deadline) {
            val runningResponse = state.api.get("/api/task/running")
            E2eAssert.status(runningResponse, 200, "[prepare] list running tasks should succeed")
            if (containsScanTask(runningResponse.body())) {
                observedRunning = true
                emptyPolls = 0
            } else {
                emptyPolls += 1
                if (observedRunning || emptyPolls >= FAST_COMPLETE_EMPTY_POLLS_THRESHOLD) {
                    return
                }
            }
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }

        fail("[prepare] scan task did not finish within timeout ${scanWaitTimeoutMillis()} ms")
    }

    private fun containsScanTask(responseBody: String): Boolean {
        val root = E2eJson.mapper.readTree(responseBody)
        if (!root.isArray) {
            return false
        }
        return root.any { item -> item.path("type").asText() == "SCAN" }
    }

    private fun prepareScanFixture(scanWorkspace: Path) {
        val sourceRoot = resolveScanSourceRoot()
        require(Files.exists(sourceRoot) && Files.isDirectory(sourceRoot)) {
            "[prepare] scan source path does not exist or is not directory: $sourceRoot"
        }

        val seedFile = collectAudioCandidates(sourceRoot).firstOrNull()
            ?: error("[prepare] no audio files found under $sourceRoot, set env $SCAN_SOURCE_PATH_ENV")

        val extension = fileExtension(seedFile)
        val fixtureRoot = scanWorkspace.resolve("account-playlist-content-${suffix()}")
        Files.createDirectories(fixtureRoot)

        repeat(SCAN_FIXTURE_FILE_COUNT) { index ->
            val target = fixtureRoot.resolve("seed-${index.toString().padStart(3, '0')}.$extension")
            symlinkOrCopy(seedFile, target)
        }
    }

    private fun collectAudioCandidates(sourceRoot: Path): List<Path> {
        val candidates = mutableListOf<Path>()
        Files.walk(sourceRoot).use { paths ->
            val iterator = paths.iterator()
            while (iterator.hasNext()) {
                val file = iterator.next()
                if (!Files.isRegularFile(file)) {
                    continue
                }
                if (fileExtension(file) !in ACCEPT_EXTENSIONS) {
                    continue
                }
                candidates.add(file.toAbsolutePath().normalize())
                if (candidates.size >= MAX_CANDIDATE_FILE_COUNT) {
                    break
                }
            }
        }
        return candidates.sortedBy { it.toString() }
    }

    private fun symlinkOrCopy(source: Path, target: Path) {
        val normalizedSource = source.toAbsolutePath().normalize()
        val symlinkCreated = runCatching {
            Files.createSymbolicLink(target, normalizedSource)
        }.isSuccess
        if (symlinkCreated) {
            return
        }
        Files.copy(normalizedSource, target, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun resolveScanSourceRoot(): Path {
        val configured = System.getenv(SCAN_SOURCE_PATH_ENV)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_SCAN_SOURCE_PATH
        return configured.expandHomePath().toAbsolutePath().normalize()
    }

    private fun scanRequestBody(state: E2eAdminSession): Map<String, Any> {
        return mapOf(
            "providerType" to "FILE_SYSTEM",
            "providerId" to resolveSystemFsProviderId(state),
        )
    }

    private fun resolveSystemFsProviderId(state: E2eAdminSession): Long {
        val response = state.api.get("/api/system/config")
        E2eAssert.status(response, 200, "[prepare] get system config should succeed")
        val fsProviderIdNode = E2eJson.mapper.readTree(response.body()).path("fsProviderId")
        assertTrue(fsProviderIdNode.isIntegralNumber, "[prepare] fsProviderId should be integral")
        return fsProviderIdNode.longValue()
    }

    private fun detailContainsRecordingId(responseBody: String, expectedRecordingId: Long): Boolean {
        val recordingsNode = E2eJson.mapper.readTree(responseBody).path("recordings")
        if (!recordingsNode.isArray) {
            return false
        }
        return recordingsNode.any { recording ->
            recording.path("id").isIntegralNumber && recording.path("id").longValue() == expectedRecordingId
        }
    }

    private fun findRecordingNode(responseBody: String, recordingId: Long, step: String): JsonNode? {
        val recordingsNode = E2eJson.mapper.readTree(responseBody).path("recordings")
        if (!recordingsNode.isArray) {
            fail("$step expected recordings array")
        }
        return recordingsNode.firstOrNull { recording ->
            recording.path("id").isIntegralNumber && recording.path("id").longValue() == recordingId
        }
    }

    private fun extractFirstRecordingId(workNode: JsonNode, step: String): Long {
        val recordings = workNode.path("recordings")
        if (!recordings.isArray || recordings.isEmpty) {
            fail("$step expected non-empty recordings array")
        }
        val recordingIdNode = recordings.first().path("id")
        if (!recordingIdNode.isIntegralNumber) {
            fail("$step expected first recording id to be integral")
        }
        return recordingIdNode.longValue()
    }

    private fun pageRows(responseBody: String, step: String): List<JsonNode> {
        val root = E2eJson.mapper.readTree(responseBody)
        val rows = root.path("rows")
        assertTrue(rows.isArray, "$step expected rows array")
        return rows.toList()
    }

    private fun pageContainsId(responseBody: String, expectedId: Long): Boolean {
        return pageRows(responseBody, "[page] response").any { row ->
            row.path("id").isIntegralNumber && row.path("id").longValue() == expectedId
        }
    }

    private fun assertArrayContainsId(responseBody: String, expectedId: Long, step: String) {
        val root = E2eJson.mapper.readTree(responseBody)
        assertTrue(root.isArray, "$step expected array response")
        assertTrue(
            root.any { item -> item.path("id").isIntegralNumber && item.path("id").longValue() == expectedId },
            "$step expected id=$expectedId to exist",
        )
    }

    private fun assertArrayEmpty(responseBody: String, step: String) {
        val root = E2eJson.mapper.readTree(responseBody)
        assertTrue(root.isArray, "$step expected array response")
        assertTrue(root.isEmpty, "$step expected empty array")
    }

    private fun readIdFromObject(responseBody: String, pointer: String, step: String): Long {
        val node = E2eJson.mapper.readTree(responseBody).at(pointer)
        return readIdFromNode(node, step)
    }

    private fun readIdFromNode(node: JsonNode, step: String): Long {
        assertTrue(node.isIntegralNumber, "$step expected integral id")
        return node.longValue()
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

    private fun accountCreatePayload(name: String, email: String, password: String): Map<String, Any> {
        return mapOf(
            "name" to name,
            "email" to email,
            "password" to password,
        )
    }

    private fun fileExtension(path: Path): String {
        val filename = path.fileName?.toString().orEmpty()
        return filename.substringAfterLast('.', "").lowercase()
    }

    private fun scanWaitTimeoutMillis(): Long {
        val raw = System.getenv(SCAN_WAIT_TIMEOUT_ENV)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return DEFAULT_SCAN_WAIT_TIMEOUT_MILLIS
        val value = raw.toLongOrNull()
        require(value != null && value > 0) {
            "[prepare] env $SCAN_WAIT_TIMEOUT_ENV must be positive integer milliseconds, actual=$raw"
        }
        return value
    }

    private fun suffix(): String = UUID.randomUUID().toString().replace("-", "").take(10)

    private fun baseUrl(): String = "http://127.0.0.1:$port"

    private data class CreatedAccount(
        val id: Long,
        val name: String,
        val email: String,
        val password: String,
    )

    private data class PreparedData(
        val recordingId: Long,
        val albumId: Long,
    )

    companion object {
        private const val SCAN_SOURCE_PATH_ENV = "E2E_SCAN_SOURCE_PATH"
        private const val SCAN_WAIT_TIMEOUT_ENV = "E2E_SCAN_WAIT_TIMEOUT_MILLIS"
        private const val DEFAULT_SCAN_SOURCE_PATH = "~/Music"
        private const val DEFAULT_SCAN_WAIT_TIMEOUT_MILLIS = 120_000L
        private const val POLL_INTERVAL_MILLIS = 150L
        private const val FAST_COMPLETE_EMPTY_POLLS_THRESHOLD = 3
        private const val SCAN_FIXTURE_FILE_COUNT = 16
        private const val MAX_CANDIDATE_FILE_COUNT = 10_000

        private val ACCEPT_EXTENSIONS = setOf("mp3", "wav", "ogg", "flac", "aac", "wma", "m4a")

        @JvmStatic
        @DynamicPropertySource
        fun registerDatasource(registry: DynamicPropertyRegistry) {
            E2eRuntime.registerDatasource(registry)
        }
    }
}
