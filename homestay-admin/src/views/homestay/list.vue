<template>
    <div class="homestay-list">
        <div class="search-box">
            <table-search :query="searchForm" :options="searchOptions" :search="handleSearch">
                <template #actions>
                    <el-button type="success" @click="handleAdd" :icon="Plus">新增房源</el-button>
                    <el-button type="warning" @click="goToAuditWorkbench" v-if="hasPendingItems" :icon="Warning">去审核中心</el-button>
                </template>
            </table-search>
        </div>

        <!-- 批量操作区域 -->
        <div class="batch-operation" v-if="selectedRows.length > 0">
            <el-alert title="批量操作" type="info" :closable="false" show-icon>
                <template #default>
                    已选择 <strong>{{ selectedRows.length }}</strong> 项
                    <div class="batch-buttons">
                        <el-button size="small" type="success" @click="handleBatchActive">批量上架</el-button>
                        <el-button size="small" type="info" @click="handleBatchInactive">批量下架</el-button>
                        <el-button size="small" type="danger" @click="handleBatchDelete">批量删除</el-button>
                    </div>
                </template>
            </el-alert>
        </div>

        <el-table :data="tableData" border style="width: 100%" v-loading="loading"
            @selection-change="handleSelectionChange" @sort-change="handleSortChange">
            <el-table-column type="selection" width="55" />
            <!-- 新增：封面列 -->
            <el-table-column label="封面" width="120">
                <template #default="scope">
                    <el-image 
                        v-if="scope.row.coverImage || (scope.row.images && scope.row.images.length > 0)"
                        :src="scope.row.coverImage || scope.row.images[0]" 
                        :preview-src-list="scope.row.images || []"
                        fit="cover"
                        style="width: 80px; height: 60px; border-radius: 4px;"
                    />
                    <div v-else class="no-image">无图</div>
                </template>
            </el-table-column>
            <el-table-column prop="id" label="ID" width="80" sortable="custom" />
            <el-table-column prop="title" label="房源名称" sortable="custom" />
            <el-table-column prop="price" label="价格" width="120" sortable="custom">
                <template #default="scope">
                    ¥{{ scope.row.price || scope.row.pricePerNight || 'N/A' }}
                </template>
            </el-table-column>
            <el-table-column label="地址" width="250">
                <template #default="scope">
                    {{ getLocationText(scope.row) }}
                </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="120" sortable="custom">
                <template #default="scope">
                    <el-tag :type="getStatusTagType(scope.row.status)" size="small">
                        {{ formatStatus(scope.row.status) }}
                    </el-tag>
                </template>
            </el-table-column>

            <el-table-column label="精选" width="110" align="center">
                <template #default="scope">
                    <el-tooltip
                        :content="scope.row.status === 'ACTIVE' ? '控制首页精选房源展示' : '仅已上架房源会在首页展示'"
                        placement="top"
                    >
                        <el-switch
                            :model-value="!!scope.row.featured"
                            :loading="isFeaturedUpdating(scope.row.id)"
                            active-text="是"
                            inactive-text="否"
                            inline-prompt
                            @change="(val: boolean) => handleFeaturedChange(scope.row, val)"
                        />
                    </el-tooltip>
                </template>
            </el-table-column>

            <el-table-column label="操作" width="180" fixed="right">
                <template #default="scope">
                    <el-button type="primary" link @click="handleViewDetail(scope.row)">详情</el-button>
                    <el-dropdown @command="(cmd: string) => handleCommand(cmd, scope.row)">
                        <el-button type="primary" link>
                            更多<el-icon class="el-icon--right"><arrow-down /></el-icon>
                        </el-button>
                        <template #dropdown>
                            <el-dropdown-menu>
                                <template v-if="scope.row.status === 'PENDING'">
                                    <el-dropdown-item command="audit">去审核</el-dropdown-item>
                                    <el-dropdown-item command="auditLog">审核记录</el-dropdown-item>
                                </template>
                                <template v-if="scope.row.status === 'ACTIVE'">
                                    <el-dropdown-item command="toggleStatus">下架</el-dropdown-item>
                                    <el-dropdown-item command="forceDelist">强制下架</el-dropdown-item>
                                    <el-dropdown-item command="edit">编辑</el-dropdown-item>
                                    <el-dropdown-item command="auditLog">审核记录</el-dropdown-item>
                                    <el-dropdown-item command="violations">违规记录</el-dropdown-item>
                                </template>
                                <template v-if="scope.row.status === 'INACTIVE'">
                                    <el-dropdown-item command="toggleStatus">上架</el-dropdown-item>
                                    <el-dropdown-item command="forceDelist">强制下架</el-dropdown-item>
                                    <el-dropdown-item command="edit">编辑</el-dropdown-item>
                                    <el-dropdown-item command="auditLog">审核记录</el-dropdown-item>
                                    <el-dropdown-item command="violations">违规记录</el-dropdown-item>
                                </template>
                                <template v-if="scope.row.status === 'REJECTED'">
                                    <el-dropdown-item command="viewDetail">查看详情</el-dropdown-item>
                                    <el-dropdown-item command="auditLog">审核记录</el-dropdown-item>
                                    <el-dropdown-item command="delete">删除</el-dropdown-item>
                                </template>
                                <template v-if="['DRAFT', 'SUSPENDED'].includes(scope.row.status)">
                                    <el-dropdown-item command="edit">编辑</el-dropdown-item>
                                    <el-dropdown-item command="auditLog">审核记录</el-dropdown-item>
                                    <el-dropdown-item command="delete">删除</el-dropdown-item>
                                </template>
                            </el-dropdown-menu>
                        </template>
                    </el-dropdown>
                </template>
            </el-table-column>
        </el-table>

        <div class="pagination">
            <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize"
                :page-sizes="[10, 20, 50, 100]" :total="total" layout="total, sizes, prev, pager, next, jumper"
                @size-change="handleSizeChange" @current-change="handleCurrentChange" />
        </div>

        <!-- 房源详情对话框 -->
        <el-dialog v-model="detailDialogVisible" title="房源详情" width="80%" top="5vh">
            <div v-if="currentDetailItem" class="detail-content">
                <el-descriptions :column="2" border>
                    <el-descriptions-item label="房源ID">{{ currentDetailItem.id }}</el-descriptions-item>
                    <el-descriptions-item label="状态">
                        <el-tag :type="getStatusTagType(currentDetailItem.status)" size="small">
                            {{ formatStatus(currentDetailItem.status) }}
                        </el-tag>
                    </el-descriptions-item>
                    <el-descriptions-item label="房源名称" :span="2">
                        <strong>{{ currentDetailItem.title }}</strong>
                    </el-descriptions-item>
                    <el-descriptions-item label="价格">¥{{ currentDetailItem.price }}/晚</el-descriptions-item>
                    <el-descriptions-item label="房源类型">{{ currentDetailItem.type || 'N/A' }}</el-descriptions-item>
                    <el-descriptions-item label="地址" :span="2">
                        {{ getLocationText(currentDetailItem) }}
                    </el-descriptions-item>
                    <el-descriptions-item label="描述" :span="2">
                        {{ currentDetailItem.description || '暂无描述' }}
                    </el-descriptions-item>
                    <!-- 房东信息 -->
                    <el-descriptions-item label="房东姓名" v-if="currentDetailItem.ownerName">
                        {{ currentDetailItem.ownerName || 'N/A' }}
                    </el-descriptions-item>
                    <el-descriptions-item label="房东手机" v-if="currentDetailItem.ownerPhone">
                        {{ currentDetailItem.ownerPhone || 'N/A' }}
                    </el-descriptions-item>
                </el-descriptions>

                <!-- 房源图片 -->
                <div class="detail-section" v-if="currentDetailItem.images && currentDetailItem.images.length > 0">
                    <h4>房源图片</h4>
                    <div class="image-list">
                        <el-image 
                            v-for="(img, index) in currentDetailItem.images" 
                            :key="index"
                            :src="img" 
                            :preview-src-list="currentDetailItem.images"
                            fit="cover"
                            class="detail-image"
                        />
                    </div>
                </div>

                <!-- 配套设施 -->
                <div class="detail-section" v-if="currentDetailItem.amenities && currentDetailItem.amenities.length > 0">
                    <h4>配套设施</h4>
                    <div class="amenity-list">
                        <el-tag v-for="(amenity, index) in currentDetailItem.amenities" :key="index" class="amenity-tag">
                            {{ amenity }}
                        </el-tag>
                    </div>
                </div>
            </div>
            <template #footer>
                <el-button @click="detailDialogVisible = false">关闭</el-button>
            </template>
        </el-dialog>

        <!-- 审核记录查看对话框 -->
        <el-dialog v-model="auditLogsDialogVisible" title="审核记录" width="70%">
            <div v-if="selectedHomestay">
                <h4>{{ selectedHomestay.title }} - 审核历史</h4>
                <el-table :data="auditLogsData" border style="width: 100%;" v-loading="auditLogsLoading">
                    <el-table-column prop="createdAt" label="操作时间" width="160">
                        <template #default="scope">
                            {{ formatDateTime(scope.row.createdAt) }}
                        </template>
                    </el-table-column>
                    <el-table-column prop="actionType" label="操作类型" width="100">
                        <template #default="scope">
                            <el-tag :type="getAuditLogType(scope.row.actionType)" size="small">
                                {{ getActionTypeText(scope.row.actionType) }}
                            </el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column label="状态变更" width="150">
                        <template #default="scope">
                            <span>{{ formatStatus(scope.row.oldStatus) }}</span>
                            <el-icon style="margin: 0 5px;">
                                <ArrowRight />
                            </el-icon>
                            <span>{{ formatStatus(scope.row.newStatus) }}</span>
                        </template>
                    </el-table-column>
                    <el-table-column prop="reviewerName" label="操作人" width="120" />
                    <el-table-column prop="reviewReason" label="原因" />
                </el-table>

                <div class="pagination" style="margin-top: 20px;">
                    <el-pagination v-model:current-page="auditLogsPage" v-model:page-size="auditLogsPageSize"
                        :page-sizes="[5, 10, 20]" :total="auditLogsTotal" layout="total, sizes, prev, pager, next"
                        @size-change="handleAuditLogsPageSizeChange" @current-change="handleAuditLogsPageChange" />
                </div>
            </div>
            <template #footer>
                <el-button @click="auditLogsDialogVisible = false">关闭</el-button>
            </template>
        </el-dialog>

        <!-- 强制下架对话框 -->
        <el-dialog v-model="forceDelistDialogVisible" title="强制下架房源" width="50%">
            <div v-if="selectedHomestay">
                <el-alert title="警告" type="warning" :closable="false" show-icon style="margin-bottom: 20px;">
                    <template #default>
                        您正在强制下架房源：<strong>{{ selectedHomestay.title }}</strong>。此操作将立即生效，房东将收到通知。
                    </template>
                </el-alert>
                
                <el-form :model="forceDelistForm" label-width="100px">
                    <el-form-item label="违规类型">
                        <el-select v-model="forceDelistForm.violationType" placeholder="请选择违规类型" clearable>
                            <el-option label="虚假信息" value="FALSE_INFO" />
                            <el-option label="违规内容" value="PROHIBITED_CONTENT" />
                            <el-option label="安全隐患" value="SAFETY_ISSUE" />
                            <el-option label="价格欺诈" value="PRICE_FRAUD" />
                            <el-option label="其他违规" value="OTHER" />
                        </el-select>
                    </el-form-item>
                    <el-form-item label="下架原因" required>
                        <el-input v-model="forceDelistForm.reason" type="textarea" :rows="3" placeholder="请详细说明下架原因" />
                    </el-form-item>
                    <el-form-item label="备注">
                        <el-input v-model="forceDelistForm.notes" type="textarea" :rows="2" placeholder="可选备注信息" />
                    </el-form-item>
                </el-form>
            </div>
            <template #footer>
                <el-button @click="forceDelistDialogVisible = false">取消</el-button>
                <el-button type="danger" @click="submitForceDelist" :loading="forceDelistLoading">确认强制下架</el-button>
            </template>
        </el-dialog>

        <!-- 违规记录对话框 -->
        <el-dialog v-model="violationRecordsDialogVisible" title="房源违规记录" width="70%">
            <div v-if="selectedHomestay">
                <h4>{{ selectedHomestay.title }} - 违规历史</h4>
                <el-table :data="violationRecordsData" border style="width: 100%;" v-loading="violationRecordsLoading">
                    <el-table-column prop="createdAt" label="举报时间" width="160">
                        <template #default="scope">
                            {{ formatDateTime(scope.row.createdAt) }}
                        </template>
                    </el-table-column>
                    <el-table-column prop="violationType" label="违规类型" width="120">
                        <template #default="scope">
                            <el-tag :type="getViolationTypeTag(scope.row.violationType)" size="small">
                                {{ formatViolationType(scope.row.violationType) }}
                            </el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column prop="status" label="处理状态" width="100">
                        <template #default="scope">
                            <el-tag :type="getViolationStatusTag(scope.row.status)" size="small">
                                {{ formatViolationStatus(scope.row.status) }}
                            </el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column prop="reporterName" label="举报人" width="120" />
                    <el-table-column prop="reason" label="举报原因" />
                    <el-table-column prop="actionTaken" label="处理措施" width="150" />
                </el-table>
                
                <div v-if="violationRecordsData.length === 0 && !violationRecordsLoading" style="text-align: center; padding: 20px;">
                    暂无违规记录
                </div>
            </div>
            <template #footer>
                <el-button @click="violationRecordsDialogVisible = false">关闭</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowRight, Plus, Warning, ArrowDown } from '@element-plus/icons-vue'
