<template>
    <div class="profile-container">
        <div class="profile-header">
            <h1>个人资料</h1>
            <el-button type="primary" @click="handleSubmit" :loading="loading">保存修改</el-button>
        </div>

        <!-- 页面加载状态 -->
        <div v-if="loading && !formData.username" class="page-loading">
            <el-skeleton :rows="8" animated />
        </div>

        <div v-if="!loading || formData.username" class="profile-layout">
            <!-- 左侧列 - 头像和统计信息 -->
            <div class="profile-left-column">
                <!-- 头像卡片 -->
                <el-card class="avatar-card">
                    <div class="avatar-wrapper">
                        <div class="avatar-container">
                            <img :src="formatAvatar(formData.avatar)" class="avatar-image" alt="用户头像"
                                @error="handleAvatarError" />
                            <div class="avatar-overlay">
                                <el-upload class="avatar-uploader" :action="'/api/files/upload'"
                                    with-credentials :data="{ type: 'avatar' }"
                                    :on-success="handleAvatarSuccess" :before-upload="beforeAvatarUpload"
                                    :show-file-list="false">
                                    <el-button class="upload-btn" type="primary" circle>
                                        <el-icon>
                                            <Upload />
                                        </el-icon>
                                    </el-button>
                                </el-upload>
                            </div>
                        </div>
                    </div>

                    <div class="host-info">
                        <h2 class="host-name">{{ formData.nickname || formData.username }}</h2>
                        <div class="host-since">
                            <el-icon>
                                <Calendar />
                            </el-icon>
                            <span>加入时间: {{ hostSince }}</span>
                        </div>
                        <div class="verification-status" v-if="formData.verificationStatus">
                            <el-tag :type="getVerificationStatusType" size="small">
                                {{ getVerificationStatusText }}
                            </el-tag>
                        </div>
                    </div>
                </el-card>

                <!-- 统计卡片 -->
                <el-card class="statistics-card">
                    <template #header>
                        <div class="card-header">
                            <h2>房东数据统计</h2>
                        </div>
                    </template>
                    <div class="stats-grid">
                        <div class="stat-item">
                            <div class="stat-icon homestay-icon">
                                <el-icon>
                                    <House />
                                </el-icon>
                            </div>
                            <div class="stat-content">
                                <div class="stat-value">{{ statistics.homestayCount || 0 }}</div>
                                <div class="stat-label">房源数量</div>
                            </div>
                        </div>
                        <div class="stat-item">
                            <div class="stat-icon order-icon">
                                <el-icon>
                                    <Document />
                                </el-icon>
                            </div>
                            <div class="stat-content">
                                <div class="stat-value">{{ statistics.orderCount || 0 }}</div>
                                <div class="stat-label">订单数量</div>
                            </div>
                        </div>
                        <div class="stat-item">
                            <div class="stat-icon review-icon">
                                <el-icon>
                                    <ChatDotRound />
                                </el-icon>
                            </div>
                            <div class="stat-content">
                                <div class="stat-value">{{ statistics.reviewCount || 0 }}</div>
                                <div class="stat-label">评价数量</div>
                            </div>
                        </div>
                        <div class="stat-item">
                            <div class="stat-icon rating-icon">
                                <el-icon>
                                    <Star />
                                </el-icon>
                            </div>
                            <div class="stat-content">
                                <div class="stat-value">{{ statistics.rating ? statistics.rating.toFixed(1) : '0.0' }}
                                </div>
                                <div class="stat-label">平均评分</div>
                            </div>
                        </div>
                    </div>
                </el-card>
            </div>

            <!-- 右侧列 - 表单和密码 -->
            <div class="profile-right-column">
                <el-tabs type="border-card" class="profile-tabs" v-model="activeTab">
                    <el-tab-pane label="基本信息" name="basic">
                        <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px"
                            class="profile-form">
                            <el-row :gutter="20">
                                <el-col :span="12">
                                    <el-form-item label="用户名" prop="username">
                                        <el-input v-model="formData.username" disabled />
                                    </el-form-item>
                                </el-col>
                                <el-col :span="12">
                                    <el-form-item label="昵称" prop="nickname">
                                        <el-input v-model="formData.nickname" />
                                    </el-form-item>
                                </el-col>
                            </el-row>

                            <el-row :gutter="20">
                                <el-col :span="12">
                                    <el-form-item label="电子邮箱" prop="email">
                                        <el-input v-model="formData.email" />
                                    </el-form-item>
                                </el-col>
                                <el-col :span="12">
                                    <el-form-item label="手机号码" prop="phone">
                                        <el-input v-model="formData.phone" />
                                    </el-form-item>
                                </el-col>
                            </el-row>

                            <el-row :gutter="20">
                                <el-col :span="12">
                                    <el-form-item label="真实姓名" prop="realName">
                                        <el-input v-model="formData.realName" />
                                    </el-form-item>
                                </el-col>
                                <el-col :span="12">
                                    <el-form-item label="性别" prop="gender">
                                        <el-select v-model="formData.gender" placeholder="请选择性别" style="width: 100%">
                                            <el-option label="男" value="MALE" />
                                            <el-option label="女" value="FEMALE" />
                                            <el-option label="保密" value="OTHER" />
                                        </el-select>
                                    </el-form-item>
                                </el-col>
                            </el-row>

                            <el-row :gutter="20">
                                <el-col :span="12">
                                    <el-form-item label="出生日期" prop="birthday">
                                        <el-date-picker v-model="formData.birthday" type="date" placeholder="请选择出生日期"
                                            style="width: 100%" format="YYYY-MM-DD" value-format="YYYY-MM-DD" />
                                    </el-form-item>
                                </el-col>
                            </el-row>
                        </el-form>
                    </el-tab-pane>

                    <el-tab-pane label="房东信息" name="host">
                        <el-form ref="hostFormRef" :model="formData" label-width="100px" class="profile-form">
                            <el-form-item label="个人介绍" prop="introduction">
                                <el-input v-model="formData.introduction" type="textarea" :rows="4"
                                    placeholder="介绍一下自己，让房客更了解您..." />
                                <div class="form-tip">优质的自我介绍能提高客人的信任感，增加预订率</div>
                            </el-form-item>

                            <el-row :gutter="20">
                                <el-col :span="12">
                                    <el-form-item label="职业" prop="occupation">
                                        <el-input v-model="formData.occupation" placeholder="您的职业" />
                                    </el-form-item>
                                </el-col>
                                <el-col :span="12">
                                    <el-form-item label="语言能力" prop="languages">
                                        <el-select v-model="formData.languages" multiple placeholder="您会说的语言"
                                            style="width: 100%">
                                            <el-option label="中文" value="CHINESE" />
                                            <el-option label="英语" value="ENGLISH" />
                                            <el-option label="日语" value="JAPANESE" />
                                            <el-option label="韩语" value="KOREAN" />
                                            <el-option label="法语" value="FRENCH" />
                                            <el-option label="德语" value="GERMAN" />
                                            <el-option label="西班牙语" value="SPANISH" />
                                            <el-option label="俄语" value="RUSSIAN" />
                                            <el-option label="阿拉伯语" value="ARABIC" />
                                        </el-select>
                                    </el-form-item>
                                </el-col>
                            </el-row>

                            <el-form-item label="接待伙伴" prop="companions">
                                <div class="companions-container">
                                    <div class="companions-list">
                                        <div v-for="(companion, index) in formData.companions" :key="index"
                                            class="companion-card">
                                            <div class="companion-header">
                                                <span class="companion-title">伙伴 {{ index + 1 }}</span>
                                                <el-button type="danger" circle size="small"
                                                    @click="removeCompanion(index)">
                                                    <el-icon>
                                                        <Delete />
                                                    </el-icon>
                                                </el-button>
                                            </div>
                                            <div class="companion-content">
                                                <el-input v-model="companion.name" placeholder="伙伴姓名" />
                                            </div>
                                        </div>
                                    </div>
                                    <el-button type="primary" plain @click="addCompanion" class="add-companion-btn">
                                        <el-icon>
                                            <Plus />
                                        </el-icon> 添加接待伙伴
                                    </el-button>
                                </div>
                            </el-form-item>
                        </el-form>
                    </el-tab-pane>

                    <el-tab-pane label="身份认证" name="identity">
                        <!-- 认证状态概览 -->
                        <div class="verification-status-overview">
                            <el-alert :title="getVerificationAlertTitle" :type="getVerificationAlertType"
                                :description="getVerificationAlertDescription" :closable="false" show-icon />
                        </div>

                        <!-- 已认证用户显示的内容 -->
                        <div v-if="formData.verificationStatus === 'VERIFIED'" class="verified-user-content">
                            <el-result icon="success" title="身份认证已通过" sub-title="您的身份信息已验证，可以正常使用所有功能">
                                <template #extra>
                                    <div class="verified-info">
                                        <el-descriptions title="认证信息" :column="2" border>
                                            <el-descriptions-item label="认证状态">
                                                <el-tag type="success">已认证</el-tag>
                                            </el-descriptions-item>
                                            <el-descriptions-item label="身份证号">
                                                {{ maskedIdCard }}
                                            </el-descriptions-item>
                                            <el-descriptions-item label="认证时间">
                                                {{ hostSince || '未知' }}
                                            </el-descriptions-item>
                                            <el-descriptions-item label="有效期">
                                                永久有效
                                            </el-descriptions-item>
                                        </el-descriptions>
                                    </div>
                                </template>
                            </el-result>

                            <div class="verified-actions">
                                <el-button type="info" plain @click="showVerifiedImagePreview">
                                    <el-icon>
                                        <View />
                                    </el-icon>
                                    查看认证资料
                                </el-button>
                                <el-button type="warning" plain @click="showContactSupport">
                                    <el-icon>
                                        <Service />
                                    </el-icon>
                                    联系客服
                                </el-button>
                            </div>
                        </div>

                        <!-- 未认证/待审核/被拒绝用户显示的表单 -->
                        <div v-else class="unverified-user-content">
                            <el-form ref="verifyFormRef" :model="verifyForm" :rules="verifyRules" label-width="120px"
                                class="profile-form">
                                <el-form-item label="身份证号码" prop="idCard">
                                    <el-input v-model="verifyForm.idCard" placeholder="请输入您的身份证号码"
                                        :disabled="formData.verificationStatus === 'VERIFIED'">
                                    </el-input>
                                    <div v-if="verifyForm.idCard && formData.verificationStatus" class="id-card-masked">
                                        <el-tag type="info">您的身份证号: {{ maskedIdCard }}</el-tag>
                                    </div>
                                </el-form-item>

                                <el-form-item label="身份证正面照片" prop="idCardFront">
                                    <el-upload :http-request="handleCustomUpload('idCardFront')"
                                        :file-list="idCardFrontFileList" list-type="picture-card" :limit="1"
                                        :disabled="formData.verificationStatus === 'VERIFIED'">
                                        <el-icon v-if="formData.verificationStatus !== 'VERIFIED'">
                                            <Plus />
                                        </el-icon>
                                        <template #tip>
                                            <div class="upload-tip">请上传清晰的身份证人像面照片</div>
                                        </template>
                                    </el-upload>
                                    <!-- 预览已上传图片 -->
                                    <div v-if="verifyForm.idCardFront" class="uploaded-preview privacy-protected">
                                        <div class="image-blur-container">
                                            <el-image :src="formatAvatar(verifyForm.idCardFront)" fit="cover"
                                                class="blurred-image"
                                                @error="(e: Event) => handleImageError(e, '身份证正面')" />
                                            <div class="privacy-overlay">
                                                <el-button type="primary" size="small" @click="previewIdCard('front')">
                                                    点击查看
                                                </el-button>
                                                <div class="privacy-text">为保护您的隐私，图片已模糊处理</div>
                                            </div>
                                        </div>
                                    </div>
                                </el-form-item>

                                <el-form-item label="身份证背面照片" prop="idCardBack">
                                    <el-upload :http-request="handleCustomUpload('idCardBack')"
                                        :file-list="idCardBackFileList" list-type="picture-card" :limit="1"
                                        :disabled="formData.verificationStatus === 'VERIFIED'">
                                        <el-icon v-if="formData.verificationStatus !== 'VERIFIED'">
                                            <Plus />
                                        </el-icon>
                                        <template #tip>
                                            <div class="upload-tip">请上传清晰的身份证国徽面照片</div>
                                        </template>
                                    </el-upload>
                                    <!-- 预览已上传图片 -->
                                    <div v-if="verifyForm.idCardBack" class="uploaded-preview privacy-protected">
                                        <div class="image-blur-container">
                                            <el-image :src="formatAvatar(verifyForm.idCardBack)" fit="cover"
                                                class="blurred-image"
                                                @error="(e: Event) => handleImageError(e, '身份证背面')" />
                                            <div class="privacy-overlay">
                                                <el-button type="primary" size="small" @click="previewIdCard('back')">
                                                    点击查看
                                                </el-button>
                                                <div class="privacy-text">为保护您的隐私，图片已模糊处理</div>
                                            </div>
                                        </div>
                                    </div>
                                </el-form-item>

                                <div class="id-verify-note">
                                    <el-alert title="您的信息安全将受到严格保护" type="info"
                                        description="您上传的身份证信息将被加密存储，仅用于身份验证，平台不会将您的身份信息透露给任何第三方。您的照片在页面上已进行模糊处理，只有您点击查看时才会显示原图。"
                                        :closable="false" show-icon />
                                    <div class="privacy-tips">
                                        <h4>身份信息保护提示:</h4>
                                        <ul>
                                            <li>您的身份证号码仅会显示前6位和后4位，中间8位以*号替代</li>
                                            <li>身份证照片采用模糊处理技术，保护您的个人隐私</li>
                                            <li>查看原图时会有二次确认，防止他人窥视</li>
                                            <li>我们采用高强度加密技术存储您的身份信息</li>
                                            <li>身份认证通过后，您将无法再编辑身份证信息，以确保安全</li>
                                        </ul>
                                    </div>
                                </div>

                                <el-form-item v-if="formData.verificationStatus !== 'VERIFIED'">
                                    <el-button type="primary" @click="submitVerification" :loading="verifyLoading">
                                        {{ getVerificationButtonText }}
                                    </el-button>
                                </el-form-item>
                            </el-form>
                        </div>
                    </el-tab-pane>

                    <el-tab-pane label="密码设置" name="password">
                        <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="100px">
                            <el-form-item label="当前密码" prop="oldPassword">
                                <el-input v-model="passwordForm.oldPassword" type="password" show-password />
                            </el-form-item>

                            <el-row :gutter="20">
                                <el-col :span="12">
                                    <el-form-item label="新密码" prop="newPassword">
                                        <el-input v-model="passwordForm.newPassword" type="password" show-password />
                                    </el-form-item>
                                </el-col>
                                <el-col :span="12">
                                    <el-form-item label="确认新密码" prop="confirmPassword">
                                        <el-input v-model="passwordForm.confirmPassword" type="password"
                                            show-password />
                                    </el-form-item>
                                </el-col>
                            </el-row>

                            <el-form-item>
                                <el-button type="primary" @click="handleChangePassword" :loading="passwordLoading">
                                    修改密码
                                </el-button>
                            </el-form-item>
                        </el-form>
                    </el-tab-pane>
                </el-tabs>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import {  } from 'vue-router'
