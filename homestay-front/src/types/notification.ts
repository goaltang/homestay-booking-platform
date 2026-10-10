import type { AxiosResponse } from "axios";

export type NotificationCategory =
  "order" | "message" | "review" | "homestay" | "coupon" | "system";

export interface NotificationDto {
  id: number;
  userId: number;
  actorId: number | null;
  type: string;
  rawType?: string | null;
  entityType: string | null;
  rawEntityType?: string | null;
  entityId: string | null;
  content: string;
  read: boolean;
  isRead?: boolean;
  readAt: string | null;
  createdAt: string;
  actorUsername?: string;
  entityTitle?: string;
  category?: NotificationCategory | string | null;
  title?: string | null;
  deepLink?: string | null;
  payload?: Record<string, unknown> | null;
}

export interface NotificationPageData {
  content: NotificationDto[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export type NotificationApiResponse = AxiosResponse<NotificationPageData>;

export const normalizeNotification = (notification: NotificationDto): NotificationDto => {
  const read = notification.read ?? notification.isRead ?? false;
  const type = notification.type || notification.rawType || "UNKNOWN";
  const entityType = notification.entityType || notification.rawEntityType || null;

  const normalized: NotificationDto = {
    ...notification,
    type,
    entityType,
    read,
    isRead: read,
  };

  // 开发环境断言：展示元数据应由后端 NotificationServiceImpl 统一注入
  if (import.meta.env.DEV) {
    if (!notification.category) {
      console.warn(
        `[Notification] 后端未返回 category，请检查 NotificationServiceImpl.applyPresentationMetadata: type=${type}`
      );
    }
    if (!notification.title) {
      console.warn(
        `[Notification] 后端未返回 title，请检查 NotificationServiceImpl.applyPresentationMetadata: type=${type}`
      );
    }
  }

  return normalized;
};

// 注：category / title / deepLink 由后端 NotificationServiceImpl.applyPresentationMetadata() 统一注入
// 前端直接信任后端返回的展示字段，不再维护本地解析映射
