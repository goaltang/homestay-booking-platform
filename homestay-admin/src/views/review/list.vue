<template>
    <div class="review-container">
        <TableSearch :query="query" :options="searchOptions" :search="handleSearch" />

        <!-- 批量操作栏 -->
        <div v-if="selectedIds.length > 0" class="batch-toolbar">
            <span class="batch-info">已选择 {{ selectedIds.length }} 条</span>
            <el-button size="small" type="primary" @click="handleBatchShow">批量显示</el-button>
            <el-button size="small" type="warning" @click="handleBatchHide">批量隐藏</el-button>
            <el-button size="small" type="danger" @click="handleBatchDelete">批量删除</el-button>
        </div>

        <el-table :data="tableData" border style="width: 100%" v-loading="loading" @sort-change="handleSortChange"
            @selection-change="handleSelectionChange">
            <el-table-column type="selection" width="55" align="center" />
            <el-table-column prop="id" label="ID" width="80" sortable="custom" />
            <el-table-column prop="userName" label="用户" width="120">
                <template #default="scope">
                    <div class="user-info">
                        <el-avatar :size="30" :src="scope.row.userAvatar" />
                        <span style="margin-left: 5px;">{{ scope.row.userName }}</span>
                    </div>
                </template>
            </el-table-column>
            <el-table-column prop="homestayTitle" label="民宿" width="180" show-overflow-tooltip />
            <el-table-column prop="rating" label="评分" width="100" sortable="custom">
                <template #default="scope">
                    <el-rate :model-value="scope.row.rating" disabled size="small" />
                </template>
            </el-table-column>
            <el-table-column prop="content" label="评价内容" show-overflow-tooltip />
            <el-table-column prop="createTime" label="评价时间" width="160" sortable="custom">
                <template #default="scope">{{ formatDateTime(scope.row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="可见性" width="100" align="center">
                <template #default="scope">
                    <el-switch v-model="scope.row.isPublic" :active-value="true" :inactive-value="false"
                        :loading="scope.row.visibilityLoading" @change="handleVisibilityChange(scope.row)" />
                </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right" align="center">
                <template #default="scope">
                    <el-button size="small" @click="handleDetail(scope.row)">查看</el-button>
                    <el-button size="small" type="danger" @click="handleDelete(scope.row)">删除</el-button>
                </template>
            </el-table-column>
        </el-table>

        <div class="pagination-container">
            <el-pagination
                v-model:current-page="pageUI"
                v-model:page-size="pagination.size"
                :page-sizes="[10, 20, 50, 100]"
                layout="total, sizes, prev, pager, next, jumper"
                :total="pagination.total"
                @size-change="handleSizeChange"
                @current-change="handlePageChange"
            />
        </div>

        <!-- 评价详情弹窗 -->
        <el-dialog title="评价详情" v-model="detailVisible" width="600px">
            <div class="review-detail" v-if="currentReview">
                <div class="review-header">
                    <div class="user-info">
                        <el-avatar :size="40" :src="currentReview.userAvatar" />
                        <div>
                            <div class="username">{{ currentReview.userName }}</div>
                            <div class="review-time">{{ currentReview.createTime }}</div>
                        </div>
                    </div>
                    <div class="rating">
                        <el-rate v-model="currentReview.rating" disabled />
                    </div>
                </div>

                <div class="review-content">{{ currentReview.content }}</div>

                <div class="review-images" v-if="currentReview.images && currentReview.images.length > 0">
                    <el-image v-for="(img, index) in currentReview.images" :key="index" :src="img"
                        :preview-src-list="currentReview.images" fit="cover" class="review-image" />
                </div>

                <div class="review-reply" v-if="currentReview.response">
                    <div class="reply-header">房东回复</div>
                    <div class="reply-content">{{ currentReview.response }}</div>
                    <div class="reply-time">{{ currentReview.responseTime }}</div>
                </div>
            </div>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useCrud } from '@/composables/useCrud'
import { getAdminReviewList, deleteReview, setReviewVisibility, batchSetVisibility, batchDelete } from '@/api/review'
import TableSearch from '@/components/table-search.vue'
import type { FormOptionList } from '@/types/form-option'
import dayjs from 'dayjs'

interface ReviewItem {
    id: number;
    userName: string;
    userAvatar?: string;
    homestayTitle: string;
    rating: number;
    content: string;
    createTime: string;
    response?: string | null;
    responseTime?: string | null;
    isPublic: boolean;
    visibilityLoading?: boolean;
    images?: string[];
}

// 详情弹窗状态
const detailVisible = ref(false)
const currentReview = ref<ReviewItem | null>(null)

// 批量选择状态
const selectedIds = ref<number[]>([])
const selectedRows = ref<ReviewItem[]>([])

// 包装列表 API
const wrappedListApi = async (params: any) => {
    const res: any = await getAdminReviewList({
        page: params.page,
        size: params.size,
        rating: params.rating,
        status: params.status,
        sort: params.sort || 'createTime,desc',
    })
    const content = (res.content || []).map((r: ReviewItem) => ({ ...r, visibilityLoading: false }))
    return {
        content,
        totalElements: res.totalElements || 0,
    }
}

// 筛选配置
const searchOptions: FormOptionList[] = [
    {
        label: '评分',
        prop: 'rating',
        type: 'select',
        opts: [
            { label: '1星', value: 1 },
            { label: '2星', value: 2 },
            { label: '3星', value: 3 },
            { label: '4星', value: 4 },
            { label: '5星', value: 5 },
        ],
    },
    {
        label: '状态',
        prop: 'status',
        type: 'select',
        opts: [
            { label: '已回复', value: 'RESPONDED' },
            { label: '未回复', value: 'UNREPLIED' },
        ],
    },
]

