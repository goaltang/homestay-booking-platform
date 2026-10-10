import type { Ref } from "vue";
import type { Router, RouteLocationNormalizedLoaded } from "vue-router";
import type { MapViewState } from "@/composables/useMapSearch";

export interface QuerySyncFormState {
  searchMode: "normal" | "nearby" | "landmark";
  hasResolvedLandmark: boolean;
  selectedRegion: string[];
  minPrice?: number;
  maxPrice?: number;
  guestCount?: number;
  checkInDate?: string;
  checkOutDate?: string;
  nearbyRadius: number;
  landmarkLat?: number;
  landmarkLng?: number;
  resolvedLandmarkName: string;
  viewportSearchEnabled: boolean;
  mapView: MapViewState;
  globalSearchKeyword: string;
}

export interface HydratedQueryState {
  selectedRegion: string[];
  minPrice: number | undefined;
  maxPrice: number | undefined;
  guestCount: number | undefined;
  checkInDate: string | undefined;
  checkOutDate: string | undefined;
  dateRange: [string, string] | null;
  searchMode: "normal" | "nearby" | "landmark";
  nearbyRadius: number;
  landmarkKeyword: string;
  landmarkLat: number | undefined;
  landmarkLng: number | undefined;
  landmarkSuggestions: any[];
  lastLandmarkSuggestionKeyword: string;
  isLandmarkDirty: boolean;
  viewportSearchEnabled: boolean;
  resolvedLandmark: {
    id: string;
    name: string;
    address: string;
    latitude: number;
    longitude: number;
    secondaryText: string;
  } | null;
  globalSearchKeyword: string;
}

