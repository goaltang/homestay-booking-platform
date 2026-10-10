// 房源状态
export type HomestayStatus = "DRAFT" | "PENDING" | "ACTIVE" | "INACTIVE" | "REJECTED" | "SUSPENDED";

// 订单状态
export type OrderStatus =
  | "PENDING"
  | "CONFIRMED"
  | "PAID"
  | "PAYMENT_PENDING"
  | "PAYMENT_FAILED"
  | "CHECKED_IN"
  | "COMPLETED"
  | "CANCELLED"
  | "CANCELLED_BY_USER"
  | "CANCELLED_BY_HOST"
  | "CANCELLED_SYSTEM"
  | "REJECTED"
  | "REFUND_PENDING"
  | "REFUNDED"
  | "REFUND_FAILED"
  | "READY_FOR_CHECKIN"
  | "DISPUTE_PENDING"
  | "DISPUTED";

// 支付状态 (新增)
export type PaymentStatus =
  | "UNPAID"
  | "PAID"
  | "PAYMENT_FAILED"
  | "REFUNDED"
  | "REFUND_PENDING"
  | "REFUND_FAILED"
  | "PARTIALLY_REFUNDED"; // 根据实际情况调整

// 支付方式 (新增)
export type PaymentMethod = "ALIPAY" | "WECHAT_PAY" | "OTHER"; // 根据实际情况调整

// 房源类型代码（与 homestay_types.code 一致）
export type PropertyTypeCode =
  | "ENTIRE" // 整套公寓
  | "PRIVATE" // 独立房间
  | "LOFT" // 复式住宅
  | "VILLA" // 别墅
  | "STUDIO" // 开间/单间
  | "TOWNHOUSE" // 联排别墅
  | "COURTYARD" // 四合院/院子
  | "HOTEL"; // 酒店公寓

// 兼容旧名称（已废弃，请使用 PropertyTypeCode）
export type HomestayType = PropertyTypeCode;

// 分页响应数据
export interface PaginationResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

// 房源基本信息
export interface HomestayBasic {
  id: number;
  title: string;
  coverImage: string;
  type: HomestayType;
  price: number;
  maxGuests: number;
  status: HomestayStatus;
}

// 订单基本信息
export interface OrderBasic {
  orderNumber: string;
  homestayTitle: string;
  guestName: string;
  guestPhone: string;
  checkInDate: string;
  checkOutDate: string;
  nights: number;
  totalAmount: number;
  status: OrderStatus;
  paymentStatus: PaymentStatus; // 新增
  createTime: string;
}

export interface HomestayGroup {
  id: number;
  name: string;
  code?: string;
  description?: string;
  icon?: string;
  color?: string;
  ownerId: number;
  ownerUsername?: string;
  sortOrder: number;
  isDefault: boolean;
  enabled: boolean;
  homestayCount: number;
  createdAt?: string;
  updatedAt?: string;
}
