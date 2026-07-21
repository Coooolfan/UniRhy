package com.coooolfan.unirhy.service.task.common

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal

/**
 * 任务表单参数契约：JSON Schema Draft 2020-12 的服务端白名单子集。
 *
 * - 表单定义为 `{ "schema": <根 Schema>, "order": [字段名...] }`
 * - 根 Schema 必须声明 `type: object`、`properties`、`required` 与 `additionalProperties: false`
 * - 字段仅支持 string / integer / number / boolean 标量类型
 * - 未列入白名单的关键字直接拒绝，不静默忽略
 */
object TaskFormSchema {

    const val DRAFT_2020_12 = "https://json-schema.org/draft/2020-12/schema"

    private val ROOT_ALLOWED_KEYS =
        setOf("\$schema", "type", "title", "description", "properties", "required", "additionalProperties")
    private val FIELD_COMMON_KEYS = setOf("type", "title", "description", "default", "enum")
    private val FIELD_STRING_KEYS = FIELD_COMMON_KEYS + setOf("minLength", "maxLength")
    private val FIELD_NUMERIC_KEYS =
        FIELD_COMMON_KEYS + setOf("minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf")
    private val FIELD_TYPES = setOf("string", "integer", "number", "boolean")

    /** 未声明 form 时使用的空表单定义：不接受任何字段 */
    fun emptyFormDefinition(): JsonNode = JsonMapper.shared().readTree(
        """{"schema":{"type":"object","properties":{},"required":[],"additionalProperties":false},"order":[]}"""
    )

    /**
     * 校验完整表单定义 `{schema, order}`，非法时抛出 [IllegalArgumentException]。
     */
    fun validateFormDefinition(formDefinition: JsonNode) {
        require(formDefinition.isObject) { "form definition must be an object" }
        val unknownKeys = formDefinition.propertyNames() - setOf("schema", "order")
        require(unknownKeys.isEmpty()) { "form definition contains unknown keys: $unknownKeys" }

        val schema = formDefinition.get("schema")
            ?: throw IllegalArgumentException("form definition missing 'schema'")
        val order = formDefinition.get("order")
            ?: throw IllegalArgumentException("form definition missing 'order'")
        validateSchema(schema)
        validateOrder(order, schema)
    }

    private fun validateSchema(schema: JsonNode) {
        require(schema.isObject) { "form.schema must be an object" }
        val unknownKeys = schema.propertyNames() - ROOT_ALLOWED_KEYS
        require(unknownKeys.isEmpty()) { "form.schema contains unsupported keywords: $unknownKeys" }

        schema.get("\$schema")?.let {
            require(it.isString && it.stringValue() == DRAFT_2020_12) {
                "form.schema \$schema only accepts $DRAFT_2020_12"
            }
        }
        require(schema.get("type")?.isString == true && schema.get("type").stringValue() == "object") {
            "form.schema must declare type: object"
        }
        schema.get("title")?.let { require(it.isString) { "form.schema title must be a string" } }
        schema.get("description")?.let { require(it.isString) { "form.schema description must be a string" } }
        require(schema.get("additionalProperties")?.isBoolean == true && !schema.get("additionalProperties").booleanValue()) {
            "form.schema must declare additionalProperties: false"
        }

        val properties = schema.get("properties")
            ?: throw IllegalArgumentException("form.schema must declare properties")
        require(properties.isObject) { "form.schema properties must be an object" }
        for ((name, fieldSchema) in properties.properties()) {
            validateFieldSchema(name, fieldSchema)
        }

        val required = schema.get("required")
            ?: throw IllegalArgumentException("form.schema must declare required")
        require(required.isArray) { "form.schema required must be an array" }
        val requiredNames = mutableSetOf<String>()
        for (item in required) {
            require(item.isString) { "form.schema required must only contain strings" }
            val name = item.stringValue()
            require(properties.has(name)) { "form.schema required references unknown field: $name" }
            require(requiredNames.add(name)) { "form.schema required contains duplicate field: $name" }
        }
    }

