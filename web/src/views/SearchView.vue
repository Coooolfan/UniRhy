<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import AlbumGridCard from '@/components/media/AlbumGridCard.vue'
import WorkGridCard from '@/components/media/WorkGridCard.vue'
import MergeSelectModal from '@/components/modals/MergeSelectModal.vue'
import { api, normalizeApiError } from '@/ApiInstance'
import { resolveCover, formatYear } from '@/composables/recordingMedia'
import {
    resolveAlbumPlayableTrack,
    resolveWorkPlayableTrack,
    type PlayableTrack,
} from '@/services/playableTrackResolver'
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
    playableTrack?: PlayableTrack
}

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

    const nextSelectedIds = new Set(selectedWorkIds.value)
    const nextSelectedWorks = new Map(selectedWorks.value)

    if (nextSelectedIds.has(item.id)) {
        nextSelectedIds.delete(item.id)
        nextSelectedWorks.delete(item.id)
    } else {
        nextSelectedIds.add(item.id)
        nextSelectedWorks.set(item.id, {
            id: item.id,
            title: item.title,
            subtitle: item.subtitle,
        })
    }

    selectedWorkIds.value = nextSelectedIds
    selectedWorks.value = nextSelectedWorks
}

const openMergeModal = () => {
    if (!hasSelectedWorks.value) {
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

type Artist = {
    name?: string
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

        const lowerKeyword = keyword.toLowerCase()

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
                work.recordings?.find((recording) => recording.defaultInWork) ??
                work.recordings?.[0]

            return {
                id: work.id,
                type: 'work',
                title: work.title || 'Untitled Work',
                subtitle: mainRecording?.artists?.[0]?.name || 'Unknown Artist',
                details: `${work.recordings?.length ?? 0} Recordings`,
                cover: resolveCover(mainRecording?.cover?.id),
                stackedImages: work.recordings?.map((recording) => ({
                    id: recording.id,
                    cover: resolveCover(recording.cover?.id),
                })),
            }
        })

        const artistMap = new Map<string, SearchResultItem>()

        const addArtist = (name: string) => {
            if (!name || name === 'Unknown Artist' || artistMap.has(name)) {
                return
            }
            if (!name.toLowerCase().includes(lowerKeyword)) {
                return
            }
            artistMap.set(name, {
                id: name,
                type: 'artist',
                title: name,
                subtitle: 'Artist',
                details: '',
                cover: getRandomEmoji(),
                isEmoji: true,
            })
        }

        workResults.forEach((work) => {
            work.recordings?.forEach((recording) => {
                recording.artists?.forEach((artist: Artist) => addArtist(artist.name || ''))
            })
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

watch(
    () => route.query.q,
    (newQ) => {
        const nextQuery = (newQ as string) || ''
        searchQuery.value = nextQuery
        performSearch(nextQuery)
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
    if (activeTab.value === 'Albums') {
        return { albums: albums.value, works: [], artists: [] }
    }
    if (activeTab.value === 'Works') {
        return { albums: [], works: works.value, artists: [] }
    }
    if (activeTab.value === 'Artists') {
        return { albums: [], works: [], artists: artists.value }
    }
    return { albums: albums.value, works: works.value, artists: artists.value }
})

const hasResults = computed(
    () => albums.value.length > 0 || works.value.length > 0 || artists.value.length > 0,
)

const navigateToDetail = (item: SearchResultItem) => {
    if (item.type === 'album') {
        router.push({ name: 'album-detail', params: { id: item.id } })
    } else if (item.type === 'work') {
        router.push({ name: 'work-detail', params: { id: item.id } })
    }
}

const isItemPlaying = (item: SearchResultItem) => {
    return (
        audioStore.isPlaying &&
        item.playableTrack !== undefined &&
        audioStore.currentTrack?.id === item.playableTrack.id
    )
}

const playItem = async (item: SearchResultItem) => {
    if (playLoadingItemId.value === item.id || item.type === 'artist') {
        return
    }
    if (typeof item.id !== 'number') {
        return
    }

    try {
        playLoadingItemId.value = item.id

        if (!item.playableTrack) {
            const fallback = {
                title: item.title,
                artist: item.subtitle,
                cover: item.cover,
            }
            item.playableTrack =
                item.type === 'album'
                    ? await resolveAlbumPlayableTrack(item.id, fallback)
                    : await resolveWorkPlayableTrack(item.id, fallback)
        }

        if (!item.playableTrack) {
            console.warn('No playable track found', item.id)
            return
        }

        audioStore.play(item.playableTrack)
    } catch (error) {
        console.error('Failed to play', error)
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

                <div v-if="filteredResults.albums.length > 0">
                    <h3 v-if="activeTab === 'All'" class="text-xl font-serif text-[#2C2420] mb-6">
                        专辑
                    </h3>
                    <div
                        class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-x-12 gap-y-16"
                    >
                        <AlbumGridCard
                            v-for="item in filteredResults.albums"
                            :key="item.id"
                            :title="item.title"
                            :subtitle="item.subtitle"
                            :details="item.details"
                            :badge="item.badge"
                            :cover="item.cover"
                            :play-loading="playLoadingItemId === item.id"
                            :is-playing="isItemPlaying(item)"
                            @open="navigateToDetail(item)"
                            @play="playItem(item)"
                        />
                    </div>
                </div>

                <div v-if="filteredResults.works.length > 0">
                    <h3 v-if="activeTab === 'All'" class="text-xl font-serif text-[#2C2420] mb-6">
                        作品
                    </h3>
                    <div
                        class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-x-12 gap-y-16"
                    >
                        <WorkGridCard
                            v-for="item in filteredResults.works"
                            :key="item.id"
                            :title="item.title"
                            :subtitle="item.subtitle"
                            :details="item.details"
                            :cover="item.cover"
                            :stacked-images="item.stackedImages || []"
                            :is-selected="isWorkSelected(item)"
                            selectable
                            :play-loading="playLoadingItemId === item.id"
                            :is-playing="isItemPlaying(item)"
                            @open="navigateToDetail(item)"
                            @play="playItem(item)"
                            @toggle-select="toggleWorkSelection(item)"
                        />
                    </div>
                </div>
            </div>
        </div>
    </div>

    <MergeSelectModal
        :open="mergeModalOpen"
        title="合并作品"
        description="请选择保留的目标作品，其余已选作品将合并到该作品。"
        :options="selectedWorkOptions"
        :target-id="mergeTargetWorkId"
        :error="mergeModalError"
        :submitting="mergeSubmitting"
        :confirm-disabled="!canSubmitMerge"
        @update:target-id="mergeTargetWorkId = $event"
        @close="closeMergeModal"
        @confirm="submitMerge"
    />
</template>
