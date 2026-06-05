<template>
    <div class="order-confirm-container">
        <div v-if="loading" class="loading-container">
            <el-skeleton :rows="10" animated />
        </div>
        <div v-else-if="!orderData" class="error-container">
            <el-empty description="订单数据不存在">
                <template #description>
                    <p>未找到订单数据，请返回房源详情页重新预订</p>
                </template>
                <el-button type="primary" @click="goBack">返回房源详情</el-button>
            </el-empty>
        </div>
        <div v-else class="order-content">
            <h1>确认并支付</h1>

            <!-- 提醒信息 -->
            <div class="order-notice">
                <!-- 日期冲突警告 -->
                <el-alert v-if="dateConflictFlag" title="日期已被预订" type="warning"
                    description="您之前选择的日期已被其他用户预订，请重新选择入住日期。您的旅客信息已保留。"
                    show-icon :closable="false" class="conflict-alert">
                    <template #default>
                        <div class="conflict-actions">
                            <span>您的旅客信息已保留</span>
                            <el-button type="warning" size="small" @click="goBackToSelectDates">
                                返回重新选择日期
                            </el-button>
                        </div>
                    </template>
                </el-alert>
                <el-alert v-if="orderData.autoConfirm" title="即时预订" type="success"
                    description="🚀 此房源支持即时预订！订单确认后无需等待房东审核，可直接支付。请在2小时内完成支付，超时将被自动取消。" show-icon :closable="false" />
                <el-alert v-else title="房东确认制" type="info" description="📋 此房源采用房东确认制，订单提交后需等待房东确认才能支付。房东通常会在24小时内回复。"
                    show-icon :closable="false" />
            </div>

            <!-- 订单信息卡片 -->
            <div class="order-summary-card">
                <div class="card-header">
                    <h2>房源信息</h2>
                    <span class="homestay-id">房源编号: {{ orderData.homestayId }}</span>
                </div>
                <div class="homestay-info">
                    <div class="homestay-image">
                        <img :src="processImageUrl(orderData.imageUrl)" alt="房源图片">
                        <div class="image-overlay" @click="showFullImage">
                            <i class="el-icon-zoom-in"></i> 查看更多图片
                        </div>
                    </div>
                    <div class="homestay-details">
                        <h3>{{ orderData.homestayTitle }}</h3>
                        <p class="homestay-address"><i class="el-icon-location-outline"></i> {{ formattedAddress }}</p>
                        <p class="date-highlight"><i class="el-icon-date"></i> {{ formatDateRange(orderData.checkInDate,
                            orderData.checkOutDate) }}
                        </p>
                        <p><i class="el-icon-user"></i> {{ orderData.guestCount }}位房客 · {{ orderData.nights }}晚</p>
                        <div class="host-info" v-if="hostInfo">
                            <img :src="hostInfo.avatar || 'https://picsum.photos/100/100'" class="host-avatar"
                                alt="房东头像">
                            <div class="host-details">
                                <div class="host-name">房东: {{ hostInfo.name || '房东' }}</div>
                                <div class="host-rating">
                                    <el-rate v-model="hostInfo.rating" disabled show-score text-color="#ff9900" />
                                </div>
                                <div class="host-contact">
                                    <el-button type="text" size="small">
                                        <i class="el-icon-message"></i> 联系房东
                                    </el-button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <!-- 入住退房信息 -->
            <div class="checkin-checkout-section">
                <div class="checkin-info">
                    <h3>入住信息</h3>
                    <div class="time-info">
                        <i class="el-icon-time"></i> 入住时间: 14:00后
                    </div>
                    <div class="date-info">
                        <i class="el-icon-date"></i> 入住日期: {{ formatDate(orderData.checkInDate) }}
                    </div>
                </div>
                <div class="checkout-info">
                    <h3>退房信息</h3>
                    <div class="time-info">
                        <i class="el-icon-time"></i> 退房时间: 12:00前
                    </div>
                    <div class="date-info">
                        <i class="el-icon-date"></i> 退房日期: {{ formatDate(orderData.checkOutDate) }}
                    </div>
                </div>
            </div>

            <!-- 订单规则 -->
            <div class="order-rules-section">
                <h2>订单规则</h2>
                <div class="rules-content">
                    <div class="rule-item">
                        <i class="el-icon-warning"></i>
                        <span>2小时内未支付，订单将被自动取消</span>
                    </div>
                    <div class="rule-item">
                        <i class="el-icon-warning"></i>
                        <span>入住前48小时可全额退款，48小时内按比例扣除违约金</span>
                    </div>
                </div>
            </div>

            <!-- 旅客信息 -->
            <div class="guest-info-section">
                <h2>旅客信息</h2>
                <div class="guest-info-content">
                    <div class="info-display" v-if="!editingUserInfo">
                        <div class="info-item">
                            <span class="label">联系人:</span>
                            <span>{{ guestInfo.name || '未设置' }}</span>
                        </div>
                        <div class="info-item">
                            <span class="label">联系电话:</span>
                            <span>{{ guestInfo.phone || '未设置' }}</span>
                        </div>
                        <div class="info-item">
                            <span class="label">留言:</span>
                            <span>{{ guestInfo.message || '无' }}</span>
                        </div>
                        <div class="edit-button">
                            <el-button type="primary" plain size="small" @click="editingUserInfo = true">
                                <i class="el-icon-edit"></i> 修改信息
                            </el-button>
                        </div>
                    </div>
                    <el-form v-else :model="guestInfo" label-position="top">
                        <el-form-item label="联系人姓名">
                            <el-input v-model="guestInfo.name" placeholder="请输入联系人姓名" />
                        </el-form-item>
                        <el-form-item label="联系电话">
                            <el-input v-model="guestInfo.phone" placeholder="请输入联系电话" />
                        </el-form-item>
                        <el-form-item label="给房东的留言">
                            <el-input v-model="guestInfo.message" type="textarea" placeholder="告诉房东您的到达时间或特殊需求"
                                rows="3" />
                        </el-form-item>
                        <div class="form-actions">
                            <el-button @click="cancelEdit">取消</el-button>
                            <el-button type="primary" @click="saveUserInfo">保存</el-button>
                        </div>
                    </el-form>
                </div>
            </div>

            <!-- 价格详情 -->
            <div class="price-details-section">
                <h2>价格详情</h2>
                <div class="price-breakdown">
                    <div class="price-row">
                        <span>房源原价</span>
                        <span>{{ orderData.roomOriginalAmount || orderData.baseAmount }}元</span>
                    </div>
                    <div class="price-row discount" v-if="orderData.activityDiscountAmount">
                        <span>活动优惠</span>
                        <span>-{{ orderData.activityDiscountAmount }}元</span>
                    </div>
                    <div class="price-row discount" v-if="orderData.couponDiscountAmount">
                        <span>优惠券</span>
                        <span>-{{ orderData.couponDiscountAmount }}元</span>
                    </div>
                    <div class="price-row" v-if="orderData.cleaningFee">
                        <span>清洁费</span>
                        <span>{{ orderData.cleaningFee }}元</span>
                    </div>
                    <div class="price-row" v-if="orderData.serviceFee">
                        <span>服务费</span>
                        <span>{{ orderData.serviceFee }}元</span>
                    </div>
                    <div class="price-row total">
                        <span>总价</span>
                        <span>{{ orderData.totalAmount }}元</span>
                    </div>

                    <!-- 每日价格展开 -->
                    <div v-if="orderData.dailyPrices && orderData.dailyPrices.length > 0" class="daily-prices-section">
                        <div class="daily-prices-toggle" @click="showDailyPrices = !showDailyPrices">
                            <span>{{ showDailyPrices ? '收起每日价格' : '展开每日价格' }}</span>
                            <el-icon><ArrowDown v-if="!showDailyPrices" /><ArrowUp v-else /></el-icon>
                        </div>
                        <div v-if="showDailyPrices" class="daily-prices-list">
                            <div class="daily-price-item" v-for="(dp, idx) in orderData.dailyPrices" :key="idx">
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
                </div>

                <!-- 优惠券选择 -->
                <div class="coupon-selector" v-if="availableCoupons.length > 0" v-loading="isRecalculating">
                    <el-select v-model="selectedCouponIdProxy" placeholder="选择优惠券" size="small" @change="handleCouponChange">
                        <el-option label="不使用优惠券" :value="null" />
                        <el-option v-for="coupon in availableCoupons" :key="coupon.id" :label="`${coupon.name} (${formatCouponLabel(coupon)})`" :value="coupon.id" />
                    </el-select>
                </div>
                <!-- 添加价格保障说明 -->
                <div class="price-guarantee">
                    <i class="el-icon-check"></i>
                    <span>价格透明保障：无任何隐藏费用，最终支付金额即为上述总价</span>
                </div>
            </div>

            <!-- 用户协议 -->
            <div class="agreement-section">
                <el-checkbox v-model="agreementChecked">我已阅读并同意</el-checkbox>
                <a href="javascript:void(0)" @click="showAgreement" class="agreement-link">《民宿预订服务协议》</a>
                <a href="javascript:void(0)" @click="showCancellationPolicy" class="agreement-link">《取消政策》</a>
            </div>

            <!-- 提交按钮 -->
            <div class="submit-section">
                <div class="total-price-display">
                    <span class="total-price-label">总价:</span>
                    <span class="total-price-value">¥{{ orderData.totalAmount }}</span>
                </div>
                <el-button type="primary" :loading="submitting" :disabled="!agreementChecked" @click="submitOrder">
                    {{ orderData.autoConfirm ? '确认预订并支付' : '提交预订申请' }}
                </el-button>
                <el-button @click="goBack">返回</el-button>
            </div>

            <!-- 协议弹窗 -->
            <el-dialog v-model="agreementDialogVisible" title="民宿预订服务协议" width="60%" :before-close="handleCloseDialog">
                <div class="agreement-content">
                    <p>本协议是您与民宿平台之间关于民宿预订的相关事宜的约定。</p>
                    <h3>一、订单规则</h3>
                    <p>1. 订单确认后，需在2小时内完成支付，否则订单将被自动取消。</p>
                    <p>2. 入住前48小时之上取消，可全额退款。</p>
                    <p>3. 入住前24-48小时内取消，将收取订单总额50%作为违约金。</p>
                    <p>4. 入住前24小时内或入住当天取消，将收取首晚房费作为违约金。</p>
                    <h3>二、入住规则</h3>
                    <p>1. 入住时间为14:00后，退房时间为12:00前。</p>
                    <p>2. 请爱护房屋内设施，如有损坏需照价赔偿。</p>
                    <h3>三、其他事项</h3>
                    <p>平台将对您的信息进行保密，未经您的同意不会向任何第三方提供您的个人信息。</p>
                </div>
                <template #footer>
                    <span class="dialog-footer">
                        <el-button @click="agreementDialogVisible = false">关闭</el-button>
                        <el-button type="primary" @click="agreeToTerms">
                            我已阅读并同意
                        </el-button>
                    </span>
                </template>
            </el-dialog>

            <!-- 取消政策弹窗 -->
            <el-dialog v-model="cancellationDialogVisible" title="取消政策" width="60%" :before-close="handleCloseDialog">
                <div class="cancellation-content">
                    <h3>阶梯取退政策</h3>
                    <p>入住前48小时以上，可全额退款。</p>
                    <p>入住前24-48小时内取消，扣除订单总额50%作为违约金。</p>
                    <p>入住前24小时内取消，收取首晚房费。</p>
                    <p>如需取消订单，请在"我的订单"中操作。</p>
                </div>
                <template #footer>
                    <span class="dialog-footer">
                        <el-button @click="cancellationDialogVisible = false">关闭</el-button>
                    </span>
                </template>
            </el-dialog>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import { createOrder } from '../../api/order'
