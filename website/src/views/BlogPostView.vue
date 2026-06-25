<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useHead } from '@unhead/vue'
import BlogLayout from '@/components/BlogLayout.vue'
import type { BlogModule } from '@/types/blog'
import { useLang } from '@/composables/useLang'
import { useToc } from '@/composables/useToc'

const route = useRoute()
const { lang, setLang } = useLang()
const blogListPath = computed(() => `/${lang.value}/blog`)

const proseRef = ref<HTMLElement | null>(null)
const tocListRef = ref<HTMLElement | null>(null)
const { items: tocItems, activeIds, scrollTo, refresh: refreshToc } = useToc(proseRef)
const activeTocIndexes = computed(() =>
  tocItems.value.flatMap((item, index) => (activeIds.has(item.id) ? [index] : [])),
)
const primaryActiveTocId = computed(() => {
  const firstIndex = activeTocIndexes.value[0]
  return firstIndex === undefined ? undefined : tocItems.value[firstIndex]?.id
})
const activeTocTrackStyle = ref({ height: '0px', opacity: '0', top: '0px' })
let tocTrackFrame = 0

function updateActiveTocTrack() {
  const list = tocListRef.value
  if (!list || tocItems.value.length === 0) {
    activeTocTrackStyle.value = { ...activeTocTrackStyle.value, opacity: '0' }
    return
  }

  const firstIndex = activeTocIndexes.value[0]
  const lastIndex = activeTocIndexes.value.at(-1) ?? firstIndex
  if (firstIndex === undefined || lastIndex === undefined) {
    activeTocTrackStyle.value = { ...activeTocTrackStyle.value, opacity: '0' }
    return
  }

  const entries = list.querySelectorAll<HTMLElement>('.toc-entry')
  const firstEntry = entries[firstIndex]
  const lastEntry = entries[lastIndex]
  if (!firstEntry || !lastEntry) {
    activeTocTrackStyle.value = { ...activeTocTrackStyle.value, opacity: '0' }
    return
  }

  const listRect = list.getBoundingClientRect()
  const firstRect = firstEntry.getBoundingClientRect()
  const lastRect = lastEntry.getBoundingClientRect()
  const top = firstRect.top - listRect.top
  const bottom = lastRect.bottom - listRect.top

  activeTocTrackStyle.value = {
    top: `${top}px`,
    height: `${Math.max(2, bottom - top)}px`,
    opacity: '1',
  }
}

function scheduleActiveTocTrackUpdate() {
  if (tocTrackFrame !== 0) return
  tocTrackFrame = window.requestAnimationFrame(() => {
    tocTrackFrame = 0
    updateActiveTocTrack()
  })
}

watch([lang, () => route.params.slug], async () => {
  await nextTick()
  refreshToc()
})

watch(
  () => [tocItems.value.map((item) => item.id).join('\n'), activeTocIndexes.value.join(',')],
  async () => {
    await nextTick()
    updateActiveTocTrack()
  },
  { flush: 'post' },
)

onMounted(() => {
  window.addEventListener('scroll', scheduleActiveTocTrackUpdate, { passive: true })
  window.addEventListener('resize', scheduleActiveTocTrackUpdate)
})

onUnmounted(() => {
  if (tocTrackFrame !== 0) {
    window.cancelAnimationFrame(tocTrackFrame)
    tocTrackFrame = 0
  }
  window.removeEventListener('scroll', scheduleActiveTocTrackUpdate)
  window.removeEventListener('resize', scheduleActiveTocTrackUpdate)
})

const modules = import.meta.glob<BlogModule>('/content/blog/**/*.md', { eager: true })

const post = computed(() => {
  const slug = route.params.slug as string
  const key = `/content/blog/${lang.value}/${slug}.md`
  const mod = modules[key]
  if (!mod?.frontmatter.title) return null
  return mod
})

const altLangSlugExists = computed(() => {
  const slug = route.params.slug as string
  const otherLang = lang.value === 'zh' ? 'en' : 'zh'
  return Boolean(modules[`/content/blog/${otherLang}/${slug}.md`]?.frontmatter.title)
})

