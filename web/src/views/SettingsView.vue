<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Database, Edit2, FolderOpen, Plus, Save, Trash2, HardDrive } from 'lucide-vue-next'
import { api, normalizeApiError } from '@/ApiInstance'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'

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
const isDeleting = ref<number | null>(null)
const isSaving = ref(false)
const isLoadingSystem = ref(false)
const isLoadingStorage = ref(false)

const systemError = ref('')
const storageError = ref('')

const editForm = reactive({ name: '', parentPath: '', readonly: true })

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
    editForm.readonly = true
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

const startDelete = (id: number) => {
    if (isSaving.value) return
    isDeleting.value = id
}

const cancelDelete = () => {
    isDeleting.value = null
}

const confirmDelete = async () => {
    if (isDeleting.value === null || isSaving.value) {
        return
    }
    isSaving.value = true
    storageError.value = ''
    try {
        await api.fileSystemStorageController.delete({ id: isDeleting.value })
        await Promise.all([fetchStorageNodes(), fetchSystemConfig()])
        isDeleting.value = null
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

onMounted(() => {
    loadData()
})
</script>
<template>
    <div
        class="min-h-screen bg-[#f2efe9] text-[#3D3D3D] font-sans selection:bg-[#C67C4E] selection:text-white pb-32"
    >
        <DashboardTopBar />

        <div class="max-w-5xl mx-auto px-8 pt-6">
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
            <section class="animate-in fade-in duration-500 font-serif">
                <div class="flex items-center justify-between mb-6">
                    <h2 class="text-3xl italic text-[#2A2A2A]">存储节点</h2>
                    <button
                        class="group flex items-center gap-2 px-6 py-2 bg-[#C67C4E] text-[#F7F5F0] hover:bg-[#A6633C] transition-all duration-300 shadow-md hover:shadow-lg disabled:opacity-50 disabled:cursor-not-allowed"
                        :disabled="isCreating || isSaving"
                        @click="startCreate"
                    >
                        <Plus :size="16" />
                        <span>新增节点</span>
                    </button>
                </div>

                <div v-if="storageError" class="text-sm text-[#B95D5D] mb-4">
                    {{ storageError }}
                </div>
                <div v-else-if="isLoadingStorage" class="text-sm text-[#8A8A8A] mb-4">
                    加载中...
                </div>

                <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                    <!-- Create New Card Modal -->
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
                                v-if="isCreating"
                                class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#2B221B]/60"
                                @click.self="cancelEdit"
                            >
                                <div
                                    class="bg-[#fffcf5] p-8 w-full max-w-md shadow-[0_8px_30px_rgba(0,0,0,0.12)] border border-[#EAE6DE] relative transform transition-all"
                                >
                                    <!-- Decorative elements -->
                                    <div
                                        class="absolute top-0 right-0 w-16 h-16 bg-linear-to-bl from-[#EAE6DE]/30 to-transparent pointer-events-none"
                                    ></div>

                                    <div class="mb-8 text-center">
                                        <div
                                            class="inline-flex items-center justify-center w-12 h-12 rounded-full bg-[#FAF9F6] mb-4 border border-[#EAE6DE]"
                                        >
                                            <Database :size="24" class="text-[#C67C4E]" />
                                        </div>
                                        <h3 class="font-serif text-2xl text-[#2B221B]">
                                            新增存储节点
                                        </h3>
                                        <p class="text-xs text-[#8A8A8A] mt-2 font-serif italic">
                                            Add New Storage Node
                                        </p>
                                    </div>

                                    <div class="space-y-6">
                                        <div>
                                            <label
                                                class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                                                >Name</label
                                            >
                                            <input
                                                v-model="editForm.name"
                                                type="text"
                                                placeholder="e.g. Local Backup"
                                                class="w-full bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE]"
                                            />
                                        </div>
                                        <div>
                                            <label
                                                class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                                                >Root Path</label
                                            >
                                            <input
                                                v-model="editForm.parentPath"
                                                type="text"
                                                placeholder="/path/to/dir"
                                                class="w-full bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE]"
                                            />
                                        </div>
                                        <label class="flex items-center gap-3 cursor-pointer group">
                                            <div class="relative flex items-center">
                                                <input
                                                    v-model="editForm.readonly"
                                                    type="checkbox"
                                                    class="peer sr-only"
                                                />
                                                <div
                                                    class="w-9 h-5 bg-[#EAE6DE] peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-[#C67C4E]"
                                                ></div>
                                            </div>
                                            <span
                                                class="text-sm text-[#5A5A5A] group-hover:text-[#2B221B] transition-colors"
                                                >只读模式 (Read-Only)</span
                                            >
                                        </label>

                                        <div class="flex gap-3 mt-8 pt-6 border-t border-[#EAE6DE]">
                                            <button
                                                class="flex-1 px-4 py-2.5 border border-[#D6D1C4] text-[#8A8A8A] hover:bg-[#F7F5F0] hover:text-[#5A5A5A] transition-colors text-sm uppercase tracking-wide"
                                                @click="cancelEdit"
                                            >
                                                取消
                                            </button>
                                            <button
                                                class="flex-1 px-4 py-2.5 bg-[#2B221B] text-[#F7F5F0] hover:bg-[#C67C4E] transition-colors text-sm uppercase tracking-wide shadow-md disabled:opacity-60 disabled:cursor-not-allowed"
                                                :disabled="isSaving"
                                                @click="saveCreate"
                                            >
                                                <span v-if="isSaving">Creating...</span>
                                                <span v-else>创建节点</span>
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </Transition>
                    </Teleport>

                    <!-- Delete Confirmation Modal -->
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
                                v-if="isDeleting !== null"
                                class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#2B221B]/60"
                                @click.self="cancelDelete"
                            >
                                <div
                                    class="bg-[#fffcf5] p-8 w-full max-w-sm shadow-[0_8px_30px_rgba(0,0,0,0.12)] border border-[#EAE6DE] relative transform transition-all text-center"
                                >
                                    <div class="mb-6">
                                        <div
                                            class="inline-flex items-center justify-center w-12 h-12 rounded-full bg-[#FAF9F6] mb-4 border border-[#EAE6DE] text-[#B95D5D]"
                                        >
                                            <Trash2 :size="24" />
                                        </div>
                                        <h3 class="font-serif text-xl text-[#2B221B] mb-2">
                                            确认删除?
                                        </h3>
                                        <p class="text-sm text-[#8A8A8A] font-serif">
                                            此操作无法撤销。
                                        </p>
                                    </div>

                                    <div class="flex gap-3 pt-2">
                                        <button
                                            class="flex-1 px-4 py-2.5 border border-[#D6D1C4] text-[#8A8A8A] hover:bg-[#F7F5F0] hover:text-[#5A5A5A] transition-colors text-sm uppercase tracking-wide"
                                            @click="cancelDelete"
                                        >
                                            取消
                                        </button>
                                        <button
                                            class="flex-1 px-4 py-2.5 bg-[#B95D5D] text-[#F7F5F0] hover:bg-[#9E4C4C] transition-colors text-sm uppercase tracking-wide shadow-md disabled:opacity-60"
                                            :disabled="isSaving"
                                            @click="confirmDelete"
                                        >
                                            <span v-if="isSaving">Deleting...</span>
                                            <span v-else>删除</span>
                                        </button>
                                    </div>
                                </div>
                            </div>
                        </Transition>
                    </Teleport>

                    <!-- Existing Nodes List -->
                    <div
                        v-for="node in storageNodes"
                        :key="node.id"
                        class="group relative bg-[#F7F5F0] p-0 rounded-sm hover:shadow-[0_8px_30px_rgba(0,0,0,0.04)] transition-all duration-300 border border-transparent hover:border-white"
                    >
                        <div
                            v-if="isEditing === node.id"
                            class="p-6 border border-[#C67C4E] bg-[#fffcf5]"
                        >
                            <div class="space-y-4">
                                <div>
                                    <label
                                        class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif"
                                        >Name</label
                                    >
                                    <input
                                        v-model="editForm.name"
                                        type="text"
                                        class="bg-[#F2F0E9] border-b border-[#D6D1C4] p-2 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif w-full"
                                    />
                                </div>
                                <div>
                                    <label
                                        class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif"
                                        >Root Path</label
                                    >
                                    <input
                                        v-model="editForm.parentPath"
                                        type="text"
                                        class="bg-[#F2F0E9] border-b border-[#D6D1C4] p-2 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif w-full"
                                    />
                                </div>
                                <label class="flex items-center gap-2 cursor-pointer mt-2">
                                    <input
                                        v-model="editForm.readonly"
                                        type="checkbox"
                                        class="accent-[#C67C4E] w-4 h-4"
                                    />
                                    <span class="text-sm text-[#5A5A5A]">只读模式 (Read-Only)</span>
                                </label>
                                <div
                                    class="flex gap-2 mt-4 justify-end border-t border-[#EAE6D9] pt-4"
                                >
                                    <button
                                        class="text-xs uppercase tracking-wide px-3 py-1 hover:text-[#C67C4E]"
                                        @click="cancelEdit"
                                    >
                                        取消
                                    </button>
                                    <button
                                        class="flex items-center gap-1 text-xs uppercase tracking-wide px-3 py-1 bg-[#3D3D3D] text-[#F7F5F0] hover:bg-[#C67C4E] transition-colors disabled:opacity-60"
                                        :disabled="isSaving"
                                        @click="saveEdit"
                                    >
                                        <Save :size="12" />
                                        保存
                                    </button>
                                </div>
                            </div>
                        </div>

                        <div v-else class="flex h-full">
                            <div
                                class="w-24 bg-[#EAE6D9] flex items-center justify-center text-[#8A8A8A] border-r border-[#D6D1C4]/30 relative overflow-hidden group-hover:text-[#C67C4E] transition-colors duration-300"
                            >
                                <HardDrive :size="32" stroke-width="1.5" />
                            </div>

                            <div class="flex-1 p-6 flex flex-col">
                                <div class="flex justify-between items-start mb-2">
                                    <div>
                                        <h3
                                            class="text-xl font-medium group-hover:text-[#C67C4E] transition-colors duration-300"
                                        >
                                            {{ node.name }}
                                        </h3>
                                        <div
                                            class="text-[10px] text-[#8A8A8A] uppercase tracking-widest mt-1"
                                        >
                                            ID: {{ node.id }}
                                        </div>
                                    </div>
                                    <div
                                        class="flex flex-col gap-1 items-end group-hover:opacity-0 transition-opacity duration-300"
                                    >
                                        <span
                                            v-if="node.readonly"
                                            class="px-2 py-0.5 border border-[#D6D1C4] text-[10px] text-[#8A8A8A] uppercase"
                                        >
                                            Read Only
                                        </span>
                                        <span
                                            v-if="systemConfig.fsProviderId === node.id"
                                            class="px-2 py-0.5 bg-[#C67C4E] text-[10px] text-white uppercase"
                                        >
                                            Active
                                        </span>
                                    </div>
                                </div>

                                <div
                                    class="flex items-center gap-2 text-[#7A756D] text-sm font-mono bg-[#EAE6D9]/50 p-2 rounded-sm mt-auto"
                                >
                                    <FolderOpen :size="14" class="text-[#C67C4E]" />
                                    <span class="truncate" :title="node.parentPath">
                                        {{ node.parentPath }}
                                    </span>
                                </div>
                            </div>

                            <div
                                class="absolute top-4 right-4 flex gap-2 opacity-0 group-hover:opacity-100 transition-opacity"
                            >
                                <button
                                    title="编辑"
                                    class="p-2 hover:text-[#C67C4E] transition-colors disabled:opacity-50"
                                    :disabled="isSaving"
                                    @click="startEdit(node)"
                                >
                                    <Edit2 :size="14" />
                                </button>
                                <button
                                    title="删除"
                                    class="p-2 hover:text-red-500 transition-colors disabled:opacity-50"
                                    :disabled="isSaving"
                                    @click="startDelete(node.id)"
                                >
                                    <Trash2 :size="14" />
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </section>
        </div>
    </div>
</template>
