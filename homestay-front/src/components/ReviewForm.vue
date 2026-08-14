<template>
    <el-dialog v-model="dialogVisible" :title="'评价订单: ' + homestayTitle" width="600px" :before-close="handleClose"
        @open="onDialogOpen">
        <el-form ref="reviewFormRef" :model="reviewForm" :rules="rules" label-width="80px">
            <el-form-item label="总体评分" prop="rating">
                <el-rate v-model="reviewForm.rating" :max="5" show-score text-color="#ff9900" />
            </el-form-item>

            <!-- 可选：添加各项细分评分 -->
            <el-collapse v-model="activeCollapse">
                <el-collapse-item title="详细评分 (可选)" name="detailedRatings">
                    <el-row :gutter="20">
                        <el-col :span="12">
                            <el-form-item label="清洁度">
                                <el-rate v-model="reviewForm.cleanlinessRating" :max="5" />
                            </el-form-item>
                        </el-col>
                        <el-col :span="12">
                            <el-form-item label="准确性">
                                <el-rate v-model="reviewForm.accuracyRating" :max="5" />
                            </el-form-item>
                        </el-col>
                        <el-col :span="12">
                            <el-form-item label="沟通">
                                <el-rate v-model="reviewForm.communicationRating" :max="5" />
                            </el-form-item>
                        </el-col>
                        <el-col :span="12">
                            <el-form-item label="位置">
                                <el-rate v-model="reviewForm.locationRating" :max="5" />
                            </el-form-item>
                        </el-col>
                        <el-col :span="12">
                            <el-form-item label="入住">
                                <el-rate v-model="reviewForm.checkInRating" :max="5" />
                            </el-form-item>
                        </el-col>
                        <el-col :span="12">
                            <el-form-item label="性价比">
                                <el-rate v-model="reviewForm.valueRating" :max="5" />
                            </el-form-item>
                        </el-col>
                    </el-row>
                </el-collapse-item>
            </el-collapse>

            <el-form-item label="评论内容" prop="content">
                <el-input v-model="reviewForm.content" type="textarea" :rows="4" placeholder="分享您的入住体验..."
                    maxlength="500" show-word-limit />
            </el-form-item>

            <el-form-item label="上传图片">
                <div class="image-upload-container">
                    <el-upload
                        action="/api/files/upload"
                        list-type="picture-card"
                        :auto-upload="false"
                        :limit="9"
                        :on-change="handleImageChange"
                        :on-remove="handleImageRemove"
                        :file-list="imageFileList"
                        accept="image/*"
                    >
                        <el-icon><Plus /></el-icon>
                    </el-upload>
                    <div class="upload-tip">最多上传9张图片</div>
                </div>
            </el-form-item>
        </el-form>
        <template #footer>
            <span class="dialog-footer">
                <el-button @click="handleClose">取消</el-button>
                <el-button type="primary" @click="submitForm" :loading="loading">提交评价</el-button>
            </span>
        </template>
    </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch, defineProps, defineEmits } from 'vue';
import type { FormInstance, FormRules, UploadFile, UploadRawFile } from 'element-plus';
import { ElMessage } from 'element-plus';
import { Plus } from '@element-plus/icons-vue';
import request from '@/utils/request';

// --- Props --- 
const props = defineProps({
    visible: {
        type: Boolean,
        required: true,
    },
    orderId: {
        type: Number,
        required: true,
    },
    homestayId: {
        type: Number,
        required: true,
    },
    homestayTitle: {
        type: String,
        default: '民宿'
    }
});

// --- Emits --- 
const emit = defineEmits(['update:visible', 'submit']);

// --- Refs and Reactive Variables ---
const dialogVisible = ref(props.visible);
const reviewFormRef = ref<FormInstance>();
const loading = ref(false);
const activeCollapse = ref<string[]>([]); // 控制折叠面板默认不展开
const imageFileList = ref<UploadFile[]>([]);
const imageUrls = ref<string[]>([]);

const initialFormState = () => ({
    orderId: props.orderId,
    homestayId: props.homestayId,
    rating: 0,
    content: '',
    cleanlinessRating: 0,
    accuracyRating: 0,
    communicationRating: 0,
    locationRating: 0,
    checkInRating: 0,
    valueRating: 0,
});

const reviewForm = reactive(initialFormState());

