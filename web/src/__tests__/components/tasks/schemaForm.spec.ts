import { describe, expect, it } from 'vitest'
import {
    initialFormValues,
    isFormValid,
    parseFormDefinition,
    toSubmissionParams,
} from '@/components/tasks/schemaForm'

const formDefinition = {
    schema: {
        type: 'object',
        properties: {
            tags: {
                type: 'array',
                title: 'Tags',
                items: { type: 'string' },
                minItems: 1,
                default: ['classical'],
            },
            ids: {
                type: 'array',
                title: 'IDs',
                items: { type: 'integer' },
                maxItems: 2,
            },
            weights: {
                type: 'array',
                title: 'Weights',
                items: { type: 'number' },
            },
        },
        required: ['tags'],
        additionalProperties: false,
    },
    order: ['tags', 'ids', 'weights'],
}

describe('schemaForm arrays', () => {
    it('parses homogeneous array declarations and defaults', () => {
        const fields = parseFormDefinition(formDefinition)

        expect(fields.map((field) => [field.name, field.items?.type])).toEqual([
            ['tags', 'string'],
            ['ids', 'integer'],
            ['weights', 'number'],
        ])
        expect(initialFormValues(fields)).toEqual({
            tags: ['classical'],
            ids: [''],
            weights: [''],
        })
    })

    it('validates array bounds and converts numeric items for submission', () => {
        const fields = parseFormDefinition(formDefinition)
        const values = {
            tags: ['ambient', 'instrumental'],
            ids: ['7', '9'],
            weights: ['0.5', '2'],
        }

        expect(isFormValid(fields, values)).toBe(true)
        expect(toSubmissionParams(fields, values)).toEqual({
            tags: ['ambient', 'instrumental'],
            ids: [7, 9],
            weights: [0.5, 2],
        })
        expect(isFormValid(fields, { ...values, tags: [] })).toBe(false)
        expect(isFormValid(fields, { ...values, ids: ['1', '2', '3'] })).toBe(false)
        expect(isFormValid(fields, { ...values, ids: ['1.5'] })).toBe(false)
    })

    it('omits empty optional arrays and submits an empty required array without minItems', () => {
        const fields = parseFormDefinition({
            schema: {
                type: 'object',
                properties: {
                    requiredValues: {
                        type: 'array',
                        title: 'Required values',
                        items: { type: 'string' },
                    },
                    optionalValues: {
                        type: 'array',
                        title: 'Optional values',
                        items: { type: 'string' },
                    },
                },
                required: ['requiredValues'],
                additionalProperties: false,
            },
            order: ['requiredValues', 'optionalValues'],
        })

        const values = initialFormValues(fields)
        expect(values).toEqual({ requiredValues: [''], optionalValues: [''] })
        expect(toSubmissionParams(fields, values)).toEqual({ requiredValues: [] })
    })
})
