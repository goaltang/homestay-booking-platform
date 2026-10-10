import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { defineComponent, nextTick, ref, type Ref } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";

interface SearchOrigin {
  latitude: number;
  longitude: number;
  source: "map-center" | "user" | "landmark";
  accuracy?: number;
  name?: string;
}

interface MapSearchMock {
  homestays: Ref<Array<Record<string, unknown>>>;
  selectedHomestayId: Ref<number | null>;
  hoveredHomestayId: Ref<number | null>;
  viewportSearchEnabled: Ref<boolean>;
  useClusterMode: Ref<boolean>;
  searchMode: Ref<"normal" | "nearby" | "landmark">;
  nearbyRadius: Ref<number>;
  userLocation: Ref<SearchOrigin | null>;
  activeLandmark: Ref<SearchOrigin | null>;
  isLocating: Ref<boolean>;
  locationError: Ref<string | null>;
  isLoading: Ref<boolean>;
  isMapReady: Ref<boolean>;
  mapError: Ref<string | null>;
  searchError: Ref<string | null>;
  mapView: Ref<{ centerLat?: number; centerLng?: number; zoom?: number }>;
  currentSearchContext: Ref<{
    mode: "normal" | "nearby" | "landmark";
    filters: Record<string, unknown>;
    viewportSearchEnabled: boolean;
    mapView: { centerLat?: number; centerLng?: number; zoom?: number };
    nearbyRadius: number;
    nearbyOrigin: SearchOrigin | null;
    landmark: SearchOrigin | null;
    selectedHomestayId: number | null;
    hoveredHomestayId: number | null;
  }>;
  initMap: ReturnType<typeof vi.fn>;
  applySearchState: ReturnType<typeof vi.fn>;
  retrySearch: ReturnType<typeof vi.fn>;
  setMapView: ReturnType<typeof vi.fn>;
  selectHomestay: ReturnType<typeof vi.fn>;
  hoverHomestay: ReturnType<typeof vi.fn>;
  destroyMap: ReturnType<typeof vi.fn>;
  loadClusters: ReturnType<typeof vi.fn>;
  loadHomestays: ReturnType<typeof vi.fn>;
  locateUser: ReturnType<typeof vi.fn>;
  searchNearby: ReturnType<typeof vi.fn>;
  searchByLandmark: ReturnType<typeof vi.fn>;
  resetSearchMode: ReturnType<typeof vi.fn>;
  setOnViewDetail: ReturnType<typeof vi.fn>;
  rememberNormalSearchSnapshot: ReturnType<typeof vi.fn>;
}

type CurrentSearchContext = MapSearchMock["currentSearchContext"]["value"];

const elMessageError = vi.fn();
const elMessageWarning = vi.fn();

vi.mock("element-plus", () => ({
  ElMessage: {
    error: elMessageError,
    warning: elMessageWarning,
  },
}));

const geocodeAddressMock = vi.fn();
const poiSuggestionsMock = vi.fn();

vi.mock("@/utils/mapService", () => ({
  geocodeAddress: geocodeAddressMock,
  searchAmapPoiSuggestions: poiSuggestionsMock,
}));

let currentMapSearchMock: MapSearchMock;

vi.mock("@/composables/useMapSearch", () => ({
  useMapSearch: () => currentMapSearchMock,
}));

const MapSearch = (await import("@/views/MapSearch.vue")).default;

const TestRouteComponent = defineComponent({
  components: { MapSearch },
  template: "<MapSearch />",
});