import { getHomestayUnavailableDates } from '@/api/homestay'
import { useUserStore } from '../../stores/user'
import { getUserInfo } from '../../api/user'
import { getHomestayById } from '@/api/homestay'
import { codeToText } from 'element-china-area-data'
import type { DailyPriceItem } from '@/utils/homestayUtils'

// 定义订单数据类型
interface OrderData {
    homestayId: number
    homestayTitle: string
    imageUrl: string
    address: string
    provinceCode?: string
    cityCode?: string
    districtCode?: string
    checkInDate: string
    checkOutDate: string
    nights: number
    guestCount: number
    price: number
    baseAmount: number
    cleaningFee?: number
    serviceFee?: number
    totalAmount: number
    hostId: number
    hostName: string
    autoConfirm?: boolean
    quoteToken?: string
    couponIds?: number[]
    roomOriginalAmount?: number
    activityDiscountAmount?: number
    couponDiscountAmount?: number
    appliedPromotions?: any[]
    availableCoupons?: any[]
    dailyPrices?: DailyPriceItem[]
}

// 定义旅客信息类型
interface GuestInfo {
    name: string
    phone: string
    message: string
}

// HostInfo类型定义，移除不再需要的id属性
interface HostInfo {
    name: string;
    avatar: string;
    rating: number;
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const submitting = ref(false)
const orderData = ref<OrderData | null>(null)
const selectedCouponIds = ref<number[]>([])
const availableCoupons = ref<any[]>([])
const isRecalculating = ref(false)
const showDailyPrices = ref(false)

const selectedCouponIdProxy = computed({
    get: () => selectedCouponIds.value[0] || null,
    set: (val: number | null) => {
        selectedCouponIds.value = val ? [val] : []
    }
})

const formatCouponLabel = (coupon: any) => {
    if (coupon.couponType === 'CASH' || coupon.couponType === 'FULL_REDUCTION') {
        return `减${coupon.faceValue}元`
    }
    if (coupon.couponType === 'DISCOUNT') {
        return `${(coupon.discountRate * 10).toFixed(1)}折`
    }
    return ''
}

// 初始化旅客信息
const guestInfo = reactive<GuestInfo>({
    name: '',
    phone: '',
    message: ''
})

// 房东信息
const hostInfo = ref<HostInfo>({
    name: '房东名称',
    avatar: '',
    rating: 4.5
})

// 用户协议相关
const agreementChecked = ref(false)
const agreementDialogVisible = ref(false)
const cancellationDialogVisible = ref(false)

const editingUserInfo = ref(false)

// 日期冲突标记
const dateConflictFlag = ref(false)

// 格式化单个日期
const formatDate = (dateString: string) => {
    if (!dateString) return ''
    const date = new Date(dateString)
    return date.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })
}

