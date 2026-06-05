<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api, normalizeApiError } from '@/ApiInstance'
import ArtistCard from '@/components/artist/ArtistCard.vue'
import ArtistEditModal, { type ArtistEditForm } from '@/components/artist/ArtistEditModal.vue'
import LibraryEmptyHint from '@/components/dashboard/LibraryEmptyHint.vue'
import { useModal } from '@/composables/useModal'
import { useUserStore } from '@/stores/user'

type ArtistItem = {
    id: number
    displayName: string
    alias: string[]
    comment: string
}

const props = defineProps<{
    pageIndex: number
    pageSize: number
}>()

const emit = defineEmits<{
    (e: 'update:totalPageCount', value: number): void
}>()

const modal = useModal()
const userStore = useUserStore()

const isLoading = ref(false)
const errorMessage = ref('')
const artists = ref<ArtistItem[]>([])

const hasResults = computed(() => artists.value.length > 0)

const subtitleOf = (artist: ArtistItem) =>
    artist.alias.length > 0 ? artist.alias.join(' / ') : '艺术家'

const toArtistItem = (raw: {
    id: number
    displayName: string
    alias: ReadonlyArray<string>
    comment: string
}): ArtistItem => ({
    id: raw.id,
    displayName: raw.displayName,
    alias: [...raw.alias],
    comment: raw.comment,
})

const fetchArtists = async () => {
    isLoading.value = true
    errorMessage.value = ''

    try {
        const page = await api.artistController.listArtists({
            pageIndex: props.pageIndex,
            pageSize: props.pageSize,
        })
        emit('update:totalPageCount', page.totalPageCount)
        artists.value = page.rows.map((raw) => toArtistItem(raw))
    } catch (error) {
        errorMessage.value = normalizeApiError(error).message ?? '艺术家加载失败'
        artists.value = []
        emit('update:totalPageCount', 0)
    } finally {
        isLoading.value = false
    }
}

watch(
    () => [props.pageIndex, props.pageSize],
    () => {
        fetchArtists()
    },
    { immediate: true },
)

const openEditModal = async (artist: ArtistItem) => {
    if (!userStore.isAdmin) {
        return
    }

    await modal.open(ArtistEditModal, {
        title: '编辑艺术家',
        size: 'md',
        props: {
            initialForm: {
                displayName: artist.displayName,
                alias: [...artist.alias],
                comment: artist.comment,
            },
            submitText: '保存更改',
            submittingText: '保存中...',
            nameFailureMessage: '更新艺术家失败',
            onSubmit: async (form: ArtistEditForm) => {
                await api.artistController.updateArtist({
                    id: artist.id,
                    body: {
                        displayName: form.displayName,
                        alias: form.alias,
                        comment: form.comment,
                    },
                })
            },
        },
    })

    await fetchArtists()
}
</script>

<template>
    <div class="space-y-6">
        <div v-if="isLoading && artists.length === 0" class="text-sm text-[#8C857B]">加载中...</div>
        <div v-else-if="errorMessage" class="text-sm text-[#B75D5D]">
            {{ errorMessage }}
            <button class="ml-4 text-[#C27E46]" type="button" @click="fetchArtists">重试</button>
        </div>

        <div v-else :class="{ 'pointer-events-none opacity-50': isLoading }">
            <LibraryEmptyHint
                v-if="!hasResults"
                :show-settings-button="false"
                title="艺术家库空空如也"
                :description="['尚未录入任何艺术家。']"
            />
            <div
                v-else
                class="grid grid-cols-2 gap-5 sm:gap-8 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6"
            >
                <ArtistCard
                    v-for="artist in artists"
                    :key="artist.id"
                    :id="artist.id"
                    :title="artist.displayName"
                    :subtitle="subtitleOf(artist)"
                    :openable="userStore.isAdmin"
                    @open="openEditModal(artist)"
                />
            </div>
        </div>
    </div>
</template>
