<template>
    <div class="my-orders-container">
        <div class="page-header">
            <h1>我的订单</h1>
        </div>

        <div class="filters-bar">
            <div class="status-tabs">
                <el-tabs v-model="activeTab" @tab-change="handleTabChange">
                    <el-tab-pane label="全部订单" name="all"></el-tab-pane>
                    <el-tab-pane label="待确认" name="PENDING">
                        <template #label>
                            <span>待确认</span>
                            <el-badge v-if="statusCounts.PENDING > 0" :value="statusCounts.PENDING" class="tab-badge" />
                        </template>
                    </el-tab-pane>
                    <el-tab-pane label="待支付" name="NEED_PAYMENT">
                        <template #label>
                            <span>待支付</span>
                            <el-badge v-if="statusCounts.NEED_PAYMENT > 0" :value="statusCounts.NEED_PAYMENT"
                                class="tab-badge" />
                        </template>
                    </el-tab-pane>
                    <el-tab-pane label="进行中" name="IN_PROGRESS">
                        <template #label>
                            <span>进行中</span>
                            <el-badge v-if="statusCounts.IN_PROGRESS > 0" :value="statusCounts.IN_PROGRESS"
                                class="tab-badge" />
                        </template>
                    </el-tab-pane>
                    <el-tab-pane label="已完成" name="COMPLETED">
                        <template #label>
                            <span>已完成</span>
                            <el-badge v-if="statusCounts.COMPLETED > 0" :value="statusCounts.COMPLETED"
                                class="tab-badge" />
                        </template>
                    </el-tab-pane>
                    <el-tab-pane label="已取消" name="CANCELLED">
                        <template #label>
                            <span>已取消</span>
                            <el-badge v-if="statusCounts.CANCELLED > 0" :value="statusCounts.CANCELLED"
                                class="tab-badge" />
                        </template>
                    </el-tab-pane>
                    <el-tab-pane label="退款相关" name="REFUND_RELATED">
                        <template #label>
                            <span>退款相关</span>
                            <el-badge v-if="statusCounts.REFUND_RELATED > 0" :value="statusCounts.REFUND_RELATED"
                                class="tab-badge" />
                        </template>
                    </el-tab-pane>
                </el-tabs>
            </div>
        </div>

        <div class="order-list-section">
            <div v-if="loading" class="loading-container">
                <el-skeleton :rows="5" animated />
            </div>

            <div v-else-if="!orders.length" class="empty-container">
                <el-empty description="没有找到订单">
                    <template #description>
                        <p>{{ getEmptyDescription() }}</p>
                    </template>
                    <el-button type="primary" @click="goToHomepage">查找房源</el-button>
                </el-empty>
            </div>

            <template v-else>
                <div v-for="order in orders" :key="order.id" class="order-card">
                        <!-- 左图 -->
                        <div class="order-image-col" :class="{ 'skeleton': !isImageLoaded(order.id) }">
                            <img :src="processImageUrl(order.imageUrl)" :alt="order.homestayTitle"
                                loading="lazy"
                                @load="onImageLoad(order.id)"
                                @error="onImageError($event, order.id)"
                                v-show="isImageLoaded(order.id)">
                        </div>

                        <!-- 中间信息 -->
                        <div class="order-info-col">
                            <div class="order-title-row">
                                <h3 class="order-title" :title="order.homestayTitle">{{ order.homestayTitle }}</h3>
                                <el-tag effect="light" :type="getStatusType(order)" class="status-tag">
                                    {{ getStatusText(order) }}
                                </el-tag>
                            </div>

                            <div class="order-meta-info">
                                <p class="date-info"><i class="el-icon-date"></i> {{ formatDateRange(order.checkInDate, order.checkOutDate) }} · {{ order.nights }}晚</p>
                                <p class="guest-info"><i class="el-icon-user"></i> {{ order.guestCount }}位房客</p>
                                <p class="host-info"><i class="el-icon-s-custom"></i> 房东: {{ order.hostName || '未知' }}</p>

                                <p class="order-number-text">
                                    订单号: {{ order.orderNumber }} <span class="divider">|</span> 下单时间: {{ formatDate(order.createTime) }}
                                </p>
                            </div>

                            <!-- 暂存倒计时 -->
                            <div v-if="order.status === 'PENDING'" class="order-countdown warning mt-2">
                                <el-tooltip :content="`超过${pendingTimeoutHours}小时未确认的订单将被自动取消`">
                                    <span><i class="el-icon-time"></i> 确认剩余: {{ getCountdownTime(order.createTime, pendingTimeoutHours) }}</span>
                                </el-tooltip>
                            </div>
                            <div v-if="order.status === 'CONFIRMED'" class="order-countdown danger mt-2">
                                <el-tooltip :content="`超过${confirmedTimeoutHours}小时未支付的订单将被自动取消`">
                                    <span><i class="el-icon-time"></i> 支付剩余: {{ getCountdownTime(order.updateTime, confirmedTimeoutHours) }}</span>
                                </el-tooltip>
                            </div>
                        </div>

                        <!-- 右侧操作 -->
                        <div class="order-action-col">
                            <div class="price-display">
                                <span class="price-currency">¥</span>
                                <span class="price-amount">{{ order.totalAmount }}</span>
                            </div>

                            <div class="action-buttons-vertical">
                            <!-- 待支付 (已确认 & 未支付 或 支付中状态) -->
                            <template
                                v-if="(order.status === 'CONFIRMED' && order.paymentStatus === 'UNPAID') || order.status === 'PAYMENT_PENDING'">
                                <el-button type="default" size="small"
                                    @click="viewOrderDetail(order.id)">查看详情</el-button>
                                <el-button type="danger" plain size="small"
                                    @click="cancelOrder(order.id)">取消订单</el-button>
                                <el-button type="primary" size="small" @click="goToPayment(order.id)">立即支付</el-button>
                                <!-- 保留支付提示，或根据需要调整 -->
                                <div v-if="order.status === 'CONFIRMED' || order.status === 'PAYMENT_PENDING'"
                                    class="payment-reminder danger small-text">
                                    <el-tooltip :content="`超过${confirmedTimeoutHours}小时未支付的订单将被自动取消`">
                                        <span><i class="el-icon-time"></i> 支付剩余: {{ getCountdownTime(order.updateTime ||
                                            order.createTime,
                                            confirmedTimeoutHours)
                                        }}</span>
                                    </el-tooltip>
                                </div>
                            </template>

                            <!-- 退款被拒绝（paymentStatus=PAID 但有拒绝原因）-->
                            <template
                                v-else-if="order.paymentStatus === 'PAID' && order.refundRejectionReason">
                                <div class="refund-rejected-banner">
                                    <span class="rejected-icon">❌</span>
                                    <span>退款被拒绝：{{ order.refundRejectionReason }}</span>
                                </div>
                                <el-button type="primary" size="small">联系房东</el-button>
                                <el-button type="info" text bg size="small"
                                    @click="requestRefund(order.id)">再次申请退款</el-button>
                                <el-button type="default" size="small"
                                    @click="viewOrderDetail(order.id)">查看详情</el-button>
                            </template>

                            <!-- 已支付 (且未完成/未取消) -->
                            <template
                                v-else-if="order.paymentStatus === 'PAID' && order.status !== 'COMPLETED' && order.status !== 'CANCELLED'">
                                <el-button type="primary" size="small">联系房东</el-button>
                                <el-button type="info" text bg size="small"
                                    @click="requestRefund(order.id)">申请退款</el-button>
                                <el-button type="default" size="small"
                                    @click="viewOrderDetail(order.id)">查看详情</el-button>
                            </template>

                            <!-- 新增：支付失败 -->
                            <template
                                v-else-if="order.status === 'PAYMENT_FAILED' || order.paymentStatus === 'PAYMENT_FAILED'">
                                <el-button type="default" size="small"
                                    @click="viewOrderDetail(order.id)">查看详情</el-button>
                                <el-button type="warning" plain size="small"
                                    @click="goToPayment(order.id)">重新支付</el-button>
                                <el-button type="danger" plain size="small"
                                    @click="cancelOrder(order.id)">取消订单</el-button>
                            </template>

                            <!-- 待确认 -->
                            <template v-else-if="order.status === 'PENDING'">
                                <el-button type="default" size="small"
                                    @click="viewOrderDetail(order.id)">查看详情</el-button>
                                <el-button type="danger" plain size="small"
                                    @click="cancelOrder(order.id)">取消订单</el-button>
                                <!-- 保留确认提示 -->
                                <div v-if="order.status === 'PENDING'" class="payment-reminder warning small-text">
                                    <el-tooltip :content="`超过${pendingTimeoutHours}小时未确认的订单将被自动取消`">
                                        <span><i class="el-icon-time"></i> 确认剩余: {{ getCountdownTime(order.createTime,
                                            pendingTimeoutHours)
                                        }}</span>
                                    </el-tooltip>
                                </div>
                            </template>

                            <!-- 已完成 / 已取消 / 其他最终状态 -->
                            <template v-else>
                                <el-button type="default" size="small"
                                    @click="viewOrderDetail(order.id)">查看详情</el-button>
                                <!-- 添加评价按钮，仅在订单完成、未评价且在时间窗口内时显示 -->
                                <el-button v-if="order.status === 'COMPLETED' && !order.reviewed && isWithinReviewWindow(order)" type="primary" plain
                                    size="small" @click="openReviewModal(order)">
                                    评价订单
                                </el-button>
                                <el-tooltip v-else-if="order.status === 'COMPLETED' && !order.reviewed && !isWithinReviewWindow(order)" content="订单已完成超过30天，无法提交评价">
                                    <el-button type="info" plain size="small" disabled>评价已过期</el-button>
                                </el-tooltip>
                                <!-- 可以添加一个已评价的提示或按钮 -->
                                <el-button v-else-if="order.status === 'COMPLETED' && order.reviewed" type="info" plain
                                    size="small" disabled>
                                    已评价
                                </el-button>
                            </template>
                        </div>
                    </div>
                </div>

                <div class="pagination-container">
                    <el-pagination background layout="prev, pager, next" :total="paginationTotal" :page-size="pageSize"
                        :current-page="currentPage" @current-change="handlePageChange" />
                </div>
            </template>
        </div>

        <!-- 评价模态框 -->
        <ReviewForm v-if="currentReviewOrder" v-model:visible="reviewDialogVisible" :order-id="currentReviewOrder.id"
            :homestay-id="currentReviewOrder.homestayId" :homestay-title="currentReviewOrder.homestayTitle"
            @submit="handleSubmitReview" />


    </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelOrder as apiCancelOrder } from '@/api/order'
