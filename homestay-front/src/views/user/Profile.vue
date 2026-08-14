<template>
    <div class="profile-container">
        <el-card class="profile-card">
            <template #header>
                <div class="card-header">
                    <h2>个人中心</h2>
                </div>
            </template>

            <div class="avatar-container">
                <div class="avatar-wrapper">
                    <AvatarUpload v-model="userStore.userInfo!.avatar" :size="100" button-text="更换头像" :max-size="10"
                        :show-debug="isDev" @success="handleAvatarUploadSuccess" @error="handleAvatarUploadError" />
                </div>
            </div>

            <!-- 数据概览 -->
            <div v-loading="dashboardLoading" class="dashboard-row">
                <div class="dashboard-item" @click="goTo('/user/bookings')">
                    <div class="dashboard-value">{{ dashboardLoading ? '—' : (dashboard.pendingPaymentCount || 0) }}</div>
                    <div class="dashboard-label">待付款</div>
                </div>
                <div class="dashboard-divider" />
                <div class="dashboard-item" @click="goTo('/user/reviews')">
                    <div class="dashboard-value">{{ dashboardLoading ? '—' : (dashboard.pendingReviewCount || 0) }}</div>
                    <div class="dashboard-label">待评价</div>
                </div>
                <div class="dashboard-divider" />
                <div class="dashboard-item" @click="goTo('/user/favorites')">
                    <div class="dashboard-value">{{ dashboardLoading ? '—' : (dashboard.favoriteCount || 0) }}</div>
                    <div class="dashboard-label">收藏</div>
                </div>
                <div class="dashboard-divider" />
                <div class="dashboard-item" @click="goTo('/user/coupons')">
                    <div class="dashboard-value">{{ dashboardLoading ? '—' : (dashboard.couponCount || 0) }}</div>
                    <div class="dashboard-label">优惠券</div>
                </div>
                <div class="dashboard-divider" />
                <div class="dashboard-item" @click="goTo('/user/notifications')">
                    <div class="dashboard-value">{{ dashboardLoading ? '—' : (dashboard.unreadNotificationCount || 0) }}</div>
                    <div class="dashboard-label">未读通知</div>
                    <el-badge v-if="!dashboardLoading && dashboard.unreadNotificationCount" :value="dashboard.unreadNotificationCount" class="dashboard-badge" />
                </div>
            </div>

            <el-tabs v-model="activeTab" class="profile-tabs">
                <el-tab-pane label="基本信息" name="basic">
                    <div class="info-section">
                        <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="100px"
                            class="profile-form">
                            <el-form-item label="用户名">
                                <el-input v-model="profileForm.username" disabled />
                            </el-form-item>
                            <el-form-item label="邮箱">
                                <el-input v-model="profileForm.email" disabled />
                            </el-form-item>
                            <el-form-item label="手机号码">
                                <el-input v-model="profileForm.phone" disabled placeholder="未设置" />
                            </el-form-item>
                            <el-form-item label="昵称" prop="nickname">
                                <el-input v-model="profileForm.nickname" placeholder="设置一个昵称" maxlength="50"
                                    show-word-limit />
                            </el-form-item>
                            <el-form-item label="性别">
                                <el-select v-model="profileForm.gender" placeholder="请选择" clearable style="width: 100%">
                                    <el-option label="男" value="MALE" />
                                    <el-option label="女" value="FEMALE" />
                                    <el-option label="保密" value="SECRET" />
                                </el-select>
                            </el-form-item>
                            <el-form-item label="生日">
                                <el-date-picker v-model="profileForm.birthday" type="date" placeholder="选择生日"
                                    style="width: 100%" value-format="YYYY-MM-DD" />
                            </el-form-item>
                            <el-form-item label="职业">
                                <el-input v-model="profileForm.occupation" placeholder="您的职业" maxlength="100"
                                    show-word-limit />
                            </el-form-item>
                            <el-form-item label="语言">
                                <el-input v-model="profileForm.languages" placeholder="例如：中文、English" maxlength="100"
                                    show-word-limit />
                            </el-form-item>
                            <el-form-item label="个人简介">
                                <el-input v-model="profileForm.introduction" type="textarea" :rows="4"
                                    placeholder="介绍一下自己" maxlength="500" show-word-limit />
                            </el-form-item>
                            <el-form-item>
                                <el-button type="primary" @click="submitProfileUpdate" :loading="saving">
                                    保存资料
                                </el-button>
                                <el-button @click="resetProfileForm">重置</el-button>
                            </el-form-item>
                        </el-form>

                        <div class="verification-section">
                            <div class="section-header">
                                <h3>实名认证信息</h3>
                                <el-tag v-if="isVerified" type="success" size="small">已认证</el-tag>
                                <el-button v-else type="primary" size="small" @click="handleVerification">
                                    去认证
                                </el-button>
                            </div>
                            <el-descriptions :column="1" border>
                                <el-descriptions-item label="真实姓名">
                                    <template v-if="userStore.userInfo?.realName">
                                        {{ maskedRealName }}
                                        <el-tag size="small" type="success" style="margin-left: 8px">已验证</el-tag>
                                    </template>
                                    <template v-else>
                                        未设置
                                    </template>
                                </el-descriptions-item>
                                <el-descriptions-item label="身份证号">
                                    <template v-if="userStore.userInfo?.idCard">
                                        {{ maskedIdCard }}
                                        <el-tag size="small" type="success" style="margin-left: 8px">已验证</el-tag>
                                    </template>
                                    <template v-else>
                                        未设置
                                    </template>
                                </el-descriptions-item>
                            </el-descriptions>
                        </div>
                    </div>
                </el-tab-pane>

                <el-tab-pane label="账号安全" name="security">
                    <div class="security-section">
                        <el-descriptions :column="1" border>
                            <el-descriptions-item label="登录密码">
                                <span>已设置</span>
                                <el-button type="primary" link @click="handleChangePassword">
                                    修改密码
                                </el-button>
                            </el-descriptions-item>
                            <el-descriptions-item label="手机绑定">
                                <span>{{ userStore.userInfo?.phone || '未绑定' }}</span>
                                <el-button type="primary" link @click="handleChangePhone">
                                    {{ userStore.userInfo?.phone ? '修改' : '绑定' }}
                                </el-button>
                            </el-descriptions-item>
                            <el-descriptions-item label="邮箱绑定">
                                <span>{{ userStore.userInfo?.email }}</span>
                                <el-button type="primary" link @click="handleChangeEmail">
                                    修改
                                </el-button>
                            </el-descriptions-item>
                        </el-descriptions>
                    </div>
                </el-tab-pane>
            </el-tabs>
        </el-card>

        <!-- 修改密码对话框 -->
        <el-dialog v-model="passwordDialogVisible" title="修改密码" width="400px">
            <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="100px">
                <el-form-item label="原密码" prop="oldPassword">
                    <el-input v-model="passwordForm.oldPassword" type="password" show-password />
                </el-form-item>
                <el-form-item label="新密码" prop="newPassword">
                    <el-input v-model="passwordForm.newPassword" type="password" show-password />
                </el-form-item>
                <el-form-item label="确认新密码" prop="confirmPassword">
                    <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
                </el-form-item>
            </el-form>
            <template #footer>
                <span class="dialog-footer">
                    <el-button @click="passwordDialogVisible = false">取消</el-button>
                    <el-button type="primary" @click="submitPasswordChange">
                        确认
                    </el-button>
                </span>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { useUserStore } from '@/stores/user';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';
