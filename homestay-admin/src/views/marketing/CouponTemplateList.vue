<template>
  <div class="coupon-list-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>优惠券模板管理</span>
          <el-button type="primary" @click="handleAddClean">创建模板</el-button>
        </div>
      </template>

      <!-- 搜索筛选 -->
      <el-form :model="query" inline class="search-form">
        <el-form-item label="名称">
          <el-input v-model="query.name" placeholder="模板名称" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable>
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已激活" value="ACTIVE" />
            <el-option label="已暂停" value="PAUSED" />
            <el-option label="已过期" value="EXPIRED" />
          </el-select>
        </el-form-item>
        <el-form-item label="承担方">
          <el-select v-model="query.subsidyBearer" placeholder="全部" clearable>
            <el-option label="平台" value="PLATFORM" />
            <el-option label="房东" value="HOST" />
            <el-option label="混合" value="MIXED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="clearSearch">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="模板名称" min-width="150" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag>{{ formatType(row.couponType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="承担方" width="100">
          <template #default="{ row }">
            <el-tag :type="row.subsidyBearer === 'HOST' ? 'warning' : 'info'">
              {{ formatBearer(row.subsidyBearer) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="新人券" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.isNewUserCoupon" type="success">是</el-tag>
            <span v-else class="text-gray">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="faceValue" label="面值" width="100">
          <template #default="{ row }">
            {{ row.faceValue ? '¥' + row.faceValue : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="discountRate" label="折扣率" width="90">
          <template #default="{ row }">
            {{ row.discountRate ? (row.discountRate * 10) + '折' : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="thresholdAmount" label="使用门槛" width="120">
          <template #default="{ row }">
            {{ row.thresholdAmount ? '满¥' + row.thresholdAmount : '无门槛' }}
          </template>
        </el-table-column>
        <el-table-column prop="totalStock" label="库存" width="100">
          <template #default="{ row }">
            {{ row.issuedCount || 0 }} / {{ row.totalStock || '∞' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="formatStatusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleEditWithDate(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pageUI"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        layout="total, prev, pager, next"
        @current-change="handlePageChange"
        class="pagination"
      />
    </el-card>

    <!-- 创建/编辑模板对话框 -->
    <el-dialog v-model="dialogVisible" :title="editMode ? '编辑模板' : '创建优惠券模板'" width="600px">
      <el-form ref="formRef" :model="form" label-width="120px">
        <el-form-item label="模板名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="券类型">
          <el-select v-model="form.couponType">
            <el-option label="现金券" value="CASH" />
            <el-option label="折扣券" value="DISCOUNT" />
            <el-option label="满减券" value="FULL_REDUCTION" />
          </el-select>
        </el-form-item>
        <el-form-item label="承担方">
          <el-select v-model="form.subsidyBearer">
            <el-option label="平台" value="PLATFORM" />
            <el-option label="房东" value="HOST" />
            <el-option label="混合" value="MIXED" />
          </el-select>
        </el-form-item>
        <el-form-item label="互斥组">
          <el-input v-model="form.stackGroup" placeholder="DEFAULT 表示可与任何券叠加" />
          <span class="form-tip">同组券不可叠加，DEFAULT 可与所有券叠加</span>
        </el-form-item>
        <el-form-item label="自动发放触发">
          <el-select v-model="form.autoIssueTrigger">
            <el-option label="不自动发放" value="NONE" />
            <el-option label="注册时发放" value="REGISTER" />
            <el-option label="首单完成后发放" value="FIRST_ORDER" />
            <el-option label="订单完成后发放" value="ORDER_COMPLETED" />
          </el-select>
        </el-form-item>
        <el-form-item label="新人专属">
          <el-switch v-model="form.isNewUserCoupon" active-text="注册自动发放" />
        </el-form-item>
        <el-form-item v-if="form.couponType !== 'DISCOUNT'" label="面值">
          <el-input-number v-model="form.faceValue" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item v-if="form.couponType === 'DISCOUNT'" label="折扣率(0-1)">
          <el-input-number v-model="form.discountRate" :min="0.01" :max="0.99" :precision="2" />
        </el-form-item>
        <el-form-item label="使用门槛">
          <el-input-number v-model="form.thresholdAmount" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="最大优惠金额">
          <el-input-number v-model="form.maxDiscount" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="总库存">
          <el-input-number v-model="form.totalStock" :min="0" :precision="0" />
        </el-form-item>
        <el-form-item label="每人限领">
          <el-input-number v-model="form.perUserLimit" :min="1" :precision="0" />
        </el-form-item>
        <el-form-item label="有效期">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
          />
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
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useCrud } from '@/composables/useCrud'
import {
  getCouponTemplates, createCouponTemplate,
  updateCouponTemplate, deleteCouponTemplate,
} from '@/api/marketing'

const submitLoading = ref(false)
const dateRange = ref<[Date, Date] | null>(null)

const formatLocalDateTime = (date: Date) => {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

// 包装列表 API：前端 page 从 1 开始，后端从 0 开始
const wrappedListApi = async (params: any) => {
  const res: any = await getCouponTemplates({
    page: params.page,
    size: params.size,
    name: params.name,
    status: params.status,
    subsidyBearer: params.subsidyBearer,
  })
  const data = res.data || res
  return {
    content: data.content || data || [],
    totalElements: data.totalElements || data.total || 0,
  }
}

// 包装提交 API：把 dateRange 转成 ISO 字符串
const wrapPayload = (formData: any) => {
  const payload = { ...formData }
  if (dateRange.value && dateRange.value.length === 2) {
    payload.validStartAt = formatLocalDateTime(dateRange.value[0])
    payload.validEndAt = formatLocalDateTime(dateRange.value[1])
  }
  return payload
}

const wrappedCreateApi = async (data: any) => {
  return createCouponTemplate(wrapPayload(data))
}

const wrappedUpdateApi = async (id: number | string, data: any) => {
  return updateCouponTemplate(Number(id), wrapPayload(data))
}

const {
  loading, tableData, query, pagination,
  dialogVisible, editMode, formRef, form,
  getList, handleAdd, handleDelete, handlePageChange,
} = useCrud({
  listApi: wrappedListApi,
  createApi: wrappedCreateApi,
  updateApi: wrappedUpdateApi,
  deleteApi: deleteCouponTemplate,
  defaultQuery: { name: '', status: '', subsidyBearer: '' },
  defaultForm: {
    name: '',
    couponType: 'CASH',
    subsidyBearer: 'PLATFORM',
    stackGroup: 'DEFAULT',
    autoIssueTrigger: 'NONE',
    isNewUserCoupon: false,
    faceValue: 0,
    discountRate: 0,
    thresholdAmount: 0,
    maxDiscount: 0,
    totalStock: 100,
    perUserLimit: 1,
    validType: 'FIXED_TIME',
    status: 'ACTIVE',
    validStartAt: '',
    validEndAt: '',
  },
})

const validateCouponForm = () => {
  if (!form.name?.trim()) {
    ElMessage.warning('请填写模板名称')
    return false
  }
  if (form.couponType === 'DISCOUNT') {
    const rate = Number(form.discountRate)
    if (!rate || rate <= 0 || rate >= 1) {
      ElMessage.warning('折扣率必须大于 0 且小于 1')
      return false
    }
  } else if (!Number(form.faceValue) || Number(form.faceValue) <= 0) {
    ElMessage.warning('面值必须大于 0')
    return false
  }
  if (form.couponType === 'FULL_REDUCTION' && (!Number(form.thresholdAmount) || Number(form.thresholdAmount) <= 0)) {
    ElMessage.warning('满减券必须设置大于 0 的使用门槛')
    return false
  }
  if (form.validType !== 'AFTER_CLAIM_DAYS' && (!dateRange.value || dateRange.value.length !== 2)) {
    ElMessage.warning('请选择有效期')
    return false
  }
  return true
}

// UI 分页显示（+1）
const pageUI = computed({
  get: () => pagination.page + 1,
  set: (val: number) => { pagination.page = val - 1 },
})

// 搜索
const handleSearch = () => {
  pagination.page = 0
  getList()
}

const clearSearch = () => {
  query.name = ''
  query.status = ''
  query.subsidyBearer = ''
  pagination.page = 0
  getList()
}

// 覆盖 handleAdd，清空日期范围
const _rawAdd = handleAdd
const handleAddClean = () => {
  _rawAdd()
  delete (form as any).id
  dateRange.value = null
}

// 覆盖 handleEdit，同步日期范围（编辑逻辑见 handleEditWithDate）
const handleEditWithDate = (row: any) => {
  // 手动处理编辑
  editMode.value = true
  Object.assign(form, {
    id: row.id,
    name: row.name,
    couponType: row.couponType,
    subsidyBearer: row.subsidyBearer || 'PLATFORM',
    stackGroup: row.stackGroup || 'DEFAULT',
    autoIssueTrigger: row.autoIssueTrigger || 'NONE',
    isNewUserCoupon: row.isNewUserCoupon || false,
    faceValue: row.faceValue || 0,
    discountRate: row.discountRate || 0,
    thresholdAmount: row.thresholdAmount || 0,
    maxDiscount: row.maxDiscount || 0,
    totalStock: row.totalStock || 100,
    perUserLimit: row.perUserLimit || 1,
    validType: row.validType || 'FIXED_TIME',
    status: row.status || 'ACTIVE',
  })
  if (row.validStartAt && row.validEndAt) {
    dateRange.value = [new Date(row.validStartAt), new Date(row.validEndAt)]
  } else {
    dateRange.value = null
  }
  dialogVisible.value = true
}

// 覆盖 handleSubmit，增加 submitLoading 和日期范围处理
const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid: boolean) => {
    if (!valid) return
    if (!validateCouponForm()) return
    submitLoading.value = true
    try {
      if (editMode.value && (form as any).id) {
        await wrappedUpdateApi((form as any).id, form)
        ElMessage.success('更新成功')
      } else {
        await wrappedCreateApi(form)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      dateRange.value = null
      await getList()
    } catch (e: any) {
      ElMessage.error(e?.response?.data?.error || e?.response?.data || '操作失败')
    } finally {
      submitLoading.value = false
    }
  })
}

// 格式化函数
const formatType = (type: string) => {
  const map: Record<string, string> = { CASH: '现金券', DISCOUNT: '折扣券', FULL_REDUCTION: '满减券' }
  return map[type] || type
}

const formatBearer = (bearer: string) => {
  const map: Record<string, string> = { PLATFORM: '平台', HOST: '房东', MIXED: '混合' }
  return map[bearer] || bearer
}

const formatStatusType = (status: string) => {
  const map: Record<string, any> = { ACTIVE: 'success', PAUSED: 'warning', DRAFT: 'info', EXPIRED: 'danger' }
  return map[status] || 'info'
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.search-form {
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #eee;
}
.pagination {
  margin-top: 20px;
  text-align: right;
}
</style>