import { submitReview } from '@/api/review'
import { requestRefund as apiRequestRefund } from '@/api/refund'
import ReviewForm from '@/components/ReviewForm.vue'
import { getHomestayImageUrl, handleImageError } from '@/utils/image'
import { type OrderStatus, type PaymentStatus } from '@/types/index'
import { useOrderStore, type OrderItem } from '@/stores/order'
import dayjs from 'dayjs'

const router = useRouter()
const store = useOrderStore()

const currentPage = ref(1)
const pageSize = ref(10)
const activeTab = ref('all')

// --- 评价模态框状态 ---
const reviewDialogVisible = ref(false);
const currentReviewOrder = ref<OrderItem | null>(null);

// 从 Store 读取状态
const loading = computed(() => store.loading)
const orders = computed(() => store.orders)
const statusCounts = computed(() => store.statusCounts)

// 超时配置（从 Store 动态读取，fallback 2h）
const pendingTimeoutHours = computed(() => store.timeoutConfig?.pendingTimeoutHours ?? 2)
const confirmedTimeoutHours = computed(() => store.timeoutConfig?.confirmedTimeoutHours ?? 2)

// 图片加载状态跟踪（用于骨架屏）
const loadedImages = ref<Set<number>>(new Set())
const isImageLoaded = (orderId: number) => loadedImages.value.has(orderId)
const onImageLoad = (orderId: number) => loadedImages.value.add(orderId)
const onImageError = (event: Event, orderId: number) => {
    loadedImages.value.add(orderId)
    handleImageError(event, 'homestay')
}

