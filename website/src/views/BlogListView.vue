<script setup lang="ts">
import { computed } from 'vue'
import { useHead } from '@unhead/vue'
import BlogLayout from '@/components/BlogLayout.vue'
import type { BlogModule, BlogPostMeta } from '@/types/blog'
import { useLang } from '@/composables/useLang'

const { lang, setLang } = useLang()

useHead(() => ({
  title: lang.value === 'zh' ? '博客 · UniRhy' : 'Blog · UniRhy',
  htmlAttrs: { lang: lang.value === 'zh' ? 'zh-CN' : 'en' },
  meta: [
    {
      name: 'description',
      content:
        lang.value === 'zh'
          ? '关于音乐、技术和产品的思考。'
          : 'Thoughts on music, technology and product.',
    },
  ],
  link: [
    { rel: 'alternate', hreflang: 'zh-CN', href: '/zh/blog' },
    { rel: 'alternate', hreflang: 'en', href: '/en/blog' },
    { rel: 'alternate', hreflang: 'x-default', href: '/zh/blog' },
  ],
}))

const modules = import.meta.glob<BlogModule>('/content/blog/**/*.md', { eager: true })

const isDev = import.meta.env.DEV

const posts = computed<BlogPostMeta[]>(() => {
  const prefix = `/content/blog/${lang.value}/`
  return Object.entries(modules)
    .filter(([path, mod]) => path.startsWith(prefix) && (isDev || !mod.frontmatter.draft))
    .map(([path, mod]): BlogPostMeta => {
      const fm = mod.frontmatter
      return {
        slug: path.split('/').pop()!.replace('.md', ''),
        title: fm.title,
        description: fm.description,
        publishAt: fm.publishAt,
        cover: fm.cover,
        draft: fm.draft,
      }
    })
    .sort((a, b) => b.publishAt - a.publishAt)
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
    <div class="mx-auto max-w-[800px] px-6 py-16">
      <!-- Back Link (fixed top-left) -->
      <router-link
        to="/"
        class="fixed top-4 left-4 z-10 inline-block font-brand-sans text-xs tracking-[0.2em] text-[#9c968b] uppercase no-underline transition-colors duration-300 hover:text-[#d98c28] lg:top-8 lg:left-8"
      >
        ← UniRhy
      </router-link>

      <!-- Language Toggle (fixed top-right) -->
      <div
        class="fixed top-4 right-4 z-10 flex items-center gap-3 font-brand-sans text-sm select-none lg:top-8 lg:right-8"
      >
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

      <!-- Header -->
      <header class="mb-16 text-center">
        <h1
          class="mb-4 font-serif text-[clamp(2.5rem,6vw,4rem)] font-bold leading-[1.1] tracking-[0.05em] text-[#2c2825]"
        >
          Blog
        </h1>
        <div class="mx-auto mb-4 h-[2px] w-10 bg-[#d98c28]"></div>
        <p class="font-serif text-lg italic text-[#8a817c]">
          {{
            lang === 'zh'
              ? '关于音乐、技术和产品的思考'
              : 'Thoughts on music, technology and product'
          }}
        </p>
      </header>

      <!-- Post List -->
      <div class="space-y-6">
        <router-link
          v-for="post in posts"
          :key="post.slug"
          :to="`/${lang}/blog/${post.slug}`"
          class="blog-card group block no-underline"
        >
          <article class="blog-card-inner">
            <div class="flex flex-col gap-4" :class="post.cover ? 'sm:flex-row' : ''">
              <!-- Cover Image -->
              <div
                v-if="post.cover"
                class="aspect-video w-full shrink-0 overflow-hidden rounded sm:aspect-square sm:w-48"
              >
                <img
                  :src="post.cover"
                  :alt="post.title"
                  class="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
                />
              </div>
              <!-- Content -->
              <div class="flex min-w-0 flex-1 flex-col justify-center">
                <time
                  class="mb-2 block font-brand-sans text-xs tracking-[0.15em] text-[#9c968b] uppercase"
                >
                  {{ formatDate(post.publishAt) }}
                </time>
                <h2
                  class="mb-2 font-serif text-xl font-bold leading-snug text-[#2c2825] transition-colors duration-300 group-hover:text-[#d98c28]"
                >
                  <span
                    v-if="post.draft"
                    class="mr-2 inline-block rounded bg-[#d98c28]/15 px-1.5 py-0.5 align-middle font-brand-sans text-[0.65rem] leading-none text-[#d98c28]"
                  >
                    DRAFT
                  </span>
                  {{ post.title }}
                </h2>
                <p class="line-clamp-2 text-base leading-relaxed text-[#8a817c]">
                  {{ post.description }}
                </p>
              </div>
            </div>
          </article>
        </router-link>
      </div>

      <!-- Empty State -->
      <div v-if="posts.length === 0" class="py-20 text-center">
        <p class="text-lg italic text-[#8a817c]">
          {{ lang === 'zh' ? '暂无文章' : 'No posts yet' }}
        </p>
      </div>
    </div>
  </BlogLayout>
</template>

<style scoped>
.blog-card-inner {
  background: #fcfbf9;
  padding: 2rem;
  border: 1px solid rgba(255, 255, 255, 0.8);
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);
  border-radius: 4px;
  position: relative;
  overflow: hidden;
  transition: all 0.3s ease;
}

.blog-card-inner::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image: url('https://www.transparenttextures.com/patterns/cream-paper.png');
  opacity: 0.4;
  pointer-events: none;
  z-index: 0;
}

.blog-card-inner > * {
  position: relative;
  z-index: 1;
}

.blog-card:hover .blog-card-inner {
  box-shadow: 0 10px 30px -10px rgba(168, 160, 149, 0.4);
  transform: translateY(-2px);
}
</style>
