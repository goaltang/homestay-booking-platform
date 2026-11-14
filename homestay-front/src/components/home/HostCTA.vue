<template>
  <section class="host-strip">
    <div class="host-copy">
      <h2 class="host-title">家里有空房？可以把它变成民宿</h2>
      <p class="host-desc">一间空房或一整栋小院都可以。提交房源信息，审核通过后就能接单。</p>
    </div>
    <el-button class="host-btn" size="large" @click="goToHost">成为房东</el-button>
  </section>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";

const router = useRouter();
const goToHost = () => {
  router.push("/host/onboarding");
};
</script>

<style scoped>
.host-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-6);
  padding: var(--space-8) var(--space-10);
  border-radius: var(--radius-lg);
  background: linear-gradient(
    110deg,
    var(--color-neutral-900) 0%,
    var(--color-primary-900) 70%,
    var(--color-primary-800) 100%
  );
  color: white;
}

.host-title {
  /* 全局 h1-h6 设了深色，深色底上要继承容器的白字 */
  color: inherit;
  margin: 0 0 var(--space-2);
  font-size: var(--text-h2);
}

.host-desc {
  margin: 0;
  font-size: var(--text-body);
  line-height: 1.6;
  opacity: 0.82;
}

.host-btn {
  flex-shrink: 0;
  --el-button-bg-color: var(--color-surface-elevated);
  --el-button-border-color: var(--color-surface-elevated);
  --el-button-text-color: var(--color-primary-800);
  --el-button-hover-bg-color: var(--color-primary-50);
  --el-button-hover-border-color: var(--color-primary-50);
  --el-button-hover-text-color: var(--color-primary-800);
  font-weight: 600;
}

@media (max-width: 768px) {
  .host-strip {
    flex-direction: column;
    align-items: stretch;
    padding: var(--space-6);
  }
}
</style>
