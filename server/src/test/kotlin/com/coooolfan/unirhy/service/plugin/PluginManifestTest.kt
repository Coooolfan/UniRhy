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
                tasks:
                  - type: FETCH_COVER
                    concurrency: 4
                    userSubmittable: true
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
        assertEquals("FETCH_COVER", manifest.tasks.single().type)
        assertEquals(4, manifest.tasks.single().concurrency)
        assertEquals(listOf(TaskKey("com.example.task-plugin", "FETCH_COVER")), manifest.taskKeys())
        assertNull(manifest.validate())
    }

    @Test
    fun `loads entry task and worker task with independent forms`() {
        val manifest = parse(
            """
                id: com.example.two-stage
                version: 1.0.0
                runtime:
                  type: wasm
                  abi: unirhy-wasm-abi-v1
                tasks:
                  - type: IMPORT
                    concurrency: 2
                    userSubmittable: true
                    form:
                      schema:
                        type: object
                        properties:
                          url:
                            type: string
                            title: URL
                        required:
                          - url
                        additionalProperties: false
                      order:
                        - url
                  - type: IMPORT_ITEM
                    concurrency: 8
                    form:
                      schema:
                        type: object
                        properties:
                          itemId:
                            type: integer
                            title: 条目 ID
                        required:
                          - itemId
                        additionalProperties: false
                      order:
                        - itemId
            """.trimIndent()
        )

        assertNull(manifest.validate())
        val entry = manifest.tasks.first { it.userSubmittable }
        val worker = manifest.tasks.first { !it.userSubmittable }
        assertEquals("IMPORT", entry.type)
        assertEquals("IMPORT_ITEM", worker.type)
        assertEquals(
            setOf("url"),
            manifest.formDefinition(entry).path("schema").path("properties").propertyNames().toSet(),
        )
        assertEquals(
            setOf("itemId"),
            manifest.formDefinition(worker).path("schema").path("properties").propertyNames().toSet(),
        )
    }

    @Test
    fun `loads task form and plugin configuration independently`() {
        val manifest = parse(
            """
                id: com.example.configured
                version: 1.0.0
                runtime:
                  type: wasm
                  abi: unirhy-wasm-abi-v1
                tasks:
                  - type: IMPORT
                    concurrency: 1
                    userSubmittable: true
                    form:
                      schema:
                        type: object
                        properties:
                          url:
                            type: string
                            title: URL
                        required:
                          - url
                        additionalProperties: false
                      order:
                        - url
                config:
                  schema:
                    type: object
                    properties:
                      apiKey:
                        type: string
                        title: API Key
                        writeOnly: true
                    required:
                      - apiKey
                    additionalProperties: false
                  order:
                    - apiKey
            """.trimIndent()
        )

        assertNull(manifest.validate())
        assertEquals(
            setOf("url"),
            manifest.formDefinition(manifest.tasks.single()).path("schema").path("properties").propertyNames().toSet(),
        )
        assertEquals(
            setOf("apiKey"),
            manifest.configDefinition().path("schema").path("properties").propertyNames().toSet(),
        )
    }

    @Test
    fun `rejects writeOnly in submission form`() {
        val manifest = parse(
            """
                id: com.example.invalid-secret-form
                version: 1.0.0
                runtime:
                  type: wasm
                  abi: unirhy-wasm-abi-v1
                tasks:
                  - type: IMPORT
                    concurrency: 1
                    userSubmittable: true
                    form:
                      schema:
                        type: object
                        properties:
                          apiKey:
                            type: string
                            title: API Key
                            writeOnly: true
                        required: []
                        additionalProperties: false
                      order:
                        - apiKey
            """.trimIndent()
        )

        assertNotNull(manifest.validate())
    }

    @Test
    fun `rejects writeOnly on non-string configuration`() {
        val manifest = parse(
            """
                id: com.example.invalid-secret-config
                version: 1.0.0
                runtime:
                  type: wasm
                  abi: unirhy-wasm-abi-v1
                tasks:
                  - type: IMPORT
                    concurrency: 1
                    userSubmittable: true
                config:
                  schema:
                    type: object
                    properties:
                      retries:
                        type: integer
                        title: Retries
                        writeOnly: true
                    required: []
                    additionalProperties: false
                  order:
                    - retries
            """.trimIndent()
        )

        assertNotNull(manifest.validate())
    }

    @Test
    fun `task without form uses empty form definition`() {
        val manifest = parse(
            """
                id: com.example.simple
                version: 1.0.0
                runtime:
                  type: wasm
                  abi: unirhy-wasm-abi-v1
                tasks:
                  - type: SIMPLE
                    concurrency: 1
                    userSubmittable: true
            """.trimIndent()
        )

        assertNull(manifest.validate())
        val formDefinition = manifest.formDefinition(manifest.tasks.single())
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
                tasks:
                  - type: EVIL
                    concurrency: 1
                    userSubmittable: true
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
                tasks:
                  - type: lower_case
                    concurrency: 1
                    userSubmittable: true
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
                tasks:
                  - type: SIMPLE
                    concurrency: 0
                    userSubmittable: true
            """.trimIndent()
        )
        assertNotNull(manifest.validate())
    }

    @Test
    fun `rejects manifest without task`() {
        val manifest = parse(
            """
                id: com.example.empty
                version: 1.0.0
                runtime:
                  type: wasm
                  abi: unirhy-wasm-abi-v1
                tasks: []
            """.trimIndent()
        )
        assertNotNull(manifest.validate())
    }

    @Test
    fun `rejects manifest without user-submittable task`() {
        val manifest = parse(
            """
                id: com.example.no-entry
                version: 1.0.0
                runtime:
                  type: wasm
                  abi: unirhy-wasm-abi-v1
                tasks:
                  - type: WORKER_ONLY
                    concurrency: 1
            """.trimIndent()
        )
        assertNotNull(manifest.validate())
    }

    @Test
    fun `rejects duplicate task type`() {
        val manifest = parse(
            """
                id: com.example.duplicate
                version: 1.0.0
                runtime:
                  type: wasm
                  abi: unirhy-wasm-abi-v1
                tasks:
                  - type: IMPORT
                    concurrency: 1
                    userSubmittable: true
                  - type: IMPORT
                    concurrency: 2
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
                tasks:
                  - type: SIMPLE
                    concurrency: 1
                    userSubmittable: true
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
                tasks:
                  - type: SIMPLE
                    concurrency: 1
                    userSubmittable: true
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
