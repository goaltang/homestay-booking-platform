<template>
    <div class="dashboard">
        <div v-if="loading" class="loading-overlay">
            <el-icon class="is-loading" :size="30">
                <Loading />
            </el-icon>
            <span>加载数据中...</span>
        </div>

        <div v-else>
            <!-- 欢迎区 -->
            <div class="greeting-section">
                <div class="greeting-left">
                    <h1 class="greeting-title">{{ greeting }}，{{ userName }}</h1>
                    <p class="greeting-sub">欢迎回到房东中心，以下是您的数据概览</p>
                </div>
                <div class="greeting-right">
                    <div class="quick-actions">
                        <el-button type="primary" size="small" @click="$router.push('/host/homestay/new')">
                            <el-icon>
                                <Plus />
                            </el-icon>发布房源
                        </el-button>
                        <el-button size="small" @click="$router.push('/host/orders')">
                            <el-icon>
                                <List />
                            </el-icon>处理订单
                        </el-button>
                        <el-button size="small" @click="$router.push('/host/homestay')">
                            <el-icon>
                                <House />
                            </el-icon>管理房源
                        </el-button>
                    </div>
                    <div class="date-display">
                        <span class="date-week">{{ weekDay }}</span>
                        <span class="date-full">{{ fullDate }}</span>
                    </div>
                </div>
            </div>

            <!-- 统计卡片区 -->
            <div class="stats-grid">
                <div v-for="(stat, index) in statCards" :key="stat.key" class="stat-card"
                    :class="[`stat-card--${stat.color}`, { 'is-clickable': stat.route }]"
                    :style="{ animationDelay: `${index * 80}ms` }" @click="handleCardClick(stat.route)">
                    <div class="stat-card__icon">
                        <el-icon :size="24">
                            <component :is="stat.icon" />
                        </el-icon>
                    </div>
                    <div class="stat-card__content">
                        <div class="stat-card__value" :ref="el => statValueRefs[index] = el as HTMLElement">
                            {{ stat.prefix }}{{ formatStatValue(stat.value) }}
                        </div>
                        <div class="stat-card__label">{{ stat.label }}</div>
                    </div>
                </div>
            </div>

            <!-- 待办提醒区 -->
            <div class="todo-section">
                <div class="section-header">
                    <span class="section-title">待办事项</span>
                </div>
                <el-row :gutter="16">
                    <el-col :xs="12" :sm="12" :md="6" :lg="6" v-for="todo in todoList" :key="todo.key">
                        <div class="todo-card" :class="{ 'is-zero': todo.value === 0 }"
                            @click="todo.route && $router.push(todo.route)">
                            <div class="todo-card__icon" :class="`todo-card__icon--${todo.color}`">
                                <el-icon :size="20">
                                    <component :is="todo.icon" />
                                </el-icon>
                            </div>
                            <div class="todo-card__content">
                                <div class="todo-card__value">{{ todo.value }}</div>
                                <div class="todo-card__label">{{ todo.label }}</div>
                            </div>
                            <el-icon class="todo-card__arrow" v-if="todo.route">
                                <ArrowRight />
                            </el-icon>
                        </div>
                    </el-col>
                </el-row>
            </div>

            <!-- 图表区域 -->
            <div class="charts-grid">
                <div class="chart-card chart-card--pie">
                    <div class="chart-card__header">
                        <span class="chart-card__title">订单状态分布</span>
                    </div>
                    <div class="chart-card__body">
                        <div ref="orderChartRef" style="width: 100%; height: 100%"></div>
                    </div>
                </div>
            </div>

            <!-- 下方两列：最近订单 + 最新评价 -->
            <div class="bottom-grid">
                <div class="bottom-card">
                    <div class="card-header">
                        <span class="card-header__title">最近订单</span>
                        <el-button text type="primary" size="small" @click="$router.push('/host/orders')">
                            查看全部
                        </el-button>
                    </div>
                    <div v-if="recentOrders && recentOrders.length > 0">
                        <el-table :data="recentOrders" style="width: 100%" stripe size="small">
                            <el-table-column prop="orderNumber" label="订单号" width="120" />
                            <el-table-column label="房源" min-width="140">
                                <template #default="{ row }">
                                    <el-tooltip :content="row.homestayTitle || row.homestayName" placement="top">
                                        <span class="ellipsis-text">{{ row.homestayTitle || row.homestayName
                                            }}</span>
                                    </el-tooltip>
                                </template>
                            </el-table-column>
                            <el-table-column label="入住日期" width="130">
                                <template #default="{ row }">
                                    <div class="date-range">
                                        <span>{{ formatDate(row.checkInDate) }}</span>
                                        <span class="date-separator">至</span>
                                        <span>{{ formatDate(row.checkOutDate) }}</span>
                                    </div>
                                </template>
                            </el-table-column>
                            <el-table-column prop="totalAmount" label="金额" width="90" align="right">
                                <template #default="{ row }">
                                    <span class="amount">¥{{ formatAmount(row.totalAmount) }}</span>
                                </template>
                            </el-table-column>
                            <el-table-column prop="status" label="状态" width="90" align="center">
                                <template #default="{ row }">
                                    <el-tag :type="getOrderStatusType(row.status)" effect="plain" size="small">
                                        {{ getOrderStatusText(row.status) }}
                                    </el-tag>
                                </template>
                            </el-table-column>
                            <el-table-column label="操作" width="70" align="center">
                                <template #default="{ row }">
                                    <el-button link type="primary" size="small"
                                        @click="$router.push(`/host/orders?id=${row.id}`)">
                                        详情
                                    </el-button>
                                </template>
                            </el-table-column>
                        </el-table>
                    </div>
                    <div v-else class="empty-data">
                        <el-icon :size="40" color="#dcdfe6">
                            <List />
                        </el-icon>
                        <p>暂无最近订单</p>
                    </div>
                </div>

                <div class="bottom-card">
                    <div class="card-header">
                        <span class="card-header__title">最新评价</span>
                        <el-button text type="primary" size="small" @click="$router.push('/host/reviews')">
                            查看全部
                        </el-button>
                    </div>
                    <div v-if="recentReviews && recentReviews.length > 0" class="review-list">
                        <div v-for="review in recentReviews" :key="review.id" class="review-item">
                            <div class="review-item__header">
                                <el-avatar :size="32" :src="review.userAvatar"
                                    @error="$event.target.style.display = 'none'">
                                    {{ review.userName?.charAt(0)?.toUpperCase() || '?' }}
                                </el-avatar>
                                <div class="review-item__meta">
                                    <div class="review-item__name">{{ review.userName }}</div>
                                    <el-rate :model-value="review.rating" disabled size="small" />
                                </div>
                                <span class="review-item__date">{{ formatDate(review.createdAt) }}</span>
                            </div>
                            <div class="review-item__content">
                                <el-tooltip :content="review.content" placement="top" :show-after="500">
                                    <p class="review-item__text">{{ review.content }}</p>
                                </el-tooltip>
                            </div>
                            <div class="review-item__homestay">
                                <el-icon size="12">
                                    <House />
                                </el-icon>
                                <span>{{ review.homestayTitle }}</span>
                            </div>
                        </div>
                    </div>
                    <div v-else class="empty-data">
                        <el-icon :size="40" color="#dcdfe6">
                            <Star />
                        </el-icon>
                        <p>暂无评价</p>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import echarts from '@/utils/echarts'
