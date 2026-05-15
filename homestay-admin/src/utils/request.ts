import axios from "axios";
import type { AxiosInstance, AxiosRequestConfig } from "axios";
import { ElMessage, ElNotification } from "element-plus";
import { useUserStore } from "@/stores/user";

interface UnwrappedRequest extends Omit<AxiosInstance, "request" | "get" | "delete" | "head" | "options" | "post" | "put" | "patch"> {
  <T = any>(config: AxiosRequestConfig): Promise<T>;
  <T = any>(url: string, config?: AxiosRequestConfig): Promise<T>;
  request<T = any>(config: AxiosRequestConfig): Promise<T>;
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T>;
  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T>;
  head<T = any>(url: string, config?: AxiosRequestConfig): Promise<T>;
  options<T = any>(url: string, config?: AxiosRequestConfig): Promise<T>;
  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T>;
  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T>;
  patch<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T>;
}

// 创建一个带类型的request实例
const request = axios.create({
  baseURL: import.meta.env.VITE_API_URL || "http://localhost:8080",
  timeout: 10000,
}) as UnwrappedRequest;

// 日志级别
const logLevels = {
  none: 0,
  error: 1,
  warn: 2,
  info: 3,
  debug: 4,
};

// 当前日志级别
const currentLogLevel = import.meta.env.VITE_LOG_LEVEL
  ? logLevels[import.meta.env.VITE_LOG_LEVEL as keyof typeof logLevels]
  : import.meta.env.DEV ? logLevels.debug : logLevels.error;

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    const userStore = useUserStore();
    const token = userStore.token;
    if (token) {
      config.headers["Authorization"] = `Bearer ${token}`;
    }

    // 开发环境下记录请求日志
    if (currentLogLevel >= logLevels.debug) {
      console.log(
        "Request:",
        config.method?.toUpperCase(),
        config.url,
        config.params || config.data
      );
    }

    return config;
  },
  (error) => {
    console.error("请求错误:", error);
    return Promise.reject(error);
  }
);

// 错误信息映射
const errorMessages: Record<number, string> = {
  400: "请求参数错误",
  401: "未授权，请重新登录",
  403: "没有权限执行此操作",
  404: "请求的资源不存在",
  405: "请求方法不允许",
  408: "请求超时",
  409: "数据冲突",
  410: "请求的资源已被永久删除",
  422: "验证错误",
  429: "请求过于频繁，请稍后再试",
  500: "服务器内部错误",
  501: "服务未实现",
  502: "网关错误",
  503: "服务不可用",
  504: "网关超时",
};

// 格式化错误消息
function formatErrorMessage(error: any): string {
  if (!error.response) {
    return "网络连接失败，请检查您的网络连接";
  }

  const status = error.response.status;
  const defaultMsg = errorMessages[status] || "未知错误";

  // 尝试从响应体中获取详细错误信息
  return (
    error.response?.data?.error ||
    error.response?.data?.message ||
    error.response?.data?.msg ||
    defaultMsg
  );
}

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    // response interceptor handles successful HTTP statuses (2xx)
    const res = response.data;
    const httpStatus = response.status; // Get HTTP status code

    if (currentLogLevel >= logLevels.debug) {
      console.log(
        "Response:",
        response.config.url,
        "HTTP Status:",
        httpStatus,
        "Data:",
        res
      );
    }

    // 1. Check for the specific {status: 'success', data: ...} structure first
    if (res && res.status === "success" && res.data !== undefined) {
      if (currentLogLevel >= logLevels.debug) {
        console.log(
          "Extracting response.data based on {status: 'success'}:",
          res.data
        );
      }
      return res.data; // Return extracted data
    }
    // 2. If the structure doesn't match, but HTTP status is OK (implicitly 2xx here),
    //    return the raw data. This handles APIs returning data directly or different structures.
    else {
      // Log a warning if the structure is unexpected but potentially okay
      if (
        currentLogLevel >= logLevels.warn &&
        res &&
        res.status !== undefined &&
        res.status !== "success"
      ) {
        console.warn(
          `Response status is '${res.status}' (not 'success') for ${response.config.url}. Returning raw data.`,
          res
        );
      } else if (
        currentLogLevel >= logLevels.warn &&
        (!res || res.status === undefined)
      ) {
        console.warn(
          `Response data structure does not match {status, data} for ${response.config.url}. Returning raw data.`,
          res
        );
      }
      return res; // Return raw data for other successful responses (like list API)
    }
  },
  (error) => {
    // Error interceptor handles non-2xx HTTP statuses or network errors
    // 记录错误日志
    if (currentLogLevel >= logLevels.error) {
      console.error("响应错误:", error);
    }

    if (!error.response) {
      // 网络错误或请求被取消
      ElMessage.error("网络连接失败，请检查您的网络连接");
      return Promise.reject(error);
    }

    const status = error.response.status;
    const errorMsg = formatErrorMessage(error);

    // 处理不同的HTTP状态码
    switch (status) {
      case 401:
        // 未授权，清除用户状态并重定向到登录页
        const userStore = useUserStore();
        userStore.logout();
        window.location.href = "/login";
        ElMessage.error("登录已过期，请重新登录");
        break;
      case 403:
        ElMessage.error("没有权限执行此操作");
        break;
      case 422:
        // 验证错误，可能包含多个字段的错误信息
        if (error.response.data?.errors) {
          const validationErrors = error.response.data.errors;
          // 显示验证错误的通知
          ElNotification({
            title: "输入验证错误",
            message: Object.entries(validationErrors)
              .map(
                ([field, msgs]: [string, any]) =>
                  `${field}: ${Array.isArray(msgs) ? msgs.join(", ") : msgs}`
              )
              .join("<br>"),
            type: "error",
            dangerouslyUseHTMLString: true,
            duration: 5000,
          });
        } else {
          ElMessage.error(errorMsg);
        }
        break;
      case 500:
        ElMessage.error(errorMsg);
        // 记录严重错误到日志系统
        if (currentLogLevel >= logLevels.error) {
          console.error("服务器错误:", error.response?.data);
        }
        break;
      default:
        ElMessage.error(errorMsg);
    }

    return Promise.reject(error);
  }
);

// 导出 axios 实例，供 API 文件使用 request.get(), request.post() 等方法
export default request;
