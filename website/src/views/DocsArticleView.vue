<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useHead } from '@unhead/vue'
import DocsLayout from '@/components/DocsLayout.vue'
import type { DocsModule } from '@/types/docs'
import { findPage, flattenDocs } from '@/app/docs.config'
import { useLang } from '@/composables/useLang'
import { useToc } from '@/composables/useToc'

const route = useRoute()
const { lang } = useLang()
const docsHome = computed(() => `/${lang.value}/docs`)

const proseRef = ref<HTMLElement | null>(null)
const { items: tocItems, activeIds, scrollTo, refresh: refreshToc } = useToc(proseRef)

watch([lang, () => route.params.section, () => route.params.slug], async () => {
  await nextTick()
  refreshToc()
})

const modules = import.meta.glob<DocsModule>('/content/docs/**/*.md', { eager: true })

const sectionSlug = computed(() => {
  const v = route.params.section
  return typeof v === 'string' ? v : ''
})
const pageSlug = computed(() => {
  const v = route.params.slug
  return typeof v === 'string' ? v : ''
})

const meta = computed(() => findPage(sectionSlug.value, pageSlug.value))

const post = computed(() => {
  const key = `/content/docs/${lang.value}/${sectionSlug.value}/${pageSlug.value}.md`
  const mod = modules[key]
  if (!mod?.frontmatter.title) return null
  return mod
})

const flat = flattenDocs()
const currentIndex = computed(() =>
  flat.findIndex((p) => p.sectionSlug === sectionSlug.value && p.pageSlug === pageSlug.value),
)
const prev = computed(() => {
  const i = currentIndex.value
  return i > 0 ? flat[i - 1] : null
})
const next = computed(() => {
  const i = currentIndex.value
  return i >= 0 && i < flat.length - 1 ? flat[i + 1] : null
})

useHead(() => {
  const fm = post.value?.frontmatter
  const titleZh = meta.value?.page.title.zh ?? meta.value?.section.title.zh ?? '文档'
  const titleEn = meta.value?.page.title.en ?? meta.value?.section.title.en ?? 'Docs'
  const localTitle = fm?.title ?? (lang.value === 'zh' ? titleZh : titleEn)
  const description = fm?.description ?? meta.value?.section.description[lang.value] ?? ''
  return {
    title: `${localTitle} · UniRhy`,
    htmlAttrs: { lang: lang.value === 'zh' ? 'zh-CN' : 'en' },
    meta: description ? [{ name: 'description', content: description }] : [],
    link: [
      {
        rel: 'canonical',
        href: `/${lang.value}/docs/${sectionSlug.value}/${pageSlug.value}`,
      },
    ],
  }
})
</script>

