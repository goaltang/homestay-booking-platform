<template>
    <div class="host-order-detail-container">
        <div v-if="loading" class="loading-container">
            <el-skeleton :rows="10" animated />
        </div>
        <div v-else-if="!orderData" class="error-container">
            <el-empty description="订单不存在">
                <template #description>
                    <p>未找到订单信息，请返回订单列表</p>
                </template>
                <el-button type="primary" @click="goToOrders">返回订单列表</el-button>
            </el-empty>
        </div>
        <div v-else class="order-content">
            <div class="order-header">
                <h1>订单详情</h1>
                <div class="order-status">
                    <el-tag :type="getStatusType(orderData)">
                        {{ getStatusText(orderData) }}
                    </el-tag>
                </div>
            </div>

            <!-- 快速操作面板 - 仅待确认订单显示 -->
            <div class="action-panel" v-if="orderData.status === 'PENDING'">
                <div class="action-card">
                    <div class="action-info">
                        <div class="action-title">订单需要您的确认</div>
                        <div class="action-desc">租客已提交预订申请，请尽快确认或拒绝。</div>
                    </div>
                    <div class="action-buttons">
                        <el-button type="danger" @click="showRejectDialog">拒绝预订</el-button>
                        <el-button type="primary" @click="confirmOrder" :loading="submitting">接受预订</el-button>
                    </div>
                </div>
            </div>

            <!-- 订单信息卡片 -->
            <div class="order-summary-card">
                <div class="card-header">
                    <h2>订单信息</h2>
                    <span class="order-number">订单号: {{ orderData.orderNumber }}</span>
                    <span class="order-time" style="margin-left: 15px; color: #909399; font-size: 14px;">下单时间: {{
                        formatDateTime(orderData.createTime) }}</span>
                </div>
                <div class="homestay-info">
                    <div class="homestay-image">
                        <img :src="processImageUrl(orderData.imageUrl)" alt="房源图片" @error="handleImageErrorEvent">
                    </div>
                    <div class="homestay-details">
                        <h3>{{ orderData.homestayTitle }}</h3>
                        <p><i class="el-icon-date"></i> {{ formatDateRange(orderData.checkInDate,
                            orderData.checkOutDate) }}</p>
                        <p><i class="el-icon-user"></i> {{ orderData.guestCount }}位房客 · {{ orderData.nights }}晚</p>
                    </div>
                </div>
                <div class="payment-info-section"
                    style="margin-top: 16px; padding-top: 16px; border-top: 1px solid #eee;">
                    <h2 style="font-size: 16px; margin-bottom: 10px;">支付信息</h2>
                    <div class="info-item">
                        <span class="label">支付状态:</span>
                        <span>{{ orderData.paymentStatus === 'PAID' ? '已支付' : (orderData.paymentStatus === 'UNPAID' ?
                            '未支付' :
                            orderData.paymentStatus) }}</span>
                    </div>
                    <div class="info-item" v-if="orderData.paymentStatus === 'PAID' && orderData.paymentMethod">
                        <span class="label">支付方式:</span>
                        <span>{{ getPaymentMethodText(orderData.paymentMethod) }}</span>
                    </div>
                </div>
            </div>

            <!-- 旅客信息 -->
            <div class="guest-info-section">
                <h2>旅客信息</h2>
                <div class="info-item">
                    <span class="label">联系人:</span>
                    <span>{{ orderData.guestName }}</span>
                </div>
                <div class="info-item">
                    <span class="label">联系电话:</span>
                    <span>{{ orderData.guestPhone }}</span>
                </div>
                <div class="info-item" v-if="orderData.remark && !orderData.remark.includes('拒绝原因')">
                    <span class="label">备注:</span>
                    <span style="white-space: pre-wrap;">{{ orderData.remark }}</span>
                </div>
            </div>

            <!-- 价格详情 -->
            <div class="price-details-section">
                <h2>价格详情</h2>
                <div class="price-item">
                    <span>每晚价格 x {{ orderData.nights }}晚</span>
                    <span>¥{{ (orderData.price * orderData.nights).toFixed(2) }}</span>
                </div>
                <div class="price-total">
                    <span>总价</span>
                    <span>¥{{ orderData.totalAmount.toFixed(2) }}</span>
                </div>
            </div>

            <!-- 拒绝原因 - 如果订单被拒绝 -->
            <div class="reject-reason-section" v-if="orderData.status === 'REJECTED' && orderData.remark">
                <h2>拒绝原因</h2>
                <div class="reject-reason">
                    <p>{{ extractRejectReason(orderData.remark) }}</p>
                </div>
            </div>

            <!-- 订单时间线 -->
            <div class="order-timeline-section">
                <h2>订单状态记录</h2>
                <el-timeline>
                    <el-timeline-item :timestamp="formatDateTime(orderData.createTime)" placement="top" type="primary">
                        <h4>订单创建</h4>
                        <p>租客提交了预订申请</p>
                    </el-timeline-item>
                    <el-timeline-item v-if="orderData.status !== 'PENDING'"
                        :timestamp="formatDateTime(orderData.updateTime)" placement="top"
                        :type="getStatusType(orderData) === 'danger' ? 'danger' : (getStatusType(orderData) === 'warning' ? 'warning' : 'success')">
                        <h4>{{ getStatusChangeTitle(orderData) }}</h4>
                        <p>{{ getStatusChangeDesc(orderData) }}</p>
                    </el-timeline-item>
                </el-timeline>
            </div>

            <!-- 底部操作栏 -->
            <div class="bottom-actions"
                style="margin-top: 30px; padding-top: 20px; border-top: 1px solid #eee; text-align: right;">
                <el-button @click="goToOrders">返回列表</el-button>
                <el-button type="primary" plain
                    v-if="orderData.status !== 'CANCELLED' && orderData.status !== 'REJECTED'">
                    联系租客
                </el-button>
                <el-button
                    v-if="(orderData.status === 'PENDING' || orderData.status === 'CONFIRMED') && orderData.paymentStatus === 'UNPAID'"
                    type="danger" plain @click="handleCancel" :loading="submitting">
                    取消订单
                </el-button>
                <el-button v-if="orderData.status === 'CONFIRMED' && orderData.paymentStatus === 'PAID'" type="primary"
                    @click="handleCheckIn" :loading="submitting">
                    办理入住
                </el-button>
                <el-button
                    v-if="orderData.status === 'CHECKED_IN' || (orderData.paymentStatus === 'PAID' && orderData.status !== 'COMPLETED' && orderData.status !== 'CANCELLED')"
                    type="success" @click="handleComplete" :loading="submitting">
                    完成订单
                </el-button>
            </div>
        </div>

        <!-- 拒绝订单对话框 -->
        <el-dialog v-model="rejectDialogVisible" title="拒绝预订" width="500px">
            <div class="reject-dialog-content">
                <p>请输入拒绝预订的原因，这将帮助租客了解您的决定。</p>
                <el-form :model="rejectForm" :rules="rejectRules" ref="rejectFormRef">
                    <el-form-item prop="reason">
                        <el-input v-model="rejectForm.reason" type="textarea" rows="4"
                            placeholder="例如：该日期我已有其他安排，无法接待..."></el-input>
                    </el-form-item>
                </el-form>
            </div>
            <template #footer>
                <span class="dialog-footer">
                    <el-button @click="rejectDialogVisible = false">取消</el-button>
                    <el-button type="danger" @click="rejectOrder" :loading="submitting">
                        确认拒绝
                    </el-button>
                </span>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOrderDetail } from '@/api/order'
