import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { ElMessage } from "element-plus";
import api from "@/utils/request";
import type { Conversation, Message, ChatMessage } from "@/types/chat";

export const useChatStore = defineStore("chat", () => {
  const conversations = ref<Conversation[]>([]);
  const currentConversation = ref<Conversation | null>(null);
  const messages = ref<Message[]>([]);
  const unreadCount = ref(0);
  const loading = ref(false);
  const chatDialogVisible = ref(false);
  const targetHostId = ref<number | null>(null);
  const targetHomestayId = ref<number | null>(null);

  const isLoggedIn = computed(() => {
    // token 已迁移 httpOnly cookie，登录态以 userInfo 为准
    return !!(localStorage.getItem("homestay_user") || localStorage.getItem("userInfo"));
  });

  const currentUserId = computed(() => {
    const userInfo = localStorage.getItem("homestay_user") || localStorage.getItem("userInfo");
    if (userInfo) {
      try {
        return JSON.parse(userInfo).id;
      } catch {
        return null;
      }
    }
    return null;
  });

  const fetchConversations = async () => {
    loading.value = true;
    try {
      const response = await api.get("/api/chat/conversations");
      conversations.value = response.data.data.content || [];
      updateUnreadCount();
    } catch (error) {
      console.error("获取对话列表失败:", error);
    } finally {
      loading.value = false;
    }
  };

  const getOrCreateConversation = async (homestayId: number | null, hostId: number) => {
    try {
      const response = await api.post("/api/chat/conversations", {
        homestayId,
        hostId
      });
      return response.data.data;
    } catch (error) {
      console.error("创建对话失败:", error);
      throw error;
    }
  };

  const fetchMessages = async (conversationId: number) => {
    loading.value = true;
    try {
      const response = await api.get(`/api/chat/conversations/${conversationId}/messages`);
      messages.value = response.data.data.content || [];
    } catch (error) {
      console.error("获取消息列表失败:", error);
    } finally {
      loading.value = false;
    }
  };

  const sendMessage = async (conversationId: number, content: string) => {
    try {
      const response = await api.post(`/api/chat/conversations/${conversationId}/messages`, {
        content
      });
      const newMessage = response.data.data;
      messages.value.unshift(newMessage);
      return newMessage;
    } catch (error) {
      console.error("发送消息失败:", error);
      throw error;
    }
  };

  const markAsRead = async (conversationId: number) => {
    try {
      await api.put(`/api/chat/conversations/${conversationId}/read`);
      const conv = conversations.value.find((c) => c.id === conversationId);
      if (conv) conv.unreadCount = 0;
      updateUnreadCount();
    } catch (error) {
      console.error("标记已读失败:", error);
    }
  };

  const openChatDialog = async (homestayId: number | null, hostId: number) => {
    if (!isLoggedIn.value) {
      ElMessage.warning("请先登录");
      return;
    }
    targetHomestayId.value = homestayId;
    targetHostId.value = hostId;
    try {
      const conv = await getOrCreateConversation(homestayId, hostId);
      currentConversation.value = conv;
      await fetchMessages(conv.id);
      await markAsRead(conv.id);
      chatDialogVisible.value = true;
    } catch (error) {
      ElMessage.error("打开聊天失败");
    }
  };

  const closeChatDialog = () => {
    chatDialogVisible.value = false;
    currentConversation.value = null;
    messages.value = [];
  };

  const handleNewMessage = (chatMessage: ChatMessage) => {
    if (currentConversation.value?.id === chatMessage.conversationId) {
      const exists = messages.value.find((m) => m.id === chatMessage.message.id);
      if (!exists) {
        messages.value.unshift(chatMessage.message);
      }
      if (chatMessage.message.senderId !== currentUserId.value) {
        markAsRead(chatMessage.conversationId);
      }
    }
    const convIndex = conversations.value.findIndex(
      (c) => c.id === chatMessage.conversationId
    );
    if (convIndex !== -1) {
      const conv = conversations.value[convIndex];
      conv.lastMessageContent = chatMessage.message.content;
      conv.lastMessageAt = chatMessage.message.createdAt;
      if (currentConversation.value?.id !== chatMessage.conversationId) {
        conv.unreadCount = chatMessage.unreadCount;
      }
    }
    updateUnreadCount();
  };

  const updateUnreadCount = () => {
    unreadCount.value = conversations.value.reduce((sum, c) => sum + c.unreadCount, 0);
  };

  const fetchUnreadCount = async () => {
    try {
      const response = await api.get("/api/chat/unread-count");
      unreadCount.value = response.data.data.unreadCount || 0;
    } catch (error) {
      console.error("获取未读数失败:", error);
    }
  };

  return {
    conversations,
    currentConversation,
    messages,
    unreadCount,
    loading,
    chatDialogVisible,
    targetHostId,
    targetHomestayId,
    isLoggedIn,
    currentUserId,
    fetchConversations,
    getOrCreateConversation,
    fetchMessages,
    sendMessage,
    markAsRead,
    openChatDialog,
    closeChatDialog,
    handleNewMessage,
    fetchUnreadCount,
  };
});
