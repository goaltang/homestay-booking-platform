<template>
    <div class="avatar-upload-wrapper">
        <div class="avatar-container" :class="{ 'hover-enabled': !disabled }">
            <el-avatar :size="size" :src="avatarUrl" @error="handleImageError">
                <img :src="defaultAvatar" />
            </el-avatar>

            <div v-if="!disabled" class="upload-overlay">
                <el-upload class="avatar-uploader"
                    :action="apiBaseUrl ? `${apiBaseUrl}/api/files/upload` : '/api/files/upload'"
                    :headers="uploadHeaders" :data="{ type: 'avatar' }" :on-success="handleAvatarSuccess"
                    :before-upload="beforeAvatarUpload" :on-error="handleAvatarError" :show-file-list="false"
                    :multiple="false" accept="image/*" name="file">
                    <el-button v-if="buttonMode" :size="buttonSize" type="primary">
                        {{ buttonText }}
                    </el-button>
                    <div v-else class="upload-icon">
                        <el-icon>
                            <Upload />
                        </el-icon>
                        <span class="upload-text">更换头像</span>
                    </div>
                </el-upload>
            </div>
        </div>

        <!-- 调试信息（开发环境显示） -->
        <div v-if="showDebug && isDev" class="avatar-debug">
            <small>{{ currentAvatar }}</small>
        </div>
    </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { ElMessage, ElAvatar, ElUpload, ElButton, ElIcon } from 'element-plus';
import type { UploadProps, UploadFile, UploadFiles } from 'element-plus';
import { Upload } from '@element-plus/icons-vue';

interface Props {
    modelValue?: string | null | undefined; // 当前头像URL，允许null和undefined
    size?: number; // 头像大小
    disabled?: boolean; // 是否禁用上传
    buttonMode?: boolean; // 是否使用按钮模式
    buttonText?: string; // 按钮文字
    buttonSize?: 'large' | 'default' | 'small'; // 按钮大小
    showDebug?: boolean; // 是否显示调试信息
    defaultAvatar?: string; // 默认头像
    maxSize?: number; // 最大文件大小(MB)
}

interface Emits {
    (e: 'update:modelValue', value: string): void;
    (e: 'success', fileName: string): void;
    (e: 'error', error: any): void;
}

const props = withDefaults(defineProps<Props>(), {
    size: 100,
    disabled: false,
    buttonMode: true,
    buttonText: '更换头像',
    buttonSize: 'small',
    showDebug: false,
    defaultAvatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=fallback',
    maxSize: 10
});

const emit = defineEmits<Emits>();

// 环境变量
const apiBaseUrl = import.meta.env.VITE_API_URL || '';
const isDev = import.meta.env.DEV;

// 计算属性
const currentAvatar = computed(() => props.modelValue || '');

const avatarUrl = computed(() => {
    const avatar = currentAvatar.value;
    if (!avatar) return props.defaultAvatar;

    // 如果已经是完整URL，直接返回
    if (avatar.startsWith('http')) return avatar;

    // 如果是相对路径，补充API基础URL（如果有的话）
    if (avatar.startsWith('/api/files/')) {
        return apiBaseUrl ? `${apiBaseUrl}${avatar}` : avatar; // 当没有apiBaseUrl时直接使用相对路径（通过代理）
    }

    // 如果只是文件名，构建完整路径
    if (avatar && !avatar.includes('/')) {
        const path = `/api/files/avatar/${avatar}`;
        return apiBaseUrl ? `${apiBaseUrl}${path}` : path; // 当没有apiBaseUrl时直接使用相对路径（通过代理）
    }

    return avatar || props.defaultAvatar;
});

// 上传请求头
const uploadHeaders = computed(() => {
    const token = localStorage.getItem('homestay_token') || localStorage.getItem('token');
    return token ? { Authorization: `Bearer ${token}` } : {};
});

// 头像加载错误处理
const handleImageError = () => {
    console.warn('头像加载失败，使用默认头像');
};

// 上传前验证
const beforeAvatarUpload: UploadProps['beforeUpload'] = (file) => {
    console.log('开始上传验证:', {
        文件名: file.name,
        类型: file.type,
        大小: Math.round(file.size / 1024) + 'KB'
    });

    // 检查文件类型
    const isImage = file.type.startsWith('image/');
    if (!isImage) {
        ElMessage.error('只能上传图片文件!');
        return false;
    }

    // 检查文件大小
    const isLtMaxSize = file.size / 1024 / 1024 < props.maxSize;
    if (!isLtMaxSize) {
        ElMessage.error(`图片大小不能超过${props.maxSize}MB!`);
        return false;
    }

    ElMessage.info({
        message: '正在上传头像，请稍候...',
        duration: 2000
    });

    return true;
};

// 上传成功处理
const handleAvatarSuccess: UploadProps['onSuccess'] = (response: any, _uploadFile: UploadFile, _uploadFiles: UploadFiles) => {
    console.log('头像上传成功:', response);

    try {
        // 统一的响应数据提取逻辑
        let fileName = '';

        if (response?.data?.fileName) {
            fileName = response.data.fileName;
        } else if (response?.fileName) {
            fileName = response.fileName;
        } else if (typeof response === 'string') {
            fileName = response;
        } else if (response?.data?.url) {
            // 从URL中提取文件名
            const urlParts = response.data.url.split('/');
            fileName = urlParts[urlParts.length - 1];
        }

        if (!fileName) {
            throw new Error('服务器未返回有效的文件名');
        }

        console.log('提取的文件名:', fileName);

        // 构建完整的头像URL
        const avatarUrl = `/api/files/avatar/${fileName}`;

        // 更新组件值
        emit('update:modelValue', avatarUrl);
        emit('success', fileName);

        ElMessage.success('头像更新成功');

    } catch (error) {
        console.error('处理头像上传响应失败:', error);
        emit('error', error);
    }
};

// 上传错误处理
const handleAvatarError: UploadProps['onError'] = (error: any) => {
    console.error('头像上传失败:', error);
    emit('error', error);
    ElMessage.error('头像上传失败，请检查网络连接');
};
</script>

<style scoped>
.avatar-upload-wrapper {
    display: flex;
    flex-direction: column;
    align-items: center;
}

.avatar-container {
    position: relative;
    display: inline-block;
}

.avatar-container.hover-enabled:hover .upload-overlay {
    opacity: 1;
}

.upload-overlay {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background-color: rgba(0, 0, 0, 0.5);
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    opacity: 0;
    transition: opacity 0.3s ease;
    cursor: pointer;
}

.upload-icon {
    display: flex;
    flex-direction: column;
    align-items: center;
    color: white;
    font-size: 12px;
}

.upload-icon .el-icon {
    font-size: 20px;
    margin-bottom: 4px;
}

.upload-text {
    font-size: 10px;
    white-space: nowrap;
}

.avatar-uploader {
    width: 100%;
    height: 100%;
}

.avatar-uploader .el-upload {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
}

.avatar-debug {
    margin-top: 8px;
    font-size: 12px;
    color: #909399;
    max-width: 200px;
    word-break: break-all;
    text-align: center;
}

/* 按钮模式下的样式调整 */
.avatar-container:not(.hover-enabled) .upload-overlay,
.avatar-container .upload-overlay:has(.el-button) {
    position: static;
    background: transparent;
    opacity: 1;
    border-radius: 0;
    margin-top: 10px;
}
</style>