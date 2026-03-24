import { ref, watch } from 'vue'

export type Lang = 'zh' | 'en'

function loadLang(): Lang {
  const stored = localStorage.getItem('site-lang')
  if (stored === 'zh' || stored === 'en') return stored
  return navigator.language.startsWith('zh') ? 'zh' : 'en'
}

const lang = ref<Lang>(loadLang())

watch(lang, (v) => {
  localStorage.setItem('site-lang', v)
})

export function useLang() {
  return { lang }
}
