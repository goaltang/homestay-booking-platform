import request from "@/utils/request"; // 导入封装好的 axios 实例
import type { NotificationPageData, NotificationDto } from "@/types/notification";
import { normalizeNotification } from "@/types/notification";

// 定义通用的分页参数类型 (如果项目中还没有)
interface PageParams {
  page: number;
  size: number;
}

/**
 * 获取通知列表
 * @param params - 查询参数，包含分页、isRead 状态和可选的 type
 */
export const getNotifications = (
  params: PageParams & { isRead?: boolean; type?: string }
): Promise<{ data: NotificationPageData }> => {
  // 后端分页是从0开始，前端通常从1开始，这里进行转换
  const adjustedParams: any = {
    ...params,
    page: params.page > 0 ? params.page - 1 : 0, // 页码转为0-based
  };
  // 移除 undefined 的参数
  if (!adjustedParams.type) delete adjustedParams.type;
  if (adjustedParams.isRead === undefined) delete adjustedParams.isRead;
  return request({
    url: "/api/notifications",
    method: "get",
    params: adjustedParams,
  }).then((response) => ({
    ...response,
    data: {
      ...response.data,
      content: (response.data.content || []).map(normalizeNotification),
    },
  }));
};

/**
 * 将单个通知标记为已读
 * @param notificationId - 通知 ID
 */
export const markAsRead = (notificationId: number): Promise<NotificationDto> => {
  return request({
    url: "/api/notifications/" + notificationId + "/read",
    method: "post",
  }).then((response) => normalizeNotification(response.data));
};

/**
 * 将所有未读通知标记为已读
 */
export const markAllAsRead = (): Promise<{ markedCount: number }> => {
  return request({
    url: "/api/notifications/read-all",
    method: "post",
  }).then((response) => response.data);
};

/**
 * 删除单个通知
 * @param notificationId - 通知 ID
 */
export const deleteNotification = (notificationId: number): Promise<void> => {
  return request({
    url: "/api/notifications/" + notificationId,
    method: "delete",
  }).then(() => undefined);
};

/**
 * 批量标记通知为已读
 * @param notificationIds - 通知 ID 数组
 */
export const markMultipleAsRead = (notificationIds: number[]): Promise<{ markedCount: number }> => {
  return request({
    url: "/api/notifications/read-multiple",
    method: "post",
    data: notificationIds,
  }).then((response) => response.data);
};

/**
 * 批量删除通知
 * @param notificationIds - 通知 ID 数组
 */
export const deleteMultipleNotifications = (
  notificationIds: number[]
): Promise<{ deletedCount: number }> => {
  return request({
    url: "/api/notifications/delete-multiple",
    method: "post",
    data: notificationIds,
  }).then((response) => response.data);
};

/**
 * 获取未读通知数量
 */
export const getUnreadCount = (): Promise<{ unreadCount: number }> => {
  return request({
    url: "/api/notifications/unread-count",
    method: "get",
  }).then((response) => response.data);
};

/**
 * 获取通知偏好设置
 */
export const getNotificationPreferences = (): Promise<Record<string, boolean>> => {
  return request({
    url: "/api/notifications/preferences",
    method: "get",
  }).then((response) => response.data);
};

/**
 * 更新指定业务域的通知偏好
 * @param domain - 业务域名称（小写）
 * @param enabled - 是否开启
 */
export const updateNotificationPreference = (domain: string, enabled: boolean): Promise<void> => {
  return request({
    url: `/api/notifications/preferences/${domain}`,
    method: "put",
    data: { enabled },
  }).then(() => undefined);
};
