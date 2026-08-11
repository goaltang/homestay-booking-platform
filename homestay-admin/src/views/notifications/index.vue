<template>
    <div class="notifications-page">
        <!-- 页面头部 -->
        <div class="page-header">
            <el-card shadow="never">
                <div class="header-content">
                    <div class="header-left">
                        <h2>通知中心</h2>
                        <p>管理审核通知、系统消息和重要提醒</p>
                    </div>
                    <div class="header-right">
                        <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="badge">
                            <el-button type="primary" @click="markAllAsRead" :disabled="unreadCount === 0">
                                <el-icon>
                                    <Check />
                                </el-icon>
                                全部已读
                            </el-button>
                        </el-badge>
                        <el-button type="warning" @click="showSendDialog = true">
                            <el-icon>
                                <Bell />
                            </el-icon>
                            发送系统通知
                        </el-button>
                        <el-button @click="refreshNotifications">
                            <el-icon>
                                <Refresh />
                            </el-icon>
                            刷新
                        </el-button>
                    </div>
                </div>
            </el-card>
        </div>

        <!-- 筛选条件 -->
        <div class="filter-section">
            <el-card shadow="never">
                <el-form :inline="true" :model="filterForm">
                    <el-form-item label="通知类型">
                        <el-select v-model="filterForm.type" placeholder="全部类型" clearable style="width: 200px;">
                            <el-option
                                v-for="type in notificationTypes"
                                :key="type.value"
                                :label="type.label"
                                :value="type.value"
                            />
                        </el-select>
                    </el-form-item>
                    <el-form-item label="状态">
                        <el-select v-model="filterForm.read" placeholder="全部状态" clearable style="width: 120px;">
                            <el-option label="未读" :value="false" />
                            <el-option label="已读" :value="true" />
                        </el-select>
                    </el-form-item>
                    <el-form-item>
                        <el-button type="primary" @click="handleSearch">
                            <el-icon>
                                <Search />
                            </el-icon>
                            搜索
                        </el-button>
                        <el-button @click="resetSearch">重置</el-button>
                    </el-form-item>
                </el-form>
            </el-card>
        </div>

        <!-- 通知列表 -->
        <div class="notifications-list">
            <el-card shadow="never">
                <div v-loading="loading">
                    <div v-if="notifications.length > 0" class="batch-toolbar">
                        <div class="batch-left">
                            <el-checkbox v-model="currentPageAllSelected" :indeterminate="currentPageIndeterminate">
                                全选当前页
                            </el-checkbox>
                            <span class="selected-count">已选择 {{ selectedNotificationIds.length }} 条</span>
                        </div>
                        <div class="batch-actions">
                            <el-button
                                size="small"
                                type="primary"
                                :disabled="selectedUnreadCount === 0"
                                @click="batchMarkAsRead"
                            >
                                批量标记已读
                            </el-button>
                            <el-button
                                size="small"
                                type="danger"
                                :disabled="selectedNotificationIds.length === 0"
                                @click="batchDeleteNotifications"
                            >
                                批量删除
                            </el-button>
                        </div>
                    </div>
                    <div v-if="notifications.length === 0" class="empty-state">
                        <el-empty description="暂无通知" />
                    </div>
                    <div v-else class="notification-items">
                        <div v-for="notification in notifications" :key="notification.id" class="notification-item"
                            :class="{ 'unread': !notification.read }" @click="handleNotificationClick(notification)">
                            <div class="notification-select" @click.stop>
                                <el-checkbox
                                    :model-value="selectedNotificationIds.includes(notification.id)"
                                    @change="toggleNotificationSelection(notification.id, $event)"
                                />
                            </div>
                            <div class="notification-icon">
                                <el-icon :class="getNotificationIconClass(notification.type)">
                                    <component :is="getNotificationIcon(notification.type)" />
                                </el-icon>
                            </div>
                            <div class="notification-content">
                                <div class="notification-header">
                                    <h4 class="notification-title">{{ notification.title || '通知' }}</h4>
                                    <span class="notification-time">{{ formatTime(notification.createdAt) }}</span>
                                </div>
                                <p class="notification-message">{{ notification.content }}</p>
                                <div class="notification-meta" v-if="notification.entityType === 'HOMESTAY'">
                                    <el-tag size="small" type="info">房源ID: {{ notification.entityId }}</el-tag>
                                </div>
                            </div>
                            <div class="notification-actions">
                                <el-button v-if="!notification.read" type="primary" size="small" text
                                    @click.stop="markAsRead(notification.id)">
                                    标记已读
                                </el-button>
                                <el-button type="danger" size="small" text
                                    @click.stop="deleteNotification(notification.id)">
                                    删除
                                </el-button>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- 分页 -->
                <div class="pagination" v-if="total > 0">
                    <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize"
                        :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper"
                        @size-change="handleSizeChange" @current-change="handleCurrentChange" />
                </div>
            </el-card>
        </div>

        <!-- 广播任务历史 -->
        <div class="broadcast-history">
            <el-card shadow="never">
                <template #header>
                    <div class="section-header">
                        <h3>广播任务历史</h3>
                        <div class="section-actions">
                            <el-select
                                v-model="broadcastJobFilter.status"
                                placeholder="全部状态"
                                clearable
                                style="width: 140px;"
                                @change="handleBroadcastJobStatusChange"
                            >
                                <el-option
                                    v-for="status in broadcastJobStatusOptions"
                                    :key="status.value"
                                    :label="status.label"
                                    :value="status.value"
                                />
                            </el-select>
                            <el-button @click="refreshBroadcastJobs" :loading="broadcastJobsLoading">
                                <el-icon>
                                    <Refresh />
                                </el-icon>
                                刷新
                            </el-button>
                        </div>
                    </div>
                </template>

                <el-table
                    v-loading="broadcastJobsLoading"
                    :data="broadcastJobs"
                    border
                    stripe
                    style="width: 100%"
                    empty-text="暂无广播任务"
                >
                    <el-table-column prop="jobId" label="任务ID" width="100" />
                    <el-table-column prop="status" label="状态" width="110">
                        <template #default="{ row }">
                            <el-tag :type="getBroadcastJobStatusTagType(row.status)" size="small">
                                {{ formatBroadcastJobStatus(row.status) }}
                            </el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column prop="targetCount" label="目标数" width="100" align="center" />
                    <el-table-column prop="successCount" label="成功数" width="100" align="center" />
                    <el-table-column prop="failureReason" label="失败原因" min-width="220" show-overflow-tooltip>
                        <template #default="{ row }">
                            {{ row.failureReason || '-' }}
                        </template>
                    </el-table-column>
                    <el-table-column prop="submittedAt" label="提交时间" width="180">
                        <template #default="{ row }">
                            {{ formatDateTime(row.submittedAt) }}
                        </template>
                    </el-table-column>
                    <el-table-column label="操作" width="110" fixed="right" align="center">
                        <template #default="{ row }">
                            <el-button type="primary" size="small" text @click="handleViewBroadcastJob(row)">
                                <el-icon>
                                    <View />
                                </el-icon>
                                详情
                            </el-button>
                        </template>
                    </el-table-column>
                </el-table>

                <div class="pagination" v-if="broadcastJobTotal > 0">
                    <el-pagination
                        v-model:current-page="broadcastJobCurrentPage"
                        v-model:page-size="broadcastJobPageSize"
                        :page-sizes="[10, 20, 50]"
                        :total="broadcastJobTotal"
                        layout="total, sizes, prev, pager, next, jumper"
                        @size-change="handleBroadcastJobSizeChange"
                        @current-change="handleBroadcastJobCurrentChange"
                    />
                </div>
            </el-card>
        </div>

        <!-- 发送系统通知弹窗 -->
        <el-dialog v-model="showSendDialog" title="发送系统通知" width="500px" align-center>
            <el-form :model="sendForm" label-width="100px">
                <el-form-item label="发送对象">
                    <el-tag type="info">全体用户</el-tag>
                </el-form-item>
                <el-form-item label="通知内容">
                    <el-input
                        v-model="sendForm.content"
                        type="textarea"
                        :rows="4"
                        placeholder="请输入通知内容"
                        maxlength="500"
                        show-word-limit
                    />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="showSendDialog = false">取消</el-button>
                <el-button type="primary" @click="handleSendNotification" :loading="sendLoading">
                    发送
                </el-button>
            </template>
        </el-dialog>

        <!-- 广播任务详情弹窗 -->
        <el-dialog v-model="broadcastJobDetailVisible" title="广播任务详情" width="680px" align-center>
            <div v-loading="broadcastJobDetailLoading">
                <el-descriptions v-if="currentBroadcastJob" :column="2" border>
                    <el-descriptions-item label="任务ID">{{ currentBroadcastJob.jobId }}</el-descriptions-item>
                    <el-descriptions-item label="状态">
                        <el-tag :type="getBroadcastJobStatusTagType(currentBroadcastJob.status)" size="small">
                            {{ formatBroadcastJobStatus(currentBroadcastJob.status) }}
                        </el-tag>
                    </el-descriptions-item>
                    <el-descriptions-item label="提交人">
                        {{ currentBroadcastJob.initiatedByUsername || currentBroadcastJob.initiatedBy || '-' }}
                    </el-descriptions-item>
                    <el-descriptions-item label="内容长度">
                        {{ currentBroadcastJob.contentLength }}
                    </el-descriptions-item>
                    <el-descriptions-item label="目标数">{{ currentBroadcastJob.targetCount }}</el-descriptions-item>
                    <el-descriptions-item label="成功数">{{ currentBroadcastJob.successCount }}</el-descriptions-item>
                    <el-descriptions-item label="失败数">{{ currentBroadcastJob.failureCount }}</el-descriptions-item>
                    <el-descriptions-item label="提交时间">
                        {{ formatDateTime(currentBroadcastJob.submittedAt) }}
                    </el-descriptions-item>
                    <el-descriptions-item label="开始时间">
                        {{ formatDateTime(currentBroadcastJob.startedAt) }}
                    </el-descriptions-item>
                    <el-descriptions-item label="完成时间">
                        {{ formatDateTime(currentBroadcastJob.completedAt) }}
                    </el-descriptions-item>
                    <el-descriptions-item label="内容摘要" :span="2">
                        <span class="description-text">{{ currentBroadcastJob.contentSummary || '-' }}</span>
                    </el-descriptions-item>
                    <el-descriptions-item label="失败原因" :span="2">
                        <span class="description-text">{{ currentBroadcastJob.failureReason || '-' }}</span>
                    </el-descriptions-item>
                </el-descriptions>
            </div>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
    Check,
    Refresh,
    Bell,
    Document,
    Warning,
    CircleCheck,
    InfoFilled,
    House,
    Search,
    View
} from '@element-plus/icons-vue'
import {
    getNotifications,
    getUnreadCount,
    getNotificationTypes,
    markAsRead as markNotificationAsRead,
    markMultipleAsRead as markMultipleNotificationsAsRead,
    markAllAsRead as markAllNotificationsAsRead,
    deleteNotification as removeNotification,
    deleteMultipleNotifications as removeMultipleNotifications,
    broadcastSystemNotification,
    getNotificationBroadcastJobs,
    getNotificationBroadcastJob,
    type NotificationTypeOption,
    type NotificationDto,
    type NotificationBroadcastJob
} from '@/api/notification'
import { useRouter } from 'vue-router'