// 使用 useCrud
const {
    loading, tableData, query, pagination,
    getList, handleDelete, handlePageChange, handleSizeChange,
} = useCrud<ReviewItem>({
    listApi: wrappedListApi,
    deleteApi: deleteReview,
    defaultQuery: { rating: null, status: null, sort: 'createTime,desc', page: 0, size: 10 } as any,
    pagination: true,
})

// UI 分页显示（+1）
const pageUI = computed({
    get: () => pagination.page + 1,
    set: (val: number) => { pagination.page = val - 1 },
})

// 搜索
const handleSearch = () => {
    pagination.page = 0
    getList()
}

// 排序变化
const handleSortChange = ({ prop, order }: any) => {
    if (!prop || !order) {
        query.sort = 'createTime,desc'
    } else {
        const direction = order === 'descending' ? 'desc' : 'asc'
        query.sort = `${prop},${direction}`
    }
    pagination.page = 0
    getList()
}

// 可见性切换
const handleVisibilityChange = async (review: ReviewItem) => {
    const newVisibility = review.isPublic
    const actionText = newVisibility ? '显示' : '隐藏'
    review.visibilityLoading = true
    try {
        await setReviewVisibility(review.id, newVisibility)
        ElMessage.success(`评价${actionText}成功`)
    } catch (error: any) {
        ElMessage.error(`设置失败: ${error?.response?.data?.message || error.message}`)
        review.isPublic = !newVisibility
    } finally {
        review.visibilityLoading = false
    }
}

// 详情
const handleDetail = (row: ReviewItem) => {
    currentReview.value = row
    detailVisible.value = true
}

// 格式化
const formatDateTime = (dateString?: string | null) => {
    if (!dateString) return ''
    return dayjs(dateString).format('YYYY-MM-DD HH:mm')
}

// 批量选择变化
const handleSelectionChange = (rows: ReviewItem[]) => {
    selectedRows.value = rows
    selectedIds.value = rows.map(r => r.id)
}

// 批量显示
const handleBatchShow = async () => {
    if (selectedIds.value.length === 0) return
    try {
        await ElMessageBox.confirm(`确定要批量显示选中的 ${selectedIds.value.length} 条评价吗？`, '批量显示', { type: 'warning' })
        await batchSetVisibility(selectedIds.value, true)
        ElMessage.success('批量显示成功')
        selectedIds.value = []
        getList()
    } catch (error: any) {
        if (error !== 'cancel') {
            ElMessage.error(error?.response?.data?.message || '批量显示失败')
        }
    }
}

// 批量隐藏
const handleBatchHide = async () => {
    if (selectedIds.value.length === 0) return
    try {
        await ElMessageBox.confirm(`确定要批量隐藏选中的 ${selectedIds.value.length} 条评价吗？`, '批量隐藏', { type: 'warning' })
        await batchSetVisibility(selectedIds.value, false)
        ElMessage.success('批量隐藏成功')
        selectedIds.value = []
        getList()
    } catch (error: any) {
        if (error !== 'cancel') {
            ElMessage.error(error?.response?.data?.message || '批量隐藏失败')
        }
    }
}

// 批量删除
const handleBatchDelete = async () => {
    if (selectedIds.value.length === 0) return
    try {
        await ElMessageBox.confirm(`确定要批量删除选中的 ${selectedIds.value.length} 条评价吗？删除后不可恢复。`, '批量删除', { type: 'error' })
        await batchDelete(selectedIds.value)
        ElMessage.success('批量删除成功')
        selectedIds.value = []
        getList()
    } catch (error: any) {
        if (error !== 'cancel') {
            ElMessage.error(error?.response?.data?.message || '批量删除失败')
        }
    }
}
</script>

<style scoped lang="scss">
.review-container {
    padding: 20px;

    .batch-toolbar {
        margin-bottom: 15px;
        padding: 10px 15px;
        background-color: #f0f9ff;
        border: 1px solid #b3d8ff;
        border-radius: 4px;
        display: flex;
        align-items: center;
        gap: 10px;

        .batch-info {
            font-size: 14px;
            color: var(--el-color-primary);
            font-weight: 500;
            margin-right: 10px;
        }
    }

    .filter-container {
        background-color: #f5f7fa;
        padding: 15px;
        border-radius: 4px;
        margin-bottom: 20px;
    }

    .user-info {
        display: flex;
        align-items: center;
    }

    .el-pagination {
        margin-top: 20px;
        justify-content: flex-end;
    }

    .pagination-container {
        margin-top: 20px;
        display: flex;
        justify-content: flex-end;
    }

    .review-detail {
        .review-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 15px;
            padding-bottom: 15px;
            border-bottom: 1px solid #eee;

            .user-info {
                display: flex;
                align-items: center;
                gap: 10px;

                .username {
                    font-weight: bold;
                }

                .review-time {
                    font-size: 12px;
                    color: #999;
                }
            }

            .rating .el-rate {
                height: auto;
            }
        }

        .review-content {
            margin-bottom: 15px;
            white-space: pre-wrap;
            line-height: 1.6;
        }

        .review-images {
            display: flex;
            flex-wrap: wrap;
            gap: 10px;
            margin-bottom: 15px;

            .review-image {
                width: 100px;
                height: 100px;
                border-radius: 4px;
                cursor: pointer;
            }
        }

        .review-reply {
            background-color: #f8f8f8;
            border-radius: 4px;
            padding: 15px;
            margin-top: 15px;

            .reply-header {
                font-weight: bold;
                margin-bottom: 8px;
                color: #333;
            }

            .reply-content {
                white-space: pre-wrap;
                line-height: 1.6;
                color: #555;
                margin-bottom: 8px;
            }

            .reply-time {
                font-size: 12px;
                color: #999;
                text-align: right;
            }
        }
    }
}
</style>
