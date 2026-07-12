package com.coooolfan.unirhy.service.plugin

import com.coooolfan.unirhy.service.task.common.TaskType
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PluginManifestTest {

    @Test
    fun `loads valid manifest`() {
        val path = createTempFile(suffix = ".yml")
        path.writeText(
            """
                id: com.example.task-plugin
                version: 1.0.0
                runtime:
                  type: wasm
                  target: wasm32-unknown-unknown
                  wasi: none
                  abi: unirhy-wasm-abi-v1
                tasks:
                  - type: METADATA_PARSE
                    extension: metadata.scan@1
                permissions:
                  network:
                    allow:
                      - example.com
                      - api.example.com
            """.trimIndent()
        )

        val manifest = assertNotNull(loadPluginManifest(path))
        assertEquals("com.example.task-plugin", manifest.id)
        assertEquals("1.0.0", manifest.version)
        assertEquals("wasm", manifest.runtime.type)
        assertEquals(UNIRHY_WASM_ABI_V1, manifest.runtime.abi)
        assertEquals(1, manifest.tasks.size)
        assertEquals(TaskType.METADATA_PARSE, manifest.tasks.single().type)
        assertEquals("metadata.scan@1", manifest.tasks.single().extension)
        assertEquals(setOf("example.com", "api.example.com"), manifest.networkAllowHosts())
    }

    @Test
    fun `rejects unsupported abi`() {
        val path = createTempFile(suffix = ".yml")
        path.writeText(
            """
                id: x
                version: 1
                runtime:
                  type: wasm
                  abi: unirhy-wasm-abi-v999
                tasks:
                  - type: METADATA_PARSE
                    extension: metadata.scan@1
            """.trimIndent()
        )
        assertNull(loadPluginManifest(path))
    }

    @Test
    fun `rejects non-wasm runtime`() {
        val path = createTempFile(suffix = ".yml")
        path.writeText(
            """
                id: x
                version: 1
                runtime:
                  type: jvm
                  abi: unirhy-wasm-abi-v1
                tasks:
                  - type: METADATA_PARSE
                    extension: metadata.scan@1
            """.trimIndent()
        )
        assertNull(loadPluginManifest(path))
    }

    @Test
    fun `rejects empty tasks`() {
        val path = createTempFile(suffix = ".yml")
        path.writeText(
            """
                id: x
                version: 1
                runtime:
                  type: wasm
                  abi: unirhy-wasm-abi-v1
                tasks: []
            """.trimIndent()
        )
        assertNull(loadPluginManifest(path))
    }
}
