<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const activeTab = ref('发现')
const navItems = ['发现', '阅览室', '收藏', '最近播放']
const playlists = ['雨天巴赫', '咖啡馆噪音', '深夜阅读']

const handleNavClick = (item: string) => {
    activeTab.value = item
    if (item === '发现') {
        router.push({ name: 'dashboard-home' })
    }
}
</script>

<template>
    <aside class="w-64 flex flex-col pt-12 pl-10 pr-6 z-10 md:flex">
        <div class="mb-12">
            <h1 class="text-3xl font-serif tracking-tight text-[#2C2C2C]">UniRhy.</h1>
        </div>

        <nav class="space-y-6 flex-1">
            <div
                v-for="item in navItems"
                :key="item"
                class="text-sm cursor-pointer transition-colors duration-300"
                :class="
                    activeTab === item
                        ? 'text-[#C27E46] font-medium'
                        : 'text-[#8A857D] hover:text-[#5E5950]'
                "
                @click="handleNavClick(item)"
            >
                {{ item }}
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
