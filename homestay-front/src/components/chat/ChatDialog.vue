<template>
  <el-dialog
    v-model="chatStore.chatDialogVisible"
    :title="dialogTitle"
    width="500px"
    :before-close="handleClose"
    :close-on-click-modal="false"
  >
    <div class="chat-container">
      <div class="chat-header" v-if="chatStore.currentConversation">
        <img :src="targetAvatar" class="avatar" />
        <div class="target-info">
          <span class="username">{{ targetUsername }}</span>
          <span class="homestay" v-if="chatStore.currentConversation.homestayTitle">
            房源: {{ chatStore.currentConversation.homestayTitle }}
          </span>
        </div>
      </div>

      <el-divider />

      <div class="message-list" ref="messageListRef">
        <div v-loading="chatStore.loading">
          <div v-if="chatStore.messages.length === 0" class="empty-messages">
            暂无消息,开始对话吧
          </div>
          <div
            v-for="message in chatStore.messages"
            :key="message.id"
            class="message-item"
            :class="{ 'message-self': isSelf(message.senderId) }"
          >
            <img
              v-if="!isSelf(message.senderId)"
              :src="message.senderAvatar || defaultAvatar"
              class="message-avatar"
            />
            <div class="message-content-wrapper">
              <div class="message-content">{{ message.content }}</div>
              <div class="message-time">{{ formatTime(message.createdAt) }}</div>
            </div>
          </div>
        </div>
      </div>

      <div class="message-input">
        <el-input
          v-model="messageContent"
          type="textarea"
          :rows="2"
          placeholder="输入消息... (Ctrl+Enter发送)"
          @keyup.enter.ctrl="sendMessageHandler"
        />
        <el-button type="primary" @click="sendMessageHandler" :disabled="!messageContent.trim()">
          发送
        </el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, watch } from "vue";
import { useChatStore } from "@/stores/chat";

const chatStore = useChatStore();
const messageContent = ref("");
const messageListRef = ref<HTMLElement | null>(null);

const defaultAvatar = "https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png";

const currentUserId = computed(() => chatStore.currentUserId);

const isSelf = (senderId: number) => senderId === currentUserId.value;

const targetUsername = computed(() => {
  const conv = chatStore.currentConversation;
  if (!conv) return "";
  return conv.hostId === currentUserId.value ? conv.guestUsername : conv.hostUsername;
});

const targetAvatar = computed(() => {
  const conv = chatStore.currentConversation;
  if (!conv) return defaultAvatar;
  const avatar = conv.hostId === currentUserId.value ? conv.guestAvatar : conv.hostAvatar;
  return avatar || defaultAvatar;
});

const dialogTitle = computed(() => {
  return targetUsername.value ? `与 ${targetUsername.value} 的对话` : "聊天";
});

const formatTime = (dateString: string) => {
  if (!dateString) return "";
  const date = new Date(dateString);
  return date.toLocaleString("zh-CN", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
};

const sendMessageHandler = async () => {
  if (!messageContent.value.trim() || !chatStore.currentConversation) return;
  try {
    await chatStore.sendMessage(chatStore.currentConversation.id, messageContent.value.trim());
    messageContent.value = "";
    await nextTick();
    scrollToBottom();
  } catch (error) {
  }
};

const scrollToBottom = () => {
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight;
  }
};

const handleClose = () => {
  chatStore.closeChatDialog();
};

watch(
  () => chatStore.messages.length,
  () => {
    nextTick(() => scrollToBottom());
  }
);
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 500px;
}

.chat-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.chat-header .avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
}

.target-info {
  display: flex;
  flex-direction: column;
}

.target-info .username {
  font-weight: bold;
  font-size: 16px;
}

.target-info .homestay {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px 0;
  min-height: 300px;
  max-height: 350px;
}

.empty-messages {
  text-align: center;
  color: #909399;
  padding: 60px 0;
}

.message-item {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  align-items: flex-start;
}

.message-item.message-self {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

.message-content-wrapper {
  max-width: 70%;
  display: flex;
  flex-direction: column;
}

.message-self .message-content-wrapper {
  align-items: flex-end;
}

.message-content {
  background-color: #f4f4f5;
  padding: 10px 14px;
  border-radius: 8px;
  word-break: break-word;
  line-height: 1.5;
}

.message-self .message-content {
  background-color: #409eff;
  color: white;
}

.message-time {
  font-size: 11px;
  color: #c0c4cc;
  margin-top: 4px;
}

.message-input {
  display: flex;
  gap: 10px;
  padding-top: 16px;
  border-top: 1px solid #eee;
}

.message-input .el-textarea {
  flex: 1;
}
</style>
