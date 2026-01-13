<template>
    <div
        class="auth-shell min-h-screen flex items-center justify-center p-4 relative overflow-hidden font-serif text-[#2c2825]"
    >
        <!-- Main Container -->
        <div
            class="relative z-10 w-full max-w-sm sm:max-w-100 h-125 perspective-1000 mx-auto translate-x-64"
        >
            <div class="deco-circle circle-1"></div>
            <div class="deco-circle circle-2"></div>
            <!-- Register Card (Back/Front based on state) -->
            <div
                class="paper-card absolute inset-0 rounded-xs p-10 flex flex-col transition-all duration-700 ease-in-out"
                :class="
                    isLoginMode
                        ? 'z-0 transform -rotate-3 -translate-x-4 translate-y-2 opacity-80 scale-[0.98] cursor-pointer hover:-translate-y-1'
                        : 'z-20 transform rotate-0 translate-x-0 translate-y-0 opacity-100 scale-100'
                "
                :role="isLoginMode ? 'button' : undefined"
                :tabindex="isLoginMode ? 0 : -1"
                :aria-label="isLoginMode ? '切换到注册' : undefined"
                @click="switchToRegister"
                @keydown.enter.space.prevent.self="switchToRegister"
            >
                <div v-if="!isLoginMode" class="relative h-full flex flex-col">
                    <h1
                        class="text-3xl font-bold mb-10 text-left tracking-widest border-b-2 border-[#2c2825] pb-4"
                    >
                        注册
                    </h1>

                    <form
                        @submit.prevent="handleRegister"
                        class="flex-1 flex flex-col justify-center space-y-6"
                    >
                        <div class="group relative">
                            <input
                                id="reg-username"
                                v-model="registerForm.username"
                                type="text"
                                name="username"
                                autocomplete="username"
                                autocapitalize="off"
                                inputmode="text"
                                required
                                class="peer w-full bg-transparent border-b border-[#d6d0c4] focus:border-[#d98c28] outline-none py-2 text-[#2c2825] placeholder-transparent transition-colors"
                                placeholder="Username"
                            />
                            <label
                                for="reg-username"
                                class="absolute left-0 -top-3.5 text-[#8a817c] text-sm transition-all peer-placeholder-shown:text-base peer-placeholder-shown:text-[#b0a8a0] peer-placeholder-shown:top-2 peer-focus:-top-3.5 peer-focus:text-[#2c2825] peer-focus:text-sm cursor-text"
                            >
                                用户名
                            </label>
                        </div>

                        <div class="group relative">
                            <input
                                id="reg-email"
                                v-model="registerForm.email"
                                type="email"
                                name="email"
                                autocomplete="email"
                                autocapitalize="off"
                                inputmode="email"
                                required
                                class="peer w-full bg-transparent border-b border-[#d6d0c4] focus:border-[#d98c28] outline-none py-2 text-[#2c2825] placeholder-transparent transition-colors"
                                placeholder="Email"
                            />
                            <label
                                for="reg-email"
                                class="absolute left-0 -top-3.5 text-[#8a817c] text-sm transition-all peer-placeholder-shown:text-base peer-placeholder-shown:text-[#b0a8a0] peer-placeholder-shown:top-2 peer-focus:-top-3.5 peer-focus:text-[#2c2825] peer-focus:text-sm cursor-text"
                            >
                                电子邮箱
                            </label>
                        </div>

                        <div class="group relative">
                            <input
                                id="reg-password"
                                v-model="registerForm.password"
                                type="password"
                                name="password"
                                autocomplete="new-password"
                                required
                                class="peer w-full bg-transparent border-b border-[#d6d0c4] focus:border-[#d98c28] outline-none py-2 text-[#2c2825] placeholder-transparent transition-colors"
                                placeholder="Password"
                            />
                            <label
                                for="reg-password"
                                class="absolute left-0 -top-3.5 text-[#8a817c] text-sm transition-all peer-placeholder-shown:text-base peer-placeholder-shown:text-[#b0a8a0] peer-placeholder-shown:top-2 peer-focus:-top-3.5 peer-focus:text-[#2c2825] peer-focus:text-sm cursor-text"
                            >
                                密码
                            </label>
                        </div>

                        <div class="pt-8 text-center">
                            <button
                                type="submit"
                                class="outline-button px-8 py-2 font-bold tracking-widest transition-all duration-300"
                            >
                                提交
                            </button>
                        </div>
                    </form>

                    <div class="text-center mt-6">
                        <button
                            @click.stop="switchToLogin"
                            class="text-sm text-[#8a817c] hover:text-[#d98c28] underline decoration-dotted underline-offset-4"
                        >
                            已有账号？返回登录
                        </button>
                    </div>
                </div>

                <!-- Vertical Text for collapsed state -->
                <div
                    v-else
                    class="h-full flex items-center justify-center opacity-40 group-hover:opacity-60 transition-opacity"
                >
                    <h2 class="text-3xl font-bold tracking-widest writing-vertical-rl select-none">
                        注册
                    </h2>
                </div>
            </div>

            <!-- Login Card (Back/Front based on state) -->
            <div
                class="paper-card absolute inset-0 rounded-xs p-10 flex flex-col transition-all duration-700 ease-in-out"
                :class="
                    !isLoginMode
                        ? 'z-0 transform rotate-3 translate-x-4 translate-y-2 opacity-80 scale-[0.98] cursor-pointer hover:-translate-y-1'
                        : 'z-20 transform rotate-0 translate-x-0 translate-y-0 opacity-100 scale-100'
                "
                :role="!isLoginMode ? 'button' : undefined"
                :tabindex="!isLoginMode ? 0 : -1"
                :aria-label="!isLoginMode ? '切换到登录' : undefined"
                @click="switchToLogin"
                @keydown.enter.space.prevent.self="switchToLogin"
            >
                <div v-if="isLoginMode" class="relative h-full flex flex-col">
                    <h1
                        class="text-3xl mb-10 text-left tracking-widest border-b-2 border-[#2c2825] pb-4"
                    >
                        欢迎回来
                    </h1>

                    <form
                        @submit.prevent="handleLogin"
                        class="flex-1 flex flex-col justify-center space-y-8"
                    >
                        <div class="group relative">
                            <input
                                id="login-username"
                                v-model="loginForm.username"
                                type="text"
                                name="email"
                                autocomplete="email"
                                autocapitalize="off"
                                inputmode="email"
                                required
                                class="peer w-full bg-transparent border-b border-[#d6d0c4] focus:border-[#d98c28] outline-none py-2 text-[#2c2825] placeholder-transparent transition-colors"
                                placeholder="Email"
                            />
                            <label
                                for="login-username"
                                class="absolute left-0 -top-3.5 text-[#8a817c] text-sm transition-all peer-placeholder-shown:text-base peer-placeholder-shown:text-[#b0a8a0] peer-placeholder-shown:top-2 peer-focus:-top-3.5 peer-focus:text-[#2c2825] peer-focus:text-sm cursor-text"
                            >
                                邮箱
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
                            />
                            <label
                                for="login-password"
                                class="absolute left-0 -top-3.5 text-[#8a817c] text-sm transition-all peer-placeholder-shown:text-base peer-placeholder-shown:text-[#b0a8a0] peer-placeholder-shown:top-2 peer-focus:-top-3.5 peer-focus:text-[#2c2825] peer-focus:text-sm cursor-text"
                            >
                                密码
                            </label>
                        </div>

                        <div class="pt-8 text-center">
                            <button
                                type="submit"
                                class="outline-button px-8 py-2 font-bold tracking-widest transition-all duration-300 transform active:scale-95"
                            >
                                进 入
                            </button>
                        </div>
                    </form>

                    <div class="text-center mt-6 flex justify-between items-center px-2">
                        <a
                            href="#"
                            class="text-sm text-[#8a817c] hover:text-[#d98c28] decoration-dotted hover:underline underline-offset-4 transition-colors"
                            >忘记密码？</a
                        >
                        <button
                            @click.stop="switchToRegister"
                            class="text-sm text-[#8a817c] hover:text-[#d98c28] decoration-dotted hover:underline underline-offset-4 transition-colors"
                        >
                            注册账号
                        </button>
                    </div>
                </div>

                <div v-else class="h-full flex items-center justify-center opacity-40">
                    <h2 class="text-3xl font-bold tracking-widest writing-vertical-rl select-none">
                        登录
                    </h2>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { api, normalizeApiError } from '@/ApiInstance'

const router = useRouter()
const isLoginMode = ref(true)

const loginForm = reactive({
    username: '',
    password: '',
})

const registerForm = reactive({
    username: '',
    email: '',
    password: '',
})

const switchToRegister = () => {
    if (isLoginMode.value) {
        isLoginMode.value = false
    }
}

const switchToLogin = () => {
    if (!isLoginMode.value) {
        isLoginMode.value = true
    }
}

const handleLogin = async () => {
    try {
        await api.tokenController.login({
            email: loginForm.username,
            password: loginForm.password,
        })
    } catch (error) {
        const normalizedError = normalizeApiError(error, 'tokenController', 'login')
        alert(normalizedError.message || '登录失败')
        return
    }
    router.push('/dashboard')
}

const handleRegister = async () => {
    try {
        await api.accountController.create({
            create: {
                name: registerForm.username,
                email: registerForm.email,
                password: registerForm.password,
            },
        })
    } catch (error) {
        const normalizedError = normalizeApiError(error, 'accountController', 'create')
        alert(normalizedError.message || '注册失败')
        return
    }

    alert('注册成功，请登录')
    switchToLogin()
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
