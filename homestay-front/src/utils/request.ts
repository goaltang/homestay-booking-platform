import axios from "axios";
import type { AxiosInstance } from "axios";
import router from "../router";
import { useUserStore } from "@/stores/user";
import { ElMessage } from "element-plus";
import { formatApiErrorMessage } from "./errorHandler";

declare module "axios" {
  interface InternalAxiosRequestConfig {
    isWhitelisted?: boolean;
  }
}

const baseURL = import.meta.env.VITE_API_BASE_URL || "";
const debugRequests = import.meta.env.DEV && import.meta.env.VITE_DEBUG_REQUESTS === "true";

const requestLog = (...args: unknown[]) => {
  if (debugRequests) console.log(...args);
};

const requestWarn = (...args: unknown[]) => {
  if (debugRequests) console.warn(...args);
};

const request: AxiosInstance = axios.create({
  baseURL,
  timeout: 30000,
  withCredentials: true, // 携带/接收 httpOnly 认证 Cookie（跨源同站）
  headers: {
    "Content-Type": "application/json",
  },
});

// 公开接口（无需认证）：这些路径不附加 Authorization。
// 策略：默认认证（deny-by-default）——凡不在白名单的请求一律尝试携带 token，
// 避免新增私有接口因忘记登记而匿名暴露。
const publicPaths = [
  // 认证流程（POST，必须匿名）
  "/api/auth/login",
  "/api/auth/register",
  "/api/auth/forgot-password",
  "/api/auth/reset-password",
  // 公开搜索（GET/POST 均公开）
  "/api/homestays/search",
  "/api/homestays/map-search",
  "/api/homestays/map-clusters",
  "/api/homestays/nearby",
  "/api/homestays/landmark-search",
  // 公开元数据
  "/api/homestays/amenities",
  "/api/homestay-types",
  "/api/amenities",
  "/api/locations/",
  // 静态资源
  "/uploads/",
  "/api/uploads/",
];

// 仅 GET 公开（同名路径下的写操作仍需认证）
const publicGetPaths = ["/api/homestays/", "/api/recommendations/", "/api/files/"];

// 公开前缀下的私有端点：即便命中上面的公开前缀也必须认证
const authRequiredPaths = [
  "/api/homestays/owner",
  "/api/homestays/batch",
  "/api/homestays/submit-review",
  "/api/homestays/withdraw-review",
  "/api/homestay-images/upload",
];

const isPublicPath = (url: string, method: string): boolean => {
  if (authRequiredPaths.some((path) => url.startsWith(path))) return false;
  if (publicPaths.some((path) => url.startsWith(path))) return true;
  if (method === "GET" && publicGetPaths.some((path) => url.startsWith(path))) {
    return true;
  }
  return false;
};

request.interceptors.request.use(
  (config) => {
    const method = config.method?.toUpperCase() || "GET";
    const url = config.url || "unknown-url";
    const isPublic = isPublicPath(url, method);

    config.headers = config.headers || {};

    if (config.data instanceof FormData) {
      delete config.headers["Content-Type"];
    }

    requestLog("request", {
      method,
      url,
      public: isPublic,
    });

    return config;
  },
  (error) => {
    console.error("Request interceptor error");
    return Promise.reject(error);
  }
);

request.interceptors.response.use(
  (response) => {
    requestLog("response", {
      method: response.config.method?.toUpperCase() || "UNKNOWN",
      url: response.config.url || "unknown-url",
      status: response.status,
    });
    return response;
  },
  (error) => {
    if (error.response) {
      const { status, config } = error.response;
      const url = config?.url || "unknown-url";

      console.error("Response error:", {
        method: config?.method?.toUpperCase() || "UNKNOWN",
        url,
        status,
        hasAuthHeader: Boolean(config?.headers?.Authorization),
      });

      if (status === 401 && !publicPaths.slice(0, 4).some((path) => url.startsWith(path))) {
        const store = useUserStore();
        store.clearSession();
        // 启动 current 的匿名 401 由初始化和守卫处理，不抢占初始路由。
        if ((url !== "/api/auth/current" || store.initialized) && url !== "/api/auth/logout") {
          ElMessage.error("登录状态无效或已过期，请重新登录");
          if (router.currentRoute.value.path !== "/login") {
            void router.push({
              path: "/login",
              query: { redirect: router.currentRoute.value.fullPath },
            });
          }
        }
      } else if (status === 401) {
        // 登录接口的 401 交给登录页处理，避免弹出"已过期"误导用户
        requestWarn("Login API returned 401.");
      } else {
        // 统一弹一次错误提示（含错误码映射）；业务层 handleApiError 只返回消息不再弹窗
        ElMessage.error(formatApiErrorMessage(error));
      }
    } else if (error.request) {
      console.error("Network error");
      ElMessage.error("网络错误，请检查您的网络连接");
    } else {
      console.error("Request config error:", error.message);
    }

    return Promise.reject(error);
  }
);

export default request;
