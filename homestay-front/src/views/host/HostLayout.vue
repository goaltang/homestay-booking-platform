<template>
    <div class="host-layout">
        <el-container class="layout-container">
            <el-aside width="220px" class="aside">
                <div class="logo">
                    <h2>房东中心</h2>
                </div>
                <el-menu router :default-active="activeMenu" class="el-menu-vertical" background-color="#304156"
                    text-color="#bfcbd9" active-text-color="#409EFF">
                    <el-menu-item index="/host">
                        <el-icon>
                            <Monitor />
                        </el-icon>
                        <span>控制台</span>
                    </el-menu-item>

                    <el-menu-item index="/host/homestay">
                        <el-icon>
                            <House />
                        </el-icon>
                        <span>房源管理</span>
                    </el-menu-item>

                    <el-menu-item index="/host/calendar">
                        <el-icon>
                            <Calendar />
                        </el-icon>
                        <span>日历管理</span>
                    </el-menu-item>

                    <el-menu-item index="/host/orders">
                        <el-icon>
                            <List />
                        </el-icon>
                        <span>订单管理</span>
                    </el-menu-item>

                    <el-menu-item index="/host/earnings">
                        <el-icon>
                            <Money />
                        </el-icon>
                        <span>收益管理</span>
                    </el-menu-item>

                    <el-menu-item index="/host/promotions">
                        <el-icon>
                            <Ticket />
                        </el-icon>
                        <span>营销活动</span>
                    </el-menu-item>

                    <el-menu-item index="/host/promotions/stats">
                        <el-icon>
                            <TrendCharts />
                        </el-icon>
                        <span>营销数据</span>
                    </el-menu-item>

                    <el-menu-item index="/host/reviews">
                        <el-icon>
                            <StarFilled />
                        </el-icon>
                        <span>评价管理</span>
                    </el-menu-item>

                    <el-menu-item index="/host/notifications">
                        <el-icon>
                            <Bell />
                        </el-icon>
                        <span>通知管理</span>
                    </el-menu-item>

                    <el-menu-item index="/host/messages">
                        <el-icon>
                            <ChatDotRound />
                        </el-icon>
                        <span>消息中心</span>
                    </el-menu-item>

                    <el-menu-item index="/host/profile">
                        <el-icon>
                            <User />
                        </el-icon>
                        <span>个人资料</span>
                    </el-menu-item>

                    <el-menu-item index="/" class="home-link">
                        <el-icon>
                            <HomeFilled />
                        </el-icon>
                        <span>返回首页</span>
                    </el-menu-item>
                </el-menu>
            </el-aside>

            <el-container>
                <el-header class="header">
                    <div class="header-left">
                        <el-breadcrumb separator="/">
                            <el-breadcrumb-item :to="{ path: '/host' }">房东中心</el-breadcrumb-item>
                            <el-breadcrumb-item>{{ currentPageTitle }}</el-breadcrumb-item>
                        </el-breadcrumb>
                    </div>
                    <div class="header-right">
                        <NotificationBell class="mr-4" />
                        <MessageBell class="mr-4" />
                        <el-dropdown>
                            <span class="user-dropdown">
                                <el-avatar :size="32" :src="getAvatarUrl(userStore.userInfo?.avatar)"
                                    @error="handleAvatarError">
                                    {{ userName.charAt(0)?.toUpperCase() }}
                                </el-avatar>
                                <span class="username">{{ userName }}</span>
                                <el-icon>
                                    <ArrowDown />
                                </el-icon>
                            </span>
                            <template #dropdown>
                                <el-dropdown-menu>
                                    <el-dropdown-item @click="goToProfile">个人资料</el-dropdown-item>
                                    <el-dropdown-item @click="goToHomePage">返回首页</el-dropdown-item>
                                    <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
                                </el-dropdown-menu>
                            </template>
                        </el-dropdown>
                    </div>
                </el-header>

                <el-main class="main">
                    <router-view />
                </el-main>
            </el-container>
        </el-container>
    </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
    Monitor,
    House,
    Calendar,
    List,
    Money,
    StarFilled,
    User,
    ArrowDown,
    HomeFilled,
    Bell,
    ChatDotRound
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { ElMessageBox, ElMessage, ElContainer, ElAside, ElMenu, ElMenuItem, ElIcon, ElHeader, ElBreadcrumb, ElBreadcrumbItem, ElDropdown, ElDropdownMenu, ElDropdownItem, ElAvatar, ElMain } from 'element-plus'
import NotificationBell from '@/components/NotificationBell.vue'
import MessageBell from '@/components/MessageBell.vue'
import { useChatStore } from '@/stores/chat'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const chatStore = useChatStore()

