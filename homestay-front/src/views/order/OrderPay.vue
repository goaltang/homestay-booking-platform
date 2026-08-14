<template>
    <div class="order-pay-container">
        <div v-if="loading" class="loading-container">
            <el-skeleton :rows="5" animated />
        </div>
        <div v-else-if="!orderData" class="error-container">
            <el-empty description="订单不存在">
                <template #description>
                    <p>未找到订单信息，请返回订单列表</p>
                </template>
                <el-button type="primary" @click="goToOrders">查看我的订单</el-button>
            </el-empty>
        </div>
        <div v-else class="pay-content">
            <!-- 新增：支付失败提示 -->
            <el-alert v-if="orderData.status === 'PAYMENT_FAILED' || orderData.paymentStatus === 'FAILED'" title="支付提醒"
                type="warning" description="您之前的支付尝试未成功，请重新选择支付方式并完成支付。" show-icon :closable="false"
                style="margin-bottom: 20px;">
            </el-alert>

            <div class="pay-header">
                <h1>订单支付</h1>
                <div class="order-info">
                    <p class="amount">支付金额: <span class="price">¥{{ orderData.totalAmount }}</span></p>
                    <p class="order-number">订单号: {{ orderData.orderNumber }}</p>
                    <el-tag
                        :type="orderData.status === 'PAID' ? 'success' : (orderData.status === 'CANCELLED' ? 'info' : 'warning')">
                        {{ getStatusText(orderData.status) }}
                    </el-tag>
                </div>
            </div>

            <!-- 添加订单超时倒计时提醒 -->
            <div class="order-timeout-alert" :class="{ 'urgent': isTimeoutUrgent }">
                <el-alert title="请在限定时间内完成支付" type="warning" :description="`超过${store.timeoutConfig?.confirmedTimeoutHours ?? 2}小时未完成支付，订单将被自动取消`" show-icon
                    :closable="false">
                    <template #default>
                        <div class="timeout-countdown">
                            <span>支付倒计时：</span>
                            <span class="countdown-time">{{ orderTimeoutDisplay }}</span>
                        </div>
                    </template>
                </el-alert>
            </div>

            <div class="payment-section" v-if="['PENDING_PAYMENT', 'PAYMENT_PENDING', 'CONFIRMED'].includes(orderData.status)">
                <!-- 如果已经跳转支付宝页面，显示提示信息 -->
                <div class="payment-redirect-notice" v-if="qrCodeGenerated && paymentMethod === 'alipay' && !qrCode">
                    <div class="redirect-content">
                        <div class="redirect-icon">
                            <el-icon size="40" color="#1677ff">
                                <Promotion />
                            </el-icon>
                        </div>
                        <h3>已跳转支付宝支付页面</h3>
                        <p>如果支付页面未自动打开，请点击下方按钮手动跳转</p>
                        <div class="redirect-actions">
                            <el-button type="primary" @click="generateQRCode">
                                <i class="fab fa-alipay"></i> 重新跳转支付宝
                            </el-button>
                            <el-button @click="resetPayment">选择其他支付方式</el-button>
                        </div>
                        <div class="payment-guide">
                            <h4>支付完成后：</h4>
                            <ol>
                                <li>支付成功后，会自动跳转回民宿平台</li>
                                <li>您也可以点击下方"我已完成支付"按钮确认</li>
                                <li>支付状态会在几分钟内更新到您的订单中</li>
                            </ol>
                        </div>
                    </div>
                </div>

                <!-- 二维码支付（微信或扫码模式） -->
                <div class="qr-code-container" v-else-if="qrCode">
                    <div class="qr-code-wrapper">
                        <qrcode-vue :value="qrCode" :size="200" level="H" />
                    </div>
                    <p class="qr-tip">请使用{{ getPaymentMethodText(paymentMethod) }}扫码支付</p>
                    <div class="countdown" v-if="countdown > 0">
                        二维码有效期: {{ formatCountdown(countdown) }}
                    </div>
                </div>

                <!-- 支付方式选择 -->
                <div v-else class="payment-methods">
                    <h3>选择支付方式</h3>
                    <div class="payment-options">
                        <el-radio-group v-model="paymentMethod" @change="generateQRCode">
                            <!-- 只保留支付宝支付 -->
                            <div class="payment-option">
                                <el-radio-button label="alipay">
                                    <div class="payment-option-content">
                                        <i class="fab fa-alipay"></i>
                                        <span>支付宝</span>
                                        <small>页面跳转支付</small>
                                    </div>
                                </el-radio-button>
                            </div>
                        </el-radio-group>
                    </div>
                    <el-button type="primary" class="generate-qr" @click="generateQRCode" :disabled="!paymentMethod" :loading="paymentLoading">
                        {{ paymentLoading ? '正在连接支付宝...' : '立即支付' }}
                    </el-button>
                </div>
            </div>

            <div class="order-summary">
                <h3>订单信息</h3>
                <div class="summary-item">
                    <span>房源名称</span>
                    <span>{{ orderData.homestayTitle }}</span>
                </div>
                <div class="summary-item">
                    <span>入住日期</span>
                    <span>{{ formatDate(orderData.checkInDate) }}</span>
                </div>
                <div class="summary-item">
                    <span>退房日期</span>
                    <span>{{ formatDate(orderData.checkOutDate) }}</span>
                </div>
                <div class="summary-item">
                    <span>入住人数</span>
                    <span>{{ orderData.guestCount }}人</span>
                </div>
            </div>

            <div class="actions" v-if="['PENDING_PAYMENT', 'PAYMENT_PENDING', 'CONFIRMED'].includes(orderData.status)">
                <el-button @click="cancelOrder" plain>取消订单</el-button>
                <el-button type="primary" @click="checkPaymentStatus" :disabled="!qrCodeGenerated">
                    我已完成支付
                </el-button>
                <!-- 重新生成支付按钮 -->
                <el-button type="warning" plain @click="resetPayment"
                    v-if="qrCodeGenerated && paymentMethod === 'alipay'">
                    重新生成支付
                </el-button>
                <!-- 手动跳转按钮 -->
                <el-button type="danger" plain @click="manualJumpToAlipay"
                    v-if="qrCodeGenerated && paymentMethod === 'alipay' && lastPaymentUrl">
                    手动跳转支付宝
                </el-button>

            </div>

            <!-- 支付说明 -->
            <div class="payment-tips" v-if="paymentMethod === 'alipay' && qrCodeGenerated">
                <el-alert title="支付说明" type="info" :closable="false" show-icon>
                    <template #default>
                        <div class="tips-content">
                            <p>• 如果页面没有自动跳转到支付宝，请点击"重新生成支付"</p>
                            <p>• 如果一直无法跳转，请取消订单重新尝试或联系客服</p>
                            <p>• 支付完成后请点击"我已完成支付"确认订单状态</p>
                        </div>
                    </template>
                </el-alert>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { Promotion } from '@element-plus/icons-vue'