import {
    getHomestayList,
    deleteHomestay,
    updateHomestayStatus,
    updateHomestayFeatured,
    batchDeleteHomestays,
    batchUpdateHomestayStatus,
    getHomestayDetail,
    getHomestayAuditLogs,
    forceDelistHomestay
} from '@/api/homestay'
import { getHomestayReports } from '@/api/violation'
import type { Homestay } from '@/types'
import TableSearch from '@/components/table-search.vue'
import type { FormOptionList } from '@/types/form-option'
// 区域代码映射（两位国标码）
const codeToText: Record<string, string> = {
    '11': '北京市',
    '12': '天津市',
    '13': '河北省',
    '14': '山西省',
    '15': '内蒙古自治区',
    '21': '辽宁省',
    '22': '吉林省',
    '23': '黑龙江省',
    '31': '上海市',
    '32': '江苏省',
    '33': '浙江省',
    '34': '安徽省',
    '35': '福建省',
    '36': '江西省',
    '37': '山东省',
    '41': '河南省',
    '42': '湖北省',
    '43': '湖南省',
    '44': '广东省',
    '45': '广西壮族自治区',
    '46': '海南省',
    '50': '重庆市',
    '51': '四川省',
    '52': '贵州省',
    '53': '云南省',
    '54': '西藏自治区',
    '61': '陕西省',
    '62': '甘肃省',
    '63': '青海省',
    '64': '宁夏回族自治区',
    '65': '新疆维吾尔自治区',
    '71': '台湾省',
    '81': '香港特别行政区',
    '82': '澳门特别行政区',
}

