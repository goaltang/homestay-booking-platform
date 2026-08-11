<template>
  <div>
    <div class="container">
      <div class="handle-box">
        <el-row :gutter="20">
          <el-col :span="24">
            <el-select v-model="query.operationType" placeholder="操作类型" clearable class="handle-select mr10" @change="handleSearch">
              <el-option label="全部操作" value=""></el-option>
              <el-option label="登录" value="LOGIN"></el-option>
              <el-option label="登出" value="LOGOUT"></el-option>
              <el-option label="创建" value="CREATE"></el-option>
              <el-option label="更新" value="UPDATE"></el-option>
              <el-option label="删除" value="DELETE"></el-option>
              <el-option label="查看" value="VIEW"></el-option>
            </el-select>
            <el-select v-model="query.resource" placeholder="资源类型" clearable class="handle-select mr10" @change="handleSearch">
              <el-option label="全部资源" value=""></el-option>
              <el-option label="系统配置" value="SYSTEM_CONFIG"></el-option>
              <el-option label="房源" value="HOMESTAY"></el-option>
              <el-option label="订单" value="ORDER"></el-option>
              <el-option label="用户" value="USER"></el-option>
            </el-select>
            <el-input v-model="query.operator" placeholder="操作人" class="handle-input mr10" @keyup.enter="handleSearch"></el-input>
            <el-date-picker
              v-model="query.dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              class="mr10"
              @change="handleSearch"
            />
            <el-button :icon="Search" @click="handleSearch">搜索</el-button>
            <el-button :icon="Refresh" circle @click="clearSearch"></el-button>
          </el-col>
        </el-row>
      </div>

      <el-card shadow="hover">
        <template #header>
          <div class="card-header">
            <span>操作日志</span>
            <span class="log-count" v-if="pageTotal > 0">共 {{ pageTotal }} 条记录</span>
          </div>
        </template>

        <el-table v-loading="loading" :data="tableData" border stripe highlight-current-row class="table">
          <el-table-column prop="operateTime" label="操作时间" width="180" sortable>
            <template #default="scope">
              {{ formatDate(scope.row.operateTime) }}
            </template>
          </el-table-column>
          <el-table-column prop="operator" label="操作人" width="120"></el-table-column>
          <el-table-column prop="operationType" label="操作类型" width="100">
            <template #default="scope">
              <el-tag size="small" :type="getOperationTypeTag(scope.row.operationType)">
                {{ scope.row.operationType }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="resource" label="资源类型" width="120"></el-table-column>
          <el-table-column prop="resourceId" label="资源ID" width="120">
            <template #default="scope">
              <span v-if="scope.row.resourceId">{{ scope.row.resourceId }}</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="ipAddress" label="IP地址" width="140"></el-table-column>
          <el-table-column prop="detail" label="操作详情" min-width="250" show-overflow-tooltip></el-table-column>
          <el-table-column prop="status" label="状态" width="80" align="center">
            <template #default="scope">
              <el-tag size="small" :type="scope.row.status === 'SUCCESS' ? 'success' : 'danger'">
                {{ scope.row.status === 'SUCCESS' ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>

          <template #empty>
            <el-empty description="暂无操作日志" />
          </template>
        </el-table>

        <div class="pagination">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next, jumper"
            :current-page="pageIndex"
            :page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="pageTotal"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Search, Refresh } from '@element-plus/icons-vue';
import { getOperationLogsApi, OperationLog, LogQueryParams } from '@/api/systemConfig';
import { usePagination } from '@/composables/usePagination';

const tableData = ref<OperationLog[]>([]);
const loading = ref(false);
const pageTotal = ref(0);

const { pageIndex, pageSize, currentChange, sizeChange, reset, buildParams } = usePagination(20);

const query = reactive<LogQueryParams & { dateRange?: string[] }>({
  operator: '',
  operationType: '',
  resource: '',
  startTime: '',
  endTime: '',
  dateRange: []
});

onMounted(() => {
  getLogs();
});

const getLogs = () => {
  loading.value = true;
  const params: LogQueryParams = {
    ...buildParams(),
    operator: query.operator || undefined,
    operationType: query.operationType || undefined,
    resource: query.resource || undefined,
    startTime: query.dateRange && query.dateRange.length === 2 ? query.dateRange[0] : undefined,
    endTime: query.dateRange && query.dateRange.length === 2 ? query.dateRange[1] : undefined
  };

  getOperationLogsApi(params)
    .then(response => {
      if (response.success) {
        tableData.value = response.data || [];
        pageTotal.value = response.total || 0;
      } else {
        ElMessage.error(response.message || '获取日志失败');
        tableData.value = [];
        pageTotal.value = 0;
      }
    })
    .catch(error => {
      console.error('获取日志出错:', error);
      ElMessage.error('获取日志出错');
      tableData.value = [];
      pageTotal.value = 0;
    })
    .finally(() => {
      loading.value = false;
    });
};

const handleSearch = () => {
  reset();
  getLogs();
};

const clearSearch = () => {
  query.operator = '';
  query.operationType = '';
  query.resource = '';
  query.startTime = '';
  query.endTime = '';
  query.dateRange = [];
  reset();
  getLogs();
};

const handlePageChange = (val: number) => {
  currentChange(val);
  getLogs();
};

const handleSizeChange = (val: number) => {
  sizeChange(val);
  getLogs();
};

const getOperationTypeTag = (type?: string) => {
  const typeMap: Record<string, string> = {
    LOGIN: 'primary',
    LOGOUT: 'info',
    CREATE: 'success',
    UPDATE: 'warning',
    DELETE: 'danger',
    VIEW: 'info'
  };
  return typeMap[type || 'OTHER'] || 'info';
};

const formatDate = (dateStr?: string) => {
  if (!dateStr) return '-';
  const date = new Date(dateStr);
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  });
};
</script>

<style scoped>
.handle-box { margin-bottom: 20px; }
.handle-input { width: 150px; display: inline-block; }
.handle-select { width: 130px; display: inline-block; }
.mr10 { margin-right: 10px; }
.table { width: 100%; font-size: 14px; }
.pagination { margin: 20px 0; text-align: right; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.log-count { font-size: 12px; color: #909399; }
</style>
