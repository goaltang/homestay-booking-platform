import { defineStore } from "pinia";
import { isAxiosError } from "axios";
import { computed, ref, shallowRef } from "vue";
import { getUserInfo, login as loginApi, logout as logoutApi } from "@/api/auth";
import type { Admin, LoginParams } from "@/api/auth";

const TOKEN_KEY = "homestay_admin_token";
const USER_KEY = "homestay_admin_user";
const USERNAME_KEY = "homestay_admin_username";
const USERID_KEY = "homestay_admin_userId";

function normalizeAdmin(value: Admin): Admin {
  const role = value.role
    .trim()
    .toUpperCase()
    .replace(/^ROLE_/, "");
  if (role !== "ADMIN") throw new Error("请使用管理员账号登录");
  return { ...value, role };
}

export const useUserStore = defineStore("user", () => {
  const token = shallowRef(localStorage.getItem(TOKEN_KEY) || "");
  const userInfo = ref<Admin | null>(null);
  const initialized = shallowRef(false);
  let initialization: Promise<void> | undefined;

  const isLoggedIn = computed(() => !!token.value && userInfo.value?.role === "ADMIN");
  const username = computed(() => userInfo.value?.username);
  const role = computed(() => userInfo.value?.role);

  function setToken(value: string) {
    token.value = value;
    userInfo.value = null;
    initialized.value = false;
    localStorage.setItem(TOKEN_KEY, value);
  }

  function setUserInfo(value: Admin) {
    value = normalizeAdmin(value);
    userInfo.value = value;
    localStorage.setItem(USER_KEY, JSON.stringify(value));
    localStorage.setItem(USERNAME_KEY, value.username);
    localStorage.setItem(USERID_KEY, String(value.id));
  }

  function clearSession() {
    token.value = "";
    userInfo.value = null;
    initialized.value = true;
    for (const key of [TOKEN_KEY, USER_KEY, USERNAME_KEY, USERID_KEY]) {
      localStorage.removeItem(key);
    }
  }

  async function login(data: LoginParams) {
    const result = await loginApi(data);
    try {
      if (!result.token || !result.admin) throw new Error("请使用管理员账号登录");
      const admin = normalizeAdmin(result.admin);
      setToken(result.token);
      setUserInfo(admin);
    } catch (error) {
      clearSession();
      throw error;
    }
    initialized.value = true;
  }

  async function initialize() {
    if (initialized.value) return;
    if (initialization) return initialization;
    if (!token.value) {
      clearSession();
      return;
    }
    const currentToken = token.value;
    initialization = (async () => {
      try {
        const admin = await getUserInfo();
        if (token.value === currentToken) setUserInfo(admin);
      } catch (error) {
        if (token.value === currentToken) {
          if (isAxiosError(error) && !error.response) {
            // 暂时无法验证：拒绝授权，保留 Bearer 供刷新后重试。
            userInfo.value = null;
          } else {
            clearSession();
          }
        }
      } finally {
        if (token.value === currentToken) initialized.value = true;
        initialization = undefined;
      }
    })();
    return initialization;
  }

  async function logout() {
    try {
      if (token.value) await logoutApi();
    } finally {
      clearSession();
    }
  }

  return {
    token,
    userInfo,
    initialized,
    isLoggedIn,
    username,
    role,
    setToken,
    setUserInfo,
    clearSession,
    login,
    initialize,
    logout,
  };
});
