<template>
    <div class="statistics-container">
        <el-card shadow="hover" class="filter-card">
            <el-form :inline="true" :model="dateRange">
                <el-form-item label="统计时间">
                    <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期"
                        end-placeholder="结束日期" :shortcuts="dateShortcuts" />
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" @click="fetchData">查询</el-button>
                    <el-button @click="exportData">导出数据</el-button>
                </el-form-item>
            </el-form>
        </el-card>

        <!-- 数据概览 -->
        <el-row :gutter="20" class="stat-row">
            <el-col :span="6" v-for="(item, index) in summaryData" :key="index">
                <el-card shadow="hover" class="summary-card">
                    <div class="summary-content">
                        <div class="summary-icon" :class="`icon-${index + 1}`">
                            <el-icon>
                                <component :is="item.icon"></component>
                            </el-icon>
                        </div>
                        <div class="summary-info">
                            <div class="summary-value">{{ item.value }}</div>
                            <div class="summary-title">{{ item.title }}</div>
                        </div>
                    </div>
                    <div class="summary-trend" v-if="item.trend !== 0">
                        <span :class="item.trend > 0 ? 'up' : 'down'">
                            {{ Math.abs(item.trend) }}%
                            <el-icon>
                                <component :is="item.trend > 0 ? 'ArrowUp' : 'ArrowDown'"></component>
                            </el-icon>
                        </span>
                        较上期
                    </div>
                </el-card>
            </el-col>
        </el-row>

        <!-- 图表区域 -->
        <el-row :gutter="20">
            <el-col :span="12">
                <el-card shadow="hover" class="chart-card">
                    <template #header>
                        <div class="card-header">
                            <span>订单趋势</span>
                        </div>
                    </template>
                    <div class="chart-container">
                        <div ref="orderChartRef" class="chart"></div>
                    </div>
                </el-card>
            </el-col>
            <el-col :span="12">
                <el-card shadow="hover" class="chart-card">
                    <template #header>
                        <div class="card-header">
                            <span>收入统计</span>
                        </div>
                    </template>
                    <div class="chart-container">
                        <div ref="incomeChartRef" class="chart"></div>
                    </div>
                </el-card>
            </el-col>
        </el-row>

        <el-row :gutter="20" style="margin-top: 20px;">
            <el-col :span="12">
                <el-card shadow="hover" class="chart-card">
                    <template #header>
                        <div class="card-header">
                            <span>用户增长</span>
                        </div>
                    </template>
                    <div class="chart-container">
                        <div ref="userChartRef" class="chart"></div>
                    </div>
                </el-card>
            </el-col>
            <el-col :span="12">
                <el-card shadow="hover" class="chart-card">
                    <template #header>
                        <div class="card-header">
                            <span>民宿分布</span>
                        </div>
                    </template>
                    <div class="chart-container">
                        <div ref="homestayChartRef" class="chart"></div>
                    </div>
                </el-card>
            </el-col>
        </el-row>
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import * as echarts from 'echarts';
import { getStatisticsData, exportStatistics } from '@/api/statistics';
import { ElMessage } from 'element-plus';

// 日期快捷选项
const dateShortcuts = [
    {
        text: '最近一周',
        value: () => {
            const end = new Date();
            const start = new Date();
            start.setTime(start.getTime() - 3600 * 1000 * 24 * 7);
            return [start, end];
        },
    },
    {
        text: '最近一个月',
        value: () => {
            const end = new Date();
            const start = new Date();
            start.setTime(start.getTime() - 3600 * 1000 * 24 * 30);
            return [start, end];
        },
    },
    {
        text: '最近三个月',
        value: () => {
            const end = new Date();
            const start = new Date();
            start.setTime(start.getTime() - 3600 * 1000 * 24 * 90);
            return [start, end];
        },
    },
];

interface SummaryItem {
    title: string;
    value: number | string;
    icon: string;
    trend: number;
}

// 日期范围
const dateRange = ref<[Date, Date]>([
    new Date(new Date().getTime() - 3600 * 1000 * 24 * 30),
    new Date()
]);

// 概览数据
const summaryData = ref<SummaryItem[]>([
    { title: '总订单数', value: 0, icon: 'ShoppingCart', trend: 0 },
    { title: '总收入', value: '¥0', icon: 'Money', trend: 0 },
    { title: '用户总数', value: 0, icon: 'User', trend: 0 },
    { title: '民宿总数', value: 0, icon: 'House', trend: 0 }
]);

// 图表引用
const orderChartRef = ref<HTMLElement | null>(null);
const incomeChartRef = ref<HTMLElement | null>(null);
const userChartRef = ref<HTMLElement | null>(null);
const homestayChartRef = ref<HTMLElement | null>(null);
let charts: echarts.ECharts[] = [];

