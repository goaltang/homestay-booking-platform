<template>
    <div class="earning-container">
        <div class="header">
            <h1>收益管理</h1>
            <div class="header-actions">
                <el-button type="success" @click="handleSettleEarnings" :loading="settling">结算收益</el-button>
                <el-button type="primary" @click="goToWithdrawal">提现管理</el-button>
            </div>
        </div>

        <!-- 收益概览卡片 -->
        <el-card class="overview-card" v-loading="loading">
            <el-row :gutter="20">
                <el-col :span="6">
                    <div class="stat-box">
                        <div class="stat-label">总收益</div>
                        <div class="stat-value">¥{{ totalEarnings.toFixed(2) }}</div>
                    </div>
                </el-col>
                <el-col :span="6">
                    <div class="stat-box">
                        <div class="stat-label">本月收益</div>
                        <div class="stat-value">¥{{ currentMonthEarnings.toFixed(2) }}</div>
                    </div>
                </el-col>
                <el-col :span="6">
                    <div class="stat-box">
                        <div class="stat-label">上月收益</div>
                        <div class="stat-value">¥{{ lastMonthEarnings.toFixed(2) }}</div>
                    </div>
                </el-col>
                <el-col :span="6">
                    <div class="stat-box">
                        <div class="stat-label">未结算</div>
                        <div class="stat-value">¥{{ pendingEarnings.toFixed(2) }}</div>
                    </div>
                </el-col>
            </el-row>
        </el-card>

        <!-- 收益趋势图 -->
        <el-card class="chart-card">
            <div class="card-header">
                <h2>收益趋势</h2>
                <el-radio-group v-model="chartPeriod" size="small" @change="fetchTrendData">
                    <el-radio-button label="week">最近一周</el-radio-button>
                    <el-radio-button label="month">最近一月</el-radio-button>
                    <el-radio-button label="year">最近一年</el-radio-button>
                </el-radio-group>
            </div>
            <div style="height: 300px; margin-top: 20px;" class="chart-container" ref="chartRef">
                <!-- 图表将在这里渲染 -->
            </div>
        </el-card>

        <!-- 收益明细表格 -->
        <el-card class="detail-card">
            <div class="card-header">
                <h2>收益明细</h2>
                <div class="actions">
                    <el-dropdown @command="exportData">
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

            <!-- 筛选器 -->
            <div class="filter-section">
                <el-form :inline="true" :model="filterForm">
                    <el-form-item label="时间范围">
                        <el-date-picker v-model="filterForm.dateRange" type="daterange" range-separator="至"
                            start-placeholder="开始日期" end-placeholder="结束日期" format="YYYY-MM-DD"
                            value-format="YYYY-MM-DD" />
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
            </div>

            <div v-if="loading" class="loading-container">
                <el-skeleton :rows="3" animated />
            </div>
            <div v-else-if="earningsData.length === 0" class="empty-data">
                <el-empty description="暂无收益数据" />
                <p class="empty-tip">您目前还没有收益记录，可能是因为：</p>
                <ul class="empty-reasons">
                    <li>您还没有成功完成的订单</li>
                    <li>您的订单还未结算</li>
                    <li>系统数据同步可能有延迟</li>
                </ul>
            </div>
            <el-table v-else :data="earningsData" stripe style="width: 100%">
                <el-table-column prop="createdAt" label="日期" width="180">
                    <template #default="{ row }">
                        {{ formatDateTime(row.createdAt) }} 
                    </template>
                </el-table-column>
                <el-table-column prop="orderNumber" label="订单号" width="180" />
                <el-table-column prop="homestayTitle" label="房源名称" min-width="150" />
                <el-table-column prop="guestName" label="客人姓名" width="120" />
                <el-table-column prop="checkInDate" label="入住日期" width="120">
                    <template #default="{ row }">
                        {{ formatDate(row.checkInDate) }}
                    </template>
                </el-table-column>
                <el-table-column prop="checkOutDate" label="离店日期" width="120">
                     <template #default="{ row }">
                        {{ formatDate(row.checkOutDate) }}
                    </template>
                </el-table-column>
                <el-table-column prop="nights" label="入住天数" width="80" align="center" />
                <el-table-column prop="amount" label="收益金额" width="120">
                    <template #default="{ row }">
                        ¥{{ row.amount.toFixed(2) }}
                    </template>
                </el-table-column>
                <el-table-column prop="status" label="状态" width="120">
                    <template #default="{ row }">
                        <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
                    </template>
                </el-table-column>
            </el-table>

            <div v-if="earningsData.length > 0" class="pagination">
                <el-pagination v-model:currentPage="currentPage" v-model:page-size="pageSize"
                    :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next, jumper" :total="total"
                    @size-change="handleSizeChange" @current-change="handleCurrentChange" />
            </div>
        </el-card>
    </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { ArrowDown } from '@element-plus/icons-vue'
