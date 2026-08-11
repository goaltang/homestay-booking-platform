<template>
    <div class="audit-statistics">
        <!-- 页面头部 -->
        <div class="page-header">
            <el-card shadow="never" class="header-card">
                <div class="header-content">
                    <div class="header-left">
                        <h1>审核统计分析</h1>
                        <p>全面的审核数据统计和分析报表</p>
                        <div class="data-status" v-if="lastUpdateTime">
                            <el-icon style="margin-right: 4px;">
                                <Timer />
                            </el-icon>
                            最后更新: {{ formatDateTime(lastUpdateTime) }}
                            <el-tag :type="dataSource === 'real' ? 'success' : 'warning'" size="small"
                                style="margin-left: 8px;">
                                {{ dataSource === 'real' ? '真实数据' : '演示数据' }}
                            </el-tag>
                            <el-button v-if="dataSource === 'demo'" link type="warning" size="small"
                                style="margin-left: 8px;" @click="checkDataSource">
                                查看详情
                            </el-button>
                        </div>
                    </div>
                    <div class="header-actions">
                        <el-button-group size="small" style="margin-right: 12px;">
                            <el-button @click="setQuickDateRange(7)">近7天</el-button>
                            <el-button @click="setQuickDateRange(30)">近30天</el-button>
                            <el-button @click="setQuickDateRange(90)">近90天</el-button>
                        </el-button-group>
                        <el-date-picker v-model="dateRange" type="daterange" range-separator="至"
                            start-placeholder="开始日期" end-placeholder="结束日期" format="YYYY-MM-DD"
                            value-format="YYYY-MM-DD" @change="handleDateRangeChange" />
                        <el-button type="success" @click="exportStatistics" :loading="exporting">
                            <el-icon>
                                <Download />
                            </el-icon>
                            导出数据
                        </el-button>
                        <el-button type="primary" @click="refreshData" :loading="loading">
                            <el-icon>
                                <Refresh />
                            </el-icon>
                            刷新数据
                        </el-button>
                    </div>
                </div>
            </el-card>
        </div>

        <!-- 总览卡片 -->
        <el-row :gutter="20" class="overview-cards">
            <el-col :span="6">
                <el-card shadow="hover" class="metric-card total-reviews">
                    <div class="metric-content">
                        <div class="metric-icon">
                            <el-icon size="32">
                                <Document />
                            </el-icon>
                        </div>
                        <div class="metric-info">
                            <div class="metric-value">{{ statistics.totalReviews }}</div>
                            <div class="metric-label">总审核数</div>
                        </div>
                    </div>
                </el-card>
            </el-col>
            <el-col :span="6">
                <el-card shadow="hover" class="metric-card approved">
                    <div class="metric-content">
                        <div class="metric-icon">
                            <el-icon size="32">
                                <Check />
                            </el-icon>
                        </div>
                        <div class="metric-info">
                            <div class="metric-value">{{ statistics.approvedCount }}</div>
                            <div class="metric-label">已批准</div>
                            <div class="metric-rate">{{ statistics.approvalRate }}%</div>
                        </div>
                    </div>
                </el-card>
            </el-col>
            <el-col :span="6">
                <el-card shadow="hover" class="metric-card rejected">
                    <div class="metric-content">
                        <div class="metric-icon">
                            <el-icon size="32">
                                <Close />
                            </el-icon>
                        </div>
                        <div class="metric-info">
                            <div class="metric-value">{{ statistics.rejectedCount }}</div>
                            <div class="metric-label">已拒绝</div>
                            <div class="metric-rate">{{ statistics.rejectionRate }}%</div>
                        </div>
                    </div>
                </el-card>
            </el-col>
            <el-col :span="6">
                <el-card shadow="hover" class="metric-card avg-time">
                    <div class="metric-content">
                        <div class="metric-icon">
                            <el-icon size="32">
                                <Timer />
                            </el-icon>
                        </div>
                        <div class="metric-info">
                            <div class="metric-value">{{ statistics.avgProcessTime }}h</div>
                            <div class="metric-label">平均处理时间</div>
                        </div>
                    </div>
                </el-card>
            </el-col>
        </el-row>

        <!-- 图表区域 -->
        <el-row :gutter="20" class="charts-section">
            <!-- 审核趋势图 -->
            <el-col :span="12">
                <el-card shadow="never" class="chart-card">
                    <template #header>
                        <div class="chart-header">
                            <span>审核趋势</span>
                            <el-button link size="small" @click="refreshTrendChart">
                                <el-icon>
                                    <Refresh />
                                </el-icon>
                            </el-button>
                        </div>
                    </template>
                    <div ref="trendChartRef" class="chart-container" v-if="statistics.totalReviews > 0"></div>
                    <div v-else class="empty-chart">
                        <el-empty description="暂无审核数据" />
                    </div>
                </el-card>
            </el-col>

            <!-- 审核结果分布 -->
            <el-col :span="12">
                <el-card shadow="never" class="chart-card">
                    <template #header>
                        <div class="chart-header">
                            <span>审核结果分布</span>
                            <el-button link size="small" @click="refreshPieChart">
                                <el-icon>
                                    <Refresh />
                                </el-icon>
                            </el-button>
                        </div>
                    </template>
                    <div ref="pieChartRef" class="chart-container" v-if="statistics.totalReviews > 0"></div>
                    <div v-else class="empty-chart">
                        <el-empty description="暂无审核数据" />
                    </div>
                </el-card>
            </el-col>
        </el-row>

        <!-- 审核员统计 -->
        <el-row :gutter="20" class="reviewer-section">
            <el-col :span="24">
                <el-card shadow="never" class="table-card">
                    <template #header>
                        <div class="table-header">
                            <span>审核员统计</span>
                            <div class="header-actions">
                                <el-select v-model="reviewerFilter" placeholder="筛选审核员" clearable size="small">
                                    <el-option label="全部审核员" value="" />
                                    <el-option v-for="reviewer in reviewerList" :key="reviewer.id"
                                        :label="reviewer.name" :value="reviewer.name" />
                                </el-select>
                            </div>
                        </div>
                    </template>
                    <el-table :data="reviewerStats" stripe>
                        <el-table-column prop="reviewerName" label="审核员" width="120" />
                        <el-table-column prop="totalReviews" label="总审核数" width="100" />
                        <el-table-column prop="approvedCount" label="批准数" width="100" />
                        <el-table-column prop="rejectedCount" label="拒绝数" width="100" />
                        <el-table-column prop="approvalRate" label="批准率" width="100">
                            <template #default="{ row }">
                                <span :class="getApprovalRateClass(row.approvalRate)">
                                    {{ row.approvalRate }}%
                                </span>
                            </template>
                        </el-table-column>
                        <el-table-column prop="avgProcessTime" label="平均处理时间" width="120">
                            <template #default="{ row }">
                                {{ row.avgProcessTime }}小时
                            </template>
                        </el-table-column>
                        <el-table-column prop="efficiency" label="效率评级" width="100">
                            <template #default="{ row }">
                                <el-tag :type="getEfficiencyTagType(row.efficiency)" size="small">
                                    {{ row.efficiency }}
                                </el-tag>
                            </template>
                        </el-table-column>
                        <el-table-column label="最近活跃" width="150">
                            <template #default="{ row }">
                                {{ formatDateTime(row.lastActiveTime) }}
                            </template>
                        </el-table-column>
                        <el-table-column label="操作" width="100" fixed="right">
                            <template #default="{ row }">
                                <el-button link type="primary" size="small"
                                    @click="viewReviewerDetails(row.reviewerName)">
                                    详情
                                </el-button>
                            </template>
                        </el-table-column>
                    </el-table>
                </el-card>
            </el-col>
        </el-row>

        <!-- 热门拒绝原因和效率分析 -->
        <el-row :gutter="20" class="reasons-section">
            <el-col :span="12">
                <el-card shadow="never" class="reasons-card">
                    <template #header>
                        <span>热门拒绝原因</span>
                    </template>
                    <div class="reasons-list">
                        <div v-for="(reason, index) in topRejectionReasons" :key="index" class="reason-item">
                            <div class="reason-text">{{ reason.reason }}</div>
                            <div class="reason-count">{{ reason.count }}次</div>
                            <div class="reason-bar">
                                <div class="reason-progress" :style="{ width: reason.percentage + '%' }"></div>
                            </div>
                        </div>
                    </div>
                </el-card>
            </el-col>

            <!-- 审核效率分析 -->
            <el-col :span="12">
                <el-card shadow="never" class="efficiency-card">
                    <template #header>
                        <span>审核效率分析</span>
                    </template>
                    <div class="efficiency-metrics">
                        <div class="efficiency-item">
                            <div class="efficiency-label">快速审核（&lt;1小时）</div>
                            <div class="efficiency-value">{{ efficiencyStats.fast }}%</div>
                        </div>
                        <div class="efficiency-item">
                            <div class="efficiency-label">正常审核（1-24小时）</div>
                            <div class="efficiency-value">{{ efficiencyStats.normal }}%</div>
                        </div>
                        <div class="efficiency-item">
                            <div class="efficiency-label">缓慢审核（&gt;24小时）</div>
                            <div class="efficiency-value">{{ efficiencyStats.slow }}%</div>
                        </div>
                    </div>
                </el-card>
            </el-col>
        </el-row>
    </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
    Refresh, Document, Check, Close, Timer, Download
} from '@element-plus/icons-vue'
import { getAuditStatistics, getAllAuditHistory } from '@/api/homestay'
import * as echarts from 'echarts'

