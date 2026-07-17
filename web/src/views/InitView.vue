<template>
    <div
        class="min-h-screen bg-[#F0ECE6] flex flex-col items-center justify-center p-4 md:p-8 text-[#4A4A4A] font-sans relative overflow-hidden"
    >
        <!-- Brand Watermark -->
        <BrandWatermark
            class="absolute left-5 top-5 z-20 font-serif text-xl tracking-wide text-[#2C2C2C] opacity-80 sm:left-8 sm:top-8 sm:text-2xl md:left-12 md:top-12"
        />

        <!-- Main Container -->
        <div class="relative w-full max-w-5xl">
            <!-- Decorative Stacked Background Papers -->
            <div
                class="absolute inset-0 bg-[#E8E4DD] rounded-sm transform translate-x-2 translate-y-3 -rotate-1 shadow-sm opacity-60 z-0"
            ></div>
            <div
                class="absolute inset-0 bg-[#F5F2EE] rounded-sm transform -translate-x-1 translate-y-1 rotate-1 shadow-md z-0"
            ></div>

            <!-- Top Active Layer (The Content) -->
            <div
                class="relative z-10 bg-[#FCFAF7] shadow-2xl rounded-sm border border-white/40 flex flex-col md:flex-row min-h-[540px] overflow-hidden"
            >
                <form @submit.prevent="handleInit" class="contents">
                    <!-- ================= LEFT COLUMN: Account System ================= -->
                    <div
                        class="flex-1 p-10 md:p-14 md:border-r border-[#EFEBE5] flex flex-col relative bg-[#FCFAF7]"
                    >
                        <div
                            class="absolute top-0 left-0 w-full h-[3px] bg-linear-to-r from-[#D4A373] to-transparent opacity-50"
                        ></div>

                        <div class="mb-8 md:mb-12">
                            <span
                                class="text-[10px] tracking-[0.2em] text-[#999999] uppercase font-bold block mb-2"
                                >{{ t('init.part01') }}</span
                            >
                            <h2 class="text-3xl font-serif text-[#2C2C2C] mb-3 tracking-tight">
                                {{ t('init.adminProfile') }}
                            </h2>
                            <p class="text-xs text-[#888888] font-light leading-relaxed max-w-xs">
                                {{ t('init.adminDescription') }}
                            </p>
                        </div>

                        <div class="space-y-8 w-full max-w-sm">
                            <!-- Account Name -->
                            <div class="group">
                                <label
                                    class="text-xs text-[#A0A0A0] uppercase tracking-wider mb-2 block"
                                    >{{ t('init.accountName') }}</label
                                >
                                <div
                                    class="flex items-center pb-2 border-b border-[#E0E0E0] group-focus-within:border-[#D4A373] transition-colors relative"
                                >
                                    <User
                                        :size="18"
                                        class="text-[#BBBBBB] group-focus-within:text-[#D4A373] transition-colors mr-3 shrink-0"
                                    />
                                    <input
                                        type="text"
                                        v-model="form.adminAccountName"
                                        required
                                        class="w-full bg-transparent outline-none text-[#333333] placeholder-[#DCDcdc] font-normal"
                                    />
                                </div>
                            </div>

                            <!-- Email -->
                            <div class="group">
                                <label
                                    class="text-xs text-[#A0A0A0] uppercase tracking-wider mb-2 block"
                                    >{{ t('init.email') }}</label
                                >
                                <div
                                    class="flex items-center pb-2 border-b border-[#E0E0E0] group-focus-within:border-[#D4A373] transition-colors relative"
                                >
                                    <Mail
                                        :size="18"
                                        class="text-[#BBBBBB] group-focus-within:text-[#D4A373] transition-colors mr-3 shrink-0"
                                    />
                                    <input
                                        type="email"
                                        v-model="form.adminAccountEmail"
                                        required
                                        class="w-full bg-transparent outline-none text-[#333333] placeholder-[#DCDcdc] font-normal"
                                    />
                                </div>
                            </div>

                            <!-- Password -->
                            <div class="group">
                                <label
                                    class="text-xs text-[#A0A0A0] uppercase tracking-wider mb-2 block"
                                    >{{ t('init.password') }}</label
                                >
                                <div
                                    class="flex items-center pb-2 border-b border-[#E0E0E0] group-focus-within:border-[#D4A373] transition-colors relative"
                                >
                                    <Key
                                        :size="18"
                                        class="text-[#BBBBBB] group-focus-within:text-[#D4A373] transition-colors mr-3 shrink-0"
                                    />
                                    <input
                                        type="password"
                                        v-model="form.adminPassword"
                                        required
                                        class="w-full bg-transparent outline-none text-[#333333] placeholder-[#DCDcdc] font-normal"
                                    />
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- ================= RIGHT COLUMN: Storage System ================= -->
                    <div
                        class="flex-1 p-10 md:p-14 bg-[#FAF9F6] relative flex flex-col justify-between"
                    >
                        <!-- Texture -->
                        <div
                            class="absolute inset-0 opacity-[0.03] pointer-events-none paper-texture"
                        ></div>

                        <!-- Top Content -->
                        <div class="relative z-10 w-full max-w-sm">
                            <div class="mb-8 md:mb-12">
                                <span
                                    class="text-[10px] tracking-[0.2em] text-[#999999] uppercase font-bold block mb-2"
                                    >{{ t('init.part02') }}</span
                                >
                                <h2 class="text-3xl font-serif text-[#2C2C2C] mb-3 tracking-tight">
                                    {{ t('init.storageConfig') }}
                                </h2>
                                <p
                                    class="text-xs text-[#888888] font-light leading-relaxed max-w-xs"
                                >
                                    {{ t('init.storageDescription') }}
                                </p>
                            </div>

                            <!-- Visual Component -->
                            <div
                                class="mb-8 p-5 border border-dashed border-[#D0D0D0] rounded-[2px] flex items-center gap-4 bg-[#F2F0EC]/50 hover:bg-[#F2F0EC] transition-colors cursor-default"
                            >
                                <div
                                    class="w-12 h-12 rounded-full bg-[#E8E4DD] flex items-center justify-center text-[#888888]"
                                >
                                    <Database :size="22" :stroke-width="1.5" />
                                </div>
                                <div class="flex-1 min-w-0">
                                    <p
                                        class="text-xs font-bold text-[#555] uppercase tracking-wider mb-1"
                                    >
                                        {{ t('init.localFileSystem') }}
                                    </p>
                                    <p class="text-[11px] text-[#999] truncate font-mono">
                                        {{ t('init.statusNotObservable') }}
                                    </p>
                                </div>
                                <div class="w-2 h-2 rounded-full bg-[#D4A373] animate-pulse"></div>
                            </div>

                            <!-- Input -->
                            <div class="group">
                                <label
                                    class="text-xs text-[#A0A0A0] uppercase tracking-wider mb-2 block"
                                    >{{ t('init.storagePath') }}</label
                                >
                                <div
                                    class="flex items-center pb-2 border-b border-[#E0E0E0] group-focus-within:border-[#D4A373] transition-colors relative"
                                >
                                    <HardDrive
                                        :size="18"
                                        class="text-[#BBBBBB] group-focus-within:text-[#D4A373] transition-colors mr-3 shrink-0"
                                    />
                                    <input
                                        type="text"
                                        v-model="form.storageProviderPath"
                                        required
                                        class="w-full bg-transparent outline-none text-[#333333] placeholder-[#DCDcdc] font-mono text-sm"
                                        placeholder="./data"
                                    />
                                </div>
                            </div>
                        </div>

                        <!-- Bottom: Action Button (Fixed Layout) -->
                        <div
                            class="relative z-10 mt-8 flex items-center justify-start gap-6 pt-10 md:mt-0 md:justify-end md:pt-0"
                        >
                            <button
                                type="submit"
                                :disabled="isSubmitting"
                                class="group flex w-full items-center justify-center gap-2 rounded-[2px] bg-[#2C2C2C] px-8 py-3 text-[#F0ECE6] shadow-lg transition-all duration-300 hover:bg-[#D4A373] disabled:cursor-not-allowed disabled:opacity-70 sm:w-auto"
                            >
                                <span class="font-medium tracking-widest text-xs uppercase">{{
                                    isSubmitting ? t('init.initializing') : t('init.initialize')
                                }}</span>
                                <ArrowRight
                                    :size="14"
                                    class="group-hover:translate-x-1 transition-transform"
                                />
                            </button>
                        </div>
                    </div>
                </form>
            </div>
        </div>

        <!-- Footer Info -->
        <div class="mt-8 text-center opacity-40 hover:opacity-100 transition-opacity duration-500">
            <p class="text-[10px]">
                {{ t('init.legalNotice') }}
                <a
                    href="https://github.com/coooolfan/unirhy"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="text-[#D4A373] hover:underline"
                    >GitHub</a
                >
                {{ t('init.legalNoticeSuffix') }}
            </p>
        </div>
    </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { api } from '@/ApiInstance'
