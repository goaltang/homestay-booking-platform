<template>
  <div>
    <div class="container">
      <div class="handle-box">
        <el-row :gutter="20">
          <el-col :span="24" class="mb10">
            <el-button type="primary" :icon="Plus" @click="handleAdd">新增设施</el-button>
            <el-button type="success" :icon="Lightning" @click="handleInitDefaults"
              >一键初始化设施</el-button
            >
            <el-button type="warning" :icon="Check" @click="handleActivateAll"
              >一键激活所有设施</el-button
            >
            <el-button
              type="danger"
              :icon="Delete"
              @click="handleBatchDelete"
              :disabled="multipleSelection.length === 0"
            >
              批量删除<span v-if="multipleSelection.length > 0"
                >({{ multipleSelection.length }})</span
              >
            </el-button>
            <el-tooltip content="刷新设施列表" placement="top">
              <el-button :icon="Refresh" circle @click="getAmenities"></el-button>
            </el-tooltip>
          </el-col>
          <el-col :span="24">
            <el-select
              v-model="query.categoryCode"
              placeholder="选择分类"
              clearable
              class="handle-select mr10"
            >
              <el-option label="全部分类" value=""></el-option>
              <el-option
                v-for="item in categories"
                :key="item.code"
                :label="item.name"
                :value="item.code"
              ></el-option>
            </el-select>
            <el-input
              v-model="query.keyword"
              placeholder="请输入设施名称"
              class="handle-input mr10"
            >
              <template #append>
                <el-button :icon="Search" @click="handleSearch"></el-button>
              </template>
            </el-input>
            <el-button
              :icon="Delete"
              @click="clearSearch"
              :disabled="!query.keyword && !query.categoryCode"
              >清除筛选</el-button
            >
            <el-switch
              v-model="query.onlyActive"
              active-text="只显示激活设施"
              inactive-text="显示所有设施"
              @change="handleSearch"
              class="ml10"
            ></el-switch>
          </el-col>
        </el-row>
      </div>

      <el-card shadow="hover" class="mb20">
        <template #header>
          <div class="card-header">
            <span>设施列表</span>
            <el-tag type="info" v-if="pageTotal > 0">共 {{ pageTotal }} 项</el-tag>
          </div>
        </template>

        <el-table
          v-loading="loading"
          :data="tableData"
          border
          stripe
          highlight-current-row
          class="table"
          ref="multipleTable"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="55"></el-table-column>
          <el-table-column prop="value" label="设施编码" width="120" sortable></el-table-column>
          <el-table-column prop="label" label="设施名称" width="120" sortable></el-table-column>
          <el-table-column prop="description" label="描述" show-overflow-tooltip></el-table-column>
          <el-table-column prop="categoryName" label="所属分类" width="100" sortable>
            <template #default="scope">
              <el-tag size="small" type="success" v-if="scope.row.categoryName">{{
                scope.row.categoryName
              }}</el-tag>
              <el-tag size="small" type="info" v-else>未分类</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="icon" label="图标" width="70">
            <template #default="scope">
              <el-icon v-if="scope.row.icon">
                <component :is="scope.row.icon"></component>
              </el-icon>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="usageCount" label="使用次数" width="90" sortable>
            <template #default="scope">
              <el-tag type="info" size="small" v-if="scope.row.usageCount">{{
                scope.row.usageCount
              }}</el-tag>
              <span v-else>0</span>
            </template>
          </el-table-column>
          <el-table-column prop="active" label="状态" width="80" sortable>
            <template #default="scope">
              <el-tag :type="scope.row.active ? 'success' : 'info'" size="small">
                {{ scope.row.active ? "已激活" : "未激活" }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="280" align="center" fixed="right">
            <template #default="scope">
              <el-button type="primary" size="small" :icon="Edit" @click="handleEdit(scope.row)"
                >编辑</el-button
              >
              <el-button type="danger" size="small" :icon="Delete" @click="handleDelete(scope.row)"
                >删除</el-button
              >
              <el-button
                v-if="!scope.row.active"
                type="success"
                size="small"
                :icon="Check"
                @click="handleActivate(scope.row)"
                >激活</el-button
              >
              <el-button
                v-if="scope.row.active"
                type="warning"
                size="small"
                :icon="Close"
                @click="handleDeactivate(scope.row)"
                >禁用</el-button
              >
            </template>
          </el-table-column>

          <template #empty>
            <el-empty description="暂无设施数据">
              <template #description>
                <p>暂无设施数据</p>
                <p v-if="query.keyword || query.categoryCode" class="empty-tips">
                  可能没有符合筛选条件的数据，请尝试
                  <el-button type="text" @click="clearSearch">清除筛选条件</el-button>
                </p>
                <p v-else class="empty-tips">
                  可以点击
                  <el-button type="text" @click="handleInitDefaults">一键初始化设施</el-button>
                  添加系统预设的设施
                </p>
              </template>
            </el-empty>
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
          >
          </el-pagination>
        </div>
      </el-card>
    </div>

    <!-- 新增/编辑弹出框 -->
    <el-dialog
      :title="editMode ? '编辑设施' : '新增设施'"
      v-model="dialogVisible"
      width="40%"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" status-icon>
        <el-form-item label="设施编码" prop="value">
          <el-input
            v-model="form.value"
            placeholder="请输入设施编码（大写英文和数字）"
            :disabled="editMode"
          >
            <template #append v-if="editMode">
              <el-tooltip content="编码创建后不可修改" placement="top">
                <el-icon>
                  <Lock />
                </el-icon>
              </el-tooltip>
            </template>
          </el-input>
          <div class="form-tip" v-if="!editMode">
            设施编码创建后不可修改，建议使用大写英文和下划线
          </div>
        </el-form-item>
        <el-form-item label="设施名称" prop="label">
          <el-input v-model="form.label" placeholder="请输入设施名称"></el-input>
        </el-form-item>
        <el-form-item label="所属分类" prop="categoryCode">
          <el-select v-model="form.categoryCode" placeholder="请选择分类" style="width: 100%">
            <el-option label="未分类" value=""></el-option>
            <el-option
              v-for="item in categories"
              :key="item.code"
              :label="item.name"
              :value="item.code"
            ></el-option>
          </el-select>
          <div class="form-tip">设施分类可以在"设施分类管理"中添加和修改</div>
        </el-form-item>
        <el-form-item label="图标" prop="icon">
          <el-input v-model="form.icon" placeholder="请输入图标名称（例如：House）">
            <template #append>
              <el-icon v-if="form.icon">
                <component :is="form.icon"></component>
              </el-icon>
              <span v-else>无图标</span>
            </template>
          </el-input>
          <div class="form-tip">图标名称对应Element Plus的图标组件名称</div>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            type="textarea"
            v-model="form.description"
            placeholder="请输入设施描述"
            :rows="3"
          ></el-input>
        </el-form-item>
        <el-form-item label="状态" prop="active">
          <el-switch
            v-model="form.active"
            :active-value="true"
            :inactive-value="false"
            active-text="激活"
            inactive-text="未激活"
          >
          </el-switch>
          <div class="form-tip">只有激活状态的设施才会显示在房源添加设施的列表中</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取 消</el-button>
          <el-button type="primary" @click="saveAmenity" :loading="submitLoading">确 定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox, FormInstance } from "element-plus";