useHead(() => {
  const slug = route.params.slug as string
  const fm = post.value?.frontmatter
  let title: string
  if (fm?.title) {
    title = `${fm.title} · UniRhy`
  } else if (lang.value === 'zh') {
    title = '文章不存在 · UniRhy'
  } else {
    title = 'Post not found · UniRhy'
  }
  const description = fm?.description ?? ''
  const otherLang = lang.value === 'zh' ? 'en' : 'zh'
  const links = [
    { rel: 'canonical', href: `/${lang.value}/blog/${slug}` },
    {
      rel: 'alternate',
      hreflang: lang.value === 'zh' ? 'zh-CN' : 'en',
      href: `/${lang.value}/blog/${slug}`,
    },
    ...(altLangSlugExists.value
      ? [
          {
            rel: 'alternate',
            hreflang: otherLang === 'zh' ? 'zh-CN' : 'en',
            href: `/${otherLang}/blog/${slug}`,
          },
        ]
      : []),
  ]
  return {
    title,
    htmlAttrs: { lang: lang.value === 'zh' ? 'zh-CN' : 'en' },
    meta: description ? [{ name: 'description', content: description }] : [],
    link: links,
  }
})

function formatDate(epochSeconds: number): string {
  const date = new Date(epochSeconds * 1000)
  return date.toLocaleDateString(lang.value === 'zh' ? 'zh-CN' : 'en-US', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    timeZone: 'UTC',
  })
}
</script>

<template>
  <BlogLayout>
    <!-- Top bar -->
    <div
      class="fixed top-4 right-4 left-4 z-20 flex items-center justify-between font-brand-sans text-sm select-none lg:top-8 lg:right-8 lg:left-8"
    >
      <router-link
        to="/"
        class="tracking-wide text-[#9c968b] no-underline transition-colors duration-300 hover:text-[#2c2825]"
      >
        ← UniRhy
      </router-link>
      <div class="flex items-center gap-3">
        <span
          class="cursor-pointer tracking-wide transition-all duration-300"
          :class="lang === 'zh' ? 'font-bold text-[#d98c28]' : 'text-[#9c968b] hover:text-[#2c2825]'"
          @click="setLang('zh')"
        >
          中文
        </span>
        <span class="text-[#dcd6cc]">/</span>
        <span
          class="cursor-pointer tracking-wide transition-all duration-300"
          :class="lang === 'en' ? 'font-bold text-[#d98c28]' : 'text-[#9c968b] hover:text-[#2c2825]'"
          @click="setLang('en')"
        >
          EN
        </span>
      </div>
    </div>

    <!-- Not Found -->
    <div v-if="!post" class="mx-auto max-w-[700px] px-6 py-20 text-center">
      <h1 class="mb-4 font-serif text-4xl font-bold text-[#2c2825]">404</h1>
      <p class="mb-8 text-lg italic text-[#8a817c]">
        {{ lang === 'zh' ? '文章不存在' : 'Post not found' }}
      </p>
      <router-link
        :to="blogListPath"
        class="inline-block border border-[#d98c28] bg-transparent px-8 py-3 font-brand-sans text-sm font-bold tracking-[0.15em] text-[#d98c28] uppercase no-underline transition-all duration-300 hover:bg-[#d98c28] hover:text-white"
      >
        ← {{ lang === 'zh' ? '回到碎碎念' : 'Back to stray notes' }}
      </router-link>
    </div>

    <!-- Article -->
    <div v-else class="px-6 py-16 lg:py-12 xl:pl-72">
      <!-- TOC -->
      <aside v-if="tocItems.length > 0" class="docs-toc">
        <p class="mb-3 font-brand-sans text-xs tracking-[0.15em] text-[#9c968b] uppercase">
          {{ lang === 'zh' ? '目录' : 'Contents' }}
        </p>
        <div class="toc-list-frame">
          <div aria-hidden="true" class="toc-active-track" :style="activeTocTrackStyle"></div>
          <ul ref="tocListRef" class="toc-list">
            <li v-for="item in tocItems" :key="item.id" class="toc-entry">
              <button
                class="toc-item w-full text-left"
                :class="item.id === primaryActiveTocId ? 'toc-item-active' : ''"
                :style="{ '--toc-indent': `${(item.level - 1) * 0.75}rem` }"
                @click="scrollTo(item.id)"
              >
                {{ item.text }}
              </button>
            </li>
          </ul>
        </div>
      </aside>

      <div class="mx-auto max-w-[760px]">
        <header class="mb-10">
          <h1
            class="mb-4 font-serif text-[clamp(1.875rem,4vw,2.5rem)] font-bold leading-[1.2] tracking-[0.02em] text-[#2c2825]"
          >
            {{ post.frontmatter.title }}
          </h1>
          <time class="block font-brand-sans text-xs tracking-[0.15em] text-[#9c968b] uppercase">
            {{ formatDate(post.frontmatter.publishAt) }}
          </time>
          <p v-if="post.frontmatter.description" class="mt-3 text-base italic text-[#8a817c]">
            {{ post.frontmatter.description }}
          </p>
          <div class="mt-4 h-[2px] w-10 bg-[#d98c28]"></div>
        </header>

        <div v-if="post.frontmatter.cover" class="mb-10 overflow-hidden rounded">
          <img
            :src="post.frontmatter.cover"
            :alt="post.frontmatter.title"
            class="w-full object-cover"
          />
        </div>

        <article class="docs-article">
          <div class="docs-article-card">
            <div class="docs-article-inner">
              <div ref="proseRef" class="blog-prose" v-html="post.html"></div>
            </div>
          </div>
        </article>

        <footer class="mt-12 border-t border-[#dcd6cc] pt-8 text-center">
          <router-link
            :to="blogListPath"
            class="inline-block border border-[#d98c28] bg-transparent px-8 py-3 font-brand-sans text-sm font-bold tracking-[0.15em] text-[#d98c28] uppercase no-underline transition-all duration-300 hover:bg-[#d98c28] hover:text-white"
          >
            ← {{ lang === 'zh' ? '回到碎碎念' : 'Back to stray notes' }}
          </router-link>
        </footer>
      </div>
    </div>
  </BlogLayout>