import {
    exportEarningsData,
    getEarningsSummary,
    getEarningsDetail,
    getEarningsTrend,
    getMonthlyEarnings,
    getPendingEarnings,
    settleHostEarnings,
} from '@/api/earnings'
import echarts, { type EChartsType } from '@/utils/echarts'
import { getOwnerHomestays } from '@/api/homestay'
import request from '@/utils/request'

const router = useRouter()

// 统计数据 - 改为响应式数据，由API获取
const totalEarnings = ref(0)
const currentMonthEarnings = ref(0)
const lastMonthEarnings = ref(0)
const pendingEarnings = ref(0)

// 图表相关
const chartPeriod = ref('month')
const chartRef = ref<HTMLElement | null>(null)
let chart: EChartsType | null = null

// 表格数据
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 筛选表单
const filterForm = reactive({
    dateRange: null,
    homestayId: null
})

// 房源选项
const homestayOptions = ref<{ value: number, label: string }[]>([])

// 收益数据
const earningsData = ref<any[]>([])

// 状态对应的标签类型
const getStatusType = (status: string): string => {
    const types: Record<string, string> = {
        'SETTLED': 'success',
        'PENDING': 'warning',
        'REFUNDED': 'danger',
        'COMPLETED': 'success',
        'CANCELLED': 'danger',
        'UNKNOWN': 'info'
    }
    return types[status] || 'info'
}

// 状态对应的文本
const getStatusText = (status: string): string => {
    const texts: Record<string, string> = {
        'SETTLED': '已结算',
        'PENDING': '待结算',
        'REFUNDED': '已退款',
        'COMPLETED': '已完成',
        'CANCELLED': '已取消',
        'UNKNOWN': '未知状态'
    }
    return texts[status] || '未知状态'
}

// 处理筛选
const handleFilter = () => {
    fetchEarningsData()
}

// 重置筛选
const resetFilter = () => {
    filterForm.dateRange = null
    filterForm.homestayId = null
    fetchEarningsData()
}

// 跳转到提现管理页面
const goToWithdrawal = () => {
    router.push('/host/withdrawal')
}

// 结算加载状态
const settling = ref(false)

// 手动触发结算
const handleSettleEarnings = async () => {
    settling.value = true;
    try {
        const response = await settleHostEarnings();
        ElMessage.success(response.data.message || '结算请求成功');
        // 刷新统计数据和表格数据
        await Promise.all([
            fetchStatistics(),
            fetchEarningsData()
        ]);
    } catch (error: any) {
        console.error("结算收益失败:", error);
    } finally {
        settling.value = false;
    }
};

// 导出数据
const exportData = async (command: string) => {
    try {
        // 构建导出参数
        const params = {
            startDate: filterForm.dateRange?.[0] || null,
            endDate: filterForm.dateRange?.[1] || null,
            homestayId: filterForm.homestayId || null,
            format: command
        }

        const blob = await exportEarningsData(params)

        // 创建下载链接
        const link = document.createElement('a')
        link.href = URL.createObjectURL(blob)

        // 设置文件名
        const fileName = `收益明细_${new Date().toISOString().split('T')[0]}`
        link.download = command === 'pdf' ? `${fileName}.pdf` : (command === 'csv' ? `${fileName}.csv` : `${fileName}.xlsx`)

        // 触发下载
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)

        ElMessage.success(`${command === 'pdf' ? '账单' : '数据'}导出成功`)
    } catch (error) {
        console.error('导出失败:', error)
    }
}

