<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Check, Database, Edit2, HardDrive, Plus, Trash2 } from 'lucide-vue-next'
import { api, normalizeApiError } from '@/ApiInstance'

type StorageNode = {
    id: number
    name: string
    parentPath: string
    readonly: boolean
}

type SystemConfig = {
    ossProviderId: number | null
    fsProviderId: number | null
}

const storageNodes = ref<StorageNode[]>([])
const systemConfig = ref<SystemConfig>({
    ossProviderId: null,
    fsProviderId: null,
})
const configExists = ref(true)

const isEditing = ref<number | null>(null)
const isCreating = ref(false)
const isSaving = ref(false)
const isLoadingSystem = ref(false)
const isLoadingStorage = ref(false)

const systemError = ref('')
const storageError = ref('')

const editForm = reactive({ name: '', parentPath: '', readonly: false })

const activeFsLabel = computed(() => {
    const activeId = systemConfig.value.fsProviderId
    if (activeId === null) {
        return '未选择'
    }
    const node = storageNodes.value.find((item) => item.id === activeId)
    return node ? node.name : `ID ${activeId}`
})

const resetForm = () => {
    editForm.name = ''
    editForm.parentPath = ''
    editForm.readonly = false
}

const fetchSystemConfig = async () => {
    isLoadingSystem.value = true
    systemError.value = ''
    try {
        const config = await api.systemConfigController.get()
        systemConfig.value.fsProviderId = config.fsProviderId ?? null
        systemConfig.value.ossProviderId = config.ossProviderId ?? null
        configExists.value = true
    } catch (error) {
        const normalized = normalizeApiError(error)
        configExists.value = false
        systemConfig.value.fsProviderId = null
        systemConfig.value.ossProviderId = null
        systemError.value = normalized.message ?? '系统配置加载失败'
    } finally {
        isLoadingSystem.value = false
    }
}

const fetchStorageNodes = async () => {
    isLoadingStorage.value = true
    storageError.value = ''
    try {
        const list = await api.fileSystemStorageController.list()
        storageNodes.value = list.map((item) => ({
            id: item.id,
            name: item.name,
            parentPath: item.parentPath,
            readonly: item.readonly,
        }))
    } catch (error) {
        const normalized = normalizeApiError(error)
        storageError.value = normalized.message ?? '存储节点加载失败'
    } finally {
        isLoadingStorage.value = false
    }
}

const loadData = async () => {
    await Promise.all([fetchSystemConfig(), fetchStorageNodes()])
}

const handleDelete = async (id: number) => {
    if (isSaving.value) {
        return
    }
    isSaving.value = true
    storageError.value = ''
    try {
        await api.fileSystemStorageController.delete({ id })
        await Promise.all([fetchStorageNodes(), fetchSystemConfig()])
    } catch (error) {
        const normalized = normalizeApiError(error)
        storageError.value = normalized.message ?? '删除失败'
    } finally {
        isSaving.value = false
    }
}

const startEdit = (node: StorageNode) => {
    isCreating.value = false
    isEditing.value = node.id
    editForm.name = node.name
    editForm.parentPath = node.parentPath
    editForm.readonly = node.readonly
}

const cancelEdit = () => {
    isEditing.value = null
    isCreating.value = false
    resetForm()
}

const saveEdit = async () => {
    if (isEditing.value === null) {
        return
    }
    const name = editForm.name.trim()
    const parentPath = editForm.parentPath.trim()
    if (!name || !parentPath) {
        storageError.value = '请填写名称与路径'
        return
    }
    if (isSaving.value) {
        return
    }
    isSaving.value = true
    storageError.value = ''
    try {
        await api.fileSystemStorageController.update({
            id: isEditing.value,
            update: {
                name,
                parentPath,
                readonly: editForm.readonly,
            },
        })
        await fetchStorageNodes()
        isEditing.value = null
        resetForm()
    } catch (error) {
        const normalized = normalizeApiError(error)
        storageError.value = normalized.message ?? '更新失败'
    } finally {
        isSaving.value = false
    }
}

const startCreate = () => {
    isEditing.value = null
    isCreating.value = true
    resetForm()
}

