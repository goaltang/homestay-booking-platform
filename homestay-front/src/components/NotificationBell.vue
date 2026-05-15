<template>
    <el-popover placement="bottom-end" :width="350" trigger="click" popper-class="notification-popover"
        @show="loadNotifications">
        <template #reference>
            <el-badge :is-dot="hasUnread" :class="['notification-badge', { 'shake-animation': isAnimating }]">
                <el-icon :size="20" class="notification-icon">
                    <Bell />
                </el-icon>
            </el-badge>
        </template>
        <template #default>
            <div class="notification-panel">
                <div class="notification-header">
                    <span>通知</span>
                    <el-button v-if="hasUnread" type="primary" link size="small" @click="handleMarkAllRead"
                        :disabled="loading">
                        全部已读
                    </el-button>
                </div>
                <el-scrollbar max-height="300px" class="notification-list-scrollbar"
                    v-if="!loading && notifications.length > 0">
                    <ul class="notification-list">
                        <li v-for="notification in notifications" :key="notification.id"
                            :class="['notification-item', { unread: !notification.read }]"
                            @click="handleNotificationClick(notification)">
                            <div class="notification-content">
                                <el-icon class="notification-item-icon">
                                    <component :is="getNotificationIcon(notification)" />
                                </el-icon>
                                <div class="notification-copy">
                                    <p class="notification-title">{{ notification.title }}</p>
                                    <p class="notification-text">{{ notification.content }}</p>
                                </div>
                            </div>
                            <div class="notification-meta">
                                <span class="notification-time">{{ formatTimeAgo(notification.createdAt) }}</span>
                                <span v-if="!notification.read" class="unread-dot"></span>
                            </div>
                        </li>
                    </ul>
                </el-scrollbar>
                <div v-else-if="loading" class="notification-loading">
                    <el-skeleton :rows="3" animated />
                </div>
                <div v-else class="notification-empty">
                    <el-empty description="暂无通知" :image-size="60" />
                </div>
                <div class="notification-footer">
                    <el-button type="primary" link @click="goToAppropriateNotificationCenter">
                        查看全部通知
                    </el-button>
                </div>
            </div>
        </template>
    </el-popover>
</template>

<script setup lang="ts">
import { computed, onMounted, shallowRef, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useNotificationStore } from '@/stores/notification';
import type { Notification } from '@/stores/notification';

import { ElPopover, ElBadge, ElIcon, ElScrollbar, ElButton, ElEmpty, ElSkeleton } from 'element-plus';
import { Bell, Calendar, ChatDotRound, Star, InfoFilled, Goods, House } from '@element-plus/icons-vue';
import { formatDistanceToNow } from 'date-fns';
import { zhCN } from 'date-fns/locale';
import { useUserStore } from '@/stores/user';

const router = useRouter();
const route = useRoute();
const notificationStore = useNotificationStore();
const userStore = useUserStore();
const isAnimating = shallowRef(false);

// 从 store 获取状态
const unreadCount = computed(() => notificationStore.unreadCount);
const notifications = computed(() => notificationStore.notifications); // 直接使用 store 的列表
const loading = computed(() => notificationStore.loading);
const hasUnread = computed(() => notificationStore.hasUnread);
const isAuthenticated = computed(() => userStore.isAuthenticated);

// 加载通知列表 (当 Popover 显示时触发)
const loadNotifications = async () => {
    if (!isAuthenticated.value) return;

    // 只加载最新的 5 条用于下拉面板
    await Promise.all([
        notificationStore.fetchUnreadCount(),
        notificationStore.fetchNotifications(0, 5, null, 'dropdown'),
    ]);
};

// 处理点击单条通知
const handleNotificationClick = (notification: Notification) => {
    console.log('点击通知 (Bell):', notification);
    if (!notification.read) {
        void notificationStore.markAsRead(notification.id);
    }

    if (notification.deepLink) {
        router.push(notification.deepLink);
    } else {
        goToAppropriateNotificationCenter();
    }
};

// 处理点击"全部已读"
const handleMarkAllRead = () => {
    void notificationStore.markAllAsRead();
};

// 跳转到通知中心页面
const goToAppropriateNotificationCenter = () => {
    if (route.path.startsWith('/host')) {
        router.push('/host/notifications');
    } else {
        router.push('/user/notifications');
    }
};