import { resolveErrorMessage } from '@/i18n/errors'
import BrandWatermark from '@/components/common/BrandWatermark.vue'
import { setInitializationStatus } from '@/services/systemInitialization'
import { User, Mail, Key, HardDrive, Database, ArrowRight } from 'lucide-vue-next'

const { t } = useI18n()

const router = useRouter()
const isSubmitting = ref(false)

const form = reactive({
    adminAccountName: '',
    adminAccountEmail: '',
    adminPassword: '',
    storageProviderPath: './data',
})

const handleInit = async () => {
    if (isSubmitting.value) return
    isSubmitting.value = true

    try {
        await api.systemConfigController.create({
            body: {
                adminAccountName: form.adminAccountName,
                adminAccountEmail: form.adminAccountEmail,
                adminPassword: form.adminPassword,
                storageProviderPath: form.storageProviderPath,
            },
        })
        setInitializationStatus({ initialized: true })
        router.push('/login')
    } catch (error) {
        alert(resolveErrorMessage(error, 'errors.fallback.systemInit'))
    } finally {
        isSubmitting.value = false
    }
}
</script>

<style scoped>
.paper-texture {
    background-image: url("data:image/svg+xml,%3Csvg width='100' height='100' viewBox='0 0 100 100' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.8' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100' height='100' filter='url(%23noise)' opacity='0.03'/%3E%3C/svg%3E");
}
</style>
