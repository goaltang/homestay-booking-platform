<template>
    <div class="notification-center">
        <h1 class="text-2xl font-semibold mb-6">通知中心</h1>

        <!-- 操作与筛选 -->
        <div class="flex justify-between items-center mb-4">
            <el-radio-group v-model="filterStatus" @change="handleFilterChange">
                <el-radio-button label="all">全部</el-radio-button>
                <el-radio-button label="unread">未读</el-radio-button>
                <el-radio-button label="read">已读</el-radio-button>
            </el-radio-group>
            <div class="flex gap-2">
                <el-button type="default" plain @click="showSettingsDialog = true">
                    通知设置
                </el-button>
                <el-button type="primary" plain @click="handleMarkAllRead"
                    :disabled="loading || !canMarkAllRead">
                    全部标记为已读
                </el-button>
            </div>
        </div>

        <!-- 批量操作栏 -->
        <div v-if="selectedNotificationIds.length > 0" class="batch-toolbar mb-4">
            <el-alert type="info" :closable="false" show-icon>
                <template #title>
                    <div class="flex items-center justify-between w-full">
                        <span>已选择 <strong>{{ selectedNotificationIds.length }}</strong> 条通知</span>
                        <div class="flex gap-2">
                            <el-button
                                size="small"
                                type="primary"
                                :disabled="selectedUnreadCount === 0"
                                @click="handleBatchMarkRead"
                            >
                                批量标记已读
                            </el-button>
                            <el-button
                                size="small"
                                type="danger"
                                @click="handleBatchDelete"
                            >
                                批量删除
                            </el-button>
                        </div>
                    </div>
                </template>
            </el-alert>
        </div>

        <!-- 加载状态 -->
        <div v-if="loading" class="loading-container">
            <el-skeleton :rows="4" animated v-for="i in 3" :key="i" />
        </div>

        <!-- 空状态 -->
        <div v-else-if="notifications.length === 0" class="empty-container">
            <el-empty :description="emptyText" />
        </div>

        <!-- 通知卡片列表 -->
        <div v-else class="notification-list">
            <div class="list-header">
                <el-checkbox
                    v-model="currentPageAllSelected"
                    :indeterminate="currentPageIndeterminate"
                    class="mr-2"
                >
                    全选当前页
                </el-checkbox>
            </div>
            <div
                v-for="notification in notifications"
                :key="notification.id"
                class="notification-card"
                :class="{ 'notification-unread': !notification.read }"
                @click="handleNotificationClick(notification)"
            >
                <div class="notification-select" @click.stop>
                    <el-checkbox
                        :model-value="selectedNotificationIds.includes(notification.id)"
                        @change="(val: any) => toggleSelection(notification.id, val as boolean)"
                    />
                </div>

                <div class="notification-icon" :class="`icon-${notification.category}`">
                    <el-icon :size="22">
                        <component :is="getIcon(notification)" />
                    </el-icon>
                </div>

                <div class="notification-body">
                    <div class="notification-header">
                        <el-tag size="small" :type="getTagType(notification)" effect="plain">
                            {{ notification.title }}
                        </el-tag>
                        <span class="notification-time">{{ formatDate(notification.createdAt, 'YYYY-MM-DD HH:mm') }}</span>
                    </div>
                    <div class="notification-content" :class="{ 'font-semibold': !notification.read }">
                        {{ notification.content }}
                    </div>
                </div>

                <div class="notification-actions" @click.stop>
                    <el-button v-if="!notification.read" type="success" link size="small"
                        @click="handleMarkRead(notification.id)" :loading="markingIds.has(notification.id)">
                        标记已读
                    </el-button>
                    <el-button type="danger" link size="small" @click="handleDelete(notification.id)"
                        :loading="deletingIds.has(notification.id)">
                        删除
                    </el-button>
                </div>
            </div>
        </div>

        <!-- 分页 -->
        <div class="mt-6 flex justify-center" v-if="!loading && totalNotifications > pageSize">
            <el-pagination background layout="prev, pager, next" :total="totalNotifications" :page-size="pageSize"
                :current-page="currentPage" @current-change="handlePageChange" />
        </div>

        <!-- 通知偏好设置弹窗 -->
        <el-dialog v-model="showSettingsDialog" title="通知设置" width="400px" align-center>
            <div v-loading="preferenceLoading" class="preference-list">
                <div v-for="key in domainKeys" :key="key" class="preference-item">
                    <span class="preference-label">{{ domainLabels[key] }}</span>
                    <el-switch
                        :model-value="notificationStore.preferences[key] !== false"
                        :disabled="key === 'system' || preferenceLoading"
                        @change="(val: any) => handlePreferenceChange(key, Boolean(val))"
                    />
                </div>
            </div>
            <template #footer>
                <el-button @click="showSettingsDialog = false">关闭</el-button>
            </template>
        </el-dialog>

    </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, shallowRef, onBeforeUnmount } from 'vue';
