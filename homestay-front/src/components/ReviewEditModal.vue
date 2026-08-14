<template>
  <el-dialog
    :model-value="visible"
    title="修改评价"
    width="600px"
    @close="handleClose"
    :close-on-click-modal="false"
  >
    <el-form
      ref="editFormRef"
      :model="formData"
      :rules="formRules"
      label-width="80px"
      v-loading="loading"
    >
      <el-form-item label="总体评分" prop="rating">
        <el-rate v-model="formData.rating" :colors="['#99A9BF', '#F7BA2A', '#FF9900']" show-score />
      </el-form-item>

      <!-- 详细评分 -->
      <el-collapse v-model="activeCollapse">
        <el-collapse-item title="详细评分 (可选)" name="detailedRatings">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="清洁度">
                <el-rate v-model="formData.cleanlinessRating" :max="5" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="准确性">
                <el-rate v-model="formData.accuracyRating" :max="5" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="沟通">
                <el-rate v-model="formData.communicationRating" :max="5" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="位置">
                <el-rate v-model="formData.locationRating" :max="5" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="入住">
                <el-rate v-model="formData.checkInRating" :max="5" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="性价比">
                <el-rate v-model="formData.valueRating" :max="5" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-collapse-item>
      </el-collapse>

      <el-form-item label="评价内容" prop="content">
        <el-input
          type="textarea"
          v-model="formData.content"
          :rows="5"
          maxlength="1000"
          show-word-limit
          placeholder="请输入评价内容"
        />
      </el-form-item>

      <!-- 图片管理 -->
      <el-form-item label="评价图片">
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
          <div class="upload-tip">最多上传9张图片，支持新增和删除</div>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          确认修改
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch, nextTick } from 'vue';
import type { FormInstance, FormRules, UploadFile, UploadRawFile } from 'element-plus';
import { ElMessage } from 'element-plus';
import { Plus } from '@element-plus/icons-vue';
import { updateReview, updateReviewImages } from '@/api/review';
import request from '@/utils/request';

// 定义评价数据接口
interface ReviewData {
  id: number;
  rating: number;
  content: string;
  cleanlinessRating?: number;
  accuracyRating?: number;
  communicationRating?: number;
  locationRating?: number;
  checkInRating?: number;
  valueRating?: number;
  images?: string[];
}

// 定义 props
const props = defineProps<{
  visible: boolean;
  reviewData: ReviewData | null;
}>();

// 定义 emits
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void;
  (e: 'submitted', updatedReview: ReviewData): void;
}>();

const editFormRef = ref<FormInstance>();
const loading = ref(false);
const submitting = ref(false);
const activeCollapse = ref<string[]>([]);
const imageFileList = ref<UploadFile[]>([]);

// 表单数据
const formData = reactive<ReviewData>({
  id: 0,
  rating: 0,
  content: '',
  cleanlinessRating: 0,
  accuracyRating: 0,
  communicationRating: 0,
  locationRating: 0,
  checkInRating: 0,
  valueRating: 0,
});

// 表单验证规则
const formRules = reactive<FormRules>({
  rating: [{ required: true, message: '请选择评分', trigger: 'change' }],
  content: [{ required: true, message: '请输入评价内容', trigger: 'blur' }],
});

// 监听 props.reviewData 变化，填充表单
watch(() => props.reviewData, (newVal) => {
  if (newVal && props.visible) {
    nextTick(() => {
      editFormRef.value?.resetFields();
      formData.id = newVal.id;
      formData.rating = newVal.rating;
      formData.content = newVal.content;
      formData.cleanlinessRating = newVal.cleanlinessRating || 0;
      formData.accuracyRating = newVal.accuracyRating || 0;
      formData.communicationRating = newVal.communicationRating || 0;
      formData.locationRating = newVal.locationRating || 0;
      formData.checkInRating = newVal.checkInRating || 0;
      formData.valueRating = newVal.valueRating || 0;

      // 初始化图片列表
      imageFileList.value = (newVal.images || []).map((url, index) => ({
        name: `image-${index}`,
        url: url,
        uid: Date.now() + index,
      } as UploadFile));
    });
  } else {
    nextTick(() => {
      editFormRef.value?.resetFields();
      formData.id = 0;
      formData.rating = 0;
      formData.content = '';
      formData.cleanlinessRating = 0;
      formData.accuracyRating = 0;
      formData.communicationRating = 0;
      formData.locationRating = 0;
      formData.checkInRating = 0;
      formData.valueRating = 0;
      imageFileList.value = [];
    });
  }
}, { immediate: true, deep: true });

