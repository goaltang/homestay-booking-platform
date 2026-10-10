<template>
  <div class="countdown-container" :class="urgencyClass">
    <div class="countdown-title">{{ statusText }}倒计时</div>
    <div class="countdown-timer" :class="{ urgent: urgency === 'critical' || urgency === 'high' }">
      {{ countdownText }}
    </div>
    <div class="countdown-desc">
      {{ getTimeoutDescription() }}
    </div>

    <!-- 进度条 -->
    <div class="progress-bar" v-if="!isTimedOut && remainingTime !== Infinity">
      <div
        class="progress-fill"
        :style="{ width: progressPercentage + '%', backgroundColor: statusColor }"
      ></div>
    </div>

    <!-- 预警消息 -->
    <div class="warning-message" v-if="showWarning && warningMessage">
      <el-icon>
        <InfoFilled />
      </el-icon>
      <span>{{ warningMessage }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watchEffect } from "vue";
import { InfoFilled } from "@element-plus/icons-vue";
import {
  calculateRemainingTime,
  getTimeoutCountdownText,
  getTimeoutStatusColor,
  getOrderUrgency,
  setupTimeoutHandler,
  clearOrderTimers,
  isOrderTimedOut,
} from "@/utils/orderTimeout";
import { OrderStatus } from "@/types/order";
import { useOrderStore } from "@/stores/order";

interface Props {
  orderId: number;
  orderStatus: OrderStatus;
  createTime: string;
  confirmTime?: string;
  updateTime?: string;
  showProgress?: boolean;
  showWarning?: boolean;
}

interface Emits {
  (e: "timeout"): void;
  (e: "warning", remainingTime: number): void;
}

const props = withDefaults(defineProps<Props>(), {
  showProgress: true,
  showWarning: true,
});

const emit = defineEmits<Emits>();

// 响应式状态
const remainingTime = ref(0);
const isTimedOut = ref(false);
const urgency = ref<"low" | "medium" | "high" | "critical">("low");

// 更新间隔定时器
let updateInterval: ReturnType<typeof setInterval> | null = null;

// 计算属性
const statusColor = computed(() => getTimeoutStatusColor(remainingTime.value));

const urgencyClass = computed(() => `urgency-${urgency.value}`);

const statusText = computed(() => {
  if (isTimedOut.value) return "已超时";
  if (remainingTime.value === Infinity) return "无时间限制";

  switch (props.orderStatus) {
    case OrderStatus.PENDING:
      return "订单确认";
    case OrderStatus.CONFIRMED:
      return "支付";
    case OrderStatus.PAYMENT_PENDING:
      return "支付";
    default:
      return "订单";
  }
});

const countdownText = computed(() => {
  if (remainingTime.value === Infinity) return "";
  if (isTimedOut.value) return "已超时";

  // 始终显示秒数
  return getTimeoutCountdownText(remainingTime.value, true);
});

const progressPercentage = computed(() => {
  if (remainingTime.value === Infinity || isTimedOut.value) return 0;

  // 计算总超时时间
  const totalTimeout = getTotalTimeout();
  if (totalTimeout === 0) return 0;

  return Math.max(0, Math.min(100, (remainingTime.value / totalTimeout) * 100));
});

const warningMessage = computed(() => {
  if (!props.showWarning || urgency.value === "low") return "";

  switch (urgency.value) {
    case "critical":
      return "紧急：订单即将超时，请立即处理！";
    case "high":
      return "注意：订单即将超时，请尽快处理";
    case "medium":
      return "提醒：请注意订单处理时间";
    default:
      return "";
  }
});

const store = useOrderStore();

// 获取超时描述文本
function getTimeoutDescription(): string {
  const config = store.timeoutConfig;
  switch (props.orderStatus) {
    case OrderStatus.PENDING:
      return `超过${config?.pendingTimeoutHours ?? 2}小时未确认，订单将自动取消`;
    case OrderStatus.CONFIRMED:
    case OrderStatus.PAYMENT_PENDING:
      return `超过${config?.confirmedTimeoutHours ?? 2}小时未支付，订单将自动取消`;
    default:
      return "";
  }
}

// 方法
function getTotalTimeout(): number {
  const config = store.timeoutConfig;
  switch (props.orderStatus) {
    case OrderStatus.PENDING:
      return (config?.pendingTimeoutHours ?? 2) * 60 * 60 * 1000;
    case OrderStatus.CONFIRMED:
    case OrderStatus.PAYMENT_PENDING:
      return (config?.confirmedTimeoutHours ?? 2) * 60 * 60 * 1000;
    default:
      return 0;
  }
}

