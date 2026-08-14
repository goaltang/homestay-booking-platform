<template>
  <div class="login-container">
    <div class="ambient ambient-sun"></div>
    <div class="ambient ambient-lake"></div>

    <main class="login-box">
      <section class="login-left" aria-label="民宿预订平台介绍">
        <div class="login-overlay"></div>
        <div class="brand-pill">
          <Logo class="brand-pill-icon" />
          <span>Homestay Select</span>
        </div>

        <div class="login-content">
          <p class="login-eyebrow">STAY LOCAL · TRAVEL BETTER</p>
          <h1>
            <span>住进风景里，</span>
            <span>也住进生活里。</span>
          </h1>
          <p class="login-description">
            从城市天台到海边小院，收藏真实房东、安心订单和下一次刚刚好的出发。
          </p>

          <div class="login-highlights">
            <div class="highlight-item">
              <strong>安心入住</strong>
              <span>订单状态清晰可追踪</span>
            </div>
            <div class="highlight-item">
              <strong>真实房东</strong>
              <span>房源信息统一管理</span>
            </div>
            <div class="highlight-item">
              <strong>灵活出行</strong>
              <span>收藏、预订、入住一站完成</span>
            </div>
          </div>
        </div>

        <div class="journey-card" aria-hidden="true">
          <div class="journey-card-visual">
            <span class="route-dot route-dot-start"></span>
            <span class="route-line"></span>
            <span class="route-dot route-dot-end"></span>
          </div>
          <div class="journey-card-info">
            <span class="journey-card-tag">登录后继续</span>
            <strong>查看订单、收藏与房东工作台</strong>
            <p>你的旅行计划会在这里安全保存。</p>
          </div>
        </div>
      </section>

      <section class="login-right" aria-label="用户登录">
        <div class="login-form-container">
          <div class="login-logo">
            <Logo class="logo-icon" />
            <h2>民宿预订平台</h2>
          </div>

          <div class="login-copy">
            <p class="form-kicker">安全登录</p>
            <h3>欢迎回来</h3>
            <p class="login-subtitle">登录后继续管理订单、收藏和房东工作台。</p>
          </div>

          <el-card class="login-card">
            <div class="login-form-wrapper">
              <el-form ref="loginFormRef" :model="loginForm" :rules="rules" label-position="top"
                :validate-on-rule-change="false" @submit.prevent="handleLogin">
                <el-form-item label="用户名" prop="username">
                  <el-input ref="usernameInputRef" v-model="loginForm.username" placeholder="请输入用户名" prefix-icon="User" name="username"
                    autocomplete="username" :disabled="loading" @keyup.enter.stop.prevent="handleLogin" @blur="validateField('username')"
                    clearable />
                </el-form-item>

                <el-form-item label="密码" prop="password">
                  <el-input ref="passwordInputRef" v-model="loginForm.password" type="password" placeholder="请输入密码" prefix-icon="Lock"
                    name="current-password" autocomplete="current-password" show-password :disabled="loading" @keyup.enter.stop.prevent="handleLogin"
                    @keydown="handlePasswordKeyState" @keyup="handlePasswordKeyState"
                    @blur="handlePasswordBlur" clearable />
                  <p v-if="capsLockOn" class="field-tip field-tip-warning">已开启大写锁定，密码可能输入错误。</p>
                </el-form-item>

                <div class="remember-forgot">
                  <el-checkbox v-model="loginForm.remember">记住我</el-checkbox>
                  <el-button type="text" @click.stop.prevent="goToForgotPassword" :disabled="loading">忘记密码？</el-button>
                </div>

                <el-button native-type="submit" type="primary" class="submit-btn" :class="{ 'submit-btn-ready': canSubmit }"
                  :loading="loading" :disabled="loading">
                  {{ loading ? '登录中...' : '登录' }}
                </el-button>
                <p class="submit-helper">支持回车登录，成功后会回到你刚才访问的页面。</p>

                <div class="admin-entry">
                  <span>平台管理员？</span>
                  <el-button type="text" @click.stop.prevent="goToAdminLogin" :disabled="loading">前往管理后台</el-button>
                </div>

                <div v-if="showQuickLogin" class="fast-login-section">
                  <el-divider content-position="center">测试账号一键登录</el-divider>
                  <div class="fast-login-options">
                    <el-button
                      v-for="account in quickLoginAccounts"
                      :key="account.username"
                      size="small"
                      :type="account.type"
                      plain
                      @click.stop.prevent="fastLogin(account.username, account.password)"
                      :loading="loading"
                    >
                      {{ account.icon }} {{ account.label }}
                    </el-button>
                  </div>
                </div>

                <div class="register-link">
                  还没有账号？
                  <el-button type="text" @click.stop.prevent="goToRegister" :disabled="loading">立即注册</el-button>
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
import { ref, reactive, onMounted, computed, nextTick, shallowRef } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useUserStore } from "@/stores/user";
import type { FormInstance } from "element-plus";
import { ElMessage } from "element-plus";
import Logo from "@/components/common/Logo.vue";

