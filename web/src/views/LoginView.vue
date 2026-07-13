<template>
    <div
        class="auth-shell relative flex min-h-screen items-center justify-center overflow-x-hidden overflow-y-auto px-4 py-6 font-serif text-[#2c2825] md:overflow-y-hidden md:p-4"
    >
        <!-- Main Container -->
        <div
            class="relative z-10 mx-auto h-125 w-full max-w-sm perspective-1000 md:translate-x-64 md:max-w-100"
        >
            <div class="deco-circle circle-1"></div>
            <div class="deco-circle circle-2"></div>
            <!-- Register Card (Back/Front based on state) -->
            <div
                class="paper-card absolute inset-0 flex flex-col rounded-xs p-6 transition-all duration-700 ease-in-out sm:p-10"
                :class="
                    isLoginMode
                        ? 'z-0 transform -rotate-3 -translate-x-2 translate-y-2 opacity-80 scale-[0.98] cursor-pointer md:-translate-x-4 md:hover:-translate-y-1'
                        : 'z-20 transform rotate-0 translate-x-0 translate-y-0 opacity-100 scale-100'
                "
                :role="isLoginMode ? 'button' : undefined"
                :tabindex="isLoginMode ? 0 : -1"
                :aria-label="isLoginMode ? t('login.switchToRegister') : undefined"
                @click="switchToRegister"
                @keydown.enter.space.self.prevent="switchToRegister"
            >
                <div v-if="!isLoginMode" class="relative h-full flex flex-col">
                    <h1
                        class="mb-8 border-b-2 border-[#2c2825] pb-4 text-left text-3xl font-bold tracking-widest sm:mb-10"
                    >
                        {{ t('login.register') }}
                    </h1>

                    <div
                        class="flex-1 flex flex-col items-center justify-center text-center text-[#5a534d] leading-relaxed tracking-wide"
                    >
                        <p>{{ t('login.notPublicSite') }}</p>
                        <p class="mt-2">{{ t('login.contactAdmin') }}</p>
                    </div>

                    <div class="text-center mt-6">
                        <button
                            @click.stop="switchToLogin"
                            class="text-sm text-[#8a817c] hover:text-[#d98c28] underline decoration-dotted underline-offset-4"
                        >
                            {{ t('login.alreadyHaveAccount') }}
                        </button>
                    </div>
                </div>

                <!-- Vertical Text for collapsed state -->
                <div
                    v-else
                    class="flex h-full items-center justify-center opacity-40 transition-opacity md:group-hover:opacity-60"
                >
                    <h2
                        class="writing-vertical-rl select-none text-2xl font-bold tracking-widest sm:text-3xl"
                    >
                        {{ t('login.login') }}
                    </h2>
                </div>
            </div>

            <!-- Login Card (Back/Front based on state) -->
            <div
                class="paper-card absolute inset-0 flex flex-col rounded-xs p-6 transition-all duration-700 ease-in-out sm:p-10"
                :class="
                    !isLoginMode
                        ? 'z-0 transform rotate-3 translate-x-2 translate-y-2 opacity-80 scale-[0.98] cursor-pointer md:translate-x-4 md:hover:-translate-y-1'
                        : 'z-20 transform rotate-0 translate-x-0 translate-y-0 opacity-100 scale-100'
                "
                :role="!isLoginMode ? 'button' : undefined"
                :tabindex="!isLoginMode ? 0 : -1"
                :aria-label="!isLoginMode ? t('login.switchToLogin') : undefined"
                @click="switchToLogin"
                @keydown.enter.space.self.prevent="switchToLogin"
            >
                <div v-if="isLoginMode" class="relative h-full flex flex-col">
                    <h1
                        class="mb-8 border-b-2 border-[#2c2825] pb-4 text-left text-3xl tracking-widest sm:mb-10"
                    >
                        {{ t('login.title') }}
                    </h1>

                    <form
                        v-if="!isEditingBackendUrl"
                        @submit.prevent="handleLogin"
                        class="flex-1 flex flex-col justify-center space-y-6 sm:space-y-8"
                    >
                        <div class="group relative">
                            <input
                                id="login-email"
                                v-model="loginForm.email"
                                type="text"
                                name="email"
                                autocomplete="email"
                                autocapitalize="off"
                                inputmode="email"
                                required
                                class="peer w-full bg-transparent border-b border-[#d6d0c4] focus:border-[#d98c28] outline-none py-2 text-[#2c2825] placeholder-transparent transition-colors"
                                placeholder="Email"
                                data-i18n-ignore
                                @input="clearLoginError"
                            />
                            <label
                                for="login-email"
                                class="absolute left-0 -top-3.5 text-[#8a817c] text-sm transition-all peer-placeholder-shown:text-base peer-placeholder-shown:text-[#b0a8a0] peer-placeholder-shown:top-2 peer-focus:-top-3.5 peer-focus:text-[#2c2825] peer-focus:text-sm cursor-text"
                            >
                                {{ t('login.email') }}
                            </label>
                        </div>

                        <div class="group relative">
                            <input
                                id="login-password"
                                v-model="loginForm.password"
                                type="password"
                                name="password"
                                autocomplete="current-password"
                                required
                                class="peer w-full bg-transparent border-b border-[#d6d0c4] focus:border-[#d98c28] outline-none py-2 text-[#2c2825] placeholder-transparent transition-colors"
                                placeholder="Password"
                                data-i18n-ignore
                                @input="clearLoginError"
                            />
                            <label
                                for="login-password"
                                class="absolute left-0 -top-3.5 text-[#8a817c] text-sm transition-all peer-placeholder-shown:text-base peer-placeholder-shown:text-[#b0a8a0] peer-placeholder-shown:top-2 peer-focus:-top-3.5 peer-focus:text-[#2c2825] peer-focus:text-sm cursor-text"
                            >
                                {{ t('login.password') }}
                            </label>
                        </div>

                        <div class="min-h-5 text-center text-sm text-[#c4563a]">
                            {{ loginError }}
                        </div>

                        <div class="text-center">
                            <button
                                type="submit"
                                class="outline-button px-8 py-2 font-bold tracking-widest transition-all duration-300 transform active:scale-95"
                            >
                                {{ t('login.enter') }}
                            </button>
                        </div>
                    </form>

                    <form
                        v-else
                        class="flex-1 flex flex-col justify-center space-y-8"
                        @submit.prevent="saveBackendUrl"
                    >
                        <div class="group relative">
                            <input
                                id="backend-url"
                                v-model="backendUrl"
                                type="text"
                                placeholder="http://localhost:8654"
                                class="peer w-full bg-transparent border-b border-[#d6d0c4] focus:border-[#d98c28] outline-none py-2 text-[#2c2825] placeholder-transparent transition-colors"
                            />
                            <label
                                for="backend-url"
                                class="absolute left-0 -top-3.5 text-[#8a817c] text-sm transition-all peer-placeholder-shown:text-base peer-placeholder-shown:text-[#b0a8a0] peer-placeholder-shown:top-2 peer-focus:-top-3.5 peer-focus:text-[#2c2825] peer-focus:text-sm cursor-text"
                            >
                                {{ t('login.backendUrl') }}
                            </label>
                        </div>

                        <p v-if="backendUrlError" class="text-center text-sm text-[#c4563a]">
                            {{ backendUrlError }}
                        </p>

                        <div class="text-center">
                            <button
                                type="submit"
                                class="outline-button px-8 py-2 font-bold tracking-widest transition-all duration-300 transform active:scale-95"
                            >
                                {{ t('login.save') }}
                            </button>
                        </div>
                    </form>

                    <div v-if="!isEditingBackendUrl" class="text-center mt-6 px-2">
                        <button
                            @click.stop="switchToRegister"
                            class="text-sm text-[#8a817c] hover:text-[#d98c28] decoration-dotted hover:underline underline-offset-4 transition-colors"
                        >
                            {{ t('login.registerAccount') }}
                        </button>
                    </div>

                    <div
                        v-if="showBackendEndpoint && !isEditingBackendUrl"
                        class="mt-4 border-t border-[#e6dcc8] pt-3 text-left text-xs text-[#8a817c]"
                    >
                        <span>{{ t('login.connectedTo') }}</span>
                        <button
                            type="button"
                            class="break-all text-[#2c2825] underline decoration-dotted underline-offset-4 transition-colors hover:text-[#d98c28]"
                            @click="startEditingBackendUrl"
                        >
                            {{ backendUrl }}
                        </button>
                    </div>
                </div>

                <div v-else class="h-full flex items-center justify-center opacity-40">
                    <h2
                        class="writing-vertical-rl select-none text-2xl font-bold tracking-widest sm:text-3xl"
                    >
                        {{ t('login.register') }}
                    </h2>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { api, saveAuthToken } from '@/ApiInstance'
