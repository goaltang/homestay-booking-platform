<template>
    <div class="homestay-manage">
        <div class="header">
            <h2>我的房源管理</h2>
            <div class="header-buttons">
                <el-button v-if="isDev" type="success" @click="handleQuickAddHomestay" :loading="quickAddLoading">
                    一键添加测试房源
                </el-button>
                <el-button type="primary" @click="handleCreateHomestay">添加新房源</el-button>
            </div>
        </div>

        <el-card shadow="never" class="filter-card">
            <el-form :inline="true" :model="filterForm" class="filter-form">
                <el-form-item label="房源状态">
                    <el-select v-model="filterForm.status" placeholder="全部状态" clearable>
                        <el-option label="草稿" value="DRAFT" />
                        <el-option label="待审核" value="PENDING" />
                        <el-option label="已上线" value="ACTIVE" />
                        <el-option label="已下架" value="INACTIVE" />
                        <el-option label="审核拒绝" value="REJECTED" />
                        <el-option label="已暂停" value="SUSPENDED" />
                    </el-select>
                </el-form-item>
                <el-form-item label="房源类型">
                    <el-select v-model="filterForm.type" placeholder="全部类型" clearable>
                        <el-option v-for="item in homestayTypeOptions" :key="item.value" :label="item.label"
                            :value="item.value" />
                    </el-select>
                </el-form-item>
                <el-form-item label="房源分组">
                    <el-select v-model="filterForm.groupId" placeholder="全部分组" clearable>
                        <el-option v-for="item in groupOptions" :key="item.id" :label="item.name"
                            :value="item.id" />
                    </el-select>
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" @click="handleFilter">筛选</el-button>
                    <el-button @click="resetFilter">重置</el-button>
                    <el-button type="success" @click="openGroupManageDialog">
                        <el-icon><Folder /></el-icon>
                        管理分组
                    </el-button>
                </el-form-item>
            </el-form>
        </el-card>

        <!-- 批量操作区域 -->
        <div class="batch-operation" v-if="selectedRows.length > 0">
            <el-alert title="批量操作" type="info" :closable="false" show-icon>
                <template #default>
                    已选择 <strong>{{ selectedRows.length }}</strong> 项
                    <div class="batch-buttons">
                        <el-button size="small" type="success" @click="handleBatchActivate">批量上线</el-button>
                        <el-button size="small" type="warning" @click="handleBatchDeactivate">批量下架</el-button>
                        <el-button size="small" type="info" @click="openBatchGroupDialog">批量分组</el-button>
                        <el-button size="small" type="danger" @click="handleBatchDelete">批量删除</el-button>
                    </div>
                </template>
            </el-alert>
        </div>

        <el-table :data="homestays" stripe style="width: 100%" v-loading="loading"
            @selection-change="handleSelectionChange">
            <el-table-column type="selection" width="55" />
            <el-table-column prop="coverImage" label="房源图片" width="180">
                <template #default="{ row }">
                    <el-image :src="row.coverImage || '/img/no-image.png'" style="width: 120px; height: 80px"
                        fit="cover" :preview-src-list="row.coverImage ? [row.coverImage] : []" />
                </template>
            </el-table-column>
            <el-table-column prop="title" label="房源名称" min-width="180" />
            <el-table-column prop="type" label="类型" width="100">
                <template #default="{ row }">
                    <el-tag>{{ getHomestayTypeLabel(row.type) }}</el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="price" label="价格" width="120">
                <template #default="{ row }">
                    ¥{{ row.price }}/晚
                </template>
            </el-table-column>
            <el-table-column prop="maxGuests" label="最大入住" width="100">
                <template #default="{ row }">
                    {{ row.maxGuests }}人
                </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }">
                    <el-tag :type="getStatusType(row.status)">
                        {{ getStatusText(row.status) }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="groupName" label="分组" width="120">
                <template #default="{ row }">
                    <el-tag v-if="row.groupName" :color="row.groupColor || '#409eff'" style="color: #fff; border: none;">
                        {{ row.groupName }}
                    </el-tag>
                    <span v-else class="no-group">未分组</span>
                </template>
            </el-table-column>
            <el-table-column width="260" label="操作" fixed="right">
                <template #default="{ row }">
                    <div class="action-buttons">
                        <!-- 草稿状态：可以编辑、提交审核 -->
                        <template v-if="row.status === 'DRAFT'">
                            <el-button size="small" type="primary" @click="handleEdit(row.id)">
                                <el-icon>
                                    <Edit />
                                </el-icon>
                                编辑
                            </el-button>
                            <!-- 信息完整时显示提交审核按钮 -->
                            <el-button v-if="isHomestayComplete(row)" size="small" type="success"
                                @click="handleSubmitForReview(row.id)">
                                <el-icon>
                                    <Upload />
                                </el-icon>
                                提交审核
                            </el-button>
                            <!-- 信息不完整时显示完善信息按钮 -->
                            <el-tooltip v-else placement="top">
                                <template #content>
                                    <div style="max-width: 250px;">
                                        <strong>信息不完整，无法提交审核</strong><br />
                                        缺失以下必要信息：<br />
                                        <span v-for="field in getMissingFields(row)" :key="field"
                                            style="color: #F56C6C;">
                                            • {{ field }}<br />
                                        </span>
                                        <br />请先完善这些信息后再提交审核
                                    </div>
                                </template>
                                <el-button size="small" type="warning" @click="handleCompleteInfo(row.id)">
                                    <el-icon>
                                        <Warning />
                                    </el-icon>
                                    完善信息
                                </el-button>
                            </el-tooltip>
                        </template>

                        <!-- 待审核状态：可以撤回审核申请 -->
                        <template v-else-if="row.status === 'PENDING'">
                            <el-tooltip content="撤回审核申请，房源将回到草稿状态，您可以修改后重新提交" placement="top">
                                <el-button size="small" type="warning" @click="handleWithdrawReview(row.id)">
                                    <el-icon>
                                        <RefreshRight />
                                    </el-icon>
                                    撤回申请
                                </el-button>
                            </el-tooltip>
                            <el-tooltip placement="top">
                                <template #content>
                                    <div style="max-width: 200px;">
                                        <strong>审核状态说明：</strong><br />
                                        • 房源正在管理员审核中<br />
                                        • 审核期间无法修改房源信息<br />
                                        • 您可以撤回申请后重新编辑<br />
                                        • 通常审核需要1-3个工作日
                                    </div>
                                </template>
                                <el-button size="small" type="info" disabled>
                                    <el-icon>
                                        <Edit />
                                    </el-icon>
                                    审核中
                                </el-button>
                            </el-tooltip>
                        </template>

                        <!-- 已上线状态：可以下架 -->
                        <template v-else-if="row.status === 'ACTIVE'">
                            <el-button size="small" type="warning" @click="handleDeactivate(row.id)">
                                下架
                            </el-button>
                            <el-button size="small" type="primary" @click="handleEdit(row.id)">
                                <el-icon>
                                    <Edit />
                                </el-icon>
                                编辑
                            </el-button>
                        </template>

                        <!-- 已下架状态：可以重新上线 -->
                        <template v-else-if="row.status === 'INACTIVE'">
                            <el-button size="small" type="success" @click="handleActivate(row.id)">
                                上线
                            </el-button>
                            <el-button size="small" type="primary" @click="handleEdit(row.id)">
                                <el-icon>
                                    <Edit />
                                </el-icon>
                                编辑
                            </el-button>
                        </template>

                        <!-- 已拒绝状态：可以编辑修改后重新提交 -->
                        <template v-else-if="row.status === 'REJECTED'">
                            <el-button size="small" type="primary" @click="handleEdit(row.id)">
                                <el-icon>
                                    <Edit />
                                </el-icon>
                                修改
                            </el-button>
                            <el-button size="small" type="success" @click="handleSubmitForReview(row.id)">
                                <el-icon>
                                    <Upload />
                                </el-icon>
                                重新提交
                            </el-button>
                        </template>

                        <!-- 已暂停状态：可以申请重新上架 -->
                        <template v-else-if="row.status === 'SUSPENDED'">
                            <el-button size="small" type="warning" @click="handleRequestReactivation(row.id)">
                                <el-icon>
                                    <RefreshRight />
                                </el-icon>
                                申请恢复
                            </el-button>
                            <el-button size="small" type="info" @click="handleEdit(row.id)" disabled>
                                <el-icon>
                                    <Edit />
                                </el-icon>
                                已暂停
                            </el-button>
                        </template>

                        <!-- 更多操作下拉菜单 -->
                        <el-dropdown trigger="click" style="margin-left: 8px;">
                            <el-button size="small">
                                更多<el-icon class="el-icon--right">
                                    <ArrowDown />
                                </el-icon>
                            </el-button>
                            <template #dropdown>
                                <el-dropdown-menu>
                                    <el-dropdown-item @click="handleViewOrders(row.id)"
                                        :disabled="row.status === 'DRAFT' || row.status === 'PENDING'">
                                        <el-icon>
                                            <Document />
                                        </el-icon> 查看订单
                                    </el-dropdown-item>
                                    <el-dropdown-item @click="handleViewAuditHistory(row.id)"
                                        v-if="row.status !== 'DRAFT'">
                                        <el-icon>
                                            <Document />
                                        </el-icon> 审核记录
                                    </el-dropdown-item>
                                    <el-dropdown-item @click="handleWithdrawReview(row.id)"
                                        v-if="row.status === 'PENDING'">
                                        <el-icon>
                                            <RefreshRight />
                                        </el-icon> 撤回审核申请
                                    </el-dropdown-item>
                                    <el-dropdown-item @click="handleDelete(row.id)" divided
                                        :disabled="row.status === 'ACTIVE' || row.status === 'PENDING'">
                                        <el-icon>
                                            <Delete />
                                        </el-icon> 删除
                                    </el-dropdown-item>
                                </el-dropdown-menu>
                            </template>
                        </el-dropdown>
                    </div>
                </template>
            </el-table-column>
            <template #empty>
                <div class="empty-state">
                    <el-empty :description="emptyText">
                        <el-button type="primary" @click="handleCreateHomestay">添加新房源</el-button>
                    </el-empty>
                </div>
            </template>
        </el-table>

        <div class="pagination" v-if="total > 0">
            <el-pagination v-model:currentPage="currentPage" v-model:page-size="pageSize" :page-sizes="[10, 20, 30, 50]"
                layout="total, sizes, prev, pager, next, jumper" :total="total" @size-change="handleSizeChange"
                @current-change="handleCurrentChange" />
        </div>

        <!-- 审核记录对话框 -->
        <el-dialog v-model="auditHistoryDialogVisible" title="房源审核记录" width="70%" top="5vh"
            :close-on-click-modal="false">
            <div v-if="currentAuditHomestayId" class="audit-history-content">
                <!-- 房源基本信息 -->
                <el-card class="homestay-info-card" shadow="never" style="margin-bottom: 20px;">
                    <template #header>
                        <div class="card-header">
                            <el-icon>
                                <House />
                            </el-icon>
                            <span>房源信息</span>
                        </div>
                    </template>
                    <el-descriptions :column="3" border v-if="currentAuditHomestay">
                        <el-descriptions-item label="房源ID">{{ currentAuditHomestayId }}</el-descriptions-item>
                        <el-descriptions-item label="房源名称">
                            <strong>{{ currentAuditHomestay.title }}</strong>
                        </el-descriptions-item>
                        <el-descriptions-item label="当前状态">
                            <el-tag :type="getStatusType(currentAuditHomestay.status)">
                                {{ getStatusText(currentAuditHomestay.status) }}
                            </el-tag>
                        </el-descriptions-item>
                        <el-descriptions-item label="房源类型">{{ getHomestayTypeLabel(currentAuditHomestay.type)
                        }}</el-descriptions-item>
                        <el-descriptions-item label="价格">¥{{ currentAuditHomestay.price }}/晚</el-descriptions-item>
                        <el-descriptions-item label="最大入住">{{ currentAuditHomestay.maxGuests }}人</el-descriptions-item>
                    </el-descriptions>
                </el-card>

                <!-- 审核记录 -->
                <el-card shadow="never">
                    <template #header>
                        <div class="card-header">
                            <el-icon>
                                <Document />
                            </el-icon>
                            <span>审核历史记录</span>
                            <div style="margin-left: auto; display: flex; gap: 8px;">
                                <el-button type="text" size="small" @click="refreshCurrentAuditHistory">
                                    <el-icon>
                                        <Refresh />
                                    </el-icon>
                                    刷新
                                </el-button>
                                <el-button type="text" size="small" @click="showDataQualityInfo = !showDataQualityInfo">
                                    <el-icon>
                                        <InfoFilled />
                                    </el-icon>
                                    数据说明
                                </el-button>
                            </div>
                        </div>
                    </template>

                    <!-- 数据质量说明 -->
                    <el-alert v-if="showDataQualityInfo" title="数据说明" type="info" :closable="false"
                        style="margin-bottom: 16px;">
                        <template #default>
                            <p>系统已自动过滤以下类型的记录：</p>
                            <ul style="margin: 8px 0; padding-left: 20px;">
                                <li>✅ 系统数据迁移记录</li>
                                <li>✅ 测试账户的操作记录</li>
                                <li>✅ 无效的历史数据</li>
                            </ul>
                            <p style="color: #909399; font-size: 12px;">只显示真实有效的审核操作记录。</p>
                        </template>
                    </el-alert>

                    <!-- 审核记录列表 -->
                    <div v-loading="loadingAuditHistory">
                        <div v-if="auditRecords.length > 0" class="audit-timeline">
                            <el-timeline>
                                <el-timeline-item v-for="record in auditRecords" :key="record.id"
                                    :type="getTimelineType(record.actionType)"
                                    :timestamp="formatDateTime(record.createdAt)" placement="top">
                                    <div class="timeline-item">
                                        <div class="timeline-header">
                                            <div class="action-info">
                                                <strong>{{ getActionText(record.actionType) }}</strong>
                                                <el-tag v-if="record.actionType === 'APPROVE'" type="success"
                                                    size="small">
                                                    已通过
                                                </el-tag>
                                                <el-tag v-else-if="record.actionType === 'REJECT'" type="danger"
                                                    size="small">
                                                    已拒绝
                                                </el-tag>
                                                <el-tag v-else-if="record.actionType === 'SUBMIT'" type="primary"
                                                    size="small">
                                                    已提交
                                                </el-tag>
                                                <el-tag v-else-if="record.actionType === 'RESUBMIT'" type="primary"
                                                    size="small">
                                                    重新提交
                                                </el-tag>
                                                <el-tag v-else-if="record.actionType === 'WITHDRAW'" type="warning"
                                                    size="small">
                                                    已撤回
                                                </el-tag>
                                                <el-tag v-else type="info" size="small">
                                                    {{ record.actionType }}
                                                </el-tag>
                                            </div>
                                        </div>
                                        <div class="timeline-content">
                                            <div class="reviewer-info" v-if="record.reviewerName">
                                                <el-icon>
                                                    <User />
                                                </el-icon>
                                                <span><strong>操作人：</strong>{{ record.reviewerName }}</span>
                                                <span v-if="record.reviewerId" class="reviewer-id">
                                                    (ID: {{ record.reviewerId }})
                                                </span>
                                            </div>
                                            <div v-if="record.reviewReason" class="reason-info">
                                                <el-icon>
                                                    <InfoFilled />
                                                </el-icon>
                                                <span><strong>原因：</strong>{{ record.reviewReason }}</span>
                                            </div>
                                            <div v-if="record.reviewNotes" class="notes-info">
                                                <el-icon>
                                                    <Document />
                                                </el-icon>
                                                <span><strong>备注：</strong>{{ record.reviewNotes }}</span>
                                            </div>
                                            <div v-if="record.oldStatus && record.newStatus" class="status-change">
                                                <el-icon>
                                                    <TrendCharts />
                                                </el-icon>
                                                <span><strong>状态变化：</strong>{{ formatStatus(record.oldStatus) }} → {{
                                                    formatStatus(record.newStatus) }}</span>
                                            </div>
                                        </div>
                                    </div>
                                </el-timeline-item>
                            </el-timeline>
                        </div>
                        <div v-else class="no-audit-history">
                            <div class="empty-state">
                                <el-icon size="48" color="#c0c4cc">
                                    <Document />
                                </el-icon>
                                <p style="margin: 12px 0 4px;">暂无审核记录</p>
                                <p style="color: #909399; font-size: 12px;">
                                    该房源尚未进行过审核，或审核记录已被系统清理
                                </p>
                            </div>
                        </div>
                    </div>
                </el-card>
            </div>
        </el-dialog>

        <GroupManageDialog ref="groupManageDialogRef" v-model="groupManageDialogVisible" :selected-rows="selectedRows" @groups-changed="handleGroupsChanged" />

    </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import {
    getOwnerHomestays,
    activateHomestay,
    deactivateHomestay,
    deleteHomestay,
    batchActivateHomestays,
    batchDeactivateHomestays,
    batchDeleteHomestays,
    createHomestay,
    getHomestayTypesForFilter,
    submitHomestayForReview,
    withdrawHomestayReview,
    requestReactivation,
    getHomestayAuditHistory,
    getHomestayGroups
} from '@/api/homestay';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { HomestayStatus, HomestayGroup } from '@/types';
import { ArrowDown, Document, Delete, Upload, Edit, RefreshRight, House, User, InfoFilled, TrendCharts, Refresh, Warning, Folder } from '@element-plus/icons-vue';
import GroupManageDialog from '@/components/host/GroupManageDialog.vue';

interface Homestay {
    id: number;
    title: string;
    coverImage: string;
    type: 'ENTIRE' | 'PRIVATE';
    price: number;
    maxGuests: number;
    status: HomestayStatus;
    description?: string;
    addressDetail?: string;
    groupId?: number;
    groupName?: string;
    groupCode?: string;
    groupColor?: string;
}

const router = useRouter();
const loading = ref(false);
const homestays = ref<Homestay[]>([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(10);
const emptyText = ref('加载中...');
const selectedRows = ref<Homestay[]>([]);

const filterForm = ref({
    status: '',
    type: '',
    groupId: undefined as number | undefined,
});

const homestayTypeOptions = ref<{ label: string; value: string }[]>([]);

// 分组相关
const groups = ref<HomestayGroup[]>([]);
const groupsLoading = ref(false);
const groupOptions = computed(() => groups.value.filter(g => g.enabled));

const groupManageDialogVisible = ref(false);
const groupManageDialogRef = ref<InstanceType<typeof GroupManageDialog>>();

const fetchHomestayTypes = async () => {
    try {
        const response = await getHomestayTypesForFilter();
        if (response && response.data && response.data.success && Array.isArray(response.data.data)) {
            homestayTypeOptions.value = response.data.data;
        } else {
            console.error('获取房源类型数据格式错误:', response);
            homestayTypeOptions.value = [];
            ElMessage.warning('加载房源类型选项失败，请检查接口返回');
        }
    } catch (error) {
        console.error('获取房源类型接口请求失败:', error);
        homestayTypeOptions.value = [];
    }
};

// 获取房源列表
const fetchHomestays = async () => {
    try {
        loading.value = true;
        emptyText.value = '正在加载...';

        const response = await getOwnerHomestays({
            page: currentPage.value - 1,
            size: pageSize.value,
            status: filterForm.value.status,
            type: filterForm.value.type,
            groupId: filterForm.value.groupId
        });

        if (response.data && Array.isArray(response.data.data)) {
            homestays.value = response.data.data;
            total.value = response.data.total || 0;

            if (homestays.value.length === 0) {
                if (filterForm.value.status || filterForm.value.type) {
                    emptyText.value = '没有符合筛选条件的房源';
                } else {
                    emptyText.value = '您还没有添加任何房源';
                }
            }
        } else {
            homestays.value = [];
            total.value = 0;
            emptyText.value = '数据格式错误，请联系管理员';
            console.error('返回的数据格式不正确', response);
        }
    } catch (error: any) {
        console.error('获取房源列表失败', error);
        homestays.value = [];
        total.value = 0;

        if (error.response && error.response.status === 403) {
            emptyText.value = '没有访问权限，请确认您已经注册为房东';
        } else {
            emptyText.value = '加载失败，请刷新重试';
        }
    } finally {
        loading.value = false;
    }
};

// 页码变化
const handleCurrentChange = (page: number) => {
    currentPage.value = page;
    fetchHomestays();
};

// 每页数量变化
const handleSizeChange = (size: number) => {
    pageSize.value = size;
    fetchHomestays();
};

// 筛选
const handleFilter = () => {
    currentPage.value = 1;
    fetchHomestays();
};

// 重置筛选
const resetFilter = () => {
    filterForm.value = {
        status: '',
        type: '',
        groupId: undefined,
    };
    handleFilter();
};

// 创建新房源
const handleCreateHomestay = () => {
    router.push('/host/homestay/create');
};

// 编辑房源
const handleEdit = (id: number) => {
    try {
        console.log(`准备编辑房源，ID: ${id}`);
        router.push(`/host/homestay/edit/${id}`);
    } catch (error) {
        console.error('编辑房源导航失败', error);
    }
};

// 上线房源
const handleActivate = async (id: number) => {
    try {
        await activateHomestay(id);
        ElMessage.success('房源已上线');
        fetchHomestays();
    } catch (error) {
        console.error('上线房源失败', error);
    }
};

// 下架房源
const handleDeactivate = async (id: number) => {
    try {
        await deactivateHomestay(id);
        ElMessage.success('房源已下架');
        fetchHomestays();
    } catch (error) {
        console.error('下架房源失败', error);
    }
};

// 查看房源订单
const handleViewOrders = (id: number) => {
    router.push(`/host/orders?homestayId=${id}`);
};

// 提交房源审核
const handleSubmitForReview = async (id: number) => {
    try {
        await ElMessageBox.confirm(
            '房源信息检查完整！\n\n确认要提交此房源进行审核吗？提交后将无法修改，直到审核完成。',
            '提交审核确认',
            {
                confirmButtonText: '确认提交',
                cancelButtonText: '取消',
                type: 'info'
            }
        );

        loading.value = true;
        await submitHomestayForReview(id);

        ElMessage.success('房源已提交审核，请耐心等待审核结果');
        fetchHomestays();
    } catch (error: any) {
        if (error === 'cancel' || error.toString().includes('cancel')) {
            return;
        }
        console.error('提交审核失败', error);

    } finally {
        loading.value = false;
    }
};

// 撤回审核申请
const handleWithdrawReview = async (id: number) => {
    try {
        await ElMessageBox.confirm(
            '确认要撤回此房源的审核申请吗？撤回后房源状态将变为草稿，您可以修改后重新提交。',
            '撤回审核申请',
            {
                confirmButtonText: '确认撤回',
                cancelButtonText: '取消',
                type: 'warning'
            }
        );

        loading.value = true;
        await withdrawHomestayReview(id);

        ElMessage.success('审核申请已撤回，房源状态已变为草稿');
        fetchHomestays();
    } catch (error: any) {
        if (error === 'cancel' || error.toString().includes('cancel')) {
            return;
        }
        console.error('撤回审核申请失败', error);

    } finally {
        loading.value = false;
    }
};

// 申请重新上架
const handleRequestReactivation = async (id: number) => {
    try {
        const { value: reason } = await ElMessageBox.prompt(
            '请说明申请重新上架的原因：',
            '申请重新上架',
            {
                confirmButtonText: '提交申请',
                cancelButtonText: '取消',
                inputType: 'textarea',
                inputPlaceholder: '请详细说明您已经解决的问题或申请重新上架的理由...',
                inputValidator: (value) => {
                    if (!value || value.trim().length < 10) {
                        return '申请理由不能少于10个字符';
                    }
                    return true;
                }
            }
        );

        loading.value = true;
        await requestReactivation(id, reason);

        ElMessage.success('重新上架申请已提交，管理员将尽快处理');
        fetchHomestays();
    } catch (error: any) {
        if (error === 'cancel' || error.toString().includes('cancel')) {
            return;
        }
        console.error('申请重新上架失败', error);

    } finally {
        loading.value = false;
    }
};

// 查看审核历史
const handleViewAuditHistory = async (id: number) => {
    try {
        // 找到对应的房源信息
        const homestay = homestays.value.find(h => h.id === id);
        if (!homestay) {
            ElMessage.error('房源信息不存在');
            return;
        }

        // 设置当前房源信息
        currentAuditHomestayId.value = id;
        currentAuditHomestay.value = homestay;
        showDataQualityInfo.value = false;

        // 显示对话框
        auditHistoryDialogVisible.value = true;

        // 加载审核历史
        await refreshCurrentAuditHistory();
    } catch (error: any) {
        console.error('查看审核历史失败', error);
    }
};

// 删除房源
const handleDelete = async (id: number) => {
    try {
        // 添加确认对话框
        await ElMessageBox.confirm(
            '删除房源后将无法恢复，确定要删除吗？',
            '删除提示',
            {
                confirmButtonText: '确定删除',
                cancelButtonText: '取消',
                type: 'warning',
                confirmButtonClass: 'el-button--danger'
            }
        );

        // 显示加载状态
        loading.value = true;

        // 用户确认后执行删除
        const res = await deleteHomestay(id);

        // 检查返回结果
        if (res && (res.data?.success || res.status === 200 || res.status === 204)) {
            ElMessage.success('房源已成功删除');
            fetchHomestays();
        } else {
            throw new Error('删除请求未返回成功状态');
        }
    } catch (error: any) {
        // 用户取消不显示错误
        if (error === 'cancel' || error.toString().includes('cancel')) {
            return;
        }

        console.error('删除房源失败', error);

        // 可选：询问用户是否重试
        try {
            const shouldRetry = await ElMessageBox.confirm(
                '删除操作失败，是否重试？',
                '重试确认',
                {
                    confirmButtonText: '重试',
                    cancelButtonText: '取消',
                    type: 'info'
                }
            );

            if (shouldRetry) {
                await handleDelete(id);
            }
        } catch {
            // 用户取消重试，不做处理
        }
    } finally {
        loading.value = false;
    }
};

// 房源状态对应的标签类型
const getStatusType = (status: HomestayStatus): string => {
    const types: Record<HomestayStatus, string> = {
        DRAFT: 'info',
        PENDING: 'warning',
        ACTIVE: 'success',
        INACTIVE: 'info',
        REJECTED: 'danger',
        SUSPENDED: 'danger'
    };
    return types[status] || 'info';
};

// 房源状态对应的文本
const getStatusText = (status: HomestayStatus): string => {
    const texts: Record<HomestayStatus, string> = {
        DRAFT: '草稿',
        PENDING: '待审核',
        ACTIVE: '已上线',
        INACTIVE: '已下架',
        REJECTED: '已拒绝',
        SUSPENDED: '已暂停'
    };
    return texts[status] || '未知状态';
};

// 处理表格选择变化
const handleSelectionChange = (selection: Homestay[]) => {
    selectedRows.value = selection;
};

// 批量上线房源
const handleBatchActivate = async () => {
    if (selectedRows.value.length === 0) {
        ElMessage.warning('请至少选择一项');
        return;
    }

    try {
        await ElMessageBox.confirm('确认要批量上线选中的房源吗？', '批量操作', {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
        });

        const ids = selectedRows.value.map(item => item.id);
        await batchActivateHomestays(ids);
        ElMessage.success('批量上线成功');
        fetchHomestays();
    } catch (error: any) {
        console.error('批量上线失败:', error);
    }
};

// 批量下架房源
const handleBatchDeactivate = async () => {
    if (selectedRows.value.length === 0) {
        ElMessage.warning('请至少选择一项');
        return;
    }

    try {
        await ElMessageBox.confirm('确认要批量下架选中的房源吗？', '批量操作', {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
        });

        const ids = selectedRows.value.map(item => item.id);
        await batchDeactivateHomestays(ids);
        ElMessage.success('批量下架成功');
        fetchHomestays();
    } catch (error: any) {
        console.error('批量下架失败:', error);
    }
};

// 批量删除房源
const handleBatchDelete = async () => {
    if (selectedRows.value.length === 0) {
        ElMessage.warning('请至少选择一项');
        return;
    }

    try {
        await ElMessageBox.confirm(`确认要删除选中的 ${selectedRows.value.length} 个房源吗？此操作不可恢复！`, '批量操作', {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
        });

        const ids = selectedRows.value.map(item => item.id);
        await batchDeleteHomestays(ids);
        ElMessage.success('批量删除成功');
        fetchHomestays();
    } catch (error: any) {
        console.error('批量删除失败:', error);
    }
};

// 检查是否为开发环境
const isDev = computed(() => {
    return import.meta.env.MODE === 'development' || window.location.hostname === 'localhost';
});

// 一键添加测试房源
const quickAddLoading = ref(false);
const handleQuickAddHomestay = async () => {
    if (!isDev.value) {
        ElMessage.warning('此功能仅在开发环境可用');
        return;
    }

    try {
        quickAddLoading.value = true;

        // 随机数，用于避免标题重复
        const randomNum = Math.floor(Math.random() * 10000);

        // 随机房源标题
        const titles = [
            '湖景豪华套房',
            '城市中心温馨公寓',
            '西湖边的家',
            '市中心江景公寓',
            '古城墙附近的民宿',
            '梦幻花园独栋别墅',
            '山景舒适度假屋',
            '现代loft公寓',
            '运河边的小屋',
            '临湖浪漫套房'
        ];

        // 随机设施列表
        const allAmenities = [
            'WIFI', 'AIR_CONDITIONING', 'KITCHEN', 'WASHER', 'PARKING', 'POOL',
            'TV', 'WORKSPACE', 'DRYER', 'HEATING', 'BREAKFAST',
            'GYM', 'HOT_TUB', 'BBQ', 'FIRST_AID', 'SMOKE_ALARM'
        ];

        // 随机选择3-8个设施
        const selectedAmenities: string[] = [];
        const amenityCount = Math.floor(Math.random() * 6) + 3; // 3-8个设施
        while (selectedAmenities.length < amenityCount) {
            const randomIndex = Math.floor(Math.random() * allAmenities.length);
            const amenity = allAmenities[randomIndex];
            if (!selectedAmenities.includes(amenity)) {
                selectedAmenities.push(amenity);
            }
        }

        // 随机地址
        const streets = ['翠苑路', '文三路', '莫干山路', '西湖大道', '教工路', '余杭塘路', '湖墅南路', '凤起路'];
        const randomStreet = streets[Math.floor(Math.random() * streets.length)];
        const randomNum1 = Math.floor(Math.random() * 500) + 1;
        const randomNum2 = Math.floor(Math.random() * 20) + 1;
        const randomAddress = `${randomStreet}${randomNum1}号${randomNum2}单元`;

        // 构建测试房源数据
        const testHomestay = {
            title: Math.random() > 0.5 ? `${titles[Math.floor(Math.random() * titles.length)]} #${randomNum}` : `测试房源 #${randomNum}`,
            type: Math.random() > 0.5 ? 'ENTIRE' : 'PRIVATE',
            price: String(Math.floor(Math.random() * 500) + 100), // 100-600元随机价格
            status: 'INACTIVE', // 默认不上线，避免干扰
            maxGuests: Math.floor(Math.random() * 6) + 1, // 1-6人
            minNights: Math.floor(Math.random() * 3) + 1, // 1-3晚
            province: 'zhejiang',
            city: 'hangzhou',
            district: 'xihu',
            address: randomAddress,
            amenities: [], // 设置为空数组，稍后通过API添加
            description: `这是一个自动生成的测试房源(#${randomNum})。位于美丽的西湖附近，交通便利，周边设施齐全。\n\n房源亮点：\n- 位置优越，靠近景点\n- 设施齐全，温馨舒适\n- 适合商务和休闲旅行\n\n温馨提示：此房源仅用于开发和测试目的。`,
            // 使用空字符串或相对路径代替外部URL
            coverImage: '',
            images: [],
            featured: false
        };

        // 调用创建API
        const response = await createHomestay(testHomestay);

        if (response && response.data) {
            const newHomestayId = response.data.id;

            // 添加设施
            if (selectedAmenities.length > 0) {
                try {
                    // 引入设施API
                    import('@/api/amenities').then(async ({ addAmenityToHomestayApi }) => {
                        let addedCount = 0;
                        for (const amenity of selectedAmenities) {
                            try {
                                const result = await addAmenityToHomestayApi(newHomestayId, amenity);
                                if (result.data && result.data.success) {
                                    addedCount++;
                                    console.log(`成功添加设施: ${amenity}`);
                                }
                            } catch (error) {
                                console.error(`添加设施${amenity}失败:`, error);
                            }
                        }
                        console.log(`设施添加成功，共添加${addedCount}个设施`);
                    });
                } catch (amenityError) {
                    console.error('设施添加失败:', amenityError);
                }
            }

            ElMessage.success(`测试房源"${testHomestay.title}"创建成功，请添加图片`);

            // 询问用户是否立即编辑添加图片
            ElMessageBox.confirm(
                '房源创建成功！是否立即前往编辑页面添加图片？',
                '添加图片',
                {
                    confirmButtonText: '立即添加图片',
                    cancelButtonText: '稍后再说',
                    type: 'info'
                }
            ).then(() => {
                // 跳转到编辑页面
                router.push(`/host/homestay/edit/${newHomestayId}`);
            }).catch(() => {
                // 用户选择稍后添加图片，只刷新列表
                fetchHomestays();
            });
        } else {
            throw new Error('创建失败，服务器未返回期望的响应');
        }
    } catch (error: any) {
        console.error('创建测试房源失败', error);

    } finally {
        quickAddLoading.value = false;
    }
};

// --- 新增：根据类型代码获取显示标签 ---
const getHomestayTypeLabel = (typeCode: string): string => {
    const foundType = homestayTypeOptions.value.find(option => option.value === typeCode);
    return foundType ? foundType.label : typeCode; // 找不到时返回原始代码
};

// 审核记录对话框
const auditHistoryDialogVisible = ref(false);
const currentAuditHomestayId = ref<number | null>(null);
const currentAuditHomestay = ref<Homestay | null>(null);
const auditRecords = ref<any[]>([]);
const loadingAuditHistory = ref(false);
const showDataQualityInfo = ref(false);

const getTimelineType = (actionType: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' => {
    const types: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
        APPROVE: 'success',
        REJECT: 'danger',
        SUBMIT: 'primary',
        RESUBMIT: 'primary',
        WITHDRAW: 'warning',
        REVIEW: 'warning'
    };
    return types[actionType] || 'info';
};

const getActionText = (actionType: string): string => {
    const texts: Record<string, string> = {
        APPROVE: '通过审核',
        REJECT: '拒绝审核',
        SUBMIT: '提交审核',
        RESUBMIT: '重新提交',
        WITHDRAW: '撤回审核',
        REVIEW: '开始审核'
    };
    return texts[actionType] || actionType;
};

const formatDateTime = (timestamp: string): string => {
    if (!timestamp) return '未知时间';
    try {
        const date = new Date(timestamp);
        return date.toLocaleString('zh-CN', {
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });
    } catch (error) {
        return '时间格式错误';
    }
};

const formatStatus = (status: HomestayStatus): string => {
    const texts: Record<HomestayStatus, string> = {
        DRAFT: '草稿',
        PENDING: '待审核',
        ACTIVE: '已上线',
        INACTIVE: '已下架',
        REJECTED: '已拒绝',
        SUSPENDED: '已暂停'
    };
    return texts[status] || '未知状态';
};

const refreshCurrentAuditHistory = async () => {
    if (currentAuditHomestayId.value) {
        try {
            loadingAuditHistory.value = true;
            const response = await getHomestayAuditHistory(currentAuditHomestayId.value, 0, 10);

            if (response.data && response.data.content) {
                auditRecords.value = response.data.content;
                console.log('审核历史已加载，条目数:', response.data.content.length);
            } else {
                auditRecords.value = [];
            }
        } catch (error: any) {
            console.error('刷新审核历史失败', error);

            auditRecords.value = [];
        } finally {
            loadingAuditHistory.value = false;
        }
    }
};

// 检查房源信息是否完整
const isHomestayComplete = (homestay: Homestay): boolean => {
    return !!(
        homestay.title && homestay.title.trim() !== '' &&
        homestay.coverImage && homestay.coverImage.trim() !== '' &&
        homestay.price && homestay.price > 0 &&
        homestay.maxGuests && homestay.maxGuests > 0 &&
        homestay.description && homestay.description.trim() !== '' &&
        homestay.addressDetail && homestay.addressDetail.trim() !== ''
    );
};

// 获取房源缺失字段
const getMissingFields = (homestay: Homestay): string[] => {
    const missingFields: string[] = [];
    if (!homestay.title || homestay.title.trim() === '') {
        missingFields.push('房源标题');
    }
    if (!homestay.coverImage || homestay.coverImage.trim() === '') {
        missingFields.push('封面图片');
    }
    if (!homestay.price || homestay.price <= 0) {
        missingFields.push('房源价格');
    }
    if (!homestay.maxGuests || homestay.maxGuests <= 0) {
        missingFields.push('最大入住人数');
    }
    if (!homestay.description || homestay.description.trim() === '') {
        missingFields.push('房源描述');
    }
    if (!homestay.addressDetail || homestay.addressDetail.trim() === '') {
        missingFields.push('详细地址');
    }
    return missingFields;
};

// 处理完善信息
const handleCompleteInfo = (id: number) => {
    handleEdit(id);
};

// 分组相关方法
const fetchGroups = async () => {
    try {
        groupsLoading.value = true;
        groups.value = await getHomestayGroups();
    } catch (error) {
        console.error('获取分组列表失败:', error);
    } finally {
        groupsLoading.value = false;
    }
};

const openGroupManageDialog = () => {
    groupManageDialogVisible.value = true;
};

// 分组增删改后，刷新筛选分组列表与房源列表
const handleGroupsChanged = async () => {
    await fetchGroups();
    await fetchHomestays();
};

// 打开批量分组（透传给 GroupManageDialog）
const openBatchGroupDialog = () => {
    groupManageDialogRef.value?.openBatchGroupDialog();
};

onMounted(() => {
    fetchHomestays();
    fetchHomestayTypes();
    fetchGroups();
});
</script>

<style scoped>
.homestay-manage {
    padding: 20px;
}

.header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
}

.header-buttons {
    display: flex;
    gap: 10px;
}

.filter-card {
    margin-bottom: 20px;
}

.filter-form {
    display: flex;
    flex-wrap: wrap;
}

.empty-state {
    padding: 40px 0;
    text-align: center;
}

.pagination {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
}

/* 添加按钮布局样式 */
.action-buttons {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
}

.batch-operation {
    margin-bottom: 20px;
}

.batch-buttons {
    display: inline-block;
    margin-left: 15px;
}

.no-group {
    color: #909399;
    font-size: 12px;
}

.group-manage-content {
    .group-header {
        margin-bottom: 16px;
        display: flex;
        justify-content: flex-end;
    }
}

/* 审核记录对话框样式 */
.audit-history-content {
    .homestay-info-card {
        .card-header {
            display: flex;
            align-items: center;
            gap: 8px;
            font-weight: 600;

            .el-icon {
                color: #409eff;
            }
        }
    }

    .audit-timeline {
        .timeline-item {
            .timeline-header {
                display: flex;
                justify-content: space-between;
                align-items: center;
                margin-bottom: 8px;

                .action-info {
                    display: flex;
                    align-items: center;
                    gap: 8px;
                }
            }

            .timeline-content {
                >div {
                    display: flex;
                    align-items: center;
                    gap: 8px;
                    margin: 6px 0;
                    padding: 4px 0;
                }

                .reviewer-info,
                .reason-info,
                .notes-info,
                .status-change {
                    border-left: 3px solid #e4e7ed;
                    padding-left: 8px;
                    margin-left: 8px;
                }

                .reviewer-info {
                    border-left-color: #409eff;
                }

                .reason-info {
                    border-left-color: #e6a23c;
                }

                .notes-info {
                    border-left-color: #909399;
                }

                .status-change {
                    border-left-color: #67c23a;
                }

                .reviewer-id {
                    color: #909399;
                    font-size: 12px;
                }
            }
        }
    }

    .no-audit-history {
        .empty-state {
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            padding: 60px 20px;
            color: #909399;

            p {
                margin: 8px 0;
            }
        }
    }

    .card-header {
        display: flex;
        align-items: center;
        gap: 8px;
        font-weight: 600;

        .el-icon {
            color: #409eff;
        }
    }
}
</style>