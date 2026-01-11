<template>
  <div
    class="min-h-screen bg-[#f2e6d8] flex items-center justify-center p-4 relative overflow-hidden font-serif text-[#4a3b2a]"
  >
    <!-- Background Texture (Desk/Surface) -->
    <div
      class="absolute inset-0 pointer-events-none opacity-40 mix-blend-multiply"
      style="
        background-image: url(&quot;data:image/svg+xml,%3Csvg width='200' height='200' viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.8' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noise)' opacity='0.15'/%3E%3C/svg%3E&quot;);
      "
    ></div>

    <!-- Main Container -->
    <div
      class="relative w-full max-w-sm sm:max-w-100 h-125 perspective-1000 mx-auto translate-x-64"
    >
      <!-- Register Card (Back/Front based on state) -->
      <div
        class="absolute inset-0 bg-[#fdfaf0] shadow-xl border border-[#d6cbb5] rounded-xs p-10 flex flex-col transition-all duration-700 ease-in-out"
        :class="
          isLoginMode
            ? 'z-0 transform -rotate-3 -translate-x-4 translate-y-2 opacity-80 scale-[0.98] cursor-pointer hover:-translate-y-1'
            : 'z-20 transform rotate-0 translate-x-0 translate-y-0 opacity-100 scale-100'
        "
        @click="switchToRegister"
      >
        <!-- Paper Texture -->
        <div
          class="absolute inset-0 opacity-10 pointer-events-none bg-repeat mix-blend-multiply"
          style="
            background-image: url(&quot;data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noiseFilter'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.8' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noiseFilter)'/%3E%3C/svg%3E&quot;);
          "
        ></div>

        <div v-if="!isLoginMode" class="relative h-full flex flex-col">
          <h1
            class="text-3xl font-bold mb-10 text-left tracking-widest border-b-2 border-[#4a3b2a] pb-4"
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
                required
                class="peer w-full bg-transparent border-b border-[#8c7b6a] focus:border-[#4a3b2a] outline-none py-2 text-[#2c241b] placeholder-transparent transition-colors"
                placeholder="Username"
              />
              <label
                for="reg-username"
                class="absolute left-0 -top-3.5 text-[#8c7b6a] text-sm transition-all peer-placeholder-shown:text-base peer-placeholder-shown:text-[#a89b8a] peer-placeholder-shown:top-2 peer-focus:-top-3.5 peer-focus:text-[#4a3b2a] peer-focus:text-sm cursor-text"
              >
                用户名
              </label>
            </div>

            <div class="group relative">
              <input
                id="reg-email"
                v-model="registerForm.email"
                type="email"
                required
                class="peer w-full bg-transparent border-b border-[#8c7b6a] focus:border-[#4a3b2a] outline-none py-2 text-[#2c241b] placeholder-transparent transition-colors"
                placeholder="Email"
              />
              <label
                for="reg-email"
                class="absolute left-0 -top-3.5 text-[#8c7b6a] text-sm transition-all peer-placeholder-shown:text-base peer-placeholder-shown:text-[#a89b8a] peer-placeholder-shown:top-2 peer-focus:-top-3.5 peer-focus:text-[#4a3b2a] peer-focus:text-sm cursor-text"
              >
                电子邮箱
              </label>
            </div>

            <div class="group relative">
              <input
                id="reg-password"
                v-model="registerForm.password"
                type="password"
                required
                class="peer w-full bg-transparent border-b border-[#8c7b6a] focus:border-[#4a3b2a] outline-none py-2 text-[#2c241b] placeholder-transparent transition-colors"
                placeholder="Password"
              />
              <label
                for="reg-password"
                class="absolute left-0 -top-3.5 text-[#8c7b6a] text-sm transition-all peer-placeholder-shown:text-base peer-placeholder-shown:text-[#a89b8a] peer-placeholder-shown:top-2 peer-focus:-top-3.5 peer-focus:text-[#4a3b2a] peer-focus:text-sm cursor-text"
              >
                密码
              </label>
            </div>

            <div class="pt-8 text-center">
              <button
                type="submit"
                class="px-8 py-2 border-2 border-[#4a3b2a] text-[#4a3b2a] font-bold tracking-widest hover:bg-[#4a3b2a] hover:text-[#fdfaf0] transition-all duration-300"
              >
                提交
              </button>
            </div>
          </form>

          <div class="text-center mt-6">
            <button
              @click.stop="switchToLogin"
              class="text-sm text-[#8c7b6a] hover:text-[#4a3b2a] underline decoration-dotted underline-offset-4"
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
          <h2 class="text-3xl font-bold tracking-widest writing-vertical-rl select-none">注册</h2>
        </div>
      </div>

      <!-- Login Card (Back/Front based on state) -->
      <div
        class="absolute inset-0 bg-[#fffdf5] shadow-[0_4px_20px_-5px_rgba(0,0,0,0.1)] border border-[#d6cbb5] rounded-xs p-10 flex flex-col transition-all duration-700 ease-in-out"
        :class="
          !isLoginMode
            ? 'z-0 transform rotate-3 translate-x-4 translate-y-2 opacity-80 scale-[0.98] cursor-pointer hover:-translate-y-1'
            : 'z-20 transform rotate-0 translate-x-0 translate-y-0 opacity-100 scale-100'
        "
        @click="switchToLogin"
      >
        <!-- Paper Texture -->
        <div
          class="absolute inset-0 opacity-10 pointer-events-none bg-repeat mix-blend-multiply"
          style="
            background-image: url(&quot;data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noiseFilter'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.8' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noiseFilter)'/%3E%3C/svg%3E&quot;);
          "
        ></div>

        <div v-if="isLoginMode" class="relative h-full flex flex-col">
          <h1
            class="text-3xl font-bold mb-10 text-left tracking-widest border-b-2 border-[#4a3b2a] pb-4"
          >
            登录
          </h1>

          <form @submit.prevent="handleLogin" class="flex-1 flex flex-col justify-center space-y-8">
            <div class="group relative">
              <input
                id="login-username"
                v-model="loginForm.username"
                type="text"
                required
                class="peer w-full bg-transparent border-b border-[#8c7b6a] focus:border-[#4a3b2a] outline-none py-2 text-[#2c241b] placeholder-transparent transition-colors"
                placeholder="Username"
              />
              <label
                for="login-username"
                class="absolute left-0 -top-3.5 text-[#8c7b6a] text-sm transition-all peer-placeholder-shown:text-base peer-placeholder-shown:text-[#a89b8a] peer-placeholder-shown:top-2 peer-focus:-top-3.5 peer-focus:text-[#4a3b2a] peer-focus:text-sm cursor-text"
              >
                用户名 / 邮箱
              </label>
            </div>

            <div class="group relative">
              <input
                id="login-password"
                v-model="loginForm.password"
                type="password"
                required
                class="peer w-full bg-transparent border-b border-[#8c7b6a] focus:border-[#4a3b2a] outline-none py-2 text-[#2c241b] placeholder-transparent transition-colors"
                placeholder="Password"
              />
              <label
                for="login-password"
                class="absolute left-0 -top-3.5 text-[#8c7b6a] text-sm transition-all peer-placeholder-shown:text-base peer-placeholder-shown:text-[#a89b8a] peer-placeholder-shown:top-2 peer-focus:-top-3.5 peer-focus:text-[#4a3b2a] peer-focus:text-sm cursor-text"
              >
                密码
              </label>
            </div>

            <div class="pt-8 text-center">
              <button
                type="submit"
                class="px-8 py-2 border-2 border-[#4a3b2a] text-[#4a3b2a] font-bold tracking-widest hover:bg-[#4a3b2a] hover:text-[#fdfaf0] transition-all duration-300 transform active:scale-95"
              >
                进 入
              </button>
            </div>
          </form>

          <div class="text-center mt-6 flex justify-between items-center px-2">
            <a
              href="#"
              class="text-sm text-[#8c7b6a] hover:text-[#8c3b2d] decoration-dotted hover:underline underline-offset-4 transition-colors"
              >忘记密码？</a
            >
            <button
              @click.stop="switchToRegister"
              class="text-sm text-[#8c7b6a] hover:text-[#4a3b2a] decoration-dotted hover:underline underline-offset-4 transition-colors"
            >
              注册账号
            </button>
          </div>
        </div>

        <div v-else class="h-full flex items-center justify-center opacity-40">
          <h2 class="text-3xl font-bold tracking-widest writing-vertical-rl select-none">登录</h2>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'

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

const handleLogin = () => {
  console.log('Login with:', loginForm)
  // Simulate login success
  router.push('/dashboard')
}

const handleRegister = () => {
  console.log('Register with:', registerForm)
  // Simulate register success -> go to dashboard or back to login
  alert('注册功能开发中 (模拟成功)')
  switchToLogin()
}
</script>

<style scoped>
.writing-vertical-rl {
  writing-mode: vertical-rl;
  text-orientation: upright;
}
</style>