type LoginError = {
  displayMessage?: string;
  message?: string;
  response?: {
    status?: number;
    data?: {
      message?: string;
      error?: string;
      msg?: string;
    } | string;
  };
};

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const loginFormRef = ref<FormInstance>();
const usernameInputRef = ref();
const passwordInputRef = ref();
const loading = shallowRef(false);
const capsLockOn = shallowRef(false);
const lastLoginAttempt = shallowRef(0);
const USERNAME_PATTERN = /^[a-zA-Z0-9_\u4e00-\u9fa5]+$/;
const AUTH_PAGE_PATHS = ["/login", "/register", "/forgot-password", "/reset-password"];
const quickLoginAccounts = [
  { username: "user", password: "111111", label: "普通用户", icon: "👤", type: "success" },
  { username: "host", password: "111111", label: "房东账号", icon: "🏠", type: "warning" },
] as const;

const loginForm = reactive({
  username: "",
  password: "",
  remember: false
});

const normalizedUsername = computed(() => loginForm.username.trim());
const showQuickLogin = computed(() => import.meta.env.DEV || import.meta.env.VITE_SHOW_TEST_ACCOUNTS === "true");

const getAdminLoginUrl = () => {
  const configuredBaseUrl = import.meta.env.VITE_ADMIN_BASE_URL?.trim();
  const defaultBaseUrl =
    window.location.port === "5173"
      ? `${window.location.protocol}//${window.location.hostname}:5174`
      : "/admin";
  const baseUrl = configuredBaseUrl || defaultBaseUrl;
  const normalizedBaseUrl = baseUrl.replace(/\/+$/, "");

  if (/^https?:\/\//i.test(normalizedBaseUrl)) {
    return `${normalizedBaseUrl}/login`;
  }

  return `${normalizedBaseUrl}/login`;
};

const adminLoginUrl = computed(() => getAdminLoginUrl());

const getRouteQueryMessage = () => {
  const message = route.query.message;
  return Array.isArray(message) ? message[0] : message;
};

const getSafeRedirectPath = (fallbackPath: string) => {
  const redirect = route.query.redirect;
  if (typeof redirect !== "string" || !redirect.startsWith("/") || redirect.startsWith("//")) {
    return fallbackPath;
  }

  try {
    const resolved = router.resolve(redirect);
    if (AUTH_PAGE_PATHS.some((path) => resolved.path.startsWith(path))) {
      return fallbackPath;
    }
    return redirect;
  } catch {
    return fallbackPath;
  }
};

const persistRememberedUsername = () => {
  if (loginForm.remember) {
    localStorage.setItem("rememberedUsername", normalizedUsername.value);
  } else {
    localStorage.removeItem("rememberedUsername");
  }
};

const extractLoginErrorMessage = (error: LoginError) => {
  if (error.displayMessage) return error.displayMessage;

  const responseData = error.response?.data;
  if (responseData) {
    if (typeof responseData === "string") return responseData;
    if (responseData.message) return responseData.message;
    if (responseData.error) return responseData.error;
    if (responseData.msg) return responseData.msg;
  }

  if (error.response?.status === 401) return "用户名或密码错误，请重新输入";
  if (error.response?.status === 404) return "用户不存在，请检查用户名或先注册账号";
  if (error.response?.status === 429) return "登录尝试过于频繁，请稍后再试";
  if (error.response?.status && error.response.status >= 500) return "服务器暂时无法连接，请稍后重试";
  if (error.message?.includes("Network Error")) return "网络连接失败，请检查网络设置";
  if (error.message) return error.message;

  return "登录失败，请重试";
};

