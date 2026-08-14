/**
 * 订单详情页（C 端）状态展示纯函数
 * 注意：与 host 端 utils/orderDisplay.ts 语义不同（含退款/争议状态），勿混用
 */

/** 获取退款类型文本（带括号，C 端展示用） */
export function getRefundTypeText(refundType?: string): string {
  if (!refundType) return "";

  switch (refundType) {
    case "USER_REQUESTED":
      return "（用户申请）";
    case "HOST_CANCELLED":
      return "（房东取消）";
    case "ADMIN_INITIATED":
      return "（管理员发起）";
    case "SYSTEM_AUTOMATIC":
      return "（系统自动）";
    default:
      return "";
  }
}

/** 获取订单状态的步骤 */
export function getStatusStep(status: string): number {
  const statusSteps: Record<string, number> = {
    PENDING: 1, // 预订申请(步骤1)
    CONFIRMED: 2, // 房东确认(步骤2)
    PAYMENT_PENDING: 2, // 待支付(在确认后)
    REJECTED: 1, // 被拒绝(保持在步骤1)
    CANCELLED: 1, // 已取消(保持在步骤1)
    CANCELLED_SYSTEM: 1, // 系统取消(保持在步骤1)
    PAID: 3, // 已支付(步骤3)
    CHECKED_IN: 4, // 已入住(步骤4)
    COMPLETED: 5, // 已完成(步骤5)
  };
  return statusSteps[status] || 0;
}

/** 获取状态显示的类型 */
export function getStatusType(
  status: string,
  paymentStatus?: string,
  refundRejectionReason?: string
): string {
  if (paymentStatus === "PAID" && refundRejectionReason) {
    return "danger";
  }

  const statusTypes: Record<string, string> = {
    PENDING: "warning",
    CONFIRMED: "success",
    PAYMENT_PENDING: "warning",
    REJECTED: "danger",
    CANCELLED: "info",
    CANCELLED_SYSTEM: "info",
    CANCELLED_BY_USER: "info",
    CANCELLED_BY_HOST: "info",
    PAID: "success",
    CHECKED_IN: "success",
    COMPLETED: "success",
  };
  return statusTypes[status] || "info";
}

/** 获取状态显示文本 */
export function getStatusText(
  status: string,
  paymentStatus?: string,
  refundType?: string,
  refundRejectionReason?: string,
  disputeResolution?: string
): string {
  // 优先处理退款相关状态
  if (paymentStatus === "PAID" && refundRejectionReason) {
    return "退款被拒绝";
  }

  // 争议状态
  if (status === "DISPUTE_PENDING") return "争议待处理";
  if (status === "DISPUTED") return "争议处理中";

  // 争议解决结果
  if (disputeResolution === "APPROVED") return "争议已解决（退款）";
  if (disputeResolution === "REJECTED") return "争议已解决（拒绝退款）";

  if (paymentStatus === "REFUND_PENDING") {
    return `退款中${getRefundTypeText(refundType)}`;
  }
  if (paymentStatus === "REFUNDED") {
    return `已退款${getRefundTypeText(refundType)}`;
  }
  if (paymentStatus === "REFUND_FAILED") {
    return `退款失败${getRefundTypeText(refundType)}`;
  }

  const statusTexts: Record<string, string> = {
    PENDING: "待确认",
    CONFIRMED: "已确认",
    PAYMENT_PENDING: "待支付",
    REJECTED: "已拒绝",
    CANCELLED: "已取消",
    CANCELLED_SYSTEM: "系统已取消",
    CANCELLED_BY_USER: "已取消",
    CANCELLED_BY_HOST: "已取消",
    PAID: "已支付",
    CHECKED_IN: "已入住",
    COMPLETED: "已完成",
    PAYMENT_FAILED: "支付失败",
    REFUND_PENDING: "退款中",
    REFUNDED: "已退款",
    REFUND_FAILED: "退款失败",
    DISPUTE_PENDING: "争议待处理",
    DISPUTED: "争议处理中",
  };

  return statusTexts[status] || status;
}

/** 格式化日期范围 */
export function formatDateRange(checkIn: string, checkOut: string): string {
  if (!checkIn || !checkOut) return "";

  const checkInDate = new Date(checkIn);
  const checkOutDate = new Date(checkOut);

  return `${checkInDate.getMonth() + 1}月${checkInDate.getDate()}日 - ${
    checkOutDate.getMonth() + 1
  }月${checkOutDate.getDate()}日`;
}

/** 提取拒绝原因 */
export function extractRejectReason(remark: string): string {
  if (!remark) return "";

  const reasonMatch = remark.match(/拒绝原因: (.+)/);
  return reasonMatch ? reasonMatch[1] : remark;
}
