<template>
  <div class="dispute-list">
    <div class="container">
      <div class="handle-box">
        <el-row :gutter="20">
          <el-col :span="24">
            <el-input
              v-model="query.orderNumber"
              placeholder="订单号"
              class="handle-input mr10"
              @keyup.enter="handleSearch"
            >
              <template #append>
                <el-button :icon="Search" @click="handleSearch"></el-button>
              </template>
            </el-input>
            <el-button :icon="Refresh" circle @click="clearSearch"></el-button>
          </el-col>
        </el-row>
      </div>

      <el-card shadow="hover">
        <template #header>
          <div class="card-header">
            <span>争议管理</span>
            <el-tag type="info" v-if="total > 0">共 {{ total }} 条记录</el-tag>
          </div>
        </template>

        <el-table
          v-loading="loading"
          :data="tableData"
          border
          stripe
          highlight-current-row
          class="table"
        >
          <el-table-column prop="orderId" label="订单ID" width="100"></el-table-column>
          <el-table-column prop="orderNumber" label="订单号" width="150"></el-table-column>
          <el-table-column
            prop="homestayTitle"
            label="房源"
            min-width="150"
            show-overflow-tooltip
          ></el-table-column>
          <el-table-column
            prop="disputeReason"
            label="争议原因"
            min-width="150"
            show-overflow-tooltip
          ></el-table-column>
          <el-table-column prop="raisedByUsername" label="发起人" width="100"></el-table-column>
          <el-table-column prop="raisedAt" label="发起时间" width="160">
            <template #default="scope">
              {{ formatDate(scope.row.raisedAt) }}
            </template>
          </el-table-column>
          <el-table-column prop="resolution" label="仲裁结果" width="120" align="center">
            <template #default="scope">
              <el-tag v-if="scope.row.resolution === 'APPROVED'" type="success" size="small"
                >批准退款</el-tag
              >
              <el-tag v-else-if="scope.row.resolution === 'REJECTED'" type="danger" size="small"
                >拒绝退款</el-tag
              >
              <el-tag v-else type="warning" size="small">待处理</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="resolvedByUsername" label="仲裁人" width="100"></el-table-column>
          <el-table-column prop="resolvedAt" label="仲裁时间" width="160">
            <template #default="scope">
              {{ scope.row.resolvedAt ? formatDate(scope.row.resolvedAt) : "-" }}
            </template>
          </el-table-column>
          <el-table-column
            prop="resolutionNote"
            label="仲裁备注"
            min-width="150"
            show-overflow-tooltip
          ></el-table-column>

          <template #empty>
            <el-empty description="暂无争议记录" />
          </template>
        </el-table>

        <div class="pagination">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next, jumper"
            :current-page="query.pageIndex"
            :page-size="query.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="total"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage } from "element-plus";
import { Search, Refresh } from "@element-plus/icons-vue";
import { getDisputeRecords } from "@/api/dispute";

interface DisputeRecord {
  id: number;
  orderId: number;
  orderNumber: string;
  homestayTitle: string;
  disputeReason: string;
  raisedBy: number;
  raisedByUsername: string;
  raisedAt: string;
  resolution: string;
  resolvedBy: number;
  resolvedByUsername: string;
  resolvedAt: string;
  resolutionNote: string;
}

const tableData = ref<DisputeRecord[]>([]);
const loading = ref(false);
const total = ref(0);

const query = reactive({
  pageIndex: 1,
  pageSize: 10,
  orderNumber: "",
});

onMounted(() => {
  fetchData();
});

const fetchData = () => {
  loading.value = true;

  getDisputeRecords({
    page: query.pageIndex - 1,
    size: query.pageSize,
    orderId: query.orderNumber || undefined,
  })
    .then((res) => {
      tableData.value = res.list || [];
      total.value = res.total || 0;
    })
    .catch((error: any) => {
      console.error("获取争议记录出错:", error);
      ElMessage.error("获取争议记录出错");
      tableData.value = [];
      total.value = 0;
    })
    .finally(() => {
      loading.value = false;
    });
};

const handleSearch = () => {
  query.pageIndex = 1;
  fetchData();
};

const clearSearch = () => {
  query.orderNumber = "";
  query.pageIndex = 1;
  fetchData();
};

const handlePageChange = (val: number) => {
  query.pageIndex = val;
  fetchData();
};

const handleSizeChange = (val: number) => {
  query.pageSize = val;
  query.pageIndex = 1;
  fetchData();
};

const formatDate = (dateStr?: string) => {
  if (!dateStr) return "";
  const date = new Date(dateStr);
  return date.toLocaleString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
};
</script>

<style scoped lang="scss">
.dispute-list {
  padding: 20px;
}

.handle-box {
  margin-bottom: 20px;
}

.handle-input {
  width: 250px;
  display: inline-block;
}

.mr10 {
  margin-right: 10px;
}

.table {
  width: 100%;
  font-size: 14px;
}

.pagination {
  margin: 20px 0;
  text-align: right;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
