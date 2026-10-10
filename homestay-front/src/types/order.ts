/**
 * 订单状态枚举
 * 统一前后端的订单状态定义
 */
export enum OrderStatus {
  // 基础状态
  PENDING = "PENDING", // 待确认
  CONFIRMED = "CONFIRMED", // 已确认
  PAID = "PAID", // 已支付
  CHECKED_IN = "CHECKED_IN", // 已入住
  COMPLETED = "COMPLETED", // 已完成
  REJECTED = "REJECTED", // 已拒绝

  // 取消状态
  CANCELLED = "CANCELLED", // 已取消（通用）
  CANCELLED_BY_USER = "CANCELLED_BY_USER", // 用户取消
  CANCELLED_BY_HOST = "CANCELLED_BY_HOST", // 房东取消
  CANCELLED_SYSTEM = "CANCELLED_SYSTEM", // 系统取消

  // 支付相关状态
  PAYMENT_PENDING = "PAYMENT_PENDING", // 支付中
  PAYMENT_FAILED = "PAYMENT_FAILED", // 支付失败

  // 退款相关状态
  REFUND_PENDING = "REFUND_PENDING", // 退款中
  REFUNDED = "REFUNDED", // 已退款
  REFUND_FAILED = "REFUND_FAILED", // 退款失败

  // 入住相关状态
  READY_FOR_CHECKIN = "READY_FOR_CHECKIN", // 待入住
  CHECKED_OUT = "CHECKED_OUT", // 已退房

  // 争议状态
  DISPUTE_PENDING = "DISPUTE_PENDING", // 争议待处理
}

/**
 * 订单状态流转规则
 * 定义了每个状态可以转换到哪些状态
 */
export const statusTransitions: Record<OrderStatus, OrderStatus[]> = {
  [OrderStatus.PENDING]: [
    OrderStatus.CONFIRMED,
    OrderStatus.CANCELLED,
    OrderStatus.CANCELLED_BY_USER,
    OrderStatus.CANCELLED_BY_HOST,
    OrderStatus.CANCELLED_SYSTEM,
  ],
  [OrderStatus.CONFIRMED]: [
    OrderStatus.PAYMENT_PENDING,
    OrderStatus.CANCELLED,
    OrderStatus.CANCELLED_BY_USER,
    OrderStatus.CANCELLED_BY_HOST,
  ],
  [OrderStatus.PAYMENT_PENDING]: [
    OrderStatus.PAID,
    OrderStatus.PAYMENT_FAILED,
    OrderStatus.CANCELLED_BY_USER,
  ],
  [OrderStatus.PAYMENT_FAILED]: [
    OrderStatus.PAYMENT_PENDING,
    OrderStatus.CANCELLED,
    OrderStatus.CANCELLED_BY_USER,
  ],
  [OrderStatus.PAID]: [
    OrderStatus.READY_FOR_CHECKIN,
    OrderStatus.REFUND_PENDING,
    OrderStatus.CANCELLED_BY_HOST,
  ],
  [OrderStatus.READY_FOR_CHECKIN]: [OrderStatus.CHECKED_IN, OrderStatus.REFUND_PENDING],
  [OrderStatus.CHECKED_IN]: [OrderStatus.COMPLETED, OrderStatus.CHECKED_OUT],
  [OrderStatus.CHECKED_OUT]: [OrderStatus.COMPLETED],
  [OrderStatus.COMPLETED]: [],
  [OrderStatus.CANCELLED]: [],
  [OrderStatus.CANCELLED_BY_USER]: [],
  [OrderStatus.CANCELLED_BY_HOST]: [OrderStatus.REFUND_PENDING],
  [OrderStatus.CANCELLED_SYSTEM]: [],
  [OrderStatus.REFUND_PENDING]: [
    OrderStatus.REFUNDED,
    OrderStatus.REFUND_FAILED,
    OrderStatus.DISPUTE_PENDING,
  ],
  [OrderStatus.DISPUTE_PENDING]: [OrderStatus.REFUNDED, OrderStatus.PAID],
  [OrderStatus.REFUNDED]: [],
  [OrderStatus.REFUND_FAILED]: [OrderStatus.REFUND_PENDING],
  [OrderStatus.REJECTED]: [],
};

