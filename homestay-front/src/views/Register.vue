<template>
  <div class="register-container">
    <div class="ambient ambient-sun"></div>
    <div class="ambient ambient-sage"></div>

    <main class="register-box">
      <section class="register-left" aria-label="注册平台介绍">
        <div class="register-overlay"></div>
        <div class="brand-pill">
          <Logo class="brand-pill-icon" />
          <span>Homestay Select</span>
        </div>

        <div class="register-content">
          <p class="register-eyebrow">CREATE YOUR STAY ACCOUNT</p>
          <h1>
            <span>把每一次出发，</span>
            <span>变成可期待的日常。</span>
          </h1>
          <p class="register-description">
            创建账号后，你可以收藏喜欢的民宿、追踪订单进度；也可以成为房东，管理房源和入住安排。
          </p>

          <div class="register-highlights">
            <div class="highlight-item">
              <strong>旅客账号</strong>
              <span>收藏、预订、查看入住安排</span>
            </div>
            <div class="highlight-item">
              <strong>房东账号</strong>
              <span>发布房源、处理订单、管理收益</span>
            </div>
            <div class="highlight-item">
              <strong>统一安全</strong>
              <span>账号资料与订单状态统一管理</span>
            </div>
          </div>
        </div>

        <div class="register-flow-card" aria-hidden="true">
          <div class="flow-step is-active">
            <span>1</span>
            <strong>创建账号</strong>
          </div>
          <div class="flow-line"></div>
          <div class="flow-step">
            <span>2</span>
            <strong>选择身份</strong>
          </div>
          <div class="flow-line"></div>
          <div class="flow-step">
            <span>3</span>
            <strong>开始使用</strong>
          </div>
        </div>
      </section>

      <section class="register-right" aria-label="创建账号">
        <div class="register-form-container">
          <div class="register-logo">
            <Logo class="logo-icon" />
            <h2>民宿预订平台</h2>
          </div>

          <div class="register-copy">
            <p class="form-kicker">快速开始</p>
            <h3>创建账号</h3>
            <p class="register-subtitle">选择你的身份，填写必要信息即可开始使用平台。</p>
          </div>

          <el-card class="register-card">
            <div class="register-form-wrapper">
              <el-form ref="registerFormRef" :model="form" :rules="rules" label-position="top"
                :validate-on-rule-change="false" @submit.prevent="handleRegister">
                <el-form-item label="用户名" prop="username">
                  <el-input v-model="form.username" placeholder="请输入用户名（3-20个字符）" prefix-icon="User" name="username" autocomplete="username"
                    :disabled="loading" :suffix-icon="usernameValidating ? 'Loading' : ''" clearable @blur="validateField('username')" />
                  <div class="form-item-tip" v-if="usernameValidating">
                    <span class="checking-text">正在检查用户名是否可用...</span>
                  </div>
                  <div class="form-item-tip" v-else-if="form.username && form.username.length >= 3">
                    <span class="success-text">✓ 用户名可用</span>
                  </div>
                </el-form-item>

                <el-form-item label="邮箱" prop="email">
                  <el-autocomplete v-model="form.email" placeholder="请输入邮箱地址" :fetch-suggestions="queryEmailSearch"
                    :trigger-on-focus="false" clearable style="width: 100%;" prefix-icon="Message" name="email" autocomplete="email" :disabled="loading"
                    :suffix-icon="emailValidating ? 'Loading' : ''" @blur="validateField('email')" />
                  <div class="form-item-tip" v-if="emailValidating">
                    <span class="checking-text">正在检查邮箱是否可用...</span>
                  </div>
                  <div class="form-item-tip" v-else-if="form.email && isValidEmail(form.email)">
                    <span class="success-text">✓ 邮箱格式正确</span>
                  </div>
                </el-form-item>

                <el-form-item label="手机号" prop="phone">
                  <el-input v-model="form.phone" placeholder="请输入手机号（可选）" prefix-icon="Phone" name="tel" autocomplete="tel" :disabled="loading"
                    clearable @blur="validateField('phone')" />
                  <div class="form-item-tip" v-if="form.phone && isValidPhone(form.phone)">
                    <span class="success-text">✓ 手机号格式正确</span>
                  </div>
                </el-form-item>

                <el-form-item label="密码" prop="password">
                  <el-input v-model="form.password" type="password" placeholder="请输入密码（6-20个字符）" prefix-icon="Lock"
                    name="new-password" autocomplete="new-password" show-password :disabled="loading" clearable @input="onPasswordInput"
                    @blur="validateField('password')" />
                  <!-- 密码强度指示器 -->
                  <div class="password-strength" v-if="form.password">
                    <div class="strength-bars">
                      <div class="strength-bar" :class="getPasswordStrengthClass(index)" v-for="index in 4"
                        :key="index"></div>
                    </div>
                    <span class="strength-text" :class="passwordStrength.class">
                      {{ passwordStrength.text }}
                    </span>
                  </div>
                  <!-- 密码要求提示 -->
                  <div class="password-tips" v-if="showPasswordTips">
                    <p class="tip-title">密码要求：</p>
                    <ul class="tip-list">
                      <li :class="{ 'tip-success': form.password.length >= 6 }">
                        ✓ 至少6个字符
                      </li>
                      <li :class="{ 'tip-success': /[A-Z]/.test(form.password) }">
                        ✓ 包含大写字母（推荐）
                      </li>
                      <li :class="{ 'tip-success': /[0-9]/.test(form.password) }">
                        ✓ 包含数字（推荐）
                      </li>
                      <li :class="{ 'tip-success': hasSpecialChar }">
                        ✓ 包含特殊字符（推荐）
                      </li>
                    </ul>
                  </div>
                </el-form-item>

                <el-form-item label="确认密码" prop="confirmPassword">
                  <el-input v-model="form.confirmPassword" type="password" placeholder="请再次输入密码" prefix-icon="Lock"
                    name="confirm-password" autocomplete="new-password" show-password :disabled="loading" clearable @blur="validateField('confirmPassword')" />
                  <div class="form-item-tip" v-if="form.confirmPassword && form.password === form.confirmPassword">
                    <span class="success-text">✓ 密码匹配</span>
                  </div>
                </el-form-item>

                <el-form-item label="注册身份" prop="role">
                  <el-radio-group v-model="form.role" class="role-group">
                    <el-radio label="ROLE_USER" class="role-radio">
                      <div class="role-option">
                        <div class="role-title">普通用户</div>
                        <div class="role-desc">浏览和预订民宿</div>
                      </div>
                    </el-radio>
                    <el-radio label="ROLE_HOST" class="role-radio">
                      <div class="role-option">
                        <div class="role-title">房东</div>
                        <div class="role-desc">发布房源，管理预订</div>
                      </div>
                    </el-radio>
                  </el-radio-group>
                  <div class="role-tip" v-if="form.role === 'ROLE_HOST'">
                    <el-alert title="房东特权：发布房源、管理订单、获得收益、专属客服支持" type="info" :closable="false" show-icon />
                  </div>
                </el-form-item>

                <el-form-item label="邀请码（可选）">
                  <el-input v-model="form.referralCode" placeholder="如有邀请码，请输入" prefix-icon="Ticket" name="referralCode" autocomplete="off" :disabled="loading" clearable />
                  <div class="form-item-tip" v-if="form.referralCode">
                    <span class="checking-text">注册成功后将自动领取邀请奖励</span>
                  </div>
                </el-form-item>

                <!-- 用户协议和隐私政策 -->
                <el-form-item prop="agreement">
                  <el-checkbox v-model="form.agreement" :disabled="loading" class="agreement-checkbox">
                    我已阅读并同意
                    <el-button type="text" class="agreement-link" @click="showUserAgreement">《用户服务协议》</el-button>
                    和
                    <el-button type="text" class="agreement-link" @click="showPrivacyPolicy">《隐私政策》</el-button>
                  </el-checkbox>
                </el-form-item>

                <el-button native-type="submit" type="primary" class="submit-btn" :class="{ 'submit-btn-ready': isFormValid }"
                  :loading="loading" :disabled="loading || usernameValidating || emailValidating"
                >
                  {{ loading ? '注册中...' : '立即注册' }}
                </el-button>
                <p class="submit-helper">提交时会逐项校验信息，未完成的内容会直接显示在表单中。</p>

                <div class="login-link">
                  已有账号？
                  <el-button type="text" @click="goToLogin" :disabled="loading">立即登录</el-button>
                </div>
              </el-form>
            </div>
          </el-card>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, shallowRef } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores/user';