import { useRouter } from 'vue-router';
import {
    ElRadioGroup, ElRadioButton, ElTag, ElButton, ElPagination,
    ElMessage, ElMessageBox, ElSkeleton, ElEmpty, ElIcon,
    ElDialog, ElSwitch, ElAlert, ElCheckbox
} from 'element-plus';
import {
    Bell, ChatDotRound, Goods, House, Star, Tickets
} from '@element-plus/icons-vue';
import { useUserStore } from '@/stores/user';
import { useNotificationStore } from '@/stores/notification';
import { formatDate } from '@/utils/format';
import type { NotificationDto } from '@/types/notification';
import {
    markMultipleAsRead,
    deleteMultipleNotifications,
} from '@/services/notificationService';

const router = useRouter();
const userStore = useUserStore();
const notificationStore = useNotificationStore();

const notifications = computed(() => notificationStore.notifications);
const loading = computed(() => notificationStore.loading);
const currentPage = shallowRef(1);
const pageSize = shallowRef(10);
const totalNotifications = computed(() => notificationStore.pagination?.totalElements ?? 0);
const filterStatus = shallowRef<'all' | 'read' | 'unread'>('all');
const markingIds = ref<Set<number>>(new Set());
const deletingIds = ref<Set<number>>(new Set());

const isMounted = ref(true);
onBeforeUnmount(() => {
    isMounted.value = false;
});

let currentFetchId = 0;

const selectedNotificationIds = ref<number[]>([]);

const selectedNotifications = computed(() => {
    const ids = new Set(selectedNotificationIds.value);
    return notifications.value.filter(n => ids.has(n.id));
});

const selectedUnreadCount = computed(() =>
    selectedNotifications.value.filter(n => !n.read).length
);

const currentPageAllSelected = computed({
    get: () =>
        notifications.value.length > 0 &&
        notifications.value.every(n => selectedNotificationIds.value.includes(n.id)),
    set: (checked: boolean) => {
        const ids = notifications.value.map(n => n.id);
        if (checked) {
            const set = new Set(selectedNotificationIds.value);
            ids.forEach(id => set.add(id));
            selectedNotificationIds.value = Array.from(set);
        } else {
            selectedNotificationIds.value = selectedNotificationIds.value.filter(id => !ids.includes(id));
        }
    }
});

const currentPageIndeterminate = computed(() => {
    const pageIds = notifications.value.map(n => n.id);
    const selected = selectedNotificationIds.value.filter(id => pageIds.includes(id));
    return selected.length > 0 && selected.length < pageIds.length;
});

const emptyText = computed(() => {
    const map = { all: '暂无通知', unread: '暂无未读通知', read: '暂无已读通知' };
    return map[filterStatus.value];
});

const canMarkAllRead = computed(() => {
    return notificationStore.unreadCount > 0 || notifications.value.some(notification => !notification.read);
});

// --- 通知分类与图标映射 ---
const getIcon = (notification: NotificationDto) => {
    const iconMap: Record<string, any> = {
        order: Tickets,
        message: ChatDotRound,
        review: Star,
        homestay: House,
        coupon: Goods,
        system: Bell,
    };
    return iconMap[notification.category || ''] || Bell;
};

const getTagType = (notification: NotificationDto): any => {
    const typeMap: Record<string, any> = {
        order: 'primary',
        message: 'success',
        review: 'warning',
        homestay: 'info',
        coupon: 'danger',
        system: '',
    };
    return typeMap[notification.category || ''] || '';
};

