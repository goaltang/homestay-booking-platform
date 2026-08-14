<template>
  <div class="my-invite-container">
    <h2 class="page-title">我的邀请</h2>

    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :xs="24" :sm="8">
        <el-card class="stat-card" v-loading="loading">
          <div class="stat-value">{{ stats.totalCodes || 0 }}</div>
          <div class="stat-label">我的邀请码</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card class="stat-card" v-loading="loading">
          <div class="stat-value">{{ stats.totalUsed || 0 }}</div>
          <div class="stat-label">已被使用</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card class="stat-card" v-loading="loading">
          <div class="stat-value">{{ stats.activeCodes || 0 }}</div>
          <div class="stat-label">活跃中</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 我的邀请码列表 -->
    <el-card class="codes-card" v-loading="loading">
      <template #header>
        <div class="card-header">
          <span>我的邀请码</span>
        </div>
      </template>

      <div v-if="myCodes.length === 0" class="empty-text">
        您还没有邀请码，联系客服获取或参与活动领取。
      </div>

      <div v-else class="code-list">
        <div v-for="code in myCodes" :key="code.id" class="code-item">
          <div class="code-main">
            <div class="code-value">{{ code.referralCode }}</div>
            <div class="code-meta">
              <el-tag :type="code.status === 'ACTIVE' ? 'success' : 'info'" size="small">
                {{ code.status === 'ACTIVE' ? '有效' : '已用完' }}
              </el-tag>
              <span class="meta-text">有效期至 {{ formatDate(code.expireAt) }}</span>
              <span class="meta-text">已用 {{ code.usedCount }}/{{ code.maxUses }}</span>
            </div>
          </div>
          <div class="code-actions">
            <el-button type="primary" link size="small" @click="copyCode(code.referralCode)">
              <el-icon><CopyDocument /></el-icon> 复制
            </el-button>
          </div>
        </div>
      </div>

      <!-- 分享链接 -->
      <div v-if="myCodes.length > 0" class="share-section">
        <div class="share-label">快速分享链接</div>
        <div class="share-input-group">
          <el-input v-model="shareLink" readonly class="share-input">
            <template #append>
              <el-button @click="copyLink">
                <el-icon><CopyDocument /></el-icon> 复制链接
              </el-button>
            </template>
          </el-input>
        </div>
        <p class="share-tip">将链接分享给好友，好友注册后双方均可获得奖励。</p>
      </div>
    </el-card>

    <!-- 使用邀请码 -->
    <el-card class="action-card">
      <template #header>
        <span>使用邀请码</span>
      </template>
      <p class="tip-text">如果你有朋友分享的邀请码，可以在此输入领取奖励。</p>
      <el-input v-model="referralCode" placeholder="请输入邀请码" clearable style="width: 280px" />
      <el-button type="primary" @click="claimReferral" :loading="claiming" style="margin-left: 12px">领取奖励</el-button>
    </el-card>

    <!-- 邀请说明 -->
    <el-card class="info-card">
      <template #header>
        <span>邀请奖励说明</span>
      </template>
      <ul class="info-list">
        <li>邀请好友注册，双方均可获得优惠券奖励。</li>
        <li>每个邀请码有使用次数上限，请在有效期内使用。</li>
        <li>奖励优惠券将自动发放到您的账户中，可在下单时使用。</li>
      </ul>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument } from '@element-plus/icons-vue'
import api from '@/api'
import dayjs from 'dayjs'

interface ReferralCodeItem {
  id: number
  referralCode: string
  status: string
  usedCount: number
  maxUses: number
  expireAt: string
  createdAt: string
}

const stats = ref<any>({})
const myCodes = ref<ReferralCodeItem[]>([])
const referralCode = ref('')
const claiming = ref(false)
const loading = ref(false)

const shareLink = computed(() => {
  const base = window.location.origin
  const code = myCodes.value.find(c => c.status === 'ACTIVE')?.referralCode
  return code ? `${base}/register?ref=${code}` : base + '/register'
})

const fetchData = async () => {
  loading.value = true
  try {
    const [statsRes, codesRes] = await Promise.all([
      api.get('/api/coupons/referral/stats'),
      api.get('/api/coupons/referral/my-codes')
    ])
    stats.value = statsRes.data || {}
    myCodes.value = Array.isArray(codesRes.data) ? codesRes.data : []
  } catch (e) {
    console.error('获取邀请数据失败', e)
  } finally {
    loading.value = false
  }
}

const claimReferral = async () => {
  if (!referralCode.value.trim()) {
    ElMessage.warning('请输入邀请码')
    return
  }
  claiming.value = true
  try {
    const res = await api.post(`/api/coupons/referral/${referralCode.value.trim()}/claim`)
    ElMessage.success(res.data?.message || '领取成功')
    referralCode.value = ''
    fetchData()
  } catch (e: any) {
  } finally {
    claiming.value = false
  }
}

const formatDate = (date: string) => {
  if (!date) return '-'
  return dayjs(date).format('YYYY-MM-DD')
}

const copyCode = (code: string) => {
  navigator.clipboard.writeText(code).then(() => {
    ElMessage.success('邀请码已复制')
  }).catch(() => {
  })
}

const copyLink = () => {
  navigator.clipboard.writeText(shareLink.value).then(() => {
    ElMessage.success('分享链接已复制')
  }).catch(() => {
  })
}

onMounted(fetchData)
</script>

<style scoped>
.my-invite-container {
  max-width: 900px;
  margin: 0 auto;
  padding: 20px;
}
.page-title {
  font-size: 24px;
  font-weight: 700;
  margin-bottom: 20px;
  color: #303133;
}
.stats-row {
  margin-bottom: 20px;
}
.stat-card {
  text-align: center;
}
.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: var(--el-color-primary);
  margin-bottom: 8px;
}
.stat-label {
  font-size: 14px;
  color: #606266;
}
.codes-card {
  margin-bottom: 20px;
}
.card-header {
  font-weight: 600;
}
.empty-text {
  color: #909399;
  text-align: center;
  padding: 24px 0;
}
.code-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.code-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background: #f9fafb;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
}
.code-main {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.code-value {
  font-size: 18px;
  font-weight: 700;
  color: #303133;
  font-family: monospace;
  letter-spacing: 1px;
}
.code-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.meta-text {
  font-size: 13px;
  color: #909399;
}
.code-actions {
  flex-shrink: 0;
}
.share-section {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px dashed #dcdfe6;
}
.share-label {
  font-size: 14px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 12px;
}
.share-input-group {
  display: flex;
  max-width: 480px;
}
.share-input :deep(.el-input__wrapper) {
  background-color: #f5f7fa;
}
.share-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
  margin-bottom: 0;
}
.action-card {
  margin-bottom: 20px;
}
.tip-text {
  color: #606266;
  margin-bottom: 16px;
  font-size: 14px;
}
.info-card {
  margin-bottom: 20px;
}
.info-list {
  padding-left: 20px;
  color: #606266;
  line-height: 2;
  font-size: 14px;
}
@media (max-width: 768px) {
  .code-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  .share-input-group {
    max-width: 100%;
  }
}
</style>
