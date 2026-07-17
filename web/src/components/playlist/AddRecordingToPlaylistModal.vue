<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Search } from 'lucide-vue-next'
import { api } from '@/ApiInstance'
import { resolveErrorMessage } from '@/i18n/errors'
import { useModalContext } from '@/components/modals/modalContext'

const { t } = useI18n()

type PlaylistOption = {
    id: number
    name: string
}

const props = defineProps<{
    recordingId: number | null
}>()

const modal = useModalContext<undefined>()

const playlists = ref<PlaylistOption[]>([])
const keyword = ref('')
const error = ref('')
const isLoading = ref(true)
const isSubmitting = ref(false)

const filteredPlaylists = computed(() => {
    const query = keyword.value.trim().toLowerCase()
    if (!query) {
        return playlists.value
    }

    return playlists.value.filter((playlist) => playlist.name.toLowerCase().includes(query))
})

const closeModal = () => {
    if (isSubmitting.value) {
        return
    }

    modal.close()
}

const fetchPlaylists = async () => {
    isLoading.value = true
    error.value = ''

    try {
        const data = await api.playlistController.listPlaylists()
        playlists.value = data.map((playlist) => ({
            id: playlist.id,
            name: playlist.name?.trim() || t('addToPlaylist.unnamedPlaylist'),
        }))
    } catch (fetchError) {
        error.value = resolveErrorMessage(fetchError, 'errors.fallback.playlistLoad')
        playlists.value = []
    } finally {
        isLoading.value = false
    }
}

onMounted(() => {
    void fetchPlaylists()
})

const addToPlaylist = async (playlist: PlaylistOption) => {
    if (props.recordingId === null || isSubmitting.value) {
        return
    }

    isSubmitting.value = true
    error.value = ''

    try {
        await api.playlistController.addRecordingToPlaylist({
            id: playlist.id,
            recordingId: props.recordingId,
        })
        modal.resolve(undefined)
    } catch (submitError) {
        error.value = resolveErrorMessage(submitError, 'errors.fallback.playlistAddRecording')
    } finally {
        isSubmitting.value = false
    }
}
</script>

<template>
    <div class="flex max-h-[72vh] flex-col">
        <div class="mb-4">
            <label class="group relative block">
                <Search
                    :size="14"
                    class="absolute top-1/2 left-3 -translate-y-1/2 text-[#9C968B] transition-colors group-focus-within:text-[#C67C4E]"
                />
                <input
                    v-model="keyword"
                    type="text"
                    :placeholder="t('addToPlaylist.searchPlaceholder')"
                    class="w-full border-b border-[#D6D1C4] bg-[#F7F5F0] py-3 pr-3 pl-9 font-serif text-sm text-[#3D3D3D] transition-colors placeholder:text-[#BDB9AE] focus:border-[#C67C4E] focus:outline-none"
                    :disabled="isSubmitting"
                />
            </label>
        </div>

        <div class="min-h-0 flex-1 overflow-y-auto">
            <div v-if="isLoading" class="py-4 text-center text-sm text-[#9C968B]">
                {{ t('common.loading') }}
            </div>
            <p v-else-if="error" class="py-4 text-center text-sm text-[#B95D5D]">{{ error }}</p>
            <div v-else-if="playlists.length === 0" class="py-4 text-center text-sm text-[#8C857B]">
                {{ t('addToPlaylist.noPlaylists') }}
            </div>
            <div
                v-else-if="filteredPlaylists.length === 0"
                class="py-4 text-center text-sm text-[#8C857B]"
            >
                {{ t('addToPlaylist.noMatch') }}
            </div>
            <ul v-else class="space-y-2 pb-4">
                <li v-for="playlist in filteredPlaylists" :key="playlist.id">
                    <button
                        type="button"
                        class="group flex w-full items-center justify-between border-b border-[#EAE6DE] bg-[#F7F5F0]/50 px-4 py-3 text-left text-[#3D3D3D] transition-colors last:border-0 hover:bg-[#F7F5F0] disabled:cursor-not-allowed disabled:opacity-60"
                        :disabled="isSubmitting || recordingId === null"
                        @click="addToPlaylist(playlist)"
                    >
                        <span class="font-serif transition-colors group-hover:text-[#C67C4E]">
                            {{ playlist.name }}
                        </span>
                    </button>
                </li>
            </ul>
        </div>

        <div class="mt-6 border-t border-[#EAE6DE] pt-6">
            <button
                type="button"
                class="w-full border border-[#D6D1C4] px-4 py-2.5 text-sm uppercase tracking-wide text-[#8A8A8A] transition-colors hover:bg-[#F7F5F0] hover:text-[#5A5A5A]"
                :disabled="isSubmitting"
                @click="closeModal"
            >
                {{ t('common.cancel') }}
            </button>
        </div>
    </div>
</template>
