export interface BlogFrontmatter {
  title: string
  description: string
  publishAt: number
  cover?: string
  draft: boolean
}

export interface BlogModule {
  frontmatter: BlogFrontmatter
  html: string
}

export interface BlogPostMeta extends BlogFrontmatter {
  slug: string
}