// 表单验证规则
const rules = {
  username: [
    { required: true, message: "请输入用户名", trigger: "blur" },
    { min: 3, max: 20, message: "用户名长度应在 3-20 个字符之间", trigger: "blur" },
    { pattern: USERNAME_PATTERN, message: "用户名只能包含字母、数字、下划线和中文", trigger: "blur" }
  ],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { min: 6, max: 20, message: "密码长度应在 6-20 个字符之间", trigger: "blur" },
  ],
};

// 计算表单是否有效
const canSubmit = computed(() => {
  return normalizedUsername.value.length >= 3 &&
    loginForm.password.length >= 6 &&
    normalizedUsername.value.length <= 20 &&
    loginForm.password.length <= 20 &&
    USERNAME_PATTERN.test(normalizedUsername.value);
});

// 单个字段验证
const validateField = async (prop: string) => {
  if (!loginFormRef.value) return;
  try {
    await loginFormRef.value.validateField(prop);
  } catch {
    // 验证失败时不做任何操作，错误信息已经显示
  }
};

const handlePasswordKeyState = (event: KeyboardEvent) => {
  capsLockOn.value = event.getModifierState("CapsLock");
};

const handlePasswordBlur = async () => {
  capsLockOn.value = false;
  await validateField("password");
};

// 处理登录
const handleLogin = async (event?: Event) => {
  // 阻止任何可能的默认行为
  if (event) {
    event.preventDefault();
    event.stopPropagation();
  }

  if (!loginFormRef.value || loading.value) {
    return false;
  }

  try {
    loginForm.username = normalizedUsername.value;

    // 验证表单
    await loginFormRef.value.validate();

    // 防抖机制：防止快速重复点击
    const now = Date.now();
    if (now - lastLoginAttempt.value < 1000) {
      return false;
    }
    lastLoginAttempt.value = now;

    loading.value = true;
    console.log("开始登录验证:", { username: normalizedUsername.value });

    const success = await userStore.login(normalizedUsername.value, loginForm.password);

    if (success) {
      console.log("登录成功，获取用户信息中...");

      try {
        // 获取完整用户信息
        const userData = await userStore.fetchUserInfo();
        console.log("用户信息获取结果:", userData);

        // 如果 fetchUserInfo 返回 null 或 undefined，说明获取失败
        if (!userData || !userStore.userInfo) {
          throw new Error("获取用户信息失败，请重新登录");
        }

        ElMessage.success({
          message: `欢迎回来，${userStore.userInfo?.realName || userStore.userInfo?.username || '用户'}！`,
          duration: 2000
        });

        persistRememberedUsername();

        // 导航逻辑
        await handleLoginSuccess();

      } catch (fetchError: any) {
        console.error("获取用户信息失败:", fetchError);
        userStore.logout();
      }

    } else {
      ElMessage.error("登录失败，请检查用户名和密码");
    }

  } catch (error: unknown) {
    console.error("登录过程中出错:", error);

    // 确保即使发生错误也阻止页面刷新
    if (event) {
      event.preventDefault();
      event.stopPropagation();
    }

    const errorMessage = extractLoginErrorMessage(error as LoginError);

    // 显示错误信息
    ElMessage.error(errorMessage);

    // 如果是用户不存在的错误，延迟显示注册提示
    if (errorMessage.includes("用户不存在")) {
      setTimeout(() => {
        ElMessage.info('还没有账号？点击下方"立即注册"创建新账号');
      }, 1500);
    }
  } finally {
    loading.value = false;
  }

  return false; // 确保不会触发任何默认提交行为
};

// 处理登录成功后的导航
const handleLoginSuccess = async () => {
  await nextTick(); // 确保 DOM 更新完成

  // 从最新的 userInfo 中获取角色，确保使用 fetchUserInfo 后的最新数据
  const role = userStore.userInfo?.role?.toUpperCase() || '';

  console.log("导航决策:", {
    role: userStore.userInfo?.role,
    normalizedRole: role,
    isAdmin: userStore.isAdmin,
    isLandlord: userStore.isLandlord
  });

  // 管理员 → 管理后台
  if (userStore.isAdmin) {
    window.location.assign(getAdminLoginUrl());
    return;
  }

  // 房东 → 房东管理页
  if (userStore.isLandlord) {
    await router.push(getSafeRedirectPath('/host'));
    return;
  }

  // 普通用户 → 检查 redirect 参数，否则去首页
  await router.push(getSafeRedirectPath('/'));
};

