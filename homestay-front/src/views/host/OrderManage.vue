<template>
    <div class="order-container">
        <h1>订单管理</h1>

        <!-- 统计数据 -->
        <el-row :gutter="20" class="stat-row">
            <el-col :span="6">
                <el-card shadow="hover" class="stat-card">
                    <div class="stat-content">
                        <div class="stat-title">总订单数</div>
                        <div class="stat-value">{{ orderStats.total }}</div>
                    </div>
                </el-card>
            </el-col>
            <el-col :span="6">
                <el-card shadow="hover" class="stat-card">
                    <div class="stat-content">
                        <div class="stat-title">待确认订单</div>
                        <div class="stat-value">{{ orderStats.pending }}</div>
                    </div>
                </el-card>
            </el-col>
            <el-col :span="6">
                <el-card shadow="hover" class="stat-card">
                    <div class="stat-content">
                        <div class="stat-title">已确认订单</div>
                        <div class="stat-value">{{ orderStats.confirmed }}</div>
                    </div>
                </el-card>
            </el-col>
            <el-col :span="6">
                <el-card shadow="hover" class="stat-card">
                    <div class="stat-content">
                        <div class="stat-title">已支付订单</div>
                        <div class="stat-value">{{ orderStats.paid }}</div>
                    </div>
                </el-card>
            </el-col>
        </el-row>

        <!-- 自动状态管理统计 -->
        <el-row :gutter="20" class="stat-row auto-status-row">
            <el-col :span="5">
                <el-card shadow="hover" class="stat-card auto-status-card">
                    <div class="stat-content">
                        <div class="stat-title">
                            <el-icon>
                                <Clock />
                            </el-icon>
                            待自动入住
                        </div>
                        <div class="stat-value auto-checkin">{{ autoStatusStats.pendingAutoCheckIn || 0 }}</div>
                        <div class="stat-desc">今日22:00后自动入住</div>
                    </div>
                </el-card>
            </el-col>
            <el-col :span="5">
                <el-card shadow="hover" class="stat-card auto-status-card">
                    <div class="stat-content">
                        <div class="stat-title">
                            <el-icon>
                                <CircleCheck />
                            </el-icon>
                            待自动完成
                        </div>
                        <div class="stat-value auto-complete">{{ autoStatusStats.pendingAutoComplete || 0 }}</div>
                        <div class="stat-desc">明日06:00后自动完成</div>
                    </div>
                </el-card>
            </el-col>
            <el-col :span="5">
                <el-card shadow="hover" class="stat-card auto-status-card">
                    <div class="stat-content">
                        <div class="stat-title">
                            <el-icon>
                                <Warning />
                            </el-icon>
                            错过入住
                        </div>
                        <div class="stat-value missed-checkin">{{ autoStatusStats.missedCheckIn || 0 }}</div>
                        <div class="stat-desc">需要特别关注的订单</div>
                    </div>
                </el-card>
            </el-col>
            <el-col :span="9">
                <el-card shadow="hover" class="stat-card auto-status-card management-card">
                    <div class="stat-content">
                        <div class="stat-title">
                            <el-icon>
                                <Setting />
                            </el-icon>
                            自动状态管理
                        </div>
                        <div class="management-actions">
                            <div class="action-group">
                                <el-button type="primary" size="small" @click="showAutoStatusConfig" plain>
                                    查看配置
                                </el-button>
                                <el-button type="success" size="small" @click="manualTriggerAutoStatus"
                                    :loading="autoStatusLoading" plain>
                                    手动执行
                                </el-button>
                            </div>
                        </div>
                    </div>
                </el-card>
            </el-col>
        </el-row>

        <el-row :gutter="20" class="stat-row">
            <el-col :span="6">
                <el-card shadow="hover" class="stat-card">
                    <div class="stat-content">
                        <div class="stat-title">已入住订单</div>
                        <div class="stat-value">{{ orderStats.checked_in }}</div>
                    </div>
                </el-card>
            </el-col>
            <el-col :span="6">
                <el-card shadow="hover" class="stat-card">
                    <div class="stat-content">
                        <div class="stat-title">已完成订单</div>
                        <div class="stat-value">{{ orderStats.completed }}</div>
                    </div>
                </el-card>
            </el-col>
            <el-col :span="6">
                <el-card shadow="hover" class="stat-card">
                    <div class="stat-content">
                        <div class="stat-title">已取消订单</div>
                        <div class="stat-value">{{ orderStats.cancelled }}</div>
                    </div>
                </el-card>
            </el-col>
            <el-col :span="6">
                <el-card shadow="hover" class="stat-card">
                    <div class="stat-content">
                        <div class="stat-title">已拒绝订单</div>
                        <div class="stat-value">{{ orderStats.rejected }}</div>
                    </div>
                </el-card>
            </el-col>
        </el-row>

        <!-- 筛选器 -->
        <el-card class="filter-card">
            <div class="filter-header">
                <h3>订单筛选</h3>
            </div>
            <el-form :inline="true" :model="filterForm" class="filter-form">
                <el-form-item label="房源">
                    <el-select v-model="filterForm.homestayId" placeholder="全部房源" clearable filterable
                        :popper-append-to-body="false" style="width: 150px;">
                        <el-option v-for="item in homestayOptions" :key="item.id" :label="item.title"
                            :value="item.id" />
                    </el-select>
                </el-form-item>
                <el-form-item label="订单状态">
                    <el-select v-model="filterForm.status" placeholder="全部状态" clearable style="width: 150px;">
                        <el-option label="待确认" value="PENDING" />
                        <el-option label="已确认" value="CONFIRMED" />
                        <el-option label="已支付" value="PAID" />
                        <el-option label="待入住" value="READY_FOR_CHECKIN" />
                        <el-option label="已入住" value="CHECKED_IN" />
                        <el-option label="已退房" value="CHECKED_OUT" />
                        <el-option label="已完成" value="COMPLETED" />
                        <el-option label="已取消" value="CANCELLED" />
                        <el-option label="系统取消" value="CANCELLED_SYSTEM" />
                        <el-option label="用户取消" value="CANCELLED_BY_USER" />
                        <el-option label="已拒绝" value="REJECTED" />
                        <el-option label="退款中" value="REFUND_PENDING" />
                        <el-option label="已退款" value="REFUNDED" />
                        <el-option label="退款失败" value="REFUND_FAILED" />
                    </el-select>
                </el-form-item>
                <el-form-item label="入住日期">
                    <el-date-picker v-model="filterForm.dateRange" type="daterange" range-separator="至"
                        start-placeholder="开始日期" end-placeholder="结束日期" format="YYYY-MM-DD" value-format="YYYY-MM-DD"
                        style="width: 300px;" />
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" @click="handleFilter">
                        <el-icon>
                            <Search />
                        </el-icon> 筛选
                    </el-button>
                    <el-button @click="resetFilter">
                        <el-icon>
                            <Refresh />
                        </el-icon> 重置
                    </el-button>
                </el-form-item>
            </el-form>
        </el-card>

        <!-- 订单列表 -->
        <el-card class="order-list-card" v-loading="loading">
            <div class="list-header">
                <h3>订单列表</h3>
                <el-button type="primary" size="small" @click="fetchOrders">
                    <el-icon>
                        <Refresh />
                    </el-icon> 刷新
                </el-button>
            </div>

            <div v-if="orders.length === 0" class="empty-data">
                <el-empty description="暂无订单数据" />
            </div>

            <el-table v-else :data="orders" style="width: 100%" border stripe>
                <el-table-column label="订单号" width="90" align="center">
                    <template #default="scope">
                        <span style="color: var(--el-text-color-secondary); font-size: 13px;">{{ scope.row.id }}</span>
                    </template>
                </el-table-column>
                <el-table-column label="房源名称" min-width="180">
                    <template #default="scope">
                        <el-tooltip :content="scope.row.homestayTitle || scope.row.homestayName" placement="top">
                            <span class="ellipsis-text" style="font-weight: 600; color: var(--el-text-color-primary);">{{ scope.row.homestayTitle || scope.row.homestayName }}</span>
                        </el-tooltip>
                    </template>
                </el-table-column>
                <el-table-column label="预订客户" width="110">
                    <template #default="scope">
                        <span style="color: var(--el-text-color-regular);">{{ scope.row.guestName }}</span>
                    </template>
                </el-table-column>
                <el-table-column label="订单金额" width="120" align="right">
                    <template #default="scope">
                        <span class="price-value" style="font-weight: bold; color: var(--el-color-danger); font-size: 15px;">
                            ¥{{ formatAmount(scope.row.totalPrice || scope.row.totalAmount) }}
                        </span>
                    </template>
                </el-table-column>
                <el-table-column label="入离日期" width="160">
                    <template #default="scope">
                        <div class="date-range-vertical" style="display: flex; flex-direction: column; gap: 4px; font-size: 13px;">
                            <div class="check-in-date" style="display: flex; align-items: center;">
                                <span style="color: var(--el-text-color-regular); margin-right: 8px;">入住</span>
                                <span style="font-family: inherit; font-size: 14px;">{{ formatDateString(scope.row.checkInDate) }}</span>
                            </div>
                            <div class="check-out-date" style="display: flex; align-items: center;">
                                <span style="color: var(--el-text-color-secondary); margin-right: 8px;">退房</span>
                                <span style="color: var(--el-text-color-secondary); font-family: inherit; font-size: 14px;">{{ formatDateString(scope.row.checkOutDate) }}</span>
                            </div>
                        </div>
                    </template>
                </el-table-column>
                <el-table-column label="订单状态" width="100" align="center">
                    <template #default="scope">
                        <el-tag :type="getStatusType(scope.row.status)" effect="plain">
                            {{ getStatusText(scope.row) }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="创建时间" width="150" align="center">
                    <template #default="scope">
                        <span style="color: var(--el-text-color-secondary); font-size: 13px;">
                            {{ formatDateTime(scope.row.createTime || scope.row.createdTime) }}
                        </span>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="200" fixed="right" align="center">
                    <template #default="scope">
                        <div class="action-buttons" style="display: flex; gap: 8px; justify-content: center; align-items: center; flex-wrap: nowrap;">
                            <!-- 主操作区（按订单状态展示最核心操作） -->
                            <el-button v-if="scope.row.status === 'PENDING'" type="primary" size="small"
                                @click="handleConfirm(scope.row)">
                                确认接单
                            </el-button>
                            <el-button v-if="scope.row.status === 'PAID'" type="primary" size="small"
                                @click="openPrepareCheckIn(scope.row)">
                                设置准备入住
                            </el-button>
                            <el-button v-if="scope.row.status === 'READY_FOR_CHECKIN'" type="primary" size="small"
                                @click="handlePerformCheckIn(scope.row)">
                                办理入住
                            </el-button>
                            <el-button v-if="scope.row.status === 'CHECKED_IN'" type="success" size="small"
                                @click="openCheckOut(scope.row)">
                                办理退房
                            </el-button>
                            <el-button v-if="scope.row.status === 'CHECKED_OUT'" type="warning" size="small"
                                @click="openDeposit(scope.row)">
                                押金操作
                            </el-button>
                            <el-button v-if="scope.row.status === 'CHECKED_OUT'" type="success" size="small"
                                @click="handleConfirmSettlement(scope.row)">
                                确认结算
                            </el-button>
                            <el-button v-if="scope.row.paymentStatus === 'REFUND_PENDING' && scope.row.status !== 'DISPUTE_PENDING'" type="warning" size="small"
                                @click="handleReviewRefund(scope.row)">
                                审核退款
                            </el-button>

                            <!-- 更多操作下拉菜单 -->
                            <el-dropdown trigger="click" v-if="hasMoreActions(scope.row)">
                                <el-button type="info" link size="small">
                                    更多<el-icon class="el-icon--right"><arrow-down /></el-icon>
                                </el-button>
                                <template #dropdown>
                                    <el-dropdown-menu>
                                        <!-- 详情 -->
                                        <el-dropdown-item @click="handleDetails(scope.row)">
                                            <span>详情</span>
                                        </el-dropdown-item>
                                        <!-- 拒绝接单 -->
                                        <el-dropdown-item v-if="canDo(OrderAction.REJECT, scope.row)"
                                            @click="handleReject(scope.row)">
                                            <span style="color: var(--el-color-danger)">拒绝接单</span>
                                        </el-dropdown-item>
                                        <!-- 取消订单 -->
                                        <el-dropdown-item v-if="canDo(OrderAction.CANCEL, scope.row)"
                                            @click="handleCancel(scope.row)">
                                            <span style="color: var(--el-color-danger)">取消订单</span>
                                        </el-dropdown-item>
                                        <!-- 取消准备 -->
                                        <el-dropdown-item v-if="scope.row.status === 'READY_FOR_CHECKIN'"
                                            @click="handleCancelPrepare(scope.row)">
                                            <span style="color: var(--el-color-warning)">取消准备</span>
                                        </el-dropdown-item>
                                        <!-- 查看凭证 -->
                                        <el-dropdown-item v-if="scope.row.status === 'PAID' || scope.row.status === 'READY_FOR_CHECKIN'"
                                            @click="openViewCredential(scope.row)">
                                            <span style="color: var(--el-color-info)">查看凭证</span>
                                        </el-dropdown-item>
                                        <!-- 发起退款 -->
                                        <el-dropdown-item v-if="canInitiateRefund(scope.row)"
                                            @click="handleRefund(scope.row)">
                                            <span style="color: var(--el-color-warning)">发起退款</span>
                                        </el-dropdown-item>
                                        <!-- 发起争议 -->
                                        <el-dropdown-item v-if="canDo(OrderAction.DISPUTE, scope.row)"
                                            @click="handleRaiseDispute(scope.row)">
                                            <span style="color: var(--el-color-danger)">发起争议</span>
                                        </el-dropdown-item>
                                    </el-dropdown-menu>
                                </template>
                            </el-dropdown>
                        </div>
                    </template>
                </el-table-column>
            </el-table>

            <!-- 分页 -->
            <div class="pagination">
                <el-pagination v-model:currentPage="currentPage" v-model:page-size="pageSize"
                    :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next, jumper" :total="total"
                    @size-change="handleSizeChange" @current-change="handleCurrentChange" background />
            </div>
        </el-card>

        <!-- 订单详情对话框 -->
        <el-dialog v-model="detailsDialogVisible" title="订单详情" width="60%">
            <div v-if="currentOrder" class="order-details">
                <el-descriptions :column="2" border>
                    <el-descriptions-item label="订单号" :span="2">{{ currentOrder.id }}</el-descriptions-item>
                    <el-descriptions-item label="房源名称" :span="2">{{ currentOrder.homestayTitle ||
                        currentOrder.homestayName
                    }}</el-descriptions-item>
                    <el-descriptions-item label="客户姓名">{{ currentOrder.guestName }}</el-descriptions-item>
                    <el-descriptions-item label="客户电话">{{ currentOrder.guestPhone }}</el-descriptions-item>
                    <el-descriptions-item label="入住日期">{{ currentOrder.checkInDate }}</el-descriptions-item>
                    <el-descriptions-item label="退房日期">{{ currentOrder.checkOutDate }}</el-descriptions-item>
                    <el-descriptions-item label="入住天数">{{ currentOrder.nights }}晚</el-descriptions-item>
                    <el-descriptions-item label="入住人数">{{ currentOrder.guestCount }}人</el-descriptions-item>
                    <el-descriptions-item label="订单金额" :span="2">
                        <span class="price">¥{{ formatAmount(currentOrder.totalPrice || currentOrder.totalAmount) }}</span>
                    </el-descriptions-item>
                    <el-descriptions-item label="订单状态" :span="2">
                        <el-tag :type="getStatusType(currentOrder.status)">
                            {{ getStatusText(currentOrder) }}
                        </el-tag>
                    </el-descriptions-item>
                    <el-descriptions-item label="支付状态" :span="2">
                        <el-tag :type="getPaymentStatusType(currentOrder.paymentStatus)" effect="plain">
                            {{ getPaymentStatusText(currentOrder.paymentStatus) }}
                        </el-tag>
                    </el-descriptions-item>
                    <el-descriptions-item label="创建时间" :span="2">{{ currentOrder.createTime ||
                        currentOrder.createdTime
                        }}</el-descriptions-item>
                    <el-descriptions-item label="备注" :span="2">{{ currentOrder.remark ||
                        currentOrder.remarks || '无'
                        }}</el-descriptions-item>
                </el-descriptions>

                <!-- 退款信息区域 -->
                <div v-if="hasRefundInfo(currentOrder)" class="refund-info-section">
                    <el-divider content-position="left">
                        <el-icon><Warning /></el-icon>
                        退款信息
                    </el-divider>
                    <el-descriptions :column="2" border size="small">
                        <el-descriptions-item label="退款状态" :span="2">
                            <el-tag :type="getPaymentStatusType(currentOrder.paymentStatus)" effect="dark" size="small">
                                {{ getPaymentStatusText(currentOrder.paymentStatus) }}
                            </el-tag>
                        </el-descriptions-item>
                        <el-descriptions-item v-if="currentOrder.refundType" label="退款类型">
                            {{ getRefundTypeText(currentOrder.refundType) }}
                        </el-descriptions-item>
                        <el-descriptions-item v-if="currentOrder.refundAmount" label="退款金额">
                            <span class="refund-amount">¥{{ formatAmount(currentOrder.refundAmount) }}</span>
                        </el-descriptions-item>
                        <el-descriptions-item v-if="currentOrder.refundReason" label="退款原因" :span="2">
                            {{ currentOrder.refundReason }}
                        </el-descriptions-item>
                        <el-descriptions-item v-if="currentOrder.refundInitiatedByName" label="退款发起人">
                            {{ currentOrder.refundInitiatedByName }}
                        </el-descriptions-item>
                        <el-descriptions-item v-if="currentOrder.refundInitiatedAt" label="发起时间">
                            {{ formatDateTime(currentOrder.refundInitiatedAt) }}
                        </el-descriptions-item>
                        <el-descriptions-item v-if="currentOrder.refundProcessedByName" label="退款处理人">
                            {{ currentOrder.refundProcessedByName }}
                        </el-descriptions-item>
                        <el-descriptions-item v-if="currentOrder.refundProcessedAt" label="处理时间">
                            {{ formatDateTime(currentOrder.refundProcessedAt) }}
                        </el-descriptions-item>
                        <el-descriptions-item v-if="currentOrder.refundTransactionId" label="退款交易号" :span="2">
                            <el-tag size="small" type="info" effect="plain">{{ currentOrder.refundTransactionId }}</el-tag>
                        </el-descriptions-item>
                    </el-descriptions>
                </div>

                <!-- 操作按钮 -->
                <div class="detail-actions" style="margin-top: 20px; text-align: right;">
                    <el-button v-if="currentOrder.status === 'PENDING'" type="success"
                        @click="handleConfirm(currentOrder)">
                        确认订单
                    </el-button>
                    <el-button v-if="canDo(OrderAction.REJECT, currentOrder)" type="danger"
                        @click="handleReject(currentOrder)">
                        拒绝订单
                    </el-button>
                    <el-button v-if="currentOrder.status === 'PAID'" type="primary"
                        @click="openPrepareCheckIn(currentOrder)">
                        设置准备入住
                    </el-button>
                    <el-button v-if="currentOrder.status === 'READY_FOR_CHECKIN'" type="primary"
                        @click="handlePerformCheckIn(currentOrder)">
                        办理入住
                    </el-button>
                    <el-button v-if="currentOrder.status === 'READY_FOR_CHECKIN'" type="warning"
                        @click="handleCancelPrepare(currentOrder)">
                        取消准备
                    </el-button>
                    <el-button v-if="currentOrder.status === 'CHECKED_IN'" type="success"
                        @click="openCheckOut(currentOrder)">
                        办理退房
                    </el-button>
                    <el-button v-if="currentOrder.status === 'CHECKED_OUT'" type="warning"
                        @click="openDeposit(currentOrder)">
                        押金操作
                    </el-button>
                    <el-button v-if="currentOrder.status === 'CHECKED_OUT'" type="success"
                        @click="handleConfirmSettlement(currentOrder)">
                        确认结算
                    </el-button>
                    <el-button v-if="canDo(OrderAction.CANCEL, currentOrder)"
                        type="danger" @click="handleCancel(currentOrder)">
                        取消订单
                    </el-button>
                    <el-button
                        v-if="canInitiateRefund(currentOrder)"
                        type="danger" plain @click="handleRefund(currentOrder)">
                        发起退款
                    </el-button>
                    <el-button
                        v-if="currentOrder.paymentStatus === 'REFUND_PENDING' && currentOrder.status !== 'DISPUTE_PENDING'"
                        type="warning" @click="handleReviewRefund(currentOrder)">
                        审核退款
                    </el-button>
                    <el-button v-if="canDo(OrderAction.DISPUTE, currentOrder)"
                        type="danger" plain @click="handleRaiseDispute(currentOrder)">
                        发起争议
                    </el-button>
                    <el-button @click="detailsDialogVisible = false">关闭</el-button>
                </div>
            </div>
            <div v-else>加载中或无订单数据...</div>
        </el-dialog>

        <!-- 取消订单对话框 -->
        <ReasonDialog v-model="cancelDialogVisible" title="取消订单" label="取消原因"
            confirm-text="确认取消订单" :on-submit="submitCancel" />

        <!-- 拒绝订单对话框 -->
        <ReasonDialog v-model="rejectDialogVisible" title="拒绝订单" label="拒绝原因"
            confirm-text="确认拒绝订单" :on-submit="submitReject" />

        <!-- 退款确认对话框 -->
        <RefundDialog v-model="refundDialogVisible" :order="currentOrder" @confirmed="handleRefundConfirmed" />

        <!-- 退款审核对话框 -->
        <el-dialog v-model="reviewRefundDialogVisible" title="审核退款申请" width="45%">
            <div v-if="currentOrder" class="refund-dialog-content">
                <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 20px;">
                    <template #title>
                        <span>请审核对订单 <strong>#{{ currentOrder.id }}</strong> 发起的退款申请。</span>
                    </template>
                </el-alert>

                <el-descriptions :column="1" border size="small" class="refund-order-info">
                    <el-descriptions-item label="退款原因">{{ currentOrder.refundReason || '无' }}</el-descriptions-item>
                    <el-descriptions-item label="退款金额">
                        <span class="refund-amount-highlight">¥{{ formatAmount(currentOrder.refundAmount || currentOrder.totalAmount) }}</span>
                    </el-descriptions-item>
                </el-descriptions>

                <el-form :model="reviewRefundForm" ref="reviewRefundFormRef" style="margin-top: 20px;">
                    <el-form-item label="审核结果" required>
                        <el-radio-group v-model="reviewRefundForm.action">
                            <el-radio label="approve" value="approve">同意退款</el-radio>
                            <el-radio label="reject" value="reject">拒绝退款</el-radio>
                        </el-radio-group>
                    </el-form-item>
                    <el-form-item :label="reviewRefundForm.action === 'approve' ? '同意备注' : '拒绝原因'" prop="reason"
                        :rules="[{ required: reviewRefundForm.action === 'reject', message: '请输入拒绝原因', trigger: 'blur' }]">
                        <el-input v-model="reviewRefundForm.reason" type="textarea" :rows="3"
                            :placeholder="reviewRefundForm.action === 'approve' ? '选填：同意退款备注' : '必填：请输入拒绝退款的原因'" maxlength="200" show-word-limit />
                    </el-form-item>
                </el-form>
            </div>
            <template #footer>
                <span class="dialog-footer">
                    <el-button @click="reviewRefundDialogVisible = false">取消</el-button>
                    <el-button :type="reviewRefundForm.action === 'approve' ? 'success' : 'danger'" @click="confirmReviewRefund" :loading="reviewRefundSubmitting">
                        确认提交
                    </el-button>
                </span>
            </template>
        </el-dialog>

        <!-- 发起争议对话框 -->
        <el-dialog v-model="disputeDialogVisible" title="发起争议" width="45%">
            <div v-if="currentOrder" class="refund-dialog-content">
                <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 20px;">
                    <template #title>
                        <span>对订单 <strong>#{{ currentOrder.id }}</strong> 的退款有异议？</span>
                    </template>
                    <div>发起争议后，订单将进入争议处理流程，需要管理员进行仲裁。</div>
                </el-alert>

                <el-descriptions :column="1" border size="small" class="refund-order-info">
                    <el-descriptions-item label="订单号">{{ currentOrder.orderNumber || currentOrder.id }}</el-descriptions-item>
                    <el-descriptions-item label="退款原因">{{ currentOrder.refundReason || '无' }}</el-descriptions-item>
                    <el-descriptions-item label="退款金额">
                        <span class="refund-amount-highlight">¥{{ formatAmount(currentOrder.refundAmount || currentOrder.totalAmount) }}</span>
                    </el-descriptions-item>
                </el-descriptions>

                <el-form :model="disputeForm" ref="disputeFormRef" style="margin-top: 20px;">
                    <el-form-item label="争议原因" prop="reason"
                        :rules="[{ required: true, message: '请输入争议原因', trigger: 'blur' }]">
                        <el-input v-model="disputeForm.reason" type="textarea" :rows="3"
                            placeholder="请输入您认为不应退款的原因，例如：客人违反了房屋使用规定、房屋损坏等" maxlength="200" show-word-limit />
                    </el-form-item>
                </el-form>
            </div>
            <template #footer>
                <span class="dialog-footer">
                    <el-button @click="disputeDialogVisible = false">取消</el-button>
                    <el-button type="danger" @click="confirmDispute" :loading="disputeSubmitting">
                        发起争议
                    </el-button>
                </span>
            </template>
        </el-dialog>

        <!-- 自动状态配置对话框 -->
        <el-dialog v-model="autoStatusConfigVisible" title="自动状态流转配置" width="60%">
            <div v-if="autoStatusConfig" class="auto-config-content">
                <el-descriptions title="配置信息" :column="2" border>
                    <el-descriptions-item label="自动入住时间">{{ autoStatusConfig.autoCheckInTime }}</el-descriptions-item>
                    <el-descriptions-item label="自动完成时间">{{ autoStatusConfig.autoCheckOutTime }}</el-descriptions-item>
                    <el-descriptions-item label="错过入住处理时间">{{ autoStatusConfig.cancelMissedCheckInTime
                        }}</el-descriptions-item>
                    <el-descriptions-item label="检查频率">{{ autoStatusConfig.checkInterval }}</el-descriptions-item>
                </el-descriptions>

                <el-divider content-position="left">
                    <strong>自动流转规则</strong>
                </el-divider>

                <div v-if="autoStatusConfig.rules" class="rules-content">
                    <el-card v-for="(rule, key) in autoStatusConfig.rules" :key="key" class="rule-card" shadow="never">
                        <template #header>
                            <span class="rule-title">{{ key }}</span>
                        </template>
                        <p class="rule-description">{{ rule }}</p>
                    </el-card>
                </div>

                <el-alert title="说明" type="info" :closable="false" style="margin-top: 20px;">
                    <p>系统会根据上述规则自动处理订单状态流转，减少人工干预，提高效率。</p>
                    <p>如有特殊情况需要人工处理，请及时联系系统管理员。</p>
                </el-alert>
            </div>
            <template #footer>
                <span class="dialog-footer">
                    <el-button @click="autoStatusConfigVisible = false">关闭</el-button>
                </span>
            </template>
        </el-dialog>

        <!-- 设置准备入住对话框 -->
        <el-dialog v-model="prepareCheckInDialogVisible" title="设置准备入住" width="50%">
            <div v-if="currentOrder" class="prepare-checkin-content">
                <el-alert type="info" :closable="false" show-icon style="margin-bottom: 20px;">
                    <template #title>
                        <span>为订单 <strong>#{{ currentOrder.id }}</strong> 设置入住凭证</span>
                    </template>
                </el-alert>

                <el-descriptions :column="2" border size="small" style="margin-bottom: 20px;">
                    <el-descriptions-item label="房源">{{ currentOrder.homestayTitle || currentOrder.homestayName }}</el-descriptions-item>
                    <el-descriptions-item label="客户">{{ currentOrder.guestName }}</el-descriptions-item>
                    <el-descriptions-item label="入住日期">{{ currentOrder.checkInDate }}</el-descriptions-item>
                    <el-descriptions-item label="退房日期">{{ currentOrder.checkOutDate }}</el-descriptions-item>
                </el-descriptions>

                <el-form :model="prepareCheckInForm" label-width="120px">
                    <el-form-item label="入住方式">
                        <el-radio-group v-model="prepareCheckInForm.checkInMethod">
                            <el-radio label="MANUAL">人工办理</el-radio>
                            <el-radio label="SELF_SERVICE">自助入住</el-radio>
                        </el-radio-group>
                    </el-form-item>
                    <el-form-item label="门锁密码" v-if="prepareCheckInForm.checkInMethod === 'MANUAL' || prepareCheckInForm.checkInMethod === 'SELF_SERVICE'">
                        <el-input v-model="prepareCheckInForm.doorPassword" placeholder="请输入门锁密码" />
                    </el-form-item>
                    <el-form-item label="密钥箱密码" v-if="prepareCheckInForm.checkInMethod === 'SELF_SERVICE'">
                        <el-input v-model="prepareCheckInForm.lockboxCode" placeholder="请输入密钥箱密码" />
                    </el-form-item>
                    <el-form-item label="位置描述" v-if="prepareCheckInForm.checkInMethod === 'SELF_SERVICE'">
                        <el-input v-model="prepareCheckInForm.locationDescription" type="textarea" :rows="2" placeholder="请输入房源位置描述，帮助客人找到房源" />
                    </el-form-item>
                    <el-form-item label="备注">
                        <el-input v-model="prepareCheckInForm.remark" type="textarea" :rows="2" placeholder="选填：备注信息" />
                    </el-form-item>
                </el-form>
            </div>
            <template #footer>
                <span class="dialog-footer">
                    <el-button @click="prepareCheckInDialogVisible = false">取消</el-button>
                    <el-button type="primary" @click="submitPrepareCheckIn" :loading="prepareCheckInSubmitting">
                        确认设置
                    </el-button>
                </span>
            </template>
        </el-dialog>

        <!-- 查看入住凭证对话框 -->
        <el-dialog v-model="viewCredentialDialogVisible" title="入住凭证" width="50%">
            <div v-if="checkInCredential" class="credential-content">
                <el-descriptions :column="2" border size="small">
                    <el-descriptions-item label="入住方式">{{ checkInCredential.checkInMethod === 'MANUAL' ? '人工办理' : '自助入住' }}</el-descriptions-item>
                    <el-descriptions-item label="入住码">
                        <span style="font-weight: bold; font-size: 18px; color: var(--el-color-primary);">{{ checkInCredential.checkInCode }}</span>
                    </el-descriptions-item>
                    <el-descriptions-item label="门锁密码" v-if="checkInCredential.doorPassword">{{ checkInCredential.doorPassword }}</el-descriptions-item>
                    <el-descriptions-item label="密钥箱密码" v-if="checkInCredential.lockboxCode">{{ checkInCredential.lockboxCode }}</el-descriptions-item>
                    <el-descriptions-item label="位置描述" :span="2" v-if="checkInCredential.locationDescription">{{ checkInCredential.locationDescription }}</el-descriptions-item>
                    <el-descriptions-item label="有效起始" v-if="checkInCredential.validFrom">{{ checkInCredential.validFrom }}</el-descriptions-item>
                    <el-descriptions-item label="有效截止" v-if="checkInCredential.validUntil">{{ checkInCredential.validUntil }}</el-descriptions-item>
                    <el-descriptions-item label="备注" :span="2" v-if="checkInCredential.remark">{{ checkInCredential.remark }}</el-descriptions-item>
                </el-descriptions>
            </div>
            <template #footer>
                <span class="dialog-footer">
                    <el-button @click="viewCredentialDialogVisible = false">关闭</el-button>
                </span>
            </template>
        </el-dialog>

        <!-- 办理退房对话框 -->
        <el-dialog v-model="checkOutDialogVisible" title="办理退房" width="50%">
            <div v-if="currentOrder" class="checkout-content">
                <el-alert type="info" :closable="false" show-icon style="margin-bottom: 20px;">
                    <template #title>
                        <span>确认办理订单 <strong>#{{ currentOrder.id }}</strong> 退房</span>
                    </template>
                </el-alert>

                <el-descriptions :column="2" border size="small" style="margin-bottom: 20px;">
                    <el-descriptions-item label="房源">{{ currentOrder.homestayTitle || currentOrder.homestayName }}</el-descriptions-item>
                    <el-descriptions-item label="客户">{{ currentOrder.guestName }}</el-descriptions-item>
                    <el-descriptions-item label="入住时间">{{ currentOrder.checkedInAt || currentOrder.checkInDate }}</el-descriptions-item>
                    <el-descriptions-item label="退房时间">{{ formatDateTime(new Date().toISOString()) }}</el-descriptions-item>
                    <el-descriptions-item label="押金金额">¥{{ currentOrder.depositAmount || 0 }}</el-descriptions-item>
                </el-descriptions>

                <el-form :model="checkOutForm" label-width="100px">
                    <el-form-item label="备注">
                        <el-input v-model="checkOutForm.remark" type="textarea" :rows="2" placeholder="选填：退房备注" />
                    </el-form-item>
                </el-form>
            </div>
            <template #footer>
                <span class="dialog-footer">
                    <el-button @click="checkOutDialogVisible = false">取消</el-button>
                    <el-button type="primary" @click="submitCheckOut" :loading="checkOutSubmitting">
                        确认退房
                    </el-button>
                </span>
            </template>
        </el-dialog>

        <!-- 押金操作对话框 -->
        <el-dialog v-model="depositDialogVisible" title="押金操作" width="50%">
            <div v-if="currentOrder && checkOutRecord" class="deposit-content">
                <el-alert type="info" :closable="false" show-icon style="margin-bottom: 20px;">
                    <template #title>
                        <span>请选择押金操作类型</span>
                    </template>
                </el-alert>

                <el-descriptions :column="2" border size="small" style="margin-bottom: 20px;">
                    <el-descriptions-item label="押金状态">
                        <el-tag :type="getDepositStatusType(checkOutRecord.depositStatus)" size="small">
                            {{ getDepositStatusText(checkOutRecord.depositStatus) }}
                        </el-tag>
                    </el-descriptions-item>
                    <el-descriptions-item label="押金金额">¥{{ checkOutRecord.depositAmount || 0 }}</el-descriptions-item>
                    <el-descriptions-item label="结算金额">¥{{ checkOutRecord.settlementAmount || 0 }}</el-descriptions-item>
                    <el-descriptions-item label="额外费用">¥{{ checkOutRecord.extraCharges || 0 }}</el-descriptions-item>
                </el-descriptions>

                <el-form :model="depositForm" label-width="100px">
                    <el-form-item label="操作类型" required>
                        <el-radio-group v-model="depositForm.action">
                            <el-radio label="COLLECT">收取押金</el-radio>
                            <el-radio label="REFUND">退还押金</el-radio>
                            <el-radio label="RETAIN">扣押押金</el-radio>
                            <el-radio label="WAIVE">免除押金</el-radio>
                        </el-radio-group>
                    </el-form-item>
                    <el-form-item label="金额" v-if="depositForm.action === 'COLLECT' || depositForm.action === 'RETAIN'">
                        <el-input-number v-model="depositForm.amount" :min="0" :precision="2" :step="10" style="width: 200px;" />
                    </el-form-item>
                    <el-form-item label="说明" v-if="depositForm.action === 'RETAIN'">
                        <el-input v-model="depositForm.note" type="textarea" :rows="2" placeholder="请输入扣押原因" />
                    </el-form-item>
                </el-form>
            </div>
            <template #footer>
                <span class="dialog-footer">
                    <el-button @click="depositDialogVisible = false">取消</el-button>
                    <el-button type="primary" @click="submitDeposit" :loading="depositSubmitting">
                        确认操作
                    </el-button>
                </span>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { Search, Refresh, Clock, CircleCheck, Warning, Setting, ArrowDown } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import {
    getHostOrders,
    cancelOrder,
    confirmOrder,
    rejectOrder,
    getHostOrderStats,
    hostApproveRefund,
    hostRejectRefund,
    hostRaiseDispute,
    prepareCheckIn,
    getCheckInCredential,
    performCheckIn,
    cancelPrepareCheckIn,
    performCheckOut,
    getCheckOutRecord,
    processDeposit,
    confirmSettlement
} from '@/api/hostOrder'
import { getHostHomestayOptions } from '@/api/host'
import { canPerformActionOnStatus, OrderAction } from '@/utils/orderPermission'
import type { HostOrderItem } from '@/types/hostOrder'
import {
    getStatusText, getStatusType, canInitiateRefund, getPaymentStatusText, getPaymentStatusType,
    getRefundTypeText, hasRefundInfo, formatAmount, getDepositStatusText, getDepositStatusType,
    formatDateString, formatDateTime
} from '@/utils/orderDisplay'
import ReasonDialog from '@/components/host/order/ReasonDialog.vue'
import RefundDialog from '@/components/host/order/RefundDialog.vue'


// 新增：定义房源选项接口
interface HomestayOption {
    id: number;
    title: string;
}

const userStore = useUserStore()

const currentUserIdentity = computed(() => {
    if (!userStore.user) return null
    return {
        userId: userStore.user.id,
        role: userStore.user.role || ''
    }
})

// 辅助：判断当前用户是否可执行某操作
const canDo = (action: OrderAction, order: HostOrderItem) => {
    if (!currentUserIdentity.value) return false
    return canPerformActionOnStatus(action, order.status, currentUserIdentity.value.role)
}

// 统计数据
const orderStats = reactive({
    total: 0,
    pending: 0,
    confirmed: 0,
    checked_in: 0,
    paid: 0,
    completed: 0,
    cancelled: 0,
    rejected: 0
})

// 自动状态统计数据
const autoStatusStats = reactive({
    pendingAutoCheckIn: 0,
    pendingAutoComplete: 0,
    missedCheckIn: 0
})

// 自动状态管理相关
const autoStatusLoading = ref(false)
const autoStatusConfigVisible = ref(false)
const autoStatusConfig = ref<any>({})

// 筛选表单
const filterForm = reactive({
    homestayId: null as number | null,
    status: null as string | null,
    dateRange: null as [string, string] | null
})

// 房源选项 - 修改类型定义
const homestayOptions = ref<HomestayOption[]>([])

// 页码相关
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 订单列表 - 使用定义的接口
const orders = ref<HostOrderItem[]>([])

// 详情对话框相关
const detailsDialogVisible = ref(false)
const currentOrder = ref<HostOrderItem | null>(null)

// 取消/拒绝订单对话框（ReasonDialog 内部管理 form 与 submitting）
const cancelDialogVisible = ref(false)
const rejectDialogVisible = ref(false)

// 退款对话框相关（RefundDialog 内部管理 form/preview/submitting）
const refundDialogVisible = ref(false)

// 审核退款相关
const reviewRefundDialogVisible = ref(false)
const reviewRefundFormRef = ref<FormInstance>()
const reviewRefundSubmitting = ref(false)
const reviewRefundForm = reactive({
    action: 'approve',
    reason: ''
})

// 争议对话框相关
const disputeDialogVisible = ref(false)
const disputeFormRef = ref<FormInstance>()
const disputeSubmitting = ref(false)
const disputeForm = reactive({
    reason: ''
})

// 设置准备入住对话框相关
const prepareCheckInDialogVisible = ref(false)
const prepareCheckInSubmitting = ref(false)
const prepareCheckInForm = reactive({
    checkInMethod: 'MANUAL',
    doorPassword: '',
    lockboxCode: '',
    locationDescription: '',
    remark: ''
})

// 查看入住凭证对话框相关
const viewCredentialDialogVisible = ref(false)
const checkInCredential = ref<any>(null)

// 办理退房对话框相关
const checkOutDialogVisible = ref(false)
const checkOutSubmitting = ref(false)
const checkOutForm = reactive({
    remark: ''
})

// 押金操作对话框相关
const depositDialogVisible = ref(false)
const depositSubmitting = ref(false)
const depositForm = reactive({
    action: 'REFUND',
    amount: 0,
    note: ''
})
const checkOutRecord = ref<any>(null)

// 处理筛选
const handleFilter = () => {
    fetchOrders()
}

// 重置筛选
const resetFilter = () => {
    filterForm.homestayId = null
    filterForm.status = null
    filterForm.dateRange = null
    fetchOrders()
}

// 获取状态文本（包含退款类型信息）
// 获取状态类型
// 判断是否可以发起退款
// 获取支付状态文本
// 获取支付状态标签类型
// 获取退款类型文本
// 判断是否有退款信息
// 判断是否有”更多”下拉菜单的操作（始终显示，因为详情已移入下拉菜单）
// 安全金额格式化
// 判断是否有"更多"下拉菜单的操作（除详情外是否还有其他可操作项）
const hasMoreActions = (order: HostOrderItem) => {
    if (!order || !currentUserIdentity.value) return false
    return canDo(OrderAction.REJECT, order) ||
        canDo(OrderAction.CANCEL, order) ||
        canDo(OrderAction.DISPUTE, order) ||
        order.status === 'READY_FOR_CHECKIN' ||
        order.status === 'PAID' ||
        canInitiateRefund(order)
}

// 展示订单详情
const handleDetails = (order: HostOrderItem) => {
    currentOrder.value = order
    detailsDialogVisible.value = true
}

// 获取房源列表 - 修改函数实现
const fetchHomestayOptions = async () => {
    try {
        // 调用正确的 API
        const options = await getHostHomestayOptions()
        // 假设 options 直接就是 {id, title}[] 格式
        // 如果不是，可能需要解包，例如 options = options.data
        if (options && Array.isArray(options)) {
            homestayOptions.value = options.map(opt => ({ id: opt.id, title: opt.title }))
        } else {
            console.warn('getHostHomestayOptions 返回的数据格式不符合预期:', options)
            homestayOptions.value = []
        }

    } catch (error) {
        console.error('获取房源选项失败:', error)
        homestayOptions.value = [] // 出错时清空
    }
}



// 确认订单
const handleConfirm = async (order: HostOrderItem) => {
    if (!order || !order.id) {
        ElMessage.error('无效的订单数据')
        return
    }

    ElMessageBox.confirm('确认接受此订单吗？', '确认订单', {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'success'
    }).then(async () => {
        loading.value = true
        try {
            console.log('确认订单ID:', order.id)
            await confirmOrder(order.id)
            ElMessage.success('订单已确认')

            // 关闭详情对话框（如果打开）
            if (detailsDialogVisible.value) {
                detailsDialogVisible.value = false
            }

            await refreshOrdersAndStats()
        } catch (error: any) {
            console.error('确认订单失败:', error)
        } finally {
            loading.value = false
        }
    }).catch(() => { })
}

// 办理入住 - 房东手动办理（READY_FOR_CHECKIN -> CHECKED_IN）
const handlePerformCheckIn = async (order: HostOrderItem) => {
    if (!order || !order.id) {
        ElMessage.error('无效的订单数据')
        return
    }

    ElMessageBox.confirm('确认为客户办理入住吗？', '办理入住', {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'info'
    }).then(async () => {
        loading.value = true
        try {
            console.log('办理入住，订单ID:', order.id)
            await performCheckIn(order.id)
            ElMessage.success('已办理入住')

            // 关闭详情对话框（如果打开）
            if (detailsDialogVisible.value) {
                detailsDialogVisible.value = false
            }

            await refreshOrdersAndStats()
        } catch (error: any) {
            console.error('办理入住失败:', error)
        } finally {
            loading.value = false
        }
    }).catch(() => { })
}

// 打开设置准备入住对话框
const openPrepareCheckIn = (order: HostOrderItem) => {
    if (!order || !order.id) {
        ElMessage.error('无效的订单数据')
        return
    }
    currentOrder.value = order
    // 重置表单
    prepareCheckInForm.checkInMethod = 'MANUAL'
    prepareCheckInForm.doorPassword = ''
    prepareCheckInForm.lockboxCode = ''
    prepareCheckInForm.locationDescription = ''
    prepareCheckInForm.remark = ''
    prepareCheckInDialogVisible.value = true
}

// 提交设置准备入住
const submitPrepareCheckIn = async () => {
    if (!currentOrder.value || !currentOrder.value.id) {
        ElMessage.error('无效的订单数据')
        return
    }

    prepareCheckInSubmitting.value = true
    try {
        console.log('设置准备入住，订单ID:', currentOrder.value.id, prepareCheckInForm)
        await prepareCheckIn(currentOrder.value.id, {
            checkInMethod: prepareCheckInForm.checkInMethod,
            doorPassword: prepareCheckInForm.doorPassword || undefined,
            lockboxCode: prepareCheckInForm.lockboxCode || undefined,
            locationDescription: prepareCheckInForm.locationDescription || undefined,
            remark: prepareCheckInForm.remark || undefined
        })
        ElMessage.success('已设置准备入住')
        prepareCheckInDialogVisible.value = false

        // 关闭详情对话框（如果打开）
        if (detailsDialogVisible.value) {
            detailsDialogVisible.value = false
        }

        updateStats()
        fetchOrders()
    } catch (error: any) {
        console.error('设置准备入住失败:', error)
    } finally {
        prepareCheckInSubmitting.value = false
    }
}

// 查看入住凭证
const openViewCredential = async (order: HostOrderItem) => {
    if (!order || !order.id) {
        ElMessage.error('无效的订单数据')
        return
    }
    try {
        const res = await getCheckInCredential(order.id)
        checkInCredential.value = res.data || res
        viewCredentialDialogVisible.value = true
    } catch (error: any) {
        console.error('获取入住凭证失败:', error)
    }
}

// 取消准备入住
const handleCancelPrepare = async (order: HostOrderItem) => {
    if (!order || !order.id) {
        ElMessage.error('无效的订单数据')
        return
    }

    ElMessageBox.confirm('确认取消准备入住吗？', '取消准备', {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning'
    }).then(async () => {
        loading.value = true
        try {
            console.log('取消准备入住，订单ID:', order.id)
            await cancelPrepareCheckIn(order.id)
            ElMessage.success('已取消准备入住')

            // 关闭详情对话框（如果打开）
            if (detailsDialogVisible.value) {
                detailsDialogVisible.value = false
            }

            await refreshOrdersAndStats()
        } catch (error: any) {
            console.error('取消准备入住失败:', error)
        } finally {
            loading.value = false
        }
    }).catch(() => { })
}

// 打开退房对话框
const openCheckOut = (order: HostOrderItem) => {
    if (!order || !order.id) {
        ElMessage.error('无效的订单数据')
        return
    }
    currentOrder.value = order
    checkOutForm.remark = ''
    checkOutDialogVisible.value = true
}

// 提交退房
const submitCheckOut = async () => {
    if (!currentOrder.value || !currentOrder.value.id) {
        ElMessage.error('无效的订单数据')
        return
    }

    checkOutSubmitting.value = true
    try {
        console.log('办理退房，订单ID:', currentOrder.value.id)
        await performCheckOut(currentOrder.value.id, {
            remark: checkOutForm.remark || undefined
        })
        ElMessage.success('已办理退房')
        checkOutDialogVisible.value = false

        // 关闭详情对话框（如果打开）
        if (detailsDialogVisible.value) {
            detailsDialogVisible.value = false
        }

        await refreshOrdersAndStats()
    } catch (error: any) {
        console.error('办理退房失败:', error)
    } finally {
        checkOutSubmitting.value = false
    }
}

// 打开押金操作对话框
const openDeposit = async (order: HostOrderItem) => {
    if (!order || !order.id) {
        ElMessage.error('无效的订单数据')
        return
    }
    currentOrder.value = order
    // 获取退房记录
    try {
        const res = await getCheckOutRecord(order.id)
        checkOutRecord.value = res.data || res
        depositForm.action = 'REFUND'
        depositForm.amount = checkOutRecord.value?.depositAmount || 0
        depositForm.note = ''
        depositDialogVisible.value = true
    } catch (error: any) {
        console.error('获取退房记录失败:', error)
    }
}

// 提交押金操作
const submitDeposit = async () => {
    if (!currentOrder.value || !currentOrder.value.id) {
        ElMessage.error('无效的订单数据')
        return
    }

    if ((depositForm.action === 'COLLECT' || depositForm.action === 'RETAIN') && depositForm.amount <= 0) {
        ElMessage.error('请输入有效的金额')
        return
    }

    depositSubmitting.value = true
    try {
        console.log('押金操作，订单ID:', currentOrder.value.id, depositForm)
        await processDeposit(
            currentOrder.value.id,
            depositForm.action,
            depositForm.action === 'COLLECT' || depositForm.action === 'RETAIN' ? depositForm.amount : undefined,
            depositForm.action === 'RETAIN' ? depositForm.note : undefined
        )
        ElMessage.success('押金操作成功')
        depositDialogVisible.value = false

        // 关闭详情对话框（如果打开）
        if (detailsDialogVisible.value) {
            detailsDialogVisible.value = false
        }

        await refreshOrdersAndStats()
    } catch (error: any) {
        console.error('押金操作失败:', error)
    } finally {
        depositSubmitting.value = false
    }
}

// 确认结算
const handleConfirmSettlement = async (order: HostOrderItem) => {
    if (!order || !order.id) {
        ElMessage.error('无效的订单数据')
        return
    }

    ElMessageBox.confirm('确认结算此订单吗？结算后将生成收益记录。', '确认结算', {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'success'
    }).then(async () => {
        loading.value = true
        try {
            console.log('确认结算，订单ID:', order.id)
            await confirmSettlement(order.id)
            ElMessage.success('已确认结算，订单已完成')

            // 关闭详情对话框（如果打开）
            if (detailsDialogVisible.value) {
                detailsDialogVisible.value = false
            }

            await refreshOrdersAndStats()
        } catch (error: any) {
            console.error('确认结算失败:', error)
        } finally {
            loading.value = false
        }
    }).catch(() => { })
}

// 获取押金状态文本
// 获取押金状态类型
// 取消订单
const handleCancel = (order: HostOrderItem) => {
    if (!order || !order.id) {
        ElMessage.error('无效的订单数据')
        return
    }

    currentOrder.value = order
    cancelDialogVisible.value = true
}

// 取消订单提交（由 ReasonDialog 回调）
const submitCancel = async (reason: string) => {
    if (!currentOrder.value) {
        ElMessage.error('当前没有选中的订单')
        return
    }

    await cancelOrder(currentOrder.value.id, reason)
    ElMessage.success('订单已取消')

    if (detailsDialogVisible.value) {
        detailsDialogVisible.value = false
    }

    await refreshOrdersAndStats()
}



// 处理拒绝订单
const handleReject = (order: HostOrderItem) => {
    if (!order || !order.id) {
        ElMessage.error('无效的订单数据')
        return
    }

    currentOrder.value = order
    rejectDialogVisible.value = true
}

// 拒绝订单提交（由 ReasonDialog 回调）
const submitReject = async (reason: string) => {
    if (!currentOrder.value) {
        ElMessage.error('当前没有选中的订单')
        return
    }

    await rejectOrder(currentOrder.value.id, reason)
    ElMessage.success('订单已拒绝')

    if (detailsDialogVisible.value) {
        detailsDialogVisible.value = false
    }

    await refreshOrdersAndStats()
}

// 发起退款
const handleRefund = (order: HostOrderItem) => {
    if (!order || !order.id) {
        ElMessage.error('无效的订单数据')
        return
    }

    currentOrder.value = order
    refundDialogVisible.value = true
}

// 退款成功回调（RefundDialog 触发）
const handleRefundConfirmed = async () => {
    if (detailsDialogVisible.value) {
        detailsDialogVisible.value = false
    }

    await refreshOrdersAndStats()
}

// 审核退款
const handleReviewRefund = (order: HostOrderItem) => {
    if (!order || !order.id) {
        ElMessage.error('无效的订单数据')
        return
    }

    currentOrder.value = order
    reviewRefundForm.action = 'approve'
    reviewRefundForm.reason = ''
    reviewRefundDialogVisible.value = true
}

// 确认审核退款
const confirmReviewRefund = async () => {
    if (!reviewRefundFormRef.value) return
    if (!currentOrder.value) {
        ElMessage.error('当前没有选中的订单')
        return
    }

    await reviewRefundFormRef.value.validate(async (valid: boolean) => {
        if (valid) {
            reviewRefundSubmitting.value = true

            try {
                if (reviewRefundForm.action === 'approve') {
                    console.log('同意退款，订单ID:', currentOrder.value!.id, '备注:', reviewRefundForm.reason)
                    await hostApproveRefund(currentOrder.value!.id, reviewRefundForm.reason)
                    ElMessage.success('已同意退款')
                } else {
                    console.log('拒绝退款，订单ID:', currentOrder.value!.id, '原因:', reviewRefundForm.reason)
                    await hostRejectRefund(currentOrder.value!.id, reviewRefundForm.reason)
                    ElMessage.success('已拒绝退款')
                }

                reviewRefundDialogVisible.value = false

                if (detailsDialogVisible.value) {
                    detailsDialogVisible.value = false
                }

                await refreshOrdersAndStats()
            } catch (error: any) {
                console.error('审核退款失败:', error)
            } finally {
                reviewRefundSubmitting.value = false
            }
        }
    })
}

// 发起争议
const handleRaiseDispute = (order: HostOrderItem) => {
    if (!order || !order.id) {
        ElMessage.error('无效的订单数据')
        return
    }

    currentOrder.value = order
    disputeForm.reason = ''
    disputeDialogVisible.value = true
}

// 确认发起争议
const confirmDispute = async () => {
    if (!disputeFormRef.value) return
    if (!currentOrder.value) {
        ElMessage.error('当前没有选中的订单')
        return
    }

    await disputeFormRef.value.validate(async (valid: boolean) => {
        if (valid) {
            disputeSubmitting.value = true

            try {
                console.log('发起争议，订单ID:', currentOrder.value!.id, '原因:', disputeForm.reason)
                await hostRaiseDispute(currentOrder.value!.id, disputeForm.reason)

                ElMessage.success('争议已发起，等待管理员仲裁')
                disputeDialogVisible.value = false

                if (detailsDialogVisible.value) {
                    detailsDialogVisible.value = false
                }

                await refreshOrdersAndStats()
            } catch (error: any) {
                console.error('发起争议失败:', error)
            } finally {
                disputeSubmitting.value = false
            }
        }
    })
}

// 更新统计数据
const updateStats = async () => {
    try {
        // 使用后端的getHostOrderStats接口获取统计数据
        const response = await getHostOrderStats();
        if (response && response.data) {
            // 从响应中获取统计数据
            orderStats.pending = response.data.pending || 0;
            orderStats.confirmed = response.data.confirmed || 0;
            orderStats.checked_in = response.data.checkedIn || 0;
            orderStats.paid = response.data.paid || 0;
            orderStats.completed = response.data.completed || 0;
            orderStats.cancelled = response.data.cancelled || 0;
            orderStats.rejected = response.data.rejected || 0;
            orderStats.total = response.data.total || 0;
        } else {
            // 如果API不可用，则使用本地数据计算
            orderStats.pending = orders.value.filter(item => item.status === 'PENDING').length;
            orderStats.confirmed = orders.value.filter(item => item.status === 'CONFIRMED').length;
            orderStats.checked_in = orders.value.filter(item => item.status === 'CHECKED_IN').length;
            orderStats.paid = orders.value.filter(item => item.status === 'PAID').length;
            orderStats.completed = orders.value.filter(item => item.status === 'COMPLETED').length;
            orderStats.cancelled = orders.value.filter(item => ['CANCELLED', 'CANCELLED_SYSTEM', 'CANCELLED_BY_USER', 'CANCELLED_BY_HOST'].includes(item.status)).length;
            orderStats.rejected = orders.value.filter(item => item.status === 'REJECTED').length;
            orderStats.total = orders.value.length;
        }
    } catch (error) {
        console.error('获取订单统计失败:', error);
        // 降级为使用本地数据计算
        orderStats.pending = orders.value.filter(item => item.status === 'PENDING').length;
        orderStats.confirmed = orders.value.filter(item => item.status === 'CONFIRMED').length;
        orderStats.checked_in = orders.value.filter(item => item.status === 'CHECKED_IN').length;
        orderStats.paid = orders.value.filter(item => item.status === 'PAID').length;
        orderStats.completed = orders.value.filter(item => item.status === 'COMPLETED').length;
        orderStats.cancelled = orders.value.filter(item => ['CANCELLED', 'CANCELLED_SYSTEM', 'CANCELLED_BY_USER', 'CANCELLED_BY_HOST'].includes(item.status)).length;
        orderStats.rejected = orders.value.filter(item => item.status === 'REJECTED').length;
        orderStats.total = orders.value.length;
    }
}

// 页码变化
const handleCurrentChange = (page: number) => {
    currentPage.value = page
    fetchOrders()
}

// 每页数量变化
const handleSizeChange = (size: number) => {
    pageSize.value = size
    fetchOrders()
}

// 安全刷新：操作后若当前页为空则自动回退上一页
const refreshOrdersAndStats = async () => {
    await fetchOrders()
    await updateStats()
    // 若当前页无数据且不在第一页，回退一页
    if (orders.value.length === 0 && currentPage.value > 1) {
        currentPage.value -= 1
        await fetchOrders()
    }
}

// 统一提取分页数据（兼容多种后端返回格式）
const extractPageData = (res: any): { content: any[], totalElements: number } => {
    if (!res) return { content: [], totalElements: 0 }
    const payload = res.data ?? res
    if (payload && Array.isArray(payload.content)) {
        return { content: payload.content, totalElements: payload.totalElements ?? payload.content.length }
    }
    if (payload && payload.data && Array.isArray(payload.data.content)) {
        return { content: payload.data.content, totalElements: payload.data.totalElements ?? payload.data.content.length }
    }
    if (Array.isArray(payload)) {
        return { content: payload, totalElements: payload.length }
    }
    if (payload && payload.data && Array.isArray(payload.data)) {
        return { content: payload.data, totalElements: payload.data.length }
    }
    if (payload && typeof payload === 'object') {
        return { content: [payload], totalElements: 1 }
    }
    return { content: [], totalElements: 0 }
}

// 获取订单列表
const fetchOrders = async () => {
    loading.value = true

    try {
        // 构建查询参数
        const params: any = {
            page: currentPage.value - 1,
            size: pageSize.value,
        }

        if (filterForm.homestayId) {
            params.homestayId = filterForm.homestayId
        }

        if (filterForm.status) {
            params.status = filterForm.status
        }

        if (filterForm.dateRange && Array.isArray(filterForm.dateRange) && filterForm.dateRange.length === 2) {
            params.startDate = filterForm.dateRange[0]
            params.endDate = filterForm.dateRange[1]
        }

        console.log('发送查询参数:', params)

        try {
            const response = await getHostOrders(params)
            console.log('订单API响应:', response)
            const { content, totalElements } = extractPageData(response)
            orders.value = content
            total.value = totalElements

            // 更新统计数据
            await updateStats()
        } catch (apiError) {
            console.error('房东订单接口不可用:', apiError)
            ElMessage.warning('订单接口请求异常，请稍后再试')
            orders.value = []
            total.value = 0
        }
    } catch (error) {
        console.error('获取订单列表失败:', error)
        orders.value = []
        total.value = 0
    } finally {
        loading.value = false
    }
}

// 格式化日期字符串为 (MM-DD) 格式
// 格式化日期时间字符串
// 获取自动状态统计
const fetchAutoStatusStats = async () => {
    try {
        const response = await request({
            url: '/api/host/order-auto-status/stats',
            method: 'get'
        })
        if (response && response.data) {
            autoStatusStats.pendingAutoCheckIn = response.data.pendingAutoCheckIn || 0
            autoStatusStats.pendingAutoComplete = response.data.pendingAutoComplete || 0
            autoStatusStats.missedCheckIn = response.data.missedCheckIn || 0
        }
    } catch (error: any) {
        console.error('获取自动状态统计失败:', error)
        // 如果是403错误，说明权限不足，隐藏自动管理模块
        if (error.response?.status === 403) {
            console.warn('当前用户没有访问自动状态管理的权限')
            // 可以设置一个标志来隐藏自动管理模块
        }
        // 不显示错误消息，避免干扰用户
    }
}

// 显示自动状态配置
const showAutoStatusConfig = async () => {
    try {
        const response = await request({
            url: '/api/host/order-auto-status/config',
            method: 'get'
        })
        if (response && response.data) {
            autoStatusConfig.value = response.data
            autoStatusConfigVisible.value = true
        }
    } catch (error) {
        console.error('获取自动状态配置失败:', error)
    }
}

// 手动触发自动状态流转
const manualTriggerAutoStatus = async () => {
    ElMessageBox.confirm('确认手动执行一次自动状态流转吗？', '手动执行', {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'info'
    }).then(async () => {
        autoStatusLoading.value = true
        try {
            await request({
                url: '/api/host/order-auto-status/trigger',
                method: 'post'
            })
            ElMessage.success('自动状态流转执行完成')
            // 刷新统计数据
            fetchAutoStatusStats()
            await refreshOrdersAndStats()
        } catch (error: any) {
            console.error('手动触发自动状态流转失败:', error)
        } finally {
            autoStatusLoading.value = false
        }
    }).catch(() => { })
}

onMounted(() => {
    fetchHomestayOptions()
    fetchOrders()
    updateStats()
    fetchAutoStatusStats()
})
</script>

<style scoped>
.order-container {
    padding: 24px;
    background-color: #f5f7fa;
    min-height: calc(100vh - 60px);
}

.stat-row {
    margin-bottom: 20px;
}

.stat-card {
    height: 110px;
    border-radius: 12px;
    border: none;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04);
    transition: all 0.3s ease;
}