import { ElMessage, ElMessageBox, FormInstance, FormRules } from 'element-plus'
import { Upload, House, Calendar, Document, ChatDotRound, Star, Delete, Plus, View, Service } from '@element-plus/icons-vue'
import { getHostProfile, updateHostProfile } from '@/api/host'
import { useUserStore } from '@/stores/user'
import { getAvatarUrl } from '@/utils/image'
import { useIdCardUpload } from '@/composables/useIdCardUpload'
import { useAvatarUpload } from '@/composables/useAvatarUpload'

const userStore = useUserStore()
const formRef = ref<FormInstance>()
const hostFormRef = ref<FormInstance>()
const verifyFormRef = ref<FormInstance>()
const passwordFormRef = ref<FormInstance>()
const loading = ref(false)
const passwordLoading = ref(false)
const verifyLoading = ref(false)
const hostSince = ref('')
const activeTab = ref('basic')

// 获取API服务器基础URL

// 统计数据
const statistics = reactive({
    homestayCount: 0,
    orderCount: 0,
    reviewCount: 0,
    rating: 0
})

interface Companion {
    name: string;
    avatar?: string;
}

// 表单数据
const formData = reactive({
    username: '',
    nickname: '',
    email: '',
    phone: '',
    realName: '',
    idCard: '',
    gender: '',
    avatar: '',
    birthday: '',
    introduction: '',
    occupation: '',
    languages: [] as string[],
    companions: [] as Companion[],
    verificationStatus: ''
})