const createMapSearchMock = (): MapSearchMock => {
  const homestays = ref<Array<Record<string, unknown>>>([]);
  const selectedHomestayId = ref<number | null>(null);
  const hoveredHomestayId = ref<number | null>(null);
  const viewportSearchEnabled = ref(true);
  const useClusterMode = ref(false);
  const searchMode = ref<"normal" | "nearby" | "landmark">("normal");
  const nearbyRadius = ref(5);
  const userLocation = ref<SearchOrigin | null>(null);
  const activeLandmark = ref<SearchOrigin | null>(null);
  const isLocating = ref(false);
  const locationError = ref<string | null>(null);
  const isLoading = ref(false);
  const isMapReady = ref(false);
  const mapError = ref<string | null>(null);
  const searchError = ref<string | null>(null);
  const mapView = ref<{ centerLat?: number; centerLng?: number; zoom?: number }>({
    centerLat: 39.90923,
    centerLng: 116.397428,
    zoom: 12,
  });
  const currentSearchContext = ref<CurrentSearchContext>({
    mode: "normal",
    filters: {},
    viewportSearchEnabled: true,
    mapView: mapView.value,
    nearbyRadius: 5,
    nearbyOrigin: null,
    landmark: null,
    selectedHomestayId: null,
    hoveredHomestayId: null,
  });

  const initMap = vi.fn(async () => {
    isMapReady.value = true;
  });

  const applySearchState = vi.fn(async () => {});
  const retrySearch = vi.fn(async () => {});
  const setMapView = vi.fn(
    (nextMapView: { centerLat?: number; centerLng?: number; zoom?: number }) => {
      mapView.value = {
        ...mapView.value,
        ...nextMapView,
      };
      currentSearchContext.value = {
        ...currentSearchContext.value,
        mapView: mapView.value,
      };
    }
  );
  const selectHomestay = vi.fn((id: number) => {
    selectedHomestayId.value = id;
    currentSearchContext.value = {
      ...currentSearchContext.value,
      selectedHomestayId: id,
    };
  });
  const hoverHomestay = vi.fn((id: number | null) => {
    hoveredHomestayId.value = id;
    currentSearchContext.value = {
      ...currentSearchContext.value,
      hoveredHomestayId: id,
    };
  });
  const destroyMap = vi.fn();
  const loadClusters = vi.fn(async () => {});
  const loadHomestays = vi.fn(async () => {});
  const locateUser = vi.fn(async () => {
    const location: SearchOrigin = {
      latitude: 31.2304,
      longitude: 121.4737,
      accuracy: 18,
      source: "user",
    };
    userLocation.value = location;
    currentSearchContext.value = {
      ...currentSearchContext.value,
      mode: "nearby",
      nearbyOrigin: location,
    };
    mapView.value = {
      centerLat: location.latitude,
      centerLng: location.longitude,
      zoom: 14,
    };
    return location;
  });
  const searchNearby = vi.fn(
    async (options?: {
      latitude?: number;
      longitude?: number;
      radius?: number;
      source?: "map-center" | "user";
      fitView?: boolean;
      filters?: Record<string, unknown>;
    }) => {
      searchMode.value = "nearby";
      if (options?.radius !== undefined) {
        nearbyRadius.value = options.radius;
      }
      const nearbyOrigin =
        options?.latitude !== undefined && options?.longitude !== undefined
          ? {
              latitude: options.latitude,
              longitude: options.longitude,
              source: options.source ?? "map-center",
            }
          : (currentSearchContext.value.nearbyOrigin ?? userLocation.value);
      currentSearchContext.value = {
        ...currentSearchContext.value,
        mode: "nearby",
        nearbyRadius: nearbyRadius.value,
        nearbyOrigin: nearbyOrigin ?? null,
      };
    }
  );
  const searchByLandmark = vi.fn(
    async (
      landmark: SearchOrigin,
      options?: { radius?: number; filters?: Record<string, unknown>; fitView?: boolean }
    ) => {
      searchMode.value = "landmark";
      if (options?.radius !== undefined) {
        nearbyRadius.value = options.radius;
      }
      activeLandmark.value = landmark;
      currentSearchContext.value = {
        ...currentSearchContext.value,
        mode: "landmark",
        nearbyRadius: nearbyRadius.value,
        landmark,
      };
    }
  );
  const resetSearchMode = vi.fn(async () => {
    searchMode.value = "normal";
    activeLandmark.value = null;
    currentSearchContext.value = {
      ...currentSearchContext.value,
      mode: "normal",
      nearbyOrigin: null,
      landmark: null,
    };
  });
  const setOnViewDetail = vi.fn();
  const rememberNormalSearchSnapshot = vi.fn();

  return {
    homestays,
    selectedHomestayId,
    hoveredHomestayId,
    viewportSearchEnabled,
    useClusterMode,
    searchMode,
    nearbyRadius,
    userLocation,
    activeLandmark,
    isLocating,
    locationError,
    isLoading,
    isMapReady,
    mapError,
    searchError,
    mapView,
    currentSearchContext,
    initMap,
    applySearchState,
    retrySearch,
    setMapView,
    selectHomestay,
    hoverHomestay,
    destroyMap,
    loadClusters,
    loadHomestays,
    locateUser,
    searchNearby,
    searchByLandmark,
    resetSearchMode,
    setOnViewDetail,
    rememberNormalSearchSnapshot,
  };
};

