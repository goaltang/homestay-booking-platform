import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// 最小化的高德地图替身：记录事件处理器，程序移动时同步派发事件（与 immediately=true 行为一致）
type Handler = () => void;

class FakeEmitter {
  handlers: Record<string, Handler[]> = {};
  on(event: string, handler: Handler) {
    (this.handlers[event] ||= []).push(handler);
  }
  emit(event: string) {
    (this.handlers[event] || []).forEach((handler) => handler());
  }
}

class FakeMap extends FakeEmitter {
  center: [number, number];
  zoom: number;
  added: unknown[] = [];
  removed: unknown[] = [];
  constructor(_container: unknown, options: { center: [number, number]; zoom: number }) {
    super();
    this.center = options.center;
    this.zoom = options.zoom;
    fakeMaps.push(this);
  }
  getCenter() {
    return { getLat: () => this.center[1], getLng: () => this.center[0] };
  }
  getZoom() {
    return this.zoom;
  }
  getBounds() {
    const [lng, lat] = this.center;
    return {
      getNorthEast: () => ({ getLat: () => lat + 0.1, getLng: () => lng + 0.1 }),
      getSouthWest: () => ({ getLat: () => lat - 0.1, getLng: () => lng - 0.1 }),
    };
  }
  setCenter(center: [number, number]) {
    this.center = center;
    this.emit("moveend");
  }
  setZoom(zoom: number) {
    this.zoom = zoom;
    this.emit("zoomend");
    this.emit("moveend");
  }
  setZoomAndCenter(zoom: number, center: [number, number]) {
    this.zoom = zoom;
    this.center = center;
    this.emit("zoomend");
    this.emit("moveend");
  }
  setFitView() {
    this.emit("moveend");
  }
  lngLatToContainer() {
    return { x: 0, y: 0 };
  }
  add(items: unknown) {
    this.added.push(...(Array.isArray(items) ? items : [items]));
  }
  remove(items: unknown) {
    this.removed.push(...(Array.isArray(items) ? items : [items]));
  }
  destroy() {}
  /** 模拟用户拖动地图 */
  userDragTo(center: [number, number]) {
    this.center = center;
    this.emit("moveend");
  }
}

class FakeMarker extends FakeEmitter {
  options: Record<string, unknown>;
  constructor(options: Record<string, unknown>) {
    super();
    this.options = options;
  }
  getPosition() {
    return this.options.position;
  }
  getExtData() {
    return this.options.extData as { count?: number } | undefined;
  }
  getContent() {
    return this.options.content;
  }
  setOffset(offset: unknown) {
    this.options.offset = offset;
  }
}

class FakeInfoWindow extends FakeEmitter {
  content: unknown = null;
  setContent(content: unknown) {
    this.content = content;
  }
  open() {}
  close() {}
}

const fakeMaps: FakeMap[] = [];
const FakeAMap = {
  Map: FakeMap,
  Marker: FakeMarker,
  InfoWindow: FakeInfoWindow,
  Circle: class {},
  Pixel: class {
    constructor(
      public x: number,
      public y: number
    ) {}
  },
};

vi.mock("@/utils/amapLoader", () => ({
  ensureAMapLoaded: vi.fn(async () => {
    (window as unknown as { AMap: unknown }).AMap = FakeAMap;
    return FakeAMap;
  }),
}));

const listing = (id: number, coverImage = "https://images.unsplash.com/photo-1?w=800") => ({
  id,
  title: `房源${id}`,
  price: 100 * id,
  coverImage,
  latitude: 39.9 + id * 0.001,
  longitude: 116.4 + id * 0.001,
});

vi.mock("@/api/homestay/search", () => ({
  mapSearchHomestays: vi.fn(async () => ({ data: [listing(1), listing(2)] })),
  searchHomestays: vi.fn(async () => ({ data: { content: [] } })),
  getMapClusters: vi.fn(async () => ({
    data: [{ latitude: 39.9, longitude: 116.4, count: 7 }],
  })),
  getNearbyHomestays: vi.fn(async () => ({ data: [] })),
  landmarkSearchHomestays: vi.fn(async () => ({ data: [] })),
}));