// 格式化日期范围 - 优化现有函数
const formatDateRange = (checkIn: string, checkOut: string) => {
    if (!checkIn || !checkOut) return ''

    const checkInDate = new Date(checkIn)
    const checkOutDate = new Date(checkOut)

    const options: Intl.DateTimeFormatOptions = { month: 'long', day: 'numeric' }
    return `${checkInDate.toLocaleDateString('zh-CN', options)} - ${checkOutDate.toLocaleDateString('zh-CN', options)}`
}

// 显示完整图片
const showFullImage = () => {
    ElMessage.info('查看更多房源图片功能将在未来版本开放')
}

// 取消编辑用户信息
const cancelEdit = () => {
    // 恢复原来的信息
    populateUserInfo()
    editingUserInfo.value = false
}

// 保存用户信息
const saveUserInfo = async () => {
    if (!guestInfo.name || !guestInfo.phone) {
        ElMessage.warning('联系人姓名和电话不能为空')
        return
    }

    // 保存到本地 userStore（用于下次预订自动填充）
    if (userStore.userInfo) {
        userStore.userInfo.phone = guestInfo.phone
        // 如果 realName 为空，也保存姓名
        if (!userStore.userInfo.realName && guestInfo.name) {
            userStore.userInfo.realName = guestInfo.name
        }
        // 同步到 localStorage
        localStorage.setItem('homestay_user', JSON.stringify(userStore.userInfo))
    }

    // 如果用户已登录，同步到后端
    if (userStore.isAuthenticated) {
        try {
            await userStore.updateProfile({
                username: userStore.userInfo?.username || '',
                email: userStore.userInfo?.email || '',
                phone: guestInfo.phone,
                realName: guestInfo.name || userStore.userInfo?.realName || ''
            })
        } catch (error) {
            console.error('保存手机号到后端失败:', error)
            // 后端保存失败不影响本地保存的提示
        }
    }

    editingUserInfo.value = false
    ElMessage.success('旅客信息已保存，下次预订将自动填充')
}

