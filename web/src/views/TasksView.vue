<script setup lang="ts">
import { onMounted, ref } from 'vue'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import { useTaskManagement } from '@/composables/useTaskManagement'
import { Loader2, Play, RefreshCw, Activity } from 'lucide-vue-next'

const {
    runningTasks,
    providerOptions,
    isLoadingTasks,
    isLoadingProviders,
    isSubmitting,
    taskError,
    submitError,
    submitSuccess,
    fetchRunningTasks,
    startScanTask,
    init,
} = useTaskManagement()

const selectedProviderId = ref<number | ''>('')

onMounted(() => {
    init()
})

const handleScan = async () => {
    if (selectedProviderId.value === '') return

    const provider = providerOptions.value.find((p) => p.id === selectedProviderId.value)
    if (!provider) return

    await startScanTask(provider.type, provider.id)
}

const formatDate = (timestamp: number) => {
    return new Date(timestamp).toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
    })
}

const getTaskLabel = (type: string) => {
    switch (type) {
        case 'SCAN':
            return '媒体库扫描'
        default:
            return type
    }
}
</script>

<template>
    <div
        class="min-h-screen bg-[#f2efe9] text-[#3D3D3D] font-sans selection:bg-[#C67C4E] selection:text-white pb-32"
    >
        <DashboardTopBar />

        <div class="max-w-6xl mx-auto px-8 pt-6">
            <header class="mb-12 flex justify-between items-end border-b border-[#D6D1C7] pb-6">
                <div>
                    <h1 class="font-serif text-4xl text-[#2B221B] tracking-tight mb-3">任务管理</h1>
                    <p class="text-[#8A8A8A] font-serif italic text-base">
                        System Tasks & Background Jobs
                    </p>
                </div>
                <button
                    @click="fetchRunningTasks"
                    class="p-3 text-[#8A8A8A] hover:text-[#C67C4E] transition-colors rounded-full hover:bg-[#EBE7E0]"
                    :disabled="isLoadingTasks"
                    title="刷新任务列表"
                >
                    <RefreshCw :size="20" :class="{ 'animate-spin': isLoadingTasks }" />
                </button>
            </header>

            <!-- Error Messages -->
            <div
                v-if="taskError || submitError"
                class="mb-8 p-4 bg-red-50 text-red-600 rounded-lg text-sm border border-red-100 flex items-center"
            >
                <span class="mr-2">⚠️</span>
                {{ taskError || submitError }}
            </div>

            <div
                v-if="submitSuccess"
                class="mb-8 p-4 bg-green-50 text-green-600 rounded-lg text-sm border border-green-100 flex items-center"
            >
                <span class="mr-2">✅</span>
                {{ submitSuccess }}
            </div>

            <div class="grid grid-cols-1 lg:grid-cols-12 gap-12">
                <!-- Left Column: Create Tasks -->
                <div class="lg:col-span-4 space-y-8">
                    <section class="bg-white px-8 py-10 shadow-[0_2px_8px_rgba(0,0,0,0.04)]">
                        <h2 class="font-serif text-2xl text-[#2B221B] mb-8 flex items-center">
                            发起新任务
                        </h2>

                        <div class="space-y-6">
                            <div>
                                <label
                                    class="block text-sm font-medium text-[#6B665E] mb-2 uppercase tracking-wider"
                                    >媒体库扫描</label
                                >
                                <p class="text-sm text-[#9C968B] mb-4 font-serif italic">
                                    Update media library index from storage
                                </p>

                                <div
                                    v-if="isLoadingProviders"
                                    class="text-sm text-[#8A8A8A] py-2 flex items-center"
                                >
                                    <Loader2 :size="16" class="animate-spin mr-2" />
                                    加载存储节点...
                                </div>

                                <div
                                    v-else-if="providerOptions.length === 0"
                                    class="text-sm text-[#8A8A8A] py-2 italic"
                                >
                                    暂无可用存储节点
                                </div>

                                <div v-else class="space-y-6">
                                    <div class="relative">
                                        <select
                                            v-model="selectedProviderId"
                                            class="w-full appearance-none bg-transparent border-b border-[#D6D1C7] py-2 pr-8 text-[#2B221B] focus:outline-none focus:border-[#C67C4E] transition-colors rounded-none text-base"
                                        >
                                            <option value="" disabled>选择存储节点</option>
                                            <option
                                                v-for="opt in providerOptions"
                                                :key="opt.id"
                                                :value="opt.id"
                                            >
                                                {{ opt.name }}
                                            </option>
                                        </select>
                                        <div
                                            class="absolute right-0 top-1/2 -translate-y-1/2 pointer-events-none text-[#8A8A8A]"
                                        >
                                            <svg
                                                xmlns="http://www.w3.org/2000/svg"
                                                width="16"
                                                height="16"
                                                viewBox="0 0 24 24"
                                                fill="none"
                                                stroke="currentColor"
                                                stroke-width="2"
                                                stroke-linecap="round"
                                                stroke-linejoin="round"
                                            >
                                                <path d="m6 9 6 6 6-6" />
                                            </svg>
                                        </div>
                                    </div>

                                    <button
                                        @click="handleScan"
                                        :disabled="selectedProviderId === '' || isSubmitting"
                                        class="w-full py-3 px-6 border border-[#C67C4E] text-[#C67C4E] hover:bg-[#C67C4E] hover:text-white transition-all duration-300 rounded text-sm font-medium tracking-wide flex items-center justify-center disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:text-[#C67C4E]"
                                    >
                                        <Loader2
                                            v-if="isSubmitting"
                                            :size="16"
                                            class="animate-spin mr-2"
                                        />
                                        <Play v-else :size="16" class="mr-2" />
                                        开始扫描
                                    </button>
                                </div>
                            </div>
                        </div>
                    </section>
                </div>

                <!-- Right Column: Running Tasks -->
                <div class="lg:col-span-8">
                    <section class="bg-white min-h-[500px] shadow-[0_2px_8px_rgba(0,0,0,0.04)]">
                        <div class="px-8 py-10 border-b border-[#F2EFE9]">
                            <h2 class="font-serif text-2xl text-[#2B221B]">运行中的任务</h2>
                        </div>

                        <div
                            v-if="isLoadingTasks && runningTasks.length === 0"
                            class="flex flex-col items-center justify-center py-20 text-[#9C968B]"
                        >
                            <Loader2 :size="32" class="animate-spin mb-4 text-[#C67C4E]" />
                            <p class="font-serif italic">Loading tasks...</p>
                        </div>

                        <div
                            v-else-if="runningTasks.length === 0"
                            class="flex flex-col items-center justify-center py-20 text-[#9C968B]"
                        >
                            <div class="mb-4 opacity-50">
                                <Activity :size="48" stroke-width="1" />
                            </div>
                            <p class="font-serif italic text-lg">No active tasks running</p>
                        </div>

                        <div v-else>
                            <!-- List Header -->
                            <div
                                class="grid grid-cols-12 gap-4 px-8 py-4 text-xs font-medium text-[#9C968B] uppercase tracking-widest border-b border-[#F2EFE9]"
                            >
                                <div class="col-span-1">#</div>
                                <div class="col-span-5">Task Name</div>
                                <div class="col-span-4 text-right">Started At</div>
                                <div class="col-span-2 text-right">Status</div>
                            </div>

                            <!-- List Items -->
                            <ul class="divide-y divide-[#F2EFE9]">
                                <li
                                    v-for="(task, index) in runningTasks"
                                    :key="index"
                                    class="group hover:bg-[#FBF9F6] transition-colors duration-200"
                                >
                                    <div class="grid grid-cols-12 gap-4 px-8 py-5 items-center">
                                        <div
                                            class="col-span-1 font-serif text-[#C67C4E] font-medium"
                                        >
                                            {{ String(index + 1).padStart(2, '0') }}
                                        </div>
                                        <div class="col-span-5">
                                            <div class="font-medium text-[#2B221B] text-base">
                                                {{ getTaskLabel(task.type) }}
                                            </div>
                                        </div>
                                        <div
                                            class="col-span-4 text-right text-sm text-[#8A8A8A] font-serif"
                                        >
                                            {{ formatDate(task.startedAt) }}
                                        </div>
                                        <div class="col-span-2 flex justify-end">
                                            <div
                                                class="flex items-center text-[#C67C4E] text-xs font-medium bg-[#C67C4E]/10 px-3 py-1 rounded-full"
                                            >
                                                <RefreshCw :size="12" class="animate-spin mr-2" />
                                                RUNNING
                                            </div>
                                        </div>
                                    </div>
                                </li>
                            </ul>
                        </div>
                    </section>
                </div>
            </div>
        </div>
    </div>
</template>
