<template>
    <div class="booking-card">
        <div class="booking-card-header">
            <div class="price-info">
                <span class="price">¥{{ pricePerNight }}</span>
                <span class="night">/晚</span>
            </div>
            <div class="rating-info">
                <el-rate v-model="numericRating" disabled text-color="#FF9900" disabled-void-color="#C6D1DE" />
                <span class="review-link">{{ reviewCount }}条评价</span>
            </div>
        </div>

        <div class="booking-form">
            <div class="date-picker-container">
                <div class="date-picker-header">
                    <div class="check-in-label">入住</div>
                    <div class="check-out-label">退房</div>
                </div>
                <el-date-picker v-model="localDateRange" type="daterange" range-separator="至" start-placeholder="选择日期"
                    end-placeholder="选择日期" format="YYYY/MM/DD" :disabled-date="disablePastDates"
                    @change="handleDateRangeChange" @calendar-change="handleCalendarChange"
                    @visible-change="handleVisibleChange" :size="'large'"
                    class="date-range-picker" :loading="loadingDates" popper-class="booking-date-popper" />
            </div>

            <div class="guest-selector-container">
                <div class="guest-selector-label">房客</div>
                <el-select v-model="localGuests" placeholder="1位房客" :size="'large'" class="guest-dropdown">
                    <el-option v-for="i in maxGuests" :key="i" :label="`${i}位房客`" :value="i" />
                </el-select>
            </div>

            <!-- 日期可用性提示 -->
            <div class="date-availability-hint" v-if="!loadingDates">
                <el-icon>
                    <InfoFilled />
                </el-icon>
                <span v-if="unavailableDates.length > 0">
                    部分日期已被预订，置灰日期无法选择
                </span>
                <span v-else>
                    当前房源暂无已被预订的日期
                </span>
            </div>

            <!-- 最长可选区间提示 -->
            <div class="max-stay-hint" v-if="confirmedCheckIn && maxCheckOutDate && !loadingDates">
                <el-icon><Clock /></el-icon>
                <span>
                    从 {{ formatDateToString(confirmedCheckIn) }} 起最多可住至 {{ formatDateToString(maxCheckOutDate) }}
                    <span class="nights-count">（最多 {{ availableMaxNights }} 晚）</span>
                </span>
            </div>

            <!-- 最低晚数要求提示 -->
            <div class="min-stay-hint" v-if="minNights > 1">
                <el-icon><InfoFilled /></el-icon>
                <span>此房源最少需入住 <strong>{{ minNights }} 晚</strong></span>
            </div>

            <!-- 最高晚数限制提示 -->
            <div class="max-stay-limit-hint" v-if="maxNights > 0">
                <el-icon><InfoFilled /></el-icon>
                <span>此房源最多可入住 <strong>{{ maxNights }} 晚</strong></span>
            </div>

            <!-- 自动修正内联提示 -->
            <div class="auto-fix-hint" v-if="autoFixMessage">
                <el-icon><WarningFilled /></el-icon>
                <span>{{ autoFixMessage }}</span>
            </div>
        </div>

        <!-- 自动确认模式提示 -->
        <div class="booking-mode-notice" v-if="autoConfirm">
            <el-icon class="auto-confirm-icon" :size="16">
                <Lightning />
            </el-icon>
            <span>即时预订 - 确认后立即生效</span>
        </div>

        <el-button type="primary" class="booking-button" @click="handleBooking"
            :disabled="!localDateRange || !localDateRange[0] || !localDateRange[1]" :loading="isVerifying">
            {{ isVerifying ? '验证中...' : (autoConfirm ? '立即预订' : '申请预订') }}
        </el-button>

        <div class="price-breakdown" v-if="totalNights > 0">
            <div v-if="isCalculating" class="price-loading-placeholder" style="margin: 20px 0;">
                <el-skeleton animated :rows="3" />
            </div>
            <template v-else-if="calculatedFees">
                <div class="price-row">
                    <div class="price-item">¥{{ pricePerNight }} x {{ calculatedFees.nights }}晚</div>
                    <div class="price-value">¥{{ calculatedFees.basePrice }}</div>
                </div>

                <!-- 日期级调价明细 -->
                <template v-if="pricingRuleBreakdown.length > 0">
                    <div class="price-row discount" v-for="(item, idx) in pricingRuleBreakdown" :key="'rule-'+idx">
                        <div class="price-item">{{ item.label }}</div>
                        <div class="price-value">{{ item.amount >= 0 ? '+' : '' }}¥{{ Math.abs(item.amount).toFixed(2) }}</div>
                    </div>
                </template>

                <div class="price-row discount" v-if="calculatedFees.activityDiscountAmount > 0">
                    <div class="price-item">活动优惠</div>
                    <div class="price-value">-¥{{ calculatedFees.activityDiscountAmount }}</div>
                </div>
                <div class="price-row discount" v-if="calculatedFees.couponDiscountAmount > 0">
                    <div class="price-item">优惠券</div>
                    <div class="price-value">-¥{{ calculatedFees.couponDiscountAmount }}</div>
                </div>
                <div class="price-row" v-if="calculatedFees.cleaningFee > 0">
                    <div class="price-item">清洁费</div>
                    <div class="price-value">¥{{ calculatedFees.cleaningFee }}</div>
                </div>
                <div class="price-row" v-if="calculatedFees.serviceFee > 0">
                    <div class="price-item">服务费</div>
                    <div class="price-value">¥{{ calculatedFees.serviceFee }}</div>
                </div>
                <div class="price-divider"></div>
                <div class="price-row total">
                    <div class="price-item">总价</div>
                    <div class="price-value">¥{{ calculatedFees.totalPrice }}</div>
                </div>

                <!-- 每日价格展开 -->
                <div v-if="calculatedFees.dailyPrices && calculatedFees.dailyPrices.length > 0" class="daily-prices-section">
                    <div class="daily-prices-toggle" @click="showDailyPrices = !showDailyPrices">
                        <span>{{ showDailyPrices ? '收起' : '展开每日价格' }}</span>
                        <el-icon><ArrowDown v-if="!showDailyPrices" /><ArrowUp v-else /></el-icon>
                    </div>
                    <div v-if="showDailyPrices" class="daily-prices-list">
                        <div class="daily-price-item" v-for="(dp, idx) in calculatedFees.dailyPrices" :key="idx">
                            <div class="dp-date">{{ dp.date }}</div>
                            <div class="dp-tags">
                                <el-tag v-if="dp.isHoliday" size="small" type="danger">{{ dp.holidayName }}</el-tag>
                                <el-tag v-else-if="dp.isWeekend" size="small" type="warning">周末</el-tag>
                            </div>
                            <div class="dp-price">
                                <span v-if="dp.basePrice !== dp.finalPrice" class="dp-base">¥{{ dp.basePrice }}</span>
                                <span>¥{{ dp.finalPrice }}</span>
                            </div>
                        </div>
                    </div>
                </div>
            </template>
        </div>

        <!-- 优惠券选择 -->
        <div class="coupon-section" v-if="availableCoupons && availableCoupons.length > 0 && calculatedFees">
            <div class="coupon-label">可用优惠券</div>
            <el-select v-model="localSelectedCoupons" placeholder="选择优惠券" size="small" class="coupon-select" @change="handleCouponChange">
                <el-option label="不使用优惠券" :value="null" />
                <el-option v-for="coupon in availableCoupons" :key="coupon.id" :label="`${coupon.name} (${formatCouponValue(coupon)})`" :value="coupon.id" />
            </el-select>
        </div>

        <div class="booking-note">
            <el-icon>
                <InfoFilled />
            </el-icon>
            <span>预订前不会向您收取任何费用</span>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { InfoFilled, Lightning, Clock, WarningFilled, ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getHomestayUnavailableDates } from '@/api/homestay'