const saveCreate = async () => {
    const name = editForm.name.trim()
    const parentPath = editForm.parentPath.trim()
    if (!name || !parentPath) {
        storageError.value = '请填写名称与路径'
        return
    }
    if (isSaving.value) {
        return
    }
    isSaving.value = true
    storageError.value = ''
    try {
        await api.fileSystemStorageController.create({
            body: {
                name,
                parentPath,
                readonly: editForm.readonly,
            },
        })
        await fetchStorageNodes()
        isCreating.value = false
        resetForm()
    } catch (error) {
        const normalized = normalizeApiError(error)
        storageError.value = normalized.message ?? '创建失败'
    } finally {
        isSaving.value = false
    }
}

const setActiveFsProvider = async (id: number) => {
    if (isSaving.value) {
        return
    }
    isSaving.value = true
    systemError.value = ''
    try {
        const payload = { fsProviderId: id }
        const config = await api.systemConfigController.update({ update: payload })
        systemConfig.value.fsProviderId = config.fsProviderId ?? null
        systemConfig.value.ossProviderId = config.ossProviderId ?? null
        configExists.value = true
    } catch (error) {
        const normalized = normalizeApiError(error)
        systemError.value = normalized.message ?? '系统配置更新失败'
    } finally {
        isSaving.value = false
    }
}

