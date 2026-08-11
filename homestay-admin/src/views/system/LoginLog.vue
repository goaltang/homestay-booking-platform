<template>
  <div>
    <div class="container">
      <div class="handle-box">
        <el-row :gutter="20">
          <el-col :span="24">
            <el-select v-model="query.loginType" placeholder="登录类型" clearable class="handle-select mr10" @change="handleSearch">
              <el-option label="全部类型" value=""></el-option>
              <el-option label="管理员" value="ADMIN"></el-option>
              <el-option label="用户" value="USER"></el-option>
            </el-select>
            <el-select v-model="query.loginStatus" placeholder="登录状态" clearable class="handle-select mr10" @change="handleSearch">
              <el-option label="全部状态" value=""></el-option>
              <el-option label="成功" value="SUCCESS"></el-option>
              <el-option label="失败" value="FAIL"></el-option>
            </el-select>
            <el-input v-model="query.username" placeholder="用户名" class="handle-input mr10" @keyup.enter="handleSearch"></el-input>
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
            <span>登录日志</span>
            <span class="log-count" v-if="pageTotal > 0">共 {{ pageTotal }} 条记录</span>
          </div>
        </template>

        <el-table v-loading="loading" :data="tableData" border stripe highlight-current-row class="table">
          <el-table-column prop="loginTime" label="登录时间" width="180" sortable>
            <template #default="scope">
              {{ formatDate(scope.row.loginTime) }}
            </template>
          </el-table-column>
          <el-table-column prop="username" label="用户名" width="120"></el-table-column>
          <el-table-column prop="loginType" label="登录类型" width="100">
            <template #default="scope">
              <el-tag size="small" :type="scope.row.loginType === 'ADMIN' ? 'primary' : 'info'">
                {{ scope.row.loginType === 'ADMIN' ? '管理员' : '用户' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="loginStatus" label="登录状态" width="100" align="center">
            <template #default="scope">
              <el-tag size="small" :type="scope.row.loginStatus === 'SUCCESS' ? 'success' : 'danger'">
                {{ scope.row.loginStatus === 'SUCCESS' ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="ipAddress" label="IP地址" width="140"></el-table-column>
          <el-table-column prop="userAgent" label="登录设备" min-width="200" show-overflow-tooltip></el-table-column>
          <el-table-column prop="logoutTime" label="登出时间" width="180">
            <template #default="scope">
              {{ scope.row.logoutTime ? formatDate(scope.row.logoutTime) : '-' }}
            </template>
          </el-table-column>

          <template #empty>
            <el-empty description="暂无登录日志" />
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
import { getLoginLogsApi, LoginLog, LoginLogQueryParams } from '@/api/loginLog';
import { usePagination } from '@/composables/usePagination';

const tableData = ref<LoginLog[]>([]);
const loading = ref(false);
const pageTotal = ref(0);

const { pageIndex, pageSize, currentChange, sizeChange, reset, buildParams } = usePagination(20);

const query = reactive<LoginLogQueryParams & { dateRange?: string[] }>({
  username: '',
  loginType: '',
  startTime: '',
  endTime: '',
  dateRange: [],
  loginStatus: ''
});

onMounted(() => {
  getLogs();
});

const getLogs = () => {
  loading.value = true;
  const params: LoginLogQueryParams = {
    ...buildParams(),
    username: query.username || undefined,
    loginType: query.loginType || undefined,
    startTime: query.dateRange && query.dateRange.length === 2 ? query.dateRange[0] : undefined,
    endTime: query.dateRange && query.dateRange.length === 2 ? query.dateRange[1] : undefined,
    loginStatus: query.loginStatus || undefined
  };

  getLoginLogsApi(params)
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
  query.username = '';
  query.loginType = '';
  query.startTime = '';
  query.endTime = '';
  query.dateRange = [];
  query.loginStatus = '';
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