const router = useRouter()

// 响应式数据
const loading = ref(false)
const notifications = ref<NotificationDto[]>([])
const notificationTypes = ref<NotificationTypeOption[]>([])
const selectedNotificationIds = ref<number[]>([])
const unreadCount = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const broadcastJobsLoading = ref(false)
const broadcastJobs = ref<NotificationBroadcastJob[]>([])
const broadcastJobCurrentPage = ref(1)
const broadcastJobPageSize = ref(10)
const broadcastJobTotal = ref(0)
const broadcastJobDetailVisible = ref(false)
const broadcastJobDetailLoading = ref(false)
const currentBroadcastJob = ref<NotificationBroadcastJob | null>(null)
const detailRequestId = ref(0)
const fetchId = ref(0)
const broadcastFetchId = ref(0)

const broadcastJobStatusOptions = [
    { label: '待执行', value: 'PENDING' },
    { label: '执行中', value: 'RUNNING' },
    { label: '已成功', value: 'SUCCEEDED' },
    { label: '已失败', value: 'FAILED' },
    { label: '已限流', value: 'RATE_LIMITED' }
]

const selectedNotifications = computed(() => {
    const selectedIds = new Set(selectedNotificationIds.value)
    return notifications.value.filter(notification => selectedIds.has(notification.id))
})

