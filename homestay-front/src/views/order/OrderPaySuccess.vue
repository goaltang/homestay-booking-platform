<template>
    <div class="pay-success-container">
        <div class="success-card">
            <div class="icon-container">
                <el-icon class="success-icon">
                    <CircleCheck />
                </el-icon>
            </div>
            <h1>支付成功！</h1>
            <p class="status-text">您的预订已完成，我们已为您发送确认信息</p>

            <div class="order-info">
                <div class="order-number">
                    <span>订单号：</span>
                    <span class="number">{{ orderNumber }}</span>
                    <el-button type="text" size="small" class="copy-btn" @click="copyOrderNumber">
                        复制
                    </el-button>
                </div>
                <div class="payment-amount">
                    <span>支付金额：</span>
                    <span class="amount">¥{{ paymentAmount }}</span>
                </div>
            </div>

            <div class="next-steps">
                <h3>接下来您可以</h3>
                <div class="step-list">
                    <div class="step">
                        <div class="step-icon">
                            <el-icon><Calendar /></el-icon>
                        </div>
                        <div class="step-content">
                            <h4>查看入住信息</h4>
                            <p>确认您的入住日期和房源详情</p>
                        </div>
                    </div>
                    <div class="step">
                        <div class="step-icon">
                            <el-icon><ChatDotRound /></el-icon>
                        </div>
                        <div class="step-content">
                            <h4>联系房东</h4>
                            <p>提前与房东沟通入住细节</p>
                        </div>
                    </div>
                    <div class="step">
                        <div class="step-icon">
                            <el-icon><Location /></el-icon>
                        </div>
                        <div class="step-content">
                            <h4>查看位置</h4>
                            <p>了解房源周边交通和设施</p>
                        </div>
                    </div>
                </div>
            </div>

            <div class="action-buttons">
                <el-button @click="goToOrderDetail" type="primary">查看订单详情</el-button>
                <el-button @click="goToHomestayDetail" plain>查看房源信息</el-button>
                <el-button @click="goToHome" plain>继续探索</el-button>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCheck, Calendar, ChatDotRound, Location } from '@element-plus/icons-vue'
import { getOrderDetail } from '@/api/order'

const route = useRoute()
const router = useRouter()
const orderId = ref<number>(Number(route.params.id) || 0)
const orderNumber = ref<string>('')
const paymentAmount = ref<number>(0)
const homestayId = ref<number>(0)

// 获取订单详情
onMounted(async () => {
    if (!orderId.value) {
        router.push('/user/bookings')
        return
    }

    try {
        const response = await getOrderDetail(orderId.value)
        orderNumber.value = response.data.orderNumber
        paymentAmount.value = response.data.totalAmount
        homestayId.value = response.data.homestayId
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

// 跳转到房源详情
const goToHomestayDetail = () => {
    router.push(`/homestays/${homestayId.value}`)
}

// 跳转到首页
const goToHome = () => {
    router.push('/')
}
</script>

<style scoped>
.pay-success-container {
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

.order-number, .payment-amount {
    display: flex;
    align-items: center;
    margin-bottom: 12px;
}

.number, .amount {
    font-weight: bold;
    margin-right: 8px;
    color: #409eff;
}

.copy-btn {
    margin-left: 8px;
    padding: 0;
}

.next-steps {
    margin-bottom: 32px;
    text-align: left;
}

.next-steps h3 {
    font-size: 18px;
    margin-bottom: 16px;
    color: #303133;
    text-align: center;
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

.step-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    background-color: #409eff;
    color: white;
    border-radius: 50%;
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