import QrcodeVue from 'qrcode.vue'
import { generatePaymentQRCode, checkPayment, cancelOrder as apiCancelOrder } from '../../api/order'
import { useOrderStore } from '@/stores/order'

const route = useRoute()
const router = useRouter()
const store = useOrderStore()

const loading = ref(true)
const orderData = computed(() => store.currentOrder)
const paymentMethod = ref(route.query.method?.toString() || '')
const qrCode = ref('')
const countdown = ref(900) // 15分钟倒计时
const qrCodeGenerated = ref(false) // 新增状态变量，标记二维码是否已生成
const lastPaymentUrl = ref('') // 保存最后一次生成的支付URL
const paymentLoading = ref(false) // 支付按钮加载状态
let timer: ReturnType<typeof setInterval> | null = null
let paymentStatusPollTimer: ReturnType<typeof setInterval> | null = null
let pollCount = 0
const MAX_POLL_COUNT = 120 // 最多轮询 120 次（约 6 分钟）

// 获取订单详情
const fetchOrderDetail = async () => {
    try {
        const orderId = Number(route.params.id)
        await store.fetchOrderDetail(orderId)
    } catch (error: any) {
        console.error('获取订单详情失败:', error)
    } finally {
        loading.value = false
    }
}

// 生成支付二维码或跳转支付页面
const generateQRCode = async () => {
    if (!orderData.value || !paymentMethod.value) return
    paymentLoading.value = true

    try {
        const response = await generatePaymentQRCode({
            orderId: orderData.value.id,
            method: paymentMethod.value
        })

        // 适配新的返回格式，优先处理二维码支付
        if (response.data.success) {
            // 如果返回的是二维码（现在优先返回二维码）
            if (response.data.qrCode || (response.data.paymentUrl && response.data.paymentUrl.startsWith('http'))) {
                console.log('收到支付二维码')
                qrCode.value = response.data.qrCode || response.data.paymentUrl
                qrCodeGenerated.value = true
                ElMessage.success('支付二维码生成成功，请使用支付宝扫码支付')
                startCountdown()
                startPaymentStatusPoll()
                return
            }

            // 如果返回的是支付页面URL（支付宝页面跳转支付）
            if (response.data.paymentUrl && paymentMethod.value === 'alipay') {
                console.log('支付宝返回的paymentUrl:', response.data.paymentUrl)

                // 方法1: 解析表单数据并直接跳转（最可靠的方法）
                try {
                    console.log('尝试方法1: 解析表单数据直接跳转')

                    // 创建一个临时div来解析HTML
                    const tempDiv = document.createElement('div')
                    tempDiv.innerHTML = response.data.paymentUrl

                    // 查找表单
                    const form = tempDiv.querySelector('form')
                    if (form) {
                        console.log('找到支付表单，准备解析')
                        console.log('表单action:', form.action)
                        console.log('表单method:', form.method)

                        // 提取表单数据
                        const formData = new FormData(form)
                        const params = new URLSearchParams()

                        for (const [key, value] of formData.entries()) {
                            params.append(key, value.toString())
                        }

                        // 构建完整的URL
                        const baseUrl = form.action
                        const fullUrl = baseUrl + (baseUrl.includes('?') ? '&' : '?') + params.toString()

                        console.log('构建的跳转URL长度:', fullUrl.length)
                        console.log('准备跳转到:', baseUrl)

                        // 保存支付URL供手动跳转使用
                        lastPaymentUrl.value = fullUrl

                        ElMessage.success('正在跳转到支付页面...')

                        // 尝试多种跳转方式
                        console.log('开始尝试跳转，URL:', fullUrl.substring(0, 100) + '...')

                        // 方式1: 直接提交表单（最可靠的方法，支持POST）
                        try {
                            console.log('尝试方式1: 直接渲染HTML表单跳转')
                            // 创建一个隐藏的容器
                            const container = document.createElement('div')
                            container.style.display = 'none'
                            container.innerHTML = response.data.paymentUrl
                            document.body.appendChild(container)
                            
                            // 找到表单并提交
                            const form = container.querySelector('form')
                            if (form) {
                                console.log('找到表单，准备提交到:', form.action)
                                form.target = '_self'
                                form.submit()
                                return
                            }
                        } catch (e) {
                            console.error('方式1表单提交失败:', e)
                        }

                        // 方式2: 直接跳转（GET方式，可能失败）
                        try {
                            console.log('尝试方式2: GET跳转')
                            window.location.href = fullUrl
                        } catch (e) {
                            console.error('方式2跳转失败:', e)
                        }

                        qrCodeGenerated.value = true
                        startPaymentStatusPoll()
                        return
                    } else {
                        console.log('未找到表单元素')
                    }
                } catch (error) {
                    console.error('方法1失败:', error)
                }

                // 方法2: 如果方法1失败，尝试表单提交
                try {
                    console.log('尝试方法2: 使用表单提交')

                    // 解析HTML表单
                    const tempDiv = document.createElement('div')
                    tempDiv.innerHTML = response.data.paymentUrl
                    const form = tempDiv.querySelector('form')

                    if (form) {
                        // 将表单添加到页面并提交
                        form.style.display = 'none'
                        form.target = '_self'
                        document.body.appendChild(form)

                        ElMessage.success('正在跳转到支付页面...')

                        // 立即提交表单
                        form.submit()
                        qrCodeGenerated.value = true
                        startPaymentStatusPoll()

                        // 清理DOM
                        setTimeout(() => {
                            if (document.body.contains(form)) {
                                document.body.removeChild(form)
                            }
                        }, 2000)
                        return
                    }
                } catch (error) {
                    console.error('方法2失败:', error)
                }

                // 方法3: 如果直接跳转失败，尝试在新窗口打开
                try {
                    console.log('尝试方法3: 在新窗口打开支付页面')
                    const newWindow = window.open('about:blank', '_blank')
                    if (newWindow) {
                        newWindow.document.write(response.data.paymentUrl)
                        newWindow.document.close()
                        ElMessage.success('支付页面已在新窗口打开，请完成支付')
                        qrCodeGenerated.value = true
                        startPaymentStatusPoll()
                        return
                    }
                } catch (error) {
                    console.error('新窗口打开失败:', error)
                }

                // 方法3: 如果都失败了，显示错误并提供手动操作
                ElMessage.error('自动跳转失败，请尝试重新生成支付或使用其他支付方式')
                console.log('所有跳转方法都失败，需要手动处理')
                qrCodeGenerated.value = false  // 重置状态，允许重新尝试
            }
            else {
                ElMessage.error('支付方式不支持，请选择其他支付方式')
            }
        } else {
            ElMessage.error(response.data.message || '生成支付失败')
        }
    } catch (error: any) {
        console.error('支付生成失败:', error)

        // 检查是否是"订单已有待支付的支付记录"错误
        const errorMsg = error.response?.data?.message || ''
        if (errorMsg.includes('待支付') && errorMsg.includes('支付记录')) {
            ElMessage.warning('检测到您有未完成的支付，正在为您查询支付状态...')
            // 自动转为轮询支付状态
            qrCodeGenerated.value = true
            startPaymentStatusPoll()
            paymentLoading.value = false
            return
        }

        // 检查是否是502错误（支付宝服务器问题）
        if (error.response?.status === 502 ||
            (errorMsg && errorMsg.includes('502'))) {
            ElNotification({
                title: '支付服务提示',
                message: '当前支付宝沙箱环境出现临时故障，建议：\n1. 稍后重试\n2. 选择微信支付\n3. 联系客服处理',
                type: 'warning',
                duration: 8000
            })
        } else {
        }
    } finally {
        paymentLoading.value = false
    }
}

