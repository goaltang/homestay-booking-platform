<template>
  <div class="forgot-password-container">
    <div class="forgot-password-box">
      <div class="forgot-password-form">
        <div class="logo-container">
          <Logo class="logo-icon" />
          <h2>民宿预订平台</h2>
        </div>
        <h3>找回密码</h3>
        <p class="subtitle">请输入您的邮箱地址，我们将向您发送重置密码的链接</p>

        <el-card class="form-card" shadow="never">
          <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="handleSubmit">
            <el-form-item label="邮箱地址" prop="email">
              <el-input v-model="form.email" type="email" placeholder="请输入邮箱地址" prefix-icon="Message"
                :disabled="loading" />
            </el-form-item>

            <el-button type="primary" class="submit-btn" :loading="loading" @click="handleSubmit">
              {{ loading ? '发送中...' : '发送重置链接' }}
            </el-button>

            <div class="back-to-login">
              记起密码了？
              <el-button type="link" @click="router.push('/login')">
                返回登录
              </el-button>
            </div>
          </el-form>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import type { FormInstance } from 'element-plus';
import { useUserStore } from '@/stores/user';
import Logo from '@/components/common/Logo.vue';

const router = useRouter();
const userStore = useUserStore();
const formRef = ref<FormInstance>();
const loading = ref(false);

const form = reactive({
  email: ''
});

const rules = {
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ]
};

const handleSubmit = async () => {
  if (!formRef.value) return;

  try {
    await formRef.value.validate();
    loading.value = true;

    console.log('发送忘记密码请求:', {
      email: form.email
    });

    await userStore.forgotPassword(form.email);

    ElMessage.success('重置密码链接已发送到您的邮箱，请查收');
  } catch (error: any) {
    // 拦截器已统一弹出后端返回的错误信息
    console.error('发送重置链接失败:', error);
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.forgot-password-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background-color: #f5f7fa;
  padding: 20px;
}

.forgot-password-box {
  width: 460px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.05);
  overflow: hidden;
}

.forgot-password-form {
  padding: 40px;
}

.logo-container {
  display: flex;
  align-items: center;
  margin-bottom: 30px;
}

.logo-icon {
  margin-right: 12px;
}

.logo-container h2 {
  font-size: 1.5rem;
  font-weight: 600;
  color: #333;
  margin: 0;
}

h3 {
  font-size: 1.8rem;
  font-weight: 600;
  color: #333;
  margin-bottom: 8px;
}

.subtitle {
  color: #666;
  margin-bottom: 30px;
}

.form-card {
  border: none;
  box-shadow: none;
}

.submit-btn {
  width: 100%;
  margin: 20px 0;
}

.back-to-login {
  text-align: center;
  color: #606266;
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

@media (max-width: 480px) {
  .forgot-password-box {
    width: 100%;
  }

  .forgot-password-form {
    padding: 20px;
  }
}
</style>