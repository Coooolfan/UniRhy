/**
 * 任务表单 Schema（JSON Schema Draft 2020-12 白名单子集）的前端解析与校验。
 *
 * 客户端校验只用于交互反馈，服务端在创建 submission 前执行权威校验。
 */

export type SchemaFieldType = 'string' | 'integer' | 'number' | 'boolean' | 'array'
export type SchemaArrayItemType = 'string' | 'integer' | 'number'

export type SchemaArrayItems = {
    type: SchemaArrayItemType
}

export type SchemaField = {
    name: string
    type: SchemaFieldType
    title: string
    description?: string
    required: boolean
    writeOnly: boolean
    default?: string | number | boolean | Array<string | number>
    enum?: Array<string | number>
    minLength?: number
    maxLength?: number
    minimum?: number
    maximum?: number
    exclusiveMinimum?: number
    exclusiveMaximum?: number
    multipleOf?: number
    items?: SchemaArrayItems
    minItems?: number
    maxItems?: number
}

const FIELD_TYPES: readonly SchemaFieldType[] = ['string', 'integer', 'number', 'boolean', 'array']
const ARRAY_ITEM_TYPES: readonly SchemaArrayItemType[] = ['string', 'integer', 'number']

const isRecord = (value: unknown): value is Record<string, unknown> =>
    typeof value === 'object' && value !== null && !Array.isArray(value)

const isFieldType = (value: unknown): value is SchemaFieldType =>
    typeof value === 'string' && (FIELD_TYPES as readonly string[]).includes(value)

const isArrayItemType = (value: unknown): value is SchemaArrayItemType =>
    typeof value === 'string' && (ARRAY_ITEM_TYPES as readonly string[]).includes(value)

const asNumber = (value: unknown): number | undefined =>
    typeof value === 'number' ? value : undefined

const asString = (value: unknown): string | undefined =>
    typeof value === 'string' ? value : undefined

export const parseFormDefinition = (formDefinition: unknown): SchemaField[] => {
    if (!isRecord(formDefinition)) return []
    const schema = formDefinition.schema
    if (!isRecord(schema)) return []
    const properties = isRecord(schema.properties) ? schema.properties : {}
    const required = new Set(
        Array.isArray(schema.required) ? schema.required.filter((v) => typeof v === 'string') : [],
    )
    const order = Array.isArray(formDefinition.order)
        ? formDefinition.order.filter((v): v is string => typeof v === 'string')
        : Object.keys(properties)

    const fields: SchemaField[] = []
    for (const name of order) {
        const fieldSchema = properties[name]
        if (!isRecord(fieldSchema) || !isFieldType(fieldSchema.type)) continue
        const items = isRecord(fieldSchema.items) ? fieldSchema.items : undefined
        if (fieldSchema.type === 'array' && !isArrayItemType(items?.type)) continue
        const defaultValue = fieldSchema.default
        const enumValues = Array.isArray(fieldSchema.enum)
            ? fieldSchema.enum.filter(
                  (v): v is string | number => typeof v === 'string' || typeof v === 'number',
              )
            : undefined
        fields.push({
            name,
            type: fieldSchema.type,
            title: asString(fieldSchema.title) ?? name,
            description: asString(fieldSchema.description),
            required: required.has(name),
            writeOnly: fieldSchema.writeOnly === true,
            default:
                typeof defaultValue === 'string' ||
                typeof defaultValue === 'number' ||
                typeof defaultValue === 'boolean' ||
                (Array.isArray(defaultValue) &&
                    defaultValue.every(
                        (value) => typeof value === 'string' || typeof value === 'number',
                    ))
                    ? defaultValue
                    : undefined,
            enum: enumValues,
            minLength: asNumber(fieldSchema.minLength),
            maxLength: asNumber(fieldSchema.maxLength),
            minimum: asNumber(fieldSchema.minimum),
            maximum: asNumber(fieldSchema.maximum),
            exclusiveMinimum: asNumber(fieldSchema.exclusiveMinimum),
            exclusiveMaximum: asNumber(fieldSchema.exclusiveMaximum),
            multipleOf: asNumber(fieldSchema.multipleOf),
            items:
                fieldSchema.type === 'array' && isArrayItemType(items?.type)
                    ? { type: items.type }
                    : undefined,
            minItems: asNumber(fieldSchema.minItems),
            maxItems: asNumber(fieldSchema.maxItems),
        })
    }
    return fields
}

/** 表单值以字符串保存（checkbox 为 boolean，数组为字符串列表），提交时按字段类型转换 */
export type SchemaFormValue = string | boolean | string[]
export type SchemaFormValues = Record<string, SchemaFormValue>