// 开始倒计时
const startCountdown = () => {
    countdown.value = 900
    if (timer) clearInterval(timer)
    timer = setInterval(() => {
        if (countdown.value > 0) {
            countdown.value--
        } else {
            qrCode.value = ''
            if (timer) clearInterval(timer)
        }
    }, 1000)
}

// 启动支付状态轮询
const startPaymentStatusPoll = () => {
    if (paymentStatusPollTimer) clearInterval(paymentStatusPollTimer)
    pollCount = 0
    
    paymentStatusPollTimer = setInterval(async () => {
        if (!orderData.value) return
        
        pollCount++
        if (pollCount > MAX_POLL_COUNT) {
            clearInterval(paymentStatusPollTimer!)
            paymentStatusPollTimer = null
            ElMessage.warning('支付状态查询超时，请点击"我已完成支付"手动确认')
            return
        }
        
        try {
            const response = await checkPayment(orderData.value.id)
            if (response.data.success && response.data.isPaid) {
                clearInterval(paymentStatusPollTimer!)
                paymentStatusPollTimer = null
                ElMessage.success('支付成功！')
                // 刷新 Store 订单数据，确保列表同步
                store.fetchOrders()
                store.fetchStatsOrders()
                router.push(`/orders/${orderData.value.id}/pay-success`)
            }
        } catch (error) {
            console.error('轮询检查支付状态失败:', error)
        }
    }, 3000) // 每3秒轮询一次
}