// 跳转到注册页
const goToRegister = () => {
  if (loading.value) return;
  router.push("/register");
};

// 跳转到忘记密码页
const goToForgotPassword = () => {
  if (loading.value) return;
  router.push("/forgot-password");
};

const goToAdminLogin = () => {
  if (loading.value) return;
  window.location.assign(adminLoginUrl.value);
};

// 一键快速登录
const fastLogin = async (username: string, password: string = '111111') => {
  if (loading.value || !showQuickLogin.value) return;
  loginForm.username = username;
  loginForm.password = password;
  loginForm.remember = true;
  await nextTick();
  handleLogin();
};

// 页面加载完成后的初始化
onMounted(async () => {
  // 处理路由消息
  const routeMessage = getRouteQueryMessage();
  if (routeMessage) {
    ElMessage.info(routeMessage);
  }

  // 恢复记住的用户名
  const rememberedUsername = localStorage.getItem('rememberedUsername');
  if (rememberedUsername) {
    loginForm.username = rememberedUsername;
    loginForm.remember = true;
  }

  // 恢复用户名时聚焦密码，否则聚焦用户名，减少一次手动切换。
  await nextTick();
  const inputToFocus = rememberedUsername ? passwordInputRef.value : usernameInputRef.value;
  inputToFocus?.focus?.();
});
</script>

