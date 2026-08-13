import { shallowRef } from "vue";
import { useNotificationStore } from "@/stores/notification";
import { useChatStore } from "@/stores/chat";
import { normalizeNotification } from "@/types/notification";
import SockJS from "sockjs-client";
import Stomp from "stompjs";

let stompClient: any = null;
let activeUserId: number | null = null;
let activeToken: string | null = null;
let reconnectAttempts = 0;
let reconnectTimer: number | null = null;
let manuallyDisconnected = false;
let listenersRegistered = false;

const isConnected = shallowRef(false);

const getApiBaseUrl = () =>
  import.meta.env.VITE_API_URL ||
  import.meta.env.VITE_API_BASE_URL ||
  "http://localhost:8081";

const getStoredToken = () => {
  const token =
    localStorage.getItem("homestay_token") ||
    sessionStorage.getItem("homestay_token") ||
    localStorage.getItem("token") ||
    sessionStorage.getItem("token");

  if (!token) {
    return null;
  }

  return token.startsWith("Bearer ") ? token.slice(7) : token;
};

const clearReconnectTimer = () => {
  if (reconnectTimer !== null) {
    window.clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
};

const closeCurrentClient = () => {
  const client = stompClient;
  stompClient = null;
  isConnected.value = false;

  if (!client) {
    return;
  }

  try {
    if (client.connected) {
      client.disconnect(() => undefined);
    } else {
      client.ws?.close?.();
    }
  } catch (error) {
    console.warn("关闭 WebSocket 连接失败:", error);
  }
};

const scheduleReconnect = () => {
  if (manuallyDisconnected || !activeUserId || reconnectTimer !== null) {
    return;
  }

  if (typeof navigator !== "undefined" && !navigator.onLine) {
    return;
  }

  const delay = Math.min(3000 * 2 ** reconnectAttempts, 60000);
  reconnectAttempts += 1;
  reconnectTimer = window.setTimeout(() => {
    reconnectTimer = null;
    connect(activeUserId);
  }, delay);
};

const handleConnectionLost = () => {
  if (manuallyDisconnected) {
    return;
  }

  isConnected.value = false;
  closeCurrentClient();
  scheduleReconnect();
};

const registerNetworkListeners = () => {
  if (listenersRegistered || typeof window === "undefined") {
    return;
  }

  window.addEventListener("online", () => {
    if (!manuallyDisconnected && activeUserId && !isWebSocketConnected()) {
      reconnectAttempts = 0;
      clearReconnectTimer();
      connect(activeUserId);
    }
  });

  window.addEventListener("offline", () => {
    isConnected.value = false;
    clearReconnectTimer();
    closeCurrentClient();
  });

  listenersRegistered = true;
};

const parseUnreadCount = (body: string) => {
  try {
    const parsed = JSON.parse(body);
    if (typeof parsed === "number") {
      return parsed;
    }
    return Number(parsed?.unreadCount ?? parsed?.count ?? 0);
  } catch {
    return Number(body);
  }
};

const subscribeUserQueues = (userId: number) => {
  stompClient.subscribe("/user/queue/notifications", (message: any) => {
    try {
      const notificationDTO = normalizeNotification(JSON.parse(message.body));
      const notificationStore = useNotificationStore();
      notificationStore.upsertRealtimeNotification(notificationDTO);
    } catch (error) {
      console.error("解析实时通知失败:", error);
    }
  });

  stompClient.subscribe("/user/queue/unread-count", (message: any) => {
    const count = parseUnreadCount(message.body);
    const notificationStore = useNotificationStore();
    notificationStore.setUnreadCount(Number.isFinite(count) ? count : 0);
  });

  stompClient.subscribe(`/topic/chat/user/${userId}`, (message: any) => {
    try {
      const chatStore = useChatStore();
      chatStore.handleNewMessage(JSON.parse(message.body));
    } catch (error) {
      console.error("解析聊天消息失败:", error);
    }
  });

  stompClient.subscribe(`/topic/calendar/${userId}`, (message: any) => {
    try {
      window.dispatchEvent(
        new CustomEvent("calendar-update", { detail: JSON.parse(message.body) })
      );
    } catch (error) {
      console.error("解析日历更新失败:", error);
    }
  });
};

const connect = (userId: number | null) => {
  if (!userId) {
    return;
  }

  const token = getStoredToken();
  if (!token) {
    console.warn("缺少 WebSocket token，跳过连接");
    return;
  }

  if (
    stompClient &&
    isWebSocketConnected() &&
    activeUserId === userId &&
    activeToken === token
  ) {
    return;
  }

  activeUserId = userId;
  activeToken = token;
  closeCurrentClient();

  if (typeof navigator !== "undefined" && !navigator.onLine) {
    return;
  }

  const socket = new SockJS(`${getApiBaseUrl()}/ws`);
  stompClient = Stomp.over(socket);
  stompClient.debug = () => undefined;

  stompClient.connect(
    { Authorization: `Bearer ${token}` },
    () => {
      isConnected.value = true;
      reconnectAttempts = 0;
      clearReconnectTimer();
      subscribeUserQueues(userId);

      const notificationStore = useNotificationStore();
      notificationStore.fetchUnreadCount();
    },
    (error: any) => {
      console.error("WebSocket 连接错误:", error);
      handleConnectionLost();
    }
  );
};

export const initWebSocket = (userId: number | null) => {
  if (!userId) {
    return;
  }

  manuallyDisconnected = false;
  registerNetworkListeners();
  connect(userId);
};

export const disconnectWebSocket = () => {
  manuallyDisconnected = true;
  activeUserId = null;
  activeToken = null;
  reconnectAttempts = 0;
  clearReconnectTimer();
  closeCurrentClient();
};

export const isWebSocketConnected = (): boolean =>
  Boolean(isConnected.value && stompClient?.connected);

export const useWebSocket = (userId: number | null = null) => {
  if (userId) {
    initWebSocket(userId);
  }

  return {
    initWebSocket,
    disconnectWebSocket,
    isWebSocketConnected,
    isConnected,
  };
};