// 停止支付状态轮询
const stopPaymentStatusPoll = () => {
    if (paymentStatusPollTimer) {
        clearInterval(paymentStatusPollTimer)
        paymentStatusPollTimer = null
    }
}

// 格式化倒计时
const formatCountdown = (seconds: number) => {
    const minutes = Math.floor(seconds / 60)
    const remainingSeconds = seconds % 60
    return `${minutes}:${remainingSeconds.toString().padStart(2, '0')}`
}

// 检查支付状态
const checkPaymentStatus = async () => {
    if (!orderData.value) return

    const loadingInstance = ElMessage({ type: 'info', message: '正在确认支付状态...', duration: 0 });
    try {
        const response = await checkPayment(orderData.value.id)
        loadingInstance.close(); // 关闭加载提示

        // 适配新的返回格式
        if (response.data.success) {
            if (response.data.isPaid) {
                ElMessage.success('支付成功！')
                // 刷新 Store 订单数据，确保列表同步
                store.fetchOrders()
                store.fetchStatsOrders()
                // 跳转到支付成功页面
                router.push(`/orders/${orderData.value.id}/pay-success`)
            } else {
                ElMessage.warning(response.data.message || '支付状态尚未确认，请稍后再试或联系客服');
            }
        } else {
            ElMessage.error(response.data.message || '查询支付状态失败');
        }
    } catch (error: any) {
        loadingInstance.close(); // 关闭加载提示
        console.error("检查支付状态接口调用失败:", error);
    }
}