// 验证信息表单
const verifyForm = reactive({
    idCard: '',
    idCardFront: '',
    idCardBack: '',
})

// 上传文件列表
const idCardFrontFileList = ref<any[]>([]);
const idCardBackFileList = ref<any[]>([]);

// 身份证照片上传逻辑
const { handleCustomUpload } = useIdCardUpload(
    verifyForm,
    idCardFrontFileList,
    idCardBackFileList
);

// 验证身份是否需要补录
const needVerification = computed(() => {
    return !formData.verificationStatus || formData.verificationStatus === 'UNVERIFIED';
})

// 获取验证状态文本
const getVerificationStatusText = computed(() => {
    switch (formData.verificationStatus) {
        case 'VERIFIED':
            return '已认证';
        case 'PENDING':
            return '审核中';
        case 'REJECTED':
            return '认证失败';
        default:
            return '未认证';
    }
})

// 获取验证状态类型
const getVerificationStatusType = computed(() => {
    switch (formData.verificationStatus) {
        case 'VERIFIED':
            return 'success';
        case 'PENDING':
            return 'warning';
        case 'REJECTED':
            return 'danger';
        default:
            return 'info';
    }
})

// 获取验证按钮文本
const getVerificationButtonText = computed(() => {
    switch (formData.verificationStatus) {
        case 'PENDING':
            return '更新认证信息';
        case 'REJECTED':
            return '重新提交认证';
        default:
            return '提交认证';
    }
})

