<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Play, Pause } from 'lucide-vue-next'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import StackedCovers from '@/components/StackedCovers.vue'
import { api, normalizeApiError } from '@/ApiInstance'
import { useAudioStore } from '@/stores/audio'

const route = useRoute()
const router = useRouter()
const audioStore = useAudioStore()

const searchQuery = ref('')
const isLoading = ref(false)
const searchTabs = ['All', 'Artists', 'Albums', 'Works'] as const
type SearchTab = (typeof searchTabs)[number]
const activeTab = ref<SearchTab>('All')

type SearchResultItem = {
    id: number | string
    type: 'album' | 'work' | 'artist'
    title: string
    subtitle: string
    details: string
    cover: string
    isEmoji?: boolean
    badge?: string
    stackedImages?: { id: number | string; cover?: string }[]
    playTrackId?: number
    playTitle?: string
    playArtist?: string
    playCover?: string
    playSrc?: string
    playWorkId?: number
}

// Data states
const artists = ref<SearchResultItem[]>([])
const albums = ref<SearchResultItem[]>([])
const works = ref<SearchResultItem[]>([])
const playLoadingItemId = ref<number | string | null>(null)
type SelectedWorkOption = {
    id: number
    title: string
    subtitle: string
}

const selectedWorkIds = ref<Set<number>>(new Set())
const selectedWorks = ref<Map<number, SelectedWorkOption>>(new Map())
const mergeModalOpen = ref(false)
const mergeTargetWorkId = ref<number | null>(null)
const mergeModalError = ref('')
const mergeSubmitting = ref(false)

const selectedWorkOptions = computed(() => Array.from(selectedWorks.value.values()))
const hasSelectedWorks = computed(() => selectedWorkOptions.value.length > 0)
const canSubmitMerge = computed(
    () =>
        !mergeSubmitting.value &&
        selectedWorkOptions.value.length >= 2 &&
        mergeTargetWorkId.value !== null,
)

const isWorkSelected = (item: SearchResultItem) =>
    item.type === 'work' && typeof item.id === 'number' && selectedWorkIds.value.has(item.id)

const toggleWorkSelection = (item: SearchResultItem) => {
    if (item.type !== 'work' || typeof item.id !== 'number') {
        return
    }

    const newSet = new Set(selectedWorkIds.value)
    const newSelectedWorks = new Map(selectedWorks.value)
    if (newSet.has(item.id)) {
        newSet.delete(item.id)
        newSelectedWorks.delete(item.id)
    } else {
        newSet.add(item.id)
        newSelectedWorks.set(item.id, {
            id: item.id,
            title: item.title,
            subtitle: item.subtitle,
        })
    }

    selectedWorkIds.value = newSet
    selectedWorks.value = newSelectedWorks
}

const openMergeModal = () => {
    if (selectedWorkOptions.value.length === 0) {
        return
    }

    mergeModalError.value = ''
    mergeTargetWorkId.value = selectedWorkOptions.value[0]?.id ?? null
    mergeModalOpen.value = true
}

const closeMergeModal = () => {
    if (mergeSubmitting.value) {
        return
    }
    mergeModalOpen.value = false
    mergeModalError.value = ''
    mergeTargetWorkId.value = null
}

const submitMerge = async () => {
    if (selectedWorkOptions.value.length < 2) {
        mergeModalError.value = '至少选择 2 个作品后才能合并。'
        return
    }

    if (mergeTargetWorkId.value === null) {
        mergeModalError.value = '请选择一个目标作品。'
        return
    }

    const sourceWorkIds = selectedWorkOptions.value
        .map((work) => work.id)
        .filter((id) => id !== mergeTargetWorkId.value)

    if (sourceWorkIds.length === 0) {
        mergeModalError.value = '请选择至少一个要合并的来源作品。'
        return
    }

    mergeSubmitting.value = true
    mergeModalError.value = ''
    try {
        await api.workController.mergeWork({
            body: {
                targetId: mergeTargetWorkId.value,
                needMergeIds: sourceWorkIds,
            },
        })

        selectedWorkIds.value = new Set()
        selectedWorks.value = new Map()
        mergeModalOpen.value = false
        mergeTargetWorkId.value = null
        await performSearch(searchQuery.value)
    } catch (error) {
        const normalized = normalizeApiError(error)
        mergeModalError.value = normalized.message ?? '合并作品失败。'
    } finally {
        mergeSubmitting.value = false
    }
}