/**
 * 判断状态转换是否有效
 * @param currentStatus 当前状态
 * @param targetStatus 目标状态
 */
export function isValidStatusTransition(
  currentStatus: OrderStatus,
  targetStatus: OrderStatus
): boolean {
  return statusTransitions[currentStatus]?.includes(targetStatus) || false;
}

/**
 * 根据状态判断订单是否已取消
 * @param status 订单状态
 */
export function isOrderCancelled(status: string): boolean {
  return [
    OrderStatus.CANCELLED,
    OrderStatus.CANCELLED_BY_USER,
    OrderStatus.CANCELLED_BY_HOST,
    OrderStatus.CANCELLED_SYSTEM,
  ].includes(status as OrderStatus);
}

/**
 * 订单状态对应的展示文本
 */
export const orderStatusText: Record<string, string> = {
  [OrderStatus.PENDING]: "待确认",
  [OrderStatus.CONFIRMED]: "已确认",
  [OrderStatus.PAYMENT_PENDING]: "支付中",
  [OrderStatus.PAYMENT_FAILED]: "支付失败",
  [OrderStatus.PAID]: "已支付",
  [OrderStatus.READY_FOR_CHECKIN]: "待入住",
  [OrderStatus.CHECKED_IN]: "已入住",
  [OrderStatus.COMPLETED]: "已完成",
  [OrderStatus.CANCELLED]: "已取消",
  [OrderStatus.CANCELLED_BY_USER]: "用户已取消",
  [OrderStatus.CANCELLED_BY_HOST]: "房东已取消",
  [OrderStatus.CANCELLED_SYSTEM]: "系统已取消",
  [OrderStatus.REJECTED]: "已拒绝",
  [OrderStatus.REFUND_PENDING]: "退款中",
  [OrderStatus.REFUNDED]: "已退款",
  [OrderStatus.REFUND_FAILED]: "退款失败",
  [OrderStatus.CHECKED_OUT]: "已退房",
  [OrderStatus.DISPUTE_PENDING]: "争议待处理",
};

/**
 * 订单状态对应的标签类型
 */
export const orderStatusType: Record<string, string> = {
  [OrderStatus.PENDING]: "info",
  [OrderStatus.CONFIRMED]: "primary",
  [OrderStatus.PAYMENT_PENDING]: "warning",
  [OrderStatus.PAYMENT_FAILED]: "danger",
  [OrderStatus.PAID]: "success",
  [OrderStatus.READY_FOR_CHECKIN]: "primary",
  [OrderStatus.CHECKED_IN]: "success",
  [OrderStatus.COMPLETED]: "success",
  [OrderStatus.CANCELLED]: "danger",
  [OrderStatus.CANCELLED_BY_USER]: "danger",
  [OrderStatus.CANCELLED_BY_HOST]: "danger",
  [OrderStatus.CANCELLED_SYSTEM]: "danger",
  [OrderStatus.REJECTED]: "danger",
  [OrderStatus.REFUND_PENDING]: "warning",
  [OrderStatus.REFUNDED]: "info",
  [OrderStatus.REFUND_FAILED]: "danger",
  [OrderStatus.CHECKED_OUT]: "info",
  [OrderStatus.DISPUTE_PENDING]: "warning",
};

// --- 新增：用于状态显示的订单接口定义 ---
import type { PaymentStatus } from "./index"; // 假设 PaymentStatus 在 index.ts

export interface DisplayOrder {
  status: OrderStatus | string; // 允许 string 作为 fallback
  paymentStatus: PaymentStatus | string | null;
}