import { differenceInCalendarDays } from 'date-fns'

// Props
interface Props {
    pricePerNight: number
    rating: number
    reviewCount: number
    maxGuests: number
    homestayId: number
    autoConfirm?: boolean
    dates?: [Date, Date] | null
    guests?: number
    calculatedFees?: any
    isCalculating?: boolean
    minNights?: number  // 房东设置的最低入住晚数
    maxNights?: number  // 房东设置的最大入住晚数
    availableCoupons?: any[]
    selectedCouponIds?: number[]
}

const props = withDefaults(defineProps<Props>(), {
    autoConfirm: false,
    dates: null,
    guests: 1,
    isCalculating: false,
    minNights: 1,
    maxNights: 0,  // 0 表示不限制
    availableCoupons: () => [],
    selectedCouponIds: () => []
})

// Emits
const emit = defineEmits<{
    'booking-confirmed': []
    'date-changed': [checkIn: Date | null, checkOut: Date | null]
    'guests-changed': [guests: number]
    'coupon-changed': [couponIds: number[]]
}>()

// Local reactive data
const localDateRange = ref<[Date, Date] | null>(null)
const localGuests = ref(props.guests)
const localSelectedCoupons = ref<number | null>(props.selectedCouponIds?.[0] || null)
const showDailyPrices = ref(false)

watch(() => props.selectedCouponIds, (newVal) => {
    localSelectedCoupons.value = newVal?.[0] || null
})

const handleCouponChange = (val: number | null) => {
    emit('coupon-changed', val ? [val] : [])
}

const formatCouponValue = (coupon: any) => {
    if (coupon.couponType === 'CASH' || coupon.couponType === 'FULL_REDUCTION') {
        return `减${coupon.faceValue}元`
    }
    if (coupon.couponType === 'DISCOUNT') {
        return `${(coupon.discountRate * 10).toFixed(1)}折`
    }
    return ''
}