import AvatarUpload from '@/components/AvatarUpload.vue';
import api from '@/api';

const userStore = useUserStore();
const router = useRouter();
const activeTab = ref('basic');
const mode = ref(import.meta.env.MODE || 'development');
const isDev = ref(mode.value === 'development');

// 数据概览
const dashboard = ref({
    pendingPaymentCount: 0,
    pendingReviewCount: 0,
    favoriteCount: 0,
    couponCount: 0,
    unreadNotificationCount: 0
});

const fetchDashboard = async () => {
    dashboardLoading.value = true;
    try {
        const res = await api.get('/api/auth/dashboard');
        dashboard.value = res.data || {};
    } catch (e) {
        console.error('获取仪表盘数据失败', e);
    } finally {
        dashboardLoading.value = false;
    }
};

const goTo = (path: string) => {
    router.push(path);
};

// 表单数据
// 认证状态相关
const isVerified = computed(() => {
    // 如果有真实姓名和身份证号，则认为已认证
    return !!(userStore.userInfo?.realName && userStore.userInfo?.idCard);
});

// 信息脱敏处理
const maskedRealName = computed(() => {
    const realName = userStore.userInfo?.realName;
    if (!realName) return '';
    if (realName.length <= 2) {
        return realName.charAt(0) + '*';
    }
    return realName.charAt(0) + '*'.repeat(realName.length - 2) + realName.charAt(realName.length - 1);
});

