<template>
    <div class="review-container">
        <h1>评价管理</h1>

        <!-- 统计数据 -->
        <el-row :gutter="20" class="stat-row">
            <el-col :span="6">
                <el-card shadow="hover" class="stat-card">
                    <div class="stat-content">
                        <div class="stat-title">总评价数</div>
                        <div class="stat-value">{{ reviewStats.total || 0 }}</div>
                    </div>
                </el-card>
            </el-col>
            <el-col :span="6">
                <el-card shadow="hover" class="stat-card">
                    <div class="stat-content">
                        <div class="stat-title">好评数 (4-5星)</div>
                        <div class="stat-value">{{ reviewStats.good || 0 }}</div>
                    </div>
                </el-card>
            </el-col>
            <el-col :span="6">
                <el-card shadow="hover" class="stat-card">
                    <div class="stat-content">
                        <div class="stat-title">中评数 (3星)</div>
                        <div class="stat-value">{{ reviewStats.neutral || 0 }}</div>
                    </div>
                </el-card>
            </el-col>
            <el-col :span="6">
                <el-card shadow="hover" class="stat-card">
                    <div class="stat-content">
                        <div class="stat-title">差评数 (1-2星)</div>
                        <div class="stat-value">{{ reviewStats.bad || 0 }}</div>
                    </div>
                </el-card>
            </el-col>
        </el-row>

        <!-- 筛选器 & 布局切换 -->
        <el-card class="filter-card">
            <div class="filter-controls">
                <el-form :inline="true" :model="filterForm" class="filter-form">
                    <el-form-item label="房源">
                        <el-select v-model="filterForm.homestayId" placeholder="全部房源" clearable style="width: 200px;">
                            <el-option v-for="item in homestayOptions" :key="item.id" :label="item.title"
                                :value="item.id" />
                        </el-select>
                    </el-form-item>
                    <el-form-item label="评分">
                        <el-select v-model="filterForm.rating" placeholder="全部评分" clearable style="width: 120px;">
                            <el-option label="5星" :value="5" />
                            <el-option label="4星" :value="4" />
                            <el-option label="3星" :value="3" />
                            <el-option label="2星" :value="2" />
                            <el-option label="1星" :value="1" />
                        </el-select>
                    </el-form-item>
                    <el-form-item label="回复状态">
                        <el-select v-model="filterForm.replyStatus" placeholder="全部状态" clearable style="width: 120px;">
                            <el-option label="已回复" value="REPLIED" />
                            <el-option label="未回复" value="PENDING" />
                        </el-select>
                    </el-form-item>
                    <el-form-item>
                        <el-button type="primary" @click="handleFilter" :icon="Search">筛选</el-button>
                        <el-button @click="resetFilter" :icon="Refresh">重置</el-button>
                    </el-form-item>
                </el-form>
                <div class="layout-switcher">
                    <el-radio-group v-model="layoutMode" size="small">
                        <el-radio-button label="card">卡片</el-radio-button>
                        <el-radio-button label="table">表格</el-radio-button>
                    </el-radio-group>
                </div>
            </div>
        </el-card>

        <!-- 评价列表 -->
        <el-card class="review-list-card" v-loading="loading">
            <div v-if="!loading && reviews.length === 0" class="empty-data">
                暂无评价数据
            </div>

            <!-- 卡片布局 -->
            <div v-if="layoutMode === 'card'" class="card-layout">
                <div v-for="review in reviews" :key="review.id" class="review-item"
                    :class="{ 'unreplied': !review.response }">
                    <div class="review-header">
                        <div class="review-info">
                            <div class="review-homestay">{{ review.homestayTitle }}</div>
                            <div class="review-user">{{ review.userName }} · {{ formatDate(review.createTime) }}</div>
                        </div>
                        <div class="review-rating">
                            <el-rate :model-value="review.rating" disabled text-color="#ff9900" size="small" />
                        </div>
                    </div>

                    <div class="review-content">{{ review.content }}</div>

                    <div class="review-reply" v-if="review.response">
                        <div class="reply-header">
                            <span class="reply-title">我的回复:</span>
                            <span class="reply-date">{{ formatDateTime(review.responseTime) }}</span>
                        </div>
                        <div class="reply-content">{{ review.response }}</div>
                    </div>

                    <div class="review-actions">
                        <el-button v-if="!review.response" type="primary" size="small" @click="showReplyDialog(review)">
                            回复
                        </el-button>
                        <template v-else>
                            <el-button type="primary" plain size="small" @click="showReplyDialog(review, true)">
                                修改回复
                            </el-button>
                            <el-button type="danger" plain size="small" @click="handleDeleteReply(review)">
                                删除回复
                            </el-button>
                        </template>
                    </div>
                </div>
            </div>

            <!-- 表格布局 -->
            <div v-if="layoutMode === 'table'" class="table-layout">
                <el-table :data="reviews" style="width: 100%" @sort-change="handleSortChange">
                    <el-table-column prop="homestayTitle" label="房源" width="180" show-overflow-tooltip />
                    <el-table-column label="用户" width="150">
                        <template #default="scope">
                            {{ scope.row.userName }}
                        </template>
                    </el-table-column>
                    <el-table-column prop="rating" label="评分" width="100" sortable="custom">
                        <template #default="scope">
                            <el-rate :model-value="scope.row.rating" disabled size="small" />
                        </template>
                    </el-table-column>
                    <el-table-column prop="content" label="评价内容" show-overflow-tooltip />
                    <el-table-column prop="createTime" label="评价时间" width="110" sortable="custom">
                        <template #default="scope">{{ formatDate(scope.row.createTime) }}</template>
                    </el-table-column>
                    <el-table-column label="回复状态/操作" width="180" fixed="right" align="center">
                        <template #default="scope">
                            <el-tag v-if="!scope.row.response" type="warning" size="small">待回复</el-tag>
                            <el-button v-if="!scope.row.response" type="primary" link size="small"
                                @click="showReplyDialog(scope.row)">
                                回复
                            </el-button>
                            <template v-else>
                                <el-tag type="success" size="small">已回复</el-tag>
                                <el-button type="primary" link size="small" @click="showReplyDialog(scope.row, true)">
                                    修改
                                </el-button>
                                <el-button type="danger" link size="small" @click="handleDeleteReply(scope.row)">
                                    删除
                                </el-button>
                            </template>
                        </template>
                    </el-table-column>
                </el-table>
            </div>

            <!-- 分页 -->
            <div class="pagination" v-if="total > 0">
                <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize"
                    :page-sizes="[5, 10, 20, 50]" layout="total, sizes, prev, pager, next, jumper" :total="total"
                    @size-change="handleSizeChange" @current-change="handleCurrentChange" />
            </div>
        </el-card>

        <!-- 回复对话框 -->
        <el-dialog v-model="replyDialogVisible" :title="currentReview && currentReview.response ? '修改回复' : '回复评价'"
            width="50%">
            <el-form :model="replyForm" ref="replyFormRef">
                <el-form-item label="回复内容" prop="content"
                    :rules="[{ required: true, message: '请输入回复内容', trigger: 'blur' }]">
                    <el-input v-model="replyForm.content" type="textarea" :rows="4" placeholder="请输入回复内容" />
                </el-form-item>
            </el-form>
            <template #footer>
                <span class="dialog-footer">
                    <el-button @click="replyDialogVisible = false">取消</el-button>
                    <el-button type="primary" @click="submitReply" :loading="submitting">
                        {{ currentReview && currentReview.response ? '更新' : '提交' }}
                    </el-button>
                </span>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'; // Import icons
