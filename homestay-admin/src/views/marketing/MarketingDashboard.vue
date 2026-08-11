<template>
  <div class="marketing-dashboard">
    <h2>营销报表</h2>

    <!-- 数据概览卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card>
          <div class="stat-value">¥{{ formatAmount(statistics.totalDiscount) }}</div>
          <div class="stat-label">累计优惠金额</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat-value">{{ statistics.totalUsageCount }}</div>
          <div class="stat-label">优惠使用次数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat-value">¥{{ formatAmount(statistics.platformDiscount) }}</div>
          <div class="stat-label">平台承担金额</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat-value">¥{{ formatAmount(statistics.hostDiscount) }}</div>
          <div class="stat-label">房东承担金额</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 筛选条件 -->
    <el-card class="filter-card">
      <el-form :model="filterForm" inline>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item label="活动类型">
          <el-select v-model="filterForm.campaignType" placeholder="全部" clearable>
            <el-option label="限时折扣" value="FLASH_SALE" />
            <el-option label="满减活动" value="FULL_REDUCTION" />
            <el-option label="房源折扣" value="HOMESTAY_DISCOUNT" />
          </el-select>
        </el-form-item>
        <el-form-item label="承担方">
          <el-select v-model="filterForm.subsidyBearer" placeholder="全部" clearable>
            <el-option label="平台" value="PLATFORM" />
            <el-option label="房东" value="HOST" />
            <el-option label="混合" value="MIXED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadStatistics">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- ROI 概览 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card>
          <div class="stat-value" style="color: #67c23a">¥{{ formatAmount(roiOverview.totalGmv) }}</div>
          <div class="stat-label">优惠带动 GMV</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat-value" style="color: #f56c6c">¥{{ formatAmount(roiOverview.totalDiscountCost) }}</div>
          <div class="stat-label">优惠总成本</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat-value" style="color: #e6a23c">{{ roiOverview.roi }}x</div>
          <div class="stat-label">ROI（投入产出比）</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat-value">{{ roiOverview.totalOrders }}</div>
          <div class="stat-label">带动订单数</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 承担方占比 + 核销概况 -->
    <el-row :gutter="20" class="chart-row">
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>承担方占比</span>
          </template>
          <div class="bearer-chart">
            <div class="bearer-item">
              <span class="bearer-name">平台承担</span>
              <el-progress
                :percentage="calculatePercent(statistics.platformDiscount, statistics.totalDiscount)"
                color="#6366f1"
              />
              <span class="bearer-amount">¥{{ formatAmount(statistics.platformDiscount) }}</span>
            </div>
            <div class="bearer-item">
              <span class="bearer-name">房东承担</span>
              <el-progress
                :percentage="calculatePercent(statistics.hostDiscount, statistics.totalDiscount)"
                color="#e6a23c"
              />
              <span class="bearer-amount">¥{{ formatAmount(statistics.hostDiscount) }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>核销概况</span>
          </template>
          <div class="usage-summary">
            <div class="summary-item">
              <div class="summary-value">{{ statistics.totalUsageCount }}</div>
              <div class="summary-label">总使用次数</div>
            </div>
            <div class="summary-item">
              <div class="summary-value">{{ statistics.usedCount }}</div>
              <div class="summary-label">已核销</div>
            </div>
            <div class="summary-item">
              <div class="summary-value">
                {{ statistics.totalUsageCount > 0
                  ? ((statistics.usedCount / statistics.totalUsageCount) * 100).toFixed(1)
                  : 0 }}%
              </div>
              <div class="summary-label">核销率</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ROI 活动排行 -->
    <el-card class="top-campaigns">
      <template #header>
        <span>活动 ROI 排行（TOP10）</span>
      </template>
      <el-empty v-if="!roiCampaigns.length" description="暂无数据" />
      <el-table v-else :data="roiCampaigns" stripe>
        <el-table-column type="index" label="排名" width="60" />
        <el-table-column prop="campaignName" label="活动名称" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            {{ formatType(row.campaignType) }}
          </template>
        </el-table-column>
        <el-table-column label="GMV" width="120">
          <template #default="{ row }">
            ¥{{ formatAmount(row.gmv) }}
          </template>
        </el-table-column>
        <el-table-column label="优惠成本" width="120">
          <template #default="{ row }">
            ¥{{ formatAmount(row.discountCost) }}
          </template>
        </el-table-column>
        <el-table-column label="订单数" width="90">
          <template #default="{ row }">
            {{ row.orderCount }}
          </template>
        </el-table-column>
        <el-table-column label="ROI" width="80">
          <template #default="{ row }">
            <el-tag :type="row.roi >= 1 ? 'success' : row.roi >= 0.5 ? 'warning' : 'danger'">
              {{ row.roi }}x
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="核销率" width="90">
          <template #default="{ row }">
            {{ row.usageRate }}%
          </template>
        </el-table-column>
        <el-table-column label="预算使用率" width="120">
          <template #default="{ row }">
            <el-progress :percentage="Math.min(row.budgetUsageRate || 0, 100)" :status="row.budgetUsageRate >= 80 ? 'exception' : ''" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { getPromotionStatistics, getCampaigns, getRoiOverview, getRoiCampaigns } from '@/api/marketing';

const statistics = reactive({
  totalDiscount: 0,
  totalUsageCount: 0,
  usedCount: 0,
  platformDiscount: 0,
  hostDiscount: 0,
  campaignCount: 0,
});

const roiOverview = reactive({
  totalGmv: 0,
  totalDiscountCost: 0,
  totalOrders: 0,
  roi: 0,
  usageRate: 0,
  avgOrderValue: 0,
  avgDiscountPerOrder: 0,
  platformCost: 0,
  hostCost: 0,
});

const topCampaigns = ref<any[]>([]);
const roiCampaigns = ref<any[]>([]);
const dateRange = ref<string[]>([]);
const filterForm = reactive({
  campaignType: '',
  subsidyBearer: '',
});

function formatAmount(amount: number) {
  if (!amount) return '0.00';
  return amount.toFixed(2);
}

function formatType(type: string) {
  const map: Record<string, string> = {
    FLASH_SALE: '限时折扣',
    FULL_REDUCTION: '满减',
    HOMESTAY_DISCOUNT: '房源折扣',
  };
  return map[type] || type;
}

function calculatePercent(part: number, total: number) {
  if (!total || total <= 0) return 0;
  return Math.round((part / total) * 100);
}

const resetFilter = () => {
  dateRange.value = [];
  filterForm.campaignType = '';
  filterForm.subsidyBearer = '';
  loadStatistics();
};

const loadStatistics = async () => {
  try {
    const params: any = {};
    if (filterForm.campaignType) params.campaignType = filterForm.campaignType;
    if (filterForm.subsidyBearer) params.subsidyBearer = filterForm.subsidyBearer;
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0];
      params.endDate = dateRange.value[1];
    }

    const res: any = await getPromotionStatistics(params);
    Object.assign(statistics, res);
  } catch (e) {
    ElMessage.error('加载统计数据失败');
  }

  // 加载 ROI 概览
  try {
    const roiParams: any = {};
    if (dateRange.value && dateRange.value.length === 2) {
      roiParams.startDate = dateRange.value[0];
      roiParams.endDate = dateRange.value[1];
    }
    const roiRes: any = await getRoiOverview(roiParams);
    Object.assign(roiOverview, roiRes);
  } catch (e) {
    // 忽略
  }

  // 加载 ROI 活动排行
  try {
    const roiParams: any = {};
    if (dateRange.value && dateRange.value.length === 2) {
      roiParams.startDate = dateRange.value[0];
      roiParams.endDate = dateRange.value[1];
    }
    const roiCampRes: any = await getRoiCampaigns({ ...roiParams, limit: 10 });
    roiCampaigns.value = roiCampRes || [];
  } catch (e) {
    // 忽略
  }

  // 加载活动排行（兼容旧逻辑）
  try {
    const res: any = await getCampaigns({ page: 0, size: 10 });
    const campaigns = res.content || res || [];
    topCampaigns.value = campaigns.slice(0, 10);
  } catch (e) {
    // 忽略
  }
};

onMounted(loadStatistics);
</script>

<style scoped>
.marketing-dashboard {
  padding: 20px;
}
.stats-row {
  margin: 20px 0;
}
.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: var(--el-color-primary);
  text-align: center;
}
.stat-label {
  text-align: center;
  color: #666;
  margin-top: 8px;
  font-size: 14px;
}
.filter-card {
  margin: 20px 0;
}
.chart-row {
  margin: 20px 0;
}
.bearer-chart {
  padding: 10px;
}
.bearer-item {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.bearer-name {
  width: 80px;
  font-size: 14px;
  color: #666;
}
.bearer-amount {
  width: 100px;
  text-align: right;
  font-size: 14px;
  color: #333;
}
.usage-summary {
  display: flex;
  justify-content: space-around;
  padding: 20px 0;
}
.summary-item {
  text-align: center;
}
.summary-value {
  font-size: 32px;
  font-weight: bold;
  color: #67c23a;
}
.summary-label {
  margin-top: 8px;
  color: #666;
  font-size: 14px;
}
.top-campaigns {
  margin-top: 20px;
}
</style>
