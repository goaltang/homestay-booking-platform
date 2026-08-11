<template>
  <div class="holiday-page">
    <!-- 顶部统计卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :xs="12" :sm="6">
        <div class="stat-card stat-primary">
          <div class="stat-icon"><el-icon><Calendar /></el-icon></div>
          <div class="stat-body">
            <div class="stat-value">{{ statTotal }}</div>
            <div class="stat-label">节假日总数</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card stat-danger">
          <div class="stat-icon"><el-icon><Clock /></el-icon></div>
          <div class="stat-body">
            <div class="stat-value">{{ statHolidays }}</div>
            <div class="stat-label">法定假日</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card stat-warning">
          <div class="stat-icon"><el-icon><WarnTriangleFilled /></el-icon></div>
          <div class="stat-body">
            <div class="stat-value">{{ statMakeup }}</div>
            <div class="stat-label">调休补班</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card stat-success">
          <div class="stat-icon"><el-icon><Sunny /></el-icon></div>
          <div class="stat-body">
            <div class="stat-value">{{ statUpcoming }}</div>
            <div class="stat-label">即将到来</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 操作栏 -->
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-button type="primary" :icon="Plus" @click="handleAdd">
            新增节假日
          </el-button>
          <el-button type="success" plain :icon="MagicStick" @click="showGenerateDialog">
            一键生成
          </el-button>
          <el-button type="warning" plain :icon="Upload" @click="showBatchDialog">
            批量导入
          </el-button>
        </div>
        <div class="toolbar-right">
          <el-radio-group v-model="filterType" size="small" @change="applyFilter">
            <el-radio-button label="ALL">全部</el-radio-button>
            <el-radio-button label="HOLIDAY">假日</el-radio-button>
            <el-radio-button label="MAKEUP">调休</el-radio-button>
          </el-radio-group>
          <el-date-picker
            v-model="query.year"
            type="year"
            placeholder="选择年份"
            value-format="YYYY"
            style="width: 100px; margin-left: 12px"
            @change="getList"
          />
        </div>
      </div>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="filteredData"
        stripe
        :row-class-name="rowClassName"
        style="width: 100%"
      >
        <el-table-column label="日期" width="140">
          <template #default="scope">
            <div class="date-cell">
              <span class="date-day">{{ formatDay(scope.row.date) }}</span>
              <span class="date-week">{{ formatWeekday(scope.row.date) }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="节假日名称" min-width="160">
          <template #default="scope">
            <div class="name-cell">
              <span class="name-text">{{ scope.row.name }}</span>
              <el-tag
                v-if="isUpcoming(scope.row.date)"
                size="small"
                effect="dark"
                type="success"
                class="upcoming-tag"
              >
                即将到达
              </el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="类型" width="120" align="center">
          <template #default="scope">
            <div class="type-badge" :class="getTypeClass(scope.row)">
              <el-icon v-if="scope.row.isMakeupWorkday" class="type-icon"><OfficeBuilding /></el-icon>
              <el-icon v-else-if="scope.row.isHoliday" class="type-icon"><StarFilled /></el-icon>
              <el-icon v-else class="type-icon"><Document /></el-icon>
              <span>{{ getTypeText(scope.row) }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="regionCode" label="地区" width="80" align="center">
          <template #default="scope">
            <el-tag size="small" effect="plain">{{ scope.row.regionCode }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="scope">
            <el-button link type="primary" :icon="Edit" @click="handleEdit(scope.row)">
              编辑
            </el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(scope.row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 空状态 -->
      <el-empty v-if="!loading && filteredData.length === 0" description="暂无节假日数据" />
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      :title="editMode ? '编辑节假日' : '新增节假日'"
      v-model="dialogVisible"
      width="520px"
      destroy-on-close
      class="holiday-dialog"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="日期" prop="date">
          <el-date-picker
            v-model="form.date"
            type="date"
            placeholder="选择日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：国庆节">
            <template #prefix>
              <el-icon><Flag /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.type">
            <el-radio-button label="PUBLIC_HOLIDAY">
              <el-icon><StarFilled /></el-icon> 法定节假日
            </el-radio-button>
            <el-radio-button label="MAKEUP_WORKDAY">
              <el-icon><OfficeBuilding /></el-icon> 调休补班
            </el-radio-button>
            <el-radio-button label="CUSTOM">
              <el-icon><Document /></el-icon> 自定义
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="地区">
          <el-input v-model="form.regionCode" placeholder="默认 CN">
            <template #prefix>
              <el-icon><Location /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="属性">
          <el-space>
            <el-switch
              v-model="form.isHoliday"
              :active-value="true"
              :inactive-value="false"
              active-text="节假日"
              inline-prompt
            />
            <el-switch
              v-model="form.isMakeupWorkday"
              :active-value="true"
              :inactive-value="false"
              active-text="调休"
              inline-prompt
            />
          </el-space>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 一键生成对话框 -->
    <el-dialog
      title="一键生成全年节假日"
      v-model="generateDialogVisible"
      width="480px"
      class="generate-dialog"
    >
      <div class="generate-body">
        <el-icon class="generate-icon" :size="48" color="#67c23a"><Calendar /></el-icon>
        <h3 class="generate-title">中国法定节假日模板</h3>
        <p class="generate-desc">
          内置 2024-2027 年法定假日数据（含调休补班），一键导入当前年份。
        </p>
        <el-form label-width="80px">
          <el-form-item label="选择年份">
            <el-select
              v-model="generateYear"
              placeholder="选择年份"
              style="width: 200px"
            >
              <el-option
                v-for="opt in yearOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>
        </el-form>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          style="margin-top: 16px"
        >
          已存在的日期会自动跳过，不会重复添加
        </el-alert>
      </div>
      <template #footer>
        <el-button @click="generateDialogVisible = false">取消</el-button>
        <el-button type="success" :loading="generating" @click="handleGenerate">
          立即导入
        </el-button>
      </template>
    </el-dialog>

    <!-- 批量导入对话框 -->
    <el-dialog
      title="批量导入节假日"
      v-model="batchDialogVisible"
      width="640px"
      class="batch-dialog"
    >
      <el-tabs v-model="batchActiveTab">
        <el-tab-pane label="粘贴数据" name="paste">
          <el-alert type="info" :closable="false" style="margin-bottom: 16px">
            <p>每行一条，格式：<code>日期,名称,类型,是否节假日,是否调休</code></p>
          </el-alert>
          <el-input
            v-model="batchText"
            type="textarea"
            :rows="12"
            placeholder="请粘贴CSV格式数据..."
            class="batch-textarea"
          />
          <div class="batch-example">
            <el-divider>示例数据</el-divider>
            <pre>2025-01-01,元旦,PUBLIC_HOLIDAY,true,false
2025-01-26,春节调休补班,MAKEUP_WORKDAY,false,true
2025-05-01,劳动节,PUBLIC_HOLIDAY,true,false</pre>
          </div>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button type="warning" :loading="batchLoading" @click="handleBatchImport">
          确认导入 {{ batchCount }} 条
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Edit, Delete, Calendar, Upload, MagicStick,
  Clock, WarnTriangleFilled, Sunny, StarFilled,
  OfficeBuilding, Document, Flag, Location,
} from '@element-plus/icons-vue'
import {
  getHolidays, createHoliday, updateHoliday, deleteHoliday,
  generateHolidays, batchCreateHolidays,
} from '@/api/pricing'

interface HolidayItem {
  id?: number
  date: string
  name: string
  type: string
  regionCode: string
  isHoliday: boolean
  isMakeupWorkday: boolean
}

interface HolidayImportResult {
  year?: number
  total?: number
  imported?: number
  skipped?: number
  message?: string
}

const loading = ref(false)
const tableData = ref<HolidayItem[]>([])
const query = reactive({ year: new Date().getFullYear() })
const filterType = ref('ALL')

// 筛选后的数据
const filteredData = computed(() => {
  if (filterType.value === 'ALL') return tableData.value
  if (filterType.value === 'HOLIDAY') return tableData.value.filter(r => r.isHoliday && !r.isMakeupWorkday)
  if (filterType.value === 'MAKEUP') return tableData.value.filter(r => r.isMakeupWorkday)
  return tableData.value
})

// 统计
const statTotal = computed(() => tableData.value.length)
const statHolidays = computed(() => tableData.value.filter(r => r.isHoliday && !r.isMakeupWorkday).length)
const statMakeup = computed(() => tableData.value.filter(r => r.isMakeupWorkday).length)
const statUpcoming = computed(() => {
  const today = new Date().toISOString().slice(0, 10)
  return tableData.value.filter(r => r.date >= today).length
})

// 新增/编辑
const dialogVisible = ref(false)
const editMode = ref(false)
const formRef = ref()
const form = reactive<any>({
  date: '',
  name: '',
  type: 'PUBLIC_HOLIDAY',
  regionCode: 'CN',
  isHoliday: true,
  isMakeupWorkday: false,
})
const currentId = ref<number | null>(null)

// 一键生成
const generateDialogVisible = ref(false)
const generateYear = ref(new Date().getFullYear())
const generating = ref(false)
const yearOptions = [2024, 2025, 2026, 2027].map(y => ({ value: y, label: `${y} 年` }))

// 批量导入
const batchDialogVisible = ref(false)
const batchActiveTab = ref('paste')
const batchText = ref('')
const batchLoading = ref(false)
const batchCount = computed(() => {
  if (!batchText.value.trim()) return 0
  return batchText.value.trim().split('\n').filter(l => l.trim() && !l.trim().startsWith('#')).length
})

const rules = {
  date: [{ required: true, message: '请选择日期', trigger: 'change' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
}

const getList = async () => {
  loading.value = true
  try {
    const holidays = await getHolidays({ year: Number(query.year) || undefined })
    tableData.value = Array.isArray(holidays) ? holidays : []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const applyFilter = () => {
  // 筛选仅在前端执行，无需重新请求
}

const formatDay = (dateStr: string) => {
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}月${d.getDate()}日`
}

const formatWeekday = (dateStr: string) => {
  const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  const d = new Date(dateStr)
  return weekdays[d.getDay()]
}

const isUpcoming = (dateStr: string) => {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const target = new Date(dateStr)
  const diff = (target.getTime() - today.getTime()) / (1000 * 60 * 60 * 24)
  return diff >= 0 && diff <= 30
}

const getTypeClass = (row: HolidayItem) => {
  if (row.isMakeupWorkday) return 'type-makeup'
  if (row.isHoliday) return 'type-holiday'
  return 'type-normal'
}

const getTypeText = (row: HolidayItem) => {
  if (row.isMakeupWorkday) return '调休补班'
  if (row.isHoliday) return '法定节假日'
  return '普通'
}

const rowClassName = ({ row }: { row: HolidayItem }) => {
  if (row.isMakeupWorkday) return 'row-makeup'
  if (row.isHoliday) return 'row-holiday'
  return ''
}

const handleAdd = () => {
  editMode.value = false
  currentId.value = null
  Object.assign(form, {
    date: '',
    name: '',
    type: 'PUBLIC_HOLIDAY',
    regionCode: 'CN',
    isHoliday: true,
    isMakeupWorkday: false,
  })
  dialogVisible.value = true
}

const handleEdit = (row: any) => {
  editMode.value = true
  currentId.value = row.id
  Object.assign(form, row)
  dialogVisible.value = true
}

const handleSubmit = async () => {
  await formRef.value.validate()
  try {
    if (editMode.value && currentId.value) {
      await updateHoliday(currentId.value, form)
      ElMessage.success('更新成功')
    } else {
      await createHoliday(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    getList()
  } catch (e: any) {
    ElMessage.error(e.response?.data || '操作失败')
  }
}

const handleDelete = (row: any) => {
  ElMessageBox.confirm(`确定删除 ${row.date}「${row.name}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteHoliday(row.id)
      ElMessage.success('删除成功')
      getList()
    })
    .catch(() => {})
}

// 一键生成
const showGenerateDialog = () => {
  generateYear.value = Number(query.year) || new Date().getFullYear()
  generateDialogVisible.value = true
}

const handleGenerate = async () => {
  generating.value = true
  try {
    const result = await generateHolidays(generateYear.value) as HolidayImportResult
    ElMessage.success(result.message || `成功导入 ${result.imported || 0} 条`)
    generateDialogVisible.value = false
    query.year = generateYear.value
    getList()
  } catch (e: any) {
    ElMessage.error(e.response?.data || '生成失败')
  } finally {
    generating.value = false
  }
}

// 批量导入
const showBatchDialog = () => {
  batchText.value = ''
  batchDialogVisible.value = true
}

const handleBatchImport = async () => {
  if (!batchText.value.trim()) {
    ElMessage.warning('请输入数据')
    return
  }

  const lines = batchText.value.trim().split('\n').filter(l => l.trim() && !l.trim().startsWith('#'))
  const holidays: any[] = []

  for (const line of lines) {
    const parts = line.split(',').map(s => s.trim())
    if (parts.length < 2) continue

    const [date, name, type = 'PUBLIC_HOLIDAY', isHoliday = 'true', isMakeup = 'false'] = parts
    holidays.push({
      date,
      name,
      type,
      regionCode: 'CN',
      isHoliday: isHoliday.toLowerCase() === 'true',
      isMakeupWorkday: isMakeup.toLowerCase() === 'true',
    })
  }

  if (holidays.length === 0) {
    ElMessage.warning('未解析到有效数据')
    return
  }

  batchLoading.value = true
  try {
    const result = await batchCreateHolidays(holidays) as HolidayImportResult
    ElMessage.success(result.message || `成功导入 ${result.imported || 0} 条`)
    batchDialogVisible.value = false
    getList()
  } catch (e: any) {
    ElMessage.error(e.response?.data || '导入失败')
  } finally {
    batchLoading.value = false
  }
}

onMounted(getList)
</script>

<style scoped>
.holiday-page {
  padding: 20px;
  background: #f5f7fa;
  min-height: 100vh;
}

/* 统计卡片 */
.stat-row {
  margin-bottom: 16px;
}

.stat-card {
  display: flex;
  align-items: center;
  padding: 16px 20px;
  border-radius: 12px;
  color: #fff;
  transition: transform 0.2s, box-shadow 0.2s;
}
.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 16px rgba(0,0,0,0.1);
}
.stat-primary { background: linear-gradient(135deg, var(--el-color-primary), #66b1ff); }
.stat-danger  { background: linear-gradient(135deg, #f56c6c, #f89898); }
.stat-warning { background: linear-gradient(135deg, #e6a23c, #eebe77); }
.stat-success { background: linear-gradient(135deg, #67c23a, #95d475); }

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: rgba(255,255,255,0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  margin-right: 16px;
}
.stat-body {
  flex: 1;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
}
.stat-label {
  font-size: 13px;
  opacity: 0.9;
  margin-top: 4px;
}

/* 工具栏 */
.toolbar-card {
  margin-bottom: 16px;
  border-radius: 12px;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
.toolbar-left {
  display: flex;
  gap: 8px;
}
.toolbar-right {
  display: flex;
  align-items: center;
}

/* 表格卡片 */
.table-card {
  border-radius: 12px;
}

/* 表格样式 */
:deep(.el-table) {
  --el-table-row-hover-bg-color: #f5f7fa;
}
:deep(.row-holiday) {
  background-color: #fff5f5 !important;
}
:deep(.row-holiday:hover > td) {
  background-color: #ffecec !important;
}
:deep(.row-makeup) {
  background-color: #fffaf0 !important;
}
:deep(.row-makeup:hover > td) {
  background-color: #fff3e0 !important;
}

/* 日期单元格 */
.date-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
.date-day {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.date-week {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

/* 名称单元格 */
.name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.name-text {
  font-weight: 500;
}
.upcoming-tag {
  font-size: 11px;
  padding: 0 6px;
  height: 20px;
}

/* 类型徽章 */
.type-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
}
.type-holiday {
  background: #fde2e2;
  color: #c45656;
}
.type-makeup {
  background: #faecd8;
  color: #a16207;
}
.type-normal {
  background: #e9e9eb;
  color: #606266;
}
.type-icon {
  font-size: 14px;
}

/* 对话框 */
.holiday-dialog :deep(.el-radio-button__inner) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.generate-body {
  text-align: center;
  padding: 20px 0;
}
.generate-icon {
  margin-bottom: 16px;
}
.generate-title {
  margin: 0 0 8px;
  font-size: 18px;
  color: #303133;
}
.generate-desc {
  margin: 0 0 24px;
  font-size: 14px;
  color: #606266;
}

.batch-textarea :deep(.el-textarea__inner) {
  font-family: 'Courier New', monospace;
  font-size: 13px;
}
.batch-example pre {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 8px;
  font-size: 12px;
  color: #606266;
  overflow-x: auto;
}

/* 空状态 */
:deep(.el-empty) {
  padding: 60px 0;
}
</style>