import type { FormInstance } from 'element-plus';
import { ElMessage, ElMessageBox } from 'element-plus';
import Logo from '@/components/common/Logo.vue';
import api from '@/api';
import { debounce } from 'lodash-es';

const router = useRouter();
const userStore = useUserStore();
const registerFormRef = ref<FormInstance>();
const loading = shallowRef(false);
const showPasswordTips = shallowRef(false);

const form = reactive({
  username: '',
  email: '',
  phone: '',
  password: '',
  confirmPassword: '',
  role: 'ROLE_USER',
  referralCode: '',
  agreement: false,
});

// 添加验证状态跟踪
const usernameValidating = shallowRef(false);
const emailValidating = shallowRef(false);

const hasSpecialChar = computed(() => /[!@#$%^&*(),.?":{}|<>]/.test(form.password));
const usernamePattern = /^[a-zA-Z0-9_\u4e00-\u9fa5]+$/;

// 计算表单是否有效
const isFormValid = computed(() => {
  return form.username.length >= 3 &&
    form.username.length <= 20 &&
    usernamePattern.test(form.username) &&
    isValidEmail(form.email) &&
    form.password.length >= 6 &&
    form.password.length <= 20 &&
    form.confirmPassword === form.password &&
    form.agreement &&
    !usernameValidating.value &&
    !emailValidating.value;
});

// 密码强度计算
const passwordStrength = computed(() => {
  const password = form.password;
  if (!password) return { score: 0, text: '', class: '' };

  let score = 0;

  // 长度检查
  if (password.length >= 6) score++;
  if (password.length >= 8) score++;

  // 复杂度检查
  if (/[a-z]/.test(password)) score++;
  if (/[A-Z]/.test(password)) score++;
  if (/[0-9]/.test(password)) score++;
  if (/[!@#$%^&*(),.?":{}|<>]/.test(password)) score++;

  // 根据分数确定强度
  if (score <= 2) return { score, text: '弱', class: 'weak' };
  if (score <= 4) return { score, text: '中等', class: 'medium' };
  return { score, text: '强', class: 'strong' };
});

const getPasswordStrengthClass = (index: number) => {
  const score = passwordStrength.value.score;
  if (index <= score) {
    if (score <= 2) return 'weak';
    if (score <= 4) return 'medium';
    return 'strong';
  }
  return '';
};

// 邮箱验证
const isValidEmail = (email: string) => {
  return /^[\w-]+(\.[\w-]+)*@([\w-]+\.)+[a-zA-Z]{2,7}$/.test(email);
};

// 手机号验证
const isValidPhone = (phone: string) => {
  return /^1[3-9]\d{9}$/.test(phone);
};

// 密码输入处理
const onPasswordInput = () => {
  showPasswordTips.value = form.password.length > 0 && form.password.length < 6;
};

// 单个字段验证
const validateField = async (prop: string) => {
  if (!registerFormRef.value) return;
  try {
    await registerFormRef.value.validateField(prop);
  } catch {
    // 验证失败时不做任何操作，错误信息已经显示
  }
};

const emailSuffixes = ['@qq.com', '@gmail.com', '@163.com', '@126.com', '@sina.com', '@hotmail.com', '@outlook.com', '@live.com', '@icloud.com'];

interface EmailSuggestion { value: string; }

const queryEmailSearch = (queryString: string, cb: (suggestions: EmailSuggestion[]) => void) => {
  let results: EmailSuggestion[] = [];
  if (queryString) {
    const atIndex = queryString.indexOf('@');
    if (atIndex === -1) { // 用户尚未输入 @
      results = emailSuffixes.map(suffix => ({ value: queryString + suffix }));
    } else { // 用户已输入 @
      const prefix = queryString.substring(0, atIndex);
      const domainInput = queryString.substring(atIndex);
      results = emailSuffixes
        .filter(suffix => suffix.startsWith(domainInput))
        .map(suffix => ({ value: prefix + suffix }));

      // 如果用户输入的@xxx.com 不在预设后缀中，也允许其作为选项，但优先显示匹配的预设后缀
      if (!emailSuffixes.some(s => s === domainInput) && domainInput.length > 1 && domainInput.includes('.')) {
        results.unshift({ value: queryString });
      }
    }
  }
  cb(results);
};

// 检查用户名是否存在的函数
const checkUsernameExists = debounce(async (username: string, callback: Function) => {
  if (!username || username.length < 3) {
    callback();
    return;
  }

  try {
    usernameValidating.value = true;
    const response = await api.get(`/api/auth/check-username?username=${encodeURIComponent(username)}`);
    const exists = response.data?.exists;

    if (exists) {
      callback(new Error('该用户名已被使用'));
    } else {
      callback();
    }
  } catch (error) {
    console.error('检查用户名失败:', error);
    // 如果API请求失败，我们不阻止表单提交
    callback();
  } finally {
    usernameValidating.value = false;
  }
}, 500);

// 检查邮箱是否存在的函数
const checkEmailExists = debounce(async (email: string, callback: Function) => {
  if (!email || !isValidEmail(email)) {
    callback();
    return;
  }

  try {
    emailValidating.value = true;
    const response = await api.get(`/api/auth/check-email?email=${encodeURIComponent(email)}`);
    const exists = response.data?.exists;

    if (exists) {
      callback(new Error('该邮箱已被注册'));
    } else {
      callback();
    }
  } catch (error) {
    console.error('检查邮箱失败:', error);
    // 如果API请求失败，我们不阻止表单提交
    callback();
  } finally {
    emailValidating.value = false;
  }
}, 500);

// 自定义用户名验证
const validateUsername = (_rule: any, value: string, callback: any) => {
  if (value === '') {
    callback(new Error('请输入用户名'));
    return;
  }

  if (value.length < 3 || value.length > 20) {
    callback(new Error('用户名长度应在 3-20 个字符之间'));
    return;
  }

  if (!/^[a-zA-Z0-9_\u4e00-\u9fa5]+$/.test(value)) {
    callback(new Error('用户名只能包含字母、数字、下划线和中文'));
    return;
  }

  // 仅在生产环境或有后端API时检查用户名
  if (import.meta.env.PROD || import.meta.env.VITE_API_BASE_URL) {
    checkUsernameExists(value, callback);
  } else {
    callback();
  }
};

// 自定义邮箱验证
const validateEmail = (_rule: any, value: string, callback: any) => {
  if (!value) {
    callback(new Error('请输入邮箱'));
    return;
  }

  if (!isValidEmail(value)) {
    callback(new Error('请输入正确的邮箱地址'));
    return;
  }

  // 仅在生产环境或有后端API时检查邮箱
  if (import.meta.env.PROD || import.meta.env.VITE_API_BASE_URL) {
    checkEmailExists(value, callback);
  } else {
    callback();
  }
};

const validatePass2 = (_rule: any, value: string, callback: any) => {
  if (value === '') {
    callback(new Error('请再次输入密码'));
  } else if (value !== form.password) {
    callback(new Error('两次输入密码不一致'));
  } else {
    callback();
  }
};

const validatePhone = (_rule: any, value: string, callback: any) => {
  if (!value) {
    callback();
    return;
  }
  if (!isValidPhone(value)) {
    callback(new Error('请输入正确的手机号'));
  } else {
    callback();
  }
};

const validateAgreement = (_rule: any, value: boolean, callback: any) => {
  if (!value) {
    callback(new Error('请阅读并同意用户协议和隐私政策'));
  } else {
    callback();
  }
};

// 更新验证规则
const rules = {
  username: [
    { validator: validateUsername, trigger: 'blur' }
  ],
  email: [
    { validator: validateEmail, trigger: 'blur' }
  ],
  phone: [
    { validator: validatePhone, trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度应在 6-20 个字符之间', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, validator: validatePass2, trigger: 'blur' },
  ],
  role: [
    { required: true, message: '请选择注册身份', trigger: 'change' },
  ],
  agreement: [
    { validator: validateAgreement, trigger: 'change' },
  ],
};

const handleRegister = async () => {
  if (!registerFormRef.value) return;

  try {
    await registerFormRef.value.validate();
    loading.value = true;

    const { confirmPassword, agreement, ...registerData } = form;
    console.log('提交注册信息:', registerData);

    try {
      // 注册
      const registerSuccess = await userStore.register(registerData);

      if (registerSuccess) {
        // 注册成功，根据角色提供不同的成功提示
        if (form.role === 'ROLE_HOST') {
          ElMessage.success({
            message: '恭喜您！已成功注册为房东。',
            duration: 3000
          });

          // 延迟跳转提示
          setTimeout(() => {
            ElMessage.info({
              message: '即将跳转到房东信息完善页面...',
              duration: 2000
            });
          }, 1000);

          // 跳转到房东信息完善引导页面
          setTimeout(() => {
            router.push('/host/onboarding');
          }, 3000);
        } else {
          ElMessage.success({
            message: '注册成功！欢迎加入我们的平台！',
            duration: 3000
          });

          // 普通用户跳转到首页
          setTimeout(() => {
            router.push('/');
          }, 2000);
        }
      } else {
        ElMessage.error('注册处理失败，请稍后重试');
      }
    } catch (error: any) {
      // 注册失败：拦截器已统一弹出后端返回的具体错误信息（如"邮箱已存在"/"用户名已存在"）
      console.error('注册失败详情:', error);
    }
  } catch (formError: any) {
    console.error('表单验证失败:', formError);
    ElMessage.error('请正确填写所有必填信息');
  } finally {
    loading.value = false;
  }
};

const goToLogin = () => {
  router.push('/login');
};

// 显示用户协议
const showUserAgreement = () => {
  ElMessageBox.alert(
    '这里是用户服务协议的内容...\n\n1. 用户权利和义务\n2. 平台服务内容\n3. 隐私保护\n4. 免责声明\n\n详细内容请访问我们的官方网站。',
    '用户服务协议',
    {
      confirmButtonText: '我知道了',
      type: 'info'
    }
  );
};

// 显示隐私政策
const showPrivacyPolicy = () => {
  ElMessageBox.alert(
    '这里是隐私政策的内容...\n\n1. 信息收集\n2. 信息使用\n3. 信息保护\n4. 第三方服务\n\n我们承诺保护您的隐私安全。',
    '隐私政策',
    {
      confirmButtonText: '我知道了',
      type: 'info'
    }
  );
};
</script>

<style scoped>
.register-container {
  --register-ink: #21332d;
  --register-muted: #6a7770;
  --register-paper: rgba(255, 252, 246, 0.92);
  --register-clay: #c56f44;
  --register-sage: #517764;
  --register-line: rgba(73, 99, 87, 0.14);
  --register-shadow: 0 28px 80px rgba(33, 51, 45, 0.18);

  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: 32px;
  overflow-x: hidden;
  overflow-y: auto;
  color: var(--register-ink);
  background:
    radial-gradient(circle at 12% 20%, rgba(241, 174, 110, 0.34), transparent 28%),
    radial-gradient(circle at 86% 78%, rgba(93, 131, 113, 0.22), transparent 31%),
    linear-gradient(135deg, #f8ebd6 0%, #eef4ec 48%, #d9e9ec 100%);
  font-family: "Avenir Next", "PingFang SC", "Microsoft YaHei", sans-serif;
}

.register-container::before {
  content: "";
  position: absolute;
  inset: 0;
  opacity: 0.3;
  background-image:
    linear-gradient(rgba(81, 119, 100, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(81, 119, 100, 0.08) 1px, transparent 1px);
  background-size: 34px 34px;
  mask-image: linear-gradient(120deg, transparent 0%, #000 24%, #000 76%, transparent 100%);
}

.ambient {
  position: absolute;
  border-radius: 999px;
  filter: blur(4px);
  pointer-events: none;
}

.ambient-sun {
  top: 8%;
  left: 9%;
  width: 190px;
  height: 190px;
  background: rgba(230, 138, 77, 0.22);
  animation: floatSlow 8s ease-in-out infinite;
}

.ambient-sage {
  right: 7%;
  bottom: 10%;
  width: 250px;
  height: 250px;
  background: rgba(76, 133, 136, 0.18);
  animation: floatSlow 10s ease-in-out infinite reverse;
}

.register-box {
  position: relative;
  z-index: 1;
  isolation: isolate;
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(520px, 1.1fr);
  width: min(1180px, 100%);
  min-height: 760px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 34px;
  overflow: hidden;
  box-shadow: var(--register-shadow);
  background: rgba(255, 255, 255, 0.44);
  backdrop-filter: blur(20px);
}

.register-left {
  position: relative;
  min-height: 760px;
  overflow: hidden;
  color: white;
  background:
    linear-gradient(180deg, rgba(31, 66, 58, 0.08), rgba(31, 66, 58, 0.52)),
    linear-gradient(135deg, #315b4c 0%, #5b8d7b 52%, #d98b55 100%);
}

.register-left::before {
  content: "";
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 28% 22%, rgba(255, 255, 255, 0.26), transparent 13%),
    linear-gradient(132deg, transparent 0 54%, rgba(255, 255, 255, 0.16) 54% 56%, transparent 56%),
    linear-gradient(18deg, rgba(20, 41, 36, 0.22) 0 24%, transparent 24% 100%);
  opacity: 0.86;
}

.register-overlay {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(180deg, rgba(17, 36, 31, 0.08) 0%, rgba(17, 36, 31, 0.64) 100%),
    radial-gradient(circle at 70% 18%, rgba(255, 220, 172, 0.36), transparent 20%);
}

.brand-pill {
  position: absolute;
  top: 32px;
  left: 34px;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border: 1px solid rgba(255, 255, 255, 0.36);
  border-radius: 999px;
  color: rgba(255, 255, 255, 0.92);
  background: rgba(255, 255, 255, 0.14);
  backdrop-filter: blur(14px);
  letter-spacing: 0.04em;
}

.brand-pill-icon {
  width: 24px;
  height: 24px;
}

.register-content {
  position: relative;
  z-index: 2;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 86px 52px 200px;
}

.register-eyebrow {
  margin: 0 0 18px;
  color: rgba(255, 255, 255, 0.74);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.2em;
}

.register-content h1 {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: 480px;
  margin: 0;
  font-family: "Noto Serif SC", "Source Han Serif SC", "Songti SC", serif;
  font-size: clamp(40px, 4.8vw, 60px);
  font-weight: 800;
  line-height: 1.08;
  letter-spacing: -0.06em;
  text-wrap: balance;
}

.register-description {
  max-width: 400px;
  margin: 24px 0 0;
  color: rgba(255, 255, 255, 0.78);
  font-size: 16px;
  line-height: 1.85;
}

.register-highlights {
  display: grid;
  gap: 12px;
  max-width: 420px;
  margin-top: 36px;
}

.highlight-item {
  padding: 16px 18px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(12px);
}

.highlight-item strong {
  display: block;
  font-size: 18px;
  line-height: 1.2;
}

.highlight-item span {
  display: block;
  margin-top: 8px;
  color: rgba(255, 255, 255, 0.72);
  font-size: 12px;
  line-height: 1.45;
}

.register-flow-card {
  position: absolute;
  right: 34px;
  bottom: 34px;
  left: 34px;
  z-index: 3;
  display: grid;
  grid-template-columns: 1fr 32px 1fr 32px 1fr;
  align-items: center;
  padding: 18px;
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: 28px;
  background:
    linear-gradient(135deg, rgba(255, 250, 241, 0.22), rgba(255, 250, 241, 0.1)),
    rgba(255, 250, 241, 0.16);
  backdrop-filter: blur(18px);
  box-shadow: 0 18px 46px rgba(20, 41, 36, 0.22);
  animation: riseIn 700ms ease both;
}

.flow-step {
  min-width: 0;
  text-align: center;
  color: rgba(255, 255, 255, 0.76);
}

.flow-step span {
  display: inline-grid;
  place-items: center;
  width: 32px;
  height: 32px;
  margin-bottom: 8px;
  border-radius: 999px;
  background: rgba(255, 250, 241, 0.18);
  font-weight: 800;
}

.flow-step strong {
  display: block;
  font-size: 13px;
}

.flow-step.is-active {
  color: #fffaf1;
}

.flow-step.is-active span {
  background: var(--register-clay);
  box-shadow: 0 0 0 5px rgba(255, 250, 241, 0.14);
}

.flow-line {
  height: 2px;
  border-top: 2px dashed rgba(255, 250, 241, 0.44);
}

.register-right {
  display: flex;
  align-items: center;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.86), rgba(255, 248, 237, 0.94)),
    var(--register-paper);
  padding: 42px 50px;
}

.register-form-container {
  width: 100%;
  max-width: 560px;
  margin: 0 auto;
}

.register-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 26px;
}

.logo-icon {
  width: 34px;
  height: 34px;
  color: var(--register-sage);
}

.register-logo h2 {
  color: var(--register-ink);
  font-size: 18px;
  font-weight: 800;
  margin: 0;
  letter-spacing: 0.03em;
}

.register-copy {
  margin-bottom: 24px;
}

.form-kicker {
  margin: 0 0 8px;
  color: var(--register-clay);
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.16em;
}

.register-form-container h3 {
  margin: 0;
  color: var(--register-ink);
  font-family: "Noto Serif SC", "Source Han Serif SC", "Songti SC", serif;
  font-size: 34px;
  font-weight: 800;
  letter-spacing: -0.04em;
}

.register-subtitle {
  color: var(--register-muted);
  margin: 10px 0 0;
  line-height: 1.75;
}

.register-card {
  border: 1px solid rgba(255, 255, 255, 0.86);
  border-radius: 28px;
  box-shadow: 0 18px 48px rgba(49, 91, 76, 0.12);
  background: rgba(255, 255, 255, 0.64);
}

.register-card :deep(.el-card__body) {
  padding: 28px;
}

.register-form-wrapper {
  position: relative;
}

.register-form-wrapper::before {
  content: "";
  position: absolute;
  top: -28px;
  right: 18px;
  width: 78px;
  height: 5px;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--register-clay), var(--register-sage));
}

:deep(.el-form-item) {
  margin-bottom: 14px;
}

:deep(.el-form-item__label) {
  padding-bottom: 9px;
  color: var(--register-ink);
  font-weight: 800;
}

:deep(.el-input__wrapper) {
  min-height: 46px;
  padding: 1px 16px;
  border-radius: 16px;
  background-color: rgba(255, 252, 246, 0.86);
  box-shadow: inset 0 0 0 1px var(--register-line);
  transition: box-shadow 180ms ease, transform 180ms ease, background-color 180ms ease;
}

:deep(.el-input__wrapper:hover) {
  background-color: #fffdf8;
  box-shadow: inset 0 0 0 1px rgba(81, 119, 100, 0.32);
}

:deep(.el-input__wrapper.is-focus) {
  background-color: #fffdf8;
  box-shadow: inset 0 0 0 2px rgba(197, 111, 68, 0.55), 0 10px 24px rgba(197, 111, 68, 0.12);
  transform: translateY(-1px);
}

:deep(.el-input__prefix) {
  margin-right: 8px;
  color: var(--register-sage);
}

.submit-btn {
  width: 100%;
  height: 50px;
  margin: 10px 0 16px;
  border: none;
  border-radius: 16px;
  color: #fffaf1;
  font-size: 16px;
  font-weight: 800;
  background: linear-gradient(135deg, #c56f44 0%, #315b4c 100%);
  box-shadow: 0 14px 30px rgba(49, 91, 76, 0.24);
  transition: transform 180ms ease, box-shadow 180ms ease, filter 180ms ease;
}

.submit-btn:not(.is-disabled):hover {
  transform: translateY(-2px);
  filter: saturate(1.08);
  box-shadow: 0 18px 36px rgba(49, 91, 76, 0.28);
}

.submit-btn:focus-visible,
.login-link :deep(.el-button:focus-visible),
.agreement-link:focus-visible {
  outline: 3px solid rgba(197, 111, 68, 0.34);
  outline-offset: 3px;
}

.submit-btn-ready {
  box-shadow: 0 16px 34px rgba(49, 91, 76, 0.3);
}

.submit-helper {
  margin: -6px 0 14px;
  color: var(--register-muted);
  font-size: 12px;
  line-height: 1.5;
  text-align: center;
}

.login-link {
  text-align: center;
  color: var(--register-muted);
}

.login-link :deep(.el-button),
.agreement-link {
  height: auto;
  padding: 0 2px;
  color: var(--register-clay);
  font-weight: 800;
  vertical-align: baseline;
}

.role-tip {
  margin-top: 8px;
}

.role-debug {
  margin-top: 8px;
}

.role-debug-text {
  font-size: 0.8rem;
  color: #909399;
}

.form-item-tip {
  margin-top: 7px;
  text-align: right;
}

.checking-text {
  font-size: 0.8rem;
  color: #909399;
}

.success-text {
  font-size: 0.8rem;
  color: #4f8a65;
}

.password-strength {
  margin-top: 8px;
  margin-bottom: 8px;
}

.strength-bars {
  display: flex;
  height: 6px;
  background-color: rgba(81, 119, 100, 0.12);
  border-radius: 999px;
  gap: 4px;
  margin-bottom: 6px;
}

.strength-bar {
  flex: 1;
  height: 100%;
  background-color: rgba(81, 119, 100, 0.16);
  border-radius: 999px;
}

.strength-bar.weak {
  background-color: #F56C6C;
}

.strength-bar.medium {
  background-color: #E6A23C;
}

.strength-bar.strong {
  background-color: #67C23A;
}

.strength-text {
  font-size: 0.8rem;
  color: var(--register-ink);
  margin-left: 8px;
}

.strength-text.weak {
  color: #F56C6C;
}

.strength-text.medium {
  color: #E6A23C;
}

.strength-text.strong {
  color: #67C23A;
}

.password-tips {
  margin-top: 8px;
  padding: 12px 14px;
  border: 1px solid var(--register-line);
  border-radius: 16px;
  background-color: rgba(255, 252, 246, 0.72);
}

.tip-title {
  font-size: 0.9rem;
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--register-ink);
}

.tip-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.tip-list li {
  font-size: 0.8rem;
  margin-bottom: 4px;
  color: var(--register-muted);
}

.tip-list li.tip-success {
  color: #4f8a65;
}

.role-group {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  width: 100%;
  gap: 12px;
}

.role-radio {
  height: auto;
  margin: 0;
  padding: 16px;
  border: 1px solid var(--register-line);
  border-radius: 18px;
  background: rgba(255, 252, 246, 0.7);
  transition: border-color 180ms ease, box-shadow 180ms ease, transform 180ms ease;
}

.role-radio:hover {
  border-color: rgba(197, 111, 68, 0.48);
  transform: translateY(-1px);
}

.role-radio.is-checked {
  border-color: rgba(197, 111, 68, 0.72);
  box-shadow: 0 12px 24px rgba(197, 111, 68, 0.12);
}

.role-radio :deep(.el-radio__input.is-focus .el-radio__inner) {
  box-shadow: 0 0 0 4px rgba(197, 111, 68, 0.2);
}

.role-radio :deep(.el-radio__label) {
  width: 100%;
  padding-left: 10px;
}

.role-option {
  display: flex;
  flex-direction: column;
}

.role-title {
  font-weight: 800;
  color: var(--register-ink);
}

.role-desc {
  font-size: 0.8rem;
  color: var(--register-muted);
  margin-top: 2px;
}

.agreement-checkbox {
  align-items: flex-start;
  line-height: 1.6;
  color: var(--register-muted);
}

@keyframes floatSlow {
  0%, 100% {
    transform: translate3d(0, 0, 0);
  }

  50% {
    transform: translate3d(16px, -18px, 0);
  }
}

@keyframes riseIn {
  from {
    opacity: 0;
    transform: translateY(18px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 768px) {
  .register-container {
    min-height: 100svh;
    padding: 18px;
  }

  .register-box {
    display: block;
    width: 100%;
    min-height: auto;
    border-radius: 26px;
  }

  .register-left {
    display: none;
  }

  .register-right {
    padding: 28px 18px;
  }

  .register-form-container {
    max-width: none;
  }

  .register-logo {
    margin-bottom: 24px;
  }

  .register-form-container h3 {
    font-size: 30px;
  }

  .register-card :deep(.el-card__body) {
    padding: 22px 18px;
  }

  .role-group {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 480px) {
  .register-container {
    padding: 12px;
  }

  .register-box {
    border-radius: 22px;
  }

  .register-right {
    padding: 24px 14px;
  }

  .register-card :deep(.el-card__body) {
    padding: 20px 16px;
  }

  .agreement-checkbox {
    font-size: 13px;
  }
}

@media (min-width: 769px) and (max-width: 1080px) {
  .register-box {
    grid-template-columns: 0.78fr 1.22fr;
  }

  .register-left {
    min-height: 720px;
  }

  .register-content {
    padding: 78px 34px 200px;
  }

  .register-content h1 {
    font-size: 44px;
  }

  .register-right {
    padding: 36px 28px;
  }
}

@media (max-height: 860px) and (min-width: 769px) {
  .register-container {
    align-items: flex-start;
    padding-top: 24px;
    padding-bottom: 24px;
  }

  .register-box,
  .register-left {
    min-height: calc(100vh - 48px);
  }

  .register-right {
    align-items: flex-start;
  }

  .register-content {
    padding-top: 76px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ambient,
  .register-flow-card,
  .submit-btn,
  :deep(.el-input__wrapper),
  .role-radio {
    animation: none;
    transition: none;
  }
}
</style>
