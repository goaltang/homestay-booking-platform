/**
 * 用户信息相关的小工具函数
 * 用于统一处理登录/注册/获取用户信息中的重复逻辑
 */

interface AuthorityLike {
  authority?: string;
}

/** 从登录/注册/用户信息响应中提取角色 */
export function extractRole(
  data: Record<string, unknown> | null | undefined,
  fallback = "ROLE_USER"
): string {
  if (!data) return fallback;

  // 优先级：嵌套 user 对象 > 顶层 role > authorities 数组 > fallback
  const nestedUser = data.user as Record<string, unknown> | undefined;
  if (nestedUser && typeof nestedUser.role === "string" && nestedUser.role) {
    return nestedUser.role;
  }

  if (typeof data.role === "string" && data.role) {
    return data.role;
  }

  const authorities = data.authorities;
  if (Array.isArray(authorities)) {
    for (const auth of authorities) {
      const authority =
        typeof auth === "string" ? auth : (auth as AuthorityLike)?.authority;
      if (authority && authority.startsWith("ROLE_")) {
        return authority;
      }
    }
  }

  return fallback;
}

/** 规范化头像 URL：统一为 /api/files/avatar/{filename} 或相对 API 路径 */
export function normalizeAvatarUrl(avatar?: string | null): string {
  if (!avatar) return "";

  try {
    // 完整 URL：转换为相对 API 路径
    if (avatar.startsWith("http://") || avatar.startsWith("https://")) {
      const url = new URL(avatar);
      if (
        url.pathname.includes("/uploads/avatars/") ||
        url.pathname.includes("/uploads/avatar/")
      ) {
        return `/api/files/avatar/${url.pathname.split("/").pop()}`;
      }
      if (url.pathname.includes("/uploads/")) {
        return `/api${url.pathname}`;
      }
      return avatar;
    }

    // 旧格式：/uploads/avatars/{filename}
    if (
      avatar.startsWith("/uploads/avatars/") ||
      avatar.startsWith("/uploads/avatar/")
    ) {
      return `/api/files/avatar/${avatar.split("/").pop()}`;
    }

    // 纯文件名：构建完整路径
    if (!avatar.startsWith("/api/files/avatar/") && !avatar.includes("/")) {
      return `/api/files/avatar/${avatar}`;
    }

    return avatar;
  } catch {
    return avatar;
  }
}
