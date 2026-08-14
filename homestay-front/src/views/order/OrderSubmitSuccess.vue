<template>
    <div class="order-success-container">
        <div class="success-card">
            <div class="icon-container">
                <el-icon class="success-icon">
                    <Check />
                </el-icon>
            </div>
            <h1>预订申请已提交</h1>
            <p class="status-text">您的预订申请已成功提交，正在等待房东确认</p>

            <div class="order-info">
                <div class="order-number">
                    <span>订单号：</span>
                    <span class="number">{{ orderNumber }}</span>
                    <el-button type="text" size="small" class="copy-btn" @click="copyOrderNumber">
                        复制
                    </el-button>
                </div>
                <p class="notice">房东将在24小时内确认您的预订申请。确认后，您将收到通知并可进行支付。</p>
            </div>

            <div class="next-steps">
                <h3>后续步骤</h3>
                <div class="step-list">
                    <div class="step">
                        <div class="step-number">1</div>
                        <div class="step-content">
                            <h4>等待房东确认</h4>
                            <p>房东会审核您的预订申请，并在24小时内给予回复</p>
                        </div>
                    </div>
                    <div class="step">
                        <div class="step-number">2</div>
                        <div class="step-content">
                            <h4>预订确认通知</h4>
                            <p>您将收到预订确认通知，请及时查看</p>
                        </div>
                    </div>
                    <div class="step">
                        <div class="step-number">3</div>
                        <div class="step-content">
                            <h4>完成支付</h4>
                            <p>确认后请尽快完成支付以确保住宿</p>
                        </div>
                    </div>
                </div>
            </div>

            <div class="action-buttons">
                <el-button @click="goToOrderDetail">查看订单详情</el-button>
                <el-button type="primary" @click="goToMyOrders">查看我的订单</el-button>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Check } from '@element-plus/icons-vue'
import { getOrderDetail } from '@/api/order'

const route = useRoute()
const router = useRouter()
const orderId = ref<number>(Number(route.params.id) || 0)
const orderNumber = ref<string>('')

// 获取订单详情
onMounted(async () => {
    if (!orderId.value) {
        router.push('/user/bookings')
        return
    }

    try {
        const response = await getOrderDetail(orderId.value)
        orderNumber.value = response.data.orderNumber
    } catch (error) {
        console.error('获取订单详情失败:', error)
    }
})

// 复制订单号
const copyOrderNumber = () => {
    navigator.clipboard.writeText(orderNumber.value)
        .then(() => ElMessage.success('订单号已复制到剪贴板'))
        .catch(() => ElMessage.error('复制失败，请手动复制'))
}

// 跳转到订单详情
const goToOrderDetail = () => {
    router.push(`/orders/${orderId.value}`)
}

// 跳转到我的订单列表
const goToMyOrders = () => {
    router.push('/user/bookings')
}
</script>

<style scoped>
.order-success-container {
    max-width: 800px;
    margin: 40px auto;
    padding: 0 20px;
}

.success-card {
    background-color: white;
    border-radius: 12px;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
    padding: 40px;
    text-align: center;
}

.icon-container {
    margin-bottom: 24px;
}

.success-icon {
    font-size: 60px;
    color: #67c23a;
    background-color: rgba(103, 194, 58, 0.1);
    border-radius: 50%;
    padding: 16px;
}

h1 {
    font-size: 28px;
    margin-bottom: 12px;
    color: #303133;
}

.status-text {
    font-size: 16px;
    color: #606266;
    margin-bottom: 32px;
}

.order-info {
    background-color: #f8f9fa;
    border-radius: 8px;
    padding: 20px;
    margin-bottom: 32px;
    text-align: left;
}

.order-number {
    display: flex;
    align-items: center;
    margin-bottom: 12px;
}

.number {
    font-weight: bold;
    margin-right: 8px;
}

.copy-btn {
    margin-left: 8px;
    padding: 0;
}

.notice {
    color: #606266;
    margin: 0;
    line-height: 1.5;
}

.next-steps {
    margin-bottom: 32px;
    text-align: left;
}

.next-steps h3 {
    font-size: 18px;
    margin-bottom: 16px;
    color: #303133;
}

.step-list {
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.step {
    display: flex;
    align-items: flex-start;
    gap: 16px;
}

.step-number {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    background-color: #409eff;
    color: white;
    border-radius: 50%;
    font-weight: bold;
    flex-shrink: 0;
}

.step-content {
    flex: 1;
}

.step-content h4 {
    margin: 0 0 8px 0;
    font-size: 16px;
    color: #303133;
}

.step-content p {
    margin: 0;
    color: #606266;
    font-size: 14px;
}

.action-buttons {
    display: flex;
    justify-content: center;
    gap: 16px;
    margin-top: 40px;
}

@media (max-width: 600px) {
    .success-card {
        padding: 24px;
    }

    .action-buttons {
        flex-direction: column;
        gap: 8px;
    }
}
</style>