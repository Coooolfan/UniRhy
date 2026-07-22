package com.coooolfan.unirhy.service.plugin.hostapi

import com.coooolfan.unirhy.model.Artist
import com.coooolfan.unirhy.model.Recording
import com.coooolfan.unirhy.model.SystemConfig
import com.coooolfan.unirhy.model.storage.FileProviderFileSystem
import com.coooolfan.unirhy.model.storage.FileProviderOss
import com.coooolfan.unirhy.model.storage.FileProviderType
import com.coooolfan.unirhy.service.RecordingService
import com.coooolfan.unirhy.service.SystemConfigService
import com.coooolfan.unirhy.service.storage.FileSystemStorageNode
import com.coooolfan.unirhy.service.storage.FileSystemStorageService
import com.coooolfan.unirhy.service.storage.OssStorageNode
import com.coooolfan.unirhy.service.storage.OssStorageService
import com.coooolfan.unirhy.service.storage.StorageNodeObjectService
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.eq
import org.mockito.Answers
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.babyfish.jimmer.jackson.v3.ImmutableModuleV3
import run.endive.runtime.ExportFunction
import run.endive.runtime.Instance
import run.endive.runtime.Memory
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.jacksonMapperBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginHostDomainCreationTest {
    private val objectMapper = jacksonMapperBuilder().addModule(ImmutableModuleV3()).build()

    @Test
    fun `default writable FS node is returned without storage details`() {
        val provider = FileProviderFileSystem {
            id = 11
            name = "Local Music"
            parentPath = "/var/lib/unirhy"
            readonly = false
        }
        val config = SystemConfig {
            id = 0
            fsProvider = provider
            ossProvider = null
        }
        val configService = mockSystemConfigService(config)
        val storageObjects = mock(StorageNodeObjectService::class.java)
        `when`(storageObjects.resolve(FileProviderType.FILE_SYSTEM, 11))
            .thenReturn(FileSystemStorageNode(provider))

        val response = invokeStorageFunction(
            "host_storage_default_write_node_get",
            "{}",
            configService = configService,
            storageObjects = storageObjects,
        )

        assertTrue(response.path("ok").booleanValue())
        assertEquals("FS", response.path("data").path("type").stringValue())
        assertEquals(11, response.path("data").path("id").longValue())
        assertEquals("Local Music", response.path("data").path("name").stringValue())
        assertFalse(response.path("data").has("parentPath"))
    }

    @Test
    fun `default writable OSS node is returned`() {
        val provider = FileProviderOss {
            id = 12
            name = "Remote Music"
            host = "https://oss.example.invalid"
            bucket = "music"
            parentPath = "library"
            accessKey = "access"
            secretKey = "secret"
            readonly = false
        }
        val config = SystemConfig {
            id = 0
            fsProvider = null
            ossProvider = provider
        }
        val configService = mockSystemConfigService(config)
        val storageObjects = mock(StorageNodeObjectService::class.java)
        `when`(storageObjects.resolve(FileProviderType.OSS, 12))
            .thenReturn(OssStorageNode(provider))

        val response = invokeStorageFunction(
            "host_storage_default_write_node_get",
            "{}",
            configService = configService,
            storageObjects = storageObjects,
        )

        assertTrue(response.path("ok").booleanValue())
        assertEquals("OSS", response.path("data").path("type").stringValue())
        assertEquals(12, response.path("data").path("id").longValue())
        assertEquals("Remote Music", response.path("data").path("name").stringValue())
        assertFalse(response.path("data").has("host"))
        assertFalse(response.path("data").has("bucket"))
    }

    @Test
    fun `default readonly node returns conflict`() {
        val provider = FileProviderFileSystem {
            id = 13
            name = "Read only"
            parentPath = "/var/lib/unirhy"
            readonly = true
        }
        val config = SystemConfig {
            id = 0
            fsProvider = provider
            ossProvider = null
        }
        val configService = mockSystemConfigService(config)
        val storageObjects = mock(StorageNodeObjectService::class.java)
        `when`(storageObjects.resolve(FileProviderType.FILE_SYSTEM, 13))
            .thenReturn(FileSystemStorageNode(provider))

        val response = invokeStorageFunction(
            "host_storage_default_write_node_get",
            "{}",
            configService = configService,
            storageObjects = storageObjects,
        )

        assertFalse(response.path("ok").booleanValue())
        assertEquals("CONFLICT", response.path("error").path("code").stringValue())
    }

    @Test
    fun `missing default node returns not found`() {
        val config = SystemConfig {
            id = 0
            fsProvider = null
            ossProvider = null
        }
        val configService = mockSystemConfigService(config)
        val storageObjects = mock(StorageNodeObjectService::class.java)

        val response = invokeStorageFunction(
            "host_storage_default_write_node_get",
            "{}",
            configService = configService,
            storageObjects = storageObjects,
        )

        assertFalse(response.path("ok").booleanValue())
        assertEquals("NOT_FOUND", response.path("error").path("code").stringValue())
        verifyNoInteractions(storageObjects)
    }

    @Test
    fun `media location lookup resolves FS and OSS providers`() {
        val fsProvider = FileProviderFileSystem {
            id = 21
            name = "fs"
            parentPath = "/tmp"
            readonly = false
        }
        val ossProvider = FileProviderOss {
            id = 22
            name = "oss"
            host = "https://oss.example.invalid"
            bucket = "music"
            parentPath = null
            accessKey = "access"
            secretKey = "secret"
            readonly = false
        }
        val fsNode = FileSystemStorageNode(fsProvider)
        val ossNode = OssStorageNode(ossProvider)
        val storageObjects = mock(StorageNodeObjectService::class.java)
        `when`(storageObjects.resolve(FileProviderType.FILE_SYSTEM, 21)).thenReturn(fsNode)
        `when`(storageObjects.resolve(FileProviderType.OSS, 22)).thenReturn(ossNode)
        val mediaService = mock(PluginMediaService::class.java)
        val fsMedia = mediaView(101, "FS", 21)
        val ossMedia = mediaView(102, "OSS", 22)
        `when`(mediaService.getMediaFileByLocation(fsNode, "imports/fs.m4a")).thenReturn(fsMedia)
        `when`(mediaService.getMediaFileByLocation(ossNode, "imports/oss.m4a")).thenReturn(ossMedia)
        val instanceHolder = arrayOfNulls<Instance>(1)
        val functions = buildMediaHostFunctions(
            mediaService = mediaService,
            storageObjects = storageObjects,
            objectMapper = objectMapper,
            instanceRef = { instanceHolder[0] ?: error("instance is not initialized") },
        )

        val fsResponse = invokeFunction(
            functions.single { it.name() == "host_media_file_get_by_location" },
            """{"node":{"type":"FS","id":21},"objectKey":"imports/fs.m4a"}""",
            instanceHolder,
        )
        val ossResponse = invokeFunction(
            functions.single { it.name() == "host_media_file_get_by_location" },
            """{"node":{"type":"OSS","id":22},"objectKey":"imports/oss.m4a"}""",
            instanceHolder,
        )

        assertTrue(fsResponse.path("ok").booleanValue())
        assertEquals(101, fsResponse.path("data").path("id").longValue())
        assertEquals("FS", fsResponse.path("data").path("node").path("type").stringValue())
        assertTrue(ossResponse.path("ok").booleanValue())
        assertEquals(102, ossResponse.path("data").path("id").longValue())
        assertEquals("OSS", ossResponse.path("data").path("node").path("type").stringValue())
        verify(mediaService).getMediaFileByLocation(fsNode, "imports/fs.m4a")
        verify(mediaService).getMediaFileByLocation(ossNode, "imports/oss.m4a")
    }

    @Test
    fun `recording create applies defaults and removes duplicate ids and labels`() {
        val result = Recording {
            id = 301
            workId = 7
            artists = listOf(Artist { id = 41 })
            label = listOf("label")
            title = null
            comment = ""
            durationMs = 0
            defaultInWork = false
            coverId = null
        }
        val recordingService = mock(RecordingService::class.java) { invocation ->
            if (invocation.method.name == "createRecording") {
                result
            } else {
                Answers.RETURNS_DEFAULTS.answer(invocation)
            }
        }
        val instanceHolder = arrayOfNulls<Instance>(1)
        val function = buildRecordingHostFunctions(
            recordingService = recordingService,
            objectMapper = objectMapper,
            instanceRef = { instanceHolder[0] ?: error("instance is not initialized") },
        ).single { it.name() == "host_recording_create" }

        val response = invokeFunction(
            function,
            """{"workId":7,"artistIds":[41,41],"label":["label","label"],"durationMs":0}""",
            instanceHolder,
        )

        assertTrue(response.path("ok").booleanValue())
        val invocation = mockingDetails(recordingService).invocations
            .single { it.method.name == "createRecording" }
        val input = invocation.arguments[0] as Recording
        assertEquals(7, input.work.id)
        assertEquals(listOf(41L), input.artists.map { it.id })
        assertEquals(listOf("label"), input.label)
        assertEquals("", input.comment)
        assertFalse(input.defaultInWork)
    }

    private fun invokeStorageFunction(
        name: String,
        request: String,
        configService: SystemConfigService,
        storageObjects: StorageNodeObjectService,
    ): ObjectNode {
        val instanceHolder = arrayOfNulls<Instance>(1)
        val function = buildStorageHostFunctions(
            fileSystemStorageService = mock(FileSystemStorageService::class.java),
            ossStorageService = mock(OssStorageService::class.java),
            systemConfigService = configService,
            storageObjects = storageObjects,
            objectMapper = objectMapper,
            instanceRef = { instanceHolder[0] ?: error("instance is not initialized") },
        ).single { it.name() == name }
        return invokeFunction(function, request, instanceHolder)
    }

    private fun invokeFunction(
        function: run.endive.runtime.HostFunction,
        request: String,
        instanceHolder: Array<Instance?>,
    ): ObjectNode {
        val instance = mock(Instance::class.java)
        val memory = mock(Memory::class.java)
        val alloc = ExportFunction { longArrayOf(1024L) }
        val requestBytes = request.toByteArray()
        `when`(instance.memory()).thenReturn(memory)
        `when`(memory.readBytes(16, requestBytes.size)).thenReturn(requestBytes)
        `when`(instance.export("alloc")).thenReturn(alloc)
        instanceHolder[0] = instance
        function.handle().apply(instance, 16L, requestBytes.size.toLong())
        val bytes = ArgumentCaptor.forClass(ByteArray::class.java)
        verify(memory).write(eq(1024), bytes.capture())
        return objectMapper.readTree(bytes.value) as ObjectNode
    }

    private fun mediaView(id: Long, type: String, nodeId: Long): HostMediaFileView = HostMediaFileView(
        id = id,
        objectKey = "imports/$type.m4a",
        mimeType = "audio/mp4",
        size = 12,
        width = null,
        height = null,
        node = HostStorageNodeView(type, nodeId),
    )

    private fun mockSystemConfigService(config: SystemConfig): SystemConfigService =
        mock(SystemConfigService::class.java) { invocation ->
            if (invocation.method.name == "get") {
                config
            } else {
                Answers.RETURNS_DEFAULTS.answer(invocation)
            }
        }
}
