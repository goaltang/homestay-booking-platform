<template>
    <div class="order-list app-container">
        <!-- 异常订单统计卡片 -->
        <div class="stats-grid" v-loading="statsLoading">
            <el-card shadow="hover" class="stat-card exception-card" @click="filterException('pendingTimeout')">
                <div class="stat-content">
                    <div class="stat-icon warning">
                        <el-icon><Clock /></el-icon>
                    </div>
                    <div class="stat-info">
                        <div class="stat-value">{{ exceptionStats.pendingTimeout }}</div>
                        <div class="stat-label">待处理超时</div>
                    </div>
                </div>
            </el-card>
            <el-card shadow="hover" class="stat-card exception-card" @click="filterException('paymentFailed')">
                <div class="stat-content">
                    <div class="stat-icon danger">
                        <el-icon><Money /></el-icon>
                    </div>
                    <div class="stat-info">
                        <div class="stat-value">{{ exceptionStats.paymentFailed }}</div>
                        <div class="stat-label">支付失败</div>
                    </div>
                </div>
            </el-card>
            <el-card shadow="hover" class="stat-card exception-card" @click="filterException('refundFailed')">
                <div class="stat-content">
                    <div class="stat-icon danger">
                        <el-icon><Warning /></el-icon>
                    </div>
                    <div class="stat-info">
                        <div class="stat-value">{{ exceptionStats.refundFailed }}</div>
                        <div class="stat-label">退款失败</div>
                    </div>
                </div>
            </el-card>
            <el-card shadow="hover" class="stat-card exception-card" @click="filterException('notCheckedIn')">
                <div class="stat-content">
                    <div class="stat-icon warning">
                        <el-icon><House /></el-icon>
                    </div>
                    <div class="stat-info">
                        <div class="stat-value">{{ exceptionStats.notCheckedIn }}</div>
                        <div class="stat-label">未入住</div>
                    </div>
                </div>
            </el-card>
            <el-card shadow="hover" class="stat-card exception-card" @click="filterException('refundPending')">
                <div class="stat-content">
                    <div class="stat-icon info">
                        <el-icon><Refresh /></el-icon>
                    </div>
                    <div class="stat-info">
                        <div class="stat-value">{{ exceptionStats.refundPending }}</div>
                        <div class="stat-label">退款处理中</div>
                    </div>
                </div>
            </el-card>
            <el-card shadow="hover" class="stat-card exception-card" @click="filterException('disputePending')">
                <div class="stat-content">
                    <div class="stat-icon warning">
                        <el-icon><Warning /></el-icon>
                    </div>
                    <div class="stat-info">
                        <div class="stat-value">{{ exceptionStats.disputePending }}</div>
                        <div class="stat-label">争议待处理</div>
                    </div>
                </div>
            </el-card>
            <el-card shadow="hover" class="stat-card total-card" @click="filterException('all')">
                <div class="stat-content">
                    <div class="stat-icon primary">
                        <el-icon><WarningFilled /></el-icon>
                    </div>
                    <div class="stat-info">
                        <div class="stat-value">{{ exceptionStats.total }}</div>
                        <div class="stat-label">异常订单总数</div>
                    </div>
                </div>
            </el-card>
        </div>

        <!-- 当前筛选提示 -->
        <el-alert
            v-if="currentExceptionFilter"
            :title="currentExceptionFilterTitle"
            type="warning"
            :closable="true"
            @close="clearExceptionFilter"
            class="exception-filter-alert"
            show-icon
        >
            <template #default>
                点击 <el-button type="primary" link @click="clearExceptionFilter">清除筛选</el-button> 返回全部订单
            </template>
        </el-alert>

        <!-- 搜索区域 -->
        <div class="search-box">
            <el-form :inline="true" :model="searchForm" ref="searchFormRef">
                <el-form-item label="订单号" prop="orderNumber">
                    <el-input v-model="searchForm.orderNumber" placeholder="请输入订单号" clearable />
                </el-form-item>
                <el-form-item label="住客名称" prop="guestName">
                    <el-input v-model="searchForm.guestName" placeholder="请输入住客名称" clearable />
                </el-form-item>
                <el-form-item label="订单状态" prop="status">
                    <el-select v-model="searchForm.status" placeholder="订单状态" clearable style="width: 140px">
                        <el-option v-for="(item, key) in orderStatusMap" :key="key" :label="item.text" :value="key" />
                    </el-select>
                </el-form-item>

                <!-- 高级搜索条件 -->
                <transition name="el-zoom-in-top">
                    <div v-show="isSearchExpanded" style="display: inline-block;">
                        <el-form-item label="房源标题" prop="homestayTitle">
                            <el-input v-model="searchForm.homestayTitle" placeholder="请输入房源标题" clearable />
                        </el-form-item>
                        <el-form-item label="房东名称" prop="hostName">
                            <el-input v-model="searchForm.hostName" placeholder="请输入房东名称" clearable />
                        </el-form-item>
                        <el-form-item label="支付状态" prop="paymentStatus">
                            <el-select v-model="searchForm.paymentStatus" placeholder="支付状态" clearable style="width: 140px">
                                <el-option v-for="(item, key) in paymentStatusMap" :key="key" :label="item.text" :value="key" />
                            </el-select>
                        </el-form-item>
                        <el-form-item label="支付方式" prop="paymentMethod">
                            <el-input v-model="searchForm.paymentMethod" placeholder="支付方式" clearable />
                        </el-form-item>
                        <el-form-item label="入住日期" prop="checkInDateRange">
                            <el-date-picker v-model="searchForm.checkInDateRange" type="daterange" range-separator="至"
                                start-placeholder="开始" end-placeholder="结束" clearable style="width: 240px" />
                        </el-form-item>
                        <el-form-item label="创建日期" prop="createTimeRange">
                            <el-date-picker v-model="searchForm.createTimeRange" type="daterange" range-separator="至"
                                start-placeholder="开始" end-placeholder="结束" clearable style="width: 240px" />
                        </el-form-item>
                    </div>
                </transition>

                <el-form-item class="search-actions">
                    <el-button type="primary" @click="handleSearch" :icon="Search">搜索</el-button>
                    <el-button @click="resetSearch" :icon="Refresh">重置</el-button>
                    <el-button type="info" plain @click="toggleSearch">
                        {{ isSearchExpanded ? '收起' : '高级搜索' }}
                        <el-icon class="search-toggle-icon"><component :is="isSearchExpanded ? ArrowUp : ArrowDown" /></el-icon>
                    </el-button>
                    <el-button type="success" @click="handleExport" :icon="Download" style="margin-left: 12px;">导出订单</el-button>
                </el-form-item>
            </el-form>
        </div>

        <!-- 批量操作悬浮条 -->
        <transition name="el-zoom-in-bottom">
            <div class="batch-operation-bar" v-if="selectedRows.length > 0">
                <div class="batch-info">
                    <el-icon class="info-icon"><InfoFilled /></el-icon>
                    <span>已选择 <strong style="color: #6366f1; font-size: 16px;">{{ selectedRows.length }}</strong> 项订单可以进行操作</span>
                </div>
                <div class="batch-buttons">
                    <el-button size="default" type="warning" plain @click="handleBatchRefund">批量发起退款</el-button>
                    <el-button size="default" type="danger" plain @click="handleBatchCancel">批量强制取消</el-button>
                    <el-button size="default" type="primary" @click="handleBatchExport">批量导出Excel</el-button>
                </div>
            </div>
        </transition>

        <el-table :data="tableData" border style="width: 100%" v-loading="loading"
            @selection-change="handleSelectionChange" @sort-change="handleSortChange"
            :default-sort="{ prop: 'createTime', order: 'descending' }">
            <el-table-column type="selection" width="50" align="center" />

            <!-- 订单基础信息 -->
            <el-table-column prop="orderNumber" label="订单号" width="160" sortable="custom" show-overflow-tooltip />

            <!-- 房源和人员信息 -->
            <el-table-column label="房源信息" min-width="200" show-overflow-tooltip>
                <template #default="scope">
                    <div class="homestay-info">
                        <div class="homestay-title">{{ scope.row.homestayTitle }}</div>
                        <div class="host-info">房东：{{ scope.row.hostName }}</div>
                    </div>
                </template>
            </el-table-column>

            <!-- 客人信息 -->
            <el-table-column label="客人信息" width="130" show-overflow-tooltip>
                <template #default="scope">
                    <div class="guest-info">
                        <div class="guest-name">{{ scope.row.guestName }}</div>
                        <div class="guest-count">{{ scope.row.guestCount }}位客人</div>
                    </div>
                </template>
            </el-table-column>

            <!-- 入住信息 -->
            <el-table-column label="入住信息" width="140" prop="checkInDate" sortable="custom"
                :sort-orders="['descending', 'ascending']">
                <template #default="scope">
                    <div class="checkin-info">
                        <div class="date-range">{{ scope.row.checkInDate }} 至</div>
                        <div class="date-range">{{ scope.row.checkOutDate }}</div>
                        <div class="nights">{{ scope.row.nights }}晚</div>
                    </div>
                </template>
            </el-table-column>

            <!-- 订单金额 -->
            <el-table-column prop="totalAmount" label="订单金额" width="120" sortable="custom" align="right">
                <template #default="scope">
                    <div class="amount-info">
                        <div class="total-amount">¥{{ scope.row.totalAmount?.toFixed(2) }}</div>
                        <div class="unit-price">¥{{ scope.row.price }}/晚</div>
                    </div>
                </template>
            </el-table-column>

            <!-- 状态信息 -->
            <el-table-column label="状态" width="140" prop="status" sortable="custom">
                <template #default="scope">
                    <div class="status-info">
                        <el-tag :type="getOrderStatusType(scope.row.status)" size="small" class="status-tag">
                            {{ getOrderStatusText(scope.row.status) }}
                        </el-tag>
                        <el-tag :type="getPaymentStatusType(scope.row.paymentStatus)" size="small" class="payment-tag">
                            {{ getPaymentStatusText(scope.row.paymentStatus) }}
                        </el-tag>
                    </div>
                </template>
            </el-table-column>

            <!-- 创建时间 -->
            <el-table-column prop="createTime" label="创建时间" width="140" sortable="custom">
                <template #default="scope">
                    <div class="time-info">
                        <div class="date">{{ format(new Date(scope.row.createTime), 'MM-dd') }}</div>
                        <div class="time">{{ format(new Date(scope.row.createTime), 'HH:mm') }}</div>
                    </div>
                </template>
            </el-table-column>

            <!-- 备注 -->
            <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip>
                <template #default="scope">
                    <span v-if="scope.row.remark">{{ scope.row.remark }}</span>
                    <span v-else class="text-gray">无</span>
                </template>
            </el-table-column>

            <!-- 操作 -->
            <el-table-column label="操作" width="200" fixed="right" align="center">
                <template #default="scope">
                    <div class="action-buttons">
                        <!-- 核心操作外露 -->
                        <el-button type="primary" link size="small" @click="handleDetail(scope.row)">详情</el-button>

                        <el-button type="success" link size="small" @click="handleConfirmPayment(scope.row)"
                            v-if="scope.row.paymentStatus === 'UNPAID'">
                            确认支付
                        </el-button>

                        <el-button type="primary" link size="small" @click="openPrepareCheckIn(scope.row)"
                            v-if="scope.row.status === 'PAID' && scope.row.paymentStatus === 'PAID'">
                            准备入住
                        </el-button>

                        <el-button type="success" link size="small" @click="handlePerformCheckIn(scope.row)"
                            v-if="scope.row.status === 'READY_FOR_CHECKIN'">
                            办理入住
                        </el-button>

                        <el-button type="primary" link size="small" @click="openCheckOut(scope.row)"
                            v-if="scope.row.status === 'CHECKED_IN'">
                            办理退房
                        </el-button>

                        <el-button type="success" link size="small" @click="handleConfirmSettlement(scope.row)"
                            v-if="scope.row.status === 'CHECKED_OUT'">
                            确认结算
                        </el-button>

                        <!-- 退款申请比较重要，保留在外侧 -->
                        <template v-if="scope.row.paymentStatus === 'REFUND_PENDING'">
                            <el-button type="success" link size="small" @click="handleApproveRefund(scope.row)">
                                批准退款
                            </el-button>
                            <el-button type="danger" link size="small" @click="handleRejectRefund(scope.row)">
                                拒绝退款
                            </el-button>
                        </template>

                        <!-- 更多操作 dropdown -->
                        <el-dropdown trigger="click" @command="(cmd: string) => handleCommand(cmd, scope.row)" v-if="hasMoreActions(scope.row)">
                            <el-button type="primary" link size="small" class="more-btn">
                                更多<el-icon class="el-icon--right"><arrow-down /></el-icon>
                            </el-button>
                            <template #dropdown>
                                <el-dropdown-menu>
                                    <el-dropdown-item command="refund" v-if="scope.row.paymentStatus === 'PAID' && !['COMPLETED', 'CANCELLED', 'REFUNDED'].includes(scope.row.status)">发起退款</el-dropdown-item>
                                    <el-dropdown-item command="raiseDispute" v-if="scope.row.paymentStatus === 'REFUND_PENDING'">发起争议</el-dropdown-item>
                                    <el-dropdown-item command="resolveDispute" v-if="scope.row.status === 'DISPUTE_PENDING'">处理争议</el-dropdown-item>
                                    <el-dropdown-item command="cancelPrepare" v-if="scope.row.status === 'READY_FOR_CHECKIN'">取消准备</el-dropdown-item>
                                    <el-dropdown-item command="deposit" v-if="scope.row.status === 'CHECKED_OUT'">押金操作</el-dropdown-item>
                                    <el-dropdown-item command="complete" v-if="['CHECKED_IN', 'CHECKED_OUT'].includes(scope.row.status)">强制完成</el-dropdown-item>
                                    <el-dropdown-item command="cancel" v-if="!['COMPLETED', 'CANCELLED', 'REFUNDED', 'CANCELLED_BY_USER', 'CANCELLED_BY_HOST', 'CANCELLED_SYSTEM'].includes(scope.row.status) && scope.row.paymentStatus !== 'REFUNDED'" divided class="danger-item">强制取消</el-dropdown-item>
                                </el-dropdown-menu>
                            </template>
                        </el-dropdown>
                    </div>
                </template>
            </el-table-column>
        </el-table>

        <div class="pagination">
            <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize"
                :page-sizes="[10, 20, 50, 100]" :total="total" layout="total, sizes, prev, pager, next, jumper"
                @size-change="handleSizeChange" @current-change="handleCurrentChange" />
        </div>
    </div>

    <!-- 争议处理对话框 -->
    <el-dialog v-model="disputeDialogVisible" title="处理争议" width="500px" destroy-on-close>
        <el-form :model="disputeForm" label-width="100px">
            <el-form-item label="订单号">
                <span>{{ disputeForm.orderNumber }}</span>
            </el-form-item>
            <el-form-item label="退款原因">
                <span>{{ disputeForm.refundReason || '无' }}</span>
            </el-form-item>
            <el-form-item label="争议原因">
                <span>{{ disputeForm.disputeReason || '无' }}</span>
            </el-form-item>
            <el-form-item label="仲裁结果" required>
                <el-radio-group v-model="disputeForm.resolution">
                    <el-radio label="APPROVED">批准退款</el-radio>
                    <el-radio label="REJECTED">拒绝退款（恢复订单）</el-radio>
                </el-radio-group>
            </el-form-item>
            <el-form-item label="仲裁备注">
                <el-input v-model="disputeForm.note" type="textarea" :rows="3" placeholder="可选" />
            </el-form-item>
        </el-form>
        <template #footer>
            <el-button @click="disputeDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="submitDisputeResolution" :loading="submitLoading">确认仲裁</el-button>
        </template>
    </el-dialog>

    <!-- 设置准备入住对话框 -->
    <el-dialog v-model="prepareCheckInDialogVisible" title="设置准备入住" width="550px" destroy-on-close>
        <el-form :model="checkInCredentialForm" label-width="110px">
            <el-form-item label="订单号">
                <span>{{ checkInCredentialForm.orderNumber }}</span>
            </el-form-item>
            <el-form-item label="入住方式">
                <el-select v-model="checkInCredentialForm.checkInMethod" placeholder="选择入住方式" style="width: 100%">
                    <el-option label="手动办理" value="MANUAL" />
                    <el-option label="自助入住" value="SELF_SERVICE" />
                    <el-option label="混合模式" value="MIXED" />
                </el-select>
            </el-form-item>
            <el-form-item label="入住码" v-if="checkInCredentialForm.checkInMethod !== 'MANUAL'">
                <el-input v-model="checkInCredentialForm.checkInCode" placeholder="自动生成的6位数字码" disabled />
                <div class="form-tip">入住码将在保存后自动生成</div>
            </el-form-item>
            <el-form-item label="门锁密码">
                <el-input v-model="checkInCredentialForm.doorPassword" placeholder="请输入门锁密码" />
            </el-form-item>
            <el-form-item label="密钥箱密码">
                <el-input v-model="checkInCredentialForm.lockboxCode" placeholder="请输入密钥箱密码" />
            </el-form-item>
            <el-form-item label="位置描述">
                <el-input v-model="checkInCredentialForm.locationDescription" placeholder="如：小区北门进来左转第3栋" />
            </el-form-item>
            <el-form-item label="备注">
                <el-input v-model="checkInCredentialForm.remark" type="textarea" :rows="2" placeholder="可选备注" />
            </el-form-item>
        </el-form>
        <template #footer>
            <el-button @click="prepareCheckInDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="submitPrepareCheckIn" :loading="submitLoading">确认设置</el-button>
        </template>
    </el-dialog>

    <!-- 入住凭证查看对话框 -->
    <el-dialog v-model="checkInCredentialDialogVisible" title="入住凭证" width="500px" destroy-on-close>
        <el-descriptions :column="1" border v-if="currentCheckInRecord">
            <el-descriptions-item label="入住码">
                <el-tag type="primary" size="large">{{ currentCheckInRecord.checkInCode || '无' }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="门锁密码">{{ currentCheckInRecord.doorPassword || '无' }}</el-descriptions-item>
            <el-descriptions-item label="密钥箱密码">{{ currentCheckInRecord.lockboxCode || '无' }}</el-descriptions-item>
            <el-descriptions-item label="位置描述">{{ currentCheckInRecord.locationDescription || '无' }}</el-descriptions-item>
            <el-descriptions-item label="入住方式">{{ currentCheckInRecord.checkInMethod }}</el-descriptions-item>
            <el-descriptions-item label="有效时间">
                {{ currentCheckInRecord.validFrom || '' }} ~ {{ currentCheckInRecord.validUntil || '' }}
            </el-descriptions-item>
            <el-descriptions-item label="备注">{{ currentCheckInRecord.remark || '无' }}</el-descriptions-item>
        </el-descriptions>
        <template #footer>
            <el-button @click="checkInCredentialDialogVisible = false">关闭</el-button>
        </template>
    </el-dialog>

    <!-- 退房对话框 -->
    <el-dialog v-model="checkOutDialogVisible" title="办理退房" width="500px" destroy-on-close>
        <el-form :model="checkOutForm" label-width="100px">
            <el-form-item label="订单号">
                <span>{{ checkOutForm.orderNumber }}</span>
            </el-form-item>
            <el-form-item label="备注">
                <el-input v-model="checkOutForm.remark" type="textarea" :rows="3" placeholder="退房备注（可选）" />
            </el-form-item>
        </el-form>
        <template #footer>
            <el-button @click="checkOutDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="submitCheckOut" :loading="submitLoading">确认退房</el-button>
        </template>
    </el-dialog>

    <!-- 退房记录查看对话框 -->
    <el-dialog v-model="checkOutRecordDialogVisible" title="退房记录" width="550px" destroy-on-close>
        <el-descriptions :column="1" border v-if="currentCheckOutRecord">
            <el-descriptions-item label="退房方式">{{ currentCheckOutRecord.checkOutMethod }}</el-descriptions-item>
            <el-descriptions-item label="退房时间">{{ currentCheckOutRecord.checkedOutAt }}</el-descriptions-item>
            <el-descriptions-item label="实际住宿">{{ currentCheckOutRecord.actualNights }} 晚</el-descriptions-item>
            <el-descriptions-item label="押金金额">¥{{ currentCheckOutRecord.depositAmount?.toFixed(2) || '0.00' }}</el-descriptions-item>
            <el-descriptions-item label="押金状态">
                <el-tag :type="getDepositStatusType(currentCheckOutRecord.depositStatus)">
                    {{ getDepositStatusText(currentCheckOutRecord.depositStatus) }}
                </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="额外费用">¥{{ currentCheckOutRecord.extraCharges?.toFixed(2) || '0.00' }}</el-descriptions-item>
            <el-descriptions-item label="结算金额">
                <el-tag type="warning">¥{{ currentCheckOutRecord.settlementAmount?.toFixed(2) || '0.00' }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="退房状态">
                <el-tag :type="getSettlementStatusType(currentCheckOutRecord.status)">
                    {{ getSettlementStatusText(currentCheckOutRecord.status) }}
                </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="备注">{{ currentCheckOutRecord.remark || '无' }}</el-descriptions-item>
        </el-descriptions>
        <template #footer>
            <el-button @click="checkOutRecordDialogVisible = false">关闭</el-button>
        </template>
    </el-dialog>

    <!-- 押金操作对话框 -->
    <el-dialog v-model="depositDialogVisible" title="押金操作" width="500px" destroy-on-close>
        <el-form :model="depositForm" label-width="100px">
            <el-form-item label="订单号">
                <span>{{ depositForm.orderNumber }}</span>
            </el-form-item>
            <el-form-item label="押金金额">
                <span class="amount-text">¥{{ depositForm.currentDeposit?.toFixed(2) || '0.00' }}</span>
            </el-form-item>
            <el-form-item label="当前状态">
                <el-tag :type="getDepositStatusType(depositForm.depositStatus)">
                    {{ getDepositStatusText(depositForm.depositStatus) }}
                </el-tag>
            </el-form-item>
            <el-form-item label="操作类型" required>
                <el-radio-group v-model="depositForm.action">
                    <el-radio label="COLLECT">收取押金</el-radio>
                    <el-radio label="REFUND">退还押金</el-radio>
                    <el-radio label="RETAIN">扣押押金</el-radio>
                    <el-radio label="WAIVE">免除押金</el-radio>
                </el-radio-group>
            </el-form-item>
            <el-form-item label="金额" v-if="['COLLECT', 'RETAIN'].includes(depositForm.action)">
                <el-input-number v-model="depositForm.amount" :min="0" :precision="2" placeholder="输入金额" style="width: 100%" />
            </el-form-item>
            <el-form-item label="备注">
                <el-input v-model="depositForm.note" type="textarea" :rows="2" placeholder="押金操作备注" />
            </el-form-item>
        </el-form>
        <template #footer>
            <el-button @click="depositDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="submitDeposit" :loading="submitLoading">确认操作</el-button>
        </template>
    </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { format } from 'date-fns'
import { Search, Refresh, Download, Clock, Money, Warning, WarningFilled, House, ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import {
    getAdminOrders,
    updateOrderStatus,
    exportOrders,
    batchUpdateOrderStatus,
    batchExportOrders,
    confirmPayment,
    initiateRefund,
    approveRefund,
    rejectRefund,
    raiseDispute,
    resolveDispute,
    getExceptionOrderStats,
    prepareCheckIn,
    performCheckIn,
    performCheckOut,
    cancelPrepareCheckIn,
    processDeposit,
    getCheckOutRecord,
    confirmSettlement,
    CheckInRecord,
    CheckOutRecord
} from '@/api/order'

// 定义后端返回的 Order 数据类型 (需要与 OrderDTO.java 对应)
interface AdminOrder {
    id: number;
    orderNumber: string;
    homestayId: number;
    homestayTitle: string;
    guestId: number;
    guestName: string;
    guestPhone: string;
    hostId: number;
    hostName: string;
    checkInDate: string; // LocalDate -> string
    checkOutDate: string; // LocalDate -> string
    nights: number;
    guestCount: number;
    price: number;
    totalAmount: number;
    status: string; // 订单状态 (e.g., PENDING, PAID, CANCELLED)
    paymentStatus: string | null; // 支付状态 (e.g., UNPAID, PAID, REFUNDED)
    paymentMethod: string | null; // 支付方式
    remark: string | null;
    // 退款相关字段
    refundType?: string;
    refundReason?: string;
    refundAmount?: number;
    // 争议相关字段
    disputeReason?: string;
    disputeRaisedBy?: number;
    disputeRaisedAt?: string;
    disputeResolvedAt?: string;
    disputeResolution?: string;
    disputeResolutionNote?: string;
    createTime: string; // LocalDateTime -> string
    updateTime: string; // LocalDateTime -> string
}

// 搜索表单的类型 (根据后端 AdminOrderController 的 getOrders 参数调整)
interface OrderSearchForm {
    orderNumber: string;
    guestName: string;
    homestayTitle: string;
    status: string;
    paymentStatus: string;
    paymentMethod: string;
    hostName: string;
    checkInDateRange: [string, string] | null; // [start, end]
    createTimeRange: [string, string] | null; // [start, end]
}

// 搜索表单
const searchForm = reactive<OrderSearchForm>({
    orderNumber: '',
    guestName: '',
    homestayTitle: '',
    status: '',
    paymentStatus: '',
    paymentMethod: '',
    hostName: '',
    checkInDateRange: null,
    createTimeRange: null
})

// 表格数据
const tableData = ref<AdminOrder[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const selectedRows = ref<AdminOrder[]>([])
const sortInfo = ref({ prop: 'createTime', order: 'descending' }) // 默认排序

// 争议处理对话框状态
const disputeDialogVisible = ref(false)
const submitLoading = ref(false)
const currentDisputeOrderId = ref<number>()
const disputeForm = reactive({
    orderNumber: '',
    refundReason: '',
    disputeReason: '',
    resolution: 'APPROVED' as 'APPROVED' | 'REJECTED',
    note: ''
})

// ========== 入住管理相关 ==========

// 设置准备入住对话框
const prepareCheckInDialogVisible = ref(false)
const checkInCredentialDialogVisible = ref(false)
const checkInCredentialForm = reactive({
    orderId: 0,
    orderNumber: '',
    checkInMethod: 'MANUAL',
    checkInCode: '',
    doorPassword: '',
    lockboxCode: '',
    locationDescription: '',
    remark: ''
})
const currentCheckInRecord = ref<CheckInRecord | null>(null)

// 退房对话框
const checkOutDialogVisible = ref(false)
const checkOutRecordDialogVisible = ref(false)
const checkOutForm = reactive({
    orderId: 0,
    orderNumber: '',
    remark: ''
})
const currentCheckOutRecord = ref<CheckOutRecord | null>(null)

// 押金操作对话框
const depositDialogVisible = ref(false)
const depositForm = reactive({
    orderId: 0,
    orderNumber: '',
    action: 'COLLECT',
    amount: 0,
    note: '',
    currentDeposit: 0,
    depositStatus: ''
})

// 押金状态映射
const depositStatusMap: Record<string, { text: string; type: string }> = {
    PENDING: { text: '待处理', type: 'warning' },
    PAID: { text: '已收取', type: 'success' },
    REFUNDED: { text: '已退还', type: 'info' },
    RETAINED: { text: '已扣押', type: 'danger' },
    WAIVED: { text: '已免除', type: 'info' }
}

const getDepositStatusType = (status: string) => depositStatusMap[status]?.type || ''
const getDepositStatusText = (status: string) => depositStatusMap[status]?.text || status || '未知'

// 结算状态映射
const settlementStatusMap: Record<string, { text: string; type: string }> = {
    PENDING: { text: '待结算', type: 'warning' },
    COMPLETED: { text: '已完成', type: 'success' }
}

const getSettlementStatusType = (status: string) => settlementStatusMap[status]?.type || ''
const getSettlementStatusText = (status: string) => settlementStatusMap[status]?.text || status || '未知'

// ========== 异常订单统计 ==========
const statsLoading = ref(false)
const exceptionStats = ref({
    pendingTimeout: 0,
    paymentFailed: 0,
    refundFailed: 0,
    notCheckedIn: 0,
    refundPending: 0,
    disputePending: 0,
    pendingConfirm: 0,
    total: 0
})
const currentExceptionFilter = ref<string | null>(null)
const currentExceptionFilterTitle = ref('')

// 加载异常订单统计数据
const loadExceptionStats = async () => {
    try {
        statsLoading.value = true
        const res = await getExceptionOrderStats() as any
        exceptionStats.value = {
            pendingTimeout: res.pendingTimeout || 0,
            paymentFailed: res.paymentFailed || 0,
            refundFailed: res.refundFailed || 0,
            notCheckedIn: res.notCheckedIn || 0,
            refundPending: res.refundPending || 0,
            disputePending: res.disputePending || 0,
            pendingConfirm: res.pendingConfirm || 0,
            total: res.total || 0
        }
    } catch (error) {
        console.error('加载异常订单统计失败:', error)
    } finally {
        statsLoading.value = false
    }
}

// 异常订单筛选映射
const exceptionFilterMap: Record<string, { title: string; status?: string; paymentStatus?: string; checkInDateEnd?: string }> = {
    pendingTimeout: { title: '待处理超时订单（PENDING超过24小时）', status: 'PENDING' },
    paymentFailed: { title: '支付失败订单', paymentStatus: 'PAYMENT_FAILED' },
    refundFailed: { title: '退款失败订单', paymentStatus: 'REFUND_FAILED' },
    notCheckedIn: { title: '已支付但未入住订单', status: 'PAID', checkInDateEnd: new Date().toISOString().split('T')[0] },
    refundPending: { title: '退款处理中订单', paymentStatus: 'REFUND_PENDING' },
    disputePending: { title: '争议待处理订单', status: 'DISPUTE_PENDING' },
    pendingConfirm: { title: '待确认订单', status: 'PENDING' },
    all: { title: '所有异常订单' }
}

// 点击异常卡片筛选
const filterException = (type: string) => {
    const filter = exceptionFilterMap[type]
    if (!filter) return

    currentExceptionFilter.value = type
    currentExceptionFilterTitle.value = filter.title

    // 重置搜索表单
    resetSearchForm()

    // 设置筛选条件
    if (filter.status) {
        searchForm.status = filter.status
    }
    if (filter.paymentStatus) {
        searchForm.paymentStatus = filter.paymentStatus
    }
    if (filter.checkInDateEnd) {
        // 设置入住日期结束时间为今天（筛选已过期的）
        searchForm.checkInDateRange = ['', filter.checkInDateEnd]
    }

    // 重新加载数据
    fetchData()
}

// 清除异常筛选
const clearExceptionFilter = () => {
    currentExceptionFilter.value = null
    currentExceptionFilterTitle.value = ''
    resetSearch()
}

// 重置搜索表单（保留函数）
const resetSearchForm = () => {
    searchForm.orderNumber = ''
    searchForm.guestName = ''
    searchForm.homestayTitle = ''
    searchForm.status = ''
    searchForm.paymentStatus = ''
    searchForm.paymentMethod = ''
    searchForm.hostName = ''
    searchForm.checkInDateRange = null
    searchForm.createTimeRange = null
}

// 状态映射
const orderStatusMap = {
    PENDING: { text: '待确认', type: 'warning' },
    CONFIRMED: { text: '已确认', type: 'primary' },
    PAID: { text: '已支付', type: 'success' },
    READY_FOR_CHECKIN: { text: '待入住', type: 'info' },
    CHECKED_IN: { text: '已入住', type: 'warning' },
    CHECKED_OUT: { text: '已退房', type: 'info' },
    COMPLETED: { text: '已完成', type: 'success' },
    CANCELLED: { text: '已取消', type: 'info' },
    CANCELLED_BY_USER: { text: '用户取消', type: 'info' },
    CANCELLED_BY_HOST: { text: '房东取消', type: 'info' },
    CANCELLED_SYSTEM: { text: '系统取消', type: 'info' },
    PAYMENT_PENDING: { text: '支付中', type: 'warning' },
    PAYMENT_FAILED: { text: '支付失败', type: 'danger' },
    REFUND_PENDING: { text: '退款中', type: 'warning' },
    REFUNDED: { text: '已退款', type: 'info' },
    REFUND_FAILED: { text: '退款失败', type: 'danger' },
    DISPUTE_PENDING: { text: '争议待处理', type: 'warning' },
    REJECTED: { text: '已拒绝', type: 'danger' }
}

const paymentStatusMap = {
    UNPAID: { text: '未支付', type: 'warning' },
    PAID: { text: '已支付', type: 'success' },
    PAYMENT_FAILED: { text: '支付失败', type: 'danger' },
    REFUND_PENDING: { text: '退款中', type: 'warning' },
    DISPUTED: { text: '争议中', type: 'warning' },
    REFUNDED: { text: '已退款', type: 'info' },
    PARTIALLY_REFUNDED: { text: '部分退款', type: 'primary' },
    REFUND_FAILED: { text: '退款失败', type: 'danger' }
}

const getOrderStatusText = (status: string) => {
    return orderStatusMap[status as keyof typeof orderStatusMap]?.text || status || '未知'
}

const getOrderStatusType = (status: string) => {
    return orderStatusMap[status as keyof typeof orderStatusMap]?.type || ''
}

const getPaymentStatusText = (status: string | null) => {
    if (!status) return 'N/A';
    return paymentStatusMap[status as keyof typeof paymentStatusMap]?.text || status || '未知'
}

const getPaymentStatusType = (status: string | null) => {
    if (!status) return 'info';
    return paymentStatusMap[status as keyof typeof paymentStatusMap]?.type || ''
}

// 获取数据 - 重写以适配新API和筛选
const fetchData = async () => {
    loading.value = true
    const params = {
        page: currentPage.value - 1, // 后端需要 0-based page
        size: pageSize.value,
        orderNumber: searchForm.orderNumber || undefined,
        guestName: searchForm.guestName || undefined,
        homestayTitle: searchForm.homestayTitle || undefined,
        status: searchForm.status || undefined,
        paymentStatus: searchForm.paymentStatus || undefined,
        paymentMethod: searchForm.paymentMethod || undefined,
        hostName: searchForm.hostName || undefined,
        checkInDateStart: searchForm.checkInDateRange?.[0] ? format(new Date(searchForm.checkInDateRange[0]), 'yyyy-MM-dd') : undefined,
        checkInDateEnd: searchForm.checkInDateRange?.[1] ? format(new Date(searchForm.checkInDateRange[1]), 'yyyy-MM-dd') : undefined,
        createTimeStart: searchForm.createTimeRange?.[0] ? format(new Date(searchForm.createTimeRange[0]), 'yyyy-MM-dd') : undefined,
        createTimeEnd: searchForm.createTimeRange?.[1] ? format(new Date(searchForm.createTimeRange[1]), 'yyyy-MM-dd') : undefined,
        sort: `${sortInfo.value.prop},${sortInfo.value.order === 'ascending' ? 'asc' : 'desc'}`
    }
    console.log('Fetching orders with params:', params);
    try {
        const res = await getAdminOrders(params) // 调用新的API函数
        tableData.value = res.content
        total.value = res.totalElements
    } catch (error) {
        console.error('获取订单列表失败:', error)
        ElMessage.error('获取订单列表失败')
    } finally {
        loading.value = false
    }
}

// 表格排序变化
const handleSortChange = ({ prop, order }: { prop: string; order: string | null }) => {
    sortInfo.value.prop = prop;
    sortInfo.value.order = order || 'descending'; // 默认为降序
    fetchData();
};

// 搜索方法
const handleSearch = () => {
    currentPage.value = 1
    fetchData()
}

// 高级搜索展开状态
const isSearchExpanded = ref(false)
const toggleSearch = () => {
    isSearchExpanded.value = !isSearchExpanded.value
}

// 重置搜索
const resetSearch = () => {
    searchForm.orderNumber = ''
    searchForm.guestName = ''
    searchForm.homestayTitle = ''
    searchForm.status = ''
    searchForm.paymentStatus = ''
    searchForm.paymentMethod = ''
    searchForm.hostName = ''
    searchForm.checkInDateRange = null
    searchForm.createTimeRange = null
    handleSearch()
}

// 分页方法
const handleSizeChange = (val: number) => {
    pageSize.value = val
    fetchData()
}

const handleCurrentChange = (val: number) => {
    currentPage.value = val
    fetchData()
}

// 查看详情
const handleDetail = (_row: any) => {
    // TODO: 跳转到订单详情页面
    ElMessage.info('订单详情功能开发中')
}

// 强制完成订单（管理员异常处理）
const handleComplete = async (row: AdminOrder) => {
    try {
        await ElMessageBox.confirm(
            `确认要强制完成订单 ${row.orderNumber} 吗？<br/><small>此操作通常用于处理异常情况，请谨慎操作</small>`,
            '强制完成订单',
            {
                confirmButtonText: '确定完成',
                cancelButtonText: '取消',
                type: 'warning',
                dangerouslyUseHTMLString: true
            }
        );
        loading.value = true;
        await updateOrderStatus(row.id, 'COMPLETED');
        ElMessage.success('订单已强制标记为完成');
        const index = tableData.value.findIndex(item => item.id === row.id);
        if (index !== -1) {
            tableData.value[index].status = 'COMPLETED';
        }
    } catch (error) {
        if (error !== 'cancel') {
            console.error('强制完成订单失败:', error);
            const message = (error as any)?.response?.data?.message || (error as Error)?.message || '操作失败，请重试';
            ElMessage.error(`强制完成订单失败: ${message}`);
        }
    } finally {
        loading.value = false;
    }
}

// 强制取消订单（管理员异常处理）
const handleCancel = async (row: AdminOrder) => {
    try {
        await ElMessageBox.prompt(
            `确认要强制取消订单 ${row.orderNumber} 吗？<br/><small>此操作通常用于处理违规、纠纷等异常情况</small>`,
            '强制取消订单',
            {
                confirmButtonText: '确定取消',
                cancelButtonText: '取消',
                inputPlaceholder: '请输入取消原因（必填）',
                inputType: 'textarea',
                type: 'warning',
                dangerouslyUseHTMLString: true,
                inputValidator: (value: string) => {
                    if (!value || value.trim() === '') {
                        return '请输入取消原因'
                    }
                    return true
                }
            }
        )
        loading.value = true;
        await updateOrderStatus(row.id, 'CANCELLED_SYSTEM');
        ElMessage.success('订单已强制取消');
        const index = tableData.value.findIndex(item => item.id === row.id);
        if (index !== -1) {
            tableData.value[index].status = 'CANCELLED_SYSTEM';
        }
    } catch (error) {
        if (error !== 'cancel') {
            console.error('强制取消订单失败:', error);
            const message = (error as any)?.response?.data?.message || (error as Error)?.message || '操作失败';
            ElMessage.error(`强制取消订单失败: ${message}`);
        }
    } finally {
        loading.value = false;
    }
}

// 导出订单
const handleExport = async () => {
    try {
        loading.value = true
        const blob = await exportOrders({
            ...searchForm,
            page: currentPage.value,
            pageSize: pageSize.value
        })

        // 创建下载链接
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = `订单数据_${new Date().toISOString().slice(0, 10)}.xlsx`
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        URL.revokeObjectURL(url)

        ElMessage.success('导出成功')
    } catch (error) {
        console.error('导出失败:', error)
        ElMessage.error('导出失败')
    } finally {
        loading.value = false
    }
}

// 批量发起退款
const handleBatchRefund = async () => {
    if (selectedRows.value.length === 0) {
        ElMessage.warning('请至少选择一项')
        return
    }

    // 筛选出可以退款的订单（已支付且未完成/取消的订单）
    const validOrders = selectedRows.value.filter(item =>
        item.paymentStatus === 'PAID' &&
        !['COMPLETED', 'CANCELLED', 'REFUNDED'].includes(item.status)
    )

    if (validOrders.length === 0) {
        ElMessage.warning('所选订单中没有可以发起退款的订单（需要已支付且未完成状态）')
        return
    }

    try {
        await ElMessageBox.confirm(
            `确认要为选中的 ${validOrders.length} 个订单批量发起退款吗？<br/><small>此操作将把这些订单标记为退款处理中</small>`,
            '批量发起退款',
            {
                confirmButtonText: '确定发起',
                cancelButtonText: '取消',
                type: 'warning',
                dangerouslyUseHTMLString: true
            }
        )

        loading.value = true
        // 这里需要调用批量退款API，暂时使用循环调用单个退款API
        for (const order of validOrders) {
            await initiateRefund(order.id)
        }
        ElMessage.success(`成功为 ${validOrders.length} 个订单发起退款`)
        fetchData()
    } catch (error) {
        if (error !== 'cancel') {
            console.error('批量发起退款失败:', error)
            ElMessage.error('批量发起退款失败')
        }
    } finally {
        loading.value = false
    }
}

// 批量强制取消订单
const handleBatchCancel = async () => {
    if (selectedRows.value.length === 0) {
        ElMessage.warning('请至少选择一项')
        return
    }

    // 筛选出可以取消的订单（未完成/未取消的订单）
    const validOrders = selectedRows.value.filter(item =>
        !['COMPLETED', 'CANCELLED', 'REFUNDED', 'CANCELLED_BY_USER', 'CANCELLED_BY_HOST', 'CANCELLED_SYSTEM'].includes(item.status)
    )

    if (validOrders.length === 0) {
        ElMessage.warning('所选订单中没有可以取消的订单')
        return
    }

    try {
        await ElMessageBox.prompt(
            `确认要批量强制取消选中的 ${validOrders.length} 个订单吗？<br/><small>此操作通常用于处理违规、纠纷等异常情况</small>`,
            '批量强制取消',
            {
                confirmButtonText: '确定取消',
                cancelButtonText: '取消',
                inputPlaceholder: '请输入取消原因（必填）',
                inputType: 'textarea',
                type: 'warning',
                dangerouslyUseHTMLString: true,
                inputValidator: (value: string) => {
                    if (!value || value.trim() === '') {
                        return '请输入取消原因'
                    }
                    return true
                }
            }
        )

        loading.value = true
        const ids = validOrders.map(item => item.id)
        await batchUpdateOrderStatus(ids, 'CANCELLED_SYSTEM')
        ElMessage.success(`成功强制取消 ${validOrders.length} 个订单`)
        fetchData()
    } catch (error) {
        if (error !== 'cancel') {
            console.error('批量强制取消失败:', error)
            ElMessage.error('批量强制取消失败')
        }
    } finally {
        loading.value = false
    }
}

// 批量导出订单
const handleBatchExport = async () => {
    if (selectedRows.value.length === 0) {
        ElMessage.warning('请至少选择一项')
        return
    }

    try {
        loading.value = true
        const ids = selectedRows.value.map(item => item.id)
        const blob = await batchExportOrders(ids)

        // 创建下载链接
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = `订单数据_${selectedRows.value.length}条_${new Date().toISOString().slice(0, 10)}.xlsx`
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        URL.revokeObjectURL(url)

        ElMessage.success('批量导出成功')
    } catch (error) {
        console.error('批量导出失败:', error)
        ElMessage.error('批量导出失败')
    } finally {
        loading.value = false
    }
}

// 确认支付
const handleConfirmPayment = async (row: AdminOrder) => {
    try {
        await ElMessageBox.confirm(
            `确认要将订单 ${row.orderNumber} 标记为已支付吗？`,
            '确认支付',
            { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
        )
        loading.value = true;
        await confirmPayment(row.id);
        ElMessage.success('订单支付状态已确认');
        fetchData(); // 重新加载数据以更新状态
    } catch (error) {
        if (error !== 'cancel') { // 用户取消操作时通常返回 'cancel'
            console.error('确认支付失败:', error);
            // 尝试从 error 对象获取后端返回的错误信息
            const message = (error as any)?.response?.data?.error || (error as Error)?.message || '操作失败';
            ElMessage.error(`确认支付失败: ${message}`);
        }
    } finally {
        loading.value = false;
    }
}

// 发起退款
const handleRefund = async (row: AdminOrder) => {
    try {
        await ElMessageBox.confirm(
            `确认要为订单 ${row.orderNumber} 发起退款吗？<br/><small>(此操作仅标记状态为退款中，实际退款需后续处理)</small>`,
            '发起退款',
            {
                confirmButtonText: '确定发起',
                cancelButtonText: '取消',
                type: 'warning',
                dangerouslyUseHTMLString: true // 允许 HTML 字符串
            }
        )
        loading.value = true;
        await initiateRefund(row.id);
        ElMessage.success('已发起退款流程，订单状态更新为退款中');
        fetchData(); // 重新加载数据
    } catch (error) {
        if (error !== 'cancel') {
            console.error('发起退款失败:', error);
            const message = (error as any)?.response?.data?.error || (error as Error)?.message || '操作失败';
            ElMessage.error(`发起退款失败: ${message}`);
        }
    } finally {
        loading.value = false;
    }
}

// 批准退款申请
const handleApproveRefund = async (row: AdminOrder) => {
    try {
        const { value: refundNote } = await ElMessageBox.prompt(
            `确认批准订单 ${row.orderNumber} 的退款申请吗？<br/><small>批准后将自动完成退款处理</small>`,
            '批准退款',
            {
                confirmButtonText: '批准退款',
                cancelButtonText: '取消',
                inputPlaceholder: '请输入批准备注（可选）',
                inputType: 'textarea',
                dangerouslyUseHTMLString: true
            }
        )
        loading.value = true;
        await approveRefund(row.id, refundNote || '');
        ElMessage.success('退款申请已批准并完成处理');
        fetchData();
    } catch (error) {
        if (error !== 'cancel') {
            console.error('批准退款失败:', error);
            const message = (error as any)?.response?.data?.error || (error as Error)?.message || '操作失败';
            ElMessage.error(`批准退款失败: ${message}`);
        }
    } finally {
        loading.value = false;
    }
}

// 拒绝退款申请
const handleRejectRefund = async (row: AdminOrder) => {
    try {
        const { value: rejectReason } = await ElMessageBox.prompt(
            `确认拒绝订单 ${row.orderNumber} 的退款申请吗？`,
            '拒绝退款',
            {
                confirmButtonText: '拒绝',
                cancelButtonText: '取消',
                inputPlaceholder: '请输入拒绝原因（必填）',
                inputType: 'textarea',
                inputValidator: (value: string) => {
                    if (!value || value.trim() === '') {
                        return '请输入拒绝原因'
                    }
                    return true
                }
            }
        )
        loading.value = true;
        await rejectRefund(row.id, rejectReason);
        ElMessage.success('退款申请已拒绝');
        fetchData();
    } catch (error) {
        if (error !== 'cancel') {
            console.error('拒绝退款失败:', error);
            const message = (error as any)?.response?.data?.error || (error as Error)?.message || '操作失败';
            ElMessage.error(`拒绝退款失败: ${message}`);
        }
    } finally {
        loading.value = false;
    }
}

// 发起争议
const handleRaiseDispute = async (row: AdminOrder) => {
    try {
        const { value: disputeReason } = await ElMessageBox.prompt(
            `确认要对订单 ${row.orderNumber} 发起争议吗？<br/><small>发起争议后，订单将进入争议处理流程，需要管理员进行仲裁</small>`,
            '发起争议',
            {
                confirmButtonText: '发起争议',
                cancelButtonText: '取消',
                inputPlaceholder: '请输入争议原因（必填）',
                inputType: 'textarea',
                inputValidator: (value: string) => {
                    if (!value || value.trim() === '') {
                        return '请输入争议原因'
                    }
                    return true
                },
                dangerouslyUseHTMLString: true
            }
        )
        loading.value = true;
        await raiseDispute(row.id, disputeReason);
        ElMessage.success('争议已发起，等待管理员仲裁');
        fetchData();
    } catch (error) {
        if (error !== 'cancel') {
            console.error('发起争议失败:', error);
            const message = (error as any)?.response?.data?.error || (error as Error)?.message || '操作失败';
            ElMessage.error(`发起争议失败: ${message}`);
        }
    } finally {
        loading.value = false;
    }
}

// 解决争议（仲裁）- 打开对话框
const handleResolveDispute = (row: AdminOrder) => {
    currentDisputeOrderId.value = row.id
    disputeForm.orderNumber = row.orderNumber
    disputeForm.refundReason = row.refundReason || ''
    disputeForm.disputeReason = row.disputeReason || ''
    disputeForm.resolution = 'APPROVED'
    disputeForm.note = ''
    disputeDialogVisible.value = true
}

// 提交仲裁结果
const submitDisputeResolution = async () => {
    if (!disputeForm.resolution) {
        ElMessage.warning('请选择仲裁结果')
        return
    }
    try {
        submitLoading.value = true
        await resolveDispute(currentDisputeOrderId.value!, disputeForm.resolution, disputeForm.note)

        if (disputeForm.resolution === 'APPROVED') {
            ElMessage.success('仲裁完成，已批准退款')
        } else {
            ElMessage.success('仲裁完成，已拒绝退款，订单恢复为已支付')
        }
        disputeDialogVisible.value = false
        fetchData()
    } catch (error) {
        console.error('解决争议失败:', error);
        const message = (error as any)?.response?.data?.error || (error as Error)?.message || '操作失败';
        ElMessage.error(`解决争议失败: ${message}`);
    } finally {
        submitLoading.value = false
    }
}

// 处理表格选择变化
const handleSelectionChange = (selection: AdminOrder[]) => {
    selectedRows.value = selection
}

// ========== 入住管理相关处理函数 ==========

// 打开设置准备入住对话框
const openPrepareCheckIn = async (row: AdminOrder) => {
    checkInCredentialForm.orderId = row.id
    checkInCredentialForm.orderNumber = row.orderNumber
    checkInCredentialForm.checkInMethod = 'MANUAL'
    checkInCredentialForm.checkInCode = ''
    checkInCredentialForm.doorPassword = ''
    checkInCredentialForm.lockboxCode = ''
    checkInCredentialForm.locationDescription = ''
    checkInCredentialForm.remark = ''
    prepareCheckInDialogVisible.value = true
}

// 提交设置准备入住
const submitPrepareCheckIn = async () => {
    try {
        submitLoading.value = true
        const credential = {
            checkInMethod: checkInCredentialForm.checkInMethod,
            doorPassword: checkInCredentialForm.doorPassword || undefined,
            lockboxCode: checkInCredentialForm.lockboxCode || undefined,
            locationDescription: checkInCredentialForm.locationDescription || undefined,
            remark: checkInCredentialForm.remark || undefined
        }
        await prepareCheckIn(checkInCredentialForm.orderId, credential)
        ElMessage.success('已设置准备入住，入住凭证已生成并通知客人')
        prepareCheckInDialogVisible.value = false
        fetchData()
    } catch (error) {
        const message = (error as any)?.response?.data?.error || (error as Error)?.message || '操作失败'
        ElMessage.error(`设置准备入住失败: ${message}`)
    } finally {
        submitLoading.value = false
    }
}

// 办理入住
const handlePerformCheckIn = async (row: AdminOrder) => {
    try {
        await ElMessageBox.confirm(
            `确认要为订单 ${row.orderNumber} 办理入住吗？`,
            '办理入住',
            { confirmButtonText: '确定', cancelButtonText: '取消', type: 'info' }
        )
        loading.value = true
        await performCheckIn(row.id)
        ElMessage.success('已办理入住')
        fetchData()
    } catch (error) {
        if (error !== 'cancel') {
            const message = (error as any)?.response?.data?.error || (error as Error)?.message || '操作失败'
            ElMessage.error(`办理入住失败: ${message}`)
        }
    } finally {
        loading.value = false
    }
}

// 取消准备入住
const handleCancelPrepare = async (row: AdminOrder) => {
    try {
        await ElMessageBox.confirm(
            `确认要取消订单 ${row.orderNumber} 的准备入住状态吗？`,
            '取消准备入住',
            { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
        )
        loading.value = true
        await cancelPrepareCheckIn(row.id)
        ElMessage.success('已取消准备入住状态')
        fetchData()
    } catch (error) {
        if (error !== 'cancel') {
            const message = (error as any)?.response?.data?.error || (error as Error)?.message || '操作失败'
            ElMessage.error(`取消准备入住失败: ${message}`)
        }
    } finally {
        loading.value = false
    }
}

// 打开退房对话框
const openCheckOut = (row: AdminOrder) => {
    checkOutForm.orderId = row.id
    checkOutForm.orderNumber = row.orderNumber
    checkOutForm.remark = ''
    checkOutDialogVisible.value = true
}

// 提交退房
const submitCheckOut = async () => {
    try {
        submitLoading.value = true
        await performCheckOut(checkOutForm.orderId, { remark: checkOutForm.remark } as any)
        ElMessage.success('已办理退房')
        checkOutDialogVisible.value = false
        fetchData()
    } catch (error) {
        const message = (error as any)?.response?.data?.error || (error as Error)?.message || '操作失败'
        ElMessage.error(`办理退房失败: ${message}`)
    } finally {
        submitLoading.value = false
    }
}

// 打开押金操作对话框
const openDeposit = async (row: AdminOrder) => {
    try {
        // 获取退房记录
        const record = await getCheckOutRecord(row.id) as any
        currentCheckOutRecord.value = record
        depositForm.orderId = row.id
        depositForm.orderNumber = row.orderNumber
        depositForm.action = 'COLLECT'
        depositForm.amount = record.depositAmount || 0
        depositForm.note = ''
        depositForm.currentDeposit = record.depositAmount || 0
        depositForm.depositStatus = record.depositStatus || 'PENDING'
        depositDialogVisible.value = true
    } catch (error) {
        const message = (error as any)?.response?.data?.error || (error as Error)?.message || '获取退房记录失败'
        ElMessage.error(message)
    }
}

// 提交押金操作
const submitDeposit = async () => {
    try {
        if (['COLLECT', 'RETAIN'].includes(depositForm.action) && !depositForm.amount) {
            ElMessage.warning('请输入金额')
            return
        }
        submitLoading.value = true
        await processDeposit(
            depositForm.orderId,
            depositForm.action,
            depositForm.amount || undefined,
            depositForm.note || undefined
        )
        ElMessage.success('押金操作成功')
        depositDialogVisible.value = false
        fetchData()
    } catch (error) {
        const message = (error as any)?.response?.data?.error || (error as Error)?.message || '操作失败'
        ElMessage.error(`押金操作失败: ${message}`)
    } finally {
        submitLoading.value = false
    }
}

// 确认结算
const handleConfirmSettlement = async (row: AdminOrder) => {
    try {
        await ElMessageBox.confirm(
            `确认要完成订单 ${row.orderNumber} 的结算吗？`,
            '确认结算',
            { confirmButtonText: '确定', cancelButtonText: '取消', type: 'info' }
        )
        loading.value = true
        await confirmSettlement(row.id)
        ElMessage.success('结算完成，订单已归档')
        fetchData()
    } catch (error) {
        if (error !== 'cancel') {
            const message = (error as any)?.response?.data?.error || (error as Error)?.message || '操作失败'
            ElMessage.error(`确认结算失败: ${message}`)
        }
    } finally {
        loading.value = false
    }
}

// ====== 更多操作菜单 ======
const hasMoreActions = (row: AdminOrder) => {
    const canRefund = row.paymentStatus === 'PAID' && !['COMPLETED', 'CANCELLED', 'REFUNDED'].includes(row.status);
    const canRaiseDispute = row.paymentStatus === 'REFUND_PENDING';
    const canResolveDispute = row.status === 'DISPUTE_PENDING';
    const canCancelPrepare = row.status === 'READY_FOR_CHECKIN';
    const canDeposit = row.status === 'CHECKED_OUT';
    const canComplete = ['CHECKED_IN', 'CHECKED_OUT'].includes(row.status);
    const canCancel = !['COMPLETED', 'CANCELLED', 'REFUNDED', 'CANCELLED_BY_USER', 'CANCELLED_BY_HOST', 'CANCELLED_SYSTEM'].includes(row.status) && row.paymentStatus !== 'REFUNDED';
    
    return canRefund || canRaiseDispute || canResolveDispute || canCancelPrepare || canDeposit || canComplete || canCancel;
}

const handleCommand = (command: string, row: AdminOrder) => {
    switch (command) {
        case 'refund':
            handleRefund(row);
            break;
        case 'raiseDispute':
            handleRaiseDispute(row);
            break;
        case 'resolveDispute':
            handleResolveDispute(row);
            break;
        case 'cancelPrepare':
            handleCancelPrepare(row);
            break;
        case 'deposit':
            openDeposit(row);
            break;
        case 'complete':
            handleComplete(row);
            break;
        case 'cancel':
            handleCancel(row);
            break;
    }
}

// 初始化
onMounted(() => {
    fetchData()
    loadExceptionStats()
})
</script>

<style scoped lang="scss">
.order-list {
    padding: 20px;

    // 异常订单统计卡片样式
    .stats-grid {
        display: flex;
        gap: 16px;
        margin-bottom: 20px;
        overflow-x: auto;
        flex-wrap: nowrap;
        padding-bottom: 8px; /* 留出阴影和滚动条空间 */

        /* 可选：美化或隐藏滚动条 */
        &::-webkit-scrollbar {
            height: 4px;
        }
        &::-webkit-scrollbar-thumb {
            background-color: #dcdfe6;
            border-radius: 4px;
        }

        .stat-card {
            flex: 1;
            min-width: 180px; /* 防止被无限挤压 */
            cursor: pointer;
            transition: all 0.3s;
            border-radius: 8px;

            &:hover {
                transform: translateY(-2px);
                box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
            }

            .stat-content {
                display: flex;
                align-items: center;
                gap: 12px;

                .stat-icon {
                    width: 48px;
                    height: 48px;
                    border-radius: 8px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 24px;

                    &.warning {
                        background-color: #fdf6ec;
                        color: #e6a23c;
                    }

                    &.danger {
                        background-color: #fef0f0;
                        color: #f56c6c;
                    }

                    &.info {
                        background-color: #f4f4f5;
                        color: #909399;
                    }

                    &.primary {
                        background-color: #ecf5ff;
                        color: var(--el-color-primary);
                    }
                }

                .stat-info {
                    .stat-value {
                        font-size: 24px;
                        font-weight: bold;
                        color: #303133;
                        line-height: 1.2;
                    }

                    .stat-label {
                        font-size: 12px;
                        color: #909399;
                        margin-top: 4px;
                    }
                }
            }
        }

        .exception-card {
            border-left: 3px solid;
        }

        .total-card {
            border-left: 3px solid var(--el-color-primary);
        }
    }

    .exception-filter-alert {
        margin-bottom: 16px;
    }

    .search-box {
        margin-bottom: 20px;
    }

    .pagination {
        margin-top: 20px;
        display: flex;
        justify-content: flex-end;
    }

    .batch-operation {
        margin-bottom: 15px;
    }

    .batch-buttons {
        margin-top: 8px;
        display: flex;
        gap: 10px;
    }

    /* 表格内容样式优化 */
    .homestay-info {
        .homestay-title {
            font-weight: 500;
            margin-bottom: 2px;
            color: #303133;
        }

        .host-info {
            font-size: 12px;
            color: #909399;
        }
    }

    .guest-info {
        .guest-name {
            font-weight: 500;
            margin-bottom: 2px;
            color: #303133;
        }

        .guest-count {
            font-size: 12px;
            color: #909399;
        }
    }

    .checkin-info {
        font-size: 13px;

        .date-range {
            margin-bottom: 1px;
            color: #606266;
        }

        .nights {
            font-size: 12px;
            color: var(--el-color-primary);
            font-weight: 500;
        }
    }

    .amount-info {
        .total-amount {
            font-weight: 600;
            font-size: 14px;
            color: #e6a23c;
            margin-bottom: 2px;
        }

        .unit-price {
            font-size: 12px;
            color: #909399;
        }
    }

    .status-info {
        .status-tag {
            display: block;
            margin-bottom: 4px;
            width: fit-content;
        }

        .payment-tag {
            display: block;
            width: fit-content;
        }
    }

    .time-info {
        text-align: center;

        .date {
            font-weight: 500;
            margin-bottom: 2px;
            color: #303133;
        }

        .time {
            font-size: 12px;
            color: #909399;
        }
    }

    .form-tip {
        font-size: 12px;
        color: #909399;
        margin-top: 4px;
    }

    .amount-text {
        color: #e6a23c;
        font-weight: 600;
        font-size: 16px;
    }

    .text-gray {
        color: #c0c4cc;
        font-style: italic;
    }

    /* 确保证下拉按钮文字颜色对于危险操作的展示 */
    :deep(.danger-item) {
        color: #f56c6c;
    }

    .action-buttons {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 8px;
        flex-wrap: wrap;
        
        .el-button {
            margin-left: 0;
            margin-bottom: 2px;
            margin-top: 2px;
        }

        .more-btn {
            display: inline-flex;
            align-items: center;
            margin-bottom: 2px;
            margin-top: 2px;
            
            .el-icon {
                margin-left: 2px;
            }
        }
    }

    /* 确保 tooltip 生效 */
    .el-table .el-table__cell .cell {
        white-space: nowrap;
    }
}
</style>