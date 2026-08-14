import { PaymentMethod, PaymentStatus } from "./index";

// 房源类型
export interface Homestay {
  id: number;
  title: string;
  type: string;
  price: number;
  maxGuests: number;
  minNights: number;
  province: string;
  city: string;
  district: string;
  address: string;
  amenities: string[];
  description: string;
  coverImage: string;
  images: string[];
  status: string;
  featured: boolean;
  createdAt: string;
  updatedAt: string;
}

// 订单类型
export interface Order {
  id: number;
  orderNumber: string;
  homestayId: number;
  homestayTitle: string;
  hostId: number;
  hostName: string;
  guestId: number;
  guestName: string;
  guestPhone: string;
  checkInDate: string;
  checkOutDate: string;
  nights: number;
  guestCount: number;
  price: number;
  totalAmount: number;
  status: string;
  paymentStatus: PaymentStatus;
  paymentMethod?: PaymentMethod;
  remark: string;
  createTime: string;
  updateTime: string;
}

// 评价类型
export interface Review {
  id: number;
  homestayId: number;
  homestayTitle: string;
  userId: number;
  userName: string;
  userAvatar: string;
  rating: number;
  content: string;
  images: string[];
  response: string;
  responseTime: string;
  createTime: string;
}

// 收益类型
export interface Earning {
  id: number;
  orderNumber: string;
  homestayId: number;
  homestayTitle: string;
  guestName: string;
  checkInDate: string;
  checkOutDate: string;
  nights: number;
  amount: number;
  status: string;
  createTime: string;
}

// 收益汇总类型
export interface EarningSummary {
  totalEarnings: number;
  totalOrders: number;
  averagePerOrder: number;
}

// 收益趋势类型
export interface EarningTrend {
  labels: string[];
  values: number[];
}

// 分页查询结果类型
export interface PageResult<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

// 房东基础信息接口 - 与后端HostDTO完全匹配
export interface HostDTO {
  id: number;
  username: string;
  nickname?: string | null;
  email: string;
  avatar?: string | null;
  phone?: string;
  realName?: string;
  idCard?: string;
  occupation?: string;
  introduction?: string | null;
  languages?: string[] | null;
  companions?: Array<{ name: string; avatar?: string }> | null;

  // 房东统计相关字段
  hostSince?: string | null;
  hostRating?: string | null;
  hostAccommodations?: string | null;
  hostYears?: string | null;
  hostResponseRate?: string | null;
  hostResponseTime?: string | null;

  // 统计数据 - 数值类型
  homestayCount?: number;
  orderCount?: number;
  reviewCount?: number;
  rating?: number; // 统一使用这个字段作为评分
}

// 为了向后兼容，保留原有接口但标记为已废弃
/** @deprecated 请使用 HostDTO */
export type HostDetailInfoData = HostDTO;

// 房东展示信息计算属性接口
export interface HostDisplayInfo {
  displayName: string; // 显示名称（优先级：realName > nickname > username）
  displayAvatar: string; // 显示头像（带默认值）
  displayRating: number; // 显示评分（优先rating，然后hostRating转数值）
  isVerified: boolean; // 是否已认证（根据realName/phone/email判断）
  joinDuration: string; // 加入时长（如"房东2年"）
  achievementBadges: string[]; // 成就徽章列表
}

// 房东统计数据接口
export interface HostStatistics {
  homestayCount: number;
  orderCount: number;
  reviewCount: number;
  rating: number;
  totalEarnings?: number;
  pendingOrders?: number;
  completedOrders?: number;
  cancelledOrders?: number;
}

// 房东简介展示接口
export interface HostProfile {
  basicInfo: HostDTO;
  displayInfo: HostDisplayInfo;
  statistics: HostStatistics;
}

export interface HostCompanion {
  name: string;
  avatar?: string;
}

export interface HostHomestay {
  id: number;
  title: string;
  status: string;
  pricePerNight: number;
  bookingCount: number;
  reviewCount: number;
  rating: number;
  income: number;
  createdAt: string;
  coverImage: string;
  location: string;
  groupId?: number;
  groupName?: string;
}

export interface HostOrder {
  id: number;
  orderNumber: string;
  homestayId: number;
  homestayTitle: string;
  guestName: string;
  guestPhone?: string;
  guestAvatar?: string;
  checkInDate: string;
  checkOutDate: string;
  guestCount: number;
  totalAmount: number;
  status: string;
  paymentStatus?: PaymentStatus | string;
  paymentMethod?: PaymentMethod;
  createdAt?: string;
  createTime?: string;
  updateTime?: string;
  imageUrl?: string;
  nights: number;
  price: number;
  remark?: string;
}

export interface HostReview {
  id: number;
  homestayId: number;
  homestayTitle: string;
  userId: number;
  userName: string;
  userAvatar?: string;
  rating: number;
  content: string;
  images?: string[];
  createdAt: string;
  response?: string;
}

export interface HostPasswordChange {
  oldPassword: string;
  newPassword: string;
}

// 筛选参数类型
export interface FilterParams {
  page?: number;
  size?: number;
  startDate?: string;
  endDate?: string;
  homestayId?: number;
  status?: string;
  type?: string;
  rating?: number;
  responseStatus?: string;
}
