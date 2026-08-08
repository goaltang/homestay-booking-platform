<template>
    <div class="order-detail-container">
        <div v-if="loading" class="loading-container">
            <el-skeleton :rows="10" animated />
        </div>
        <div v-else-if="!orderData" class="error-container">
            <el-empty description="订单不存在">
                <template #description>
                    <p>未找到订单信息，请返回订单列表</p>
                </template>
                <el-button type="primary" @click="goToOrders">查看我的订单</el-button>
            </el-empty>
        </div>
        <div v-else class="order-content">
            <div class="order-header">
                <h1>订单详情</h1>
            </div>

            <!-- 订单状态流程提示 -->
            <div class="order-status-flow card-style">
                <el-steps :active="getStatusStep(orderData.status)" 
                          :process-status="['REJECTED', 'CANCELLED', 'CANCELLED_SYSTEM', 'CANCELLED_BY_USER', 'CANCELLED_BY_HOST'].includes(orderData.status) ? 'error' : (orderData.paymentStatus === 'PAID' && orderData.refundRejectionReason ? 'error' : 'process')"
                          finish-status="success" align-center>
                    <el-step title="预订申请" />
                    <el-step title="房东确认" />
                    <el-step title="支付订单" />
                    <el-step title="入住" />
                    <el-step title="完成" />
                </el-steps>
            </div>

            <!-- 订单信息卡片 -->
            <div class="order-summary-card card-style">
                <div class="card-header">
                    <h2>订单信息</h2>
                    <div class="header-right">
                        <span class="order-number">订单号: {{ orderData.orderNumber }}</span>
                        <el-tag :type="getStatusType(orderData.status, orderData.paymentStatus, orderData.refundRejectionReason)" size="large" class="status-tag" effect="light">
                            {{ getStatusText(orderData.status, orderData.paymentStatus, orderData.refundType, orderData.refundRejectionReason, orderData.disputeResolution) }}
                        </el-tag>
                    </div>
                </div>
                <div class="homestay-info-wrapper">
                    <div class="homestay-image">
                        <img :src="processImageUrl(orderData.imageUrl)" alt="房源图片" @error="handleImageErrorEvent">
                    </div>
                    <div class="homestay-details">
                        <h3>{{ orderData.homestayTitle }}</h3>
                        <p class="detail-row"><el-icon><Location /></el-icon> {{ orderData.address }}</p>
                        <p class="detail-row"><el-icon><Calendar /></el-icon> {{ formatDateRange(orderData.checkInDate, orderData.checkOutDate) }}</p>
                        <p class="detail-row"><el-icon><User /></el-icon> {{ orderData.guestCount }}位房客 · {{ orderData.nights }}晚</p>
                    </div>
                </div>

                <!-- 旅客信息整合到订单信息中 -->
                <div class="guest-info-grid">
                    <div class="guest-info-item">
                        <div class="guest-icon"><el-icon><UserFilled /></el-icon></div>
                        <div class="guest-text">
                            <span class="label">联系人</span>
                            <span class="value">{{ orderData.guestName }}</span>
                        </div>
                    </div>
                    <div class="guest-info-item">
                        <div class="guest-icon"><el-icon><PhoneFilled /></el-icon></div>
                        <div class="guest-text">
                            <span class="label">联系电话</span>
                            <span class="value">{{ orderData.guestPhone }}</span>
                        </div>
                    </div>
                    <div class="guest-info-item remark-item" v-if="orderData.remark && orderData.status !== 'REJECTED'">
                        <div class="guest-icon"><el-icon><EditPen /></el-icon></div>
                        <div class="guest-text">
                            <span class="label">备注</span>
                            <span class="value">{{ orderData.remark }}</span>
                        </div>
                    </div>
                </div>
            </div>

            <!-- 预订状态提示 -->
            <div class="status-notice" v-if="orderData.status === 'PENDING'">
                <el-alert title="等待房东确认" type="warning" description="您的预订申请已提交" show-icon :closable="false" />
                <OrderTimeoutIndicator :order-id="orderData.id" :order-status="orderData.status as OrderStatus"
                    :create-time="orderData.createTime" :confirm-time="orderData.updateTime"
                    :update-time="orderData.updateTime" @timeout="handleOrderTimeout" @warning="handleOrderWarning" />
            </div>

            <div class="status-notice" v-else-if="orderData.status === 'CONFIRMED'">
                <el-alert title="房东已确认预订" type="success" description="请及时完成支付" show-icon :closable="false" />
                <OrderTimeoutIndicator :order-id="orderData.id" :order-status="orderData.status as OrderStatus"
                    :create-time="orderData.createTime" :confirm-time="orderData.updateTime"
                    :update-time="orderData.updateTime" @timeout="fetchOrderDetail" @warning="handleOrderWarning" />
            </div>

            <!-- 待入住状态提示 -->
            <div class="status-notice" v-else-if="orderData.status === 'READY_FOR_CHECKIN'">
                <el-alert title="房源已准备好，等待入住" type="info" description="房东已为您准备好入住凭证，请查看并完成入住" show-icon :closable="false" />
                <div class="checkin-actions-tip" style="margin-top: 15px;">
                    <el-button type="primary" size="small" @click="openCheckInCredential">查看入住凭证</el-button>
                    <el-button type="success" size="small" @click="selfCheckInDialogVisible = true">自助入住</el-button>
                    <el-button type="warning" plain size="small" @click="handleConfirmArrival" :loading="confirmArrivalLoading">确认到达</el-button>
                </div>
            </div>

            <!-- 已入住状态提示 -->
            <div class="status-notice" v-else-if="orderData.status === 'CHECKED_IN'">
                <el-alert title="已办理入住" type="success" description="祝您入住愉快！" show-icon :closable="false" />
            </div>

            <!-- 已退房状态提示 -->
            <div class="status-notice" v-else-if="orderData.status === 'CHECKED_OUT'">
                <el-alert title="已办理退房" type="info" description="等待房东确认结算" show-icon :closable="false" />
            </div>

            <div class="status-notice" v-else-if="orderData.status === 'REJECTED'">
                <el-alert title="预订被拒绝" type="error" description="很抱歉，房东拒绝了您的预订申请。" show-icon :closable="false" />
                <div class="reject-reason" v-if="orderData.remark">
                    <h4>拒绝原因:</h4>
                    <p>{{ extractRejectReason(orderData.remark) }}</p>
                </div>
            </div>

            <!-- 退款状态提示 -->
            <div class="status-notice" v-else-if="isRefundStatus">
                <el-alert :title="getRefundStatusTitle" :type="getRefundStatusType" :description="getRefundStatusDesc"
                    show-icon :closable="false" />
                <!-- 进度提示条 -->
                <div class="refund-progress-tip" v-if="orderData.paymentStatus === 'REFUND_PENDING'">
                    <el-icon><Clock /></el-icon>
                    <span>预计1-3个工作日内到账，退款将原路退回您的支付账户</span>
                </div>
                <div class="refund-progress-tip success" v-else-if="orderData.paymentStatus === 'REFUNDED'">
                    <el-icon><CircleCheck /></el-icon>
                    <span>退款已完成，资金已原路退回您的支付账户</span>
                </div>
                <div class="refund-progress-tip danger" v-else-if="orderData.paymentStatus === 'REFUND_FAILED'">
                    <el-icon><CircleClose /></el-icon>
                    <span>退款失败，请联系客服处理。如有拒绝原因请查看下方退款信息</span>
                </div>
            </div>



            <!-- 价格详情 -->
            <div class="price-details-section receipt-style">
                <div class="receipt-header">
                    <h2>{{ isRefundStatus ? '费用明细' : '价格详情' }}</h2>
                </div>
                
                <div class="receipt-body">
                    <div class="price-item">
                        <span class="item-name">每晚价格 x {{ orderData.nights }}晚</span>
                        <span class="item-price">¥{{ Math.round((orderData.price ?? 0) * orderData.nights * 100) / 100 }}</span>
                    </div>
                    <div class="price-item" v-if="orderData.cleaningFee">
                        <span class="item-name">清洁费</span>
                        <span class="item-price">¥{{ orderData.cleaningFee }}</span>
                    </div>
                    <div class="price-item" v-if="orderData.serviceFee">
                        <span class="item-name">服务费</span>
                        <span class="item-price">¥{{ orderData.serviceFee }}</span>
                    </div>
                    
                    <template v-if="isRefundStatus && orderData?.refundAmount">
                        <div class="price-item refund-item">
                            <span class="item-name">退款金额</span>
                            <span class="refund-amount">-¥{{ orderData?.refundAmount }}</span>
                        </div>
                    </template>
                </div>
                
                <!-- 价格分割线 (类似小票齿孔线) -->
                <div class="receipt-divider"></div>
                
                <div class="receipt-footer">
                    <div class="price-total">
                        <span>原始总价</span>
                        <span class="total-amount">¥{{ orderData.totalAmount }}</span>
                    </div>
                    
                    <div class="price-total final-amount" v-if="isRefundStatus && orderData?.paymentStatus === 'REFUNDED'">
                        <span>实际扣费</span>
                        <span class="actual-amount">¥{{ (orderData?.totalAmount || 0) - (orderData?.refundAmount || 0) }}</span>
                    </div>
                </div>
            </div>

            <!-- 退款信息 -->
            <div v-if="hasRefundInfo" class="refund-details-section">
                <h2>退款信息</h2>
                <div class="info-item">
                    <span class="label">退款类型:</span>
                    <span>{{ getRefundTypeText(orderData.refundType).replace(/[（）]/g, '') }}</span>
                </div>
                <div v-if="orderData.refundInitiatedByName" class="info-item">
                    <span class="label">发起人:</span>
                    <span>{{ orderData.refundInitiatedByName }}</span>
                </div>
                <div v-if="orderData.refundInitiatedAt" class="info-item">
                    <span class="label">申请时间:</span>
                    <span>{{ formatDateTime(orderData.refundInitiatedAt) }}</span>
                </div>
                <div v-if="orderData.refundReason" class="info-item">
                    <span class="label">退款原因:</span>
                    <span>{{ orderData.refundReason }}</span>
                </div>
                <div v-if="orderData.refundAmount" class="info-item">
                    <span class="label">退款金额:</span>
                    <span class="refund-amount">¥{{ orderData.refundAmount }}</span>
                </div>
                <!-- 拒绝原因（退款被拒绝时显示） -->
                <div v-if="orderData.refundRejectionReason" class="info-item rejection-reason-item">
                    <span class="label">拒绝原因:</span>
                    <span class="rejection-reason-text">❌ {{ orderData.refundRejectionReason }}</span>
                </div>
                <div v-if="orderData.refundProcessedByName" class="info-item">
                    <span class="label">处理人:</span>
                    <span>{{ orderData.refundProcessedByName }}</span>
                </div>
                <div v-if="orderData.refundProcessedAt" class="info-item">
                    <span class="label">处理时间:</span>
                    <span>{{ formatDateTime(orderData.refundProcessedAt) }}</span>
                </div>
                <div v-if="orderData.refundTransactionId" class="info-item">
                    <span class="label">退款交易号:</span>
                    <span>{{ orderData.refundTransactionId }}</span>
                </div>
            </div>

            <!-- 评价详情 (如果已完成且已评价) -->
            <div class="review-details-section" v-if="orderData?.status === 'COMPLETED' && orderData.review">
                <div class="review-header">
                    <h2>我的评价</h2>
                    <div class="review-actions">
                        <el-button v-if="canEditReview(orderData.review)" type="primary" plain size="small"
                            @click="openEditModal(orderData.review)">
                            修改评价
                        </el-button>
                        <el-button v-if="canDeleteReview(orderData.review)" type="danger" plain size="small"
                            @click="handleDeleteReview(orderData.review.id)" :loading="isDeletingReview"
                            class="delete-review-btn">
                            删除评价
                        </el-button>
                    </div>
                </div>
                <div class="review-content">
                    <div class="rating-line">
                        <span class="label">评分:</span>
                        <el-rate :model-value="orderData.review.rating" disabled size="small" text-color="#ff9900" />
                    </div>
                    <!-- 细分评分 -->
                    <div class="detailed-ratings" v-if="orderData.review.cleanlinessRating || orderData.review.accuracyRating">
                        <el-row :gutter="10">
                            <el-col :span="8" v-if="orderData.review.cleanlinessRating"><span class="det-label">清洁度</span><el-rate :model-value="orderData.review.cleanlinessRating" disabled size="small" /></el-col>
                            <el-col :span="8" v-if="orderData.review.accuracyRating"><span class="det-label">准确性</span><el-rate :model-value="orderData.review.accuracyRating" disabled size="small" /></el-col>
                            <el-col :span="8" v-if="orderData.review.communicationRating"><span class="det-label">沟通</span><el-rate :model-value="orderData.review.communicationRating" disabled size="small" /></el-col>
                            <el-col :span="8" v-if="orderData.review.locationRating"><span class="det-label">位置</span><el-rate :model-value="orderData.review.locationRating" disabled size="small" /></el-col>
                            <el-col :span="8" v-if="orderData.review.checkInRating"><span class="det-label">入住</span><el-rate :model-value="orderData.review.checkInRating" disabled size="small" /></el-col>
                            <el-col :span="8" v-if="orderData.review.valueRating"><span class="det-label">性价比</span><el-rate :model-value="orderData.review.valueRating" disabled size="small" /></el-col>
                        </el-row>
                    </div>
                    <!-- 评价图片 -->
                    <div class="review-images" v-if="orderData.review.images && orderData.review.images.length > 0">
                        <el-image v-for="(img, idx) in orderData.review.images" :key="idx" :src="img" :preview-src-list="orderData.review.images" fit="cover" class="review-thumb" />
                    </div>
                    <div class="info-item">
                        <span class="label">评价内容:</span>
                        <p>{{ orderData.review.content }}</p>
                    </div>
                    <div class="info-item">
                        <span class="label">评价时间:</span>
                        <span>{{ formatDate(orderData.review.createTime) }}</span>
                    </div>
                    <div v-if="orderData.review.response" class="host-response-detail">
                        <el-divider direction="horizontal" />
                        <h4>房东回复:</h4>
                        <p>{{ orderData.review.response }}</p>
                        <div class="response-time" v-if="orderData.review.responseTime">
                            回复于: {{ formatDateTime(orderData.review.responseTime) }}
                        </div>
                    </div>
                </div>
            </div>

            <!-- 按钮操作区 -->
            <div class="action-buttons card-style">
                <el-button @click="goToOrders" class="btn-back">返回订单列表</el-button>

                <el-button type="primary" plain @click="contactAgent">
                    <el-icon>
                        <ChatDotRound />
                    </el-icon>
                    咨询 AI 客服
                </el-button>

                <!-- 退款被拒特有操作分支 -->
                <template v-if="orderData.paymentStatus === 'PAID' && orderData.refundRejectionReason">
                    <el-button type="default" plain @click="contactHost">联系房东</el-button>
                    <el-button type="warning" @click="confirmRequestRefund">再次申请退款</el-button>
                </template>

                <!-- 非退款状态下的正常操作 -->
                <template v-else-if="!isRefundStatus">
                    <!-- 联系房东按钮：已支付且未完成的订单可以联系房东 -->
                    <el-button type="default" plain
                        v-if="orderData.paymentStatus === 'PAID' && !['COMPLETED', 'CANCELLED'].includes(orderData.status)"
                        @click="contactHost">
                        联系房东
                    </el-button>
                    <el-button type="danger" plain v-if="canCancel" @click="confirmCancel">
                        取消订单
                    </el-button>
                    <el-button type="primary" size="large" v-if="['CONFIRMED', 'PAYMENT_PENDING'].includes(orderData.status)" @click="showPaymentDialog" class="primary-action-btn">
                        立即支付
                    </el-button>
                    <!-- READY_FOR_CHECKIN 状态：入住相关操作 -->
                    <el-button type="primary" v-if="orderData.status === 'READY_FOR_CHECKIN'" @click="openCheckInCredential">
                        查看入住凭证
                    </el-button>
                    <el-button type="success" v-if="orderData.status === 'READY_FOR_CHECKIN'" @click="selfCheckInDialogVisible = true">
                        自助入住
                    </el-button>
                    <el-button type="warning" plain v-if="orderData.status === 'READY_FOR_CHECKIN'" @click="handleConfirmArrival" :loading="confirmArrivalLoading">
                        确认到达
                    </el-button>
                    <!-- CHECKED_IN 状态 -->
                    <el-button type="warning" plain v-if="canRequestRefund" @click="confirmRequestRefund">
                        申请退款
                    </el-button>
                    <el-button type="primary" v-if="orderData.status === 'COMPLETED' && !orderData.review && isWithinReviewWindow(orderData)" @click="openReviewModal" class="primary-action-btn">
                        评价房源
                    </el-button>
                    <el-tooltip v-else-if="orderData.status === 'COMPLETED' && !orderData.review && !isWithinReviewWindow(orderData)" content="订单已完成超过30天，无法提交评价">
                        <el-button type="info" disabled class="primary-action-btn">评价已过期</el-button>
                    </el-tooltip>
                </template>

                <!-- 退款状态下的简化操作 -->
                <template v-else>
                    <el-button type="warning" @click="confirmRaiseDispute"
                        v-if="orderData.paymentStatus === 'REFUND_PENDING'">
                        申请争议
                    </el-button>
                    <el-button type="warning" @click="confirmRequestRefund"
                        v-if="orderData.paymentStatus === 'REFUND_FAILED'">
                        重试退款
                    </el-button>
                    <el-button type="info" plain @click="contactCustomerService"
                        v-if="orderData.paymentStatus === 'REFUND_FAILED'">
                        联系客服
                    </el-button>
                </template>
            </div>

        </div>

        <!-- 支付对话框 -->
        <el-dialog v-model="paymentDialogVisible" title="订单支付" width="400px" :close-on-click-modal="false"
            @close="handlePaymentDialogClose">
            <div class="payment-dialog-content">
                <template v-if="qrCodeLoading">
                    <div class="qr-loading" v-loading="true" element-loading-text="正在生成支付二维码..."></div>
                </template>
                <template v-else-if="paymentQrCode">
                    <div class="payment-qr-info">
                        <p class="payment-amount">支付金额: <span>¥{{ orderData?.totalAmount }}</span></p>
                        <div class="qr-code-wrapper">
                            <!-- 动态渲染：支持图片URL和原始数据 -->
                            <img v-if="paymentQrCode.startsWith('http')" :src="paymentQrCode" alt="支付二维码" class="qr-image" />
                            <qrcode-vue v-else :value="paymentQrCode" :size="200" level="H" />
                        </div>
                        <p class="payment-tip">请使用支付宝扫描二维码完成支付</p>

                        <!-- 模拟支付入口（仅开发环境显示） -->
                        <div class="mock-pay-section" v-if="isDev">
                            <el-divider>测试专用</el-divider>
                            <el-button type="success" @click="handleManualPay" :loading="payLoading" icon="CircleCheck">
                                模拟直接支付 (调用 payOrder API)
                            </el-button>
                            <p class="mock-tip">提示：此按钮将显式触发后端支付确认逻辑</p>
                        </div>

                        <el-divider />
                        <div class="payment-status-info">
                            <el-icon class="is-loading" v-if="isPolling">
                                <Loading />
                            </el-icon>
                            <span>{{ pollingStatusText }}</span>
                        </div>
                    </div>
                </template>
                <template v-else>
                    <el-empty description="二维码生成失败">
                        <el-button type="primary" @click="generateQrCode">重试</el-button>
                    </el-empty>
                </template>
            </div>
            <template #footer>
                <span class="dialog-footer">
                    <el-button @click="paymentDialogVisible = false">取消支付</el-button>
                    <el-button type="success" @click="checkPaymentStatus" :loading="checkingStatus">已完成支付</el-button>
                </span>
            </template>
        </el-dialog>

        <!-- 入住凭证查看对话框 -->
        <el-dialog v-model="checkInCredentialDialogVisible" title="入住凭证" width="450px">
            <div v-if="checkInCredential" class="credential-content">
                <el-descriptions :column="1" border>
                    <el-descriptions-item label="入住方式">
                        {{ checkInCredential.checkInMethod === 'MANUAL' ? '人工办理' : '自助入住' }}
                    </el-descriptions-item>
                    <el-descriptions-item label="入住码">
                        <span style="font-weight: bold; font-size: 20px; color: var(--el-color-primary);">
                            {{ checkInCredential.checkInCode }}
                        </span>
                    </el-descriptions-item>
                    <el-descriptions-item label="门锁密码" v-if="checkInCredential.doorPassword">
                        {{ checkInCredential.doorPassword }}
                    </el-descriptions-item>
                    <el-descriptions-item label="密钥箱密码" v-if="checkInCredential.lockboxCode">
                        {{ checkInCredential.lockboxCode }}
                    </el-descriptions-item>
                    <el-descriptions-item label="位置描述" :span="2" v-if="checkInCredential.locationDescription">
                        {{ checkInCredential.locationDescription }}
                    </el-descriptions-item>
                    <el-descriptions-item label="有效时间" v-if="checkInCredential.validFrom || checkInCredential.validUntil">
                        {{ checkInCredential.validFrom }} ~ {{ checkInCredential.validUntil }}
                    </el-descriptions-item>
                    <el-descriptions-item label="备注" :span="2" v-if="checkInCredential.remark">
                        {{ checkInCredential.remark }}
                    </el-descriptions-item>
                </el-descriptions>
                <el-alert type="info" :closable="false" style="margin-top: 15px;">
                    请保管好您的入住码，到达后可用于自助办理入住。
                </el-alert>
            </div>
            <template #footer>
                <el-button @click="checkInCredentialDialogVisible = false">关闭</el-button>
            </template>
        </el-dialog>

        <!-- 自助入住对话框 -->
        <el-dialog v-model="selfCheckInDialogVisible" title="自助入住" width="400px">
            <div class="self-checkin-content">
                <el-alert type="info" :closable="false" show-icon style="margin-bottom: 20px;">
                    请输入房东提供的6位入住码完成入住。
                </el-alert>
                <el-form>
                    <el-form-item label="入住码">
                        <el-input v-model="selfCheckInCode" placeholder="请输入6位入住码" maxlength="6" />
                    </el-form-item>
                </el-form>
            </div>
            <template #footer>
                <el-button @click="selfCheckInDialogVisible = false">取消</el-button>
                <el-button type="primary" @click="submitSelfCheckIn" :loading="selfCheckInLoading">确认入住</el-button>
            </template>
        </el-dialog>

        <!-- Add Edit Modal -->
        <ReviewEditModal v-model:visible="isEditModalVisible" :review-data="currentEditingReview"
            @submitted="handleReviewUpdated" />

        <!-- 评价模态框 (新增评价) -->
        <ReviewForm v-if="orderData && !orderData.review" v-model:visible="reviewDialogVisible" :order-id="orderData.id"
            :homestay-id="orderData.homestayId" :homestay-title="orderData.homestayTitle"
            @submit="handleSubmitReview" />
    </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, Clock, CircleCheck, CircleClose, Location, Calendar, User, UserFilled, PhoneFilled, EditPen, ChatDotRound } from '@element-plus/icons-vue'
