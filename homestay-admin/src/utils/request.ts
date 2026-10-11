import axios from "axios";
import type { AxiosInstance, AxiosRequestConfig } from "axios";
import { ElMessage, ElNotification } from "element-plus";
import { useUserStore } from "@/stores/user";

interface UnwrappedRequest extends Omit<
  AxiosInstance,
  "request" | "get" | "delete" | "head" | "options" | "post" | "put" | "patch"
> {
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
  baseURL: import.meta.env.VITE_API_URL || window.location.origin,
  timeout: 10000,
}) as UnwrappedRequest;

request.interceptors.request.use((config) => {
  const token = useUserStore().token;
  if (token && config.url !== "/api/admin/auth/login") {
    config.headers["Authorization"] = `Bearer ${token}`;
  }
  return config;
});

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
    if (res && res.status === "success" && res.data !== undefined) {
      return res.data;
    }
    return res;
  },
  (error) => {
    if (!error.response) {
      // 网络错误或请求被取消
      ElMessage.error("网络连接失败，请检查您的网络连接");
      return Promise.reject(error);
    }

    const status = error.response.status;
    const errorMsg = formatErrorMessage(error);

    // 处理不同的HTTP状态码
    switch (status) {
      case 401: {
        const url = error.config?.url;
        if (url === "/api/admin/auth/login") {
          ElMessage.error(errorMsg);
          break;
        }
        useUserStore().clearSession();
        if (url !== "/api/admin/auth/info" && url !== "/api/admin/auth/logout") {
          window.location.href = "/login";
        }
        ElMessage.error("登录已过期，请重新登录");
        break;
      }
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
        break;
      default:
        ElMessage.error(errorMsg);
    }

    return Promise.reject(error);
  }
);

// 导出 axios 实例，供 API 文件使用 request.get(), request.post() 等方法
export default request;
