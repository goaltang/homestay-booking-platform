import { ref } from "vue";
import type {
  SearchFilters,
  MapViewState,
  SearchOrigin,
  CurrentSearchContext,
  NormalSearchSnapshot,
  LandmarkCandidate,
} from "@/composables/useMapSearch";

type SearchMode = "normal" | "nearby" | "landmark";

export function useMapSearchState() {
  // 搜索模式状态机核心状态
  const searchMode = ref<SearchMode>("normal");
  const nearbyRadius = ref<number>(5);
  const currentSearchContext = ref<CurrentSearchContext>({
    mode: "normal",
    filters: {},
    viewportSearchEnabled: true,
    mapView: {},
    nearbyRadius: 5,
    nearbyOrigin: null,
    landmark: null,
    selectedHomestayId: null,
    hoveredHomestayId: null,
  });
  const normalSearchSnapshot = ref<NormalSearchSnapshot | null>(null);

  // 与搜索模式共享的状态（原 useMapSearch.ts 中也会被地图操作使用）
  const filters = ref<SearchFilters>({});
  const viewportSearchEnabled = ref(true);
  const mapView = ref<MapViewState>({});
  const nearbySearchOrigin = ref<SearchOrigin | null>(null);
  const activeLandmark = ref<SearchOrigin | null>(null);
  const selectedHomestayId = ref<number | null>(null);
  const hoveredHomestayId = ref<number | null>(null);
  const useClusterMode = ref(false);
  const locationError = ref<string | null>(null);
  const userLocation = ref<SearchOrigin | null>(null);

  const cloneFilters = (value: SearchFilters = {}) => {
    const { northEastLat, northEastLng, southWestLat, southWestLng, ...rest } = value;
    return { ...rest };
  };

  const cloneMapView = (value: MapViewState = {}) => ({ ...value });

  const syncSearchContext = () => {
    currentSearchContext.value = {
      mode: searchMode.value,
      filters: cloneFilters(filters.value),
      viewportSearchEnabled: viewportSearchEnabled.value,
      mapView: cloneMapView(mapView.value),
      nearbyRadius: nearbyRadius.value,
      nearbyOrigin: nearbySearchOrigin.value ? { ...nearbySearchOrigin.value } : null,
      landmark: activeLandmark.value ? { ...activeLandmark.value } : null,
      selectedHomestayId: selectedHomestayId.value,
      hoveredHomestayId: hoveredHomestayId.value,
    };
  };

  const rememberNormalSearchSnapshot = () => {
    normalSearchSnapshot.value = {
      filters: cloneFilters(filters.value),
      viewportSearchEnabled: viewportSearchEnabled.value,
      mapView: cloneMapView(mapView.value),
    };
  };

  const getSearchAreaContext = () => {
    if (searchMode.value === "nearby" && nearbySearchOrigin.value) {
      return {
        center: nearbySearchOrigin.value,
        radiusKm: nearbyRadius.value,
      };
    }

    if (searchMode.value === "landmark" && activeLandmark.value) {
      return {
        center: activeLandmark.value,
        radiusKm: nearbyRadius.value,
      };
    }

    return null;
  };

  const clearSpecialSearchContext = (clearOverlays: () => void) => {
    nearbySearchOrigin.value = null;
    activeLandmark.value = null;
    locationError.value = null;
    clearOverlays();
    syncSearchContext();
  };

  const setLandmarkCandidate = (
    landmark: LandmarkCandidate | null,
    deps: {
      clearSearchContextOverlays: () => void;
      updateSearchContextOverlays: (options?: { fitView?: boolean }) => void;
    }
  ) => {
    if (!landmark) {
      activeLandmark.value = null;
      deps.clearSearchContextOverlays();
      syncSearchContext();
      return;
    }

    activeLandmark.value = {
      latitude: landmark.latitude,
      longitude: landmark.longitude,
      name: landmark.name,
      source: "landmark",
    };
    searchMode.value = "landmark";
    nearbySearchOrigin.value = null;
    useClusterMode.value = false;
    syncSearchContext();
    deps.updateSearchContextOverlays();
  };

  const resetSearchMode = async (
    options?: { reload?: boolean; filters?: SearchFilters; fitView?: boolean },
    deps?: {
      loadHomestays: (options?: { fitView?: boolean }) => Promise<void>;
      clearSpecialSearchContext: () => void;
      restoreNormalSearchSnapshot: () => boolean;
      syncMapViewState: () => void;
    }
  ) => {
    searchMode.value = "normal";
    deps?.clearSpecialSearchContext();
    if (options?.filters) {
      filters.value = cloneFilters(options.filters);
    } else {
      deps?.restoreNormalSearchSnapshot();
    }
    syncSearchContext();
    if (!options?.filters) {
      deps?.syncMapViewState();
    }
    if (options?.reload) {
      await deps?.loadHomestays({ fitView: options?.fitView ?? true });
    }
  };

  const runCurrentSearch = async (
    options?: { fitView?: boolean; filters?: SearchFilters },
    executors?: {
      searchNearby: (options?: { filters?: SearchFilters; fitView?: boolean }) => Promise<void>;
      searchByLandmark: (
        landmark: { latitude: number; longitude: number; name?: string },
        options?: { radius?: number; filters?: SearchFilters; fitView?: boolean }
      ) => Promise<void>;
      loadHomestays: (options?: { fitView?: boolean }) => Promise<void>;
      applyViewportBounds: (baseFilters: SearchFilters) => SearchFilters;
    }
  ) => {
    const nextFilters = options?.filters ?? filters.value;
    filters.value = cloneFilters(nextFilters);
    syncSearchContext();

    if (searchMode.value === "nearby") {
      await executors?.searchNearby({ filters: nextFilters, fitView: options?.fitView });
      return;
    }

    if (searchMode.value === "landmark" && activeLandmark.value) {
      await executors?.searchByLandmark(activeLandmark.value, {
        filters: nextFilters,
        fitView: options?.fitView,
      });
      return;
    }

    searchMode.value = "normal";
    filters.value =
      executors?.applyViewportBounds(cloneFilters(nextFilters)) ?? cloneFilters(nextFilters);
    rememberNormalSearchSnapshot();
    syncSearchContext();
    await executors?.loadHomestays({ fitView: options?.fitView ?? true });
  };

  const setSearchMode = async (
    mode: SearchMode,
    deps: {
      searchNearby: (options?: any) => Promise<void>;
      searchByLandmark: (landmark: any, options?: any) => Promise<void>;
      resetSearchMode: (options?: any) => Promise<void>;
      clearSearchContextOverlays: () => void;
    }
  ) => {
    useClusterMode.value = false;

    if (mode === "nearby") {
      await deps.searchNearby();
      return;
    }

    if (mode === "landmark") {
      if (activeLandmark.value) {
        await deps.searchByLandmark(activeLandmark.value, {
          radius: nearbyRadius.value,
          filters: filters.value,
        });
      } else {
        searchMode.value = "landmark";
        deps.clearSearchContextOverlays();
        syncSearchContext();
      }
      return;
    }

    if (mode === "normal") {
      await deps.resetSearchMode({ reload: true });
    }
  };

  return {
    searchMode,
    nearbyRadius,
    currentSearchContext,
    normalSearchSnapshot,
    filters,
    viewportSearchEnabled,
    mapView,
    nearbySearchOrigin,
    activeLandmark,
    selectedHomestayId,
    hoveredHomestayId,
    useClusterMode,
    locationError,
    userLocation,
    syncSearchContext,
    rememberNormalSearchSnapshot,
    getSearchAreaContext,
    clearSpecialSearchContext,
    setLandmarkCandidate,
    resetSearchMode,
    runCurrentSearch,
    setSearchMode,
  };
}
