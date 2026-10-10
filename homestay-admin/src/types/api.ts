/**
 * 后端API接口类型定义
 */

// 通用分页响应格式
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
  empty: boolean;
  first: boolean;
  last: boolean;
}

// 用户相关API
// 后端用户对象
export interface UserDTO {
  id: number;
  username: string;
  nickname?: string;
  email?: string;
  phoneNumber?: string;
  enabled: boolean;
  role?: string;
  createdAt?: string;
  updatedAt?: string;
}

// 用户状态更新请求
export interface UserUpdateStatusRequest {
  enabled: boolean;
}

// 批量操作请求
export interface UserBatchRequest {
  ids: number[];
}

// 批量状态更新请求
export interface UserBatchStatusRequest extends UserBatchRequest {
  enabled: boolean;
}

// 密码重置响应
export interface UserResetPasswordResponse {
  newPassword: string;
}

// 批量密码重置响应
export interface UserBatchResetPasswordResponse {
  [id: string]: string;
}


// 房源相关API
// 后端房源对象
export interface HomestayDTO {
  id: number;
  title: string;
  description?: string;
  price: number;
  address?: string;
  province?: string;
  city?: string;
  district?: string;
  location?: string;
  featuredImage?: string;
  images?: string[];
  status: "ACTIVE" | "INACTIVE";
  host?: UserDTO;
  createdAt?: string;
  updatedAt?: string;
}

// 房源状态更新请求
export interface HomestayUpdateStatusRequest {
  status: "ACTIVE" | "INACTIVE";
}

// 批量操作请求
export interface HomestayBatchRequest {
  ids: number[];
}

// 批量状态更新请求
export interface HomestayBatchStatusRequest extends HomestayBatchRequest {
  status: "ACTIVE" | "INACTIVE";
}


// 订单相关API
// 后端订单对象
export interface OrderDTO {
  id: number;
  orderNumber: string;
  totalAmount: number;
  status: "PENDING" | "PAID" | "CANCELLED" | "COMPLETED";
  checkInDate?: string;
  checkOutDate?: string;
  homestay?: HomestayDTO;
  guest?: UserDTO;
  createdAt?: string;
  updatedAt?: string;
}

// 订单状态更新请求
export interface OrderUpdateStatusRequest {
  status: "PENDING" | "PAID" | "CANCELLED" | "COMPLETED";
}

// 批量操作请求
export interface OrderBatchRequest {
  ids: number[];
}

// 批量状态更新请求
export interface OrderBatchStatusRequest extends OrderBatchRequest {
  status: "PENDING" | "PAID" | "CANCELLED" | "COMPLETED";
}
