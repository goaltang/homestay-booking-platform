import { describe, expect, it, vi } from "vitest";

const load = vi.fn();
vi.mock("@amap/amap-jsapi-loader", () => ({ default: { load } }));
vi.mock("@/utils/amapConfig", () => ({
  AMAP_CONFIG: { apiKey: "k", version: "2.0", plugins: [] },
  applyAmapSecurityConfig: vi.fn(),
}));

const { ensureAMapLoaded } = await import("@/utils/amapLoader");

describe("ensureAMapLoaded", () => {
  it("加载失败后再次调用会重新加载，成功后复用同一结果", async () => {
    const AMap = { Map: class {} };
    load.mockRejectedValueOnce(new Error("network")).mockResolvedValue(AMap);

    await expect(ensureAMapLoaded()).rejects.toThrow("network");
    await expect(ensureAMapLoaded()).resolves.toBe(AMap);
    await expect(ensureAMapLoaded()).resolves.toBe(AMap);

    expect(load).toHaveBeenCalledTimes(2);
  });
});
