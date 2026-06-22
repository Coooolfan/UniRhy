// Build-time copy of repository-wide markdown documents into the docs content
// tree. Keeps a single source of truth in the original repo locations and
// avoids hand-maintained duplicates.
import fs from 'node:fs'
import path from 'node:path'

const rootDir = path.resolve(import.meta.dirname, '..', '..')
const docsDir = path.resolve(import.meta.dirname, '..', 'content', 'docs')

const sources = [
  {
    from: path.join(rootDir, 'docs', 'TERMINOLOGY.md'),
    to: path.join(docsDir, 'zh', 'reference', 'terminology.md'),
    title: '术语词典',
    description: '同步自仓库根目录的 TERMINOLOGY.md，是项目级术语标准源。',
  },
  {
    from: path.join(rootDir, 'server', 'README', 'PLAYBACK_SYNC_PROTOCOL.md'),
    to: path.join(docsDir, 'zh', 'reference', 'playback-sync-protocol.md'),
    title: '播放同步协议',
    description: '同步自 server/README 的 PLAYBACK_SYNC_PROTOCOL.md，定义 WebSocket 线级协议。',
  },
]

function stripLeadingH1(content) {
  return content.replace(/^\s*#\s+[^\n]*\n+/u, '')
}

function build(meta, body) {
  const sourceRel = path.relative(rootDir, meta.from).split(path.sep).join('/')
  return [
    '---',
    `title: ${meta.title}`,
    `description: ${meta.description}`,
    '---',
    '',
    `> 本页由构建脚本同步自 \`${sourceRel}\`，请勿在 website 内直接编辑——修改请提交到源文件。`,
    '',
    body.trim(),
    '',
  ].join('\n')
}

for (const meta of sources) {
  if (!fs.existsSync(meta.from)) {
    console.warn(`[sync-shared-docs] missing source: ${meta.from}`)
    continue
  }
  const raw = fs.readFileSync(meta.from, 'utf8')
  const body = stripLeadingH1(raw)
  fs.mkdirSync(path.dirname(meta.to), { recursive: true })
  fs.writeFileSync(meta.to, build(meta, body))
  console.log(`[sync-shared-docs] ${meta.from} → ${meta.to}`)
}
