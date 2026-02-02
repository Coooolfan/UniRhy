<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { LayoutGrid, List as ListIcon, Play } from 'lucide-vue-next'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import { api, normalizeApiError } from '@/ApiInstance'

type AlbumItem = {
    id: number
    title: string
    artist: string
    year: string
    kind: string
    cover: string
    tracks: number
}

const router = useRouter()
const viewMode = ref<'grid' | 'list'>('grid')
const activeTab = ref('All')
const searchQuery = ref('')
const albums = ref<AlbumItem[]>([])
const isLoading = ref(false)
const errorMessage = ref('')

const resolveCover = (coverId?: number) => (coverId ? `/api/media/${coverId}` : '')

const formatYear = (releaseDate?: string) => {
    if (!releaseDate) {
        return ''
    }
    const date = new Date(releaseDate)
    if (Number.isNaN(date.getTime())) {
        return ''
    }
    return date.getFullYear().toString()
}

const fetchAlbums = async () => {
    isLoading.value = true
    errorMessage.value = ''
    try {
        const list = await api.albumController.listAlbums()
        albums.value = list.map((album) => ({
            id: album.id,
            title: album.title || 'Untitled Album',
            artist: album.recordings?.[0]?.label || 'Unknown Artist',
            year: formatYear(album.releaseDate),
            kind: album.kind?.trim() ? album.kind : '其他',
            cover: resolveCover(album.cover?.id),
            tracks: album.recordings?.length ?? 0,
        }))
    } catch (error) {
        const normalized = normalizeApiError(error)
        errorMessage.value = normalized.message ?? '专辑加载失败'
    } finally {
        isLoading.value = false
    }
}

const tabs = computed(() => {
    const kinds = new Set(
        albums.value.map((album) => album.kind).filter((kind) => kind.trim().length > 0),
    )
    return ['All', ...Array.from(kinds)]
})

const filteredAlbums = computed(() => {
    const query = searchQuery.value.trim().toLowerCase()
    return albums.value.filter((album) => {
        if (activeTab.value !== 'All' && album.kind !== activeTab.value) {
            return false
        }
        if (!query) {
            return true
        }
        return (
            album.title.toLowerCase().includes(query) ||
            album.artist.toLowerCase().includes(query) ||
            album.year.toLowerCase().includes(query) ||
            album.kind.toLowerCase().includes(query)
        )
    })
})

const navigateToAlbum = (id: number) => {
    router.push({ name: 'album-detail', params: { id } })
}

onMounted(() => {
    fetchAlbums()
})
</script>

