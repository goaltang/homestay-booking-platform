import axios from "axios";
import request from "./request";
import { ElMessage, ElMessageBox } from "element-plus";
import router from "@/router";

/**
 * 检查当前的认证状态并进行测试请求
 * 这个函数用于调试认证问题，可以在前端页面中添加一个按钮来调用它
 */
export const detailedAuthCheck = async () => {
  console.log("====== 开始检查认证状态 ======");

  // 1. 检查本地存储的token
  const localToken = localStorage.getItem("homestay_token") || localStorage.getItem("token");
  console.log(
    "localStorage中的token: ",
    localToken ? `${localToken.substring(0, 15)}...` : "不存在"
  );

  const sessionToken = sessionStorage.getItem("token");
  console.log(
    "sessionStorage中的token: ",
    sessionToken ? `${sessionToken.substring(0, 15)}...` : "不存在"
  );

  // 2. 检查用户信息
  let userInfo = null;
  try {
    const storedUserInfo = (localStorage.getItem("homestay_user") || localStorage.getItem("userInfo"));
    if (storedUserInfo) {
      userInfo = JSON.parse(storedUserInfo);
      console.log("localStorage中的用户信息: ", {
        username: userInfo.username || "未知",
        role: userInfo.role || "未知",
        tokenExists: !!userInfo.token,
      });
    } else {
      console.log("localStorage中没有用户信息");
    }
  } catch (e) {
    console.error("解析用户信息失败: ", e);
  }

  // 3. 发送测试请求到/api/auth/current
  console.log("发送测试请求到 /api/auth/current...");
  try {
    const currentUserResponse = await request({
      url: "/api/auth/current",
      method: "get",
    });
    console.log("获取当前用户成功: ", currentUserResponse.data);
  } catch (error) {
    console.error("获取当前用户失败: ", error);
  }

  // 4. 使用原生axios发送测试请求，确保请求拦截器正常工作
  console.log("使用原生axios发送测试请求，检查请求拦截器...");

  const headers: Record<string, string> = {};
  if (localToken) {
    headers["Authorization"] = `Bearer ${localToken}`;
  }

  try {
    const response = await axios.get("/api/auth/current", { headers });
    console.log("原生请求成功: ", response.data);
  } catch (error) {
    console.error("原生请求失败: ", error);
  }

  // 5. 测试请求到房源API
  console.log("测试请求到房源API...");
  try {
    const homestayResponse = await request({
      url: "/api/homestays/owner",
      method: "get",
    });
    console.log("获取房东房源成功: ", homestayResponse.data);
  } catch (error) {
    console.error("获取房东房源失败: ", error);
  }

  console.log("====== 认证状态检查完成 ======");

  return {
    localTokenExists: !!localToken,
    sessionTokenExists: !!sessionToken,
    userInfoExists: !!userInfo,
  };
};

/**
 * 使用指定的token发送测试请求
 * @param url 请求的URL
 * @param token 要使用的token
 */
export const testRequestWithToken = async (url: string, token: string) => {
  console.log(`测试请求: ${url} (使用提供的token)`);

  try {
    const response = await axios.get(url, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });

    console.log("测试请求成功: ", response.data);
    return {
      success: true,
      data: response.data,
    };
  } catch (error) {
    console.error("测试请求失败: ", error);
    return {
      success: false,
      error,
    };
  }
};

// token 已迁移到 httpOnly cookie（JS 不可读），登录态以 userInfo 为准
export function getToken(): string | null {
  return null;
}

// token 已迁移 httpOnly cookie，不再写入 localStorage（保留函数签名兼容旧调用）
export function setToken(_token: string): void {
  // no-op
}

// 移除token
export function removeToken(): void {
  localStorage.removeItem("homestay_token");
  localStorage.removeItem("token");
}

// 获取当前用户信息
export function getCurrentUser(): any {
  const userString = (localStorage.getItem("homestay_user") || localStorage.getItem("user"));
  if (!userString) return null;

  try {
    return JSON.parse(userString);
  } catch (e) {
    console.error("解析用户信息失败", e);
    return null;
  }
}