// 显示协议
const showAgreement = () => {
    agreementDialogVisible.value = true
}

// 显示取消政策
const showCancellationPolicy = () => {
    cancellationDialogVisible.value = true
}

// 关闭弹窗
const handleCloseDialog = () => {
    agreementDialogVisible.value = false
    cancellationDialogVisible.value = false
}

// 同意条款
const agreeToTerms = () => {
    agreementChecked.value = true
    agreementDialogVisible.value = false
}

// 重新报价
const recalculatePrice = async () => {
    if (!orderData.value) return
    isRecalculating.value = true
    try {
        const { getPricingQuote } = await import('@/api/promotion')
        const res: any = await getPricingQuote({
            homestayId: orderData.value.homestayId,
            checkInDate: orderData.value.checkInDate,
            checkOutDate: orderData.value.checkOutDate,
            guestCount: orderData.value.guestCount,
            couponIds: selectedCouponIds.value,
        })
        const result = res.data || res
        // 更新订单金额
        orderData.value.totalAmount = result.payableAmount || result.totalPrice || orderData.value.totalAmount
        orderData.value.roomOriginalAmount = result.roomOriginalAmount
        orderData.value.activityDiscountAmount = result.activityDiscountAmount
        orderData.value.couponDiscountAmount = result.couponDiscountAmount
        orderData.value.quoteToken = result.quoteToken
        orderData.value.dailyPrices = result.dailyPrices
        availableCoupons.value = result.availableCoupons || []
    } catch (e) {
        console.error('重新报价失败:', e)
    } finally {
        isRecalculating.value = false
    }
}

const handleCouponChange = async (val: number | null) => {
    selectedCouponIds.value = val ? [val] : []
    await recalculatePrice()
}