// 初始化图表
const initCharts = () => {
    // 销毁旧图表
    charts.forEach(chart => chart?.dispose());
    charts = [];

    // 订单趋势图
    if (orderChartRef.value) {
        const chart = echarts.init(orderChartRef.value);
        charts.push(chart);
        chart.setOption({
            tooltip: {
                trigger: 'axis'
            },
            xAxis: {
                type: 'category',
                data: ['01-01', '01-02', '01-03', '01-04', '01-05', '01-06', '01-07']
            },
            yAxis: {
                type: 'value'
            },
            series: [
                {
                    name: '订单数',
                    type: 'line',
                    smooth: true,
                    data: [30, 40, 35, 50, 55, 45, 60]
                }
            ]
        });
    }

    // 收入统计图
    if (incomeChartRef.value) {
        const chart = echarts.init(incomeChartRef.value);
        charts.push(chart);
        chart.setOption({
            tooltip: {
                trigger: 'axis'
            },
            xAxis: {
                type: 'category',
                data: ['01-01', '01-02', '01-03', '01-04', '01-05', '01-06', '01-07']
            },
            yAxis: {
                type: 'value'
            },
            series: [
                {
                    name: '收入',
                    type: 'bar',
                    data: [3000, 4000, 3500, 5000, 5500, 4500, 6000]
                }
            ]
        });
    }

    // 用户增长图
    if (userChartRef.value) {
        const chart = echarts.init(userChartRef.value);
        charts.push(chart);
        chart.setOption({
            tooltip: {
                trigger: 'axis'
            },
            xAxis: {
                type: 'category',
                data: ['01-01', '01-02', '01-03', '01-04', '01-05', '01-06', '01-07']
            },
            yAxis: {
                type: 'value'
            },
            series: [
                {
                    name: '新增用户',
                    type: 'line',
                    stack: 'Total',
                    areaStyle: {},
                    data: [10, 12, 8, 15, 20, 18, 25]
                }
            ]
        });
    }

    // 民宿分布图
    if (homestayChartRef.value) {
        const chart = echarts.init(homestayChartRef.value);
        charts.push(chart);
        chart.setOption({
            tooltip: {
                trigger: 'item'
            },
            legend: {
                orient: 'vertical',
                left: 'left'
            },
            series: [
                {
                    name: '民宿分布',
                    type: 'pie',
                    radius: '50%',
                    data: [
                        { value: 35, name: '北京' },
                        { value: 30, name: '上海' },
                        { value: 25, name: '广州' },
                        { value: 20, name: '深圳' },
                        { value: 15, name: '杭州' }
                    ],
                    emphasis: {
                        itemStyle: {
                            shadowBlur: 10,
                            shadowOffsetX: 0,
                            shadowColor: 'rgba(0, 0, 0, 0.5)'
                        }
                    }
                }
            ]
        });
    }
};

// 获取统计数据
const fetchData = async () => {
    try {
        const [startDate, endDate] = dateRange.value;
        const params = {
            startDate: startDate.toISOString().split('T')[0],
            endDate: endDate.toISOString().split('T')[0]
        };

        const data: any = await getStatisticsData(params);

        // 更新概览数据
        if (data) {
            summaryData.value[0].value = data.orderCount || 0;
            summaryData.value[0].trend = data.orderCountTrend || 0;
            summaryData.value[1].value = `¥${data.totalIncome || 0}`;
            summaryData.value[1].trend = data.incomeTrend || 0;
            summaryData.value[2].value = data.userCount || 0;
            summaryData.value[2].trend = data.userCountTrend || 0;
            summaryData.value[3].value = data.homestayCount || 0;
            summaryData.value[3].trend = data.homestayCountTrend || 0;
        }

        // 更新图表数据
        initCharts();

    } catch (error) {
        console.error('获取统计数据失败:', error);
        ElMessage.error('获取统计数据失败');
    }
};

// 导出数据
const exportData = async () => {
    try {
        const [startDate, endDate] = dateRange.value;
        const params = {
            startDate: startDate.toISOString().split('T')[0],
            endDate: endDate.toISOString().split('T')[0]
        };

        const blob = await exportStatistics(params);

        const link = document.createElement('a');
        const url = URL.createObjectURL(blob);
        link.setAttribute('href', url);
        link.setAttribute('download', `statistics_${params.startDate}_${params.endDate}.csv`);
        link.style.visibility = 'hidden';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);

        ElMessage.success('导出成功');
    } catch (error) {
        console.error('导出数据失败:', error);
        ElMessage.error('导出数据失败');
    }
};

// 窗口大小变化时重绘图表
const handleResize = () => {
    charts.forEach(chart => chart?.resize());
};

onMounted(() => {
    fetchData();
    window.addEventListener('resize', handleResize);
});

onUnmounted(() => {
    window.removeEventListener('resize', handleResize);
    charts.forEach(chart => chart?.dispose());
});
</script>

<style scoped lang="scss">
.statistics-container {
    padding: 20px;

    .filter-card {
        margin-bottom: 20px;
    }

    .stat-row {
        margin-bottom: 20px;
    }

    .summary-card {
        .summary-content {
            display: flex;
            align-items: center;

            .summary-icon {
                width: 56px;
                height: 56px;
                border-radius: 8px;
                display: flex;
                align-items: center;
                justify-content: center;
                margin-right: 15px;

                .el-icon {
                    font-size: 28px;
                    color: white;
                }
            }

            .icon-1 {
                background-color: var(--el-color-primary);
            }

            .icon-2 {
                background-color: #67C23A;
            }

            .icon-3 {
                background-color: #E6A23C;
            }

            .icon-4 {
                background-color: #F56C6C;
            }

            .summary-info {
                .summary-value {
                    font-size: 24px;
                    font-weight: bold;
                }

                .summary-title {
                    font-size: 14px;
                    color: #909399;
                }
            }
        }

        .summary-trend {
            margin-top: 10px;
            font-size: 12px;
            color: #909399;

            .up {
                color: #67C23A;
            }

            .down {
                color: #F56C6C;
            }
        }
    }

    .chart-card {
        margin-bottom: 20px;

        .chart-container {
            height: 300px;

            .chart {
                width: 100%;
                height: 100%;
            }
        }
    }
}
</style>
