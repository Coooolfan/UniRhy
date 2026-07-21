import { ref } from 'vue'
import { api, getAuthToken } from '@/ApiInstance'
import { i18n } from '@/i18n'
import { resolveErrorMessage } from '@/i18n/errors'
import { buildApiUrl } from '@/runtime/platform'
import { runtimeFetch } from '@/runtime/http'
import type { PluginInfoResponse } from '@/__generated/model/static/PluginInfoResponse'

const TOKEN_HEADER_NAME = 'unirhy-token'

export const usePluginSettings = () => {
    const plugins = ref<ReadonlyArray<PluginInfoResponse>>([])
    const isLoading = ref(false)
    const isUploading = ref(false)
    const error = ref('')

    const fetch = async () => {
        isLoading.value = true
        error.value = ''
        try {
            plugins.value = await api.pluginController.listPlugins()
        } catch (e) {
            error.value = resolveErrorMessage(e, 'errors.fallback.pluginList')
        } finally {
            isLoading.value = false
        }
    }

    const upload = async (file: File) => {
        isUploading.value = true
        error.value = ''
        try {
            await api.pluginController.upload({ body: { file } })
            await fetch()
        } catch (e) {
            error.value = resolveErrorMessage(e, 'errors.fallback.pluginUpload')
            throw e
        } finally {
            isUploading.value = false
        }
    }

    const setEnabled = async (id: string, enabled: boolean) => {
        try {
            await api.pluginController.setEnabled({ id, enabled })
            await fetch()
        } catch (e) {
            error.value = resolveErrorMessage(e)
            throw e
        }
    }

    const updateConcurrency = async (id: string, concurrency: number) => {
        try {
            await api.pluginController.updateConcurrency({ id, concurrency })
            await fetch()
        } catch (e) {
            error.value = resolveErrorMessage(e)
            throw e
        }
    }

    const deletePlugin = async (id: string) => {
        try {
            await api.pluginController.delete({ id })
            await fetch()
        } catch (e) {
            error.value = resolveErrorMessage(e, 'common.deleteFailed')
            throw e
        }
    }

    const downloadPlugin = async (plugin: PluginInfoResponse) => {
        const token = getAuthToken()
        const headers: Record<string, string> = {}
        if (token) headers[TOKEN_HEADER_NAME] = token

        const response = await runtimeFetch(buildApiUrl(`/api/plugins/${plugin.id}/package`), {
            method: 'GET',
            credentials: 'include',
            headers,
        })
        if (!response.ok)
            throw new Error(i18n.global.t('plugins.downloadFailed', { status: response.status }))

        const blob = await response.blob()
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = `${plugin.id}-${plugin.version}.up`
        a.click()
        URL.revokeObjectURL(url)
    }

    return {
        plugins,
        isLoading,
        isUploading,
        error,
        fetch,
        upload,
        setEnabled,
        updateConcurrency,
        deletePlugin,
        downloadPlugin,
    }
}