const maskedIdCard = computed(() => {
    const idCard = userStore.userInfo?.idCard;
    if (!idCard) return '';
    return idCard.substring(0, 6) + '*'.repeat(8) + idCard.substring(14);
});

// 新的头像上传成功处理（配合AvatarUpload组件）
const handleAvatarUploadSuccess = (fileName: string) => {
    console.log('头像上传成功，文件名:', fileName);

    // 组件已经自动更新了v-model的值，这里只需要：
    // 1. 更新localStorage
    if (userStore.userInfo) {
        localStorage.setItem('homestay_user', JSON.stringify(userStore.userInfo));
    }

    // 2. 刷新用户信息确保数据同步
    userStore.fetchUserInfo();

    ElMessage.success('头像更新成功');
};

// 新的头像上传错误处理（配合AvatarUpload组件）
const handleAvatarUploadError = (error: any) => {
    console.error('头像上传失败:', error);
    ElMessage.error('头像上传失败，请重试');
};

// 个人资料编辑表单
const profileFormRef = ref();
const saving = ref(false);
const dashboardLoading = ref(false);
const profileForm = ref({
    username: '',
    email: '',
    phone: '',
    nickname: '',
    gender: '',
    birthday: '',
    occupation: '',
    introduction: '',
    languages: ''
});

const profileRules = {
    nickname: [
        { max: 50, message: '昵称最多50个字符', trigger: 'blur' }
    ]
};

const initProfileForm = () => {
    const info = userStore.userInfo;
    if (info) {
        profileForm.value = {
            username: info.username || '',
            email: info.email || '',
            phone: info.phone || '',
            nickname: info.nickname || '',
            gender: info.gender || '',
            birthday: info.birthday || '',
            occupation: info.occupation || '',
            introduction: info.introduction || '',
            languages: info.languages || ''
        };
    }
};

const submitProfileUpdate = async () => {
    if (!profileFormRef.value) return;
    try {
        await profileFormRef.value.validate();
        saving.value = true;
        await userStore.updateProfile(profileForm.value);
        ElMessage.success('资料保存成功');
    } catch (error: any) {
        console.error('保存资料失败:', error);
    } finally {
        saving.value = false;
    }
};

const resetProfileForm = () => {
    initProfileForm();
    ElMessage.info('已重置为当前资料');
};

// 密码修改相关
const passwordDialogVisible = ref(false);
const passwordFormRef = ref();
const passwordForm = ref({
    oldPassword: '',
    newPassword: '',
    confirmPassword: ''
});

const passwordRules = {
    oldPassword: [
        { required: true, message: '请输入原密码', trigger: 'blur' },
        { min: 6, message: '密码长度不能小于6位', trigger: 'blur' }
    ],
    newPassword: [
        { required: true, message: '请输入新密码', trigger: 'blur' },
        { min: 6, message: '密码长度不能小于6位', trigger: 'blur' }
    ],
    confirmPassword: [
        { required: true, message: '请确认新密码', trigger: 'blur' },
        {
            validator: (_rule: any, value: string, callback: (error?: Error) => void) => {
                if (value !== passwordForm.value.newPassword) {
                    callback(new Error('两次输入的密码不一致'));
                } else {
                    callback();
                }
            },
            trigger: 'blur'
        }
    ]
};

// 各种操作处理函数
const handleVerification = () => {
    // TODO: 实现实名认证逻辑
    ElMessage.info('实名认证功能开发中');
};

const handleChangePassword = () => {
    passwordDialogVisible.value = true;
};

const handleChangePhone = () => {
    // TODO: 实现手机号修改逻辑
    ElMessage.info('手机号修改功能开发中');
};

const handleChangeEmail = () => {
    // TODO: 实现邮箱修改逻辑
    ElMessage.info('邮箱修改功能开发中');
};

const submitPasswordChange = async () => {
    if (!passwordFormRef.value) return;

    try {
        await passwordFormRef.value.validate();
        await userStore.changePassword({
            oldPassword: passwordForm.value.oldPassword,
            newPassword: passwordForm.value.newPassword
        });
        ElMessage.success('密码修改成功');
        passwordDialogVisible.value = false;
        passwordForm.value = {
            oldPassword: '',
            newPassword: '',
            confirmPassword: ''
        };
    } catch (error: any) {
    }
};

