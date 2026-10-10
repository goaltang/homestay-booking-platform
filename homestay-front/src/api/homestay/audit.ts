import request from "../../utils/request";
import type { Homestay } from "../../types/homestay";
import { updateHomestay, createHomestay } from "./core";
import type { AuditHistoryResponse, OwnerAuditStats } from "../../types/homestay";

/**
 * 提交房源审核
 */
export function submitHomestayForReview(homestayId: number) {
  console.log(`提交房源审核，ID: ${homestayId}`);
  return request({
    url: `/api/homestays/${homestayId}/submit-review`,
    method: "post",
  })
    .then((response) => {
      console.log(`房源${homestayId}提交审核成功:`, response.data);
      return response;
    })
    .catch((error) => {
      console.error(`房源${homestayId}提交审核失败:`, error);
      throw error;
    });
}

/**
 * 撤回房源审核申请
 */
export function withdrawHomestayReview(homestayId: number) {
  console.log(`撤回房源审核申请，ID: ${homestayId}`);
  return request({
    url: `/api/homestays/${homestayId}/withdraw-review`,
    method: "post",
  })
    .then((response) => {
      console.log(`房源${homestayId}撤回审核成功:`, response.data);
      return response;
    })
    .catch((error) => {
      console.error(`房源${homestayId}撤回审核失败:`, error);
      throw error;
    });
}

/**
 * 申请重新上架 (暂停状态下申请恢复)
 */
export function requestReactivation(homestayId: number, reason: string) {
  console.log(`申请重新上架，ID: ${homestayId}, 理由: ${reason}`);
  return request({
    url: `/api/homestays/${homestayId}/request-reactivation`,
    method: "post",
    data: { reason },
  })
    .then((response) => {
      console.log(`房源${homestayId}申请重新上架成功:`, response.data);
      return response;
    })
    .catch((error) => {
      console.error(`房源${homestayId}申请重新上架失败:`, error);
      throw error;
    });
}

/**
 * 获取房源审核历史
 */
export function getHomestayAuditHistory(
  homestayId: number,
  page: number = 0,
  size: number = 10
): Promise<{ data: AuditHistoryResponse }> {
  console.log(`获取房源审核历史，ID: ${homestayId}, 页码: ${page}, 大小: ${size}`);
  return request({
    url: `/api/homestays/${homestayId}/audit-logs`,
    method: "get",
    params: { page, size },
  })
    .then((response) => {
      console.log(`房源${homestayId}审核历史获取成功:`, response.data);
      return response;
    })
    .catch((error) => {
      console.error(`房源${homestayId}审核历史获取失败:`, error);
      throw error;
    });
}

/**
 * 保存房源为草稿
 */
export function saveHomestayDraft(data: Partial<Homestay>) {
  console.log("保存房源草稿:", data);
  const cleanData = { ...data, status: "DRAFT" };

  if (cleanData.id) {
    return updateHomestay(cleanData.id, cleanData);
  } else {
    return createHomestay(cleanData as Omit<Homestay, "id">);
  }
}

/**
 * 检查房源是否可以提交审核
 */
export function checkHomestayReadyForReview(homestayId: number) {
  console.log(`检查房源是否可以提交审核，ID: ${homestayId}`);
  return request({
    url: `/api/homestays/${homestayId}/check-review-ready`,
    method: "get",
  })
    .then((response) => {
      console.log(`房源${homestayId}审核准备状态:`, response.data);
      return response;
    })
    .catch((error) => {
      console.error(`房源${homestayId}审核准备检查失败:`, error);
      throw error;
    });
}

/**
 * 获取审核统计信息（房东视角）
 */
export function getOwnerAuditStats(): Promise<{ data: OwnerAuditStats }> {
  console.log("获取房东审核统计信息");
  return request({
    url: "/api/homestays/owner/audit-stats",
    method: "get",
  })
    .then((response) => {
      console.log("房东审核统计信息获取成功:", response.data);
      return response;
    })
    .catch((error) => {
      console.error("房东审核统计信息获取失败:", error);
      throw error;
    });
}
