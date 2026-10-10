import request from "@/utils/request";

/**
 * 获取房东的订单列表
 * @param params 分页和过滤参数
 */
export function getHostOrders(params?: { page?: number; size?: number; status?: string }) {
  return request({
    url: "/api/orders/host",
    method: "get",
    params,
  });
}

/**
 * 更新订单状态
 * @param id 订单ID
 * @param status 新状态（CONFIRMED, CHECKED_IN, COMPLETED等）
 */
export function updateOrderStatus(id: number, status: string) {
  return request({
    url: `/api/orders/${id}/status`,
    method: "put",
    data: { status },
  });
}

/**
 * 确认订单
 * @param id 订单ID
 */
export function confirmOrder(id: number) {
  return request({
    url: `/api/orders/${id}/confirm`,
    method: "put",
  });
}

/**
 * 拒绝订单
 * @param id 订单ID
 * @param reason 拒绝原因
 */
export function rejectOrder(id: number, reason: string) {
  return request({
    url: `/api/orders/${id}/reject`,
    method: "put",
    data: { reason },
  });
}

/**
 * 获取房东待处理订单数量
 */
export function getPendingOrderCount() {
  return request({
    url: "/api/orders/pending/count",
    method: "get",
  });
}

/**
 * 根据房源ID获取该房源的订单
 * @param homestayId 房源ID
 * @param params 查询参数
 */
export function getOrdersByHomestay(
  homestayId: number,
  params?: {
    page?: number;
    size?: number;
    status?: string;
  }
) {
  return request({
    url: `/api/orders/homestay/${homestayId}`,
    method: "get",
    params,
  });
}

/**
 * 获取房东订单统计信息
 */
export function getHostOrderStats() {
  return request({
    url: "/api/orders/host/stats",
    method: "get",
  });
}

/**
 * 取消订单（通用接口，后端根据角色自动判定为房东取消或用户取消）
 * @param id 订单ID
 * @param reason 取消原因
 */
export function cancelOrder(id: number, reason = "") {
  return request({
    url: `/api/orders/${id}/cancel`,
    method: "put",
    data: {
      reason,
    },
  });
}

/**
 * 房东发起退款
 * @param orderId 订单ID
 * @param reason 退款原因
 */
export function hostInitiateRefund(orderId: number, reason: string) {
  return request({
    url: `/api/orders/${orderId}/refund`,
    method: "post",
    data: { reason },
  });
}

/**
 * 获取订单退款详情
 * @param orderId 订单ID
 */
export function getRefundDetails(orderId: number) {
  return request({
    url: `/api/orders/${orderId}/refund-details`,
    method: "get",
  });
}
/**
 * 房东同意用户退款申请
 * @param orderId 订单ID
 * @param refundNote 同意备注
 */
export function hostApproveRefund(orderId: number, refundNote: string) {
  return request({
    url: `/api/orders/${orderId}/refund/approve`,
    method: "post",
    data: { refundNote },
  });
}

/**
 * 房东拒绝用户退款申请
 * @param orderId 订单ID
 * @param rejectReason 拒绝原因
 */
export function hostRejectRefund(orderId: number, rejectReason: string) {
  return request({
    url: `/api/orders/${orderId}/refund/reject`,
    method: "post",
    data: { rejectReason },
  });
}

/**
 * 房东发起争议（对退款有异议）
 * @param orderId 订单ID
 * @param reason 争议原因
 */
export function hostRaiseDispute(orderId: number, reason: string) {
  return request({
    url: `/api/orders/${orderId}/dispute`,
    method: "post",
    data: { reason },
  });
}

// ==================== 入住相关API ====================

/**
 * 设置准备入住（生成入住凭证）
 * @param orderId 订单ID
 * @param data 入住凭证信息
 */
export function prepareCheckIn(
  orderId: number,
  data: {
    checkInMethod?: string;
    doorPassword?: string;
    lockboxCode?: string;
    locationDescription?: string;
    remark?: string;
  }
) {
  return request({
    url: `/api/orders/${orderId}/prepare-checkin`,
    method: "put",
    data,
  });
}

/**
 * 获取入住凭证
 * @param orderId 订单ID
 */
export function getCheckInCredential(orderId: number) {
  return request({
    url: `/api/orders/${orderId}/checkin-credential`,
    method: "get",
  });
}

/**
 * 办理入住（房东/管理员手动）
 * @param orderId 订单ID
 */
export function performCheckIn(orderId: number) {
  return request({
    url: `/api/orders/${orderId}/check-in`,
    method: "put",
  });
}

/**
 * 取消准备入住
 * @param orderId 订单ID
 */
export function cancelPrepareCheckIn(orderId: number) {
  return request({
    url: `/api/orders/${orderId}/cancel-prepare`,
    method: "put",
  });
}

/**
 * 获取入住记录
 * @param orderId 订单ID
 */
export function getCheckInRecord(orderId: number) {
  return request({
    url: `/api/orders/${orderId}/checkin-record`,
    method: "get",
  });
}

// ==================== 退房相关API ====================

/**
 * 办理退房
 * @param orderId 订单ID
 * @param data 退房信息
 */
export function performCheckOut(orderId: number, data?: { remark?: string }) {
  return request({
    url: `/api/orders/${orderId}/check-out`,
    method: "put",
    data: data || {},
  });
}

/**
 * 获取退房记录
 * @param orderId 订单ID
 */
export function getCheckOutRecord(orderId: number) {
  return request({
    url: `/api/orders/${orderId}/checkout-record`,
    method: "get",
  });
}

/**
 * 处理押金
 * @param orderId 订单ID
 * @param action 操作类型：COLLECT/REFUND/RETAIN/WAIVE
 * @param amount 金额
 * @param note 备注
 */
export function processDeposit(orderId: number, action: string, amount?: number, note?: string) {
  return request({
    url: `/api/orders/${orderId}/deposit`,
    method: "post",
    data: { action, amount, note },
  });
}

/**
 * 确认结算
 * @param orderId 订单ID
 */
export function confirmSettlement(orderId: number) {
  return request({
    url: `/api/orders/${orderId}/checkout/settle`,
    method: "put",
  });
}

/**
 * 获取退款预览信息
 * @param orderId 订单ID
 */
export function getRefundPreview(orderId: number) {
  return request({
    url: `/api/orders/${orderId}/refund-preview`,
    method: "get",
  });
}

/**
 * 更新额外费用
 * @param orderId 订单ID
 * @param extraCharges 额外费用
 * @param description 说明
 */
export function updateExtraCharges(orderId: number, extraCharges: number, description?: string) {
  return request({
    url: `/api/orders/${orderId}/extra-charges`,
    method: "put",
    data: { extraCharges, description },
  });
}
