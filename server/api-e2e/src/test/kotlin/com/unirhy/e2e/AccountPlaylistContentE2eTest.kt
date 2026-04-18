package com.unirhy.e2e

import com.coooolfan.unirhy.UnirhyApplication
import com.fasterxml.jackson.databind.JsonNode
import com.unirhy.e2e.support.AudioFixtureMetadata
import com.unirhy.e2e.support.E2eAdminSession
import com.unirhy.e2e.support.E2eAssert
import com.unirhy.e2e.support.E2eHttpClient
import com.unirhy.e2e.support.E2eJson
import com.unirhy.e2e.support.E2eRuntime
import com.unirhy.e2e.support.SyntheticAudioFixture
import com.unirhy.e2e.support.bootstrapAdminSession
import org.springframework.beans.factory.annotation.Autowired
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
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

    @Autowired
    private lateinit var jdbc: NamedParameterJdbcTemplate

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
            api.get("/api/recordings/1"),
            "[auth] get recording should require login",
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
        val suffix = suffix()
        val playlistOrderData = preparePlaylistOrderData(state)

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

        val addRecordingResponse1 = ownerApi.put("/api/playlists/$playlistId/recordings/${playlistOrderData.firstRecordingId}")
        E2eAssert.status(addRecordingResponse1, 204, "[playlists] first add recording should succeed")

        val addRecordingResponse2 = ownerApi.put("/api/playlists/$playlistId/recordings/${playlistOrderData.firstRecordingId}")
        E2eAssert.status(addRecordingResponse2, 204, "[playlists] second add recording should stay idempotent")

        val addRecordingResponse3 = ownerApi.put("/api/playlists/$playlistId/recordings/${playlistOrderData.secondRecordingId}")
        E2eAssert.status(addRecordingResponse3, 204, "[playlists] append second recording should succeed")

        val ownerDetailAfterAppendResponse = ownerApi.get("/api/playlists/$playlistId")
        E2eAssert.status(ownerDetailAfterAppendResponse, 200, "[playlists] detail after append should succeed")
        assertRecordingOrder(
            responseBody = ownerDetailAfterAppendResponse.body(),
            expectedRecordingIds = listOf(
                playlistOrderData.firstRecordingId,
                playlistOrderData.secondRecordingId,
            ),
            step = "[playlists] appended recordings should keep insertion order",
        )

        val removeRecordingResponse1 = ownerApi.delete("/api/playlists/$playlistId/recordings/${playlistOrderData.firstRecordingId}")
        E2eAssert.status(removeRecordingResponse1, 204, "[playlists] first remove recording should succeed")

        val reAddRecordingResponse = ownerApi.put("/api/playlists/$playlistId/recordings/${playlistOrderData.firstRecordingId}")
        E2eAssert.status(reAddRecordingResponse, 204, "[playlists] re-add removed recording should append to tail")

        val ownerDetailAfterReAddResponse = ownerApi.get("/api/playlists/$playlistId")
        E2eAssert.status(ownerDetailAfterReAddResponse, 200, "[playlists] detail after re-add should succeed")
        assertRecordingOrder(
            responseBody = ownerDetailAfterReAddResponse.body(),
            expectedRecordingIds = listOf(
                playlistOrderData.secondRecordingId,
                playlistOrderData.firstRecordingId,
            ),
            step = "[playlists] re-added recording should move to tail order",
        )

        val removeRecordingResponse2 = ownerApi.delete("/api/playlists/$playlistId/recordings/${playlistOrderData.secondRecordingId}")
        E2eAssert.status(removeRecordingResponse2, 204, "[playlists] remove second recording should succeed")

        val removeRecordingResponse3 = ownerApi.delete("/api/playlists/$playlistId/recordings/${playlistOrderData.secondRecordingId}")
        E2eAssert.status(removeRecordingResponse3, 204, "[playlists] duplicate remove should stay idempotent")

        val removeRecordingResponse4 = ownerApi.delete("/api/playlists/$playlistId/recordings/${playlistOrderData.firstRecordingId}")
        E2eAssert.status(removeRecordingResponse4, 204, "[playlists] remove last recording should succeed")

        val ownerDetailAfterRemoveResponse = ownerApi.get("/api/playlists/$playlistId")
        E2eAssert.status(ownerDetailAfterRemoveResponse, 200, "[playlists] detail after remove should succeed")
        assertFalse(
            detailContainsRecordingId(ownerDetailAfterRemoveResponse.body(), playlistOrderData.firstRecordingId),
            "[playlists] playlist detail should not contain removed first recording",
        )
        assertFalse(
            detailContainsRecordingId(ownerDetailAfterRemoveResponse.body(), playlistOrderData.secondRecordingId),
            "[playlists] playlist detail should not contain removed second recording",
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
            visitorApi.put("/api/playlists/$playlistId/recordings/${playlistOrderData.firstRecordingId}"),
            404,
            "[playlists] non-owner add recording should return not found",
        )
        E2eAssert.status(
            visitorApi.delete("/api/playlists/$playlistId/recordings/${playlistOrderData.firstRecordingId}"),
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
    @Order(5)
    fun `recording merge should move album and playlist associations to target recording`() {
        val state = bootstrapAdminSession(baseUrl())
        val mergeData = prepareRecordingMergeData(state)
        val suffix = suffix()

        val createPlaylistResponse = state.api.post(
            path = "/api/playlists",
            json = mapOf(
                "name" to "recording-merge-$suffix",
                "comment" to "recording-merge-comment-$suffix",
            ),
        )
        E2eAssert.status(createPlaylistResponse, 201, "[recording-merge] create playlist should succeed")
        val playlistId = readIdFromObject(
            responseBody = createPlaylistResponse.body(),
            pointer = "/id",
            step = "[recording-merge] playlist id should exist",
        )

        val addSourceRecordingResponse = state.api.put("/api/playlists/$playlistId/recordings/${mergeData.sourceRecordingId}")
        E2eAssert.status(addSourceRecordingResponse, 204, "[recording-merge] add source recording to playlist should succeed")

        val sourceAlbumBeforeMergeResponse = state.api.get("/api/albums/${mergeData.sourceAlbumId}")
        E2eAssert.status(sourceAlbumBeforeMergeResponse, 200, "[recording-merge] source album detail before merge should succeed")
        assertRecordingOrder(
            responseBody = sourceAlbumBeforeMergeResponse.body(),
            expectedRecordingIds = listOf(mergeData.sourceRecordingId),
            step = "[recording-merge] source album should initially contain source recording only",
        )

        val playlistBeforeMergeResponse = state.api.get("/api/playlists/$playlistId")
        E2eAssert.status(playlistBeforeMergeResponse, 200, "[recording-merge] playlist detail before merge should succeed")
        assertRecordingOrder(
            responseBody = playlistBeforeMergeResponse.body(),
            expectedRecordingIds = listOf(mergeData.sourceRecordingId),
            step = "[recording-merge] playlist should initially contain source recording only",
        )

        val mergeResponse = state.api.put(
            path = "/api/recordings/merge",
            json = mapOf(
                "targetId" to mergeData.targetRecordingId,
                "needMergeIds" to listOf(mergeData.targetRecordingId, mergeData.sourceRecordingId),
            ),
        )
        E2eAssert.status(mergeResponse, 200, "[recording-merge] merge recording should succeed")

        val missingSourceRecordingResponse = state.api.get("/api/recordings/${mergeData.sourceRecordingId}")
        E2eAssert.status(missingSourceRecordingResponse, 404, "[recording-merge] source recording should be deleted after merge")

        val sourceAlbumAfterMergeResponse = state.api.get("/api/albums/${mergeData.sourceAlbumId}")
        E2eAssert.status(sourceAlbumAfterMergeResponse, 200, "[recording-merge] source album detail after merge should succeed")
        assertRecordingOrder(
            responseBody = sourceAlbumAfterMergeResponse.body(),
            expectedRecordingIds = listOf(mergeData.targetRecordingId),
            step = "[recording-merge] source album should now reference target recording",
        )

        val playlistAfterMergeResponse = state.api.get("/api/playlists/$playlistId")
        E2eAssert.status(playlistAfterMergeResponse, 200, "[recording-merge] playlist detail after merge should succeed")
        assertRecordingOrder(
            responseBody = playlistAfterMergeResponse.body(),
            expectedRecordingIds = listOf(mergeData.targetRecordingId),
            step = "[recording-merge] playlist should now reference target recording",
        )
    }

    @Test
    @Order(6)
    fun `recording merge should append target to inherited album and playlist tail`() {
        val state = bootstrapAdminSession(baseUrl())
        val targetRecordingId = ensurePreparedData(state).recordingId
        val targetWorkId = fetchRecordingWorkId(state, targetRecordingId, "[recording-merge-tail] target recording work id")
        val suffix = suffix()

        val sourceAlbumId = insertAlbum(
            title = "recording-merge-tail-album-$suffix",
            comment = "recording-merge-tail-album-comment-$suffix",
        )
        val anchorRecordingId = insertRecording(
            workId = targetWorkId,
            title = "recording-merge-tail-anchor-$suffix",
            comment = "recording-merge-tail-anchor-comment-$suffix",
        )
        val sourceRecordingId = insertRecording(
            workId = targetWorkId,
            title = "recording-merge-tail-source-$suffix",
            comment = "recording-merge-tail-source-comment-$suffix",
        )
        insertAlbumRecording(albumId = sourceAlbumId, recordingId = anchorRecordingId, sortOrder = 0)
        insertAlbumRecording(albumId = sourceAlbumId, recordingId = sourceRecordingId, sortOrder = 1)

        val playlistId = createPlaylistForAdmin(
            state = state,
            name = "recording-merge-tail-$suffix",
            comment = "recording-merge-tail-comment-$suffix",
            step = "[recording-merge-tail] create playlist",
        )
        addRecordingToPlaylist(
            state = state,
            playlistId = playlistId,
            recordingId = anchorRecordingId,
            step = "[recording-merge-tail] add anchor recording to playlist",
        )
        addRecordingToPlaylist(
            state = state,
            playlistId = playlistId,
            recordingId = sourceRecordingId,
            step = "[recording-merge-tail] add source recording to playlist",
        )

        val mergeResponse = state.api.put(
            path = "/api/recordings/merge",
            json = mapOf(
                "targetId" to targetRecordingId,
                "needMergeIds" to listOf(targetRecordingId, sourceRecordingId),
            ),
        )
        E2eAssert.status(mergeResponse, 200, "[recording-merge-tail] merge recording should succeed")

        val sourceAlbumAfterMergeResponse = state.api.get("/api/albums/$sourceAlbumId")
        E2eAssert.status(sourceAlbumAfterMergeResponse, 200, "[recording-merge-tail] source album detail after merge should succeed")
        assertRecordingOrder(
            responseBody = sourceAlbumAfterMergeResponse.body(),
            expectedRecordingIds = listOf(anchorRecordingId, targetRecordingId),
            step = "[recording-merge-tail] source album should keep anchor then append target",
        )

        val playlistAfterMergeResponse = state.api.get("/api/playlists/$playlistId")
        E2eAssert.status(playlistAfterMergeResponse, 200, "[recording-merge-tail] playlist detail after merge should succeed")
        assertRecordingOrder(
            responseBody = playlistAfterMergeResponse.body(),
            expectedRecordingIds = listOf(anchorRecordingId, targetRecordingId),
            step = "[recording-merge-tail] playlist should keep anchor then append target",
        )
    }

    @Test
    @Order(7)
    fun `recording merge should deduplicate target in inherited album and playlist`() {
        val state = bootstrapAdminSession(baseUrl())
        val targetRecordingId = ensurePreparedData(state).recordingId
        val targetWorkId = fetchRecordingWorkId(state, targetRecordingId, "[recording-merge-dedupe] target recording work id")
        val suffix = suffix()

        val sourceAlbumId = insertAlbum(
            title = "recording-merge-dedupe-album-$suffix",
            comment = "recording-merge-dedupe-album-comment-$suffix",
        )
        val sourceRecordingId = insertRecording(
            workId = targetWorkId,
            title = "recording-merge-dedupe-source-$suffix",
            comment = "recording-merge-dedupe-source-comment-$suffix",
        )
        insertAlbumRecording(albumId = sourceAlbumId, recordingId = targetRecordingId, sortOrder = 0)
        insertAlbumRecording(albumId = sourceAlbumId, recordingId = sourceRecordingId, sortOrder = 1)

        val playlistId = createPlaylistForAdmin(
            state = state,
            name = "recording-merge-dedupe-$suffix",
            comment = "recording-merge-dedupe-comment-$suffix",
            step = "[recording-merge-dedupe] create playlist",
        )
        addRecordingToPlaylist(
            state = state,
            playlistId = playlistId,
            recordingId = targetRecordingId,
            step = "[recording-merge-dedupe] add target recording to playlist",
        )
        addRecordingToPlaylist(
            state = state,
            playlistId = playlistId,
            recordingId = sourceRecordingId,
            step = "[recording-merge-dedupe] add source recording to playlist",
        )

        val mergeResponse = state.api.put(
            path = "/api/recordings/merge",
            json = mapOf(
                "targetId" to targetRecordingId,
                "needMergeIds" to listOf(targetRecordingId, sourceRecordingId),
            ),
        )
        E2eAssert.status(mergeResponse, 200, "[recording-merge-dedupe] merge recording should succeed")

        val sourceAlbumAfterMergeResponse = state.api.get("/api/albums/$sourceAlbumId")
        E2eAssert.status(sourceAlbumAfterMergeResponse, 200, "[recording-merge-dedupe] source album detail after merge should succeed")
        assertRecordingOrder(
            responseBody = sourceAlbumAfterMergeResponse.body(),
            expectedRecordingIds = listOf(targetRecordingId),
            step = "[recording-merge-dedupe] source album should keep one target recording only",
        )

        val playlistAfterMergeResponse = state.api.get("/api/playlists/$playlistId")
        E2eAssert.status(playlistAfterMergeResponse, 200, "[recording-merge-dedupe] playlist detail after merge should succeed")
        assertRecordingOrder(
            responseBody = playlistAfterMergeResponse.body(),
            expectedRecordingIds = listOf(targetRecordingId),
            step = "[recording-merge-dedupe] playlist should keep one target recording only",
        )
    }

    @Test
    @Order(4)
    fun `content endpoints should support search update recording update and merge flow`() {
        val state = bootstrapAdminSession(baseUrl())
        val data = ensurePreparedData(state)
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

        val targetAlbumId = data.albumId
        val targetAlbumTitle = data.albumTitle

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

        val getRecordingResponse = state.api.get("/api/recordings/$sourceRecordingId")
        E2eAssert.status(getRecordingResponse, 200, "[content] recording detail should succeed")
        E2eAssert.jsonAt(
            getRecordingResponse.body(),
            "/id",
            sourceRecordingId,
            "[content] recording detail id should match",
        )
        E2eAssert.jsonAt(
            getRecordingResponse.body(),
            "/work/id",
            sourceWorkId,
            "[content] recording detail work id should match",
        )
        E2eAssert.jsonAt(
            getRecordingResponse.body(),
            "/title",
            recordingUpdatePayload["title"],
            "[content] recording detail title should match",
        )
        E2eAssert.jsonAt(
            getRecordingResponse.body(),
            "/comment",
            recordingUpdatePayload["comment"],
            "[content] recording detail comment should match",
        )
        assertTrue(
            E2eJson.mapper.readTree(getRecordingResponse.body()).path("artists").isArray,
            "[content] recording detail artists should be an array",
        )

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

        val missingRecordingResponse = state.api.get("/api/recordings/${Long.MAX_VALUE}")
        E2eAssert.status(missingRecordingResponse, 404, "[content] missing recording detail should return 404")
    }

    @Test
    @Order(8)
    fun `album reorder should update sort order and validate input`() {
        val state = bootstrapAdminSession(baseUrl())
        val unrelatedRecordingId = ensurePreparedData(state).recordingId
        val workId = fetchRecordingWorkId(
            state = state,
            recordingId = unrelatedRecordingId,
            step = "[album-reorder] reference work id",
        )
        val suffix = suffix()

        val albumId = insertAlbum(
            title = "album-reorder-$suffix",
            comment = "album-reorder-comment-$suffix",
        )
        val firstRecordingId = insertRecording(
            workId = workId,
            title = "album-reorder-first-$suffix",
            comment = "album-reorder-first-comment-$suffix",
        )
        val secondRecordingId = insertRecording(
            workId = workId,
            title = "album-reorder-second-$suffix",
            comment = "album-reorder-second-comment-$suffix",
        )
        val thirdRecordingId = insertRecording(
            workId = workId,
            title = "album-reorder-third-$suffix",
            comment = "album-reorder-third-comment-$suffix",
        )
        insertAlbumRecording(albumId = albumId, recordingId = firstRecordingId, sortOrder = 0)
        insertAlbumRecording(albumId = albumId, recordingId = secondRecordingId, sortOrder = 1)
        insertAlbumRecording(albumId = albumId, recordingId = thirdRecordingId, sortOrder = 2)

        val initialDetail = state.api.get("/api/albums/$albumId")
        E2eAssert.status(initialDetail, 200, "[album-reorder] initial detail should succeed")
        assertRecordingOrder(
            responseBody = initialDetail.body(),
            expectedRecordingIds = listOf(firstRecordingId, secondRecordingId, thirdRecordingId),
            step = "[album-reorder] initial order should match fixtures",
        )

        val reorderResponse = state.api.put(
            path = "/api/albums/$albumId/recordings/reorder",
            json = mapOf(
                "recordingIds" to listOf(thirdRecordingId, firstRecordingId, secondRecordingId),
            ),
        )
        E2eAssert.status(reorderResponse, 204, "[album-reorder] reorder should succeed")

        val afterReorderDetail = state.api.get("/api/albums/$albumId")
        E2eAssert.status(afterReorderDetail, 200, "[album-reorder] detail after reorder should succeed")
        assertRecordingOrder(
            responseBody = afterReorderDetail.body(),
            expectedRecordingIds = listOf(thirdRecordingId, firstRecordingId, secondRecordingId),
            step = "[album-reorder] recordings should be returned in the new order",
        )

        E2eAssert.status(
            state.api.put(
                path = "/api/albums/$albumId/recordings/reorder",
                json = mapOf(
                    "recordingIds" to listOf(thirdRecordingId, firstRecordingId, secondRecordingId),
                ),
            ),
            204,
            "[album-reorder] reorder with same order should stay idempotent",
        )

        E2eAssert.status(
            state.api.put(
                path = "/api/albums/${Long.MAX_VALUE}/recordings/reorder",
                json = mapOf("recordingIds" to listOf(firstRecordingId)),
            ),
            404,
            "[album-reorder] nonexistent album should return not found",
        )

        E2eAssert.status(
            state.api.put(
                path = "/api/albums/$albumId/recordings/reorder",
                json = mapOf(
                    "recordingIds" to listOf(firstRecordingId, firstRecordingId, secondRecordingId),
                ),
            ),
            400,
            "[album-reorder] duplicate recording ids should fail",
        )

        E2eAssert.status(
            state.api.put(
                path = "/api/albums/$albumId/recordings/reorder",
                json = mapOf(
                    "recordingIds" to listOf(firstRecordingId, secondRecordingId),
                ),
            ),
            400,
            "[album-reorder] missing recording id should fail",
        )

        E2eAssert.status(
            state.api.put(
                path = "/api/albums/$albumId/recordings/reorder",
                json = mapOf(
                    "recordingIds" to listOf(
                        firstRecordingId,
                        secondRecordingId,
                        thirdRecordingId,
                        unrelatedRecordingId,
                    ),
                ),
            ),
            400,
            "[album-reorder] extra recording id should fail",
        )
    }

    @Test
    @Order(9)
    fun `playlist reorder should update sort order and validate input`() {
        val state = bootstrapAdminSession(baseUrl())
        val playlistOrderData = preparePlaylistOrderData(state)
        val unrelatedRecordingId = ensurePreparedData(state).recordingId
        val suffix = suffix()

        val owner = createAccountByAdmin(
            state = state,
            name = "playlist-reorder-owner-$suffix",
            email = "playlist-reorder-owner-$suffix@example.invalid",
            password = "playlist-reorder-owner-$suffix-password",
        )

        val ownerApi = loginAsAccount(owner.email, owner.password)

        val createPlaylistResponse = ownerApi.post(
            path = "/api/playlists",
            json = mapOf(
                "name" to "playlist-reorder-$suffix",
                "comment" to "playlist-reorder-comment-$suffix",
            ),
        )
        E2eAssert.status(createPlaylistResponse, 201, "[playlist-reorder] create should succeed")
        val playlistId = readIdFromObject(
            responseBody = createPlaylistResponse.body(),
            pointer = "/id",
            step = "[playlist-reorder] created playlist should contain id",
        )

        E2eAssert.status(
            ownerApi.put("/api/playlists/$playlistId/recordings/${playlistOrderData.firstRecordingId}"),
            204,
            "[playlist-reorder] add first recording should succeed",
        )
        E2eAssert.status(
            ownerApi.put("/api/playlists/$playlistId/recordings/${playlistOrderData.secondRecordingId}"),
            204,
            "[playlist-reorder] add second recording should succeed",
        )

        val initialDetail = ownerApi.get("/api/playlists/$playlistId")
        E2eAssert.status(initialDetail, 200, "[playlist-reorder] initial detail should succeed")
        assertRecordingOrder(
            responseBody = initialDetail.body(),
            expectedRecordingIds = listOf(
                playlistOrderData.firstRecordingId,
                playlistOrderData.secondRecordingId,
            ),
            step = "[playlist-reorder] initial insertion order",
        )

        val reorderResponse = ownerApi.put(
            path = "/api/playlists/$playlistId/recordings/reorder",
            json = mapOf(
                "recordingIds" to listOf(
                    playlistOrderData.secondRecordingId,
                    playlistOrderData.firstRecordingId,
                ),
            ),
        )
        E2eAssert.status(reorderResponse, 204, "[playlist-reorder] reorder should succeed")

        val afterReorderDetail = ownerApi.get("/api/playlists/$playlistId")
        E2eAssert.status(afterReorderDetail, 200, "[playlist-reorder] detail after reorder should succeed")
        assertRecordingOrder(
            responseBody = afterReorderDetail.body(),
            expectedRecordingIds = listOf(
                playlistOrderData.secondRecordingId,
                playlistOrderData.firstRecordingId,
            ),
            step = "[playlist-reorder] recordings should follow new order",
        )

        E2eAssert.status(
            ownerApi.put(
                path = "/api/playlists/$playlistId/recordings/reorder",
                json = mapOf(
                    "recordingIds" to listOf(
                        playlistOrderData.secondRecordingId,
                        playlistOrderData.firstRecordingId,
                    ),
                ),
            ),
            204,
            "[playlist-reorder] same-order reorder should stay idempotent",
        )

        E2eAssert.status(
            ownerApi.put(
                path = "/api/playlists/${Long.MAX_VALUE}/recordings/reorder",
                json = mapOf("recordingIds" to listOf(playlistOrderData.firstRecordingId)),
            ),
            400,
            "[playlist-reorder] nonexistent playlist should fail",
        )

        E2eAssert.status(
            ownerApi.put(
                path = "/api/playlists/$playlistId/recordings/reorder",
                json = mapOf(
                    "recordingIds" to listOf(
                        playlistOrderData.firstRecordingId,
                        playlistOrderData.firstRecordingId,
                    ),
                ),
            ),
            400,
            "[playlist-reorder] duplicate recording ids should fail",
        )

        E2eAssert.status(
            ownerApi.put(
                path = "/api/playlists/$playlistId/recordings/reorder",
                json = mapOf("recordingIds" to listOf(playlistOrderData.firstRecordingId)),
            ),
            400,
            "[playlist-reorder] missing recording id should fail",
        )

        E2eAssert.status(
            ownerApi.put(
                path = "/api/playlists/$playlistId/recordings/reorder",
                json = mapOf(
                    "recordingIds" to listOf(
                        playlistOrderData.firstRecordingId,
                        playlistOrderData.secondRecordingId,
                        unrelatedRecordingId,
                    ),
                ),
            ),
            400,
            "[playlist-reorder] extra recording id should fail",
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
        val token = E2eJson.mapper.readTree(loginResponse.body()).path("token").asText()
        api.setAuthToken(token)
        return api
    }

    private fun createPlaylistForAdmin(state: E2eAdminSession, name: String, comment: String, step: String): Long {
        val response = state.api.post(
            path = "/api/playlists",
            json = mapOf(
                "name" to name,
                "comment" to comment,
            ),
        )
        E2eAssert.status(response, 201, "$step should succeed")
        return readIdFromObject(response.body(), "/id", "$step should expose playlist id")
    }

    private fun addRecordingToPlaylist(state: E2eAdminSession, playlistId: Long, recordingId: Long, step: String) {
        val response = state.api.put("/api/playlists/$playlistId/recordings/$recordingId")
        E2eAssert.status(response, 204, "$step should succeed")
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

        val firstAlbumTitle = albums.first().path("title").asText()
        if (firstAlbumTitle.isBlank()) {
            return null
        }

        return PreparedData(
            recordingId = firstRecordingId,
            albumId = firstAlbumId.longValue(),
            albumTitle = firstAlbumTitle,
        )
    }

    private fun executeScanAndPrepareData(state: E2eAdminSession): PreparedData {
        val requestBody = scanRequestBody(state)
        val fixture = prepareScanFixture(state.runtime.scanWorkspace)
        val baselineStats = fetchTaskStats(state, "[prepare] baseline task stats before submit")
        val baselinePending = taskCount(baselineStats, "METADATA_PARSE", "PENDING")
        val baselineCompleted = taskCount(baselineStats, "METADATA_PARSE", "COMPLETED")
        val baselineFailed = taskCount(baselineStats, "METADATA_PARSE", "FAILED")

        val submitResponse = state.api.post(
            path = "/api/task/scan",
            json = requestBody,
        )
        E2eAssert.status(submitResponse, 202, "[prepare] submit scan task should return accepted")
        awaitScanTaskFinished(
            state = state,
            baselinePending = baselinePending,
            baselineCompleted = baselineCompleted,
            baselineFailed = baselineFailed,
        )

        val worksResponse = state.api.get(
            path = "/api/works",
            query = mapOf("pageIndex" to 0, "pageSize" to 200),
        )
        E2eAssert.status(worksResponse, 200, "[prepare] list works after scan should succeed")
        val works = pageRows(worksResponse.body(), "[prepare] works after scan")
        assertTrue(works.isNotEmpty(), "[prepare] expected works after scan")
        val recordingId = extractFirstRecordingId(works.first(), "[prepare] first work after scan should include recording")

        val albumSearchResponse = state.api.get(
            path = "/api/albums/search",
            query = mapOf("name" to fixture.albumTitle),
        )
        E2eAssert.status(albumSearchResponse, 200, "[prepare] album search after scan should succeed")
        val albums = E2eJson.mapper.readTree(albumSearchResponse.body())
        assertTrue(albums.isArray && albums.size() > 0, "[prepare] expected albums after scan search")
        val albumId = readIdFromNode(albums.first().path("id"), "[prepare] first album after scan search should include id")

        return PreparedData(
            recordingId = recordingId,
            albumId = albumId,
            albumTitle = fixture.albumTitle,
        )
    }

    private fun preparePlaylistOrderData(state: E2eAdminSession): PlaylistOrderData {
        val suffix = suffix()
        val fixtureRoot = state.runtime.scanWorkspace.resolve("playlist-order-$suffix")
        Files.createDirectories(fixtureRoot)

        val firstTitle = "playlist-order-a-$suffix"
        val secondTitle = "playlist-order-b-$suffix"

        SyntheticAudioFixture.generateOne(
            outputDir = fixtureRoot,
            fileName = "playlist-order-a.mp3",
            metadata = AudioFixtureMetadata(
                title = firstTitle,
                artist = "playlist-order-artist-$suffix",
                album = "playlist-order-album-$suffix",
                comment = "playlist-order-fixture-$suffix",
            ),
        )
        SyntheticAudioFixture.generateOne(
            outputDir = fixtureRoot,
            fileName = "playlist-order-b.mp3",
            metadata = AudioFixtureMetadata(
                title = secondTitle,
                artist = "playlist-order-artist-$suffix",
                album = "playlist-order-album-$suffix",
                comment = "playlist-order-fixture-$suffix",
            ),
        )

        submitScanAndAwaitCompletion(state, expectedTaskCount = 2)

        return PlaylistOrderData(
            firstRecordingId = findSingleRecordingIdByWorkTitle(
                state = state,
                workTitle = firstTitle,
                step = "[playlists] first playlist-order work",
            ),
            secondRecordingId = findSingleRecordingIdByWorkTitle(
                state = state,
                workTitle = secondTitle,
                step = "[playlists] second playlist-order work",
            ),
        )
    }

    private fun prepareRecordingMergeData(state: E2eAdminSession): RecordingMergeData {
        val baseData = ensurePreparedData(state)
        val suffix = suffix()

        val targetWorkId = fetchRecordingWorkId(
            state = state,
            recordingId = baseData.recordingId,
            step = "[recording-merge] target recording work id",
        )

        val sourceAlbumId = insertAlbum(
            title = "recording-merge-album-source-$suffix",
            comment = "recording-merge-source-album-$suffix",
        )
        val sourceRecordingId = insertRecording(
            workId = targetWorkId,
            title = "recording-merge-source-$suffix",
            comment = "recording-merge-source-comment-$suffix",
        )
        insertAlbumRecording(albumId = sourceAlbumId, recordingId = sourceRecordingId, sortOrder = 0)

        return RecordingMergeData(
            targetRecordingId = baseData.recordingId,
            sourceAlbumId = sourceAlbumId,
            sourceRecordingId = sourceRecordingId,
        )
    }

    private fun fetchRecordingWorkId(state: E2eAdminSession, recordingId: Long, step: String): Long {
        val response = state.api.get("/api/recordings/$recordingId")
        E2eAssert.status(response, 200, "$step should succeed")
        return readIdFromObject(
            responseBody = response.body(),
            pointer = "/work/id",
            step = "$step should expose work id",
        )
    }

    private fun insertAlbum(title: String, comment: String): Long {
        val params = MapSqlParameterSource()
            .addValue("title", title)
            .addValue("comment", comment)
        return jdbc.queryForObject(
            """
                INSERT INTO album(title, kind, release_date, comment, cover_id)
                VALUES (:title, 'CD', NULL, :comment, NULL)
                RETURNING id
            """.trimIndent(),
            params,
            Long::class.java,
        ) ?: fail("[recording-merge] failed to insert album fixture")
    }

    private fun insertRecording(workId: Long, title: String, comment: String): Long {
        val params = MapSqlParameterSource()
            .addValue("workId", workId)
            .addValue("title", title)
            .addValue("comment", comment)
        return jdbc.queryForObject(
            """
                INSERT INTO recording(work_id, kind, label, title, comment, duration_ms, default_in_work, cover_id)
                VALUES (:workId, 'CD', 'CD', :title, :comment, 0, false, NULL)
                RETURNING id
            """.trimIndent(),
            params,
            Long::class.java,
        ) ?: fail("[recording-merge] failed to insert recording fixture")
    }

    private fun insertAlbumRecording(albumId: Long, recordingId: Long, sortOrder: Int) {
        val params = MapSqlParameterSource()
            .addValue("albumId", albumId)
            .addValue("recordingId", recordingId)
            .addValue("sortOrder", sortOrder)
        val affectedRows = jdbc.update(
            """
                INSERT INTO album_recording_mapping(album_id, recording_id, sort_order)
                VALUES (:albumId, :recordingId, :sortOrder)
            """.trimIndent(),
            params,
        )
        assertEquals(1, affectedRows, "[recording-merge] album-recording fixture insert should affect one row")
    }

    private fun submitScanAndAwaitCompletion(state: E2eAdminSession, expectedTaskCount: Int) {
        val baselineStats = fetchTaskStats(state, "[scan-helper] baseline task stats")
        val baselinePending = taskCount(baselineStats, "METADATA_PARSE", "PENDING")
        val baselineCompleted = taskCount(baselineStats, "METADATA_PARSE", "COMPLETED")
        val baselineFailed = taskCount(baselineStats, "METADATA_PARSE", "FAILED")

        val submitResponse = state.api.post(
            path = "/api/task/scan",
            json = scanRequestBody(state),
        )
        E2eAssert.status(submitResponse, 202, "[scan-helper] submit scan task should return accepted")

        val deadline = System.currentTimeMillis() + scanWaitTimeoutMillis()
        var lastStatsBody = "[]"

        while (System.currentTimeMillis() <= deadline) {
            val statsRows = fetchTaskStats(state, "[scan-helper] task stats")
            val pending = taskCount(statsRows, "METADATA_PARSE", "PENDING")
            val completed = taskCount(statsRows, "METADATA_PARSE", "COMPLETED")
            val failed = taskCount(statsRows, "METADATA_PARSE", "FAILED")
            val terminalDelta = (completed - baselineCompleted) + (failed - baselineFailed)
            lastStatsBody = statsRows.joinToString(prefix = "[", postfix = "]") { it.toString() }

            if (pending <= baselinePending && terminalDelta >= expectedTaskCount) {
                assertEquals(
                    0L,
                    failed - baselineFailed,
                    "[scan-helper] scan should not introduce failed tasks, last=$lastStatsBody",
                )
                return
            }

            Thread.sleep(POLL_INTERVAL_MILLIS)
        }

        fail("[scan-helper] scan task did not finish within timeout ${scanWaitTimeoutMillis()} ms, last=$lastStatsBody")
    }

    private fun awaitScanTaskFinished(
        state: E2eAdminSession,
        baselinePending: Long,
        baselineCompleted: Long,
        baselineFailed: Long,
    ) {
        val deadline = System.currentTimeMillis() + scanWaitTimeoutMillis()
        var observedPending = false

        while (System.currentTimeMillis() <= deadline) {
            val statsRows = fetchTaskStats(state, "[prepare] scan task stats")
            val pending = taskCount(statsRows, "METADATA_PARSE", "PENDING")
            val completed = taskCount(statsRows, "METADATA_PARSE", "COMPLETED")
            val failed = taskCount(statsRows, "METADATA_PARSE", "FAILED")

            if (pending > baselinePending) {
                observedPending = true
            }
            if (pending <= baselinePending && (completed > baselineCompleted || failed > baselineFailed)) {
                assertTrue(observedPending || completed > baselineCompleted, "[prepare] scan task stats should advance before finishing")
                return
            }
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }

        fail("[prepare] scan task did not finish within timeout ${scanWaitTimeoutMillis()} ms")
    }

    private fun fetchTaskStats(state: E2eAdminSession, step: String): List<JsonNode> {
        val response = state.api.get("/api/task/logs")
        E2eAssert.status(response, 200, "$step should succeed")
        val root = E2eJson.mapper.readTree(response.body())
        assertTrue(root.isArray, "$step expected root array")
        return root.toList()
    }

    private fun taskCount(rows: List<JsonNode>, taskType: String, status: String): Long {
        return rows.firstOrNull { row ->
            row.path("taskType").asText() == taskType && row.path("status").asText() == status
        }?.path("count")?.longValue() ?: 0L
    }

    private fun prepareScanFixture(scanWorkspace: Path): FixtureInfo {
        val suffix = suffix()
        val albumTitle = "e2e-playlist-album-$suffix"
        val fixtureRoot = scanWorkspace.resolve("account-playlist-content-$suffix")
        java.nio.file.Files.createDirectories(fixtureRoot)
        repeat(SCAN_FIXTURE_FILE_COUNT) { index ->
            SyntheticAudioFixture.generateOne(
                outputDir = fixtureRoot,
                fileName = "fixture-${index.toString().padStart(4, '0')}.mp3",
                metadata = AudioFixtureMetadata(
                    title = "e2e-playlist-track-$index-$suffix",
                    artist = "e2e-playlist-artist",
                    album = albumTitle,
                    comment = "e2e-playlist-fixture-$suffix",
                ),
            )
        }
        return FixtureInfo(albumTitle = albumTitle)
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

    private fun assertRecordingOrder(responseBody: String, expectedRecordingIds: List<Long>, step: String) {
        assertEquals(expectedRecordingIds, recordingIdsFromDetail(responseBody, step), step)
    }

    private fun recordingIdsFromDetail(responseBody: String, step: String): List<Long> {
        return recordingIdsFromNode(
            container = E2eJson.mapper.readTree(responseBody),
            pointer = "recordings",
            step = step,
        )
    }

    private fun recordingIdsFromNode(container: JsonNode, pointer: String, step: String): List<Long> {
        val recordingsNode = container.path(pointer)
        if (!recordingsNode.isArray) {
            fail("$step expected recordings array")
        }
        return recordingsNode.mapIndexed { index, recording ->
            val idNode = recording.path("id")
            if (!idNode.isIntegralNumber) {
                fail("$step expected integral recording id at index=$index")
            }
            idNode.longValue()
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

    private fun findSingleRecordingIdByWorkTitle(state: E2eAdminSession, workTitle: String, step: String): Long {
        val response = state.api.get(
            path = "/api/works/search",
            query = mapOf("name" to workTitle),
        )
        E2eAssert.status(response, 200, "$step search should succeed")
        val workNode = findWorkNodeByTitle(
            responseBody = response.body(),
            expectedTitle = workTitle,
            step = step,
        )
        val recordingIds = recordingIdsFromNode(workNode, "recordings", step)
        assertEquals(1, recordingIds.size, "$step should expose exactly one recording")
        return recordingIds.first()
    }

    private fun findWorkNodeByTitle(responseBody: String, expectedTitle: String, step: String): JsonNode {
        val root = E2eJson.mapper.readTree(responseBody)
        assertTrue(root.isArray, "$step expected array response")
        return root.firstOrNull { node -> node.path("title").asText() == expectedTitle }
            ?: fail("$step expected work title=$expectedTitle")
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
        val albumTitle: String,
    )

    private data class FixtureInfo(
        val albumTitle: String,
    )

    private data class PlaylistOrderData(
        val firstRecordingId: Long,
        val secondRecordingId: Long,
    )

    private data class RecordingMergeData(
        val targetRecordingId: Long,
        val sourceAlbumId: Long,
        val sourceRecordingId: Long,
    )

    companion object {
        private const val SCAN_WAIT_TIMEOUT_ENV = "E2E_SCAN_WAIT_TIMEOUT_MILLIS"
        private const val DEFAULT_SCAN_WAIT_TIMEOUT_MILLIS = 120_000L
        private const val POLL_INTERVAL_MILLIS = 150L
        private const val SCAN_FIXTURE_FILE_COUNT = 16

        @JvmStatic
        @DynamicPropertySource
        fun registerDatasource(registry: DynamicPropertyRegistry) {
            E2eRuntime.registerDatasource(registry)
        }
    }
}
