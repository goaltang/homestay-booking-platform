import { defineStore } from "pinia";
import { ref, computed } from "vue";
import api from "@/api";
import { RegisterRequest } from "@/types/auth";
import { ElMessage } from "element-plus";
import { initWebSocket, disconnectWebSocket, setAuthToken } from "@/services/websocketService";
import { useNotificationStore } from "@/stores/notification";
import { extractErrorMessage, ApiRequestError } from "@/types/error";
import { extractRole, normalizeAvatarUrl } from "@/utils/userHelpers";

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
}

const USER_KEY = "homestay_user";

function migrateOldKeys() {
  const oldUserInfo = localStorage.getItem("userInfo");
  if (oldUserInfo && !localStorage.getItem(USER_KEY)) {
    localStorage.setItem(USER_KEY, oldUserInfo);
  }
}

migrateOldKeys();

const log = (...args: unknown[]) => {
  if (import.meta.env.DEV) console.log(...args);
};
const warn = (...args: unknown[]) => {
  if (import.meta.env.DEV) console.warn(...args);
};

export const useUserStore = defineStore("user", () => {
  // token 迁移后，JWT 存 httpOnly cookie + 内存（供 WebSocket），不落 localStorage
  const token = ref<string | null>(null);
  const userInfo = ref<UserInfo | null>(
    JSON.parse(localStorage.getItem(USER_KEY) || "null")
  );

  // 登录态以 userInfo 为准（token 在 httpOnly cookie，JS 不可读）
  const isAuthenticated = computed(() => !!userInfo.value);
  const normalizedRole = computed(() => userInfo.value?.role?.toUpperCase() || "");

  /** @deprecated 请直接使用 userInfo */
  const user = computed(() => userInfo.value);
  /** @deprecated 请直接使用 normalizedRole */
  const userRole = computed(() => normalizedRole.value);
  const isAdmin = computed(() =>
    normalizedRole.value === "ROLE_ADMIN" || normalizedRole.value === "ADMIN"
  );
  const isLandlord = computed(() => {
    if (!normalizedRole.value) return false;

    // 支持多种可能的房东角色名称
    const role = normalizedRole.value;
    const isLandlordResult =
      role === "ROLE_HOST" ||
      role === "ROLE_LANDLORD" || // 保留兼容旧数据
      role === "LANDLORD" ||
      role === "HOST";

    log("isLandlord计算:", {
      role: userInfo.value?.role,
      normalized: role,
      result: isLandlordResult,
    });

    return isLandlordResult;
  });

  const unreadNotificationCount = ref<number>(0);

  const setToken = (newToken: string | null) => {
    token.value = newToken;
    // 同步内存 token 到 WebSocket 服务（供 STOMP 握手用）
    setAuthToken(newToken);
  };

  const setUser = (user: UserInfo) => {
    log("设置用户信息:", user);
    userInfo.value = user;
    // 保存用户信息到 localStorage
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    if (token.value && user.id) {
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

      log("登录响应:", response.data);

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
          verificationStatus:
            userObj?.verificationStatus || response.data.verificationStatus || "",
        };

        log("准备保存的用户数据:", userData);
        setUser(userData);

        // 确保localStorage保存了用户信息
        localStorage.setItem(USER_KEY, JSON.stringify(userData));
        log("用户信息已保存到localStorage", userData);
        log("检查isLandlord:", isLandlord.value);

        await fetchUnreadCount();

        return true;
      }
      return false;
    } catch (error) {
      console.error("登录失败:", error);

      const apiError = error as ApiRequestError;

      // 优先使用API拦截器中提取的错误信息
      if (apiError.displayMessage) {
        throw new Error(apiError.displayMessage);
      }

      // 检查是否是 Axios 错误并且有响应
      if (apiError.response) {
        console.error("错误响应:", apiError.response.data);

        // 提取后端返回的具体错误信息
        let errorMessage = "登录失败，请重试";
        const data = apiError.response.data;

        if (data) {
          if (typeof data === "string") {
            errorMessage = data;
          } else if (typeof data === "object") {
            if ("message" in data && typeof data.message === "string") {
              errorMessage = data.message;
            } else if ("error" in data && typeof data.error === "string") {
              errorMessage = data.error;
            }
          }
        }

        throw new Error(errorMessage);
      }

      // 对于网络错误等其他情况
      const msg = extractErrorMessage(error);
      throw new Error(msg);
    }
  };

  const register = async (
    registerData: Omit<RegisterRequest, "confirmPassword">
  ) => {
    try {
      // 确保角色信息是大写且格式正确
      if (registerData.role && !registerData.role.startsWith("ROLE_")) {
        registerData.role = `ROLE_${registerData.role.toUpperCase()}`;
      }

      log("尝试注册新用户，发送数据:", JSON.stringify(registerData));
      // 添加后端API调用
      const response = await api.post("/api/auth/register", registerData);

      log("注册响应:", response.data);

      if (response.data) {
        // token 由后端写入 httpOnly cookie；这里仅记录到内存供 WebSocket 使用
        if (response.data.token) {
          setToken(response.data.token);
        }

        // 统一提取角色（顶层 role > authorities，回退到注册时选择的角色）
        const role = extractRole(response.data, registerData.role || "ROLE_USER");

        // 设置用户信息
        const userData = {
          id: response.data.id,
          username: response.data.username,
          email: response.data.email,
          phone: response.data.phone || registerData.phone,
          role: role,
          avatar: normalizeAvatarUrl(response.data.avatar),
          verificationStatus: response.data.verificationStatus,
          authorities: response.data.authorities, // 确保authorities也被保存
        };

        setUser(userData);

        // 确保localStorage保存了用户信息
        localStorage.setItem(USER_KEY, JSON.stringify(userData));
        log("用户信息已保存到localStorage", userData);

        // 不再强制刷新页面，让调用方处理导航
        // window.location.reload();

        await fetchUnreadCount();

        return true;
      }

      // 如果后端API调用失败，回退到模拟注册
      if (import.meta.env.DEV) {
        warn("使用模拟注册数据（仅用于开发测试）");
        setToken("mock-token-" + Date.now());

        const mockUserData = {
          id: Date.now(),
          username: registerData.username,
          email: registerData.email,
          phone: registerData.phone || "",
          role: registerData.role || "ROLE_USER",
          avatar: "",
        };

        setUser(mockUserData);
        log("模拟注册成功:", mockUserData);
        return true;
      }

      return false;
    } catch (error) {
      console.error("注册失败:", error);
      const apiError = error as ApiRequestError;

      // 只有在开发环境才使用模拟数据
      if (import.meta.env.DEV && !apiError.response) {
        warn("API调用失败，使用模拟注册数据（仅用于开发测试）");
        setToken("mock-token-" + Date.now());

        const mockUserData = {
          id: Date.now(),
          username: registerData.username,
          email: registerData.email,
          phone: registerData.phone || "",
          role: registerData.role || "ROLE_USER",
          avatar: "",
        };

        setUser(mockUserData);
        log("模拟注册成功:", mockUserData);
        return true;
      }

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

  const logout = async () => {
    disconnectWebSocket();
    // 清除内存 token 和用户信息
    setToken(null);
    userInfo.value = null;

    // 清除 localStorage 中的用户数据（含迁移前的旧 token）
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem("token");
    localStorage.removeItem("userInfo");
    localStorage.removeItem("user");
    localStorage.removeItem("homestay_token");
    localStorage.removeItem("favorites");

    // 调用后端清 httpOnly cookie（不阻塞跳转）
    try {
      await api.post("/api/auth/logout");
    } catch (error) {
      // 即使后端失败也继续本地登出
      console.warn("调用登出接口失败:", error);
    }

    unreadNotificationCount.value = 0;
    useNotificationStore().setUnreadCount(0);

    // 导航到登录页
    window.location.href = "/login";
  };

  const updateProfile = async (data: ProfileUpdateRequest) => {
    try {
      const response = await api.put<UserInfo>("/api/users/profile", data);
      userInfo.value = response.data;
      return response.data;
    } catch (error) {
      const apiError = error as ApiRequestError;
      throw new Error(
        (typeof apiError.response?.data === "object" && apiError.response.data?.message)
          ? apiError.response.data.message
          : "更新个人信息失败"
      );
    }
  };

  const changePassword = async (data: PasswordChangeRequest) => {
    try {
      await api.post("/api/users/change-password", data);
      ElMessage.success("密码修改成功");
      return true;
    } catch (error) {
      console.error("修改密码失败:", extractErrorMessage(error));
      ElMessage.error("修改密码失败，请检查原密码是否正确");
      return false;
    }
  };

  const fetchUserInfo = async () => {
    try {
      log("开始获取用户信息");

      // 如果没有用户信息（登录态），不执行请求
      if (!userInfo.value) {
        warn("没有用户信息，无法获取用户信息");
        return null;
      }

      // 尝试从API获取用户信息
      const response = await api.get("/api/auth/current");
      log("获取用户信息响应:", response.data);

      // 处理可能的不同响应格式
      let userData: UserInfo | null = null;

      if (response.data) {
        // 处理响应中直接包含用户数据的情况
        if (response.data.username || response.data.id) {
          log("用户信息直接在响应中");
          userData = {
            id: response.data.id || 0,
            username: response.data.username || "",
            email: response.data.email || "",
            phone: response.data.phone || "",
            realName: response.data.realName || "",
            idCard: response.data.idCard || "",
            role: extractRole(response.data),
            avatar: normalizeAvatarUrl(response.data.avatar),
            verificationStatus: response.data.verificationStatus || "",
          };
        }
        // 处理响应中嵌套在data或user中的情况
        else if (response.data.data || response.data.user) {
          const userDataObj = response.data.data || response.data.user;
          log("用户信息嵌套在data或user字段中:", userDataObj);

          if (userDataObj) {
            userData = {
              id: userDataObj.id || 0,
              username: userDataObj.username || "",
              email: userDataObj.email || "",
              phone: userDataObj.phone || "",
              realName: userDataObj.realName || "",
              idCard: userDataObj.idCard || "",
              role: extractRole(userDataObj),
              avatar: normalizeAvatarUrl(userDataObj.avatar),
              verificationStatus: userDataObj.verificationStatus || "",
            };
          }
        }
      }

      if (userData) {
        log("解析到的用户数据:", userData);

        // 统一格式化头像 URL
        userData.avatar = normalizeAvatarUrl(userData.avatar);

        setUser(userData);
        await fetchUnreadCount();
        return userData;
      } else {
        warn("未能从响应中解析出用户数据");
        return null;
      }
    } catch (error) {
      console.error("获取用户信息失败:", extractErrorMessage(error));
      const apiError = error as ApiRequestError;

      if (apiError.response) {
        console.error("API响应错误:", {
          status: apiError.response.status,
          data: apiError.response.data,
        });
      }

      // 401 时直接抛出异常，让调用方处理（避免内部 logout 导致状态混乱）
      if (apiError.response?.status === 401) {
        throw new Error("登录状态已过期，请重新登录");
      }

      // 尝试备用接口（仅非401错误时）
      try {
        log("尝试备用API获取用户信息");
        const backupResponse = await api.get("/api/auth/current");
        if (
          backupResponse.data &&
          (backupResponse.data.username || backupResponse.data.id)
        ) {
          const userData = {
            id: backupResponse.data.id || 0,
            username: backupResponse.data.username || "",
            email: backupResponse.data.email || "",
            phone: backupResponse.data.phone || "",
            realName: backupResponse.data.realName || "",
            idCard: backupResponse.data.idCard || "",
            role: backupResponse.data.role || "ROLE_USER",
            avatar: backupResponse.data.avatar || "",
            verificationStatus: backupResponse.data.verificationStatus || "",
          };
          setUser(userData);
          await fetchUnreadCount();
          return userData;
        }
      } catch (backupError) {
        console.error("备用API也失败:", extractErrorMessage(backupError));
      }

      throw new Error(apiError.displayMessage || "获取用户信息失败");
    }
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
      console.error("密码重置失败:", error);
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
      console.error("发送重置密码邮件失败:", error);
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

  // 初始化时检查并同步用户角色
  if (userInfo.value) {
    syncUserRole();
  }

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
