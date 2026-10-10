import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mount } from "@vue/test-utils";
import { useMap } from "../useMap";
import * as mapService from "@/utils/mapService";
import LocationInfo from "@/components/homestay/LocationInfo.vue";
import type { HomestayDetail } from "@/types/homestay";

vi.mock("@/utils/request", () => ({ default: { get: vi.fn() } }));

const homestay = {
  provinceCode: "520000",
  cityCode: "520100",
  addressDetail: "测试地址",
} as HomestayDetail;

describe("房源地图失败处理", () => {
  beforeEach(() => {
    vi.spyOn(console, "log").mockImplementation(() => {});
    vi.spyOn(console, "warn").mockImplementation(() => {});
    vi.spyOn(console, "error").mockImplementation(() => {});
    vi.spyOn(mapService, "geocodeAddress").mockResolvedValue({
      lat: 26.6,
      lng: 106.7,
      formattedAddress: "测试地址",
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("周边查询网络失败后保留真实结果并展示错误，成功重试清除错误", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        status: "1",
        pois: [{ name: "真实设施", distance: "100", address: "测试地址" }],
      }),
    });
    vi.stubGlobal("fetch", fetchMock);
    const state = useMap();
    await state.initializeMap(homestay);
    const original = state.nearbyPlaces.value;
    expect(original).toHaveLength(3);

    fetchMock.mockRejectedValue(new Error("offline"));
    await state.initializeMap(homestay);
    expect(state.nearbyPlaces.value).toEqual(original);
    expect(state.nearbyError.value).toBeTruthy();
    const wrapper = mount(LocationInfo, {
      props: {
        formattedLocation: "贵阳",
        nearbyPlaces: state.nearbyPlaces.value,
        nearbyError: state.nearbyError.value,
      },
    });
    expect(wrapper.text()).toContain("真实设施");
    expect(wrapper.text()).toContain("周边设施加载失败");
    wrapper.unmount();

    fetchMock.mockResolvedValue({ ok: true, json: async () => ({ status: "1", pois: [] }) });
    await state.initializeMap(homestay);
    expect(state.nearbyPlaces.value).toEqual([]);
    expect(state.nearbyError.value).toBe("");
  });

  it.each([
    { ok: false, status: 503, json: async () => ({}) },
    { ok: true, json: async () => ({ status: "0", info: "INVALID_USER_KEY" }) },
  ])("HTTP 或高德业务失败时拒绝返回成功空结果", async (response) => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response));
    await expect(mapService.searchNearbyPlaces(26.6, 106.7)).rejects.toThrow();
  });

  it("首次定位失败时不生成城市或北京坐标，页面显示失败", async () => {
    vi.mocked(mapService.geocodeAddress).mockResolvedValue(null);
    const state = useMap();
    await state.initializeMap(homestay);
    expect(state.mapData.value.hasLocation).toBe(false);
    expect(state.mapData.value.staticMapUrl).toBe("");
    expect(state.mapData.value.isLoading).toBe(false);
    const wrapper = mount(LocationInfo, {
      props: { formattedLocation: "贵阳", nearbyPlaces: [] },
    });
    expect(wrapper.text()).toContain("地图加载失败");
    expect(wrapper.find("img").exists()).toBe(false);
    wrapper.unmount();
  });

  it("重复定位失败保留已有地图，图片失败及重置清除相关状态", async () => {
    vi.spyOn(mapService, "searchNearbyPlaces").mockResolvedValue([]);
    const state = useMap();
    await state.initializeMap(homestay);
    const originalUrl = state.mapData.value.staticMapUrl;
    vi.mocked(mapService.geocodeAddress).mockRejectedValue(new Error("offline"));
    await state.initializeMap(homestay);
    expect(state.mapData.value.staticMapUrl).toBe(originalUrl);
    expect(state.mapData.value.hasLocation).toBe(true);
    state.onMapImageError();
    expect(state.mapData.value.hasLocation).toBe(false);
    expect(state.mapData.value.staticMapUrl).toBe("");
    state.nearbyError.value = "失败";
    state.resetMap();
    expect(state.nearbyError.value).toBe("");
    expect(state.nearbyPlaces.value).toEqual([]);
  });
});
