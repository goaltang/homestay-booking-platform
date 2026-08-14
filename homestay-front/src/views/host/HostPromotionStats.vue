<template>
  <div class="host-promotion-stats">
    <h2>营销数据</h2>

    <el-row :gutter="20" class="stats-cards">
      <el-col :span="6">
        <el-card>
          <div class="stat-value">{{ stats.totalCampaigns }}</div>
          <div class="stat-label">活动总数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat-value" style="color: #67c23a">{{ stats.activeCampaigns }}</div>
          <div class="stat-label">进行中活动</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat-value" style="color: #f56c6c">¥{{ formatAmount(stats.totalDiscount) }}</div>
          <div class="stat-label">累计优惠金额</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat-value">{{ stats.totalUsageCount }}</div>
          <div class="stat-label">优惠使用次数</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ROI 概览 -->
    <el-row :gutter="20" class="stats-cards">
      <el-col :span="6">
        <el-card>
          <div class="stat-value" style="color: #67c23a">¥{{ formatAmount(roi.totalGmv) }}</div>
          <div class="stat-label">带动 GMV</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat-value" style="color: #f56c6c">¥{{ formatAmount(roi.totalDiscountCost) }}</div>
          <div class="stat-label">优惠成本</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat-value" style="color: #e6a23c">{{ roi.roi }}x</div>
          <div class="stat-label">ROI</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat-value">{{ roi.totalOrders }}</div>
          <div class="stat-label">带动订单</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ROI 活动明细 -->
    <el-card class="detail-table" v-loading="loading">
      <template #header>
        <span>活动 ROI 明细</span>
      </template>
      <el-empty v-if="!roiCampaigns.length" description="暂无数据" />
      <el-table v-else :data="roiCampaigns" stripe>
        <el-table-column prop="campaignName" label="活动名称" />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            {{ formatCampaignType(row.campaignType) }}
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
        <el-table-column label="预算使用" width="120">
          <template #default="{ row }">
            <el-progress :percentage="Math.min(row.budgetUsageRate || 0, 100)" :status="row.budgetUsageRate >= 80 ? 'exception' : ''" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { getHostCampaigns, getHostPromotionStats, getHostRoiOverview, getHostRoiCampaigns } from "@/api/hostPromotion";
import type { HostCampaign, HostPromotionStats, HostRoiOverview, HostRoiCampaign } from "@/api/hostPromotion";

const loading = ref(false);
const campaignList = ref<HostCampaign[]>([]);
const stats = reactive<HostPromotionStats>({
  totalCampaigns: 0,
  activeCampaigns: 0,
  totalDiscount: 0,
  totalUsageCount: 0,
  usedCount: 0,
});

const roi = reactive<HostRoiOverview>({
  totalGmv: 0,
  totalDiscountCost: 0,
  totalOrders: 0,
  totalUsageCount: 0,
  usedCount: 0,
  platformCost: 0,
  hostCost: 0,
  roi: 0,
  usageRate: 0,
  avgDiscountPerOrder: 0,
  avgOrderValue: 0,
});

const roiCampaigns = ref<HostRoiCampaign[]>([]);

function formatCampaignType(type: string) {
  const map: Record<string, string> = {
    FLASH_SALE: "限时折扣",
    HOMESTAY_DISCOUNT: "房源折扣",
    FULL_REDUCTION: "满减活动",
  };
  return map[type] || type;
}

function formatAmount(amount: number) {
  if (!amount) return "0.00";
  return amount.toFixed(2);
}

async function loadData() {
  loading.value = true;
  try {
    const [campaignRes, statsRes, roiRes, roiCampRes]: any = await Promise.all([
      getHostCampaigns({ page: 0, size: 100 }),
      getHostPromotionStats(),
      getHostRoiOverview(),
      getHostRoiCampaigns(),
    ]);
    campaignList.value = campaignRes.content || [];
    Object.assign(stats, statsRes);
    Object.assign(roi, roiRes);
    roiCampaigns.value = roiCampRes || [];
  } catch (e) {
  } finally {
    loading.value = false;
  }
}

onMounted(loadData);
</script>

<style scoped>
.host-promotion-stats {
  padding: 20px;
}
.stats-cards {
  margin: 20px 0;
}
.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #409eff;
  text-align: center;
}
.stat-label {
  text-align: center;
  color: #666;
  margin-top: 8px;
  font-size: 14px;
}
.detail-table {
  margin-top: 20px;
}
</style>
