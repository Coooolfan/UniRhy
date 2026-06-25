import type { SupportedLang } from '@/router/routes'

export interface DocsPage {
  slug: string
  title: Record<SupportedLang, string>
}

export interface DocsSection {
  slug: string
  title: Record<SupportedLang, string>
  description: Record<SupportedLang, string>
  pages: readonly DocsPage[]
}

export const DOCS_SECTIONS: readonly DocsSection[] = [
  {
    slug: 'intro',
    title: { zh: '入门', en: 'Introduction' },
    description: {
      zh: '了解 UniRhy 的定位与整体架构。',
      en: 'Learn what UniRhy is and how it is structured.',
    },
    pages: [
      {
        slug: 'about',
        title: { zh: '关于 UniRhy', en: 'About UniRhy' },
      },
    ],
  },
  {
    slug: 'install',
    title: { zh: '部署', en: 'Install' },
    description: {
      zh: '自托管部署指南，覆盖 Docker 与数据库。',
      en: 'Self-hosted deployment guide for Docker and the database.',
    },
    pages: [
      {
        slug: 'docker',
        title: { zh: 'Docker 部署', en: 'Docker' },
      },
      {
        slug: 'database',
        title: { zh: '自有数据库', en: 'Bring Your Own Database' },
      },
    ],
  },
  {
    slug: 'usage',
    title: { zh: '使用', en: 'Usage' },
    description: {
      zh: '从首次启动开始的使用说明。',
      en: 'Usage guides starting from first run.',
    },
    pages: [
      {
        slug: 'first-run',
        title: { zh: '首次启动', en: 'First Run' },
      },
    ],
  },
] as const

export function findSection(slug: string): DocsSection | undefined {
  // eslint-disable-next-line typescript-eslint/prefer-readonly-parameter-types -- Array.find callback parameter
  return DOCS_SECTIONS.find((s) => s.slug === slug)
}

export function findPage(
  sectionSlug: string,
  pageSlug: string,
): { section: DocsSection; page: DocsPage } | undefined {
  const section = findSection(sectionSlug)
  if (!section) return undefined
  // eslint-disable-next-line typescript-eslint/prefer-readonly-parameter-types -- Array.find callback parameter
  const page = section.pages.find((p) => p.slug === pageSlug)
  if (!page) return undefined
  return { section, page }
}

export interface FlatDocsPage {
  sectionSlug: string
  pageSlug: string
  title: Record<SupportedLang, string>
}

export function flattenDocs(): FlatDocsPage[] {
  return DOCS_SECTIONS.flatMap(
    // eslint-disable-next-line typescript-eslint/prefer-readonly-parameter-types -- Array.flatMap callback parameter
    (section) =>
      // eslint-disable-next-line typescript-eslint/prefer-readonly-parameter-types -- Array.map callback parameter
      section.pages.map((page) => ({
        sectionSlug: section.slug,
        pageSlug: page.slug,
        title: page.title,
      })),
  )
}
