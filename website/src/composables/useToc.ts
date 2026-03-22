import { onMounted, onUnmounted, ref, reactive, type Ref } from 'vue'

export interface TocItem {
  id: string
  text: string
  level: number
}

function slugify(text: string): string {
  return text
    .toLowerCase()
    .replaceAll(/\s+/g, '-')
    .replaceAll(/[^\w\u4E00-\u9FFF-]/g, '')
}

function scrollToHeading(id: string) {
  const el = document.querySelector(`#${CSS.escape(id)}`)
  el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function findCurrentSectionId(headingIds: readonly string[]): string | null {
  const scrollY = window.scrollY
  let current: string | null = null
  for (const id of headingIds) {
    const el = document.querySelector(`#${CSS.escape(id)}`)
    if (el && el.getBoundingClientRect().top + scrollY <= scrollY + 80) {
      current = id
    }
  }
  return current
}

// eslint-disable-next-line typescript-eslint/prefer-readonly-parameter-types -- Ref<HTMLElement> is not deeply readonly
export function useToc(containerRef: Ref<HTMLElement | null>) {
  const items = ref<TocItem[]>([])
  const activeIds = reactive(new Set<string>())
  const visibleIds = new Set<string>()
  let observer: IntersectionObserver | null = null
  const headingIds: string[] = []

  function updateActive() {
    activeIds.clear()
    if (visibleIds.size > 0) {
      for (const id of visibleIds) {
        activeIds.add(id)
      }
    } else {
      const current = findCurrentSectionId(headingIds)
      if (current) {
        activeIds.add(current)
      }
    }
  }

  function onScroll() {
    if (visibleIds.size === 0) {
      updateActive()
    }
  }

  function cleanup() {
    observer?.disconnect()
    observer = null
    visibleIds.clear()
    activeIds.clear()
    headingIds.length = 0
    items.value = []
  }

  function parse() {
    cleanup()

    const el = containerRef.value
    if (!el) return

    const headings = el.querySelectorAll<HTMLElement>('h1, h2, h3')
    const seenSlugs = new Map<string, number>()

    for (const heading of headings) {
      const text = heading.textContent.trim()
      let slug = slugify(text)
      const count = seenSlugs.get(slug) ?? 0
      seenSlugs.set(slug, count + 1)
      if (count > 0) slug = `${slug}-${count}`

      heading.id = slug
      headingIds.push(slug)
      items.value.push({ id: slug, text, level: Number.parseInt(heading.tagName[1], 10) })
    }

    observer = new IntersectionObserver(
      // eslint-disable-next-line typescript-eslint/prefer-readonly-parameter-types -- browser API
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            visibleIds.add(entry.target.id)
          } else {
            visibleIds.delete(entry.target.id)
          }
        }
        updateActive()
      },
      { threshold: 0 },
    )

    for (const heading of headings) {
      observer.observe(heading)
    }
  }

  onMounted(() => {
    parse()
    window.addEventListener('scroll', onScroll, { passive: true })
  })

  onUnmounted(() => {
    cleanup()
    window.removeEventListener('scroll', onScroll)
  })

  return { items, activeIds, scrollTo: scrollToHeading, refresh: parse }
}
