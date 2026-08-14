<template>
    <div class="notification-center container mx-auto p-4 md:p-6">
        <h1 class="text-2xl font-semibold mb-6">通知中心 (房东)</h1> <!-- 标题区分 -->

        <!-- 操作与筛选 -->
        <div class="flex flex-wrap justify-between items-center gap-4 mb-4">
            <div class="flex items-center gap-4">
                <el-radio-group v-model="filterStatus" @change="handleFilterChange">
                    <el-radio-button label="all">全部状态</el-radio-button>
                    <el-radio-button label="unread">未读</el-radio-button>
                    <el-radio-button label="read">已读</el-radio-button>
                </el-radio-group>

                <el-select v-model="filterType" placeholder="筛选类型" clearable @change="handleFilterChange"
                    style="width: 180px;">
                    <el-option v-for="item in notificationTypes" :key="item.value" :label="item.label"
                        :value="item.value" />
                </el-select>
            </div>

            <el-button type="primary" plain @click="handleMarkAllRead"
                :disabled="loading || notificationStore.unreadCount === 0">
                全部标记为已读
            </el-button>
        </div>

        <!-- 批量操作栏 -->
        <div v-if="selectedRows.length > 0" class="batch-toolbar mb-4">
            <el-alert type="info" :closable="false" show-icon>
                <template #title>
                    <div class="flex items-center justify-between w-full">
                        <span>已选择 <strong>{{ selectedRows.length }}</strong> 条通知</span>
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

        <!-- 恢复 el-table -->
        <el-table :data="notifications" v-loading="loading" style="width: 100%" empty-text="暂无通知" row-key="id"
            @selection-change="handleSelectionChange">
            <el-table-column type="selection" width="55" />
            <el-table-column label="状态" width="100">
                <template #default="{ row }">
                    <!-- 使用 row.read 判断 -->
                    <el-tag :type="row.read ? 'info' : 'warning'" size="small">
                        {{ row.read ? '已读' : '未读' }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column label="内容">
                <template #default="{ row }">
                    <!-- 修改内容展示方式 -->
                    <div @click="handleNotificationClick(row)" class="cursor-pointer hover:text-blue-600"
                        :class="{ 'font-semibold': !row.read }">
                        <component :is="renderNotificationContent(row)" />
                    </div>
                </template>
            </el-table-column>
            <el-table-column label="时间" width="180">
                <template #default="{ row }">
                    {{ formatDate(row.createdAt, 'YYYY-MM-DD HH:mm') }}
                </template>
            </el-table-column>
            <el-table-column label="操作" width="150" align="center">
                <template #default="{ row }">
                    <!-- 使用 row.read 判断 -->
                    <el-button v-if="!row.read" type="success" link size="small" @click="handleMarkRead(row.id)"
                        :disabled="loading">
                        标记已读
                    </el-button>
                    <el-button type="danger" link size="small" @click="handleDelete(row.id)" :disabled="loading">
                        删除
                    </el-button>
                </template>
            </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div class="mt-6 flex justify-center" v-if="!loading && totalNotifications > pageSize">
            <el-pagination background layout="prev, pager, next" :total="totalNotifications" :page-size="pageSize"
                :current-page="currentPage" @current-change="handlePageChange" />
        </div>

    </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import {
    ElRadioGroup,
    ElRadioButton,
    ElTable,
    ElTableColumn,
    ElTag,
    ElButton,
    ElPagination,
    ElMessage,
    ElMessageBox,
    vLoading,
    ElSelect,
    ElOption,
    ElAlert
} from 'element-plus';

import { useRouter } from 'vue-router';
import {
    getNotifications,
    markAsRead,
    markAllAsRead,
    markMultipleAsRead,
    deleteNotification,
    deleteMultipleNotifications,
} from '@/services/notificationService'; // 复用 service
import type { NotificationDto } from '@/types/notification'; // 复用类型

import { useUserStore } from '@/stores/user';
import { useNotificationStore } from '@/stores/notification';
import { formatDate } from '@/utils/format';
import { VNode, h } from 'vue';
import { RouterLink } from 'vue-router';

const router = useRouter();
const userStore = useUserStore();
const notificationStore = useNotificationStore();

// --- 状态定义 (与用户版一致) ---
const notifications = ref<NotificationDto[]>([]);
const loading = ref(true);
const currentPage = ref(1);
const pageSize = ref(10);
const totalNotifications = ref(0);
const filterStatus = ref<'all' | 'read' | 'unread'>('all');
const filterType = ref<string>('');
const selectedRows = ref<NotificationDto[]>([]);

const selectedUnreadCount = ref(0);

// 新增：通知类型选项
const notificationTypes = ref([
    { label: '所有类型', value: '' },
    { label: '预订请求', value: 'BOOKING_REQUEST' },
    { label: '预订已接受', value: 'BOOKING_ACCEPTED' },
    { label: '预订已拒绝', value: 'BOOKING_REJECTED' },
    { label: '预订已取消', value: 'BOOKING_CANCELLED' },
    { label: '订单已完成', value: 'ORDER_COMPLETED' },
    { label: '收到付款', value: 'PAYMENT_RECEIVED' }, // 根据实际情况调整 key
    { label: '新评价', value: 'NEW_REVIEW' },
    { label: '评价被回复', value: 'REVIEW_REPLIED' },
    { label: '系统通知', value: 'SYSTEM_ANNOUNCEMENT' }, // 假设有系统通知
    { label: '欢迎消息', value: 'WELCOME_MESSAGE' },
    // 根据后端 NotificationType 枚举添加更多类型...
]);

// --- 新增：渲染通知内容的函数 ---
const renderNotificationContent = (notification: NotificationDto): VNode => {
    const { type, actorUsername, entityTitle, content, entityType, entityId } = notification;
    if (notification.title || notification.deepLink) {
        return h('span', [
            h('span', { class: 'font-medium' }, notification.title || ''),
            content ? `：${content}` : '',
        ]);
    }

    const actorLink = actorUsername && notification.actorId != null
        ? h(RouterLink, { class: 'font-medium text-blue-700 hover:underline', to: `/user/profile/${notification.actorId}` }, () => actorUsername)
        : '系统';
    let entityLink: VNode | string = entityTitle || '';
    let entityPath = '';

    // 生成实体链接 (根据房东视角)
    if (entityType && entityId) {
        try {
            const id = parseInt(entityId, 10);
            if (!isNaN(id)) {
                switch (entityType) {
                    case 'BOOKING':
                    case 'ORDER':
                        entityPath = `/host/orders?highlightOrderId=${id}`;
                        entityLink = entityTitle ? h(RouterLink, { class: 'font-medium text-blue-700 hover:underline', to: entityPath }, () => entityTitle) : `订单 #${id}`;
                        break;
                    case 'HOMESTAY':
                        entityPath = `/host/homestay/edit/${id}`;
                        entityLink = entityTitle ? h(RouterLink, { class: 'font-medium text-blue-700 hover:underline', to: entityPath }, () => entityTitle) : `房源 #${id}`;
                        break;
                    case 'REVIEW':
                        entityPath = `/host/reviews?highlightReviewId=${id}`;
                        // 对于评价，entityTitle 可能是房源名，链接指向评价管理
                        const reviewLinkText = entityTitle ? `对房源 '${entityTitle}' 的评价` : `评价 #${id}`;
                        entityLink = h(RouterLink, { class: 'font-medium text-blue-700 hover:underline', to: entityPath }, () => reviewLinkText);
                        break;
                    case 'USER': // 如果 entity 是用户，actor 可能是系统或其他用户
                        entityPath = `/user/profile/${id}`; // 假设可以查看用户资料
                        entityLink = entityTitle ? h(RouterLink, { class: 'font-medium text-blue-700 hover:underline', to: entityPath }, () => entityTitle) : `用户 #${id}`;
                        break;
                }
            }
        } catch (e) {
            console.error("Error creating entity link:", e);
        }
    }

    // 根据通知类型构建 VNode
    switch (type) {
        case 'BOOKING_REQUEST':
            return h('span', ['您收到了来自用户 ', actorLink, ' 的新预订请求 (', entityLink, ')，请及时处理。']);
        case 'BOOKING_ACCEPTED': // 需要后端支持并填充 entityTitle 为订单号
            return h('span', ['您接受了用户 ', actorLink, ' 的预订 (', entityLink, ').']);
        case 'BOOKING_REJECTED': // 需要后端支持并填充 entityTitle 为订单号
            return h('span', ['您拒绝了用户 ', actorLink, ' 的预订 (', entityLink, ').']);
        case 'BOOKING_CANCELLED': // entityTitle 可能是订单号
            return h('span', ['订单 ', entityLink, ' 已被用户 ', actorLink, ' 取消。']);
        case 'ORDER_COMPLETED': // entityTitle 可能是订单号
            return h('span', [entityLink, ' 已完成。请尽快结算相关收益。']);
        case 'PAYMENT_RECEIVED': // entityTitle 可能是订单号
            return h('span', ['用户 ', actorLink, ' 已支付 ', entityLink, '。']);
        case 'NEW_REVIEW': // entityTitle 可能是房源名
            return h('span', ['用户 ', actorLink, ' 提交了 ', entityLink, '。']);
        case 'REVIEW_REPLIED': // entityTitle 可能是房源名
            // 这个理论上是给房客的，但如果房东也收到...
            return h('span', ['您回复了用户 ', actorLink, ' 关于 ', entityLink, ' 的评价。']);
        case 'WELCOME_MESSAGE':
            return h('span', [content]); // 欢迎消息直接用原始内容
        case 'HOMESTAY_APPROVED': // 假设 entityTitle 是房源名
            return h('span', ['您的房源 ', entityLink, ' 已通过审核。']);
        case 'HOMESTAY_REJECTED': // 假设 entityTitle 是房源名
            return h('span', ['您的房源 ', entityLink, ' 未通过审核。', content ? ` 原因: ${content}` : '']); // 可选地显示拒绝原因
        // 添加其他通知类型的渲染逻辑...
        default:
            // 使用原始 content 作为后备
            return h('span', content || '通知内容缺失');
    }
};

// --- API 调用与逻辑 (大部分可复用) ---
let currentFetchId = 0;

const fetchNotifications = async () => {
    console.log('[HostNotificationManage] fetchNotifications called');

    if (!userStore.isAuthenticated) {
        console.warn('[HostNotificationManage] 用户未登录，无法获取通知');
        loading.value = false;
        return;
    }
    console.log('[HostNotificationManage] 用户已认证，继续获取通知');

    const thisFetchId = ++currentFetchId;
    loading.value = true;
    try {
        const params: { page: number; size: number; isRead?: boolean; type?: string } = {
            page: currentPage.value,
            size: pageSize.value,
        };
        if (filterStatus.value !== 'all') {
            params.isRead = filterStatus.value === 'read';
        }
        if (filterType.value && filterType.value !== '') {
            params.type = filterType.value;
        }
        console.log('[HostNotificationManage] 调用 getNotifications API, params:', params);
        const response = await getNotifications(params);
        console.log('[HostNotificationManage] getNotifications API 响应:', response);

        if (thisFetchId !== currentFetchId) return;

        notifications.value = response.data.content;
        totalNotifications.value = response.data.totalElements;
        console.log('[HostNotificationManage] fetchNotifications 成功，赋值后的 notifications.value:', JSON.stringify(notifications.value));

    } catch (error) {
        if (thisFetchId !== currentFetchId) return;
        console.error('[HostNotificationManage] 获取通知失败 (catch block):', error);
        notifications.value = [];
        totalNotifications.value = 0;
    } finally {
        loading.value = false;
    }
};

const handleSelectionChange = (selection: NotificationDto[]) => {
    selectedRows.value = selection;
    selectedUnreadCount.value = selection.filter(n => !n.read).length;
};

const handleMarkRead = async (id: number) => {
    console.log(`[HostNotificationManage] handleMarkRead called for id: ${id}`);
    try {
        console.log(`[HostNotificationManage] Calling markAsRead API for id: ${id}`);
        await markAsRead(id);
        console.log(`[HostNotificationManage] markAsRead API success for id: ${id}`);
        ElMessage.success('已标记为已读');

        console.log(`[HostNotificationManage] Calling fetchUnreadCount`);
        await notificationStore.fetchUnreadCount();
        console.log(`[HostNotificationManage] fetchUnreadCount finished`);

        console.log(`[HostNotificationManage] Reloading notifications after marking as read...`);
        await fetchNotifications();
        console.log(`[HostNotificationManage] Reload notifications finished`);

    } catch (error) {
        console.error('[HostNotificationManage] 标记已读失败 (catch block):', error);
    }
};

const handleMarkAllRead = async () => {
    loading.value = true;
    try {
        const response = await markAllAsRead();
        ElMessage.success(`成功标记 ${response.markedCount} 条通知为已读`);
        selectedRows.value = [];
        await fetchNotifications();
        await notificationStore.fetchUnreadCount();
    } catch (error) {
        console.error('全部标记已读失败:', error);
        loading.value = false;
    }
};

const handleBatchMarkRead = async () => {
    const ids = selectedRows.value.filter(n => !n.read).map(n => n.id);
    if (ids.length === 0) {
        ElMessage.warning('请选择未读通知');
        return;
    }
    try {
        const res = await markMultipleAsRead(ids);
        ElMessage.success(`已批量标记 ${res.markedCount ?? ids.length} 条通知为已读`);
        selectedRows.value = [];
        await fetchNotifications();
        await notificationStore.fetchUnreadCount();
    } catch (error) {
        console.error('批量标记已读失败:', error);
    }
};

const handleDelete = async (id: number) => {
    try {
        await ElMessageBox.confirm(
            '确定要删除这条通知吗？此操作不可恢复。',
            '确认删除',
            {
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                type: 'warning',
            }
        );
        loading.value = true;
        await deleteNotification(id);
        ElMessage.success('通知已删除');
        const shouldMoveToPreviousPage = notifications.value.length === 1 && currentPage.value > 1;
        if (shouldMoveToPreviousPage) {
            currentPage.value -= 1;
        }
        await fetchNotifications();
        await notificationStore.fetchUnreadCount();
    } catch (error: any) {
        if (error !== 'cancel') {
            console.error('删除通知失败:', error);
        }
    } finally {
        loading.value = false;
    }
};

const handleBatchDelete = async () => {
    const ids = selectedRows.value.map(n => n.id);
    if (ids.length === 0) {
        ElMessage.warning('请先选择通知');
        return;
    }
    try {
        await ElMessageBox.confirm(
            `确定要删除选中的 ${ids.length} 条通知吗？此操作不可恢复。`,
            '批量删除',
            {
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                type: 'warning',
            }
        );
        const res = await deleteMultipleNotifications(ids);
        ElMessage.success(`已批量删除 ${res.deletedCount ?? ids.length} 条通知`);
        selectedRows.value = [];
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

// --- 核心区别：点击跳转逻辑 --- 
const handleNotificationClick = async (notification: NotificationDto) => {
    console.log('(房东)点击通知:', notification);

    // 点击整行时仍然标记为已读
    if (!notification.read) {
        try {
            await markAsRead(notification.id);
            const index = notifications.value.findIndex(n => n.id === notification.id);
            if (index !== -1) {
                notifications.value[index].read = true;
            }
            await notificationStore.fetchUnreadCount();
        } catch (error) {
            console.error('点击时标记已读失败:', error);
            // 即使标记失败，也尝试跳转
        }
    }

    if (notification.deepLink) {
        router.push(notification.deepLink);
    }
};

// --- 事件处理 (分页和筛选不变) ---
const handlePageChange = (newPage: number) => {
    currentPage.value = newPage;
    fetchNotifications();
};

const handleFilterChange = () => {
    currentPage.value = 1;
    selectedRows.value = [];
    selectedUnreadCount.value = 0;
    fetchNotifications();
};

// --- 生命周期钩子 ---
onMounted(() => {
    console.log('[HostNotificationManage] Component onMounted hook called');
    console.log('[HostNotificationManage] onMounted - isAuthenticated:', userStore.isAuthenticated);
    fetchNotifications();
    notificationStore.fetchUnreadCount();
});

</script>

<style scoped>
/* 恢复之前的样式 */
.el-table .el-button+.el-button {
    margin-left: 8px;
}

.flex-wrap {
    flex-wrap: wrap;
}

.gap-4 {
    gap: 1rem;
    /* 16px */
}

.batch-toolbar :deep(.el-alert__title) {
    width: 100%;
}
</style>