const mountMapSearch = async (query: Record<string, string> = {}) => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: "/map-search",
        component: TestRouteComponent,
      },
    ],
  });

  await router.push({
    path: "/map-search",
    query,
  });
  await router.isReady();

  const wrapper = mount(TestRouteComponent, {
    global: {
      plugins: [router],
    },
  });

  await settle();

  return { wrapper, router };
};

const settle = async () => {
  await flushPromises();
  await nextTick();
  await flushPromises();
  await nextTick();
  await flushPromises();
};

const clickCurrentLocation = async (wrapper: VueWrapper) => {
  const locateButton = wrapper.find('[aria-label="定位到当前位置"]');

  expect(locateButton.exists(), "current location control should exist").toBe(true);
  await locateButton.trigger("click");
  await settle();
};

const waitForAutoSearch = async () => {
  await new Promise((resolve) => setTimeout(resolve, 550));
  await settle();
};

const findInputByPlaceholder = (wrapper: VueWrapper, placeholder: string) => {
  const input = wrapper
    .findAll("input")
    .find((candidate) => candidate.attributes("placeholder")?.includes(placeholder));

  expect(input, `input with placeholder ${placeholder} should exist`).toBeDefined();
  return input!;
};

beforeEach(() => {
  currentMapSearchMock = createMapSearchMock();
  geocodeAddressMock.mockReset();
  poiSuggestionsMock.mockReset();
  elMessageError.mockReset();
  elMessageWarning.mockReset();
});

