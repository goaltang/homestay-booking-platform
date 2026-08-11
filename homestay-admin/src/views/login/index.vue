<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-form-container">
        <div class="login-logo">
          <h2>民宿管理系统</h2>
        </div>
        <h3>管理员登录</h3>
        <p class="login-subtitle">请输入您的管理员账号和密码</p>

        <el-card class="login-card">
          <el-form ref="loginFormRef" :model="loginForm" :rules="rules" label-position="top"
            @submit.prevent="handleLogin">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="loginForm.username" placeholder="请输入用户名" prefix-icon="User" :disabled="loading"
                @keyup.enter="handleLogin" />
            </el-form-item>

            <el-form-item label="密码" prop="password">
              <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" prefix-icon="Lock"
                show-password :disabled="loading" @keyup.enter="handleLogin" />
            </el-form-item>

            <div class="remember-forgot">
              <el-checkbox v-model="loginForm.remember">记住我</el-checkbox>
              <span class="forgot-tip">忘记密码请联系系统管理员</span>
            </div>

            <el-button type="primary" class="submit-btn" :loading="loading" @click="handleLogin">
              {{ loading ? '登录中...' : '登录' }}
            </el-button>
          </el-form>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from "vue";
import { useRouter } from "vue-router";
import { useUserStore } from "@/stores/user";
import type { FormInstance } from "element-plus";
import { ElMessage } from "element-plus";
import { login } from '@/api/auth';

const router = useRouter();
const userStore = useUserStore();
const loginFormRef = ref<FormInstance>();
const loading = ref(false);

const loginForm = reactive({
  username: "",
  password: "",
  remember: false
});

const rules = {
  username: [
    { required: true, message: "请输入用户名", trigger: "blur" },
    { min: 3, max: 20, message: "长度在 3 到 20 个字符", trigger: "blur" },
  ],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { min: 6, max: 20, message: "长度在 6 到 20 个字符", trigger: "blur" },
  ],
};

const handleLogin = async () => {
  if (!loginFormRef.value) return;

  try {
    await loginFormRef.value.validate();
    loading.value = true;

    // 调用登录API
    const result = await login({
      username: loginForm.username,
      password: loginForm.password
    });

    // 使用 Pinia store 保存用户信息和 token
    userStore.setToken(result.token);
    userStore.setUserInfo({
      username: result.admin.username,
      role: result.admin.role
    });

    // 如果选择了记住我，可以在本地存储中保存用户名
    if (loginForm.remember) {
      localStorage.setItem('rememberedUsername', loginForm.username);
    } else {
      localStorage.removeItem('rememberedUsername');
    }

    ElMessage.success("登录成功");
    router.push("/");
  } catch (error: any) {
    console.error("登录出错:", error);

    // 增强错误处理，尝试获取详细错误信息
    let errorMessage = "登录失败，请重试";

    if (error.response) {
      // 如果有响应对象，尝试从不同位置获取错误信息
      errorMessage = error.response.data?.error ||
        error.response.data?.message ||
        error.response.data?.msg ||
        (typeof error.response.data === 'string' ? error.response.data : errorMessage);
    } else if (error.message) {
      // 如果有错误信息，直接使用
      errorMessage = error.message;
    }

    ElMessage.error(errorMessage);
  } finally {
    loading.value = false;
  }
};

// 页面加载时，如果之前记住了用户名，就自动填充
const initRememberedUsername = () => {
  const rememberedUsername = localStorage.getItem('rememberedUsername');
  if (rememberedUsername) {
    loginForm.username = rememberedUsername;
    loginForm.remember = true;
  }
};

// 在组件挂载时初始化
initRememberedUsername();
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  padding: 20px;
  position: relative;
  overflow: hidden;
}

.login-container::before {
  content: '';
  position: absolute;
  width: 200%;
  height: 200%;
  top: -50%;
  left: -50%;
  background: radial-gradient(circle at center, rgba(255, 255, 255, 0.8) 0%, rgba(255, 255, 255, 0) 60%);
  animation: rotate 30s linear infinite;
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}

.login-box {
  width: 420px;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
  background-color: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  padding: 40px;
  position: relative;
  z-index: 1;
}

.login-form-container {
  width: 100%;
}

.login-logo {
  text-align: center;
  margin-bottom: 30px;
}

.login-logo h2 {
  font-size: 28px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0;
  background: linear-gradient(45deg, #6366f1, #9294f5);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.login-form-container h3 {
  font-size: 24px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 8px;
  text-align: center;
}

.login-subtitle {
  color: #666;
  margin-bottom: 30px;
  text-align: center;
  font-size: 14px;
}

.login-card {
  border: none;
  box-shadow: none;
  background: transparent;
}

.remember-forgot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.forgot-tip {
  color: #909399;
  font-size: 13px;
}

.submit-btn {
  width: 100%;
  margin-bottom: 20px;
  height: 44px;
  font-size: 16px;
  font-weight: 500;
  letter-spacing: 1px;
  background: linear-gradient(45deg, #6366f1, #9294f5);
  border: none;
  border-radius: 8px;
  transition: all 0.3s ease;
}

.submit-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 5px 15px rgba(99, 102, 241, 0.3);
}

:deep(.el-form-item__label) {
  padding-bottom: 8px;
  font-weight: 500;
  color: #1a1a1a;
}

:deep(.el-input__wrapper) {
  padding: 1px 15px;
  border-radius: 8px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.05);
  background-color: #fff;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.2);
}

:deep(.el-input__prefix) {
  margin-right: 8px;
  color: #6366f1;
}
</style>