function buildTimeoutConfig(): Record<string, number> {
  const config = store.timeoutConfig;
  if (!config) return {};
  return {
    [OrderStatus.PENDING]: config.pendingTimeoutHours * 60 * 60 * 1000,
    [OrderStatus.CONFIRMED]: config.confirmedTimeoutHours * 60 * 60 * 1000,
    [OrderStatus.PAYMENT_PENDING]: config.paymentPendingTimeoutHours * 60 * 60 * 1000,
  };
}

function updateStatus(): void {
  const timeoutConfig = buildTimeoutConfig();

  remainingTime.value = calculateRemainingTime(
    props.orderStatus,
    props.createTime,
    props.confirmTime,
    props.updateTime,
    timeoutConfig
  );

  isTimedOut.value = isOrderTimedOut(
    props.orderStatus,
    props.createTime,
    props.confirmTime,
    props.updateTime,
    timeoutConfig
  );

  urgency.value = getOrderUrgency(
    props.orderStatus,
    props.createTime,
    props.confirmTime,
    props.updateTime,
    timeoutConfig
  );
}

function startUpdateTimer(): void {
  // 根据紧急程度决定更新频率
  let interval: number;
  switch (urgency.value) {
    case "critical":
      interval = 1000; // 1秒
      break;
    case "high":
      interval = 5000; // 5秒
      break;
    case "medium":
      interval = 30000; // 30秒
      break;
    default:
      interval = 60000; // 1分钟
  }

  updateInterval = setInterval(updateStatus, interval);
}

function stopUpdateTimer(): void {
  if (updateInterval) {
    clearInterval(updateInterval);
    updateInterval = null;
  }
}

// 生命周期
onMounted(() => {
  // 初始化状态
  updateStatus();

  // 设置超时处理器
  setupTimeoutHandler(
    props.orderId,
    props.orderStatus,
    props.createTime,
    props.confirmTime,
    props.updateTime,
    () => {
      emit("timeout");
      updateStatus();
    },
    (warningTime) => {
      emit("warning", warningTime);
      updateStatus();
    }
  );

  // 开始定时更新
  startUpdateTimer();
});

onUnmounted(() => {
  // 清理定时器
  stopUpdateTimer();
  clearOrderTimers(props.orderId);
});

// 监听紧急程度变化，调整更新频率
watchEffect(() => {
  if (updateInterval) {
    stopUpdateTimer();
    startUpdateTimer();
  }
});
</script>

<style scoped>
.countdown-container {
  margin-top: 15px;
  padding: 20px;
  border-radius: 8px;
  background-color: #f8f9fa;
  text-align: center;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
}

.urgency-low {
  background-color: #f0f9ff;
  border: 1px solid #67c23a;
}

.urgency-medium {
  background-color: #ecf5ff;
  border: 1px solid #409eff;
}

.urgency-high {
  background-color: #fdf6ec;
  border: 1px solid #e6a23c;
  animation: pulse-warning 2s infinite;
}

.urgency-critical {
  background-color: #fef0f0;
  border: 1px solid #f56c6c;
  animation: pulse-critical 1s infinite;
}

@keyframes pulse-warning {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(230, 162, 60, 0.4);
  }

  50% {
    box-shadow: 0 0 10px 5px rgba(230, 162, 60, 0.2);
  }
}

@keyframes pulse-critical {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(245, 108, 108, 0.4);
  }

  50% {
    box-shadow: 0 0 15px 8px rgba(245, 108, 108, 0.3);
  }
}

.countdown-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #333;
}

.countdown-timer {
  font-size: 28px;
  font-weight: bold;
  color: #409eff;
  margin-bottom: 12px;
  transition: color 0.3s ease;
}

.countdown-timer.urgent {
  color: #f56c6c;
  animation: blink 1s infinite;
}

.countdown-desc {
  font-size: 14px;
  color: #606266;
  margin-bottom: 12px;
}

.progress-bar {
  width: 100%;
  height: 6px;
  background-color: #e4e7ed;
  border-radius: 3px;
  margin-top: 12px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  transition:
    width 0.3s ease,
    background-color 0.3s ease;
  border-radius: 3px;
}

.warning-message {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 12px;
  margin-top: 12px;
  background-color: rgba(230, 162, 60, 0.1);
  border: 1px solid #e6a23c;
  border-radius: 4px;
  font-size: 13px;
  color: #e6a23c;
  font-weight: 500;
}

.urgency-critical .warning-message {
  background-color: rgba(245, 108, 108, 0.1);
  border-color: #f56c6c;
  color: #f56c6c;
}

@keyframes blink {
  0% {
    opacity: 1;
  }

  50% {
    opacity: 0.5;
  }

  100% {
    opacity: 1;
  }
}
</style>
