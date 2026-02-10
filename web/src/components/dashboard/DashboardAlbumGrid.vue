<script setup lang="ts">
import { Pause, Play } from 'lucide-vue-next'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/ApiInstance'
import { useAudioStore } from '@/stores/audio'

const router = useRouter()
const audioStore = useAudioStore()
const pageSize = 10

type AlbumCard = {
    id: number
    title: string
    artist: string
    cover: string
    defaultRecordingId?: number
    defaultTrackTitle?: string
    defaultTrackArtist?: string
    defaultTrackCover?: string
    defaultTrackSrc?: string
}

const albums = ref<AlbumCard[]>([])
const playLoadingAlbumId = ref<number | null>(null)

const resolveCover = (coverId?: number) => {
    if (coverId !== undefined) {
        return `/api/media/${coverId}`
    }
    return ''
}

const navigateToAlbum = (id: number) => {
    router.push({ name: 'album-detail', params: { id } })
}

type Asset = {
    mediaFile: {
        id: number
        mimeType: string
    }
}

const resolveAudio = (assets: readonly Asset[]) => {
    const audioAsset = assets.find((asset) => asset.mediaFile.mimeType.startsWith('audio/'))
    if (audioAsset) {
        return `/api/media/${audioAsset.mediaFile.id}`
    }
    return undefined
}

const isAlbumPlaying = (album: AlbumCard) => {
    return (
        audioStore.isPlaying &&
        album.defaultRecordingId !== undefined &&
        audioStore.currentTrack?.id === album.defaultRecordingId
    )
}

const playAlbum = async (album: AlbumCard) => {
    if (playLoadingAlbumId.value === album.id) {
        return
    }

    try {
        playLoadingAlbumId.value = album.id
        if (!album.defaultTrackSrc || album.defaultRecordingId === undefined) {
            const detail = await api.albumController.getAlbum({ id: album.id })
            const defaultTrack = detail.recordings.find(
                (recording) => recording.defaultInWork && resolveAudio(recording.assets || []),
            )
            const firstPlayableTrack = detail.recordings.find((recording) =>
                resolveAudio(recording.assets || []),
            )
            const targetTrack = defaultTrack ?? firstPlayableTrack
            const targetSrc = resolveAudio(targetTrack?.assets || [])
            if (!targetTrack || !targetSrc) {
                console.warn('No playable track for album', album.id)
                return
            }

            album.defaultRecordingId = targetTrack.id
            album.defaultTrackTitle = targetTrack.title || targetTrack.comment || detail.title
            album.defaultTrackArtist =
                targetTrack.artists.map((artist) => artist.name).join(', ') || album.artist
            album.defaultTrackCover = targetTrack.cover?.id
                ? resolveCover(targetTrack.cover.id)
                : album.cover
            album.defaultTrackSrc = targetSrc
        }

        if (!album.defaultTrackSrc || album.defaultRecordingId === undefined) {
            return
        }

        audioStore.play({
            id: album.defaultRecordingId,
            title: album.defaultTrackTitle || album.title,
            artist: album.defaultTrackArtist || album.artist,
            cover: album.defaultTrackCover || album.cover,
            src: album.defaultTrackSrc,
        })
    } catch (error) {
        console.error('Failed to play album:', error)
    } finally {
        playLoadingAlbumId.value = null
    }
}

const fetchAlbums = async () => {
    try {
        const page = await api.albumController.listAlbums({
            pageIndex: 0,
            pageSize,
        })
        albums.value = page.rows.map((album) => ({
            id: album.id,
            title: album.title ?? 'Untitled Album',
            artist: album.recordings?.[0]?.label ?? 'Unknown Artist',
            cover: resolveCover(album.cover?.id),
        }))
    } catch (error) {
        console.error('Failed to fetch albums:', error)
    }
}

onMounted(() => {
    fetchAlbums()
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
                    <button
                        type="button"
                        class="absolute bottom-3 right-3 w-8 h-8 bg-white/90 rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 transition-all duration-300 shadow-sm translate-y-2 group-hover:translate-y-0"
                        @click.stop="playAlbum(album)"
                    >
                        <Play
                            v-if="playLoadingAlbumId === album.id"
                            :size="12"
                            fill="#C27E46"
                            class="text-[#C27E46] animate-pulse"
                        />
                        <Pause
                            v-else-if="isAlbumPlaying(album)"
                            :size="12"
                            fill="#C27E46"
                            class="text-[#C27E46]"
                        />
                        <Play v-else :size="12" fill="#C27E46" class="text-[#C27E46]" />
                    </button>
                </div>
                <h4
                    class="font-serif text-[#2C2C2C] text-lg leading-tight group-hover:text-[#C27E46] transition-colors line-clamp-1"
                >
                    {{ album.title }}
                </h4>
                <p class="text-xs text-[#9C968B] mt-1">{{ album.artist }}</p>
                <p class="text-[10px] text-[#B0AAA0] mt-0.5 font-mono">--:--</p>
            </div>
        </div>
    </div>
</template>
