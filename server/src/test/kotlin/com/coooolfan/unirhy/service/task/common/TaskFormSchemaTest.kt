package com.coooolfan.unirhy.service.task.common

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TaskFormSchemaTest {

    private val mapper = JsonMapper.shared()

    @Test
    fun `accepts homogeneous string integer and number arrays`() {
        val definition = definition(
            """
                "tags": {
                  "type": "array",
                  "title": "Tags",
                  "items": { "type": "string" },
                  "minItems": 1
                },
                "ids": {
                  "type": "array",
                  "title": "IDs",
                  "items": { "type": "integer" }
                },
                "weights": {
                  "type": "array",
                  "title": "Weights",
                  "items": { "type": "number" },
                  "maxItems": 2
                }
            """.trimIndent(),
            required = "[\"tags\"]",
            order = "[\"tags\", \"ids\", \"weights\"]",
        )

        TaskFormSchema.validateFormDefinition(definition)
        assertTrue(
            TaskFormSchema.validateParams(
                definition,
                mapper.readTree("""{"tags":["ambient"],"ids":[1,2],"weights":[0.5,2]}"""),
            ).isEmpty(),
        )
    }

    @Test
    fun `rejects mixed and invalid array items`() {
        val definition = definition(
            """
                "tags": {
                  "type": "array",
                  "title": "Tags",
                  "items": { "type": "string" },
                  "minItems": 2
                }
            """.trimIndent(),
            required = "[\"tags\"]",
            order = "[\"tags\"]",
        )
        TaskFormSchema.validateFormDefinition(definition)

        val errors = TaskFormSchema.validateParams(
            definition,
            mapper.readTree("""{"tags":["ambient",2]}"""),
        )
        assertContains(errors, "field 'tags' item 1 must be of type string")
        assertContains(
            TaskFormSchema.validateParams(definition, mapper.readTree("""{"tags":[]}""")),
            "field 'tags' must contain at least 2 items",
        )
    }

    @Test
    fun `requires a supported homogeneous item type`() {
        val missingItems = definition(
            """"tags": { "type": "array", "title": "Tags" }""",
            order = "[\"tags\"]",
        )
        val booleanItems = definition(
            """"flags": { "type": "array", "title": "Flags", "items": { "type": "boolean" } }""",
            order = "[\"flags\"]",
        )

        assertFailsWith<IllegalArgumentException> { TaskFormSchema.validateFormDefinition(missingItems) }
        assertFailsWith<IllegalArgumentException> { TaskFormSchema.validateFormDefinition(booleanItems) }
    }

    @Test
    fun `does not expose arrays in plugin configuration`() {
        val definition = definition(
            """"tags": { "type": "array", "title": "Tags", "items": { "type": "string" } }""",
            order = "[\"tags\"]",
        )

        assertFailsWith<IllegalArgumentException> { TaskFormSchema.validateConfigDefinition(definition) }
    }

    private fun definition(
        properties: String,
        required: String = "[]",
        order: String,
    ): JsonNode = mapper.readTree(
        """
            {
              "schema": {
                "type": "object",
                "properties": { $properties },
                "required": $required,
                "additionalProperties": false
              },
              "order": $order
            }
        """.trimIndent(),
    )
}