// 不可用日期
const unavailableDates = ref<string[]>([])
const loadingDates = ref(false)

// 定时轮询：每 60 秒静默刷新不可用日期，防止用户长时间停留导致库存数据过期
const POLL_INTERVAL_MS = 60 * 1000
let pollTimer: ReturnType<typeof setInterval> | null = null

// 新增：最长可选退房日计算相关状态
const maxCheckOutDate = ref<Date | null>(null)
const confirmedCheckIn = ref<Date | null>(null)  // 已确认的入住日（用户选定后不再变更）
const autoFixMessage = ref('')  // 自动修正提示文字
const pendingCheckIn = ref<Date | null>(null)  // 用户选了第一个日期后暂存（用于高亮计算）

// Watch for prop changes
watch(() => props.dates, (newDates) => {
    localDateRange.value = newDates
}, { immediate: true })

watch(() => props.guests, (newGuests) => {
    localGuests.value = newGuests
}, { immediate: true })

// Computed properties
const numericRating = computed(() => {
    return isNaN(props.rating) ? 0 : props.rating
})

// 聚合定价规则明细（日期级规则 + 入住级折扣）
const pricingRuleBreakdown = computed(() => {
    const ruleMap: Record<string, { label: string; amount: number }> = {}
    const labelMap: Record<string, string> = {
        WEEKEND: '周末加价',
        HOLIDAY: '节假日加价',
        DATE_RANGE: '日期调价',
        EARLY_BIRD: '早鸟优惠',
        LONG_STAY: '连住优惠'
    }

    // 1. 聚合 dailyPrices 中的日期级规则
    if (props.calculatedFees?.dailyPrices) {
        for (const dp of props.calculatedFees.dailyPrices) {
            if (!dp.appliedRules) continue
            for (const rule of dp.appliedRules) {
                const key = rule.ruleType + '|' + rule.ruleName
                if (!ruleMap[key]) {
                    ruleMap[key] = { label: labelMap[rule.ruleType] || rule.ruleName, amount: 0 }
                }
                ruleMap[key].amount += rule.deltaAmount
            }
        }
    }

    // 2. 聚合入住级折扣（appliedPricingRules）
    if (props.calculatedFees?.appliedPricingRules) {
        for (const rule of props.calculatedFees.appliedPricingRules) {
            const key = rule.ruleType + '|' + rule.ruleName
            if (!ruleMap[key]) {
                ruleMap[key] = { label: labelMap[rule.ruleType] || rule.ruleName, amount: 0 }
            }
            ruleMap[key].amount += rule.deltaAmount
        }
    }

    return Object.values(ruleMap).filter(item => Math.abs(item.amount) > 0.001)
})

const totalNights = computed(() => {
    if (localDateRange.value && localDateRange.value[0] && localDateRange.value[1]) {
        return differenceInCalendarDays(localDateRange.value[1], localDateRange.value[0])
    }
    return 0
})

// 基于可用日期计算的最长可住晚数（受限于已预订日期）
const availableMaxNights = computed(() => {
    if (confirmedCheckIn.value && maxCheckOutDate.value) {
        return differenceInCalendarDays(maxCheckOutDate.value, confirmedCheckIn.value)
    }
    return 0
})

// 改为由外部通过 computedFees 传入算价结果，不再进行本地本地算费以免导致各处算价不统一的业务灾难

// Methods
const fetchUnavailableDates = async () => {
    if (!props.homestayId) return

    loadingDates.value = true
    try {
        const response = await getHomestayUnavailableDates(props.homestayId)

        // 适配新的后端响应格式
        const responseData = response.data || {}
        let dates = responseData.data || []

        // 确保日期格式为字符串数组 (YYYY-MM-DD)
        if (Array.isArray(dates)) {
            unavailableDates.value = dates.map(date => {
                if (typeof date === 'string') {
                    // 如果已经是字符串，确保格式正确
                    return date.includes('-') ? date : formatStringToStandardDate(date)
                } else if (date instanceof Date) {
                    // 如果是Date对象，转换为YYYY-MM-DD格式
                    return formatDateToString(date)
                } else if (typeof date === 'object' && date !== null) {
                    // 如果是类似 [2024, 12, 25] 的数组格式，转换为字符串
                    if (Array.isArray(date) && date.length >= 3) {
                        const [year, month, day] = date
                        return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
                    }
                    // 如果是对象格式，尝试提取年月日
                    if (date.year && date.month && date.day) {
                        return `${date.year}-${String(date.month).padStart(2, '0')}-${String(date.day).padStart(2, '0')}`
                    }
                }
                return String(date) // 其他情况尝试转为字符串
            })
        } else {
            unavailableDates.value = []
        }

        // 显示错误信息（如果有）
        if (responseData.error) {
            console.warn('后端返回错误信息:', responseData.error)
        }

    } catch (error) {
        console.error('获取不可用日期失败:', error)
        unavailableDates.value = []
    } finally {
        loadingDates.value = false
    }
}

