/**
 * 任务表单 Schema（JSON Schema Draft 2020-12 白名单子集）的前端解析与校验。
 *
 * 客户端校验只用于交互反馈，服务端在创建 submission 前执行权威校验。
 */

export type SchemaFieldType = 'string' | 'integer' | 'number' | 'boolean'

export type SchemaField = {
    name: string
    type: SchemaFieldType
    title: string
    description?: string
    required: boolean
    default?: string | number | boolean
    enum?: Array<string | number>
    minLength?: number
    maxLength?: number
    minimum?: number
    maximum?: number
    exclusiveMinimum?: number
    exclusiveMaximum?: number
    multipleOf?: number
}

const FIELD_TYPES: readonly SchemaFieldType[] = ['string', 'integer', 'number', 'boolean']

const isRecord = (value: unknown): value is Record<string, unknown> =>
    typeof value === 'object' && value !== null && !Array.isArray(value)

const isFieldType = (value: unknown): value is SchemaFieldType =>
    typeof value === 'string' && (FIELD_TYPES as readonly string[]).includes(value)

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
            default:
                typeof defaultValue === 'string' ||
                typeof defaultValue === 'number' ||
                typeof defaultValue === 'boolean'
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
        })
    }
    return fields
}

/** 表单值以字符串保存（checkbox 为 boolean），提交时按字段类型转换 */
export type SchemaFormValues = Record<string, string | boolean>

export const initialFormValues = (fields: SchemaField[]): SchemaFormValues => {
    const values: SchemaFormValues = {}
    for (const field of fields) {
        if (field.type === 'boolean') {
            values[field.name] = field.default === true
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

export const isFieldValid = (field: SchemaField, raw: string | boolean | undefined): boolean => {
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

export const isFormValid = (fields: SchemaField[], values: SchemaFormValues): boolean =>
    fields.every((field) => isFieldValid(field, values[field.name]))

/** 转换为提交用 params；未填写的可选字段不写入（服务端不自动填 default） */
export const toSubmissionParams = (
    fields: SchemaField[],
    values: SchemaFormValues,
): Record<string, unknown> => {
    const params: Record<string, unknown> = {}
    for (const field of fields) {
        const raw = values[field.name]
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
