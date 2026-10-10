<template>
  <el-container class="user-layout-container h-screen">
    <!-- 移动端遮罩 -->
    <div v-if="sidebarOpen" class="sidebar-overlay" @click="sidebarOpen = false" />

    <!-- 侧边栏 -->
    <el-aside width="260px" class="user-sidebar bg-white shadow-sm" :class="{ open: sidebarOpen }">
      <!-- 1. 顶部：个人概览 -->
      <div class="sidebar-profile">
        <el-avatar :size="48" :src="userStore.userInfo?.avatar || ''" class="profile-avatar">
          {{ userStore.userInfo?.username?.charAt(0)?.toUpperCase() || "U" }}
        </el-avatar>
        <div class="profile-info">
          <div class="profile-greeting">欢迎回来,</div>
          <div class="profile-name">{{ userStore.userInfo?.username || "用户" }}</div>
        </div>
        <!-- 移动端关闭按钮 -->
        <el-button text circle class="sidebar-close" @click="sidebarOpen = false">
          <el-icon :size="18"><Close /></el-icon>
        </el-button>
      </div>

      <!-- 2. 中间：功能菜单栏 -->
      <div class="sidebar-menu-wrapper">
        <el-menu
          :default-active="activeMenu"
          class="el-menu-vertical-user"
          router
          @select="handleMenuSelect"
        >
          <el-menu-item index="/user/profile">
            <el-icon><User /></el-icon>
            <span>个人资料</span>
          </el-menu-item>
          <el-menu-item index="/user/bookings">
            <el-icon><List /></el-icon>
            <span>我的订单</span>
          </el-menu-item>
          <el-menu-item index="/user/favorites">
            <el-icon><Star /></el-icon>
            <span>我的收藏</span>
          </el-menu-item>
          <el-menu-item index="/user/reviews">
            <el-icon><Comment /></el-icon>
            <span>我的评价</span>
          </el-menu-item>
          <el-menu-item index="/user/coupons">
            <el-icon><Ticket /></el-icon>
            <span>我的优惠券</span>
          </el-menu-item>
          <el-menu-item index="/user/companions">
            <el-icon><Postcard /></el-icon>
            <span>常用入住人</span>
          </el-menu-item>
          <el-menu-item index="/user/notifications">
            <el-icon><Bell /></el-icon>
            <span>通知中心</span>
          </el-menu-item>
          <el-menu-item index="/user/invite">
            <el-icon><Present /></el-icon>
            <span>我的邀请</span>
          </el-menu-item>
        </el-menu>
      </div>

      <!-- 3. 底部：操作区 (顶到底部) -->
      <div class="sidebar-bottom-actions">
        <!-- 切换到房东端 -->
        <div v-if="userStore.isLandlord" class="pseudo-menu-item" @click="switchToHost">
          <el-icon><Shop /></el-icon>
          <span>切换到房东端</span>
        </div>
        <!-- 退出登录 -->
        <div class="pseudo-menu-item logout-item" @click="handleLogout">
          <el-icon><SwitchButton /></el-icon>
          <span>退出登录</span>
        </div>
      </div>
    </el-aside>

    <!-- 主内容区 -->
    <el-main class="user-main-content bg-white p-4 md:p-6 overflow-y-auto">
      <!-- 移动端顶部导航栏 -->
      <div class="mobile-header">
        <el-button text circle @click="sidebarOpen = true">
          <el-icon :size="20"><Fold /></el-icon>
        </el-button>
        <span class="mobile-title">{{ pageTitle }}</span>
      </div>

      <!-- 内容容器 -->
      <div class="main-content-wrapper w-full">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { useRoute, RouterView } from "vue-router";
