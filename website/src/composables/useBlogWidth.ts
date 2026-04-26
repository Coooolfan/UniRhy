import { onMounted, ref, watch } from 'vue'

const isWide = ref(false)
let initialized = false

export function useBlogWidth() {
  if (!initialized && typeof window !== 'undefined') {
    initialized = true
    onMounted(() => {
      isWide.value = localStorage.getItem('blog-width') === 'wide'
      watch(isWide, (v) => {
        localStorage.setItem('blog-width', v ? 'wide' : 'narrow')
      })
    })
  }
  return { isWide }
}
