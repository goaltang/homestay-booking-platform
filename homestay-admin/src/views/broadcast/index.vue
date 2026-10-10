<template>
  <div class="broadcast-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <el-card shadow="never">
        <div class="header-content">
          <div class="header-left">
            <h2>广播管理</h2>
            <p>向全体用户发送系统通知并管理广播任务</p>
          </div>
          <div class="header-right">
            <el-button type="primary" @click="showSendDialog = true">
              <el-icon>
                <Bell />
              </el-icon>
              发送系统通知
            </el-button>
            <el-button @click="refreshBroadcastJobs">
              <el-icon>
                <Refresh />
              </el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-section">
      <el-row :gutter="20">
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-content">
              <div class="stat-title">总任务数</div>
              <div class="stat-value">{{ broadcastJobTotal }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-content">
              <div class="stat-title">当前页待执行/执行中</div>
              <div class="stat-value pending">{{ pendingRunningCount }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-content">
              <div class="stat-title">当前页已成功</div>
              <div class="stat-value success">{{ succeededCount }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-content">
              <div class="stat-title">当前页已失败/限流</div>
              <div class="stat-value failed">{{ failedCount }}</div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 广播任务历史 -->
    <div class="broadcast-history">
      <el-card shadow="never">
        <template #header>
          <div class="section-header">
            <h3>广播任务历史</h3>
            <div class="section-actions">
              <el-select
                v-model="broadcastJobFilter.status"
                placeholder="全部状态"
                clearable
                style="width: 140px"
                @change="handleBroadcastJobStatusChange"
              >
                <el-option
                  v-for="status in broadcastJobStatusOptions"
                  :key="status.value"
                  :label="status.label"
                  :value="status.value"
                />
              </el-select>
              <el-button @click="refreshBroadcastJobs" :loading="broadcastJobsLoading">
                <el-icon>
                  <Refresh />
                </el-icon>
                刷新
              </el-button>
            </div>
          </div>
        </template>

        <el-table
          v-loading="broadcastJobsLoading"
          :data="broadcastJobs"
          border
          stripe
          style="width: 100%"
          empty-text="暂无广播任务"
        >
          <el-table-column prop="jobId" label="任务ID" width="100" />
          <el-table-column
            prop="contentSummary"
            label="内容摘要"
            min-width="240"
            show-overflow-tooltip
          />
          <el-table-column prop="status" label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="getBroadcastJobStatusTagType(row.status)" size="small">
                {{ formatBroadcastJobStatus(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="contentLength" label="内容长度" width="100" align="center" />
          <el-table-column prop="targetCount" label="目标数" width="100" align="center" />
          <el-table-column prop="successCount" label="成功数" width="100" align="center" />
          <el-table-column prop="failureCount" label="失败数" width="100" align="center" />
          <el-table-column prop="initiatedByUsername" label="提交人" width="120">
            <template #default="{ row }">
              {{ row.initiatedByUsername || row.initiatedBy || "-" }}
            </template>
          </el-table-column>
          <el-table-column prop="submittedAt" label="提交时间" width="180">
            <template #default="{ row }">
              {{ formatDateTime(row.submittedAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right" align="center">
            <template #default="{ row }">
              <el-button type="primary" size="small" text @click="handleViewBroadcastJob(row)">
                <el-icon>
                  <View />
                </el-icon>
                详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination" v-if="broadcastJobTotal > 0">
          <el-pagination
            v-model:current-page="broadcastJobCurrentPage"
            v-model:page-size="broadcastJobPageSize"
            :page-sizes="[10, 20, 50]"
            :total="broadcastJobTotal"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleBroadcastJobSizeChange"
            @current-change="handleBroadcastJobCurrentChange"
          />
        </div>
      </el-card>
    </div>

    <!-- 发送系统通知弹窗 -->
    <el-dialog v-model="showSendDialog" title="发送系统通知" width="500px" align-center>
      <el-form :model="sendForm" label-width="100px">
        <el-form-item label="发送对象">
          <el-tag type="info">全体用户</el-tag>
        </el-form-item>
        <el-form-item label="通知内容" prop="content">
          <el-input
            v-model="sendForm.content"
            type="textarea"
            :rows="4"
            placeholder="请输入通知内容，最长500字"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSendDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSendNotification" :loading="sendLoading">
          发送
        </el-button>
      </template>
    </el-dialog>

    <!-- 广播任务详情弹窗 -->
    <el-dialog v-model="broadcastJobDetailVisible" title="广播任务详情" width="680px" align-center>
      <div v-loading="broadcastJobDetailLoading">
        <el-descriptions v-if="currentBroadcastJob" :column="2" border>
          <el-descriptions-item label="任务ID">{{
            currentBroadcastJob.jobId
          }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getBroadcastJobStatusTagType(currentBroadcastJob.status)" size="small">
              {{ formatBroadcastJobStatus(currentBroadcastJob.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="提交人">
            {{ currentBroadcastJob.initiatedByUsername || currentBroadcastJob.initiatedBy || "-" }}
          </el-descriptions-item>
          <el-descriptions-item label="内容长度">
            {{ currentBroadcastJob.contentLength }}
          </el-descriptions-item>
          <el-descriptions-item label="目标数">{{
            currentBroadcastJob.targetCount
          }}</el-descriptions-item>
          <el-descriptions-item label="成功数">{{
            currentBroadcastJob.successCount
          }}</el-descriptions-item>
          <el-descriptions-item label="失败数">{{
            currentBroadcastJob.failureCount
          }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">
            {{ formatDateTime(currentBroadcastJob.submittedAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="开始时间">
            {{ formatDateTime(currentBroadcastJob.startedAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="完成时间">
            {{ formatDateTime(currentBroadcastJob.completedAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="内容摘要" :span="2">
            <span class="description-text">{{ currentBroadcastJob.contentSummary || "-" }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="失败原因" :span="2">
            <span class="description-text">{{ currentBroadcastJob.failureReason || "-" }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from "vue";
import { ElMessage } from "element-plus";
import { Bell, Refresh, View } from "@element-plus/icons-vue";
import {
  broadcastSystemNotification,
  getNotificationBroadcastJobs,
  getNotificationBroadcastJob,
  type NotificationBroadcastJob,
} from "@/api/notification";

// 响应式数据
const broadcastJobsLoading = ref(false);
const broadcastJobs = ref<NotificationBroadcastJob[]>([]);
const broadcastJobCurrentPage = ref(1);
const broadcastJobPageSize = ref(10);
const broadcastJobTotal = ref(0);
const broadcastJobDetailVisible = ref(false);
const broadcastJobDetailLoading = ref(false);
const currentBroadcastJob = ref<NotificationBroadcastJob | null>(null);
const detailRequestId = ref(0);
const fetchId = ref(0);

const broadcastJobStatusOptions = [
  { label: "待执行", value: "PENDING" },
  { label: "执行中", value: "RUNNING" },
  { label: "已成功", value: "SUCCEEDED" },
  { label: "已失败", value: "FAILED" },
  { label: "已限流", value: "RATE_LIMITED" },
];

const pendingRunningCount = computed(
  () => broadcastJobs.value.filter((j) => j.status === "PENDING" || j.status === "RUNNING").length
);
const succeededCount = computed(
  () => broadcastJobs.value.filter((j) => j.status === "SUCCEEDED").length
);
const failedCount = computed(
  () =>
    broadcastJobs.value.filter((j) => j.status === "FAILED" || j.status === "RATE_LIMITED").length
);

const broadcastJobFilter = reactive({
  status: "",
});

// 发送系统通知
const showSendDialog = ref(false);
const sendLoading = ref(false);
const sendForm = reactive({
  content: "",
});

const handleSendNotification = async () => {
  if (!sendForm.content.trim()) {
    ElMessage.warning("请输入通知内容");
    return;
  }

  sendLoading.value = true;
  try {
    const job = await broadcastSystemNotification(sendForm.content.trim());
    ElMessage.success(`系统通知广播任务已提交，任务ID：${job.jobId}`);
    showSendDialog.value = false;
    sendForm.content = "";
    broadcastJobCurrentPage.value = 1;
    await loadBroadcastJobs();
  } catch (error: any) {
    console.error("发送系统通知失败:", error);
    ElMessage.error(
      error?.response?.data?.failureReason || error?.response?.data?.error || "发送系统通知失败"
    );
  } finally {
    sendLoading.value = false;
  }
};

const loadBroadcastJobs = async () => {
  const currentFetchId = ++fetchId.value;
  try {
    broadcastJobsLoading.value = true;
    const response = await getNotificationBroadcastJobs({
      page: broadcastJobCurrentPage.value - 1,
      size: broadcastJobPageSize.value,
      status: broadcastJobFilter.status || undefined,
    });

    if (currentFetchId !== fetchId.value) return true;
    broadcastJobs.value = response.content;
    broadcastJobTotal.value = response.totalElements;
    return true;
  } catch (error) {
    if (currentFetchId !== fetchId.value) return false;
    console.error("获取广播任务历史失败:", error);
    ElMessage.error("获取广播任务历史失败");
    return false;
  } finally {
    if (currentFetchId === fetchId.value) {
      broadcastJobsLoading.value = false;
    }
  }
};

const refreshBroadcastJobs = async () => {
  const refreshed = await loadBroadcastJobs();
  if (refreshed) {
    ElMessage.success("广播任务历史已刷新");
  }
};

const handleBroadcastJobStatusChange = () => {
  broadcastJobCurrentPage.value = 1;
  loadBroadcastJobs();
};

const handleBroadcastJobSizeChange = (val: number) => {
  broadcastJobPageSize.value = val;
  broadcastJobCurrentPage.value = 1;
  loadBroadcastJobs();
};

const handleBroadcastJobCurrentChange = (val: number) => {
  broadcastJobCurrentPage.value = val;
  loadBroadcastJobs();
};

const handleViewBroadcastJob = async (job: NotificationBroadcastJob) => {
  const requestId = ++detailRequestId.value;
  currentBroadcastJob.value = job;
  broadcastJobDetailVisible.value = true;
  broadcastJobDetailLoading.value = true;

  try {
    const jobDetail = await getNotificationBroadcastJob(job.jobId);
    if (requestId !== detailRequestId.value) return;
    currentBroadcastJob.value = jobDetail;
  } catch (error) {
    if (requestId !== detailRequestId.value) return;
    console.error("获取广播任务详情失败:", error);
    ElMessage.error("获取广播任务详情失败");
  } finally {
    if (requestId === detailRequestId.value) {
      broadcastJobDetailLoading.value = false;
    }
  }
};

const formatBroadcastJobStatus = (status: string) => {
  const statusMap: Record<string, string> = {
    PENDING: "待执行",
    RUNNING: "执行中",
    SUCCEEDED: "已成功",
    FAILED: "已失败",
    RATE_LIMITED: "已限流",
  };
  return statusMap[status] || status;
};

const getBroadcastJobStatusTagType = (
  status: string
): "success" | "info" | "warning" | "danger" => {
  const typeMap: Record<string, "success" | "info" | "warning" | "danger"> = {
    PENDING: "info",
    RUNNING: "warning",
    SUCCEEDED: "success",
    FAILED: "danger",
    RATE_LIMITED: "warning",
  };
  return typeMap[status] || "info";
};

const formatDateTime = (dateTime?: string | null) => {
  if (!dateTime) return "-";

  const time = new Date(dateTime);
  if (Number.isNaN(time.getTime())) {
    return dateTime;
  }

  return time.toLocaleString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
};

// 初始化
onMounted(() => {
  loadBroadcastJobs();
});
</script>

<style scoped lang="scss">
.broadcast-page {
  padding: 20px;
  background-color: #f5f7fa;
  min-height: 100vh;

  .page-header {
    margin-bottom: 20px;

    .header-content {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .header-left {
        h2 {
          margin: 0 0 8px 0;
          color: #303133;
          font-size: 24px;
          font-weight: 600;
        }

        p {
          margin: 0;
          color: #909399;
          font-size: 14px;
        }
      }

      .header-right {
        display: flex;
        align-items: center;
        gap: 12px;
      }
    }
  }

  .stats-section {
    margin-bottom: 20px;

    .stat-card {
      .stat-content {
        text-align: center;
        padding: 8px 0;

        .stat-title {
          font-size: 14px;
          color: #606266;
          margin-bottom: 8px;
        }

        .stat-value {
          font-size: 28px;
          font-weight: 600;
          color: #303133;

          &.pending {
            color: #e6a23c;
          }

          &.success {
            color: #67c23a;
          }

          &.failed {
            color: #f56c6c;
          }
        }
      }
    }
  }

  .broadcast-history {
    .section-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;

      h3 {
        margin: 0;
        color: #303133;
        font-size: 18px;
        font-weight: 600;
      }

      .section-actions {
        display: flex;
        align-items: center;
        gap: 12px;
      }
    }

    .pagination {
      margin-top: 20px;
      display: flex;
      justify-content: center;
    }
  }

  .description-text {
    white-space: pre-wrap;
    word-break: break-word;
  }
}
</style>