import {
  ElContainer,
  ElAside,
  ElMain,
  ElMenu,
  ElMenuItem,
  ElIcon,
  ElMessageBox,
  ElButton,
  ElAvatar,
} from "element-plus";
import {
  User,
  Bell,
  List,
  Star,
  Comment,
  Shop,
  SwitchButton,
  Present,
  Ticket,
  Postcard,
  Fold,
  Close,
} from "@element-plus/icons-vue";
import { useUserStore } from "@/stores/user";
import { useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const sidebarOpen = ref(false);

const activeMenu = computed(() => route.path);
const pageTitle = computed(() => (route.meta?.title as string) || "用户中心");

const handleMenuSelect = () => {
  // 移动端点击菜单后自动关闭侧边栏
  if (window.innerWidth <= 768) {
    sidebarOpen.value = false;
  }
};

const switchToHost = () => {
  sidebarOpen.value = false;
  router.push("/host");
};

const handleLogout = () => {
  sidebarOpen.value = false;
  ElMessageBox.confirm("确定要退出登录吗？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  })
    .then(() => {
      userStore.logout();
    })
    .catch(() => {
      // 用户取消
    });
};
</script>

<style scoped>
.user-layout-container {
  max-width: 1200px;
  margin: 0 auto;
}

.user-sidebar {
  position: relative;
  border-right: 1px solid var(--el-border-color-lighter);
  display: flex;
  flex-direction: column;
  z-index: 101;
}

/* 移除 el-menu 的默认右边框 */
.el-menu-vertical-user {
  border-right: none;
}

/* 菜单项间距和圆角 */
.el-menu-vertical-user .el-menu-item {
  color: #909399;
  height: 52px;
  line-height: 52px;
  margin-bottom: 10px;
  margin-left: 12px;
  margin-right: 12px;
  border-radius: 6px;
}
.el-menu-vertical-user .el-menu-item:last-child {
  margin-bottom: 0px;
}

/* 激活菜单项的样式 */
.el-menu-item.is-active {
  background-color: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}

.el-menu-item.is-active::before {
  content: "";
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 60%;
  background-color: var(--el-color-primary);
  border-radius: 0 4px 4px 0;
}

.el-menu-item:hover {
  background-color: var(--el-fill-color-light);
}

/* 内容区域过渡动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* 顶部个人概览区 */
.sidebar-profile {
  display: flex;
  align-items: center;
  padding: 32px 20px 24px;
  gap: 16px;
  position: relative;
}

.profile-avatar {
  background-color: var(--el-color-primary-light-3);
  font-size: 20px;
  font-weight: 600;
}

.profile-info {
  display: flex;
  flex-direction: column;
}

.profile-greeting {
  font-size: 13px;
  color: #909399;
  margin-bottom: 4px;
}

.profile-name {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

/* 移动端关闭按钮默认隐藏 */
.sidebar-close {
  display: none;
  margin-left: auto;
}

/* 底部操作区菜单化 */
.sidebar-bottom-actions {
  margin-top: auto;
  padding-bottom: 24px;
}

.pseudo-menu-item {
  display: flex;
  align-items: center;
  height: 50px;
  line-height: 50px;
  padding: 0 20px;
  color: #606266;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s;
  margin-left: 12px;
  margin-right: 12px;
  border-radius: 6px;
  margin-bottom: 8px;
}

.pseudo-menu-item .el-icon {
  width: 24px;
  text-align: center;
  font-size: 18px;
  margin-right: 8px;
  vertical-align: middle;
}

.pseudo-menu-item:hover {
  background-color: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
}

.pseudo-menu-item.logout-item:hover {
  background-color: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

/* 移动端顶部导航栏默认隐藏 */
.mobile-header {
  display: none;
  align-items: center;
  gap: 12px;
  padding: 8px 0 16px;
  margin-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.mobile-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

/* 移动端遮罩默认隐藏 */
.sidebar-overlay {
  display: none;
}

/* ================= 移动端适配 ================= */
@media (max-width: 768px) {
  .user-layout-container {
    max-width: 100%;
  }

  .user-sidebar {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    transform: translateX(-100%);
    transition: transform 0.3s ease;
    background: #fff;
  }

  .user-sidebar.open {
    transform: translateX(0);
  }

  .sidebar-close {
    display: inline-flex;
  }

  .sidebar-overlay {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.45);
    z-index: 100;
  }

  .mobile-header {
    display: flex;
  }

  .user-main-content {
    padding: 12px 16px !important;
  }
}
</style>