<style scoped>
.login-container {
  --login-ink: #21332d;
  --login-muted: #6a7770;
  --login-cream: #fff8ed;
  --login-paper: rgba(255, 252, 246, 0.92);
  --login-clay: #c56f44;
  --login-sage: #517764;
  --login-sky: #dfeee9;
  --login-line: rgba(73, 99, 87, 0.14);
  --login-shadow: 0 28px 80px rgba(33, 51, 45, 0.18);

  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: 32px;
  overflow-x: hidden;
  overflow-y: auto;
  color: var(--login-ink);
  background:
    radial-gradient(circle at 14% 16%, rgba(241, 174, 110, 0.38), transparent 28%),
    radial-gradient(circle at 88% 78%, rgba(93, 131, 113, 0.24), transparent 30%),
    linear-gradient(135deg, #f8ebd6 0%, #edf4eb 48%, #d9e9ec 100%);
  font-family: "Avenir Next", "PingFang SC", "Microsoft YaHei", sans-serif;
}

.login-container::before {
  content: "";
  position: absolute;
  inset: 0;
  opacity: 0.32;
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
  left: 10%;
  width: 180px;
  height: 180px;
  background: rgba(230, 138, 77, 0.24);
  animation: floatSlow 8s ease-in-out infinite;
}

.ambient-lake {
  right: 7%;
  bottom: 10%;
  width: 240px;
  height: 240px;
  background: rgba(76, 133, 136, 0.18);
  animation: floatSlow 10s ease-in-out infinite reverse;
}

.login-box {
  position: relative;
  z-index: 1;
  isolation: isolate;
  display: grid;
  grid-template-columns: minmax(0, 1.04fr) minmax(420px, 0.96fr);
  width: min(1120px, 100%);
  min-height: 680px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 34px;
  overflow: hidden;
  box-shadow: var(--login-shadow);
  background: rgba(255, 255, 255, 0.44);
  backdrop-filter: blur(20px);
}

.login-left {
  position: relative;
  min-height: 680px;
  overflow: hidden;
  color: white;
  background:
    linear-gradient(180deg, rgba(31, 66, 58, 0.08), rgba(31, 66, 58, 0.5)),
    linear-gradient(135deg, #315b4c 0%, #5b8d7b 48%, #d98b55 100%);
}

.login-left::before {
  content: "";
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 32% 24%, rgba(255, 255, 255, 0.3), transparent 13%),
    linear-gradient(130deg, transparent 0 56%, rgba(255, 255, 255, 0.16) 56% 58%, transparent 58%),
    linear-gradient(18deg, rgba(20, 41, 36, 0.22) 0 24%, transparent 24% 100%);
  opacity: 0.86;
}

.login-overlay {
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

.login-content {
  position: relative;
  z-index: 2;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 86px 56px 190px;
}

.login-eyebrow {
  margin: 0 0 18px;
  color: rgba(255, 255, 255, 0.74);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.22em;
}

.login-content h1 {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: 460px;
  margin: 0;
  font-family: "Noto Serif SC", "Source Han Serif SC", "Songti SC", serif;
  font-size: clamp(42px, 5vw, 64px);
  font-weight: 800;
  line-height: 1.05;
  letter-spacing: -0.06em;
  text-wrap: balance;
}

.login-description {
  max-width: 390px;
  margin: 24px 0 0;
  color: rgba(255, 255, 255, 0.78);
  font-size: 16px;
  line-height: 1.85;
}

.login-highlights {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  max-width: 430px;
  margin-top: 38px;
}

.highlight-item {
  padding: 16px 14px;
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

.journey-card {
  position: absolute;
  right: 34px;
  bottom: 34px;
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 18px;
  width: min(400px, calc(100% - 68px));
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

.journey-card-visual {
  position: relative;
  flex: 0 0 104px;
  height: 92px;
  border-radius: 24px;
  overflow: hidden;
  background:
    radial-gradient(circle at 78% 22%, rgba(255, 239, 189, 0.95) 0 8%, transparent 9%),
    linear-gradient(160deg, rgba(36, 66, 57, 0.96) 0 35%, rgba(121, 168, 144, 0.94) 35% 62%, rgba(240, 189, 127, 0.9) 62% 100%);
}

.route-dot,
.route-line {
  position: absolute;
  z-index: 2;
}

.route-dot {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(255, 250, 241, 0.92);
  border-radius: 999px;
  background: var(--login-clay);
  box-shadow: 0 0 0 4px rgba(255, 250, 241, 0.18);
}

.route-dot-start {
  left: 20px;
  bottom: 22px;
}

.route-dot-end {
  top: 22px;
  right: 22px;
  background: var(--login-sage);
}

.route-line {
  left: 30px;
  right: 30px;
  top: 45px;
  height: 2px;
  border-top: 2px dashed rgba(255, 250, 241, 0.72);
  transform: rotate(-24deg);
  transform-origin: center;
}

.journey-card-info {
  display: flex;
  min-width: 0;
  flex-direction: column;
  justify-content: center;
}

.journey-card-tag {
  width: fit-content;
  margin-bottom: 8px;
  padding: 4px 9px;
  border-radius: 999px;
  color: #fff2dc;
  background: rgba(31, 58, 50, 0.36);
  font-size: 12px;
}

.journey-card-info strong {
  color: #fffaf1;
  font-size: 17px;
  line-height: 1.45;
}

.journey-card-info p {
  margin: 7px 0 0;
  color: rgba(255, 255, 255, 0.72);
  font-size: 13px;
  line-height: 1.5;
}

.login-right {
  display: flex;
  align-items: center;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.86), rgba(255, 248, 237, 0.94)),
    var(--login-paper);
  padding: 54px;
}

.login-form-container {
  width: 100%;
  max-width: 430px;
  margin: 0 auto;
}

.login-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 34px;
}

.logo-icon {
  width: 34px;
  height: 34px;
  color: var(--login-sage);
}

.login-logo h2 {
  color: var(--login-ink);
  font-size: 18px;
  font-weight: 800;
  margin: 0;
  letter-spacing: 0.03em;
}

.login-copy {
  margin-bottom: 28px;
}

.form-kicker {
  margin: 0 0 8px;
  color: var(--login-clay);
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.16em;
}

.login-form-container h3 {
  margin: 0;
  color: var(--login-ink);
  font-family: "Noto Serif SC", "Source Han Serif SC", "Songti SC", serif;
  font-size: 34px;
  font-weight: 800;
  letter-spacing: -0.04em;
}

.login-subtitle {
  color: var(--login-muted);
  margin: 10px 0 0;
  line-height: 1.75;
}

.login-card {
  border: 1px solid rgba(255, 255, 255, 0.86);
  border-radius: 28px;
  box-shadow: 0 18px 48px rgba(49, 91, 76, 0.12);
  background: rgba(255, 255, 255, 0.64);
}

.login-card :deep(.el-card__body) {
  padding: 28px;
}

.login-form-wrapper {
  position: relative;
}

.login-form-wrapper::before {
  content: "";
  position: absolute;
  top: -28px;
  right: 18px;
  width: 78px;
  height: 5px;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--login-clay), var(--login-sage));
}