// Helper functions (reused from AlbumListView)
const resolveCover = (coverId?: number) => (coverId ? `/api/media/${coverId}` : '')

type Asset = {
    mediaFile: {
        id: number
        mimeType: string
    }
}

const resolveAudio = (assets: readonly Asset[]) => {
    const audioAsset = assets.find((asset) => asset.mediaFile.mimeType.startsWith('audio/'))
    return audioAsset ? `/api/media/${audioAsset.mediaFile.id}` : undefined
}

type Artist = {
    name?: string
}

const resolveArtistName = (artists?: ReadonlyArray<Artist>) => {
    return (
        artists
            ?.map((a) => a.name)
            .filter(Boolean)
            .join(', ') ?? 'Unknown Artist'
    )
}

const formatYear = (releaseDate?: string) => {
    if (!releaseDate) return ''
    const date = new Date(releaseDate)
    return Number.isNaN(date.getTime()) ? '' : date.getFullYear().toString()
}

const getRandomEmoji = () => {
    const emojis = ['🎤', '🎹', '🎸', '🎻', '🎷', '🎺', '🎼', '🎧', '🎙️', '🥁']
    return emojis[Math.floor(Math.random() * emojis.length)] ?? '🎤'
}

const performSearch = async (query: string) => {
    const keyword = query.trim()
    if (!keyword) {
        artists.value = []
        albums.value = []
        works.value = []
        return
    }

    isLoading.value = true
    try {
        const [albumResults, workResults] = await Promise.all([
            api.albumController.getAlbumByName({ name: keyword }),
            api.workController.getWorkByName({ name: keyword }),
        ])

        const q = keyword.toLowerCase()

        albums.value = albumResults.map((album) => ({
            id: album.id,
            type: 'album',
            title: album.title || 'Untitled Album',
            subtitle: album.recordings?.[0]?.label || 'Unknown Artist',
            details: formatYear(album.releaseDate),
            cover: resolveCover(album.cover?.id),
            badge: album.kind?.trim() ? album.kind : 'Album',
        }))

        works.value = workResults.map((work) => {
            const mainRecording =
                work.recordings?.find((r) => r.defaultInWork) ?? work.recordings?.[0]
            return {
                id: work.id,
                type: 'work',
                title: work.title || 'Untitled Work',
                subtitle: mainRecording?.artists?.[0]?.name || 'Unknown Artist',
                details: `${work.recordings?.length ?? 0} Recordings`,
                cover: resolveCover(mainRecording?.cover?.id),
                stackedImages: work.recordings?.map((r) => ({
                    id: r.id,
                    cover: resolveCover(r.cover?.id),
                })),
            }
        })

        // Extract Artists from results
        const artistMap = new Map<string, SearchResultItem>()

        // Helper to add artist
        const addArtist = (name: string) => {
            if (!name || name === 'Unknown Artist' || artistMap.has(name)) return
            if (name.toLowerCase().includes(q)) {
                artistMap.set(name, {
                    id: name, // Use name as ID for now
                    type: 'artist',
                    title: name,
                    subtitle: 'Artist',
                    details: '',
                    cover: getRandomEmoji(),
                    isEmoji: true,
                })
            }
        }

        workResults.forEach((w) => {
            w.recordings?.forEach((r) => r.artists?.forEach((art) => addArtist(art.name)))
        })

        artists.value = Array.from(artistMap.values())
    } catch (error) {
        console.error('Search failed:', error)
    } finally {
        isLoading.value = false
    }
}

