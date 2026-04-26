import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { DEFAULT_LANG, SUPPORTED_LANGS, type SupportedLang } from '@/router/routes'

export type Lang = SupportedLang

function isLang(value: unknown): value is Lang {
  return typeof value === 'string' && (SUPPORTED_LANGS as readonly string[]).includes(value)
}

const preferenceLang = ref<Lang>(DEFAULT_LANG)
let preferenceInitialized = false

function loadPreference(): Lang {
  if (typeof window === 'undefined') return DEFAULT_LANG
  const stored = localStorage.getItem('site-lang')
  if (isLang(stored)) return stored
  return navigator.language.startsWith('zh') ? 'zh' : 'en'
}

function ensurePreferenceInitialized() {
  if (preferenceInitialized || typeof window === 'undefined') return
  preferenceInitialized = true
  preferenceLang.value = loadPreference()
  watch(preferenceLang, (v) => {
    localStorage.setItem('site-lang', v)
  })
}

export function useLang() {
  ensurePreferenceInitialized()

  const route = useRoute()
  const router = useRouter()

  const lang = computed<Lang>(() => {
    const param = route.params.lang
    if (isLang(param)) return param
    return preferenceLang.value
  })

  function setLang(next: Lang) {
    preferenceLang.value = next
    const param = route.params.lang
    if (isLang(param) && param !== next) {
      const newPath = route.fullPath.replace(/^\/(zh|en)(?=\/|$)/, `/${next}`)
      void router.push(newPath)
    }
  }

  return { lang, setLang }
}
