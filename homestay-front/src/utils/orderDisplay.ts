import type { HostOrderItem } from "@/types/hostOrder";

/** 订单状态 → 中文文案 */
export function getStatusText(order: HostOrderItem | string): string {
  let status: string;
  let refundType: string | undefined;

  if (typeof order === "string") {
    status = order;
    refundType = undefined;
  } else {
    status = order.status;
    refundType = order.refundType;
  }

  const refundTypeMap: Record<string, string> = {
    USER_REQUESTED: "用户申请",
    HOST_CANCELLED: "房东取消",
    ADMIN_INITIATED: "管理员发起",
    SYSTEM_AUTOMATIC: "系统自动",
  };

  if (status === "REFUNDED") {
    return refundType ? `已退款（${refundTypeMap[refundType] || refundType}）` : "已退款";
  }
  if (status === "REFUND_PENDING") {
    return refundType ? `退款中（${refundTypeMap[refundType] || refundType}）` : "退款中";
  }

  const statusMap: Record<string, string> = {
    PENDING: "待确认",
    CONFIRMED: "已确认",
    PAYMENT_PENDING: "待支付",
    PAID: "已支付",
    READY_FOR_CHECKIN: "待入住",
    CHECKED_IN: "已入住",
    CHECKED_OUT: "已退房",
    COMPLETED: "已完成",
    CANCELLED: "已取消",
    CANCELLED_SYSTEM: "系统取消",
    CANCELLED_BY_USER: "用户取消",
    CANCELLED_BY_HOST: "房东取消",
    REJECTED: "已拒绝",
    REFUND_FAILED: "退款失败",
    DISPUTE_PENDING: "争议待处理",
    DISPUTED: "争议中",
  };
  return statusMap[status] || status;
}

/** 订单状态 → Element Plus tag 类型 */
export function getStatusType(status: string): string {
  const statusMap: Record<string, string> = {
    PENDING: "warning",
    CONFIRMED: "primary",
    PAYMENT_PENDING: "warning",
    PAID: "success",
    READY_FOR_CHECKIN: "primary",
    CHECKED_IN: "primary",
    CHECKED_OUT: "warning",
    COMPLETED: "info",
    CANCELLED: "danger",
    CANCELLED_SYSTEM: "danger",
    CANCELLED_BY_USER: "danger",
    CANCELLED_BY_HOST: "danger",
    REJECTED: "danger",
    REFUND_PENDING: "warning",
    REFUNDED: "info",
    REFUND_FAILED: "danger",
    DISPUTE_PENDING: "warning",
    DISPUTED: "warning",
  };
  return statusMap[status] || "";
}

/** 是否可发起退款（已支付/已入住/已确认 且支付状态为已支付） */
export function canInitiateRefund(order: HostOrderItem): boolean {
  const refundableStatus = ["PAID", "CHECKED_IN", "CONFIRMED"];
  const isPaid = order.paymentStatus === "PAID";
  const isRefundableStatus = refundableStatus.includes(order.status);
  return isRefundableStatus && isPaid;
}

/** 支付状态 → 中文文案 */
export function getPaymentStatusText(paymentStatus: string | null | undefined): string {
  const map: Record<string, string> = {
    UNPAID: "未支付",
    PAID: "已支付",
    REFUND_PENDING: "退款处理中",
    REFUNDED: "已退款",
    REFUND_FAILED: "退款失败",
  };
  return map[paymentStatus || ""] || paymentStatus || "未知";
}

/** 支付状态 → Element Plus tag 类型 */
export function getPaymentStatusType(paymentStatus: string | null | undefined): string {
  const map: Record<string, string> = {
    UNPAID: "info",
    PAID: "success",
    REFUND_PENDING: "warning",
    REFUNDED: "info",
    REFUND_FAILED: "danger",
  };
  return map[paymentStatus || ""] || "";
}

/** 退款类型 → 中文文案 */
export function getRefundTypeText(refundType: string | undefined): string {
  const map: Record<string, string> = {
    USER_REQUESTED: "用户申请",
    HOST_CANCELLED: "房东取消",
    ADMIN_INITIATED: "管理员发起",
    SYSTEM_AUTOMATIC: "系统自动",
  };
  return map[refundType || ""] || refundType || "未知";
}

/** 判断订单是否含退款信息 */
export function hasRefundInfo(order: HostOrderItem | null): boolean {
  if (!order) return false;
  return (
    !!order.refundType ||
    !!order.refundReason ||
    !!order.refundAmount ||
    !!order.refundInitiatedByName ||
    !!order.refundProcessedByName ||
    ["REFUND_PENDING", "REFUNDED", "REFUND_FAILED"].includes(order.paymentStatus || "")
  );
}

/** 安全金额格式化（两位小数） */
export function formatAmount(value: unknown): string {
  const num = Number(value);
  if (isNaN(num)) return "0.00";
  return num.toFixed(2);
}

/** 押金状态 → 中文文案 */
export function getDepositStatusText(status: string): string {
  const statusMap: Record<string, string> = {
    PENDING: "待处理",
    PAID: "已收取",
    REFUNDED: "已退还",
    RETAINED: "已扣押",
    WAIVED: "已免除",
    WAITED: "待处理",
  };
  return statusMap[status] || status;
}

/** 押金状态 → Element Plus tag 类型 */
export function getDepositStatusType(status: string): string {
  const typeMap: Record<string, string> = {
    PENDING: "warning",
    PAID: "success",
    REFUNDED: "info",
    RETAINED: "danger",
    WAIVED: "info",
    WAITED: "warning",
  };
  return typeMap[status] || "info";
}

/** 格式化为 MM-DD */
export function formatDateString(dateString: string): string {
  if (!dateString) return "";
  const date = new Date(dateString);
  if (isNaN(date.getTime())) return "";
  const month = date.getMonth() + 1;
  const day = date.getDate();
  return `${month.toString().padStart(2, "0")}-${day.toString().padStart(2, "0")}`;
}

/** 格式化为 YYYY-MM-DD HH:mm */
export function formatDateTime(dateTimeString: string): string {
  if (!dateTimeString) return "";
  const date = new Date(dateTimeString);
  if (isNaN(date.getTime())) return "";
  const year = date.getFullYear();
  const month = date.getMonth() + 1;
  const day = date.getDate();
  const hours = date.getHours();
  const minutes = date.getMinutes();
  return `${year}-${month.toString().padStart(2, "0")}-${day
    .toString()
    .padStart(2, "0")} ${hours.toString().padStart(2, "0")}:${minutes
    .toString()
    .padStart(2, "0")}`;
}