</template>

<style scoped>
.docs-toc {
  display: none;
}

@media (min-width: 1280px) {
  .docs-toc {
    display: block;
    position: fixed;
    top: 5rem;
    left: 2rem;
    width: 14rem;
    max-height: calc(100vh - 8rem);
    overflow-y: auto;
    scrollbar-width: none;
  }

  .docs-toc::-webkit-scrollbar {
    display: none;
  }
}

.toc-list-frame {
  position: relative;
}

.toc-list-frame::before {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 2px;
  background: #e5ddd2;
  z-index: 0;
}

.toc-active-track {
  position: absolute;
  left: 0;
  width: 2px;
  background: #d98c28;
  transform: translateZ(0);
  transition:
    top 180ms ease,
    height 180ms ease,
    opacity 120ms ease;
  z-index: 1;
}

.toc-list {
  list-style: none;
  padding: 0;
  margin: 0;
  position: relative;
  z-index: 2;
}

.toc-entry {
  position: relative;
}

.toc-item {
  display: block;
  padding: 0.3rem 0.5rem 0.3rem calc(0.75rem + var(--toc-indent));
  font-family: 'Barlow Condensed', sans-serif;
  font-size: 0.8rem;
  line-height: 1.4;
  color: #8a817c;
  border: none;
  background: none;
  cursor: pointer;
  transition: color 0.2s;
}

.toc-item:hover {
  color: #2c2825;
}

.toc-item-active {
  color: #d98c28;
}

.docs-article-card {
  background: #fcfbf9;
  border: 1px solid rgba(255, 255, 255, 0.8);
  box-shadow: 0 10px 30px -10px rgba(168, 160, 149, 0.4);
  border-radius: 2px;
  position: relative;
  overflow: hidden;
}

.docs-article-card::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image: url('https://www.transparenttextures.com/patterns/cream-paper.png');
  opacity: 0.4;
  pointer-events: none;
  z-index: 0;
}

.docs-article-inner {
  position: relative;
  z-index: 1;
  padding: 2.5rem 2rem;
}

@media (max-width: 640px) {
  .docs-article-inner {
    padding: 1.75rem 1.25rem;
  }
}
</style>
