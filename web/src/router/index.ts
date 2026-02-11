import { createRouter, createWebHistory } from 'vue-router'
import { api } from '@/ApiInstance'

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        {
            path: '/',
            redirect: '/login',
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
                    component: () => import('../views/SongListDetailView.vue'),
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
            ],
        },
    ],
})

router.beforeEach(async (to, from, next) => {
    try {
        const status = await api.systemConfigController.isInitialized()
        if (!status.initialized) {
            if (to.path !== '/init') {
                next('/init')
                return
            }
        } else {
            if (to.path === '/init') {
                next('/login')
                return
            }
        }
        next()
    } catch (error) {
        console.error('Failed to check initialization status', error)
        // If check fails, maybe let them proceed or show error page?
        // For now let's proceed to avoid infinite loops if API is down
        next()
    }
})

export default router
