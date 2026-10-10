import { cancelOrder } from "@/api/order";
import { OrderStatus } from "@/types/order";
import { ElMessage, ElNotification } from "element-plus";

/**
 * 订单超时处理工具 - 优化版
 * 用于处理订单在各个状态的超时情况，增加服务器时间同步和实时通知
 *
 * 注意：超时配置已迁移到 useOrderStore，本工具接收外部传入的配置，
 * 不再维护独立的硬编码超时时间。
 */

// 默认超时配置（单位：毫秒）——作为 fallback，当外部未传入配置时使用
const DEFAULT_TIMEOUT_MS: Record<string, number> = {
  [OrderStatus.PENDING]: 2 * 60 * 60 * 1000,
  [OrderStatus.CONFIRMED]: 2 * 60 * 60 * 1000,
  [OrderStatus.PAYMENT_PENDING]: 2 * 60 * 60 * 1000,
  [OrderStatus.PAID]: Infinity,
  [OrderStatus.CHECKED_IN]: Infinity,
  [OrderStatus.COMPLETED]: Infinity,
  [OrderStatus.CANCELLED_BY_USER]: Infinity,
  [OrderStatus.CANCELLED_BY_HOST]: Infinity,
  [OrderStatus.CANCELLED]: Infinity,
  [OrderStatus.CANCELLED_SYSTEM]: Infinity,
  [OrderStatus.PAYMENT_FAILED]: Infinity,
  [OrderStatus.REFUND_PENDING]: Infinity,
  [OrderStatus.REFUNDED]: Infinity,
  [OrderStatus.REFUND_FAILED]: Infinity,
  [OrderStatus.READY_FOR_CHECKIN]: Infinity,
};

// 预警配置（单位：毫秒）——默认配置
const DEFAULT_WARNING_MS: Record<string, number[]> = {
  [OrderStatus.PENDING]: [60 * 60 * 1000, 30 * 60 * 1000],
  [OrderStatus.CONFIRMED]: [30 * 60 * 1000, 10 * 60 * 1000, 5 * 60 * 1000],
  [OrderStatus.PAYMENT_PENDING]: [30 * 60 * 1000, 10 * 60 * 1000, 5 * 60 * 1000],
};

// 全局状态管理
interface ActiveTimer {
  timeoutId: ReturnType<typeof setTimeout>;
  warningIds: ReturnType<typeof setTimeout>[];
  orderId: number;
  orderStatus: OrderStatus;
}

const activeTimers = new Map<number, ActiveTimer>();

/**
 * 获取服务器时间（用于同步时间差）
 */
let serverTimeOffset = 0;

export async function syncServerTime(): Promise<void> {
  try {
    const response = await fetch("/api/system/time");
    const serverTime = await response.json();
    const clientTime = Date.now();
    serverTimeOffset = serverTime.timestamp - clientTime;
    console.log("服务器时间同步成功，偏移量：", serverTimeOffset, "ms");
  } catch (error) {
    console.warn("服务器时间同步失败，使用本地时间：", error);
    serverTimeOffset = 0;
  }
}

/**
 * 获取同步后的当前时间
 */
function getCurrentTime(): number {
  return Date.now() + serverTimeOffset;
}

/**
 * 计算订单超时剩余时间（改进版）
 * @param orderStatus 订单状态
 * @param createTime 订单创建时间
 * @param confirmTime 订单确认时间
 * @param updateTime 订单更新时间
 * @param customTimeoutConfig 自定义超时配置（单位：毫秒），不传则使用默认配置
 */
export function calculateRemainingTime(
  orderStatus: OrderStatus,
  createTime: string,
  confirmTime?: string,
  updateTime?: string,
  customTimeoutConfig?: Record<string, number>
): number {
  const timeoutConfig = customTimeoutConfig || DEFAULT_TIMEOUT_MS;
  const timeout = timeoutConfig[orderStatus] ?? Infinity;

  if (timeout === Infinity) {
    return Infinity;
  }

  let startTime: number;

  switch (orderStatus) {
    case OrderStatus.PENDING:
      startTime = new Date(createTime).getTime();
      break;

    case OrderStatus.CONFIRMED:
    case OrderStatus.PAYMENT_PENDING:
      startTime = confirmTime
        ? new Date(confirmTime).getTime()
        : updateTime
          ? new Date(updateTime).getTime()
          : new Date(createTime).getTime();
      break;

    default:
      return Infinity;
  }

  const endTime = startTime + timeout;
  const now = getCurrentTime();
  const remaining = endTime - now;

  return Math.max(0, remaining);
}

