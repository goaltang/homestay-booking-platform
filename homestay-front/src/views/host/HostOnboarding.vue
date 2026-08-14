<template>
    <div class="host-onboarding">
        <div class="onboarding-header">
            <div class="header-top">
                <el-button :icon="ArrowLeft" @click="handleBack" class="back-btn">返回</el-button>
            </div>
            <h1>欢迎成为房东</h1>
            <p>完善以下信息，开始您的房东之旅</p>
        </div>

        <el-card class="onboarding-card">
            <el-steps :active="activeStep" finish-status="success" class="onboarding-steps" process-status="process">
                <el-step title="个人介绍" description="向客人介绍您自己"></el-step>
                <el-step title="身份验证" description="提交身份证明，增加可信度"></el-step>
                <el-step title="完成" description="准备开始您的房东之旅！"></el-step>
            </el-steps>

            <div class="step-content">
                <!-- 步骤1：个人介绍 -->
                <div v-if="activeStep === 0">
                    <h2>个人介绍</h2>
                    <p class="step-description">向客人介绍自己，让他们更了解您，增加信任感和预订率</p>

                    <el-form ref="introFormRef" :model="introForm" :rules="introRules" label-width="120px">
                        <el-form-item label="职业" prop="occupation">
                            <el-input v-model="introForm.occupation" placeholder="请输入您的职业"></el-input>
                        </el-form-item>

                        <el-form-item label="性别" prop="gender">
                            <el-select v-model="introForm.gender" placeholder="请选择">
                                <el-option label="男" value="MALE"></el-option>
                                <el-option label="女" value="FEMALE"></el-option>
                                <el-option label="保密" value="OTHER"></el-option>
                            </el-select>
                        </el-form-item>

                        <el-form-item label="语言能力" prop="languages">
                            <el-select v-model="introForm.languages" multiple placeholder="请选择您会的语言">
                                <el-option label="中文" value="CHINESE"></el-option>
                                <el-option label="英语" value="ENGLISH"></el-option>
                                <el-option label="日语" value="JAPANESE"></el-option>
                                <el-option label="韩语" value="KOREAN"></el-option>
                                <el-option label="法语" value="FRENCH"></el-option>
                                <el-option label="德语" value="GERMAN"></el-option>
                                <el-option label="西班牙语" value="SPANISH"></el-option>
                                <el-option label="其他" value="OTHER"></el-option>
                            </el-select>
                        </el-form-item>

                        <el-form-item label="自我介绍" prop="introduction">
                            <el-input v-model="introForm.introduction" type="textarea" :rows="6"
                                placeholder="向客人介绍您自己，包括您的兴趣爱好、为什么成为房东、您能为客人提供什么独特体验等" maxlength="1000"
                                show-word-limit></el-input>
                            <div class="form-tip">优质的自我介绍能提高客人的信任感，增加预订率</div>
                        </el-form-item>
                    </el-form>

                    <div class="step-actions">
                        <el-button type="primary" @click="validateAndNext(0)" :icon="ArrowRight">下一步</el-button>
                    </div>
                </div>

                <!-- 步骤2：身份验证 -->
                <div v-if="activeStep === 1">
                    <h2>身份验证</h2>
                    <p class="step-description">为了确保平台安全，我们需要验证您的身份。上传您的身份证正反面照片。</p>

                    <el-form ref="verifyFormRef" :model="verifyForm" :rules="verifyRules" label-width="120px">
                        <el-form-item label="身份证号码" prop="idCard">
                            <el-input v-model="verifyForm.idCard" placeholder="请输入您的身份证号码"></el-input>
                        </el-form-item>

                        <el-form-item label="身份证正面照片" prop="idCardFront">
                            <el-upload action="/api/files/upload" with-credentials
                                :on-success="(res: any) => handleIdCardFrontSuccess(res)" :before-upload="beforeIdCardUpload"
                                :file-list="idCardFrontFileList" list-type="picture-card" :limit="1">
                                <el-icon>
                                    <Plus />
                                </el-icon>
                            </el-upload>
                            <div class="upload-tip">请上传清晰的身份证人像面照片</div>
                        </el-form-item>

                        <el-form-item label="身份证背面照片" prop="idCardBack">
                            <el-upload action="/api/files/upload" with-credentials
                                :on-success="(res: any) => handleIdCardBackSuccess(res)" :before-upload="beforeIdCardUpload"
                                :file-list="idCardBackFileList" list-type="picture-card" :limit="1">
                                <el-icon>
                                    <Plus />
                                </el-icon>
                            </el-upload>
                            <div class="upload-tip">请上传清晰的身份证国徽面照片</div>
                        </el-form-item>
                    </el-form>

                    <div class="id-verify-note">
                        <el-alert title="您的信息安全将受到严格保护" type="info"
                            description="您上传的身份证信息将被加密存储，仅用于身份验证，平台不会将您的身份信息透露给任何第三方。" :closable="false" show-icon />
                    </div>

                    <div class="step-actions">
                        <el-button @click="prevStep" :icon="ArrowLeft">上一步</el-button>
                        <el-button @click="skipVerification" plain>暂时跳过</el-button>
                        <el-button type="primary" @click="validateAndNext(1)" :icon="Check">提交验证</el-button>
                    </div>
                </div>

                <!-- 步骤3：完成 -->
                <div v-if="activeStep === 2" class="completion-step">
                    <div class="completion-icon">
                        <el-icon class="success-icon">
                            <CircleCheckFilled />
                        </el-icon>
                    </div>
                    <h2>恭喜您！</h2>
                    <p class="completion-message">您已成功完成房东信息设置，现在可以开始发布您的房源了！</p>

                    <div class="next-steps">
                        <h3>接下来，您可以：</h3>
                        <div class="next-steps-actions">
                            <el-button type="primary" size="large" @click="goToCreateHomestay">
                                <el-icon>
                                    <Plus />
                                </el-icon>发布第一个房源
                            </el-button>
                            <el-button size="large" @click="goToDashboard">
                                <el-icon>
                                    <HomeFilled />
                                </el-icon>进入房东中心
                            </el-button>
                        </div>
                    </div>
                </div>
            </div>
        </el-card>
    </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores/user';