// 获取验证状态提醒标题
const getVerificationAlertTitle = computed(() => {
    switch (formData.verificationStatus) {
        case 'VERIFIED':
            return '身份认证已通过';
        case 'PENDING':
            return '身份认证审核中';
        case 'REJECTED':
            return '身份认证被拒绝';
        default:
            return '请完成身份认证';
    }
})

// 获取验证状态提醒类型
const getVerificationAlertType = computed(() => {
    switch (formData.verificationStatus) {
        case 'VERIFIED':
            return 'success';
        case 'PENDING':
            return 'warning';
        case 'REJECTED':
            return 'error';
        default:
            return 'info';
    }
})

// 获取验证状态提醒描述
const getVerificationAlertDescription = computed(() => {
    switch (formData.verificationStatus) {
        case 'VERIFIED':
            return '您的身份信息已通过审核，可以正常使用所有功能';
        case 'PENDING':
            return '您的身份认证信息正在审核中，通常在1-3个工作日内完成';
        case 'REJECTED':
            return '您的身份认证被拒绝，请重新提交正确的身份信息';
        default:
            return '完成身份认证可以增加您的可信度，提高预订率';
    }
})

// 显示脱敏的身份证号
const maskedIdCard = computed(() => {
    const idCard = verifyForm.idCard;
    if (!idCard) return '';
    return idCard.substring(0, 6) + '*'.repeat(8) + idCard.substring(14);
});

// 表单验证规则
const rules = reactive<FormRules>({
    email: [
        { required: true, message: '请输入电子邮箱', trigger: 'blur' },
        { type: 'email', message: '请输入有效的电子邮箱地址', trigger: 'blur' }
    ],
    phone: [
        { required: true, message: '请输入手机号码', trigger: 'blur' },
        { pattern: /^1[3456789]\d{9}$/, message: '请输入有效的手机号码', trigger: 'blur' }
    ],
    realName: [
        { required: true, message: '请输入真实姓名', trigger: 'blur' }
    ],
    nickname: [
        { required: false, message: '请输入昵称', trigger: 'blur' }
    ],
    gender: [
        { required: true, message: '请选择性别', trigger: 'change' }
    ]
})

