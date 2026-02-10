<script setup lang="ts">
import { ChevronRight } from 'lucide-vue-next'
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, normalizeApiError } from '@/ApiInstance'
import { useAudioStore } from '@/stores/audio'

type NavItem = {
    label: string
    routeName?: string
    matchNames?: string[]
}

const router = useRouter()
const route = useRoute()
const audioStore = useAudioStore()
const navItems: NavItem[] = [
    { label: '发现', routeName: 'dashboard-home' },
    { label: '阅览室', routeName: 'album-list', matchNames: ['album-detail'] },
    { label: '任务管理', routeName: 'tasks' },
    { label: '系统设置', routeName: 'settings' },
]
type SidebarPlaylist = {
    id: number
    name: string
}

const playlists = ref<SidebarPlaylist[]>([])
const isLoadingPlaylists = ref(false)
const playlistError = ref('')

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

const fetchPlaylists = async () => {
    isLoadingPlaylists.value = true
    playlistError.value = ''
    try {
        const data = await api.playlistController.listPlaylists()
        playlists.value = data.map((playlist) => ({
            id: playlist.id,
            name: playlist.name?.trim() || '未命名歌单',
        }))
    } catch (error) {
        const normalized = normalizeApiError(error)
        playlistError.value = normalized.message ?? '歌单加载失败'
        playlists.value = []
    } finally {
        isLoadingPlaylists.value = false
    }
}

onMounted(() => {
    fetchPlaylists()
})
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

        <div
            :class="{
                'pb-32': audioStore.currentTrack && !audioStore.isPlayerHidden,
                'pb-8': !audioStore.currentTrack || audioStore.isPlayerHidden,
            }"
        >
            <button
                v-if="!isLoadingPlaylists && !playlistError && playlists.length === 0"
                type="button"
                class="inline-flex items-center gap-2 text-sm text-[#8A857D] hover:text-[#C27E46] transition-colors"
            >
                <span>创建歌单</span>
                <ChevronRight :size="14" aria-hidden="true" />
            </button>
            <template v-else>
                <div
                    class="text-xs text-[#9C968B] uppercase tracking-widest mb-4 border-b border-[#D6D1C7] pb-2"
                >
                    我的歌单
                </div>
                <div v-if="isLoadingPlaylists" class="text-sm text-[#9C968B]">加载中...</div>
                <div v-else-if="playlistError" class="text-sm text-red-500">
                    {{ playlistError }}
                </div>
                <ul v-else class="space-y-3 text-sm text-[#6B665E]">
                    <li
                        v-for="playlist in playlists"
                        :key="playlist.id"
                        class="hover:text-[#C27E46] cursor-pointer transition-colors"
                    >
                        {{ playlist.name }}
                    </li>
                </ul>
            </template>
        </div>
    </aside>
</template>