const selectedUnreadCount = computed(() =>
    selectedNotifications.value.filter(notification => !notification.read).length
)

const currentPageAllSelected = computed({
    get: () =>
        notifications.value.length > 0 &&
        notifications.value.every(notification => selectedNotificationIds.value.includes(notification.id)),
    set: (checked: boolean) => {
        const pageIds = notifications.value.map(n => n.id);
        if (checked) {
            const set = new Set([...selectedNotificationIds.value, ...pageIds]);
            selectedNotificationIds.value = Array.from(set);
        } else {
            selectedNotificationIds.value = selectedNotificationIds.value.filter(id => !pageIds.includes(id));
        }
    }
})

const currentPageIndeterminate = computed(() =>
    selectedNotificationIds.value.length > 0 && !currentPageAllSelected.value
)

// 筛选表单
const filterForm = reactive({
    type: '',
    read: null as boolean | null
})

const broadcastJobFilter = reactive({
    status: ''
})

// 发送系统通知
const showSendDialog = ref(false)
const sendLoading = ref(false)
const sendForm = reactive({
    target: 'all' as 'all' | 'user',
    userId: null as number | null,
    content: ''
})

const handleSendNotification = async () => {
    if (!sendForm.content.trim()) {
        ElMessage.warning('请输入通知内容')
        return
    }

    sendLoading.value = true
    try {
        const job = await broadcastSystemNotification(sendForm.content.trim())
        ElMessage.success(`系统通知广播任务已提交，任务ID：${job.jobId}`)
        showSendDialog.value = false
        sendForm.content = ''
        broadcastJobCurrentPage.value = 1
        await loadBroadcastJobs()
    } catch (error: any) {
        console.error('发送系统通知失败:', error)
        ElMessage.error(
            error?.response?.data?.failureReason ||
            error?.response?.data?.error ||
            '发送系统通知失败'
        )
    } finally {
        sendLoading.value = false
    }
}

