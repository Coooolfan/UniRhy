<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import ArtistCard from '@/components/artist/ArtistCard.vue'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import LibraryEmptyHint from '@/components/dashboard/LibraryEmptyHint.vue'
import AlbumGridCard from '@/components/media/AlbumGridCard.vue'
import WorkGridCard from '@/components/media/WorkGridCard.vue'
import { useModal } from '@/composables/useModal'
import MergeSelectModal from '@/components/modals/MergeSelectModal.vue'
import { api } from '@/ApiInstance'
import { resolveArtistName, resolveCover } from '@/composables/recordingMedia'
import {
    peekResolvedPlayableTrack,
    resolveAlbumPlayableTrack,
    resolveWorkPlayableTrack,
} from '@/services/playableTrackResolver'
import { useAudioStore } from '@/stores/audio'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const audioStore = useAudioStore()
const userStore = useUserStore()
const modal = useModal()

const searchQuery = ref('')
const searchedQuery = ref('')
const isLoading = ref(false)
const searchTabs = ['All', 'Artists', 'Albums', 'Works'] as const
type SearchTab = (typeof searchTabs)[number]
const activeTab = ref<SearchTab>('All')

type SearchResultItem = {
    id: number | string
    type: 'album' | 'work' | 'artist'
    title: string
    subtitle: string
    cover: string
    stackedImages?: { id: number | string; cover?: string }[]
}

const artists = ref<SearchResultItem[]>([])
const albums = ref<SearchResultItem[]>([])
const works = ref<SearchResultItem[]>([])
const playLoadingItemId = ref<number | string | null>(null)

type SelectedMergeOption = {
    id: number
    title: string
    subtitle: string
}

const selectedWorkIds = ref<Set<number>>(new Set())
const selectedWorks = ref<Map<number, SelectedMergeOption>>(new Map())
const selectedArtistIds = ref<Set<number>>(new Set())
const selectedArtists = ref<Map<number, SelectedMergeOption>>(new Map())

const selectedWorkOptions = computed(() => Array.from(selectedWorks.value.values()))
const hasSelectedWorks = computed(() => selectedWorkOptions.value.length > 0)

const selectedArtistOptions = computed(() => Array.from(selectedArtists.value.values()))
const hasSelectedArtists = computed(() => selectedArtistOptions.value.length > 0)

const isWorkSelected = (item: SearchResultItem) =>
    item.type === 'work' && typeof item.id === 'number' && selectedWorkIds.value.has(item.id)

const isArtistSelected = (item: SearchResultItem) =>
    item.type === 'artist' && typeof item.id === 'number' && selectedArtistIds.value.has(item.id)

