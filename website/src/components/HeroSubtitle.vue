<script setup lang="ts">
defineProps<{
  isChinese: boolean
}>()
</script>

<template>
  <Transition name="fade-up" mode="out-in">
    <p class="subtitle" :key="isChinese ? 'cn' : 'en'">
      {{ isChinese ? '享受音乐' : 'Just Listen' }}
    </p>
  </Transition>
</template>

<style scoped>
.fade-up-enter-active,
.fade-up-leave-active {
  transition: all 0.4s cubic-bezier(0.25, 0.46, 0.45, 0.94);
}

.fade-up-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.fade-up-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

.subtitle {
  font-size: 1.5rem;
  margin: 1.5rem 0 0;
  font-weight: 300;
  letter-spacing: 0.5em;
  opacity: 1;
  text-transform: uppercase;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.5);
  padding-bottom: 1.5rem;
}
</style>