// 方法
const loadNotifications = async () => {
    const currentFetchId = ++fetchId.value
    try {
        loading.value = true
        const params = {
            page: currentPage.value - 1, // 后端从0开始计数
            size: pageSize.value,
            type: filterForm.type || undefined,
            isRead: filterForm.read !== null ? filterForm.read : undefined
        }

        const response = await getNotifications(params)
        if (currentFetchId !== fetchId.value) return
        notifications.value = response.content
        total.value = response.totalElements
        selectedNotificationIds.value = []
    } catch (error) {
        if (currentFetchId !== fetchId.value) return
        console.error('获取通知列表失败:', error)
        ElMessage.error('获取通知列表失败')
    } finally {
        if (currentFetchId === fetchId.value) {
            loading.value = false
        }
    }
}

const loadUnreadCount = async () => {
    try {
        const response = await getUnreadCount()
        unreadCount.value = response.unreadCount
    } catch (error) {
        console.error('获取未读数量失败:', error)
    }
}

const loadNotificationTypes = async () => {
    try {
        notificationTypes.value = await getNotificationTypes()
    } catch (error) {
        console.error('获取通知类型失败:', error)
        ElMessage.error('获取通知类型失败')
    }
}

const loadBroadcastJobs = async () => {
    const currentFetchId = ++broadcastFetchId.value
    try {
        broadcastJobsLoading.value = true
        const response = await getNotificationBroadcastJobs({
            page: broadcastJobCurrentPage.value - 1,
            size: broadcastJobPageSize.value,
            status: broadcastJobFilter.status || undefined
        })

        if (currentFetchId !== broadcastFetchId.value) return true
        broadcastJobs.value = response.content
        broadcastJobTotal.value = response.totalElements
        return true
    } catch (error) {
        if (currentFetchId !== broadcastFetchId.value) return false
        console.error('获取广播任务历史失败:', error)
        ElMessage.error('获取广播任务历史失败')
        return false
    } finally {
        if (currentFetchId === broadcastFetchId.value) {
            broadcastJobsLoading.value = false
        }
    }
}

