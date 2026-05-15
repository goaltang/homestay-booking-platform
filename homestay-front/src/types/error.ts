/**
 * 前端统一错误类型定义
 */

/** API 响应中的错误结构 */
export interface ApiErrorResponse {
  status: 'error'
  message: string
  code?: string
  errors?: Record<string, string | string[]>
}

/** Axios 错误包装 */
export interface ApiRequestError extends Error {
  response?: {
    status: number
    data?: ApiErrorResponse | string
  }
  displayMessage?: string
}

/** 表单验证错误 */
export interface ValidationError {
  field: string
  message: string
}

/** 统一错误提取工具 */
export function extractErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message
  }
  if (typeof error === 'string') {
    return error
  }
  return '未知错误'
}
