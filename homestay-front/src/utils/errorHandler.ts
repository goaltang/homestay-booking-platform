import type { AxiosError } from "axios";

// 错误码映射表
const errorCodeMessages: Record<string, string> = {
  // 通用错误
  UNAUTHORIZED: "您未登录或登录已过期，请重新登录",
  FORBIDDEN: "您没有权限执行此操作",
  NOT_FOUND: "请求的资源不存在",
  INTERNAL_SERVER_ERROR: "服务器内部错误，请稍后再试",
  BAD_REQUEST: "请求参数错误",

  // 订单相关错误
  ORDER_NOT_FOUND: "订单不存在",
  ORDER_ALREADY_CANCELLED: "订单已取消，无法执行此操作",
  ORDER_ALREADY_PAID: "订单已支付，不能重复支付",
  ORDER_NOT_CONFIRMED: "订单尚未确认，无法支付",
  ORDER_CANCELLATION_NOT_ALLOWED: "当前状态的订单不允许取消",
  INVALID_STATUS_TRANSITION: "不允许的状态变更操作",
  DATES_NOT_AVAILABLE: "所选日期已被预订，请选择其他日期",

  // 支付相关错误
  PAYMENT_FAILED: "支付失败，请稍后重试",
  PAYMENT_TIMEOUT: "支付超时，请重新发起支付",
  REFUND_FAILED: "退款处理失败，请联系客服",

  // 权限相关错误
  NOT_ORDER_OWNER: "您不是此订单的所有者",
  NOT_HOMESTAY_OWNER: "您不是此房源的所有者",

  // 用户相关错误
  USER_NOT_FOUND: "用户不存在",
  INVALID_CREDENTIALS: "用户名或密码错误",
  USER_ALREADY_EXISTS: "用户已存在",
};

interface ApiErrorData {
  message?: string;
  error?: string;
  msg?: string;
  code?: string;
  errorCode?: string;
  details?: any;
  [key: string]: any;
}

/**
 * 将 Axios 错误格式化为可展示的错误消息（纯函数，不产生副作用）
 * @param error Axios错误对象
 * @param defaultMsg 默认错误消息
 * @returns 格式化后的错误消息
 */
export function formatApiErrorMessage(
  error: AxiosError,
  defaultMsg = "操作失败，请稍后再试"
): string {
  // 网络错误（无响应）
  if (!error.response) {
    return "网络连接失败，请检查网络设置";
  }

  const { status, data } = error.response;

  // 401 未授权
  if (status === 401) {
    return "登录已过期，请重新登录";
  }

  // 提取错误信息
  let errorMessage = defaultMsg;
  let errorCode = "";

  if (data) {
    // 尝试从多种可能的错误格式中提取信息
    if (typeof data === "string") {
      errorMessage = data;
    } else {
      // 将data转换为我们定义的ApiErrorData类型
      const errorData = data as ApiErrorData;

      if (errorData.message) {
        errorMessage = errorData.message;
      } else if (errorData.error) {
        errorMessage = errorData.error;
      } else if (errorData.msg) {
        errorMessage = errorData.msg;
      }

      // 提取错误码
      errorCode = errorData.code || errorData.errorCode || "";
    }
  }

  // 使用预定义的错误码消息
  if (errorCode && errorCodeMessages[errorCode]) {
    errorMessage = errorCodeMessages[errorCode];
  }

  // 无错误码、且未提取到具体错误信息时，按 HTTP 状态码兜底，
  // 避免用笼统的"操作失败"，同时不覆盖后端已返回的具体 message。
  if (!errorCode && errorMessage === defaultMsg) {
    const statusMessages: Record<number, string> = {
      400: "请求参数错误",
      403: "没有权限访问该资源",
      404: "请求的资源不存在",
      500: "服务器错误，请稍后再试",
    };
    if (statusMessages[status]) {
      errorMessage = statusMessages[status];
    }
  }

  return errorMessage;
}

/**
 * 处理API错误并返回格式化后的错误消息。
 * 注意：错误提示（ElMessage）由 request.ts 响应拦截器统一弹出，
 * 本函数只负责格式化消息，避免同一错误被重复弹出。
 * @param error Axios错误对象
 * @param defaultMsg 默认错误消息
 * @returns 格式化后的错误消息
 */
export function handleApiError(
  error: AxiosError,
  defaultMsg = "操作失败，请稍后再试"
): string {
  return formatApiErrorMessage(error, defaultMsg);
}

/**
 * 将多种可能的API错误格式统一
 * @param error 原始错误
 * @returns 统一格式的错误对象
 */
export function normalizeApiError(error: any): {
  code: string;
  message: string;
  status: number;
  details?: any;
} {
  if (!error) {
    return {
      code: "UNKNOWN_ERROR",
      message: "未知错误",
      status: 500,
    };
  }

  const response = error.response || {};
  const data = response.data || ({} as ApiErrorData);

  return {
    code: data.code || data.errorCode || "UNKNOWN_ERROR",
    message:
      data.message || data.error || data.msg || error.message || "未知错误",
    status: response.status || 500,
    details: data.details || data,
  };
}

export default handleApiError;