// 身份验证规则
const verifyRules = {
    idCard: [
        { required: true, message: '请输入身份证号码', trigger: 'blur' },
        { pattern: /(^\d{15}$)|(^\d{18}$)|(^\d{17}(\d|X|x)$)/, message: '请输入有效的身份证号码', trigger: 'blur' }
    ],
    idCardFront: [
        { required: true, message: '请上传身份证正面照片', trigger: 'change' }
    ],
    idCardBack: [
        { required: true, message: '请上传身份证背面照片', trigger: 'change' }
    ]
};

// 密码表单数据
const passwordForm = reactive({
    oldPassword: '',
    newPassword: '',
    confirmPassword: ''
})

// 密码表单验证规则
const passwordRules = reactive<FormRules>({
    oldPassword: [
        { required: true, message: '请输入当前密码', trigger: 'blur' }
    ],
    newPassword: [
        { required: true, message: '请输入新密码', trigger: 'blur' },
        { min: 6, message: '密码长度不能少于6个字符', trigger: 'blur' }
    ],
    confirmPassword: [
        { required: true, message: '请确认新密码', trigger: 'blur' },
        {
            validator: (_rule, value, callback) => {
                if (value !== passwordForm.newPassword) {
                    callback(new Error('两次输入的密码不一致'))
                } else {
                    callback()
                }
            },
            trigger: 'blur'
        }
    ]
})

// 改用普通函数替代computed函数
const formatAvatar = (url: string) => {
    if (!url) {
        return 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png';
    }
    // 使用来自utils/image的头像URL处理函数，添加时间戳防缓存
    return getAvatarUrl(url, true);
};

// 自定义处理图片错误函数，避免与import冲突
const handleImageError = (e: Event, _type: string = 'avatar') => {
    const img = e.target as HTMLImageElement;
    console.error(`图片加载错误: ${img.src}`);
    img.src = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png';
}

// 处理头像加载错误
const handleAvatarError = (e: Event) => {
    handleImageError(e, 'avatar');
}

// 头像上传逻辑（解析与上传，副作用通过回调处理）
const { handleAvatarSuccess, beforeAvatarUpload } = useAvatarUpload(formData, (avatarUrl) => {
    if (userStore.userInfo) {
        userStore.userInfo.avatar = avatarUrl;
        localStorage.setItem('homestay_user', JSON.stringify(userStore.userInfo));
    }

    setTimeout(() => {
        const avatarElement = document.querySelector('.avatar-image') as HTMLImageElement;
        if (avatarElement) {
            avatarElement.src = formatAvatar(avatarUrl);
        }
    }, 500);

    setTimeout(() => {
        userStore.fetchUserInfo();
    }, 1000);
});

// 获取房东信息
const fetchHostInfo = async () => {
    loading.value = true;
    try {
        // 使用getHostProfile获取更完整的用户信息
        const profileData = await getHostProfile();
        if (import.meta.env.DEV) {
            console.log('获取到的用户资料:', profileData);
        }

        if (profileData) {
            // 填充基本表单数据
            formData.username = profileData.username || '';
            formData.nickname = profileData.nickname || '';
            formData.email = profileData.email || '';
            formData.phone = profileData.phone || '';
            formData.realName = profileData.realName || '';
            formData.idCard = profileData.idCard || '';
            formData.avatar = profileData.avatar || '';
            formData.birthday = profileData.birthday || '';
            formData.introduction = profileData.introduction || '';
            formData.occupation = profileData.occupation || '';
            formData.gender = profileData.gender || '';
            formData.languages = profileData.languages || [];
            formData.companions = profileData.companions || [];
            formData.verificationStatus = profileData.verificationStatus || 'UNVERIFIED';

            // 填充验证表单数据
            verifyForm.idCard = profileData.idCard || '';
            verifyForm.idCardFront = profileData.idCardFront || '';
            verifyForm.idCardBack = profileData.idCardBack || '';

            // 如果有身份证照片，填充文件列表
            if (profileData.idCardFront) {
                idCardFrontFileList.value = [{ url: profileData.idCardFront, name: '身份证正面' }];
            }

            if (profileData.idCardBack) {
                idCardBackFileList.value = [{ url: profileData.idCardBack, name: '身份证背面' }];
            }

            hostSince.value = profileData.hostSince || '未知';

            // 统计数据
            statistics.homestayCount = profileData.homestayCount || 0;
            statistics.orderCount = profileData.orderCount || 0;
            statistics.reviewCount = profileData.reviewCount || 0;
            statistics.rating = profileData.rating || 0;

            // 根据身份认证状态给出相应提示
            if (needVerification.value) {
                ElMessage.warning('您尚未完成身份认证，请完善身份认证信息');
                activeTab.value = 'identity';
            } else if (formData.verificationStatus === 'PENDING') {
                ElMessage.info('您的身份认证正在审核中');
            } else if (formData.verificationStatus === 'REJECTED') {
                ElMessage.warning('您的身份认证被拒绝，请重新提交');
                activeTab.value = 'identity';
            }
        }
    } catch (error) {
        console.error('获取房东信息失败:', error);
    } finally {
        loading.value = false;
    }
}