import dayjs from 'dayjs';

// --- Import APIs ---
import { getHostReviews, getHostReviewStats, respondToReview, deleteReviewResponse } from '@/api/review';
import { getHostHomestayOptions } from '@/api/host';
// --- End Import APIs ---

// --- Define Interfaces ---
interface ReviewStatData {
    total: number;
    good: number;
    neutral: number;
    bad: number;
    // 可以根据后端返回添加其他字段，例如 averageRating, responded, unreplied
}

interface HomestayOption {
    id: number;
    title: string;
}

// 对应后端 ReviewDTO
interface ReviewItem {
    id: number;
    userId: number;
    userName: string;
    userAvatar?: string;
    homestayId: number;
    homestayTitle: string;
    orderId?: number;
    rating: number;
    content: string;
    response?: string | null; // 明确可以为 null
    createTime: string;
    updateTime?: string;
    responseTime?: string | null; // 明确可以为 null
    cleanlinessRating?: number;
    accuracyRating?: number;
    communicationRating?: number;
    locationRating?: number;
    checkInRating?: number;
    valueRating?: number;
    isPublic?: boolean;
}
// --- End Define Interfaces ---

// 统计数据
const reviewStats = reactive<ReviewStatData>({ total: 0, good: 0, neutral: 0, bad: 0 });