/**
 * 静默刷新不可用日期（不显示 loading 状态，不打断用户操作）
 * 刷新后主动检测已选日期是否被抢，若冲突则提示用户重新选择
 */
const silentRefreshUnavailableDates = async () => {
    if (!props.homestayId) return

    try {
        const response = await getHomestayUnavailableDates(props.homestayId)
        const responseData = response.data || {}
        const dates = responseData.data || []

        if (!Array.isArray(dates)) return

        // 解析为标准日期字符串
        const latestUnavailable = dates.map((date: any) => {
            if (typeof date === 'string') return date.includes('-') ? date : formatStringToStandardDate(date)
            if (date instanceof Date) return formatDateToString(date)
            if (Array.isArray(date) && date.length >= 3) {
                const [year, month, day] = date
                return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
            }
            if (typeof date === 'object' && date?.year && date?.month && date?.day) {
                return `${date.year}-${String(date.month).padStart(2, '0')}-${String(date.day).padStart(2, '0')}`
            }
            return String(date)
        })

        // 检查是否有新增的不可用日期（对比之前的数据）
        const previousSet = new Set(unavailableDates.value)
        const newlyUnavailable = latestUnavailable.filter((d: string) => !previousSet.has(d))

        // 更新不可用日期列表
        unavailableDates.value = latestUnavailable

        // 重新计算最长可选退房日（如果已确认入住日）
        if (confirmedCheckIn.value) {
            maxCheckOutDate.value = computeMaxCheckOutDate(confirmedCheckIn.value)
        }

        // 如果有新的不可用日期，检测对已选范围的影响
        if (newlyUnavailable.length > 0) {
            const newDatesDisplay = newlyUnavailable.length > 3
                ? `${newlyUnavailable.slice(0, 3).join('、')}...等${newlyUnavailable.length}天`
                : newlyUnavailable.join('、')

            // 有已选日期范围的情况
            if (localDateRange.value?.[0] && localDateRange.value?.[1]) {
                const checkInStr = formatDateToString(localDateRange.value[0])
                const checkOutStr = formatDateToString(localDateRange.value[1])

                // 入住日被抢，或住宿期间某天被抢（退房日当天不算）
                const selectedDateConflict = latestUnavailable.some(
                    (d: string) => (d >= checkInStr && d < checkOutStr)
                )

                if (selectedDateConflict) {
                    // 严重冲突：已选日期被抢 → 弹窗警告
                    ElMessageBox.alert(
                        `您选择的入住日期范围内有日期（${newDatesDisplay}）刚刚被其他用户预订，请返回重新选择。`,
                        '日期已被预订',
                        {
                            confirmButtonText: '重新选择日期',
                            type: 'warning',
                            center: true
                        }
                    ).then(() => {
                        // 清除已选日期
                        localDateRange.value = null
                        confirmedCheckIn.value = null
                        maxCheckOutDate.value = null
                        autoFixMessage.value = ''
                        emit('date-changed', null, null)
                    }).catch(() => {
                        // 取消也清空，保持一致
                        localDateRange.value = null
                        confirmedCheckIn.value = null
                        maxCheckOutDate.value = null
                        autoFixMessage.value = ''
                        emit('date-changed', null, null)
                    })
                } else {
                    // 无冲突但有新日期被订 → 轻提示
                    ElMessage.info({
                        message: `部分日期（${newDatesDisplay}）刚刚被预订，可能影响您的选择`,
                        duration: 4000,
                        showClose: true
                    })
                }
            } else {
                // 没有选日期范围，但也提示有日期被预订
                ElMessage.info({
                    message: `部分日期（${newDatesDisplay}）刚刚被预订，置灰日期无法选择`,
                    duration: 4000,
                    showClose: true
                })
            }
        }
    } catch (error) {
        // 静默刷新失败不影响用户操作，仅记录日志
        console.warn('静默刷新不可用日期失败:', error)
    }
}

// 辅助函数：格式化字符串日期为标准格式
const formatStringToStandardDate = (dateStr: string): string => {
    try {
        const date = new Date(dateStr)
        if (isNaN(date.getTime())) {
            return dateStr // 如果无法解析，返回原始字符串
        }
        return formatDateToString(date)
    } catch {
        return dateStr
    }
}

