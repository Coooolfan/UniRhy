<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '@/ApiInstance'
import { resolveErrorMessage } from '@/i18n/errors'
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
const { t } = useI18n()

const isLoading = ref(false)
const errorMessage = ref('')
const artists = ref<ArtistItem[]>([])

const hasResults = computed(() => artists.value.length > 0)

const subtitleOf = (artist: ArtistItem) =>
    artist.alias.length > 0 ? artist.alias.join(' / ') : t('artistLibrary.fallback')

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
        errorMessage.value = resolveErrorMessage(error, 'errors.fallback.artistLoad')
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
        title: t('artistLibrary.editTitle'),
        size: 'md',
        props: {
            initialForm: {
                displayName: artist.displayName,
                alias: [...artist.alias],
                comment: artist.comment,
            },
            submitText: t('common.saveChanges'),
            submittingText: t('common.saving'),
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
        <div v-if="isLoading && artists.length === 0" class="text-sm text-[#8C857B]">
            {{ t('common.loading') }}
        </div>
        <div v-else-if="errorMessage" class="text-sm text-[#B75D5D]">
            {{ errorMessage }}
            <button class="ml-4 text-[#C27E46]" type="button" @click="fetchArtists">
                {{ t('common.retry') }}
            </button>
        </div>

        <div v-else :class="{ 'pointer-events-none opacity-50': isLoading }">
            <LibraryEmptyHint
                v-if="!hasResults"
                :show-settings-button="false"
                :title="t('artistLibrary.emptyTitle')"
                :description="[t('artistLibrary.emptyDescription')]"
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
