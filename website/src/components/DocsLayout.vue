<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { DOCS_SECTIONS } from '@/app/docs.config'
import { useLang } from '@/composables/useLang'
import BlogLayout from '@/components/BlogLayout.vue'

const { lang, setLang } = useLang()
const route = useRoute()
const drawerOpen = ref(false)

const currentSection = computed(() => {
  const v = route.params.section
  return typeof v === 'string' ? v : null
})
const currentSlug = computed(() => {
  const v = route.params.slug
  return typeof v === 'string' ? v : null
})

const docsHome = computed(() => `/${lang.value}/docs`)

function pageHref(sectionSlug: string, pageSlug: string): string {
  return `/${lang.value}/docs/${sectionSlug}/${pageSlug}`
}

function isActive(sectionSlug: string, pageSlug: string): boolean {
  return currentSection.value === sectionSlug && currentSlug.value === pageSlug
}
</script>

<template>
  <BlogLayout>
    <!-- Mobile drawer toggle -->
    <button
      class="docs-drawer-toggle fixed top-4 left-4 z-20 inline-flex items-center gap-2 rounded border border-[#dcd6cc] bg-[#fcfbf9]/90 px-3 py-1.5 font-brand-sans text-xs tracking-[0.15em] text-[#2c2825] uppercase backdrop-blur lg:hidden"
      type="button"
      @click="drawerOpen = !drawerOpen"
    >
      {{ drawerOpen ? (lang === 'zh' ? '关闭' : 'Close') : lang === 'zh' ? '目录' : 'Menu' }}
    </button>

    <!-- Top-right Controls -->
    <div
      class="fixed top-4 right-4 z-20 flex items-center gap-3 font-brand-sans text-sm select-none lg:top-8 lg:right-8"
    >
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

    <!-- Sidebar -->
    <aside
      class="docs-sidebar"
      :class="[
        drawerOpen
          ? 'fixed inset-y-0 left-0 z-10 w-72 overflow-y-auto bg-[#f5f0e6]/95 px-6 py-16 shadow-xl backdrop-blur'
          : 'hidden lg:fixed lg:inset-y-0 lg:left-0 lg:z-10 lg:block lg:w-64 lg:overflow-y-auto lg:px-6 lg:py-12',
      ]"
    >
      <router-link
        to="/"
        class="mb-6 inline-block font-brand-sans text-xs tracking-[0.2em] text-[#9c968b] uppercase no-underline transition-colors duration-300 hover:text-[#d98c28]"
        @click="drawerOpen = false"
      >
        ← UniRhy
      </router-link>

      <router-link
        :to="docsHome"
        class="mb-6 block font-serif text-xl font-bold tracking-wide text-[#2c2825] no-underline transition-colors duration-300 hover:text-[#d98c28]"
        @click="drawerOpen = false"
      >
        {{ lang === 'zh' ? '文档' : 'Docs' }}
      </router-link>

      <nav class="space-y-6">
        <div v-for="section in DOCS_SECTIONS" :key="section.slug">
          <p class="mb-2 font-brand-sans text-xs tracking-[0.15em] text-[#9c968b] uppercase">
            {{ section.title[lang] }}
          </p>
          <ul class="space-y-1 border-l border-[#dcd6cc]">
            <li v-for="page in section.pages" :key="page.slug">
              <router-link
                :to="pageHref(section.slug, page.slug)"
                class="docs-nav-item"
                :class="isActive(section.slug, page.slug) ? 'docs-nav-item-active' : ''"
                @click="drawerOpen = false"
              >
                {{ page.title[lang] }}
              </router-link>
            </li>
          </ul>
        </div>
      </nav>
    </aside>

    <!-- Main -->
    <div class="lg:pl-64">
      <slot />
    </div>
  </BlogLayout>
</template>

<style scoped>
.docs-nav-item {
  display: block;
  padding: 0.3rem 0.75rem;
  margin-left: -1px;
  border-left: 2px solid transparent;
  font-family: 'Georgia', 'Times New Roman', Times, serif;
  font-size: 0.95rem;
  line-height: 1.4;
  color: #6b6560;
  text-decoration: none;
  transition: all 0.2s;
}

.docs-nav-item:hover {
  color: #2c2825;
}

.docs-nav-item-active {
  color: #d98c28;
  border-left-color: #d98c28;
  font-weight: 600;
}
</style>
