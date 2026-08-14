<template>
    <div class="earnings">
        <div class="header">
            <h2>收益统计</h2>
            <div class="header-actions">
                <el-button type="primary" @click="goToWithdrawal">提现管理</el-button>
            </div>
        </div>

        <el-card shadow="never" class="filter-card">
            <el-form :inline="true" :model="filterForm" class="filter-form">
                <el-form-item label="时间范围">
                    <el-date-picker v-model="filterForm.dateRange" type="daterange" range-separator="至"
                        start-placeholder="开始日期" end-placeholder="结束日期" :shortcuts="dateShortcuts" />
                </el-form-item>
                <el-form-item label="房源">
                    <el-select v-model="filterForm.homestayId" placeholder="全部房源" clearable>
                        <el-option v-for="item in homestayOptions" :key="item.value" :label="item.label"
                            :value="item.value" />
                    </el-select>
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" @click="handleFilter">查询</el-button>
                    <el-button @click="resetFilter">重置</el-button>
                </el-form-item>
            </el-form>
        </el-card>

        <div class="summary-cards">
            <el-row :gutter="20">
                <el-col :span="8">
                    <el-card shadow="hover">
                        <div class="summary-item">
                            <div class="summary-title">总收益</div>
                            <div class="summary-value">¥{{ summary.totalEarnings }}</div>
                        </div>
                    </el-card>
                </el-col>
                <el-col :span="8">
                    <el-card shadow="hover">
                        <div class="summary-item">
                            <div class="summary-title">订单数</div>
                            <div class="summary-value">{{ summary.totalOrders }}</div>
                        </div>
                    </el-card>
                </el-col>
                <el-col :span="8">
                    <el-card shadow="hover">
                        <div class="summary-item">
                            <div class="summary-title">平均每单</div>
                            <div class="summary-value">¥{{ summary.averagePerOrder }}</div>
                        </div>
                    </el-card>
                </el-col>
            </el-row>
        </div>

        <el-card shadow="never" class="chart-card">
            <div class="chart-header">
                <h3>收益趋势</h3>
                <el-radio-group v-model="chartType" size="small">
                    <el-radio-button label="daily">日</el-radio-button>
                    <el-radio-button label="monthly">月</el-radio-button>
                </el-radio-group>
            </div>
            <div class="chart-container" ref="chartRef"></div>
        </el-card>

        <el-card shadow="never" class="table-card">
            <div class="table-header">
                <h3>收益明细</h3>
                <div class="export-actions">
                    <el-dropdown @command="handleExport">
                        <el-button type="primary" size="small">
                            导出数据 <el-icon class="el-icon--right"><arrow-down /></el-icon>
                        </el-button>
                        <template #dropdown>
                            <el-dropdown-menu>
                                <el-dropdown-item command="excel">导出Excel</el-dropdown-item>
                                <el-dropdown-item command="csv">导出CSV</el-dropdown-item>
                                <el-dropdown-item command="pdf">下载收益账单(PDF)</el-dropdown-item>
                            </el-dropdown-menu>
                        </template>
                    </el-dropdown>
                </div>
            </div>
            <el-table :data="earnings" stripe style="width: 100%" v-loading="loading">
                <el-table-column prop="orderNumber" label="订单号" min-width="120" />
                <el-table-column prop="homestayTitle" label="房源名称" min-width="180" />
                <el-table-column prop="guestName" label="客人姓名" width="120" />
                <el-table-column prop="checkInDate" label="入住日期" width="120" />
                <el-table-column prop="checkOutDate" label="退房日期" width="120" />
                <el-table-column prop="nights" label="天数" width="80" />
                <el-table-column prop="amount" label="金额" width="120">
                    <template #default="{ row }">
                        ¥{{ row.amount }}
                    </template>
                </el-table-column>
                <el-table-column prop="status" label="状态" width="100">
                    <template #default="{ row }">
                        <el-tag type="success" v-if="row.status === 'COMPLETED'">已完成</el-tag>
                        <el-tag type="info" v-else>{{ row.status }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="createTime" label="订单时间" width="180" />
            </el-table>

            <div class="pagination">
                <el-pagination v-model:currentPage="currentPage" v-model:page-size="pageSize"
                    :page-sizes="[10, 20, 30, 50]" layout="total, sizes, prev, pager, next, jumper" :total="total"
                    @size-change="handleSizeChange" @current-change="handleCurrentChange" />
            </div>
        </el-card>
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue';
import {
    getEarningsSummary,
    getEarningsDetail,
    getEarningsTrend,
    exportEarningsData
} from '@/api/earnings';
import { getHomestaysByOwner } from '@/api/homestay';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';
import { ArrowDown } from '@element-plus/icons-vue';
import type { EChartsType } from 'echarts';
import * as echarts from 'echarts';

const router = useRouter();

interface HomestayOption {
    label: string;
    value: number;
}

interface Summary {
    totalEarnings: number;
    totalOrders: number;
    averagePerOrder: number;
}

interface FilterForm {
    dateRange: Date[];
    homestayId: number | '';
}

const loading = ref(false);
const earnings = ref<any[]>([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(10);
const homestayOptions = ref<HomestayOption[]>([]);
const chartType = ref<'daily' | 'monthly'>('monthly');
const chartRef = ref<HTMLElement | null>(null);
let chart: EChartsType | null = null;

const summary = ref<Summary>({
    totalEarnings: 0,
    totalOrders: 0,
    averagePerOrder: 0
});

const filterForm = ref<FilterForm>({
    dateRange: [],
    homestayId: '',
});

const selectedHomestayId = () => filterForm.value.homestayId || null;

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
        text: '今年',
        value: () => {
            const end = new Date();
            const start = new Date(new Date().getFullYear(), 0, 1);
            return [start, end];
        },
    },
];

// 获取收益汇总数据
const fetchSummary = async () => {
    try {
        const params = {
            startDate: filterForm.value.dateRange?.[0] ? new Date(filterForm.value.dateRange[0]).toISOString().split('T')[0] : null,
            endDate: filterForm.value.dateRange?.[1] ? new Date(filterForm.value.dateRange[1]).toISOString().split('T')[0] : null,
            homestayId: selectedHomestayId(),
        };

        const response = await getEarningsSummary(params);
        summary.value = response;
    } catch (error) {
        console.error('获取收益汇总失败', error);
    }
};

// 获取收益明细
const fetchEarnings = async () => {
    try {
        loading.value = true;

        const params = {
            page: currentPage.value - 1,
            size: pageSize.value,
            startDate: filterForm.value.dateRange?.[0] ? new Date(filterForm.value.dateRange[0]).toISOString().split('T')[0] : null,
            endDate: filterForm.value.dateRange?.[1] ? new Date(filterForm.value.dateRange[1]).toISOString().split('T')[0] : null,
            homestayId: selectedHomestayId(),
        };

        const response = await getEarningsDetail(params);
        earnings.value = response.content;
        total.value = response.totalElements;
    } catch (error) {
        console.error('获取收益明细失败', error);
    } finally {
        loading.value = false;
    }
};

interface TrendData {
    labels: string[];
    values: number[];
}

// 获取收益趋势数据
const fetchTrend = async () => {
    try {
        const params = {
            type: chartType.value,
            startDate: filterForm.value.dateRange?.[0] ? new Date(filterForm.value.dateRange[0]).toISOString().split('T')[0] : null,
            endDate: filterForm.value.dateRange?.[1] ? new Date(filterForm.value.dateRange[1]).toISOString().split('T')[0] : null,
            homestayId: selectedHomestayId(),
        };

        const response = await getEarningsTrend(params);
        renderChart(response);
    } catch (error) {
        console.error('获取收益趋势失败', error);
    }
};

// 渲染图表
const renderChart = (data: TrendData) => {
    if (!chartRef.value) return;

    if (!chart) {
        chart = echarts.init(chartRef.value);
    }

    const option = {
        tooltip: {
            trigger: 'axis',
            formatter: '{b}<br />{a}: ¥{c}'
        },
        xAxis: {
            type: 'category',
            data: data.labels,
            axisLabel: {
                rotate: chartType.value === 'daily' ? 45 : 0
            }
        },
        yAxis: {
            type: 'value',
            axisLabel: {
                formatter: '¥{value}'
            }
        },
        series: [
            {
                name: '收益',
                type: 'bar',
                data: data.values,
                itemStyle: {
                    color: '#409EFF'
                }
            }
        ],
        grid: {
            left: '3%',
            right: '4%',
            bottom: '3%',
            containLabel: true
        }
    };

    chart.setOption(option);
};

// 获取房源列表作为筛选选项
const fetchHomestays = async () => {
    try {
        const response = await getHomestaysByOwner();
        homestayOptions.value = response.data.map((item: any) => ({
            label: item.title,
            value: item.id
        }));
    } catch (error) {
        console.error('获取房源列表失败', error);
    }
};

// 页码变化
const handleCurrentChange = (page: number) => {
    currentPage.value = page;
    fetchEarnings();
};

// 每页数量变化
const handleSizeChange = (size: number) => {
    pageSize.value = size;
    fetchEarnings();
};

// 筛选
const handleFilter = () => {
    currentPage.value = 1;
    fetchSummary();
    fetchEarnings();
    fetchTrend();
};

// 重置筛选
const resetFilter = () => {
    filterForm.value = {
        dateRange: [],
        homestayId: '',
    };
    handleFilter();
};

// 监听图表类型变化
watch(chartType, () => {
    fetchTrend();
});

// 窗口大小变化时调整图表大小
const handleResize = () => {
    chart && chart.resize();
};

// 去提现管理页面
const goToWithdrawal = () => {
    router.push('/host/withdrawal');
};

// 处理导出
const handleExport = async (command: string) => {
    try {
        const params = {
            startDate: filterForm.value.dateRange?.[0] ? new Date(filterForm.value.dateRange[0]).toISOString().split('T')[0] : null,
            endDate: filterForm.value.dateRange?.[1] ? new Date(filterForm.value.dateRange[1]).toISOString().split('T')[0] : null,
            homestayId: selectedHomestayId(),
            format: command
        };

        const blob = await exportEarningsData(params);

        // 创建下载链接
        const link = document.createElement('a');
        link.href = URL.createObjectURL(blob);

        // 设置文件名
        const fileName = `收益明细_${new Date().toISOString().split('T')[0]}`;
        link.download = command === 'pdf' ? `${fileName}.pdf` : (command === 'csv' ? `${fileName}.csv` : `${fileName}.xlsx`);

        // 触发下载
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);

        ElMessage.success(`${command === 'pdf' ? '账单' : '数据'}导出成功`);
    } catch (error) {
        console.error('导出失败:', error);
    }
};