describe("MapSearch", () => {
  it("supports normal search and syncs query params", async () => {
    const { wrapper, router } = await mountMapSearch();

    currentMapSearchMock.applySearchState.mockClear();
    currentMapSearchMock.resetSearchMode.mockClear();

    const guestCountInput = wrapper.findAll('input[type="number"]').at(0);
    expect(guestCountInput).toBeDefined();
    await guestCountInput!.setValue("4");
    await guestCountInput!.trigger("change");
    await waitForAutoSearch();

    expect(currentMapSearchMock.resetSearchMode).toHaveBeenCalledWith({
      filters: {
        minGuests: 4,
      },
    });

    expect(currentMapSearchMock.applySearchState).toHaveBeenLastCalledWith(
      {
        minGuests: 4,
      },
      {
        viewportOnly: true,
        fitView: false,
        skipCityCenter: true,
        mapView: {
          centerLat: 39.90923,
          centerLng: 116.397428,
          zoom: 12,
        },
      }
    );

    expect(router.currentRoute.value.query).toMatchObject({
      guestCount: "4",
      mapLat: "39.909230",
      mapLng: "116.397428",
      mapZoom: "12",
    });
  });

  it("supports current-location mode and syncs nearby query", async () => {
    const { wrapper, router } = await mountMapSearch();

    currentMapSearchMock.locateUser.mockClear();
    currentMapSearchMock.searchNearby.mockClear();

    await clickCurrentLocation(wrapper);

    expect(currentMapSearchMock.locateUser).toHaveBeenCalledWith({
      recenter: true,
    });

    expect(currentMapSearchMock.searchMode.value).toBe("nearby");
    expect(currentMapSearchMock.userLocation.value).toMatchObject({
      latitude: 31.2304,
      longitude: 121.4737,
      source: "user",
    });

    expect(currentMapSearchMock.searchNearby).toHaveBeenCalledWith({
      latitude: 31.2304,
      longitude: 121.4737,
      radius: 5,
      source: "user",
      filters: {},
      fitView: true,
    });

    expect(router.currentRoute.value.query).toMatchObject({
      mode: "nearby",
      radiusKm: "5",
      q: "我的当前位置",
      mapLat: "31.230400",
      mapLng: "121.473700",
      mapZoom: "14",
    });
  });

  it("supports landmark search and replays landmark query", async () => {
    poiSuggestionsMock.mockResolvedValue([
      {
        id: "poi-1",
        name: "科技园",
        latitude: 22.5431,
        longitude: 114.0579,
        district: "南山区",
        address: "科技园",
      },
    ]);

    const { wrapper, router } = await mountMapSearch();

    currentMapSearchMock.searchByLandmark.mockClear();

    const landmarkInput = findInputByPlaceholder(wrapper, "搜索目的地");
    await landmarkInput.setValue("科技园");
    await landmarkInput.trigger("keyup", { key: "Enter" });
    await settle();

    expect(poiSuggestionsMock).toHaveBeenCalledWith("科技园", {
      city: undefined,
      limit: 1,
    });
    expect(currentMapSearchMock.searchByLandmark).toHaveBeenLastCalledWith(
      {
        latitude: 22.5431,
        longitude: 114.0579,
        name: "科技园",
      },
      {
        radius: 5,
        filters: {},
        fitView: true,
      }
    );

    expect(router.currentRoute.value.query).toMatchObject({
      mode: "landmark",
      landmark: "科技园",
      landmarkLat: "22.543100",
      landmarkLng: "114.057900",
      radiusKm: "5",
      q: "科技园",
    });
  });

  it("replays query state after refresh", async () => {
    const { wrapper } = await mountMapSearch({
      mode: "landmark",
      landmark: "深圳湾公园",
      landmarkLat: "22.510000",
      landmarkLng: "113.950000",
      radiusKm: "8",
      guestCount: "2",
      viewportOnly: "0",
      mapLat: "22.520000",
      mapLng: "113.960000",
      mapZoom: "13.5",
    });

    expect(currentMapSearchMock.setMapView).toHaveBeenCalledWith({
      centerLat: 22.52,
      centerLng: 113.96,
      zoom: 13.5,
    });

    expect(currentMapSearchMock.searchByLandmark).toHaveBeenCalledWith(
      {
        latitude: 22.51,
        longitude: 113.95,
        name: "深圳湾公园",
      },
      {
        radius: 8,
        filters: {
          minGuests: 2,
        },
        fitView: false,
      }
    );

    const landmarkInput = findInputByPlaceholder(wrapper, "搜索目的地");
    expect((landmarkInput.element as HTMLInputElement).value).toBe("深圳湾公园");

    const viewportSwitch = wrapper.find('input[type="checkbox"]');
    expect((viewportSwitch.element as HTMLInputElement).checked).toBe(false);
  });

  it("replays nearby query state after refresh", async () => {
    await mountMapSearch({
      mode: "nearby",
      radiusKm: "7",
      nearbyLat: "31.230400",
      nearbyLng: "121.473700",
      nearbySource: "user",
      viewportOnly: "0",
      mapLat: "31.240000",
      mapLng: "121.480000",
      mapZoom: "13",
    });

    expect(currentMapSearchMock.searchNearby).toHaveBeenCalledWith({
      radius: 7,
      filters: {},
      fitView: false,
    });
  });

  it("reruns nearby search immediately when radius changes", async () => {
    const { wrapper, router } = await mountMapSearch({
      mode: "nearby",
      radiusKm: "5",
      nearbyLat: "31.230400",
      nearbyLng: "121.473700",
      nearbySource: "user",
      mapLat: "31.230400",
      mapLng: "121.473700",
      mapZoom: "14",
    });

    currentMapSearchMock.searchNearby.mockClear();

    const radiusSlider = wrapper.find('input[type="range"]');
    expect(radiusSlider.exists()).toBe(true);
    await radiusSlider.setValue("9");
    await radiusSlider.trigger("change");
    await settle();

    expect(currentMapSearchMock.searchNearby).toHaveBeenCalledWith({
      radius: 9,
      filters: {},
      fitView: false,
    });

    expect(router.currentRoute.value.query).toMatchObject({
      mode: "nearby",
      radiusKm: "9",
    });
  });

  it("supports exiting special search mode", async () => {
    const { wrapper, router } = await mountMapSearch({
      guestCount: "2",
    });

    currentMapSearchMock.locateUser.mockClear();
    await clickCurrentLocation(wrapper);

    expect(currentMapSearchMock.locateUser).toHaveBeenCalledWith({
      recenter: true,
    });

    const exitButton = wrapper
      .findAll("button")
      .find((candidate) => candidate.text().includes("退出"));
    expect(exitButton, "exit special search button should exist").toBeDefined();

    currentMapSearchMock.resetSearchMode.mockClear();

    await exitButton!.trigger("click");
    await settle();

    expect(currentMapSearchMock.resetSearchMode).toHaveBeenCalled();
    expect(currentMapSearchMock.searchMode.value).toBe("normal");
    expect(router.currentRoute.value.query).not.toHaveProperty("mode");
    expect(router.currentRoute.value.query).not.toHaveProperty("radiusKm");
    expect(router.currentRoute.value.query).not.toHaveProperty("q");
    expect(router.currentRoute.value.query).toMatchObject({
      guestCount: "2",
      mapLat: "31.230400",
      mapLng: "121.473700",
      mapZoom: "14",
    });
  });
});
