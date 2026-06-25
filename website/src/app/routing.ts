import * as fs from 'node:fs'
import path from 'node:path'
import { SUPPORTED_LANGS } from '@/router/routes'

const HERE = import.meta.dirname
const BLOG_ROOT = path.resolve(HERE, '../../content/blog')
const DOCS_ROOT = path.resolve(HERE, '../../content/docs')

export interface PrerenderPath {
  pathname: string
  lang: 'zh' | 'en' | null
  slug: string | null
}

function listBlogSlugs(lang: 'zh' | 'en'): string[] {
  const dir = path.join(BLOG_ROOT, lang)
  if (!fs.existsSync(dir)) return []
  return fs
    .readdirSync(dir)
    .filter((f) => f.endsWith('.md'))
    .map((f) => f.replace(/\.md$/u, ''))
}

interface DocsPath {
  section: string
  slug: string
}

function listDocsPaths(lang: 'zh' | 'en'): DocsPath[] {
  const langDir = path.join(DOCS_ROOT, lang)
  if (!fs.existsSync(langDir)) return []
  const out: DocsPath[] = []
  for (const section of fs.readdirSync(langDir)) {
    const sectionDir = path.join(langDir, section)
    if (!fs.statSync(sectionDir).isDirectory()) continue
    for (const file of fs.readdirSync(sectionDir)) {
      if (!file.endsWith('.md')) continue
      out.push({ section, slug: file.replace(/\.md$/u, '') })
    }
  }
  return out
}

export function listPrerenderPaths(): PrerenderPath[] {
  const paths: PrerenderPath[] = [{ pathname: '/', lang: null, slug: null }]
  for (const lang of SUPPORTED_LANGS) {
    paths.push({ pathname: `/${lang}/blog`, lang, slug: null })
    for (const slug of listBlogSlugs(lang)) {
      paths.push({ pathname: `/${lang}/blog/${slug}`, lang, slug })
    }
    for (const doc of listDocsPaths(lang)) {
      paths.push({
        pathname: `/${lang}/docs/${doc.section}/${doc.slug}`,
        lang,
        slug: `${doc.section}/${doc.slug}`,
      })
    }
  }
  return paths
}

export function slugExistsForLang(slug: string, lang: 'zh' | 'en'): boolean {
  return listBlogSlugs(lang).includes(slug)
}