// 页码变化
const handleCurrentChange = (page: number) => {
    currentPage.value = page
    fetchEarningsData()
}

// 每页数量变化
const handleSizeChange = (size: number) => {
    pageSize.value = size
    fetchEarningsData()
}

// 获取统计数据
const fetchStatistics = async () => {
    try {
        loading.value = true;

        // 获取总收益
        const summaryResponse: any = await getEarningsSummary({});
        console.log("收益汇总响应:", summaryResponse);

        // 处理不同格式的响应
        if (summaryResponse && typeof summaryResponse.totalEarnings === 'number') {
            totalEarnings.value = summaryResponse.totalEarnings;
        } else if (summaryResponse && summaryResponse.data && typeof summaryResponse.data.totalEarnings === 'number') {
            totalEarnings.value = summaryResponse.data.totalEarnings;
        } else {
            console.warn("获取总收益失败，响应格式不正确:", summaryResponse);
            totalEarnings.value = 0;
        }

        // 获取当月收益
        const currentMonthResponse: any = await getMonthlyEarnings();
        console.log("当月收益响应:", currentMonthResponse);

        // 处理不同格式的响应
        if (typeof currentMonthResponse === 'number') {
            currentMonthEarnings.value = currentMonthResponse;
        } else if (currentMonthResponse && typeof currentMonthResponse.data === 'number') {
            currentMonthEarnings.value = currentMonthResponse.data;
        } else if (currentMonthResponse && currentMonthResponse.data && typeof currentMonthResponse.data.amount === 'number') {
            currentMonthEarnings.value = currentMonthResponse.data.amount;
        } else {
            console.warn("获取当月收益失败，响应格式不正确:", currentMonthResponse);
            currentMonthEarnings.value = 0;
        }

        // 获取上月收益
        const lastMonthStartDate = new Date();
        lastMonthStartDate.setMonth(lastMonthStartDate.getMonth() - 1);
        lastMonthStartDate.setDate(1);
        const lastMonthEndDate = new Date();
        lastMonthEndDate.setDate(0); // 设置为上月最后一天

        const lastMonthParams = {
            startDate: lastMonthStartDate.toISOString().split('T')[0],
            endDate: lastMonthEndDate.toISOString().split('T')[0]
        };
        const lastMonthResponse: any = await getEarningsSummary(lastMonthParams);
        console.log("上月收益响应:", lastMonthResponse);

        // 处理不同格式的响应
        if (lastMonthResponse && typeof lastMonthResponse.totalEarnings === 'number') {
            lastMonthEarnings.value = lastMonthResponse.totalEarnings;
        } else if (lastMonthResponse && lastMonthResponse.data && typeof lastMonthResponse.data.totalEarnings === 'number') {
            lastMonthEarnings.value = lastMonthResponse.data.totalEarnings;
        } else {
            console.warn("获取上月收益失败，响应格式不正确:", lastMonthResponse);
            lastMonthEarnings.value = 0;
        }

        // 获取待结算收益
        const pendingEarningsResponse: any = await getPendingEarnings();
        console.log("待结算收益响应:", pendingEarningsResponse);

        // 处理不同格式的响应
        if (typeof pendingEarningsResponse === 'number') {
            pendingEarnings.value = pendingEarningsResponse;
        } else if (pendingEarningsResponse && typeof pendingEarningsResponse.data === 'number') {
            pendingEarnings.value = pendingEarningsResponse.data;
        } else if (pendingEarningsResponse && pendingEarningsResponse.data && typeof pendingEarningsResponse.data.amount === 'number') {
            pendingEarnings.value = pendingEarningsResponse.data.amount;
        } else {
            console.warn("获取待结算收益失败，响应格式不正确:", pendingEarningsResponse);
            pendingEarnings.value = 0;
        }

    } catch (error) {
        console.error('获取统计数据失败:', error);
        // 设置默认值
        totalEarnings.value = 0;
        currentMonthEarnings.value = 0;
        lastMonthEarnings.value = 0;
        pendingEarnings.value = 0;
    } finally {
        loading.value = false;
    }
};

