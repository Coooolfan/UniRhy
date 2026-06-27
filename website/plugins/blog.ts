import type { Plugin } from 'vite'
import matter from 'gray-matter'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({ html: true, linkify: true, typographer: true })

interface MermaidRenderer {
  render: (id: string, src: string) => Promise<{ svg: string }>
}

let mermaidPromise: Promise<MermaidRenderer> | null = null

function getMermaid(): Promise<MermaidRenderer> {
  if (!mermaidPromise) {
    mermaidPromise = (async () => {
      const { createHTMLWindow } = await import('svgdom')
      const { JSDOM } = await import('jsdom')
      const svgWindow = createHTMLWindow()
      const jsdomWindow = new JSDOM('').window
      Object.assign(globalThis, {
        window: svgWindow,
        document: svgWindow.document,
        CSSStyleSheet: jsdomWindow.CSSStyleSheet,
        DocumentFragment: jsdomWindow.DocumentFragment,
      })
      const mermaidModule = await import('mermaid')
      const mermaid = mermaidModule.default
      mermaid.initialize({
        startOnLoad: false,
        htmlLabels: false,
        flowchart: { htmlLabels: false, curve: 'basis', padding: 16 },
        securityLevel: 'loose',
        theme: 'base',
        themeVariables: {
          fontFamily: "'Georgia', 'Times New Roman', Times, serif",
          fontSize: '15px',
          background: 'transparent',
          primaryColor: '#f5f0e6',
          primaryBorderColor: '#b8721b',
          primaryTextColor: '#2c2825',
          secondaryColor: '#ece4d3',
          secondaryBorderColor: '#b8721b',
          secondaryTextColor: '#2c2825',
          tertiaryColor: '#f0e8d6',
          tertiaryBorderColor: '#b8721b',
          tertiaryTextColor: '#2c2825',
          lineColor: '#8a6a3a',
          edgeLabelBackground: '#f5f0e6',
          nodeBorder: '#b8721b',
          clusterBkg: '#ece4d3',
          clusterBorder: '#dcd6cc',
        },
      })
      return mermaid satisfies MermaidRenderer
    })()
  }
  return mermaidPromise
}

const MERMAID_FENCE = /<pre><code class="language-mermaid">(?<source>[\s\S]*?)<\/code><\/pre>/gu

function decodeEntities(s: string): string {
  return s
    .replaceAll('&lt;', '<')
    .replaceAll('&gt;', '>')
    .replaceAll('&quot;', '"')
    .replaceAll('&#39;', "'")
    .replaceAll('&amp;', '&')
}

const ACC_TITLE = /^\s*accTitle\s*:\s*(?<value>.+)$/mu
const ACC_DESCR = /^\s*accDescr\s*:\s*(?<value>.+)$/mu

function escapeHtmlAttr(s: string): string {
  return s
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

async function renderMermaidBlocks(html: string, fileId: string): Promise<string> {
  const matches = [...html.matchAll(MERMAID_FENCE)]
  if (matches.length === 0) return html
  const mermaid = await getMermaid()
  let result = ''
  let cursor = 0
  for (const [i, m] of matches.entries()) {
    const src = decodeEntities(m.groups?.source ?? '').trim()
    const graphId = `m-${Buffer.from(fileId + String(i))
      .toString('base64url')
      .slice(0, 12)}`
    const { svg } = await mermaid.render(graphId, src)
    const title = ACC_TITLE.exec(src)?.groups?.value.trim() ?? 'Mermaid diagram'
    const descr = ACC_DESCR.exec(src)?.groups?.value.trim() ?? ''
    const descrId = `${graphId}-desc`
    const encodedSrc = Buffer.from(src, 'utf8').toString('base64')
    const ariaDescribedby = descr ? ` aria-describedby="${descrId}"` : ''
    const descrEl = descr ? `<p id="${descrId}" class="sr-only">${escapeHtmlAttr(descr)}</p>` : ''
    const figure =
      `<figure class="mermaid-figure" role="figure" aria-label="${escapeHtmlAttr(title)}"${ariaDescribedby}>` +
      `<button type="button" class="mermaid-copy-btn" data-mermaid-source="${encodedSrc}"` +
      ` aria-label="Copy Mermaid source">` +
      `<span class="mermaid-copy-label" aria-hidden="true">Copy</span>` +
      `</button>` +
      svg +
      descrEl +
      `</figure>`
    const start = m.index
    result += html.slice(cursor, start)
    result += figure
    cursor = start + m[0].length
  }
  result += html.slice(cursor)
  return result
}

export default function blogPlugin(): Plugin {
  let isBuild = false

  return {
    name: 'vite-plugin-blog',
    // eslint-disable-next-line typescript-eslint/prefer-readonly-parameter-types -- Vite API
    config(_cfg, { command }) {
      isBuild = command === 'build'
    },
    async transform(code, id) {
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
      const renderedHtml = md.render(content)
      const html = await renderMermaidBlocks(renderedHtml, id)
      return {
        code: `export const frontmatter = ${JSON.stringify(frontmatter)};\nexport const html = ${JSON.stringify(html)};`,
        map: null,
      }
    },
  }
}
