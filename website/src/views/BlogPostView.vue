<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useHead } from '@unhead/vue'
import BlogLayout from '@/components/BlogLayout.vue'
import type { BlogModule } from '@/types/blog'
import { useLang } from '@/composables/useLang'
import { useToc } from '@/composables/useToc'
import { useBlogWidth } from '@/composables/useBlogWidth'

const route = useRoute()
const { lang, setLang } = useLang()
const { isWide } = useBlogWidth()
const blogListPath = computed(() => `/${lang.value}/blog`)

const proseRef = ref<HTMLElement | null>(null)
const { items: tocItems, activeIds, scrollTo, refresh: refreshToc } = useToc(proseRef)

watch(lang, async () => {
  await nextTick()
  refreshToc()
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
    <!-- Not Found -->
    <div v-if="!post" class="mx-auto max-w-[700px] px-6 py-16">
      <div class="py-20 text-center">
        <h1 class="mb-4 font-serif text-4xl font-bold text-[#2c2825]">404</h1>
        <p class="mb-8 text-lg italic text-[#8a817c]">
          {{ lang === 'zh' ? '文章不存在' : 'Post not found' }}
        </p>
        <router-link
          :to="blogListPath"
          class="inline-block border border-[#d98c28] bg-transparent px-8 py-3 font-brand-sans text-sm font-bold tracking-[0.15em] text-[#d98c28] uppercase no-underline transition-all duration-300 hover:bg-[#d98c28] hover:text-white hover:shadow-[0_4px_15px_rgba(217,140,40,0.25)]"
        >
          {{ lang === 'zh' ? '返回博客' : 'Back to Blog' }}
        </router-link>
      </div>
    </div>

    <!-- Post Content -->
    <div v-else class="px-6 py-16">
      <!-- Back Link (small screens only, sidebar has it on lg) -->
      <router-link
        :to="blogListPath"
        class="fixed top-4 left-4 z-10 inline-block font-brand-sans text-xs tracking-[0.2em] text-[#9c968b] uppercase no-underline transition-colors duration-300 hover:text-[#d98c28] lg:hidden"
      >
        ← Blog
      </router-link>

      <!-- TOC Sidebar (fixed position) -->
      <aside v-if="tocItems.length > 0" class="fixed top-8 left-8 hidden w-52 lg:block">
        <nav>
          <!-- Back Link -->
          <router-link
            :to="blogListPath"
            class="mb-5 inline-block font-brand-sans text-xs tracking-[0.2em] text-[#9c968b] uppercase no-underline transition-colors duration-300 hover:text-[#d98c28]"
          >
            ← Blog
          </router-link>

          <!-- TOC Heading -->
          <p class="mb-3 font-brand-sans text-xs tracking-[0.15em] text-[#9c968b] uppercase">
            {{ lang === 'zh' ? '目录' : 'Contents' }}
          </p>

          <!-- TOC Items -->
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
        </nav>
      </aside>

      <!-- Top-right Controls (fixed) -->
      <div
        class="fixed top-4 right-4 z-10 flex flex-col items-end gap-3 font-brand-sans text-sm select-none lg:top-8 lg:right-8"
      >
        <div class="flex items-center gap-3">
          <span
            class="cursor-pointer tracking-wide transition-all duration-300"
            :class="
              lang === 'zh' ? 'font-bold text-[#d98c28]' : 'text-[#9c968b] hover:text-[#2c2825]'
            "
            @click="setLang('zh')"
          >
            中文
          </span>
          <span class="text-[#dcd6cc]">/</span>
          <span
            class="cursor-pointer tracking-wide transition-all duration-300"
            :class="
              lang === 'en' ? 'font-bold text-[#d98c28]' : 'text-[#9c968b] hover:text-[#2c2825]'
            "
            @click="setLang('en')"
          >
            EN
          </span>
        </div>
        <div class="hidden items-center gap-3 lg:flex">
          <span
            class="cursor-pointer tracking-wide transition-all duration-300"
            :class="!isWide ? 'font-bold text-[#d98c28]' : 'text-[#9c968b] hover:text-[#2c2825]'"
            @click="isWide = false"
          >
            {{ lang === 'zh' ? '标准' : 'Standard' }}
          </span>
          <span class="text-[#dcd6cc]">/</span>
          <span
            class="cursor-pointer tracking-wide transition-all duration-300"
            :class="isWide ? 'font-bold text-[#d98c28]' : 'text-[#9c968b] hover:text-[#2c2825]'"
            @click="isWide = true"
          >
            {{ lang === 'zh' ? '全宽' : 'Wide' }}
          </span>
        </div>
      </div>

      <!-- Main Content -->
      <div class="lg:pl-60">
        <div
          class="mx-auto transition-[max-width] duration-300"
          :class="isWide ? 'max-w-[1200px]' : 'max-w-[700px]'"
        >
          <header class="mb-12 text-center">
            <h1
              class="mb-4 font-serif text-[clamp(2rem,5vw,3rem)] font-bold leading-[1.15] tracking-[0.02em] text-[#2c2825]"
            >
              {{ post.frontmatter.title }}
            </h1>
            <time
              class="mb-4 block font-brand-sans text-xs tracking-[0.15em] text-[#9c968b] uppercase"
            >
              {{ formatDate(post.frontmatter.publishAt) }}
            </time>
            <div class="mx-auto mt-4 h-[2px] w-10 bg-[#d98c28]"></div>
          </header>

          <!-- Cover Image -->
          <div v-if="post.frontmatter.cover" class="mb-10 overflow-hidden rounded">
            <img
              :src="post.frontmatter.cover"
              :alt="post.frontmatter.title"
              class="w-full object-cover"
            />
          </div>

          <!-- Article Body -->
          <article class="blog-article">
            <div class="card-stack-1"></div>
            <div class="card-stack-2"></div>
            <div class="blog-article-card">
              <div class="blog-article-inner">
                <div ref="proseRef" class="blog-prose" v-html="post.html"></div>
              </div>
            </div>
          </article>

          <!-- Footer Nav -->
          <footer class="mt-12 border-t border-[#dcd6cc] pt-8 text-center">
            <router-link
              :to="blogListPath"
              class="inline-block border border-[#d98c28] bg-transparent px-8 py-3 font-brand-sans text-sm font-bold tracking-[0.15em] text-[#d98c28] uppercase no-underline transition-all duration-300 hover:bg-[#d98c28] hover:text-white hover:shadow-[0_4px_15px_rgba(217,140,40,0.25)]"
            >
              {{ lang === 'zh' ? '返回博客' : 'Back to Blog' }}
            </router-link>
          </footer>
        </div>
      </div>
    </div>
  </BlogLayout>
</template>

<style scoped>
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
  border-radius: 0;
}

.toc-item:hover {
  color: #2c2825;
}

.toc-item-active {
  color: #d98c28;
  border-left-color: #d98c28;
}

.blog-article {
  position: relative;
}

.card-stack-1 {
  position: absolute;
  top: 1rem;
  left: 1rem;
  right: -0.5rem;
  bottom: -0.5rem;
  background: #f0ebe3;
  border: 1px solid #e6e1d8;
  transform: rotate(1deg);
  border-radius: 4px;
  z-index: -2;
}

.card-stack-2 {
  position: absolute;
  top: 0.5rem;
  left: 0.5rem;
  right: -0.25rem;
  bottom: -0.25rem;
  background: #f5f2eb;
  border: 1px solid #e6e1d8;
  transform: rotate(-1deg);
  border-radius: 4px;
  z-index: -1;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);
}