// 定义 AuditLog 类型
interface AuditLog {
    id: number;
    homestayId: number;
    homestayTitle: string;
    reviewerId: number;
    reviewerName: string;
    oldStatus: string;
    newStatus: string;
    actionType: string;
    reviewReason?: string;
    reviewNotes?: string;
    createdAt: string;
    ipAddress?: string;
}

const router = useRouter()

// 搜索表单
const searchForm = reactive({
    name: '',
    status: '',
    type: '',
    minPrice: undefined as number | undefined,
    maxPrice: undefined as number | undefined,
    landlordName: '',
    sort: 'createdAt,desc'
})

// 筛选配置
const searchOptions: FormOptionList[] = [
    { label: '房源名称', prop: 'name', type: 'input', placeholder: '请输入房源名称' },
    { label: '状态', prop: 'status', type: 'select', placeholder: '请选择状态', opts: [
        { label: '草稿', value: 'DRAFT' },
        { label: '待审核', value: 'PENDING' },
        { label: '已上架', value: 'ACTIVE' },
        { label: '已下架', value: 'INACTIVE' },
        { label: '已拒绝', value: 'REJECTED' },
        { label: '已暂停', value: 'SUSPENDED' },
    ]},
    { label: '房源类型', prop: 'type', type: 'select', placeholder: '请选择类型', opts: [
        { label: '整套房源', value: 'ENTIRE' },
        { label: '独立房间', value: 'PRIVATE' },
        { label: '共享房间', value: 'SHARED' },
        { label: '民宿', value: 'HOMESTAY' },
    ]},
    { label: '最低价', prop: 'minPrice', type: 'number', placeholder: '¥' },
    { label: '最高价', prop: 'maxPrice', type: 'number', placeholder: '¥' },
    { label: '房东姓名', prop: 'landlordName', type: 'input', placeholder: '请输入房东姓名' },
]

