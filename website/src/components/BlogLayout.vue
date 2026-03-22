<template>
  <div class="blog-layout">
    <div class="blog-noise"></div>
    <div class="blog-deco-circle blog-circle-1"></div>
    <div class="blog-deco-circle blog-circle-2"></div>
    <div class="blog-layout-inner">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.blog-layout {
  min-height: 100vh;
  background: radial-gradient(circle at top left, #f5f0e6, #e6dcc8);
  color: #2c2825;
  font-family: 'Georgia', 'Times New Roman', Times, serif;
  position: relative;
  overflow-x: hidden;
}

.blog-noise {
  position: absolute;
  inset: 0;
  background-image: url("data:image/svg+xml,%3Csvg width='100' height='100' viewBox='0 0 100 100' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.8' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100' height='100' filter='url(%23noise)' opacity='0.03'/%3E%3C/svg%3E");
  pointer-events: none;
  z-index: 0;
}

.blog-deco-circle {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  z-index: 0;
  opacity: 0.6;
}

.blog-circle-1 {
  width: 400px;
  height: 400px;
  background: rgba(217, 140, 40, 0.1);
  top: -100px;
  left: -100px;
}

.blog-circle-2 {
  width: 300px;
  height: 300px;
  background: rgba(217, 140, 40, 0.15);
  right: -50px;
}

.blog-layout-inner {
  position: relative;
  z-index: 1;
  min-height: 100vh;
}
</style>
