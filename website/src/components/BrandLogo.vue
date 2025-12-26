<script setup lang="ts">
defineProps<{
  isChinese: boolean
}>()
</script>

<template>
  <div class="logo-wrapper">
    <Transition name="fade-up" mode="out-in">
      <div :key="isChinese ? 'cn' : 'en'">
        <div :class="isChinese ? 'english-bg' : 'chinese-bg'">
          {{ isChinese ? 'UniRhy' : '独一律' }}
        </div>
        <h1 :class="['brand', isChinese ? 'brand-cn' : '']">
          {{ isChinese ? '独一律' : 'UniRhy' }}
        </h1>
      </div>
    </Transition>
  </div>
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

.logo-wrapper {
  position: relative;
  display: inline-block;
}

.chinese-bg {
  position: absolute;
  top: 15%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 9rem;
  font-weight: bold;
  color: rgba(200, 200, 200, 0.3);
  z-index: -1;
  white-space: nowrap;
  letter-spacing: 1em;
  padding-left: 1em;
  font-family: 'Songti SC', 'SimSun', serif;
}

.english-bg {
  position: absolute;
  top: 15%;
  left: 50%;
  transform: translate(-50%, -60%);
  font-size: 9rem;
  font-weight: bold;
  color: rgba(200, 200, 200, 0.3);
  z-index: -1;
  white-space: nowrap;
  letter-spacing: 0.3em;
  padding-left: 0.3em;
  font-family: 'Barlow Condensed', sans-serif;
}

.brand {
  font-size: 8rem;
  font-weight: 800;
  margin: 0;
  line-height: 1;
  letter-spacing: 0.05em;
  color: #ffffff;
  filter: drop-shadow(0 4px 8px rgba(0, 0, 0, 0.3)) drop-shadow(0 0 30px rgba(255, 215, 0, 0.5));
}

.brand.brand-cn {
  font-family: 'Songti SC', 'SimSun', serif;
  letter-spacing: 0.3em;
}
</style>