// 提交订单
const submitOrder = async () => {
    if (!orderData.value) {
        ElMessage.error('订单数据不存在')
        return
    }

    if (!guestInfo.name || !guestInfo.phone) {
        ElMessage.warning('请填写联系人信息')
        return
    }

    if (!agreementChecked.value) {
        ElMessage.warning('请阅读并同意相关协议')
        return
    }

    // 【关键改进】提交前二次校验：重新拉取最新不可用日期，防止页面停留期间库存被抢占
    try {
        const availResponse = await getHomestayUnavailableDates(orderData.value.homestayId)
        const latestUnavailable: string[] = availResponse.data?.data || []
        const checkInStr = orderData.value.checkInDate
        const checkOutStr = orderData.value.checkOutDate

        // 检查入住日期区间内是否有不可用日期
        const hasConflict = latestUnavailable.some(d => d > checkInStr && d < checkOutStr)

        if (hasConflict) {
            ElMessage.warning('您选择的日期已被其他用户预订，请返回重新选择')
            // 标记冲突状态并刷新
            dateConflictFlag.value = true
            await fetchUnavailableDates()
            // 清除日期
            orderData.value.checkInDate = ''
            orderData.value.checkOutDate = ''
            orderData.value.nights = 0
            return
        }
    } catch (e) {
        console.warn('预校验可用性失败，跳过预检直接提交:', e)
        // 预检失败不影响正常提交流程
    }

    try {
        submitting.value = true
        const orderRequestData = {
            homestayId: orderData.value.homestayId,
            checkInDate: orderData.value.checkInDate,
            checkOutDate: orderData.value.checkOutDate,
            guestCount: orderData.value.guestCount,
            totalAmount: orderData.value.totalAmount,
            guestName: guestInfo.name,
            guestPhone: guestInfo.phone,
            remark: guestInfo.message,
            quoteToken: orderData.value.quoteToken,
            couponIds: selectedCouponIds.value
        }

        const response = await createOrder(orderRequestData)
        if (response && response.data && response.data.id) {
            const successMessage = orderData.value?.autoConfirm
                ? '预订成功！订单已确认，正在跳转支付页面...'
                : '预订申请已提交！等待房东确认'
            ElMessage.success(successMessage)

            // 根据autoConfirm决定跳转的页面
            if (orderData.value?.autoConfirm) {
                // 自动确认的房源直接跳转到支付页面，并传递autoConfirm参数
                router.push({
                    path: `/orders/${response.data.id}/pay`,
                    query: { autoConfirm: 'true' }
                })
            } else {
                // 房东确认的房源跳转到提交成功页面
                router.push(`/orders/submit-success/${response.data.id}`)
            }
        } else {
            ElMessage.error('订单创建失败')
        }
    } catch (error: any) {
        console.error('提交订单出错:', error)

        // 解析错误响应，检测是否是日期冲突错误
        // 后端 GlobalExceptionHandler 将 IllegalArgumentException 转为 500 错误
        // 实际错误消息藏在 response.data.data.error（开发模式）或 response.data.message
        const httpStatus = error?.response?.status
        const errorMsg =
            error?.response?.data?.data?.error ||
            error?.response?.data?.message ||
            error?.message ||
            String(error)

        // 价格变动检测（优先于日期冲突，因为也是 409）
        const isPriceChanged = error?.response?.data?.data?.error === 'PRICE_CHANGED'

        if (isPriceChanged) {
            const latestQuote = error?.response?.data?.data?.latestQuote || error?.response?.data?.latestQuote
            const newAmount = latestQuote?.payableAmount || latestQuote?.totalPrice
            ElMessageBox.confirm(
                `价格已发生变化，最新总价为 ¥${newAmount}，是否按最新价格继续下单？`,
                '价格变动提醒',
                {
                    confirmButtonText: '确认最新价格并继续',
                    cancelButtonText: '返回修改',
                    type: 'warning',
                    center: true
                }
            ).then(() => {
                if (orderData.value && latestQuote) {
                    orderData.value.quoteToken = latestQuote.quoteToken
                    orderData.value.totalAmount = newAmount
                }
                submitOrder()
            }).catch(() => {
                ElMessage.info('已取消，请重新选择日期或优惠券')
            })
            return
        }

        // 改进的冲突检测：优先通过 HTTP 状态码判断，其次通过错误消息
        const isConflictError =
            httpStatus === 409 ||  // 明确的状态码表示冲突
            httpStatus === 400 ||  // 某些冲突返回 400
            errorMsg.includes('conflict') ||
            errorMsg.includes('已被预订') ||
            errorMsg.includes('不可用') ||
            errorMsg.includes('所选日期') ||
            errorMsg.includes('该日期') ||
            errorMsg.includes('已被其他用户') ||
            errorMsg.includes('日期冲突')

        if (isConflictError) {
            // 日期冲突：保留旅客信息到sessionStorage
            dateConflictFlag.value = true

            // 保存旅客信息到 sessionStorage，供返回时恢复
            sessionStorage.setItem('guest-info-reserved', JSON.stringify({
                name: guestInfo.name,
                phone: guestInfo.phone,
                message: guestInfo.message
            }))

            // 重新获取最新不可用日期
            await fetchUnavailableDates()

            // 清除日期，标记冲突状态
            if (orderData.value) {
                orderData.value.checkInDate = ''
                orderData.value.checkOutDate = ''
                orderData.value.nights = 0
            }

            // 使用更醒目的对话框提示，而非 toast
            ElMessageBox.alert(
                '您所选的日期已被其他用户预订，请返回房源页重新选择日期。\n\n您的旅客联系信息已保留，返回后可快速重新预订。',
                '日期已被预订',
                {
                    confirmButtonText: '返回重新选择日期',
                    cancelButtonText: '留在当前页',
                    type: 'warning',
                    center: true,
                    distinguishCancelAndClose: true
                }
            ).then(() => {
                // 用户点击确定，返回重新选择日期
                goBackToSelectDates()
            }).catch((action) => {
                // 用户点击取消或关闭，留在当前页
                if (action !== 'cancel' && action !== 'close') {
                    // 其他情况也尝试返回
                    goBackToSelectDates()
                }
            })
        } else {
            ElMessage.error('订单提交失败，请稍后重试')
        }
    } finally {
        submitting.value = false
    }
}

// 获取最新不可用日期（用于冲突后刷新）
const fetchUnavailableDates = async () => {
    if (!orderData.value?.homestayId) return

    try {
        const response = await getHomestayUnavailableDates(orderData.value.homestayId)
        const dates = response.data?.data || []
        // 将更新后的不可用日期通过事件通知父组件
        // 注意：由于 OrderConfirm 不是 BookingCard 的直接父组件，
        // 这里实际上需要通过 sessionStorage 或其他机制来传递更新
        sessionStorage.setItem('unavailable-dates-updated', JSON.stringify(dates))
        console.log('已更新不可用日期:', dates)
    } catch (err) {
        console.error('获取不可用日期失败:', err)
    }
}

// 返回上一页
const goBack = () => {
    router.back()
}

// 返回房源详情页重新选择日期
const goBackToSelectDates = () => {
    if (orderData.value?.homestayId) {
        // 携带日期冲突标记返回，让 BookingCard 知道要恢复旅客信息
        router.push({
            path: `/homestay/${orderData.value.homestayId}`,
            query: { dateConflict: 'true' }
        })
    } else {
        router.back()
    }
}