// 获取收益趋势数据
const fetchTrendData = async () => {
    try {
        loading.value = true;

        // 确定日期范围
        let startDate: Date | null = null;
        const endDate = new Date();

        if (chartPeriod.value === 'week') {
            startDate = new Date();
            startDate.setDate(startDate.getDate() - 7);
        } else if (chartPeriod.value === 'month') {
            startDate = new Date();
            startDate.setMonth(startDate.getMonth() - 1);
        } else if (chartPeriod.value === 'year') {
            startDate = new Date();
            startDate.setFullYear(startDate.getFullYear() - 1);
        }

        const params = {
            type: chartPeriod.value === 'year' ? 'monthly' as const : 'daily' as const,
            startDate: startDate ? startDate.toISOString().split('T')[0] : undefined,
            endDate: endDate.toISOString().split('T')[0],
        };

        const response: any = await getEarningsTrend(params);
        console.log("收益趋势响应:", response);

        // 处理不同格式的响应
        let labels: string[] = [];
        let values: number[] = [];

        if (response && Array.isArray(response.labels) && Array.isArray(response.values)) {
            // 标准格式
            labels = response.labels;
            values = response.values;
        } else if (response && response.data && Array.isArray(response.data.labels) && Array.isArray(response.data.values)) {
            // 嵌套在data中
            labels = response.data.labels;
            values = response.data.values;
        } else if (Array.isArray(response)) {
            // 数组格式
            labels = response.map((item: any) => item.date || item.label);
            values = response.map((item: any) => item.amount || item.value || 0);
        } else {
            console.warn("无法解析的收益趋势数据格式:", response);
        }

        renderChart({ labels, values });
    } catch (error) {
        console.error('获取趋势数据失败:', error);
        // 即使出错也渲染一个空图表
        renderChart({ labels: [], values: [] });
    } finally {
        loading.value = false;
    }
};