// 检查是否登录（token 在 httpOnly cookie，登录态以 userInfo 为准）
export function isLoggedIn(): boolean {
  return !!getCurrentUser();
}

// 检查认证状态（认证请求依赖 httpOnly cookie）
export function checkAuthentication(): Promise<boolean> {
  const user = getCurrentUser();

  console.log("检查认证状态:");
  console.log(`- 用户信息存在: ${!!user}`);
  if (user) {
    console.log(`- 用户信息:`, user);
  }

  // 如果没有用户信息，直接返回未认证
  if (!user) {
    console.error("认证检查失败: 没有找到用户信息");
    ElMessage.error("请先登录");
    return Promise.resolve(false);
  }

  // 发送测试请求到用户API（cookie 自动携带）
  return request({
    url: "/api/auth/current",
    method: "get",
  })
    .then((response) => {
      console.log("认证检查成功:", response.data);
      ElMessage.success("认证状态正常，您已成功登录");
      return true;
    })
    .catch((error) => {
      console.error("认证检查失败:", error);

      if (error.response) {
        console.error(`状态码: ${error.response.status}`);
        console.error(`响应数据:`, error.response.data);

        if (error.response.status === 401 || error.response.status === 403) {
          ElMessage.error("认证已过期或无效，请重新登录");
        } else {
          ElMessage.error(`认证检查失败: ${error.message || "未知错误"}`);
        }
      } else {
        ElMessage.error(`认证检查失败: ${error.message || "网络错误"}`);
      }

      return false;
    });
}

// 显示登录提示并重定向
function alertLogin(redirectPath: string = "/login"): void {
  console.error("未找到登录token，将重定向到登录页");
  ElMessage.error("请先登录后再进行此操作");

  // 保存当前URL，以便登录后返回
  const currentPath = window.location.pathname;
  sessionStorage.setItem("redirectAfterLogin", currentPath);

  // 延迟重定向，让用户看到提示信息
  setTimeout(() => {
    window.location.href = redirectPath;
  }, 1500);
}

/**
 * 检查用户是否已登录，并且具有所需的角色权限
 * 如果用户未登录，会自动跳转到登录页面，并在登录成功后跳转回当前页面
 * 如果用户已登录但缺少权限，会显示错误消息
 * @param requiredRoles 所需角色数组，可选
 * @param redirectPath 重定向路径，默认为登录页面
 * @returns Promise<boolean> 如果用户已登录并具有所需权限，则为true
 */
