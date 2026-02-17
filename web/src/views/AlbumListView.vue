<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
    ChevronLeft,
    ChevronRight,
    LayoutGrid,
    List as ListIcon,
    Pause,
    Play,
} from 'lucide-vue-next'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import AlbumGridCard from '@/components/media/AlbumGridCard.vue'
import WorkGridCard from '@/components/media/WorkGridCard.vue'
import { api, normalizeApiError } from '@/ApiInstance'
import { formatYear, resolveCover } from '@/composables/recordingMedia'
import {
    resolveAlbumPlayableTrack,
    resolveWorkPlayableTrack,
    type PlayableTrack,
} from '@/services/playableTrackResolver'
import { useAudioStore } from '@/stores/audio'

type DisplayItem = {
    id: number
    type: 'album' | 'work'
    title: string
    subtitle: string
    details: string
    cover: string
    stackedImages?: { id: number | string; cover?: string }[]
    badge?: string
    playableTrack?: PlayableTrack
}

const router = useRouter()
const route = useRoute()
const audioStore = useAudioStore()
const viewMode = ref<'grid' | 'list'>('grid')
const activeTab = ref<'Albums' | 'Works'>('Albums')
const searchQuery = ref('')
const displayItems = ref<DisplayItem[]>([])
const isLoading = ref(false)
const errorMessage = ref('')
const playLoadingItemId = ref<number | null>(null)

const pageIndex = ref(0)
const pageSize = ref(10)
const totalPageCount = ref(0)

const fetchAlbums = async () => {
    isLoading.value = true
    errorMessage.value = ''

    try {
        const page = await api.albumController.listAlbums({
            pageIndex: pageIndex.value,
            pageSize: pageSize.value,
        })
        totalPageCount.value = page.totalPageCount
        displayItems.value = page.rows.map((album) => ({
            id: album.id,
            type: 'album',
            title: album.title || 'Untitled Album',
            subtitle: album.recordings?.[0]?.label || 'Unknown Artist',
            details: formatYear(album.releaseDate),
            cover: resolveCover(album.cover?.id),
            badge: album.kind?.trim() ? album.kind : '其他',
        }))
    } catch (error) {
        const normalized = normalizeApiError(error)
        errorMessage.value = normalized.message ?? '专辑加载失败'
    } finally {
        isLoading.value = false
    }
}

const fetchWorks = async () => {
    isLoading.value = true
    errorMessage.value = ''

    try {
        const page = await api.workController.listWork({
            pageIndex: pageIndex.value,
            pageSize: pageSize.value,
        })
        totalPageCount.value = page.totalPageCount
        displayItems.value = page.rows.map((work) => {
            const mainRecording =
                work.recordings?.find((recording) => recording.defaultInWork) ??
                work.recordings?.[0]
            const artistName = mainRecording?.artists?.[0]?.name || 'Unknown Artist'

            return {
                id: work.id,
                type: 'work',
                title: work.title || 'Untitled Work',
                subtitle: artistName,
                details: `${work.recordings?.length ?? 0} Recordings`,
                cover: resolveCover(mainRecording?.cover?.id),
                stackedImages: work.recordings?.map((recording) => ({
                    id: recording.id,
                    cover: resolveCover(recording.cover?.id),
                })),
            }
        })
    } catch (error) {
        const normalized = normalizeApiError(error)
        errorMessage.value = normalized.message ?? '作品加载失败'
    } finally {
        isLoading.value = false
    }
}

const fetchData = () => {
    if (activeTab.value === 'Albums') {
        fetchAlbums()
    } else {
        fetchWorks()
    }
}

const handlePageChange = (newPage: number) => {
    if (newPage < 0 || newPage >= totalPageCount.value) {
        return
    }

    router.push({
        query: {
            ...route.query,
            page: (newPage + 1).toString(),
        },
    })
}

const handleTabChange = (tab: 'Albums' | 'Works') => {
    if (activeTab.value === tab) {
        return
    }

    router.push({
        query: {
            ...route.query,
            tab,
            page: undefined,
        },
    })
}

const syncFromRoute = () => {
    const tab = route.query.tab as string
    const page = Number(route.query.page)

    const targetTab = tab === 'Works' || tab === 'Albums' ? tab : 'Albums'
    const tabChanged = targetTab !== activeTab.value
    activeTab.value = targetTab

    if (!Number.isNaN(page) && page > 0) {
        pageIndex.value = page - 1
    } else {
        pageIndex.value = 0
    }

    if (tabChanged) {
        displayItems.value = []
    }

    fetchData()
}