import {
  Delete,
  Edit,
  Search,
  Plus,
  Lightning,
  Check,
  Close,
  Refresh,
  Lock,
} from "@element-plus/icons-vue";
import {
  getAmenitiesApi,
  createAmenityApi,
  updateAmenityApi,
  deleteAmenityApi,
  initDefaultAmenitiesApi,
  activateAllAmenitiesApi,
  getCategoriesApi,
} from "@/api/amenities";

// 数据类型定义
interface Amenity {
  value: string;
  label: string;
  description?: string;
  icon?: string;
  categoryCode?: string;
  categoryName?: string;
  active: boolean;
  usageCount?: number;
  createdAt?: string;
  updatedAt?: string;
}

interface AmenityCategory {
  code: string;
  name: string;
  active?: boolean;
}

// 表格数据
const tableData = ref<Amenity[]>([]);
// 表格加载状态
const loading = ref(false);
// 表单提交状态
const submitLoading = ref(false);
// 多选数据
const multipleSelection = ref<Amenity[]>([]);
// 查询参数
const query = reactive({
  keyword: "",
  pageIndex: 1,
  pageSize: 10,
  categoryCode: "",
  onlyActive: false,
});
// 分页总数
const pageTotal = ref(0);
// 弹出框显示状态
const dialogVisible = ref(false);
// 是否是编辑模式
const editMode = ref(false);
// 表单对象
const form = reactive<Amenity>({
  value: "",
  label: "",
  description: "",
  icon: "",
  categoryCode: "",
  active: true,
});
// 表单引用
const formRef = ref<FormInstance>();
// 表单验证规则
const rules = reactive({
  value: [
    { required: true, message: "请输入设施编码", trigger: "blur" },
    { pattern: /^[A-Z0-9_]+$/, message: "编码只能包含大写字母、数字和下划线", trigger: "blur" },
  ],
  label: [
    { required: true, message: "请输入设施名称", trigger: "blur" },
    { min: 1, max: 50, message: "名称长度在1-50个字符之间", trigger: "blur" },
  ],
  description: [{ max: 500, message: "描述不能超过500个字符", trigger: "blur" }],
});

