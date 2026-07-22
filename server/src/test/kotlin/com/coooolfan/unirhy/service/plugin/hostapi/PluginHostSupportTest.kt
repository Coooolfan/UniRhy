package com.coooolfan.unirhy.service.plugin.hostapi

import com.coooolfan.unirhy.service.plugin.WasmPluginException
import run.endive.runtime.HostFunction
import run.endive.runtime.Instance
import run.endive.runtime.Memory
import run.endive.runtime.ExportFunction
import run.endive.wasm.types.FunctionType
import run.endive.wasm.types.ValType
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginHostSupportTest {
    private val objectMapper = jacksonObjectMapper()
    private val support = PluginHostSupport(objectMapper) { error("memory is not used by these tests") }

    @Test
    fun `catalog contains exactly the documented imports`() {
        assertEquals(63, PLUGIN_HOST_FUNCTION_NAMES.size)
        assertFalse("host_http_check" in PLUGIN_HOST_FUNCTION_NAMES)
        assertFalse("host_list_artist_ids" in PLUGIN_HOST_FUNCTION_NAMES)
        assertFalse("host_get_artists_by_ids" in PLUGIN_HOST_FUNCTION_NAMES)
        assertFalse("host_merge_artists" in PLUGIN_HOST_FUNCTION_NAMES)
        assertFalse("host_split_artist" in PLUGIN_HOST_FUNCTION_NAMES)
        validatePluginHostFunctions(PLUGIN_HOST_FUNCTION_NAMES.map(::dummyFunction))
    }

    @Test
    fun `pagination applies defaults and validates its range`() {
        assertEquals(HostPageRequest(0, 100), support.page(objectMapper.createObjectNode()))
        assertEquals(
            HostPageRequest(3, 1000),
            support.page(objectMapper.readTree("""{"pageIndex":3,"pageSize":1000}""") as ObjectNode),
        )

        val tooLarge = objectMapper.readTree("""{"pageSize":1001}""") as ObjectNode
        val ex = assertFailsWith<PluginHostException> { support.page(tooLarge) }
        assertEquals(PluginHostErrorCode.INVALID_ARGUMENT, ex.errorCode)
    }

    @Test
    fun `request field helpers preserve omitted and null values`() {
        val request = objectMapper.readTree("""{"id":42,"optional":null,"names":["a","b"]}""") as ObjectNode
        assertEquals(42L, request.requiredLong("id"))
        assertEquals(listOf("a", "b"), request.requiredTextList("names"))
        assertNull(request.optionalText("missing"))
        assertNull(request.optionalText("optional"))

        val ex = assertFailsWith<PluginHostException> { request.requiredText("missing") }
        assertEquals(PluginHostErrorCode.INVALID_ARGUMENT, ex.errorCode)
    }

    @Test
    fun `json function writes success and business error envelopes`() {
        val success = invokeJsonFunction("{}") { mapOf("value" to 7) }
        assertTrue(success.path("ok").booleanValue())
        assertEquals(7, success.path("data").path("value").intValue())

        val failure = invokeJsonFunction("{}") { invalidArgument("bad request") }
        assertFalse(failure.path("ok").booleanValue())
        assertEquals("INVALID_ARGUMENT", failure.path("error").path("code").stringValue())
        assertEquals("bad request", failure.path("error").path("message").stringValue())
    }

    @Test
    fun `invalid json remains a protocol trap`() {
        val instance = mock(Instance::class.java)
        val memory = mock(Memory::class.java)
        `when`(instance.memory()).thenReturn(memory)
        `when`(memory.readBytes(16, 1)).thenReturn(byteArrayOf('{'.code.toByte()))
        val function = PluginHostSupport(objectMapper) { instance }.jsonFunction("host_test") { null }

        assertFailsWith<WasmPluginException> {
            function.handle().apply(instance, 16L, 1L)
        }
    }

    private fun dummyFunction(name: String): HostFunction {
        val params = when (name) {
            "host_log" -> listOf(ValType.I32, ValType.I32, ValType.I32)
            "host_storage_object_write" -> listOf(ValType.I32, ValType.I32, ValType.I32, ValType.I32)
            else -> listOf(ValType.I32, ValType.I32)
        }
        val returns = if (name == "host_log") emptyList() else listOf(ValType.I64)
        return HostFunction("env", name, FunctionType.of(params, returns)) { _: Instance, _: LongArray ->
            if (returns.isEmpty()) longArrayOf() else longArrayOf(0L)
        }
    }

    private fun invokeJsonFunction(
        request: String,
        handler: (ObjectNode) -> Any?,
    ): ObjectNode {
        val instance = mock(Instance::class.java)
        val memory = mock(Memory::class.java)
        val alloc = ExportFunction { longArrayOf(1024L) }
        val requestBytes = request.toByteArray()
        `when`(instance.memory()).thenReturn(memory)
        `when`(memory.readBytes(16, requestBytes.size)).thenReturn(requestBytes)
        `when`(instance.export("alloc")).thenReturn(alloc)

        val function = PluginHostSupport(objectMapper) { instance }.jsonFunction("host_test", handler)
        function.handle().apply(instance, 16L, requestBytes.size.toLong())

        val bytes = ArgumentCaptor.forClass(ByteArray::class.java)
        verify(memory).write(eq(1024), bytes.capture())
        return objectMapper.readTree(bytes.value) as ObjectNode
    }
}