// 当前筛选状态下的总数（后端分页返回）
const paginationTotal = computed(() => store.pagination.total)



// 获取订单列表
const fetchOrders = async () => {
    await store.fetchOrders({
        page: currentPage.value - 1,
        size: pageSize.value,
        tab: activeTab.value === 'all' ? undefined : activeTab.value
    })
    // 切换页码/Tab 后重置图片加载状态
    loadedImages.value.clear()
}

// 处理图片URL
const processImageUrl = (url?: string) => {
    return getHomestayImageUrl(url);
}

// 处理标签页切换
const handleTabChange = () => {
    currentPage.value = 1
    // Tab 切换时重新获取数据（后续可配合后端 status 参数做精确分页）
    fetchOrders()
}

// 处理分页变化
const handlePageChange = (page: number) => {
    currentPage.value = page
    fetchOrders()
}

// 取消订单
const cancelOrder = async (orderId: number) => {
    try {
        await ElMessageBox.confirm(
            '确定要取消该订单吗？取消后将无法恢复',
            '取消订单',
            {
                confirmButtonText: '确定取消',
                cancelButtonText: '再想想',
                type: 'warning',
                distinguishCancelAndClose: true,
                closeOnClickModal: false
            }
        )

        // 显示取消中的加载状态
        ElMessage({
            type: 'info',
            message: '订单取消中...',
            duration: 0
        })

        try {
            const response = await apiCancelOrder(orderId)
            console.log('取消订单响应:', response)

            // 关闭加载消息
            ElMessage.closeAll()

            // 显示更详细的成功消息，并提供跳转链接
            ElMessageBox.alert(
                '订单已成功取消，您可以继续浏览其他房源',
                '取消成功',
                {
                    confirmButtonText: '查看其他房源',
                    callback: (action: string) => {
                        if (action === 'confirm') {
                            router.push('/')
                        } else {
                            // 刷新当前页面的订单列表
                            fetchOrders()
                            store.fetchStatsOrders()
                        }
                    }
                }
            )
        } catch (apiError: any) {
            // 关闭加载消息
            ElMessage.closeAll()

            // 显示具体错误信息
            if (apiError.response && apiError.response.data && apiError.response.data.error) {
            } else {
            }
            console.error('取消订单API错误:', apiError)
        }
    } catch (error: any) {
        // 用户取消了确认对话框，不做处理
        if (error !== 'cancel' && error !== 'close') {
            console.error('取消订单确认框错误:', error)
        }
    }
}

