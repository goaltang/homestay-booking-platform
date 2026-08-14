<template>
    <div class="reviews">
        <div class="header">
            <h2>评价管理</h2>
        </div>

        <el-card shadow="never" class="filter-card">
            <el-form :inline="true" :model="filterForm" class="filter-form">
                <el-form-item label="评价状态">
                    <el-select v-model="filterForm.responseStatus" placeholder="全部状态" clearable>
                        <el-option label="未回复" value="NO_RESPONSE" />
                        <el-option label="已回复" value="RESPONDED" />
                    </el-select>
                </el-form-item>
                <el-form-item label="评分">
                    <el-select v-model="filterForm.rating" placeholder="全部评分" clearable>
                        <el-option label="5星" value="5" />
                        <el-option label="4星" value="4" />
                        <el-option label="3星" value="3" />
                        <el-option label="2星" value="2" />
                        <el-option label="1星" value="1" />
                    </el-select>
                </el-form-item>
                <el-form-item label="房源">
                    <el-select v-model="filterForm.homestayId" placeholder="全部房源" clearable>
                        <el-option v-for="item in homestayOptions" :key="item.value" :label="item.label"
                            :value="item.value" />
                    </el-select>
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" @click="handleFilter">筛选</el-button>
                    <el-button @click="resetFilter">重置</el-button>
                </el-form-item>
            </el-form>
        </el-card>

        <div class="reviews-content">
            <div v-if="reviews.length === 0 && !loading" class="no-reviews">
                <el-empty description="暂无评价" />
            </div>

            <div v-else class="reviews-list">
                <el-card v-for="review in reviews" :key="review.id" class="review-card" shadow="hover">
                    <div class="review-header">
                        <div class="user-info">
                            <el-avatar :size="50"
                                :src="review.userAvatar || 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'" />
                            <div class="user-details">
                                <div class="username">{{ review.userName }}</div>
                                <div class="review-date">{{ review.createTime }}</div>
                            </div>
                        </div>
                        <div class="review-rating">
                            <div class="rating-text">{{ review.rating }}.0</div>
                            <div class="rating-stars">
                                <el-rate v-model="review.rating" disabled text-color="#ff9900" />
                            </div>
                        </div>
                    </div>

                    <div class="review-content">
                        <div class="homestay-info">
                            <span class="label">房源:</span>
                            <span class="value">{{ review.homestayTitle }}</span>
                        </div>
                        <div class="review-text">{{ review.content }}</div>

                        <div class="review-images" v-if="review.images && review.images.length > 0">
                            <el-image v-for="(image, index) in review.images" :key="index" :src="image"
                                :preview-src-list="review.images" fit="cover" class="review-image" />
                        </div>
                    </div>

                    <div v-if="review.response" class="review-response">
                        <div class="response-header">
                            <span class="label">您的回复:</span>
                            <span class="response-date">{{ review.responseTime }}</span>
                        </div>
                        <div class="response-content">{{ review.response }}</div>
                    </div>

                    <div class="review-actions">
                        <el-button v-if="!review.response" type="primary" size="small" @click="handleReply(review)">
                            回复
                        </el-button>
                        <el-button v-else type="info" size="small" @click="handleEditReply(review)">
                            编辑回复
                        </el-button>
                    </div>
                </el-card>
            </div>

            <div class="pagination">
                <el-pagination v-model:currentPage="currentPage" v-model:page-size="pageSize"
                    :page-sizes="[10, 20, 30, 50]" layout="total, sizes, prev, pager, next, jumper" :total="total"
                    @size-change="handleSizeChange" @current-change="handleCurrentChange" />
            </div>
        </div>

        <!-- 回复对话框 -->
        <el-dialog v-model="replyDialogVisible" :title="isEdit ? '编辑回复' : '回复评价'" width="600px">
            <el-form v-if="currentReview" :model="replyForm">
                <el-form-item label="评价内容" label-width="100px">
                    <div class="review-preview">
                        <div class="preview-rating">
                            <el-rate v-model="currentReview.rating" disabled />
                            <span class="rating-value">{{ currentReview.rating }}.0</span>
                        </div>
                        <div class="preview-content">{{ currentReview.content }}</div>
                    </div>
                </el-form-item>
                <el-form-item label="您的回复" label-width="100px" prop="response">
                    <el-input v-model="replyForm.response" type="textarea" :rows="5" placeholder="请输入回复内容..."
                        maxlength="500" show-word-limit />
                </el-form-item>
            </el-form>
            <template #footer>
                <span class="dialog-footer">
                    <el-button @click="replyDialogVisible = false">取消</el-button>
                    <el-button type="primary" @click="submitReply">{{ isEdit ? '更新回复' : '提交回复' }}</el-button>
                </span>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { getReviews, replyToReview } from '@/api/review';
import { getHomestaysByOwner } from '@/api/homestay';
import { ElMessage } from 'element-plus';

const loading = ref(false);
interface ReviewItem {
    id: number;
    userAvatar?: string;
    userName: string;
    createTime: string;
    rating: number;
    homestayTitle: string;
    content: string;
    images?: string[];
    response?: string;
    responseTime?: string;
}

interface HomestayOption {
    label: string;
    value: number;
}

