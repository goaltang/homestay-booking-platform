<template>
  <section class="map-strip">
    <svg class="map-thumb" viewBox="0 0 240 120" aria-hidden="true">
      <rect width="240" height="120" fill="var(--color-neutral-100)" />
      <ellipse cx="200" cy="22" rx="46" ry="26" fill="var(--color-secondary-100)" />
      <rect x="18" y="70" width="58" height="36" rx="4" fill="var(--color-secondary-100)" />
      <g stroke="var(--color-neutral-300)" fill="none" stroke-linecap="round">
        <path d="M0 60 Q120 54 240 62" stroke-width="3" />
        <path d="M120 0 Q114 60 122 120" stroke-width="3" />
        <path d="M0 30 Q80 34 160 28 T240 32" stroke-width="1.5" />
        <path d="M60 0 Q56 60 62 120" stroke-width="1.5" />
        <path d="M184 0 Q180 60 186 120" stroke-width="1.5" />
        <path d="M0 96 Q120 92 240 98" stroke-width="1.5" />
      </g>
      <g v-for="(pin, i) in pins" :key="i">
        <circle :cx="pin.x" :cy="pin.y" r="7" fill="white" />
        <circle :cx="pin.x" :cy="pin.y" r="3.5" :fill="pin.color" />
      </g>
    </svg>

    <div class="map-copy">
      <h2 class="map-title">在地图上找房</h2>
      <p class="map-desc">拖动地图只看这片区域的房源，也可以搜地标，看看附近有哪些能住。</p>
    </div>

    <el-button class="map-btn" type="primary" size="large" @click="goToMap">打开地图</el-button>
  </section>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";

const router = useRouter();

const pins = [
  { x: 92, y: 44, color: "var(--color-primary-500)" },
  { x: 140, y: 72, color: "var(--color-primary-500)" },
  { x: 168, y: 40, color: "var(--color-secondary-600)" },
  { x: 40, y: 50, color: "var(--color-primary-500)" },
  { x: 212, y: 84, color: "var(--color-secondary-600)" },
];

const goToMap = () => {
  router.push("/map-search");
};
</script>

<style scoped>
.map-strip {
  display: flex;
  align-items: center;
  gap: var(--space-6);
  padding: var(--space-4);
  padding-right: var(--space-8);
  background: var(--color-surface-elevated);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
}

.map-thumb {
  flex-shrink: 0;
  width: 240px;
  height: 120px;
  border-radius: var(--radius-md);
}

.map-copy {
  flex: 1;
  min-width: 0;
}

.map-title {
  margin: 0 0 var(--space-2);
  font-size: var(--text-h3);
}

.map-desc {
  margin: 0;
  max-width: 34em;
  font-size: var(--text-body);
  line-height: 1.6;
  color: var(--color-neutral-600);
}

.map-btn {
  flex-shrink: 0;
}

@media (max-width: 768px) {
  .map-strip {
    flex-wrap: wrap;
    gap: var(--space-4);
    padding: var(--space-4);
  }

  .map-thumb {
    width: 100%;
    height: 96px;
  }

  .map-btn {
    width: 100%;
  }
}
</style>
