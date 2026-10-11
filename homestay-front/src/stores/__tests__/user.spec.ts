import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { AxiosError } from "axios";
import type { AxiosResponse, InternalAxiosRequestConfig } from "axios";
import request from "@/utils/request";
import { useUserStore } from "@/stores/user";
import { useChatStore } from "@/stores/chat";
import { useNotificationStore } from "@/stores/notification";
import { ensureUserLoggedIn, authManager, AccessLevel } from "@/utils/auth";
import { disconnectWebSocket, initWebSocket, setAuthToken } from "@/services/websocketService";
import router from "@/router";

vi.mock("@/router", () => ({
  default: {
    currentRoute: { value: { path: "/host/homestay/create", fullPath: "/host/homestay/create" } },
    push: vi.fn().mockResolvedValue(undefined),
  },
}));
vi.mock("@/services/websocketService", () => ({
  initWebSocket: vi.fn(),
  disconnectWebSocket: vi.fn(),
  setAuthToken: vi.fn(),
}));
vi.mock("element-plus", () => ({
  ElMessage: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}));

const guest = { id: 7, username: "guest", email: "guest@example.test", role: "ROLE_USER" };
function response(config: InternalAxiosRequestConfig, data: unknown): AxiosResponse {
  return { config, data, status: 200, statusText: "OK", headers: {} };
}
function unauthorized(config: InternalAxiosRequestConfig): AxiosError {
  const res = { ...response(config, { message: "unauthorized" }), status: 401 };
  return new AxiosError("unauthorized", "ERR_BAD_REQUEST", config, undefined, res);
}

beforeEach(() => {
  setActivePinia(createPinia());
  localStorage.clear();
  sessionStorage.clear();
  request.defaults.adapter = async (config) =>
    response(config, config.url === "/api/auth/current" ? guest : { unreadCount: 0 });
});

