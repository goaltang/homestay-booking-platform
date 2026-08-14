/**
 * 房源状态与审核记录展示纯函数（HomestayManage / AuditHistoryDialog 共用）
 */
import type { HomestayStatus } from "@/types";

/** 房源状态对应的 tag 类型 */
export function getStatusType(status: HomestayStatus): string {
  const types: Record<HomestayStatus, string> = {
    DRAFT: "info",
    PENDING: "warning",
    ACTIVE: "success",
    INACTIVE: "info",
    REJECTED: "danger",
    SUSPENDED: "danger",
  };
  return types[status] || "info";
}

/** 房源状态对应的文本 */
export function getStatusText(status: HomestayStatus): string {
  const texts: Record<HomestayStatus, string> = {
    DRAFT: "草稿",
    PENDING: "待审核",
    ACTIVE: "已上线",
    INACTIVE: "已下架",
    REJECTED: "已拒绝",
    SUSPENDED: "已暂停",
  };
  return texts[status] || "未知状态";
}

/** 审核记录操作类型 → 时间线类型 */
export function getTimelineType(
  actionType: string
): "primary" | "success" | "warning" | "danger" | "info" {
  const types: Record<string, "primary" | "success" | "warning" | "danger" | "info"> = {
    APPROVE: "success",
    REJECT: "danger",
    SUBMIT: "primary",
    RESUBMIT: "primary",
    WITHDRAW: "warning",
    REVIEW: "warning",
  };
  return types[actionType] || "info";
}

/** 审核记录操作类型 → 文本 */
export function getActionText(actionType: string): string {
  const texts: Record<string, string> = {
    APPROVE: "通过审核",
    REJECT: "拒绝审核",
    SUBMIT: "提交审核",
    RESUBMIT: "重新提交",
    WITHDRAW: "撤回审核",
    REVIEW: "开始审核",
  };
  return texts[actionType] || actionType;
}

/** 格式化审核记录时间 */
export function formatDateTime(timestamp: string): string {
  if (!timestamp) return "未知时间";
  try {
    const date = new Date(timestamp);
    return date.toLocaleString("zh-CN", {
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch (error) {
    return "时间格式错误";
  }
}
