import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { AxiosError, AxiosHeaders } from "axios";
import type { InternalAxiosRequestConfig } from "axios";
import type { RouteRecordRaw } from "vue-router";
import { useUserStore } from "@/stores/user";
import request from "@/utils/request";
import router from "@/router";
import { getAllTypesApi } from "@/api/type";

vi.mock("element-plus", () => ({
  ElMessage: { error: vi.fn() },
  ElNotification: vi.fn(),
}));

vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>();
  return {
    ...actual,
    createRouter: (options: Parameters<typeof actual.createRouter>[0]) => {
      const stubComponents = (routes: typeof options.routes): RouteRecordRaw[] =>
        routes.map((route) => {
          const result = { ...route };
          if (result.component) result.component = { render: () => null };
          if (result.children) result.children = stubComponents(result.children);
          return result;
        });
      return actual.createRouter({
        ...options,
        history: actual.createMemoryHistory(),
        routes: stubComponents(options.routes),
      });
    },
  };
});

const admin = { id: 7, username: "administrator", role: "ADMIN" };
const calls: InternalAxiosRequestConfig[] = [];
let respond: (config: InternalAxiosRequestConfig) => unknown | Promise<unknown>;

function rejectHttp(config: InternalAxiosRequestConfig, status: number) {
  throw new AxiosError("Request failed", undefined, config, undefined, {
    status,
    statusText: String(status),
    headers: new AxiosHeaders(),
    config,
    data: { message: "认证失败" },
  });
}

function saveSession() {
  localStorage.setItem("homestay_admin_token", "saved-admin-token");
  localStorage.setItem("homestay_admin_user", JSON.stringify(admin));
}

beforeEach(async () => {
  vi.unstubAllGlobals();
  vi.stubGlobal("requestAnimationFrame", (callback: FrameRequestCallback) => callback(0));
  localStorage.clear();
  setActivePinia(createPinia());
  calls.length = 0;
  respond = () => ({ ...admin, role: "ROLE_ADMIN" });
  request.defaults.adapter = async (config) => {
    calls.push(config);
    const data = await respond(config);
    return { data, status: 200, statusText: "OK", headers: new AxiosHeaders(), config };
  };
  await router.push("/login");
  // The login navigation initializes the previous empty store; each test starts fresh.
  setActivePinia(createPinia());
});

afterEach(() => vi.unstubAllGlobals());