// 分类数据
const categories = ref<AmenityCategory[]>([]);

// 页面加载时获取数据
onMounted(() => {
  getAmenities();
  getCategories();
});

// 获取设施数据
const getAmenities = () => {
  loading.value = true;

  // 创建API参数对象
  const params = {
    keyword: query.keyword || undefined,
    categoryCode: query.categoryCode || undefined,
    page: query.pageIndex - 1, // 后端分页从0开始
    size: query.pageSize,
    onlyActive: query.onlyActive,
  };

  getAmenitiesApi(params)
    .then((response) => {
      if (response.success) {
        // 处理后端响应结构
        if (response.data && typeof response.data === "object") {
          // 分页对象结构 {content: [], totalElements: number, totalPages: number}
          if (Array.isArray(response.data.content)) {
            tableData.value = response.data.content;
            pageTotal.value = response.data.totalElements || 0;
            // 如果后端返回了当前页，同步前端页码
            if (typeof response.data.currentPage === "number") {
              query.pageIndex = response.data.currentPage + 1; // 后端从0开始
            }
          }
          // 如果后端直接返回数组
          else if (Array.isArray(response.data)) {
            tableData.value = response.data;
            // 如果后端没有返回总数，则可能是没有实现分页，使用数组长度作为总数
            pageTotal.value = response.data.length;
            console.log("使用数组长度作为总记录数:", pageTotal.value);

            // 如果数据量等于每页数量，可能有更多数据
            if (tableData.value.length === query.pageSize) {
              console.log("当前页数据已满，可能存在更多数据");
            }
          } else {
            console.error("未知的响应数据结构:", response);
            tableData.value = [];
            pageTotal.value = 0;
            ElMessage.warning("获取数据格式异常，请联系管理员");
          }
        } else {
          tableData.value = [];
          pageTotal.value = 0;
          ElMessage.warning("获取数据为空");
        }

        // 数据为空时提示
        if (tableData.value.length === 0) {
          ElMessage.info(
            query.keyword || query.categoryCode
              ? "没有找到符合条件的设施"
              : '设施列表为空，可以点击"一键初始化设施"添加预设设施'
          );
        }
      } else {
        ElMessage.error(response.message || "获取设施数据失败");
        tableData.value = [];
        pageTotal.value = 0;
      }
    })
    .catch((error) => {
      console.error("获取设施数据出错:", error);
      const err = error as any;
      ElMessage.error(`获取设施数据出错: ${err?.message || "网络错误"}`);
      tableData.value = [];
      pageTotal.value = 0;
    })
    .finally(() => {
      loading.value = false;
    });
};