import {
    updateOrderStatus,
    rejectOrder as rejectOrderApi,
    cancelOrder as apiHostCancelOrder
} from '@/api/hostOrder'
import { getHomestayById } from '@/api/homestay'
import { getHomestayImageUrl, handleImageError } from '@/utils/image'
import { type HostOrder } from '@/types/host'
import { type PaymentMethod, type OrderStatus } from '@/types/index'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const submitting = ref(false)
const orderData = ref<HostOrder | null>(null)
const rejectDialogVisible = ref(false)
const rejectFormRef = ref()

const rejectForm = reactive({
    reason: ''
})

const rejectRules = {
    reason: [
        { required: true, message: '请输入拒绝原因', trigger: 'blur' },
        { min: 5, max: 200, message: '拒绝原因长度在5到200个字符之间', trigger: 'blur' }
    ]
}

// 获取订单详情
const fetchOrderDetail = async () => {
    loading.value = true
    try {
        const orderId = Number(route.params.id)
        if (isNaN(orderId)) {
            ElMessage.error('无效的订单ID')
            loading.value = false;
            return
        }

        const response = await getOrderDetail(orderId)
        orderData.value = response.data

        if (orderData.value && orderData.value.homestayId) {
            try {
                const homestayResponse = await getHomestayById(orderData.value.homestayId)
                if (homestayResponse?.data) {
                    const coverImage = homestayResponse.data.coverImage ||
                        (homestayResponse.data.images?.[0]);
                    if (coverImage && orderData.value) {
                        orderData.value.imageUrl = coverImage
                    }
                }
            } catch (error) {
                console.error('获取房源详情失败:', error)
            }
        }
    } catch (error: any) {
        console.error('获取订单详情失败:', error)
        orderData.value = null;
    } finally {
        loading.value = false
    }
}