// 组件挂载时获取不可用日期
onMounted(() => {
    fetchUnavailableDates()

    // 定时轮询：每 60 秒静默刷新，防止用户长时间停留导致库存过期
    pollTimer = setInterval(silentRefreshUnavailableDates, POLL_INTERVAL_MS)

    // 页面切回前台时立即刷新（补充轮询间隔内的空窗期）
    document.addEventListener('visibilitychange', handleVisibilityChange)
})

onUnmounted(() => {
    // 清除定时器，防止组件销毁后还在轮询
    if (pollTimer) {
        clearInterval(pollTimer)
        pollTimer = null
    }
    document.removeEventListener('visibilitychange', handleVisibilityChange)
})

// 页面可见性变化：切回前台时立即刷新一次
const handleVisibilityChange = () => {
    if (document.visibilityState === 'visible') {
        console.log('页面重新可见，立即刷新不可用日期')
        silentRefreshUnavailableDates()
    }
}

/**
 * 基于已选入住日计算最长可选退房日
 * 算法：找到从入住日起第一个冲突日，该日期自身可作为退房日。
 */
const computeMaxCheckOutDate = (checkIn: Date): Date | null => {
    if (!unavailableDates.value.length) return null

    const checkInStr = formatDateToString(checkIn)
    const sortedUnavailable = [...unavailableDates.value].sort()

    // 找第一个 > checkInStr 的不可用日期（不包含入住日本身）
    const firstConflictIdx = sortedUnavailable.findIndex(d => d > checkInStr)

    if (firstConflictIdx === -1) {
        // 没有冲突日期，可以选任意远的退房日
        return null
    }

    const conflictDateStr = sortedUnavailable[firstConflictIdx]
    const [y, m, d] = conflictDateStr.split('-').map(Number)
    return new Date(y, m - 1, d)
}

/**
 * 检测所选日期范围是否跨越冲突日期
 * 注意：退房日=冲突日期本身是允许的（即早晨退房不再续住）
 */
const detectConflictInRange = (checkIn: Date, checkOut: Date): { hasConflict: boolean; conflictDate: Date | null } => {
    const checkInStr = formatDateToString(checkIn)
    const checkOutStr = formatDateToString(checkOut)

    // 找范围内第一个冲突日期（大于入住日且小于退房日的不可用日期）
    // 注意：d < checkOutStr 而不是 d <= checkOutStr
    // 因为退房日当天不算"入住在冲突日期"
    const conflictDateStr = unavailableDates.value.find(d => d > checkInStr && d < checkOutStr)

    if (conflictDateStr) {
        const [y, m, d] = conflictDateStr.split('-').map(Number)
        return { hasConflict: true, conflictDate: new Date(y, m - 1, d) }
    }
    return { hasConflict: false, conflictDate: null }
}

// 增强的日期禁用逻辑
const disablePastDates = (date: Date) => {
    const dateStr = formatDateToString(date)

    // 禁用过去日期（使用字符串比较避免时区边缘问题）
    const todayStr = formatDateToString(new Date())
    if (dateStr < todayStr) return true

    const isUnavailable = unavailableDates.value.includes(dateStr)

    // 当用户开始选择入住日，或者处于部分选中的状态
    // 我们限制只能选择入住日之后、冲突日及之前的日期
    const activeCheckIn = pendingCheckIn.value || 
                          (localDateRange.value && !localDateRange.value[1] ? localDateRange.value[0] : null) || 
                          confirmedCheckIn.value

    if (activeCheckIn) {
        const checkInStr = formatDateToString(activeCheckIn)
        if (dateStr > checkInStr) {
            const maxDate = computeMaxCheckOutDate(activeCheckIn)
            if (maxDate) {
                const maxDateStr = formatDateToString(maxDate)
                // 如果当前日期在最大可退房日期（即首个冲突日）之后，一律禁用
                if (dateStr > maxDateStr) {
                    return true
                }
                // 允许把第一个冲突日作为退房日（返回 false，即使它在 unavailableDates 中）
                if (dateStr === maxDateStr) {
                    return false
                }
            }
        }
    }

    return isUnavailable
}

/**
 * 用户在 daterange 模式选了第一个日期时触发
 * 立即计算高亮范围，不等两个日期都选完
 */
const handleCalendarChange = (dates: Date[]) => {
    if (dates && dates.length >= 1 && dates[0]) {
        pendingCheckIn.value = dates[0]
    }
}

/**
 * 日历面板显示/隐藏时触发
 * 面板关闭时清除 pendingCheckIn，防止残留状态影响下次选择
 */
const handleVisibleChange = (visible: boolean) => {
    if (!visible) {
        pendingCheckIn.value = null
    }
}

// 格式化日期为字符串（YYYY-MM-DD格式）
const formatDateToString = (date: Date): string => {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
}