/**
 * 检查订单是否已超时
 */
export function isOrderTimedOut(
  orderStatus: OrderStatus,
  createTime: string,
  confirmTime?: string,
  updateTime?: string,
  customTimeoutConfig?: Record<string, number>
): boolean {
  return (
    calculateRemainingTime(
      orderStatus,
      createTime,
      confirmTime,
      updateTime,
      customTimeoutConfig
    ) === 0
  );
}

/**
 * 获取超时倒计时文本（改进版）
 * @param remainingTime 剩余时间（毫秒）
 * @param showSeconds 是否显示秒数
 */
export function getTimeoutCountdownText(remainingTime: number, showSeconds = false): string {
  if (remainingTime === Infinity) {
    return "无时间限制";
  }

  if (remainingTime <= 0) {
    return "已超时";
  }

  const days = Math.floor(remainingTime / (24 * 60 * 60 * 1000));
  const hours = Math.floor((remainingTime % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000));
  const minutes = Math.floor((remainingTime % (60 * 60 * 1000)) / (60 * 1000));
  const seconds = Math.floor((remainingTime % (60 * 1000)) / 1000);

  if (days > 0) {
    return `${days}天${hours}小时`;
  } else if (hours > 0) {
    return showSeconds ? `${hours}小时${minutes}分钟${seconds}秒` : `${hours}小时${minutes}分钟`;
  } else if (minutes > 0) {
    return showSeconds ? `${minutes}分钟${seconds}秒` : `${minutes}分钟`;
  } else {
    return `${seconds}秒`;
  }
}

/**
 * 获取超时状态颜色
 */
export function getTimeoutStatusColor(remainingTime: number): string {
  if (remainingTime === Infinity) return "#909399";
  if (remainingTime <= 0) return "#F56C6C";
  if (remainingTime < 10 * 60 * 1000) return "#F56C6C";
  if (remainingTime < 30 * 60 * 1000) return "#E6A23C";
  if (remainingTime < 60 * 60 * 1000) return "#409EFF";
  return "#67C23A";
}

/**
 * 发送超时预警通知
 */
function sendTimeoutWarning(
  orderId: number,
  orderStatus: OrderStatus,
  remainingTime: number
): void {
  const minutes = Math.floor(remainingTime / (60 * 1000));

  let message = "";
  let type: "warning" | "error" = "warning";

  switch (orderStatus) {
    case OrderStatus.PENDING:
      message = `订单 #${orderId} 将在 ${minutes} 分钟后因未确认而自动取消`;
      break;
    case OrderStatus.CONFIRMED:
    case OrderStatus.PAYMENT_PENDING:
      message = `订单 #${orderId} 将在 ${minutes} 分钟后因未支付而自动取消`;
      if (minutes <= 5) type = "error";
      break;
  }

  ElNotification({
    title: "订单超时预警",
    message,
    type,
    duration: 0,
    showClose: true,
    onClick: () => {
      window.location.href = `/orders/${orderId}`;
    },
  });
}

/**
 * 处理订单超时
 */
export async function handleOrderTimeout(
  orderId: number,
  orderStatus: OrderStatus,
  createTime: string,
  confirmTime?: string,
  updateTime?: string,
  customTimeoutConfig?: Record<string, number>
): Promise<boolean> {
  if (!isOrderTimedOut(orderStatus, createTime, confirmTime, updateTime, customTimeoutConfig)) {
    return false;
  }

  try {
    switch (orderStatus) {
      case OrderStatus.PENDING:
      case OrderStatus.CONFIRMED:
      case OrderStatus.PAYMENT_PENDING:
        await cancelOrder(orderId, "TIMEOUT");

        ElNotification({
          title: "订单已自动取消",
          message: `订单 #${orderId} 因超时未处理已被系统自动取消`,
          type: "warning",
          duration: 5000,
        });

        return true;

      default:
        return false;
    }
  } catch (error) {
    console.error("处理订单超时失败:", error);
    ElMessage.error("处理订单超时失败，请刷新页面重试");
    return false;
  }
}