<template>
  <DocsLayout>
    <!-- Not Found -->
    <div v-if="!post" class="mx-auto max-w-[700px] px-6 py-20 text-center">
      <h1 class="mb-4 font-serif text-4xl font-bold text-[#2c2825]">404</h1>
      <p class="mb-8 text-lg italic text-[#8a817c]">
        {{ lang === 'zh' ? '文档不存在或暂未翻译' : 'Page not found or not translated yet' }}
      </p>
      <router-link
        :to="docsHome"
        class="inline-block border border-[#d98c28] bg-transparent px-8 py-3 font-brand-sans text-sm font-bold tracking-[0.15em] text-[#d98c28] uppercase no-underline transition-all duration-300 hover:bg-[#d98c28] hover:text-white"
      >
        {{ lang === 'zh' ? '返回文档' : 'Back to Docs' }}
      </router-link>
    </div>

    <!-- Article -->
    <div v-else class="px-6 py-16 lg:py-12">
      <!-- TOC -->
      <aside v-if="tocItems.length > 0" class="docs-toc">
        <p class="mb-3 font-brand-sans text-xs tracking-[0.15em] text-[#9c968b] uppercase">
          {{ lang === 'zh' ? '本页目录' : 'On this page' }}
        </p>
        <ul class="toc-list space-y-1">
          <li
            v-for="item in tocItems"
            :key="item.id"
            :style="{ paddingLeft: `${(item.level - 1) * 0.75}rem` }"
          >
            <button
              class="toc-item w-full text-left"
              :class="activeIds.has(item.id) ? 'toc-item-active' : ''"
              @click="scrollTo(item.id)"
            >
              {{ item.text }}
            </button>
          </li>
        </ul>
      </aside>

      <div class="mx-auto max-w-[760px] xl:mr-72">
        <p
          v-if="meta"
          class="mb-3 font-brand-sans text-xs tracking-[0.2em] text-[#9c968b] uppercase"
        >
          {{ meta.section.title[lang] }}
        </p>
        <header class="mb-10">
          <h1
            class="mb-4 font-serif text-[clamp(1.875rem,4vw,2.5rem)] font-bold leading-[1.2] tracking-[0.02em] text-[#2c2825]"
          >
            {{ post.frontmatter.title }}
          </h1>
          <p v-if="post.frontmatter.description" class="text-base italic text-[#8a817c]">
            {{ post.frontmatter.description }}
          </p>
          <div class="mt-4 h-[2px] w-10 bg-[#d98c28]"></div>
        </header>

        <article class="docs-article">
          <div class="docs-article-card">
            <div class="docs-article-inner">
              <div ref="proseRef" class="blog-prose" v-html="post.html"></div>
            </div>
          </div>
        </article>

        <footer class="mt-12 grid gap-4 border-t border-[#dcd6cc] pt-8 sm:grid-cols-2">
          <router-link
            v-if="prev"
            :to="`/${lang}/docs/${prev.sectionSlug}/${prev.pageSlug}`"
            class="docs-nav-card group"
          >
            <span class="block font-brand-sans text-xs tracking-[0.15em] text-[#9c968b] uppercase">
              ← {{ lang === 'zh' ? '上一篇' : 'Previous' }}
            </span>
            <span
              class="mt-1 block font-serif text-base font-bold text-[#2c2825] group-hover:text-[#d98c28]"
            >
              {{ prev.title[lang] }}
            </span>
          </router-link>
          <span v-else />
          <router-link
            v-if="next"
            :to="`/${lang}/docs/${next.sectionSlug}/${next.pageSlug}`"
            class="docs-nav-card group text-right"
          >
            <span class="block font-brand-sans text-xs tracking-[0.15em] text-[#9c968b] uppercase">
              {{ lang === 'zh' ? '下一篇' : 'Next' }} →
            </span>
            <span
              class="mt-1 block font-serif text-base font-bold text-[#2c2825] group-hover:text-[#d98c28]"
            >
              {{ next.title[lang] }}
            </span>
          </router-link>
        </footer>
      </div>
    </div>
  </DocsLayout>
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
    right: 2rem;
    width: 14rem;
    max-height: calc(100vh - 8rem);
    overflow-y: auto;
  }
}

.toc-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.toc-item {
  display: block;
  padding: 0.3rem 0.5rem;
  font-family: 'Barlow Condensed', sans-serif;
  font-size: 0.8rem;
  line-height: 1.4;
  color: #8a817c;
  border: none;
  background: none;
  border-left: 2px solid transparent;
  cursor: pointer;
  transition: all 0.2s;
}

.toc-item:hover {
  color: #2c2825;
}

.toc-item-active {
  color: #d98c28;
  border-left-color: #d98c28;
}

.docs-article-card {
  background: #fcfbf9;
  border: 1px solid rgba(255, 255, 255, 0.8);
  box-shadow: 0 10px 30px -10px rgba(168, 160, 149, 0.4);
  border-radius: 4px;
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

.docs-nav-card {
  display: block;
  padding: 1rem 1.25rem;
  background: rgba(252, 251, 249, 0.7);
  border: 1px solid #dcd6cc;
  border-radius: 4px;
  text-decoration: none;
  transition: all 0.3s ease;
}

.docs-nav-card:hover {
  border-color: #d98c28;
  box-shadow: 0 4px 12px -4px rgba(217, 140, 40, 0.25);
}
</style>