// 提交表单
const handleSubmit = async () => {
    if (!formRef.value) return;

    await formRef.value.validate(async (valid: boolean): Promise<void> => {
        if (valid) {
            loading.value = true;
            try {
                // 数据预处理和验证
                const updateData = {
                    nickname: formData.nickname || '',
                    email: formData.email.trim(),
                    phone: formData.phone.trim(),
                    realName: formData.realName.trim(),
                    gender: formData.gender,
                    birthday: formData.birthday || null,
                    introduction: formData.introduction?.trim() || '',
                    occupation: formData.occupation?.trim() || '',
                    languages: formData.languages || [],
                    companions: formData.companions.filter(c => c.name.trim()) || []
                };

                if (import.meta.env.DEV) {
                    console.log('提交的个人资料数据:', updateData);
                }

                // 使用新的API提交更新
                await updateHostProfile(updateData);
                ElMessage.success('资料更新成功');

                // 更新用户存储中的用户信息
                if (userStore.userInfo) {
                    const userInfo = userStore.userInfo as Record<string, any>;
                    userInfo.nickname = formData.nickname;
                    userInfo.avatar = formData.avatar;
                }
            } catch (error: any) {
                console.error('更新资料失败:', error);
            } finally {
                loading.value = false;
            }
        } else {
            ElMessage.warning('请正确填写表单');
        }
    });
}

// 提交身份验证
const submitVerification = async () => {
    if (!verifyFormRef.value) return;

    // 提交前检查身份证照片是否已上传
    console.log('提交前检查表单数据:', {
        idCard: verifyForm.idCard,
        idCardFront: verifyForm.idCardFront,
        idCardBack: verifyForm.idCardBack,
        前端文件列表_正面: idCardFrontFileList.value,
        前端文件列表_背面: idCardBackFileList.value
    });

    await verifyFormRef.value.validate(async (valid: boolean): Promise<void> => {
        console.log('表单验证结果:', valid);

        if (valid) {
            verifyLoading.value = true;
            try {
                // 再次确认身份证照片是否存在
                if (!verifyForm.idCardFront || !verifyForm.idCardBack) {
                    console.error('身份证照片缺失:', {
                        idCardFront: verifyForm.idCardFront,
                        idCardBack: verifyForm.idCardBack
                    });
                    throw new Error('请确保已成功上传身份证正反面照片');
                }

                // 提交身份验证信息
                const updateData = {
                    idCard: verifyForm.idCard,
                    idCardFront: verifyForm.idCardFront,
                    idCardBack: verifyForm.idCardBack,
                    verificationStatus: 'PENDING' // 设置为待审核状态
                };

                console.log('准备提交的数据:', updateData);

                await updateHostProfile(updateData);
                ElMessage.success('身份信息已提交，等待审核');
                // 更新本地状态
                formData.verificationStatus = 'PENDING';
                formData.idCard = verifyForm.idCard;
            } catch (error) {
                console.error('提交身份验证失败:', error);
            } finally {
                verifyLoading.value = false;
            }
        } else {
            ElMessage.warning('请正确填写身份信息');
        }
    });
}

// 修改密码
const handleChangePassword = async () => {
    if (!passwordFormRef.value) return;

    await passwordFormRef.value.validate(async (valid: boolean): Promise<void> => {
        if (valid) {
            passwordLoading.value = true;
            try {
                await userStore.changePassword({
                    oldPassword: passwordForm.oldPassword,
                    newPassword: passwordForm.newPassword
                });
                ElMessage.success('密码修改成功');
                // 清空密码表单
                passwordForm.oldPassword = '';
                passwordForm.newPassword = '';
                passwordForm.confirmPassword = '';
            } catch (error) {
                console.error('修改密码失败:', error);
            } finally {
                passwordLoading.value = false;
            }
        } else {
            ElMessage.warning('请正确填写表单');
        }
    });
}

// 添加接待伙伴
const addCompanion = () => {
    formData.companions.push({ name: '' });
}

// 移除接待伙伴
const removeCompanion = (index: number) => {
    formData.companions.splice(index, 1);
}

// 头像上传成功处理