import QrcodeVue from 'qrcode.vue'
import { cancelOrder, generatePaymentQRCode, checkPayment, payOrder, getCheckInCredential, selfCheckIn, confirmArrival } from '../../api/order'
import { getHomestayById } from '../../api/homestay'
import { getHomestayImageUrl, handleImageError } from '../../utils/image'
import dayjs from 'dayjs'
import { deleteReview, submitReview } from '@/api/review'
import { requestRefund, getRefundPreview, raiseDispute } from '@/api/refund'
import { useUserStore } from '@/stores/user'
import { useSupportAgentStore } from '@/stores/supportAgent'
import { useOrderStore, type ReviewItem, type OrderItem } from '@/stores/order'
import ReviewEditModal from '@/components/ReviewEditModal.vue'
import ReviewForm from '@/components/ReviewForm.vue'
import OrderTimeoutIndicator from '@/components/order/OrderTimeoutIndicator.vue'
import { OrderStatus } from '@/types/order'

// --- Add type for editable data ---
interface EditableReviewData {
    id: number;
    rating: number;
    content: string;
    images?: string[];
    cleanlinessRating?: number;
    accuracyRating?: number;
    communicationRating?: number;
    locationRating?: number;
    checkInRating?: number;
    valueRating?: number;
}
// --- End type ---

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const store = useOrderStore()
const agentStore = useSupportAgentStore()

