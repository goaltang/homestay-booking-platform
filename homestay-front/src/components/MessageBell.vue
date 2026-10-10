<template>
  <el-popover placement="bottom-end" :width="300" trigger="click" @show="loadConversations">
    <template #reference>
      <el-badge
        :value="chatStore.unreadCount"
        :hidden="chatStore.unreadCount === 0"
        :max="99"
        type="primary"
        :class="['message-badge']"
      >
        <el-icon :size="20" class="message-icon">
          <ChatDotRound />
        </el-icon>
      </el-badge>
    </template>
    <template #default>
      <div class="message-panel">
        <div class="message-header">
          <span>新消息</span>
          <span v-if="chatStore.unreadCount > 0" class="unread-hint"
            >{{ chatStore.unreadCount }}条未读</span
          >
        </div>
        <el-scrollbar
          max-height="250px"
          class="message-list-scrollbar"
          v-if="conversations.length > 0"
        >
          <div class="message-preview-list">
            <div
              v-for="conv in recentConversations"
              :key="conv.id"
              class="message-preview-item"
              @click="goToMessages"
            >
              <img :src="getAvatar(conv)" class="preview-avatar" />
              <div class="preview-content">
                <div class="preview-header">
                  <span class="preview-username">{{ getUsername(conv) }}</span>
                  <span class="preview-time">{{ formatTime(conv.lastMessageAt) }}</span>
                </div>
                <div class="preview-message">{{ conv.lastMessageContent || "暂无消息" }}</div>
              </div>
              <span v-if="conv.unreadCount > 0" class="preview-unread">{{ conv.unreadCount }}</span>
            </div>
          </div>
        </el-scrollbar>
        <div v-else class="message-empty">
          <el-empty description="暂无消息" :image-size="40" />
        </div>
        <div class="message-footer">
          <el-button type="primary" link @click="goToMessages"> 查看全部消息 </el-button>
        </div>
      </div>
    </template>
  </el-popover>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import { ChatDotRound } from "@element-plus/icons-vue";
import { useChatStore } from "@/stores/chat";
import type { Conversation } from "@/types/chat";

const chatStore = useChatStore();
const router = useRouter();

const conversations = computed(() => chatStore.conversations);
const recentConversations = computed(() => conversations.value.slice(0, 5));

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
    return date.toLocaleDateString("zh-CN", { weekday: "short" });
  } else {
    return date.toLocaleDateString("zh-CN", { month: "short", day: "numeric" });
  }
};

const loadConversations = async () => {
  if (conversations.value.length === 0) {
    await chatStore.fetchConversations();
  }
};

const goToMessages = () => {
  router.push("/host/messages");
};
</script>

<style scoped>
.message-badge {
  cursor: pointer;
}

.message-icon {
  cursor: pointer;
}

.message-panel {
  display: flex;
  flex-direction: column;
}

.message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 1px solid #eee;
  margin-bottom: 12px;
}

.message-header span:first-child {
  font-weight: 600;
  font-size: 15px;
}

.unread-hint {
  font-size: 12px;
  color: #409eff;
  font-weight: 500;
}

.message-preview-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.message-preview-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.message-preview-item:hover {
  background-color: #f5f7fa;
}

.preview-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

.preview-content {
  flex: 1;
  min-width: 0;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2px;
}

.preview-username {
  font-weight: 500;
  font-size: 13px;
  color: #303133;
}

.preview-time {
  font-size: 11px;
  color: #909399;
}

.preview-message {
  font-size: 12px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-unread {
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
  flex-shrink: 0;
}

.message-empty {
  padding: 20px 0;
}

.message-footer {
  padding-top: 12px;
  border-top: 1px solid #eee;
  margin-top: 12px;
  text-align: center;
}
</style>
