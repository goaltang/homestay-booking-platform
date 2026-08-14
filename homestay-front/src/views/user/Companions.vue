<template>
  <div class="companions-container">
    <div class="page-header">
      <h1>常用入住人</h1>
      <p class="page-desc">管理您的常用入住人信息，预订时可直接选用，无需重复填写。</p>
    </div>

    <el-card v-loading="loading || saving">
      <template #header>
        <div class="card-header">
          <span>入住人列表</span>
          <el-button type="primary" size="small" @click="showAddDialog">
            <el-icon><Plus /></el-icon> 添加入住人
          </el-button>
        </div>
      </template>

      <div v-if="companions.length === 0" class="empty-text">
        暂无常用入住人，点击右上角添加。
      </div>

      <div v-else class="companion-list">
        <div v-for="(companion, index) in companions" :key="index" class="companion-item">
          <div class="companion-info">
            <div class="companion-name">{{ companion.name }}</div>
            <div class="companion-detail">
              <span v-if="companion.phone">{{ maskPhone(companion.phone) }}</span>
              <span v-if="companion.idCard">{{ maskIdCard(companion.idCard) }}</span>
            </div>
          </div>
          <div class="companion-actions">
            <el-button type="primary" link size="small" @click="editCompanion(index)">编辑</el-button>
            <el-button type="danger" link size="small" @click="removeCompanion(index)">删除</el-button>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 添加/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑入住人' : '添加入住人'" width="420px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="真实姓名" maxlength="20" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="11位手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="身份证号" prop="idCard">
          <el-input v-model="form.idCard" placeholder="18位身份证号" maxlength="18" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

interface Companion {
  name: string
  phone?: string
  idCard?: string
}

const userStore = useUserStore()
const saving = ref(false)
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const editIndex = ref(-1)
const formRef = ref()

const form = ref<Companion>({ name: '', phone: '', idCard: '' })

const rules = {
  name: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { min: 2, max: 20, message: '姓名长度2-20个字符', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  idCard: [
    { pattern: /(^\d{15}$)|(^\d{18}$)|(^\d{17}(\d|X|x)$)/, message: '请输入正确的身份证号', trigger: 'blur' }
  ]
}

const companions = ref<Companion[]>([])

const parseCompanions = () => {
  const raw = userStore.userInfo?.frequentGuests
  if (raw) {
    try {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed)) {
        companions.value = parsed
        return
      }
    } catch (e) {
      console.error('解析常用入住人失败', e)
    }
  }
  companions.value = []
}

const serializeCompanions = () => {
  return companions.value.length > 0 ? JSON.stringify(companions.value) : ''
}

const maskPhone = (phone: string) => {
  if (!phone || phone.length !== 11) return phone
  return phone.substring(0, 3) + '****' + phone.substring(7)
}

const maskIdCard = (id: string) => {
  if (!id || id.length < 8) return id
  return id.substring(0, 4) + '********' + id.substring(id.length - 4)
}

const showAddDialog = () => {
  isEdit.value = false
  editIndex.value = -1
  form.value = { name: '', phone: '', idCard: '' }
  dialogVisible.value = true
}

const editCompanion = (index: number) => {
  isEdit.value = true
  editIndex.value = index
  form.value = { ...companions.value[index] }
  dialogVisible.value = true
}

const removeCompanion = async (index: number) => {
  companions.value.splice(index, 1)
  await saveToServer()
}

const submitForm = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    if (isEdit.value && editIndex.value >= 0) {
      companions.value[editIndex.value] = { ...form.value }
    } else {
      companions.value.push({ ...form.value })
    }
    await saveToServer()
    dialogVisible.value = false
  } catch (e) {
    // validation error
  }
}

const saveToServer = async () => {
  saving.value = true
  try {
    await userStore.updateProfile({
      username: userStore.userInfo?.username || '',
      email: userStore.userInfo?.email || '',
      frequentGuests: serializeCompanions()
    })
    ElMessage.success('保存成功')
  } catch (e: any) {
    console.error('保存常用入住人失败', e)
  } finally {
    saving.value = false
  }
}

watch(() => userStore.userInfo?.frequentGuests, () => {
  parseCompanions()
}, { immediate: true })

onMounted(() => {
  parseCompanions()
})
</script>

<style scoped>
.companions-container {
  max-width: 900px;
  margin: 0 auto;
  padding: 20px;
}
.page-header {
  margin-bottom: 24px;
}
.page-header h1 {
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 8px;
  color: #303133;
}
.page-desc {
  font-size: 14px;
  color: #909399;
  margin: 0;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}
.empty-text {
  color: #909399;
  text-align: center;
  padding: 32px 0;
}
.companion-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.companion-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background: #f9fafb;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
}
.companion-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.companion-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.companion-detail {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #909399;
}
.companion-actions {
  flex-shrink: 0;
}
@media (max-width: 768px) {
  .companion-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  .companion-actions {
    align-self: flex-end;
  }
}
</style>