// 取消订单
const cancelOrder = async () => {
    if (!orderData.value) return

    try {
        await ElMessageBox.confirm(
            '确定要取消该订单吗？取消后将无法恢复',
            '取消订单',
            {
                confirmButtonText: '确定取消',
                cancelButtonText: '继续支付',
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
            await apiCancelOrder(orderData.value.id)

            // 刷新 Store 数据，确保订单列表同步
            store.fetchOrders()
            store.fetchStatsOrders()

            // 关闭加载消息
            ElMessage.closeAll()

            // 显示更详细的成功消息，并提供跳转选项
            const result = await ElMessageBox.alert(
                '订单已成功取消，您可以继续浏览其他房源或返回订单列表',
                '取消成功',
                {
                    confirmButtonText: '查看其他房源',
                    cancelButtonText: '返回订单列表',
                    distinguishCancelAndClose: true,
                    showCancelButton: true
                }
            )

            if (result === 'confirm') {
                router.push('/') // 前往首页
            } else {
                router.push('/user/bookings') // 返回订单列表
            }
        } catch (error: any) {
            // 关闭加载消息
            ElMessage.closeAll()

            console.error('取消订单失败:', error)
        }
    } catch (error: any) {
        if (error !== 'cancel') {
            console.error('取消订单确认操作失败:', error)
        }
    }
}


// 重置支付状态，用于重新选择支付方式
const resetPayment = () => {
    qrCode.value = ''
    qrCodeGenerated.value = false
    paymentMethod.value = ''
    lastPaymentUrl.value = ''
    if (timer) {
        clearInterval(timer)
        timer = null
    }
    stopPaymentStatusPoll()
    countdown.value = 900
}

// 手动跳转到支付宝
const manualJumpToAlipay = () => {
    if (!lastPaymentUrl.value) {
        ElMessage.error('支付链接已失效，请重新生成支付')
        return
    }

    console.log('手动跳转到支付宝，URL长度:', lastPaymentUrl.value.length)

    // 尝试多种方式打开支付宝页面
    try {
        // 方式1: 新窗口打开
        const newWindow = window.open(lastPaymentUrl.value, '_blank')
        if (newWindow) {
            ElMessage.success('支付页面已在新窗口打开')
            return
        }
    } catch (e) {
        console.error('新窗口打开失败:', e)
    }

    try {
        // 方式2: 当前窗口跳转
        window.location.href = lastPaymentUrl.value
        ElMessage.success('正在跳转到支付页面...')
    } catch (e) {
        console.error('当前窗口跳转失败:', e)
    }
}

// 获取支付方式文本
const getPaymentMethodText = (method: string) => {
    const methods = {
        wechat: '微信',
        alipay: '支付宝'
    }
    return methods[method as keyof typeof methods] || ''
}

// 获取订单状态文本
const getStatusText = (status: string) => {
    const statusMap = {
        PENDING_PAYMENT: '待支付',
        PAYMENT_PENDING: '待支付',
        CONFIRMED: '待支付',
        PAID: '已支付',
        CANCELLED: '已取消'
    }
    return statusMap[status as keyof typeof statusMap] || status
}

// 格式化日期
const formatDate = (date: string) => {
    return new Date(date).toLocaleDateString('zh-CN', {
        month: 'long',
        day: 'numeric'
    })
}

// 跳转到订单列表
const goToOrders = () => {
    router.push('/user/bookings')
}

// 计算倒计时
const getCountdownTime = (startTimeStr: string, hoursLimit: number) => {
    if (!startTimeStr) return "获取中...";

    const startTime = new Date(startTimeStr).getTime();
    const currentTime = new Date().getTime();
    const limitTime = startTime + hoursLimit * 60 * 60 * 1000;
    const remainingTime = limitTime - currentTime;

    if (remainingTime <= 0) {
        return "即将自动取消";
    }

    // 计算剩余小时、分钟和秒数
    const hours = Math.floor(remainingTime / (60 * 60 * 1000));
    const minutes = Math.floor((remainingTime % (60 * 60 * 1000)) / (60 * 1000));
    const seconds = Math.floor((remainingTime % (60 * 1000)) / 1000);

    return `${hours}小时${minutes}分钟${seconds}秒`;
}

// 判断时间是否紧急（剩余不到30分钟）
const isTimeUrgent = (startTimeStr: string, hoursLimit: number) => {
    if (!startTimeStr) return false;

    const startTime = new Date(startTimeStr).getTime();
    const currentTime = new Date().getTime();
    const limitTime = startTime + hoursLimit * 60 * 60 * 1000;
    const remainingTime = limitTime - currentTime;

    // 如果剩余时间小于30分钟，显示紧急样式
    return remainingTime < 30 * 60 * 1000 && remainingTime > 0;
}

// 自动刷新倒计时
let autoRefreshTimer: ReturnType<typeof setInterval> | null = null;

const orderTimeoutDisplay = ref('获取中...')
const isTimeoutUrgent = ref(false)

const updateOrderTimeoutDisplay = () => {
    if (orderData.value?.updateTime) {
        const hours = store.timeoutConfig?.confirmedTimeoutHours ?? 2
        orderTimeoutDisplay.value = getCountdownTime(orderData.value.updateTime, hours)
        isTimeoutUrgent.value = isTimeUrgent(orderData.value.updateTime, hours)
    }
}

onMounted(async () => {
    // 重置支付状态，避免上次访问遗留的状态导致页面显示异常
    qrCode.value = ''
    qrCodeGenerated.value = false
    paymentMethod.value = route.query.method?.toString() || ''
    lastPaymentUrl.value = ''
    countdown.value = 900
    if (timer) clearInterval(timer)
    timer = null
    stopPaymentStatusPoll()

    // 确保超时配置已加载
    if (!store.timeoutConfig) {
        await store.fetchTimeoutConfig()
    }

    await fetchOrderDetail();
    updateOrderTimeoutDisplay();

    // 为自动确认订单提供更流畅的支付体验
    if (route.query.autoConfirm === 'true') {
        // 自动选择支付宝作为默认支付方式
        paymentMethod.value = 'alipay';
        // 延迟500ms后自动生成二维码，确保页面已完全加载
        setTimeout(() => {
            if (paymentMethod.value && orderData.value) {
                generateQRCode();
            }
        }, 500);
    }

    // 每秒刷新倒计时显示
    autoRefreshTimer = setInterval(() => {
        if (orderData.value?.updateTime) {
            updateOrderTimeoutDisplay()
        }
    }, 1000);

    // 如果订单已是待支付状态，自动启动轮询检查支付状态
    // 但必须满足：paymentMethod 已选择 且 qrCode 已生成
    setTimeout(() => {
        if (orderData.value &&
            (orderData.value.status === 'PAYMENT_PENDING' ||
             orderData.value.status === 'PENDING_PAYMENT' ||
             orderData.value.status === 'CONFIRMED' ||
             orderData.value.paymentStatus === 'PENDING')) {
            ElMessage.info('检测到您有未完成的支付，正在查询支付状态...')
            qrCodeGenerated.value = true
            startPaymentStatusPoll()
        }
    }, 1000);
});

onUnmounted(() => {
    if (timer) clearInterval(timer);
    if (autoRefreshTimer) clearInterval(autoRefreshTimer);
    stopPaymentStatusPoll();
});
</script>

<style scoped>
.order-pay-container {
    max-width: 800px;
    margin: 0 auto;
    padding: 40px 20px;
}

.loading-container,
.error-container {
    padding: 40px;
    text-align: center;
}

.pay-header {
    margin-bottom: 40px;
    text-align: center;
}

h1 {
    font-size: 28px;
    margin-bottom: 20px;
}

.order-info {
    text-align: center;
}

.amount {
    font-size: 20px;
    margin-bottom: 10px;
}

.price {
    color: #f56c6c;
    font-size: 24px;
    font-weight: bold;
}

.order-number {
    color: #666;
    margin-bottom: 10px;
}

.payment-section {
    background-color: #f8f9fa;
    border-radius: 12px;
    padding: 32px;
    margin-bottom: 32px;
    text-align: center;
}

.qr-code-container {
    display: flex;
    flex-direction: column;
    align-items: center;
}

.qr-code-wrapper {
    background: white;
    padding: 16px;
    border-radius: 8px;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
    margin-bottom: 16px;
    display: inline-block;
}

.qr-tip {
    color: #666;
    margin-bottom: 8px;
}

.countdown {
    color: #f56c6c;
    font-size: 14px;
}

.payment-methods {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 20px;
}

.generate-qr {
    margin-top: 20px;
}

.order-summary {
    background-color: #fff;
    border: 1px solid #eee;
    border-radius: 12px;
    padding: 24px;
    margin-bottom: 32px;
}

.summary-item {
    display: flex;
    justify-content: space-between;
    margin-bottom: 12px;
    color: #666;
}

.actions {
    display: flex;
    justify-content: center;
    gap: 16px;
    margin-top: 32px;
}

:deep(.el-radio-button__inner) {
    display: flex;
    align-items: center;
    gap: 8px;
}

.fab {
    font-size: 18px;
}

.order-timeout-alert {
    margin: 20px 0;
}

.order-timeout-alert.urgent .el-alert {
    background-color: #fef0f0;
    border-color: #f56c6c;
}

.timeout-countdown {
    margin-top: 10px;
    text-align: center;
}

.countdown-time {
    font-size: 18px;
    font-weight: bold;
    color: #e6a23c;
}

.order-timeout-alert.urgent .countdown-time {
    color: #f56c6c;
    animation: blink 1s infinite;
}

@keyframes blink {
    0% {
        opacity: 1;
    }

    50% {
        opacity: 0.5;
    }

    100% {
        opacity: 1;
    }
}

/* 支付方式选项样式 */
.payment-options {
    margin-bottom: 24px;
}

.payment-option {
    margin-bottom: 12px;
}

.payment-option-content {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 8px;
    gap: 4px;
}

.payment-option-content i {
    font-size: 24px;
    margin-bottom: 4px;
}

.payment-option-content span {
    font-weight: 500;
    font-size: 14px;
}

.payment-option-content small {
    font-size: 12px;
    color: #909399;
    opacity: 0.8;
}

/* 支付跳转提示样式 */
.payment-redirect-notice {
    background: linear-gradient(135deg, #f0f8ff 0%, #e6f3ff 100%);
    border: 2px solid #1677ff;
    border-radius: 12px;
    padding: 32px;
    text-align: center;
    margin: 20px 0;
}

.redirect-content h3 {
    color: #1677ff;
    margin: 16px 0 12px 0;
    font-size: 20px;
}

.redirect-content p {
    color: #666;
    margin-bottom: 24px;
    font-size: 14px;
}

.redirect-actions {
    margin: 24px 0;
    display: flex;
    gap: 12px;
    justify-content: center;
    flex-wrap: wrap;
}

.redirect-actions .el-button {
    padding: 10px 20px;
}

.redirect-actions .el-button i {
    margin-right: 8px;
}

.payment-guide {
    background: rgba(255, 255, 255, 0.7);
    border-radius: 8px;
    padding: 16px;
    margin-top: 24px;
    text-align: left;
}

.payment-guide h4 {
    margin: 0 0 12px 0;
    color: #303133;
    font-size: 16px;
}

.payment-guide ol {
    margin: 0;
    padding-left: 20px;
    color: #666;
}

.payment-guide li {
    margin-bottom: 8px;
    line-height: 1.5;
}

/* 支付说明样式 */
.payment-tips {
    margin: 24px 0;
}

.tips-content p {
    margin: 8px 0;
    line-height: 1.6;
    color: #606266;
    font-size: 14px;
}
</style>