import { renderToString } from 'vue/server-renderer'
import { renderSSRHead } from '@unhead/ssr'
import { createHead } from '@unhead/vue/server'
import { createApp } from './app'

export { listPrerenderPaths } from './app/routing'

export interface RenderedPage {
  appHtml: string
  head: Awaited<ReturnType<typeof renderSSRHead>>
  pathname: string
}

export async function renderPage(pathname: string): Promise<RenderedPage> {
  const { app, router } = createApp()
  const head = createHead()
  app.use(head)
  await router.push(pathname)
  await router.isReady()
  const appHtml = await renderToString(app)
  const headPayload = await renderSSRHead(head)
  return { appHtml, head: headPayload, pathname }
}
