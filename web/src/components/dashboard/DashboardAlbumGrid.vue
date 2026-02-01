<script setup lang="ts">
import { Play } from 'lucide-vue-next'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/ApiInstance'

const router = useRouter()

type AlbumCard = {
    id: number
    title: string
    artist: string
    cover: string
}

const albums = ref<AlbumCard[]>([])

const resolveCover = (coverId?: number) => {
    if (coverId !== undefined) {
        return `/api/media/${coverId}`
    }
    return ''
}

const navigateToAlbum = (id: number) => {
    router.push({ name: 'album-detail', params: { id } })
}

onMounted(async () => {
    try {
        const list = await api.albumController.listAlbums()
        if (list.length === 0) {
            return
        }
        albums.value = list.map((album) => ({
            id: album.id,
            title: album.title ?? 'Untitled Album',
            artist: album.recordings?.[0]?.label ?? 'Unknown Artist',
            cover: resolveCover(album.cover?.id),
        }))
    } catch (error) {
        console.error('Failed to fetch albums:', error)
    }
})
</script>

<template>
    <div class="mb-8">
        <div
            class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-8 px-2"
        >
            <div
                v-for="(album, idx) in albums"
                :key="idx"
                class="group cursor-pointer"
                @click="navigateToAlbum(album.id)"
            >
                <div
                    class="aspect-square bg-gray-200 mb-4 shadow-[0_4px_12px_-4px_rgba(168,160,149,0.3)] group-hover:shadow-[0_12px_24px_-8px_rgba(168,160,149,0.5)] group-hover:-translate-y-1 transition-all duration-500 rounded-sm relative border-[5px] border-white overflow-hidden"
                >
                    <img
                        v-if="album.cover"
                        :src="album.cover"
                        :alt="album.title"
                        class="w-full h-full object-cover filter sepia-[0.2] group-hover:sepia-0 transition-all duration-500"
                    />
                    <!-- Floating Play Button -->
                    <div
                        class="absolute bottom-3 right-3 w-8 h-8 bg-white/90 rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 transition-all duration-300 shadow-sm translate-y-2 group-hover:translate-y-0"
                    >
                        <Play :size="12" fill="#C27E46" class="text-[#C27E46] `ml-px" />
                    </div>
                </div>
                <h4
                    class="font-serif text-[#2C2C2C] text-lg leading-tight group-hover:text-[#C27E46] transition-colors line-clamp-1"
                >
                    {{ album.title }}
                </h4>
                <p class="text-xs text-[#9C968B] mt-1">{{ album.artist }}</p>
            </div>
        </div>
    </div>
</template>
