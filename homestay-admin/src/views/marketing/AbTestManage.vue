<template>
  <div class="ab-test-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>AB测试实验</span>
          <el-button type="primary" @click="openCreate">创建实验</el-button>
        </div>
      </template>

      <el-table :data="experimentList" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="实验名称" min-width="150" />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            {{ row.experimentType === "CAMPAIGN" ? "活动" : "优惠券" }}
          </template>
        </el-table-column>
        <el-table-column prop="targetId" label="目标ID" width="100" />
        <el-table-column prop="trafficPercent" label="流量" width="100">
          <template #default="{ row }">{{ row.trafficPercent }}%</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startAt" label="开始时间" width="160">
          <template #default="{ row }">{{ formatDate(row.startAt) }}</template>
        </el-table-column>
        <el-table-column prop="endAt" label="结束时间" width="160">
          <template #default="{ row }">{{ formatDate(row.endAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openReport(row)">报告</el-button>
            <el-button link type="success" v-if="row.status === 'DRAFT'" @click="startExp(row)"
              >启动</el-button
            >
            <el-button link type="warning" v-if="row.status === 'RUNNING'" @click="stopExp(row)"
              >停止</el-button
            >
            <el-button
              link
              type="danger"
              v-if="row.status === 'DRAFT' || row.status === 'ENDED'"
              @click="deleteExp(row)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="fetchExperiments"
        class="pagination"
      />
    </el-card>

    <!-- 创建/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" title="创建AB实验" width="600px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="实验名称">
          <el-input v-model="form.name" placeholder="如：首页 Banner 文案测试" />
        </el-form-item>
        <el-form-item label="实验描述">
          <el-input v-model="form.description" type="textarea" rows="2" />
        </el-form-item>
        <el-form-item label="实验类型">
          <el-select v-model="form.experimentType" style="width: 100%">
            <el-option label="活动" value="CAMPAIGN" />
            <el-option label="优惠券模板" value="COUPON_TEMPLATE" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标ID">
          <el-input-number v-model="form.targetId" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="流量占比">
          <el-slider v-model="form.trafficPercent" :min="1" :max="100" show-input />
        </el-form-item>
        <el-form-item label="主要指标">
          <el-select v-model="form.primaryMetric" style="width: 100%">
            <el-option label="转化率" value="CONVERSION" />
            <el-option label="下单率" value="ORDER_RATE" />
            <el-option label="GMV" value="GMV" />
          </el-select>
        </el-form-item>
        <el-form-item label="实验组">
          <div v-for="(v, idx) in form.variants" :key="idx" class="variant-row">
            <el-input v-model="v.variantKey" placeholder="组标识" style="width: 120px" />
            <el-input
              v-model="v.name"
              placeholder="组名称"
              style="width: 140px; margin-left: 8px"
            />
            <el-input-number
              v-model="v.trafficRatio"
              :min="1"
              :max="100"
              style="width: 100px; margin-left: 8px"
            />
            <el-button link type="danger" @click="removeVariant(idx)" style="margin-left: 8px"
              >删除</el-button
            >
          </div>
          <el-button type="primary" link @click="addVariant">添加实验组</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate" :loading="submitting">创建</el-button>
      </template>
    </el-dialog>

    <!-- 报告弹窗 -->
    <el-dialog v-model="reportVisible" title="实验报告" width="800px">
      <div v-if="reportData" class="report-content">
        <h3>{{ reportData.experiment?.name }}</h3>
        <p class="report-desc">{{ reportData.experiment?.description }}</p>

        <el-table :data="reportData.variants" stripe>
          <el-table-column prop="name" label="组名称" width="120" />
          <el-table-column prop="trafficRatio" label="流量占比" width="100" />
          <el-table-column prop="impression" label="曝光人数" width="100" />
          <el-table-column prop="convert" label="转化人数" width="100" />
          <el-table-column prop="conversionRate" label="转化率" width="100">
            <template #default="{ row }">{{ row.conversionRate }}%</template>
          </el-table-column>
          <el-table-column prop="liftRate" label="相对提升" width="100">
            <template #default="{ row }">
              <span
                v-if="row.liftRate !== undefined"
                :class="row.liftRate > 0 ? 'positive' : 'negative'"
              >
                {{ row.liftRate > 0 ? "+" : "" }}{{ row.liftRate }}%
              </span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="pValue" label="p-value" width="100">
            <template #default="{ row }">
              <el-tag
                v-if="row.pValue !== undefined"
                :type="row.isSignificant ? 'success' : 'info'"
                size="small"
              >
                {{ row.pValue }}
              </el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="显著性" width="100">
            <template #default="{ row }">
              <el-tag
                v-if="row.isSignificant !== undefined"
                :type="row.isSignificant ? 'success' : 'info'"
                size="small"
              >
                {{ row.isSignificant ? "显著" : "不显著" }}
              </el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import request from "@/utils/request";

const experimentList = ref<any[]>([]);
const loading = ref(false);
const page = ref(1);
const pageSize = ref(20);
const total = ref(0);

