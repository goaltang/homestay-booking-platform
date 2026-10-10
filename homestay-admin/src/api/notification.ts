import request from "@/utils/request";

export interface NotificationDto {
  id: number;
  userId: number;
  actorId: number | null;
  type: string;
  entityType: string | null;
  entityId: string | null;
  content: string;
  read: boolean;
  readAt: string | null;
  createdAt: string;
  actorUsername?: string;
  entityTitle?: string;
  category?: string;
  title?: string;
  deepLink?: string;
}

export interface NotificationListResponse {
  content: NotificationDto[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface UnreadCountResponse {
  unreadCount: number;
}

export interface NotificationTypeOption {
  value: string;
  label: string;
  domain: string;
  category: string;
}

export interface NotificationBroadcastJob {
  jobId: number;
  status: string;
  initiatedBy?: number | null;
  initiatedByUsername?: string | null;
  contentSummary: string;
  contentLength: number;
  targetCount: number;
  successCount: number;
  failureCount: number;
  failureReason?: string | null;
  submittedAt: string;
  startedAt?: string | null;
  completedAt?: string | null;
}

export interface NotificationBroadcastJobListResponse {
  content: NotificationBroadcastJob[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

/**
 * 获取用户通知列表
 */
export function getNotifications(params: {
  page?: number;
  size?: number;
  isRead?: boolean;
  type?: string;
}) {
  return request<NotificationListResponse>({
    url: "/api/notifications",
    method: "get",
    params,
  });
}

/**
 * 获取未读通知数量
 */
export function getUnreadCount() {
  return request<UnreadCountResponse>({
    url: "/api/notifications/unread-count",
    method: "get",
  });
}

/**
 * 获取通知类型筛选项
 */
export function getNotificationTypes() {
  return request<NotificationTypeOption[]>({
    url: "/api/notifications/types",
    method: "get",
  });
}

/**
 * 标记通知为已读
 */
export function markAsRead(notificationId: number) {
  return request({
    url: `/api/notifications/${notificationId}/read`,
    method: "post",
  });
}

/**
 * 批量标记通知为已读
 */
export function markMultipleAsRead(notificationIds: number[]) {
  return request<{ markedCount: number }>({
    url: "/api/notifications/read-multiple",
    method: "post",
    data: notificationIds,
  });
}

/**
 * 标记所有通知为已读
 */
export function markAllAsRead() {
  return request({
    url: "/api/notifications/read-all",
    method: "post",
  });
}

/**
 * 删除通知
 */
export function deleteNotification(notificationId: number) {
  return request({
    url: `/api/notifications/${notificationId}`,
    method: "delete",
  });
}

/**
 * 批量删除通知
 */
export function deleteMultipleNotifications(notificationIds: number[]) {
  return request<{ deletedCount: number }>({
    url: "/api/notifications/delete-multiple",
    method: "post",
    data: notificationIds,
  });
}

/**
 * 广播系统通知
 */
export function broadcastSystemNotification(content: string) {
  return request<NotificationBroadcastJob>({
    url: "/api/admin/notifications/broadcast",
    method: "post",
    data: { content },
  });
}

/**
 * 获取广播任务历史
 */
export function getNotificationBroadcastJobs(params: {
  page?: number;
  size?: number;
  status?: string;
}) {
  return request<NotificationBroadcastJobListResponse>({
    url: "/api/admin/notifications/broadcast-jobs",
    method: "get",
    params,
  });
}

/**
 * 获取广播任务详情
 */
export function getNotificationBroadcastJob(jobId: number) {
  return request<NotificationBroadcastJob>({
    url: `/api/admin/notifications/broadcast-jobs/${jobId}`,
    method: "get",
  });
}

/**
 * 格式化相对时间
 */
export function formatRelativeTime(dateString: string): string {
  const date = new Date(dateString);
  if (Number.isNaN(date.getTime())) {
    return "-";
  }
  const now = new Date();
  const diffInSeconds = Math.floor((now.getTime() - date.getTime()) / 1000);

  if (diffInSeconds < 60) {
    return "刚刚";
  } else if (diffInSeconds < 3600) {
    return `${Math.floor(diffInSeconds / 60)}分钟前`;
  } else if (diffInSeconds < 86400) {
    return `${Math.floor(diffInSeconds / 3600)}小时前`;
  } else if (diffInSeconds < 2592000) {
    return `${Math.floor(diffInSeconds / 86400)}天前`;
  } else {
    return date.toLocaleDateString("zh-CN");
  }
}
