<script setup lang="ts">
import { ChevronRight, Music, Plus } from 'lucide-vue-next'
import { onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute, useRouter } from 'vue-router'
import { api, normalizeApiError } from '@/ApiInstance'
import { useAudioStore } from '@/stores/audio'
import { usePlaylistStore } from '@/stores/playlist'

type NavItem = {
    label: string
    routeName?: string
    matchNames?: string[]
}

const router = useRouter()
const route = useRoute()
const audioStore = useAudioStore()
const playlistStore = usePlaylistStore()
const {
    playlists,
    isLoading: isLoadingPlaylists,
    error: playlistError,
} = storeToRefs(playlistStore)
const navItems: NavItem[] = [
    { label: '发现', routeName: 'dashboard-home' },
    { label: '阅览室', routeName: 'album-list', matchNames: ['album-detail'] },
    { label: '任务管理', routeName: 'tasks' },
    { label: '系统设置', routeName: 'settings' },
]
const isCreatePlaylistModalOpen = ref(false)
const isCreatingPlaylist = ref(false)
const createPlaylistName = ref('')
const createPlaylistComment = ref('')
const createPlaylistError = ref('')

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

const resetCreatePlaylistForm = () => {
    createPlaylistName.value = ''
    createPlaylistComment.value = ''
    createPlaylistError.value = ''
}

const openCreatePlaylistModal = () => {
    if (isCreatingPlaylist.value) {
        return
    }
    resetCreatePlaylistForm()
    isCreatePlaylistModalOpen.value = true
}

const closeCreatePlaylistModal = () => {
    if (isCreatingPlaylist.value) {
        return
    }
    isCreatePlaylistModalOpen.value = false
    resetCreatePlaylistForm()
}

const submitCreatePlaylist = async () => {
    const name = createPlaylistName.value.trim()
    if (!name) {
        createPlaylistError.value = '请输入歌单名称'
        return
    }
    if (isCreatingPlaylist.value) {
        return
    }
    isCreatingPlaylist.value = true
    createPlaylistError.value = ''
    try {
        await api.playlistController.createPlaylist({
            body: {
                name,
                comment: createPlaylistComment.value.trim(),
            },
        })
        isCreatePlaylistModalOpen.value = false
        resetCreatePlaylistForm()
        await playlistStore.fetchPlaylists(true)
    } catch (error) {
        const normalized = normalizeApiError(error)
        createPlaylistError.value = normalized.message ?? '创建歌单失败'
    } finally {
        isCreatingPlaylist.value = false
    }
}

onMounted(() => {
    void playlistStore.fetchPlaylists()
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
                @click="openCreatePlaylistModal"
            >
                <span>创建歌单</span>
                <ChevronRight :size="14" aria-hidden="true" />
            </button>
            <template v-else>
                <div class="mb-4 flex items-center justify-between border-b border-[#D6D1C7] pb-2">
                    <span class="text-xs text-[#9C968B] uppercase tracking-widest">我的歌单</span>
                    <button
                        v-if="!isLoadingPlaylists && !playlistError && playlists.length > 0"
                        type="button"
                        class="inline-flex h-5 w-5 items-center justify-center text-[#8A857D] hover:text-[#C27E46] transition-colors"
                        aria-label="创建歌单"
                        @click="openCreatePlaylistModal"
                    >
                        <Plus :size="14" aria-hidden="true" />
                    </button>
                </div>
                <div v-if="isLoadingPlaylists" class="text-sm text-[#9C968B]">加载中...</div>
                <div v-else-if="playlistError" class="text-sm text-red-500">
                    {{ playlistError }}
                </div>
                <ul v-else class="space-y-3 text-sm text-[#6B665E]">
                    <li
                        v-for="playlist in playlists"
                        :key="playlist.id"
                        class="cursor-pointer transition-colors"
                        :class="
                            route.name === 'playlist-detail' &&
                            Number(route.params.id) === playlist.id
                                ? 'text-[#C27E46] font-medium'
                                : 'hover:text-[#C27E46]'
                        "
                        @click="
                            router.push({
                                name: 'playlist-detail',
                                params: { id: playlist.id },
                            })
                        "
                    >
                        {{ playlist.name }}
                    </li>
                </ul>
            </template>
        </div>
    </aside>

    <Teleport to="body">
        <Transition
            enter-active-class="transition duration-200 ease-out"
            enter-from-class="opacity-0"
            enter-to-class="opacity-100"
            leave-active-class="transition duration-150 ease-in"
            leave-from-class="opacity-100"
            leave-to-class="opacity-0"
        >
            <div
                v-if="isCreatePlaylistModalOpen"
                class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#2B221B]/60"
                @click.self="closeCreatePlaylistModal"
            >
                <div
                    class="bg-[#fffcf5] p-8 w-full max-w-md shadow-[0_8px_30px_rgba(0,0,0,0.12)] border border-[#EAE6DE] relative transform transition-all"
                >
                    <div
                        class="absolute top-0 right-0 w-16 h-16 bg-linear-to-bl from-[#EAE6DE]/30 to-transparent pointer-events-none"
                    ></div>

                    <div class="mb-8 text-center">
                        <div
                            class="inline-flex items-center justify-center w-12 h-12 rounded-full bg-[#FAF9F6] mb-4 border border-[#EAE6DE]"
                        >
                            <Music :size="24" class="text-[#C67C4E]" />
                        </div>
                        <h3 class="font-serif text-2xl text-[#2B221B]">创建新歌单</h3>
                        <p class="text-xs text-[#8A8A8A] mt-2 font-serif italic">
                            Create New Playlist
                        </p>
                    </div>

                    <div class="space-y-6">
                        <label class="block">
                            <span
                                class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                                >Name</span
                            >
                            <input
                                v-model="createPlaylistName"
                                type="text"
                                maxlength="100"
                                class="w-full bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE]"
                                placeholder="e.g. Commute Daily"
                                :disabled="isCreatingPlaylist"
                            />
                        </label>

                        <label class="block">
                            <span
                                class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                            >
                                Description
                            </span>
                            <textarea
                                v-model="createPlaylistComment"
                                rows="3"
                                maxlength="500"
                                class="w-full resize-none bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE]"
                                placeholder="Optional short note for this playlist"
                                :disabled="isCreatingPlaylist"
                            />
                        </label>

                        <p v-if="createPlaylistError" class="text-sm text-[#B95D5D]">
                            {{ createPlaylistError }}
                        </p>

                        <div class="flex gap-3 mt-8 pt-6 border-t border-[#EAE6DE]">
                            <button
                                type="button"
                                class="flex-1 px-4 py-2.5 border border-[#D6D1C4] text-[#8A8A8A] hover:bg-[#F7F5F0] hover:text-[#5A5A5A] transition-colors text-sm uppercase tracking-wide"
                                :disabled="isCreatingPlaylist"
                                @click="closeCreatePlaylistModal"
                            >
                                取消
                            </button>
                            <button
                                type="button"
                                class="flex-1 px-4 py-2.5 bg-[#2B221B] text-[#F7F5F0] hover:bg-[#C67C4E] transition-colors text-sm uppercase tracking-wide shadow-md disabled:opacity-60 disabled:cursor-not-allowed"
                                :disabled="isCreatingPlaylist"
                                @click="submitCreatePlaylist"
                            >
                                <span v-if="isCreatingPlaylist">Creating...</span>
                                <span v-else>创建歌单</span>
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </Transition>
    </Teleport>
</template>