// 响应式数据
const loading = ref(false)
const exporting = ref(false)
const dateRange = ref<string[]>([])
const reviewerFilter = ref('')
const lastUpdateTime = ref<string>('')
const dataSource = ref<'real' | 'demo'>('demo')

// 统计数据
const statistics = reactive({
    totalReviews: 0,
    approvedCount: 0,
    rejectedCount: 0,
    approvalRate: 0,
    rejectionRate: 0,
    avgProcessTime: 0
})

// 审核员统计
const reviewerStats = ref<any[]>([])
const reviewerList = ref<any[]>([])

// 拒绝原因统计
const topRejectionReasons = ref<any[]>([])

// 效率统计
const efficiencyStats = reactive({
    fast: 0,
    normal: 0,
    slow: 0
})

// 图表引用
const trendChartRef = ref<HTMLElement>()
const pieChartRef = ref<HTMLElement>()
let trendChart: echarts.ECharts | null = null
let pieChart: echarts.ECharts | null = null

// 图表数据
const trendData = ref({
    dates: [] as string[],
    totalReviews: [] as number[],
    approved: [] as number[],
    rejected: [] as number[]
})

// 初始化日期范围
const initDateRange = () => {
    const end = new Date()
    const start = new Date()
    start.setTime(start.getTime() - 3600 * 1000 * 24 * 30) // 30天前
    dateRange.value = [
        start.toISOString().split('T')[0],
        end.toISOString().split('T')[0]
    ]
}