const toggleNotificationSelection = (notificationId: number, checked: unknown) => {
    if (Boolean(checked)) {
        if (!selectedNotificationIds.value.includes(notificationId)) {
            selectedNotificationIds.value = [...selectedNotificationIds.value, notificationId]
        }
        return
    }

    selectedNotificationIds.value = selectedNotificationIds.value.filter(id => id !== notificationId)
}

const refreshNotifications = async () => {
    await Promise.all([
        loadNotifications(),
        loadUnreadCount()
    ])
    ElMessage.success('刷新成功')
}

const refreshBroadcastJobs = async () => {
    const refreshed = await loadBroadcastJobs()
    if (refreshed) {
        ElMessage.success('广播任务历史已刷新')
    }
}

const handleSearch = () => {
    currentPage.value = 1
    loadNotifications()
}

const resetSearch = () => {
    filterForm.type = ''
    filterForm.read = null
    currentPage.value = 1
    loadNotifications()
}

const handleSizeChange = (val: number) => {
    pageSize.value = val
    currentPage.value = 1
    loadNotifications()
}

const handleCurrentChange = (val: number) => {
    currentPage.value = val
    loadNotifications()
}

const handleBroadcastJobStatusChange = () => {
    broadcastJobCurrentPage.value = 1
    loadBroadcastJobs()
}

const handleBroadcastJobSizeChange = (val: number) => {
    broadcastJobPageSize.value = val
    broadcastJobCurrentPage.value = 1
    loadBroadcastJobs()
}

const handleBroadcastJobCurrentChange = (val: number) => {
    broadcastJobCurrentPage.value = val
    loadBroadcastJobs()
}

const handleViewBroadcastJob = async (job: NotificationBroadcastJob) => {
    const requestId = ++detailRequestId.value
    currentBroadcastJob.value = job
    broadcastJobDetailVisible.value = true
    broadcastJobDetailLoading.value = true

    try {
        const jobDetail = await getNotificationBroadcastJob(job.jobId)
        if (requestId !== detailRequestId.value) return
        currentBroadcastJob.value = jobDetail
    } catch (error) {
        if (requestId !== detailRequestId.value) return
        console.error('获取广播任务详情失败:', error)
        ElMessage.error('获取广播任务详情失败')
    } finally {
        if (requestId === detailRequestId.value) {
            broadcastJobDetailLoading.value = false
        }
    }
}

const markAsRead = async (notificationId: number) => {
    try {
        await markNotificationAsRead(notificationId)
        // 更新本地状态
        const notification = notifications.value.find(n => n.id === notificationId)
        if (notification) {
            if (!notification.read) {
                notification.read = true
                unreadCount.value = Math.max(0, unreadCount.value - 1)
            }
        }
        ElMessage.success('已标记为已读')
    } catch (error) {
        console.error('标记已读失败:', error)
        ElMessage.error('标记已读失败')
    }
}

