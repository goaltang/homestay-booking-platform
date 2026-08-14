<template>
  <div class="coupons-container">
    <div class="page-header">
      <h1>我的优惠券</h1>
    </div>

    <el-radio-group v-model="activeStatus" @change="handleStatusChange" class="status-filter">
      <el-radio-button label="ALL">全部</el-radio-button>
      <el-radio-button label="AVAILABLE">可用</el-radio-button>
      <el-radio-button label="USED">已使用</el-radio-button>
      <el-radio-button label="EXPIRED">已过期</el-radio-button>
    </el-radio-group>

    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="3" animated v-for="i in 3" :key="i" />
    </div>

    <div v-else-if="coupons.length === 0" class="empty-result">
      <el-empty :description="emptyText">
        <el-button type="primary" @click="$router.push('/homestays')">去逛逛</el-button>
      </el-empty>
    </div>

    <div v-else class="coupon-list">
      <div v-for="coupon in coupons" :key="coupon.id" class="coupon-card"
        :class="{ 'coupon-used': activeStatus === 'USED', 'coupon-expired': activeStatus === 'EXPIRED' }">
        <div class="coupon-main">
          <div class="coupon-value">
            <template v-if="coupon.couponType === 'PERCENTAGE'">
              <span class="value-number">{{ formatDiscountRate(coupon.discountRate) }}</span>
              <span class="value-unit">折</span>
            </template>
            <template v-else>
              <span class="value-unit">¥</span>
              <span class="value-number">{{ coupon.faceValue }}</span>
            </template>
          </div>
          <div class="coupon-info">
            <div class="coupon-name">{{ coupon.name }}</div>
            <div class="coupon-threshold" v-if="coupon.thresholdAmount && Number(coupon.thresholdAmount) > 0">
              满 {{ coupon.thresholdAmount }} 元可用
            </div>
            <div class="coupon-threshold" v-else>无门槛</div>
            <div class="coupon-scope" v-if="coupon.scopeType">
              {{ formatScopeType(coupon.scopeType) }}
            </div>
            <div class="coupon-expire">
              有效期至：{{ formatDate(coupon.expireAt) }}
            </div>
          </div>
        </div>
        <div class="coupon-status">
          <el-tag v-if="activeStatus === 'AVAILABLE' || isAvailable(coupon)" type="success" size="small">可用</el-tag>
          <el-tag v-else-if="activeStatus === 'USED'" type="info" size="small">已使用</el-tag>
          <el-tag v-else type="danger" size="small">已过期</el-tag>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getMyCoupons } from '@/api/coupon'
import dayjs from 'dayjs'

interface CouponItem {
  id: number
  name: string
  couponType: string
  faceValue: number
  discountRate: number
  thresholdAmount: number
  maxDiscount: number
  expireAt: string
  scopeType: string
  subsidyBearer: string
}

const loading = ref(false)
const coupons = ref<CouponItem[]>([])
const activeStatus = ref<'ALL' | 'AVAILABLE' | 'USED' | 'EXPIRED'>('ALL')

const emptyText = computed(() => {
  const map: Record<string, string> = {
    ALL: '您还没有优惠券',
    AVAILABLE: '暂无可用优惠券',
    USED: '暂无已使用优惠券',
    EXPIRED: '暂无已过期优惠券'
  }
  return map[activeStatus.value] || '暂无优惠券'
})

const fetchCoupons = async () => {
  loading.value = true
  try {
    const response = await getMyCoupons(activeStatus.value)
    if (Array.isArray(response.data)) {
      coupons.value = response.data
    } else {
      coupons.value = []
    }
  } catch (error) {
    console.error('获取优惠券失败:', error)
    coupons.value = []
  } finally {
    loading.value = false
  }
}

const handleStatusChange = () => {
  fetchCoupons()
}

const formatDate = (date: string) => {
  if (!date) return '-'
  return dayjs(date).format('YYYY-MM-DD HH:mm')
}

const formatDiscountRate = (rate: number) => {
  if (!rate) return '-'
  // 0.85 -> 8.5
  return (Number(rate) * 10).toFixed(1)
}

const formatScopeType = (scope: string) => {
  const map: Record<string, string> = {
    ALL: '全平台通用',
    SPECIFIC_HOMESTAY: '指定房源',
    SPECIFIC_HOST: '指定房东',
    CATEGORY: '指定分类'
  }
  return map[scope] || scope
}

const isAvailable = (coupon: CouponItem) => {
  return dayjs(coupon.expireAt).isAfter(dayjs())
}

onMounted(() => {
  fetchCoupons()
})
</script>

<style scoped>
.coupons-container {
  max-width: 900px;
  margin: 0 auto;
  padding: 20px;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h1 {
  font-size: 24px;
  font-weight: 600;
  margin: 0;
}

.status-filter {
  margin-bottom: 24px;
}

.loading-container {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.empty-result {
  padding: 48px 0;
  text-align: center;
}

.coupon-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.coupon-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  background: linear-gradient(135deg, #fff5f5 0%, #fff 100%);
  border: 1px solid #ffdede;
  border-radius: 12px;
  transition: all 0.2s ease;
}

.coupon-card:hover {
  box-shadow: 0 4px 12px rgba(255, 56, 92, 0.08);
  transform: translateY(-2px);
}

.coupon-used {
  background: #f5f7fa;
  border-color: #e4e7ed;
  opacity: 0.85;
}

.coupon-expired {
  background: #f5f5f5;
  border-color: #dcdcdc;
  opacity: 0.7;
}

.coupon-main {
  display: flex;
  align-items: center;
  gap: 20px;
  flex: 1;
}

.coupon-value {
  display: flex;
  align-items: baseline;
  color: #ff385c;
  min-width: 100px;
  justify-content: center;
}

.coupon-used .coupon-value,
.coupon-expired .coupon-value {
  color: #909399;
}

.value-number {
  font-size: 36px;
  font-weight: 700;
  line-height: 1;
}

.value-unit {
  font-size: 16px;
  font-weight: 600;
  margin-left: 2px;
}

.coupon-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.coupon-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.coupon-threshold {
  font-size: 13px;
  color: #ff385c;
  font-weight: 500;
}

.coupon-used .coupon-threshold,
.coupon-expired .coupon-threshold {
  color: #909399;
}

.coupon-scope {
  font-size: 12px;
  color: #909399;
}

.coupon-expire {
  font-size: 12px;
  color: #c0c4cc;
}

.coupon-status {
  margin-left: 16px;
}

@media (max-width: 768px) {
  .coupon-card {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .coupon-main {
    width: 100%;
  }

  .coupon-status {
    margin-left: 0;
    align-self: flex-end;
  }
}
</style>