// 处理图片URL
const processImageUrl = (url?: string) => {
    return getHomestayImageUrl(url);
}

// 处理图片加载错误事件处理器
const handleImageErrorEvent = (event: Event) => {
    handleImageError(event, 'homestay');
}

// 确认订单
const confirmOrder = async () => {
    if (!orderData.value) return
    try {
        await ElMessageBox.confirm(
            '确定接受此预订吗？确认后将通知租客支付订单。',
            '接受预订',
            {
                confirmButtonText: '确认接受',
                cancelButtonText: '取消',
                type: 'success'
            }
        )

        submitting.value = true
        await updateOrderStatus(orderData.value.id, 'CONFIRMED')
        ElMessage.success('已接受预订')
        fetchOrderDetail()
    } catch (error: any) {
        if (error !== 'cancel') {
            console.error('接受预订失败:', error)
        }
    } finally {
        submitting.value = false
    }
}

// 显示拒绝对话框
const showRejectDialog = () => {
    rejectForm.reason = ''
    rejectDialogVisible.value = true
}

// 拒绝订单
const rejectOrder = async () => {
    if (!rejectFormRef.value || !orderData.value) return

    try {
        await rejectFormRef.value.validate()

        submitting.value = true
        await rejectOrderApi(orderData.value.id, rejectForm.reason)

        ElMessage.success('已拒绝预订')
        rejectDialogVisible.value = false
        fetchOrderDetail()
    } catch (error: any) {
        if (error !== false) {
            console.error('拒绝预订失败:', error)
        }
    } finally {
        submitting.value = false
    }
}

// Helper function to map payment method codes to readable names
const getPaymentMethodText = (method?: PaymentMethod): string => {
    if (!method) return '未指定';
    switch (method) {
        case 'ALIPAY': return '支付宝';
        case 'WECHAT_PAY': return '微信支付';
        case 'OTHER': return '其他';
        default: return method;
    }
}

// 获取状态显示的类型
const getStatusType = (order: HostOrder | null): string => {
    if (!order) return 'info';
    const status = order.status as OrderStatus;
    if (status === 'CANCELLED' || status === 'REJECTED') return 'danger';
    if (order.paymentStatus === 'PAID') return 'success';
    if (status === 'CONFIRMED' && order.paymentStatus === 'UNPAID') return 'warning';
    if (status === 'PENDING') return 'warning';
    if (status === 'COMPLETED') return 'info';
    return 'info';
}

// 获取状态显示文本
const getStatusText = (order: HostOrder | null): string => {
    if (!order) return '';
    const status = order.status as OrderStatus;
    if (status === 'CANCELLED') return '已取消';
    if (status === 'REJECTED') return '已拒绝';
    if (order.paymentStatus === 'PAID') {
        if (status === 'COMPLETED') return '已完成';
        return '已支付';
    }
    if (status === 'CONFIRMED' && order.paymentStatus === 'UNPAID') {
        return '待支付';
    }
    if (status === 'PENDING') return '待确认';
    if (status === 'COMPLETED') return '已完成';
    return status;
}

// 获取状态变更标题
const getStatusChangeTitle = (order: HostOrder | null): string => {
    if (!order) return '';
    const status = order.status as OrderStatus;
    switch (status) {
        case 'CONFIRMED': return order.paymentStatus === 'PAID' ? '订单已支付' : '预订已确认';
        case 'REJECTED': return '预订已拒绝';
        case 'CANCELLED': return '预订已取消';
        case 'COMPLETED': return '订单已完成';
        case 'PENDING': return '订单已创建';
        default: return '状态已更新';
    }
}