// 自动填充用户信息
const populateUserInfo = async () => {
    try {
        if (userStore.isAuthenticated && userStore.isAuthenticated) {
            // 先从userStore尝试获取缓存的用户信息
            if (userStore.userInfo) {
                guestInfo.name = userStore.userInfo.realName || userStore.userInfo.username || ''
                guestInfo.phone = userStore.userInfo.phone || ''
                return
            }

            // 如果没有缓存的用户信息，则从API获取
            const userId = userStore.user?.id
            if (userId) {
                const response = await getUserInfo(userId)
                if (response && response.data) {
                    guestInfo.name = response.data.realName || response.data.username || ''
                    guestInfo.phone = response.data.phone || ''
                }
            } else {
                // 尝试获取当前用户信息
                const currentResponse = await getUserInfo()
                if (currentResponse && currentResponse.data) {
                    guestInfo.name = currentResponse.data.realName || currentResponse.data.username || ''
                    guestInfo.phone = currentResponse.data.phone || ''
                }
            }
        }
    } catch (error) {
        console.error('获取用户信息失败:', error)
    }
}

// 更新fetchHostInfo函数来获取真实的房东信息
const fetchHostInfo = async () => {
    if (!orderData.value?.homestayId) return;

    try {
        // 获取房源详情，现在应包含完整的房东信息
        const response = await getHomestayById(orderData.value.homestayId);
        if (response && response.data) {
            // 更新房源详细信息 (保留原有逻辑，但现在应有更全数据)
            if (orderData.value) {
                orderData.value = {
                    ...orderData.value,
                    address: response.data.addressDetail || orderData.value.address,
                    provinceCode: response.data.provinceCode || '',
                    cityCode: response.data.cityCode || '',
                    districtCode: response.data.districtCode || '',
                    imageUrl: processImageUrl(response.data.coverImage || response.data.images?.[0]?.url || response.data.images?.[0]) || orderData.value.imageUrl,
                    homestayTitle: response.data.title || orderData.value.homestayTitle,
                };
                console.log('获取到房源详情:', response.data);
            }

            // 直接使用getHomestayById返回的房东信息
            const ownerName = response.data.ownerName || response.data.ownerUsername;
            const ownerAvatar = response.data.ownerAvatar;
            const ownerRating = response.data.rating;

            if (ownerName) {
                hostInfo.value = {
                    name: ownerName || '房东',
                    avatar: processImageUrl(ownerAvatar) || '',
                    rating: ownerRating !== undefined && ownerRating !== null ? Number(ownerRating) : (hostInfo.value.rating || 4.5)
                };
                console.log('已更新房东信息 (来自房源详情):', hostInfo.value);
            } else if (response.data.hostInfo) {
                hostInfo.value = {
                    name: response.data.hostInfo.name || '房东',
                    avatar: processImageUrl(response.data.hostInfo.avatar) || '',
                    rating: response.data.hostInfo.rating !== undefined && response.data.hostInfo.rating !== null ? Number(response.data.hostInfo.rating) : 4.5
                };
                console.log('已更新房东信息 (来自嵌套 hostInfo):', hostInfo.value);
            } else {
                console.warn('房源详情中未找到房东信息');
            }
        }
    } catch (error) {
        console.error('获取房源和房东信息失败:', error);
        // 可以保留一个简单的备用逻辑，但避免错误的API调用
        // 例如，如果API调用失败，显示默认信息
        hostInfo.value = {
            name: '房东信息加载失败',
            avatar: '',
            rating: 0
        };
    }
}

// 修改initOrderData函数，优先从session storage读取数据
const initOrderData = () => {
    // 优先从session storage获取完整数据
    const storedData = sessionStorage.getItem('booking-details');
    if (storedData) {
        try {
            const bookingDetails = JSON.parse(storedData);
            console.log('从session storage加载订单数据:', bookingDetails);

            // 构建订单数据对象（优先使用后端报价结果）
            const baseAmount = bookingDetails.homestayData.price * bookingDetails.nights;
            const totalAmount = bookingDetails.totalPrice || baseAmount + (bookingDetails.cleaningFee || 0) + (bookingDetails.serviceFee || 0);

            return {
                homestayId: bookingDetails.homestayId,
                homestayTitle: bookingDetails.homestayData.title || '未命名房源',
                imageUrl: bookingDetails.homestayData.coverImage || '',
                address: bookingDetails.homestayData.addressDetail || '地址未知',
                provinceCode: '',
                cityCode: '',
                districtCode: '',
                checkInDate: bookingDetails.checkInDate,
                checkOutDate: bookingDetails.checkOutDate,
                nights: bookingDetails.nights,
                guestCount: bookingDetails.guestCount,
                price: bookingDetails.homestayData.price,
                baseAmount: baseAmount,
                cleaningFee: bookingDetails.cleaningFee,
                serviceFee: bookingDetails.serviceFee,
                totalAmount: totalAmount,
                hostId: bookingDetails.homestayData.ownerId || 0,
                hostName: bookingDetails.homestayData.ownerName || '',
                autoConfirm: bookingDetails.homestayData.autoConfirm || false,
                quoteToken: bookingDetails.quoteToken,
                couponIds: bookingDetails.couponIds,
                roomOriginalAmount: bookingDetails.roomOriginalAmount,
                activityDiscountAmount: bookingDetails.activityDiscountAmount,
                couponDiscountAmount: bookingDetails.couponDiscountAmount,
                appliedPromotions: bookingDetails.appliedPromotions,
                availableCoupons: bookingDetails.availableCoupons,
                dailyPrices: bookingDetails.dailyPrices
            } as OrderData;
        } catch (error) {
            console.error('解析session storage数据失败:', error);
            // 清除损坏的数据
            sessionStorage.removeItem('booking-details');
        }
    }

    // 回退到URL参数（兼容旧的链接方式）
    const query = route.query
    if (!query.homestayId || !query.checkIn || !query.checkOut || !query.guests) {
        ElMessage.error('参数不完整，无法创建订单')
        return null
    }

    // 确保homestayId被正确转换为数字
    const homestayId = Number(query.homestayId)
    if (isNaN(homestayId) || homestayId <= 0) {
        ElMessage.error('无效的房源ID')
        return null
    }

    // 从查询参数中获取基础数据
    const price = Number(query.price) || 0
    const nights = Number(query.nights) || 1
    const cleaningFee = Number(query.cleaningFee) || 0
    const serviceFee = Number(query.serviceFee) || 0

    // 计算基础金额和总金额
    const calculatedBaseAmount = price * nights
    const calculatedTotalAmount = calculatedBaseAmount + cleaningFee + serviceFee

    // 构建订单数据对象，使用计算后的金额
    return {
        homestayId: homestayId,
        homestayTitle: query.title as string || '未命名房源',
        imageUrl: query.image as string || '',
        address: query.address as string || '地址未知',
        provinceCode: query.provinceCode as string || '',
        cityCode: query.cityCode as string || '',
        districtCode: query.districtCode as string || '',
        checkInDate: query.checkIn as string,
        checkOutDate: query.checkOut as string,
        nights: nights,
        guestCount: Number(query.guests) || 1,
        price: price,
        baseAmount: calculatedBaseAmount, // 使用计算值
        cleaningFee: cleaningFee,
        serviceFee: serviceFee,
        totalAmount: calculatedTotalAmount, // 使用计算值
        hostId: Number(query.hostId) || 0,
        hostName: query.hostName as string || '',
        autoConfirm: query.autoConfirm === 'true'
    } as OrderData
}

