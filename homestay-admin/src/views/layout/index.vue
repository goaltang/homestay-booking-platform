<template>
  <div class="common-layout">
    <div
      class="route-progress"
      :class="{ 'route-progress--active': routeProgress.visible }"
      :style="{ width: routeProgress.width + '%' }"
    ></div>
    <el-container>
      <el-aside :width="isCollapse ? '64px' : '200px'">
        <div class="sidebar">
          <el-menu
            :default-active="route.path"
            class="el-menu-vertical"
            :collapse="isCollapse"
            router
            background-color="#304156"
            text-color="#bfcbd9"
            active-text-color="#6366f1"
          >
            <template v-for="item in menuData" :key="item.id">
              <el-sub-menu v-if="item.children" :index="item.index">
                <template #title>
                  <el-icon>
                    <component :is="item.icon" />
                  </el-icon>
                  <span>{{ item.title }}</span>
                </template>
                <template v-for="subItem in item.children" :key="subItem.id">
                  <el-sub-menu v-if="subItem.children" :index="subItem.index">
                    <template #title>{{ subItem.title }}</template>
                    <el-menu-item v-for="three in subItem.children" :key="three.id" :index="three.index">
                      {{ three.title }}
                    </el-menu-item>
                  </el-sub-menu>
                  <el-menu-item v-else :index="subItem.index">
                    {{ subItem.title }}
                  </el-menu-item>
                </template>
              </el-sub-menu>
              <el-menu-item v-else :index="item.index">
                <el-icon>
                  <component :is="item.icon" />
                </el-icon>
                <template #title>{{ item.title }}</template>
              </el-menu-item>
            </template>
          </el-menu>
        </div>
      </el-aside>
      <el-container>
        <el-header>
          <div class="header">
            <div class="left">
              <el-button type="text" @click="toggleSidebar">
                <el-icon>
                  <Fold v-if="!isCollapse" />
                  <Expand v-else />
                </el-icon>
              </el-button>
              <el-breadcrumb separator="/">
                <el-breadcrumb-item
                  v-for="(item, index) in breadcrumbItems"
                  :key="index"
                  :to="index < breadcrumbItems.length - 1 ? { path: '/' } : undefined"
                >
                  {{ item }}
                </el-breadcrumb-item>
              </el-breadcrumb>
            </div>
            <div class="right">
              <el-dropdown>
                <span class="user-info">
                  {{ username }}
                  <el-icon>
                    <CaretBottom />
                  </el-icon>
                </span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
        </el-header>
        <el-main>
          <router-view v-slot="{ Component }">
            <!-- 不用 transition 包异步组件：mode="out-in" + 懒加载页面快速连续切换会卡死 router-view（旧组件退出后新组件不挂载，白屏）——切换反馈由顶部进度条承担 -->
            <component :is="Component" />
          </router-view>
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Fold, Expand, CaretBottom } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { menuData } from '@/config/menu'
import { routeProgress } from '@/router'
import type { Menus } from '@/types/menu'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 折叠状态持久化，刷新后保持
const COLLAPSE_KEY = 'admin_sidebar_collapsed'
const isCollapse = ref(localStorage.getItem(COLLAPSE_KEY) === '1')

const username = computed(() => userStore.username || '管理员')

const toggleSidebar = () => {
  isCollapse.value = !isCollapse.value
  localStorage.setItem(COLLAPSE_KEY, isCollapse.value ? '1' : '0')
}

// 多级面包屑：从菜单树反查当前路由的层级链（首页 / 一级 / 二级 / 三级）
const breadcrumbItems = computed(() => {
  const chain: string[] = []
  const find = (items: Menus[], path: string): boolean => {
    for (const item of items) {
      if (item.index === path) {
        chain.push(item.title)
        return true
      }
      if (item.children) {
        chain.push(item.title)
        if (find(item.children, path)) return true
        chain.pop()
      }
    }
    return false
  }
  if (find(menuData, route.path)) return chain
  // 兜底：未匹配到菜单的路由（如编辑页）显示自身标题
  const title = route.meta?.title as string | undefined
  return title ? ['首页', title] : ['首页']
})

const handleLogout = () => {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    userStore.logout()
    router.push('/login')
  })
}
</script>

<style scoped lang="scss">
.common-layout {
  height: 100vh;
}

// 路由切换顶部进度条：默认隐藏，切换时由 router 守卫驱动宽度/透明度
.route-progress {
  position: fixed;
  top: 0;
  left: 0;
  height: 2px;
  background-color: var(--el-color-primary);
  opacity: 0;
  pointer-events: none;
  z-index: 3000;
  transition:
    width 0.25s ease,
    opacity 0.3s ease;

  &--active {
    opacity: 1;
  }
}

.sidebar {
  height: 100vh;
  background-color: #304156;
  transition: width 0.3s ease;

  .el-menu {
    height: 100%;
    border-right: none;
    // 深色侧栏的 hover/激活配色（覆盖 EP 默认浅色变量）
    --el-menu-hover-bg-color: #263445;
    --el-menu-active-color: #6366f1;
  }

  // 激活菜单项：靛蓝半透明底 + 左侧高亮条
  :deep(.el-menu-item.is-active) {
    background-color: rgba(99, 102, 241, 0.18);
  }

  // 折叠后仅显示图标，垂直居中
  :deep(.el-menu--collapse) {
    .el-menu-item,
    .el-sub-menu__title {
      display: flex;
      align-items: center;
      justify-content: center;
    }
  }
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100%;
  padding: 0 20px;
  border-bottom: 1px solid #dcdfe6;

  .left {
    display: flex;
    align-items: center;
    gap: 20px;
  }

  .right {
    .user-info {
      display: flex;
      align-items: center;
      gap: 4px;
      cursor: pointer;
    }
  }
}

.el-main {
  background-color: #f0f2f5;
  padding: 20px;
}
</style>