// 查看订单详情
const viewOrderDetail = (orderId: number) => {
    router.push(`/orders/${orderId}`)
}

// 申请退款
const requestRefund = async (orderId: number) => {
    try {
        const result = await ElMessageBox.prompt(
            '请说明退款原因（必填）',
            '申请退款',
            {
                confirmButtonText: '提交申请',
                cancelButtonText: '取消',
                inputPattern: /\S/,
                inputErrorMessage: '退款原因不能为空',
                inputPlaceholder: '请输入退款原因...',
                type: 'warning'
            }
        )

        if (!result.value) {
            ElMessage.warning('退款原因不能为空')
            return
        }

        ElMessage({
            type: 'info',
            message: '正在提交退款申请...',
            duration: 0
        })

        try {
            await apiRequestRefund(orderId, result.value.trim())
            ElMessage.closeAll()
            ElMessage.success('退款申请已提交，请耐心等待处理')
            fetchOrders() // 刷新订单列表
            store.fetchStatsOrders() // 刷新统计
        } catch (error: any) {
            ElMessage.closeAll()
            console.error('申请退款失败:', error)
        }
    } catch (error) {
        // 用户取消了对话框
        if (error !== 'cancel') {
            console.error('退款申请确认框错误:', error)
        }
    }
}

// 前往支付页面 (恢复原始逻辑)
const goToPayment = (orderId: number) => {
    router.push(`/orders/${orderId}/pay`)
}

// 前往首页
const goToHomepage = () => {
    router.push('/')
}

// 获取不同状态下的空订单描述
const getEmptyDescription = () => {
    switch (activeTab.value) {
        case 'PENDING':
            return '暂无待确认的订单'
        case 'NEED_PAYMENT':
            return '暂无待支付的订单'
        case 'IN_PROGRESS':
            return '暂无进行中的订单'
        case 'COMPLETED':
            return '暂无已完成的订单'
        case 'CANCELLED':
            return '暂无已取消的订单'
        case 'REFUND_RELATED':
            return '暂无退款相关的订单'
        default:
            return '暂无订单记录，快去预订心仪的民宿吧！'
    }
}