const loading = ref(true)
const orderData = computed(() => store.currentOrder)
const isDeletingReview = ref(false)

// --- Add state for payment dialog ---
const paymentDialogVisible = ref(false)
const qrCodeLoading = ref(false)
const paymentQrCode = ref('')
const isPolling = ref(false)
const pollingTimer = ref<number | null>(null)
const pollingStatusText = ref('等待支付...')
const checkingStatus = ref(false)
const payLoading = ref(false)
// --- End state ---

// --- Add state for edit modal ---
const isEditModalVisible = ref(false);
const currentEditingReview = ref<EditableReviewData | null>(null);
const reviewDialogVisible = ref(false);

const openReviewModal = () => {
    reviewDialogVisible.value = true;
};

const handleSubmitReview = async (reviewData: any) => {
    try {
        await submitReview(reviewData);
        ElMessage.success('评价提交成功');
        reviewDialogVisible.value = false;
        fetchOrderDetail();
        store.fetchOrders();
        store.fetchStatsOrders();
    } catch (error: any) {
        const errMsg = error?.response?.data?.message || '评价提交失败，请稍后重试';
        ElMessage.error(errMsg);
    }
};
// --- End state ---

// --- Check-in related state ---
const checkInCredentialDialogVisible = ref(false)
const selfCheckInDialogVisible = ref(false)
const checkInCredential = ref<any>(null)
const selfCheckInCode = ref('')
const selfCheckInLoading = ref(false)
const confirmArrivalLoading = ref(false)