// 处理图片选择
const handleImageChange = (_file: UploadFile, files: UploadFile[]) => {
  imageFileList.value = files;
};

// 处理图片删除
const handleImageRemove = (_file: UploadFile, files: UploadFile[]) => {
  imageFileList.value = files;
};

// 上传图片并获取URL
const uploadImages = async (): Promise<string[]> => {
  const existingUrls = imageFileList.value
    .filter(f => f.url && !f.url.startsWith('blob:'))
    .map(f => f.url!);

  const newFiles = imageFileList.value.filter(f => !f.url || f.url.startsWith('blob:'));
  if (newFiles.length === 0) {
    return existingUrls;
  }

  const urls: string[] = [];
  for (const file of newFiles) {
    const rawFile = file.raw as UploadRawFile;
    if (!rawFile) continue;

    try {
      const formDataUpload = new FormData();
      formDataUpload.append('file', rawFile);
      const response = await request({
        url: '/api/files/upload',
        method: 'post',
        data: formDataUpload,
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      if (response.data?.url) {
        urls.push(response.data.url);
      }
    } catch (error) {
      console.error('图片上传失败:', error);
    }
  }
  return [...existingUrls, ...urls];
};

// 关闭弹窗
const handleClose = () => {
  emit('update:visible', false);
};

// 提交表单
const handleSubmit = async () => {
  if (!editFormRef.value) return;
  await editFormRef.value.validate(async (valid) => {
    if (valid) {
      submitting.value = true;
      try {
        // 1. 更新评价内容（含细分评分）
        // 注意：el-rate 最小值为 1，0 表示未选择；但编辑弹窗中已有评分时保留原值
        const payload: any = {
          rating: formData.rating,
          content: formData.content,
        };
        if (formData.cleanlinessRating && formData.cleanlinessRating > 0) payload.cleanlinessRating = formData.cleanlinessRating;
        if (formData.accuracyRating && formData.accuracyRating > 0) payload.accuracyRating = formData.accuracyRating;
        if (formData.communicationRating && formData.communicationRating > 0) payload.communicationRating = formData.communicationRating;
        if (formData.locationRating && formData.locationRating > 0) payload.locationRating = formData.locationRating;
        if (formData.checkInRating && formData.checkInRating > 0) payload.checkInRating = formData.checkInRating;
        if (formData.valueRating && formData.valueRating > 0) payload.valueRating = formData.valueRating;
        await updateReview(formData.id, payload);

        // 2. 更新评价图片
        const allImageUrls = await uploadImages();
        if (allImageUrls.length < imageFileList.value.length) {
            const failedCount = imageFileList.value.length - allImageUrls.length;
            ElMessage.warning(`图片上传部分失败，成功 ${allImageUrls.length} 张，失败 ${failedCount} 张`);
        }
        await updateReviewImages(formData.id, allImageUrls);

        ElMessage.success('评价修改成功');
        emit('submitted', { ...formData, images: allImageUrls });
        handleClose();
      } catch (error: any) {
        console.error('修改评价失败:', error);
      } finally {
        submitting.value = false;
      }
    } else {
      console.log('表单验证失败');
    }
  });
};
</script>

<style scoped>
.dialog-footer {
  text-align: right;
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

.el-collapse {
  margin-bottom: 18px;
  border-top: none;
  border-bottom: none;
}

:deep(.el-collapse-item__wrap) {
  border-bottom: none;
}

:deep(.el-collapse-item__header) {
  border-bottom: none;
}
</style>