// --- Rules --- 
const rules = reactive<FormRules>({
    rating: [
        { required: true, message: '请给出总体评分', trigger: 'change' },
        { type: 'number', min: 1, message: '评分不能低于1星', trigger: 'change' }
    ],
    content: [
        { required: true, message: '请输入评论内容', trigger: 'blur' },
        { min: 10, message: '评论内容至少需要10个字符', trigger: 'blur' },
    ],
});

// --- Watchers --- 
watch(() => props.visible, (newVal) => {
    dialogVisible.value = newVal;
    if (newVal) {
        // Reset form when dialog opens, using props passed potentially at this moment
        Object.assign(reviewForm, initialFormState());
        reviewForm.orderId = props.orderId; // Ensure IDs are set from props
        reviewForm.homestayId = props.homestayId;
        reviewFormRef.value?.resetFields(); // Also reset validation state
        activeCollapse.value = []; // Ensure collapse is closed
    }
});

watch(() => props.orderId, (newVal) => {
    if (dialogVisible.value) { // Only update if dialog is visible
        reviewForm.orderId = newVal;
    }
});

watch(() => props.homestayId, (newVal) => {
    if (dialogVisible.value) { // Only update if dialog is visible
        reviewForm.homestayId = newVal;
    }
});

// --- Methods --- 
const onDialogOpen = () => {
    // Reset form state explicitly when dialog opens
    Object.assign(reviewForm, initialFormState());
    reviewForm.orderId = props.orderId;
    reviewForm.homestayId = props.homestayId;
    reviewFormRef.value?.resetFields(); // Reset validation
    activeCollapse.value = []; // Ensure collapse is closed
};

const handleClose = () => {
    emit('update:visible', false);
};

// 处理图片选择
const handleImageChange = async (_file: UploadFile, files: UploadFile[]) => {
    imageFileList.value = files;
};

// 处理图片删除
const handleImageRemove = (_file: UploadFile, files: UploadFile[]) => {
    imageFileList.value = files;
};

// 上传图片并获取URL
const uploadImages = async (): Promise<string[]> => {
    const files = imageFileList.value.filter(f => !f.url || f.url.startsWith('blob:'));
    if (files.length === 0) {
        return imageUrls.value;
    }

    const urls: string[] = [];
    for (const file of files) {
        const rawFile = file.raw as UploadRawFile;
        if (!rawFile) continue;

        try {
            const formData = new FormData();
            formData.append('file', rawFile);
            const response = await request({
                url: '/api/files/upload',
                method: 'post',
                data: formData,
                headers: { 'Content-Type': 'multipart/form-data' },
            });
            if (response.data?.url) {
                urls.push(response.data.url);
            }
        } catch (error) {
            console.error('图片上传失败:', error);
        }
    }
    return urls;
};

const submitForm = async () => {
    if (!reviewFormRef.value) return;
    await reviewFormRef.value.validate(async (valid) => {
        if (valid) {
            loading.value = true;
            try {
                // 先上传图片
                const uploadedUrls = await uploadImages();
                // 合并已有图片URL和新上传的图片URL
                const allImages = [...imageUrls.value, ...uploadedUrls];

                console.log('Submitting review form:', reviewForm);
                // Emit the form data including all ratings and images
                emit('submit', { ...reviewForm, images: allImages });
                // Optionally close dialog here or let parent decide after API call
                // handleClose();
            } catch (error) {
                console.error('Error preparing review submission:', error);
            } finally {
                loading.value = false;
            }
        } else {
            console.log('error submit!');
            ElMessage.error('请检查表单必填项');
        }
    });
};

</script>

<style scoped>
.dialog-footer {
    text-align: right;
}

.el-rate {
    /* Align rate component better with label */
    height: var(--el-form-item-height);
    line-height: var(--el-form-item-height);
}

.el-collapse {
    margin-bottom: 18px;
    /* Add some space before content */
    border-top: none;
    border-bottom: none;
}

.el-collapse-item__header {
    border-bottom: none !important;
}

.el-collapse-item__wrap {
    border-bottom: none !important;
}

/* Remove border around collapse content */
:deep(.el-collapse-item__wrap) {
    border-bottom: none;
}

:deep(.el-collapse-item__header) {
    border-bottom: none;
}

.image-upload-container {
    width: 100%;
}

.upload-tip {
    font-size: 12px;
    color: #909399;
    margin-top: 8px;
}

:deep(.el-upload-list--picture-card) {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
}
</style>