export function useMapSearchQuerySync(options: {
  router: Router;
  route: RouteLocationNormalizedLoaded;
  skipNextRouteReplay: Ref<boolean>;
}) {
  const { router, route, skipNextRouteReplay } = options;

  const getQueryString = (value: unknown): string | undefined => {
    return typeof value === "string" && value.trim() ? value : undefined;
  };

  const getQueryNumber = (value: unknown): number | undefined => {
    const rawValue = getQueryString(value);
    if (!rawValue) return undefined;

    const parsed = Number(rawValue);
    return Number.isFinite(parsed) ? parsed : undefined;
  };

  const getQuerySignature = (query: Record<string, string>) => {
    return JSON.stringify(Object.entries(query).sort(([keyA], [keyB]) => keyA.localeCompare(keyB)));
  };

  const getTrackedRouteQuery = () => {
    const nextQuery: Record<string, string> = {};

    const region = getQueryString(route.query.region);
    const minPriceQuery = getQueryString(route.query.minPrice);
    const maxPriceQuery = getQueryString(route.query.maxPrice);
    const guestCountQuery = getQueryString(route.query.guestCount);
    const checkInQuery = getQueryString(route.query.checkIn);
    const checkOutQuery = getQueryString(route.query.checkOut);
    const modeQuery = getQueryString(route.query.mode);
    const radiusQuery = getQueryString(route.query.radiusKm);
    const landmarkQuery = getQueryString(route.query.landmark);
    const landmarkLatQuery = getQueryString(route.query.landmarkLat);
    const landmarkLngQuery = getQueryString(route.query.landmarkLng);
    const viewportOnlyQuery = getQueryString(route.query.viewportOnly);
    const mapLatQuery = getQueryString(route.query.mapLat);
    const mapLngQuery = getQueryString(route.query.mapLng);
    const mapZoomQuery = getQueryString(route.query.mapZoom);
    const searchLabelQuery = getQueryString(route.query.q);

    if (region) nextQuery.region = region;
    if (minPriceQuery) nextQuery.minPrice = minPriceQuery;
    if (maxPriceQuery) nextQuery.maxPrice = maxPriceQuery;
    if (guestCountQuery) nextQuery.guestCount = guestCountQuery;
    if (checkInQuery) nextQuery.checkIn = checkInQuery;
    if (checkOutQuery) nextQuery.checkOut = checkOutQuery;
    if (modeQuery) nextQuery.mode = modeQuery;
    if (radiusQuery) nextQuery.radiusKm = radiusQuery;
    if (landmarkQuery) nextQuery.landmark = landmarkQuery;
    if (landmarkLatQuery) nextQuery.landmarkLat = landmarkLatQuery;
    if (landmarkLngQuery) nextQuery.landmarkLng = landmarkLngQuery;
    if (viewportOnlyQuery === "0") nextQuery.viewportOnly = viewportOnlyQuery;
    if (mapLatQuery) nextQuery.mapLat = mapLatQuery;
    if (mapLngQuery) nextQuery.mapLng = mapLngQuery;
    if (mapZoomQuery) nextQuery.mapZoom = mapZoomQuery;
    if (searchLabelQuery) nextQuery.q = searchLabelQuery;

    return nextQuery;
  };

  const buildQueryFromForm = (state: QuerySyncFormState) => {
    const nextQuery: Record<string, string> = {};
    const landmarkQueryReady = state.searchMode === "landmark" && state.hasResolvedLandmark;

    if (state.selectedRegion.length > 0) {
      nextQuery.region = state.selectedRegion.join(",");
    }
    if (state.minPrice !== undefined) {
      nextQuery.minPrice = String(state.minPrice);
    }
    if (state.maxPrice !== undefined) {
      nextQuery.maxPrice = String(state.maxPrice);
    }
    if (state.guestCount !== undefined) {
      nextQuery.guestCount = String(state.guestCount);
    }
    if (state.checkInDate) {
      nextQuery.checkIn = state.checkInDate;
    }
    if (state.checkOutDate) {
      nextQuery.checkOut = state.checkOutDate;
    }

    if (state.searchMode === "nearby" || landmarkQueryReady) {
      nextQuery.mode = state.searchMode;
      nextQuery.radiusKm = String(state.nearbyRadius);
    }

    if (landmarkQueryReady) {
      nextQuery.landmark = state.resolvedLandmarkName;
      nextQuery.landmarkLat = state.landmarkLat!.toFixed(6);
      nextQuery.landmarkLng = state.landmarkLng!.toFixed(6);
    }

    if (!state.viewportSearchEnabled) {
      nextQuery.viewportOnly = "0";
    }
    if (state.mapView.centerLat !== undefined) {
      nextQuery.mapLat = state.mapView.centerLat.toFixed(6);
    }
    if (state.mapView.centerLng !== undefined) {
      nextQuery.mapLng = state.mapView.centerLng.toFixed(6);
    }
    if (state.mapView.zoom !== undefined) {
      nextQuery.mapZoom = String(Math.round(state.mapView.zoom * 100) / 100);
    }
    if (state.globalSearchKeyword) {
      nextQuery.q = state.globalSearchKeyword;
    }

    return nextQuery;
  };

  const hydrateFiltersFromQuery = (): HydratedQueryState => {
    const regionQuery = getQueryString(route.query.region);
    const selectedRegion = regionQuery ? regionQuery.split(",").filter(Boolean) : [];
    const minPrice = getQueryNumber(route.query.minPrice);
    const maxPrice = getQueryNumber(route.query.maxPrice);
    const guestCount = getQueryNumber(route.query.guestCount);
    const checkInDate = getQueryString(route.query.checkIn);
    const checkOutDate = getQueryString(route.query.checkOut);
    const dateRange =
      checkInDate && checkOutDate ? ([checkInDate, checkOutDate] as [string, string]) : null;

    const modeQuery = getQueryString(route.query.mode);
    const searchMode = modeQuery === "nearby" || modeQuery === "landmark" ? modeQuery : "normal";
    const nearbyRadius = getQueryNumber(route.query.radiusKm) ?? 5;

    const queryLandmarkName = getQueryString(route.query.landmark) ?? "";
    const queryLandmarkLat = getQueryNumber(route.query.landmarkLat);
    const queryLandmarkLng = getQueryNumber(route.query.landmarkLng);
    const querySearchLabel = getQueryString(route.query.q);

    const landmarkKeyword = queryLandmarkName;
    const landmarkLat = queryLandmarkLat;
    const landmarkLng = queryLandmarkLng;
    const landmarkSuggestions: any[] = [];
    const lastLandmarkSuggestionKeyword = "";
    const isLandmarkDirty = false;
    const viewportSearchEnabled = getQueryString(route.query.viewportOnly) !== "0";

    let resolvedLandmark: HydratedQueryState["resolvedLandmark"] = null;
    let globalSearchKeyword: string;

    if (queryLandmarkName && queryLandmarkLat !== undefined && queryLandmarkLng !== undefined) {
      resolvedLandmark = {
        id: `query-${queryLandmarkName}-${queryLandmarkLat}-${queryLandmarkLng}`,
        name: queryLandmarkName,
        address: "",
        latitude: queryLandmarkLat,
        longitude: queryLandmarkLng,
        secondaryText: "",
      };
      globalSearchKeyword = querySearchLabel ?? queryLandmarkName;
    } else if (searchMode === "nearby") {
      globalSearchKeyword = querySearchLabel ?? "我的当前位置";
    } else {
      globalSearchKeyword = querySearchLabel ?? "";
    }

    return {
      selectedRegion,
      minPrice,
      maxPrice,
      guestCount,
      checkInDate,
      checkOutDate,
      dateRange,
      searchMode,
      nearbyRadius,
      landmarkKeyword,
      landmarkLat,
      landmarkLng,
      landmarkSuggestions,
      lastLandmarkSuggestionKeyword,
      isLandmarkDirty,
      viewportSearchEnabled,
      resolvedLandmark,
      globalSearchKeyword,
    };
  };

  const getMapViewFromQuery = (): MapViewState | undefined => {
    const centerLat = getQueryNumber(route.query.mapLat);
    const centerLng = getQueryNumber(route.query.mapLng);
    const zoom = getQueryNumber(route.query.mapZoom);

    if (centerLat === undefined && centerLng === undefined && zoom === undefined) {
      return undefined;
    }

    return {
      centerLat,
      centerLng,
      zoom,
    };
  };

  const syncQueryFromForm = async (
    state: QuerySyncFormState,
    mode: "push" | "replace" = "replace",
    options2: { skipRouteReplay?: boolean } = {}
  ): Promise<boolean> => {
    const nextQuery = buildQueryFromForm(state);
    if (getQuerySignature(nextQuery) === getQuerySignature(getTrackedRouteQuery())) {
      return false;
    }

    if (options2.skipRouteReplay) {
      skipNextRouteReplay.value = true;
    }

    await router[mode]({
      path: route.path,
      query: nextQuery,
    });

    return true;
  };

  return {
    getQueryString,
    getQueryNumber,
    getQuerySignature,
    getTrackedRouteQuery,
    buildQueryFromForm,
    hydrateFiltersFromQuery,
    getMapViewFromQuery,
    syncQueryFromForm,
  };
}
