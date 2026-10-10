import request from "@/utils/request";
import type { OrderSearchParams } from "@/types";
import { adaptPageParams, adaptOrderStatus } from "@/utils/adapter";

// --- 定义更具体的类型 ---
// 管理员订单搜索参数类型 (匹配 AdminOrderController)
export interface AdminOrderSearchParams {
  page?: number;
  size?: number;
  orderNumber?: string;
  guestName?: string;
  homestayTitle?: string;
  status?: string;
  paymentStatus?: string;
  paymentMethod?: string;
  hostName?: string;
  checkInDateStart?: string; // YYYY-MM-DD
  checkInDateEnd?: string; // YYYY-MM-DD
  createTimeStart?: string; // YYYY-MM-DD
  createTimeEnd?: string; // YYYY-MM-DD
  sort?: string; // e.g., "createdAt,desc"
}

// 管理员订单列表项类型 (匹配后端 OrderDTO)
export interface AdminOrderListItem {
  id: number;
  orderNumber: string;
  homestayId: number;
  homestayTitle: string;
  guestId: number;
  guestName: string;
  guestPhone: string;
  hostId: number;
  hostName: string;
  checkInDate: string;
  checkOutDate: string;
  nights: number;
  guestCount: number;
  price: number;
  totalAmount: number;
  status: string;
  paymentStatus: string | null;
  paymentMethod: string | null;
  remark: string | null;
  createTime: string;
  updateTime: string;
}

// 分页响应类型
interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number; // 后端返回的是 0-based
  size: number;
}
// --- 类型定义结束 ---

// 获取管理员订单列表 (新)
export function getAdminOrders(params: AdminOrderSearchParams) {
  // 参数处理: 移除空值或 undefined 值，后端 @RequestParam(required = false) 会处理
  const filteredParams = Object.fromEntries(
    Object.entries(params).filter(
      ([, value]) => value !== null && value !== undefined && value !== ""
    )
  );

  return request<PageResponse<AdminOrderListItem>>({
    url: "/api/admin/orders",
    method: "get",
    params: filteredParams,
  }).then((response) => {
    // 后端返回的已经是所需结构，可能不需要 adaptPageResponse
    // 但如果需要前端页码是 1-based，可以在这里调整
    // response.page = response.page + 1;
    // 同时，如果列表项需要转换 (如日期格式)，可以在此处理
    // response.content = response.content.map(adaptOrderItem); // adaptOrderItem 需要更新
    return response; // 直接返回后端数据结构
  });
}

// 获取订单详情 (可以保留，但可能需要更新 adaptOrderItem)
export function getOrderDetail(id: number) {
  return request<any>({
    url: `/api/admin/orders/${id}`,
    method: "get",
  }).then((response) => {
    // return adaptOrderItem(response);
    return response; // 返回原始数据
  });
}

// 更新订单状态 (可以保留，注意只更新 status 字段)
export function updateOrderStatus(id: number, status: string) {
  // 注意：这个函数现在只应该用来更新订单的生命周期状态(status)
  // 而不是支付状态(paymentStatus)
  return request({
    url: `/api/admin/orders/${id}/status`,
    method: "put",
    data: { status: status }, // 直接传递后端接受的字符串状态
  });
}

// 新增: 管理员手动确认支付
export function confirmPayment(orderId: number) {
  return request<AdminOrderListItem>({
    url: `/api/admin/orders/${orderId}/confirm-payment`,
    method: "put",
  });
}

// 新增: 管理员发起退款
export function initiateRefund(orderId: number) {
  // 后端目前不需要请求体，所以 data 为空
  return request<AdminOrderListItem>({
    url: `/api/admin/orders/${orderId}/refund`,
    method: "post", // 注意后端是 POST
  });
}

// 管理员批准退款申请
export function approveRefund(orderId: number, refundNote?: string) {
  return request<AdminOrderListItem>({
    url: `/api/admin/orders/${orderId}/refund/approve`,
    method: "post",
    data: {
      refundNote: refundNote || "",
    },
  });
}

// 管理员拒绝退款申请
export function rejectRefund(orderId: number, rejectReason: string) {
  return request<AdminOrderListItem>({
    url: `/api/admin/orders/${orderId}/refund/reject`,
    method: "post",
    data: {
      rejectReason: rejectReason,
    },
  });
}

// 管理员完成退款处理
export function completeRefund(orderId: number, refundTransactionId?: string) {
  return request<AdminOrderListItem>({
    url: `/api/admin/orders/${orderId}/refund/complete`,
    method: "post",
    data: {
      refundTransactionId: refundTransactionId || "",
    },
  });
}

// 发起争议（当房东对退款有异议时）
export function raiseDispute(orderId: number, reason: string) {
  return request<AdminOrderListItem>({
    url: `/api/admin/orders/${orderId}/dispute`,
    method: "post",
    data: {
      reason: reason,
    },
  });
}

// 解决争议（管理员仲裁）
export function resolveDispute(
  orderId: number,
  resolution: "APPROVED" | "REJECTED",
  note?: string
) {
  return request<AdminOrderListItem>({
    url: `/api/admin/orders/${orderId}/dispute/resolve`,
    method: "post",
    data: {
      resolution: resolution,
      note: note || "",
    },
  });
}

// 删除订单
export function deleteOrder(id: number) {
  return request({
    url: `/api/admin/orders/${id}`,
    method: "delete",
  });
}