// --- 新增：统一获取订单显示状态文本的函数 ---
export function getDisplayOrderStatusText(order: DisplayOrder): string {
  const status = order.status as OrderStatus;
  const paymentStatus = order.paymentStatus as PaymentStatus;

  // 优先处理最终/关键状态
  if (isOrderCancelled(status)) return orderStatusText[status] || "已取消";
  if (status === OrderStatus.REJECTED) return orderStatusText[OrderStatus.REJECTED];
  if (paymentStatus === "REFUNDED") return orderStatusText[OrderStatus.REFUNDED];
  if (status === OrderStatus.COMPLETED) return orderStatusText[OrderStatus.COMPLETED];
  if (status === OrderStatus.PAYMENT_FAILED) return orderStatusText[OrderStatus.PAYMENT_FAILED];
  if (status === OrderStatus.DISPUTE_PENDING) return orderStatusText[OrderStatus.DISPUTE_PENDING];

  // 处理支付成功相关状态
  if (paymentStatus === "PAID") {
    if (status === OrderStatus.CHECKED_IN) return orderStatusText[OrderStatus.CHECKED_IN];
    // if (status === OrderStatus.READY_FOR_CHECKIN) return orderStatusText[OrderStatus.READY_FOR_CHECKIN]; // 如果需要区分
    return orderStatusText[OrderStatus.PAID]; // PAID 状态优先显示已支付
  }

  // 处理待支付/支付中
  if (status === OrderStatus.CONFIRMED && paymentStatus === "UNPAID") return "待支付";
  if (status === OrderStatus.PAYMENT_PENDING)
    return orderStatusText[OrderStatus.PAYMENT_PENDING] || "待支付";

  // 处理待确认
  if (status === OrderStatus.PENDING) return orderStatusText[OrderStatus.PENDING];

  // Fallback
  console.warn(`未处理的订单状态组合: status=${status}, paymentStatus=${paymentStatus}`);
  return orderStatusText[status] || status; // 尝试用映射，不行直接返回原始 status
}

// --- 新增：统一获取订单显示状态类型的函数 ---
export function getDisplayOrderStatusType(order: DisplayOrder): string {
  const status = order.status as OrderStatus;
  const paymentStatus = order.paymentStatus as PaymentStatus;

  // 优先处理最终/关键状态
  if (isOrderCancelled(status)) return orderStatusType[status] || "danger";
  if (status === OrderStatus.REJECTED) return orderStatusType[OrderStatus.REJECTED];
  if (paymentStatus === "REFUNDED") return orderStatusType[OrderStatus.REFUNDED];
  if (status === OrderStatus.COMPLETED) return orderStatusType[OrderStatus.COMPLETED];
  if (status === OrderStatus.PAYMENT_FAILED) return orderStatusType[OrderStatus.PAYMENT_FAILED];
  if (status === OrderStatus.DISPUTE_PENDING) return orderStatusType[OrderStatus.DISPUTE_PENDING];

  // 处理支付成功相关状态
  if (paymentStatus === "PAID") {
    if (status === OrderStatus.CHECKED_IN) return orderStatusType[OrderStatus.CHECKED_IN];
    // if (status === OrderStatus.READY_FOR_CHECKIN) return orderStatusType[OrderStatus.READY_FOR_CHECKIN];
    return orderStatusType[OrderStatus.PAID]; // PAID 状态用 success
  }

  // 处理待支付/支付中
  if (status === OrderStatus.CONFIRMED && paymentStatus === "UNPAID") return "warning";
  if (status === OrderStatus.PAYMENT_PENDING)
    return orderStatusType[OrderStatus.PAYMENT_PENDING] || "warning";

  // 处理待确认
  if (status === OrderStatus.PENDING) return orderStatusType[OrderStatus.PENDING];

  // Fallback
  return orderStatusType[status] || "info"; // 默认为 info
}
