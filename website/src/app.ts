import { createSSRApp, type App as VueApp } from 'vue'
import { createMemoryHistory, createRouter, createWebHistory, type Router } from 'vue-router'
import App from './App.vue'
import { routes } from './router/routes'

export interface AppContext {
  app: VueApp<Element>
  router: Router
}

export function createApp(): AppContext {
  const app = createSSRApp(App)
  const router = createRouter({
    history: import.meta.env.SSR ? createMemoryHistory() : createWebHistory(),
    routes,
  })
  app.use(router)
  return { app, router }
}
