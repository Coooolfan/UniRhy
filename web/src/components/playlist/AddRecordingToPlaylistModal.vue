<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Music, Search } from 'lucide-vue-next'
import { api, normalizeApiError } from '@/ApiInstance'

type PlaylistOption = {
    id: number
    name: string
}

const props = defineProps<{
    open: boolean
    recordingId: number | null
    recordingTitle?: string
}>()

const emit = defineEmits<{
    (e: 'close'): void
}>()

const playlists = ref<PlaylistOption[]>([])
const keyword = ref('')
const error = ref('')
const isLoading = ref(false)
const isSubmitting = ref(false)

const filteredPlaylists = computed(() => {
    const query = keyword.value.trim().toLowerCase()
    if (!query) {
        return playlists.value
    }
    return playlists.value.filter((playlist) => playlist.name.toLowerCase().includes(query))
})

const resetState = () => {
    playlists.value = []
    keyword.value = ''
    error.value = ''
    isLoading.value = false
    isSubmitting.value = false
}

const fetchPlaylists = async () => {
    isLoading.value = true
    error.value = ''
    try {
        const data = await api.playlistController.listPlaylists()
        playlists.value = data.map((playlist) => ({
            id: playlist.id,
            name: playlist.name?.trim() || '未命名歌单',
        }))
    } catch (e) {
        const normalized = normalizeApiError(e)
        error.value = normalized.message ?? '歌单加载失败'
        playlists.value = []
    } finally {
        isLoading.value = false
    }
}

const closeModal = () => {
    if (isSubmitting.value) {
        return
    }
    emit('close')
}

const addToPlaylist = async (playlist: PlaylistOption) => {
    const recordingId = props.recordingId
    if (recordingId === null || isSubmitting.value) {
        return
    }
    isSubmitting.value = true
    error.value = ''
    try {
        await api.playlistController.addRecordingToPlaylist({
            id: playlist.id,
            recordingId,
        })
        emit('close')
    } catch (e) {
        const normalized = normalizeApiError(e)
        error.value = normalized.message ?? '添加到歌单失败'
    } finally {
        isSubmitting.value = false
    }
}

watch(
    () => props.open,
    (open) => {
        if (open) {
            resetState()
            void fetchPlaylists()
            return
        }
        resetState()
    },
)
</script>

<template>
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
                v-if="open"
                class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#2B221B]/60"
                @click.self="closeModal"
            >
                <div
                    class="bg-[#fffcf5] w-full max-w-md max-h-[85vh] flex flex-col shadow-[0_8px_30px_rgba(0,0,0,0.12)] border border-[#EAE6DE] relative transform transition-all"
                >
                    <!-- Decorative Corner -->
                    <div
                        class="absolute top-0 right-0 w-16 h-16 bg-linear-to-bl from-[#EAE6DE]/30 to-transparent pointer-events-none"
                    ></div>

                    <!-- Header -->
                    <div class="px-8 pt-8 pb-6 text-center shrink-0">
                        <div
                            class="inline-flex items-center justify-center w-12 h-12 rounded-full bg-[#FAF9F6] mb-4 border border-[#EAE6DE]"
                        >
                            <Music :size="24" class="text-[#C67C4E]" />
                        </div>
                        <h3 class="font-serif text-2xl text-[#2B221B]">添加到歌单</h3>
                        <p class="text-xs text-[#8A8A8A] mt-2 font-serif italic truncate px-4">
                            {{ recordingTitle?.trim() || 'Add to Playlist' }}
                        </p>
                    </div>

                    <!-- Search -->
                    <div class="px-8 mb-4 shrink-0">
                        <label class="relative block group">
                            <Search
                                :size="14"
                                class="absolute left-3 top-1/2 -translate-y-1/2 text-[#9C968B] group-focus-within:text-[#C67C4E] transition-colors"
                            />
                            <input
                                v-model="keyword"
                                type="text"
                                placeholder="搜索歌单..."
                                class="w-full bg-[#F7F5F0] border-b border-[#D6D1C4] pl-9 pr-3 py-3 text-sm text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE]"
                                :disabled="isSubmitting"
                            />
                        </label>
                    </div>

                    <!-- List -->
                    <div class="px-8 overflow-y-auto flex-1 min-h-0">
                        <div v-if="isLoading" class="text-sm text-[#9C968B] text-center py-4">
                            加载中...
                        </div>
                        <p v-else-if="error" class="text-sm text-[#B95D5D] text-center py-4">
                            {{ error }}
                        </p>
                        <div
                            v-else-if="playlists.length === 0"
                            class="text-sm text-[#8C857B] text-center py-4"
                        >
                            还没有歌单，请先在侧边栏创建歌单
                        </div>
                        <div
                            v-else-if="filteredPlaylists.length === 0"
                            class="text-sm text-[#8C857B] text-center py-4"
                        >
                            没有匹配的歌单
                        </div>
                        <ul v-else class="space-y-2 pb-4">
                            <li v-for="playlist in filteredPlaylists" :key="playlist.id">
                                <button
                                    type="button"
                                    class="w-full text-left px-4 py-3 bg-[#F7F5F0]/50 hover:bg-[#F7F5F0] border-b border-[#EAE6DE] last:border-0 transition-colors text-[#3D3D3D] disabled:opacity-60 disabled:cursor-not-allowed cursor-pointer group flex items-center justify-between"
                                    :disabled="isSubmitting || recordingId === null"
                                    @click="addToPlaylist(playlist)"
                                >
                                    <span
                                        class="font-serif group-hover:text-[#C67C4E] transition-colors"
                                        >{{ playlist.name }}</span
                                    >
                                </button>
                            </li>
                        </ul>
                    </div>

                    <!-- Footer -->
                    <div class="p-8 pt-6 mt-auto border-t border-[#EAE6DE] shrink-0">
                        <button
                            type="button"
                            class="w-full px-4 py-2.5 border border-[#D6D1C4] text-[#8A8A8A] hover:bg-[#F7F5F0] hover:text-[#5A5A5A] transition-colors text-sm uppercase tracking-wide"
                            :disabled="isSubmitting"
                            @click="closeModal"
                        >
                            取消
                        </button>
                    </div>
                </div>
            </div>
        </Transition>
    </Teleport>
</template>
