<template>
  <div class="message-center">
    <el-card>
      <template #header>
        <div class="header">
          <span>消息中心</span>
          <el-badge :value="totalUnread" :hidden="totalUnread === 0" type="primary">
            <span>全部对话</span>
          </el-badge>
        </div>
      </template>

      <div v-loading="loading">
        <div v-if="conversations.length === 0" class="empty-state">
          <el-empty description="暂无消息" />
        </div>
        <div v-else class="conversation-list">
          <div
            v-for="conv in conversations"
            :key="conv.id"
            class="conversation-item"
            :class="{ unread: conv.unreadCount > 0 }"
            @click="openChat(conv)"
          >
            <div class="conv-avatar">
              <img :src="getAvatar(conv)" class="avatar" />
              <span v-if="conv.unreadCount > 0" class="unread-badge">{{
                conv.unreadCount > 99 ? "99+" : conv.unreadCount
              }}</span>
            </div>
            <div class="conv-content">
              <div class="conv-header">
                <span class="username">{{ getUsername(conv) }}</span>
                <span class="time">{{ formatTime(conv.lastMessageAt) }}</span>
              </div>
              <div class="homestay-title" v-if="conv.homestayTitle">
                房源: {{ conv.homestayTitle }}
              </div>
              <div class="last-message">{{ conv.lastMessageContent || "暂无消息" }}</div>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 聊天对话框 -->
    <ChatDialog />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { useChatStore } from "@/stores/chat";
import ChatDialog from "@/components/chat/ChatDialog.vue";
import type { Conversation } from "@/types/chat";

const chatStore = useChatStore();
const loading = ref(false);

const conversations = computed(() => chatStore.conversations);
const totalUnread = computed(() => chatStore.unreadCount);

const currentUserId = computed(() => chatStore.currentUserId);

const getUsername = (conv: Conversation) => {
  return conv.hostId === currentUserId.value ? conv.guestUsername : conv.hostUsername;
};

const getAvatar = (conv: Conversation) => {
  const avatar = conv.hostId === currentUserId.value ? conv.guestAvatar : conv.hostAvatar;
  return avatar || defaultAvatar;
};

const defaultAvatar = "https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png";

const formatTime = (dateString: string | null) => {
  if (!dateString) return "";
  const date = new Date(dateString);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  const oneDay = 24 * 60 * 60 * 1000;

  if (diff < oneDay) {
    return date.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" });
  } else if (diff < 7 * oneDay) {
    return date.toLocaleDateString("zh-CN", {
      weekday: "short",
      hour: "2-digit",
      minute: "2-digit",
    });
  } else {
    return date.toLocaleDateString("zh-CN", { month: "short", day: "numeric" });
  }
};

const openChat = async (conv: Conversation) => {
  chatStore.currentConversation = conv;
  await chatStore.fetchMessages(conv.id);
  await chatStore.markAsRead(conv.id);
  chatStore.chatDialogVisible = true;
};

onMounted(async () => {
  loading.value = true;
  await chatStore.fetchConversations();
  loading.value = false;
});
</script>

<style scoped>
.message-center {
  padding: 20px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header span:first-child {
  font-size: 18px;
  font-weight: 600;
}

.empty-state {
  padding: 60px 0;
}

.conversation-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.conversation-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.3s;
}

.conversation-item:hover {
  background-color: #f5f7fa;
}

.conversation-item.unread {
  background-color: #ecf5ff;
}

.conversation-item.unread:hover {
  background-color: #e6effe;
}

.conv-avatar {
  position: relative;
  flex-shrink: 0;
}

.conv-avatar .avatar {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  object-fit: cover;
}

.unread-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  background-color: #f56c6c;
  color: white;
  font-size: 11px;
  min-width: 18px;
  height: 18px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 4px;
}

.conv-content {
  flex: 1;
  min-width: 0;
}

.conv-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.username {
  font-weight: 600;
  font-size: 15px;
  color: #303133;
}

.time {
  font-size: 12px;
  color: #909399;
}

.homestay-title {
  font-size: 12px;
  color: #409eff;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.last-message {
  font-size: 13px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-item.unread .last-message {
  color: #303133;
  font-weight: 500;
}
</style>