<template>
    <div class="pb-32">
        <DashboardTopBar v-model="searchQuery" />

        <div class="px-8 pt-6">
            <div class="flex flex-wrap items-end justify-between gap-6 mb-8">
                <div>
                    <h2 class="text-4xl font-serif text-[#2C2420] mb-2">专辑资料库</h2>
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
                    v-for="tab in tabs"
                    :key="tab"
                    class="text-sm tracking-wide transition-colors relative"
                    :class="
                        activeTab === tab
                            ? 'text-[#2C2420] font-semibold border-b-2 border-[#C27E46] pb-4 -mb-4'
                            : 'text-[#8C857B] hover:text-[#5E5950] pb-4 -mb-4'
                    "
                    @click="activeTab = tab"
                >
                    {{ tab === 'All' ? '全部专辑' : tab }}
                </button>
            </div>
        </div>

        <div class="px-8 mt-10">
            <div v-if="isLoading" class="text-[#8C857B] text-sm">专辑加载中...</div>
            <div v-else-if="errorMessage" class="text-[#B75D5D] text-sm">
                {{ errorMessage }}
                <button class="ml-4 text-[#C27E46]" type="button" @click="fetchAlbums">重试</button>
            </div>
            <div v-else-if="filteredAlbums.length === 0" class="text-[#8C857B] text-sm">
                未找到匹配的专辑。
            </div>

            <div
                v-else-if="viewMode === 'grid'"
                class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-x-8 gap-y-12"
            >
                <div
                    v-for="album in filteredAlbums"
                    :key="album.id"
                    class="group cursor-pointer"
                    @click="navigateToAlbum(album.id)"
                >
                    <div
                        class="relative aspect-square mb-5 transition-transform duration-500 ease-out group-hover:-translate-y-2"
                    >
                        <div
                            class="absolute top-1/2 left-1/2 w-[95%] h-[95%] -translate-x-1/2 -translate-y-1/2 bg-[#1A1A1A] rounded-full shadow-xl transition-all duration-700 ease-out group-hover:translate-x-4 opacity-0 group-hover:opacity-100 flex items-center justify-center"
                        >
                            <div
                                class="w-1/3 h-1/3 bg-[#EBE7E0] rounded-full border border-[#333]"
                            ></div>
                        </div>

                        <div
                            class="relative w-full h-full shadow-lg group-hover:shadow-2xl transition-shadow duration-500 bg-[#D6D1C7]"
                        >
                            <img
                                v-if="album.cover"
                                :src="album.cover"
                                :alt="album.title"
                                class="w-full h-full object-cover"
                            />
                            <div
                                v-else
                                class="w-full h-full flex items-center justify-center text-xs text-[#8C857B]"
                            >
                                No Cover
                            </div>

                            <div
                                class="absolute inset-0 bg-black/10 opacity-0 group-hover:opacity-100 transition-opacity duration-300 flex items-center justify-center"
                            >
                                <div
                                    class="w-12 h-12 bg-[#EBE7E0]/90 backdrop-blur-sm rounded-full flex items-center justify-center shadow-sm scale-90 group-hover:scale-100 transition-transform duration-300"
                                >
                                    <Play :size="20" class="ml-1 text-[#2C2420]" fill="#2C2420" />
                                </div>
                            </div>
                        </div>
                    </div>

                    <div class="text-center md:text-left">
                        <h3
                            class="font-serif text-lg leading-tight mb-1 truncate text-[#1A1A1A] group-hover:text-[#C27E46] transition-colors"
                        >
                            {{ album.title }}
                        </h3>
                        <p class="text-xs text-[#8C857B] uppercase tracking-wider">
                            {{ album.artist }}
                        </p>
                        <p class="text-[10px] text-[#B0AAA0] mt-1">
                            {{ album.year || '----' }} · {{ album.kind }}
                        </p>
                    </div>
                </div>
            </div>

            <div v-else class="space-y-2">
                <div
                    class="grid grid-cols-12 text-xs text-[#8C857B] uppercase tracking-wider border-b border-[#D6D1C7] pb-2 mb-2 px-4"
                >
                    <div class="col-span-1">#</div>
                    <div class="col-span-5">Title</div>
                    <div class="col-span-3">Artist</div>
                    <div class="col-span-2">Year</div>
                    <div class="col-span-1 text-right">Tracks</div>
                </div>
                <div
                    v-for="(album, idx) in filteredAlbums"
                    :key="album.id"
                    class="grid grid-cols-12 items-center px-4 py-3 hover:bg-[#EFEAE2]/60 rounded-sm group transition-colors cursor-pointer"
                    @click="navigateToAlbum(album.id)"
                >
                    <div
                        class="col-span-1 text-sm font-serif text-[#8C857B] group-hover:text-[#2C2420]"
                    >
                        <span class="group-hover:hidden">{{
                            (idx + 1).toString().padStart(2, '0')
                        }}</span>
                        <Play
                            :size="14"
                            class="hidden group-hover:block ml-1"
                            fill="currentColor"
                        />
                    </div>
                    <div class="col-span-5 flex items-center gap-4">
                        <div class="w-10 h-10 shadow-sm bg-[#D6D1C7] overflow-hidden">
                            <img
                                v-if="album.cover"
                                :src="album.cover"
                                :alt="album.title"
                                class="w-full h-full object-cover"
                            />
                        </div>
                        <div>
                            <div class="font-serif text-base text-[#2C2420]">{{ album.title }}</div>
                            <div class="text-[10px] text-[#B0AAA0]">{{ album.kind }}</div>
                        </div>
                    </div>
                    <div class="col-span-3 text-sm text-[#5E5950]">{{ album.artist }}</div>
                    <div class="col-span-2 text-sm text-[#8C857B]">{{ album.year || '-' }}</div>
                    <div class="col-span-1 text-sm text-[#8C857B] text-right">
                        {{ album.tracks }}
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>
