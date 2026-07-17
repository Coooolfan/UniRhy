import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN'
import en from './en'

export const SUPPORTED_LOCALES = ['zh-CN', 'en'] as const
export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number]
export const DEFAULT_LOCALE: SupportedLocale = 'zh-CN'
export const LOCALE_STORAGE_KEY = 'unirhy.locale'

export const isSupportedLocale = (value: string | null | undefined): value is SupportedLocale =>
    value === 'zh-CN' || value === 'en'

const detectInitialLocale = (): SupportedLocale => {
    if (typeof window === 'undefined') {
        return DEFAULT_LOCALE
    }

    const saved = window.localStorage.getItem(LOCALE_STORAGE_KEY)
    if (isSupportedLocale(saved)) {
        return saved
    }

    return window.navigator.language.toLowerCase().startsWith('zh') ? 'zh-CN' : 'en'
}

export const setDocumentLocale = (locale: SupportedLocale) => {
    if (typeof document !== 'undefined') {
        document.documentElement.lang = locale
    }
}

const initialLocale = detectInitialLocale()
setDocumentLocale(initialLocale)

export const i18n = createI18n({
    legacy: false,
    locale: initialLocale,
    fallbackLocale: DEFAULT_LOCALE,
    messages: {
        'zh-CN': zhCN,
        en,
    },
})
