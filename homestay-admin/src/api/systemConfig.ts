import request from "@/utils/request";

export interface SystemConfig {
  id?: number;
  configKey: string;
  configValue: string;
  configName: string;
  description?: string;
  category?: string;
  createdAt?: string;
  updatedAt?: string;
  updatedBy?: string;
}

export interface OperationLog {
  id?: number;
  operator: string;
  operationType: string;
  resource: string;
  resourceId?: string;
  ipAddress?: string;
  detail?: string;
  status?: string;
  operateTime?: string;
  userAgent?: string;
}

export interface LogQueryParams {
  page?: number;
  size?: number;
  operator?: string;
  operationType?: string;
  resource?: string;
  startTime?: string;
  endTime?: string;
}

export interface ApiResponse<T = any> {
  success: boolean;
  message?: string;
  data?: T;
  total?: number;
  totalPages?: number;
  currentPage?: number;
}

export function getAllConfigsApi(): Promise<ApiResponse<SystemConfig[]>> {
  return request.get("/api/admin/system/configs");
}

export function getConfigsByCategoryApi(category: string): Promise<ApiResponse<SystemConfig[]>> {
  return request.get(`/api/admin/system/configs/category/${category}`);
}

export function getConfigByKeyApi(key: string): Promise<ApiResponse<SystemConfig>> {
  return request.get(`/api/admin/system/configs/key/${key}`);
}

export function getConfigsByKeysApi(keys: string[]): Promise<ApiResponse<Record<string, string>>> {
  return request.post("/api/admin/system/configs/batch", keys);
}

export function createConfigApi(
  config: SystemConfig,
  operator?: string,
  ipAddress?: string
): Promise<ApiResponse<SystemConfig>> {
  return request.post("/api/admin/system/configs", config, {
    params: { operator, ipAddress },
  });
}

export function updateConfigApi(
  key: string,
  config: SystemConfig,
  operator?: string,
  ipAddress?: string
): Promise<ApiResponse<SystemConfig>> {
  return request.put(`/api/admin/system/configs/key/${key}`, config, {
    params: { operator, ipAddress },
  });
}

export function deleteConfigApi(
  id: number,
  operator?: string,
  ipAddress?: string
): Promise<ApiResponse<null>> {
  return request.delete(`/api/admin/system/configs/${id}`, {
    params: { operator, ipAddress },
  });
}

export function initDefaultConfigsApi(): Promise<ApiResponse<null>> {
  return request.post("/api/admin/system/configs/init");
}

export function getOperationLogsApi(params: LogQueryParams): Promise<ApiResponse<OperationLog[]>> {
  return request.get("/api/admin/system/logs", { params });
}

export function getRecentLogsApi(limit: number = 50): Promise<ApiResponse<OperationLog[]>> {
  return request.get("/api/admin/system/logs/recent", { params: { limit } });
}