// 获取订单状态对应的样式类型 (更正逻辑)
const getStatusType = (order: OrderItem): string => {
    const status = order.status as OrderStatus;
    const paymentStatus = order.paymentStatus as PaymentStatus;

    // 优先处理最终/关键状态
    if (status?.startsWith('CANCELLED') || status === 'REJECTED') return 'danger';
    if (paymentStatus === 'REFUNDED') return 'warning'; // 已退款用 warning
    if (status === 'COMPLETED') return 'info'; // 已完成用 info
    if (status === 'PAYMENT_FAILED' || paymentStatus === 'PAYMENT_FAILED') return 'danger'; // 支付失败用 danger

    // 退款被拒绝（paymentStatus 恢复为 PAID 但有拒绝原因）
    if (paymentStatus === 'PAID' && order.refundRejectionReason) return 'danger';

    // 争议状态
    if (status === 'DISPUTE_PENDING' || status === 'DISPUTED') return 'warning';

    // 争议已解决
    if (order.disputeResolution) return 'info';

    // 处理支付成功状态
    if (paymentStatus === 'PAID') {
        // Add CHECKED_IN check if applicable
        // if (status === 'CHECKED_IN') return 'primary';
        return 'success'; // 其他 PAID 相关状态用 success
    }

    // 处理待支付状态 (已确认但未支付)
    if (status === 'CONFIRMED' && (paymentStatus === 'UNPAID' || paymentStatus === null || paymentStatus === undefined)) {
        return 'warning'; // 待支付用 warning
    }
    // 处理支付处理中状态
    if (status === 'PAYMENT_PENDING') {
        return 'warning'; // 支付处理中也用 warning
    }

    // 处理待确认
    if (status === 'PENDING') return 'info'; // 待确认用 info

    console.warn(`Unhandled order state in getStatusType: status=${status}, paymentStatus=${order.paymentStatus}`);
    return 'info'; // Fallback
}

// 获取订单状态文本 (更正逻辑)
const getStatusText = (order: OrderItem): string => {
    const status = order.status as OrderStatus;
    const paymentStatus = order.paymentStatus as PaymentStatus;

    // 优先处理退款相关状态
    if (paymentStatus === 'REFUND_PENDING') {
        const refundTypeText = getRefundTypeText(order.refundType);
        return `退款中${refundTypeText}`;
    }
    if (paymentStatus === 'REFUNDED') {
        const refundTypeText = getRefundTypeText(order.refundType);
        return `已退款${refundTypeText}`;
    }
    if (paymentStatus === 'REFUND_FAILED') {
        const refundTypeText = getRefundTypeText(order.refundType);
        return `退款失败${refundTypeText}`;
    }

    // 退款被拒绝（paymentStatus 恢复为 PAID 但有拒绝原因）
    if (paymentStatus === 'PAID' && order.refundRejectionReason) return '退款被拒绝';

    // 争议状态
    if (status === 'DISPUTE_PENDING') return '争议待处理';
    if (status === 'DISPUTED') return '争议处理中';

    // 争议已解决（显示解决结果）
    if (order.disputeResolution) {
        if (order.disputeResolution === 'APPROVED') return '争议已解决（退款）';
        if (order.disputeResolution === 'REJECTED') return '争议已解决（拒绝退款）';
    }

    // 优先处理最终/关键状态
    if (status?.startsWith('CANCELLED')) return '已取消';
    if (status === 'REJECTED') return '已拒绝';
    if (status === 'COMPLETED') return '已完成';
    if (status === 'PAYMENT_FAILED' || paymentStatus === 'PAYMENT_FAILED') return '支付失败'; // 支付失败文本

    // 处理支付成功状态驱动的状态
    if (paymentStatus === 'PAID') {
        // Add CHECKED_IN check if applicable
        // if (status === 'CHECKED_IN') return '已入住';
        return '已支付'; // 明确是支付状态驱动
    }

    // 处理待支付
    if (status === 'CONFIRMED' && (paymentStatus === 'UNPAID' || paymentStatus === null || paymentStatus === undefined)) {
        return '待支付';
    }
    // 处理支付处理中
    if (status === 'PAYMENT_PENDING') {
        return '待支付'; // 支付处理中也显示待支付
    }

    // 处理待确认
    if (status === 'PENDING') {
        return '待确认';
    }

    // Fallback
    console.warn(`Unhandled order state in getStatusText: status=${status}, paymentStatus=${order.paymentStatus}`);
    return '未知状态';
}

