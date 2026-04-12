<script setup lang="ts">
import { Pause, Play } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/ApiInstance'
import { pickPlayableRecordingEntry, resolveCover } from '@/composables/recordingMedia'
import { useAudioStore } from '@/stores/audio'
import LibraryEmptyHint from '@/components/dashboard/LibraryEmptyHint.vue'

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
    defaultTrackMediaFileId?: number
}

const albums = ref<AlbumCard[]>([])
const playLoadingAlbumId = ref<number | null>(null)
const isLoadingAlbums = ref(false)
const hasAlbumError = ref(false)

const showAlbumEmptyNote = computed(() => !isLoadingAlbums.value && albums.value.length === 0)

const navigateToAlbum = (id: number) => {
    router.push({ name: 'album-detail', params: { id } })
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
        if (
            !album.defaultTrackSrc ||
            album.defaultRecordingId === undefined ||
            album.defaultTrackMediaFileId === undefined
        ) {
            const detail = await api.albumController.getAlbum({ id: album.id })
            const targetEntry = pickPlayableRecordingEntry(detail.recordings)
            if (!targetEntry?.playableAudio) {
                console.warn('No playable track for album', album.id)
                return
            }

            const targetTrack = targetEntry.recording
            album.defaultRecordingId = targetTrack.id
            album.defaultTrackTitle = targetTrack.title || targetTrack.comment || detail.title
            album.defaultTrackArtist =
                targetTrack.artists.map((artist) => artist.displayName).join(', ') || album.artist
            album.defaultTrackCover = targetTrack.cover?.url
                ? resolveCover(targetTrack.cover)
                : album.cover
            album.defaultTrackSrc = targetEntry.playableAudio.src
            album.defaultTrackMediaFileId = targetEntry.playableAudio.mediaFileId
        }

        if (
            !album.defaultTrackSrc ||
            album.defaultRecordingId === undefined ||
            album.defaultTrackMediaFileId === undefined
        ) {
            return
        }

        audioStore.play({
            id: album.defaultRecordingId,
            title: album.defaultTrackTitle || album.title,
            artist: album.defaultTrackArtist || album.artist,
            cover: album.defaultTrackCover || album.cover,
            src: album.defaultTrackSrc,
            mediaFileId: album.defaultTrackMediaFileId,
        })
    } catch (error) {
        console.error('Failed to play album:', error)
    } finally {
        playLoadingAlbumId.value = null
    }
}

const fetchAlbums = async () => {
    isLoadingAlbums.value = true
    hasAlbumError.value = false
    try {
        const page = await api.albumController.listAlbums({
            pageIndex: 0,
            pageSize,
        })
        albums.value = page.rows.map((album) => ({
            id: album.id,
            title: album.title ?? 'Untitled Album',
            artist: album.recordings?.[0]?.label ?? 'Unknown Artist',
            cover: resolveCover(album.cover),
        }))
    } catch (error) {
        albums.value = []
        hasAlbumError.value = true
        console.error('Failed to fetch albums:', error)
    } finally {
        isLoadingAlbums.value = false
    }
}

onMounted(() => {
    fetchAlbums()
})
</script>

<template>
    <div class="mb-8">
        <LibraryEmptyHint v-if="showAlbumEmptyNote" :has-error="hasAlbumError" />

        <div
            v-else
            class="grid grid-cols-2 gap-5 px-1 sm:grid-cols-3 sm:gap-8 sm:px-2 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6"
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
                        class="absolute bottom-3 right-3 flex h-8 w-8 translate-y-0 items-center justify-center rounded-full bg-white/90 opacity-100 shadow-sm transition-all duration-300 sm:translate-y-2 sm:opacity-0 sm:group-hover:translate-y-0 sm:group-hover:opacity-100"
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
                    class="line-clamp-2 font-serif text-base leading-tight text-[#2C2C2C] transition-colors group-hover:text-[#C27E46] sm:text-lg"
                >
                    {{ album.title }}
                </h4>
                <p class="text-xs text-[#9C968B] mt-1">{{ album.artist }}</p>
            </div>
        </div>
    </div>
</template>