/**
 * 清理指定订单的定时器
 */
export function clearOrderTimers(orderId: number): void {
  const activeTimer = activeTimers.get(orderId);
  if (activeTimer) {
    clearTimeout(activeTimer.timeoutId);
    activeTimer.warningIds.forEach((id) => clearTimeout(id));
    activeTimers.delete(orderId);
  }
}

/**
 * 清理所有定时器
 */
export function clearAllTimers(): void {
  activeTimers.forEach((_timer, orderId) => {
    clearOrderTimers(orderId);
  });
  activeTimers.clear();
}

/**
 * 设置订单超时自动处理（改进版）
 * @param orderId 订单ID
 * @param orderStatus 订单状态
 * @param createTime 订单创建时间
 * @param confirmTime 订单确认时间
 * @param updateTime 订单更新时间
 * @param onTimeout 超时回调函数
 * @param onWarning 预警回调函数
 * @param customTimeoutConfig 自定义超时配置（单位：毫秒）
 * @param customWarningConfig 自定义预警配置（单位：毫秒）
 */
export function setupTimeoutHandler(
  orderId: number,
  orderStatus: OrderStatus,
  createTime: string,
  confirmTime?: string,
  updateTime?: string,
  onTimeout?: () => void,
  onWarning?: (remainingTime: number) => void,
  customTimeoutConfig?: Record<string, number>,
  customWarningConfig?: Record<string, number[]>
): boolean {
  clearOrderTimers(orderId);

  const remainingTime = calculateRemainingTime(
    orderStatus,
    createTime,
    confirmTime,
    updateTime,
    customTimeoutConfig
  );

  if (remainingTime <= 0 || remainingTime === Infinity) {
    return false;
  }

  const warningIds: ReturnType<typeof setTimeout>[] = [];
  const warnings = customWarningConfig || DEFAULT_WARNING_MS;
  const warningTimes = warnings[orderStatus] || [];

  warningTimes.forEach((warningTime) => {
    if (remainingTime > warningTime) {
      const warningDelay = remainingTime - warningTime;
      const warningId = setTimeout(() => {
        sendTimeoutWarning(orderId, orderStatus, warningTime);
        if (onWarning) {
          onWarning(warningTime);
        }
      }, warningDelay);
      warningIds.push(warningId);
    }
  });

  const timeoutId = setTimeout(async () => {
    const handled = await handleOrderTimeout(
      orderId,
      orderStatus,
      createTime,
      confirmTime,
      updateTime,
      customTimeoutConfig
    );

    if (handled && onTimeout) {
      onTimeout();
    }

    clearOrderTimers(orderId);
  }, remainingTime);

  activeTimers.set(orderId, {
    timeoutId,
    warningIds,
    orderId,
    orderStatus,
  });

  return true;
}

/**
 * 获取订单的紧急程度
 */
export function getOrderUrgency(
  orderStatus: OrderStatus,
  createTime: string,
  confirmTime?: string,
  updateTime?: string,
  customTimeoutConfig?: Record<string, number>
): "low" | "medium" | "high" | "critical" {
  const remainingTime = calculateRemainingTime(
    orderStatus,
    createTime,
    confirmTime,
    updateTime,
    customTimeoutConfig
  );

  if (remainingTime === Infinity) return "low";
  if (remainingTime <= 0) return "critical";
  if (remainingTime < 10 * 60 * 1000) return "critical";
  if (remainingTime < 30 * 60 * 1000) return "high";
  if (remainingTime < 60 * 60 * 1000) return "medium";
  return "low";
}

// 初始化时同步服务器时间
syncServerTime();