export async function ensureUserLoggedIn(
  requiredRoles?: string[],
  redirectPath: string = "/login"
): Promise<boolean> {
  console.log("执行登录检查，要求角色:", requiredRoles);

  // 获取用户信息（token 在 httpOnly cookie）
  const user = getCurrentUser();

  if (!user) {
    console.log("未找到用户信息，需要登录");
    alertLogin(redirectPath);
    return false;
  }

  // 获取本地角色信息
  let localUserRole = "";
  try {
    const userInfoStr = (localStorage.getItem("homestay_user") || localStorage.getItem("userInfo"));
    if (userInfoStr) {
      const userInfo = JSON.parse(userInfoStr);
      if (userInfo.role) {
        localUserRole = userInfo.role;
        console.log("从userInfo中获取到角色:", localUserRole);
      }
    }

    if (!localUserRole) {
      const userStr = (localStorage.getItem("homestay_user") || localStorage.getItem("user"));
      if (!userStr) {
        console.warn("localStorage中没有找到user信息");
      } else {
        const user = JSON.parse(userStr);
        if (user.role) {
          localUserRole = user.role;
          console.log("从user中获取到角色:", localUserRole);
        } else if (user.authorities && user.authorities.length > 0) {
          localUserRole =
            typeof user.authorities[0] === "string"
              ? user.authorities[0]
              : user.authorities[0].authority;
          console.log("从user.authorities中获取到角色:", localUserRole);
        }
      }
    }
  } catch (e) {
    console.error("解析本地用户信息失败:", e);
  }

  // 特殊处理：检查是否在编辑房源页面，如果是，自动授予ROLE_HOST权限
  const currentPath = window.location.pathname;
  const isHomestayEditPage =
    currentPath.includes("/host/homestay/edit/") ||
    currentPath.includes("/host/homestay/create");

  if (isHomestayEditPage && user) {
    console.log("检测到用户在房源编辑页面，临时授予ROLE_HOST权限");

    // 只在开发环境强制授予权限
    if (import.meta.env.DEV) {
      // 在localStorage中更新角色信息
      try {
        // 更新userInfo
        const userInfoStr = (localStorage.getItem("homestay_user") || localStorage.getItem("userInfo"));
        if (userInfoStr) {
          const userInfo = JSON.parse(userInfoStr);
          if (!userInfo.role || !userInfo.role.includes("HOST")) {
            userInfo.role = userInfo.role
              ? `${userInfo.role},ROLE_HOST`
              : "ROLE_HOST";
            localStorage.setItem("homestay_user", JSON.stringify(userInfo));
            console.log("已更新userInfo中的角色:", userInfo.role);
          }
        }

        // 更新user
        const userStr = (localStorage.getItem("homestay_user") || localStorage.getItem("user"));
        if (userStr) {
          const user = JSON.parse(userStr);
          // 检查authorities数组
          if (
            !user.authorities ||
            !user.authorities.some(
              (a: { authority?: string } | string) =>
                (typeof a === "string" && a.includes("HOST")) ||
                (a &&
                  typeof a === "object" &&
                  a.authority &&
                  a.authority.includes("HOST"))
            )
          ) {
            if (!user.authorities) {
              user.authorities = [];
            }
            user.authorities.push({ authority: "ROLE_HOST" });
            localStorage.setItem("homestay_user", JSON.stringify(user));
            console.log("已更新user.authorities:", user.authorities);
          }
        }

        // 设置本地角色变量，以便后续检查
        localUserRole = "ROLE_HOST";
      } catch (e) {
        console.error("更新本地用户角色失败:", e);
      }
    }
  }

  // 如果没有要求特定角色，则认为只要有token就可以
  if (!requiredRoles || requiredRoles.length === 0) {
    console.log("没有指定角色要求，认证通过");
    return true;
  }

  // 发送验证请求到后端
  return request({
    url: "/api/auth/current",
    method: "get",
  })
    .then((response) => {
      if (!response || !response.data) {
        console.error("验证响应为空");
        alertLogin(redirectPath);
        return false;
      }

      const userData = response.data;
      let userRole = "";

      // 尝试从响应中获取角色信息
      if (userData.role) {
        userRole = userData.role;
      } else if (userData.roles) {
        if (Array.isArray(userData.roles)) {
          userRole = userData.roles.join(",");
        } else {
          userRole = String(userData.roles);
        }
      } else if (userData.authorities) {
        if (Array.isArray(userData.authorities)) {
          userRole = userData.authorities
            .map((auth: any) =>
              typeof auth === "string" ? auth : auth.authority || ""
            )
            .filter(Boolean)
            .join(",");
        } else {
          userRole = String(userData.authorities);
        }
      }

      // 如果服务器未返回角色，使用本地角色
      if ((!userRole || userRole === "") && localUserRole) {
        userRole = localUserRole;
        console.log("服务器未返回角色，使用本地角色信息:", userRole);
      }

      console.log(
        `用户角色: ${userRole || "未设置"}, 需要的角色: ${
          requiredRoles ? requiredRoles.join(",") : "any"
        }`
      );

      // 特殊处理：房源编辑页面总是允许通过
      if (isHomestayEditPage && user && import.meta.env.DEV) {
        console.log("房源编辑页面特殊处理：强制授予访问权限");
        return true;
      }

      // 检查用户角色是否符合要求
      if (!userRole || userRole === "") {
        // 角色为空，显示错误信息
        console.error(
          "当前用户没有所需角色权限。用户角色:",
          userRole,
          "所需角色:",
          requiredRoles.join(",")
        );
        ElMessage.error("您没有所需的角色权限，请联系管理员");
        return false;
      }

      // 将用户角色和所需角色标准化（去掉ROLE_前缀）
      const userRoles = userRole.split(",").map((r) => {
        const role = r.trim();
        return role.startsWith("ROLE_") ? role.substring(5) : role;
      });

      const requiredRolesNormalized = requiredRoles.map((r) => {
        const role = r.trim();
        return role.startsWith("ROLE_") ? role.substring(5) : role;
      });

      // 检查用户是否拥有所需角色
      const hasRequiredRole = userRoles.some((ur) =>
        requiredRolesNormalized.some(
          (rr) => ur.toLowerCase() === rr.toLowerCase()
        )
      );

      if (!hasRequiredRole) {
        console.error(
          "当前用户没有所需角色权限。用户角色:",
          userRoles.join(","),
          "所需角色:",
          requiredRolesNormalized.join(",")
        );
        ElMessage.error(
          `您没有所需的角色权限 (${requiredRolesNormalized.join(",")})`
        );
        return false;
      }

      console.log("用户角色验证通过");
      return true;
    })
    .catch((error) => {
      console.error("认证检查失败:", error);

      // 特殊处理：房源编辑页面在开发环境中总是允许通过
      if (isHomestayEditPage && user && import.meta.env.DEV) {
        console.log("房源编辑页面特殊处理：尽管API失败，仍强制授予访问权限");
        return true;
      }

      // 如果有本地角色且符合要求，允许通过
      if (localUserRole && requiredRoles) {
        const normalizedLocalRole = localUserRole.replace("ROLE_", "");
        const hasLocalRequiredRole = requiredRoles.some(
          (role) =>
            normalizedLocalRole === role ||
            normalizedLocalRole === role.toUpperCase() ||
            localUserRole === role ||
            localUserRole === `ROLE_${role}` ||
            localUserRole === `ROLE_${role.toUpperCase()}`
        );

        if (hasLocalRequiredRole) {
          console.log(`虽然API请求失败，但本地角色检查通过: ${localUserRole}`);
          return true;
        }
      }

      // 获取失败，可能是令牌过期
      console.error("验证请求失败，可能是令牌已过期");
      alertLogin(redirectPath);
      return false;
    });
}

