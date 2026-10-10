<template>
  <div class="skeleton-grid">
    <div v-for="n in count" :key="n" class="skeleton-card">
      <el-skeleton animated>
        <template #template>
          <el-skeleton-item variant="image" class="skeleton-img" />
          <div class="skeleton-info">
            <el-skeleton-item variant="text" class="skeleton-title" />
            <el-skeleton-item variant="text" class="skeleton-location" />
            <div class="skeleton-meta">
              <el-skeleton-item variant="text" class="skeleton-price" />
              <el-skeleton-item variant="text" class="skeleton-rating" />
            </div>
          </div>
        </template>
      </el-skeleton>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  count?: number;
}

withDefaults(defineProps<Props>(), {
  count: 6,
});
</script>

<style scoped>
.skeleton-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.skeleton-card {
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--color-surface-elevated);
}

.skeleton-img {
  width: 100%;
  height: 200px;
  display: block;
}

.skeleton-info {
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.skeleton-title {
  width: 80%;
  height: 18px;
}

.skeleton-location {
  width: 60%;
  height: 14px;
}

.skeleton-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.skeleton-price {
  width: 80px;
  height: 20px;
}

.skeleton-rating {
  width: 60px;
  height: 16px;
}

@media (max-width: 992px) {
  .skeleton-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 576px) {
  .skeleton-grid {
    grid-template-columns: 1fr;
  }
}
</style>