const handleDateRangeChange = (dates: [Date, Date] | null) => {
    // 选择完成后，清除临时的 pendingCheckIn
    pendingCheckIn.value = null
    autoFixMessage.value = ''

    if (dates && dates.length === 2 && dates[0] && dates[1]) {
        const [newCheckIn, newCheckOut] = dates

        // 先检查基本有效性
        if (newCheckOut.getTime() <= newCheckIn.getTime()) {
            ElMessage.warning('退房日期必须在入住日期之后')
            localDateRange.value = null
            confirmedCheckIn.value = null
            maxCheckOutDate.value = null
            emit('date-changed', null, null)
            return
        }

        // 计算入住晚数（使用时区安全的日历天数差）
        const nights = differenceInCalendarDays(newCheckOut, newCheckIn)

        // 校验最低晚数要求
        if (nights < props.minNights) {
            ElMessage.warning(`此房源最少需入住 ${props.minNights} 晚，您选择了 ${nights} 晚`)
            localDateRange.value = null
            confirmedCheckIn.value = null
            maxCheckOutDate.value = null
            emit('date-changed', null, null)
            return
        }

        // 校验最高晚数限制（如果房东设置了）
        if (props.maxNights > 0 && nights > props.maxNights) {
            ElMessage.warning(`此房源最多可入住 ${props.maxNights} 晚，您选择了 ${nights} 晚`)
            localDateRange.value = null
            confirmedCheckIn.value = null
            maxCheckOutDate.value = null
            emit('date-changed', null, null)
            return
        }

        // 校验入住日本身是否已被预订（防止通过 props/URL 等方式绕过禁用态）
        const checkInStr = formatDateToString(newCheckIn)
        if (unavailableDates.value.includes(checkInStr)) {
            ElMessage.warning('您选择的入住日期已被预订，请重新选择')
            localDateRange.value = null
            confirmedCheckIn.value = null
            maxCheckOutDate.value = null
            emit('date-changed', null, null)
            return
        }

        // 入住日确认后：计算并显示最长可选区间
        // 每次重新计算，避免 unavailableDates 轮询更新后仍使用旧值
        confirmedCheckIn.value = newCheckIn
        maxCheckOutDate.value = computeMaxCheckOutDate(newCheckIn)

        // 检测是否跨越冲突日期
        const { hasConflict, conflictDate } = detectConflictInRange(newCheckIn, newCheckOut)

        if (hasConflict && conflictDate && maxCheckOutDate.value) {
            // 自动修正：退房日设为冲突日期前一天
            const autoCheckOut = maxCheckOutDate.value

            // 内联提示（非弹窗）
            const month = autoCheckOut.getMonth() + 1
            const day = autoCheckOut.getDate()
            autoFixMessage.value = `已为你调整至最晚可离店日期 ${month}月${day}日，此后日期已被预订`

            localDateRange.value = [newCheckIn, autoCheckOut]

            emit('date-changed', newCheckIn, autoCheckOut)
        } else {
            autoFixMessage.value = ''
            emit('date-changed', dates[0], dates[1])
        }
    } else {
        confirmedCheckIn.value = null
        maxCheckOutDate.value = null
        autoFixMessage.value = ''
        emit('date-changed', null, null)
    }
}

const isVerifying = ref(false)  // 轻量方案：预订前验证状态

const handleBooking = async () => {
    if (!localDateRange.value || !localDateRange.value[0] || !localDateRange.value[1]) {
        ElMessage.warning('请先选择有效的入住和退房日期')
        return
    }

    if (totalNights.value <= 0) {
        ElMessage.warning('请选择有效的住宿日期')
        return
    }

    // 轻量方案：点击预订时先验证日期是否仍可用
    isVerifying.value = true
    try {
        // 重新拉取最新的不可用日期
        const response = await getHomestayUnavailableDates(props.homestayId)
        const responseData = response.data || {}
        const latestUnavailable: string[] = responseData.data || []

        // 格式化当前选择的日期
        const checkInStr = formatDateToString(localDateRange.value[0])
        const checkOutStr = formatDateToString(localDateRange.value[1])

        // 检查入住日期区间内是否有不可用日期（入住日本身被订走也不行，退房日当天可以）
        const hasConflict = latestUnavailable.some(
            d => d >= checkInStr && d < checkOutStr
        )

        if (hasConflict) {
            ElMessage.error('您选择的日期已被其他用户预订，请重新选择')
            // 刷新不可用日期列表
            unavailableDates.value = latestUnavailable
            // 重新计算最长可选退房日
            if (confirmedCheckIn.value) {
                maxCheckOutDate.value = computeMaxCheckOutDate(confirmedCheckIn.value)
            }
            // 清除已选日期
            localDateRange.value = null
            confirmedCheckIn.value = null
            maxCheckOutDate.value = null
            emit('date-changed', null, null)
            return
        }

        emit('booking-confirmed')
    } catch (error) {
        console.error('验证可用性失败:', error)
        ElMessage.error('验证预订可用性失败，请稍后重试')
    } finally {
        isVerifying.value = false
    }
}

