<template>
    <header :class="['app-header', { 'is-scrolled': isScrolled, 'is-home': isHome, 'search-expanded': isSearchExpanded }]">
        <div class="header-content">
            <div class="logo" @click="goToHome">
                <img src="/logo.svg" alt="Homestay Logo" class="logo-image" />
                <span class="logo-text">民宿预订</span>
            </div>

            <!-- 移动端搜索按钮（桌面隐藏） -->
            <button class="mobile-search-btn" @click="isSearchExpanded = true" aria-label="搜索">
                <el-icon :size="20"><Search /></el-icon>
            </button>

            <!-- 中间迷你搜索滑块 (仅页面滚动后或非首页显示) -->
            <transition name="el-fade-in">
                <div class="mini-search-bar" v-show="showMiniSearch && !isSearchExpanded" @click="handleMiniSearchClick">
                    <span class="search-text">任何地点</span>
                    <span class="divider"></span>
                    <span class="search-text">任意一周</span>
                    <span class="divider"></span>
                    <span class="search-text light">添加房客</span>
                    <div class="search-icon-wrapper">
                        <el-icon><Search /></el-icon>
                    </div>
                </div>
            </transition>

            <!-- 顶部文本链接 (展开大搜索框时显示在中间) -->
            <transition name="el-fade-in">
                <div class="search-tabs" v-show="isSearchExpanded">
                    <span class="tab active">住宿</span>
                    <span class="tab tab-disabled" title="即将上线">体验</span>
                </div>
            </transition>

            <div class="user-menu">
                <div class="map-search-link" @click="goToMapSearch">
                    <el-icon><Location /></el-icon>
                    地图找房
                </div>
                <div class="host-link" v-if="!isLoggedIn || !userStore.isLandlord" @click="goToBecomeHost">
                    成为房东
                </div>
                <NotificationBell v-if="isLoggedIn" />
                <el-dropdown trigger="click" @command="handleCommand">
                    <div class="user-avatar">
                        <el-icon>
                            <Menu />
                        </el-icon>
                        <el-avatar :size="32" :src="userAvatar" @error="handleAvatarError" />
                    </div>
                    <template #dropdown>
                        <el-dropdown-menu>
                            <el-dropdown-item v-if="isLoggedIn" command="profile">个人中心</el-dropdown-item>
                            <el-dropdown-item v-if="isLoggedIn" command="host">房东中心</el-dropdown-item>
                            <el-dropdown-item command="favorites">我的收藏</el-dropdown-item>
                            <el-dropdown-item command="orders">我的订单</el-dropdown-item>
                            <el-dropdown-item divided v-if="isLoggedIn" command="logout">退出登录</el-dropdown-item>
                            <el-dropdown-item v-else command="login">登录</el-dropdown-item>
                        </el-dropdown-menu>
                    </template>
                </el-dropdown>
            </div>
        </div>

        <!-- 展开的全局搜索面板 -->
        <transition name="el-fade-in">
            <div class="expanded-search-container" v-show="isSearchExpanded">
                <SearchBar :initial-params="searchStore.searchParams" @search="handleGlobalSearch" @reset="isSearchExpanded = false" />
            </div>
        </transition>
    </header>

    <!-- 搜索面板的深色遮罩背景 -->
    <transition name="el-fade-in">
        <div class="search-backdrop" v-if="isSearchExpanded" @click="isSearchExpanded = false"></div>
    </transition>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { ElMessage, ElDropdown, ElDropdownMenu, ElDropdownItem, ElAvatar, ElIcon } from 'element-plus';
import { Menu, Search, Location } from '@element-plus/icons-vue';
import { useUserStore } from '@/stores/user';
import { useSearchStore } from '@/stores/search';
import { getAvatarUrl, handleImageError } from '@/utils/image';
import NotificationBell from '@/components/NotificationBell.vue';
import SearchBar from '@/components/SearchBar.vue';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const searchStore = useSearchStore();

const isLoggedIn = computed(() => userStore.isAuthenticated);
const isHome = computed(() => route.path === '/');
const isScrolled = ref(false);
const isSearchExpanded = ref(false);
const showMiniSearch = computed(() => !isHome.value || isScrolled.value);

const handleScroll = () => {
    isScrolled.value = window.scrollY > 200; // 调高阈值，200px 刚好是大搜索框滚出去的大致高度
};

onMounted(() => {
    window.addEventListener('scroll', handleScroll);
    handleScroll();
});

onUnmounted(() => {
    window.removeEventListener('scroll', handleScroll);
});

const handleMiniSearchClick = () => {
    isSearchExpanded.value = true;
};

