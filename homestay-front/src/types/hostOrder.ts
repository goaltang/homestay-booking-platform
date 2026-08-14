/** 房东端订单列表项 */
export interface HostOrderItem {
  id: number;
  orderNumber?: string;
  status: string;
  paymentStatus: string | null;
  homestayTitle?: string;
  homestayName?: string;
  guestName?: string;
  guestPhone?: string;
  totalPrice?: number;
  totalAmount?: number;
  checkInDate?: string;
  checkOutDate?: string;
  nights?: number;
  guestCount?: number;
  createTime?: string;
  createdTime?: string;
  checkedInAt?: string;
  remark?: string;
  remarks?: string;
  // 退款相关
  refundType?: string;
  refundReason?: string;
  refundAmount?: number;
  depositAmount?: number;
  refundInitiatedBy?: number;
  refundInitiatedByName?: string;
  refundInitiatedAt?: string;
  refundProcessedBy?: number;
  refundProcessedByName?: string;
  refundProcessedAt?: string;
  refundTransactionId?: string;
  // 争议相关
  disputeReason?: string;
  disputeRaisedBy?: number;
  disputeRaisedAt?: string;
  disputeResolvedAt?: string;
  disputeResolution?: string;
  disputeResolutionNote?: string;
}