// 添加一个函数来处理图片URL
const processImageUrl = (url: string | undefined): string => {
    if (!url) {
        return 'https://picsum.photos/400/300'; // 默认占位图片
    }

    // 如果是完整URL，直接返回
    if (url.startsWith('http')) {
        return url;
    }

    // 如果是相对路径，添加API前缀
    if (url.startsWith('/uploads/') || url.startsWith('/homestays/') || url.startsWith('/images/')) {
        return `/api${url}`;
    }

    // 如果是其他格式，假设是文件名，添加完整路径
    return `/api/uploads/homestays/${url}`;
}

const formattedAddress = computed(() => {
    if (!orderData.value) return '地址加载中...';

    const parts = [];
    const provinceCode = orderData.value.provinceCode;
    const cityCode = orderData.value.cityCode;
    const districtCode = orderData.value.districtCode;
    const addressDetail = orderData.value.address;

    if (provinceCode && codeToText[provinceCode]) {
        parts.push(codeToText[provinceCode]);
    }
    if (cityCode && codeToText[cityCode]) {
        if (!parts.includes(codeToText[cityCode])) {
            parts.push(codeToText[cityCode]);
        }
    }
    if (districtCode && codeToText[districtCode]) {
        parts.push(codeToText[districtCode]);
    }

    const regionPath = parts.join(' · ');

    if (addressDetail) {
        return regionPath ? `${regionPath} ${addressDetail}` : addressDetail;
    }

    return regionPath || '地址待更新';
});

onMounted(async () => {
    try {
        // 初始化订单数据
        console.log('初始化订单数据，查询参数:', route.query)
        const initialData = initOrderData()
        if (initialData) {
            orderData.value = initialData
            selectedCouponIds.value = initialData.couponIds || []
            availableCoupons.value = initialData.availableCoupons || []
            console.log('订单数据已初始化:', orderData.value)

            // 获取用户信息和房东信息
            await Promise.all([populateUserInfo(), fetchHostInfo()]);

            // 如果有quoteToken但没有重新报价过，尝试获取最新报价
            if (orderData.value?.quoteToken) {
                await recalculatePrice()
            }
        }
    } catch (error) {
        console.error('初始化订单数据失败:', error)
        ElMessage.error('加载订单数据失败')
    } finally {
        loading.value = false
    }
})

// 页面卸载时清理session storage
onUnmounted(() => {
    sessionStorage.removeItem('booking-details');
})
</script>

<style scoped>
.order-confirm-container {
    max-width: 1000px;
    margin: 0 auto;
    padding: 20px;
}

.loading-container,
.error-container {
    min-height: 400px;
    display: flex;
    justify-content: center;
    align-items: center;
}

.order-content {
    margin-top: 20px;
}

h1 {
    font-size: 28px;
    font-weight: 700;
    margin-bottom: 24px;
}

h2 {
    font-size: 20px;
    font-weight: 600;
    margin-bottom: 16px;
}

.order-summary-card,
.guest-info-section,
.price-details-section,
.payment-method-section {
    background-color: #fff;
    border-radius: 8px;
    padding: 24px;
    margin-bottom: 24px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.homestay-info {
    display: flex;
    gap: 20px;
}

.homestay-image {
    position: relative;
    width: 180px;
    height: 140px;
    border-radius: 8px;
    overflow: hidden;
}

.homestay-image img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 0.3s ease;
}

.homestay-image:hover img {
    transform: scale(1.05);
}

