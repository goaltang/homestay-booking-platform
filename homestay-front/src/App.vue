<template>
  <el-config-provider :locale="zhCn">
    <div class="app-container">
      <!-- 修改 v-if 条件，排除登录和注册页 -->
      <AppHeader v-if="showHeader" />

      <!-- 主内容区 -->
      <main class="app-main" :class="{ 'host-center-main': isHostCenterRoute }">
        <router-view />
      </main>

      <!-- 修改 v-if 条件，排除登录和注册页 -->
      <AppFooter v-if="showFooter" />

      <!-- AI 智能客服对话框（全局挂载） -->
      <SupportAgentDialog />
    </div>
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { useRoute } from 'vue-router' // 移除 useRouter, ElMessage, Menu 等 Header 相关的导入
import { useUserStore } from './stores/user'
import { useFavoritesStore } from './stores/favorites'
// import { useAuthStore } from './stores/auth' // authStore 现在由 AppHeader 使用
// import { getAvatarUrl, handleImageError } from './utils/image' // 这些现在由 AppHeader 处理
import AppHeader from '@/components/common/AppHeader.vue';
import AppFooter from '@/components/common/AppFooter.vue';
import SupportAgentDialog from '@/components/chat/SupportAgentDialog.vue';

// const router = useRouter() // router 现在由 AppHeader 使用
const route = useRoute()
const userStore = useUserStore()
const favoritesStore = useFavoritesStore()
// const authStore = useAuthStore() // authStore 现在由 AppHeader 使用

// onMounted 获取用户信息的逻辑可以保留在 App.vue，因为它关系到整个应用的状态
onMounted(async () => {
  if (userStore.token && (!userStore.userInfo || !userStore.userInfo.avatar)) {
    console.log('App.vue: 已登录但信息不完整，尝试获取完整用户信息');
    try {
      await userStore.fetchUserInfo();
      console.log('App.vue: 成功获取用户信息:', userStore.userInfo);
    } catch (error) {
      console.error('App.vue: 获取用户信息失败:', error);
    }
  }
});

// 监听用户认证状态变化，更新收藏数据
watch(() => userStore.isAuthenticated, (newAuthState, oldAuthState) => {
  console.log('App.vue: 用户认证状态发生变化:', {
    old: oldAuthState,
    new: newAuthState
  });

  // 当认证状态发生变化时，重新加载收藏数据
  favoritesStore.loadFavorites();
}, { immediate: true });

// isLoggedIn, userAvatar, goToHome, handleCommand, handleAvatarError 这些逻辑都移到了 AppHeader.vue

// isHostCenterRoute 仍然需要，因为它控制 Header 和 Footer 的显示
const isHostCenterRoute = computed(() => {
  return route.path.startsWith('/host');
});

// 控制 Header 和 Footer 的显示
const showHeader = computed(() => {
  const path = route.path;
  return !path.startsWith('/host') && path !== '/login' && path !== '/register';
});

const showFooter = computed(() => {
  const path = route.path;
  return !path.startsWith('/host') && path !== '/login' && path !== '/register' && path !== '/map-search';
});

</script>

<style>
/* 全局样式可以保留 */
:root {
  --primary-color: var(--color-primary-500, #d45f2e);
  --secondary-color: var(--color-secondary-500, #558555);
  --text-color: var(--color-neutral-900, #1f1c19);
  --text-light: var(--color-neutral-500, #8a8276);
  --border-color: var(--color-neutral-200, #e6e2db);
  --background-light: var(--color-background, #faf9f7);
}

* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

body {
  font-family: var(--font-body, 'Helvetica Neue', Helvetica, Arial, sans-serif);
  color: var(--text-color);
  line-height: 1.5;
}

/* 数字输入框样式 — 使用低优先级选择器，允许组件级覆盖 */
.el-input-number {
  width: 100px;
}

.el-input-number .el-input__inner {
  text-align: center;
  padding-left: 8px;
  padding-right: 8px;
}

/* 筛选面板中的数字输入框控制按钮 — 由组件自身控制 */
.filter-card .el-input-number__decrease,
.filter-card .el-input-number__increase {
  display: none;
}

.app-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

/* 主内容区样式 */
.app-main {
  flex: 1;
}

.host-center-main {
  padding-top: 0 !important;
}

/* 移除 App.vue 中与 Header 和 Footer 相关的局部样式 */
/* .app-header { ... } */
/* .header-content { ... } */
/* .logo { ... } */
/* ... */
/* .app-footer { ... } */
/* .footer-content { ... } */
/* ... */
</style>