// 获取入住凭证
const openCheckInCredential = async () => {
    if (!orderData.value?.id) return
    try {
        const res = await getCheckInCredential(orderData.value.id)
        checkInCredential.value = res.data || res
        checkInCredentialDialogVisible.value = true
    } catch (error: any) {
        ElMessage.error(error.message || '获取入住凭证失败')
    }
}

// 自助入住
const submitSelfCheckIn = async () => {
    if (!selfCheckInCode.value) {
        ElMessage.warning('请输入入住码')
        return
    }
    selfCheckInLoading.value = true
    try {
        await selfCheckIn(selfCheckInCode.value)
        ElMessage.success('自助入住成功')
        selfCheckInDialogVisible.value = false
        fetchOrderDetail()
        store.fetchOrders()
        store.fetchStatsOrders()
    } catch (error: any) {
        ElMessage.error(error.message || '自助入住失败')
    } finally {
        selfCheckInLoading.value = false
    }
}

// 确认到达
const handleConfirmArrival = async () => {
    if (!orderData.value?.id) return
    ElMessageBox.confirm('确认已到达房源？', '确认到达', {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'info'
    }).then(async () => {
        confirmArrivalLoading.value = true
        try {
            await confirmArrival(orderData.value!.id)
            ElMessage.success('已通知房东您已到达')
            fetchOrderDetail()
            store.fetchOrders()
            store.fetchStatsOrders()
        } catch (error: any) {
            ElMessage.error(error.message || '确认到达失败')
        } finally {
            confirmArrivalLoading.value = false
        }
    }).catch(() => {})
}
// --- End check-in state ---

// 获取当前登录用户ID
const currentUserId = userStore.userInfo?.id

// 获取订单详情
const fetchOrderDetail = async () => {
    loading.value = true;
    try {
        const orderId = Number(route.params.id)
        if (isNaN(orderId)) {
            ElMessage.error('无效的订单ID')
            return;
        }

        await store.fetchOrderDetail(orderId)
        console.log('获取到的订单详情数据:', store.currentOrder);

        // 获取房源详情，更新房源图片
        if (store.currentOrder && store.currentOrder.homestayId && !store.currentOrder.imageUrl) {
            try {
                const homestayResponse = await getHomestayById(store.currentOrder.homestayId)
                if (homestayResponse && homestayResponse.data) {
                    const coverImage = homestayResponse.data.coverImage ||
                        (homestayResponse.data.images && homestayResponse.data.images.length > 0 ?
                            homestayResponse.data.images[0] : null);

                    if (coverImage && store.currentOrder) {
                        store.currentOrder.imageUrl = coverImage;
                    }
                }
            } catch (error) {
                console.error('获取房源详情失败:', error);
            }
        }

    } catch (error: any) {
        console.error('获取订单详情失败:', error);
        ElMessage.error('获取订单详情失败，请重试');
    } finally {
        loading.value = false;
    }
}

// 处理图片URL
const processImageUrl = (url?: string) => {
    return getHomestayImageUrl(url);
}

// 处理图片加载错误事件处理器
const handleImageErrorEvent = (event: Event) => {
    handleImageError(event, 'homestay');
}

// 计算是否可以取消订单
const canCancel = computed(() => {
    if (!orderData.value) return false

    // 只有待确认、已确认的订单可以取消
    return ['PENDING', 'CONFIRMED'].includes(orderData.value.status)
})

// 计算是否有退款信息
const hasRefundInfo = computed(() => {
    if (!orderData.value) return false

    return orderData.value.refundType ||
        orderData.value.refundReason ||
        orderData.value.refundAmount ||
        orderData.value.refundInitiatedByName ||
        orderData.value.refundProcessedByName
})

// 计算是否可以申请退款
const canRequestRefund = computed(() => {
    if (!orderData.value) return false

    // 只有已支付且未退款的订单可以申请退款
    // 注意：订单状态 status 没有 PAID 值，应该只判断 paymentStatus
    const isPaid = orderData.value.paymentStatus === 'PAID'
    const notInRefund = !['REFUND_PENDING', 'REFUNDED', 'REFUND_FAILED'].includes(orderData.value.paymentStatus || '')
    const notFinal = !['COMPLETED', 'CANCELLED'].includes(orderData.value.status)

    return isPaid && notInRefund && notFinal
})

// 是否为开发环境
const isDev = computed(() => {
    return import.meta.env.DEV
})

// 计算是否为退款状态
const isRefundStatus = computed(() => {
    if (!orderData.value) return false
    return ['REFUND_PENDING', 'REFUNDED', 'REFUND_FAILED'].includes(orderData.value.paymentStatus || '')
})

