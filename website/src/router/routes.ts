import type { RouteRecordRaw } from 'vue-router'

export const SUPPORTED_LANGS = ['zh', 'en'] as const
export type SupportedLang = (typeof SUPPORTED_LANGS)[number]
export const DEFAULT_LANG: SupportedLang = 'zh'

export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/HomeView.vue'),
  },
  {
    path: '/:lang(zh|en)/blog',
    name: 'blog-list',
    component: () => import('@/views/BlogListView.vue'),
  },
  {
    path: '/:lang(zh|en)/blog/:slug',
    name: 'blog-post',
    component: () => import('@/views/BlogPostView.vue'),
  },
  {
    path: '/blog',
    redirect: `/${DEFAULT_LANG}/blog`,
  },
  {
    path: '/blog/:slug',
    // eslint-disable-next-line typescript-eslint/prefer-readonly-parameter-types -- vue-router redirect callback signature
    redirect: (to) => {
      const raw = to.params.slug
      const slug = Array.isArray(raw) ? raw[0] : raw
      return `/${DEFAULT_LANG}/blog/${slug}`
    },
  },
]