onMounted(() => {
    loadData()
})
</script>
<template>
    <div
        class="min-h-screen bg-[#f2efe9] text-[#4A3B32] font-sans selection:bg-[#C68C53] selection:text-white pb-32"
    >
        <div class="max-w-4xl mx-auto px-12 pt-12">
            <!-- Header -->
            <header class="mb-12">
                <h1 class="font-serif text-3xl text-[#2B221B] tracking-tight mb-2">系统设置</h1>
                <p class="text-[#8A8A8A] font-serif italic text-sm">
                    System Configuration & Storage Management
                </p>
            </header>

            <!-- System Configuration Section -->
            <section class="mb-16 animate-in fade-in duration-500">
                <h2
                    class="text-2xl font-serif text-[#4A3B32] mb-6 tracking-wide border-b border-[#E0Dcd0] pb-2"
                >
                    全局状态
                </h2>

                <div class="space-y-8">
                    <div
                        class="bg-[#FAF9F6] p-8 shadow-[0_4px_20px_-5px_rgba(0,0,0,0.05)] relative"
                    >
                        <!-- Paper stack effect -->
                        <div
                            class="absolute top-0 right-0 -mr-2 -mt-2 w-full h-full bg-white border border-[#EAE6DE] -z-10 shadow-sm"
                        ></div>

                        <h3 class="font-serif text-lg text-[#4A3B32] mb-6 flex items-center gap-2">
                            <Database :size="20" class="text-[#C68C53]" />
                            System Status
                        </h3>

                        <div class="flex items-center space-x-12">
                            <div class="text-center">
                                <div class="text-xs text-[#8A8A8A] mb-2 uppercase tracking-widest">
                                    FS Provider
                                </div>
                                <div class="font-serif text-xl text-[#C68C53]">
                                    <span v-if="isLoadingSystem">Loading...</span>
                                    <span v-else>{{ activeFsLabel }}</span>
                                </div>
                                <div
                                    class="text-[10px] text-[#B0AAA0] uppercase tracking-widest mt-2"
                                >
                                    ID: {{ systemConfig.fsProviderId ?? '-' }}
                                </div>
                            </div>
                        </div>

                        <div v-if="systemError" class="text-sm text-[#B95D5D] mt-4">
                            {{ systemError }}
                        </div>
                    </div>
                </div>
            </section>

            <!-- Storage Nodes Section -->
            <section class="animate-in fade-in duration-500">
                <div class="flex justify-between items-end mb-6 border-b border-[#E0Dcd0] pb-2">
                    <h2 class="text-2xl font-serif text-[#4A3B32] tracking-wide">存储节点</h2>
                    <button
                        class="group flex items-center gap-2 px-6 py-2 border border-[#C68C53] text-[#C68C53] hover:bg-[#C68C53] hover:text-white transition-all duration-300 text-sm tracking-widest outline-none disabled:opacity-50 disabled:cursor-not-allowed"
                        :disabled="isCreating || isSaving"
                        @click="startCreate"
                    >
                        <Plus :size="16" />
                        <span>新增存储</span>
                    </button>
                </div>

                <div v-if="storageError" class="text-sm text-[#B95D5D] mb-4">
                    {{ storageError }}
                </div>
                <div v-else-if="isLoadingStorage" class="text-sm text-[#8A8A8A] mb-4">
                    加载中...
                </div>

                <div class="space-y-6">
                    <!-- Create Form -->
                    <Transition
                        enter-active-class="transition duration-200 ease-out"
                        enter-from-class="opacity-0 -translate-y-2"
                        enter-to-class="opacity-100 translate-y-0"
                        leave-active-class="transition duration-150 ease-in"
                        leave-from-class="opacity-100 translate-y-0"
                        leave-to-class="opacity-0 -translate-y-2"
                    >
                        <div
                            v-if="isCreating"
                            class="bg-[#FAF9F6] p-6 shadow-[0_2px_15px_-5px_rgba(0,0,0,0.05)] border border-[#EAE6DE] relative"
                        >
                            <div class="space-y-4">
                                <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                                    <div class="group relative">
                                        <label
                                            class="text-xs text-[#C68C53] mb-1 block uppercase tracking-wider"
                                            >Name</label
                                        >
                                        <input
                                            v-model="editForm.name"
                                            type="text"
                                            placeholder="e.g. Local Backup"
                                            class="w-full bg-transparent border-b border-[#D6D0C5] py-2 text-[#4A3B32] placeholder-[#B0AAA0] focus:outline-none focus:border-[#C68C53] transition-colors duration-300"
                                        />
                                    </div>
                                    <div class="group relative">
                                        <label
                                            class="text-xs text-[#C68C53] mb-1 block uppercase tracking-wider"
                                            >Physical Path</label
                                        >
                                        <input
                                            v-model="editForm.parentPath"
                                            type="text"
                                            placeholder="/path/to/dir"
                                            class="w-full bg-transparent border-b border-[#D6D0C5] py-2 text-[#4A3B32] placeholder-[#B0AAA0] focus:outline-none focus:border-[#C68C53] transition-colors duration-300"
                                        />
                                    </div>
                                </div>
                                <div class="flex items-center justify-between pt-4">
                                    <label
                                        class="flex items-center space-x-2 text-[#6B5D52] cursor-pointer hover:text-[#4A3B32] transition-colors"
                                    >
                                        <div
                                            class="w-4 h-4 border transition-colors flex items-center justify-center"
                                            :class="
                                                editForm.readonly
                                                    ? 'bg-[#C68C53] border-[#C68C53]'
                                                    : 'border-[#C68C53]'
                                            "
                                        >
                                            <input
                                                v-model="editForm.readonly"
                                                type="checkbox"
                                                class="hidden"
                                            />
                                            <Check
                                                v-if="editForm.readonly"
                                                :size="12"
                                                class="text-white"
                                            />
                                        </div>
                                        <span class="text-sm">只读模式 (Read-Only)</span>
                                    </label>
                                    <div class="flex gap-4">
                                        <button
                                            class="text-[#8A8A8A] hover:text-[#5E4B35] text-sm tracking-widest px-4 py-2"
                                            @click="cancelEdit"
                                        >
                                            取消
                                        </button>
                                        <button
                                            class="bg-[#C68C53] text-white px-6 py-2 text-sm tracking-widest hover:bg-[#A0643B] transition-colors shadow-sm disabled:opacity-60"
                                            :disabled="isSaving"
                                            @click="saveCreate"
                                        >
                                            完成
                                        </button>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </Transition>

                    <!-- Node List -->
                    <div
                        v-for="node in storageNodes"
                        :key="node.id"
                        class="bg-[#FAF9F6] p-6 shadow-[0_2px_15px_-5px_rgba(0,0,0,0.05)] border border-[#EAE6DE] relative group hover:shadow-lg transition-all duration-500"
                    >
                        <div v-if="isEditing === node.id" class="space-y-4">
                            <!-- Edit Mode -->
                            <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                                <div>
                                    <label
                                        class="text-xs text-[#C68C53] mb-1 block uppercase tracking-wider"
                                        >Name</label
                                    >
                                    <input
                                        v-model="editForm.name"
                                        type="text"
                                        class="w-full bg-transparent border-b border-[#D6D0C5] py-2 text-[#4A3B32] placeholder-[#B0AAA0] focus:outline-none focus:border-[#C68C53] transition-colors duration-300"
                                    />
                                </div>
                                <div>
                                    <label
                                        class="text-xs text-[#C68C53] mb-1 block uppercase tracking-wider"
                                        >Physical Path</label
                                    >
                                    <input
                                        v-model="editForm.parentPath"
                                        type="text"
                                        class="w-full bg-transparent border-b border-[#D6D0C5] py-2 text-[#4A3B32] placeholder-[#B0AAA0] focus:outline-none focus:border-[#C68C53] transition-colors duration-300"
                                    />
                                </div>
                            </div>
                            <div class="flex items-center justify-between pt-4">
                                <label
                                    class="flex items-center space-x-2 text-[#6B5D52] cursor-pointer hover:text-[#4A3B32] transition-colors"
                                >
                                    <div
                                        class="w-4 h-4 border transition-colors flex items-center justify-center"
                                        :class="
                                            editForm.readonly
                                                ? 'bg-[#C68C53] border-[#C68C53]'
                                                : 'border-[#C68C53]'
                                        "
                                    >
                                        <input
                                            v-model="editForm.readonly"
                                            type="checkbox"
                                            class="hidden"
                                        />
                                        <Check
                                            v-if="editForm.readonly"
                                            :size="12"
                                            class="text-white"
                                        />
                                    </div>
                                    <span class="text-sm">只读模式 (Read-Only)</span>
                                </label>
                                <div class="flex gap-4">
                                    <button
                                        class="text-[#8A8A8A] hover:text-[#5E4B35] text-sm tracking-widest px-4 py-2"
                                        @click="cancelEdit"
                                    >
                                        取消
                                    </button>
                                    <button
                                        class="bg-[#C68C53] text-white px-6 py-2 text-sm tracking-widest hover:bg-[#A0643B] transition-colors shadow-sm disabled:opacity-60"
                                        :disabled="isSaving"
                                        @click="saveEdit"
                                    >
                                        完成
                                    </button>
                                </div>
                            </div>
                        </div>

                        <div v-else class="flex justify-between items-center">
                            <!-- View Mode -->
                            <div class="flex items-start gap-6">
                                <div class="p-4 bg-[#F2EFE9] rounded-full text-[#8A8A8A]">
                                    <HardDrive :size="24" :stroke-width="1.5" />
                                </div>
                                <div>
                                    <h3
                                        class="text-lg font-serif text-[#4A3B32] flex items-center gap-3"
                                    >
                                        {{ node.name }}
                                        <span
                                            v-if="node.readonly"
                                            class="text-[10px] border border-[#8A8A8A] text-[#8A8A8A] px-1.5 py-0.5 rounded-sm tracking-wider"
                                            >R-O</span
                                        >
                                        <span
                                            v-if="systemConfig.fsProviderId === node.id"
                                            class="text-[10px] bg-[#C68C53] text-white px-1.5 py-0.5 rounded-sm tracking-wider"
                                            >ACTIVE</span
                                        >
                                    </h3>
                                    <p class="text-[#9C948A] text-sm mt-1 font-mono">
                                        {{ node.parentPath }}
                                    </p>
                                </div>
                            </div>

                            <div
                                class="flex gap-4 opacity-0 group-hover:opacity-100 transition-opacity duration-300"
                            >
                                <button
                                    title="Set as Active"
                                    class="text-[#8A8A8A] hover:text-[#C68C53] transition-colors disabled:opacity-50"
                                    :disabled="isSaving"
                                    @click="setActiveFsProvider(node.id)"
                                >
                                    <Check :size="18" />
                                </button>
                                <button
                                    title="Edit"
                                    class="text-[#8A8A8A] hover:text-[#5E4B35] transition-colors"
                                    @click="startEdit(node)"
                                >
                                    <Edit2 :size="18" />
                                </button>
                                <button
                                    title="Delete"
                                    class="text-[#8A8A8A] hover:text-[#B95D5D] transition-colors disabled:opacity-50"
                                    :disabled="isSaving"
                                    @click="handleDelete(node.id)"
                                >
                                    <Trash2 :size="18" />
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </section>
        </div>
    </div>
</template>