// 获取退款状态标题
const getRefundStatusTitle = computed(() => {
    if (!orderData.value) return ''

    const refundTypeText = getRefundTypeText(orderData.value.refundType)

    switch (orderData.value.paymentStatus) {
        case 'REFUND_PENDING':
            return `退款申请处理中${refundTypeText}`
        case 'REFUNDED':
            return `退款已完成${refundTypeText}`
        case 'REFUND_FAILED':
            return `退款处理失败${refundTypeText}`
        default:
            return ''
    }
})

// 获取退款状态类型
const getRefundStatusType = computed(() => {
    if (!orderData.value) return 'info'

    switch (orderData.value.paymentStatus) {
        case 'REFUND_PENDING':
            return 'warning'
        case 'REFUNDED':
            return 'success'
        case 'REFUND_FAILED':
            return 'error'
        default:
            return 'info'
    }
})

// 获取退款状态描述
const getRefundStatusDesc = computed(() => {
    if (!orderData.value) return ''

    switch (orderData.value.paymentStatus) {
        case 'REFUND_PENDING':
            return '您的退款申请正在处理中，请耐心等待'
        case 'REFUNDED':
            return `退款金额 ¥${orderData.value.refundAmount || orderData.value.totalAmount} 将在1-3个工作日内到账`
        case 'REFUND_FAILED':
            return '退款处理失败，如有疑问请联系客服'
        default:
            return ''
    }
})

// 获取订单状态的步骤
const getStatusStep = (status: string) => {
    const statusSteps: Record<string, number> = {
        'PENDING': 1,       // 预订申请(步骤1)
        'CONFIRMED': 2,     // 房东确认(步骤2)
        'PAYMENT_PENDING': 2, // 待支付(在确认后)
        'REJECTED': 1,      // 被拒绝(保持在步骤1)
        'CANCELLED': 1,     // 已取消(保持在步骤1)
        'CANCELLED_SYSTEM': 1, // 系统取消(保持在步骤1)
        'PAID': 3,          // 已支付(步骤3)
        'CHECKED_IN': 4,    // 已入住(步骤4)
        'COMPLETED': 5      // 已完成(步骤5)
    }
    return statusSteps[status] || 0
}

// 获取状态显示的类型
const getStatusType = (status: string, paymentStatus?: string, refundRejectionReason?: string) => {
    if (paymentStatus === 'PAID' && refundRejectionReason) {
        return 'danger'
    }

    const statusTypes: Record<string, string> = {
        'PENDING': 'warning',
        'CONFIRMED': 'success',
        'PAYMENT_PENDING': 'warning',
        'REJECTED': 'danger',
        'CANCELLED': 'info',
        'CANCELLED_SYSTEM': 'info',
        'CANCELLED_BY_USER': 'info',
        'CANCELLED_BY_HOST': 'info',
        'PAID': 'success',
        'CHECKED_IN': 'success',
        'COMPLETED': 'success'
    }
    return statusTypes[status] || 'info'
}

// 获取状态显示文本
const getStatusText = (status: string, paymentStatus?: string, refundType?: string, refundRejectionReason?: string, disputeResolution?: string) => {
    // 优先处理退款相关状态
    if (paymentStatus === 'PAID' && refundRejectionReason) {
        return '退款被拒绝';
    }

    // 争议状态
    if (status === 'DISPUTE_PENDING') return '争议待处理';
    if (status === 'DISPUTED') return '争议处理中';

    // 争议解决结果
    if (disputeResolution === 'APPROVED') return '争议已解决（退款）';
    if (disputeResolution === 'REJECTED') return '争议已解决（拒绝退款）';

    if (paymentStatus === 'REFUND_PENDING') {
        const refundTypeText = getRefundTypeText(refundType);
        return `退款中${refundTypeText}`;
    }
    if (paymentStatus === 'REFUNDED') {
        const refundTypeText = getRefundTypeText(refundType);
        return `已退款${refundTypeText}`;
    }
    if (paymentStatus === 'REFUND_FAILED') {
        const refundTypeText = getRefundTypeText(refundType);
        return `退款失败${refundTypeText}`;
    }

    const statusTexts: Record<string, string> = {
        'PENDING': '待确认',
        'CONFIRMED': '已确认',
        'PAYMENT_PENDING': '待支付',
        'REJECTED': '已拒绝',
        'CANCELLED': '已取消',
        'CANCELLED_SYSTEM': '系统已取消',
        'CANCELLED_BY_USER': '已取消',
        'CANCELLED_BY_HOST': '已取消',
        'PAID': '已支付',
        'CHECKED_IN': '已入住',
        'COMPLETED': '已完成',
        'PAYMENT_FAILED': '支付失败',
        'REFUND_PENDING': '退款中',
        'REFUNDED': '已退款',
        'REFUND_FAILED': '退款失败',
        'DISPUTE_PENDING': '争议待处理',
        'DISPUTED': '争议处理中'
    }

    return statusTexts[status] || status
}

// 获取退款类型文本
const getRefundTypeText = (refundType?: string): string => {
    if (!refundType) return '';

    switch (refundType) {
        case 'USER_REQUESTED':
            return '（用户申请）';
        case 'HOST_CANCELLED':
            return '（房东取消）';
        case 'ADMIN_INITIATED':
            return '（管理员发起）';
        case 'SYSTEM_AUTOMATIC':
            return '（系统自动）';
        default:
            return '';
    }
}

// 格式化日期范围
const formatDateRange = (checkIn: string, checkOut: string) => {
    if (!checkIn || !checkOut) return ''

    const checkInDate = new Date(checkIn)
    const checkOutDate = new Date(checkOut)

    return `${checkInDate.getMonth() + 1}月${checkInDate.getDate()}日 - ${checkOutDate.getMonth() + 1}月${checkOutDate.getDate()}日`
}

// 提取拒绝原因
const extractRejectReason = (remark: string) => {
    if (!remark) return ''

    const reasonMatch = remark.match(/拒绝原因: (.+)/)
    return reasonMatch ? reasonMatch[1] : remark
}

// 确认取消订单
const confirmCancel = async () => {
    try {
        await ElMessageBox.confirm(
            '确定要取消该订单吗？取消后将无法恢复',
            '取消订单',
            {
                confirmButtonText: '确定取消',
                cancelButtonText: '再想想',
                type: 'warning',
                distinguishCancelAndClose: true,
                closeOnClickModal: false
            }
        )

        if (!orderData.value) return

        // 显示取消中的加载状态
        ElMessage({
            type: 'info',
            message: '订单取消中...',
            duration: 0
        })

        try {
            // 使用cancelOrder API方法，确保它使用正确的/status接口
            await cancelOrder(orderData.value.id);

            // 刷新 Store，确保订单列表同步
            store.fetchOrders();
            store.fetchStatsOrders();

            // 关闭加载消息
            ElMessage.closeAll();

            // 显示更详细的成功消息，并提供跳转链接
            const result = await ElMessageBox.alert(
                '订单已成功取消，您可以继续浏览其他房源或返回订单列表查看',
                '取消成功',
                {
                    confirmButtonText: '查看其他房源',
                    cancelButtonText: '返回订单列表',
                    distinguishCancelAndClose: true,
                    showCancelButton: true
                }
            );

            if (result === 'confirm') {
                router.push('/'); // 前往首页
            } else {
                router.push('/user/bookings'); // 返回订单列表
            }
        } catch (error: any) {
            // 关闭加载消息
            ElMessage.closeAll();

            if (error === 'cancel' || error === 'close') {
                // 用户点击了取消或关闭按钮
                router.push('/user/bookings');
                return;
            }

            // 提取错误消息
            let errorMsg = '取消订单失败，请稍后重试';
            if (error.response?.data?.message) {
                errorMsg = error.response.data.message;
            } else if (error.message) {
                errorMsg = error.message;
            }

            console.error('取消订单失败:', errorMsg);
            ElMessage.error(errorMsg);
            // 刷新订单数据
            fetchOrderDetail();
        }
    } catch (error: any) {
        if (error !== 'cancel' && error !== 'close') {
            console.error('取消订单确认失败:', error);
            ElMessage.error('取消订单失败，请重试');
        }
    }
}