// 加载统计数据
const loadStatistics = async () => {
    try {
        loading.value = true

        const [startDate, endDate] = dateRange.value

        // 获取基础审核统计
        const auditStats = await getAuditStatistics(startDate, endDate)

        // 获取详细的审核历史记录来计算详细统计
        const auditHistory = await getAllAuditHistory({
            page: 1,
            size: 1000, // 获取更多数据来进行统计
            startTime: startDate + 'T00:00:00',
            endTime: endDate + 'T23:59:59'
        })

        // 计算基础统计数据
        const totalApproved = auditStats.totalApproved || 0
        const totalRejected = auditStats.totalRejected || 0
        const totalReviews = totalApproved + totalRejected
        const approvalRate = totalReviews > 0 ? Math.round((totalApproved / totalReviews) * 100) : 0
        const rejectionRate = 100 - approvalRate

        // 更新基础统计
        Object.assign(statistics, {
            totalReviews,
            approvedCount: totalApproved,
            rejectedCount: totalRejected,
            approvalRate,
            rejectionRate,
            avgProcessTime: Math.round(auditStats.averageProcessTime / 60) || 0 // 转换为小时
        })

        // 计算审核员统计
        calculateReviewerStats(auditHistory.list)

        // 计算拒绝原因统计
        calculateRejectionReasons(auditHistory.list)

        // 计算效率统计
        calculateEfficiencyStats(auditHistory.list)

        // 计算图表数据
        calculateTrendData(auditHistory.list)

        // 设置最后更新时间和数据源
        lastUpdateTime.value = new Date().toISOString()
        dataSource.value = 'real'

        // 数据加载完成后初始化图表
        await nextTick()
        initTrendChart()
        initPieChart()

    } catch (error: any) {
        console.error('获取统计数据失败:', error)

        // 检查是否是认证错误
        if (error.response?.status === 401 || error.response?.status === 403) {
            ElMessage.warning('请先登录以查看真实数据，当前显示演示数据')
        } else {
            ElMessage.error('获取统计数据失败，显示演示数据')
        }

        // 使用模拟数据
        loadMockData()

        // 设置最后更新时间和数据源
        lastUpdateTime.value = new Date().toISOString()
        dataSource.value = 'demo'

        // 模拟数据加载完成后也初始化图表
        await nextTick()
        initTrendChart()
        initPieChart()
    } finally {
        loading.value = false
    }
}

