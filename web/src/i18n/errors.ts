import { i18n } from './index'
import { normalizeApiError } from '@/ApiInstance'

/**
 * 把任意错误解析为面向用户展示的文案。
 *
 * 优先按后端返回的 `family`/`code` 在 `errors.<family>.<code>` 下查找译文，
 * 保证同一个错误码在全站文案一致；查不到时回退到调用方提供的 fallback——
 * fallback 既可以是一个 i18n key（存在则翻译），也可以是调用方直接传入的
 * 字面量兜底文案（此时原样返回，兼容个别按业务场景自定义兜底语的调用点）；
 * 最后兜底为通用的“操作失败”文案。
 */
export const resolveErrorMessage = (error: unknown, fallback?: string): string => {
    const normalized = normalizeApiError(error)
    const family = typeof normalized.family === 'string' ? normalized.family : undefined
    const code = typeof normalized.code === 'string' ? normalized.code : undefined

    if (family && code) {
        const key = `errors.${family}.${code}`
        if (i18n.global.te(key)) {
            return i18n.global.t(key)
        }
    }

    if (fallback) {
        return i18n.global.te(fallback) ? i18n.global.t(fallback) : fallback
    }

    if (typeof normalized.message === 'string' && normalized.message.length > 0) {
        return normalized.message
    }

    return i18n.global.t('common.operationFailed')
}
