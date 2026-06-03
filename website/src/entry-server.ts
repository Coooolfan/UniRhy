import { renderToString } from 'vue/server-renderer'
import { createHead } from '@unhead/vue/server'
import { createApp } from './app'

export { listPrerenderPaths } from './app/routing'

type RenderedHead = Awaited<ReturnType<ReturnType<typeof createHead>['render']>>

export interface RenderedPage {
  appHtml: string
  head: RenderedHead
  pathname: string
}

export async function renderPage(pathname: string): Promise<RenderedPage> {
  const { app, router } = createApp()
  const head = createHead()
  app.use(head)
  await router.push(pathname)
  await router.isReady()
  const appHtml = await renderToString(app)
  const headPayload = head.render()
  return { appHtml, head: headPayload, pathname }
}
