import { initPlatformRuntime } from './runtime/platform'
import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

await initPlatformRuntime()

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
