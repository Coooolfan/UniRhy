import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        {
            path: '/',
            redirect: '/login',
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
            ],
        },
    ],
})

export default router