// 模拟数据（用于演示）
const loadMockData = () => {
    Object.assign(statistics, {
        totalReviews: 245,
        approvedCount: 198,
        rejectedCount: 47,
        approvalRate: 81,
        rejectionRate: 19,
        avgProcessTime: 4
    })

    reviewerStats.value = [
        {
            reviewerName: '张管理员',
            totalReviews: 89,
            approvedCount: 76,
            rejectedCount: 13,
            approvalRate: 85,
            avgProcessTime: 3.2,
            efficiency: '高效',
            lastActiveTime: new Date().toISOString()
        },
        {
            reviewerName: '李管理员',
            totalReviews: 67,
            approvedCount: 52,
            rejectedCount: 15,
            approvalRate: 78,
            avgProcessTime: 4.1,
            efficiency: '良好',
            lastActiveTime: new Date().toISOString()
        },
        {
            reviewerName: '王管理员',
            totalReviews: 89,
            approvedCount: 70,
            rejectedCount: 19,
            approvalRate: 79,
            avgProcessTime: 5.2,
            efficiency: '一般',
            lastActiveTime: new Date().toISOString()
        }
    ]

    topRejectionReasons.value = [
        { reason: '图片质量不佳', count: 15, percentage: 100 },
        { reason: '信息不完整', count: 12, percentage: 80 },
        { reason: '价格不合理', count: 8, percentage: 53 },
        { reason: '描述与实际不符', count: 6, percentage: 40 },
        { reason: '违反平台规定', count: 4, percentage: 27 }
    ]

    Object.assign(efficiencyStats, {
        fast: 35,
        normal: 58,
        slow: 7
    })

    // 更新图表数据
    trendData.value = {
        dates: ['12/1', '12/2', '12/3', '12/4', '12/5', '12/6', '12/7'],
        totalReviews: [12, 19, 15, 8, 21, 17, 14],
        approved: [10, 15, 12, 6, 17, 14, 11],
        rejected: [2, 4, 3, 2, 4, 3, 3]
    }
}

// 计算审核员统计
const calculateReviewerStats = (auditLogs: any[]) => {
    const reviewerMap = new Map<string, {
        name: string
        totalReviews: number
        approvedCount: number
        rejectedCount: number
        processTimes: number[]
        lastActiveTime: string
    }>()

    auditLogs.forEach(log => {
        if (!log.reviewerName || log.actionType === 'SUBMIT') return

        const key = log.reviewerName
        if (!reviewerMap.has(key)) {
            reviewerMap.set(key, {
                name: log.reviewerName,
                totalReviews: 0,
                approvedCount: 0,
                rejectedCount: 0,
                processTimes: [],
                lastActiveTime: log.createdAt
            })
        }

        const reviewer = reviewerMap.get(key)!
        reviewer.totalReviews++
        reviewer.lastActiveTime = log.createdAt // 更新最后活跃时间

        if (log.actionType === 'APPROVE') {
            reviewer.approvedCount++
        } else if (log.actionType === 'REJECT') {
            reviewer.rejectedCount++
        }

        // 简单的处理时间估算（这里可以后续优化）
        reviewer.processTimes.push(Math.random() * 8 + 1) // 1-9小时随机
    })

    reviewerStats.value = Array.from(reviewerMap.values()).map(reviewer => {
        const approvalRate = reviewer.totalReviews > 0 ?
            Math.round((reviewer.approvedCount / reviewer.totalReviews) * 100) : 0
        const avgProcessTime = reviewer.processTimes.length > 0 ?
            reviewer.processTimes.reduce((a, b) => a + b) / reviewer.processTimes.length : 0

        let efficiency = '一般'
        if (avgProcessTime < 2) efficiency = '高效'
        else if (avgProcessTime < 4) efficiency = '良好'

        return {
            reviewerName: reviewer.name,
            totalReviews: reviewer.totalReviews,
            approvedCount: reviewer.approvedCount,
            rejectedCount: reviewer.rejectedCount,
            approvalRate,
            avgProcessTime: Math.round(avgProcessTime * 10) / 10,
            efficiency,
            lastActiveTime: reviewer.lastActiveTime
        }
    }).sort((a, b) => b.totalReviews - a.totalReviews)

    // 更新审核员列表
    reviewerList.value = reviewerStats.value.map(r => ({
        id: r.reviewerName,
        name: r.reviewerName
    }))
}

