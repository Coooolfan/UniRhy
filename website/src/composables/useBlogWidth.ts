import { ref, watch } from 'vue'

const isWide = ref(localStorage.getItem('blog-width') === 'wide')

watch(isWide, (v) => {
  localStorage.setItem('blog-width', v ? 'wide' : 'narrow')
})

export function useBlogWidth() {
  return { isWide }
}
