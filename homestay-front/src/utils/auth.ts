import { ElMessage, ElMessageBox } from "element-plus";
import router from "@/router";
import { useUserStore } from "@/stores/user";
import type { UserInfo } from "@/stores/user";
import { hasRole } from "@/utils/userHelpers";

// HTTP 使用 httpOnly Cookie；保留已有工具函数签名。
export function getToken(): string | null {
  return null;
}

export function setToken(_token: string): void {
  // HTTP Cookie 由服务端设置。
}

export function removeToken(): void {
  useUserStore().clearSession();
}

export function getCurrentUser(): UserInfo | null {
  return useUserStore().userInfo;
}

export function isLoggedIn(): boolean {
  return useUserStore().isAuthenticated;
}

export async function checkAuthentication(): Promise<boolean> {
  try {
    return !!(await useUserStore().fetchUserInfo());
  } catch {
    return false;
  }
}

/** 房源操作以服务端 current 身份验证，不修改本地角色或按旧缓存兜底。 */
export async function ensureUserLoggedIn(
  requiredRoles?: string[],
  redirectPath: string = "/login"
): Promise<boolean> {
  const store = useUserStore();
  try {
    const user = await store.fetchUserInfo();
    if (!user) return false;
    if (requiredRoles?.length && !hasRole(user.role, requiredRoles)) {
      ElMessage.error("您没有所需的角色权限");
      return false;
    }
    return true;
  } catch (error) {
    const status = (error as { response?: { status: number } }).response?.status;
    if (status === 401) {
      store.clearSession();
      await router.push({
        path: redirectPath,
        query: { redirect: router.currentRoute.value.fullPath },
      });
    }
    return false;
  }
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
    return useUserStore().isAuthenticated;
  }

  /**
   * 获取用户角色
   */
  getUserRole(): string {
    return useUserStore().normalizedRole;
  }

  isHost(): boolean {
    return useUserStore().isLandlord;
  }

  isAdmin(): boolean {
    return useUserStore().isAdmin;
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
  async handleAccessDenied(result: AuthCheckResult, currentPath?: string): Promise<void> {
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
  async requireAuth(requiredLevel: AccessLevel = AccessLevel.AUTHENTICATED): Promise<boolean> {
    await useUserStore().initializeSession();
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
    await useUserStore().initializeSession();
    if (!this.isAuthenticated()) {
      try {
        await ElMessageBox.confirm(`请先登录后再进行${actionName}操作`, "需要登录", {
          confirmButtonText: "去登录",
          cancelButtonText: "取消",
          type: "info",
        });

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
export const requireAuth = (level?: AccessLevel) => authManager.requireAuth(level);
export const requireAuthForAction = (actionName: string) =>
  authManager.requireAuthForAction(actionName);

export default {
  checkAuthentication,
  ensureUserLoggedIn,
};