const batchMarkAsRead = async () => {
    const unreadIds = selectedNotifications.value
        .filter(notification => !notification.read)
        .map(notification => notification.id)

    if (unreadIds.length === 0) {
        ElMessage.warning('请选择未读通知')
        return
    }

    try {
        const response = await markMultipleNotificationsAsRead(unreadIds)
        const markedCount = response.markedCount ?? unreadIds.length
        notifications.value.forEach(notification => {
            if (unreadIds.includes(notification.id)) {
                notification.read = true
            }
        })
        unreadCount.value = Math.max(0, unreadCount.value - markedCount)
        selectedNotificationIds.value = []
        ElMessage.success(`已批量标记 ${markedCount} 条通知为已读`)
    } catch (error) {
        console.error('批量标记已读失败:', error)
        ElMessage.error('批量标记已读失败')
    }
}

const markAllAsRead = async () => {
    if (unreadCount.value === 0) return

    try {
        await ElMessageBox.confirm('确认将所有通知标记为已读？', '确认操作', {
            confirmButtonText: '确认',
            cancelButtonText: '取消',
            type: 'info'
        })

        await markAllNotificationsAsRead()

        // 更新本地状态
        notifications.value.forEach(n => n.read = true)
        unreadCount.value = 0

        ElMessage.success('已全部标记为已读')
    } catch (error) {
        if (error !== 'cancel') {
            console.error('标记全部已读失败:', error)
            ElMessage.error('标记全部已读失败')
        }
    }
}

const deleteNotification = async (notificationId: number) => {
    try {
        await ElMessageBox.confirm('确认删除该通知？', '确认删除', {
            confirmButtonText: '确认',
            cancelButtonText: '取消',
            type: 'warning'
        })

        await removeNotification(notificationId)

        // 更新本地状态
        const index = notifications.value.findIndex(n => n.id === notificationId)
        if (index !== -1) {
            const notification = notifications.value[index]
            if (!notification.read) {
                unreadCount.value = Math.max(0, unreadCount.value - 1)
            }
            notifications.value.splice(index, 1)
            total.value = Math.max(0, total.value - 1)
        }

        ElMessage.success('删除成功')
    } catch (error) {
        if (error !== 'cancel') {
            console.error('删除通知失败:', error)
            ElMessage.error('删除通知失败')
        }
    }
}

const batchDeleteNotifications = async () => {
    const ids = [...selectedNotificationIds.value]
    if (ids.length === 0) {
        ElMessage.warning('请先选择通知')
        return
    }

    try {
        await ElMessageBox.confirm(`确认删除选中的 ${ids.length} 条通知？`, '批量删除', {
            confirmButtonText: '确认',
            cancelButtonText: '取消',
            type: 'warning'
        })

        const deletedUnreadCount = selectedNotifications.value.filter(notification => !notification.read).length
        const response = await removeMultipleNotifications(ids)
        const deletedCount = response.deletedCount ?? ids.length

        notifications.value = notifications.value.filter(notification => !ids.includes(notification.id))
        unreadCount.value = Math.max(0, unreadCount.value - deletedUnreadCount)
        total.value = Math.max(0, total.value - deletedCount)
        selectedNotificationIds.value = []

        ElMessage.success(`已批量删除 ${deletedCount} 条通知`)
    } catch (error) {
        if (error !== 'cancel') {
            console.error('批量删除通知失败:', error)
            ElMessage.error('批量删除通知失败')
        }
    }
}