// 筛选表单
const filterForm = reactive({
    homestayId: null as number | null,
    rating: null as number | null,
    replyStatus: null as string | null, // 'REPLIED' or 'PENDING'
});

// 房源选项
const homestayOptions = ref<HomestayOption[]>([]);

// 页码相关
const loading = ref(true);
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);

// 评价列表
const reviews = ref<ReviewItem[]>([]);

// 回复相关
const replyDialogVisible = ref(false);
const replyFormRef = ref<FormInstance>();
const submitting = ref(false);
const currentReview = ref<ReviewItem | null>(null);
const replyForm = reactive({ content: '' });

// 新增：布局模式状态，默认卡片
const layoutMode = ref<'card' | 'table'>('card');

// 新增：排序状态
const sortProp = ref<string>('createTime'); // 默认排序字段
const sortOrder = ref<'descending' | 'ascending' | null>('descending'); // 默认排序顺序

// --- API Functions ---
// 获取统计数据
const fetchHostReviewStats = async () => {
    try {
        const response = await getHostReviewStats();
        if (response && response.data) {
            Object.assign(reviewStats, response.data); // 更新统计数据
        } else {
            ElMessage.warning('未能获取评价统计数据');
        }
    } catch (error) {
        console.error('获取评价统计失败:', error);
    }
};

// 获取房源选项
const fetchHomestayOptions = async () => {
    try {
        // getHostHomestayOptions 应该返回 Promise<HomestayOption[]>
        const options = await getHostHomestayOptions();
        homestayOptions.value = options.map(opt => ({ id: opt.id, title: opt.title })); // 确保格式正确
    } catch (error) {
        console.error('获取房源选项失败:', error);
        homestayOptions.value = []; // 出错时清空
    }
};

// 获取评价列表
const fetchReviews = async () => {
    loading.value = true;
    try {
        // 构建排序参数字符串
        let sortParam: string | undefined = undefined;
        if (sortProp.value && sortOrder.value) {
            const direction = sortOrder.value === 'ascending' ? 'asc' : 'desc';
            sortParam = `${sortProp.value},${direction}`;
        }

        const params = {
            homestayId: filterForm.homestayId,
            rating: filterForm.rating,
            replyStatus: filterForm.replyStatus,
            page: currentPage.value - 1,
            size: pageSize.value,
            sort: sortParam, // 使用构建好的排序参数
        };
        const response = await getHostReviews(params);
        console.log("Host Reviews Response:", response);
        if (response.data && response.data.content) {
            reviews.value = response.data.content;
            total.value = response.data.totalElements;
        } else {
            console.warn("获取房东评价列表数据格式无效", response);
            reviews.value = [];
            total.value = 0;
        }
    } catch (error) {
        console.error('获取评价列表失败:', error);
        reviews.value = [];
        total.value = 0;
    } finally {
        loading.value = false;
    }
};