const handleGlobalSearch = (params: any) => {
    isSearchExpanded.value = false;

    // 同步到全局搜索 store，保证各搜索栏状态一致
    searchStore.setSearchState({
        keyword: params.keyword || '',
        selectedRegion: params.selectedRegion || [],
        checkIn: params.checkIn || null,
        checkOut: params.checkOut || null,
        guestCount: params.guestCount || 1
    });

    // Convert to query params format expected by the backend and homestay list view
    const newQuery: any = {
        keyword: params.keyword || undefined,
        region: params.selectedRegion?.length ? params.selectedRegion.join(',') : undefined,
        checkIn: params.checkIn || undefined,
        checkOut: params.checkOut || undefined,
        guestCount: params.guestCount && params.guestCount > 1 ? params.guestCount : undefined,
        search: 'true'
    };

    router.push({
        path: '/homestays',
        query: newQuery
    });
};

const goToMapSearch = () => {
    router.push('/map-search');
};

const goToBecomeHost = () => {
    if (!isLoggedIn.value) {
        ElMessage.info('请先登录后再访问房东中心');
        router.push('/login?message=请先登录后再访问房东中心');
        return;
    }
    
    // Check if user is already a host
    const isLandlord = userStore.isLandlord;
                      
    if (isLandlord) {
        router.push('/host');
    } else {
        router.push('/host/onboarding');
    }
};
const userAvatar = computed(() => {
    const avatarUrl = userStore.userInfo?.avatar;
    if (!avatarUrl) {
        const seed = userStore.userInfo?.username || "default" + Date.now();
        return `https://api.dicebear.com/7.x/avataaars/svg?seed=${seed}`;
    }
    return getAvatarUrl(avatarUrl);
});

const goToHome = () => {
    router.push('/');
};

const handleCommand = (command: string) => {
    console.log('Header 命令执行:', command);
    switch (command) {
        case 'profile':
            if (!isLoggedIn.value) {
                ElMessage.info('请先登录后再访问个人中心');
                router.push('/login?message=请先登录后再访问个人中心');
                return;
            }
            router.push('/user/profile');
            break;
        case 'host':
            if (!isLoggedIn.value) {
                ElMessage.info('请先登录后再访问房东中心');
                router.push('/login?message=请先登录后再访问房东中心');
                return;
            }
            if (userStore.isLandlord) {
                router.push('/host').catch(err => {
                    console.error('跳转到房东中心失败:', err);
                    ElMessage.error('跳转到房东中心失败');
                });
            } else {
                ElMessage.warning('您不是房东，无法访问房东中心');
            }
            break;
        case 'favorites':
            if (!isLoggedIn.value) {
                ElMessage.info('请先登录后再查看收藏');
                router.push('/login?message=请先登录后再查看收藏');
                return;
            }
            router.push('/user/favorites');
            break;
        case 'orders':
            if (!isLoggedIn.value) {
                ElMessage.info('请先登录后再查看订单');
                router.push('/login?message=请先登录后再查看订单');
                return;
            }
            router.push('/user/bookings');
            break;
        case 'login':
            router.push('/login');
            break;
        case 'logout':
            userStore.logout();
            ElMessage.success('已退出登录');
            router.push('/');
            break;
    }
};

const handleAvatarError = (e: Event) => {
    handleImageError(e, 'avatar');
};
</script>

<style scoped>
.app-header {
    position: sticky;
    top: 0;
    z-index: 2000;
    background-color: white;
    padding: 16px 0;
    border-bottom: 1px solid transparent;
    transition: all 0.3s ease;
}