// 修改预览身份证照片的函数，添加加载状态和水印
const previewIdCard = (type: 'front' | 'back') => {
    const url = type === 'front' ? formatAvatar(verifyForm.idCardFront) : formatAvatar(verifyForm.idCardBack);
    if (!url) return;

    ElMessageBox.confirm(
        '确定要查看身份证照片吗？请确保您处于私密环境，周围没有其他人。',
        '隐私提醒',
        {
            confirmButtonText: '继续查看',
            cancelButtonText: '取消',
            type: 'warning'
        }
    ).then(() => {
        // 创建一个全屏遮罩并显示图片
        const imgPreview = document.createElement('div');
        imgPreview.className = 'id-card-fullscreen-preview';

        // 添加水印和加载状态
        imgPreview.innerHTML = `
            <div class="preview-container">
                <div class="preview-loading">
                    <div class="loading-spinner"></div>
                    <div class="loading-text">加载中...</div>
                </div>
                <img src="${url}" alt="身份证${type === 'front' ? '正面' : '背面'}" class="preview-image" />
                <div class="preview-watermark">
                    <div class="watermark-text">仅用于身份验证 · ${new Date().toLocaleDateString()} · ${userStore.userInfo?.username || '用户'}</div>
                </div>
                <div class="close-preview">点击任意位置关闭</div>
            </div>
        `;
        document.body.appendChild(imgPreview);

        // 监听图片加载完成
        const previewImage = imgPreview.querySelector('.preview-image') as HTMLImageElement;
        const loadingElement = imgPreview.querySelector('.preview-loading') as HTMLDivElement;

        if (previewImage) {
            previewImage.style.display = 'none'; // 先隐藏图片

            previewImage.onload = () => {
                // 图片加载完成，显示图片，隐藏加载状态
                if (loadingElement) loadingElement.style.display = 'none';
                previewImage.style.display = 'block';
            };

            previewImage.onerror = () => {
                // 图片加载失败
                if (loadingElement) {
                    loadingElement.innerHTML = '<div class="loading-error">图片加载失败</div>';
                }
            };
        }

        // 点击关闭预览
        imgPreview.addEventListener('click', () => {
            document.body.removeChild(imgPreview);
        });
    }).catch(() => {
        // 用户取消查看
    });
};

// 已认证用户查看认证资料
const showVerifiedImagePreview = () => {
    ElMessageBox.confirm(
        '您确定要查看您的身份认证资料吗？请确保您处于私密环境。',
        '查看认证资料',
        {
            confirmButtonText: '确定查看',
            cancelButtonText: '取消',
            type: 'warning'
        }
    ).then(() => {
        // 显示认证资料对话框
        ElMessageBox.alert(
            `认证状态：已通过\n身份证号：${maskedIdCard.value}\n认证时间：${hostSince.value}`,
            '认证资料信息',
            {
                confirmButtonText: '知道了',
                type: 'success'
            }
        );
    }).catch(() => {
        // 用户取消
    });
};

// 联系客服
const showContactSupport = () => {
    ElMessageBox.alert(
        '如果您需要修改身份认证信息或遇到其他问题，请联系客服：\n\n客服电话：400-123-4567\n客服邮箱：support@homestay.com\n工作时间：9:00-18:00（工作日）',
        '联系客服',
        {
            confirmButtonText: '知道了',
            type: 'info'
        }
    );
};

// 初始化
onMounted(async () => {
    if (import.meta.env.DEV) {
        console.log('房东个人资料页面初始化...');
    }

    try {
        // 获取房东信息
        await fetchHostInfo();
        if (import.meta.env.DEV) {
            console.log('初始化完成: 用户信息已加载', formData);
        }
    } catch (error) {
        console.error('初始化失败:', error);
    }
});
</script>

<style scoped>
.profile-container {
    padding: 24px;
}

.profile-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 24px;
}

.profile-layout {
    display: grid;
    grid-template-columns: 1fr 3fr;
    gap: 24px;
}

.profile-left-column {
    display: flex;
    flex-direction: column;
    gap: 24px;
}