// --- API 调用与逻辑 ---
const fetchNotifications = async () => {
    if (!userStore.isAuthenticated) {
        notificationStore.clearNotifications();
        return;
    }

    const thisFetchId = ++currentFetchId;
    try {
        const isReadFilter = filterStatus.value === 'all' ? null : filterStatus.value === 'read';
        await notificationStore.fetchNotifications(
            currentPage.value - 1,
            pageSize.value,
            isReadFilter,
            'center',
        );
        if (thisFetchId !== currentFetchId) return;
    } catch (error) {
        if (thisFetchId !== currentFetchId) return;
        console.error('获取通知失败:', error);
    }
};

const handleMarkRead = async (id: number) => {
    markingIds.value.add(id);
    try {
        await notificationStore.markAsRead(id);
        if (filterStatus.value !== 'all') {
            await fetchNotifications();
        }
        ElMessage.success('已标记为已读');
    } catch (error) {
        console.error('标记已读失败:', error);
    } finally {
        markingIds.value.delete(id);
    }
};

const handleMarkAllRead = async () => {
    try {
        const markedCount = await notificationStore.markAllAsRead();
        selectedNotificationIds.value = [];
        ElMessage.success(`成功标记 ${markedCount} 条通知为已读`);
        await fetchNotifications();
    } catch (error) {
        console.error('全部标记已读失败:', error);
    }
};

const handleBatchMarkRead = async () => {
    const ids = selectedNotifications.value
        .filter(n => !n.read)
        .map(n => n.id);
    if (ids.length === 0) {
        ElMessage.warning('请选择未读通知');
        return;
    }
    try {
        const res = await markMultipleAsRead(ids);
        ElMessage.success(`已批量标记 ${res.markedCount ?? ids.length} 条通知为已读`);
        selectedNotificationIds.value = [];
        await fetchNotifications();
        await notificationStore.fetchUnreadCount();
    } catch (error) {
        console.error('批量标记已读失败:', error);
    }
};

const handleDelete = async (id: number) => {
    try {
        await ElMessageBox.confirm('确定要删除这条通知吗？此操作不可恢复。', '确认删除', {
            confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning',
        });
        deletingIds.value.add(id);
        const shouldMoveToPreviousPage = notifications.value.length === 1 && currentPage.value > 1;
        await notificationStore.deleteNotification(id);
        selectedNotificationIds.value = selectedNotificationIds.value.filter(sid => sid !== id);
        if (shouldMoveToPreviousPage) {
            currentPage.value -= 1;
        }
        await fetchNotifications();
        await notificationStore.fetchUnreadCount();
        ElMessage.success('通知已删除');
    } catch (error: any) {
        if (error !== 'cancel') {
            console.error('删除通知失败:', error);
        }
    } finally {
        deletingIds.value.delete(id);
    }
};

const handleBatchDelete = async () => {
    const ids = [...selectedNotificationIds.value];
    if (ids.length === 0) {
        ElMessage.warning('请先选择通知');
        return;
    }
    try {
        await ElMessageBox.confirm(`确定要删除选中的 ${ids.length} 条通知吗？此操作不可恢复。`, '批量删除', {
            confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning',
        });
        const res = await deleteMultipleNotifications(ids);
        ElMessage.success(`已批量删除 ${res.deletedCount ?? ids.length} 条通知`);
        selectedNotificationIds.value = [];
        const shouldMoveToPreviousPage = notifications.value.length === ids.length && currentPage.value > 1;
        if (shouldMoveToPreviousPage) {
            currentPage.value -= 1;
        }
        await fetchNotifications();
        await notificationStore.fetchUnreadCount();
    } catch (error: any) {
        if (error !== 'cancel') {
            console.error('批量删除通知失败:', error);
        }
    }
};

const toggleSelection = (id: number, checked: boolean) => {
    if (checked) {
        if (!selectedNotificationIds.value.includes(id)) {
            selectedNotificationIds.value = [...selectedNotificationIds.value, id];
        }
    } else {
        selectedNotificationIds.value = selectedNotificationIds.value.filter(sid => sid !== id);
    }
};