// 获取分类数据
const getCategories = () => {
  getCategoriesApi()
    .then((response) => {
      if (response.success) {
        categories.value = response.data || [];
      } else {
        ElMessage.error(response.message || "获取设施分类失败");
      }
    })
    .catch((error) => {
      console.error("获取设施分类出错:", error);
      ElMessage.error("获取设施分类出错");
    });
};

// 搜索操作
const handleSearch = () => {
  // 重置到第一页
  query.pageIndex = 1;
  getAmenities();
};

// 清除搜索
const clearSearch = () => {
  query.keyword = "";
  query.categoryCode = "";
  query.pageIndex = 1;
  getAmenities();
};

// 重置表单
const resetForm = () => {
  if (formRef.value) {
    formRef.value.resetFields();
  }
  form.value = "";
  form.label = "";
  form.description = "";
  form.icon = "";
  form.categoryCode = "";
  form.active = true;
};

// 添加操作
const handleAdd = () => {
  editMode.value = false;
  resetForm();
  dialogVisible.value = true;
};

// 编辑操作
const handleEdit = (row: Amenity) => {
  editMode.value = true;
  // 复制对象，避免直接修改原对象
  Object.assign(form, {
    value: row.value,
    label: row.label,
    description: row.description || "",
    icon: row.icon || "",
    categoryCode: row.categoryCode || "",
    active: row.active,
  });
  dialogVisible.value = true;
};

// 表格选择改变
const handleSelectionChange = (selection: Amenity[]) => {
  multipleSelection.value = selection;
};

// 处理API错误
const handleApiError = (error: any, item?: string) => {
  const itemPrefix = item ? `设施"${item}"` : "设施";

  if (error && error.response && error.response.data) {
    const errorData = error.response.data;
    return errorData.message || `删除${itemPrefix}失败: ${error.response.status}`;
  } else if (error && error.message) {
    return error.message;
  } else {
    return "网络错误";
  }
};

// 批量删除
const handleBatchDelete = () => {
  if (multipleSelection.value.length === 0) {
    ElMessage.warning("请先选择要删除的设施");
    return;
  }

  ElMessageBox.confirm(
    `确定要删除选中的${multipleSelection.value.length}个设施吗？被使用中的设施可能无法删除。`,
    "提示",
    {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    }
  )
    .then(() => {
      loading.value = true;

      // 使用顺序删除而不是并行，避免大量并发请求
      const sequentialDelete = async () => {
        const results = {
          success: 0,
          fail: 0,
          messages: [] as string[],
          inUseItems: [] as string[], // 记录正被使用的设施
        };

        for (const item of multipleSelection.value) {
          try {
            const res = await deleteAmenityApi(item.value);
            if (res.success) {
              results.success++;
            } else {
              results.fail++;
              const errMsg = `设施"${item.label}"删除失败: ${res.message || "未知错误"}`;
              results.messages.push(errMsg);

              // 检查是否是使用中的设施
              if (res.message && res.message.includes("使用")) {
                results.inUseItems.push(item.label);
              }
            }
          } catch (error) {
            results.fail++;
            const errMsg = `设施"${item.label}"删除出错: ${handleApiError(error, item.label)}`;
            results.messages.push(errMsg);

            // 从错误响应中检测是否是使用中的设施
            const err = error as any; // 类型断言
            if (
              err.response &&
              err.response.data &&
              err.response.data.message &&
              err.response.data.message.includes("使用")
            ) {
              results.inUseItems.push(item.label);
            }
          }
        }

        return results;
      };

      sequentialDelete()
        .then((results) => {
          if (results.success > 0) {
            ElMessage.success(`成功删除${results.success}个设施`);
          }

          if (results.fail > 0) {
            ElMessage.error(`${results.fail}个设施删除失败`);
            console.error("删除失败的设施:", results.messages);

            // 如果失败数量不多，显示具体错误信息
            if (results.messages.length <= 3) {
              results.messages.forEach((msg) => {
                ElMessage.warning(msg);
              });
            } else {
              ElMessageBox.alert(results.messages.join("<br/>"), "删除失败详情", {
                dangerouslyUseHTMLString: true,
              });
            }

            // 如果有设施被使用中，显示提示
            if (results.inUseItems.length > 0) {
              ElMessageBox.alert(
                `以下设施正在被房源使用，无法直接删除：<br>` +
                  `${results.inUseItems.join("<br>")}<br><br>` +
                  `您可以：<br>` +
                  `1. 先从相关房源中移除这些设施<br>` +
                  `2. 或者将这些设施设为"未激活"状态而非删除`,
                "操作提示",
                { dangerouslyUseHTMLString: true, type: "warning" }
              );
            }
          }

          getAmenities(); // 刷新列表
        })
        .finally(() => {
          loading.value = false;
        });
    })
    .catch(() => {
      // 用户取消操作，不做处理
    });
};