.stat-card:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 20px rgba(0, 0, 0, 0.08);
}

.stat-content {
    height: 100%;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: flex-start;
    padding: 0 10px;
}

.stat-title {
    font-size: 14px;
    color: #64748b;
    margin-bottom: 12px;
    font-weight: 500;
}

.stat-value {
    font-size: 28px;
    font-weight: 600;
    color: #1e293b;
    line-height: 1;
}

.filter-card {
    margin-bottom: 24px;
    border-radius: 12px;
    border: none;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04);
}

.order-list-card {
    min-height: 500px;
    border-radius: 12px;
    border: none;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04);
}

.pagination {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
}

.empty-data {
    padding: 40px;
    text-align: center;
    color: #909399;
}

.order-details .price {
    color: #ff6600;
    font-weight: bold;
    font-size: 16px;
}

.detail-actions {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
    gap: 10px;
}

.filter-header {
    margin-bottom: 10px;
    font-size: 16px;
    font-weight: bold;
}

.filter-form {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
}

.ellipsis-text {
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.date-range {
    display: flex;
    align-items: center;
}

.price-value {
    font-weight: bold;
}

/* 优化表格整体感觉 */
:deep(.el-table) {
    --el-table-border-color: #f0f2f5;
    --el-table-header-bg-color: #f8fafc;
    --el-table-header-text-color: #64748b;
    border-radius: 8px;
    overflow: hidden;
}

:deep(.el-table th.el-table__cell) {
    font-weight: 600;
    padding: 12px 0;
}

:deep(.el-table td.el-table__cell) {
    padding: 16px 0;
}

:deep(.el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell) {
    background: #fafafa;
}

/* 自动状态管理样式 */
.auto-status-row {
    border-top: 2px solid #f0f2f5;
    padding-top: 20px;
    margin-top: 20px;
}

.auto-status-card {
    border: 1px solid #e8f4fd;
    background: linear-gradient(135deg, #f8fbff 0%, #f0f8ff 100%);
    height: 140px;
}

.auto-status-card .el-card__body {
    height: 100%;
    padding: 8px 12px;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
}

.auto-status-card .stat-title {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 5px;
    color: #1890ff;
    font-weight: 600;
    font-size: 13px;
    text-align: center;
    margin-bottom: 0;
}

.auto-status-card .stat-value {
    margin: 5px 0;
    font-size: 24px;
    font-weight: bold;
    text-align: center;
}

.auto-status-card .stat-value.auto-checkin {
    color: #52c41a;
}

.auto-status-card .stat-value.auto-complete {
    color: #1890ff;
}

.auto-status-card .stat-value.missed-checkin {
    color: #ff4d4f;
}

.stat-desc {
    font-size: 11px;
    color: #8c8c8c;
    margin: 0;
    line-height: 1.2;
    text-align: center;
}

.management-card {
    height: 140px !important;
}

.management-actions {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin-top: 10px;
    width: 100%;
}

.action-group {
    display: flex;
    gap: 6px;
    width: 100%;
}

.management-actions .el-button {
    flex: 1;
    margin: 0;
    padding: 6px 8px;
    font-size: 11px;
    height: 28px;
    border-radius: 4px;
    white-space: nowrap;
}

/* 配置对话框样式 */
.auto-config-content {
    padding: 10px 0;
}

.rules-content {
    margin-top: 16px;
}

.rule-card {
    margin-bottom: 12px;
    border: 1px solid #e8f4fd;
}

.rule-title {
    font-weight: 600;
    color: #1890ff;
}

.rule-description {
    margin: 0;
    color: #666;
    line-height: 1.6;
}

/* 退款相关样式 */
.refund-info-section {
    margin-top: 20px;
}

.refund-info-section .el-divider__text {
    display: flex;
    align-items: center;
    gap: 6px;
    color: #e6a23c;
    font-weight: 600;
}

.refund-amount {
    color: #f56c6c;
    font-weight: bold;
    font-size: 15px;
}

.refund-dialog-content .refund-order-info {
    margin-bottom: 10px;
}

.refund-amount-highlight {
    color: #f56c6c;
    font-weight: bold;
    font-size: 18px;
}
</style>