const handleNotificationClick = async (notification: NotificationDto) => {
    // 如果未读，先标记为已读
    if (!notification.read) {
        try {
            await markAsRead(notification.id)
        } catch {
            // 标记失败不阻塞导航
        }
    }

    // 根据通知类型和实体类型进行导航
    if (notification.entityType === 'HOMESTAY' && notification.entityId) {
        switch (notification.type) {
            case 'HOMESTAY_SUBMITTED':
            case 'HOMESTAY_RESUBMITTED':
            case 'HOMESTAY_UNDER_REVIEW':
                // 提交相关通知跳转到审核工作台
                router.push('/audit/workbench')
                break
            case 'HOMESTAY_APPROVED':
            case 'HOMESTAY_REJECTED':
                // 审核结果通知跳转到审核历史
                router.push('/audit/history')
                break
            case 'REVIEWER_ASSIGNED':
                // 审核员分配通知跳转到工作台
                router.push('/audit/workbench')
                break
            default:
                // 其他房源相关通知跳转到房源列表
                router.push('/homestays')
        }
    } else if (notification.type === 'SYSTEM_ANNOUNCEMENT' || notification.type === 'WELCOME_MESSAGE') {
        // 系统公告类通知不进行跳转，只标记已读
        ElMessage.info('已查看通知')
    } else {
        // 其他类型通知的默认处理
        ElMessage.info('通知已查看')
    }
}

// 工具方法
const getNotificationIcon = (type: string) => {
    const iconMap: Record<string, any> = {
        'HOMESTAY_SUBMITTED': Document,
        'HOMESTAY_APPROVED': CircleCheck,
        'HOMESTAY_REJECTED': Warning,
        'HOMESTAY_RESUBMITTED': Document,
        'REVIEWER_ASSIGNED': InfoFilled,
        'HOMESTAY_UNDER_REVIEW': House,
        'SYSTEM_ANNOUNCEMENT': Bell,
        'WELCOME_MESSAGE': Bell
    }
    return iconMap[type] || Bell
}

const getNotificationIconClass = (type: string) => {
    const classMap: Record<string, string> = {
        'HOMESTAY_SUBMITTED': 'icon-primary',
        'HOMESTAY_APPROVED': 'icon-success',
        'HOMESTAY_REJECTED': 'icon-danger',
        'HOMESTAY_RESUBMITTED': 'icon-warning',
        'REVIEWER_ASSIGNED': 'icon-info',
        'HOMESTAY_UNDER_REVIEW': 'icon-primary',
        'SYSTEM_ANNOUNCEMENT': 'icon-primary',
        'WELCOME_MESSAGE': 'icon-success'
    }
    return classMap[type] || 'icon-primary'
}

const formatBroadcastJobStatus = (status: string) => {
    const statusMap: Record<string, string> = {
        PENDING: '待执行',
        RUNNING: '执行中',
        SUCCEEDED: '已成功',
        FAILED: '已失败',
        RATE_LIMITED: '已限流'
    }
    return statusMap[status] || status
}

const getBroadcastJobStatusTagType = (status: string): 'success' | 'info' | 'warning' | 'danger' => {
    const typeMap: Record<string, 'success' | 'info' | 'warning' | 'danger'> = {
        PENDING: 'info',
        RUNNING: 'warning',
        SUCCEEDED: 'success',
        FAILED: 'danger',
        RATE_LIMITED: 'warning'
    }
    return typeMap[status] || 'info'
}

const formatDateTime = (dateTime?: string | null) => {
    if (!dateTime) return '-'

    const time = new Date(dateTime)
    if (Number.isNaN(time.getTime())) {
        return dateTime
    }

    return time.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    })
}

