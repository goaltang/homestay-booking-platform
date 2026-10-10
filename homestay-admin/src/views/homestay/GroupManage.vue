<template>
  <div>
    <div class="container">
      <div class="handle-box">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-input
              v-model="query.keyword"
              placeholder="搜索分组名或房东"
              clearable
              class="handle-input mr10"
              @keyup.enter="handleSearch"
            />
          </el-col>
          <el-col :span="8">
            <el-input
              v-model.number="query.ownerId"
              placeholder="按房东ID筛选"
              clearable
              class="handle-input mr10"
              @keyup.enter="handleSearch"
            />
          </el-col>
          <el-col :span="8">
            <el-button :icon="Search" type="primary" @click="handleSearch">搜索</el-button>
            <el-button :icon="Refresh" circle @click="clearSearch"></el-button>
          </el-col>
        </el-row>
      </div>

      <el-card shadow="hover">
        <template #header>
          <div class="card-header">
            <span>房源分组管理</span>
            <span class="log-count" v-if="pageTotal > 0">共 {{ pageTotal }} 条记录</span>
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
          <el-table-column prop="id" label="ID" width="80"></el-table-column>
          <el-table-column prop="name" label="分组名称" min-width="120"></el-table-column>
          <el-table-column prop="code" label="编码" width="100"></el-table-column>
          <el-table-column prop="ownerUsername" label="房东" width="120"></el-table-column>
          <el-table-column
            prop="homestayCount"
            label="房源数"
            width="80"
            align="center"
          ></el-table-column>
          <el-table-column
            prop="sortOrder"
            label="排序"
            width="70"
            align="center"
          ></el-table-column>
          <el-table-column prop="enabled" label="状态" width="80" align="center">
            <template #default="scope">
              <el-tag size="small" :type="scope.row.enabled ? 'success' : 'info'">
                {{ scope.row.enabled ? "启用" : "禁用" }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="isDefault" label="默认" width="70" align="center">
            <template #default="scope">
              <el-tag size="small" :type="scope.row.isDefault ? 'warning' : ''">
                {{ scope.row.isDefault ? "是" : "否" }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="180">
            <template #default="scope">
              {{ scope.row.createdAt || "-" }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right" align="center">
            <template #default="scope">
              <el-button type="primary" link size="small" @click="handleToggle(scope.row)">
                {{ scope.row.enabled ? "禁用" : "启用" }}
              </el-button>
              <el-button
                type="danger"
                link
                size="small"
                @click="handleDelete(scope.row)"
                :disabled="scope.row.isDefault"
                >删除</el-button
              >
            </template>
          </el-table-column>

          <template #empty>
            <el-empty description="暂无分组数据" />
          </template>
        </el-table>

        <div class="pagination">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next, jumper"
            :current-page="query.pageIndex"
            :page-size="query.pageSize"
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

<script setup lang="ts" name="GroupManage">
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Search, Refresh } from "@element-plus/icons-vue";
import { getGroups, toggleGroupEnabled, deleteGroup } from "../../api/group";

interface GroupItem {
  id: number;
  name: string;
  code: string;
  description: string;
  icon: string;
  color: string;
  ownerId: number;
  ownerUsername: string;
  sortOrder: number;
  isDefault: boolean;
  enabled: boolean;
  homestayCount: number;
  createdAt: string;
  updatedAt: string;
}

const loading = ref(false);
const tableData = ref<GroupItem[]>([]);
const pageTotal = ref(0);

const query = reactive({
  pageIndex: 1,
  pageSize: 10,
  keyword: "",
  ownerId: undefined as number | undefined,
});

const getData = async () => {
  loading.value = true;
  try {
    const res = await getGroups({
      page: query.pageIndex,
      size: query.pageSize,
      keyword: query.keyword || undefined,
      ownerId: query.ownerId,
    });
    tableData.value = res.list || [];
    pageTotal.value = res.total || 0;
  } catch (error) {
    console.error("获取分组列表失败:", error);
    ElMessage.error("获取分组列表失败");
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  query.pageIndex = 1;
  getData();
};

const clearSearch = () => {
  query.keyword = "";
  query.ownerId = undefined;
  query.pageIndex = 1;
  getData();
};

const handlePageChange = (val: number) => {
  query.pageIndex = val;
  getData();
};

const handleSizeChange = (val: number) => {
  query.pageSize = val;
  query.pageIndex = 1;
  getData();
};

const handleToggle = async (row: GroupItem) => {
  const action = row.enabled ? "禁用" : "启用";
  try {
    await ElMessageBox.confirm(`确定要${action}分组「${row.name}」吗？`, "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });
    await toggleGroupEnabled(row.id, !row.enabled);
    ElMessage.success(`${action}成功`);
    getData();
  } catch (error: any) {
    if (error !== "cancel") {
      console.error(`${action}分组失败:`, error);
      ElMessage.error(`${action}失败`);
    }
  }
};

const handleDelete = async (row: GroupItem) => {
  if (row.isDefault) {
    ElMessage.warning("默认分组不能删除");
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确定要删除分组「${row.name}」吗？该分组下的房源将变为未分组状态。`,
      "删除确认",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      }
    );
    await deleteGroup(row.id);
    ElMessage.success("删除成功");
    getData();
  } catch (error: any) {
    if (error !== "cancel") {
      console.error("删除分组失败:", error);
      ElMessage.error("删除失败");
    }
  }
};

onMounted(() => {
  getData();
});
</script>

<style scoped>
.handle-box {
  margin-bottom: 20px;
}
.handle-input {
  width: 100%;
}
.mr10 {
  margin-right: 10px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.log-count {
  color: #909399;
  font-size: 14px;
}
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