.image-overlay {
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    background: rgba(0, 0, 0, 0.5);
    color: white;
    padding: 4px 8px;
    font-size: 12px;
    text-align: center;
    cursor: pointer;
}

.homestay-details h3 {
    font-size: 18px;
    margin-bottom: 8px;
}

.homestay-details p {
    color: #666;
    margin-bottom: 4px;
}

.date-highlight {
    font-weight: bold;
    color: #409EFF;
}

.host-info {
    display: flex;
    align-items: center;
    margin-top: 12px;
    padding-top: 8px;
    border-top: 1px dashed #eee;
}

.host-avatar {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    margin-right: 10px;
}

.host-name {
    font-weight: bold;
    margin-bottom: 2px;
}

.checkin-checkout-section {
    display: flex;
    gap: 20px;
    margin: 20px 0;
    padding: 15px;
    background-color: #f9f9f9;
    border-radius: 8px;
}

.checkin-info,
.checkout-info {
    flex: 1;
    padding: 10px;
    border-radius: 5px;
    background-color: white;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.checkin-info h3,
.checkout-info h3 {
    margin-top: 0;
    color: #303133;
    font-size: 16px;
    margin-bottom: 10px;
}

.time-info,
.date-info {
    margin: 5px 0;
    display: flex;
    align-items: center;
}

.time-info i,
.date-info i {
    margin-right: 5px;
    color: #909399;
}

.order-rules-section {
    margin: 20px 0;
    padding: 15px;
    background-color: #fff;
    border: 1px solid #e6e6e6;
    border-radius: 8px;
}

.rules-content {
    margin-top: 10px;
}

.rule-item {
    display: flex;
    align-items: flex-start;
    margin-bottom: 10px;
}

.rule-item i {
    color: #E6A23C;
    margin-right: 10px;
    margin-top: 3px;
}

.guest-info-section,
.price-details-section,
.payment-method-section {
    margin: 20px 0;
    padding: 20px;
    background-color: white;
    border-radius: 8px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.payment-option-content {
    display: flex;
    align-items: center;
    gap: 8px;
}

.payment-icon {
    width: 20px;
    height: 20px;
}

.agreement-section {
    margin: 20px 0;
    display: flex;
    align-items: center;
    flex-wrap: wrap;
}

.agreement-link {
    color: #409EFF;
    margin: 0 8px;
    text-decoration: none;
}

.agreement-link:hover {
    text-decoration: underline;
}

.agreement-content,
.cancellation-content {
    max-height: 400px;
    overflow-y: auto;
}

.agreement-content h3,
.cancellation-content h3 {
    margin-top: 20px;
    margin-bottom: 10px;
    font-size: 16px;
}

.agreement-content p,
.cancellation-content p {
    margin-bottom: 8px;
    line-height: 1.5;
}

.total-price-display {
    margin-bottom: 15px;
    font-size: 18px;
}

.total-price-label {
    font-weight: normal;
    margin-right: 10px;
}

.total-price-value {
    font-weight: bold;
    color: #f56c6c;
    font-size: 24px;
}

.submit-section {
    margin-top: 30px;
    text-align: center;
    display: flex;
    flex-direction: column;
    align-items: center;
}

.submit-section .el-button {
    padding: 12px 24px;
    font-size: 16px;
}

.submit-section .el-button--primary {
    width: 200px;
}

.order-notice {
    margin-bottom: 20px;
}

.conflict-alert {
    margin-bottom: 12px;
    border: 1px solid #e6a23c;
}

.conflict-alert :deep(.el-alert__title) {
    font-weight: 600;
}

.conflict-actions {
    display: flex;
    align-items: center;
    gap: 16px;
    margin-top: 4px;
}

.price-breakdown {
    margin-top: 16px;
}

.price-row {
    display: flex;
    justify-content: space-between;
    margin-bottom: 12px;
}

.price-row.total {
    border-top: 1px solid #eee;
    padding-top: 12px;
    font-weight: bold;
    font-size: 18px;
}

.price-guarantee {
    margin-top: 12px;
    padding: 8px;
    background-color: #f0f9eb;
    border-radius: 4px;
    display: flex;
    align-items: center;
    font-size: 13px;
    color: #67c23a;
}

.price-guarantee i {
    margin-right: 8px;
}

.payment-options {
    margin-top: 16px;
}

/* 新增和修改的样式 */
.card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
}

.homestay-id {
    color: #909399;
    font-size: 14px;
}

.homestay-address {
    display: flex;
    align-items: center;
    gap: 5px;
    color: #606266;
}

.host-contact {
    margin-top: 5px;
}

.info-display {
    background-color: #f9f9f9;
    padding: 15px;
    border-radius: 8px;
}

.info-item {
    display: flex;
    margin-bottom: 10px;
}

.info-item .label {
    width: 100px;
    color: #606266;
    font-weight: 500;
}

.edit-button {
    text-align: right;
    margin-top: 10px;
}

.form-actions {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
    margin-top: 15px;
}

.guest-info-content {
    margin-top: 15px;
}

.coupon-selector {
    margin-top: 12px;
    padding: 0 8px;
}

/* 每日价格展开 */
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

/* 响应式调整 */
@media (max-width: 768px) {
    .info-item {
        flex-direction: column;
    }

    .info-item .label {
        width: 100%;
        margin-bottom: 5px;
    }
}
</style>
