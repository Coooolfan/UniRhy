import { createRouter, createWebHistory } from 'vue-router'
import { getAuthToken } from '@/ApiInstance'
import { getInitializationStatus } from '@/services/systemInitialization'

const hasPersistedAuthToken = () => {
    const token = getAuthToken()
    return token !== null && token.trim().length > 0
}

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
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
            path: '/',
            name: 'app',
            component: () => import('../views/DashboardView.vue'),
            meta: { requiresAuth: true },
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
                    path: 'albums/:id',
                    name: 'album-detail',
                    component: () => import('../views/AlbumDetailView.vue'),
                },
                {
                    path: 'playlists/:id',
                    name: 'playlist-detail',
                    component: () => import('../views/PlaylistDetailView.vue'),
                },
                {
                    path: 'works/:id',
                    name: 'work-detail',
                    component: () => import('../views/WorkDetailView.vue'),
                },
                {
                    path: 'settings',
                    name: 'settings',
                    component: () => import('../views/SettingsView.vue'),
                },
                {
                    path: 'preferences',
                    name: 'preferences',
                    component: () => import('../views/PreferencesView.vue'),
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
        {
            path: '/:pathMatch(.*)*',
            redirect: '/',
        },
    ],
})

router.beforeEach(async (to) => {
    const isAuthenticated = hasPersistedAuthToken()

    if (!isAuthenticated && to.path === '/login') {
        return true
    }

    if (!isAuthenticated && to.matched.some((record) => record.meta.requiresAuth)) {
        return '/login'
    }

    if (isAuthenticated && to.path === '/login') {
        return '/'
    }

    try {
        const status = await getInitializationStatus()

        if (status.initialized) {
            if (to.path === '/init') {
                return isAuthenticated ? '/' : '/login'
            }
        } else if (to.path !== '/init') {
            return '/init'
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