import { resolveErrorMessage } from '@/i18n/errors'
import { getPlatformRuntime, setPlatformApiBaseUrl } from '@/runtime/platform'
import { getInitializationStatus, resetInitializationStatus } from '@/services/systemInitialization'

const { t } = useI18n()

const router = useRouter()
const isLoginMode = ref(true)
const showBackendEndpoint = getPlatformRuntime().platform !== 'web'
const backendUrl = ref('')
const isEditingBackendUrl = ref(false)
const backendUrlError = ref('')
const loginError = ref('')

const loginForm = reactive({
    email: '',
    password: '',
})

onMounted(async () => {
    if (showBackendEndpoint) {
        const { invoke } = await import('@tauri-apps/api/core')
        backendUrl.value = await invoke<string>('get_backend_url')
    }
})

const saveBackendUrl = async () => {
    backendUrlError.value = ''
    try {
        const { invoke } = await import('@tauri-apps/api/core')
        const normalized = await invoke<string>('set_backend_url', { url: backendUrl.value })
        backendUrl.value = normalized
        setPlatformApiBaseUrl(normalized)
        resetInitializationStatus()
        const status = await getInitializationStatus()
        if (!status.initialized) {
            await router.push('/init')
            return
        }
        isEditingBackendUrl.value = false
    } catch (error) {
        backendUrlError.value = resolveErrorMessage(error, 'errors.fallback.backendUrlSave')
    }
}