/**
 * 页面访问级别定义
 */
export enum AccessLevel {
  PUBLIC = "public", // 公开访问，无需登录
  AUTHENTICATED = "authenticated", // 需要登录
  HOST = "host", // 需要房东权限
  ADMIN = "admin", // 需要管理员权限
}

/**
 * 权限检查结果
 */
export interface AuthCheckResult {
  hasAccess: boolean;
  reason?: string;
  redirectPath?: string;
}

/**
 * 权限管理类
 */
export class AuthManager {
  private static instance: AuthManager;

  static getInstance(): AuthManager {
    if (!AuthManager.instance) {
      AuthManager.instance = new AuthManager();
    }
    return AuthManager.instance;
  }

  /**
   * 检查用户是否已登录
   */
  isAuthenticated(): boolean {
    // token 已迁移 httpOnly cookie，登录态以 userInfo 为准
    return !!this.getUserRole() || !!getCurrentUser();
  }

  /**
   * 获取用户角色
   */
  getUserRole(): string {
    const userInfo = JSON.parse((localStorage.getItem("homestay_user") || localStorage.getItem("userInfo")) || "null");
    const user = JSON.parse((localStorage.getItem("homestay_user") || localStorage.getItem("user")) || "null");
    return userInfo?.role || user?.role || "";
  }

  /**
   * 检查用户是否为房东
   */
  isHost(): boolean {
    const role = this.getUserRole().toUpperCase();
    return ["ROLE_HOST", "ROLE_LANDLORD", "HOST", "LANDLORD"].includes(role);
  }

