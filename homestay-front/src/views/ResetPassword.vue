<template>
    <div class="reset-password-container">
        <div class="reset-password-box">
            <div class="reset-password-header">
                <h2>重置密码</h2>
                <p class="subtitle">请设置您的新密码</p>
            </div>

            <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
                <el-form-item label="新密码" prop="newPassword">
                    <el-input v-model="form.newPassword" type="password" placeholder="请输入新密码" show-password
                        prefix-icon="Lock" :disabled="loading" />
                </el-form-item>

                <el-form-item label="确认密码" prop="confirmPassword">
                    <el-input v-model="form.confirmPassword" type="password" placeholder="请再次输入新密码" show-password
                        prefix-icon="Lock" :disabled="loading" />
                </el-form-item>

                <el-button type="primary" class="submit-btn" :loading="loading" @click="handleSubmit">
                    {{ loading ? '重置中...' : '重置密码' }}
                </el-button>

                <div class="back-to-login">
                    <el-button type="text" @click="goToLogin">返回登录</el-button>
                </div>
            </el-form>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance } from 'element-plus'
import request from '@/utils/request'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
    newPassword: '',
    confirmPassword: '',
})

const validatePass2 = (_rule: any, value: string, callback: any) => {
    if (value === '') {
        callback(new Error('请再次输入密码'))
    } else if (value !== form.newPassword) {
        callback(new Error('两次输入密码不一致!'))
    } else {
        callback()
    }
}

const rules = {
    newPassword: [
        { required: true, message: '请输入新密码', trigger: 'blur' },
        { min: 6, message: '密码长度不能小于6个字符', trigger: 'blur' },
    ],
    confirmPassword: [
        { required: true, validator: validatePass2, trigger: 'blur' },
    ],
}

onMounted(() => {
    const token = route.query.token
    if (!token) {
        ElMessage.error('无效的重置链接')
        router.push('/login')
    }
})

const handleSubmit = async () => {
    if (!formRef.value) return

    const token = route.query.token as string
    if (!token) {
        ElMessage.error('无效的重置链接')
        return
    }

    try {
        await formRef.value.validate()
        loading.value = true

        await request.post('/api/auth/reset-password', {
            token,
            newPassword: form.newPassword,
        })

        ElMessage.success('密码重置成功，请使用新密码登录')
        setTimeout(() => {
            router.push('/login')
        }, 1500)
    } catch (error: any) {
        // 拦截器已统一弹出后端返回的错误信息
    } finally {
        loading.value = false
    }
}

const goToLogin = () => {
    router.push('/login')
}
</script>

<style scoped>
.reset-password-container {
    display: flex;
    justify-content: center;
    align-items: center;
    min-height: 100vh;
    background-color: #f5f7fa;
    padding: 20px;
}

.reset-password-box {
    background: white;
    padding: 40px;
    border-radius: 8px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
    width: 100%;
    max-width: 400px;
}

.reset-password-header {
    text-align: center;
    margin-bottom: 30px;
}

.reset-password-header h2 {
    color: #303133;
    margin-bottom: 10px;
}

.subtitle {
    color: #606266;
    font-size: 14px;
    line-height: 1.5;
}

.submit-btn {
    width: 100%;
    margin: 20px 0;
}

.back-to-login {
    text-align: center;
}

:deep(.el-form-item__label) {
    padding-bottom: 8px;
}

:deep(.el-input__wrapper) {
    padding: 1px 15px;
}

:deep(.el-input__prefix) {
    margin-right: 8px;
}
</style>