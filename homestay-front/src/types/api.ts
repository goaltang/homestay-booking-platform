/**
 * 统一 API 响应类型定义
 * 所有后端接口遵循此结构返回
 */

export interface ApiResponse<T = unknown> {
  status: 'success' | 'error'
  data: T
  message?: string
}

export interface ApiError {
  status: number
  message: string
  code?: string
  errors?: Record<string, string | string[]>
}

export interface PageResult<T = unknown> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export type ApiPageResponse<T = unknown> = ApiResponse<PageResult<T>>

/**
 * 通用列表查询参数
 */
export interface PageQuery {
  page?: number
  size?: number
  sort?: string
}
