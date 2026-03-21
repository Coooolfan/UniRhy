<script setup lang="ts">
defineProps<{
  isChinese: boolean
}>()
</script>

<template>
  <div class="relative inline-block">
    <Transition name="fade-up" mode="out-in">
      <div :key="isChinese ? 'cn' : 'en'">
        <div
          :class="
            isChinese
              ? 'font-brand-sans absolute top-[15%] left-1/2 -z-10 -translate-x-1/2 -translate-y-[60%] whitespace-nowrap pl-[0.3em] text-[9rem] font-bold tracking-[0.3em] text-[rgba(200,200,200,0.3)]'
              : 'font-brand-serif absolute top-[15%] left-1/2 -z-10 -translate-x-1/2 -translate-y-1/2 whitespace-nowrap pl-[1em] text-[9rem] font-bold tracking-[1em] text-[rgba(200,200,200,0.3)]'
          "
        >
          {{ isChinese ? 'UniRhy' : '独一律' }}
        </div>
        <h1
          :class="
            isChinese
              ? 'm-0 font-brand-serif text-[8rem] leading-none font-extrabold tracking-[0.3em] text-white [filter:drop-shadow(0_4px_8px_rgba(0,0,0,0.3))_drop-shadow(0_0_30px_rgba(255,215,0,0.5))]'
              : 'm-0 font-brand-sans text-[8rem] leading-none font-extrabold tracking-[0.05em] text-white [filter:drop-shadow(0_4px_8px_rgba(0,0,0,0.3))_drop-shadow(0_0_30px_rgba(255,215,0,0.5))]'
          "
        >
          {{ isChinese ? '独一律' : 'UniRhy' }}
        </h1>
      </div>
    </Transition>
  </div>
</template>
