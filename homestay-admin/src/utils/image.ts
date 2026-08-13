import type { Homestay } from "@/types";

/**
 * 获取房源主图片
 * 优先级：封面图片 > 图片集第一张 > 默认占位图
 */
export function getHomestayMainImage(homestay: Homestay): string {
  // 1. 优先使用封面图片
  if (homestay.coverImage && homestay.coverImage.trim()) {
    return homestay.coverImage;
  }

  // 2. 其次使用图片集的第一张
  if (homestay.images && homestay.images.length > 0) {
    const firstImage = homestay.images[0];
    if (firstImage && firstImage.trim()) {
      return firstImage;
    }
  }

  // 3. 返回默认占位图
  return "/placeholder-image.jpg";
}

/**
 * 获取房源所有图片
 * 返回包含封面图片和图片集的完整列表（去重）
 */
export function getHomestayAllImages(homestay: Homestay): string[] {
  const images: string[] = [];

  // 添加封面图片
  if (homestay.coverImage && homestay.coverImage.trim()) {
    images.push(homestay.coverImage);
  }

  // 添加图片集（排除已存在的封面图片）
  if (homestay.images && homestay.images.length > 0) {
    homestay.images.forEach((image) => {
      if (image && image.trim() && !images.includes(image)) {
        images.push(image);
      }
    });
  }

  return images;
}

/**
 * 确保图片URL是完整的
 */
export function ensureImageUrl(
  imagePath: string | null | undefined,
  baseUrl: string = "http://localhost:8081"
): string {
  if (!imagePath || !imagePath.trim()) {
    return "/placeholder-image.jpg";
  }

  // 如果已经是完整URL，直接返回
  if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
    return imagePath;
  }

  // 确保路径以/开头
  if (!imagePath.startsWith("/")) {
    imagePath = "/" + imagePath;
  }

  // 返回完整URL
  return baseUrl + imagePath;
}

/**
 * 检查图片URL是否有效
 */
export function isValidImageUrl(url: string): boolean {
  if (!url || !url.trim()) {
    return false;
  }

  // 简单的URL格式检查
  return (
    url.startsWith("http://") ||
    url.startsWith("https://") ||
    url.startsWith("/")
  );
}
