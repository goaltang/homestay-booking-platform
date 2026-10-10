/**
 * 统一图片处理模块
 * 处理系统中所有类型的图片URL，确保正确加载和显示
 */

// 基础配置
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "";
const DEFAULT_AVATAR =
  "https://images.unsplash.com/photo-1633332755192-727a05c4013d?ixlib=rb-4.0.3&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&auto=format&fit=crop&w=580&q=80";
const DEFAULT_HOMESTAY =
  "https://images.unsplash.com/photo-1512917774080-9991f1c4c750?ixlib=rb-4.0.3&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&auto=format&fit=crop&w=1170&q=80";
const DEFAULT_COVER =
  "https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?ixlib=rb-4.0.3&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&auto=format&fit=crop&w=1170&q=80";

// 路径配置
const PATH_CONFIG = {
  avatar: {
    folder: "avatar",
    endpoint: "avatar",
    default: DEFAULT_AVATAR,
  },
  homestay: {
    folder: "homestay",
    endpoint: "homestay",
    default: DEFAULT_HOMESTAY,
  },
  review: {
    folder: "review",
    endpoint: "review",
    default: DEFAULT_AVATAR,
  },
  common: {
    folder: "common",
    endpoint: "common",
    default: DEFAULT_COVER,
  },
};

/**
 * 处理图片URL的基础函数
 * @param url 原始图片URL
 * @param type 图片类型
 * @returns 处理后的图片URL
 */
/**
 * 为URL添加时间戳，防止缓存
 * @param url 原始URL
 * @returns 添加时间戳后的URL
 */
export function addTimestampToUrl(url: string): string {
  if (!url) return url;

  const timestamp = new Date().getTime();
  const separator = url.includes("?") ? "&" : "?";
  return `${url}${separator}t=${timestamp}`;
}

/**
 * 获取头像URL (添加防缓存参数)
 * @param filename 文件名
 * @param preventCache 是否添加防缓存参数
 * @returns 完整的头像URL
 */
export const getAvatarUrl = (filename: string, preventCache: boolean = true): string => {
  if (!filename) {
    console.error("尝试获取空文件名的头像URL");
    return PATH_CONFIG?.avatar?.default || "/assets/default-avatar.png";
  }

  // 如果已经是完整URL，直接返回
  if (filename.startsWith("http://") || filename.startsWith("https://")) {
    const url = filename;
    return preventCache ? addTimestampToUrl(url) : url;
  }

  // 提取文件名（不管是完整路径还是文件名）
  const fileNameOnly = filename.split("/").pop() || filename;

  // 构建标准API路径
  const apiPath = `${API_BASE_URL}/api/files/avatar/${fileNameOnly}`;

  // 添加时间戳防止缓存
  return preventCache ? addTimestampToUrl(apiPath) : apiPath;
};

/**
 * 处理房源图片URL
 * @param url 原始房源图片URL
 * @returns 处理后的房源图片URL
 */
export function getHomestayImageUrl(url?: string): string {
  if (!url) {
    return PATH_CONFIG.homestay.default;
  }

  // 如果已经是完整URL，直接返回
  if (url.startsWith("http://") || url.startsWith("https://")) {
    return url;
  }

  // 提取文件名（不管是完整路径还是文件名）
  const fileNameOnly = url.split("/").pop() || url;

  // 构建标准API路径
  return `${API_BASE_URL}/api/files/homestay/${fileNameOnly}`;
}

/**
 * 处理评论用户头像URL
 * @param url 原始评论用户头像URL
 * @returns 处理后的评论用户头像URL
 */
export function getReviewAvatarUrl(url?: string): string {
  if (!url) {
    return PATH_CONFIG.review.default;
  }

  // 如果已经是完整URL，直接返回
  if (url.startsWith("http://") || url.startsWith("https://")) {
    return url;
  }

  // 提取文件名（不管是完整路径还是文件名）
  const fileNameOnly = url.split("/").pop() || url;

  // 构建标准API路径
  return `${API_BASE_URL}/api/files/review/${fileNameOnly}`;
}

/**
 * 处理通用图片URL
 * @param url 原始图片URL
 * @returns 处理后的图片URL
 */
export function getCommonImageUrl(url?: string): string {
  if (!url) {
    return PATH_CONFIG.common.default;
  }

  // 如果已经是完整URL，直接返回
  if (url.startsWith("http://") || url.startsWith("https://")) {
    return url;
  }

  // 提取文件名（不管是完整路径还是文件名）
  const fileNameOnly = url.split("/").pop() || url;

  // 构建标准API路径
  return `${API_BASE_URL}/api/files/common/${fileNameOnly}`;
}

/**
 * 处理图片加载错误
 * @param event 错误事件
 * @param type 图片类型
 */
export function handleImageError(
  event: Event,
  type: "avatar" | "homestay" | "review" | "common" = "common"
): void {
  try {
    const img = event.target as HTMLImageElement;
    console.error(`图片加载失败: ${img.src}`);

    // 检查type是否为有效的类型
    if (typeof type === "string" && !["avatar", "homestay", "review", "common"].includes(type)) {
      console.error(`无效的图片类型: ${type}，使用默认类型'avatar'`);
      type = "avatar"; // 默认使用avatar类型
    }

    // 防止PATH_CONFIG为undefined导致的错误
    if (!PATH_CONFIG || !PATH_CONFIG[type]) {
      console.error(`图片类型配置错误: ${type}`);
      // 使用默认图片
      switch (type) {
        case "avatar":
          img.src = DEFAULT_AVATAR;
          break;
        case "homestay":
          img.src = DEFAULT_HOMESTAY;
          break;
        case "review":
          img.src = DEFAULT_AVATAR; // 评论也使用默认头像
          break;
        default:
          img.src = DEFAULT_COVER;
      }
      return;
    }

    // 正常使用配置的默认图片
    img.src = PATH_CONFIG[type].default;
  } catch (error) {
    console.error(`处理图片错误失败:`, error);
    // 防止完全崩溃，至少尝试设置一个默认图片
    try {
      const img = event.target as HTMLImageElement;
      img.src = DEFAULT_AVATAR;
    } catch (e) {
      console.error("无法设置默认图片", e);
    }
  }
}

/**
 * 处理多图预览
 * @param images 图片URL数组
 * @returns 处理后的图片URL数组
 */
export function processMultipleImages(images: string[] = []): string[] {
  return images.map((url) => getHomestayImageUrl(url));
}

// 导出默认值，便于其他组件使用
export const DEFAULT_IMAGES = {
  AVATAR: DEFAULT_AVATAR,
  HOMESTAY: DEFAULT_HOMESTAY,
  COVER: DEFAULT_COVER,
};