// 获取退款类型文本
const getRefundTypeText = (refundType?: string): string => {
    if (!refundType) return '';

    switch (refundType) {
        case 'USER_REQUESTED':
            return '（用户申请）';
        case 'HOST_CANCELLED':
            return '（房东取消）';
        case 'ADMIN_INITIATED':
            return '（管理员发起）';
        case 'SYSTEM_AUTOMATIC':
            return '（系统自动）';
        default:
            return '';
    }
}

// 格式化日期
const formatDate = (dateString: string) => {
    if (!dateString) return ''
    const date = new Date(dateString)
    return date.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

// 格式化日期范围
const formatDateRange = (checkInDate: string, checkOutDate: string) => {
    if (!checkInDate || !checkOutDate) return ''

    const formatDate = (dateString: string) => {
        const date = new Date(dateString)
        return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
    }

    return `${formatDate(checkInDate)} - ${formatDate(checkOutDate)}`
}

// 计算倒计时
const getCountdownTime = (startTimeStr: string, hoursLimit: number) => {
    const startTime = new Date(startTimeStr).getTime();
    const currentTime = new Date().getTime();
    const limitTime = startTime + hoursLimit * 60 * 60 * 1000;
    const remainingTime = limitTime - currentTime;

    if (remainingTime <= 0) {
        return "即将自动取消";
    }

    // 计算剩余小时和分钟
    const hours = Math.floor(remainingTime / (60 * 60 * 1000));
    const minutes = Math.floor((remainingTime % (60 * 60 * 1000)) / (60 * 1000));

    return `${hours}小时${minutes}分钟`;
}

// 判断订单是否在评价时间窗口内（30天）
const isWithinReviewWindow = (order: OrderItem): boolean => {
    if (!order.completedAt) return true;
    return dayjs().diff(dayjs(order.completedAt), 'day') <= 30;
};

// --- 评价相关方法 ---
const openReviewModal = (order: OrderItem) => {
    console.log('Opening review modal for order:', order);
    currentReviewOrder.value = order;
    reviewDialogVisible.value = true;
};

const handleSubmitReview = async (reviewData: any) => {
    console.log('Received review data from form:', reviewData);
    if (!currentReviewOrder.value) return;

    // 确认 reviewData 中包含所有需要的信息，特别是 orderId
    // 我们的 ReviewForm 应该已经包含了 orderId 和 homestayId
    // const payload = { ...reviewData }; 

    try {
        const response = await submitReview(reviewData); // 直接传递表单数据
        console.log('Submit review response:', response);
        ElMessage.success('评价提交成功！');
        reviewDialogVisible.value = false;
        // 评价成功后可以刷新订单列表，或者更新按钮状态（如果需要）
        fetchOrders(); // 重新获取订单，如果后端会标记订单已评价
    } catch (error: any) {
        console.error('提交评价失败:', error);
        // 不关闭弹窗，让用户可以修改
    }
};

// 初始化
onMounted(() => {
    if (!store.timeoutConfig) {
        store.fetchTimeoutConfig()
    }
    fetchOrders()
    store.fetchStatsOrders() // 额外拉取数据用于 badge 统计
})
</script>

<style scoped>
.my-orders-container {
    width: 100%;
}

.page-header {
    margin-bottom: 24px;
}

.page-header h1 {
    font-size: 28px;
    font-weight: 600;
    margin: 0;
    color: #333;
    position: relative;
    display: inline-block;
}

.page-header h1::after {
    content: '';
    position: absolute;
    bottom: -8px;
    left: 0;
    width: 40px;
    height: 4px;
    background-color: #409EFF;
    border-radius: 2px;
}

.filters-bar {
    margin-bottom: 24px;
    background-color: white;
    border-radius: 8px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
    border: 1px solid #ebeef5;
    padding: 12px 20px 0 20px;
}

.filters-bar :deep(.el-tabs__header) {
    margin-bottom: 0;
}

.filters-bar :deep(.el-tabs__nav-wrap::after) {
    height: 1px;
    background-color: transparent;
}

.loading-container,
.empty-container {
    padding: 60px;
    background-color: white;
    border-radius: 8px;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
    margin-bottom: 24px;
    text-align: center;
}

.order-card {
    background-color: white;
    border-radius: 12px;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
    margin-bottom: 24px;
    overflow: hidden;
    transition: transform 0.3s ease, box-shadow 0.3s ease;
    border: none;
    display: flex;
    padding: 24px;
    gap: 24px;
}

.order-card:hover {
    transform: translateY(-5px);
    box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08);
}