// 获取状态变更描述
const getStatusChangeDesc = (order: HostOrder | null): string => {
    if (!order) return '';
    const status = order.status as OrderStatus;
    switch (status) {
        case 'CONFIRMED': return order.paymentStatus === 'PAID' ? '租客已完成支付' : '您已接受租客的预订申请';
        case 'REJECTED': return '您已拒绝租客的预订申请';
        case 'CANCELLED': return '此订单已被取消';
        case 'COMPLETED': return '订单已完成，感谢您的服务';
        case 'PENDING': return '租客提交了预订申请';
        default: return '';
    }
}

// 格式化日期范围
const formatDateRange = (checkIn: string | undefined, checkOut: string | undefined) => {
    if (!checkIn || !checkOut) return ''

    const checkInDate = new Date(checkIn)
    const checkOutDate = new Date(checkOut)

    if (isNaN(checkInDate.getTime()) || isNaN(checkOutDate.getTime())) return '';

    return `${checkInDate.getMonth() + 1}月${checkInDate.getDate()}日 - ${checkOutDate.getMonth() + 1}月${checkOutDate.getDate()}日`
}

// 格式化日期时间
const formatDateTime = (dateTime: string | undefined) => {
    if (!dateTime) return ''
    const date = new Date(dateTime)
    if (isNaN(date.getTime())) return '';

    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

// 提取拒绝原因
const extractRejectReason = (remark: string | undefined) => {
    if (!remark) return ''

    const prefix = "拒绝原因：";
    if (remark.startsWith(prefix)) {
        return remark.substring(prefix.length) || '未提供具体原因';
    }
    return remark;
}

// 返回订单列表
const goToOrders = () => {
    router.push('/host/orders')
}

// Add handlers for CheckIn, Complete, Cancel
// Handle Check In
const handleCheckIn = async () => {
    if (!orderData.value) return;
    if (orderData.value.status !== 'CONFIRMED' || orderData.value.paymentStatus !== 'PAID') {
        ElMessage.warning('只有已支付的确认订单才能办理入住');
        return;
    }
    try {
        await ElMessageBox.confirm('确认要为该订单办理入住吗？', '办理入住', { type: 'info' });
        submitting.value = true;
        await updateOrderStatus(orderData.value.id, 'CHECKED_IN');
        ElMessage.success('入住办理成功');
        fetchOrderDetail();
    } catch (error: any) {
        if (error !== 'cancel') {
            console.error('办理入住失败:', error);
        }
    } finally {
        submitting.value = false;
    }
};

// Handle Complete Order
const handleComplete = async () => {
    if (!orderData.value) return;
    try {
        await ElMessageBox.confirm('确认订单已完成？', '完成订单', { type: 'success' });
        submitting.value = true;
        await updateOrderStatus(orderData.value.id, 'COMPLETED');
        ElMessage.success('订单已完成');
        fetchOrderDetail();
    } catch (error: any) {
        if (error !== 'cancel') {
            console.error('完成订单失败:', error);
        }
    } finally {
        submitting.value = false;
    }
};

// Handle Cancel Order
const handleCancel = async () => {
    if (!orderData.value) return;
    if (!((orderData.value.status === 'PENDING' || orderData.value.status === 'CONFIRMED') && orderData.value.paymentStatus === 'UNPAID')) {
        ElMessage.warning('当前状态无法取消订单');
        return;
    }
    try {
        await ElMessageBox.confirm('确定要取消此订单吗？此操作无法撤销。', '取消订单', { type: 'warning' });
        submitting.value = true;
        await apiHostCancelOrder(orderData.value.id);
        ElMessage.success('订单已取消');
        fetchOrderDetail();
    } catch (error: any) {
        if (error !== 'cancel') {
            console.error('取消订单失败:', error);
        }
    } finally {
        submitting.value = false;
    }
};

onMounted(() => {
    fetchOrderDetail()
})
</script>

<style scoped>
.host-order-detail-container {
    max-width: 800px;
    margin: 0 auto;
    padding: 40px 20px;
    background-color: #f9f9f9;
}

.loading-container,
.error-container {
    padding: 40px;
    text-align: center;
    background-color: white;
    border-radius: 12px;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.05);
}

