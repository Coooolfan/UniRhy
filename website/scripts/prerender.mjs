/* eslint-disable typescript-eslint/prefer-readonly-parameter-types, eslint-plugin-unicorn/no-useless-collection-argument -- Build-time Node script */
import fs from 'node:fs'
import path from 'node:path'
import { pathToFileURL } from 'node:url'

const rootDir = path.resolve(import.meta.dirname, '..')
const distDir = path.join(rootDir, 'dist')
const manifestPath = path.join(distDir, '.vite', 'manifest.json')
const serverEntryPath = path.join(distDir, 'server', 'entry-server.js')

const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'))
const serverEntry = await import(pathToFileURL(serverEntryPath).href)

function escapeHtml(value) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
}

function ensurePosixPath(value) {
  return value.split(path.sep).join('/')
}

function findClientEntry() {
  const entries = Object.entries(manifest).filter(([, chunk]) => chunk.isEntry)
  const preferred = entries.find(
    ([key, chunk]) => key === 'src/entry-client.ts' || chunk.src === 'src/entry-client.ts',
  )
  if (preferred) return preferred[1]
  if (entries.length === 0) {
    throw new Error('Unable to locate client entry in manifest')
  }
  return entries[0][1]
}

function collectCssFiles(chunk, seen = new Set()) {
  const cssFiles = new Set(chunk.css ?? [])
  for (const imported of chunk.imports ?? []) {
    if (seen.has(imported)) continue
    seen.add(imported)
    const importedChunk = manifest[imported]
    if (!importedChunk) continue
    for (const cssFile of collectCssFiles(importedChunk, seen)) {
      cssFiles.add(cssFile)
    }
  }
  return cssFiles
}

function renderHead(headPayload, clientEntry) {
  const cssLinks = [...collectCssFiles(clientEntry)]
    .map((href) => `<link rel="stylesheet" href="/${escapeHtml(ensurePosixPath(href))}">`)
    .join('\n    ')

  const baseHead = ['<link href="/fonts/barlow-condensed.css" rel="stylesheet">', cssLinks]
    .filter(Boolean)
    .join('\n    ')

  return `${headPayload.headTags}\n    ${baseHead}`
}

function renderDocument({ appHtml, head }, clientEntry) {
  const htmlAttrs = (head.htmlAttrs ?? '').trim()
  const bodyAttrs = (head.bodyAttrs ?? '').trim()
  return `<!doctype html>
<html${htmlAttrs ? ` ${htmlAttrs}` : ''}>
  <head>
    ${renderHead(head, clientEntry)}
  </head>
  <body${bodyAttrs ? ` ${bodyAttrs}` : ''}>
    ${head.bodyTagsOpen ?? ''}
    <div id="app">${appHtml}</div>
    ${head.bodyTags ?? ''}
    <script type="module" crossorigin src="/${escapeHtml(ensurePosixPath(clientEntry.file))}"></script>
  </body>
</html>
`
}

function resolveOutputFile(pathname) {
  if (pathname === '/') return path.join(distDir, 'index.html')
  return path.join(distDir, pathname.replace(/^\//, ''), 'index.html')
}

function writeHtmlFile(targetFile, html) {
  fs.mkdirSync(path.dirname(targetFile), { recursive: true })
  fs.writeFileSync(targetFile, html)
}

const clientEntry = findClientEntry()
const pages = serverEntry.listPrerenderPaths()

for (const page of pages) {
  const rendered = await serverEntry.renderPage(page.pathname)
  const outputFile = resolveOutputFile(page.pathname)
  writeHtmlFile(outputFile, renderDocument(rendered, clientEntry))
}

if (!process.env.KEEP_SSR_BUNDLE) {
  fs.rmSync(path.join(distDir, 'server'), { recursive: true, force: true })
}
console.log(`Prerendered ${pages.length} pages.`)