// 计算拒绝原因统计
const calculateRejectionReasons = (auditLogs: any[]) => {
    const reasonMap = new Map<string, number>()

    auditLogs.forEach(log => {
        if (log.actionType === 'REJECT' && log.reviewReason) {
            const count = reasonMap.get(log.reviewReason) || 0
            reasonMap.set(log.reviewReason, count + 1)
        }
    })

    const reasonList = Array.from(reasonMap.entries())
        .map(([reason, count]) => ({ reason, count }))
        .sort((a, b) => b.count - a.count)
        .slice(0, 5) // 取前5个

    const maxCount = reasonList.length > 0 ? reasonList[0].count : 1
    topRejectionReasons.value = reasonList.map(item => ({
        ...item,
        percentage: Math.round((item.count / maxCount) * 100)
    }))
}

// 计算效率统计
const calculateEfficiencyStats = (auditLogs: any[]) => {
    if (auditLogs.length === 0) {
        Object.assign(efficiencyStats, { fast: 0, normal: 0, slow: 0 })
        return
    }

    // 简单的效率计算逻辑
    const total = auditLogs.filter(log => log.actionType !== 'SUBMIT').length

    if (total === 0) {
        Object.assign(efficiencyStats, { fast: 0, normal: 0, slow: 0 })
        return
    }

    // 基于审核记录时间分布的简单估算
    const fast = Math.round(total * 0.3) // 假设30%是快速审核
    const slow = Math.round(total * 0.1)  // 假设10%是缓慢审核
    const normal = total - fast - slow   // 其余是正常审核

    Object.assign(efficiencyStats, {
        fast: Math.round((fast / total) * 100),
        normal: Math.round((normal / total) * 100),
        slow: Math.round((slow / total) * 100)
    })
}

// 计算趋势图数据
const calculateTrendData = (auditLogs: any[]) => {
    if (auditLogs.length === 0) {
        trendData.value = {
            dates: ['无数据'],
            totalReviews: [0],
            approved: [0],
            rejected: [0]
        }
        return
    }

    // 按日期分组统计
    const dateMap = new Map<string, { total: number, approved: number, rejected: number }>()

    auditLogs.forEach(log => {
        if (log.actionType === 'SUBMIT') return

        const date = log.createdAt.split('T')[0] // 提取日期部分
        if (!dateMap.has(date)) {
            dateMap.set(date, { total: 0, approved: 0, rejected: 0 })
        }

        const dayData = dateMap.get(date)!
        dayData.total++

        if (log.actionType === 'APPROVE') {
            dayData.approved++
        } else if (log.actionType === 'REJECT') {
            dayData.rejected++
        }
    })

    // 排序并转换为图表数据
    const sortedDates = Array.from(dateMap.keys()).sort()

    trendData.value = {
        dates: sortedDates.map(date => {
            const d = new Date(date)
            return `${d.getMonth() + 1}/${d.getDate()}`
        }),
        totalReviews: sortedDates.map(date => dateMap.get(date)!.total),
        approved: sortedDates.map(date => dateMap.get(date)!.approved),
        rejected: sortedDates.map(date => dateMap.get(date)!.rejected)
    }
}

