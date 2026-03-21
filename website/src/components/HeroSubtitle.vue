<script setup lang="ts">
defineProps<{
  isChinese: boolean
}>()
</script>

<template>
  <Transition name="fade-up" mode="out-in">
    <p
      :key="isChinese ? 'cn' : 'en'"
      class="mt-6 pb-6 text-[1.5rem] font-light uppercase tracking-[0.5em] [text-shadow:0_2px_4px_rgba(0,0,0,0.5)]"
    >
      {{ isChinese ? '享受音乐' : 'Just Listen' }}
    </p>
  </Transition>
</template>
