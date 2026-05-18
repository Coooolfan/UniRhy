package com.coooolfan.unirhy.service.plugin

import com.dylibso.chicory.runtime.HostFunction
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.wasm.types.FunctionType
import com.dylibso.chicory.wasm.types.ValType
import org.junit.jupiter.api.condition.EnabledIf
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertNotNull

private val MOCK_PLUGIN_DIR: String =
    System.getenv("UNIRHY_PLUGIN_BASE_DIR")?.let { "$it/artist-normalization-mock" }
        ?: "../../../unirhy-plugins/artist-normalization-mock"

class WasmPluginIntegrationTest {

    @Test
    @EnabledIf("mockPluginAvailable")
    fun `plugin run() completes without error against stub host functions`() {
        val manifest = assertNotNull(loadPluginManifest(Path.of(MOCK_PLUGIN_DIR, "plugin.yml")))
        val wasmBytes = Files.readAllBytes(Path.of(MOCK_PLUGIN_DIR, "plugin.wasm"))
        val plugin = assertNotNull(WasmPlugin.load(manifest, wasmBytes) { m, instanceRef ->
            buildDefaultHostFunctions(m, instanceRef) + buildStubArtistHostFunctions(instanceRef)
        })
        // stub host functions return empty pages → run() should exit cleanly with 0 merges
        plugin.run("{}".toByteArray())
    }

    @Suppress("unused")
    fun mockPluginAvailable(): Boolean {
        val wasmPath = Path.of(MOCK_PLUGIN_DIR, "plugin.wasm")
        return Files.isRegularFile(wasmPath) && Files.size(wasmPath) > 1024
    }
}

private fun buildStubArtistHostFunctions(instanceRef: () -> Instance): List<HostFunction> {
    val stubListArtists = HostFunction(
        "env",
        "host_list_artists",
        FunctionType.of(listOf(ValType.I32, ValType.I32), listOf(ValType.I64)),
    ) { _: Instance, _: LongArray ->
        // 返回空数组 "[]" 写入插件内存
        val json = "[]".toByteArray()
        val instance = instanceRef()
        val ptr = instance.export("alloc").apply(json.size.toLong())[0].toInt()
        instance.memory().write(ptr, json)
        longArrayOf((ptr.toLong() shl 32) or json.size.toLong())
    }

    val stubMergeArtists = HostFunction(
        "env",
        "host_merge_artists",
        FunctionType.of(listOf(ValType.I32, ValType.I32), emptyList()),
    ) { _: Instance, _: LongArray ->
        longArrayOf()
    }

    return listOf(stubListArtists, stubMergeArtists)
}