:deep(.el-form-item) {
  margin-bottom: 8px;
}

:deep(.el-form-item__label) {
  padding-bottom: 9px;
  color: var(--login-ink);
  font-weight: 800;
}

:deep(.el-input__wrapper) {
  min-height: 48px;
  padding: 1px 16px;
  border-radius: 16px;
  background-color: rgba(255, 252, 246, 0.86);
  box-shadow: inset 0 0 0 1px var(--login-line);
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
  color: var(--login-sage);
}

.remember-forgot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 18px 0 22px;
}

.remember-forgot :deep(.el-button),
.admin-entry :deep(.el-button),
.register-link :deep(.el-button) {
  color: var(--login-clay);
  font-weight: 800;
}

.field-tip {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.4;
}

.field-tip-warning {
  color: var(--el-color-warning);
}

.submit-btn {
  width: 100%;
  height: 50px;
  margin-bottom: 14px;
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
.remember-forgot :deep(.el-button:focus-visible),
.admin-entry :deep(.el-button:focus-visible),
.register-link :deep(.el-button:focus-visible) {
  outline: 3px solid rgba(197, 111, 68, 0.34);
  outline-offset: 3px;
}

.submit-btn-ready {
  box-shadow: 0 16px 34px rgba(49, 91, 76, 0.3);
}

.submit-helper {
  margin: -4px 0 14px;
  color: var(--login-muted);
  font-size: 12px;
  line-height: 1.5;
  text-align: center;
}

.admin-entry {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 4px;
  margin-bottom: 18px;
  color: var(--login-muted);
  font-size: 14px;
}

.fast-login-section {
  margin-bottom: 20px;
}

:deep(.el-divider__text) {
  color: var(--login-muted);
  font-size: 12px;
  font-weight: 700;
  background-color: transparent;
}

:deep(.el-divider--horizontal) {
  margin: 18px 0;
  border-top-color: var(--login-line);
}

.fast-login-options {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.fast-login-options .el-button {
  flex: 1;
  min-height: 38px;
  border-radius: 14px;
  font-weight: 800;
}

.register-link {
  text-align: center;
  color: var(--login-muted);
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
  .login-container {
    min-height: 100svh;
    padding: 18px;
  }

  .login-box {
    display: block;
    width: 100%;
    min-height: auto;
    border-radius: 26px;
  }

  .login-left {
    display: none;
  }

  .login-right {
    padding: 28px 18px;
  }

  .login-form-container {
    max-width: none;
  }

  .login-logo {
    margin-bottom: 24px;
  }

  .login-form-container h3 {
    font-size: 30px;
  }

  .login-card :deep(.el-card__body) {
    padding: 22px 18px;
  }

  .fast-login-options {
    flex-direction: column;
  }
}

@media (max-width: 480px) {
  .login-container {
    padding: 12px;
  }

  .login-box {
    border-radius: 22px;
  }

  .login-right {
    padding: 24px 14px;
  }

  .login-card :deep(.el-card__body) {
    padding: 20px 16px;
  }

  .remember-forgot {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }
}

@media (min-width: 769px) and (max-width: 1040px) {
  .login-box {
    grid-template-columns: 0.86fr 1.14fr;
  }

  .login-left {
    min-height: 640px;
  }

  .login-content {
    padding: 78px 36px 180px;
  }

  .login-content h1 {
    font-size: 46px;
  }

  .login-right {
    padding: 40px 30px;
  }
}

@media (max-height: 760px) and (min-width: 769px) {
  .login-container {
    align-items: flex-start;
    padding-top: 24px;
    padding-bottom: 24px;
  }

  .login-box,
  .login-left {
    min-height: calc(100vh - 48px);
  }

  .login-content {
    padding-top: 76px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ambient,
  .journey-card,
  .submit-btn,
  :deep(.el-input__wrapper) {
    animation: none;
    transition: none;
  }
}
</style>