export const initialFormValues = (
    fields: SchemaField[],
    source: Record<string, unknown> = {},
): SchemaFormValues => {
    const values: SchemaFormValues = {}
    for (const field of fields) {
        const sourceValue = source[field.name]
        if (field.type === 'array') {
            let arrayValue: unknown[] = []
            if (Array.isArray(sourceValue)) {
                arrayValue = sourceValue
            } else if (Array.isArray(field.default)) {
                arrayValue = field.default
            }
            const normalizedValue = arrayValue
                .filter((value) => typeof value === 'string' || typeof value === 'number')
                .map(String)
            values[field.name] = normalizedValue.length > 0 ? normalizedValue : ['']
        } else if (field.type === 'boolean') {
            values[field.name] =
                typeof sourceValue === 'boolean' ? sourceValue : field.default === true
        } else if (typeof sourceValue === 'string' || typeof sourceValue === 'number') {
            values[field.name] = String(sourceValue)
        } else if (field.default === undefined) {
            values[field.name] = ''
        } else {
            values[field.name] = String(field.default)
        }
    }
    return values
}

const parseNumericValue = (field: SchemaField, text: string): number | undefined => {
    const parsed = Number(text)
    if (Number.isNaN(parsed)) return undefined
    if (field.type === 'integer' && !Number.isInteger(parsed)) return undefined
    return parsed
}

const isNumericValueValid = (field: SchemaField, value: number): boolean => {
    if (field.minimum !== undefined && value < field.minimum) return false
    if (field.maximum !== undefined && value > field.maximum) return false
    if (field.exclusiveMinimum !== undefined && value <= field.exclusiveMinimum) return false
    if (field.exclusiveMaximum !== undefined && value >= field.exclusiveMaximum) return false
    return true
}

export const isFieldValid = (field: SchemaField, raw: SchemaFormValue | undefined): boolean => {
    if (field.type === 'array') {
        if (!Array.isArray(raw)) return !field.required
        const items = raw.filter((item) => item !== '')
        if (raw.length > 1 && items.length !== raw.length) return false
        if (field.minItems !== undefined && items.length < field.minItems) return false
        if (field.maxItems !== undefined && items.length > field.maxItems) return false
        const itemType = field.items?.type
        if (!itemType) return false
        return items.every((item) => {
            if (itemType === 'string') return true
            const value = Number(item)
            if (Number.isNaN(value)) return false
            return itemType !== 'integer' || Number.isInteger(value)
        })
    }
    if (field.type === 'boolean') {
        return true
    }
    const text = typeof raw === 'string' ? raw : ''
    if (text === '') {
        return !field.required
    }
    if (field.type === 'string') {
        if (field.minLength !== undefined && text.length < field.minLength) return false
        if (field.maxLength !== undefined && text.length > field.maxLength) return false
        if (field.enum && !field.enum.some((candidate) => candidate === text)) return false
        return true
    }
    const value = parseNumericValue(field, text)
    if (value === undefined) return false
    if (!isNumericValueValid(field, value)) return false
    if (field.enum && !field.enum.some((candidate) => candidate === value)) return false
    return true
}

export const isFormValid = (
    fields: SchemaField[],
    values: SchemaFormValues,
    configuredSecretFields: ReadonlySet<string> = new Set(),
): boolean =>
    fields.every((field) => {
        const raw = values[field.name]
        if (field.writeOnly && configuredSecretFields.has(field.name) && raw === '') return true
        return isFieldValid(field, raw)
    })

/** 转换为提交用 params；未填写的可选字段不写入（服务端不自动填 default） */
export const toSubmissionParams = (
    fields: SchemaField[],
    values: SchemaFormValues,
): Record<string, unknown> => {
    const params: Record<string, unknown> = {}
    for (const field of fields) {
        const raw = values[field.name]
        if (field.type === 'array') {
            const items = Array.isArray(raw) ? raw.filter((item) => item !== '') : []
            if (items.length === 0 && !field.required) continue
            if (field.items?.type === 'string') {
                params[field.name] = items
            } else if (field.items) {
                const converted = items.map(Number)
                if (converted.every((value) => !Number.isNaN(value))) {
                    params[field.name] = converted
                }
            }
            continue
        }
        if (field.type === 'boolean') {
            params[field.name] = raw === true || raw === 'true'
            continue
        }
        const text = typeof raw === 'string' ? raw : ''
        if (text === '') continue
        if (field.type === 'string') {
            params[field.name] = text
        } else {
            const value = parseNumericValue(field, text)
            if (value !== undefined) {
                params[field.name] = value
            }
        }
    }
    return params
}