// 表格数据
const tableData = ref<Homestay[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const selectedRows = ref<Homestay[]>([])
const featuredUpdatingIds = ref<Set<number>>(new Set())

// 详情对话框
const detailDialogVisible = ref(false)
const currentDetailItem = ref<Homestay | null>(null)

// 审核记录对话框
const auditLogsDialogVisible = ref(false)
const selectedHomestay = ref<Homestay | null>(null)
const auditLogsData = ref<AuditLog[]>([])
const auditLogsLoading = ref(false)
const auditLogsPage = ref(1)
const auditLogsPageSize = ref(10)
const auditLogsTotal = ref(0)

// 强制下架对话框
const forceDelistDialogVisible = ref(false)
const forceDelistForm = reactive({
    reason: '',
    notes: '',
    violationType: ''
})
const forceDelistLoading = ref(false)

// 违规记录对话框
const violationRecordsDialogVisible = ref(false)
const violationRecordsData = ref<any[]>([])
const violationRecordsLoading = ref(false)

// 计算属性
const hasPendingItems = computed(() => {
    return tableData.value.some(item => item.status === 'PENDING')
})

// 跳转到审核工作台
const goToAuditWorkbench = () => {
    router.push('/audit/workbench')
}

const goToAuditWorkbenchWithId = (id: number) => {
    router.push('/audit/workbench?selectedId=' + id)
}

// 处理表格选择变化
const handleSelectionChange = (selection: Homestay[]) => {
    selectedRows.value = selection
}

// 搜索方法
const handleSearch = () => {
    currentPage.value = 1
    fetchData()
}



// 获取数据
const fetchData = async () => {
    loading.value = true
    try {
        const res = await getHomestayList({
            page: currentPage.value,
            pageSize: pageSize.value,
            name: searchForm.name,
            status: searchForm.status,
            type: searchForm.type,
            minPrice: searchForm.minPrice,
            maxPrice: searchForm.maxPrice,
            landlordName: searchForm.landlordName,
            sort: searchForm.sort || 'createdAt,desc',
        });

        if (res && res.list) {
            tableData.value = res.list;
            total.value = res.total;
        } else {
            tableData.value = [];
            total.value = 0;
        }
    } catch (error) {
        console.error('获取房源列表失败:', error);
        ElMessage.error('获取房源列表失败');
        tableData.value = [];
        total.value = 0;
    } finally {
        loading.value = false;
    }
};

// 分页方法
const handleSizeChange = (val: number) => {
    pageSize.value = val
    fetchData()
}

const handleCurrentChange = (val: number) => {
    currentPage.value = val
    fetchData()
}

// 新增房源
const handleAdd = () => {
    router.push('/homestays/add')
}

// 编辑房源
const handleEdit = (row: Homestay) => {
    if (!row.id) {
        ElMessage.error('无效的房源ID');
        return;
    }
    router.push(`/homestays/edit/${row.id}`)
}

// 查看详情
const handleViewDetail = async (row: Homestay) => {
    try {
        const homestayDetail = await getHomestayDetail(row.id);
        currentDetailItem.value = homestayDetail;
        detailDialogVisible.value = true;
    } catch (error) {
        console.error('获取房源详情失败:', error);
        ElMessage.error('获取房源详情失败');
    }
}

const setFeaturedUpdating = (id: number, updating: boolean) => {
    const next = new Set(featuredUpdatingIds.value)
    if (updating) {
        next.add(id)
    } else {
        next.delete(id)
    }
    featuredUpdatingIds.value = next
}

const isFeaturedUpdating = (id: number) => featuredUpdatingIds.value.has(id)

const handleFeaturedChange = async (row: Homestay, featured: boolean) => {
    if (!row.id) {
        ElMessage.error('无效的房源ID')
        return
    }

    const oldFeatured = row.featured
    // 乐观更新：先切换 UI
    row.featured = featured
    setFeaturedUpdating(row.id, true)
    
    try {
        console.log('使用 updateHomestayFeatured 更新精选状态:', row.id, featured)
        // 使用专门的精选状态更新接口
        const result = await updateHomestayFeatured(row.id, featured)
        console.log('更新结果:', result)
        
        // 更新本地数据
        if (result && result.featured !== undefined) {
            row.featured = result.featured
        }
        
        const suffix = row.status === 'ACTIVE' ? '' : '，上架后会在首页展示'
        ElMessage.success(featured ? `已设为首页精选${suffix}` : '已取消首页精选')
    } catch (error: any) {
        console.error('更新精选状态失败:', error)
        // 回滚 UI
        row.featured = oldFeatured
        
        const errorMsg = error.response?.data?.message 
            || error.response?.data?.error 
            || error.message 
            || '更新精选状态失败'
        ElMessage.error(errorMsg)
    } finally {
        setFeaturedUpdating(row.id, false)
    }
}

// 切换状态
const handleToggleStatus = async (row: Homestay) => {
    const targetStatus = row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    const actionText = row.status === 'ACTIVE' ? '下架' : '上架';
    try {
        await ElMessageBox.confirm(
            `确认要${actionText}该房源吗？`,
            '提示',
            {
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                type: 'warning'
            }
        )
        await updateHomestayStatus(row.id, targetStatus)
        ElMessage.success(`${actionText}成功`)
        fetchData();
    } catch (error) {
        console.log('操作取消或失败');
    }
}

// 删除房源
const handleDelete = async (row: Homestay) => {
    try {
        await ElMessageBox.confirm('确认要删除该房源吗？', '提示', {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
        })
        await deleteHomestay(row.id)
        ElMessage.success('删除成功')
        fetchData()
    } catch (error) {
        console.log('删除操作取消或失败');
    }
}

// 批量操作
const handleBatchActive = async () => {
    if (selectedRows.value.length === 0) {
        ElMessage.warning('请至少选择一项')
        return
    }
    const rowsToActivate = selectedRows.value.filter(item => item.status !== 'ACTIVE');
    if (rowsToActivate.length === 0) {
        ElMessage.info('选中的房源都已是上架状态');
        return;
    }
    const ids = rowsToActivate.map(item => item.id);
    try {
        await ElMessageBox.confirm(`确认要批量上架选中的 ${ids.length} 个房源吗？`, '提示', { type: 'warning' });
        await batchUpdateHomestayStatus(ids, 'ACTIVE');
        ElMessage.success('批量上架成功');
        fetchData();
        selectedRows.value = [];
    } catch (error) {
        console.log('批量上架取消或失败');
    }
}

const handleBatchInactive = async () => {
    if (selectedRows.value.length === 0) {
        ElMessage.warning('请至少选择一项')
        return
    }
    const rowsToDeactivate = selectedRows.value.filter(item => item.status !== 'INACTIVE');
    if (rowsToDeactivate.length === 0) {
        ElMessage.info('选中的房源都已是下架状态');
        return;
    }
    const ids = rowsToDeactivate.map(item => item.id);
    try {
        await ElMessageBox.confirm(`确认要批量下架选中的 ${ids.length} 个房源吗？`, '提示', { type: 'warning' });
        await batchUpdateHomestayStatus(ids, 'INACTIVE');
        ElMessage.success('批量下架成功');
        fetchData();
        selectedRows.value = [];
    } catch (error) {
        console.log('批量下架取消或失败');
    }
}

const handleBatchDelete = async () => {
    if (selectedRows.value.length === 0) {
        ElMessage.warning('请至少选择一项')
        return
    }
    const ids = selectedRows.value.map(item => item.id);
    try {
        await ElMessageBox.confirm(`确认要批量删除选中的 ${ids.length} 个房源吗？此操作不可恢复！`, '危险操作确认', {
            confirmButtonText: '确定删除',
            cancelButtonText: '取消',
            type: 'error'
        });
        await batchDeleteHomestays(ids);
        ElMessage.success('批量删除成功');
        fetchData();
        selectedRows.value = [];
    } catch (error) {
        console.log('批量删除取消或失败');
    }
}

// 查看审核记录
const handleViewAuditLogs = async (row: Homestay) => {
    selectedHomestay.value = row;
    auditLogsPage.value = 1;
    auditLogsDialogVisible.value = true;
    await loadAuditLogsData();
}

// 加载审核记录数据
const loadAuditLogsData = async () => {
    if (!selectedHomestay.value) return;

    try {
        auditLogsLoading.value = true;
        const result = await getHomestayAuditLogs(
            selectedHomestay.value.id,
            auditLogsPage.value,
            auditLogsPageSize.value
        );
        auditLogsData.value = result.list;
        auditLogsTotal.value = result.total;
    } catch (error) {
        console.error('获取审核记录失败:', error);
        ElMessage.error('获取审核记录失败');
    } finally {
        auditLogsLoading.value = false;
    }
}

// 审核记录分页处理
const handleAuditLogsPageChange = (page: number) => {
    auditLogsPage.value = page;
    loadAuditLogsData();
}

const handleAuditLogsPageSizeChange = (size: number) => {
    auditLogsPageSize.value = size;
    auditLogsPage.value = 1;
    loadAuditLogsData();
}

// 强制下架房源
const handleForceDelist = (row: Homestay) => {
    if (!row.id) {
        ElMessage.error('无效的房源ID');
        return;
    }
    selectedHomestay.value = row;
    forceDelistForm.reason = '';
    forceDelistForm.notes = '';
    forceDelistForm.violationType = '';
    forceDelistDialogVisible.value = true;
}

// 提交强制下架
const submitForceDelist = async () => {
    if (!selectedHomestay.value) return;
    
    if (!forceDelistForm.reason.trim()) {
        ElMessage.warning('请填写下架原因');
        return;
    }
    
    try {
        forceDelistLoading.value = true;
        await forceDelistHomestay(selectedHomestay.value.id, {
            reason: forceDelistForm.reason,
            notes: forceDelistForm.notes,
            violationType: forceDelistForm.violationType
        });
        ElMessage.success('强制下架成功');
        forceDelistDialogVisible.value = false;
        fetchData();
    } catch (error) {
        console.error('强制下架失败:', error);
        ElMessage.error('强制下架失败');
    } finally {
        forceDelistLoading.value = false;
    }
}

// 查看房源违规记录
const handleViewViolations = async (row: Homestay) => {
    if (!row.id) {
        ElMessage.error('无效的房源ID');
        return;
    }
    selectedHomestay.value = row;
    violationRecordsDialogVisible.value = true;
    await loadViolationRecords();
}

// 加载违规记录
const loadViolationRecords = async () => {
    if (!selectedHomestay.value) return;
    
    try {
        violationRecordsLoading.value = true;
        const result = await getHomestayReports(selectedHomestay.value.id);
        violationRecordsData.value = result || [];
    } catch (error) {
        console.error('获取违规记录失败:', error);
        ElMessage.error('获取违规记录失败');
        violationRecordsData.value = [];
    } finally {
        violationRecordsLoading.value = false;
    }
}

// 工具方法
const formatStatus = (status: string): string => {
    switch (status) {
        case 'DRAFT': return '草稿';
        case 'PENDING': return '待审核';
        case 'ACTIVE': return '已上架';
        case 'INACTIVE': return '已下架';
        case 'REJECTED': return '已拒绝';
        case 'SUSPENDED': return '已暂停';
        default: return '未知';
    }
};

const getStatusTagType = (status: string): "" | "success" | "info" | "warning" | "danger" => {
    switch (status) {
        case 'PENDING': return 'warning';
        case 'ACTIVE': return 'success';
        case 'INACTIVE': return 'info';
        case 'REJECTED': return 'danger';
        default: return '';
    }
};

const formatDateTime = (dateTime: string | null | undefined): string => {
    if (!dateTime) return '未知时间';
    try {
        return new Date(dateTime).toLocaleString('zh-CN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });
    } catch (error) {
        return '时间格式错误';
    }
}

const getActionTypeText = (actionType: string): string => {
    switch (actionType) {
        case 'SUBMIT': return '提交';
        case 'APPROVE': return '批准';
        case 'REJECT': return '拒绝';
        case 'REVIEW': return '审核';
        case 'UPDATE': return '更新';
        case 'DELETE': return '删除';
        case 'RESUBMIT': return '重新提交';
        default: return actionType;
    }
}

const getAuditLogType = (actionType: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' => {
    switch (actionType) {
        case 'APPROVE': return 'success';
        case 'REJECT': return 'danger';
        case 'SUBMIT':
        case 'RESUBMIT': return 'primary';
        default: return 'warning';
    }
}

const getLocationText = (homestay: Homestay | null | undefined): string => {
    if (!homestay) return '地址信息不完整';
    const parts = [];
    try {
        if (homestay.provinceCode && codeToText[homestay.provinceCode]) {
            parts.push(codeToText[homestay.provinceCode]);
        }
        if (homestay.cityCode && codeToText[homestay.cityCode]) {
            const cityText = codeToText[homestay.cityCode];
            if (!parts.includes(cityText)) {
                parts.push(cityText);
            }
        }
        if (homestay.districtCode && codeToText[homestay.districtCode]) {
            const districtText = codeToText[homestay.districtCode];
            if (!parts.includes(districtText)) {
                parts.push(districtText);
            }
        }
        if (homestay.addressDetail) {
            parts.push(homestay.addressDetail);
        }
        return parts.length > 0 ? parts.join(' ') : '地址信息不完整';
    } catch (error) {
        console.error(`Error in getLocationText for homestay ID ${homestay.id}:`, error, homestay);
        return '地址处理错误';
    }
};

// 违规类型格式化
const formatViolationType = (type: string): string => {
    switch (type) {
        case 'FALSE_INFO': return '虚假信息';
        case 'PROHIBITED_CONTENT': return '违规内容';
        case 'SAFETY_ISSUE': return '安全隐患';
        case 'PRICE_FRAUD': return '价格欺诈';
        case 'OTHER': return '其他违规';
        default: return type || '未知';
    }
};

// 违规类型标签样式
const getViolationTypeTag = (type: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' => {
    switch (type) {
        case 'FALSE_INFO': return 'danger';
        case 'PROHIBITED_CONTENT': return 'danger';
        case 'SAFETY_ISSUE': return 'warning';
        case 'PRICE_FRAUD': return 'danger';
        default: return 'info';
    }
};

// 违规状态格式化
const formatViolationStatus = (status: string): string => {
    switch (status) {
        case 'PENDING': return '待处理';
        case 'PROCESSING': return '处理中';
        case 'RESOLVED': return '已处理';
        case 'DISMISSED': return '已忽略';
        case 'CONFIRMED': return '已确认';
        default: return status || '未知';
    }
};

// 违规状态标签样式
const getViolationStatusTag = (status: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' => {
    switch (status) {
        case 'PENDING': return 'warning';
        case 'PROCESSING': return 'primary';
        case 'RESOLVED': return 'success';
        case 'DISMISSED': return 'info';
        case 'CONFIRMED': return 'danger';
        default: return 'info';
    }
};

// 处理下拉菜单命令
const handleCommand = (command: string, row: Homestay) => {
    switch (command) {
        case 'audit':
            goToAuditWorkbenchWithId(row.id);
            break;
        case 'toggleStatus':
            handleToggleStatus(row);
            break;
        case 'forceDelist':
            handleForceDelist(row);
            break;
        case 'edit':
            handleEdit(row);
            break;
        case 'viewDetail':
            handleViewDetail(row);
            break;
        case 'auditLog':
            handleViewAuditLogs(row);
            break;
        case 'violations':
            handleViewViolations(row);
            break;
        case 'delete':
            handleDelete(row);
            break;
    }
};

// 排序变化
const handleSortChange = ({ prop, order }: any) => {
    if (!prop || !order) {
        searchForm.sort = 'createdAt,desc'
    } else {
        const direction = order === 'descending' ? 'desc' : 'asc'
        searchForm.sort = `${prop},${direction}`
    }
    currentPage.value = 1
    fetchData()
}

// 初始化
onMounted(() => {
    fetchData();
});
</script>

<style scoped lang="scss">
.homestay-list {
    padding: 20px;

    .search-box {
        margin-bottom: 20px;
        padding: 20px;
        background: #f5f7fa;
        border-radius: 8px;
    }

    .batch-operation {
        margin-bottom: 20px;

        .batch-buttons {
            margin-top: 10px;
        }
    }

    .pagination {
        margin-top: 20px;
        text-align: center;
    }

    .detail-content {
        padding: 20px 0;
    }

    .no-image {
        width: 80px;
        height: 60px;
        background: #f5f7fa;
        display: flex;
        align-items: center;
        justify-content: center;
        color: #999;
        font-size: 12px;
        border-radius: 4px;
    }

    .detail-section {
        margin-top: 20px;
        h4 {
            margin-bottom: 10px;
            font-size: 16px;
            color: #303133;
        }
    }

    .image-list {
        display: flex;
        flex-wrap: wrap;
        gap: 10px;
        .detail-image {
            width: 150px;
            height: 100px;
            border-radius: 4px;
        }
    }

    .amenity-list {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        .amenity-tag {
            margin: 0;
        }
    }
}
</style>
