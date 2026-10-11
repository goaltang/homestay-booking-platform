import { defineStore } from "pinia";
import { ref, computed } from "vue";
import api from "@/api";
import router from "@/router";
import { RegisterRequest } from "@/types/auth";
import { ElMessage } from "element-plus";
import { initWebSocket, disconnectWebSocket, setAuthToken } from "@/services/websocketService";
import { useNotificationStore } from "@/stores/notification";
import { extractErrorMessage, ApiRequestError } from "@/types/error";
import { extractRole, normalizeAvatarUrl, normalizeRole } from "@/utils/userHelpers";
import { useChatStore } from "@/stores/chat";

export interface UserInfo {
  id: number;
  username: string;
  email: string;
  phone?: string;
  realName?: string;
  idCard?: string;
  role: string;
  avatar?: string;
  verificationStatus?: string;
  authorities?: { authority: string }[];
  nickname?: string;
  gender?: string;
  birthday?: string;
  occupation?: string;
  introduction?: string;
  languages?: string;
  frequentGuests?: string;
}

// 添加新的类型定义
interface ProfileUpdateRequest {
  username: string;
  email: string;
  phone?: string;
  realName?: string;
  idCard?: string;
  avatar?: string;
  nickname?: string;
  gender?: string;
  birthday?: string;
  occupation?: string;
  introduction?: string;
  languages?: string;
  frequentGuests?: string;
}

interface PasswordChangeRequest {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
}

const USER_KEY = "homestay_user";

const log = (...args: unknown[]) => {
  if (import.meta.env.DEV) console.log(...args);
};