// 展示支付弹窗
const showPaymentDialog = () => {
    paymentDialogVisible.value = true
    generateQrCode()
}

// 生成二维码
const generateQrCode = async () => {
    if (!orderData.value) return

    qrCodeLoading.value = true
    paymentQrCode.value = ''

    try {
        const response = await generatePaymentQRCode({
            orderId: orderData.value.id,
            method: 'alipay'
        })

        if (response.data.success) {
            const data = response.data
            // 检查是否为 HTML 表单跳转支付
            if (data.paymentUrl && data.paymentUrl.includes('<form')) {
                logInfo('检测到支付宝跳转表单，正在准备跳转...')
                // 创建一个隐藏的 div 来存放表单
                const div = document.createElement('div')
                div.id = 'alipay-form-container'
                div.style.display = 'none'
                div.innerHTML = data.paymentUrl
                document.body.appendChild(div)
                
                // 提交表单
                const form = div.querySelector('form')
                if (form) {
                    form.submit()
                    ElMessage.success('正在跳转至支付宝支付页面...')
                } else {
                    ElMessage.error('支付表单生成失败，请重试')
                }
                return
            }

            // 如果是二维码扫码支付
            if (data.qrCode) {
                paymentQrCode.value = data.qrCode
                startPolling()
            } else if (data.paymentUrl) {
                // 如果是单纯的 URL 链接
                window.location.href = data.paymentUrl
            } else {
                ElMessage.error('获取支付信息失败：返回结果异常')
            }
        } else {
            ElMessage.error(response.data.message || '生成支付信息失败')
        }
    } catch (error) {
        console.error('生成支付信息异常:', error)
        ElMessage.error('发起支付失败，请检查网络或稍后重试')
    } finally {
        qrCodeLoading.value = false
    }
}

// 帮助函数：替代直接使用 console.log (可选，为了代码一致性)
const logInfo = (msg: string) => {
    console.log(`[支付系统] ${msg}`)
}

// 开始轮询支付状态
const startPolling = () => {
    stopPolling() // 先停止旧的
    isPolling.value = true
    pollingStatusText.value = '支付确认中...'

    let errorCount = 0;
    pollingTimer.value = window.setInterval(async () => {
        if (!orderData.value) return

        try {
            const response = await checkPayment(orderData.value.id)
            if (response.data.success && response.data.isPaid) {
                stopPolling()
                ElMessage.success('支付成功！')
                paymentDialogVisible.value = false
                fetchOrderDetail() // 刷新订单详情
            } else {
                errorCount = 0; // 重置错误计数
                pollingStatusText.value = '正在核对支付结果...'
            }
        } catch (error) {
            console.error('轮询支付状态异常:', error)
            errorCount++;
            // 连续多次错误才提示
            if (errorCount > 3) {
                pollingStatusText.value = '支付确认延迟，请勿关闭页面...'
            }
        }
    }, 4000) // 每4秒查一次，避免过于频繁触碰限流
}

// 停止轮询
const stopPolling = () => {
    isPolling.value = false
    if (pollingTimer.value) {
        clearInterval(pollingTimer.value)
        pollingTimer.value = null
    }
}

// 手动检查支付状态
const checkPaymentStatus = async () => {
    if (!orderData.value) return

    checkingStatus.value = true
    try {
        const response = await checkPayment(orderData.value.id)
        if (response.data.success && response.data.isPaid) {
            ElMessage.success('支付已成功确认')
            paymentDialogVisible.value = false
            fetchOrderDetail()
        } else {
            ElMessage.warning('尚未检测到支付成功，请扫码支付')
        }
    } catch (error) {
        ElMessage.error('查询支付状态失败')
    } finally {
        checkingStatus.value = false
    }
}

// 处理手动直接支付（模拟）
const handleManualPay = async () => {
    if (!orderData.value) return

    try {
        await ElMessageBox.confirm(
            '这将跳过实际支付流程，直接调用后端接口模拟支付成功。是否继续？',
            '手动支付确认',
            {
                confirmButtonText: '确定支付',
                cancelButtonText: '取消',
                type: 'warning'
            }
        )

        payLoading.value = true
        // 显式调用之前未被调用的 payOrder API
        const response = await payOrder(orderData.value.id, 'ALIPAY')
        
        if (response.data.success) {
            ElMessage.success('模拟支付操作成功！')
            paymentDialogVisible.value = false
            stopPolling()
            // 延迟刷新以确保后端数据已同步更新
            setTimeout(() => {
                fetchOrderDetail()
                store.fetchOrders()
                store.fetchStatsOrders()
            }, 500)
        } else {
            ElMessage.error(response.data.message || '操作失败')
        }
    } catch (error: any) {
        if (error !== 'cancel') {
            console.error('手动支付异常:', error)
            ElMessage.error(error.message || '支付接口调用失败')
        }
    } finally {
        payLoading.value = false
    }
}

// 支付弹窗关闭逻辑
const handlePaymentDialogClose = () => {
    stopPolling()
}

// 前往订单列表
const goToOrders = () => {
    router.push('/user/bookings')
}

// 联系客服
const contactCustomerService = () => {
    ElMessage.info('客服热线：400-123-4567，工作时间：9:00-21:00')
}

// 联系房东
const contactHost = () => {
    if (!orderData.value) return
    ElMessage.info('暂未开通实时聊天，具体联系方式请咨询平台客服')
}

const contactAgent = () => {
    if (!userStore.isAuthenticated) {
        ElMessage.warning('请先登录后再咨询客服')
        router.push('/login')
        return
    }
    agentStore.openAgentDialog(null, orderData.value?.id ?? null)
}


// 确认申请退款（先展示预估退款金额，再让用户输入原因）
const confirmRequestRefund = async () => {
    if (!orderData.value) return

    // 第一步：获取退款预览
    let previewMsg = '正在计算预估退款金额...'
    let estimatedAmount: number | null = null
    let policyDesc = ''

    try {
        const previewRes = await getRefundPreview(orderData.value.id)
        const previewData = previewRes.data
        if (previewData && previewData.eligible !== false) {
            estimatedAmount = previewData.estimatedRefundAmount
            policyDesc = previewData.policyDescription || ''
            previewMsg = `预计退款金额：¥${estimatedAmount}\n\n${policyDesc}`
        } else if (previewData && previewData.message) {
            ElMessage.warning(previewData.message)
            return
        }
    } catch (e) {
        console.warn('获取退款预览失败，继续申请流程', e)
        previewMsg = '无法获取预估退款金额，提交后将按取消政策计算'
    }

    // 第二步：显示金额确认 + 输入原因
    try {
        const result = await ElMessageBox.prompt(
            previewMsg + '\n\n请说明申请退款的原因（必填）',
            '申请退款',
            {
                confirmButtonText: '确认提交',
                cancelButtonText: '取消',
                inputPattern: /\S/,
                inputErrorMessage: '退款原因不能为空',
                inputPlaceholder: '请输入退款原因...',
                type: 'warning',
                dangerouslyUseHTMLString: false
            }
        )

        if (!result.value || !orderData.value) {
            ElMessage.warning('退款原因不能为空')
            return
        }

        ElMessage({
            type: 'info',
            message: '正在提交退款申请...',
            duration: 0
        })

        try {
            await requestRefund(orderData.value.id, result.value.trim())
            ElMessage.closeAll()
            ElMessage.success('退款申请已提交，预计1-3个工作日内处理')

            // 刷新订单详情和列表
            fetchOrderDetail()
            store.fetchOrders()
            store.fetchStatsOrders()
        } catch (error: any) {
            ElMessage.closeAll()
            console.error('申请退款失败:', error)

            let errorMessage = '申请退款失败，请稍后重试'
            if (error.response && error.response.data && error.response.data.message) {
                errorMessage = error.response.data.message
            }
            ElMessage.error(errorMessage)
        }
    } catch (error) {
        // 用户取消了对话框
        if (error !== 'cancel') {
            console.error('退款申请确认框错误:', error)
        }
    }
}

