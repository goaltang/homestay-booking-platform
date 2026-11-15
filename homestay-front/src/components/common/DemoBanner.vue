<template>
  <div v-if="visible" class="demo-banner" role="note">
    <span class="demo-banner__text">这是公开演示站，免注册即可体验，数据每晚自动重置。</span>
    <span class="demo-banner__actions">
      <router-link
        v-for="account in demoAccounts"
        :key="account.role"
        class="demo-banner__link"
        :to="{ path: '/login', query: { demo: account.role } }"
      >
        {{ account.icon }} {{ account.label }}
      </router-link>
    </span>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useUserStore } from "@/stores/user";
import { demoAccounts, isDemoMode } from "@/utils/demoAccounts";

const userStore = useUserStore();

// 已登录访客不再打扰；未配置演示账号的部署完全不渲染
const visible = computed(() => isDemoMode && !userStore.isAuthenticated);
</script>

<style scoped>
.demo-banner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 6px 16px;
  padding: 8px 16px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--color-neutral-900, #1f1c19);
  background: var(--color-primary-50, #fdf3ee);
  border-bottom: 1px solid var(--color-primary-100, #f8dccd);
}

.demo-banner__actions {
  display: inline-flex;
  gap: 12px;
}

.demo-banner__link {
  font-weight: 600;
  color: var(--color-primary-600, #b84e22);
  text-decoration: none;
  white-space: nowrap;
}

.demo-banner__link:hover,
.demo-banner__link:focus-visible {
  text-decoration: underline;
}
</style>
