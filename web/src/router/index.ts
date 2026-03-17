import { createRouter, createWebHistory } from 'vue-router'
import { api, getAuthToken } from '@/ApiInstance'

const hasPersistedAuthToken = () => {
    const token = getAuthToken()
    return token !== null && token.trim().length > 0
}

const requiresAuth = (path: string) => path.startsWith('/dashboard')

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        {
            path: '/',
            redirect: () => (hasPersistedAuthToken() ? '/dashboard' : '/login'),
        },
        {
            path: '/init',
            name: 'init',
            component: () => import('../views/InitView.vue'),
        },
        {
            path: '/login',
            name: 'login',
            component: () => import('../views/LoginView.vue'),
        },
        {
            path: '/dashboard',
            name: 'dashboard',
            component: () => import('../views/DashboardView.vue'),
            children: [
                {
                    path: 'search',
                    name: 'search',
                    component: () => import('../views/SearchView.vue'),
                },
                {
                    path: '',
                    name: 'dashboard-home',
                    component: () => import('../views/DashboardHome.vue'),
                },
                {
                    path: 'albums',
                    name: 'album-list',
                    component: () => import('../views/AlbumListView.vue'),
                },
                {
                    path: 'album/:id',
                    name: 'album-detail',
                    component: () => import('../views/AlbumDetailView.vue'),
                },
                {
                    path: 'playlist/:id',
                    name: 'playlist-detail',
                    component: () => import('../views/PlaylistDetailView.vue'),
                },
                {
                    path: 'work/:id',
                    name: 'work-detail',
                    component: () => import('../views/WorkDetailView.vue'),
                },
                {
                    path: 'settings',
                    name: 'settings',
                    component: () => import('../views/SettingsView.vue'),
                },
                {
                    path: 'tasks',
                    name: 'tasks',
                    component: () => import('../views/TasksView.vue'),
                },
                {
                    path: 'playback-sync-debug',
                    name: 'playback-sync-debug',
                    component: () => import('../views/PlaybackSyncDebugView.vue'),
                },
            ],
        },
    ],
})

router.beforeEach(async (to) => {
    try {
        const status = await api.systemConfigController.isInitialized()
        if (!status.initialized) {
            if (to.path !== '/init') {
                return '/init'
            }
        } else {
            if (to.path === '/init') {
                return '/login'
            }
        }

        const isAuthenticated = hasPersistedAuthToken()

        if (isAuthenticated && to.path === '/login') {
            return '/dashboard'
        }

        if (!isAuthenticated && requiresAuth(to.path)) {
            return '/login'
        }

        return true
    } catch (error) {
        console.error('Failed to check initialization status', error)
        // If check fails, maybe let them proceed or show error page?
        // For now let's proceed to avoid infinite loops if API is down
        return true
    }
})

export default router