describe("后台会话与路由授权", () => {
  it("不迁移通用token，也不信任缓存中的ADMIN身份", async () => {
    localStorage.setItem("token", "guest-token");
    localStorage.setItem("userInfo", JSON.stringify(admin));
    const store = useUserStore();
    await router.push("/dashboard");
    expect(router.currentRoute.value.path).toBe("/login");
    expect(store.isLoggedIn).toBe(false);
    expect(calls).toHaveLength(0);
    expect(localStorage.getItem("homestay_admin_token")).toBeNull();
    expect(localStorage.getItem("token")).toBe("guest-token");
  });

  it("导航和并发初始化等待info验证后才放行ADMIN", async () => {
    saveSession();
    let resolveInfo!: (value: typeof admin) => void;
    respond = () => new Promise<typeof admin>((resolve) => (resolveInfo = resolve));
    const store = useUserStore();
    const initialization = store.initialize();
    const secondInitialization = store.initialize();
    const navigation = router.push("/dashboard");
    await vi.waitFor(() => expect(calls).toHaveLength(1));
    expect(store.isLoggedIn).toBe(false);
    expect(router.currentRoute.value.path).toBe("/login");
    expect(calls[0].url).toBe("/api/admin/auth/info");
    expect(calls[0].headers.Authorization).toBe("Bearer saved-admin-token");
    expect(calls[0].withCredentials).not.toBe(true);
    resolveInfo({ ...admin, role: "ROLE_ADMIN" });
    await Promise.all([initialization, secondInitialization, navigation]);
    expect(router.currentRoute.value.path).toBe("/dashboard");
    expect(store.userInfo).toEqual(admin);
    expect(store.isLoggedIn).toBe(true);
  });

  it.each(["expired", "guest", "forbidden"])("拒绝%s会话并清理后台缓存", async (kind) => {
    saveSession();
    respond = (config) => {
      if (kind === "expired") return rejectHttp(config, 401);
      if (kind === "forbidden") return rejectHttp(config, 403);
      return { ...admin, role: "USER" };
    };
    const store = useUserStore();
    await router.push("/dashboard");
    expect(router.currentRoute.value.path).toBe("/login");
    expect(store.isLoggedIn).toBe(false);
    expect(store.token).toBe("");
    expect(store.userInfo).toBeNull();
    expect(localStorage.getItem("homestay_admin_token")).toBeNull();
    expect(localStorage.getItem("homestay_admin_user")).toBeNull();
  });

  it("初始化网络失败不授权，但保留Bearer供刷新重新验证", async () => {
    saveSession();
    respond = (config) => {
      throw new AxiosError("offline", "ERR_NETWORK", config);
    };
    const store = useUserStore();
    await router.push("/dashboard");
    expect(router.currentRoute.value.path).toBe("/login");
    expect(store.isLoggedIn).toBe(false);
    expect(store.userInfo).toBeNull();
    expect(store.token).toBe("saved-admin-token");
    expect(localStorage.getItem("homestay_admin_token")).toBe("saved-admin-token");
    // 模拟刷新：新的 Pinia 身份仍需服务端验证。
    respond = () => ({ ...admin, role: "ROLE_ADMIN" });
    setActivePinia(createPinia());
    await router.push("/dashboard");
    expect(useUserStore().isLoggedIn).toBe(true);
    expect(router.currentRoute.value.path).toBe("/dashboard");
  });

  it("退出后到达的info响应不能恢复旧会话", async () => {
    saveSession();
    let resolveInfo!: (value: typeof admin) => void;
    respond = () => new Promise<typeof admin>((resolve) => (resolveInfo = resolve));
    const store = useUserStore();
    const initialization = store.initialize();
    await vi.waitFor(() => expect(calls).toHaveLength(1));
    store.clearSession();
    resolveInfo(admin);
    await initialization;
    expect(store.isLoggedIn).toBe(false);
    expect(store.userInfo).toBeNull();
    expect(localStorage.getItem("homestay_admin_token")).toBeNull();
  });

  it("合法登录保存后端id和ADMIN身份", async () => {
    const log = vi.spyOn(console, "log");
    respond = () => ({ token: "new-admin-token", admin: { ...admin, role: "ROLE_ADMIN" } });
    const store = useUserStore();
    await store.login({ username: admin.username, password: "secret-password" });
    expect(store.isLoggedIn).toBe(true);
    expect(store.userInfo).toEqual(admin);
    expect(localStorage.getItem("homestay_admin_userId")).toBe("7");
    await router.push("/dashboard");
    await router.push("/login");
    expect(router.currentRoute.value.path).toBe("/dashboard");
    expect(calls.map((call) => call.url)).toEqual(["/api/admin/auth/login"]);
    expect(calls[0].headers.Authorization).toBeUndefined();
    expect(log).not.toHaveBeenCalled();
  });

  it("登录接口即使返回普通用户也不能建立后台会话", async () => {
    respond = () => ({ token: "guest-token", admin: { ...admin, role: "USER" } });
    const store = useUserStore();
    await expect(store.login({ username: "guest", password: "password" })).rejects.toThrow(
      "请使用管理员账号登录"
    );
    expect(store.isLoggedIn).toBe(false);
    expect(localStorage.getItem("homestay_admin_token")).toBeNull();
  });

  it("失败登录401不强跳页面且不输出凭据", async () => {
    const log = vi.spyOn(console, "log");
    const error = vi.spyOn(console, "error");
    const warn = vi.spyOn(console, "warn");
    const location = { href: "/login?retry=1" };
    vi.stubGlobal("window", { location });
    respond = (config) => rejectHttp(config, 401);
    const store = useUserStore();
    await expect(store.login({ username: "bad", password: "secret-password" })).rejects.toThrow();
    expect(location.href).toBe("/login?retry=1");
    expect(store.isLoggedIn).toBe(false);
    expect(log).not.toHaveBeenCalled();
    expect(error).not.toHaveBeenCalled();
    expect(warn).not.toHaveBeenCalled();
  });

  it.each([200, 401, 500])("退出HTTP %s只请求一次且总是清理本地后台会话", async (status) => {
    saveSession();
    localStorage.setItem("token", "guest-token");
    const store = useUserStore();
    await store.initialize();
    calls.length = 0;
    respond = (config) => (status === 200 ? {} : rejectHttp(config, status));
    if (status === 200) await store.logout();
    else await expect(store.logout()).rejects.toThrow();
    expect(calls).toHaveLength(1);
    expect(calls[0].url).toBe("/api/admin/auth/logout");
    expect(calls[0].headers.Authorization).toBe("Bearer saved-admin-token");
    expect(store.token).toBe("");
    expect(store.isLoggedIn).toBe(false);
    expect(localStorage.getItem("homestay_admin_userId")).toBeNull();
    expect(localStorage.getItem("token")).toBe("guest-token");
  });

  it("房源类型接口统一使用Bearer和公共响应解包", async () => {
    saveSession();
    const store = useUserStore();
    await store.initialize();
    respond = () => ({ success: true, data: [{ id: 1, name: "民宿" }] });
    const result = await getAllTypesApi();
    expect(result).toEqual({ success: true, data: [{ id: 1, name: "民宿" }] });
    expect(calls.at(-1)?.headers.Authorization).toBe("Bearer saved-admin-token");
    expect(calls.at(-1)?.withCredentials).not.toBe(true);
  });

  it("已验证会话的普通请求网络失败保留认证状态", async () => {
    saveSession();
    const store = useUserStore();
    await store.initialize();
    respond = (config) => {
      throw new AxiosError("Network Error", "ERR_NETWORK", config);
    };
    await expect(getAllTypesApi()).rejects.toThrow("Network Error");
    expect(store.isLoggedIn).toBe(true);
    expect(store.userInfo).toEqual(admin);
    expect(localStorage.getItem("homestay_admin_token")).toBe("saved-admin-token");
  });

  it("房源类型接口401只本地清理并跳转，不递归调用退出API", async () => {
    saveSession();
    const store = useUserStore();
    await store.initialize();
    calls.length = 0;
    const location = { href: "/types" };
    vi.stubGlobal("window", { location });
    respond = (config) => rejectHttp(config, 401);
    await expect(getAllTypesApi()).rejects.toThrow();
    expect(store.isLoggedIn).toBe(false);
    expect(location.href).toBe("/login");
    expect(calls.map((call) => call.url)).toEqual(["/api/v1/homestay-types/all"]);
  });
});