// Watch guests change
watch(localGuests, (newGuests) => {
    emit('guests-changed', newGuests)
})
</script>

<style scoped>
.booking-card {
    border-radius: 16px;
    border: 1px solid #e0e0e0;
    padding: 24px;
    box-shadow: 0 6px 24px rgba(0, 0, 0, 0.08);
    background-color: white;
    position: sticky;
    top: 20px;
    height: fit-content;
    transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.booking-card:hover {
    transform: translateY(-4px);
    box-shadow: 0 12px 32px rgba(0, 0, 0, 0.12);
}

.booking-card-header {
    margin-bottom: 24px;
}

.price-info {
    display: flex;
    align-items: baseline;
    margin-bottom: 12px;
}

.price {
    font-size: 28px;
    font-weight: 700;
    color: #303133;
}

.night {
    font-size: 14px;
    font-weight: 500;
    color: #909399;
    margin-left: 2px;
}

.rating-info {
    display: flex;
    align-items: center;
    gap: 8px;
}

.review-link {
    font-size: 14px;
    color: #606266;
    text-decoration: underline;
    cursor: pointer;
}

.booking-form {
    margin-bottom: 24px;
    border: 1px solid #e0e0e0;
    border-radius: 12px;
    overflow: hidden;
}

.date-picker-container {
    border-bottom: 1px solid #e0e0e0;
}

.date-picker-header {
    display: flex;
    padding: 10px 16px 0;
}

.check-in-label,
.check-out-label {
    font-size: 12px;
    font-weight: 600;
    color: #606266;
    flex: 1;
}

.date-range-picker {
    width: 100%;
    border: none;
}

.date-range-picker :deep(.el-input__wrapper) {
    box-shadow: none !important;
    padding: 8px 16px 16px;
}

.guest-selector-container {
    padding: 10px 16px 16px;
}

.guest-selector-label {
    font-size: 12px;
    font-weight: 600;
    color: #606266;
    margin-bottom: 5px;
}

.guest-dropdown {
    width: 100%;
}

.guest-dropdown :deep(.el-input__wrapper) {
    box-shadow: none !important;
}

.date-availability-hint {
    margin: 4px 16px 16px;
    padding: 8px 12px;
    background-color: #fafafa;
    border-radius: 6px;
    display: flex;
    align-items: center;
    gap: 6px;
}

.date-availability-hint .el-icon {
    font-size: 14px;
    color: #909399;
}

/* 已预订日期斜线纹理 */
.date-range-picker :deep(.el-date-table td.disabled) {
    position: relative;
    background-color: #f5f5f5 !important;
}

.date-range-picker :deep(.el-date-table td.disabled::before) {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: repeating-linear-gradient(
        -45deg,
        transparent,
        transparent 4px,
        rgba(180, 180, 180, 0.5) 4px,
        rgba(180, 180, 180, 0.5) 8px
    );
    pointer-events: none;
    z-index: 1;
}

.date-range-picker :deep(.el-date-table td.disabled .el-date-table-cell__text) {
    color: #c0c0c0;
    background-color: transparent;
    position: relative;
    z-index: 2;
}

.date-range-picker :deep(.el-date-table td.disabled:hover .el-date-table-cell) {
    background-color: transparent !important;
}

/* 最长可选区间提示 */
.max-stay-hint {
    margin: 4px 16px 16px;
    padding: 8px 12px;
    background-color: #f0f9ff;
    border-radius: 6px;
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: #409eff;
}

.max-stay-hint .el-icon {
    font-size: 14px;
}

.min-stay-hint {
    margin: 4px 16px 16px;
    padding: 8px 12px;
    background-color: #fff7e6;
    border-radius: 6px;
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: #d48806;
}

.min-stay-hint .el-icon {
    font-size: 14px;
}

.max-stay-limit-hint {
    margin: 4px 16px 16px;
    padding: 8px 12px;
    background-color: #fff7e6;
    border-radius: 6px;
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: #d48806;
}

.max-stay-limit-hint .el-icon {
    font-size: 14px;
}

.max-stay-hint .nights-count {
    color: #79bbff;
    font-size: 12px;
}

.booking-button {
    width: 100%;
    height: 48px;
    font-size: 16px;
    font-weight: 600;
    margin-bottom: 20px;
    border-radius: 8px;
}

.price-breakdown {
    margin-bottom: 20px;
}

.price-row {
    display: flex;
    justify-content: space-between;
    margin-bottom: 16px;
    font-size: 14px;
    color: #606266;
}

.price-row.total {
    font-weight: 600;
    color: #303133;
    font-size: 16px;
}

.price-divider {
    height: 1px;
    background-color: #e0e0e0;
    margin: 12px 0;
}

.daily-prices-section {
    margin-top: 12px;
    border-top: 1px dashed #e0e0e0;
    padding-top: 8px;
}

.daily-prices-toggle {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 4px;
    font-size: 13px;
    color: #409eff;
    cursor: pointer;
    padding: 4px 0;
}

.daily-prices-toggle:hover {
    color: #66b1ff;
}

.daily-prices-list {
    margin-top: 8px;
    background: #f7f8fa;
    border-radius: 6px;
    padding: 8px 12px;
}

.daily-price-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 4px 0;
    font-size: 13px;
    border-bottom: 1px solid #eee;
}

