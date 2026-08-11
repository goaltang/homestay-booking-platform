<template>
  <div>
    <div class="container">
      <div class="handle-box">
        <el-row :gutter="20">
          <el-col :span="24" class="mb10">
            <el-button type="primary" :icon="Plus" @click="handleAdd">新增配置</el-button>
            <el-button type="success" :icon="Lightning" @click="handleInitDefaults">初始化默认配置</el-button>
            <el-tooltip content="刷新配置列表" placement="top">
              <el-button :icon="Refresh" circle @click="getList"></el-button>
            </el-tooltip>
          </el-col>
          <el-col :span="24">
            <el-select v-model="query.category" placeholder="选择分类" clearable class="handle-select mr10" @change="applyFilter">
              <el-option label="全部分类" value=""></el-option>
              <el-option label="平台配置" value="platform"></el-option>
              <el-option label="政策配置" value="policy"></el-option>
              <el-option label="费用配置" value="fee"></el-option>
              <el-option label="房源配置" value="homestay"></el-option>
              <el-option label="其他配置" value="other"></el-option>
            </el-select>
            <el-input v-model="query.keyword" placeholder="配置名称/键" class="handle-input mr10" @keyup.enter="applyFilter">
              <template #append>
                <el-button :icon="Search" @click="applyFilter"></el-button>
              </template>
            </el-input>
            <el-button :icon="Delete" @click="clearSearch" :disabled="!query.keyword && !query.category">清除筛选</el-button>
          </el-col>
        </el-row>
      </div>

      <el-card shadow="hover">
        <template #header>
          <div class="card-header">
            <span>系统配置列表</span>
            <el-tag type="info" v-if="tableData.length > 0">共 {{ tableData.length }} 项</el-tag>
          </div>
        </template>

        <el-table v-loading="loading" :data="tableData" border stripe highlight-current-row class="table">
          <el-table-column prop="configKey" label="配置键" width="200" sortable>
            <template #default="scope">
              <span class="config-key">{{ scope.row.configKey }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="configName" label="配置名称" width="150" sortable></el-table-column>
          <el-table-column prop="configValue" label="配置值" min-width="150" show-overflow-tooltip>
            <template #default="scope">
              <span class="config-value">{{ scope.row.configValue || '(空)' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip></el-table-column>
          <el-table-column prop="category" label="分类" width="100" sortable>
            <template #default="scope">
              <el-tag size="small" :type="getCategoryType(scope.row.category)">
                {{ getCategoryName(scope.row.category) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="updatedBy" label="更新人" width="100"></el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" width="160" sortable>
            <template #default="scope">
              <span v-if="scope.row.updatedAt">{{ formatDate(scope.row.updatedAt) }}</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" align="center" fixed="right">
            <template #default="scope">
              <el-button type="primary" size="small" :icon="Edit" @click="handleEdit(scope.row)">编辑</el-button>
            </template>
          </el-table-column>

          <template #empty>
            <el-empty description="暂无配置数据">
              <template #description>
                <p>暂无配置数据</p>
                <p class="empty-tips">
                  可以点击
                  <el-button type="text" @click="handleInitDefaults">初始化默认配置</el-button>
                  添加系统预设配置
                </p>
              </template>
            </el-empty>
          </template>
        </el-table>
      </el-card>
    </div>

    <!-- 新增/编辑弹出框 -->
    <el-dialog :title="editMode ? '编辑配置' : '新增配置'" v-model="dialogVisible" width="50%" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" status-icon>
        <el-form-item label="配置键" prop="configKey">
          <el-input v-model="form.configKey" placeholder="请输入配置键" :disabled="editMode">
            <template #append v-if="editMode">
              <el-tooltip content="配置键创建后不可修改" placement="top">
                <el-icon><Lock /></el-icon>
              </el-tooltip>
            </template>
          </el-input>
          <div class="form-tip" v-if="!editMode">配置键唯一标识，创建后不可修改</div>
        </el-form-item>
        <el-form-item label="配置名称" prop="configName">
          <el-input v-model="form.configName" placeholder="请输入配置名称"></el-input>
        </el-form-item>
        <el-form-item label="配置值" prop="configValue">
          <el-input v-model="form.configValue" placeholder="请输入配置值" type="textarea" :rows="3"></el-input>
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="form.category" placeholder="请选择分类" style="width: 100%">
            <el-option label="平台配置" value="platform"></el-option>
            <el-option label="政策配置" value="policy"></el-option>
            <el-option label="费用配置" value="fee"></el-option>
            <el-option label="房源配置" value="homestay"></el-option>
            <el-option label="其他配置" value="other"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" placeholder="请输入配置描述" type="textarea" :rows="2"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取 消</el-button>
          <el-button type="primary" @click="handleSubmit" :loading="submitLoading">确 定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Search, Plus, Lightning, Refresh, Lock } from '@element-plus/icons-vue'
import { useCrud } from '@/composables/useCrud'
import {
  getAllConfigsApi,
  createConfigApi,
  updateConfigApi,
  initDefaultConfigsApi,
  type SystemConfig,
} from '@/api/systemConfig'

const categoryMap: Record<string, string> = {
  platform: '平台配置',
  policy: '政策配置',
  fee: '费用配置',
  homestay: '房源配置',
  other: '其他配置',
}

const allConfigs = ref<SystemConfig[]>([])
const submitLoading = ref(false)
const operator = ref(localStorage.getItem('homestay_admin_username') || localStorage.getItem('username') || 'admin')

// 包装列表 API：先获取全部，前端做筛选
const wrappedListApi = async () => {
  const res = await getAllConfigsApi()
  if (!res.success) throw new Error(res.message || '获取配置失败')
  allConfigs.value = res.data || []
  // 前端筛选逻辑在 getList 后执行
  return {
    content: res.data || [],
    totalElements: res.data?.length || 0,
  }
}

const wrappedCreateApi = async (data: any) => {
  const res = await createConfigApi(data, operator.value)
  if (!res.success) throw new Error(res.message || '创建失败')
  return res
}

const wrappedUpdateApi = async (_id: number | string, data: any) => {
  const res = await updateConfigApi(data.configKey, data, operator.value)
  if (!res.success) throw new Error(res.message || '更新失败')
  return res
}

// 使用 useCrud，但关闭分页（Config 是前端筛选）
const {
  loading, tableData, query, formRef, form, rules,
  dialogVisible, editMode, getList, handleAdd, handleEdit, resetForm,
} = useCrud<SystemConfig>({
  listApi: wrappedListApi,
  createApi: wrappedCreateApi,
  updateApi: wrappedUpdateApi,
  pagination: false,
  defaultQuery: { keyword: '', category: '' } as any,
  defaultForm: {
    configKey: '',
    configName: '',
    configValue: '',
    category: 'other',
    description: '',
  } as any,
  rules: {
    configKey: [
      { required: true, message: '请输入配置键', trigger: 'blur' },
      { pattern: /^[a-z_0-9.]+$/, message: '配置键只能包含小写字母、下划线和点', trigger: 'blur' },
    ],
    configName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
    category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  },
})

// 覆盖 getList，增加前端筛选
const _rawGetList = getList
const getListWithFilter = async () => {
  await _rawGetList()
  applyFilter()
}

// 前端筛选
const applyFilter = () => {
  let filtered = [...allConfigs.value]
  if (query.category) {
    filtered = filtered.filter((c: any) => c.category === query.category)
  }
  if (query.keyword) {
    const keyword = (query.keyword as string).toLowerCase()
    filtered = filtered.filter((c: any) =>
      c.configKey?.toLowerCase().includes(keyword) ||
      c.configName?.toLowerCase().includes(keyword)
    )
  }
  tableData.value = filtered
}

const clearSearch = () => {
  query.keyword = ''
  query.category = ''
  applyFilter()
}

// 覆盖 handleSubmit，增加 submitLoading 和 operator
const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid: boolean) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (editMode.value) {
        await wrappedUpdateApi(form.configKey as string, form)
        ElMessage.success('更新成功')
      } else {
        await wrappedCreateApi(form)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      resetForm()
      await getListWithFilter()
    } catch (e: any) {
      ElMessage.error(e.message || '操作失败')
    } finally {
      submitLoading.value = false
    }
  })
}