onMounted(() => {
    // 设置默认为最近一个月
    filterForm.value.dateRange = dateShortcuts[1].value();

    fetchHomestays();
    fetchSummary();
    fetchEarnings();

    // 等DOM渲染完成后再初始化图表
    setTimeout(() => {
        fetchTrend();
    }, 100);

    window.addEventListener('resize', handleResize);
});

onUnmounted(() => {
    window.removeEventListener('resize', handleResize);
    chart && chart.dispose();
    chart = null;
});
</script>

<style scoped>
.earnings {
    padding: 20px;
}

.header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
}

.header-actions {
    display: flex;
    gap: 10px;
}

.filter-card {
    margin-bottom: 20px;
}

.filter-form {
    display: flex;
    flex-wrap: wrap;
}

.summary-cards {
    margin-bottom: 20px;
}

.summary-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 20px 0;
}

.summary-title {
    font-size: 16px;
    color: #606266;
    margin-bottom: 10px;
}

.summary-value {
    font-size: 28px;
    font-weight: bold;
    color: #409EFF;
}

.chart-card {
    margin-bottom: 20px;
}

.chart-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
}

.chart-container {
    width: 100%;
    height: 400px;
}

.table-card {
    margin-bottom: 20px;
}

.table-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
}

.export-actions {
    display: flex;
    gap: 10px;
}

.pagination {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
}
</style>