const resolveSearchTab = (tabValue: unknown): SearchTab => {
    const tab = Array.isArray(tabValue) ? tabValue[0] : tabValue
    return searchTabs.includes(tab as SearchTab) ? (tab as SearchTab) : 'All'
}

const handleTabChange = (tab: SearchTab) => {
    if (activeTab.value === tab && route.query.tab === tab) {
        return
    }
    router.push({
        name: 'search',
        query: {
            ...route.query,
            tab,
        },
    })
}

// Watchers and lifecycle
watch(
    () => route.query.q,
    (newQ) => {
        const q = (newQ as string) || ''
        searchQuery.value = q
        performSearch(q)
    },
    { immediate: true },
)

watch(
    () => route.query.tab,
    (newTab) => {
        const resolvedTab = resolveSearchTab(newTab)
        activeTab.value = resolvedTab

        const currentTab = Array.isArray(newTab) ? newTab[0] : newTab
        if (currentTab !== resolvedTab) {
            router.replace({
                name: 'search',
                query: {
                    ...route.query,
                    tab: resolvedTab,
                },
            })
        }
    },
    { immediate: true },
)

const filteredResults = computed(() => {
    if (activeTab.value === 'Albums') return { albums: albums.value, works: [], artists: [] }
    if (activeTab.value === 'Works') return { albums: [], works: works.value, artists: [] }
    if (activeTab.value === 'Artists') return { albums: [], works: [], artists: artists.value }
    return { albums: albums.value, works: works.value, artists: artists.value }
})

const hasResults = computed(
    () => albums.value.length > 0 || works.value.length > 0 || artists.value.length > 0,
)

// Navigation and Playback
const navigateToDetail = (item: SearchResultItem) => {
    if (item.type === 'album') {
        router.push({ name: 'album-detail', params: { id: item.id } })
    } else if (item.type === 'work') {
        router.push({ name: 'work-detail', params: { id: item.id } })
    }
    // Artist navigation not implemented yet
}

const isItemPlaying = (item: SearchResultItem) => {
    return (
        audioStore.isPlaying &&
        item.playTrackId !== undefined &&
        audioStore.currentTrack?.id === item.playTrackId
    )
}

const playItem = async (item: SearchResultItem) => {
    if (playLoadingItemId.value === item.id) return
    if (item.type === 'artist') return // Artist playback not supported yet
    if (typeof item.id !== 'number') return

    try {
        playLoadingItemId.value = item.id
        // Play logic similar to AlbumListView
        // Simplify for brevity: Assume we need to fetch details to play
        let targetTrack, targetSrc, playWorkId

        if (item.type === 'album') {
            const detail = await api.albumController.getAlbum({ id: item.id })
            targetTrack =
                detail.recordings.find((r) => r.defaultInWork && resolveAudio(r.assets || [])) ||
                detail.recordings.find((r) => resolveAudio(r.assets || []))
            targetSrc = resolveAudio(targetTrack?.assets || [])
        } else {
            const detail = await api.workController.getWorkById({ id: item.id })
            targetTrack =
                detail.recordings.find((r) => r.defaultInWork && resolveAudio(r.assets || [])) ||
                detail.recordings.find((r) => resolveAudio(r.assets || []))
            targetSrc = resolveAudio(targetTrack?.assets || [])
            playWorkId = detail.id
        }

        if (!targetTrack || !targetSrc) {
            console.warn('No playable track found')
            return
        }

        item.playTrackId = targetTrack.id

        audioStore.play({
            id: targetTrack.id,
            title: targetTrack.title || item.title,
            artist: resolveArtistName(targetTrack.artists) || item.subtitle,
            cover: item.cover, // Simplification
            src: targetSrc,
            workId: playWorkId,
        })
    } catch (e) {
        console.error('Failed to play', e)
    } finally {
        playLoadingItemId.value = null
    }
}
</script>

