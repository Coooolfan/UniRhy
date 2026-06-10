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
      zh: '了解 UniRhy 的定位、整体架构与关键概念。',
      en: 'Learn what UniRhy is, its architecture and core concepts.',
    },
    pages: [
      {
        slug: 'what-is-unirhy',
        title: { zh: 'UniRhy 是什么', en: 'What is UniRhy' },
      },
      {
        slug: 'architecture',
        title: { zh: '整体架构', en: 'Architecture' },
      },
      {
        slug: 'concepts',
        title: { zh: '关键概念', en: 'Concepts' },
      },
    ],
  },
  {
    slug: 'install',
    title: { zh: '部署', en: 'Install' },
    description: {
      zh: '自托管部署指南，覆盖 Docker、JAR、数据库与反向代理。',
      en: 'Self-hosted deployment guide for Docker, JAR, database and reverse proxy.',
    },
    pages: [
      {
        slug: 'docker',
        title: { zh: 'Docker 部署', en: 'Docker' },
      },
      {
        slug: 'jar',
        title: { zh: 'JAR 部署', en: 'JAR' },
      },
      {
        slug: 'database',
        title: { zh: '数据库准备', en: 'Database' },
      },
      {
        slug: 'reverse-proxy',
        title: { zh: '反向代理', en: 'Reverse Proxy' },
      },
      {
        slug: 'upgrade',
        title: { zh: '版本升级', en: 'Upgrade' },
      },
    ],
  },
  {
    slug: 'usage',
    title: { zh: '使用', en: 'Usage' },
    description: {
      zh: '从首次启动到日常使用的功能说明。',
      en: 'From first run to daily usage of every feature.',
    },
    pages: [
      {
        slug: 'first-run',
        title: { zh: '首次启动', en: 'First Run' },
      },
      {
        slug: 'library',
        title: { zh: '音乐库与存储节点', en: 'Library & Storage' },
      },
      {
        slug: 'playback-sync',
        title: { zh: '多设备播放同步', en: 'Playback Sync' },
      },
      {
        slug: 'playlists',
        title: { zh: '歌单与专辑', en: 'Playlists & Albums' },
      },
      {
        slug: 'accounts',
        title: { zh: '账号管理', en: 'Accounts' },
      },
      {
        slug: 'clients',
        title: { zh: '客户端', en: 'Clients' },
      },
    ],
  },
  {
    slug: 'reference',
    title: { zh: '参考', en: 'Reference' },
    description: {
      zh: '面向集成者的配置、API、协议与插件开发文档。',
      en: 'Configuration, API, protocol and plugin development reference for integrators.',
    },
    pages: [
      {
        slug: 'configuration',
        title: { zh: '配置参考', en: 'Configuration' },
      },
      {
        slug: 'rest-api',
        title: { zh: 'REST API', en: 'REST API' },
      },
      {
        slug: 'playback-sync-protocol',
        title: { zh: '播放同步协议', en: 'Playback Sync Protocol' },
      },
      {
        slug: 'plugin-development',
        title: { zh: '插件开发', en: 'Plugin Development' },
      },
      {
        slug: 'terminology',
        title: { zh: '术语词典', en: 'Terminology' },
      },
    ],
  },
  {
    slug: 'contribute',
    title: { zh: '贡献', en: 'Contribute' },
    description: {
      zh: '参与 UniRhy 开发与发布流程。',
      en: 'Participate in UniRhy development and release process.',
    },
    pages: [
      {
        slug: 'overview',
        title: { zh: '贡献指南', en: 'Overview' },
      },
      {
        slug: 'release-process',
        title: { zh: '发布流程', en: 'Release Process' },
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