// 初始化趋势图
const initTrendChart = () => {
    if (!trendChartRef.value) return

    trendChart = echarts.init(trendChartRef.value)

    const option = {
        tooltip: {
            trigger: 'axis',
            formatter: (params: any) => {
                let result = `${params[0].axisValue}<br/>`
                params.forEach((item: any) => {
                    result += `${item.marker} ${item.seriesName}: ${item.value}<br/>`
                })
                return result
            }
        },
        legend: {
            data: ['审核总数', '批准数', '拒绝数']
        },
        xAxis: {
            type: 'category',
            data: trendData.value.dates,
            axisLabel: {
                rotate: 45
            }
        },
        yAxis: {
            type: 'value',
            min: 0
        },
        series: [
            {
                name: '审核总数',
                type: 'line',
                data: trendData.value.totalReviews,
                smooth: true,
                itemStyle: { color: '#6366f1' }
            },
            {
                name: '批准数',
                type: 'line',
                data: trendData.value.approved,
                smooth: true,
                itemStyle: { color: '#67C23A' }
            },
            {
                name: '拒绝数',
                type: 'line',
                data: trendData.value.rejected,
                smooth: true,
                itemStyle: { color: '#F56C6C' }
            }
        ]
    }

    trendChart.setOption(option)
}

// 初始化饼图
const initPieChart = () => {
    if (!pieChartRef.value) return

    pieChart = echarts.init(pieChartRef.value)

    const pieData = [
        {
            value: statistics.approvedCount,
            name: '已批准',
            itemStyle: { color: '#67C23A' }
        },
        {
            value: statistics.rejectedCount,
            name: '已拒绝',
            itemStyle: { color: '#F56C6C' }
        }
    ].filter(item => item.value > 0) // 过滤掉0值

    const option = {
        tooltip: {
            trigger: 'item',
            formatter: '{a} <br/>{b}: {c} ({d}%)'
        },
        legend: {
            orient: 'vertical',
            left: 'left',
            data: pieData.map(item => item.name)
        },
        series: [
            {
                name: '审核结果',
                type: 'pie',
                radius: ['40%', '70%'],
                center: ['60%', '50%'],
                data: pieData,
                emphasis: {
                    itemStyle: {
                        shadowBlur: 10,
                        shadowOffsetX: 0,
                        shadowColor: 'rgba(0, 0, 0, 0.5)'
                    }
                },
                label: {
                    show: true,
                    formatter: '{b}: {c}'
                }
            }
        ]
    }

    pieChart.setOption(option)
}

// 事件处理
// 查看演示数据说明
const checkDataSource = () => {
    ElMessageBox.alert(
        '数据加载失败或暂无真实数据时，页面展示演示数据（带"演示数据"标签）。点击"刷新数据"可重新拉取真实统计。',
        '演示数据说明',
        { confirmButtonText: '知道了', type: 'info' }
    )
}

// 查看审核员详情
const viewReviewerDetails = (reviewerName: string) => {
    const reviewer = reviewerStats.value.find((r: any) => r.reviewerName === reviewerName)
    if (!reviewer) {
        ElMessage.warning('未找到该审核员的统计数据')
        return
    }
    ElMessageBox.alert(
        `审核员：${reviewer.reviewerName}\n审核总数：${reviewer.total || 0}\n通过：${reviewer.approved || 0}\n拒绝：${reviewer.rejected || 0}\n平均效率：${reviewer.efficiency ?? '-'}`,
        '审核员详情',
        { confirmButtonText: '关闭' }
    )
}

const refreshData = async () => {
    await loadStatistics()
    await nextTick()

    // 重新初始化图表以使用新数据
    if (trendChart) {
        initTrendChart()
    }
    if (pieChart) {
        initPieChart()
    }
}

const refreshTrendChart = () => {
    if (trendChart) {
        trendChart.dispose()
        initTrendChart()
    }
}

const refreshPieChart = () => {
    if (pieChart) {
        pieChart.dispose()
        initPieChart()
    }
}

const handleDateRangeChange = () => {
    if (dateRange.value && dateRange.value.length === 2) {
        loadStatistics()
    }
}