const handleNotificationClick = async (notification: NotificationDto) => {
    // 标记已读
    const wasUnread = !notification.read;
    if (!notification.read) {
        try {
            await notificationStore.markAsRead(notification.id);
        } catch (e) { /* ignore */ }
    }

    if (notification.deepLink) {
        router.push(notification.deepLink);
    }

    // 无 deepLink 时仅刷新列表（若从未读筛选中移除了当前项）
    if (wasUnread && filterStatus.value === 'unread') {
        await fetchNotifications();
        if (!isMounted.value) return;
    }
};

const handlePageChange = (newPage: number) => {
    currentPage.value = newPage;
    fetchNotifications();
};

const handleFilterChange = () => {
    currentPage.value = 1;
    selectedNotificationIds.value = [];
    fetchNotifications();
};

const showSettingsDialog = ref(false);
const preferenceLoading = ref(false);

const domainLabels: Record<string, string> = {
    order: '订单通知',
    message: '消息通知',
    review: '评价通知',
    homestay: '房源通知',
    coupon: '优惠券通知',
    system: '系统通知',
};

const domainKeys = ['order', 'message', 'review', 'homestay', 'coupon', 'system'];

const handlePreferenceChange = async (domain: string, enabled: boolean) => {
    preferenceLoading.value = true;
    try {
        await notificationStore.updatePreference(domain, enabled);
        ElMessage.success(`${domainLabels[domain]}已${enabled ? '开启' : '关闭'}`);
    } catch (error) {
        console.error('更新通知偏好失败:', error);
    } finally {
        preferenceLoading.value = false;
    }
};

onMounted(() => {
    fetchNotifications();
    notificationStore.fetchUnreadCount();
    notificationStore.fetchPreferences();
});
</script>

<style scoped>
.notification-center {
    max-width: 900px;
    margin: 0 auto;
    padding: 20px;
}

.batch-toolbar :deep(.el-alert__title) {
    width: 100%;
}

.loading-container {
    padding: 24px;
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.empty-container {
    padding: 48px 0;
    text-align: center;
}

.notification-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.list-header {
    display: flex;
    align-items: center;
    padding: 0 8px;
}

.notification-card {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    padding: 16px 20px;
    background: #fff;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 12px;
    cursor: pointer;
    transition: all 0.2s ease;
}

.notification-card:hover {
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
    transform: translateY(-1px);
}

.notification-unread {
    background: linear-gradient(135deg, #fff8f0 0%, #fff 100%);
    border-color: #ffe4c4;
}

.notification-select {
    flex: 0 0 24px;
    padding-top: 4px;
}

.notification-icon {
    flex-shrink: 0;
    width: 44px;
    height: 44px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
}

.icon-order { background: linear-gradient(135deg, #409eff 0%, #79bbff 100%); }
.icon-message { background: linear-gradient(135deg, #67c23a 0%, #95d475 100%); }
.icon-review { background: linear-gradient(135deg, #e6a23c 0%, #f3d19e 100%); }
.icon-homestay { background: linear-gradient(135deg, #909399 0%, #b1b3b8 100%); }
.icon-coupon { background: linear-gradient(135deg, #f56c6c 0%, #fab6b6 100%); }
.icon-system { background: linear-gradient(135deg, #a0cfff 0%, #c6e2ff 100%); }

.notification-body {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.notification-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
}

.notification-time {
    font-size: 12px;
    color: #c0c4cc;
    flex-shrink: 0;
}

.notification-content {
    font-size: 14px;
    color: #606266;
    line-height: 1.6;
    word-break: break-all;
}

.notification-actions {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 4px;
    flex-shrink: 0;
}

.notification-actions :deep(.el-button) {
    padding: 4px 0;
}

.preference-list {
    display: flex;
    flex-direction: column;
    gap: 16px;
    padding: 8px 4px;
}

.preference-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 0;
    border-bottom: 1px solid var(--el-border-color-lighter);
}

.preference-item:last-child {
    border-bottom: none;
}

.preference-label {
    font-size: 14px;
    color: var(--el-text-color-primary);
}

@media (max-width: 768px) {
    .notification-card {
        flex-direction: column;
        gap: 12px;
    }

    .notification-actions {
        flex-direction: row;
        width: 100%;
        justify-content: flex-end;
    }
}
</style>