  /**
   * 检查用户是否为管理员
   */
  isAdmin(): boolean {
    const role = this.getUserRole().toUpperCase();
    return ["ROLE_ADMIN", "ADMIN"].includes(role);
  }

  /**
   * 检查页面访问权限
   */
  checkAccess(requiredLevel: AccessLevel): AuthCheckResult {
    switch (requiredLevel) {
      case AccessLevel.PUBLIC:
        return { hasAccess: true };

      case AccessLevel.AUTHENTICATED:
        if (!this.isAuthenticated()) {
          return {
            hasAccess: false,
            reason: "请先登录后再访问此页面",
            redirectPath: "/login",
          };
        }
        return { hasAccess: true };

      case AccessLevel.HOST:
        if (!this.isAuthenticated()) {
          return {
            hasAccess: false,
            reason: "请先登录后再访问房东中心",
            redirectPath: "/login",
          };
        }
        if (!this.isHost()) {
          return {
            hasAccess: false,
            reason: "您没有房东权限，无法访问此页面",
            redirectPath: "/",
          };
        }
        return { hasAccess: true };

      case AccessLevel.ADMIN:
        if (!this.isAuthenticated()) {
          return {
            hasAccess: false,
            reason: "请先登录后再访问管理后台",
            redirectPath: "/login",
          };
        }
        if (!this.isAdmin()) {
          return {
            hasAccess: false,
            reason: "您没有管理员权限，无法访问此页面",
            redirectPath: "/",
          };
        }
        return { hasAccess: true };

      default:
        return { hasAccess: false, reason: "未知的访问级别" };
    }
  }

  /**
   * 处理权限不足的情况
   */
  async handleAccessDenied(
    result: AuthCheckResult,
    currentPath?: string
  ): Promise<void> {
    if (result.reason) {
      if (result.redirectPath === "/login") {
        // 需要登录的情况，显示登录提示
        try {
          await ElMessageBox.confirm(result.reason, "需要登录", {
            confirmButtonText: "去登录",
            cancelButtonText: "取消",
            type: "info",
          });

          const loginQuery = currentPath ? { redirect: currentPath } : {};
          router.push({ path: "/login", query: loginQuery });
        } catch {
          // 用户取消，不做任何操作
        }
      } else {
        // 其他权限不足的情况
        ElMessage.warning(result.reason);
        if (result.redirectPath) {
          router.push(result.redirectPath);
        }
      }
    }
  }

  /**
   * 简化的权限检查方法，直接处理权限不足的情况
   */
  async requireAuth(
    requiredLevel: AccessLevel = AccessLevel.AUTHENTICATED
  ): Promise<boolean> {
    const result = this.checkAccess(requiredLevel);

    if (!result.hasAccess) {
      await this.handleAccessDenied(result, router.currentRoute.value.fullPath);
      return false;
    }

    return true;
  }

  /**
   * 检查特定操作的权限（如收藏、评论等）
   */
  async requireAuthForAction(actionName: string): Promise<boolean> {
    if (!this.isAuthenticated()) {
      try {
        await ElMessageBox.confirm(
          `请先登录后再进行${actionName}操作`,
          "需要登录",
          {
            confirmButtonText: "去登录",
            cancelButtonText: "取消",
            type: "info",
          }
        );

        router.push("/login");
        return false;
      } catch {
        return false;
      }
    }

    return true;
  }
}

// 导出单例实例
export const authManager = AuthManager.getInstance();

// 导出常用的快捷方法
export const isAuthenticated = () => authManager.isAuthenticated();
export const isHost = () => authManager.isHost();
export const isAdmin = () => authManager.isAdmin();
export const requireAuth = (level?: AccessLevel) =>
  authManager.requireAuth(level);
export const requireAuthForAction = (actionName: string) =>
  authManager.requireAuthForAction(actionName);

export default {
  checkAuthentication,
  testRequestWithToken,
  ensureUserLoggedIn,
};