import {
    House, List, Star, Warning, CircleCheck,
    Plus, ArrowRight, Loading
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import {
    getHostStatistics,
    getHostRecentOrders,
    getHostReviews,
    type HostStatisticsData,
    type HostOrderData
} from '@/api/host'


const router = useRouter()
const userStore = useUserStore()

// ========== 加载状态 ==========
const loading = ref(true)

// ========== 用户数据 ==========
const userName = computed(() => userStore.userInfo?.username || '房东')

const greeting = computed(() => {
    const hour = new Date().getHours()
    if (hour < 6) return '凌晨好'
    if (hour < 9) return '早上好'
    if (hour < 12) return '上午好'
    if (hour < 14) return '中午好'
    if (hour < 18) return '下午好'
    if (hour < 22) return '晚上好'
    return '夜深了'
})

const weekDay = computed(() => {
    const days = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
    return days[new Date().getDay()]
})

const fullDate = computed(() => {
    const d = new Date()
    return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
})

// ========== 统计数据 ==========
const statsData = ref<HostStatisticsData | null>(null)
const recentOrders = ref<HostOrderData[]>([])
const recentReviews = ref<any[]>([])

interface StatItem {
    key: string
    value: number
    label: string
    icon: any
    color: string
    prefix: string
    route?: string
}

const statCards = ref<StatItem[]>([
    { key: 'homestays', value: 0, label: '我的房源', icon: House, color: 'blue', prefix: '', route: '/host/homestay' },
    { key: 'orders', value: 0, label: '订单总数', icon: List, color: 'purple', prefix: '', route: '/host/orders' },
    { key: 'reviews', value: 0, label: '评价总数', icon: Star, color: 'green', prefix: '', route: '/host/reviews' },
    { key: 'rating', value: 0, label: '平均评分', icon: Star, color: 'orange', prefix: '' },
    { key: 'pending', value: 0, label: '待处理订单', icon: Warning, color: 'red', prefix: '', route: '/host/orders' },
    { key: 'completed', value: 0, label: '已完成订单', icon: CircleCheck, color: 'cyan', prefix: '', route: '/host/orders' }
])

const statValueRefs = ref<(HTMLElement | null)[]>([])

interface TodoItem {
    key: string
    value: number | string
    label: string
    icon: any
    color: string
    route?: string
}

const todoList = computed<TodoItem[]>(() => [
    {
        key: 'pendingOrders',
        value: statsData.value?.pendingOrders || 0,
        label: '待确认订单',
        icon: Warning,
        color: 'red',
        route: '/host/orders'
    },
    {
        key: 'processingOrders',
        value: (statsData.value?.orderCount || 0) - (statsData.value?.completedOrders || 0) - (statsData.value?.cancelledOrders || 0),
        label: '处理中订单',
        icon: List,
        color: 'orange',
        route: '/host/orders'
    },
    {
        key: 'completedOrders',
        value: statsData.value?.completedOrders || 0,
        label: '本月已完成',
        icon: CircleCheck,
        color: 'blue',
        route: '/host/orders'
    },
    {
        key: 'reviewCount',
        value: statsData.value?.reviewCount || 0,
        label: '评价总数',
        icon: Star,
        color: 'green',
        route: '/host/reviews'
    }
])

// ========== 图表 ==========
const orderChartRef = ref<HTMLElement | null>(null)
let orderChart: echarts.ECharts | null = null
let animationFrames: number[] = []

// ========== 格式化函数 ==========
const formatStatValue = (value: number): string => {
    if (value === null || value === undefined || isNaN(value)) return '0'
    // 评分保留1位小数
    if (value < 10 && value > 0 && !Number.isInteger(value)) {
        return value.toFixed(1)
    }
    return value.toLocaleString()
}

const formatDate = (dateString: string): string => {
    if (!dateString) return '-'
    const date = new Date(dateString)
    if (isNaN(date.getTime())) return dateString
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${month}-${day}`
}

const formatAmount = (amount: number | string): string => {
    if (amount === null || amount === undefined) return '0.00'
    const num = typeof amount === 'string' ? parseFloat(amount) : amount
    return isNaN(num) ? '0.00' : num.toFixed(2)
}

const getOrderStatusType = (status: string): string => {
    const types: Record<string, string> = {
        'PENDING': 'warning',
        'CONFIRMED': 'primary',
        'PAID': 'success',
        'READY_FOR_CHECKIN': 'success',
        'CHECKED_IN': 'success',
        'COMPLETED': 'info',
        'CANCELLED': 'danger',
        'CANCELLED_BY_USER': 'danger',
        'CANCELLED_SYSTEM': 'danger',
        'REJECTED': 'danger',
        'PAYMENT_PENDING': 'warning'
    }
    return types[status] || 'info'
}

const getOrderStatusText = (status: string): string => {
    const texts: Record<string, string> = {
        'PENDING': '待确认',
        'CONFIRMED': '已确认',
        'PAID': '已支付',
        'READY_FOR_CHECKIN': '待入住',
        'CHECKED_IN': '已入住',
        'COMPLETED': '已完成',
        'CANCELLED': '已取消',
        'CANCELLED_BY_USER': '用户取消',
        'CANCELLED_SYSTEM': '系统取消',
        'REJECTED': '已拒绝',
        'PAYMENT_PENDING': '待支付'
    }
    return texts[status] || status
}

// ========== 数字滚动动画 ==========
const animateValue = (el: HTMLElement, target: number, duration: number = 1000) => {
    const startTime = performance.now()
    const isDecimal = target < 10 && target > 0 && !Number.isInteger(target)

    const step = (currentTime: number) => {
        const elapsed = currentTime - startTime
        const progress = Math.min(elapsed / duration, 1)
        const easeProgress = progress === 1 ? 1 : 1 - Math.pow(2, -10 * progress)
        const current = easeProgress * target

        if (isDecimal) {
            el.textContent = current.toFixed(1)
        } else {
            el.textContent = Math.floor(current).toLocaleString()
        }

        if (progress < 1) {
            const frameId = requestAnimationFrame(step)
            animationFrames.push(frameId)
        } else {
            // 确保最终值正确
            if (isDecimal) {
                el.textContent = target.toFixed(1)
            } else {
                el.textContent = target.toLocaleString()
            }
        }
    }

    const frameId = requestAnimationFrame(step)
    animationFrames.push(frameId)
}

// ========== 卡片点击 ==========
const handleCardClick = (route?: string) => {
    if (route) {
        router.push(route)
    }
}

// ========== 图表初始化 ==========
const initOrderChart = () => {
    if (!orderChartRef.value || !statsData.value) return

    const stats = statsData.value
    const data = [
        { name: '待处理', value: stats.pendingOrders || 0, itemStyle: { color: '#f59e0b' } },
        { name: '已完成', value: stats.completedOrders || 0, itemStyle: { color: '#22c55e' } },
        { name: '已取消', value: stats.cancelledOrders || 0, itemStyle: { color: '#ef4444' } },
        { name: '其他', value: Math.max(0, (stats.orderCount || 0) - (stats.pendingOrders || 0) - (stats.completedOrders || 0) - (stats.cancelledOrders || 0)), itemStyle: { color: '#3b82f6' } }
    ].filter(d => d.value > 0)

    const total = stats.orderCount || 0

    orderChart = echarts.init(orderChartRef.value)
    orderChart.setOption({
        tooltip: {
            trigger: 'item',
            backgroundColor: 'rgba(255, 255, 255, 0.95)',
            borderColor: '#e2e8f0',
            borderWidth: 1,
            textStyle: { color: '#1e293b' },
            formatter: (params: any) => {
                return `<strong>${params.name}</strong><br/>${params.value} 单 (${params.percent}%)`
            }
        },
        legend: {
            orient: 'vertical',
            right: '5%',
            top: 'center',
            textStyle: { color: '#64748b', fontSize: 11 },
            itemWidth: 10,
            itemHeight: 10,
            itemGap: 8
        },
        series: [
            {
                name: '订单状态',
                type: 'pie',
                radius: ['45%', '70%'],
                center: ['35%', '50%'],
                avoidLabelOverlap: false,
                label: { show: false },
                emphasis: {
                    label: {
                        show: true,
                        fontSize: 16,
                        fontWeight: 'bold',
                        formatter: () => `${total}单`
                    }
                },
                labelLine: { show: false },
                data: data
            }
        ]
    })
}

// ========== 辅助函数：安全提取 API 响应数据 ==========
const extractData = (response: any): any => {
    // 如果是 AxiosResponse（有 data 属性且是对象），返回 response.data
    if (response && typeof response === 'object' && 'data' in response && response.data !== undefined) {
        return response.data
    }
    // 否则直接返回（可能是纯数据、数组、数字等）
    return response
}

// ========== 数据获取 ==========
const fetchDashboardData = async () => {
    loading.value = true

    // 核心数据：统计（独立调用，互不干扰）
    let statsResult: HostStatisticsData | null = null
    try {
        statsResult = await getHostStatistics()
    } catch (err) {
        console.error('统计接口失败:', err)
    }

    // 最近订单
    let ordersResult: HostOrderData[] = []
    try {
        ordersResult = await getHostRecentOrders(5)
    } catch (err) {
        console.error('订单接口失败:', err)
    }

    // 赋值到响应式数据
    statsData.value = statsResult
    recentOrders.value = ordersResult

    // 更新统计卡片
    if (statsResult) {
        statCards.value[0].value = statsResult.homestayCount || 0
        statCards.value[1].value = statsResult.orderCount || 0
        statCards.value[2].value = statsResult.reviewCount || 0
        statCards.value[3].value = statsResult.rating || 0
        statCards.value[4].value = statsResult.pendingOrders || 0
        statCards.value[5].value = statsResult.completedOrders || 0
    }

    // 数字滚动动画
    setTimeout(() => {
        statValueRefs.value.forEach((el, i) => {
            if (el) animateValue(el, statCards.value[i].value)
        })
    }, 300)

    // 初始化订单分布图
    initOrderChart()

    // 加载评价数据
    await loadSecondaryData()

    loading.value = false
}

const loadSecondaryData = async () => {
    // 评价
    try {
        const reviewsResponse = await getHostReviews({ page: 0, size: 3 })
        const reviewsData = extractData(reviewsResponse)
        if (reviewsData && Array.isArray(reviewsData.content)) {
            recentReviews.value = reviewsData.content
        } else if (Array.isArray(reviewsData)) {
            recentReviews.value = reviewsData.slice(0, 3)
        }
    } catch (err) {
        console.error('评价接口失败:', err)
    }
}

// ========== 窗口大小调整 ==========
const handleResize = () => {
    orderChart?.resize()
}

// ========== 生命周期 ==========
onMounted(() => {
    fetchDashboardData()
    window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
    animationFrames.forEach(id => cancelAnimationFrame(id))
    orderChart?.dispose()
    window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
/* ========== 基础布局 ========== */
.dashboard {
    padding: 20px;
    min-height: 100vh;
    background: linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%);
}

.loading-overlay {
    display: flex;
    justify-content: center;
    align-items: center;
    min-height: 300px;
    flex-direction: column;
    gap: 10px;
    color: #606266;
}

/* ========== 入场动画 ========== */
@keyframes slideUp {
    from {
        opacity: 0;
        transform: translateY(20px);
    }

    to {
        opacity: 1;
        transform: translateY(0);
    }
}

/* ========== 欢迎区 ========== */
.greeting-section {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 24px;
    padding: 24px 28px;
    background: rgba(255, 255, 255, 0.85);
    backdrop-filter: blur(20px);
    -webkit-backdrop-filter: blur(20px);
    border-radius: 16px;
    border: 1px solid rgba(226, 232, 240, 0.8);
    box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
    animation: slideUp 0.5s ease-out forwards;
}

.greeting-title {
    font-size: 24px;
    font-weight: 600;
    color: #1e293b;
    margin: 0 0 6px;
}

.greeting-sub {
    font-size: 14px;
    color: #64748b;
    margin: 0;
}

.greeting-right {
    display: flex;
    align-items: center;
    gap: 20px;
}

.quick-actions {
    display: flex;
    gap: 8px;
}

.quick-actions .el-button {
    display: inline-flex;
    align-items: center;
    gap: 4px;
}

.date-display {
    text-align: right;
}

.date-week {
    display: block;
    font-size: 14px;
    color: #6366f1;
    font-weight: 500;
    margin-bottom: 4px;
}

.date-full {
    font-size: 13px;
    color: #64748b;
}

/* ========== 统计卡片 ========== */
.stats-grid {
    display: grid;
    grid-template-columns: repeat(6, 1fr);
    gap: 16px;
    margin-bottom: 24px;
}

.stat-card {
    position: relative;
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 20px 18px;
    background: rgba(255, 255, 255, 0.85);
    backdrop-filter: blur(20px);
    -webkit-backdrop-filter: blur(20px);
    border-radius: 16px;
    border: 1px solid rgba(226, 232, 240, 0.8);
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
    transition: transform 0.3s ease, box-shadow 0.3s ease;
    animation: slideUp 0.5s ease-out forwards;
    opacity: 0;
    cursor: default;
}

.stat-card.is-clickable {
    cursor: pointer;
}

.stat-card:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
}

.stat-card__icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 48px;
    height: 48px;
    border-radius: 12px;
    font-size: 22px;
    color: #fff;
    flex-shrink: 0;
}

/* 渐变色图标 */
.stat-card--blue .stat-card__icon {
    background: linear-gradient(135deg, #3b82f6, #60a5fa);
}

.stat-card--purple .stat-card__icon {
    background: linear-gradient(135deg, #8b5cf6, #a78bfa);
}

.stat-card--green .stat-card__icon {
    background: linear-gradient(135deg, #22c55e, #4ade80);
}

.stat-card--orange .stat-card__icon {
    background: linear-gradient(135deg, #f97316, #fb923c);
}

.stat-card--red .stat-card__icon {
    background: linear-gradient(135deg, #ef4444, #f87171);
}

.stat-card--cyan .stat-card__icon {
    background: linear-gradient(135deg, #06b6d4, #22d3ee);
}

.stat-card__content {
    flex: 1;
    min-width: 0;
}

.stat-card__value {
    font-size: 24px;
    font-weight: 700;
    color: #1e293b;
    line-height: 1.2;
    font-variant-numeric: tabular-nums;
}

.stat-card__label {
    font-size: 12px;
    color: #64748b;
    margin-top: 4px;
}

/* ========== 待办事项 ========== */
.todo-section {
    margin-bottom: 24px;
}

.section-header {
    margin-bottom: 12px;
}

.section-title {
    font-size: 16px;
    font-weight: 600;
    color: #1e293b;
}

.todo-card {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 16px;
    background: rgba(255, 255, 255, 0.85);
    backdrop-filter: blur(20px);
    -webkit-backdrop-filter: blur(20px);
    border-radius: 12px;
    border: 1px solid rgba(226, 232, 240, 0.8);
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
    transition: transform 0.3s ease, box-shadow 0.3s ease;
    cursor: pointer;
    animation: slideUp 0.5s ease-out 0.2s forwards;
    opacity: 0;
}

.todo-card:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
}

.todo-card.is-zero {
    opacity: 0.7;
}

.todo-card__icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    border-radius: 10px;
    color: #fff;
    flex-shrink: 0;
}

.todo-card__icon--red {
    background: linear-gradient(135deg, #ef4444, #f87171);
}

.todo-card__icon--orange {
    background: linear-gradient(135deg, #f97316, #fb923c);
}

.todo-card__icon--green {
    background: linear-gradient(135deg, #22c55e, #4ade80);
}

.todo-card__icon--blue {
    background: linear-gradient(135deg, #3b82f6, #60a5fa);
}

.todo-card__content {
    flex: 1;
    min-width: 0;
}

.todo-card__value {
    font-size: 20px;
    font-weight: 700;
    color: #1e293b;
    line-height: 1.2;
}

.todo-card__label {
    font-size: 12px;
    color: #64748b;
    margin-top: 2px;
}

.todo-card__arrow {
    color: #94a3b8;
    font-size: 14px;
}

/* ========== 图表区 ========== */
.charts-grid {
    display: grid;
    grid-template-columns: 1fr 380px;
    gap: 20px;
    margin-bottom: 24px;
    animation: slideUp 0.5s ease-out 0.3s forwards;
    opacity: 0;
}

.chart-card {
    background: rgba(255, 255, 255, 0.85);
    backdrop-filter: blur(20px);
    -webkit-backdrop-filter: blur(20px);
    border-radius: 16px;
    border: 1px solid rgba(226, 232, 240, 0.8);
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
    padding: 20px;
    transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.chart-card:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
}

.chart-card__header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
}

.chart-card__title {
    font-size: 15px;
    font-weight: 600;
    color: #1e293b;
}

.chart-card__body {
    height: 280px;
    position: relative;
}

.chart-loading {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(255, 255, 255, 0.6);
}

/* ========== 底部两列 ========== */
.bottom-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 20px;
    animation: slideUp 0.5s ease-out 0.4s forwards;
    opacity: 0;
}

.bottom-card {
    background: rgba(255, 255, 255, 0.85);
    backdrop-filter: blur(20px);
    -webkit-backdrop-filter: blur(20px);
    border-radius: 16px;
    border: 1px solid rgba(226, 232, 240, 0.8);
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
    padding: 20px;
    transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.bottom-card:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
}

.card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
}

.card-header__title {
    font-size: 15px;
    font-weight: 600;
    color: #1e293b;
}

.empty-data {
    text-align: center;
    color: #909399;
    padding: 40px 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
}

.empty-data p {
    margin: 0;
    font-size: 14px;
}

/* ========== 订单表格 ========== */
.ellipsis-text {
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 130px;
    display: inline-block;
}

.date-range {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
}

.date-separator {
    color: #909399;
    font-size: 11px;
}

.amount {
    font-weight: 600;
    color: #f59e0b;
}

/* ========== 评价列表 ========== */
.review-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.review-item {
    padding: 12px;
    background: #f8fafc;
    border-radius: 10px;
    border: 1px solid #e2e8f0;
}

.review-item__header {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 8px;
}

.review-item__meta {
    flex: 1;
    min-width: 0;
}

.review-item__name {
    font-size: 13px;
    font-weight: 500;
    color: #1e293b;
    margin-bottom: 2px;
}

.review-item__date {
    font-size: 12px;
    color: #94a3b8;
    flex-shrink: 0;
}

.review-item__content {
    margin-bottom: 6px;
}

.review-item__text {
    font-size: 13px;
    color: #475569;
    line-height: 1.5;
    margin: 0;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

.review-item__homestay {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: #94a3b8;
}

/* ========== 响应式适配 ========== */
@media (max-width: 1400px) {
    .stats-grid {
        grid-template-columns: repeat(3, 1fr);
    }
}

@media (max-width: 1200px) {
    .charts-grid {
        grid-template-columns: 1fr;
    }

    .bottom-grid {
        grid-template-columns: 1fr;
    }
}

@media (max-width: 992px) {
    .stats-grid {
        grid-template-columns: repeat(2, 1fr);
    }

    .greeting-section {
        flex-direction: column;
        align-items: flex-start;
        gap: 16px;
    }

    .greeting-right {
        width: 100%;
        justify-content: space-between;
    }
}

@media (max-width: 576px) {
    .dashboard {
        padding: 12px;
    }

    .stats-grid {
        grid-template-columns: 1fr;
    }

    .quick-actions {
        flex-wrap: wrap;
    }

    .greeting-title {
        font-size: 18px;
    }
}
</style>
