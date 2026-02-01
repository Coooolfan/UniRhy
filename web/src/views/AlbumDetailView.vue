<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Play, Heart, MoreHorizontal, Share2, Pause } from 'lucide-vue-next'
import { api } from '@/ApiInstance'

const route = useRoute()
const isPlaying = ref(false)
const currentTrack = ref<number | null>(null)
const isLoading = ref(true)
const isCdVisible = ref(false)

type AlbumData = {
    title: string
    artist: string
    year: string
    type: string
    description: string
    cover: string
}

type Track = {
    id: number
    title: string
    duration: string
    played: boolean
}

const albumData = ref<AlbumData>({
    title: '',
    artist: '',
    year: '',
    type: 'Album',
    description: '',
    cover: '',
})

const tracks = ref<Track[]>([])

const resolveCover = (coverId?: number) => {
    if (coverId !== undefined) {
        return `/api/media/${coverId}`
    }
    return ''
}

const fetchAlbum = async (id: number) => {
    try {
        isLoading.value = true
        // 获取新专辑时重置 CD 动画
        isCdVisible.value = false

        const data = await api.albumController.getAlbum({ id })

        // 映射专辑信息
        const releaseYear = data.releaseDate
            ? new Date(data.releaseDate).getFullYear().toString()
            : ''
        const artistName = data.recordings?.[0]?.artists?.[0]?.name ?? 'Unknown Artist'

        albumData.value = {
            title: data.title,
            artist: artistName,
            year: releaseYear,
            type: data.kind || 'Album',
            description: data.comment || '',
            cover: resolveCover(data.cover?.id),
        }

        // 映射曲目
        tracks.value = (data.recordings || []).map((recording) => ({
            id: recording.id,
            title: recording.title || recording.comment || 'Untitled Track',
            // API 当前不提供时长，占位或移除
            duration: '',
            played: false,
        }))

        if (tracks.value.length > 0) {
            currentTrack.value = tracks.value[0]?.id ?? null
        }
    } catch (error) {
        console.error('Failed to fetch album details:', error)
    } finally {
        isLoading.value = false
        // 加载完成后触发动画
        setTimeout(() => {
            isCdVisible.value = true
        }, 100)
    }
}

onMounted(() => {
    const id = Number(route.params.id)
    if (!isNaN(id)) {
        fetchAlbum(id)
    }
})

watch(
    () => route.params.id,
    (newId) => {
        const id = Number(newId)
        if (!isNaN(id)) {
            fetchAlbum(id)
        }
    },
)
</script>