// 提交回复
const submitReply = async () => {
    if (!replyFormRef.value || !currentReview.value) return;

    await replyFormRef.value.validate(async (valid) => {
        if (valid) {
            submitting.value = true;
            try {
                const reviewId = currentReview.value!.id;
                const responseText = replyForm.content;

                const updatedReviewDTO = await respondToReview(reviewId, responseText);
                console.log("Reply Response:", updatedReviewDTO);

                // 更新本地数据 - 直接用API返回的数据更新
                const index = reviews.value.findIndex(item => item.id === reviewId);
                if (index !== -1 && updatedReviewDTO.data) {
                    // 使用返回的完整DTO更新列表中的项
                    reviews.value[index] = { ...reviews.value[index], ...updatedReviewDTO.data };
                } else {
                    // 如果API没返回更新后的数据，或找不到index，则保守更新
                    if (index !== -1) {
                        reviews.value[index].response = responseText;
                        reviews.value[index].responseTime = new Date().toISOString(); // 模拟时间
                    }
                }

                ElMessage.success(currentReview.value!.response ? '回复已更新' : '回复已提交');
                replyDialogVisible.value = false;
            } catch (error: any) {
                console.error('回复提交失败:', error);
            } finally {
                submitting.value = false;
            }
        }
    });
};

// 处理删除回复
const handleDeleteReply = async (review: ReviewItem) => {
    if (!review || !review.response) return;

    try {
        await ElMessageBox.confirm(
            '确定要删除您对该评价的回复吗？',
            '确认删除',
            {
                confirmButtonText: '确定删除',
                cancelButtonText: '取消',
                type: 'warning',
            }
        );

        // 用户确认删除
        loading.value = true; // 可以用一个独立的 deleting 状态，或者复用 loading
        try {
            await deleteReviewResponse(review.id);
            ElMessage.success('回复已删除');
            // 更新本地数据
            const index = reviews.value.findIndex(item => item.id === review.id);
            if (index !== -1) {
                reviews.value[index].response = null;
                reviews.value[index].responseTime = null;
                // 如果有回复状态筛选，可能需要重新获取列表或调整筛选条件
                if (filterForm.replyStatus === 'REPLIED') {
                    // 可以选择移除当前项，或者重新请求数据
                    // reviews.value.splice(index, 1);
                    // total.value--; // 如果移除了项，需要更新总数
                    // 或者更简单地重新请求当前页
                    fetchReviews();
                }
            }
        } catch (error: any) {
            console.error('删除回复失败:', error);
        } finally {
            loading.value = false;
        }

    } catch (cancel) {
        // 用户取消删除
        ElMessage.info('已取消删除回复');
    }
};
// --- End API Functions ---

// --- Event Handlers ---
// 处理筛选
const handleFilter = () => {
    currentPage.value = 1; // 筛选时回到第一页
    fetchReviews();
};

// 重置筛选
const resetFilter = () => {
    filterForm.homestayId = null;
    filterForm.rating = null;
    filterForm.replyStatus = null;
    currentPage.value = 1; // 重置时回到第一页
    fetchReviews();
};

// 显示回复对话框
const showReplyDialog = (review: ReviewItem, isEdit = false) => {
    currentReview.value = review;
    replyForm.content = isEdit ? review.response || '' : ''; // 处理 null 的情况
    replyDialogVisible.value = true;
    // 清除之前的验证状态
    replyFormRef.value?.clearValidate(['content']);
};

// 页码变化
const handleCurrentChange = (page: number) => {
    currentPage.value = page;
    fetchReviews();
};

// 每页数量变化
const handleSizeChange = (size: number) => {
    pageSize.value = size;
    currentPage.value = 1; // 更改每页数量时回到第一页
    fetchReviews();
};

// 新增：处理表格排序变化
const handleSortChange = ({ prop, order }: { prop: string, order: 'descending' | 'ascending' | null }) => {
    console.log(`Sort changed: prop=${prop}, order=${order}`);
    sortProp.value = prop; // 更新排序字段
    sortOrder.value = order; // 更新排序顺序
    currentPage.value = 1; // 排序变化时回到第一页
    fetchReviews(); // 重新获取数据
};
// --- End Event Handlers ---

// --- Utils ---
const formatDate = (dateString?: string) => {
    if (!dateString) return '';
    return dayjs(dateString).format('YYYY-MM-DD');
};

