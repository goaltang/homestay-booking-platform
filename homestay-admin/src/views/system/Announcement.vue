<template>
  <div class="container">
    <div class="handle-box">
      <el-row :gutter="20">
        <el-col :span="24">
          <el-select
            v-model="query.status"
            placeholder="公告状态"
            clearable
            class="handle-select mr10"
            @change="handleSearch"
          >
            <el-option label="全部状态" value=""></el-option>
            <el-option label="草稿" value="DRAFT"></el-option>
            <el-option label="已发布" value="PUBLISHED"></el-option>
            <el-option label="已下线" value="OFFLINE"></el-option>
          </el-select>
          <el-button :icon="Plus" type="primary" @click="handleAddClean">新增公告</el-button>
          <el-button :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" circle @click="clearSearch"></el-button>
        </el-col>
      </el-row>
    </div>

    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>公告管理</span>
          <span class="log-count" v-if="pagination.total > 0"
            >共 {{ pagination.total }} 条记录</span
          >
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
        <el-table-column
          prop="title"
          label="标题"
          min-width="200"
          show-overflow-tooltip
        ></el-table-column>
        <el-table-column prop="category" label="分类" width="150">
          <template #default="scope">
            <el-tag
              size="small"
              :type="scope.row.category === 'SYSTEM_NOTIFICATION' ? 'primary' : 'success'"
            >
              {{ scope.row.category === "SYSTEM_NOTIFICATION" ? "系统通知" : "活动公告" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag size="small" :type="getStatusTag(scope.row.status)">
              {{ getStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80" align="center">
          <template #default="scope">
            {{ scope.row.priority || 0 }}
          </template>
        </el-table-column>
        <el-table-column prop="publisherName" label="发布人" width="120"></el-table-column>
        <el-table-column prop="publishedAt" label="发布时间" width="180">
          <template #default="scope">
            {{ scope.row.publishedAt ? formatDate(scope.row.publishedAt) : "-" }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="scope">
            {{ formatDate(scope.row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="scope">
            <el-button type="primary" link size="small" @click="handleEditWithDetail(scope.row)"
              >编辑</el-button
            >
            <el-button
              v-if="scope.row.status === 'DRAFT'"
              type="success"
              link
              size="small"
              @click="handlePublish(scope.row)"
              >发布</el-button
            >
            <el-button
              v-if="scope.row.status === 'PUBLISHED'"
              type="warning"
              link
              size="small"
              @click="handleOffline(scope.row)"
              >下线</el-button
            >
            <el-button type="danger" link size="small" @click="handleDelete(scope.row)"
              >删除</el-button
            >
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无公告" />
        </template>
      </el-table>

      <div class="pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :current-page="pagination.page + 1"
          :page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          @current-change="(p: number) => handlePageChange(p - 1)"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editMode ? '编辑公告' : '新增公告'"
      width="600px"
      @close="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="公告标题" prop="title">
          <el-input
            v-model="form.title"
            placeholder="请输入公告标题"
            maxlength="200"
            show-word-limit
          ></el-input>
        </el-form-item>
        <el-form-item label="公告分类" prop="category">
          <el-select v-model="form.category" placeholder="请选择分类" style="width: 100%">
            <el-option label="系统通知" value="SYSTEM_NOTIFICATION"></el-option>
            <el-option label="活动公告" value="ACTIVITY_ANNOUNCEMENT"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-input-number
            v-model="form.priority"
            :min="0"
            :max="999"
            placeholder="数值越大越靠前"
          ></el-input-number>
        </el-form-item>
        <el-form-item label="展示时间">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          ></el-date-picker>
        </el-form-item>
        <el-form-item label="公告内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="6"
            placeholder="请输入公告内容"
            maxlength="5000"
            show-word-limit
          ></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitLoading">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Search, Refresh, Plus } from "@element-plus/icons-vue";
import { useCrud } from "@/composables/useCrud";
import {
  getAdminAnnouncementsApi,
  getAdminAnnouncementByIdApi,
  createAnnouncementApi,
  updateAnnouncementApi,
  deleteAnnouncementApi,
  publishAnnouncementApi,
  offlineAnnouncementApi,
  type Announcement,
} from "@/api/announcement";

const currentUsername = ref(
  localStorage.getItem("homestay_admin_username") || localStorage.getItem("username") || "admin"
);
const currentUserId = ref(
  Number(localStorage.getItem("homestay_admin_userId") || localStorage.getItem("userId")) || 1
);

// 日期范围辅助字段
const dateRange = ref<string[]>([]);
const submitLoading = ref(false);

// 包装 API 适配 useCrud（后端 page 从 0 开始，前端从 1 开始）
const wrappedListApi = async (params: any) => {
  const res = await getAdminAnnouncementsApi({
    page: params.page,
    size: params.size,
    status: params.status || undefined,
  });
  if (!res.success) throw new Error(res.message || "获取公告失败");
  // 返回标准分页格式，让 useCrud 统一处理
  return {
    content: res.data || [],
    totalElements: res.total || 0,
  };
};

const wrappedCreateApi = async (data: any) => {
  const payload: Announcement = {
    title: data.title,
    content: data.content,
    category: data.category,
    status: "DRAFT",
    priority: data.priority,
    startTime: dateRange.value?.[0],
    endTime: dateRange.value?.[1],
  };
  const res = await createAnnouncementApi(payload, currentUserId.value, currentUsername.value);
  if (!res.success) throw new Error(res.message || "创建失败");
  return res;
};

const wrappedUpdateApi = async (id: number | string, data: any) => {
  const payload: Announcement = {
    title: data.title,
    content: data.content,
    category: data.category,
    status: data.status,
    priority: data.priority,
    startTime: dateRange.value?.[0],
    endTime: dateRange.value?.[1],
  };
  const res = await updateAnnouncementApi(Number(id), payload, currentUsername.value);
  if (!res.success) throw new Error(res.message || "更新失败");
  return res;
};

const wrappedDeleteApi = async (id: number | string) => {
  const res = await deleteAnnouncementApi(Number(id), currentUsername.value);
  if (!res.success) throw new Error(res.message || "删除失败");
  return res;
};

// 使用 useCrud
const {
  loading,
  tableData,
  query,
  pagination,
  dialogVisible,
  editMode,
  formRef,
  form,
  rules,
  getList,
  handleAdd,
  handleDelete,
  handlePageChange,
  handleSizeChange,
  resetForm,
} = useCrud<Announcement>({
  listApi: wrappedListApi,
  createApi: wrappedCreateApi,
  updateApi: wrappedUpdateApi,
  deleteApi: wrappedDeleteApi,
  defaultQuery: { status: "", page: 0, size: 20 } as any,
  defaultForm: {
    title: "",
    content: "",
    category: "SYSTEM_NOTIFICATION",
    status: "DRAFT",
    priority: 0,
  } as any,
  rules: {
    title: [{ required: true, message: "请输入公告标题", trigger: "blur" }],
    category: [{ required: true, message: "请选择分类", trigger: "change" }],
    content: [{ required: true, message: "请输入公告内容", trigger: "blur" }],
  },
});

// 覆盖 handleSubmit，增加 submitLoading 和日期范围处理
const handleSubmit = async () => {
  if (!formRef.value) return;
  await formRef.value.validate(async (valid: boolean) => {
    if (!valid) return;
    submitLoading.value = true;
    try {
      if (editMode.value && form.id) {
        await wrappedUpdateApi(form.id, form);
        ElMessage.success("更新成功");
      } else {
        await wrappedCreateApi(form);
        ElMessage.success("创建成功");
      }
      dialogVisible.value = false;
      dateRange.value = [];
      await getList();
    } catch (e: any) {
      ElMessage.error(e.message || "操作失败");
    } finally {
      submitLoading.value = false;
    }
  });
};

// 覆盖 handleEdit，先获取详情再打开对话框
const handleEditWithDetail = async (row: Announcement) => {
  const res = await getAdminAnnouncementByIdApi(row.id!);
  if (!res.success || !res.data) {
    ElMessage.error(res.message || "获取公告详情失败");
    return;
  }
  const data = res.data;
  editMode.value = true;
  resetForm();
  Object.assign(form, {
    id: data.id,
    title: data.title,
    content: data.content,
    category: data.category,
    status: data.status,
    priority: data.priority || 0,
  });
  dateRange.value = [];
  if (data.startTime) dateRange.value[0] = data.startTime;
  if (data.endTime) dateRange.value[1] = data.endTime;
  dialogVisible.value = true;
};

// 覆盖 handleAdd，清空日期范围
const _rawAdd = handleAdd;
const handleAddClean = () => {
  _rawAdd();
  dateRange.value = [];
};

// 搜索
const handleSearch = () => {
  pagination.page = 0;
  getList();
};

const clearSearch = () => {
  query.status = "";
  pagination.page = 0;
  getList();
};

// 特殊操作：发布/下线
const handlePublish = (row: Announcement) => {
  ElMessageBox.confirm("确定要发布此公告吗？", "发布确认", { type: "warning" })
    .then(async () => {
      const res = await publishAnnouncementApi(row.id!, currentUserId.value, currentUsername.value);
      if (res.success) {
        ElMessage.success("发布成功");
        getList();
      } else {
        ElMessage.error(res.message || "发布失败");
      }
    })
    .catch(() => {});
};

const handleOffline = (row: Announcement) => {
  ElMessageBox.confirm("确定要下线此公告吗？", "下线确认", { type: "warning" })
    .then(async () => {
      const res = await offlineAnnouncementApi(row.id!, currentUsername.value);
      if (res.success) {
        ElMessage.success("下线成功");
        getList();
      } else {
        ElMessage.error(res.message || "下线失败");
      }
    })
    .catch(() => {});
};

// 状态映射
const getStatusTag = (status?: string) => {
  const map: Record<string, string> = { DRAFT: "info", PUBLISHED: "success", OFFLINE: "warning" };
  return map[status || ""] || "info";
};

const getStatusText = (status?: string) => {
  const map: Record<string, string> = { DRAFT: "草稿", PUBLISHED: "已发布", OFFLINE: "已下线" };
  return map[status || ""] || status;
};

const formatDate = (dateStr?: string) => {
  if (!dateStr) return "-";
  const date = new Date(dateStr);
  return date.toLocaleString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
};
</script>

<style scoped>
.container {
  padding: 20px;
}
.handle-box {
  margin-bottom: 20px;
}
.handle-input {
  width: 150px;
  display: inline-block;
}
.handle-select {
  width: 130px;
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
.log-count {
  font-size: 12px;
  color: #909399;
}
</style>