describe("cookie session recovery and cleanup", () => {
  it("registers with the backend nested identity and initializes WS", async () => {
    request.defaults.adapter = async (config) =>
      response(
        config,
        config.url === "/api/auth/register"
          ? { token: "new-session", user: guest }
          : { unreadCount: 0 }
      );
    const store = useUserStore();
    await store.register({ username: "guest", email: guest.email, password: "password123" });
    expect(store.userInfo?.id).toBe(7);
    expect(store.userInfo?.username).toBe("guest");
    expect(store.token).toBe("new-session");
    expect(initWebSocket).toHaveBeenLastCalledWith(7);
  });

  it("updates profile from the auth endpoint nested identity", async () => {
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) =>
      response(
        config,
        config.url === "/api/auth/profile"
          ? { user: { ...guest, phone: "13800000000" } }
          : { unreadCount: 0 }
      )
    );
    request.defaults.adapter = adapter;
    const store = useUserStore();
    await store.updateProfile({
      username: guest.username,
      email: guest.email,
      phone: "13800000000",
    });
    expect(store.userInfo?.id).toBe(7);
    expect(store.userInfo?.phone).toBe("13800000000");
    expect(adapter.mock.calls[0][0].url).toBe("/api/auth/profile");
  });

  it("changes password with confirmation and clears the revoked session", async () => {
    const store = useUserStore();
    await store.initializeSession();
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => response(config, {}));
    request.defaults.adapter = adapter;
    await store.changePassword({
      oldPassword: "original",
      newPassword: "replacement",
      confirmPassword: "replacement",
    });
    const config = adapter.mock.calls[0][0];
    expect(config.url).toBe("/api/auth/change-password");
    expect(JSON.parse(config.data)).toEqual({
      oldPassword: "original",
      newPassword: "replacement",
      confirmPassword: "replacement",
    });
    expect(store.isAuthenticated).toBe(false);
    expect(disconnectWebSocket).toHaveBeenCalled();
    expect(router.push).toHaveBeenCalledWith("/login");
  });

  it("rejects failed password changes and retains the current identity", async () => {
    const store = useUserStore();
    await store.initializeSession();
    request.defaults.adapter = async (config) => {
      throw new AxiosError("wrong password", "ERR_BAD_REQUEST", config, undefined, {
        ...response(config, {}),
        status: 400,
      });
    };
    await expect(
      store.changePassword({
        oldPassword: "wrong",
        newPassword: "replacement",
        confirmPassword: "replacement",
      })
    ).rejects.toBeInstanceOf(AxiosError);
    expect(store.isAuthenticated).toBe(true);
  });

  it("recovers valid current without cache and shares startup work", async () => {
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) =>
      response(config, config.url === "/api/auth/current" ? guest : { unreadCount: 0 })
    );
    request.defaults.adapter = adapter;
    const store = useUserStore();
    expect(store.isAuthenticated).toBe(false);
    await Promise.all([store.initializeSession(), store.initializeSession()]);
    expect(
      adapter.mock.calls.filter(([config]) => config.url === "/api/auth/current")
    ).toHaveLength(1);
    expect(store.userInfo?.id).toBe(7);
    expect(store.isAuthenticated).toBe(true);
    expect(initWebSocket).toHaveBeenCalledWith(7);
  });

  it("ignores corrupt cache and clears it after startup 401 without logout request", async () => {
    localStorage.setItem("homestay_user", "{broken");
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => {
      throw unauthorized(config);
    });
    request.defaults.adapter = adapter;
    const store = useUserStore();
    await store.initializeSession();
    expect(store.initialized).toBe(true);
    expect(store.isAuthenticated).toBe(false);
    expect(localStorage.getItem("homestay_user")).toBeNull();
    expect(adapter).toHaveBeenCalledTimes(1);
    expect(router.push).not.toHaveBeenCalled();
  });

  it("private 401 clears Pinia, cache, chat, notifications and WS while preserving AxiosError", async () => {
    const store = useUserStore();
    await store.initializeSession();
    const chat = useChatStore();
    chat.chatDialogVisible = true;
    chat.unreadCount = 4;
    const notices = useNotificationStore();
    notices.setUnreadCount(3);
    localStorage.setItem("token", "obsolete");
    sessionStorage.setItem("homestay_token", "obsolete");
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => {
      throw unauthorized(config);
    });
    request.defaults.adapter = adapter;
    await expect(request.get("/api/orders/my")).rejects.toBeInstanceOf(AxiosError);
    expect(store.isAuthenticated).toBe(false);
    expect(store.token).toBeNull();
    expect(localStorage.getItem("homestay_user")).toBeNull();
    expect(sessionStorage.getItem("homestay_token")).toBeNull();
    expect(chat.chatDialogVisible).toBe(false);
    expect(chat.unreadCount).toBe(0);
    expect(notices.unreadCount).toBe(0);
    expect(disconnectWebSocket).toHaveBeenCalled();
    expect(adapter).toHaveBeenCalledTimes(1);
    expect(router.push).toHaveBeenCalledWith(expect.objectContaining({ path: "/login" }));
    expect(authManager.checkAccess(AccessLevel.AUTHENTICATED).hasAccess).toBe(false);
  });

  it("returns original current HTTP error and rejects stale cached roles on failure", async () => {
    const store = useUserStore();
    await store.initializeSession();
    store.userInfo!.role = "ROLE_HOST";
    request.defaults.adapter = async (config) => {
      throw unauthorized(config);
    };
    await expect(store.fetchUserInfo()).rejects.toMatchObject({ response: { status: 401 } });
    expect(await ensureUserLoggedIn(["HOST"])).toBe(false);
  });

  it("wrong password 401 stays on login and retains error status", async () => {
    request.defaults.adapter = async (config) => {
      throw unauthorized(config);
    };
    await expect(useUserStore().login("guest", "wrong")).rejects.toMatchObject({
      response: { status: 401 },
    });
    expect(router.push).not.toHaveBeenCalled();
  });

  it("startup network failure never trusts cached identity and performs no remote logout", async () => {
    localStorage.setItem("homestay_user", JSON.stringify(guest));
    const adapter = vi.fn(async () => {
      throw new AxiosError("offline", "ERR_NETWORK");
    });
    request.defaults.adapter = adapter;
    const store = useUserStore();
    await store.initializeSession();
    expect(store.isAuthenticated).toBe(false);
    expect(store.initialized).toBe(true);
    expect(adapter).toHaveBeenCalledTimes(1);
  });

  it("keeps a verified identity on a later network failure", async () => {
    const store = useUserStore();
    await store.initializeSession();
    request.defaults.adapter = async () => {
      throw new AxiosError("offline", "ERR_NETWORK");
    };
    await expect(store.fetchUserInfo()).rejects.toBeInstanceOf(AxiosError);
    expect(store.userInfo?.id).toBe(7);
    expect(store.isAuthenticated).toBe(true);
  });

  it("uses the replacement host token for WS after current confirms the role", async () => {
    const store = useUserStore();
    await store.initializeSession();
    store.setToken("replacement-host-token");
    request.defaults.adapter = async (config) =>
      response(
        config,
        config.url === "/api/auth/current" ? { ...guest, role: "ROLE_HOST" } : { unreadCount: 0 }
      );
    await store.fetchUserInfo();
    expect(store.isLandlord).toBe(true);
    expect(store.token).toBe("replacement-host-token");
    expect(setAuthToken).toHaveBeenLastCalledWith("replacement-host-token");
    expect(initWebSocket).toHaveBeenLastCalledWith(7);
  });

  it("late current response cannot restore an explicitly cleared session", async () => {
    let resolveCurrent!: (res: AxiosResponse) => void;
    request.defaults.adapter = (config) =>
      new Promise((resolve) => {
        resolveCurrent = () => resolve(response(config, guest));
      });
    const store = useUserStore();
    const pending = store.fetchUserInfo();
    await vi.waitFor(() => expect(resolveCurrent).toBeTypeOf("function"));
    store.clearSession();
    resolveCurrent({} as AxiosResponse);
    await pending;
    expect(store.isAuthenticated).toBe(false);
  });
});

describe("server verified role checks", () => {
  it("never grants guest HOST on the development create page", async () => {
    const store = useUserStore();
    expect(await ensureUserLoggedIn(["ROLE_HOST", "LANDLORD"])).toBe(false);
    expect(store.userInfo?.role).toBe("ROLE_USER");
    expect(JSON.parse(localStorage.getItem("homestay_user")!).role).toBe("ROLE_USER");
  });
  it("matches the legacy landlord name using the same store role rules", async () => {
    request.defaults.adapter = async (config) =>
      response(
        config,
        config.url === "/api/auth/current" ? { ...guest, role: "landlord" } : { unreadCount: 0 }
      );
    expect(await ensureUserLoggedIn(["ROLE_HOST"])).toBe(true);
    expect(useUserStore().isLandlord).toBe(true);
    expect(authManager.checkAccess(AccessLevel.HOST).hasAccess).toBe(true);
  });
});