.order-content {
    background-color: white;
    border-radius: 12px;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.05);
    padding: 32px;
}

.order-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 24px;
}

h1 {
    font-size: 28px;
    margin: 0;
    font-weight: 600;
    color: #333;
}

.action-panel {
    margin-bottom: 24px;
}

.action-card {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background-color: #ecf5ff;
    border-radius: 12px;
    padding: 24px;
    border-left: 4px solid #409eff;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
    transition: transform 0.3s ease;
}

.action-card:hover {
    transform: translateY(-3px);
}

.action-title {
    font-size: 18px;
    font-weight: 600;
    margin-bottom: 8px;
    color: #333;
}

.action-desc {
    font-size: 14px;
    color: #606266;
}

.action-buttons {
    display: flex;
    gap: 12px;
}

.order-summary-card {
    background-color: #f8f9fa;
    border-radius: 12px;
    padding: 24px;
    margin-bottom: 32px;
    transition: transform 0.3s ease, box-shadow 0.3s ease;
    overflow: hidden;
}

.order-summary-card:hover {
    transform: translateY(-5px);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

.card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
}

.card-header h2 {
    margin: 0;
    font-size: 18px;
    color: #333;
    font-weight: 600;
}

.order-number {
    color: #666;
    font-size: 14px;
}

.homestay-info {
    display: flex;
    gap: 20px;
}

.homestay-image {
    width: 160px;
    height: 120px;
    border-radius: 10px;
    overflow: hidden;
    flex-shrink: 0;
    box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
}

.homestay-image img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 0.4s ease;
}

.homestay-image:hover img {
    transform: scale(1.05);
}

.homestay-details h3 {
    margin: 0 0 12px 0;
    font-size: 18px;
    font-weight: 600;
    color: #333;
}

.homestay-details p {
    margin: 8px 0;
    color: #666;
    display: flex;
    align-items: center;
    gap: 6px;
}

.homestay-details i {
    color: #409EFF;
}

.guest-info-section,
.price-details-section,
.reject-reason-section,
.order-timeline-section {
    margin-bottom: 32px;
    padding: 24px;
    background-color: #f8f9fa;
    border-radius: 8px;
}

h2 {
    font-size: 16px;
    margin-bottom: 16px;
    padding-bottom: 10px;
    border-bottom: 1px solid #eee;
    color: #333;
}

.info-item {
    display: flex;
    margin-bottom: 8px;
}

.info-item .label {
    color: #606266;
    margin-right: 8px;
    min-width: 70px;
    display: inline-block;
}

.price-item,
.price-total {
    display: flex;
    justify-content: space-between;
    margin-bottom: 8px;
    font-size: 14px;
}

.price-total {
    font-weight: bold;
    margin-top: 10px;
    padding-top: 10px;
    border-top: 1px dashed #eee;
}

.price-total span:last-child {
    color: #E6A23C;
    font-size: 16px;
}

.reject-reason {
    background-color: #fef0f0;
    border-radius: 8px;
    padding: 16px;
    color: #f56c6c;
}

.bottom-actions {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
}

.reject-dialog-content p {
    margin-top: 0;
    margin-bottom: 16px;
}

/* 时间线样式优化 */
.order-timeline-section :deep(.el-timeline-item__node) {
    width: 14px;
    height: 14px;
}

.order-timeline-section :deep(.el-timeline-item__tail) {
    left: 7px;
}

.order-timeline-section :deep(.el-timeline-item__content) {
    color: #666;
}

.order-timeline-section :deep(.el-timeline-item__timestamp) {
    color: #909399;
}

.order-timeline-section h4 {
    margin-top: 0;
    margin-bottom: 8px;
    font-size: 16px;
    color: #333;
}

.order-timeline-section p {
    margin: 0;
    color: #606266;
}

@media (max-width: 768px) {
    .action-card {
        flex-direction: column;
        align-items: flex-start;
    }

    .action-buttons {
        margin-top: 16px;
        width: 100%;
    }

    .homestay-info {
        flex-direction: column;
    }

    .homestay-image {
        width: 100%;
        height: 180px;
        margin-bottom: 16px;
    }

    .bottom-actions {
        flex-direction: column;
    }
}
</style>