const api = await import("@/api/homestay/search");
const { useMapSearch, MAP_RESULT_LIMIT } = await import("@/composables/useMapSearch");

const flush = async () => {
  for (let i = 0; i < 5; i++) {
    await Promise.resolve();
  }
};

const setup = async () => {
  const mapSearch = useMapSearch();
  await mapSearch.initMap(document.createElement("div"), {
    centerLat: 39.9,
    centerLng: 116.4,
    zoom: 12,
  });
  const map = fakeMaps[fakeMaps.length - 1];
  await mapSearch.runCurrentSearch({ fitView: false });
  vi.advanceTimersByTime(1000);
  vi.clearAllMocks();
  return { mapSearch, map };
};

describe("useMapSearch 地图交互", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    fakeMaps.length = 0;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("选中房源（程序居中）后，用户第一次拖动地图就会刷新视野结果", async () => {
    const { mapSearch, map } = await setup();

    mapSearch.selectHomestay(1, { source: "card" });
    expect(mapSearch.lastSelection.value).toEqual({ id: 1, source: "card" });
    vi.advanceTimersByTime(1000);

    map.userDragTo([116.6, 39.95]);
    vi.advanceTimersByTime(600);
    await flush();

    expect(api.mapSearchHomestays).toHaveBeenCalledTimes(1);
    expect(vi.mocked(api.mapSearchHomestays).mock.calls[0][0]).toMatchObject({
      northEastLng: expect.closeTo(116.7, 5),
    });
  });

  it("程序移动地图本身不触发视野搜索", async () => {
    const { mapSearch } = await setup();

    mapSearch.selectHomestay(2, { source: "marker" });
    vi.advanceTimersByTime(600);
    await flush();

    expect(api.mapSearchHomestays).not.toHaveBeenCalled();
    expect(mapSearch.lastSelection.value).toEqual({ id: 2, source: "marker" });
  });

  it("关闭聚合时立即移除聚合点，并按当前视野搜索而不是全量搜索", async () => {
    const { mapSearch, map } = await setup();

    await mapSearch.setClusterMode(true);
    expect(api.getMapClusters).toHaveBeenCalledTimes(1);
    expect(mapSearch.homestays.value).toEqual([]);
    expect(mapSearch.clusterTotal()).toBe(7);
    const clusterMarker = map.added.find(
      (item) => (item as FakeMarker).getExtData?.()?.count === 7
    );
    expect(clusterMarker).toBeTruthy();

    await mapSearch.setClusterMode(false);
    await flush();

    expect(map.removed).toContain(clusterMarker);
    expect(mapSearch.clusters.value).toEqual([]);
    expect(api.searchHomestays).not.toHaveBeenCalled();
    expect(api.mapSearchHomestays).toHaveBeenCalledTimes(1);
    expect(mapSearch.homestays.value).toHaveLength(2);
  });

  it("弹窗图片与卡片使用同一地址，不替换成随机图", async () => {
    const { mapSearch } = await setup();
    const setContent = vi.spyOn(FakeInfoWindow.prototype, "setContent");

    mapSearch.selectHomestay(1);

    const content = setContent.mock.calls[0][0] as HTMLElement;
    expect(content.querySelector("img")?.getAttribute("src")).toBe(
      "https://images.unsplash.com/photo-1?w=800"
    );
  });

  it("结果达到单次上限时标记为不完整", async () => {
    const { mapSearch, map } = await setup();
    vi.mocked(api.mapSearchHomestays).mockResolvedValueOnce({
      data: Array.from({ length: MAP_RESULT_LIMIT }, (_, index) => listing(index + 1)),
    } as never);

    map.userDragTo([116.5, 39.9]);
    vi.advanceTimersByTime(600);
    await flush();

    expect(mapSearch.isResultTruncated.value).toBe(true);
  });
});