.blog-article-card {
  background: #fcfbf9;
  border: 1px solid rgba(255, 255, 255, 0.8);
  box-shadow: 0 10px 30px -10px rgba(168, 160, 149, 0.4);
  border-radius: 4px;
  position: relative;
  overflow: hidden;
}

.blog-article-card::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image: url('https://www.transparenttextures.com/patterns/cream-paper.png');
  opacity: 0.4;
  pointer-events: none;
  z-index: 0;
}

.blog-article-inner {
  position: relative;
  z-index: 1;
  padding: 3rem 2.5rem;
}

@media (max-width: 640px) {
  .blog-article-inner {
    padding: 2rem 1.5rem;
  }
}
</style>

<style>
.blog-prose {
  color: #2c2825;
  font-family: 'Georgia', 'Times New Roman', Times, serif;
  font-size: 1.05rem;
  line-height: 1.85;
}

.blog-prose h1 {
  font-size: 2rem;
  font-weight: 700;
  margin-top: 2.5rem;
  margin-bottom: 1rem;
  line-height: 1.2;
  color: #2c2825;
}

.blog-prose h2 {
  font-size: 1.5rem;
  font-weight: 700;
  margin-top: 2rem;
  margin-bottom: 0.75rem;
  line-height: 1.3;
  color: #2c2825;
}

.blog-prose h3 {
  font-size: 1.25rem;
  font-weight: 600;
  margin-top: 1.5rem;
  margin-bottom: 0.5rem;
  color: #2c2825;
}

.blog-prose p {
  margin-bottom: 1.25rem;
  color: #4a4541;
}

.blog-prose a {
  color: #d98c28;
  text-decoration: underline;
  text-underline-offset: 2px;
  transition: color 0.2s;
}

.blog-prose a:hover {
  color: #b8721b;
}

.blog-prose ul,
.blog-prose ol {
  margin-bottom: 1.25rem;
  padding-left: 1.5rem;
}

.blog-prose li {
  margin-bottom: 0.4rem;
  color: #4a4541;
}

.blog-prose ul li {
  list-style-type: disc;
}

.blog-prose ol li {
  list-style-type: decimal;
}

.blog-prose blockquote {
  border-left: 3px solid #d98c28;
  padding: 0.75rem 1.25rem;
  margin: 1.5rem 0;
  background: rgba(217, 140, 40, 0.05);
  color: #6b6560;
  font-style: italic;
}

.blog-prose pre {
  background: #2c2825;
  color: #f0ebe3;
  padding: 1.25rem;
  border-radius: 4px;
  overflow-x: auto;
  margin: 1.5rem 0;
  font-size: 0.9rem;
  line-height: 1.6;
}

.blog-prose code {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 0.9em;
}

.blog-prose :not(pre) > code {
  background: rgba(217, 140, 40, 0.1);
  padding: 0.15em 0.4em;
  border-radius: 3px;
  color: #b8721b;
}

.blog-prose img {
  max-width: 100%;
  height: auto;
  border-radius: 4px;
  margin: 1.5rem 0;
}

.blog-prose hr {
  border: none;
  border-top: 1px solid #dcd6cc;
  margin: 2rem 0;
}

.blog-prose table {
  width: 100%;
  border-collapse: collapse;
  margin: 1.5rem 0;
}

.blog-prose th,
.blog-prose td {
  border: 1px solid #dcd6cc;
  padding: 0.6rem 1rem;
  text-align: left;
}

.blog-prose th {
  background: rgba(217, 140, 40, 0.08);
  font-weight: 600;
}
</style>
