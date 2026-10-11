import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { AxiosError } from "axios";
import type { AxiosResponse, InternalAxiosRequestConfig } from "axios";
import request from "@/utils/request";
import router from "@/router";
import { useUserStore } from "@/stores/user";

vi.mock("@/views/Home.vue", () => ({ default: { template: "<div />" } }));
vi.mock("@/views/order/OrderSubmitSuccess.vue", () => ({ default: { template: "<div />" } }));
vi.mock("@/views/order/MyOrders.vue", () => ({ default: { template: "<div />" } }));
vi.mock("@/layouts/UserLayout.vue", () => ({ default: { template: "<div />" } }));
vi.mock("@/views/host/HostLayout.vue", () => ({ default: { template: "<div />" } }));
vi.mock("@/views/Login.vue", () => ({ default: { template: "<div />" } }));
vi.mock("@/views/user/Profile.vue", () => ({ default: { template: "<div />" } }));
vi.mock("@/services/websocketService", () => ({
  initWebSocket: vi.fn(),
  disconnectWebSocket: vi.fn(),
  setAuthToken: vi.fn(),
}));
vi.mock("element-plus", () => ({ ElMessage: { error: vi.fn() } }));

const guest = { id: 7, username: "guest", email: "guest@example.test", role: "ROLE_USER" };
const response = (config: InternalAxiosRequestConfig, data: unknown): AxiosResponse => ({
  config,
  data,
  status: 200,
  statusText: "OK",
  headers: {},
});

beforeEach(() => {
  setActivePinia(createPinia());
  request.defaults.adapter = async (config) =>
    response(config, config.url === "/api/auth/current" ? guest : { unreadCount: 0 });
});

describe("initial navigation authentication", () => {
  it("waits for current before admitting protected route without a user cache", async () => {
    let resolveCurrent!: () => void;
    request.defaults.adapter = async (config) => {
      if (config.url !== "/api/auth/current") return response(config, { unreadCount: 0 });
      return new Promise((resolve) => {
        resolveCurrent = () => resolve(response(config, guest));
      });
    };
    const store = useUserStore();
    const navigation = router.push("/profile");
    await vi.waitFor(() => expect(resolveCurrent).toBeTypeOf("function"));
    expect(store.isAuthenticated).toBe(false);
    expect(router.currentRoute.value.path).not.toBe("/profile");
    resolveCurrent();
    await navigation;
    expect(router.currentRoute.value.path).toBe("/profile");
    expect(store.isAuthenticated).toBe(true);
  });

  it("startup 401 goes to login once even with a stale HOST cache", async () => {
    localStorage.setItem("homestay_user", JSON.stringify({ ...guest, role: "ROLE_HOST" }));
    request.defaults.adapter = async (config) => {
      throw new AxiosError("unauthorized", "ERR_BAD_REQUEST", config, undefined, {
        ...response(config, {}),
        status: 401,
      });
    };
    await router.push("/host/homestay/create");
    expect(router.currentRoute.value.path).toBe("/login");
    expect(router.currentRoute.value.query.redirect).toBe("/host/homestay/create");
    expect(useUserStore().isAuthenticated).toBe(false);
  });
});
