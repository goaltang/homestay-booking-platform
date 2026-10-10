import request from "@/utils/request";
import type {
  Homestay,
  HomestaySearchParams,
  PageResult, // 导入 PageResult 以获得更强的类型检查
} from "@/types";
import { adaptPageParams, adaptPageResponse, adaptHomestayItem } from "@/utils/adapter";

// Temporary type definition if not available in @/types
interface HomestayTypeDTO {
  id: number;
  code: string;
  name: string;
  description?: string;
  icon?: string;
  active: boolean;
  sortOrder: number;
  categoryId?: number;
  categoryName?: string;
  createdAt?: string;
  updatedAt?: string;
}

// 审核记录类型定义
interface AuditLog {
  id: number;
  homestayId: number;
  homestayTitle: string;
  reviewerId: number;
  reviewerName: string;
  oldStatus: string;
  newStatus: string;
  actionType: string;
  reviewReason?: string;
  reviewNotes?: string;
  createdAt: string;
  ipAddress?: string;
}

// 审核请求类型定义
interface ReviewRequest {
  actionType: "APPROVE" | "REJECT";
  targetStatus?: string;
  reviewReason?: string;
  reviewNotes?: string;
}

// 审核员工作量统计

// 审核统计数据
interface AuditStatistics {
  totalPending: number;
  totalApproved: number;
  totalRejected: number;
  averageProcessTime: number;
  approvalRate: number;
}

// 获取房源列表
export function getHomestayList(params: HomestaySearchParams): Promise<PageResult<Homestay>> {
  const adaptedParams = adaptPageParams(params);

  // 使用 title 字段进行搜索 (如果存在)
  if ("title" in params && params.title) {
    // 直接检查原始 params
    adaptedParams.title = params.title;
    // 移除旧的 name (如果 HomestaySearchParams 中还存在)
    if ("name" in adaptedParams) {
      delete adaptedParams.name;
    }
  } else if ("name" in adaptedParams) {
    // 如果仍然是 name, 做兼容转换，但建议前端统一用 title
    adaptedParams.title = adaptedParams.name;
    delete adaptedParams.name;
  }

  // 状态转换 (保持不变)
  if (adaptedParams.status) {
    adaptedParams.status = adaptedParams.status;
  }

  // 明确返回类型
  return request<{
    content: any[]; // 后端原始数据
    totalElements: number;
    totalPages: number;
    page: number;
    size: number;
  }>({
    url: "/api/admin/homestays",
    method: "get",
    params: adaptedParams,
  }).then((response) => {
    // adaptPageResponse 应该返回 PageResult<Homestay>
    return adaptPageResponse<Homestay>(response, adaptHomestayItem);
  });
}

// 获取房源详情
export function getHomestayDetail(id: number): Promise<Homestay> {
  return request<any>({
    url: `/api/admin/homestays/${id}`,
    method: "get",
  }).then((response) => {
    // adaptHomestayItem 应该返回 Homestay 类型
    return adaptHomestayItem(response.data || response); // 处理可能的包装
  });
}

// 获取房源详情（包含完整房东信息）
export function getHomestayDetailWithOwner(id: number): Promise<Homestay> {
  return request<any>({
    url: `/api/admin/homestays/${id}/with-owner`,
    method: "get",
  })
    .then((response) => {
      const data = response.data || response;
      console.log("获取到的带房东信息的房源数据:", data);
      return adaptHomestayItem(data);
    })
    .catch((error) => {
      console.warn("带房东信息的API不可用，使用标准API:", error);
      // 如果专用API不存在，降级使用标准API
      return getHomestayDetail(id);
    });
}

// 创建房源
export function createHomestay(data: Omit<Homestay, "id">): Promise<Homestay> {
  // 直接使用传入的 data (假设已包含 title 和新的地址字段)
  // 移除旧的 name -> title 转换
  const adaptedData = {
    ...data, // 包含 title, provinceCode, cityCode, districtCode, addressDetail 等
    // status: data.status, // status 应该已包含在 data 中
  };
  // 显式移除旧的 address (如果存在于 data)
  delete (adaptedData as any).address;

  return request<any>({
    url: "/api/admin/homestays",
    method: "post",
    data: adaptedData,
  }).then((response) => {
    return adaptHomestayItem(response.data || response);
  });
}

// 更新房源
export function updateHomestay(id: number, data: Partial<Homestay>): Promise<Homestay> {
  // 直接使用传入的 data (假设已包含 title 和新的地址字段)
  const adaptedData: Partial<Homestay> = { ...data };

  // 移除旧的 name -> title 转换
  if ("name" in adaptedData) {
    delete (adaptedData as any).name;
  }
  // 移除旧的 address
  if ("address" in adaptedData) {
    delete (adaptedData as any).address;
  }

  // 确保传递了所有需要的字段，包括新的地址字段

  return request<any>({
    url: `/api/admin/homestays/${id}`,
    method: "put",
    data: adaptedData,
  }).then((response) => {
    return adaptHomestayItem(response.data || response);
  });
}

// 删除房源
export function deleteHomestay(id: number) {
  return request({
    url: `/api/admin/homestays/${id}`,
    method: "delete",
  });
}

// 更新房源状态
export function updateHomestayStatus(id: number, status: string) {
  console.log(`Updating status for homestay ${id} to: ${status}`);

  return request({
    url: `/api/admin/homestays/${id}/status`,
    method: "put",
    data: { status: status },
  });
}

// 更新首页精选状态
export function updateHomestayFeatured(id: number, featured: boolean): Promise<any> {
  console.log("API调用: PUT /api/admin/homestays/" + id + "/featured", { featured });
  return request({
    url: `/api/admin/homestays/${id}/featured`,
    method: "put",
    data: { featured },
  });
}