// 渲染图表
const renderChart = (data: { labels: string[], values: number[] }) => {
    if (!chartRef.value) return

    if (!chart) {
        chart = echarts.init(chartRef.value)
    }

    const option = {
        tooltip: {
            trigger: 'axis',
            formatter: '{b}<br />{a}: ¥{c}'
        },
        xAxis: {
            type: 'category',
            data: data.labels.length > 0 ? data.labels : ['暂无数据'],
            axisLabel: {
                rotate: chartPeriod.value === 'week' || chartPeriod.value === 'month' ? 45 : 0
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
                data: data.values.length > 0 ? data.values : [0],
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
    }

    chart.setOption(option)
}

// 获取房源列表
const fetchHomestayOptions = async () => {
    try {
        const response: any = await getOwnerHomestays();
        console.log("获取房源列表响应:", response);

        let homestays = [];

        // 处理不同格式的响应
        if (response && Array.isArray(response)) {
            // 直接是数组
            homestays = response;
        } else if (response && response.data && Array.isArray(response.data)) {
            // 数据在data字段
            homestays = response.data;
        } else if (response && response.data && response.data.content && Array.isArray(response.data.content)) {
            // 标准分页格式
            homestays = response.data.content;
        } else if (response && response.content && Array.isArray(response.content)) {
            // 标准分页格式但没有data字段
            homestays = response.content;
        } else {
            console.warn("未识别的房源列表格式:", response);
            homestays = [];
        }

        // 构建选项数据
        homestayOptions.value = homestays.map((item: any) => ({
            value: item.id,
            label: item.title || item.name || `房源 #${item.id}`
        }));

        if (homestayOptions.value.length === 0) {
            console.warn("没有找到房源");
        }
    } catch (error) {
        console.error('获取房源列表失败:', error);
        homestayOptions.value = [];
    }
}

// 获取收益明细数据
const fetchEarningsData = async () => {
    try {
        loading.value = true;

        const params = {
            page: currentPage.value - 1,
            size: pageSize.value,
            startDate: filterForm.dateRange?.[0] || null,
            endDate: filterForm.dateRange?.[1] || null,
            homestayId: filterForm.homestayId || null
        };

        // 使用any类型以处理不同的响应格式
        const response: any = await getEarningsDetail(params);
        console.log("获取到的收益数据:", response);

        // 处理不同格式的响应数据
        let content = [];
        let totalElements = 0;

        // 标准Spring Data分页格式
        if (response && Array.isArray(response.content)) {
            content = response.content;
            totalElements = response.totalElements || content.length;
        }
        // 数据在data字段中
        else if (response && response.data) {
            if (Array.isArray(response.data.content)) {
                content = response.data.content;
                totalElements = response.data.totalElements || content.length;
            } else if (Array.isArray(response.data)) {
                content = response.data;
                totalElements = content.length;
            }
        }
        // 直接是数组
        else if (Array.isArray(response)) {
            content = response;
            totalElements = response.length;
        }

        // 处理响应数据
        if (content.length > 0) {
            // 安全地获取数值
            const getNumber = (val: any, defaultVal: number = 0): number => {
                return val !== undefined && val !== null ? Number(val) : defaultVal;
            };

            // 规范化状态值
            const normalizeStatus = (status: string): string => {
                if (!status) return 'UNKNOWN';

                const statusUpper = status.toUpperCase();

                // 匹配常见状态值变体
                if (statusUpper.includes('SETTL')) return 'SETTLED';
                if (statusUpper.includes('PEND')) return 'PENDING';
                if (statusUpper.includes('REFUND')) return 'REFUNDED';
                if (statusUpper.includes('COMPLET')) return 'COMPLETED';
                if (statusUpper.includes('CANCEL')) return 'CANCELLED';

                return statusUpper;
            };

            earningsData.value = content.map((item: any) => ({
                id: item.orderNumber || item.id || `id-${Math.random().toString(36).substr(2, 9)}`,
                createdAt: item.createdAt,
                orderNumber: item.orderNumber || item.orderNo || '未知订单',
                homestayTitle: item.homestayTitle || item.homestayName || '未知房源',
                guestName: item.guestName || '未知客人',
                checkInDate: item.checkInDate || item.checkIn,
                checkOutDate: item.checkOutDate || item.checkOut,
                nights: getNumber(item.nights),
                amount: getNumber(item.amount),
                status: normalizeStatus(item.status)
            }));

            total.value = totalElements;

            console.log("处理后的收益数据:", earningsData.value);

            if (earningsData.value.length === 0) {
                ElMessage.info("没有查询到收益数据");
            }
        } else {
            earningsData.value = [];
            total.value = 0;
            console.warn("收益数据为空:", response);
        }
    } catch (error) {
        console.error('获取收益明细失败:', error);
        earningsData.value = [];
        total.value = 0;
    } finally {
        loading.value = false;
    }

    return earningsData.value;
};

// 窗口大小变化时调整图表大小
const handleResize = () => {
    chart?.resize();
};

// 检查和测试API连接
const testApiConnection = async () => {
    try {
        // 测试API连接
        const response = await request.get("/api/auth/current");

        // 确保响应有数据
        if (!response || !response.data) {
            console.error("API响应没有数据");
            ElMessage.warning("无法获取用户信息");
            return false;
        }

        // 检查用户角色
        let userRole = '';
        let roleFound = false;

        // 检查各种可能的角色字段格式
        const userData = response.data;

        if (typeof userData.role === 'string') userRole = userData.role;
        else if (userData.user && typeof userData.user.role === 'string') userRole = userData.user.role;
        else if (typeof userData.roles === 'string') userRole = userData.roles;
        else if (Array.isArray(userData.roles)) {
            userRole = userData.roles.map((r: any) =>
                typeof r === 'string' ? r : (r.authority || r.role || JSON.stringify(r))
            ).join(',');
        }
        else if (Array.isArray(userData.authorities)) {
            userRole = userData.authorities.map((a: any) =>
                typeof a === 'string' ? a : (a.authority || a.role || JSON.stringify(a))
            ).join(',');
        }

        // 定义允许的角色关键字
        const allowedRoleKeywords = ['host', 'admin', 'root', 'landlord'];

        // 检查用户角色是否包含允许的关键字(不区分大小写)
        const userRoleLower = userRole.toLowerCase();
        roleFound = allowedRoleKeywords.some(role => userRoleLower.includes(role));

        // 还要检查数组形式的角色
        if (!roleFound && Array.isArray(userData.roles)) {
            roleFound = userData.roles.some((r: any) => {
                if (typeof r === 'string') {
                    return allowedRoleKeywords.some(role => r.toLowerCase().includes(role));
                } else if (r && typeof r === 'object') {
                    const roleStr = (r.authority || r.role || '').toLowerCase();
                    return allowedRoleKeywords.some(role => roleStr.includes(role));
                }
                return false;
            });
        }

        // 检查authorities数组
        if (!roleFound && Array.isArray(userData.authorities)) {
            roleFound = userData.authorities.some((a: any) => {
                if (typeof a === 'string') {
                    return allowedRoleKeywords.some(role => a.toLowerCase().includes(role));
                } else if (a && typeof a === 'object') {
                    const authStr = (a.authority || a.role || '').toLowerCase();
                    return allowedRoleKeywords.some(role => authStr.includes(role));
                }
                return false;
            });
        }

        if (!roleFound) {
            ElMessage.warning(`系统未识别出您的房东身份`);
            return false;
        } else {
            ElMessage.success(`欢迎房东用户 ${userData.username || ''}！`);
            return true;
        }
    } catch (error) {
        console.error("API连接测试失败:", error);
        return false;
    }
};

// 导入日期格式化工具 (如果项目中有统一的工具)
// import { formatDateTime, formatDate } from '@/utils/formatters'; 

// 或者定义简单的格式化函数
const formatDateTime = (dateTimeString: string | null | undefined): string => {
  if (!dateTimeString) return '未知日期';
  try {
    return new Date(dateTimeString).toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false
    });
  } catch (e) {
    console.error("Error formatting date-time:", dateTimeString, e);
    return '无效日期';
  }
};

