<template>
  <el-dialog
    v-model="agentStore.dialogVisible"
    title="AI 智能客服"
    width="480px"
    :before-close="handleClose"
    :close-on-click-modal="false"
    @open="handleOpen"
  >
    <div class="chat-container">
      <div class="message-list" ref="messageListRef">
        <div v-if="messages.length === 0" class="empty-messages">
          您好！我是 AI 智能客服，请问有什么可以帮您？
        </div>

        <template v-for="msg in messages" :key="msg.id">
          <div v-if="msg.type === 'system'" class="system-notice">
            <el-alert :title="msg.content" type="warning" :closable="false" show-icon />
          </div>

          <div
            v-else
            class="message-item"
            :class="{ 'message-self': msg.type === 'user' }"
          >
            <div v-if="msg.type === 'agent'" class="message-avatar">
              <el-tag size="small" type="primary">AI</el-tag>
            </div>
            <div class="message-content-wrapper">
              <div class="message-content">{{ msg.content }}</div>
            </div>
          </div>
        </template>

        <div v-if="sending" class="message-item">
          <div class="message-avatar">
            <el-tag size="small" type="primary">AI</el-tag>
          </div>
          <div class="message-content-wrapper">
            <div class="message-content loading-bubble">
              <span class="dot"></span>
              <span class="dot"></span>
              <span class="dot"></span>
            </div>
          </div>
        </div>
      </div>

      <div class="message-input">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="2"
          :disabled="handedOff"
          :placeholder="handedOff ? '已转接人工客服，请等待...' : '输入您的问题... (Enter发送，Shift+Enter换行)'"
          @keydown="handleKeydown"
        />
        <el-button
          type="primary"
          :disabled="!inputText.trim() || sending || handedOff"
          :loading="sending"
          @click="handleSend"
        >
          发送
        </el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, nextTick, watch } from "vue";
import { useSupportAgentStore } from "@/stores/supportAgent";
import { chatWithAgent } from "@/api/supportAgent";
import { ElMessage } from "element-plus";

interface ChatMessage {
  id: number;
  type: "user" | "agent" | "system";
  content: string;
}

let msgIdCounter = 0;
const nextMsgId = () => ++msgIdCounter;

const agentStore = useSupportAgentStore();
const messages = ref<ChatMessage[]>([]);
const inputText = ref("");
const sending = ref(false);
const handedOff = ref(false);
const conversationId = ref<string | undefined>(undefined);
const messageListRef = ref<HTMLElement | null>(null);

const scrollToBottom = () => {
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight;
  }
};

const handleOpen = () => {
  messages.value = [];
  inputText.value = "";
  sending.value = false;
  handedOff.value = false;
  conversationId.value = undefined;
};

const handleClose = () => {
  agentStore.closeAgentDialog();
};

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === "Enter" && !event.shiftKey) {
    event.preventDefault();
    handleSend();
  }
};

const handleSend = async () => {
  const question = inputText.value.trim();
  if (!question || sending.value || handedOff.value) return;

  messages.value.push({ id: nextMsgId(), type: "user", content: question });
  inputText.value = "";
  sending.value = true;
  await nextTick();
  scrollToBottom();

  try {
    const response = await chatWithAgent({
      question,
      conversationId: conversationId.value,
      orderId: agentStore.contextOrderId ?? undefined,
      homestayId: agentStore.contextHomestayId ?? undefined,
    });

    conversationId.value = response.conversationId;
    messages.value.push({ id: nextMsgId(), type: "agent", content: response.answer });

    if (response.handoffToHuman) {
      handedOff.value = true;
      messages.value.push({ id: nextMsgId(), type: "system", content: "已为您转接人工客服" });
    }
  } catch {
    ElMessage.error("AI 客服响应失败，请稍后重试");
    messages.value.push({ id: nextMsgId(), type: "agent", content: "抱歉，系统暂时无法响应，请稍后再试。" });
  } finally {
    sending.value = false;
    await nextTick();
    scrollToBottom();
  }
};

watch(
  () => messages.value.length,
  () => {
    nextTick(() => scrollToBottom());
  },
);
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 500px;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px 0;
  min-height: 300px;
  max-height: 380px;
}

.empty-messages {
  text-align: center;
  color: #909399;
  padding: 60px 0;
}

.system-notice {
  margin: 12px 0;
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
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
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

.loading-bubble {
  display: flex;
  gap: 4px;
  padding: 14px 18px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #909399;
  animation: dotBounce 1.4s infinite ease-in-out both;
}

.dot:nth-child(1) {
  animation-delay: -0.32s;
}

.dot:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes dotBounce {
  0%,
  80%,
  100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1);
  }
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
