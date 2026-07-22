package com.coooolfan.unirhy.service.plugin.hostapi

import com.coooolfan.unirhy.service.storage.StorageNodeObjectService
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import run.endive.runtime.ExportFunction
import run.endive.runtime.Instance
import run.endive.runtime.Memory
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginHostMediaTest {
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `asset list keeps recording only request compatible`() {
        val mediaService = mock(PluginMediaService::class.java)
        `when`(mediaService.listAssets(123, null)).thenReturn(emptyList())

        val response = invokeAssetList("""{"recordingId":123}""", mediaService)

        assertTrue(response.path("ok").booleanValue())
        verify(mediaService).listAssets(123, null)
    }

    @Test
    fun `asset list passes recording and media file filters together`() {
        val mediaService = mock(PluginMediaService::class.java)
        `when`(mediaService.listAssets(123, 789)).thenReturn(emptyList())

        val response = invokeAssetList("""{"recordingId":123,"mediaFileId":789}""", mediaService)

        assertTrue(response.path("ok").booleanValue())
        verify(mediaService).listAssets(123, 789)
    }

    @Test
    fun `asset list accepts media file only request`() {
        val mediaService = mock(PluginMediaService::class.java)
        `when`(mediaService.listAssets(null, 789)).thenReturn(emptyList())

        val response = invokeAssetList("""{"mediaFileId":789}""", mediaService)

        assertTrue(response.path("ok").booleanValue())
        verify(mediaService).listAssets(null, 789)
    }

    @Test
    fun `asset list rejects request without filters`() {
        val mediaService = mock(PluginMediaService::class.java)

        val response = invokeAssetList("{}", mediaService)

        assertFalse(response.path("ok").booleanValue())
        assertEquals("INVALID_ARGUMENT", response.path("error").path("code").stringValue())
        verifyNoInteractions(mediaService)
    }

    private fun invokeAssetList(request: String, mediaService: PluginMediaService): ObjectNode {
        val instance = mock(Instance::class.java)
        val memory = mock(Memory::class.java)
        val alloc = ExportFunction { longArrayOf(1024L) }
        val requestBytes = request.toByteArray()
        `when`(instance.memory()).thenReturn(memory)
        `when`(memory.readBytes(16, requestBytes.size)).thenReturn(requestBytes)
        `when`(instance.export("alloc")).thenReturn(alloc)

        val function = buildMediaHostFunctions(
            mediaService = mediaService,
            storageObjects = mock(StorageNodeObjectService::class.java),
            objectMapper = objectMapper,
            instanceRef = { instance },
        ).single { it.name() == "host_asset_list" }
        function.handle().apply(instance, 16L, requestBytes.size.toLong())

        val bytes = ArgumentCaptor.forClass(ByteArray::class.java)
        verify(memory).write(eq(1024), bytes.capture())
        return objectMapper.readTree(bytes.value) as ObjectNode
    }
}