// 删除操作
const handleDelete = (row: Amenity) => {
  ElMessageBox.confirm(
    `确定要删除设施"${row.label}"吗？如果该设施正在被房源使用，将无法删除。`,
    "提示",
    {
      type: "warning",
    }
  )
    .then(() => {
      loading.value = true;
      deleteAmenityApi(row.value)
        .then((response) => {
          if (response.success) {
            ElMessage.success(response.message || "删除成功");
            getAmenities(); // 刷新列表
          } else {
            ElMessage.error(response.message || "删除失败");
            // 提供更明确的操作指导
            if (response.message && response.message.includes("使用")) {
              ElMessageBox.alert(
                "该设施正在被房源使用，无法直接删除。您可以：<br>" +
                  "1. 先从相关房源中移除此设施<br>" +
                  '2. 或者将此设施设为"未激活"状态而非删除',
                "操作提示",
                { dangerouslyUseHTMLString: true, type: "warning" }
              );
            }
          }
        })
        .catch((error) => {
          console.error("删除设施出错:", error);
          // 处理不同类型的错误响应
          const err = error as any;
          if (err.response && err.response.data) {
            const errorData = err.response.data;
            ElMessage.error(errorData.message || `删除失败: ${err.response.status}`);

            // 对于使用中的设施，提供更多指导
            if (errorData.message && errorData.message.includes("使用")) {
              ElMessageBox.alert(
                "该设施正在被房源使用，无法直接删除。您可以：<br>" +
                  "1. 先从相关房源中移除此设施<br>" +
                  '2. 或者将此设施设为"未激活"状态而非删除',
                "操作提示",
                { dangerouslyUseHTMLString: true, type: "warning" }
              );
            }
          } else {
            ElMessage.error(`删除设施出错: ${err.message || "未知错误"}`);
          }
        })
        .finally(() => {
          loading.value = false;
        });
    })
    .catch(() => {
      // 用户取消操作，不做处理
    });
};

// 保存设施
const saveAmenity = () => {
  if (!formRef.value) return;

  formRef.value.validate((valid) => {
    if (valid) {
      submitLoading.value = true;

      const saveData = {
        value: form.value,
        label: form.label,
        description: form.description,
        icon: form.icon,
        categoryCode: form.categoryCode,
        active: form.active,
      };

      const savePromise = editMode.value
        ? updateAmenityApi(form.value, saveData)
        : createAmenityApi(saveData);

      savePromise
        .then((response) => {
          if (response.success) {
            ElMessage.success(response.message || `${editMode.value ? "更新" : "添加"}成功`);
            dialogVisible.value = false;
            getAmenities();
          } else {
            ElMessage.error(response.message || `${editMode.value ? "更新" : "添加"}失败`);
          }
        })
        .catch((error) => {
          console.error(`${editMode.value ? "更新" : "添加"}设施出错:`, error);
          ElMessage.error(`${editMode.value ? "更新" : "添加"}设施出错`);
        })
        .finally(() => {
          submitLoading.value = false;
        });
    } else {
      ElMessage.warning("请填写完整有效的表单信息");
    }
  });
};