// 新增: 批量删除房源
export function batchDeleteHomestays(ids: number[]) {
  return request({
    url: `/api/admin/homestays/batch`,
    method: "delete",
    data: { ids },
  });
}

// 新增: 批量更新房源状态
export function batchUpdateHomestayStatus(ids: number[], status: string) {
  console.log(`Batch updating status for ids ${ids} to: ${status}`);

  return request({
    url: `/api/admin/homestays/batch/status`,
    method: "put",
    data: {
      ids,
      status: status,
    },
  });
}

/**
 * 获取所有激活的房源类型列表
 */
export function getActiveHomestayTypes(): Promise<HomestayTypeDTO[]> {
  return request<HomestayTypeDTO[]>({
    url: "/api/homestay-types",
    method: "get",
  }).then((response) => {
    if (Array.isArray(response)) {
      return response;
    } else {
      console.error(
        "Invalid response received for active homestay types (expected array):",
        response
      );
      return [];
    }
  });
}

// ======= 新增：审核相关API接口 =======

/**
 * 执行房源审核操作
 */
export function reviewHomestay(id: number, reviewData: ReviewRequest): Promise<AuditLog> {
  return request<AuditLog>({
    url: `/api/homestays/${id}/review`,
    method: "post",
    data: reviewData,
  });
}

/**
 * 获取房源审核历史记录
 */
export function getHomestayAuditLogs(
  id: number,
  page: number = 1,
  size: number = 10
): Promise<PageResult<AuditLog>> {
  return request<{
    content: AuditLog[];
    totalElements: number;
    totalPages: number;
    page: number;
    size: number;
  }>({
    url: `/api/homestays/${id}/audit-logs`,
    method: "get",
    params: { page: page - 1, size }, // 后端是0开始的分页
  }).then((response) => {
    return {
      list: response.content,
      total: response.totalElements,
      totalPages: response.totalPages,
      currentPage: response.page + 1, // 转换为1开始的分页
      pageSize: response.size,
    };
  });
}

/**
 * 房东提交房源审核
 */
export function submitHomestayForReview(id: number): Promise<AuditLog> {
  return request<AuditLog>({
    url: `/api/homestays/${id}/submit-review`,
    method: "post",
  });
}

/**
 * 获取审核统计数据
 */
export function getAuditStatistics(startDate?: string, endDate?: string): Promise<AuditStatistics> {
  return request<AuditStatistics>({
    url: "/api/admin/statistics/audit",
    method: "get",
    params: {
      startDate,
      endDate,
    },
  });
}

/**
 * 获取待审核房源列表（优化版）
 */
export function getPendingReviewHomestays(
  page: number = 1,
  size: number = 10
): Promise<PageResult<Homestay>> {
  return request<{
    content: any[];
    totalElements: number;
    totalPages: number;
    page: number;
    size: number;
  }>({
    url: "/api/admin/homestays/pending-review",
    method: "get",
    params: { page: page - 1, size },
  }).then((response) => {
    return adaptPageResponse<Homestay>(response, adaptHomestayItem);
  });
}

/**
 * 批量审核房源
 */
export function batchReviewHomestays(
  ids: number[],
  reviewData: ReviewRequest
): Promise<{ successCount: number; failureCount: number; results: any[] }> {
  return request<{
    successCount: number;
    failureCount: number;
    results: any[];
  }>({
    url: "/api/homestays/batch/review",
    method: "post",
    data: {
      homestayIds: ids,
      ...reviewData,
    },
  });
}

/**
 * 获取所有审核历史记录
 */
export function getAllAuditHistory(params: {
  page: number;
  size: number;
  actionType?: string;
  reviewerName?: string;
  homestayId?: string;
  startTime?: string;
  endTime?: string;
}): Promise<PageResult<AuditLog>> {
  return request<{
    content: AuditLog[];
    totalElements: number;
    totalPages: number;
    page: number;
    size: number;
  }>({
    url: `/api/admin/audit/history`,
    method: "get",
    params: {
      ...params,
      page: params.page - 1, // 后端是0开始的分页
    },
  }).then((response) => {
    return {
      list: response.content,
      total: response.totalElements,
      totalPages: response.totalPages,
      currentPage: response.page + 1,
      pageSize: response.size,
    };
  });
}

/**
 * 获取详细审核统计数据
 */
export function getDetailedAuditStatistics(
  startDate?: string,
  endDate?: string
): Promise<{
  totalReviews: number;
  approvedCount: number;
  rejectedCount: number;
  approvalRate: number;
  rejectionRate: number;
  avgProcessTime: number;
  reviewerStats: any[];
  reviewerList: any[];
  rejectionReasons: any[];
  efficiencyStats: any;
}> {
  return request({
    url: `/api/admin/audit/detailed-statistics`,
    method: "get",
    params: {
      startDate,
      endDate,
    },
  });
}

/**
 * 强制下架房源（因违规）
 */
export function forceDelistHomestay(
  id: number,
  data: {
    reason: string;
    notes?: string;
    violationType?: string;
  }
) {
  return request({
    url: `/api/admin/homestays/${id}/force-delist`,
    method: "post",
    data,
  });
}

/**
 * 批量强制下架房源
 */
export function batchForceDelistHomestays(data: {
  ids: number[];
  reason: string;
  notes?: string;
  violationType?: string;
}) {
  return request({
    url: `/api/admin/homestays/batch/force-delist`,
    method: "post",
    data,
  });
}

// 导出新增的类型定义
export type { AuditLog, ReviewRequest, AuditStatistics };