    private fun validateFieldSchema(name: String, fieldSchema: JsonNode) {
        require(fieldSchema.isObject) { "field '$name' schema must be an object" }
        val type = fieldSchema.get("type")
        require(type != null && type.isString && type.stringValue() in FIELD_TYPES) {
            "field '$name' must declare type as one of $FIELD_TYPES"
        }
        val typeName = type.stringValue()
        val allowedKeys = when (typeName) {
            "string" -> FIELD_STRING_KEYS
            "integer", "number" -> FIELD_NUMERIC_KEYS
            else -> FIELD_COMMON_KEYS
        }
        val unknownKeys = fieldSchema.propertyNames() - allowedKeys
        require(unknownKeys.isEmpty()) { "field '$name' contains unsupported keywords: $unknownKeys" }

        require(fieldSchema.get("title")?.isString == true) { "field '$name' must declare a string title" }
        fieldSchema.get("description")?.let { require(it.isString) { "field '$name' description must be a string" } }

        fieldSchema.get("default")?.let {
            require(matchesType(it, typeName)) { "field '$name' default does not match declared type" }
        }
        fieldSchema.get("enum")?.let { enum ->
            require(enum.isArray && enum.size() > 0) { "field '$name' enum must be a non-empty array" }
            for (value in enum) {
                require(matchesType(value, typeName)) { "field '$name' enum value does not match declared type" }
            }
        }

        when (typeName) {
            "string" -> {
                fieldSchema.get("minLength")?.let {
                    require(it.canConvertToExactIntegral() && it.intValue() >= 0) { "field '$name' minLength must be a non-negative integer" }
                }
                fieldSchema.get("maxLength")?.let {
                    require(it.canConvertToExactIntegral() && it.intValue() >= 0) { "field '$name' maxLength must be a non-negative integer" }
                }
            }

            "integer", "number" -> {
                for (key in listOf("minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum")) {
                    fieldSchema.get(key)?.let { require(it.isNumber) { "field '$name' $key must be a number" } }
                }
                fieldSchema.get("multipleOf")?.let {
                    require(it.isNumber && it.decimalValue() > BigDecimal.ZERO) { "field '$name' multipleOf must be a positive number" }
                }
            }
        }
    }

    private fun validateOrder(order: JsonNode, schema: JsonNode) {
        require(order.isArray) { "form.order must be an array" }
        val orderNames = mutableSetOf<String>()
        for (item in order) {
            require(item.isString) { "form.order must only contain strings" }
            require(orderNames.add(item.stringValue())) { "form.order contains duplicate field: ${item.stringValue()}" }
        }
        val propertyNames = schema.get("properties").propertyNames().toSet()
        require(orderNames == propertyNames) {
            "form.order must contain exactly the fields declared in properties"
        }
    }

    /**
     * 按表单定义对提交参数执行权威校验，返回全部校验错误；空列表表示通过。
     *
     * `default` 保持 annotation 语义：服务端不写入默认值，required 字段必须由调用方提交。
     */
    fun validateParams(formDefinition: JsonNode, params: JsonNode): List<String> {
        val errors = mutableListOf<String>()
        if (!params.isObject) {
            return listOf("params must be a JSON object")
        }
        val schema = formDefinition.get("schema")
        val properties = schema.get("properties")

        for (name in params.propertyNames()) {
            if (!properties.has(name)) {
                errors += "unknown field: $name"
            }
        }
        for (item in schema.get("required")) {
            if (!params.has(item.stringValue())) {
                errors += "missing required field: ${item.stringValue()}"
            }
        }
        for ((name, fieldSchema) in properties.properties()) {
            val value = params.get(name) ?: continue
            errors += validateFieldValue(name, fieldSchema, value)
        }
        return errors
    }

    private fun validateFieldValue(name: String, fieldSchema: JsonNode, value: JsonNode): List<String> {
        val typeName = fieldSchema.get("type").stringValue()
        if (!matchesType(value, typeName)) {
            return listOf("field '$name' must be of type $typeName")
        }
        val errors = mutableListOf<String>()
        fieldSchema.get("enum")?.let { enum ->
            if (enum.none { it == value }) {
                errors += "field '$name' must be one of the enum values"
            }
        }
        when (typeName) {
            "string" -> {
                val length = value.stringValue().length
                fieldSchema.get("minLength")?.let {
                    if (length < it.intValue()) errors += "field '$name' length must be >= ${it.intValue()}"
                }
                fieldSchema.get("maxLength")?.let {
                    if (length > it.intValue()) errors += "field '$name' length must be <= ${it.intValue()}"
                }
            }

            "integer", "number" -> {
                val decimal = value.decimalValue()
                fieldSchema.get("minimum")?.let {
                    if (decimal < it.decimalValue()) errors += "field '$name' must be >= ${it.decimalValue()}"
                }
                fieldSchema.get("maximum")?.let {
                    if (decimal > it.decimalValue()) errors += "field '$name' must be <= ${it.decimalValue()}"
                }
                fieldSchema.get("exclusiveMinimum")?.let {
                    if (decimal <= it.decimalValue()) errors += "field '$name' must be > ${it.decimalValue()}"
                }
                fieldSchema.get("exclusiveMaximum")?.let {
                    if (decimal >= it.decimalValue()) errors += "field '$name' must be < ${it.decimalValue()}"
                }
                fieldSchema.get("multipleOf")?.let {
                    if (decimal.remainder(it.decimalValue()).compareTo(BigDecimal.ZERO) != 0) {
                        errors += "field '$name' must be a multiple of ${it.decimalValue()}"
                    }
                }
            }
        }
        return errors
    }

    private fun matchesType(value: JsonNode, typeName: String): Boolean = when (typeName) {
        "string" -> value.isString
        "boolean" -> value.isBoolean
        "integer" -> value.isNumber && value.canConvertToExactIntegral()
        "number" -> value.isNumber
        else -> false
    }
}