const formatTime = (dateTime: string) => {
    if (!dateTime || Number.isNaN(new Date(dateTime).getTime())) return '-'
    const now = new Date()
    const time = new Date(dateTime)
    const diff = now.getTime() - time.getTime()

    if (diff < 60 * 1000) {
        return '刚刚'
    } else if (diff < 60 * 60 * 1000) {
        return `${Math.floor(diff / (60 * 1000))}分钟前`
    } else if (diff < 24 * 60 * 60 * 1000) {
        return `${Math.floor(diff / (60 * 60 * 1000))}小时前`
    } else if (diff < 7 * 24 * 60 * 60 * 1000) {
        return `${Math.floor(diff / (24 * 60 * 60 * 1000))}天前`
    } else {
        return time.toLocaleDateString('zh-CN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        })
    }
}

// 初始化
onMounted(() => {
    loadNotificationTypes()
    loadNotifications()
    loadUnreadCount()
    loadBroadcastJobs()
})
</script>

<style scoped lang="scss">
.notifications-page {
    padding: 20px;
    background-color: #f5f7fa;
    min-height: 100vh;

    .page-header {
        margin-bottom: 20px;

        .header-content {
            display: flex;
            justify-content: space-between;
            align-items: center;

            .header-left {
                h2 {
                    margin: 0 0 8px 0;
                    color: #303133;
                    font-size: 24px;
                    font-weight: 600;
                }

                p {
                    margin: 0;
                    color: #909399;
                    font-size: 14px;
                }
            }

            .header-right {
                display: flex;
                align-items: center;
                gap: 12px;

                .badge {
                    .el-button {
                        padding: 8px 16px;
                    }
                }
            }
        }
    }

    .filter-section {
        margin-bottom: 20px;
    }

    .broadcast-history {
        margin-top: 20px;

        .section-header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 16px;

            h3 {
                margin: 0;
                color: #303133;
                font-size: 18px;
                font-weight: 600;
            }

            .section-actions {
                display: flex;
                align-items: center;
                gap: 12px;
            }
        }

        .pagination {
            margin-top: 20px;
            display: flex;
            justify-content: center;
        }
    }

    .description-text {
        white-space: pre-wrap;
        word-break: break-word;
    }

    .notifications-list {
        .batch-toolbar {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 16px;
            padding: 0 0 16px 0;
            border-bottom: 1px solid #ebeef5;
            margin-bottom: 4px;

            .batch-left,
            .batch-actions {
                display: flex;
                align-items: center;
                gap: 12px;
            }

            .selected-count {
                color: #606266;
                font-size: 13px;
            }
        }

        .empty-state {
            padding: 60px 0;
            text-align: center;
        }

        .notification-items {
            .notification-item {
                display: flex;
                align-items: flex-start;
                padding: 16px;
                border-bottom: 1px solid #ebeef5;
                cursor: pointer;
                transition: all 0.3s ease;

                &:hover {
                    background-color: #f5f7fa;
                }

                &.unread {
                    background-color: #f0f9ff;
                    border-left: 4px solid var(--el-color-primary);

                    .notification-title {
                        font-weight: 600;
                    }
                }

                .notification-icon {
                    margin-right: 16px;
                    padding-top: 4px;

                    .el-icon {
                        font-size: 24px;

                        &.icon-primary {
                            color: var(--el-color-primary);
                        }

                        &.icon-success {
                            color: #67c23a;
                        }

                        &.icon-warning {
                            color: #e6a23c;
                        }

                        &.icon-danger {
                            color: #f56c6c;
                        }

                        &.icon-info {
                            color: #909399;
                        }
                    }
                }

                .notification-select {
                    flex: 0 0 28px;
                    padding-top: 4px;
                }

                .notification-content {
                    flex: 1;
                    min-width: 0;

                    .notification-header {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        margin-bottom: 8px;

                        .notification-title {
                            margin: 0;
                            font-size: 16px;
                            color: #303133;
                            font-weight: 500;
                        }

                        .notification-time {
                            font-size: 12px;
                            color: #909399;
                            white-space: nowrap;
                        }
                    }

                    .notification-message {
                        margin: 0 0 8px 0;
                        font-size: 14px;
                        color: #606266;
                        line-height: 1.6;
                        word-break: break-word;
                    }

                    .notification-meta {
                        .el-tag {
                            margin-right: 8px;
                        }
                    }
                }

                .notification-actions {
                    margin-left: 16px;
                    display: flex;
                    flex-direction: column;
                    gap: 4px;
                    align-items: flex-end;

                    .el-button {
                        margin: 0;
                        padding: 4px 8px;
                        font-size: 12px;
                    }
                }
            }
        }

        .pagination {
            margin-top: 20px;
            display: flex;
            justify-content: center;
        }
    }
}
</style>