import { updateHostProfile, getHostProfile, becomeHost } from '@/api/host';
import {
    Plus,
    HomeFilled,
    CircleCheckFilled,
    ArrowRight,
    ArrowLeft,
    Check
} from '@element-plus/icons-vue';

const router = useRouter();
const userStore = useUserStore();

const activeStep = ref(0);

// 表单引用
const introFormRef = ref<FormInstance>();
const verifyFormRef = ref<FormInstance>();

// 介绍信息表单
const introForm = reactive({
    gender: '',
    occupation: '',
    languages: [] as string[],
    introduction: '',
});

// 验证信息表单
const verifyForm = reactive({
    idCard: '',
    idCardFront: '',
    idCardBack: '',
});

// 上传文件列表
const idCardFrontFileList = ref<any[]>([]);
const idCardBackFileList = ref<any[]>([]);

// 表单验证规则
const introRules = {
    gender: [
        { required: true, message: '请选择性别', trigger: 'change' }
    ],
    occupation: [
        { required: true, message: '请输入您的职业', trigger: 'blur' }
    ],
    languages: [
        { required: true, message: '请选择至少一种语言', trigger: 'change' }
    ],
    introduction: [
        { required: true, message: '请输入自我介绍', trigger: 'blur' },
        { min: 20, message: '自我介绍不能少于50个字符', trigger: 'blur' }
    ]
};

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

const handleIdCardFrontSuccess = (res: any) => {
    verifyForm.idCardFront = res.url;
    ElMessage.success('身份证正面照片上传成功');
};

const handleIdCardBackSuccess = (res: any) => {
    verifyForm.idCardBack = res.url;
    ElMessage.success('身份证背面照片上传成功');
};

const beforeIdCardUpload = (file: File) => {
    const isJPG = file.type === 'image/jpeg';
    const isPNG = file.type === 'image/png';
    const isLt2M = file.size / 1024 / 1024 < 2;

    if (!isJPG && !isPNG) {
        ElMessage.error('上传身份证照片只能是 JPG 或 PNG 格式!');
        return false;
    }
    if (!isLt2M) {
        ElMessage.error('上传身份证照片大小不能超过 2MB!');
        return false;
    }
    return true;
};

// 验证并进入下一步
const validateAndNext = async (step: number) => {
    if (step === 0) {
        if (!introFormRef.value) return;
        await introFormRef.value.validate(async (valid) => {
            if (valid) {
                try {
                    // 先调用 becomeHost 将用户角色转换为房东（此时用户已填写完个人介绍）
                    await becomeHost();

                    // 新 token 已由后端写入 httpOnly cookie，无需前端存储

                    // 刷新用户信息以同步角色变更
                    await userStore.fetchUserInfo();

                    // 然后保存介绍信息
                    await updateHostProfile({
                        gender: introForm.gender,
                        occupation: introForm.occupation,
                        languages: introForm.languages,
                        introduction: introForm.introduction
                    });
                    ElMessage.success('个人介绍保存成功');
                    nextStep();
                } catch (error) {
                    console.error('保存介绍信息失败:', error);
                }
            }
        });
    } else if (step === 1) {
        if (!verifyFormRef.value) return;
        await verifyFormRef.value.validate(async (valid) => {
            if (valid) {
                // 提交身份验证信息
                try {
                    await updateHostProfile({
                        idCard: verifyForm.idCard,
                        idCardFront: verifyForm.idCardFront,
                        idCardBack: verifyForm.idCardBack,
                        verificationStatus: 'PENDING' // 设置为待审核状态
                    });
                    ElMessage.success('身份信息已提交，等待审核');
                    nextStep();
                } catch (error) {
                    console.error('提交身份验证信息失败:', error);
                }
            }
        });
    }
};

