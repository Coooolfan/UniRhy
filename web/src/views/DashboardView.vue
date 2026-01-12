<template>
  <div
    class="min-h-screen bg-[#e6dfcc] flex flex-col font-serif text-[#4a3b2a] relative overflow-hidden"
  >
    <!-- Texture overlay for the whole desk/surface -->
    <div
      class="absolute inset-0 opacity-40 pointer-events-none mix-blend-multiply"
      style="
        background-image: url(&quot;data:image/svg+xml,%3Csvg width='200' height='200' viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.8' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noise)' opacity='0.15'/%3E%3C/svg%3E&quot;);
      "
    ></div>

    <!-- Header / Top Navigation -->
    <header
      class="z-10 px-8 py-6 flex justify-between items-end border-b border-[#cfc4b0] shadow-sm bg-[#f2ebd4]/80 backdrop-blur-sm"
    >
      <div>
        <h1 class="text-3xl font-bold tracking-widest text-[#2c241b]">UniRhy</h1>
        <p class="text-sm italic text-[#6b5d4d] mt-1">控制台 &bull; Dashboard</p>
      </div>
      <div class="flex gap-6 text-sm font-medium">
        <button
          class="hover:text-[#8c3b2d] transition-colors decoration-[#8c3b2d] hover:underline underline-offset-4"
        >
          设置
        </button>
        <button
          @click="handleLogout"
          class="hover:text-[#8c3b2d] transition-colors decoration-[#8c3b2d] hover:underline underline-offset-4"
        >
          登出
        </button>
      </div>
    </header>

    <!-- Main Content Area -->
    <main class="flex-1 p-8 overflow-y-auto relative z-0">
      <!-- Paper Sheet Container -->
      <div
        class="max-w-5xl mx-auto bg-[#fdfaf0] shadow-[0_2px_15px_-3px_rgba(60,50,40,0.15)] border border-[#e8dfc8] p-12 min-h-150 relative"
      >
        <!-- Paper Texture Grain (Inner) -->
        <div
          class="absolute inset-0 opacity-30 pointer-events-none mix-blend-multiply"
          style="
            background-image: url(&quot;data:image/svg+xml,%3Csvg width='200' height='200' viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='1.2' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noise)' opacity='0.08'/%3E%3C/svg%3E&quot;);
          "
        ></div>

        <!-- Ruled Lines Background (Optional for notebook feel) -->
        <div
          class="absolute inset-0 pointer-events-none"
          style="
            background-image: linear-gradient(#e8dfc8 1px, transparent 1px);
            background-size: 100% 2rem;
            margin-top: 4rem;
            opacity: 0.3;
          "
        ></div>

        <div class="relative z-10">
          <h2 class="text-2xl font-bold mb-8 border-b-2 border-[#4a3b2a] pb-2 inline-block">
            概览
          </h2>

          <p class="mb-6 text-lg leading-loose">欢迎回来。这是一个临时占位的开发页面。</p>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-8 mt-10">
            <!-- Card 1 -->
            <div
              class="border-2 border-dashed border-[#cfc4b0] p-6 hover:border-[#4a3b2a] transition-colors group cursor-pointer bg-[#f7f3e8]/50"
            >
              <h3 class="text-xl font-bold mb-2 group-hover:text-[#8c3b2d] transition-colors">
                项目统计
              </h3>
              <p class="text-[#6b5d4d]">查看当前的活跃项目与数据流向。</p>
              <div
                class="mt-4 text-sm font-mono text-[#8c3b2d] opacity-0 group-hover:opacity-100 transition-opacity"
              >
                -> 查看详情
              </div>
            </div>

            <!-- Card 2 -->
            <div
              class="border-2 border-dashed border-[#cfc4b0] p-6 hover:border-[#4a3b2a] transition-colors group cursor-pointer bg-[#f7f3e8]/50"
            >
              <h3 class="text-xl font-bold mb-2 group-hover:text-[#8c3b2d] transition-colors">
                用户管理
              </h3>
              <p class="text-[#6b5d4d]">管理注册用户权限与访问日志。</p>
              <div
                class="mt-4 text-sm font-mono text-[#8c3b2d] opacity-0 group-hover:opacity-100 transition-opacity"
              >
                -> 管理列表
              </div>
            </div>

            <!-- Card 3 -->
            <div
              class="border-2 border-dashed border-[#cfc4b0] p-6 hover:border-[#4a3b2a] transition-colors group cursor-pointer bg-[#f7f3e8]/50"
            >
              <h3 class="text-xl font-bold mb-2 group-hover:text-[#8c3b2d] transition-colors">
                系统日志
              </h3>
              <p class="text-[#6b5d4d]">检查系统运行状况与错误报告。</p>
              <div
                class="mt-4 text-sm font-mono text-[#8c3b2d] opacity-0 group-hover:opacity-100 transition-opacity"
              >
                -> 阅读日志
              </div>
            </div>
            <!-- Card 4 -->
            <div
              class="border-2 border-dashed border-[#cfc4b0] p-6 hover:border-[#4a3b2a] transition-colors group cursor-pointer bg-[#f7f3e8]/50"
            >
              <h3 class="text-xl font-bold mb-2 group-hover:text-[#8c3b2d] transition-colors">
                待办事项
              </h3>
              <ul class="list-disc list-inside text-[#6b5d4d] space-y-1 mt-2">
                <li>完成登录页样式调整</li>
                <li>实现注册接口对接</li>
                <li>完善 Dashboard 布局</li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { api } from '@/ApiInstance'

const router = useRouter()

const handleLogout = async () => {
  try {
    await api.tokenController.logout()
  } catch (e) {
    console.error(e)
  }
  router.push('/')
}
</script>

<style scoped>
/* Optional: Custom scrollbar to match the theme */
::-webkit-scrollbar {
  width: 8px;
}
::-webkit-scrollbar-track {
  background: #e6dfcc;
}
::-webkit-scrollbar-thumb {
  background: #cfc4b0;
  border-radius: 4px;
}
::-webkit-scrollbar-thumb:hover {
  background: #b0a490;
}
</style>