<template>
    <div class="flex-1 flex flex-col h-full relative">
        <!-- 顶部搜索栏 -->
        <DashboardTopBar />

        <div v-if="isLoading" class="flex-1 flex items-center justify-center text-[#8C857B]">
            Loading...
        </div>

        <div v-else class="px-8 pb-32 max-w-5xl mx-auto w-full">
            <!-- 专辑头部卡片 -->
            <div class="mt-8 flex flex-col md:flex-row gap-12 items-end mb-16">
                <!-- 带 CD 效果的封面艺术 -->
                <div
                    class="relative z-0 group shrink-0 w-64 h-64 md:w-80 md:h-80 select-none perspective-1000"
                >
                    <!-- 滑出的 CD 光盘 -->
                    <div
                        class="absolute top-2 right-2 bottom-2 left-2 bg-linear-to-tr from-gray-200 to-gray-100 rounded-full shadow-lg flex items-center justify-center transition-transform duration-2000 ease-out z-0"
                        :class="isCdVisible ? 'translate-x-16 md:translate-x-24' : 'translate-x-0'"
                    >
                        <div
                            class="w-1/3 h-1/3 border border-gray-300 rounded-full opacity-50"
                        ></div>
                        <div
                            class="absolute w-8 h-8 bg-[#EBE7E0] rounded-full border border-gray-300"
                        ></div>
                    </div>

                    <!-- 专辑封面 -->
                    <div
                        class="relative w-full h-full shadow-xl rounded-sm overflow-hidden bg-[#2C2420] z-10"
                    >
                        <img
                            v-if="albumData.cover"
                            :src="albumData.cover"
                            alt="Album Cover"
                            class="w-full h-full object-cover"
                        />
                        <div
                            v-else
                            class="w-full h-full flex items-center justify-center bg-[#2C2420] text-[#8C857B]"
                        >
                            <span class="text-xs">No Cover</span>
                        </div>
                    </div>
                </div>

                <!-- 专辑信息 -->
                <div class="flex flex-col gap-4 pb-2 w-full relative z-10">
                    <div
                        class="flex items-center gap-3 text-sm tracking-wider uppercase text-[#8C857B]"
                    >
                        <span>{{ albumData.type }}</span>
                        <span class="w-8 h-px bg-[#C17D46]"></span>
                        <span>{{ albumData.year }}</span>
                    </div>

                    <h1 class="text-5xl md:text-7xl font-serif text-[#2C2420] leading-tight">
                        {{ albumData.title }}
                    </h1>

                    <div class="text-xl text-[#5E564D] font-serif italic mb-2">
                        By {{ albumData.artist }}
                    </div>

                    <p class="text-sm text-[#8C857B] max-w-2xl line-clamp-3">
                        {{ albumData.description }}
                    </p>

                    <div class="flex items-center gap-4 mt-4">
                        <button
                            @click="isPlaying = !isPlaying"
                            class="px-8 py-3 border border-[#C17D46] text-[#C17D46] hover:bg-[#C17D46] hover:text-white transition-all duration-300 flex items-center gap-2 text-sm tracking-widest uppercase font-medium rounded-sm cursor-pointer"
                        >
                            <Pause v-if="isPlaying" :size="16" />
                            <Play v-else :size="16" fill="currentColor" />
                            {{ isPlaying ? '暂停播放' : '立即播放' }}
                        </button>
                        <button
                            class="p-3 text-[#8C857B] hover:text-[#C17D46] transition-colors border border-transparent hover:border-[#DCD6CC] rounded-full cursor-pointer"
                        >
                            <Heart :size="20" />
                        </button>
                        <button
                            class="p-3 text-[#8C857B] hover:text-[#C17D46] transition-colors border border-transparent hover:border-[#DCD6CC] rounded-full cursor-pointer"
                        >
                            <MoreHorizontal :size="20" />
                        </button>
                    </div>
                </div>
            </div>

            <!-- 曲目列表容器 - 卡片/纸张风格 -->
            <div class="bg-[#FDFBF7] rounded-sm shadow-sm p-8 md:p-12 relative">
                <!-- 右下角的纸张堆叠效果 -->
                <div
                    class="absolute -bottom-2 -right-2 w-full h-full bg-[#F5F1EA] rounded-sm -z-10 transform translate-x-1 translate-y-1"
                ></div>

                <div class="flex items-center justify-between mb-8 border-b border-[#EFEBE4] pb-4">
                    <h3 class="font-serif text-2xl text-[#2C2420]">Tracklist</h3>
                    <div class="text-xs text-[#8C857B] uppercase tracking-widest">
                        {{ tracks.length }} Songs
                    </div>
                </div>

                <div class="flex flex-col">
                    <div
                        v-for="(track, index) in tracks"
                        :key="track.id"
                        @click="currentTrack = track.id"
                        class="group flex items-center gap-6 py-4 px-4 rounded-sm transition-all duration-200 cursor-pointer border-b border-transparent hover:bg-[#F2EFE9]"
                        :class="{ 'bg-[#F2EFE9]': currentTrack === track.id }"
                    >
                        <div
                            class="w-6 text-center font-serif text-lg"
                            :class="
                                currentTrack === track.id
                                    ? 'text-[#C17D46]'
                                    : 'text-[#DCD6CC] group-hover:text-[#8C857B]'
                            "
                        >
                            <div
                                v-if="currentTrack === track.id && isPlaying"
                                class="flex gap-0.5 justify-center h-4 items-end"
                            >
                                <div class="w-0.5 bg-[#C17D46] h-2 animate-pulse"></div>
                                <div class="w-0.5 bg-[#C17D46] h-4 animate-pulse delay-75"></div>
                                <div class="w-0.5 bg-[#C17D46] h-3 animate-pulse delay-150"></div>
                            </div>
                            <span v-else>{{ index + 1 }}</span>
                        </div>

                        <div class="flex-1">
                            <div
                                class="text-base font-medium"
                                :class="
                                    currentTrack === track.id ? 'text-[#C17D46]' : 'text-[#4A433B]'
                                "
                            >
                                {{ track.title }}
                            </div>
                        </div>

                        <div
                            class="hidden md:flex opacity-0 group-hover:opacity-100 transition-opacity gap-4 mr-4 text-[#8C857B]"
                        >
                            <Heart :size="16" class="hover:text-[#C17D46]" />
                            <Share2 :size="16" class="hover:text-[#C17D46]" />
                        </div>

                        <div class="text-sm text-[#8C857B] font-mono w-12 text-right">
                            {{ track.duration }}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>
