import * as fs from 'node:fs'
import path from 'node:path'
import { SUPPORTED_LANGS } from '@/router/routes'

const HERE = import.meta.dirname
const CONTENT_ROOT = path.resolve(HERE, '../../content/blog')

export interface PrerenderPath {
  pathname: string
  lang: 'zh' | 'en' | null
  slug: string | null
}

function listSlugs(lang: 'zh' | 'en'): string[] {
  const dir = path.join(CONTENT_ROOT, lang)
  if (!fs.existsSync(dir)) return []
  return fs
    .readdirSync(dir)
    .filter((f) => f.endsWith('.md'))
    .map((f) => f.replace(/\.md$/u, ''))
}

export function listPrerenderPaths(): PrerenderPath[] {
  const paths: PrerenderPath[] = [{ pathname: '/', lang: null, slug: null }]
  for (const lang of SUPPORTED_LANGS) {
    paths.push({ pathname: `/${lang}/blog`, lang, slug: null })
    for (const slug of listSlugs(lang)) {
      paths.push({ pathname: `/${lang}/blog/${slug}`, lang, slug })
    }
  }
  return paths
}

export function slugExistsForLang(slug: string, lang: 'zh' | 'en'): boolean {
  return listSlugs(lang).includes(slug)
}
