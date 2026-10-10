import { defineStore } from "pinia";
import { ref, computed } from "vue";
import api from "@/utils/request"; // 你的 axios 实例
import type { Page } from "@/types/page"; // 假设你有一个 Page 类型
import type { NotificationDto } from "@/types/notification";
import { normalizeNotification } from "@/types/notification";
import {
  getNotificationPreferences,
  updateNotificationPreference,
} from "@/services/notificationService";

// 定义通知的数据结构 (与后端的 NotificationDto 对应)
export type Notification = NotificationDto;

// 定义后端返回的未读数量接口响应结构
interface UnreadCountResponse {
  unreadCount: number;
}

export const useNotificationStore = defineStore("notification", () => {
  // --- State ---
  const unreadCount = ref<number>(0);
  const notifications = ref<Notification[]>([]); // 用于下拉列表或通知中心
  const loading = ref<boolean>(false);
  const pagination = ref<Page<Notification> | null>(null); // 用于通知中心分页
  const preferences = ref<Record<string, boolean>>({}); // 通知偏好

  // --- Getters ---
  const hasUnread = computed(() => unreadCount.value > 0);

  // --- Actions ---

  const setUnreadCount = (count: number) => {
    unreadCount.value = Math.max(0, count);
  };

  const upsertRealtimeNotification = (notification: NotificationDto) => {
    const normalizedNotification = normalizeNotification(notification);

    const listIndex = notifications.value.findIndex(
      (item) => item.id === normalizedNotification.id
    );
    const isNew = listIndex === -1;

    if (isNew) {
      notifications.value.unshift(normalizedNotification);
      // 新通知且未读时，本地未读计数+1（WebSocket unread-count 消息可能稍后到达）
      if (!normalizedNotification.read) {
        setUnreadCount(unreadCount.value + 1);
      }
    } else {
      const wasUnread = !notifications.value[listIndex].read;
      const nowUnread = !normalizedNotification.read;
      notifications.value[listIndex] = {
        ...notifications.value[listIndex],
        ...normalizedNotification,
      };
      // 同步更新未读计数（如通知从未读变为已读）
      if (wasUnread && !nowUnread) {
        setUnreadCount(unreadCount.value - 1);
      }
    }

    if (pagination.value) {
      const pageIndex = pagination.value.content.findIndex(
        (item) => item.id === normalizedNotification.id
      );

      if (pageIndex === -1) {
        const newTotalElements = pagination.value.totalElements + 1;
        const newTotalPages = Math.ceil(newTotalElements / pagination.value.size);
        const nextContent =
          pagination.value.number === 0
            ? [normalizedNotification, ...pagination.value.content].slice(0, pagination.value.size)
            : pagination.value.content;

        pagination.value = {
          ...pagination.value,
          content: nextContent,
          totalElements: newTotalElements,
          totalPages: newTotalPages,
        };
      } else {
        const nextContent = [...pagination.value.content];
        nextContent[pageIndex] = {
          ...nextContent[pageIndex],
          ...normalizedNotification,
        };
        pagination.value = {
          ...pagination.value,
          content: nextContent,
        };
      }
    }
  };

  const clearNotifications = () => {
    notifications.value = [];
    pagination.value = null;
  };

  /**
   * 获取当前用户的未读通知数量
   */
  const fetchUnreadCount = async () => {
    // 不需要设置 loading，这个请求通常比较快，在后台静默执行
    try {
      const response = await api.get<UnreadCountResponse>("/api/notifications/unread-count");
      setUnreadCount(response.data.unreadCount || 0);
      console.log("获取到未读通知数量:", unreadCount.value);
      return unreadCount.value;
    } catch (error) {
      console.error("获取未读通知数量失败:", error);
      // 失败时不重置为0，避免误导用户
      return unreadCount.value;
    }
  };

  /**
   * 获取通知列表（分页）
   * @param page 页码 (0-based)
   * @param size 每页数量
   * @param isReadFilter 筛选条件 (true: 只看已读, false: 只看未读, null: 所有)
   * @param target 'dropdown' | 'center' - 用于区分是加载少量给下拉还是加载分页给中心
   */
  const fetchNotifications = async (
    page: number = 0,
    size: number = 5, // 下拉默认少一点，中心可以多一点
    isReadFilter: boolean | null = null,
    target: "dropdown" | "center" = "dropdown"
  ) => {
    loading.value = true;
    try {
      const params: Record<string, any> = { page, size };
      if (isReadFilter !== null) {
        params.isRead = isReadFilter;
      }
      console.log(
        `请求通知列表: page=${page}, size=${size}, isRead=${isReadFilter}, target=${target}`
      );

      const response = await api.get<Page<Notification>>("/api/notifications", {
        params,
      });

      if (target === "dropdown") {
        // 更新下拉列表（通常是最新的几条）
        notifications.value = (response.data.content || []).map(normalizeNotification);
        console.log("更新通知下拉列表:", notifications.value);
        return null;
      } else {
        // 更新通知中心的分页数据
        const content = (response.data.content || []).map(normalizeNotification);
        pagination.value = {
          ...response.data,
          content,
        };
        notifications.value = content; // 也更新列表，方便直接使用
        console.log("更新通知中心数据:", pagination.value);
        return pagination.value;
      }
    } catch (error) {
      console.error(`获取通知列表 (${target}) 失败:`, error);
      if (target === "center" && pagination.value) {
        pagination.value = {
          ...pagination.value,
          content: [],
        };
      }
      return null;
    } finally {
      loading.value = false;
    }
  };

  /**
   * 将单条通知标记为已读
   * @param notificationId 通知 ID
   */
  const markAsRead = async (notificationId: number) => {
    try {
      const response = await api.post<Notification>(`/api/notifications/${notificationId}/read`);
      const updatedNotification = normalizeNotification(response.data);
      console.log(`通知 ${notificationId} 已标记为已读 (API 调用成功)`);

      let wasUnread = false;

      // 更新前端状态
      const notification = notifications.value.find((n) => n.id === notificationId);
      if (notification && !notification.read) {
        notification.read = true;
        notification.readAt = updatedNotification.readAt || new Date().toISOString();
        wasUnread = true;
      }
      // 如果是在通知中心操作，同步更新分页对象中的数据
      if (pagination.value) {
        const notificationInPage = pagination.value.content.find((n) => n.id === notificationId);
        if (notificationInPage && !notificationInPage.read) {
          notificationInPage.read = true;
          notificationInPage.readAt = updatedNotification.readAt || new Date().toISOString();
          wasUnread = true;
        }
      }

      if (wasUnread) {
        setUnreadCount(unreadCount.value - 1);
        console.log(`前端状态更新: 通知 ${notificationId} 标记已读，未读数: ${unreadCount.value}`);
      }

      return updatedNotification;
    } catch (error) {
      console.error(`标记通知 ${notificationId} 为已读失败:`, error);
      // 可以考虑显示错误消息
      throw error;
    }
  };

  /**
   * 将所有未读通知标记为已读
   */
  const markAllAsRead = async () => {
    try {
      const response = await api.post<{ markedCount: number }>("/api/notifications/read-all");
      const markedCount = response.data.markedCount || 0;
      console.log(`API 响应: ${markedCount} 条通知已标记为已读`);

      // 更新前端状态
      setUnreadCount(0);
      notifications.value.forEach((n) => {
        if (!n.read) {
          n.read = true;
          n.readAt = new Date().toISOString();
        }
      });
      if (pagination.value) {
        pagination.value.content.forEach((n) => {
          if (!n.read) {
            n.read = true;
            n.readAt = new Date().toISOString();
          }
        });
      }
      console.log("前端状态更新: 所有通知标记已读，未读数: 0");
      return markedCount;
    } catch (error) {
      console.error("标记所有通知为已读失败:", error);
      // 可以考虑显示错误消息
      throw error;
    }
  };

  /**
   * 删除单条通知
   * @param notificationId 通知 ID
   */
  const deleteNotification = async (notificationId: number) => {
    const existingNotification = notifications.value.find((n) => n.id === notificationId);
    const wasUnread = existingNotification ? !existingNotification.read : false;

    try {
      await api.delete(`/api/notifications/${notificationId}`);
      notifications.value = notifications.value.filter((n) => n.id !== notificationId);

      if (pagination.value) {
        pagination.value = {
          ...pagination.value,
          content: pagination.value.content.filter((n) => n.id !== notificationId),
          totalElements: Math.max(0, pagination.value.totalElements - 1),
        };
      }

      if (wasUnread) {
        setUnreadCount(unreadCount.value - 1);
      }
      await fetchUnreadCount();
    } catch (error) {
      console.error(`删除通知 ${notificationId} 失败:`, error);
      throw error;
    }
  };

  /**
   * 获取通知偏好设置
   */
  const fetchPreferences = async () => {
    try {
      const data = await getNotificationPreferences();
      preferences.value = data;
      console.log("获取通知偏好:", preferences.value);
    } catch (error) {
      console.error("获取通知偏好失败:", error);
    }
  };

  /**
   * 更新指定业务域的通知偏好
   */
  const updatePreference = async (domain: string, enabled: boolean) => {
    try {
      await updateNotificationPreference(domain, enabled);
      preferences.value = { ...preferences.value, [domain]: enabled };
      console.log(`更新通知偏好: domain=${domain}, enabled=${enabled}`);
    } catch (error) {
      console.error(`更新通知偏好失败: domain=${domain}`, error);
      throw error;
    }
  };

  // --- 初始化 ---
  // 应用启动时获取一次未读数量 (可以在 App.vue 或布局组件中调用)
  // fetchUnreadCount();

  return {
    unreadCount,
    notifications,
    loading,
    pagination,
    hasUnread,
    setUnreadCount,
    upsertRealtimeNotification,
    clearNotifications,
    fetchUnreadCount,
    fetchNotifications,
    markAsRead,
    markAllAsRead,
    deleteNotification,
    preferences,
    fetchPreferences,
    updatePreference,
  };
});

// 定义分页类型 (如果还没有的话)
// 可以放在 @/types/page.ts
/*
export interface Page<T> {
    content: T[];
    pageable: {
        pageNumber: number;
        pageSize: number;
        sort: {
            sorted: boolean;
            unsorted: boolean;
            empty: boolean;
        };
        offset: number;
        paged: boolean;
        unpaged: boolean;
    };
    totalPages: number;
    totalElements: number;
    last: boolean;
    size: number;
    number: number; // 当前页码 (0-based)
    sort: {
        sorted: boolean;
        unsorted: boolean;
        empty: boolean;
    };
    numberOfElements: number; // 当前页元素数量
    first: boolean;
    empty: boolean;
}
*/