export const useUserStore = defineStore("user", () => {
  // token 迁移后，JWT 存 httpOnly cookie + 内存（供 WebSocket），不落 localStorage
  const token = ref<string | null>(null);
  const userInfo = ref<UserInfo | null>(null);
  const initialized = ref(false);
  let initialization: Promise<void> | null = null;
  let sessionVersion = 0;

  // 登录态以 userInfo 为准（token 在 httpOnly cookie，JS 不可读）
  const isAuthenticated = computed(() => !!userInfo.value);
  const normalizedRole = computed(() => {
    const role = normalizeRole(userInfo.value?.role || "");
    return role ? `ROLE_${role}` : "";
  });

  /** @deprecated 请直接使用 userInfo */
  const user = computed(() => userInfo.value);
  /** @deprecated 请直接使用 normalizedRole */
  const userRole = computed(() => normalizedRole.value);
  const isAdmin = computed(() => normalizedRole.value === "ROLE_ADMIN");
  const isLandlord = computed(() => normalizedRole.value === "ROLE_HOST");

  const unreadNotificationCount = ref<number>(0);

  const setToken = (newToken: string | null) => {
    token.value = newToken;
    // 同步内存 token 到 WebSocket 服务（供 STOMP 握手用）
    setAuthToken(newToken);
  };

  const setUser = (user: UserInfo) => {
    userInfo.value = user;
    // 保存用户信息到 localStorage
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    // 刷新后内存 token 可能为 null，但 WebSocket 握手支持 httpOnly cookie 认证
    if (user.id) {
      initWebSocket(user.id);
    }
    // 输出调试信息
    log("用户角色:", user.role);
    log("isLandlord计算值:", user.role === "ROLE_HOST");
  };

  const login = async (username: string, password: string) => {
    try {
      // 调用后端登录API
      const response = await api.post("/api/auth/login", {
        username,
        password,
      });

      if (response.data) {
        // token 由后端写入 httpOnly cookie；这里仅记录到内存供 WebSocket 使用
        if (response.data.token) {
          setToken(response.data.token);
        }

        // 统一提取角色（嵌套 user 对象 > 顶层 role > authorities）
        const role = extractRole(response.data);
        log("最终确定的用户角色:", role);

        // 优先使用嵌套user对象中的字段
        const userObj = response.data.user;
        const userData = {
          id: userObj?.id || response.data.id || 0,
          username: userObj?.username || response.data.username || username,
          email: userObj?.email || response.data.email || "",
          phone: userObj?.phone || response.data.phone || "",
          realName: userObj?.realName || response.data.realName || "",
          idCard: userObj?.idCard || response.data.idCard || "",
          role: role || "ROLE_USER", // 确保始终有角色
          avatar: normalizeAvatarUrl(userObj?.avatar || response.data.avatar),
          verificationStatus: userObj?.verificationStatus || response.data.verificationStatus || "",
        };

        sessionVersion += 1;
        initialized.value = true;
        setUser(userData);

        await fetchUnreadCount();

        return true;
      }
      return false;
    } catch (error) {
      console.error("登录失败:", extractErrorMessage(error));

      throw error;
    }
  };

  const register = async (registerData: Omit<RegisterRequest, "confirmPassword">) => {
    try {
      // 确保角色信息是大写且格式正确
      if (registerData.role && !registerData.role.startsWith("ROLE_")) {
        registerData.role = `ROLE_${registerData.role.toUpperCase()}`;
      }

      // 添加后端API调用
      const response = await api.post("/api/auth/register", registerData);

      if (response.data) {
        // token 由后端写入 httpOnly cookie；这里仅记录到内存供 WebSocket 使用
        if (response.data.token) {
          setToken(response.data.token);
        }

        // 统一提取角色（顶层 role > authorities，回退到注册时选择的角色）
        const role = extractRole(response.data, registerData.role || "ROLE_USER");

        const registeredUser = response.data.user;
        const userData = {
          ...registeredUser,
          role,
          avatar: normalizeAvatarUrl(registeredUser.avatar),
        };

        sessionVersion += 1;
        initialized.value = true;
        setUser(userData);

        // 不再强制刷新页面，让调用方处理导航
        // window.location.reload();

        await fetchUnreadCount();

        return true;
      }

      return false;
    } catch (error) {
      console.error("注册失败:", extractErrorMessage(error));
      const apiError = error as ApiRequestError;

      // 确保错误中包含后端返回的消息
      if (apiError.response?.data) {
        const data = apiError.response.data;
        if (typeof data === "string") {
          apiError.message = data;
        } else if (typeof data === "object" && "message" in data) {
          apiError.message = (data as { message: string }).message;
        }
      }

      // 抛出错误，让调用者处理
      throw error;
    }
  };

  // 无网络清理：401、启动失败和主动退出共用，避免再次请求 logout。
  const clearSession = () => {
    sessionVersion += 1;
    disconnectWebSocket();
    setToken(null);
    userInfo.value = null;
    for (const key of [USER_KEY, "token", "userInfo", "user", "homestay_token", "favorites"]) {
      localStorage.removeItem(key);
    }
    for (const key of ["token", "homestay_token", "redirectAfterLogin"]) {
      sessionStorage.removeItem(key);
    }
    unreadNotificationCount.value = 0;
    const notificationStore = useNotificationStore();
    notificationStore.setUnreadCount(0);
    notificationStore.clearNotifications();
    notificationStore.preferences = {};
    useChatStore().clearSession();
  };

  const initializeSession = (): Promise<void> => {
    if (initialized.value) return Promise.resolve();
    if (!initialization) {
      const version = sessionVersion;
      initialization = fetchUserInfo()
        .then(() => undefined)
        .catch(() => {
          if (version === sessionVersion) clearSession();
        })
        .finally(() => {
          initialized.value = true;
          initialization = null;
        });
    }
    return initialization;
  };

  const logout = async () => {
    clearSession();
    try {
      await api.post("/api/auth/logout");
    } catch (error) {
      console.warn("调用登出接口失败:", extractErrorMessage(error));
    } finally {
      window.location.href = "/login";
    }
  };

  const updateProfile = async (data: ProfileUpdateRequest) => {
    try {
      const response = await api.put<{ user: UserInfo }>("/api/auth/profile", data);
      setUser(response.data.user);
      return response.data.user;
    } catch (error) {
      const apiError = error as ApiRequestError;
      throw new Error(
        typeof apiError.response?.data === "object" && apiError.response.data?.message
          ? apiError.response.data.message
          : "更新个人信息失败"
      );
    }
  };

  const changePassword = async (data: PasswordChangeRequest) => {
    try {
      await api.post("/api/auth/change-password", data);
      clearSession();
      await router.push("/login");
      return true;
    } catch (error) {
      console.error("修改密码失败:", extractErrorMessage(error));
      ElMessage.error("修改密码失败，请检查原密码是否正确");
      throw error;
    }
  };

  const fetchUserInfo = async () => {
    const version = sessionVersion;
    // Cookie 是身份来源，即使没有本地资料也必须查询。
    const response = await api.get("/api/auth/current");
    const data = response.data?.user || response.data?.data || response.data;
    if (!data?.id || !data?.username || !extractRole(data, "")) {
      throw new Error("当前用户响应缺少身份信息");
    }
    if (version !== sessionVersion) return null;
    const userData: UserInfo = {
      id: data.id,
      username: data.username,
      email: data.email || "",
      phone: data.phone,
      realName: data.realName,
      idCard: data.idCard,
      verificationStatus: data.verificationStatus,
      role: extractRole(data, ""),
      avatar: normalizeAvatarUrl(data.avatar),
    };
    setUser(userData);
    await fetchUnreadCount();
    return userInfo.value;
  };

  /**
   * 上传用户头像
   */
  const uploadAvatar = async (file: File) => {
    try {
      // 基本验证
      if (!file) {
        throw new Error("请选择要上传的头像文件");
      }

      // 验证文件类型
      if (!file.type.startsWith("image/")) {
        throw new Error("只能上传图片文件");
      }

      // 验证文件大小 (10MB)
      const maxSize = 10 * 1024 * 1024;
      if (file.size > maxSize) {
        throw new Error("头像文件大小不能超过10MB");
      }

      // 记录文件信息
      const fileSizeKB = (file.size / 1024).toFixed(2);
      log("准备上传头像:", {
        文件名: file.name,
        类型: file.type,
        大小: `${fileSizeKB}KB`,
      });

      // 创建FormData对象
      const formData = new FormData();
      formData.append("file", file);
      formData.append("type", "avatar");

      // 发送请求，确保不设置Content-Type，让浏览器自动设置正确的boundary
      const response = await api.post("/api/files/upload", formData, {
        headers: {
          // 不要手动设置Content-Type，让axios自动处理
          // 'Content-Type': 'multipart/form-data'
        },
      });

      // 统一处理响应数据，与Profile组件保持一致
      let avatarPath = "";

      if (response.data) {
        // 统一的响应数据提取逻辑
        if (response.data.data?.fileName) {
          avatarPath = response.data.data.fileName;
        } else if (response.data.fileName) {
          avatarPath = response.data.fileName;
        } else if (typeof response.data === "string") {
          avatarPath = response.data;
        } else if (response.data.data?.url) {
          // 从URL中提取文件名
          avatarPath = response.data.data.url.split("/").pop();
        } else if (response.data.path) {
          avatarPath = response.data.path;
        }

        log("从响应中解析头像路径:", avatarPath);
      }

      if (!avatarPath) {
        console.error("无法从响应中解析头像路径:", response.data);
        throw new Error("上传头像失败：无法解析服务器返回的头像路径");
      }

      log("头像上传成功，文件名:", avatarPath);

      // 头像上传时，FileController会自动更新数据库中的用户头像
      // 这里只需要更新本地用户信息
      if (userInfo.value) {
        userInfo.value.avatar = avatarPath;
        localStorage.setItem(USER_KEY, JSON.stringify(userInfo.value));
        log("用户头像已更新:", avatarPath);
      }

      await fetchUnreadCount();
      return avatarPath;
    } catch (error) {
      console.error("上传头像失败:", extractErrorMessage(error));
      throw error;
    }
  };

  const resetPassword = async (token: string, newPassword: string) => {
    try {
      log("开始重置密码");
      const response = await api.post("/api/auth/reset-password", {
        token,
        newPassword,
      });
      log("密码重置成功:", response.data);
      return response.data;
    } catch (error) {
      console.error("密码重置失败:", extractErrorMessage(error));
      throw error;
    }
  };

  const forgotPassword = async (email: string) => {
    try {
      // 后端接口为 @RequestParam String email，需以 query 参数传递
      const response = await api.post("/api/auth/forgot-password", null, {
        params: { email },
      });
      log("密码重置邮件已发送:", response.data);
      return true;
    } catch (error) {
      console.error("发送重置密码邮件失败:", extractErrorMessage(error));
      throw error;
    }
  };

  /**
   * 同步用户角色信息
   */
  const syncUserRole = () => {
    if (!userInfo.value) return false;

    // 如果role已存在，不需要同步
    if (userInfo.value.role && userInfo.value.role !== "") return true;

    // 如果authorities存在，从中提取角色
    if (userInfo.value.authorities && userInfo.value.authorities.length > 0) {
      log("尝试从authorities同步用户角色:", userInfo.value.authorities);
      const authority = userInfo.value.authorities.find((auth) =>
        auth.authority.startsWith("ROLE_")
      );
      if (authority) {
        userInfo.value.role = authority.authority;
        localStorage.setItem(USER_KEY, JSON.stringify(userInfo.value));
        log("用户角色已同步:", userInfo.value.role);
        return true;
      }
    }

    return false;
  };

  const fetchUnreadCount = async () => {
    if (!isAuthenticated.value) {
      unreadNotificationCount.value = 0;
      useNotificationStore().setUnreadCount(0);
      return;
    }
    try {
      const response = await api.get("/api/notifications/unread-count");
      const count = response.data.unreadCount || 0;
      unreadNotificationCount.value = count;
      useNotificationStore().setUnreadCount(count);
    } catch (error) {
      console.error("获取未读通知数失败:", error);
    }
  };

  return {
    token,
    setToken,
    userInfo,
    user,
    userRole,
    normalizedRole,
    isAuthenticated,
    isAdmin,
    isLandlord,
    login,
    register,
    logout,
    clearSession,
    initializeSession,
    initialized,
    updateProfile,
    changePassword,
    fetchUserInfo,
    uploadAvatar,
    resetPassword,
    forgotPassword,
    syncUserRole,
    unreadNotificationCount,
    fetchUnreadCount,
  };
});
