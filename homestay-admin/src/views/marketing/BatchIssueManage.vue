<template>
  <div class="batch-issue-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>批量发券</span>
        </div>
      </template>

      <el-form :model="taskForm" label-width="120px">
        <el-form-item label="选择模板">
          <el-select
            v-model="taskForm.templateId"
            placeholder="请选择优惠券模板"
            style="width: 300px"
          >
            <el-option
              v-for="t in activeTemplates"
              :key="t.id"
              :label="t.name + ' (库存:' + (t.totalStock || '∞') + ')'"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="任务名称">
          <el-input
            v-model="taskForm.name"
            placeholder="如：30天未下单用户召回"
            style="width: 300px"
          />
        </el-form-item>
        <el-form-item label="筛选条件">
          <el-select
            v-model="taskForm.filterType"
            placeholder="选择用户筛选条件"
            style="width: 300px"
          >
            <el-option label="全部用户" value="ALL" />
            <el-option label="新注册用户（7天内）" value="NEW_USER" />
            <el-option label="流失风险用户（30天未活跃）" value="AT_RISK" />
            <el-option label="30天未下单用户" value="NO_ORDER_30D" />
            <el-option label="高价值用户（历史消费≥1000）" value="HIGH_VALUE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="createTask" :loading="creating">创建并执行</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card style="margin-top: 20px">
      <template #header>
        <span>批量发券任务列表</span>
      </template>
      <el-table :data="taskList" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="任务名称" min-width="150" />
        <el-table-column label="筛选条件" width="150">
          <template #default="{ row }">
            {{ formatFilterType(row.filterType) }}
          </template>
        </el-table-column>
        <el-table-column prop="totalCount" label="目标人数" width="100" />
        <el-table-column prop="successCount" label="成功" width="80" />
        <el-table-column prop="failCount" label="失败" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="formatStatusType(row.status)">{{ formatStatus(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button
              link
              type="warning"
              @click="retryTask(row)"
              :disabled="row.failCount === 0 || row.status !== 'COMPLETED'"
              >重试失败</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="fetchTasks"
        class="pagination"
      />
    </el-card>

    <!-- 任务详情弹窗 -->
    <el-dialog v-model="detailVisible" title="任务详情" width="700px">
      <div v-if="detailTask" class="detail-stats">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-statistic title="目标人数" :value="detailTask.totalCount" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="成功" :value="detailSuccess" value-style="color: #67c23a" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="失败" :value="detailFail" value-style="color: #f56c6c" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="成功率" :value="detailRate" suffix="%" />
          </el-col>
        </el-row>
      </div>

      <el-divider />

      <div class="detail-filter">
        <el-radio-group v-model="itemStatus" @change="fetchDetailItems">
          <el-radio-button label="">全部</el-radio-button>
          <el-radio-button label="SUCCESS">成功</el-radio-button>
          <el-radio-button label="FAILED">失败</el-radio-button>
        </el-radio-group>
      </div>

      <el-table :data="detailItems" v-loading="detailLoading" size="small" stripe height="300">
        <el-table-column prop="userId" label="用户ID" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">{{
              row.status === "SUCCESS" ? "成功" : "失败"
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMsg" label="错误信息" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="时间" width="160">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="detailPage"
        v-model:page-size="detailPageSize"
        :total="detailTotal"
        layout="total, prev, pager, next"
        @current-change="fetchDetailItems"
        class="pagination"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import request from "@/utils/request";

const activeTemplates = ref<any[]>([]);
const taskList = ref<any[]>([]);
const loading = ref(false);
const creating = ref(false);
const page = ref(1);
const pageSize = ref(20);
const total = ref(0);

const taskForm = reactive({
  templateId: null as number | null,
  name: "",
  filterType: "ALL",
});

// 详情弹窗
const detailVisible = ref(false);
const detailTask = ref<any>(null);
const detailSuccess = ref(0);
const detailFail = ref(0);
const detailItems = ref<any[]>([]);
const detailLoading = ref(false);
const detailPage = ref(1);
const detailPageSize = ref(20);
const detailTotal = ref(0);
const itemStatus = ref("");
const currentTaskId = ref<number | null>(null);

const detailRate = computed(() => {
  const total = detailSuccess.value + detailFail.value;
  if (total === 0) return 0;
  return Math.round((detailSuccess.value / total) * 100);
});

const fetchTemplates = async () => {
  try {
    const res: any = await request({
      url: "/api/admin/promotions/templates",
      method: "get",
      params: { page: 0, size: 100, status: "ACTIVE" },
    });
    const data = res.data || res;
    activeTemplates.value = (data.content || data || []).filter((t: any) => t.status === "ACTIVE");
  } catch (e) {
    console.error("获取模板失败", e);
  }
};

const fetchTasks = async () => {
  loading.value = true;
  try {
    const res: any = await request({
      url: "/api/admin/promotions/batch-tasks",
      method: "get",
      params: { page: page.value - 1, size: pageSize.value },
    });
    const data = res.data || res;
    taskList.value = data.content || data || [];
    total.value = data.totalElements || data.length || 0;
  } catch (e) {
    ElMessage.error("获取任务列表失败");
  } finally {
    loading.value = false;
  }
};

const createTask = async () => {
  if (!taskForm.templateId) {
    ElMessage.warning("请选择优惠券模板");
    return;
  }
  if (!taskForm.name.trim()) {
    ElMessage.warning("请输入任务名称");
    return;
  }
  creating.value = true;
  try {
    await request({
      url: "/api/admin/promotions/batch-tasks",
      method: "post",
      data: {
        templateId: taskForm.templateId,
        name: taskForm.name,
        filterType: taskForm.filterType,
        filterParams: {},
      },
    });
    ElMessage.success("批量发券任务已创建并开始执行");
    taskForm.name = "";
    taskForm.templateId = null;
    fetchTasks();
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.error || "创建失败");
  } finally {
    creating.value = false;
  }
};

const openDetail = async (row: any) => {
  currentTaskId.value = row.id;
  detailTask.value = row;
  detailVisible.value = true;
  detailPage.value = 1;
  itemStatus.value = "";
  // 加载统计
  try {
    const res: any = await request({
      url: `/api/admin/promotions/batch-tasks/${row.id}`,
      method: "get",
    });
    const data = res.data || res;
    detailSuccess.value = data.successCount || 0;
    detailFail.value = data.failCount || 0;
  } catch (e) {}
  await fetchDetailItems();
};

const fetchDetailItems = async () => {
  if (!currentTaskId.value) return;
  detailLoading.value = true;
  try {
    const res: any = await request({
      url: `/api/admin/promotions/batch-tasks/${currentTaskId.value}/items`,
      method: "get",
      params: { status: itemStatus.value, page: detailPage.value - 1, size: detailPageSize.value },
    });
    const data = res.data || res;
    detailItems.value = data.content || data || [];
    detailTotal.value = data.totalElements || data.length || 0;
  } catch (e) {
    ElMessage.error("获取明细失败");
  } finally {
    detailLoading.value = false;
  }
};

const retryTask = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定对任务「${row.name}」的失败项进行重试吗？`, "重试确认", {
      type: "warning",
    });
    await request({
      url: `/api/admin/promotions/batch-tasks/${row.id}/retry-failed`,
      method: "post",
    });
    ElMessage.success("重试任务已启动");
    fetchTasks();
  } catch (e: any) {
    if (e !== "cancel") {
      ElMessage.error(e?.response?.data?.error || "重试失败");
    }
  }
};

const formatFilterType = (type: string) => {
  const map: Record<string, string> = {
    ALL: "全部用户",
    NEW_USER: "新注册用户",
    AT_RISK: "流失风险用户",
    NO_ORDER_30D: "30天未下单",
    HIGH_VALUE: "高价值用户",
  };
  return map[type] || type;
};

const formatStatus = (status: string) => {
  const map: Record<string, string> = {
    PENDING: "待执行",
    PROCESSING: "执行中",
    COMPLETED: "已完成",
    FAILED: "失败",
  };
  return map[status] || status;
};

const formatStatusType = (status: string) => {
  const map: Record<string, any> = {
    PENDING: "info",
    PROCESSING: "warning",
    COMPLETED: "success",
    FAILED: "danger",
  };
  return map[status] || "info";
};

const formatDate = (date: string) => {
  if (!date) return "-";
  return new Date(date).toLocaleString();
};

onMounted(() => {
  fetchTemplates();
  fetchTasks();
});
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
.detail-stats {
  margin-bottom: 8px;
}
.detail-filter {
  margin-bottom: 12px;
}
</style>