const formatDateTime = (dateString?: string | null) => {
    if (!dateString) return '';
    return dayjs(dateString).format('YYYY-MM-DD HH:mm');
};
// --- End Utils ---

onMounted(() => {
    fetchHostReviewStats();
    fetchHomestayOptions();
    fetchReviews();
});
</script>

<style scoped>
.review-container {
    padding: 20px;
}

.stat-row {
    margin-bottom: 20px;
}

.stat-card {
    height: 100px;
}

.stat-content {
    height: 100%;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
}

.stat-title {
    font-size: 14px;
    color: #606266;
    margin-bottom: 10px;
}

.stat-value {
    font-size: 24px;
    font-weight: bold;
    color: #409EFF;
}

.filter-card {
    margin-bottom: 20px;
}

.filter-controls {
    display: flex;
    justify-content: space-between;
    align-items: center;
    /* 垂直居中 */
}

.layout-switcher {
    margin-left: 20px;
    /* 与筛选表单保持一些距离 */
}

.review-list-card {
    min-height: 400px;
}

.review-item {
    padding: 15px 0;
    border-bottom: 1px solid #EBEEF5;
    transition: background-color 0.3s ease;
    /* 添加过渡效果 */
}

/* 为未回复的评价项添加样式 */
.review-item.unreplied {
    background-color: #fdf6ec;
    /* 浅黄色背景 */
    border-left: 3px solid #e6a23c;
    /* 左侧橙色边框 */
    padding-left: 12px;
    /* 调整内边距以适应边框 */
    margin-left: -15px;
    /* 调整外边距以对齐 */
    margin-right: -15px;
    /* 调整外边距以对齐 */
    padding-right: 15px;
    /* 恢复右侧内边距 */
}

.review-item:last-child {
    border-bottom: none;
}

.review-header {
    display: flex;
    justify-content: space-between;
    margin-bottom: 10px;
}

.review-homestay {
    font-weight: bold;
    font-size: 16px;
    margin-bottom: 5px;
    color: #303133;
    /* 加深颜色 */
}

.review-user {
    color: #606266;
    /* 调整颜色 */
    font-size: 13px;
    /* 调整大小 */
}

.review-content {
    line-height: 1.6;
    margin: 10px 0 15px 0;
    color: #303133;
}

/* --- 新增：为卡片布局下的评价内容添加截断样式 --- */
.card-layout .review-content {
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 3;
    /* 最多显示 3 行 */
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: normal;
    /* 确保可以换行 */
    /* max-height: calc(1.6em * 3); */
    /* 可选：辅助限制高度，1.6em 是行高 */
}

/* --- 结束新增 --- */

.review-reply {
    background-color: #f0f9ff;
    /* 浅蓝色背景 */
    border: 1px solid #d9ecff;
    /* 浅蓝边框 */
    padding: 10px 15px;
    border-radius: 4px;
    margin: 15px 0;
    /* 调整外边距 */
}

.reply-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    /* 垂直居中 */
    margin-bottom: 8px;
    /* 增加间距 */
}

.reply-title {
    font-weight: bold;
    color: #409EFF;
}

.reply-date {
    color: #909399;
    font-size: 12px;
    /* 缩小字体 */
}

.reply-content {
    font-size: 14px;
    color: #606266;
    line-height: 1.5;
}

.review-actions {
    display: flex;
    justify-content: flex-end;
}

.review-actions .el-button {
    padding: 5px 10px;
    /* 微调按钮内边距 */
}

.review-actions .el-button+.el-button {
    margin-left: 8px;
    /* 给按钮之间添加一些间距 */
}

.filter-form .el-form-item {
    margin-bottom: 0;
    /* 减少筛选表单项间距 */
}

.pagination {
    margin-top: 25px;
    /* 调整分页上边距 */
    display: flex;
    justify-content: flex-end;
}

.empty-data {
    padding: 40px;
    text-align: center;
    color: #909399;
}

/* 给表格的操作按钮添加一些间距 */
.table-layout .el-button+.el-button {
    margin-left: 8px;
}

.table-layout .el-tag+.el-button {
    margin-left: 8px;
}
</style>