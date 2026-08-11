<template>
  <div class="container">
    <div class="handle-box">
      <el-input v-model="query.keyword" placeholder="搜索标题" class="handle-input mr10" clearable @keyup.enter="handleSearch" />
      <el-button :icon="Search" type="primary" @click="handleSearch">搜索</el-button>
      <el-button :icon="Refresh" circle @click="clearSearch" />
      <el-button :icon="Plus" type="success" class="ml10" @click="handleAdd">新增 Banner</el-button>
    </div>

    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>Banner 管理</span>
          <span class="log-count" v-if="pagination.total > 0">共 {{ pagination.total }} 条</span>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe class="table">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="预览" width="120">
          <template #default="scope">
            <div v-if="scope.row.imageUrl" class="banner-preview">
              <el-image :src="scope.row.imageUrl" fit="cover" :preview-src-list="[scope.row.imageUrl]" style="width: 100px; height: 60px; border-radius: 4px;" />
            </div>
            <div v-else-if="scope.row.bgGradient" class="banner-preview" :style="{ background: scope.row.bgGradient, width: '100px', height: '60px', borderRadius: '4px' }" />
            <span v-else class="text-gray">无预览</span>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
        <el-table-column prop="subtitle" label="副标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="linkUrl" label="跳转链接" min-width="160" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="scope">
            <el-switch v-model="scope.row.enabled" inline-prompt active-text="启用" inactive-text="禁用" @change="handleToggle(scope.row)" />
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="160">
          <template #default="scope">
            {{ formatDate(scope.row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="scope">
            <el-button type="primary" link size="small" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination background layout="total, sizes, prev, pager, next, jumper"
          :current-page="pagination.page + 1" :page-size="pagination.size" :page-sizes="[10, 20, 50]"
          :total="pagination.total" @current-change="(p: number) => handlePageChange(p - 1)"
          @size-change="handleSizeChange" />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑 Banner' : '新增 Banner'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入 Banner 标题" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="副标题" prop="subtitle">
          <el-input v-model="form.subtitle" type="textarea" :rows="2" placeholder="请输入副标题描述" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="背景图片">
          <div class="upload-wrapper">
            <el-upload class="banner-uploader" action="#" :auto-upload="false" :show-file-list="false"
              :on-change="handleImageChange" accept="image/*">
              <div v-if="form.imageUrl" class="upload-preview">
                <el-image :src="form.imageUrl" fit="cover" style="width: 200px; height: 120px; border-radius: 6px;" />
                <div class="upload-overlay">
                  <el-icon size="20"><RefreshRight /></el-icon>
                  <span>更换图片</span>
                </div>
              </div>
              <div v-else class="upload-placeholder">
                <el-icon size="28"><Plus /></el-icon>
                <span>点击上传背景图</span>
              </div>
            </el-upload>
            <el-button v-if="form.imageUrl" type="danger" link size="small" @click="form.imageUrl = ''">移除图片</el-button>
          </div>
          <div class="form-tip">支持 JPG、PNG、GIF，建议尺寸 1200×400</div>
        </el-form-item>
        <el-form-item label="渐变背景">
          <el-input v-model="form.bgGradient" placeholder="CSS 渐变，如 linear-gradient(...)" />
          <div v-if="form.bgGradient && !form.imageUrl" class="gradient-preview" :style="{ background: form.bgGradient }">
            预览
          </div>
          <div class="form-tip">无图片时显示渐变背景，有图片时优先显示图片</div>
        </el-form-item>
        <el-form-item label="跳转链接" prop="linkUrl">
          <el-input v-model="form.linkUrl" placeholder="/homestays 或 https://example.com" />
        </el-form-item>
        <el-form-item label="排序权重">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
          <span class="form-tip ml10">数字越小越靠前</span>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="form.enabled" inline-prompt active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, RefreshRight } from '@element-plus/icons-vue'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
import {
  getBannerPage, createBanner, updateBanner, deleteBanner, toggleBannerEnabled, type Banner
} from '@/api/banner'
import { uploadSingleFile } from '@/api/file'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()

const tableData = ref<Banner[]>([])

const query = reactive({
  keyword: ''
})

const pagination = reactive({
  page: 0,
  size: 10,
  total: 0
})

const form = reactive<Banner>({
  title: '',
  subtitle: '',
  imageUrl: '',
  linkUrl: '',
  bgGradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
  sortOrder: 0,
  enabled: true
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  linkUrl: [{ required: true, message: '请输入跳转链接', trigger: 'blur' }]
}

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await getBannerPage({
      page: pagination.page,
      size: pagination.size,
      keyword: query.keyword || undefined
    })
    if (res.success || res.data?.success) {
      const data = res.data || res
      tableData.value = data.data || []
      pagination.total = data.total || 0
    }
  } catch (error) {
    console.error('加载 Banner 失败:', error)
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 0
  loadData()
}

const clearSearch = () => {
  query.keyword = ''
  pagination.page = 0
  loadData()
}

const handlePageChange = (page: number) => {
  pagination.page = page
  loadData()
}

const handleSizeChange = (size: number) => {
  pagination.size = size
  pagination.page = 0
  loadData()
}

const resetForm = () => {
  form.id = undefined
  form.title = ''
  form.subtitle = ''
  form.imageUrl = ''
  form.linkUrl = ''
  form.bgGradient = 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
  form.sortOrder = 0
  form.enabled = true
}

const handleAdd = () => {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

const handleEdit = (row: Banner) => {
  isEdit.value = true
  Object.assign(form, row)
  dialogVisible.value = true
}

const handleImageChange = async (uploadFile: UploadFile) => {
  const rawFile = uploadFile.raw
  if (!rawFile) return
  try {
    ElMessage.info('正在上传...')
    const url = await uploadSingleFile(rawFile, 'banner')
    if (url) {
      form.imageUrl = url
      ElMessage.success('上传成功')
    }
  } catch (error) {
    ElMessage.error('上传失败')
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (isEdit.value && form.id) {
        await updateBanner(form.id, { ...form })
        ElMessage.success('更新成功')
      } else {
        await createBanner({ ...form })
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      loadData()
    } catch (error) {
      console.error('保存失败:', error)
      ElMessage.error('保存失败')
    } finally {
      submitting.value = false
    }
  })
}

const handleToggle = async (row: Banner) => {
  if (!row.id) return
  try {
    await toggleBannerEnabled(row.id)
    ElMessage.success('状态更新成功')
  } catch (error) {
    ElMessage.error('操作失败')
    row.enabled = !row.enabled
  }
}

const handleDelete = (row: Banner) => {
  if (!row.id) return
  ElMessageBox.confirm(`确定要删除 Banner「${row.title}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteBanner(row.id!)
    ElMessage.success('删除成功')
    loadData()
  }).catch(() => {
    // cancelled
  })
}

const formatDate = (dateStr?: string) => {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return d.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit'
  })
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.container {
  padding: 20px;
}

.handle-box {
  margin-bottom: 20px;
}

.handle-input {
  width: 200px;
}

.mr10 {
  margin-right: 10px;
}

.ml10 {
  margin-left: 10px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.log-count {
  color: #909399;
  font-size: 13px;
}

.table {
  width: 100%;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.text-gray {
  color: #c0c4cc;
  font-size: 12px;
}

.upload-wrapper {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.banner-uploader {
  cursor: pointer;
}

.upload-preview {
  position: relative;
  border-radius: 6px;
  overflow: hidden;
}

.upload-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 12px;
  opacity: 0;
  transition: opacity 0.3s;
}

.upload-preview:hover .upload-overlay {
  opacity: 1;
}

.upload-placeholder {
  width: 200px;
  height: 120px;
  border: 2px dashed #dcdfe6;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #8c939d;
  font-size: 13px;
  gap: 8px;
  transition: border-color 0.3s;
}

.upload-placeholder:hover {
  border-color: var(--el-color-primary);
}

.gradient-preview {
  margin-top: 8px;
  width: 200px;
  height: 60px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 12px;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
}

.form-tip {
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}
</style>