.daily-price-item:last-child {
    border-bottom: none;
}

.dp-date {
    color: #606266;
    min-width: 90px;
}

.dp-tags {
    flex: 1;
    display: flex;
    gap: 4px;
    justify-content: center;
}

.dp-price {
    color: #303133;
    text-align: right;
    min-width: 80px;
}

.dp-base {
    text-decoration: line-through;
    color: #909399;
    margin-right: 6px;
    font-size: 12px;
}

.booking-note {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;
    color: #909399;
    padding: 12px 16px;
    background-color: #f7f8fa;
    border-radius: 8px;
}

.booking-note .el-icon {
    font-size: 14px;
}

/* 自动确认模式样式 */
.booking-mode-notice {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    margin-bottom: 16px;
    font-size: 14px;
    color: #52c41a;
    font-weight: 500;
}

.auto-confirm-icon {
    color: #52c41a;
}
/* 自动修正内联提示 */
.auto-fix-hint {
    margin: 4px 16px 16px;
    padding: 8px 12px;
    background-color: #fffbe6;
    border-radius: 6px;
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: #d48806;
    animation: fadeSlideIn 0.3s ease;
}

.auto-fix-hint .el-icon {
    font-size: 14px;
    flex-shrink: 0;
}

@keyframes fadeSlideIn {
    from {
        opacity: 0;
        transform: translateY(-4px);
    }
    to {
        opacity: 1;
        transform: translateY(0);
    }
}

/* ================= 移动端适配 ================= */
@media (max-width: 768px) {
    /* 详情页移动端为单列流式布局，取消桌面侧栏的 sticky 定位 */
    .booking-card {
        position: static;
        top: auto;
        padding: 16px;
        border-radius: 12px;
    }

    /* 触屏无 hover，去掉位移效果避免残留 */
    .booking-card:hover {
        transform: none;
        box-shadow: 0 6px 24px rgba(0, 0, 0, 0.08);
    }

    .booking-card-header {
        margin-bottom: 16px;
    }

    .price {
        font-size: 24px;
    }

    .booking-button {
        height: 44px;
    }
}
</style>

<!-- 全局样式：覆盖 teleported 到 body 的 DatePicker popper -->
<style>
/* === 已预订日期样式（斜线纹理 + 置灰 + tooltip） === */
.booking-date-popper .el-date-table td.disabled {
    position: relative;
    cursor: not-allowed !important;
}

.booking-date-popper .el-date-table td.disabled > div,
.booking-date-popper .el-date-table td.disabled > span {
    cursor: not-allowed !important;
}

/* 斜线纹理覆盖层 */
.booking-date-popper .el-date-table td.disabled::before {
    content: '';
    position: absolute;
    top: 2px;
    left: 2px;
    right: 2px;
    bottom: 2px;
    background: repeating-linear-gradient(
        -45deg,
        transparent,
        transparent 3px,
        rgba(160, 160, 160, 0.35) 3px,
        rgba(160, 160, 160, 0.35) 6px
    );
    border-radius: 4px;
    pointer-events: none;
    z-index: 1;
}

/* 禁用日期文字样式 */
.booking-date-popper .el-date-table td.disabled .el-date-table-cell__text {
    color: #b0b0b0 !important;
    background-color: transparent !important;
    position: relative;
    z-index: 2;
}

/* hover 时的 CSS tooltip */
.booking-date-popper .el-date-table td.disabled::after {
    content: '该日期已被预订';
    position: absolute;
    bottom: calc(100% + 6px);
    left: 50%;
    transform: translateX(-50%);
    background: rgba(48, 49, 51, 0.92);
    color: #fff;
    font-size: 12px;
    padding: 4px 10px;
    border-radius: 4px;
    white-space: nowrap;
    opacity: 0;
    pointer-events: none;
    transition: opacity 0.2s ease;
    z-index: 999;
}

/* tooltip 小三角 */
.booking-date-popper .el-date-table td.disabled:hover::after {
    opacity: 1;
}

.coupon-section {
    margin: 12px 0;
    padding: 0 4px;
}

.coupon-label {
    font-size: 12px;
    font-weight: 600;
    color: #606266;
    margin-bottom: 6px;
}

.coupon-select {
    width: 100%;
}

</style>