<template>
    <div class="pb-40">
        <DashboardTopBar
            :model-value="searchQuery"
            @update:model-value="(v) => (searchQuery = v)"
        />

        <div class="px-8 pt-6">
            <div class="mb-8 flex items-start justify-between gap-4">
                <div>
                    <h2 class="text-4xl font-serif text-[#2C2420] mb-2">搜索结果</h2>
                    <p class="text-[#8C857B] font-serif italic">
                        Search results for "{{ searchQuery }}"
                    </p>
                </div>
                <button
                    v-if="hasSelectedWorks"
                    type="button"
                    class="shrink-0 mt-1 px-4 py-2 border border-[#C27E46] text-[#C27E46] text-sm tracking-wide transition-colors hover:bg-[#C27E46] hover:text-white"
                    @click="openMergeModal"
                >
                    合并
                </button>
            </div>

            <!-- Tabs -->
            <div class="flex flex-wrap gap-6 border-b border-[#D6D1C7] pb-4 mb-8">
                <button
                    v-for="tab in searchTabs"
                    :key="tab"
                    class="text-sm tracking-wide transition-colors relative pb-4 -mb-4"
                    :class="
                        activeTab === tab
                            ? 'text-[#2C2420] font-semibold border-b-2 border-[#C27E46]'
                            : 'text-[#8C857B] hover:text-[#5E5950]'
                    "
                    @click="handleTabChange(tab)"
                >
                    {{
                        tab === 'All'
                            ? '全部'
                            : tab === 'Albums'
                              ? '专辑'
                              : tab === 'Works'
                                ? '作品'
                                : '艺术家'
                    }}
                </button>
            </div>

            <div v-if="isLoading" class="text-[#8C857B] text-sm">搜索中...</div>

            <div v-else-if="!hasResults" class="text-[#8C857B] text-sm">未找到匹配的内容。</div>

            <div v-else class="space-y-12">
                <!-- Artists Section -->
                <div v-if="filteredResults.artists.length > 0">
                    <h3 v-if="activeTab === 'All'" class="text-xl font-serif text-[#2C2420] mb-6">
                        艺术家
                    </h3>
                    <div
                        class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-8"
                    >
                        <div
                            v-for="item in filteredResults.artists"
                            :key="item.id"
                            class="group cursor-pointer text-center"
                        >
                            <div
                                class="relative aspect-square mb-4 mx-auto w-32 h-32 md:w-40 md:h-40 rounded-full overflow-hidden shadow-md bg-[#EFEAE2] flex items-center justify-center text-6xl group-hover:scale-105 transition-transform duration-300"
                            >
                                {{ item.cover }}
                            </div>

                            <h3
                                class="font-serif text-lg leading-tight mb-1 truncate text-[#1A1A1A] group-hover:text-[#C27E46] transition-colors"
                            >
                                {{ item.title }}
                            </h3>
                            <p class="text-xs text-[#8C857B] uppercase tracking-wider truncate">
                                {{ item.subtitle }}
                            </p>
                        </div>
                    </div>
                </div>

                <!-- Albums Section -->
                <div v-if="filteredResults.albums.length > 0">
                    <h3 v-if="activeTab === 'All'" class="text-xl font-serif text-[#2C2420] mb-6">
                        专辑
                    </h3>
                    <div
                        class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-x-12 gap-y-16"
                    >
                        <div
                            v-for="item in filteredResults.albums"
                            :key="item.id"
                            class="group cursor-pointer"
                            @click="navigateToDetail(item)"
                        >
                            <div
                                class="relative aspect-square mb-5 transition-transform duration-500 ease-out perspective-1000"
                            >
                                <!-- CD Animation from AlbumListView -->
                                <div
                                    class="absolute top-1/2 left-1/2 w-[90%] h-[90%] -translate-x-1/2 -translate-y-1/2"
                                >
                                    <div
                                        class="w-full h-full bg-linear-to-tr from-gray-200 to-gray-100 border border-gray-300 rounded-full shadow-xl transition-all duration-700 ease-out opacity-0 group-hover:opacity-100 transform-gpu group-hover:translate-x-7 group-hover:-translate-y-8 group-hover:rotate-3 flex items-center justify-center relative"
                                    >
                                        <div
                                            class="w-1/3 h-1/3 border border-gray-300 rounded-full opacity-50"
                                        ></div>
                                        <div
                                            class="absolute w-8 h-8 bg-[#EBE7E0] rounded-full border border-gray-300"
                                        ></div>
                                    </div>
                                </div>
                                <div
                                    class="relative z-10 w-full h-full shadow-lg transition-all duration-500 ease-out bg-[#D6D1C7] transform-gpu origin-center group-hover:scale-105 group-hover:-rotate-2 group-hover:-translate-x-3 group-hover:-translate-y-1.5"
                                >
                                    <img
                                        v-if="item.cover"
                                        :src="item.cover"
                                        :alt="item.title"
                                        class="w-full h-full object-cover"
                                    />
                                    <div
                                        v-else
                                        class="w-full h-full flex items-center justify-center text-xs text-[#8C857B]"
                                    >
                                        No Cover
                                    </div>
                                </div>

                                <!-- Play Button Overlay -->
                                <button
                                    class="absolute bottom-4 right-4 w-10 h-10 bg-white/90 rounded-full shadow-lg flex items-center justify-center text-[#2C2420] opacity-0 group-hover:opacity-100 transition-all duration-300 hover:scale-110 z-20"
                                    @click.stop="playItem(item)"
                                >
                                    <Play
                                        v-if="playLoadingItemId === item.id"
                                        :size="16"
                                        class="animate-pulse"
                                        fill="currentColor"
                                    />
                                    <Pause
                                        v-else-if="isItemPlaying(item)"
                                        :size="16"
                                        fill="currentColor"
                                    />
                                    <Play v-else :size="16" fill="currentColor" class="ml-0.5" />
                                </button>
                            </div>

                            <div class="text-center md:text-left">
                                <h3
                                    class="font-serif text-lg leading-tight mb-1 truncate text-[#1A1A1A] group-hover:text-[#C27E46] transition-colors"
                                >
                                    {{ item.title }}
                                </h3>
                                <p class="text-xs text-[#8C857B] uppercase tracking-wider truncate">
                                    {{ item.subtitle }}
                                </p>
                                <p class="text-[10px] text-[#B0AAA0] mt-1">
                                    {{ item.details }} · {{ item.badge }}
                                </p>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Works Section -->
                <div v-if="filteredResults.works.length > 0">
                    <h3 v-if="activeTab === 'All'" class="text-xl font-serif text-[#2C2420] mb-6">
                        作品
                    </h3>
                    <div
                        class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-x-12 gap-y-16"
                    >
                        <div
                            v-for="item in filteredResults.works"
                            :key="item.id"
                            class="group cursor-pointer"
                            @click="navigateToDetail(item)"
                        >
                            <div
                                class="relative aspect-square mb-5 transition-all duration-300 ease-out"
                            >
                                <div class="relative w-full h-full">
                                    <StackedCovers
                                        :items="item.stackedImages || []"
                                        :default-cover="item.cover"
                                        :is-selected="isWorkSelected(item)"
                                    />

                                    <button
                                        type="button"
                                        aria-label="选择作品"
                                        class="absolute top-0 left-0 w-1/2 h-1/2 z-30 cursor-pointer"
                                        @click.stop="toggleWorkSelection(item)"
                                    ></button>

                                    <button
                                        class="absolute bottom-4 right-4 w-10 h-10 bg-white/90 rounded-full shadow-lg flex items-center justify-center text-[#2C2420] opacity-0 group-hover:opacity-100 transition-all duration-300 hover:scale-110 z-20"
                                        @click.stop="playItem(item)"
                                    >
                                        <Play
                                            v-if="playLoadingItemId === item.id"
                                            :size="16"
                                            class="animate-pulse"
                                            fill="currentColor"
                                        />
                                        <Pause
                                            v-else-if="isItemPlaying(item)"
                                            :size="16"
                                            fill="currentColor"
                                        />
                                        <Play
                                            v-else
                                            :size="16"
                                            fill="currentColor"
                                            class="ml-0.5"
                                        />
                                    </button>
                                </div>
                            </div>

                            <div class="text-center md:text-left">
                                <h3
                                    class="font-serif text-lg leading-tight mb-1 truncate text-[#1A1A1A] group-hover:text-[#C27E46] transition-colors"
                                >
                                    {{ item.title }}
                                </h3>
                                <p class="text-xs text-[#8C857B] uppercase tracking-wider truncate">
                                    {{ item.subtitle }}
                                </p>
                                <p class="text-[10px] text-[#B0AAA0] mt-1">
                                    {{ item.details }}
                                </p>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
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
                v-if="mergeModalOpen"
                class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#2B221B]/60"
                @click.self="closeMergeModal"
            >
                <div
                    class="bg-[#fffcf5] w-full max-w-lg max-h-[85vh] flex flex-col shadow-[0_8px_30px_rgba(0,0,0,0.12)] border border-[#EAE6DE]"
                >
                    <div class="px-8 pt-8 pb-6 border-b border-[#EAE6DE]">
                        <h3 class="font-serif text-2xl text-[#2B221B]">合并作品</h3>
                        <p class="text-sm text-[#8C857B] mt-2">
                            请选择保留的目标作品，其余已选作品将合并到该作品。
                        </p>
                    </div>

                    <div class="px-8 py-6 overflow-y-auto">
                        <div class="space-y-3">
                            <label
                                v-for="work in selectedWorkOptions"
                                :key="work.id"
                                class="flex items-start gap-3 p-3 border border-[#EAE6DE] cursor-pointer hover:bg-[#F7F5F0] transition-colors"
                            >
                                <input
                                    v-model="mergeTargetWorkId"
                                    type="radio"
                                    :value="work.id"
                                    class="mt-1 accent-[#C27E46]"
                                />
                                <span class="min-w-0">
                                    <span class="block text-[#2B221B] font-serif truncate">
                                        {{ work.title }}
                                    </span>
                                    <span class="block text-xs text-[#8C857B] truncate">
                                        {{ work.subtitle }}
                                    </span>
                                </span>
                            </label>
                        </div>

                        <p v-if="mergeModalError" class="text-sm text-[#B95D5D] mt-4">
                            {{ mergeModalError }}
                        </p>
                        <p
                            v-else-if="selectedWorkOptions.length < 2"
                            class="text-sm text-[#8C857B] mt-4"
                        >
                            需要至少选中 2 个作品才能执行合并。
                        </p>
                    </div>

                    <div class="p-8 pt-6 border-t border-[#EAE6DE] grid grid-cols-2 gap-3">
                        <button
                            type="button"
                            class="px-4 py-2.5 border border-[#D6D1C4] text-[#8A8A8A] hover:bg-[#F7F5F0] hover:text-[#5A5A5A] transition-colors text-sm tracking-wide"
                            :disabled="mergeSubmitting"
                            @click="closeMergeModal"
                        >
                            取消
                        </button>
                        <button
                            type="button"
                            class="px-4 py-2.5 bg-[#C27E46] text-white text-sm tracking-wide transition-colors hover:bg-[#B06D39] disabled:opacity-50 disabled:cursor-not-allowed"
                            :disabled="!canSubmitMerge"
                            @click="submitMerge"
                        >
                            {{ mergeSubmitting ? '合并中...' : '确认合并' }}
                        </button>
                    </div>
                </div>
            </div>
        </Transition>
    </Teleport>
</template>