const toggleWorkSelection = (item: SearchResultItem) => {
    if (!userStore.isAdmin) {
        return
    }

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

const toggleArtistSelection = (item: SearchResultItem) => {
    if (!userStore.isAdmin) {
        return
    }

    if (item.type !== 'artist' || typeof item.id !== 'number') {
        return
    }

    const nextSelectedIds = new Set(selectedArtistIds.value)
    const nextSelectedArtists = new Map(selectedArtists.value)

    if (nextSelectedIds.has(item.id)) {
        nextSelectedIds.delete(item.id)
        nextSelectedArtists.delete(item.id)
    } else {
        nextSelectedIds.add(item.id)
        nextSelectedArtists.set(item.id, {
            id: item.id,
            title: item.title,
            subtitle: item.subtitle,
        })
    }

    selectedArtistIds.value = nextSelectedIds
    selectedArtists.value = nextSelectedArtists
}

async function performSearch(query: string) {
    const keyword = query.trim()
    if (!keyword) {
        artists.value = []
        albums.value = []
        works.value = []
        return
    }

    isLoading.value = true
    searchedQuery.value = keyword

    try {
        const [artistResults, albumResults, workResults] = await Promise.all([
            api.artistController.getArtistByName({ name: keyword }),
            api.albumController.getAlbumByName({ name: keyword }),
            api.workController.getWorkByName({ name: keyword }),
        ])

        albums.value = albumResults.map((album) => ({
            id: album.id,
            type: 'album',
            title: album.title || 'Untitled Album',
            subtitle: resolveArtistName(album.recordings?.[0]?.artists),
            cover: resolveCover(album.cover),
        }))

        works.value = workResults.map((work) => {
            const mainRecording =
                work.recordings?.find((recording) => recording.defaultInWork) ??
                work.recordings?.[0]

            return {
                id: work.id,
                type: 'work',
                title: work.title || 'Untitled Work',
                subtitle: mainRecording?.artists?.[0]?.displayName || 'Unknown Artist',
                cover: resolveCover(mainRecording?.cover),
                stackedImages: work.recordings?.map((recording) => ({
                    id: recording.id,
                    cover: resolveCover(recording.cover),
                })),
            }
        })

        artists.value = artistResults.map((artist) => ({
            id: artist.id,
            type: 'artist',
            title: artist.displayName || 'Unknown Artist',
            subtitle: artist.alias.length > 0 ? artist.alias.join(' / ') : t('artists.fallback'),
            cover: '',
        }))
    } catch (error) {
        console.error('Search failed:', error)
    } finally {
        isLoading.value = false
    }
}

const openMergeWorksModal = async () => {
    if (!userStore.isAdmin || !hasSelectedWorks.value) {
        return
    }

    await modal.open(MergeSelectModal, {
        title: t('merge.worksTitle'),
        size: 'md',
        props: {
            description: t('merge.worksDescription'),
            options: selectedWorkOptions.value,
            missingTargetMessage: t('merge.worksMissingTargetMessage'),
            onConfirm: async (targetId: number) => {
                const sourceWorkIds = selectedWorkOptions.value
                    .map((work) => work.id)
                    .filter((id) => id !== targetId)

                if (sourceWorkIds.length === 0) {
                    throw new Error(t('merge.worksMissingSourceMessage'))
                }

                await api.workController.mergeWork({
                    body: {
                        targetId,
                        needMergeIds: sourceWorkIds,
                    },
                })

                selectedWorkIds.value = new Set()
                selectedWorks.value = new Map()
                await performSearch(searchQuery.value)
            },
        },
    })
}

const openMergeArtistsModal = async () => {
    if (!userStore.isAdmin || !hasSelectedArtists.value) {
        return
    }

    await modal.open(MergeSelectModal, {
        title: t('merge.artistsTitle'),
        size: 'md',
        props: {
            description: t('merge.artistsDescription'),
            options: selectedArtistOptions.value,
            missingTargetMessage: t('merge.artistsMissingTargetMessage'),
            onConfirm: async (targetId: number) => {
                const sourceArtistIds = selectedArtistOptions.value
                    .map((artist) => artist.id)
                    .filter((id) => id !== targetId)

                if (sourceArtistIds.length === 0) {
                    throw new Error(t('merge.artistsMissingSourceMessage'))
                }

                await api.artistController.mergeArtists({
                    body: {
                        targetId,
                        needMergeIds: sourceArtistIds,
                    },
                })

                selectedArtistIds.value = new Set()
                selectedArtists.value = new Map()
                await performSearch(searchQuery.value)
            },
        },
    })
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
    if (item.type === 'artist' || typeof item.id !== 'number') {
        return false
    }
    const track = peekResolvedPlayableTrack(item.type, item.id)
    return audioStore.isPlaying && track !== null && audioStore.currentTrack?.id === track.id
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

        const fallback = {
            title: item.title,
            artist: item.subtitle,
            cover: item.cover,
        }
        const playableTrack =
            item.type === 'album'
                ? await resolveAlbumPlayableTrack(item.id, fallback)
                : await resolveWorkPlayableTrack(item.id, fallback)

        if (!playableTrack) {
            console.warn('No playable track found', item.id)
            return
        }

        audioStore.play(playableTrack)
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

        <div class="px-4 pt-4 sm:px-6 sm:pt-6 lg:px-8">
            <div class="mb-8 flex flex-col items-start gap-4 sm:flex-row sm:justify-between">
                <div>
                    <h2 class="mb-2 text-3xl font-serif text-[#2C2420] sm:text-4xl">
                        {{ t('search.title') }}
                    </h2>
                    <p class="text-[#8C857B] font-serif italic">
                        Search results for "{{ searchedQuery }}"
                    </p>
                </div>
                <div class="flex w-full flex-col gap-2 sm:w-auto sm:flex-row sm:items-center">
                    <button
                        v-if="userStore.isAdmin && hasSelectedArtists"
                        type="button"
                        class="mt-1 w-full border border-[#C27E46] px-4 py-2 text-sm tracking-wide text-[#C27E46] transition-colors hover:bg-[#C27E46] hover:text-white sm:w-auto sm:shrink-0"
                        @click="openMergeArtistsModal"
                    >
                        {{ t('search.mergeArtists') }}
                    </button>
                    <button
                        v-if="userStore.isAdmin && hasSelectedWorks"
                        type="button"
                        class="mt-1 w-full border border-[#C27E46] px-4 py-2 text-sm tracking-wide text-[#C27E46] transition-colors hover:bg-[#C27E46] hover:text-white sm:w-auto sm:shrink-0"
                        @click="openMergeWorksModal"
                    >
                        {{ t('search.mergeWorks') }}
                    </button>
                </div>
            </div>

            <div
                class="mb-8 flex gap-6 overflow-x-auto border-b border-[#D6D1C7] pb-4 whitespace-nowrap"
            >
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
                            ? t('search.tabAll')
                            : tab === 'Albums'
                              ? t('search.tabAlbums')
                              : tab === 'Works'
                                ? t('search.tabWorks')
                                : t('search.tabArtists')
                    }}
                </button>
            </div>

            <div v-if="isLoading" class="text-[#8C857B] text-sm">{{ t('search.searching') }}</div>

            <LibraryEmptyHint
                v-else-if="!hasResults"
                :showSettingsButton="false"
                :title="t('search.noResults')"
                :description="[t('search.tryAdjustKeywords')]"
            />

            <div v-else class="space-y-12">
                <div v-if="filteredResults.artists.length > 0">
                    <h3 v-if="activeTab === 'All'" class="text-xl font-serif text-[#2C2420] mb-6">
                        {{ t('search.tabArtists') }}
                    </h3>
                    <div
                        class="grid grid-cols-2 gap-5 sm:gap-8 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6"
                    >
                        <ArtistCard
                            v-for="item in filteredResults.artists"
                            :key="item.id"
                            :id="item.id"
                            :title="item.title"
                            :subtitle="item.subtitle"
                            :selectable="userStore.isAdmin"
                            :selected="isArtistSelected(item)"
                            @toggle-select="toggleArtistSelection(item)"
                        />
                    </div>
                </div>

                <div v-if="filteredResults.albums.length > 0">
                    <h3 v-if="activeTab === 'All'" class="text-xl font-serif text-[#2C2420] mb-6">
                        {{ t('search.tabAlbums') }}
                    </h3>
                    <div
                        class="grid grid-cols-2 gap-x-5 gap-y-10 sm:gap-x-8 sm:gap-y-12 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 xl:gap-x-12 xl:gap-y-16"
                    >
                        <AlbumGridCard
                            v-for="item in filteredResults.albums"
                            :key="item.id"
                            :title="item.title"
                            :subtitle="item.subtitle"
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
                        {{ t('search.tabWorks') }}
                    </h3>
                    <div
                        class="grid grid-cols-2 gap-x-5 gap-y-10 sm:gap-x-8 sm:gap-y-12 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 xl:gap-x-12 xl:gap-y-16"
                    >
                        <WorkGridCard
                            v-for="item in filteredResults.works"
                            :key="item.id"
                            :title="item.title"
                            :subtitle="item.subtitle"
                            :cover="item.cover"
                            :stacked-images="item.stackedImages || []"
                            :is-selected="isWorkSelected(item)"
                            :selectable="userStore.isAdmin"
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
</template>
