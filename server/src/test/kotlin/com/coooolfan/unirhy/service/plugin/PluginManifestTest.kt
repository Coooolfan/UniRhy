package com.coooolfan.unirhy.service.plugin

import com.coooolfan.unirhy.service.task.common.TaskKey
import tools.jackson.databind.ObjectMapper
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.module.kotlin.kotlinModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PluginManifestTest {

    private val yamlMapper: ObjectMapper = YAMLMapper.builder().addModule(kotlinModule()).build()

    private fun parse(yaml: String): PluginManifest =
        yamlMapper.readValue(yaml, PluginManifest::class.java)

    @Test
    fun `loads valid manifest`() {
        val manifest = parse(
            """
                id: com.example.task-plugin
                version: 1.0.0
                runtime:
                  type: wasm
                  abi: unirhy-wasm-abi-v1
                task:
                  type: FETCH_COVER
                  concurrency: 4
                form:
                  schema:
                    type: object
                    properties:
                      keyword:
                        type: string
                        title: 搜索关键字
                        minLength: 1
                    required:
                      - keyword
                    additionalProperties: false
                  order:
                    - keyword
            """.trimIndent()
        )

        assertEquals("com.example.task-plugin", manifest.id)
        assertEquals("1.0.0", manifest.version)
        assertEquals("wasm", manifest.runtime.type)
        assertEquals(UNIRHY_WASM_ABI_V1, manifest.runtime.abi)
        assertEquals("FETCH_COVER", manifest.task.type)
        assertEquals(4, manifest.task.concurrency)
        assertEquals(TaskKey("com.example.task-plugin", "FETCH_COVER"), manifest.taskKey)
        assertNull(manifest.validate())
    }

    @Test
    fun `manifest without form uses empty form definition`() {
        val manifest = parse(
            """
                id: com.example.simple
                version: 1.0.0
                runtime:
                  type: wasm
                  abi: unirhy-wasm-abi-v1
                task:
                  type: SIMPLE
                  concurrency: 1
            """.trimIndent()
        )

        assertNull(manifest.validate())
        val formDefinition = manifest.formDefinition()
        assertEquals("object", formDefinition.get("schema").get("type").stringValue())
        assertEquals(0, formDefinition.get("schema").get("properties").size())
        assertEquals(0, formDefinition.get("order").size())
    }

    @Test
    fun `rejects reserved namespace`() {
        val manifest = parse(
            """
                id: app.unirhy.evil
                version: 1.0.0
                runtime:
                  type: wasm
                  abi: unirhy-wasm-abi-v1
                task:
                  type: EVIL
                  concurrency: 1
            """.trimIndent()
        )
        assertNotNull(manifest.validate())
    }

    @Test
    fun `rejects invalid task type`() {
        val manifest = parse(
            """
                id: com.example.simple
                version: 1.0.0
                runtime:
                  type: wasm
                  abi: unirhy-wasm-abi-v1
                task:
                  type: lower_case
                  concurrency: 1
            """.trimIndent()
        )
        assertNotNull(manifest.validate())
    }

    @Test
    fun `rejects non-positive concurrency`() {
        val manifest = parse(
            """
                id: com.example.simple
                version: 1.0.0
                runtime:
                  type: wasm
                  abi: unirhy-wasm-abi-v1
                task:
                  type: SIMPLE
                  concurrency: 0
            """.trimIndent()
        )
        assertNotNull(manifest.validate())
    }

    @Test
    fun `rejects schema with unknown keyword`() {
        val manifest = parse(
            """
                id: com.example.simple
                version: 1.0.0
                runtime:
                  type: wasm
                  abi: unirhy-wasm-abi-v1
                task:
                  type: SIMPLE
                  concurrency: 1
                form:
                  schema:
                    type: object
                    properties:
                      keyword:
                        type: string
                        title: 关键字
                        pattern: ".*"
                    required: []
                    additionalProperties: false
                  order:
                    - keyword
            """.trimIndent()
        )
        assertNotNull(manifest.validate())
    }

    @Test
    fun `rejects order mismatching properties`() {
        val manifest = parse(
            """
                id: com.example.simple
                version: 1.0.0
                runtime:
                  type: wasm
                  abi: unirhy-wasm-abi-v1
                task:
                  type: SIMPLE
                  concurrency: 1
                form:
                  schema:
                    type: object
                    properties:
                      keyword:
                        type: string
                        title: 关键字
                    required: []
                    additionalProperties: false
                  order: []
            """.trimIndent()
        )
        assertNotNull(manifest.validate())
    }
}