const dialogVisible = ref(false);
const submitting = ref(false);
const form = reactive({
  name: "",
  description: "",
  experimentType: "CAMPAIGN",
  targetId: 1,
  trafficPercent: 100,
  primaryMetric: "CONVERSION",
  variants: [
    { variantKey: "control", name: "对照组", trafficRatio: 50 },
    { variantKey: "variant_a", name: "实验组A", trafficRatio: 50 },
  ] as any[],
});

const reportVisible = ref(false);
const reportData = ref<any>(null);

const fetchExperiments = async () => {
  loading.value = true;
  try {
    const res: any = await request({
      url: "/api/admin/ab-tests",
      method: "get",
      params: { page: page.value - 1, size: pageSize.value },
    });
    const data = res.data || res;
    experimentList.value = data.content || data || [];
    total.value = data.totalElements || data.length || 0;
  } catch (e) {
    ElMessage.error("获取实验列表失败");
  } finally {
    loading.value = false;
  }
};

const openCreate = () => {
  form.name = "";
  form.description = "";
  form.experimentType = "CAMPAIGN";
  form.targetId = 1;
  form.trafficPercent = 100;
  form.primaryMetric = "CONVERSION";
  form.variants = [
    { variantKey: "control", name: "对照组", trafficRatio: 50 },
    { variantKey: "variant_a", name: "实验组A", trafficRatio: 50 },
  ];
  dialogVisible.value = true;
};

const addVariant = () => {
  form.variants.push({
    variantKey: `variant_${String.fromCharCode(98 + form.variants.length - 1)}`,
    name: `实验组${String.fromCharCode(65 + form.variants.length - 1)}`,
    trafficRatio: 0,
  });
};

const removeVariant = (idx: number) => {
  if (form.variants.length <= 2) {
    ElMessage.warning("至少需要2个实验组");
    return;
  }
  form.variants.splice(idx, 1);
};

const submitCreate = async () => {
  if (!form.name.trim()) {
    ElMessage.warning("请输入实验名称");
    return;
  }
  const totalRatio = form.variants.reduce((sum, v) => sum + (v.trafficRatio || 0), 0);
  if (totalRatio !== 100) {
    ElMessage.warning(`实验组流量占比之和必须等于100，当前为${totalRatio}`);
    return;
  }
  submitting.value = true;
  try {
    await request({
      url: "/api/admin/ab-tests",
      method: "post",
      data: {
        name: form.name,
        description: form.description,
        experimentType: form.experimentType,
        targetId: form.targetId,
        trafficPercent: form.trafficPercent,
        primaryMetric: form.primaryMetric,
        variants: form.variants,
      },
    });
    ElMessage.success("实验创建成功");
    dialogVisible.value = false;
    fetchExperiments();
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.error || "创建失败");
  } finally {
    submitting.value = false;
  }
};

const startExp = async (row: any) => {
  try {
    await request({ url: `/api/admin/ab-tests/${row.id}/start`, method: "post" });
    ElMessage.success("实验已启动");
    fetchExperiments();
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.error || "启动失败");
  }
};

const stopExp = async (row: any) => {
  try {
    await ElMessageBox.confirm("确定要停止该实验吗？", "确认", { type: "warning" });
    await request({ url: `/api/admin/ab-tests/${row.id}/stop`, method: "post" });
    ElMessage.success("实验已停止");
    fetchExperiments();
  } catch (e: any) {
    if (e !== "cancel") {
      ElMessage.error(e?.response?.data?.error || "停止失败");
    }
  }
};

const deleteExp = async (row: any) => {
  try {
    await ElMessageBox.confirm("确定要删除该实验吗？", "确认", { type: "warning" });
    await request({ url: `/api/admin/ab-tests/${row.id}`, method: "delete" });
    ElMessage.success("实验已删除");
    fetchExperiments();
  } catch (e: any) {
    if (e !== "cancel") {
      ElMessage.error(e?.response?.data?.error || "删除失败");
    }
  }
};

const openReport = async (row: any) => {
  reportVisible.value = true;
  reportData.value = null;
  try {
    const res: any = await request({ url: `/api/admin/ab-tests/${row.id}/report`, method: "get" });
    reportData.value = res.data || res;
  } catch (e) {
    ElMessage.error("获取报告失败");
  }
};

const statusText = (status: string) => {
  const map: Record<string, string> = {
    DRAFT: "草稿",
    RUNNING: "运行中",
    PAUSED: "已暂停",
    ENDED: "已结束",
  };
  return map[status] || status;
};

const statusType = (status: string) => {
  const map: Record<string, any> = {
    DRAFT: "info",
    RUNNING: "success",
    PAUSED: "warning",
    ENDED: "",
  };
  return map[status] || "info";
};

const formatDate = (date: string) => {
  if (!date) return "-";
  return new Date(date).toLocaleString();
};

onMounted(fetchExperiments);
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.pagination {
  margin-top: 20px;
  text-align: right;
}
.variant-row {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}
.report-content h3 {
  margin: 0 0 8px;
}
.report-desc {
  color: #606266;
  margin-bottom: 16px;
}
.positive {
  color: #67c23a;
  font-weight: bold;
}
.negative {
  color: #f56c6c;
  font-weight: bold;
}
</style>
