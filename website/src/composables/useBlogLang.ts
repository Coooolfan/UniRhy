import { ref, watch } from 'vue'

export type BlogLang = 'zh' | 'en'

function loadLang(): BlogLang {
  const stored = localStorage.getItem('blog-lang')
  if (stored === 'zh' || stored === 'en') return stored
  return navigator.language.startsWith('zh') ? 'zh' : 'en'
}

const lang = ref<BlogLang>(loadLang())

watch(lang, (v) => {
  localStorage.setItem('blog-lang', v)
})

export function useBlogLang() {
  return { lang }
}