const formatDate = (dateString: string | null | undefined): string => {
  if (!dateString) return '未知日期';
  try {
    return new Date(dateString).toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    });
  } catch (e) {
    console.error("Error formatting date:", dateString, e);
    return '无效日期';
  }
};

onMounted(async () => {
    loading.value = true;

    try {
        // 测试API连接
        const apiConnectionOk = await testApiConnection();

        if (apiConnectionOk) {
            // 尝试获取真实数据
            await Promise.all([
                fetchHomestayOptions(),
                fetchStatistics(),
                fetchEarningsData(),
                fetchTrendData()
            ]);
        } else {
            // API连接失败
            ElMessage.error('无法连接到服务器，请检查网络连接或联系管理员');
        }
    } catch (error) {
        console.error("初始化数据失败:", error);
    } finally {
        loading.value = false;
    }

    // 添加窗口大小变化监听器
    window.addEventListener('resize', handleResize);
});

onUnmounted(() => {
    // 在组件卸载时清理资源
    window.removeEventListener('resize', handleResize);

    if (chart) {
        chart.dispose();
        chart = null;
    }
});
</script>

<style scoped>
.earning-container {
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

.overview-card,
.chart-card,
.detail-card {
    margin-bottom: 20px;
}

.stat-box {
    text-align: center;
    padding: 10px;
}

.stat-label {
    font-size: 14px;
    color: #606266;
    margin-bottom: 5px;
}

.stat-value {
    font-size: 24px;
    font-weight: bold;
    color: #409EFF;
}

.card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
}

.card-header h2 {
    margin: 0;
    font-size: 18px;
}

.filter-section {
    margin-bottom: 20px;
}

.pagination {
    margin-top: 20px;
    text-align: right;
}

.chart-container {
    width: 100%;
    height: 100%;
}

.loading-container {
    padding: 20px;
    text-align: center;
}

.empty-data {
    padding: 20px;
    text-align: center;
}

.empty-tip {
    margin-top: 10px;
    margin-bottom: 10px;
    color: #606266;
    font-size: 14px;
}

.empty-reasons {
    list-style-type: disc;
    padding-left: 20px;
    text-align: left;
    max-width: 400px;
    margin: 0 auto;
    color: #909399;
    font-size: 14px;
    line-height: 1.6;
}
</style>