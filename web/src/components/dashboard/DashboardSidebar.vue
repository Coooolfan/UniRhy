<script setup lang="ts">
import { ChevronRight, Music, Plus, X } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute, useRouter } from 'vue-router'
import { api, normalizeApiError } from '@/ApiInstance'
import { useDashboardLayout } from '@/composables/useDashboardLayout'
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
    isDesktopViewport,
    isDesktopSidebarCollapsed,
    isMobileSidebarOpen,
    closeSidebar,
    closeMobileSidebar,
} = useDashboardLayout()
const {
    playlists,
    isLoading: isLoadingPlaylists,
    error: playlistError,
} = storeToRefs(playlistStore)
const navItems: NavItem[] = [
    { label: '发现', routeName: 'dashboard-home' },
    { label: '资料库', routeName: 'album-list', matchNames: ['album-detail'] },
    { label: '任务管理', routeName: 'tasks' },
    { label: '系统设置', routeName: 'settings' },
]
const isCreatePlaylistModalOpen = ref(false)
const isCreatingPlaylist = ref(false)
const createPlaylistName = ref('')
const createPlaylistComment = ref('')
const createPlaylistError = ref('')

const playlistSectionPaddingBottom = computed(() => {
    if (!audioStore.currentTrack || audioStore.isPlayerHidden) {
        return '2rem'
    }

    if (isDesktopViewport.value) {
        return '8rem'
    }

    return 'calc(10.5rem + env(safe-area-inset-bottom))'
})

const playlistSectionTransitionStyle = computed(() => {
    const isCollapsingWhilePlayerHiding = audioStore.currentTrack && audioStore.isPlayerHidden
    return {
        paddingBottom: playlistSectionPaddingBottom.value,
        transitionDuration: isCollapsingWhilePlayerHiding ? '520ms' : '420ms',
        transitionDelay: isCollapsingWhilePlayerHiding ? '160ms' : '0ms',
    }
})

const sidebarWrapperClass = computed(() => (isDesktopSidebarCollapsed.value ? 'md:w-0' : 'md:w-64'))

const sidebarPanelClass = computed(() => [
    isMobileSidebarOpen.value ? 'translate-x-0' : '-translate-x-full',
    isDesktopSidebarCollapsed.value ? 'md:-translate-x-full' : 'md:translate-x-0',
])

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
        closeMobileSidebar()
    }
}

const handlePlaylistClick = (playlistId: number) => {
    router.push({
        name: 'playlist-detail',
        params: { id: playlistId },
    })
    closeMobileSidebar()
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
    <Transition
        enter-active-class="transition-opacity duration-200 ease-out"
        enter-from-class="opacity-0"
        enter-to-class="opacity-100"
        leave-active-class="transition-opacity duration-150 ease-in"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0"
    >
        <div
            v-if="isMobileSidebarOpen"
            class="fixed inset-0 z-30 bg-[#2B221B]/40 md:hidden"
            @click="closeMobileSidebar"
        ></div>
    </Transition>

    <div
        class="relative z-40 md:z-20 md:shrink-0 md:transition-[width] md:duration-300 md:ease-out"
        :class="sidebarWrapperClass"
    >
        <aside
            class="fixed inset-y-0 left-0 z-40 flex w-80 max-w-[90vw] flex-col bg-[#EBE7E0] px-8 pb-8 pt-8 shadow-[0_24px_80px_rgba(44,34,27,0.18)] transition-transform duration-300 ease-out md:absolute md:top-0 md:h-full md:w-64 md:max-w-none md:bg-[#EBE7E0] md:px-0 md:pb-0 md:pt-0 md:shadow-none"
            :class="sidebarPanelClass"
        >
            <div class="flex h-full min-h-0 flex-col md:pl-10 md:pr-6 md:pt-12">
                <div class="mb-10 flex items-center justify-between md:mb-12">
                    <h1 class="text-3xl font-serif tracking-tight text-[#2C2C2C]">UniRhy.</h1>
                    <button
                        type="button"
                        class="inline-flex h-9 w-9 items-center justify-center rounded-full text-[#8A857D] transition-colors hover:bg-white/60 hover:text-[#5E5950] md:hidden"
                        aria-label="切换侧边栏"
                        @click="closeSidebar"
                    >
                        <X :size="18" />
                    </button>
                </div>

                <div class="flex min-h-0 flex-1 flex-col">
                    <nav class="flex-1 space-y-4 md:space-y-6">
                        <button
                            v-for="item in navItems"
                            :key="item.label"
                            type="button"
                            class="block w-full text-left text-base transition-colors duration-300 md:text-sm"
                            :class="
                                isActive(item)
                                    ? 'font-medium text-[#C27E46]'
                                    : 'text-[#8A857D] hover:text-[#5E5950]'
                            "
                            @click="handleNavClick(item)"
                        >
                            {{ item.label }}
                        </button>
                    </nav>

                    <div
                        class="mt-10 min-h-0 pr-1 md:mt-10 md:pr-0"
                        :style="playlistSectionTransitionStyle"
                    >
                        <button
                            v-if="!isLoadingPlaylists && !playlistError && playlists.length === 0"
                            type="button"
                            class="inline-flex items-center gap-2 text-sm text-[#8A857D] transition-colors hover:text-[#C27E46]"
                            @click="openCreatePlaylistModal"
                        >
                            <span>创建歌单</span>
                            <ChevronRight :size="14" aria-hidden="true" />
                        </button>
                        <template v-else>
                            <div
                                class="mb-4 flex items-center justify-between border-b border-[#D6D1C7] pb-2"
                            >
                                <span
                                    class="text-sm uppercase tracking-[0.24em] text-[#9C968B] md:text-xs"
                                    >我的歌单</span
                                >
                                <button
                                    v-if="
                                        !isLoadingPlaylists &&
                                        !playlistError &&
                                        playlists.length > 0
                                    "
                                    type="button"
                                    class="inline-flex h-5 w-5 items-center justify-center text-[#8A857D] transition-colors hover:text-[#C27E46]"
                                    aria-label="创建歌单"
                                    @click="openCreatePlaylistModal"
                                >
                                    <Plus :size="14" aria-hidden="true" />
                                </button>
                            </div>
                            <div v-if="isLoadingPlaylists" class="text-sm text-[#9C968B]">
                                加载中...
                            </div>
                            <div v-else-if="playlistError" class="text-sm text-red-500">
                                {{ playlistError }}
                            </div>
                            <ul
                                v-else
                                class="space-y-4 pb-2 text-base text-[#6B665E] md:space-y-3 md:text-sm"
                            >
                                <li
                                    v-for="playlist in playlists"
                                    :key="playlist.id"
                                    class="cursor-pointer transition-colors"
                                    :class="
                                        route.name === 'playlist-detail' &&
                                        Number(route.params.id) === playlist.id
                                            ? 'font-medium text-[#C27E46]'
                                            : 'hover:text-[#C27E46]'
                                    "
                                    @click="handlePlaylistClick(playlist.id)"
                                >
                                    {{ playlist.name }}
                                </li>
                            </ul>
                        </template>
                    </div>
                </div>
            </div>
        </aside>
    </div>

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
