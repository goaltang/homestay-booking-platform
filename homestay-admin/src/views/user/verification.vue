<template>
  <div class="verification-list">
    <div class="search-box">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="用户名">
          <el-input v-model="searchForm.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
            <el-option label="待审核" value="PENDING" />
            <el-option label="已通过" value="VERIFIED" />
            <el-option label="已拒绝" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table :data="tableData" border style="width: 100%" v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="userId" label="用户ID" width="80" />
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="realName" label="真实姓名" />
      <el-table-column prop="idCard" label="身份证号码" width="180">
        <template #default="scope">
          <!-- 身份证号码脱敏显示 -->
          {{ maskIdCard(scope.row.idCard) }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="scope">
          <el-tag :type="getStatusType(scope.row.status)">
            {{ getStatusText(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="submitTime" label="提交时间" width="180" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="scope">
          <el-button type="primary" link @click="handleViewDetail(scope.row)">查看</el-button>
          <el-button
            type="success"
            link
            @click="handleApprove(scope.row)"
            v-if="scope.row.status === 'PENDING'"
            >通过</el-button
          >
          <el-button
            type="danger"
            link
            @click="handleReject(scope.row)"
            v-if="scope.row.status === 'PENDING'"
            >拒绝</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="身份验证详情" width="800px">
      <div class="verification-detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="用户ID">{{ currentDetail.userId }}</el-descriptions-item>
          <el-descriptions-item label="用户名">{{ currentDetail.username }}</el-descriptions-item>
          <el-descriptions-item label="真实姓名">{{ currentDetail.realName }}</el-descriptions-item>
          <el-descriptions-item label="身份证号码">{{ currentDetail.idCard }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusType(currentDetail.status)">
              {{ getStatusText(currentDetail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">{{
            currentDetail.submitTime
          }}</el-descriptions-item>
        </el-descriptions>

        <div class="id-card-images">
          <div class="image-container">
            <h3>身份证正面</h3>
            <el-image
              v-if="currentDetail.idCardFront"
              :src="currentDetail.idCardFront"
              :preview-src-list="[currentDetail.idCardFront]"
              fit="cover"
            />
            <div v-else class="no-image">暂无图片</div>
          </div>
          <div class="image-container">
            <h3>身份证反面</h3>
            <el-image
              v-if="currentDetail.idCardBack"
              :src="currentDetail.idCardBack"
              :preview-src-list="[currentDetail.idCardBack]"
              fit="cover"
            />
            <div v-else class="no-image">暂无图片</div>
          </div>
        </div>

        <div class="detail-actions" v-if="currentDetail.status === 'PENDING'">
          <el-button type="success" @click="handleDetailApprove">通过</el-button>
          <el-button type="danger" @click="handleDetailReject">拒绝</el-button>
        </div>
      </div>
    </el-dialog>

    <!-- 拒绝理由弹窗 -->
    <el-dialog v-model="rejectDialogVisible" title="拒绝理由" width="500px">
      <el-form>
        <el-form-item label="拒绝理由" required>
          <el-input
            v-model="rejectForm.note"
            type="textarea"
            :rows="4"
            placeholder="请输入拒绝理由（必填）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmReject">确认拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  getVerificationList,
  getVerificationDetail,
  approveVerification,
  rejectVerification,
} from "@/api/user";

// 定义类型
interface IdentityVerification {
  id: number;
  userId: number;
  username: string;
  realName: string;
  idCard: string;
  idCardFront: string;
  idCardBack: string;
  status: string;
  submitTime: string;
  reviewTime: string;
  reviewNote: string;
}

// 搜索表单
const searchForm = reactive({
  username: "",
  status: "",
});

// 表格数据
const tableData = ref<IdentityVerification[]>([]);
const loading = ref(false);
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);

// 详情弹窗
const detailDialogVisible = ref(false);
const currentDetail = reactive<IdentityVerification>({
  id: 0,
  userId: 0,
  username: "",
  realName: "",
  idCard: "",
  idCardFront: "",
  idCardBack: "",
  status: "",
  submitTime: "",
  reviewTime: "",
  reviewNote: "",
});

// 拒绝理由弹窗
const rejectDialogVisible = ref(false);
const rejectForm = reactive({
  id: 0,
  note: "",
});

// 状态文本
const getStatusText = (status: string) => {
  switch (status) {
    case "PENDING":
      return "待审核";
    case "VERIFIED":
      return "已通过";
    case "REJECTED":
      return "已拒绝";
    default:
      return "未知状态";
  }
};

// 状态标签类型
const getStatusType = (status: string) => {
  switch (status) {
    case "PENDING":
      return "warning";
    case "VERIFIED":
      return "success";
    case "REJECTED":
      return "danger";
    default:
      return "info";
  }
};

// 身份证号码脱敏显示
const maskIdCard = (idCard: string) => {
  if (!idCard) return "";
  if (idCard.length === 18) {
    return idCard.substring(0, 6) + "********" + idCard.substring(14);
  }
  return idCard;
};

// 搜索方法
const handleSearch = () => {
  currentPage.value = 1;
  fetchData();
};

// 重置搜索
const resetSearch = () => {
  searchForm.username = "";
  searchForm.status = "";
  currentPage.value = 1;
  fetchData();
};

// 分页方法
const handleSizeChange = (size: number) => {
  pageSize.value = size;
  fetchData();
};

const handleCurrentChange = (page: number) => {
  currentPage.value = page;
  fetchData();
};

// 获取列表数据
const fetchData = async () => {
  loading.value = true;
  try {
    const params = {
      ...searchForm,
      page: currentPage.value,
      pageSize: pageSize.value,
    };
    const res = await getVerificationList(params);

    // 检查返回数据
    console.log("验证列表数据:", res);

    if (res && Array.isArray(res.list)) {
      tableData.value = res.list;
      total.value = res.total;
    } else {
      console.error("数据格式不正确:", res);
      tableData.value = [];
      total.value = 0;
      ElMessage.warning("获取数据格式不正确");
    }
  } catch (error) {
    console.error("获取身份验证列表失败:", error);
    ElMessage.error("获取身份验证列表失败");
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
};

// 查看详情
const handleViewDetail = async (row: IdentityVerification) => {
  try {
    loading.value = true;
    const res = await getVerificationDetail(row.id);
    // 复制详情到响应式对象
    Object.assign(currentDetail, res);
    detailDialogVisible.value = true;
  } catch (error) {
    console.error("获取详情失败:", error);
    ElMessage.error("获取详情失败");
  } finally {
    loading.value = false;
  }
};

// 审核通过
const handleApprove = (row: IdentityVerification) => {
  ElMessageBox.confirm("确定通过此用户的身份认证吗？", "审核确认", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "success",
  })
    .then(async () => {
      try {
        loading.value = true;
        await approveVerification(row.id);
        ElMessage.success("已通过此用户的身份认证");
        fetchData(); // 刷新数据
      } catch (error) {
        console.error("审核操作失败:", error);
        ElMessage.error("审核操作失败");
      } finally {
        loading.value = false;
      }
    })
    .catch(() => {
      // 用户取消操作
    });
};

// 审核拒绝
const handleReject = (row: IdentityVerification) => {
  rejectForm.id = row.id;
  rejectForm.note = "";
  rejectDialogVisible.value = true;
};

// 确认拒绝
const confirmReject = async () => {
  if (!rejectForm.note) {
    ElMessage.warning("请输入拒绝理由");
    return;
  }

  try {
    loading.value = true;
    await rejectVerification(rejectForm.id, rejectForm.note);
    ElMessage.success("已拒绝此用户的身份认证");
    rejectDialogVisible.value = false;
    fetchData(); // 刷新数据
  } catch (error) {
    console.error("审核操作失败:", error);
    ElMessage.error("审核操作失败");
  } finally {
    loading.value = false;
  }
};

// 详情页审核通过
const handleDetailApprove = () => {
  ElMessageBox.confirm("确定通过此用户的身份认证吗？", "审核确认", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "success",
  })
    .then(async () => {
      try {
        loading.value = true;
        await approveVerification(currentDetail.id);
        ElMessage.success("已通过此用户的身份认证");
        detailDialogVisible.value = false;
        fetchData(); // 刷新数据
      } catch (error) {
        console.error("审核操作失败:", error);
        ElMessage.error("审核操作失败");
      } finally {
        loading.value = false;
      }
    })
    .catch(() => {
      // 用户取消操作
    });
};

// 详情页审核拒绝
const handleDetailReject = () => {
  rejectForm.id = currentDetail.id;
  rejectForm.note = "";
  rejectDialogVisible.value = true;
  detailDialogVisible.value = false;
};

// 初始化
onMounted(() => {
  fetchData();
});
</script>

<style scoped>
.verification-list {
  padding: 20px;
}

.search-box {
  margin-bottom: 20px;
  background-color: #fff;
  padding: 20px;
  border-radius: 4px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.verification-detail {
  padding: 20px;
}

.id-card-images {
  display: flex;
  margin-top: 20px;
  gap: 20px;
}

.image-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.image-container h3 {
  margin-bottom: 10px;
}

.el-image {
  width: 100%;
  max-width: 350px;
  height: 220px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}

.no-image {
  width: 100%;
  max-width: 350px;
  height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f5f7fa;
  color: #909399;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}

.detail-actions {
  margin-top: 20px;
  display: flex;
  justify-content: center;
  gap: 20px;
}
</style>
