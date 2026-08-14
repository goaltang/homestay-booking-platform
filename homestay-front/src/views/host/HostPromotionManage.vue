<template>
  <div class="host-promotion-manage">
    <div class="page-header">
      <h2>营销活动管理</h2>
      <el-button type="primary" @click="showCreateDialog = true">
        <el-icon><Plus /></el-icon>
        创建活动
      </el-button>
    </div>

    <el-table :data="campaignList" v-loading="loading" stripe>
      <el-table-column prop="name" label="活动名称" min-width="180" />
      <el-table-column label="类型" width="120">
        <template #default="{ row }">
          <el-tag :type="row.campaignType === 'FLASH_SALE' ? 'danger' : 'warning'">
            {{ formatCampaignType(row.campaignType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时间范围" min-width="200">
        <template #default="{ row }">
          {{ formatDate(row.startAt) }} ~ {{ formatDate(row.endAt) }}
        </template>
      </el-table-column>
      <el-table-column label="预算使用" width="180">
        <template #default="{ row }">
          <div class="budget-info">
            <el-progress
              :percentage="calculateBudgetPercent(row)"
              :status="getBudgetStatus(row)"
              :stroke-width="8"
            />
            <span class="budget-text">
              {{ row.budgetUsed || 0 }} / {{ row.budgetTotal || '∞' }}
              <el-tag v-if="row.budgetAlertTriggered" size="small" type="warning">预警</el-tag>
            </span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">
            {{ formatStatus(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'ACTIVE'"
            size="small"
            @click="handlePause(row.id)"
          >
            暂停
          </el-button>
          <el-button
            v-if="row.status !== 'ENDED'"
            size="small"
            type="danger"
            @click="handleEnd(row.id)"
          >
            结束
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > 0"
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="loadCampaigns"
    />

    <!-- 创建活动弹窗 -->
    <el-dialog
      v-model="showCreateDialog"
      title="创建营销活动"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
        <el-form-item label="活动名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入活动名称" />
        </el-form-item>
        <el-form-item label="活动类型" prop="campaignType">
          <el-select v-model="form.campaignType" placeholder="选择活动类型">
            <el-option label="限时折扣" value="FLASH_SALE" />
            <el-option label="房源折扣" value="HOMESTAY_DISCOUNT" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围" prop="timeRange">
          <el-date-picker
            v-model="form.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
        <el-form-item label="预算上限">
          <el-input-number v-model="form.budgetTotal" :min="0" :precision="2" placeholder="不填表示无限制" />
        </el-form-item>
        <el-form-item label="预警阈值">
          <el-input-number v-model="form.budgetAlertThreshold" :min="0" :max="1" :precision="2" :step="0.05" placeholder="如 0.8" />
          <span class="form-tip">预算使用率达到该值时预警</span>
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="0" :max="100" />
        </el-form-item>
        <el-form-item label="允许叠加">
          <el-switch v-model="form.stackable" />
          <span class="form-tip">开启后可与平台活动叠加生效</span>
        </el-form-item>

        <el-divider>规则设置</el-divider>

        <div v-for="(rule, index) in form.rules" :key="index" class="rule-card">
          <div class="rule-header">
            <span>规则 {{ index + 1 }}</span>
            <el-button v-if="form.rules.length > 1" type="danger" size="small" text @click="removeRule(index)">删除</el-button>
          </div>
          <el-form-item label="规则类型">
            <el-select v-model="rule.ruleType" placeholder="选择规则类型">
              <el-option label="固定金额减免" value="AMOUNT_OFF" />
              <el-option label="百分比折扣" value="PERCENT_OFF" />
              <el-option label="满减" value="FULL_REDUCTION" />
            </el-select>
          </el-form-item>
          <el-form-item label="优惠金额" v-if="rule.ruleType === 'AMOUNT_OFF' || rule.ruleType === 'FULL_REDUCTION'">
            <el-input-number v-model="rule.discountAmount" :min="0" :precision="2" />
          </el-form-item>
          <el-form-item label="折扣率" v-if="rule.ruleType === 'PERCENT_OFF'">
            <el-input-number v-model="rule.discountRate" :min="0.01" :max="0.99" :precision="2" />
            <span class="form-tip">如 0.85 表示 8.5 折</span>
          </el-form-item>
          <el-form-item label="最大优惠">
            <el-input-number v-model="rule.maxDiscount" :min="0" :precision="2" placeholder="可选" />
          </el-form-item>
          <el-form-item label="门槛金额">
            <el-input-number v-model="rule.thresholdAmount" :min="0" :precision="2" placeholder="可选" />
          </el-form-item>
          <el-form-item label="最少入住">
            <el-input-number v-model="rule.minNights" :min="1" placeholder="可选" />
          </el-form-item>
          <el-form-item label="最多入住">
            <el-input-number v-model="rule.maxNights" :min="1" placeholder="可选" />
          </el-form-item>
          <el-form-item label="仅首单">
            <el-switch v-model="rule.firstOrderOnly" />
          </el-form-item>
        </div>

        <el-button type="primary" text @click="addRule" style="margin-top: 8px">
          <el-icon><Plus /></el-icon>
          添加规则
        </el-button>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreate" :loading="submitting">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus } from "@element-plus/icons-vue";
import {
  getHostCampaigns,
  createHostCampaign,
  pauseHostCampaign,
  endHostCampaign,
  type HostCampaign,
} from "@/api/hostPromotion";

interface RuleFormItem {
  ruleType: string;
  discountAmount: number | null;
  discountRate: number | null;
  maxDiscount: number | null;
  thresholdAmount: number | null;
  minNights: number | null;
  maxNights: number | null;
  firstOrderOnly: boolean;
}

interface HostCampaignFormLocal {
  name: string;
  campaignType: string;
  timeRange: string[];
  priority: number;
  budgetTotal: number | null;
  budgetAlertThreshold: number | null;
  stackable: boolean;
  rules: RuleFormItem[];
}

const loading = ref(false);
const campaignList = ref<HostCampaign[]>([]);
const page = ref(1);
const size = ref(10);
const total = ref(0);

const showCreateDialog = ref(false);
const submitting = ref(false);
const formRef = ref();

function createEmptyRule(): RuleFormItem {
  return {
    ruleType: "PERCENT_OFF",
    discountAmount: null,
    discountRate: 0.9,
    maxDiscount: null,
    thresholdAmount: null,
    minNights: null,
    maxNights: null,
    firstOrderOnly: false,
  };
}

const form = reactive<HostCampaignFormLocal>({
  name: "",
  campaignType: "HOMESTAY_DISCOUNT",
  timeRange: [],
  priority: 0,
  budgetTotal: null,
  budgetAlertThreshold: null,
  stackable: false,
  rules: [createEmptyRule()],
});

function addRule() {
  form.rules.push(createEmptyRule());
}

function removeRule(index: number) {
  if (form.rules.length > 1) {
    form.rules.splice(index, 1);
  }
}

const rules = {
  name: [{ required: true, message: "请输入活动名称", trigger: "blur" }],
  campaignType: [{ required: true, message: "请选择活动类型", trigger: "change" }],
  timeRange: [{ required: true, message: "请选择时间范围", trigger: "change" }],
};

function formatCampaignType(type: string) {
  const map: Record<string, string> = {
    FLASH_SALE: "限时折扣",
    HOMESTAY_DISCOUNT: "房源折扣",
    FULL_REDUCTION: "满减活动",
  };
  return map[type] || type;
}

function formatStatus(status: string) {
  const map: Record<string, string> = {
    DRAFT: "草稿",
    ACTIVE: "进行中",
    PAUSED: "已暂停",
    ENDED: "已结束",
  };
  return map[status] || status;
}

function getStatusType(status: string) {
  const map: Record<string, any> = {
    DRAFT: "info",
    ACTIVE: "success",
    PAUSED: "warning",
    ENDED: "danger",
  };
  return map[status] || "info";
}

function formatDate(date: string) {
  if (!date) return "-";
  return new Date(date).toLocaleString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function calculateBudgetPercent(row: HostCampaign) {
  if (!row.budgetTotal || row.budgetTotal <= 0) return 0;
  const percent = Math.round(((row.budgetUsed || 0) / row.budgetTotal) * 100);
  return Math.min(percent, 100);
}

function getBudgetStatus(row: HostCampaign) {
  if (!row.budgetTotal || row.budgetTotal <= 0) return "";
  const percent = ((row.budgetUsed || 0) / row.budgetTotal) * 100;
  if (percent >= 100) return "exception";
  if (row.budgetAlertTriggered) return "warning";
  return "";
}

async function loadCampaigns() {
  loading.value = true;
  try {
    const res: any = await getHostCampaigns({ page: page.value - 1, size: size.value });
    campaignList.value = res.content || [];
    total.value = res.totalElements || 0;
  } catch (e) {
  } finally {
    loading.value = false;
  }
}

async function handleCreate() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;

  submitting.value = true;
  try {
    const rules = form.rules.map(r => ({
      ruleType: r.ruleType,
      discountAmount: r.ruleType !== 'PERCENT_OFF' ? r.discountAmount : null,
      discountRate: r.ruleType === 'PERCENT_OFF' ? r.discountRate : null,
      maxDiscount: r.maxDiscount,
      thresholdAmount: r.thresholdAmount,
      minNights: r.minNights,
      maxNights: r.maxNights,
      firstOrderOnly: r.firstOrderOnly,
    }));

    const payload: any = {
      name: form.name,
      campaignType: form.campaignType,
      startAt: form.timeRange[0],
      endAt: form.timeRange[1],
      priority: form.priority,
      budgetTotal: form.budgetTotal,
      budgetAlertThreshold: form.budgetAlertThreshold,
      stackable: form.stackable,
      rules,
    };
    await createHostCampaign(payload);
    ElMessage.success("活动创建成功");
    showCreateDialog.value = false;
    loadCampaigns();
  } catch (e: any) {
  } finally {
    submitting.value = false;
  }
}

async function handlePause(id: number) {
  try {
    await ElMessageBox.confirm("确定要暂停该活动吗？", "提示", { type: "warning" });
    await pauseHostCampaign(id);
    ElMessage.success("活动已暂停");
    loadCampaigns();
  } catch (e) {
    // 取消
  }
}

async function handleEnd(id: number) {
  try {
    await ElMessageBox.confirm("确定要结束该活动吗？结束后不可恢复", "警告", { type: "warning" });
    await endHostCampaign(id);
    ElMessage.success("活动已结束");
    loadCampaigns();
  } catch (e) {
    // 取消
  }
}

onMounted(loadCampaigns);
</script>

<style scoped>
.host-promotion-manage {
  padding: 20px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.budget-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.budget-text {
  font-size: 12px;
  color: #666;
}
.form-tip {
  margin-left: 8px;
  color: #999;
  font-size: 12px;
}
.rule-card {
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 12px;
}
.rule-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-weight: 500;
  color: #303133;
}
</style>