// 获取用户已有信息（不再自动调用 becomeHost）
onMounted(async () => {
    try {
        // 仅获取已有资料用于预填表单（非房东用户会报错，正常忽略）
        const profileData = await getHostProfile();
        // 填充已有数据
        if (profileData) {
            if (profileData.gender) introForm.gender = profileData.gender;
            if (profileData.occupation) introForm.occupation = profileData.occupation;
            if (profileData.languages && Array.isArray(profileData.languages)) {
                introForm.languages = profileData.languages;
            }
            if (profileData.introduction) introForm.introduction = profileData.introduction;
            if (profileData.idCard) verifyForm.idCard = profileData.idCard;

            // 检查是否有身份证照片
            if (profileData.idCardFront) {
                verifyForm.idCardFront = profileData.idCardFront;
                idCardFrontFileList.value = [{ url: profileData.idCardFront, name: '身份证正面' }];
            }

            if (profileData.idCardBack) {
                verifyForm.idCardBack = profileData.idCardBack;
                idCardBackFileList.value = [{ url: profileData.idCardBack, name: '身份证背面' }];
            }
        }
    } catch (error) {
        // 非房东用户获取资料会失败，这是正常的，忽略错误
        console.log('获取房东资料失败（可能是新用户首次入驻），将使用空表单');
    }
});

const nextStep = () => {
    if (activeStep.value < 2) {
        activeStep.value += 1;
    }
};

const prevStep = () => {
    if (activeStep.value > 0) {
        activeStep.value -= 1;
    }
};

const skipVerification = () => {
    ElMessageBox.confirm(
        '跳过身份验证可能会影响您的信誉度和房源审核速度。您可以稍后在个人资料中完成身份验证。确定要跳过吗？',
        '跳过身份验证',
        {
            confirmButtonText: '确定跳过',
            cancelButtonText: '继续验证',
            type: 'warning',
        }
    ).then(async () => {
        try {
            // 标记用户选择跳过身份验证
            await updateHostProfile({
                verificationStatus: 'SKIPPED',
                skipVerificationTime: new Date().toISOString()
            });

            ElMessage.success('已暂时跳过身份验证，您可以稍后在个人资料中完成');

            // 直接跳转到房东中心
            router.push('/host');
        } catch (error) {
            console.error('跳过验证设置失败:', error);
        }
    }).catch(() => {
        // 用户取消跳过，继续停留在当前页面
    });
};

// 完成后的导航
const goToCreateHomestay = () => {
    router.push('/host/homestay/create');
};

const goToDashboard = () => {
    router.push('/host');
};

const handleBack = () => {
    router.back();
};
</script>

<style scoped>
.host-onboarding {
    max-width: 900px;
    margin: 40px auto;
    padding: 0 20px;
}

.onboarding-header {
    text-align: center;
    margin-bottom: 30px;
}

.header-top {
    display: flex;
    justify-content: flex-start;
    margin-bottom: 20px;
}

.back-btn {
    margin-bottom: 10px;
}

.onboarding-header h1 {
    font-size: 32px;
    color: #303133;
    margin-bottom: 12px;
    font-weight: 600;
}

.onboarding-header p {
    font-size: 18px;
    color: #606266;
}

.onboarding-card {
    padding: 30px;
    margin-bottom: 40px;
    border-radius: 12px;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

.onboarding-steps {
    margin-bottom: 50px;
}

.step-content {
    padding: 20px 30px;
    background-color: #f9fafc;
    border-radius: 8px;
}

.step-content h2 {
    font-size: 24px;
    margin-bottom: 10px;
    color: #303133;
    font-weight: 600;
}

.step-description {
    color: #606266;
    margin-bottom: 25px;
    font-size: 16px;
    line-height: 1.6;
}

.step-actions {
    display: flex;
    justify-content: space-between;
    margin-top: 40px;
    padding-top: 20px;
    border-top: 1px solid #EBEEF5;
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

.id-verify-note {
    margin: 30px 0;
}

.completion-step {
    text-align: center;
    padding: 50px 0;
}

.completion-icon {
    margin-bottom: 30px;
}

.success-icon {
    font-size: 100px;
    color: #67C23A;
}

.completion-message {
    font-size: 20px;
    color: #606266;
    margin-bottom: 40px;
    line-height: 1.5;
}

.next-steps {
    margin-top: 40px;
    text-align: center;
}

.next-steps h3 {
    margin-bottom: 30px;
    font-size: 18px;
    color: #303133;
}

.next-steps-actions {
    display: flex;
    justify-content: center;
    gap: 20px;
}

@media (max-width: 768px) {
    .onboarding-card {
        padding: 20px 15px;
    }

    .step-content {
        padding: 15px;
    }

    .next-steps-actions {
        flex-direction: column;
        align-items: center;
    }

    .next-steps-actions .el-button {
        width: 100%;
        margin-bottom: 15px;
    }
}
</style>