// 分页导航 - 页码变化
const handlePageChange = (val: number) => {
  query.pageIndex = val;
  getAmenities();
};

// 分页导航 - 每页数量变化
const handleSizeChange = (val: number) => {
  query.pageSize = val;
  query.pageIndex = 1; // 重置到第一页
  getAmenities();
};

// 初始化默认设施
const handleInitDefaults = () => {
  ElMessageBox.confirm("确定要一键初始化所有预设设施吗？这将添加系统内置的所有设施类型。", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  })
    .then(() => {
      loading.value = true;
      initDefaultAmenitiesApi()
        .then((response) => {
          if (response.success) {
            ElMessage.success(response.message || "设施初始化成功");
            getAmenities();
            // 重新获取分类数据，因为可能有新的分类被创建
            getCategories();
          } else {
            ElMessage.error(response.message || "初始化设施失败");
          }
        })
        .catch((error) => {
          console.error("初始化设施出错:", error);
          const err = error as any;
          ElMessage.error(`初始化设施出错: ${err?.message || "网络错误"}`);
        })
        .finally(() => {
          loading.value = false;
        });
    })
    .catch(() => {});
};

// 激活设施
const handleActivate = (row: Amenity) => {
  ElMessageBox.confirm("确定要激活该设施吗？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  })
    .then(() => {
      updateAmenityApi(row.value, { ...row, active: true })
        .then((response) => {
          if (response.success) {
            ElMessage.success(response.message || "激活成功");
            getAmenities();
          } else {
            ElMessage.error(response.message || "激活失败");
          }
        })
        .catch((error) => {
          console.error("激活设施出错:", error);
          const err = error as any;
          ElMessage.error(`激活设施出错: ${err?.message || "网络错误"}`);
        });
    })
    .catch(() => {});
};

// 禁用设施
const handleDeactivate = (row: Amenity) => {
  ElMessageBox.confirm("确定要禁用该设施吗？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  })
    .then(() => {
      updateAmenityApi(row.value, { ...row, active: false })
        .then((response) => {
          if (response.success) {
            ElMessage.success(response.message || "禁用成功");
            getAmenities();
          } else {
            ElMessage.error(response.message || "禁用失败");
          }
        })
        .catch((error) => {
          console.error("禁用设施出错:", error);
          const err = error as any;
          ElMessage.error(`禁用设施出错: ${err?.message || "网络错误"}`);
        });
    })
    .catch(() => {});
};

// 一键激活所有设施
const handleActivateAll = () => {
  ElMessageBox.confirm("确定要一键激活所有设施吗？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  })
    .then(() => {
      loading.value = true;
      activateAllAmenitiesApi()
        .then((response) => {
          if (response.success) {
            ElMessage.success(response.message || "设施激活成功");
            getAmenities();
          } else {
            ElMessage.error(response.message || "激活设施失败");
          }
        })
        .catch((error) => {
          console.error("激活设施出错:", error);
          const err = error as any;
          ElMessage.error(`激活设施出错: ${err?.message || "网络错误"}`);
        })
        .finally(() => {
          loading.value = false;
        });
    })
    .catch(() => {});
};
</script>

<style scoped>
.handle-box {
  margin-bottom: 20px;
}

.handle-input {
  width: 300px;
  display: inline-block;
}

.mr10 {
  margin-right: 10px;
}

.ml10 {
  margin-left: 10px;
}

.mb10 {
  margin-bottom: 10px;
}

.mb20 {
  margin-bottom: 20px;
}

.table {
  width: 100%;
  font-size: 14px;
}

.red {
  color: #ff0000;
}

.green {
  color: #67c23a;
}

.orange {
  color: #e6a23c;
}

.pagination {
  margin: 20px 0;
  text-align: right;
}

.dialog-footer {
  text-align: right;
}

.empty-tips {
  margin-top: 10px;
  text-align: center;
}

.form-tip {
  color: #999;
  font-size: 12px;
  margin-top: 5px;
  line-height: 1.5;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* 表格内容文字溢出省略显示 */
.el-table .cell {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