const reviews = ref<ReviewItem[]>([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(10);
const homestayOptions = ref<HomestayOption[]>([]);
const replyDialogVisible = ref(false);
const currentReview = ref<ReviewItem | null>(null);
const isEdit = ref(false);

const replyForm = ref({
    response: '',
});

const filterForm = ref({
    responseStatus: '',
    rating: '',
    homestayId: '' as number | '',
});

// 获取评价列表
const fetchReviews = async () => {
    try {
        loading.value = true;

        const params = {
            page: currentPage.value - 1,
            size: pageSize.value,
            responseStatus: filterForm.value.responseStatus || null,
            rating: filterForm.value.rating ? Number(filterForm.value.rating) : null,
            homestayId: filterForm.value.homestayId || null,
        };

        const response = await getReviews(params);
        const data = response.data;
        reviews.value = data.content || [];
        total.value = data.totalElements || 0;
    } catch (error) {
        console.error('获取评价列表失败', error);
    } finally {
        loading.value = false;
    }
};

// 获取房源列表作为筛选选项
const fetchHomestays = async () => {
    try {
        const response = await getHomestaysByOwner();
        homestayOptions.value = response.data.map((item: any) => ({
            label: item.title,
            value: item.id
        }));
    } catch (error) {
        console.error('获取房源列表失败', error);
    }
};

// 页码变化
const handleCurrentChange = (page: number) => {
    currentPage.value = page;
    fetchReviews();
};

// 每页数量变化
const handleSizeChange = (size: number) => {
    pageSize.value = size;
    fetchReviews();
};

// 筛选
const handleFilter = () => {
    currentPage.value = 1;
    fetchReviews();
};

// 重置筛选
const resetFilter = () => {
    filterForm.value = {
        responseStatus: '',
        rating: '',
        homestayId: '',
    };
    handleFilter();
};

// 回复评价
const handleReply = (review: ReviewItem) => {
    currentReview.value = review;
    replyForm.value.response = '';
    isEdit.value = false;
    replyDialogVisible.value = true;
};

// 编辑回复
const handleEditReply = (review: ReviewItem) => {
    currentReview.value = review;
    replyForm.value.response = review.response || '';
    isEdit.value = true;
    replyDialogVisible.value = true;
};

// 提交回复
const submitReply = async () => {
    if (!replyForm.value.response) {
        ElMessage.warning('请输入回复内容');
        return;
    }

    try {
        if (!currentReview.value) return;
        await replyToReview(currentReview.value.id, replyForm.value.response);
        ElMessage.success(isEdit.value ? '回复已更新' : '回复已提交');
        replyDialogVisible.value = false;
        fetchReviews();
    } catch (error) {
        console.error('提交回复失败', error);
    }
};

onMounted(() => {
    fetchHomestays();
    fetchReviews();
});
</script>

<style scoped>
.reviews {
    padding: 20px;
}

.header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
}

.filter-card {
    margin-bottom: 20px;
}

.filter-form {
    display: flex;
    flex-wrap: wrap;
}

.reviews-content {
    min-height: 300px;
}

.no-reviews {
    display: flex;
    justify-content: center;
    align-items: center;
    height: 300px;
}

.reviews-list {
    display: flex;
    flex-direction: column;
    gap: 20px;
}

.review-card {
    border-radius: 8px;
}

.review-header {
    display: flex;
    justify-content: space-between;
    margin-bottom: 15px;
}

.user-info {
    display: flex;
    align-items: center;
}

.user-details {
    margin-left: 15px;
}

.username {
    font-weight: bold;
    font-size: 16px;
    margin-bottom: 5px;
}

.review-date {
    color: #999;
    font-size: 14px;
}

.review-rating {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
}

.rating-text {
    font-size: 24px;
    font-weight: bold;
    color: #ff9900;
    margin-bottom: 5px;
}

.review-content {
    margin-bottom: 15px;
}

.homestay-info {
    margin-bottom: 10px;
}

.label {
    color: #666;
    margin-right: 5px;
}

.review-text {
    font-size: 16px;
    line-height: 1.5;
    margin-bottom: 10px;
}

.review-images {
    display: flex;
    gap: 10px;
    flex-wrap: wrap;
    margin-top: 10px;
}

.review-image {
    width: 100px;
    height: 100px;
    border-radius: 4px;
    object-fit: cover;
}

.review-response {
    background-color: #f5f7fa;
    padding: 15px;
    border-radius: 4px;
    margin-bottom: 15px;
}

.response-header {
    display: flex;
    justify-content: space-between;
    margin-bottom: 10px;
}

.response-date {
    color: #999;
    font-size: 14px;
}

.response-content {
    font-size: 14px;
    line-height: 1.5;
}

.review-actions {
    display: flex;
    justify-content: flex-end;
}

.pagination {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
}

.review-preview {
    background-color: #f5f7fa;
    padding: 15px;
    border-radius: 4px;
}

.preview-rating {
    display: flex;
    align-items: center;
    margin-bottom: 10px;
}

.rating-value {
    margin-left: 10px;
    font-weight: bold;
    color: #ff9900;
}

.preview-content {
    font-size: 14px;
    line-height: 1.5;
}
</style>
