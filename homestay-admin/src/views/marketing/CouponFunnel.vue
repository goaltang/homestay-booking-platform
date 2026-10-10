<template>
  <div class="coupon-funnel-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>优惠券转化漏斗</span>
        </div>
      </template>

      <el-form :model="query" inline>
        <el-form-item label="优惠券模板">
          <el-select
            v-model="query.templateId"
            placeholder="选择模板"
            clearable
            style="width: 240px"
          >
            <el-option v-for="t in templates" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
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
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
        </el-form-item>
      </el-form>

      <!-- 漏斗图表 -->
      <div v-if="funnelData" class="funnel-section">
        <h3>转化漏斗</h3>
        <el-row :gutter="20">
          <el-col :span="4" v-for="(item, key) in funnelData.funnel" :key="key">
            <el-card class="funnel-card">
              <div class="funnel-title">{{ key }}</div>
              <div class="funnel-count">{{ item.count }}</div>
              <div class="funnel-rate">转化率 {{ item.conversionRate }}%</div>
            </el-card>
          </el-col>
        </el-row>
      </div>

      <!-- 渠道统计 -->
      <div v-if="channelData && channelData.channels" class="channel-section">
        <h3>渠道归因</h3>
        <el-table :data="channelData.channels" stripe>
          <el-table-column prop="channel" label="渠道" />
          <el-table-column prop="impression" label="曝光" />
          <el-table-column prop="click" label="点击" />
          <el-table-column prop="claim" label="领取" />
          <el-table-column prop="use" label="核销" />
          <el-table-column prop="usageRate" label="核销率">
            <template #default="{ row }">{{ row.usageRate }}%</template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage } from "element-plus";
import request from "@/utils/request";

const templates = ref<any[]>([]);
const query = reactive({
  templateId: null as number | null,
});
const dateRange = ref<[string, string] | null>(null);
const funnelData = ref<any>(null);
const channelData = ref<any>(null);

const fetchTemplates = async () => {
  try {
    const res: any = await request({
      url: "/api/admin/promotions/templates",
      method: "get",
      params: { page: 0, size: 100 },
    });
    const data = res.data || res;
    templates.value = data.content || data || [];
  } catch (e) {
    console.error("获取模板失败", e);
  }
};

const loadData = async () => {
  if (!query.templateId) {
    ElMessage.warning("请选择优惠券模板");
    return;
  }
  const params: any = { templateId: query.templateId };
  if (dateRange.value) {
    params.startDate = dateRange.value[0];
    params.endDate = dateRange.value[1];
  }
  try {
    const [funnelRes, channelRes] = await Promise.all([
      request({ url: "/api/admin/promotions/analytics/funnel", method: "get", params }),
      request({ url: "/api/admin/promotions/analytics/channels", method: "get", params }),
    ]);
    funnelData.value = funnelRes.data || funnelRes;
    channelData.value = channelRes.data || channelRes;
  } catch (e) {
    ElMessage.error("加载数据失败");
  }
};

onMounted(fetchTemplates);
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.funnel-section {
  margin-top: 24px;
}
.funnel-section h3 {
  margin-bottom: 16px;
}
.funnel-card {
  text-align: center;
}
.funnel-title {
  font-size: 14px;
  color: #606266;
  margin-bottom: 8px;
}
.funnel-count {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 4px;
}
.funnel-rate {
  font-size: 12px;
  color: #67c23a;
}
.channel-section {
  margin-top: 32px;
}
.channel-section h3 {
  margin-bottom: 16px;
}
</style>