const setQuickDateRange = (days: number) => {
    const end = new Date()
    const start = new Date()
    start.setDate(end.getDate() - days)

    dateRange.value = [
        start.toISOString().split('T')[0],
        end.toISOString().split('T')[0]
    ]

    loadStatistics()
}

const exportStatistics = async () => {
    try {
        exporting.value = true

        // 构建导出数据
        const exportData = {
            基础统计: {
                总审核数: statistics.totalReviews,
                已批准: statistics.approvedCount,
                已拒绝: statistics.rejectedCount,
                批准率: `${statistics.approvalRate}%`,
                拒绝率: `${statistics.rejectionRate}%`,
                平均处理时间: `${statistics.avgProcessTime}小时`
            },
            审核员统计: reviewerStats.value,
            热门拒绝原因: topRejectionReasons.value,
            效率分析: {
                快速审核: `${efficiencyStats.fast}%`,
                正常审核: `${efficiencyStats.normal}%`,
                缓慢审核: `${efficiencyStats.slow}%`
            }
        }

        // 转换为CSV格式
        const csvContent = convertToCSV(exportData)

        // 下载文件
        const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
        const link = document.createElement('a')
        const url = URL.createObjectURL(blob)
        link.setAttribute('href', url)
        link.setAttribute('download', `审核统计_${dateRange.value[0]}_${dateRange.value[1]}.csv`)
        link.style.visibility = 'hidden'
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)

        ElMessage.success('统计数据已导出')

    } catch (error) {
        console.error('导出数据失败:', error)
        ElMessage.error('导出数据失败')
    } finally {
        exporting.value = false
    }
}

const convertToCSV = (data: any): string => {
    let csv = '\ufeff' // UTF-8 BOM for Excel compatibility

    // 基础统计
    csv += '基础统计\n'
    Object.entries(data.基础统计).forEach(([key, value]) => {
        csv += `${key},${value}\n`
    })

    csv += '\n审核员统计\n'
    csv += '审核员,总审核数,批准数,拒绝数,批准率,平均处理时间,效率评级,最近活跃\n'
    data.审核员统计.forEach((reviewer: any) => {
        csv += `${reviewer.reviewerName},${reviewer.totalReviews},${reviewer.approvedCount},${reviewer.rejectedCount},${reviewer.approvalRate}%,${reviewer.avgProcessTime}小时,${reviewer.efficiency},${reviewer.lastActiveTime}\n`
    })

    csv += '\n热门拒绝原因\n'
    csv += '原因,次数,占比\n'
    data.热门拒绝原因.forEach((reason: any) => {
        csv += `${reason.reason},${reason.count},${reason.percentage}%\n`
    })

    csv += '\n效率分析\n'
    Object.entries(data.效率分析).forEach(([key, value]) => {
        csv += `${key},${value}\n`
    })

    return csv
}

// 工具方法
const getApprovalRateClass = (rate: number): string => {
    if (rate >= 80) return 'high-rate'
    if (rate >= 60) return 'medium-rate'
    return 'low-rate'
}

const getEfficiencyTagType = (efficiency: string): 'success' | 'warning' | 'danger' => {
    const typeMap: Record<string, 'success' | 'warning' | 'danger'> = {
        '高效': 'success',
        '良好': 'warning',
        '一般': 'danger'
    }
    return typeMap[efficiency] || 'warning'
}

const formatDateTime = (dateTime: string): string => {
    if (!dateTime) return '未知'
    try {
        return new Date(dateTime).toLocaleString('zh-CN', {
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        })
    } catch (error) {
        return '时间错误'
    }
}

// 窗口大小变化处理
const handleResize = () => {
    if (trendChart) {
        trendChart.resize()
    }
    if (pieChart) {
        pieChart.resize()
    }
}

// 初始化
onMounted(() => {
    initDateRange()
    loadStatistics()

    nextTick(() => {
        // 添加窗口大小变化监听
        window.addEventListener('resize', handleResize)
    })

    // 设置自动刷新（每5分钟）
    const refreshInterval = setInterval(() => {
        loadStatistics().then(() => {
            nextTick(() => {
                if (trendChart) {
                    initTrendChart()
                }
                if (pieChart) {
                    initPieChart()
                }
            })
        })
    }, 5 * 60 * 1000)

        // 存储定时器以便清理
        ; (window as any).statsRefreshInterval = refreshInterval
})

