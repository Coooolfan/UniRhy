export interface DocsFrontmatter {
  title: string
  description?: string
  draft?: boolean
}

export interface DocsModule {
  frontmatter: DocsFrontmatter
  html: string
}