.app-header.is-scrolled,
.app-header:not(.is-home) {
    border-bottom: 1px solid var(--border-color, #ebeef5);
    box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}

.app-header.search-expanded {
    border-bottom: 1px solid transparent !important;
    box-shadow: none !important;
    background-color: white;
}

.search-tabs {
    display: flex;
    gap: 24px;
    font-size: 16px;
    font-weight: 500;
    color: var(--text-regular, #717171);
}

.search-tabs .tab {
    cursor: pointer;
    padding-bottom: 8px;
    transition: color 0.2s;
}

.search-tabs .tab:hover {
    color: var(--text-primary, #222);
}

.search-tabs .tab.active {
    color: var(--text-primary, #222);
    border-bottom: 2px solid var(--text-primary, #222);
}

.search-tabs .tab.tab-disabled {
    opacity: 0.4;
    cursor: not-allowed;
}

.expanded-search-container {
    position: absolute;
    top: 80px;
    left: 0;
    width: 100%;
    background-color: white;
    padding-bottom: 24px;
    border-bottom: 1px solid var(--border-color, #ebeef5);
    box-shadow: 0 8px 20px rgba(0,0,0,0.06);
    z-index: 2000;
}

.expanded-search-container :deep(.search-container) {
    position: relative !important;
    box-shadow: none !important;
    margin-bottom: 0 !important;
    padding-top: 0 !important;
}

.expanded-search-container :deep(.search-wrapper) {
    box-shadow: 0 3px 12px rgba(0,0,0,0.15) !important;
    border: 1px solid #ddd;
    background-color: #f7f7f7 !important;
}

.search-backdrop {
    position: fixed;
    top: 0;
    left: 0;
    width: 100%;
    height: 100vh;
    background-color: rgba(0,0,0,0.25);
    z-index: 100;
}

.header-content {
    max-width: 1200px;
    margin: 0 auto;
    padding: 0 24px;
    display: grid;
    grid-template-columns: 1fr auto 1fr;
    align-items: center;
}

.logo {
    display: flex;
    align-items: center;
    cursor: pointer;
    justify-self: start;
}

.mini-search-bar,
.search-tabs {
    justify-self: center;
    grid-column: 2;
}

.logo-image {
    height: 32px;
    margin-right: 8px;
}

.logo-text {
    font-size: 20px;
    font-weight: bold;
    color: var(--primary-color);
}

.user-menu {
    display: flex;
    align-items: center;
    justify-self: end;
    grid-column: 3;
}

.user-avatar {
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
    padding: 8px;
    border: 1px solid var(--border-color);
    border-radius: 24px;
    transition: box-shadow 0.2s ease;
}

.user-avatar:hover {
    box-shadow: 0 2px 4px rgba(0,0,0,0.18);
}

/* 移动端搜索按钮：默认隐藏，仅小屏显示 */
.mobile-search-btn {
    display: none;
    grid-column: 2;
    justify-self: center;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    border-radius: 50%;
    border: 1px solid var(--border-color, #dcdfe6);
    background-color: #fff;
    color: var(--text-primary, #303133);
    cursor: pointer;
    transition: box-shadow 0.2s ease;
}

.mobile-search-btn:hover {
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.12);
}

.host-link {
    font-size: 14px;
    font-weight: 500;
    color: var(--text-primary);
    cursor: pointer;
    padding: 8px 16px;
    border-radius: 20px;
    margin-right: 8px;
    transition: background-color 0.2s;
}

.map-search-link {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 14px;
    font-weight: 500;
    color: var(--text-primary);
    cursor: pointer;
    padding: 8px 16px;
    border-radius: 20px;
    margin-right: 8px;
    transition: background-color 0.2s;
}

.map-search-link:hover {
    background-color: var(--border-color, #f0f2f5);
}

.host-link:hover {
    background-color: var(--border-color, #f0f2f5);
}

.mini-search-bar {
    display: flex;
    align-items: center;
    border: 1px solid var(--border-color, #dcdfe6);
    border-radius: 24px;
    padding: 0 8px 0 24px;
    height: 48px;
    box-shadow: 0 1px 2px rgba(0,0,0,0.08), 0 4px 12px rgba(0,0,0,0.05);
    cursor: pointer;
    transition: all 0.2s ease;
    background: white;
}

.mini-search-bar:hover {
    box-shadow: 0 2px 4px rgba(0,0,0,0.18);
}

.mini-search-bar .search-text {
    font-size: 14px;
    font-weight: 500;
    color: var(--text-primary, #303133);
    padding: 0 8px;
}

.mini-search-bar .search-text.light {
    color: var(--text-regular, #717171);
    font-weight: 400;
}

.mini-search-bar .divider {
    width: 1px;
    height: 24px;
    background-color: var(--border-color, #e4e7ed);
}

.mini-search-bar .search-icon-wrapper {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    background-color: var(--primary-color, #ff385c);
    color: white;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-left: 8px;
}

.fade-slide-enter-active,
.fade-slide-leave-active {
    transition: all 0.3s cubic-bezier(0.2, 0, 0, 1);
}

.fade-slide-enter-from,
.fade-slide-leave-to {
    opacity: 0;
    transform: translateY(-10px) scale(0.95);
}

/* ================= 移动端适配 ================= */
@media (max-width: 768px) {
    .app-header {
        padding: 12px 0;
    }

    .header-content {
        padding: 0 16px;
        grid-template-columns: auto 1fr auto;
    }

    /* 移动端隐藏中间迷你搜索、搜索 tab 和文字链接，改用搜索按钮 */
    .mini-search-bar,
    .search-tabs,
    .map-search-link,
    .host-link {
        display: none;
    }

    .mobile-search-btn {
        display: inline-flex;
    }

    .logo-image {
        height: 28px;
    }

    .logo-text {
        font-size: 17px;
    }

    .user-avatar {
        padding: 6px;
    }

    /* 展开的搜索面板贴住 header 底部 */
    .expanded-search-container {
        top: 100%;
    }
}
</style>