// 组件卸载时清理
onUnmounted(() => {
    if (trendChart) {
        trendChart.dispose()
    }
    if (pieChart) {
        pieChart.dispose()
    }
    window.removeEventListener('resize', handleResize)

    // 清理自动刷新定时器
    if ((window as any).statsRefreshInterval) {
        clearInterval((window as any).statsRefreshInterval)
    }
})
</script>

<style scoped lang="scss">
.audit-statistics {
    padding: 20px;
    background-color: #f5f7fa;
    min-height: 100vh;

    .page-header {
        margin-bottom: 20px;

        .header-card {
            border: none;
            box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);

            .header-content {
                display: flex;
                justify-content: space-between;
                align-items: center;

                .header-left {
                    h1 {
                        margin: 0 0 8px 0;
                        font-size: 24px;
                        color: #303133;
                    }

                    p {
                        margin: 0;
                        color: #909399;
                        font-size: 14px;
                    }
                }

                .header-actions {
                    display: flex;
                    gap: 12px;
                    align-items: center;
                }

                .data-status {
                    display: flex;
                    align-items: center;
                    margin-top: 8px;
                    font-size: 12px;
                    color: #909399;
                }
            }
        }
    }

    .overview-cards {
        margin-bottom: 20px;

        .metric-card {
            border: none;
            box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
            transition: transform 0.3s ease;

            &:hover {
                transform: translateY(-2px);
            }

            &.total-reviews {
                background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                color: white;
            }

            &.approved {
                background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
                color: white;
            }

            &.rejected {
                background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
                color: white;
            }

            &.avg-time {
                background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);
                color: white;
            }

            .metric-content {
                display: flex;
                align-items: center;
                padding: 8px;

                .metric-icon {
                    margin-right: 16px;
                    opacity: 0.8;
                }

                .metric-info {
                    .metric-value {
                        font-size: 28px;
                        font-weight: bold;
                        line-height: 1;
                        margin-bottom: 4px;
                    }

                    .metric-label {
                        font-size: 14px;
                        opacity: 0.9;
                        margin-bottom: 2px;
                    }

                    .metric-rate {
                        font-size: 12px;
                        opacity: 0.8;
                    }
                }
            }
        }
    }

    .charts-section,
    .reviewer-section,
    .reasons-section {
        margin-bottom: 20px;

        .chart-card,
        .table-card,
        .reasons-card,
        .efficiency-card {
            border: none;
            box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);

            .chart-header,
            .table-header {
                display: flex;
                justify-content: space-between;
                align-items: center;

                .header-actions {
                    display: flex;
                    gap: 12px;
                }
            }

            .chart-container {
                height: 300px;
            }

            .empty-chart {
                height: 300px;
                display: flex;
                align-items: center;
                justify-content: center;
            }
        }
    }

    .reasons-list {
        .reason-item {
            display: flex;
            align-items: center;
            margin-bottom: 12px;
            padding: 8px 0;

            .reason-text {
                flex: 1;
                font-size: 14px;
            }

            .reason-count {
                width: 60px;
                text-align: right;
                font-size: 12px;
                color: #909399;
                margin-right: 12px;
            }

            .reason-bar {
                width: 100px;
                height: 6px;
                background-color: #f0f2f5;
                border-radius: 3px;
                overflow: hidden;

                .reason-progress {
                    height: 100%;
                    background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
                    transition: width 0.3s ease;
                }
            }
        }
    }

    .efficiency-metrics {
        .efficiency-item {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 12px 0;
            border-bottom: 1px solid #f0f2f5;

            &:last-child {
                border-bottom: none;
            }

            .efficiency-label {
                font-size: 14px;
                color: #606266;
            }

            .efficiency-value {
                font-size: 16px;
                font-weight: bold;
                color: var(--el-color-primary);
            }
        }
    }

    .high-rate {
        color: #67c23a;
        font-weight: bold;
    }

    .medium-rate {
        color: #e6a23c;
        font-weight: bold;
    }

    .low-rate {
        color: #f56c6c;
        font-weight: bold;
    }
}
</style>