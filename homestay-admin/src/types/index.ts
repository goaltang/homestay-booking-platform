// 分页参数
export interface PageParams {
  page: number;
  pageSize: number;
}

// 分页响应
export interface PageResult<T> {
  list: T[];
  total: number;
}

// 房源
export interface Homestay {
  id: number;
  title: string;
  name?: string; // 兼容老字段
  price?: number;
  pricePerNight?: number;
  status: string;
  createTime?: string;
  createdAt?: string; // 新增
  type?: string;
  maxGuests?: number;
  minNights?: number;
  bedrooms?: number;
  beds?: number;
  bathrooms?: number;
  amenities?: string[];
  images?: string[];
  coverImage?: string;
  description?: string;
  featured?: boolean;
  rating?: number;
  reviewCount?: number;
  hostId?: number;
  hostName?: string;
  ownerName?: string;
  ownerUsername?: string;
  ownerPhone?: string; // 房东手机号
  ownerEmail?: string; // 房东邮箱
  ownerRealName?: string; // 房东真实姓名
  ownerNickname?: string; // 房东昵称
  ownerOccupation?: string; // 房东职业
  ownerIntroduction?: string; // 房东介绍
  ownerId?: number; // 房东ID
  ownerJoinDate?: string; // 房东注册时间
  ownerHostSince?: string; // 房东开始当房东的时间
  ownerHomestayCount?: number; // 房东房源数量
  ownerHostRating?: number; // 房东评分
  ownerAvatar?: string; // 房东头像
  ownerRating?: number; // 房东评分（兼容字段）
  provinceName?: string; // 新增
  cityName?: string; // 新增
  districtName?: string; // 新增
  provinceCode?: string;
  cityCode?: string;
  districtCode?: string;
  addressDetail?: string;
  updatedAt?: string;
}

// 订单
export interface Order {
  orderNo: string;
  homestayName: string;
  userName: string;
  amount: number;
  status: string;
  createTime: string;
}

// 用户
export interface User {
  id: number;
  username: string;
  nickname: string;
  phone: string;
  email: string;
  status: string;
  createTime: string;
  userType?: string;
  verificationStatus?: string;
  realName?: string;
  avatar?: string;
  lastLoginTime?: string;
}

// 房源搜索参数
export interface HomestaySearchParams extends PageParams {
  name?: string;
  title?: string;
  status?: string;
  type?: string;
  minPrice?: number;
  maxPrice?: number;
  landlordName?: string;
  sort?: string;
}

// 订单搜索参数
export interface OrderSearchParams extends PageParams {
  orderNo?: string;
  status?: string;
}

// 用户搜索参数
export interface UserSearchParams extends PageParams {
  username?: string;
  phone?: string;
  email?: string;
  status?: string;
  userType?: string;
  startTime?: string;
  endTime?: string;
  sort?: string;
}

// 状态映射
export interface StatusMap {
  [key: string]: {
    text: string;
    type: string;
  };
}

// 身份验证
export interface IdentityVerification {
  id: number;
  userId: number;
  username: string;
  realName: string;
  idCard: string;
  idCardFront: string;
  idCardBack: string;
  status: string; // 'PENDING', 'VERIFIED', 'REJECTED'
  submitTime: string;
  reviewTime?: string;
  reviewNote?: string;
}

// 身份验证搜索参数
export interface VerificationSearchParams extends PageParams {
  username?: string;
  status?: string;
}

// 违规举报相关类型定义
export interface ViolationReport {
  id: number;
  homestayId: number;
  homestayTitle: string;
  homestayStatus: string;
  ownerName: string;
  ownerUsername: string;
  reporterId: number;
  reporterName: string;
  reporterUsername: string;
  violationType: ViolationType;
  violationTypeName: string;
  reason: string;
  details?: string;
  evidenceImages?: string[];
  status: ViolationReportStatus;
  statusName: string;
  processResult?: string;
  processorId?: number;
  processorName?: string;
  processedAt?: string;
  createdAt: string;
  updatedAt: string;
  reportCount: number;
}

// 违规类型枚举
export enum ViolationType {
  PRICE_FRAUD = "PRICE_FRAUD", // 价格欺诈
  CONTENT_VIOLATION = "CONTENT_VIOLATION", // 内容违规
  DESCRIPTION_VIOLATION = "DESCRIPTION_VIOLATION", // 描述不实
  IMAGE_VIOLATION = "IMAGE_VIOLATION", // 图片违规
  IDENTITY_FRAUD = "IDENTITY_FRAUD", // 身份造假
  SERVICE_VIOLATION = "SERVICE_VIOLATION", // 服务违规
  SAFETY_VIOLATION = "SAFETY_VIOLATION", // 安全违规
  OTHER = "OTHER", // 其他
}

// 举报状态枚举
export enum ViolationReportStatus {
  PENDING = "PENDING", // 待处理
  PROCESSING = "PROCESSING", // 处理中
  VERIFIED = "VERIFIED", // 已核实
  DISMISSED = "DISMISSED", // 已忽略
  RESOLVED = "RESOLVED", // 已解决
}

// 违规处理动作类型
export enum ViolationActionType {
  WARNING = "WARNING", // 警告
  SUSPEND = "SUSPEND", // 暂停
  BAN = "BAN", // 永久封禁
  DISMISS = "DISMISS", // 忽略举报
  MODIFY_REQUIRED = "MODIFY_REQUIRED", // 要求修改
}

// 违规统计数据接口
export interface ViolationStatistics {
  totalReports: number;
  pendingReports: number;
  processedReports: number;
  statusCounts: Record<string, number>;
  typeCounts: Record<string, number>;
}

// 违规搜索参数
export interface ViolationSearchParams extends PageParams {
  status?: string;
  violationType?: string;
  keyword?: string;
}