// 确认发起争议
const confirmRaiseDispute = async () => {
    if (!orderData.value) return

    try {
        const result = await ElMessageBox.prompt(
            '请说明发起争议的原因（必填），平台将在1-3个工作日内处理。\n\n注意：发起争议后，退款处理将暂停，等待仲裁结果。',
            '申请争议',
            {
                confirmButtonText: '确认提交',
                cancelButtonText: '取消',
                inputPattern: /\S/,
                inputErrorMessage: '争议原因不能为空',
                inputPlaceholder: '请输入发起争议的原因...',
                type: 'warning',
            }
        )

        if (!result.value || !orderData.value) {
            ElMessage.warning('争议原因不能为空')
            return
        }

        ElMessage({
            type: 'info',
            message: '正在提交争议申请...',
            duration: 0
        })

        try {
            await raiseDispute(orderData.value.id, result.value.trim())
            ElMessage.closeAll()
            ElMessage.success('争议申请已提交，平台将在1-3个工作日内处理')

            // 刷新订单详情和列表
            fetchOrderDetail()
            store.fetchOrders()
            store.fetchStatsOrders()
        } catch (error: any) {
            ElMessage.closeAll()
            console.error('发起争议失败:', error)

            let errorMessage = '发起争议失败，请稍后重试'
            if (error.response && error.response.data && error.response.data.message) {
                errorMessage = error.response.data.message
            }
            ElMessage.error(errorMessage)
        }
    } catch (error) {
        // 用户取消了对话框
        if (error !== 'cancel') {
            console.error('争议申请确认框错误:', error)
        }
    }
}



// 添加日期格式化函数
const formatDate = (dateString?: string) => {
    if (!dateString) return '';
    return dayjs(dateString).format('YYYY-MM-DD');
};

const formatDateTime = (dateString?: string) => {
    if (!dateString) return '';
    return dayjs(dateString).format('YYYY-MM-DD HH:mm');
};

// 判断是否可以删除评价 (当前用户是评价者)
const canDeleteReview = (review?: ReviewItem | null): boolean => {
    // 注意：orderData.review 可能没有 userId，如果后端 /api/orders/{id} 返回的ReviewDTO不包含userId
    // 需要确认 getOrderDetail 返回的数据中 Review 对象是否包含 userId
    // 如果不包含，需要调整逻辑，可能需要基于 orderData.guestId 判断
    // return !!currentUserId && orderData.value?.guestId === currentUserId && !!review;
    // 假设后端返回了 userId
    return !!currentUserId && !!review && review.userId === currentUserId;
};

// --- Add function to check if review can be edited ---
const canEditReview = (review?: ReviewItem | null): boolean => {
    // 权限同删除
    return canDeleteReview(review);
};
// --- End function ---

// 处理删除评价
const handleDeleteReview = async (reviewId: number) => {
    if (!reviewId) return;
    try {
        await ElMessageBox.confirm('确定要删除这条评价吗？删除后不可恢复。', '删除确认', {
            confirmButtonText: '确定删除',
            cancelButtonText: '取消',
            type: 'warning',
        });

        isDeletingReview.value = true;
        await deleteReview(reviewId);
        ElMessage.success('评价删除成功');
        // 从订单数据中移除评价信息并刷新列表
        if (orderData.value) {
            orderData.value.review = null;
        }
        store.fetchOrders();
        store.fetchStatsOrders();

    } catch (error: any) {
        if (error !== 'cancel') {
            console.error('删除评价失败:', error);
            const errMsg = error?.response?.data?.message || '删除评价失败，请稍后重试';
            ElMessage.error(errMsg);
        }
    } finally {
        isDeletingReview.value = false;
    }
};

// 处理订单超时事件
const handleOrderTimeout = () => {
    ElMessage.warning('订单已超时，正在自动取消...')
    // 刷新订单状态
    fetchOrderDetail()
}

// 处理订单超时预警事件
const handleOrderWarning = (remainingTime: number) => {
    const minutes = Math.floor(remainingTime / (60 * 1000))
    if (minutes <= 30) {
        ElMessage.warning(`订单即将超时，剩余 ${minutes} 分钟`)
    }
}

// 判断订单是否在评价时间窗口内（30天）
const isWithinReviewWindow = (order: OrderItem | null): boolean => {
    if (!order) return false;
    if (!order.completedAt) return true; // 无完成时间默认允许（兼容老数据）
    const completedAt = dayjs(order.completedAt);
    const now = dayjs();
    return now.diff(completedAt, 'day') <= 30;
};

// --- Add functions for edit modal ---
// 打开编辑弹窗
const openEditModal = (review: ReviewItem) => {
    currentEditingReview.value = {
        id: review.id,
        rating: review.rating,
        content: review.content,
        images: review.images,
        cleanlinessRating: review.cleanlinessRating,
        accuracyRating: review.accuracyRating,
        communicationRating: review.communicationRating,
        locationRating: review.locationRating,
        checkInRating: review.checkInRating,
        valueRating: review.valueRating,
    };
    isEditModalVisible.value = true;
};

// 处理评价更新事件
const handleReviewUpdated = (updatedReviewData: EditableReviewData) => {
    if (orderData.value && orderData.value.review) {
        orderData.value.review.rating = updatedReviewData.rating;
        orderData.value.review.content = updatedReviewData.content;
        orderData.value.review.images = updatedReviewData.images;
        orderData.value.review.cleanlinessRating = updatedReviewData.cleanlinessRating;
        orderData.value.review.accuracyRating = updatedReviewData.accuracyRating;
        orderData.value.review.communicationRating = updatedReviewData.communicationRating;
        orderData.value.review.locationRating = updatedReviewData.locationRating;
        orderData.value.review.checkInRating = updatedReviewData.checkInRating;
        orderData.value.review.valueRating = updatedReviewData.valueRating;
    }
    isEditModalVisible.value = false;
};
// --- End functions ---

onMounted(() => {
    if (!store.timeoutConfig) {
        store.fetchTimeoutConfig()
    }
    fetchOrderDetail()
})

onUnmounted(() => {
    stopPolling()
})
</script>

<style scoped>
.order-detail-container {
    max-width: 860px;
    margin: 0 auto;
    padding: 30px 20px;
    background-color: #f5f7fa; /* 更深一点的灰底 */
    min-height: 100vh;
}

/* 通用卡片样式 */
.card-style {
    background-color: #ffffff;
    border-radius: 12px;
    padding: 24px 32px;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04);
    margin-bottom: 24px;
    transition: transform 0.3s ease, box-shadow 0.3s ease;
}



.order-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 24px;
    padding: 0 10px;
}

h1 {
    font-size: 28px;
    margin: 0;
    font-weight: 700;
    color: #2c3e50;
    letter-spacing: -0.5px;
}

.order-status-flow {
    padding: 24px 32px;
}

.status-tag {
    font-weight: 600;
    font-size: 14px;
    padding: 6px 14px;
    border-radius: 20px;
}

/* 订单信息卡片重构 */
.card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    padding-bottom: 16px;
    border-bottom: 1px solid #ebeef5;
}