watch(
    () => route.query,
    () => {
        syncFromRoute()
    },
    { immediate: true },
)

const filteredItems = computed(() => {
    const query = searchQuery.value.trim().toLowerCase()
    if (!query) {
        return displayItems.value
    }

    return displayItems.value.filter((item) => {
        return (
            item.title.toLowerCase().includes(query) || item.subtitle.toLowerCase().includes(query)
        )
    })
})

const navigateToDetail = (id: number) => {
    if (activeTab.value === 'Albums') {
        router.push({ name: 'album-detail', params: { id } })
    } else {
        router.push({ name: 'work-detail', params: { id } })
    }
}

const isItemPlaying = (item: DisplayItem) => {
    return (
        audioStore.isPlaying &&
        item.playableTrack !== undefined &&
        audioStore.currentTrack?.id === item.playableTrack.id
    )
}

const playItem = async (item: DisplayItem) => {
    if (playLoadingItemId.value === item.id) {
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
            console.warn('No playable track for item', item.id)
            return
        }

        audioStore.play(item.playableTrack)
    } catch (error) {
        console.error('Failed to play item:', error)
    } finally {
        playLoadingItemId.value = null
    }
}
</script>

<template>
    <div class="pb-32">
        <DashboardTopBar v-model="searchQuery" />

        <div class="px-8 pt-6">
            <div class="flex flex-wrap items-end justify-between gap-6 mb-8">
                <div>
                    <h2 class="text-4xl font-serif text-[#2C2420] mb-2">资料库</h2>
                    <p class="text-[#8C857B] font-serif italic">
                        Collection of your musical journeys.
                    </p>
                </div>

                <div class="flex items-center gap-4">
                    <div class="flex bg-[#EFEAE2] p-1 rounded-md">
                        <button
                            class="p-2 rounded-sm transition-all"
                            :class="
                                viewMode === 'grid'
                                    ? 'bg-white shadow-sm text-[#2C2420]'
                                    : 'text-[#8C857B] hover:text-[#5E5950]'
                            "
                            @click="viewMode = 'grid'"
                        >
                            <LayoutGrid :size="18" />
                        </button>
                        <button
                            class="p-2 rounded-sm transition-all"
                            :class="
                                viewMode === 'list'
                                    ? 'bg-white shadow-sm text-[#2C2420]'
                                    : 'text-[#8C857B] hover:text-[#5E5950]'
                            "
                            @click="viewMode = 'list'"
                        >
                            <ListIcon :size="18" />
                        </button>
                    </div>
                </div>
            </div>

            <div class="flex flex-wrap gap-6 border-b border-[#D6D1C7] pb-4">
                <button
                    class="text-sm tracking-wide transition-colors relative"
                    :class="
                        activeTab === 'Albums'
                            ? 'text-[#2C2420] font-semibold border-b-2 border-[#C27E46] pb-4 -mb-4'
                            : 'text-[#8C857B] hover:text-[#5E5950] pb-4 -mb-4'
                    "
                    @click="handleTabChange('Albums')"
                >
                    专辑
                </button>
                <button
                    class="text-sm tracking-wide transition-colors relative"
                    :class="
                        activeTab === 'Works'
                            ? 'text-[#2C2420] font-semibold border-b-2 border-[#C27E46] pb-4 -mb-4'
                            : 'text-[#8C857B] hover:text-[#5E5950] pb-4 -mb-4'
                    "
                    @click="handleTabChange('Works')"
                >
                    作品
                </button>
            </div>
        </div>

        <div class="px-8 mt-10">
            <div v-if="isLoading && displayItems.length === 0" class="text-[#8C857B] text-sm">
                加载中...
            </div>
            <div v-else-if="errorMessage" class="text-[#B75D5D] text-sm">
                {{ errorMessage }}
                <button class="ml-4 text-[#C27E46]" type="button" @click="fetchData">重试</button>
            </div>

            <div v-else :class="{ 'opacity-50 pointer-events-none': isLoading }">
                <div v-if="filteredItems.length === 0" class="text-[#8C857B] text-sm">
                    未找到匹配的内容。
                </div>

                <div
                    v-else-if="viewMode === 'grid'"
                    class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-x-12 gap-y-16"
                >
                    <template v-if="activeTab === 'Albums'">
                        <AlbumGridCard
                            v-for="item in filteredItems"
                            :key="item.id"
                            :title="item.title"
                            :subtitle="item.subtitle"
                            :details="item.details"
                            :badge="item.badge"
                            :cover="item.cover"
                            :show-play-button="false"
                            :play-loading="playLoadingItemId === item.id"
                            :is-playing="isItemPlaying(item)"
                            @open="navigateToDetail(item.id)"
                            @play="playItem(item)"
                        />
                    </template>
                    <template v-else>
                        <WorkGridCard
                            v-for="item in filteredItems"
                            :key="item.id"
                            :title="item.title"
                            :subtitle="item.subtitle"
                            :details="item.details"
                            :cover="item.cover"
                            :stacked-images="item.stackedImages || []"
                            :show-play-button="false"
                            :play-loading="playLoadingItemId === item.id"
                            :is-playing="isItemPlaying(item)"
                            @open="navigateToDetail(item.id)"
                            @play="playItem(item)"
                        />
                    </template>
                </div>

                <div v-else class="space-y-2">
                    <div
                        class="grid grid-cols-12 text-xs text-[#8C857B] uppercase tracking-wider border-b border-[#D6D1C7] pb-2 mb-2 px-4"
                    >
                        <div class="col-span-1">#</div>
                        <div class="col-span-5">Title</div>
                        <div class="col-span-4">Subtitle</div>
                        <div class="col-span-2 text-right">Details</div>
                    </div>
                    <div
                        v-for="(item, idx) in filteredItems"
                        :key="item.id"
                        class="grid grid-cols-12 items-center px-4 py-3 hover:bg-[#EFEAE2]/60 rounded-sm group transition-colors cursor-pointer"
                        @click="navigateToDetail(item.id)"
                    >
                        <div
                            class="col-span-1 text-sm font-serif text-[#8C857B] group-hover:text-[#2C2420]"
                        >
                            <span class="group-hover:hidden">{{
                                (idx + 1 + pageIndex * pageSize).toString().padStart(2, '0')
                            }}</span>
                            <button
                                type="button"
                                class="hidden group-hover:inline-flex ml-1 hover:text-[#C27E46] transition-colors"
                                @click.stop="playItem(item)"
                            >
                                <Play
                                    v-if="playLoadingItemId === item.id"
                                    :size="14"
                                    fill="currentColor"
                                    class="animate-pulse"
                                />
                                <Pause
                                    v-else-if="isItemPlaying(item)"
                                    :size="14"
                                    fill="currentColor"
                                />
                                <Play v-else :size="14" fill="currentColor" />
                            </button>
                        </div>
                        <div class="col-span-5 flex items-center gap-4">
                            <div class="w-10 h-10 shadow-sm bg-[#D6D1C7] overflow-hidden">
                                <img
                                    v-if="item.cover"
                                    :src="item.cover"
                                    :alt="item.title"
                                    class="w-full h-full object-cover"
                                />
                            </div>
                            <div>
                                <div class="font-serif text-base text-[#2C2420]">
                                    {{ item.title }}
                                </div>
                                <div v-if="item.badge" class="text-[10px] text-[#B0AAA0]">
                                    {{ item.badge }}
                                </div>
                            </div>
                        </div>
                        <div class="col-span-4 text-sm text-[#5E5950]">{{ item.subtitle }}</div>
                        <div class="col-span-2 text-sm text-[#8C857B] text-right">
                            {{ item.details }}
                        </div>
                    </div>
                </div>
            </div>

            <div v-if="totalPageCount > 1" class="flex justify-center items-center mt-12 gap-6">
                <button
                    :disabled="pageIndex === 0 || isLoading"
                    class="p-2 text-[#8C857B] hover:text-[#C27E46] disabled:opacity-30 disabled:hover:text-[#8C857B] transition-colors"
                    @click="handlePageChange(pageIndex - 1)"
                >
                    <ChevronLeft :size="20" />
                </button>
                <span class="font-serif text-sm text-[#5E5950]">
                    Page {{ pageIndex + 1 }} of {{ totalPageCount }}
                </span>
                <button
                    :disabled="pageIndex >= totalPageCount - 1 || isLoading"
                    class="p-2 text-[#8C857B] hover:text-[#C27E46] disabled:opacity-30 disabled:hover:text-[#8C857B] transition-colors"
                    @click="handlePageChange(pageIndex + 1)"
                >
                    <ChevronRight :size="20" />
                </button>
            </div>
        </div>
    </div>
</template>
