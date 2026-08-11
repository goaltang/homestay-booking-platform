<template>
  <div class="campaign-list-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>营销活动管理</span>
          <el-button type="primary" @click="handleCreate">创建活动</el-button>
        </div>
      </template>

      <!-- 搜索筛选 -->
      <el-form :model="searchForm" inline class="search-form">
        <el-form-item label="名称">
          <el-input v-model="searchForm.name" placeholder="活动名称" clearable @keyup.enter="fetchData" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable>
            <el-option label="草稿" value="DRAFT" />
            <el-option label="进行中" value="ACTIVE" />
            <el-option label="已暂停" value="PAUSED" />
            <el-option label="已结束" value="ENDED" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="searchForm.campaignType" placeholder="全部" clearable>
            <el-option label="限时折扣" value="FLASH_SALE" />
            <el-option label="满减活动" value="FULL_REDUCTION" />
            <el-option label="房源折扣" value="HOMESTAY_DISCOUNT" />
          </el-select>
        </el-form-item>
        <el-form-item label="承担方">
          <el-select v-model="searchForm.subsidyBearer" placeholder="全部" clearable>
            <el-option label="平台" value="PLATFORM" />
            <el-option label="房东" value="HOST" />
            <el-option label="混合" value="MIXED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="campaignList" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="活动名称" min-width="150" />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-tag>{{ formatType(row.campaignType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="承担方" width="100">
          <template #default="{ row }">
            <el-tag :type="row.subsidyBearer === 'HOST' ? 'warning' : 'info'">
              {{ formatBearer(row.subsidyBearer) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="formatStatusType(row.status)">{{ formatStatus(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="预算使用" width="150">
          <template #default="{ row }">
            <div v-if="row.budgetTotal">
              <el-progress :percentage="calculateBudgetPercent(row)" :status="getBudgetStatus(row)" :stroke-width="6" />
              <span class="budget-text">{{ row.budgetUsed || 0 }} / {{ row.budgetTotal }}</span>
            </div>
            <span v-else class="budget-text">无限制</span>
          </template>
        </el-table-column>
        <el-table-column label="创建人" width="120">
          <template #default="{ row }">
            {{ row.hostId ? '房东#' + row.hostId : row.createdBy || '平台' }}
          </template>
        </el-table-column>
        <el-table-column prop="startAt" label="开始时间" width="160">
          <template #default="{ row }">
            {{ formatDate(row.startAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" type="primary" size="small" @click="handlePublish(row.id)">发布</el-button>
            <el-button v-if="row.status === 'ACTIVE'" type="warning" size="small" @click="handlePause(row.id)">暂停</el-button>
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" type="danger" size="small" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="fetchData"
        class="pagination"
      />
    </el-card>

    <!-- 创建/编辑活动对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑活动' : '创建活动'" width="750px" :close-on-click-modal="false">
      <el-form :model="form" label-width="110px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="活动名称">
              <el-input v-model="form.name" placeholder="请输入活动名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="活动类型">
              <el-select v-model="form.campaignType">
                <el-option label="限时折扣" value="FLASH_SALE" />
                <el-option label="满减活动" value="FULL_REDUCTION" />
                <el-option label="房源折扣" value="HOMESTAY_DISCOUNT" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="承担方">
              <el-select v-model="form.subsidyBearer">
                <el-option label="平台" value="PLATFORM" />
                <el-option label="房东" value="HOST" />
                <el-option label="混合" value="MIXED" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-input-number v-model="form.priority" :min="0" :max="100" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="时间范围">
          <el-date-picker v-model="dateRange" type="datetimerange" range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间" style="width: 100%" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="预算上限">
              <el-input-number v-model="form.budgetTotal" :min="0" :precision="2" placeholder="0表示无限制" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预警阈值">
              <el-input-number v-model="form.budgetAlertThreshold" :min="0" :max="1" :precision="2" :step="0.05" style="width: 100%" />
              <span class="form-tip">如 0.8 表示预算使用达80%时预警</span>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="允许叠加">
          <el-switch v-model="form.stackable" />
          <span class="form-tip" style="margin-left: 8px">开启后该活动可与其他活动同时生效</span>
        </el-form-item>

        <el-divider>优惠规则</el-divider>

        <div v-for="(rule, index) in form.rules" :key="index" class="rule-card">
          <div class="rule-header">
            <span>规则 {{ index + 1 }}</span>
            <el-button type="danger" size="small" text @click="removeRule(index)" v-if="form.rules.length > 1">删除</el-button>
          </div>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="规则类型" label-width="80px">
                <el-select v-model="rule.ruleType">
                  <el-option label="固定金额减免" value="AMOUNT_OFF" />
                  <el-option label="百分比折扣" value="PERCENT_OFF" />
                  <el-option label="满减" value="FULL_REDUCTION" />
                  <el-option label="每晩立减" value="PER_NIGHT_OFF" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="适用范围" label-width="80px">
                <el-select v-model="rule.scopeType">
                  <el-option label="全部" value="ALL" />
                  <el-option label="指定城市" value="CITY" />
                  <el-option label="指定房源" value="HOMESTAY" />
                  <el-option label="指定房东" value="HOST" />
                  <el-option label="指定分组" value="GROUP" />
                  <el-option label="指定类型" value="TYPE" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <!-- 阶梯满减配置 -->
          <div v-if="rule.ruleType === 'FULL_REDUCTION'" class="tier-config-section">
            <el-form-item label="阶梯满减" label-width="80px">
              <div class="tier-list">
                <div v-for="(tier, tIndex) in rule.tierConfig" :key="tIndex" class="tier-row">
                  <span class="tier-label">满</span>
                  <el-input-number v-model="tier.threshold" :min="0" :precision="2" :controls="false" style="width: 90px" />
                  <span class="tier-label">减</span>
                  <el-input-number v-model="tier.discount" :min="0" :precision="2" :controls="false" style="width: 90px" />
                  <el-button type="danger" size="small" text @click="rule.tierConfig.splice(tIndex, 1)" v-if="rule.tierConfig.length > 1">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
                <el-button type="primary" size="small" text @click="rule.tierConfig.push({ threshold: 0, discount: 0 })">
                  <el-icon><Plus /></el-icon> 添加档位
                </el-button>
              </div>
              <span class="form-tip">系统会自动选择满足门槛的最高档位</span>
            </el-form-item>
          </div>

          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="优惠金额" label-width="80px" v-if="rule.ruleType === 'AMOUNT_OFF' || rule.ruleType === 'PER_NIGHT_OFF'">
                <el-input-number v-model="rule.discountAmount" :min="0" :precision="2" style="width: 100%" />
              </el-form-item>
              <el-form-item label="折扣率" label-width="80px" v-if="rule.ruleType === 'PERCENT_OFF'">
                <el-input-number v-model="rule.discountRate" :min="0.01" :max="0.99" :precision="2" :step="0.05" style="width: 100%" />
              </el-form-item>
              <el-form-item label="优惠金额" label-width="80px" v-if="rule.ruleType === 'FULL_REDUCTION' && !hasValidTier(rule)">
                <el-input-number v-model="rule.discountAmount" :min="0" :precision="2" style="width: 100%" placeholder="单档满减金额" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="门槛金额" label-width="80px" v-if="rule.ruleType !== 'FULL_REDUCTION' || !hasValidTier(rule)">
                <el-input-number v-model="rule.thresholdAmount" :min="0" :precision="2" style="width: 100%" placeholder="可选" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="最大优惠" label-width="80px">
                <el-input-number v-model="rule.maxDiscount" :min="0" :precision="2" style="width: 100%" placeholder="可选" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="最少入住" label-width="80px">
                <el-input-number v-model="rule.minNights" :min="1" style="width: 100%" placeholder="可选" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="最多入住" label-width="80px">
                <el-input-number v-model="rule.maxNights" :min="1" style="width: 100%" placeholder="可选" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="仅首单" label-width="80px">
                <el-switch v-model="rule.firstOrderOnly" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="范围值" label-width="80px" v-if="rule.scopeType !== 'ALL'">
            <el-input v-model="rule.scopeValueInput" :placeholder="getScopePlaceholder(rule.scopeType)" />
            <span class="form-tip">{{ getScopeTip(rule.scopeType) }}</span>
          </el-form-item>
        </div>

        <el-button type="primary" text @click="addRule" style="margin-top: 8px">
          <el-icon><Plus /></el-icon>
          添加规则
        </el-button>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, Delete } from '@element-plus/icons-vue';
import {
  getCampaigns, createCampaign, updateCampaign,
  publishCampaign, pauseCampaign, deleteCampaign
} from '@/api/marketing';

// 阶梯配置项
interface TierItem {
  threshold: number;
  discount: number;
}

// 规则类型
interface RuleForm {
  ruleType: string;
  discountAmount: number | null;
  discountRate: number | null;
  thresholdAmount: number | null;
  maxDiscount: number | null;
  minNights: number | null;
  maxNights: number | null;
  scopeType: string;
  scopeValueInput: string;
  firstOrderOnly: boolean;
  tierConfig: TierItem[];
}

// 表单类型
interface CampaignForm {
  name: string;
  campaignType: string;
  budgetTotal: number | null;
  subsidyBearer: string;
  priority: number;
  stackable: boolean;
  budgetAlertThreshold: number | null;
  rules: RuleForm[];
}

const loading = ref(false);
const campaignList = ref<any[]>([]);
const page = ref(1);
const size = ref(20);
const total = ref(0);

const searchForm = reactive({
  name: '',
  status: '',
  campaignType: '',
  subsidyBearer: '',
});

const dialogVisible = ref(false);
const isEdit = ref(false);
const editId = ref<number | null>(null);
const dateRange = ref<[Date, Date] | null>(null);

function createEmptyRule(): RuleForm {
  return {
    ruleType: 'PERCENT_OFF',
    discountAmount: null,
    discountRate: 0.9,
    thresholdAmount: null,
    maxDiscount: null,
    minNights: null,
    maxNights: null,
    scopeType: 'ALL',
    scopeValueInput: '',
    firstOrderOnly: false,
    tierConfig: [{ threshold: 0, discount: 0 }],
  };
}

const form = reactive<CampaignForm>({
  name: '',
  campaignType: 'FULL_REDUCTION',
  budgetTotal: null,
  subsidyBearer: 'PLATFORM',
  priority: 0,
  stackable: false,
  budgetAlertThreshold: null,
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

function getScopePlaceholder(scopeType: string) {
  const map: Record<string, string> = {
    CITY: '城市编码，如 440100',
    HOMESTAY: '房源ID，多个用逗号分隔，如 1,2,3',
    HOST: '房东ID，如 10',
    GROUP: '分组ID，如 5',
    TYPE: '房源类型，如 entire,private',
  };
  return map[scopeType] || '';
}

function getScopeTip(scopeType: string) {
  const map: Record<string, string> = {
    CITY: '输入城市编码',
    HOMESTAY: '输入房源ID（逗号分隔）',
    HOST: '输入房东用户ID',
    GROUP: '输入房源分组ID',
    TYPE: '输入房源类型标识',
  };
  return map[scopeType] || '';
}

function buildScopeValueJson(scopeType: string, input: string): string {
  if (scopeType === 'ALL' || !input.trim()) {
    return '["*"]';
  }
  const values = input.split(',').map(s => s.trim()).filter(Boolean);
  // CITY/TYPE 使用字符串数组，其他使用数字数组
  if (scopeType === 'CITY' || scopeType === 'TYPE') {
    return JSON.stringify(values);
  }
  const nums = values.map(Number).filter(n => !isNaN(n));
  return JSON.stringify(nums);
}

function parseScopeValueInput(scopeType: string, scopeValueJson: string): string {
  if (!scopeValueJson || scopeType === 'ALL') return '';
  try {
    const arr = JSON.parse(scopeValueJson);
    if (Array.isArray(arr)) {
      return arr.join(', ');
    }
  } catch (e) {
    // ignore
  }
  return '';
}

const resetSearch = () => {
  searchForm.name = '';
  searchForm.status = '';
  searchForm.campaignType = '';
  searchForm.subsidyBearer = '';
  fetchData();
};

const fetchData = async () => {
  loading.value = true;
  try {
    const params: any = { page: page.value - 1, size: size.value };
    if (searchForm.name) params.name = searchForm.name;
    if (searchForm.status) params.status = searchForm.status;
    if (searchForm.campaignType) params.campaignType = searchForm.campaignType;
    if (searchForm.subsidyBearer) params.subsidyBearer = searchForm.subsidyBearer;

    const res: any = await getCampaigns(params);
    const data = res.data || res;
    campaignList.value = data.content || data || [];
    total.value = data.totalElements || data.length || 0;
  } catch (e) {
    ElMessage.error('获取活动列表失败');
  } finally {
    loading.value = false;
  }
};

const handleCreate = () => {
  isEdit.value = false;
  editId.value = null;
  form.name = '';
  form.campaignType = 'FULL_REDUCTION';
  form.budgetTotal = null;
  form.subsidyBearer = 'PLATFORM';
  form.priority = 0;
  form.stackable = false;
  form.budgetAlertThreshold = null;
  form.rules = [createEmptyRule()];
  dateRange.value = null;
  dialogVisible.value = true;
};

const handleEdit = (row: any) => {
  isEdit.value = true;
  editId.value = row.id;
  form.name = row.name;
  form.campaignType = row.campaignType;
  form.budgetTotal = row.budgetTotal || null;
  form.subsidyBearer = row.subsidyBearer || 'PLATFORM';
  form.priority = row.priority || 0;
  form.stackable = row.stackable || false;
  form.budgetAlertThreshold = row.budgetAlertThreshold || null;

  // 从已有规则还原
  if (row.rules && row.rules.length > 0) {
    form.rules = row.rules.map((r: any) => {
      let tierConfig: TierItem[] = [{ threshold: 0, discount: 0 }];
      if (r.tierConfigJson) {
        try {
          const parsed = JSON.parse(r.tierConfigJson);
          if (Array.isArray(parsed) && parsed.length > 0) {
            tierConfig = parsed.map((t: any) => ({
              threshold: Number(t.threshold) || 0,
              discount: Number(t.discount) || 0,
            }));
          }
        } catch (e) {
          // ignore
        }
      }
      return {
        ruleType: r.ruleType || 'PERCENT_OFF',
        discountAmount: r.discountAmount || null,
        discountRate: r.discountRate || null,
        thresholdAmount: r.thresholdAmount || null,
        maxDiscount: r.maxDiscount || null,
        minNights: r.minNights || null,
        maxNights: r.maxNights || null,
        scopeType: r.scopeType || 'ALL',
        scopeValueInput: parseScopeValueInput(r.scopeType || 'ALL', r.scopeValueJson || ''),
        firstOrderOnly: r.firstOrderOnly || false,
        tierConfig,
      };
    });
  } else {
    form.rules = [createEmptyRule()];
  }

  if (row.startAt && row.endAt) {
    dateRange.value = [new Date(row.startAt), new Date(row.endAt)];
  } else {
    dateRange.value = null;
  }
  dialogVisible.value = true;
};

const submitForm = async () => {
  try {
    if (!form.name.trim()) {
      ElMessage.warning('请输入活动名称');
      return;
    }
    if (!dateRange.value || dateRange.value.length !== 2) {
      ElMessage.warning('请选择时间范围');
      return;
    }

    // 构建规则数据（去除临时字段 scopeValueInput，序列化 tierConfig）
    const rules = form.rules.map(r => {
      const rule: any = {
        ruleType: r.ruleType,
        discountAmount: r.discountAmount,
        discountRate: r.ruleType === 'PERCENT_OFF' ? r.discountRate : null,
        maxDiscount: r.maxDiscount,
        thresholdAmount: r.thresholdAmount,
        minNights: r.minNights,
        maxNights: r.maxNights,
        scopeType: r.scopeType,
        scopeValueJson: buildScopeValueJson(r.scopeType, r.scopeValueInput),
        firstOrderOnly: r.firstOrderOnly,
      };
      // 阶梯满减：如果有有效阶梯配置，序列化为 JSON
      if (r.ruleType === 'FULL_REDUCTION' && r.tierConfig && r.tierConfig.length > 0) {
        const validTiers = r.tierConfig.filter(t => t.threshold > 0 && t.discount > 0);
        if (validTiers.length > 0) {
          rule.tierConfigJson = JSON.stringify(validTiers);
        }
      }
      return rule;
    });

    const payload: any = {
      name: form.name,
      campaignType: form.campaignType,
      startAt: dateRange.value[0].toISOString(),
      endAt: dateRange.value[1].toISOString(),
      priority: form.priority,
      stackable: form.stackable,
      budgetTotal: form.budgetTotal,
      subsidyBearer: form.subsidyBearer,
      budgetAlertThreshold: form.budgetAlertThreshold,
      status: isEdit.value ? undefined : 'DRAFT',
      rules,
    };

    if (isEdit.value && editId.value) {
      await updateCampaign(editId.value, payload);
      ElMessage.success('更新成功');
    } else {
      await createCampaign(payload);
      ElMessage.success('创建成功');
    }
    dialogVisible.value = false;
    fetchData();
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error || (isEdit.value ? '更新失败' : '创建失败'));
  }
};

const handlePublish = async (id: number) => {
  try {
    await publishCampaign(id);
    ElMessage.success('发布成功');
    fetchData();
  } catch (e) {
    ElMessage.error('发布失败');
  }
};

const handlePause = async (id: number) => {
  try {
    await pauseCampaign(id);
    ElMessage.success('暂停成功');
    fetchData();
  } catch (e) {
    ElMessage.error('暂停失败');
  }
};

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除该活动吗？', '提示', { type: 'warning' });
    await deleteCampaign(id);
    ElMessage.success('删除成功');
    fetchData();
  } catch (e) {
    // 取消
  }
};

const formatType = (type: string) => {
  const map: Record<string, string> = {
    FLASH_SALE: '限时折扣',
    FULL_REDUCTION: '满减',
    HOMESTAY_DISCOUNT: '房源折扣',
  };
  return map[type] || type;
};

const formatBearer = (bearer: string) => {
  const map: Record<string, string> = {
    PLATFORM: '平台',
    HOST: '房东',
    MIXED: '混合',
  };
  return map[bearer] || bearer;
};

const hasValidTier = (rule: RuleForm): boolean => {
  return rule.tierConfig.some(t => t.threshold > 0 && t.discount > 0);
};

const formatStatus = (status: string) => {
  const map: Record<string, string> = {
    ACTIVE: '进行中',
    PAUSED: '已暂停',
    DRAFT: '草稿',
    ENDED: '已结束',
  };
  return map[status] || status;
};

const formatStatusType = (status: string) => {
  const map: Record<string, any> = {
    ACTIVE: 'success',
    PAUSED: 'warning',
    DRAFT: 'info',
    ENDED: 'danger',
  };
  return map[status] || 'info';
};

const formatDate = (date: string) => {
  if (!date) return '-';
  return new Date(date).toLocaleString();
};

const calculateBudgetPercent = (row: any) => {
  if (!row.budgetTotal || row.budgetTotal <= 0) return 0;
  const percent = Math.round(((row.budgetUsed || 0) / row.budgetTotal) * 100);
  return Math.min(percent, 100);
};

const getBudgetStatus = (row: any) => {
  if (!row.budgetTotal) return '';
  const percent = ((row.budgetUsed || 0) / row.budgetTotal) * 100;
  if (percent >= 100) return 'exception';
  if (row.budgetAlertTriggered) return 'warning';
  return '';
};

onMounted(fetchData);
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.search-form {
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #eee;
}
.pagination {
  margin-top: 20px;
  text-align: right;
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
.tier-config-section {
  background: #f0f9ff;
  border: 1px dashed var(--el-color-primary);
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 12px;
}
.tier-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.tier-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.tier-label {
  color: #606266;
  font-size: 13px;
}
</style>
