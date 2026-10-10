import request from "@/utils/request";

export interface LoginLog {
  id?: number;
  username: string;
  loginTime?: string;
  ipAddress?: string;
  userAgent?: string;
  loginStatus: string;
  loginType: string;
  logoutTime?: string;
}

export interface LoginLogQueryParams {
  page?: number;
  size?: number;
  username?: string;
  loginType?: string;
  startTime?: string;
  endTime?: string;
  loginStatus?: string;
}

export interface ApiResponse<T = any> {
  success: boolean;
  message?: string;
  data?: T;
  total?: number;
  totalPages?: number;
  currentPage?: number;
}

export function getLoginLogsApi(params: LoginLogQueryParams): Promise<ApiResponse<LoginLog[]>> {
  return request.get("/api/admin/login-logs", { params });
}