.header-right {
    display: flex;
    align-items: center;
    gap: 16px;
    flex-direction: row;
}

.card-header h2 {
    margin: 0;
    font-size: 20px;
    color: #2c3e50;
    font-weight: 700;
}

.order-number {
    color: #909399;
    font-size: 14px;
    padding-right: 16px;
    border-right: 1px solid #dcdfe6;
}

.homestay-info-wrapper {
    display: flex;
    gap: 24px;
    margin-bottom: 24px;
}

.homestay-image {
    width: 200px;
    height: 140px;
    border-radius: 12px;
    overflow: hidden;
    flex-shrink: 0;
    background-color: #f0f2f5;
}

.homestay-image img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 0.5s ease;
}

.homestay-image:hover img {
    transform: scale(1.03);
}

.homestay-details h3 {
    margin: 0 0 16px 0;
    font-size: 22px;
    font-weight: 600;
    color: #303133;
    line-height: 1.3;
}

.homestay-details {
    display: flex;
    flex-direction: column;
    justify-content: center;
}

.detail-row {
    margin: 6px 0;
    color: #606266;
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 15px;
}

.detail-row .el-icon {
    color: #409EFF;
    font-size: 16px;
}

/* 网格布局的联系人信息 */
.guest-info-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
    gap: 16px;
    margin-top: 24px;
    background-color: #f8f9fa;
    padding: 20px;
    border-radius: 10px;
}

.guest-info-item {
    display: flex;
    align-items: center;
    gap: 12px;
}

.guest-icon {
    width: 40px;
    height: 40px;
    background-color: #ecf5ff;
    color: #409eff;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 20px;
}

.guest-text {
    display: flex;
    flex-direction: column;
}

.guest-text .label {
    font-size: 12px;
    color: #909399;
    margin-bottom: 4px;
}

.guest-text .value {
    font-size: 15px;
    font-weight: 500;
    color: #303133;
}

.remark-item {
    grid-column: 1 / -1;
}

.status-notice {
    margin-bottom: 32px;
}

.reject-reason {
    background-color: #fef0f0;
    border-radius: 8px;
    padding: 16px;
    margin-top: 16px;
}

.reject-reason h4 {
    margin: 0 0 8px 0;
    color: #f56c6c;
}

.reject-reason p {
    margin: 0;
}

/* 小票风格的价格明细区块 */
.receipt-style {
    background-color: #fcfcfc;
    border: 1px solid #ebeef5;
    border-radius: 12px;
    padding: 0;
    margin-bottom: 32px;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.02);
}

.receipt-header {
    background-color: #f5f7fa;
    padding: 20px 32px;
    border-bottom: 1px solid #ebeef5;
    border-radius: 12px 12px 0 0;
}

.receipt-header h2 {
    margin: 0;
    font-size: 20px;
    color: #2c3e50;
    font-weight: 700;
}

.receipt-body {
    padding: 24px 32px;
}

.price-item {
    display: flex;
    justify-content: space-between;
    margin-bottom: 16px;
    font-size: 15px;
}

.price-item:last-child {
    margin-bottom: 0;
}

.item-name {
    color: #606266;
}

.item-price {
    color: #303133;
    font-weight: 500;
}

.receipt-divider {
    height: 1px;
    background-image: linear-gradient(to right, #dcdfe6 50%, transparent 50%);
    background-size: 12px 1px;
    background-repeat: repeat-x;
    margin: 0 32px;
}

.receipt-footer {
    padding: 24px 32px;
    background-color: #ffffff;
    border-radius: 0 0 12px 12px;
}

.price-total {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-weight: 700;
    font-size: 18px;
    color: #303133;
    margin-bottom: 12px;
}

.price-total:last-child {
    margin-bottom: 0;
}

.total-amount {
    color: #f56c6c;
    font-size: 24px;
}

.actual-amount {
    color: #3b82f6;
    font-size: 24px;
}

/* 操作按钮区 */
.action-buttons {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    margin-top: 10px;
    padding: 20px 32px;
}

.btn-back {
    margin-right: auto; /* 推到左边 */
}

.primary-action-btn {
    min-width: 140px;
    box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
    transition: all 0.3s ease;
}

.primary-action-btn:hover {
    box-shadow: 0 6px 16px rgba(64, 158, 255, 0.4);
    transform: translateY(-2px);
}



@media (max-width: 768px) {
    .homestay-info {
        flex-direction: column;
    }

    .homestay-image {
        width: 100%;
        height: 180px;
        margin-bottom: 16px;
    }

    .action-buttons {
        flex-direction: column;
    }
}

.review-details-section {
    background-color: #fafafa;
    border-radius: 12px;
    padding: 24px;
    margin-bottom: 32px;
}

.review-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
}

.review-header h2 {
    margin-bottom: 0;
}

.review-actions {
    display: flex;
    gap: 10px;
}

.delete-review-btn {
    margin-left: 16px;
}

.review-content .info-item {
    display: flex;
    margin-bottom: 12px;
}

.review-content .label {
    width: 100px;
    color: #666;
    font-weight: 500;
}

.review-content p {
    margin: 0;
    flex: 1;
}

.detailed-ratings {
    margin-bottom: 12px;
    padding: 8px 0;
}

.detailed-ratings .det-label {
    font-size: 12px;
    color: #606266;
    margin-right: 6px;
}

.review-images {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 12px;
}

.review-images .review-thumb {
    width: 80px;
    height: 80px;
    border-radius: 4px;
    object-fit: cover;
    cursor: pointer;
}

.host-response-detail {
    margin-top: 15px;
    padding-top: 15px;
    border-top: 1px dashed #dcdfe6;
}

.host-response-detail h4 {
    margin: 0 0 8px 0;
    color: #409EFF;
}

.host-response-detail p {
    margin-bottom: 5px;
}

.host-response-detail .response-time {
    font-size: 12px;
    color: #909399;
    text-align: right;
}

/* 支付对话框样式 */
.payment-dialog-content {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 20px 0;
}

.payment-amount {
    font-size: 16px;
    margin-bottom: 20px;
}

.payment-amount span {
    color: #f56c6c;
    font-size: 24px;
    font-weight: bold;
}

.qr-code-wrapper {
    padding: 15px;
    background: white;
    border: 1px solid #ebeef5;
    border-radius: 8px;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
    margin-bottom: 15px;
}

.payment-tip {
    color: #606266;
    font-size: 14px;
    margin-bottom: 20px;
}

.mock-pay-section {
    margin: 20px 0;
    text-align: center;
}

.mock-tip {
    font-size: 11px;
    color: #909399;
    margin-top: 8px;
}

.qr-image {
    width: 200px;
    height: 200px;
    object-fit: contain;
}

.payment-status-info {
    display: flex;
    align-items: center;
    gap: 8px;
    color: #409eff;
    font-size: 14px;
}

.qr-loading {
    height: 230px;
    width: 230px;
    display: flex;
    justify-content: center;
    align-items: center;
}

/* 退款进度提示条 */
.refund-progress-tip {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 10px;
    padding: 10px 14px;
    background: #fdf6ec;
    border-left: 3px solid #e6a23c;
    border-radius: 4px;
    font-size: 13px;
    color: #e6a23c;
}

.refund-progress-tip.success {
    background: #f0f9eb;
    border-left-color: #67c23a;
    color: #67c23a;
}

.refund-progress-tip.danger {
    background: #fef0f0;
    border-left-color: #f56c6c;
    color: #f56c6c;
}

/* 退款拒绝原因 */
.rejection-reason-item {
    background: #fef0f0;
    padding: 10px 14px;
    border-radius: 6px;
    border-left: 3px solid #f56c6c;
}

.rejection-reason-text {
    color: #f56c6c;
    font-weight: 500;
}

</style>