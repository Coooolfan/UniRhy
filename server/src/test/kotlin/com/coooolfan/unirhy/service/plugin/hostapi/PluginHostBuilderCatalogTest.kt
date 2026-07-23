package com.coooolfan.unirhy.service.plugin.hostapi

import com.coooolfan.unirhy.service.AlbumService
import com.coooolfan.unirhy.service.ArtistService
import com.coooolfan.unirhy.service.PlaylistService
import com.coooolfan.unirhy.service.RecordingService
import com.coooolfan.unirhy.service.SystemConfigService
import com.coooolfan.unirhy.service.WorkService
import com.coooolfan.unirhy.service.plugin.WasmExecutionContext
import com.coooolfan.unirhy.service.storage.FileSystemStorageService
import com.coooolfan.unirhy.service.storage.OssStorageService
import com.coooolfan.unirhy.service.storage.StorageNodeObjectService
import com.coooolfan.unirhy.service.task.AsyncTaskService
import com.coooolfan.unirhy.service.task.TaskDefinitionService
import com.coooolfan.unirhy.service.task.TaskStatisticsService
import com.coooolfan.unirhy.service.task.common.AsyncTaskStore
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.mockito.Mockito.mock
import run.endive.runtime.Instance
import tools.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginHostBuilderCatalogTest {

    @Test
    fun `real host builders match the declared catalog`() {
        val objectMapper = mock(ObjectMapper::class.java)
        val instanceRef = mockDependency<() -> Instance>()
        val callExecutor = mock(PluginHostCallExecutor::class.java)
        val storageObjects = mock(StorageNodeObjectService::class.java)

        val groups = listOf(
            buildDefaultHostFunctions(
                storageObjects = storageObjects,
                objectMapper = objectMapper,
                instanceRef = instanceRef,
                callExecutor = callExecutor,
            ),
            buildArtistHostFunctions(
                artistService = mock(ArtistService::class.java),
                objectMapper = objectMapper,
                instanceRef = instanceRef,
                callExecutor = callExecutor,
            ),
            buildWorkHostFunctions(
                workService = mock(WorkService::class.java),
                objectMapper = objectMapper,
                instanceRef = instanceRef,
                callExecutor = callExecutor,
            ),
            buildRecordingHostFunctions(
                recordingService = mock(RecordingService::class.java),
                objectMapper = objectMapper,
                instanceRef = instanceRef,
                callExecutor = callExecutor,
            ),
            buildAlbumHostFunctions(
                albumService = mock(AlbumService::class.java),
                objectMapper = objectMapper,
                instanceRef = instanceRef,
                callExecutor = callExecutor,
            ),
            buildMediaHostFunctions(
                mediaService = mock(PluginMediaService::class.java),
                storageObjects = storageObjects,
                objectMapper = objectMapper,
                instanceRef = instanceRef,
                callExecutor = callExecutor,
            ),
            buildStorageHostFunctions(
                fileSystemStorageService = mock(FileSystemStorageService::class.java),
                ossStorageService = mock(OssStorageService::class.java),
                systemConfigService = mock(SystemConfigService::class.java),
                storageObjects = storageObjects,
                objectMapper = objectMapper,
                instanceRef = instanceRef,
                callExecutor = callExecutor,
            ),
            buildPlaylistHostFunctions(
                playlistService = mock(PlaylistService::class.java),
                objectMapper = objectMapper,
                instanceRef = instanceRef,
                callExecutor = callExecutor,
            ),
            buildTaskHostFunctions(
                taskDefinitionService = mock(TaskDefinitionService::class.java),
                asyncTaskService = mock(AsyncTaskService::class.java),
                taskStatisticsService = mock(TaskStatisticsService::class.java),
                asyncTaskStore = mock(AsyncTaskStore::class.java),
                pluginId = "com.example.test",
                executionContext = WasmExecutionContext(1L, "TEST_TASK"),
                objectMapper = objectMapper,
                instanceRef = instanceRef,
                callExecutor = callExecutor,
            ),
            buildPluginDataHostFunctions(
                pluginId = "com.example.test",
                pluginDataService = mock(PluginDataService::class.java),
                objectMapper = objectMapper,
                instanceRef = instanceRef,
                callExecutor = callExecutor,
            ),
            buildMetadataHostFunctions(
                sql = mock(KSqlClient::class.java),
                isPluginLoaded = mockDependency<(String) -> Boolean>(),
                objectMapper = objectMapper,
                instanceRef = instanceRef,
                callExecutor = callExecutor,
            ),
        )

        assertEquals(11, groups.size)
        val functions = groups.flatten()
        validatePluginHostFunctions(functions)
        assertEquals(PLUGIN_HOST_FUNCTION_NAMES, functions.map { it.name() }.toSet())
    }

    private inline fun <reified T : Any> mockDependency(): T = mock(T::class.java)
}
