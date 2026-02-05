<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'

type NavItem = {
    label: string
    routeName?: string
    matchNames?: string[]
}

const router = useRouter()
const route = useRoute()
const navItems: NavItem[] = [
    { label: '发现', routeName: 'dashboard-home' },
    { label: '阅览室', routeName: 'album-list', matchNames: ['album-detail'] },
    { label: '任务管理', routeName: 'tasks' },
    { label: '系统设置', routeName: 'settings' },
]
const playlists = ['雨天巴赫', '咖啡馆噪音', '深夜阅读']

const isActive = (item: NavItem) => {
    if (!item.routeName) {
        return false
    }
    const currentName = route.name?.toString()
    return currentName === item.routeName || item.matchNames?.includes(currentName || '')
}

const handleNavClick = (item: NavItem) => {
    if (item.routeName) {
        router.push({ name: item.routeName })
    }
}
</script>

<template>
    <aside class="w-64 flex flex-col pt-12 pl-10 pr-6 z-10 md:flex bg-[#EBE7E0]">
        <div class="mb-12">
            <h1 class="text-3xl font-serif tracking-tight text-[#2C2C2C]">UniRhy.</h1>
        </div>

        <nav class="space-y-6 flex-1">
            <div
                v-for="item in navItems"
                :key="item.label"
                class="text-sm cursor-pointer transition-colors duration-300"
                :class="
                    isActive(item)
                        ? 'text-[#C27E46] font-medium'
                        : 'text-[#8A857D] hover:text-[#5E5950]'
                "
                @click="handleNavClick(item)"
            >
                {{ item.label }}
            </div>
        </nav>

        <div class="pb-32">
            <div
                class="text-xs text-[#9C968B] uppercase tracking-widest mb-4 border-b border-[#D6D1C7] pb-2"
            >
                我的歌单
            </div>
            <ul class="space-y-3 text-sm text-[#6B665E]">
                <li
                    v-for="playlist in playlists"
                    :key="playlist"
                    class="hover:text-[#C27E46] cursor-pointer transition-colors"
                >
                    {{ playlist }}
                </li>
            </ul>
        </div>
    </aside>
</template>