// 从userInfo中获取用户名
const userName = computed(() => userStore.userInfo?.username || '房东')

// 计算当前激活的菜单项
const activeMenu = computed<string>(() => {
    const { meta, path } = route
    if (typeof meta.activeMenu === 'string') {
        return meta.activeMenu
    }
    return path
})

// 获取当前页面标题
const currentPageTitle = computed<string>(() => {
    return typeof route.meta.title === 'string' ? route.meta.title : '房东中心'
})

// 获取API服务器基础URL
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081';

// 获取头像URL
const getAvatarUrl = (url?: string) => {
    if (!url) {
        // 如果没有头像，返回默认头像
        const seed = userName.value || "default" + Date.now();
        return `https://api.dicebear.com/7.x/avataaars/svg?seed=${seed}`;
    }

    // 如果是完整URL则直接返回
    if (url.startsWith('http')) {
        return url;
    }

    // 处理基于/uploads/路径的图片
    if (url.includes('/uploads/')) {
        // 确保URL没有+1后缀
        const cleanUrl = url.replace(/\+\d+$/, '');
        return `${API_BASE_URL}${cleanUrl}`;
    }

    // 如果是相对路径但不带/api前缀，添加前缀
    if (!url.startsWith('/api/') && url.startsWith('/')) {
        return `${API_BASE_URL}${url}`;
    }

    // 如果不带任何前缀，添加完整前缀
    if (!url.startsWith('/')) {
        return `${API_BASE_URL}/api/${url}`;
    }

    return `${API_BASE_URL}${url}`;
};

// 处理头像加载错误
const handleAvatarError = () => {
    console.error('房东中心头像加载失败:', userStore.userInfo?.avatar);
};

// 前往个人资料页
const goToProfile = () => {
    router.push('/host/profile')
}

// 返回首页
const goToHomePage = () => {
    router.push('/')
}

// 处理退出登录
const handleLogout = () => {
    ElMessageBox.confirm('确定要退出登录吗?', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
    }).then(() => {
        userStore.logout()
        router.push('/login')
        ElMessage.success('已成功退出登录')
    }).catch(() => { })
}

onMounted(() => {
    console.log('房东中心组件已挂载')
    console.log('当前用户信息:', {
        userStore: userStore.userInfo,
        authStore: userStore.user
    })
    console.log('当前用户角色:', {
        userStoreRole: userStore.userInfo?.role,
        authStoreRole: userStore.userRole,
        authStoreIsLandlord: userStore.isLandlord
    })

    // 获取聊天未读数
    chatStore.fetchUnreadCount()

    // 如果用户不是房东，重定向到首页
    const isUserAuthenticated = userStore.isAuthenticated
    const isLandlord = userStore.isLandlord

    if (!isUserAuthenticated) {
        console.error('用户未登录，重定向至登录页')
        ElMessage.error('请先登录')
        router.push('/login')
    } else if (!isLandlord) {
        console.error('用户角色不是房东:', {
            userStoreRole: userStore.userRole
        })
        ElMessage.error('您没有访问房东中心的权限')
        router.push('/')
    } else {
        console.log('用户有权限访问房东中心')
    }
})
</script>

<style scoped>
.host-layout {
    min-height: 100vh;
}

.layout-container {
    height: 100vh;
}

.aside {
    background-color: #304156;
    color: #bfcbd9;
    overflow-x: hidden;
}

.logo {
    height: 60px;
    line-height: 60px;
    text-align: center;
    color: #fff;
    font-size: 18px;
    border-bottom: 1px solid #1f2d3d;
}

.header {
    background-color: #fff;
    color: #333;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 20px;
    box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
    position: sticky;
    top: 0;
    z-index: 1;
}

.header-right {
    display: flex;
    align-items: center;
}

.user-dropdown {
    cursor: pointer;
    display: flex;
    align-items: center;
    gap: 8px;
}

.username {
    font-size: 14px;
    color: #333;
    margin: 0 4px;
}

.main {
    background-color: #f0f2f5;
    padding: 20px;
}

.home-link {
    margin-top: auto;
    border-top: 1px solid #1f2d3d;
    margin-top: 20px;
}

.el-menu-vertical {
    border-right: none;
    height: calc(100vh - 60px);
    display: flex;
    flex-direction: column;
}

.el-menu-item {
    display: flex;
    align-items: center;
}

.el-icon {
    margin-right: 5px;
}

.mr-4 {
    margin-right: 1rem;
    /* 16px */
}
</style>