// 导出订单
export function exportOrders(params: OrderSearchParams) {
  // 转换参数
  const adaptedParams = adaptPageParams(params);

  // 转换状态
  if (adaptedParams.status) {
    adaptedParams.status = adaptOrderStatus(adaptedParams.status, false);
  }

  return request({
    url: "/api/admin/orders/export",
    method: "get",
    params: adaptedParams,
    responseType: "blob",
  });
}

// 新增: 批量删除订单
export function batchDeleteOrders(ids: number[]) {
  return request({
    url: `/api/admin/orders/batch`,
    method: "delete",
    data: { ids },
  });
}

// 新增: 批量更新订单状态
export function batchUpdateOrderStatus(ids: number[], status: string) {
  const backendStatus = adaptOrderStatus(status, false);

  return request({
    url: `/api/admin/orders/batch/status`,
    method: "put",
    data: {
      ids,
      status: backendStatus,
    },
  });
}

// 新增: 批量导出订单
export function batchExportOrders(ids: number[]) {
  return request({
    url: "/api/admin/orders/batch/export",
    method: "post",
    data: { ids },
    responseType: "blob",
  });
}

// ========== 异常订单统计 ==========

// 异常订单统计数据类型
export interface ExceptionOrderStats {
  pendingTimeout: number; // 待处理超时（PENDING超过24小时）
  paymentFailed: number; // 支付失败
  refundFailed: number; // 退款失败
  notCheckedIn: number; // 已支付但未入住
  refundPending: number; // 退款处理中
  disputePending: number; // 争议待处理
  pendingConfirm: number; // 待确认
  total: number; // 异常订单总数
}

// 获取异常订单统计
export function getExceptionOrderStats() {
  return request({
    url: "/api/admin/orders/exception-stats",
    method: "get",
  });
}

// ========== 入住相关 API ==========

// 入住凭证类型
export interface CheckInCredential {
  checkInMethod?: string;
  doorPassword?: string;
  lockboxCode?: string;
  locationDescription?: string;
  validFrom?: string;
  validUntil?: string;
  checkInCode?: string;
  remark?: string;
}

// 入住记录类型
export interface CheckInRecord {
  id: number;
  orderId: number;
  orderNumber: string;
  checkInMethod: string;
  checkedInAt: string;
  operatorType: string;
  checkInCode: string;
  doorPassword: string;
  lockboxCode: string;
  locationDescription: string;
  validFrom: string;
  validUntil: string;
  status: string;
  remark: string;
  createdAt: string;
}

// 退房记录类型
export interface CheckOutRecord {
  id: number;
  orderId: number;
  orderNumber: string;
  checkOutMethod: string;
  checkedOutAt: string;
  operatorType: string;
  actualNights: number;
  depositAmount: number;
  depositStatus: string;
  extraCharges: number;
  extraChargesDescription: string;
  settlementAmount: number;
  status: string;
  remark: string;
  createdAt: string;
}

// 房东设置准备入住
export function prepareCheckIn(orderId: number, credential: CheckInCredential) {
  return request<CheckInRecord>({
    url: `/api/orders/${orderId}/prepare-checkin`,
    method: "put",
    data: credential,
  });
}

// 获取入住凭证
export function getCheckInCredential(orderId: number) {
  return request<CheckInCredential>({
    url: `/api/orders/${orderId}/checkin-credential`,
    method: "get",
  });
}

// 办理入住
export function performCheckIn(orderId: number) {
  return request<CheckInRecord>({
    url: `/api/orders/${orderId}/check-in`,
    method: "put",
  });
}

// 自助入住
export function selfCheckIn(checkInCode: string) {
  return request<CheckInRecord>({
    url: `/api/orders/checkin/self`,
    method: "post",
    data: { checkInCode },
  });
}

// 客人确认到达
export function confirmArrival(orderId: number) {
  return request<CheckInRecord>({
    url: `/api/orders/${orderId}/confirm-arrival`,
    method: "post",
  });
}

// 取消准备入住
export function cancelPrepareCheckIn(orderId: number) {
  return request<CheckInRecord>({
    url: `/api/orders/${orderId}/cancel-prepare`,
    method: "put",
  });
}

// 获取入住记录
export function getCheckInRecord(orderId: number) {
  return request<CheckInRecord>({
    url: `/api/orders/${orderId}/checkin-record`,
    method: "get",
  });
}

// 办理退房
export function performCheckOut(orderId: number, checkOutData?: any) {
  return request<CheckOutRecord>({
    url: `/api/orders/${orderId}/check-out`,
    method: "put",
    data: checkOutData || {},
  });
}

// 自助退房
export function selfCheckOut(orderId: number) {
  return request<CheckOutRecord>({
    url: `/api/orders/${orderId}/checkout/self`,
    method: "post",
  });
}

// 押金操作
export function processDeposit(orderId: number, action: string, amount?: number, note?: string) {
  return request<CheckOutRecord>({
    url: `/api/orders/${orderId}/deposit`,
    method: "post",
    data: {
      action,
      amount,
      note,
    },
  });
}

// 获取退房记录
export function getCheckOutRecord(orderId: number) {
  return request<CheckOutRecord>({
    url: `/api/orders/${orderId}/checkout-record`,
    method: "get",
  });
}

// 确认结算
export function confirmSettlement(orderId: number) {
  return request<CheckOutRecord>({
    url: `/api/orders/${orderId}/checkout/settle`,
    method: "put",
  });
}