// 初始化默认配置（特殊业务按钮）
const handleInitDefaults = () => {
  ElMessageBox.confirm('确定要初始化默认配置吗？这将添加系统预设的配置项。', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      loading.value = true
      try {
        const res = await initDefaultConfigsApi()
        if (res.success) {
          ElMessage.success(res.message || '初始化成功')
          await getListWithFilter()
        } else {
          ElMessage.error(res.message || '初始化失败')
        }
      } finally {
        loading.value = false
      }
    })
    .catch(() => {})
}

const getCategoryName = (category?: string) => categoryMap[category || ''] || category || '其他'

const getCategoryType = (category?: string) => {
  const typeMap: Record<string, string> = {
    platform: 'primary', policy: 'success', fee: 'warning', homestay: 'danger', other: 'info',
  }
  return typeMap[category || 'other'] || 'info'
}

const formatDate = (dateStr?: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  })
}

// 初始化加载
getListWithFilter()
</script>

<style scoped>
.handle-box { margin-bottom: 20px; }
.handle-input { width: 300px; display: inline-block; }
.handle-select { width: 150px; display: inline-block; }
.mr10 { margin-right: 10px; }
.mb10 { margin-bottom: 10px; }
.table { width: 100%; font-size: 14px; }
.pagination { margin: 20px 0; text-align: right; }
.dialog-footer { text-align: right; }
.empty-tips { margin-top: 10px; text-align: center; }
.form-tip { color: #999; font-size: 12px; margin-top: 5px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.config-key { font-family: monospace; color: var(--el-color-primary); }
.config-value { font-family: monospace; }
</style>
