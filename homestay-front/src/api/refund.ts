import request from "@/utils/request";

// 用户申请退款
export const requestRefund = (orderId: number, reason?: string) => {
  return request({
    url: `/api/orders/${orderId}/refund`,
    method: "post",
    data: { reason },
  });
};

// 检查订单退款状态和详情
export const getRefundDetails = (orderId: number) => {
  return request({
    url: `/api/orders/${orderId}/refund-details`,
    method: "get",
  });
};

// 管理员批准退款
export const approveRefund = (orderId: number, refundNote?: string) => {
  return request({
    url: `/api/admin/orders/${orderId}/refund/approve`,
    method: "post",
    data: { refundNote },
  });
};

// 管理员拒绝退款
export const rejectRefund = (orderId: number, rejectReason: string) => {
  return request({
    url: `/api/admin/orders/${orderId}/refund/reject`,
    method: "post",
    data: { rejectReason },
  });
};

// 管理员完成退款
export const completeRefund = (orderId: number, refundTransactionId?: string) => {
  return request({
    url: `/api/admin/orders/${orderId}/refund/complete`,
    method: "post",
    data: { refundTransactionId },
  });
};

// 获取退款预览信息（申请退款前查看预计退款金额）
export const getRefundPreview = (orderId: number) => {
  return request({
    url: `/api/orders/${orderId}/refund-preview`,
    method: "get",
  });
};

// 用户发起争议
export const raiseDispute = (orderId: number, reason: string) => {
  return request({
    url: `/api/orders/${orderId}/dispute`,
    method: "post",
    data: { reason },
  });
};