// 检查用户信息是否完整，如果不完整则自动获取
const checkUserInfo = async () => {
    if (userStore.token && !userStore.userInfo) {
        try {
            console.log("用户已登录但缺少用户信息，尝试获取用户信息");
            await userStore.fetchUserInfo();
            console.log("用户信息获取成功:", userStore.userInfo);
        } catch (error: any) {
            console.error("获取用户信息失败:", error);
            if (error.response && error.response.status === 401) {
                userStore.logout();
                router.push("/login");
            }
        }
    }
};

// 监听用户信息变化，自动同步表单和看板
watch(() => userStore.userInfo, (info) => {
    if (info) {
        initProfileForm();
        fetchDashboard();
    }
}, { deep: true });

// 初始化
onMounted(async () => {
    if (userStore.token && !userStore.userInfo) {
        try {
            await userStore.fetchUserInfo();
        } catch (error) {
            console.error("获取用户信息失败:", error);
        }
    }

    console.log("个人中心初始化信息:", {
        开发模式: isDev.value
    });

    await checkUserInfo();
    initProfileForm();
    fetchDashboard();
});
</script>

<style scoped>
.profile-container {
    max-width: 900px;
    margin: 0 auto;
    padding: 20px;
}

.profile-card {
    background-color: #fff;
    border-radius: 8px;
}

.profile-header {
    text-align: center;
    padding: 20px 0;
    border-bottom: 1px solid var(--el-border-color-light);
}

.avatar-container {
    text-align: center;
    margin-bottom: 20px;
}

.avatar-wrapper {
    position: relative;
    display: inline-block;
}

.profile-tabs {
    margin-top: 20px;
}

.info-section,
.security-section {
    padding: 20px 0;
}

.verification-section {
    margin-top: 30px;
    background-color: var(--el-fill-color-blank);
    border-radius: 4px;
}

.section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
    padding: 16px 24px;
    border-bottom: 1px solid var(--el-border-color-light);
}

.section-header h3 {
    margin: 0;
    color: var(--el-text-color-primary);
    font-size: 16px;
    font-weight: 500;
}

:deep(.el-descriptions) {
    margin-bottom: 0;
}

:deep(.el-descriptions__cell) {
    padding: 16px 24px;
}

:deep(.el-descriptions__label) {
    width: 120px;
    color: var(--el-text-color-regular);
    font-weight: normal;
}

:deep(.el-tag--small) {
    height: 20px;
    padding: 0 6px;
    font-size: 12px;
}

.verified-info {
    display: flex;
    align-items: center;
    gap: 8px;
}

.verified-info .el-tag {
    margin-left: 8px;
}

.dialog-footer {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
    margin-top: 20px;
}

.profile-form {
    max-width: 560px;
    margin: 0 auto;
    padding: 8px 0;
}

.profile-form :deep(.el-form-item__label) {
    font-weight: 500;
}

.profile-form .el-input.is-disabled {
    --el-input-text-color: var(--el-text-color-regular);
}

.profile-form .el-input.is-disabled :deep(.el-input__wrapper) {
    background-color: var(--el-fill-color-light);
}

/* 数据概览 */
.dashboard-row {
    display: flex;
    align-items: center;
    justify-content: space-around;
    padding: 20px 0;
    margin-bottom: 20px;
    background: linear-gradient(135deg, #f5f7fa 0%, #ffffff 100%);
    border-radius: 12px;
    border: 1px solid var(--el-border-color-lighter);
}

.dashboard-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    cursor: pointer;
    padding: 8px 16px;
    border-radius: 8px;
    transition: background-color 0.2s;
    position: relative;
}

.dashboard-item:hover {
    background-color: var(--el-fill-color-light);
}

.dashboard-value {
    font-size: 24px;
    font-weight: 700;
    color: var(--el-color-primary);
    line-height: 1;
}

.dashboard-label {
    font-size: 13px;
    color: #909399;
}

.dashboard-divider {
    width: 1px;
    height: 32px;
    background: var(--el-border-color-lighter);
}

.dashboard-badge :deep(.el-badge__content) {
    top: 4px;
    right: 8px;
}

@media (max-width: 768px) {
    .dashboard-row {
        flex-wrap: wrap;
        gap: 8px;
    }

    .dashboard-divider {
        display: none;
    }

    .dashboard-item {
        flex: 1 1 calc(33% - 16px);
        min-width: 80px;
    }
}
</style>