const getNotificationIcon = (notification: Notification) => {
    const iconMap: Record<string, any> = {
        order: Calendar,
        message: ChatDotRound,
        review: Star,
        homestay: House,
        coupon: Goods,
        system: InfoFilled,
    };
    return iconMap[notification.category || ''] || InfoFilled;
};

// 格式化时间为 "多久以前"
const formatTimeAgo = (dateString: string): string => {
    try {
        const date = new Date(dateString);
        return formatDistanceToNow(date, { addSuffix: true, locale: zhCN });
    } catch (e) {
        console.error('时间格式化错误:', e);
        return dateString; // 返回原始字符串作为回退
    }
};

// 监听未读数量变化，仅在数量增加时触发动画
watch(unreadCount, (newVal, oldVal) => {
    if (newVal > oldVal) {
        isAnimating.value = true;
        // 动画持续时间后移除 class (例如 600ms)
        setTimeout(() => {
            isAnimating.value = false;
        }, 600);
    }
});

// 组件挂载时获取未读计数
onMounted(() => {
    // 如果用户已登录，尝试获取未读数
    if (isAuthenticated.value) {
        void notificationStore.fetchUnreadCount();
    }
});

</script>

<style scoped>
.notification-badge {
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    height: 100%;
    /* 确保徽章垂直居中 */
    margin-right: 15px;
    /* 可以调整与其他导航项的间距 */
}

.notification-icon {
    color: #606266;
    /* 可以根据你的主题调整 */
    outline: none;
    /* 移除点击时的轮廓 */
}

.notification-icon:hover {
    color: var(--el-color-primary);
}

.notification-panel {
    display: flex;
    flex-direction: column;
}

.notification-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px 15px;
    border-bottom: 1px solid var(--el-border-color-lighter);
    font-size: 14px;
    font-weight: bold;
}

.notification-list-scrollbar {
    padding: 0 5px;
    /* 给滚动条留出空间 */
}

.notification-list {
    list-style: none;
    padding: 0;
    margin: 0;
}

.notification-item {
    padding: 12px 10px;
    cursor: pointer;
    border-bottom: 1px solid var(--el-border-color-lighter);
    transition: background-color 0.2s;
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
}

.notification-item:last-child {
    border-bottom: none;
}

.notification-item:hover {
    background-color: var(--el-fill-color-light);
}

.notification-item.unread {
    /* background-color: #ecf5ff; */
    /* 可以用淡蓝色背景标记未读 */
}

.notification-content {
    display: flex;
    align-items: flex-start;
    margin-right: 10px;
    /* 与右侧 meta 分开 */
}

.notification-item-icon {
    margin-right: 8px;
    margin-top: 2px;
    /* 微调图标垂直对齐 */
    color: var(--el-text-color-secondary);
    flex-shrink: 0;
    /* 防止图标被压缩 */
}

.notification-copy {
    flex-grow: 1;
    min-width: 0;
}

.notification-title {
    font-size: 13px;
    font-weight: 600;
    color: var(--el-text-color-primary);
    line-height: 1.35;
    margin: 0 0 3px;
    word-break: break-word;
}

.notification-text {
    font-size: 13px;
    color: var(--el-text-color-regular);
    line-height: 1.4;
    margin: 0;
    word-break: break-word;
    /* 防止长单词溢出 */
}

.notification-meta {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    flex-shrink: 0;
    /* 防止被压缩 */
}

.notification-time {
    font-size: 11px;
    color: var(--el-text-color-secondary);
    margin-bottom: 4px;
    white-space: nowrap;
    /* 防止时间换行 */
}

.unread-dot {
    width: 6px;
    height: 6px;
    background-color: var(--el-color-primary);
    border-radius: 50%;
    display: inline-block;
}

.notification-loading,
.notification-empty {
    padding: 20px;
    text-align: center;
    color: var(--el-text-color-secondary);
}

.notification-footer {
    padding: 10px 15px;
    text-align: center;
    border-top: 1px solid var(--el-border-color-lighter);
}

/* 定义晃动动画 */
@keyframes shake {

    0%,
    100% {
        transform: translateX(0);
    }

    10%,
    30%,
    50%,
    70%,
    90% {
        transform: translateX(-3px);
    }

    20%,
    40%,
    60%,
    80% {
        transform: translateX(3px);
    }
}

/* 应用动画的类 */
.shake-animation {
    animation: shake 0.6s ease-in-out;
}
</style>

/* Popover 样式微调 (全局或局部) */
<style>
.notification-popover {
    padding: 0 !important;
    /* 移除 ElPopover 的默认内边距 */
}
</style>