.profile-right-column {
    background-color: white;
    border-radius: 4px;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.avatar-container {
    text-align: center;
    margin-bottom: 24px;
}

/* 头像相关样式 */
.avatar-card {
    text-align: center;
    padding: 20px;
}

.avatar-wrapper {
    position: relative;
    width: 100px;
    height: 100px;
    margin: 0 auto 16px;
}

.avatar-container {
    width: 100%;
    height: 100%;
    border-radius: 50%;
    overflow: hidden;
    border: 3px solid #f2f6fc;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
    position: relative;
}

.avatar-image {
    width: 100%;
    height: 100%;
    object-fit: cover;
}

.avatar-overlay {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: rgba(0, 0, 0, 0);
    display: flex;
    align-items: center;
    justify-content: center;
    opacity: 0;
    transition: all 0.3s;
    border-radius: 50%;
}

.avatar-container:hover .avatar-overlay {
    background: rgba(0, 0, 0, 0.3);
    opacity: 1;
}

.upload-btn {
    font-size: 14px;
    padding: 4px;
}

.host-info {
    text-align: center;
    margin-bottom: 20px;
}

.host-name {
    font-size: 18px;
    font-weight: 600;
    margin: 10px 0 5px;
}

.host-since {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 5px;
    font-size: 13px;
    color: #606266;
    margin-bottom: 8px;
}

.verification-alert {
    margin-bottom: 24px;
}

.verification-status {
    display: inline-block;
    padding: 4px 8px;
    border-radius: 4px;
    font-size: 14px;
    margin-top: 8px;
}

.id-verify-note {
    margin: 20px 0;
    border-radius: 4px;
    background-color: #f8f9fa;
    padding: 15px;
    border-left: 4px solid #409EFF;
}

.privacy-tips {
    margin-top: 15px;
    font-size: 14px;
    color: #606266;
}

.privacy-tips h4 {
    margin-bottom: 10px;
    color: #409EFF;
}

.privacy-tips ul {
    padding-left: 20px;
}

.privacy-tips li {
    margin-bottom: 5px;
    line-height: 1.5;
}

.id-card-masked {
    margin-top: 5px;
    font-size: 14px;
}

.privacy-protected {
    position: relative;
    width: 200px;
}

.image-blur-container {
    position: relative;
    overflow: hidden;
    border-radius: 4px;
    width: 100%;
}

.blurred-image {
    width: 100%;
    height: 120px;
    filter: blur(10px);
    transition: filter 0.3s;
}

.privacy-overlay {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    background-color: rgba(0, 0, 0, 0.4);
    color: white;
    z-index: 1;
}

.privacy-text {
    margin-top: 8px;
    font-size: 12px;
}

.id-card-fullscreen-preview {
    position: fixed;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background-color: rgba(0, 0, 0, 0.9);
    z-index: 9999;
    display: flex;
    justify-content: center;
    align-items: center;
}

.preview-container {
    position: relative;
    max-width: 80%;
    max-height: 80%;
    display: flex;
    flex-direction: column;
    align-items: center;
}

.preview-container img {
    max-width: 100%;
    max-height: 80vh;
    object-fit: contain;
}

.close-preview {
    color: white;
    margin-top: 20px;
    font-size: 14px;
    cursor: pointer;
}

.upload-tip {
    font-size: 12px;
    color: #909399;
    margin-top: 5px;
}

.form-tip {
    font-size: 13px;
    color: #409EFF;
    margin-top: 8px;
    font-style: italic;
}

/* 伙伴列表样式 */
.companions-container {
    width: 100%;
}

.companions-list {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 16px;
    margin-bottom: 16px;
}

.companion-card {
    border: 1px solid #ebeef5;
    border-radius: 4px;
    padding: 12px;
    background-color: #f8f9fa;
}

.companion-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
}

.companion-title {
    font-weight: 600;
    font-size: 14px;
    color: #606266;
}

.add-companion-btn {
    width: 100%;
}

@media (max-width: 992px) {
    .profile-layout {
        grid-template-columns: 1fr;
    }

    .companions-list {
        grid-template-columns: 1fr;
    }
}

.preview-image {
    max-width: 100%;
    max-height: 80vh;
    object-fit: contain;
    position: relative;
    border: 2px solid white;
    box-shadow: 0 0 20px rgba(0, 0, 0, 0.5);
}

.preview-loading {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    background-color: rgba(0, 0, 0, 0.2);
    z-index: 2;
}

.loading-spinner {
    width: 40px;
    height: 40px;
    border: 4px solid rgba(255, 255, 255, 0.3);
    border-radius: 50%;
    border-top-color: white;
    animation: spin 1s ease-in-out infinite;
}

@keyframes spin {
    to {
        transform: rotate(360deg);
    }
}

.loading-text {
    color: white;
    margin-top: 10px;
    font-size: 14px;
}

.loading-error {
    color: #f56c6c;
    font-size: 16px;
}

.preview-watermark {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    display: flex;
    justify-content: center;
    align-items: center;
    pointer-events: none;
    z-index: 1;
}

.watermark-text {
    color: rgba(255, 255, 255, 0.5);
    font-size: 16px;
    transform: rotate(-30deg);
    white-space: nowrap;
    text-shadow: 1px 1px 2px rgba(0, 0, 0, 0.5);
}

/* 统计卡片样式 */
.statistics-card {
    background-color: #fff;
}

.card-header h2 {
    font-size: 16px;
    font-weight: 600;
    margin: 0;
    color: #303133;
}

.stats-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 16px;
    margin-top: 16px;
}

.stat-item {
    display: flex;
    align-items: center;
    padding: 12px;
    border-radius: 8px;
    background-color: #f8f9fa;
    transition: all 0.3s;
}

.stat-item:hover {
    transform: translateY(-3px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.stat-icon {
    width: 36px;
    height: 36px;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-right: 12px;
    color: white;
    font-size: 16px;
}

.homestay-icon {
    background-color: #67c23a;
}

.order-icon {
    background-color: #409eff;
}

.review-icon {
    background-color: #e6a23c;
}

.rating-icon {
    background-color: #f56c6c;
}

.stat-content {
    flex: 1;
}

.stat-value {
    font-size: 18px;
    font-weight: 600;
    line-height: 1.2;
    color: #303133;
}

.stat-label {
    font-size: 12px;
    color: #909399;
}

/* 表单样式 */
.profile-form {
    padding: 16px 8px;
    max-width: 800px;
}

/* 身份认证页面样式 */
.verification-status-overview {
    margin-bottom: 24px;
}

.verified-user-content {
    text-align: center;
    padding: 40px 20px;
}

.verified-info {
    margin-top: 24px;
    text-align: left;
}

.verified-actions {
    margin-top: 32px;
    display: flex;
    justify-content: center;
    gap: 16px;
}

.unverified-user-content {
    padding: 0;
}

@media (max-width: 768px) {
    .verified-actions {
        flex-direction: column;
        align-items: center;
    }
}
</style>