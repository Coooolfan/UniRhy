import { createHead } from '@unhead/vue/client'
import { createApp } from './app'
import './style/main.css'

const { app, router } = createApp()
app.use(createHead())

await router.isReady()
app.mount('#app')