const startEditingBackendUrl = () => {
    backendUrlError.value = ''
    isEditingBackendUrl.value = true
}

const switchToRegister = () => {
    if (isLoginMode.value) {
        loginError.value = ''
        isLoginMode.value = false
    }
}

const switchToLogin = () => {
    if (!isLoginMode.value) {
        loginError.value = ''
        isLoginMode.value = true
    }
}

const clearLoginError = () => {
    loginError.value = ''
}

const getLoginErrorMessage = (error: unknown) =>
    resolveErrorMessage(error, 'errors.fallback.loginFailed')

const handleLogin = async () => {
    loginError.value = ''
    try {
        const result = await api.tokenController.login({
            body: {
                email: loginForm.email,
                password: loginForm.password,
            },
        })
        saveAuthToken(result.token)
    } catch (error) {
        loginError.value = getLoginErrorMessage(error)
        return
    }
    window.location.assign(router.resolve('/').href)
}
</script>

<style scoped>
.auth-shell {
    --primary-gold: #d98c28;
    --primary-gold-hover: #b8721b;
    --paper-bg: #f9f7f2;
    --text-dark: #2c2825;
    --text-light: #8a817c;
    --shadow-soft: 0 10px 30px rgba(217, 140, 40, 0.15);
    --shadow-deep: 0 20px 40px rgba(44, 40, 37, 0.15);
    background: radial-gradient(circle at top left, #f5f0e6, #e6dcc8);
}

.deco-circle {
    position: absolute;
    border-radius: 50%;
    filter: blur(80px);
    z-index: -1;
}

.circle-1 {
    width: 400px;
    height: 400px;
    background: rgba(217, 140, 40, 0.1);
    top: -100px;
    left: -100px;
}

.circle-2 {
    width: 400px;
    height: 400px;
    background: rgba(217, 140, 40, 0.15);
    bottom: -100px;
    right: -100px;
}

.paper-card {
    background-color: var(--paper-bg);
    background-image: url("data:image/svg+xml,%3Csvg width='100' height='100' viewBox='0 0 100 100' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.8' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100' height='100' filter='url(%23noise)' opacity='0.03'/%3E%3C/svg%3E");
    box-shadow: var(--shadow-deep);
    border: 1px solid rgba(255, 255, 255, 0.6);
}

.outline-button {
    border: 1px solid var(--primary-gold);
    color: var(--primary-gold);
    background: transparent;
    box-shadow: 0 4px 15px rgba(217, 140, 40, 0.2);
}

.outline-button:hover {
    background: var(--primary-gold-hover);
    border-color: var(--primary-gold-hover);
    color: #ffffff;
    box-shadow: 0 4px 15px rgba(217, 140, 40, 0.35);
}

.writing-vertical-rl {
    writing-mode: vertical-rl;
    text-orientation: upright;
}
</style>