.order-image-col {
    width: 200px;
    height: 140px;
    border-radius: 8px;
    overflow: hidden;
    flex-shrink: 0;
    background-color: #f2f2f2;
}

.order-image-col.skeleton {
    background: linear-gradient(90deg, #f2f2f2 25%, #e6e6e6 50%, #f2f2f2 75%);
    background-size: 200% 100%;
    animation: skeleton-shimmer 1.5s infinite;
}

@keyframes skeleton-shimmer {
    0% { background-position: 200% 0; }
    100% { background-position: -200% 0; }
}

.order-image-col img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 0.4s ease;
}

.order-image-col:hover img {
    transform: scale(1.05);
}

.order-info-col {
    flex: 1;
    display: flex;
    flex-direction: column;
}

.order-title-row {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 12px;
}

.order-title {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
    color: #1a1a1a;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

.order-meta-info {
    flex: 1;
}

.date-info,
.guest-info,
.host-info {
    margin: 6px 0;
    color: #909399;
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
}

.order-number-text {
    margin-top: 12px;
    font-size: 13px;
    color: #909399;
}

.order-number-text .divider {
    margin: 0 8px;
    color: #e4e7ed;
}

.order-action-col {
    width: 200px;
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    justify-content: space-between;
    border-left: 1px dashed #ebeef5;
    padding-left: 24px;
    flex-shrink: 0;
}

.price-display {
    text-align: right;
    margin-bottom: 16px;
}

.price-currency {
    font-size: 14px;
    color: #f56c6c;
    font-weight: bold;
    margin-right: 2px;
}

.price-amount {
    font-size: 24px;
    font-weight: 700;
    color: #f56c6c;
}

.action-buttons-vertical {
    display: flex;
    flex-direction: column;
    gap: 10px;
    width: 100%;
}

.action-buttons-vertical :deep(.el-button) {
    width: 100%;
    margin-left: 0;
}

.order-countdown {
    font-size: 12px;
    color: #606266;
    padding: 4px 8px;
    background-color: #f8f8f8;
    border-radius: 4px;
    display: inline-block;
}

.pagination-container {
    margin-top: 32px;
    display: flex;
    justify-content: center;
}

.order-countdown {
    margin-left: 8px;
    font-size: 12px;
    color: #606266;
    padding: 2px 8px;
    background-color: #f8f8f8;
    border-radius: 4px;
}

.payment-reminder {
    margin-top: 8px;
    font-size: 12px;
    color: #606266;
    font-style: italic;
}

@media (max-width: 768px) {
    .order-card-content {
        flex-direction: column;
    }

    .homestay-image {
        width: 100%;
        height: 200px;
    }

    .order-info {
        flex-direction: column;
        gap: 4px;
    }

    .action-buttons {
        flex-wrap: wrap;
    }
}

/* Add styles for small-text if needed */
.small-text {
    font-size: 12px;
    color: #909399;
    /* Adjust color as needed */
    margin-left: 10px;
}

.payment-reminder.danger {
    color: var(--el-color-danger);
}

.tab-badge {
    margin-left: 6px;
}

.tab-badge :deep(.el-badge__content) {
    background-color: #e6f1fc;
    color: #409eff;
    border: none;
    font-size: 11px;
    font-weight: 600;
    height: 18px;
    line-height: 18px;
    padding: 0 6px;
    border-radius: 9px;
}

.payment-reminder.warning {
    color: var(--el-color-warning);
}

/* 退款被拒绝横幅 */
.refund-rejected-banner {
    display: flex;
    align-items: center;
    gap: 6px;
    width: 100%;
    padding: 8px 12px;
    margin-bottom: 8px;
    background: #fef0f0;
    border: 1px solid #fde2e2;
    border-left: 3px solid #f56c6c;
    border-radius: 4px;
    color: #f56c6c;
    font-size: 13px;
    font-weight: 500;
}

.rejected-icon {
    flex-shrink: 0;
}
</style>