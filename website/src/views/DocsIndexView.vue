<script setup lang="ts">
import { useHead } from '@unhead/vue'
import { DOCS_SECTIONS } from '@/app/docs.config'
import { useLang } from '@/composables/useLang'
import DocsLayout from '@/components/DocsLayout.vue'

const { lang } = useLang()

useHead(() => ({
  title: lang.value === 'zh' ? '文档 · UniRhy' : 'Docs · UniRhy',
  htmlAttrs: { lang: lang.value === 'zh' ? 'zh-CN' : 'en' },
  meta: [
    {
      name: 'description',
      content:
        lang.value === 'zh'
          ? 'UniRhy 自托管音乐流媒体平台的部署、使用与开发文档。'
          : 'Deployment, usage and development docs for UniRhy self-hosted music streaming.',
    },
  ],
  link: [
    { rel: 'alternate', hreflang: 'zh-CN', href: '/zh/docs' },
    { rel: 'alternate', hreflang: 'en', href: '/en/docs' },
    { rel: 'alternate', hreflang: 'x-default', href: '/zh/docs' },
  ],
}))
</script>

<template>
  <DocsLayout>
    <div class="mx-auto max-w-[900px] px-6 py-16">
      <header class="mb-12 text-center">
        <h1
          class="mb-4 font-serif text-[clamp(2.25rem,5vw,3.5rem)] font-bold leading-[1.1] tracking-[0.04em] text-[#2c2825]"
        >
          {{ lang === 'zh' ? '文档' : 'Documentation' }}
        </h1>
        <div class="mx-auto mb-4 h-[2px] w-10 bg-[#d98c28]"></div>
        <p class="font-serif text-lg italic text-[#8a817c]">
          {{
            lang === 'zh'
              ? '自托管、可扩展的音乐流媒体平台'
              : 'Self-hosted, extensible music streaming'
          }}
        </p>
      </header>

      <div class="grid gap-6 sm:grid-cols-2">
        <router-link
          v-for="section in DOCS_SECTIONS"
          :key="section.slug"
          :to="`/${lang}/docs/${section.slug}/${section.pages[0]?.slug ?? ''}`"
          class="docs-card group block no-underline"
        >
          <div class="docs-card-inner">
            <h2
              class="mb-2 font-serif text-xl font-bold text-[#2c2825] transition-colors duration-300 group-hover:text-[#d98c28]"
            >
              {{ section.title[lang] }}
            </h2>
            <p class="mb-3 text-sm leading-relaxed text-[#6b6560]">
              {{ section.description[lang] }}
            </p>
            <ul class="space-y-1">
              <li
                v-for="page in section.pages"
                :key="page.slug"
                class="font-brand-sans text-xs tracking-wide text-[#9c968b]"
              >
                · {{ page.title[lang] }}
              </li>
            </ul>
          </div>
        </router-link>
      </div>
    </div>
  </DocsLayout>
</template>

<style scoped>
.docs-card-inner {
  background: #fcfbf9;
  padding: 1.75rem;
  border: 1px solid rgba(255, 255, 255, 0.8);
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);
  border-radius: 4px;
  position: relative;
  overflow: hidden;
  transition: all 0.3s ease;
  height: 100%;
}

.docs-card-inner::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image: url('https://www.transparenttextures.com/patterns/cream-paper.png');
  opacity: 0.4;
  pointer-events: none;
  z-index: 0;
}

.docs-card-inner > * {
  position: relative;
  z-index: 1;
}

.docs-card:hover .docs-card-inner {
  box-shadow: 0 10px 30px -10px rgba(168, 160, 149, 0.4);
  transform: translateY(-2px);
}
</style>
