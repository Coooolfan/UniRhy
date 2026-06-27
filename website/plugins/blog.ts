import type { Plugin } from 'vite'
import matter from 'gray-matter'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({ html: true, linkify: true, typographer: true })

export default function blogPlugin(): Plugin {
  let isBuild = false

  return {
    name: 'vite-plugin-blog',
    // eslint-disable-next-line typescript-eslint/prefer-readonly-parameter-types -- Vite API
    config(_cfg, { command }) {
      isBuild = command === 'build'
    },
    transform(code, id) {
      if (!id.endsWith('.md')) return null
      const { data, content } = matter(code)
      if (isBuild && data.draft) {
        return {
          code: `export const frontmatter = ${JSON.stringify({ draft: true })};\nexport const html = "";`,
          map: null,
        }
      }
      for (const [k, v] of Object.entries(data)) {
        if (v instanceof Date) {
          data[k] = Math.floor(v.getTime() / 1000)
        }
      }
      const frontmatter = data
      const html = md.render(content)
      return {
        code: `export const frontmatter = ${JSON.stringify(frontmatter)};\nexport const html = ${JSON.stringify(html)};`,
        map: null,
      }
    },
  }
}
