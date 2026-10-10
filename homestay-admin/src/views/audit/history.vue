<template>
  <div class="audit-history">
    <!-- 页面头部 -->
    <div class="page-header">
      <el-card shadow="never" class="header-card">
        <div class="header-content">
          <div class="header-left">
            <h1>审核历史记录</h1>
            <p>查看所有房源的审核历史和操作记录</p>
          </div>
          <div class="header-actions">
            <el-button type="primary" @click="refreshData">
              <el-icon>
                <Refresh />
              </el-icon>
              刷新数据
            </el-button>
            <el-button @click="exportHistory">
              <el-icon>
                <Download />
              </el-icon>
              导出记录
            </el-button>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 筛选条件 -->
    <el-card shadow="never" class="filter-card">
      <el-form :model="filterForm" inline label-width="80px">
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="filterForm.dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 350px"
          />
        </el-form-item>
        <el-form-item label="操作类型">
          <el-select v-model="filterForm.actionType" placeholder="选择操作类型" clearable>
            <el-option label="全部" value="" />
            <el-option label="提交审核" value="SUBMIT" />
            <el-option label="批准上架" value="APPROVE" />
            <el-option label="拒绝申请" value="REJECT" />
            <el-option label="重新提交" value="RESUBMIT" />
          </el-select>
        </el-form-item>
        <el-form-item label="审核员">
          <el-input v-model="filterForm.reviewerName" placeholder="请输入审核员姓名" clearable />
        </el-form-item>
        <el-form-item label="房源ID">
          <el-input v-model="filterForm.homestayId" placeholder="请输入房源ID" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="applyFilter">
            <el-icon>
              <Search />
            </el-icon>
            查询
          </el-button>
          <el-button @click="resetFilter">
            <el-icon>
              <RefreshLeft />
            </el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 历史记录表格 -->
    <el-card shadow="never" class="table-card">
      <el-table
        :data="auditHistory"
        v-loading="loading"
        stripe
        border
        style="width: 100%"
        :default-sort="{ prop: 'createdAt', order: 'descending' }"
      >
        <el-table-column prop="id" label="记录ID" width="80" />
        <el-table-column prop="homestayId" label="房源ID" width="100" />
        <el-table-column
          prop="homestayTitle"
          label="房源名称"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column prop="actionType" label="操作类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getActionTagType(row.actionType)" size="small">
              {{ getActionText(row.actionType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reviewerName" label="操作人" width="120" />
        <el-table-column prop="oldStatus" label="原状态" width="100">
          <template #default="{ row }">
            <el-tag type="info" size="small">{{ formatStatus(row.oldStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="newStatus" label="新状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.newStatus)" size="small">
              {{ formatStatus(row.newStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="reviewReason"
          label="审核原因"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column prop="createdAt" label="操作时间" width="160" sortable>
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="viewDetail(row)"> 查看详情 </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="审核记录详情" width="60%">
      <div v-if="currentRecord" class="detail-content">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="记录ID">{{ currentRecord.id }}</el-descriptions-item>
          <el-descriptions-item label="房源ID">{{ currentRecord.homestayId }}</el-descriptions-item>
          <el-descriptions-item label="房源名称" :span="2">{{
            currentRecord.homestayTitle
          }}</el-descriptions-item>
          <el-descriptions-item label="操作类型">
            <el-tag :type="getActionTagType(currentRecord.actionType)">
              {{ getActionText(currentRecord.actionType) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="操作人">{{
            currentRecord.reviewerName
          }}</el-descriptions-item>
          <el-descriptions-item label="原状态">
            <el-tag type="info">{{ formatStatus(currentRecord.oldStatus) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="新状态">
            <el-tag :type="getStatusTagType(currentRecord.newStatus)">
              {{ formatStatus(currentRecord.newStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="审核原因" :span="2">{{
            currentRecord.reviewReason || "无"
          }}</el-descriptions-item>
          <el-descriptions-item label="备注说明" :span="2">{{
            currentRecord.reviewNotes || "无"
          }}</el-descriptions-item>
          <el-descriptions-item label="操作时间" :span="2">{{
            formatDateTime(currentRecord.createdAt)
          }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="viewHomestay">查看房源</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage } from "element-plus";
import { Refresh, Download, Search, RefreshLeft } from "@element-plus/icons-vue";
import { getAllAuditHistory, type AuditLog } from "@/api/homestay";
import { useRouter } from "vue-router";

const router = useRouter();

// 响应式数据
const loading = ref(false);
const auditHistory = ref<AuditLog[]>([]);
const detailDialogVisible = ref(false);
const currentRecord = ref<AuditLog | null>(null);

// 筛选表单
const filterForm = reactive({
  dateRange: [] as string[],
  actionType: "",
  reviewerName: "",
  homestayId: "",
});

// 分页信息
const pagination = reactive({
  page: 1,
  size: 20,
  total: 0,
});

// 加载审核历史数据
const loadAuditHistory = async () => {
  try {
    loading.value = true;

    const params = {
      page: pagination.page,
      size: pagination.size,
      ...filterForm,
      startTime: filterForm.dateRange[0] || "",
      endTime: filterForm.dateRange[1] || "",
    };

    // 移除dateRange，使用startTime和endTime
    delete (params as any).dateRange;

    const result = await getAllAuditHistory(params);
    auditHistory.value = result.list || [];
    pagination.total = result.total || 0;
  } catch (error) {
    console.error("获取审核历史失败:", error);
    ElMessage.error("获取审核历史失败");
  } finally {
    loading.value = false;
  }
};

// 刷新数据
const refreshData = () => {
  loadAuditHistory();
  ElMessage.success("数据已刷新");
};

// 应用筛选
const applyFilter = () => {
  pagination.page = 1;
  loadAuditHistory();
};

// 重置筛选
const resetFilter = () => {
  Object.assign(filterForm, {
    dateRange: [],
    actionType: "",
    reviewerName: "",
    homestayId: "",
  });
  pagination.page = 1;
  loadAuditHistory();
};

// 分页处理
const handleSizeChange = (val: number) => {
  pagination.size = val;
  pagination.page = 1;
  loadAuditHistory();
};

const handleCurrentChange = (val: number) => {
  pagination.page = val;
  loadAuditHistory();
};

// 查看详情
const viewDetail = (record: AuditLog) => {
  currentRecord.value = record;
  detailDialogVisible.value = true;
};

// 查看房源
const viewHomestay = () => {
  if (currentRecord.value) {
    router.push(`/homestays/edit/${currentRecord.value.homestayId}`);
  }
};

// 导出历史记录
const exportHistory = () => {
  ElMessage.info("导出功能开发中...");
};

// 工具方法
const getActionText = (actionType: string): string => {
  const textMap: Record<string, string> = {
    SUBMIT: "提交审核",
    APPROVE: "批准上架",
    REJECT: "拒绝申请",
    REVIEW: "开始审核",
    RESUBMIT: "重新提交",
  };
  return textMap[actionType] || actionType;
};

const getActionTagType = (
  actionType: string
): "primary" | "success" | "warning" | "danger" | "info" => {
  const typeMap: Record<string, "primary" | "success" | "warning" | "danger" | "info"> = {
    SUBMIT: "primary",
    APPROVE: "success",
    REJECT: "danger",
    REVIEW: "warning",
    RESUBMIT: "info",
  };
  return typeMap[actionType] || "info";
};

const formatStatus = (status: string): string => {
  const statusMap: Record<string, string> = {
    DRAFT: "草稿",
    PENDING: "待审核",
    ACTIVE: "已上架",
    INACTIVE: "已下架",
    REJECTED: "已拒绝",
    SUSPENDED: "已暂停",
  };
  return statusMap[status] || status;
};

const getStatusTagType = (status: string): "success" | "info" | "warning" | "danger" => {
  const typeMap: Record<string, "success" | "info" | "warning" | "danger"> = {
    PENDING: "warning",
    ACTIVE: "success",
    REJECTED: "danger",
    SUSPENDED: "info",
  };
  return typeMap[status] || "info";
};

const formatDateTime = (dateTime: string | null | undefined): string => {
  if (!dateTime) return "未知时间";
  try {
    return new Date(dateTime).toLocaleString("zh-CN");
  } catch (error) {
    return "时间格式错误";
  }
};

// 初始化
onMounted(() => {
  loadAuditHistory();
});
</script>

<style scoped lang="scss">
.audit-history {
  padding: 20px;
  background-color: #f5f7fa;
  min-height: 100vh;

  .page-header {
    margin-bottom: 20px;

    .header-card {
      border: none;
      box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);

      .header-content {
        display: flex;
        justify-content: space-between;
        align-items: center;

        .header-left {
          h1 {
            margin: 0 0 8px 0;
            font-size: 24px;
            color: #303133;
          }

          p {
            margin: 0;
            color: #909399;
            font-size: 14px;
          }
        }

        .header-actions {
          display: flex;
          gap: 12px;
        }
      }
    }
  }

  .filter-card,
  .table-card {
    margin-bottom: 20px;
    border: none;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  }

  .pagination-wrapper {
    margin-top: 20px;
    display: flex;
    justify-content: center;
  }

  .detail-content {
    padding: 20px 0;
  }
}
</style>
