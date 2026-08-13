/**
 * 地图找房核心组合式函数
 * 封装：
 * - 高德地图实例的初始化和生命周期管理
 * - 房源数据加载（调用现有搜索 API）
 * - 地图标记（Marker）的创建和管理
 * - 地图视口变化时的搜索触发（防抖）
 * - 卡片与地图互相联动的状态管理
 */

import { ref } from 'vue';
import { useMapSearchState } from './useMapSearchState';
import { ensureAMapLoaded } from '@/utils/amapLoader';

import { mapSearchHomestays, searchHomestays, getMapClusters, getNearbyHomestays, landmarkSearchHomestays } from '@/api/homestay/search';

// 共享高德地图配置
// 默认城市中心坐标（用于失败降级）
const DEFAULT_CITY_CENTERS: Record<string, { lat: number; lng: number }> = {
  '1101': { lat: 39.9042, lng: 116.4074 }, // 北京
  '3101': { lat: 31.2304, lng: 121.4737 }, // 上海
  '4403': { lat: 22.5431, lng: 114.0579 }, // 深圳
  '4401': { lat: 23.1291, lng: 113.2644 }, // 广州
  '4602': { lat: 20.0444, lng: 110.1989 }, // 三亚
  '5101': { lat: 30.5728, lng: 104.0668 }, // 成都
  '3301': { lat: 30.2741, lng: 120.1551 }, // 杭州
  '3201': { lat: 32.0603, lng: 118.7969 }, // 南京
  '5001': { lat: 29.5630, lng: 106.5516 }, // 重庆
  '4406': { lat: 22.5311, lng: 113.1248 }, // 珠海
  '6101': { lat: 34.3416, lng: 108.9398 }, // 西安
  '4201': { lat: 30.5928, lng: 114.3055 }, // 武汉
  '3205': { lat: 31.2989, lng: 120.5853 }, // 苏州
  '1201': { lat: 39.0842, lng: 117.2009 }, // 天津
  '3502': { lat: 24.4798, lng: 118.0894 }, // 厦门
  '3702': { lat: 36.0671, lng: 120.3826 }, // 青岛
  '4301': { lat: 28.2282, lng: 112.9388 }, // 长沙
  '5301': { lat: 25.0389, lng: 102.7183 }, // 昆明
  '2102': { lat: 38.9140, lng: 121.6147 }, // 大连
  '3302': { lat: 29.8683, lng: 121.5440 }, // 宁波
};

const getCityCenter = (cityCode?: string) => {
  if (!cityCode) return undefined;

  return DEFAULT_CITY_CENTERS[cityCode]
    ?? (
      cityCode.length === 6 && cityCode.endsWith('00')
        ? DEFAULT_CITY_CENTERS[cityCode.slice(0, 4)]
        : undefined
    );
};

export interface MapHomestay {
  id: number;
  title: string;
  price: number;
  coverImage: string;
  rating?: number;
  provinceCode?: string;
  cityCode?: string;
  districtCode?: string;
  addressDetail?: string;
  maxGuests?: number;
  propertyType?: string;
  autoConfirm?: boolean;
  latitude?: number;
  longitude?: number;
  // 地理编码后的坐标
  lat?: number;
  lng?: number;
  // 距离（km）
  distanceKm?: number;
  // 标记对象
  marker?: any;
  // 唯一标识（用于缓存）
  geoKey?: string;
}

export interface SearchFilters {
  provinceCode?: string;
  cityCode?: string;
  districtCode?: string;
  minPrice?: number;
  maxPrice?: number;
  minGuests?: number;
  maxGuests?: number;
  keyword?: string;
  checkInDate?: string;
  checkOutDate?: string;
  northEastLat?: number;
  northEastLng?: number;
  southWestLat?: number;
  southWestLng?: number;
}

export interface MapViewState {
  centerLat?: number;
  centerLng?: number;
  zoom?: number;
}

export interface MapCluster {
  latitude: number;
  longitude: number;
  count: number;
}

export interface SearchOrigin {
  latitude: number;
  longitude: number;
  name?: string;
  source: 'map-center' | 'user' | 'landmark';
  accuracy?: number;
}

export interface LandmarkCandidate {
  latitude: number;
  longitude: number;
  name?: string;
}

type SearchMode = 'normal' | 'nearby' | 'landmark';

type ViewportBounds = Required<Pick<
  SearchFilters,
  'northEastLat' | 'northEastLng' | 'southWestLat' | 'southWestLng'
>>;

export interface NormalSearchSnapshot {
  filters: SearchFilters;
  viewportSearchEnabled: boolean;
  mapView: MapViewState;
}

export interface CurrentSearchContext {
  mode: SearchMode;
  filters: SearchFilters;
  viewportSearchEnabled: boolean;
  mapView: MapViewState;
  nearbyRadius: number;
  nearbyOrigin: SearchOrigin | null;
  landmark: SearchOrigin | null;
  selectedHomestayId: number | null;
  hoveredHomestayId: number | null;
}

export function useMapSearch() {
  const {
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
    clearSpecialSearchContext: _clearSpecialSearchContext,
    setLandmarkCandidate: _setLandmarkCandidate,
    resetSearchMode: _resetSearchMode,
    runCurrentSearch: _runCurrentSearch,
    setSearchMode: _setSearchMode,
  } = useMapSearchState();

  // 地图相关状态
  const mapInstance = ref<any>(null);
  let onViewDetailCallback: ((id: number) => void) | null = null;
  const markers = ref<any[]>([]);
  const clusterMarkers = ref<any[]>([]);
  const homestays = ref<MapHomestay[]>([]);
  const clusters = ref<MapCluster[]>([]);
  const isLoading = ref(false);
  const isMapReady = ref(false);
  const mapError = ref<string | null>(null);
  const searchError = ref<string | null>(null);
  const infoWindow = ref<any>(null);
  const searchCenterMarker = ref<any>(null);
  const searchRadiusCircle = ref<any>(null);
  const currentCityCode = ref<string>('');
  const isLocating = ref(false);

  // 防抖定时器
  let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null;
  let suppressedViewportEvents = 0;
  let latestSearchRequestId = 0;
  let pendingViewportSearch = false;

  const hasUsableCoordinates = (homestay: Pick<MapHomestay, 'latitude' | 'longitude' | 'lat' | 'lng'>) => {
    const lat = homestay.lat ?? homestay.latitude;
    const lng = homestay.lng ?? homestay.longitude;
    return Number.isFinite(lat) && Number.isFinite(lng);
  };

  const suppressViewportSearch = (count = 1) => {
    suppressedViewportEvents += count;
  };

  const cloneFilters = (value: SearchFilters = {}) => ({ ...removeViewportBounds(value) });

  const cloneMapView = (value: MapViewState = {}) => ({ ...value });

  const restoreNormalSearchSnapshot = () => {
    if (!normalSearchSnapshot.value) {
      return false;
    }

    filters.value = cloneFilters(normalSearchSnapshot.value.filters);
    viewportSearchEnabled.value = normalSearchSnapshot.value.viewportSearchEnabled;

    const snapshotMapView = normalSearchSnapshot.value.mapView;
    const hasSnapshotMapView = (
      snapshotMapView.centerLat !== undefined ||
      snapshotMapView.centerLng !== undefined ||
      snapshotMapView.zoom !== undefined
    );

    if (hasSnapshotMapView && mapInstance.value) {
      setMapView(snapshotMapView);
    } else {
      mapView.value = cloneMapView(snapshotMapView);
      syncSearchContext();
    }

    return true;
  };

  const applyViewportBounds = (baseFilters: SearchFilters): SearchFilters => {
    if (!viewportSearchEnabled.value) {
      const { northEastLat, northEastLng, southWestLat, southWestLng, ...rest } = baseFilters;
      return rest;
    }

    const viewportBounds = getViewportBounds();
    if (!viewportBounds) {
      return baseFilters;
    }

    return {
      ...baseFilters,
      ...viewportBounds,
    };
  };

  const getViewportBounds = () => {
    if (!mapInstance.value) return null;

    const bounds = mapInstance.value.getBounds();
    if (!bounds) return null;

    const northEast = bounds.getNorthEast?.();
    const southWest = bounds.getSouthWest?.();
    if (!northEast || !southWest) return null;

    return {
      northEastLat: northEast.getLat(),
      northEastLng: northEast.getLng(),
      southWestLat: southWest.getLat(),
      southWestLng: southWest.getLng(),
    };
  };

  const syncMapViewState = () => {
    if (!mapInstance.value) return;

    const center = mapInstance.value.getCenter?.();
    const zoom = mapInstance.value.getZoom?.();
    if (!center || zoom === undefined) return;

    mapView.value = {
      centerLat: center.getLat?.(),
      centerLng: center.getLng?.(),
      zoom: Number(zoom),
    };
    syncSearchContext();
  };

  const clearSearchContextOverlays = () => {
    if (!mapInstance.value) {
      searchCenterMarker.value = null;
      searchRadiusCircle.value = null;
      return;
    }

    const overlaysToRemove = [
      searchCenterMarker.value,
      searchRadiusCircle.value,
    ].filter(Boolean);

    if (overlaysToRemove.length > 0) {
      mapInstance.value.remove(overlaysToRemove);
    }

    searchCenterMarker.value = null;
    searchRadiusCircle.value = null;
  };

  const clearSpecialSearchContext = () => _clearSpecialSearchContext(clearSearchContextOverlays);

  const createSearchCenterMarkerContent = (label?: string) => {
    const markerEl = document.createElement('div');
    markerEl.className = 'map-search-center-marker';
    markerEl.style.cssText = [
      'display:flex',
      'align-items:center',
      'gap:8px',
      'pointer-events:none',
    ].join(';');

    const dotEl = document.createElement('span');
    dotEl.style.cssText = [
      'display:block',
      'width:14px',
      'height:14px',
      'border-radius:9999px',
      'border:3px solid #ffffff',
      'background:#1677ff',
      'box-shadow:0 2px 8px rgba(22,119,255,0.35)',
    ].join(';');
    markerEl.appendChild(dotEl);

    if (label) {
      const labelEl = document.createElement('span');
      labelEl.textContent = label;
      labelEl.style.cssText = [
        'display:block',
        'padding:4px 8px',
        'border-radius:9999px',
        'background:rgba(255,255,255,0.94)',
        'color:#1f2937',
        'font-size:12px',
        'font-weight:600',
        'line-height:1',
        'white-space:nowrap',
        'box-shadow:0 4px 14px rgba(15,23,42,0.16)',
      ].join(';');
      markerEl.appendChild(labelEl);
    }

    return markerEl;
  };

  const updateSearchContextOverlays = (options: { fitView?: boolean } = {}) => {
    const { fitView = false } = options;
    if (!mapInstance.value) return;

    const AMap = (window as any).AMap;
    if (!AMap) return;

    clearSearchContextOverlays();

    const searchAreaContext = getSearchAreaContext();
    if (!searchAreaContext) {
      return;
    }

    const { center, radiusKm } = searchAreaContext;
    const radius = Math.max(0, radiusKm) * 1000;
    const centerPosition: [number, number] = [center.longitude, center.latitude];

    searchCenterMarker.value = new AMap.Marker({
      position: centerPosition,
      content: createSearchCenterMarkerContent(center.name),
      offset: new AMap.Pixel(-7, -7),
      clickable: false,
      zIndex: 160,
    });

    searchRadiusCircle.value = new AMap.Circle({
      center: centerPosition,
      radius,
      strokeColor: '#1677ff',
      strokeWeight: 2,
      strokeOpacity: 0.75,
      fillColor: '#1677ff',
      fillOpacity: 0.12,
      zIndex: 90,
    });

    mapInstance.value.add([searchRadiusCircle.value, searchCenterMarker.value]);

    if (fitView) {
      const fitOverlays = [
        ...markers.value,
        searchCenterMarker.value,
        searchRadiusCircle.value,
      ].filter(Boolean);

      if (fitOverlays.length > 0) {
        suppressViewportSearch(2);
        mapInstance.value.setFitView(fitOverlays, false, [60, 60, 60, 60], undefined, true);
      }
    }
  };

  const getMapCenterOrigin = () => {
    const center = mapInstance.value?.getCenter?.();
    if (!center) return null;

    return {
      latitude: center.getLat(),
      longitude: center.getLng(),
      source: 'map-center' as const,
    };
  };

  const extractHomestayList = (response: any): any[] => {
    const payload = response?.data ?? response;

    if (Array.isArray(payload)) {
      return payload;
    }
    if (Array.isArray(payload?.content)) {
      return payload.content;
    }
    if (Array.isArray(payload?.data)) {
      return payload.data;
    }
    if (Array.isArray(payload?.data?.content)) {
      return payload.data.content;
    }

    return [];
  };

  const getSearchRequestViewportBounds = (searchRequest: SearchFilters): ViewportBounds | null => {
    if (
      searchRequest.northEastLat === undefined ||
      searchRequest.northEastLng === undefined ||
      searchRequest.southWestLat === undefined ||
      searchRequest.southWestLng === undefined
    ) {
      return null;
    }

    return {
      northEastLat: searchRequest.northEastLat,
      northEastLng: searchRequest.northEastLng,
      southWestLat: searchRequest.southWestLat,
      southWestLng: searchRequest.southWestLng,
    };
  };

  const hasSearchCriteriaBeyondViewport = (searchRequest: SearchFilters) => {
    return Boolean(
      searchRequest.keyword ||
      searchRequest.provinceCode ||
      searchRequest.cityCode ||
      searchRequest.districtCode ||
      searchRequest.minPrice !== undefined ||
      searchRequest.maxPrice !== undefined ||
      searchRequest.minGuests !== undefined ||
      searchRequest.maxGuests !== undefined ||
      searchRequest.checkInDate ||
      searchRequest.checkOutDate
    );
  };

  const removeViewportBounds = <T extends SearchFilters>(searchRequest: T) => {
    const { northEastLat, northEastLng, southWestLat, southWestLng, ...rest } = searchRequest;
    return rest;
  };

  const isWithinViewportBounds = (homestay: MapHomestay, bounds: ViewportBounds) => {
    if (!hasUsableCoordinates(homestay)) {
      return false;
    }

    const lat = homestay.lat ?? homestay.latitude!;
    const lng = homestay.lng ?? homestay.longitude!;
    const minLat = Math.min(bounds.northEastLat, bounds.southWestLat);
    const maxLat = Math.max(bounds.northEastLat, bounds.southWestLat);
    const minLng = Math.min(bounds.northEastLng, bounds.southWestLng);
    const maxLng = Math.max(bounds.northEastLng, bounds.southWestLng);

    return lat >= minLat && lat <= maxLat && lng >= minLng && lng <= maxLng;
  };

  const setMapView = (nextMapView: MapViewState) => {
    if (!mapInstance.value) return;

    const hasCenter = nextMapView.centerLat !== undefined && nextMapView.centerLng !== undefined;
    const hasZoom = nextMapView.zoom !== undefined;

    suppressViewportSearch(2);
    if (hasCenter && hasZoom && mapInstance.value.setZoomAndCenter) {
      mapInstance.value.setZoomAndCenter(nextMapView.zoom, [nextMapView.centerLng, nextMapView.centerLat], true);
    } else {
      if (hasCenter) {
        mapInstance.value.setCenter([nextMapView.centerLng, nextMapView.centerLat], true);
      }
      if (hasZoom) {
        mapInstance.value.setZoom(nextMapView.zoom, true);
      }
    }

    syncMapViewState();
  };

  const toOptionalNumber = (value: unknown): number | undefined => {
    if (value === null || value === undefined || value === '') {
      return undefined;
    }

    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : undefined;
  };

  const normalizeHomestay = (item: any): MapHomestay => ({
    id: item.id,
    title: item.title,
    price: toOptionalNumber(item.price) ?? 0,
    coverImage: item.coverImage,
    rating: toOptionalNumber(item.rating),
    provinceCode: item.provinceCode,
    cityCode: item.cityCode,
    districtCode: item.districtCode,
    addressDetail: item.addressDetail,
    maxGuests: toOptionalNumber(item.maxGuests),
    propertyType: item.propertyType || item.type,
    autoConfirm: item.autoConfirm,
    latitude: toOptionalNumber(item.latitude),
    longitude: toOptionalNumber(item.longitude),
    lat: toOptionalNumber(item.latitude),
    lng: toOptionalNumber(item.longitude),
    distanceKm: toOptionalNumber(item.distanceKm),
  });

  const syncActiveHomestayState = (list: MapHomestay[]) => {
    if (!list.some(item => item.id === selectedHomestayId.value)) {
      selectedHomestayId.value = null;
      if (infoWindow.value) {
        infoWindow.value.close();
      }
    }
    if (!list.some(item => item.id === hoveredHomestayId.value)) {
      hoveredHomestayId.value = null;
    }
    syncSearchContext();
  };

  const isCoordinateInViewport = (lat: number, lng: number) => {
    const bounds = getViewportBounds();
    if (!bounds) {
      return false;
    }

    const minLat = Math.min(bounds.northEastLat, bounds.southWestLat);
    const maxLat = Math.max(bounds.northEastLat, bounds.southWestLat);
    const minLng = Math.min(bounds.northEastLng, bounds.southWestLng);
    const maxLng = Math.max(bounds.northEastLng, bounds.southWestLng);

    return lat >= minLat && lat <= maxLat && lng >= minLng && lng <= maxLng;
  };

  const focusHomestayOnMap = (
    homestay: MapHomestay,
    options: { reason: 'hover' | 'select'; force?: boolean } = { reason: 'select' }
  ) => {
    if (!mapInstance.value || !hasUsableCoordinates(homestay)) {
      return;
    }

    const lat = homestay.lat ?? homestay.latitude!;
    const lng = homestay.lng ?? homestay.longitude!;
    const shouldCenter = options.force || options.reason === 'select' || !isCoordinateInViewport(lat, lng);
    if (!shouldCenter) {
      return;
    }

    suppressViewportSearch(2);
    // 统一使用 setCenter + immediately=true，避免 panTo 的缓慢动画导致卡顿
    mapInstance.value.setCenter([lng, lat], true);
    syncMapViewState();
  };

  /**
   * 初始化高德地图
   */

  const initMap = async (container: HTMLElement, initialView?: MapViewState) => {
    if (isMapReady.value) return;

    try {
      mapError.value = null;
      const AMap = await ensureAMapLoaded();

      const defaultCenter = [116.397428, 39.90923];
      const defaultZoom = 12;

      const center = (initialView?.centerLng != null && initialView?.centerLat != null)
        ? [initialView.centerLng, initialView.centerLat]
        : defaultCenter;
      const zoom = initialView?.zoom ?? defaultZoom;

      // 创建地图实例
      const map = new AMap.Map(container, {
        zoom,
        center,
        resizeEnable: true,
      });

      mapInstance.value = map;

      // 创建信息窗体
      infoWindow.value = new AMap.InfoWindow({
        offset: new AMap.Pixel(0, -30),
        closeWhenClickMap: true,
      });

      // 监听信息窗关闭，清除选中态
      infoWindow.value.on('close', () => {
        selectedHomestayId.value = null;
        hoveredHomestayId.value = null;
        syncMarkerStateClasses();
      });

      // 监听地图移动结束事件
      map.on('moveend', handleMapMoveEnd);
      // 监听缩放结束事件
      map.on('zoomend', handleMapMoveEnd);

      isMapReady.value = true;
      syncMapViewState();
      updateSearchContextOverlays();
    } catch (e) {
      console.error('[MapSearch] 地图初始化失败:', e);
      mapError.value = '地图加载失败，请重试';
      isMapReady.value = false;
    }
  };

  /**
   * 地图移动结束处理
   */
  const handleMapMoveEnd = () => {
    syncMapViewState();

    if (suppressedViewportEvents > 0) {
      suppressedViewportEvents--;
      return;
    }

    if (searchMode.value !== 'normal') {
      return;
    }

    // 防抖处理
    if (searchDebounceTimer) {
      clearTimeout(searchDebounceTimer);
    }
    searchDebounceTimer = setTimeout(() => {
      if (useClusterMode.value) {
        loadClusters();
      } else {
        searchVisibleHomestays();
      }
    }, 500);
  };

  /**
   * 搜索可视区域的房源
   */
  const searchVisibleHomestays = async () => {
    if (!mapInstance.value) return;
    if (!viewportSearchEnabled.value) return;
    if (searchMode.value !== 'normal') return;
    if (isLoading.value) {
      pendingViewportSearch = true;
      return;
    }

    const viewportBounds = getViewportBounds();
    if (!viewportBounds) return;

    currentCityCode.value = filters.value.cityCode || '';

    filters.value = {
      ...filters.value,
      ...viewportBounds,
    };

    await loadHomestays({ fitView: false });
  };

  /**
   * 加载房源数据
   */
  const loadHomestays = async (options: { fitView?: boolean } = {}) => {
    const { fitView = true } = options;
    const requestId = ++latestSearchRequestId;
    isLoading.value = true;
    searchError.value = null;
    try {
      const searchRequest: any = {};

      if (filters.value.keyword) searchRequest.keyword = filters.value.keyword;
      if (filters.value.provinceCode) searchRequest.provinceCode = filters.value.provinceCode;
      if (filters.value.cityCode) searchRequest.cityCode = filters.value.cityCode;
      if (filters.value.districtCode) searchRequest.districtCode = filters.value.districtCode;
      if (filters.value.minPrice !== undefined) searchRequest.minPrice = filters.value.minPrice;
      if (filters.value.maxPrice !== undefined) searchRequest.maxPrice = filters.value.maxPrice;
      if (filters.value.minGuests !== undefined) searchRequest.minGuests = filters.value.minGuests;
      if (filters.value.maxGuests !== undefined) searchRequest.maxGuests = filters.value.maxGuests;
      if (filters.value.checkInDate) searchRequest.checkInDate = filters.value.checkInDate;
      if (filters.value.checkOutDate) searchRequest.checkOutDate = filters.value.checkOutDate;
      if (filters.value.northEastLat !== undefined) searchRequest.northEastLat = filters.value.northEastLat;
      if (filters.value.northEastLng !== undefined) searchRequest.northEastLng = filters.value.northEastLng;
      if (filters.value.southWestLat !== undefined) searchRequest.southWestLat = filters.value.southWestLat;
      if (filters.value.southWestLng !== undefined) searchRequest.southWestLng = filters.value.southWestLng;

      // 分页加载所有可见区域的房源
      searchRequest.page = 0;
      searchRequest.size = 100;

      const viewportBounds = getSearchRequestViewportBounds(searchRequest);
      let response = viewportBounds
        ? await mapSearchHomestays(searchRequest)
        : await searchHomestays(searchRequest);
      let data = extractHomestayList(response);
      let usedFallbackWithoutViewport = false;

      if (
        data.length === 0 &&
        viewportBounds &&
        hasSearchCriteriaBeyondViewport(searchRequest)
      ) {
        response = await searchHomestays(removeViewportBounds(searchRequest));
        data = extractHomestayList(response);
        usedFallbackWithoutViewport = true;
      }

      if (requestId !== latestSearchRequestId) {
        return;
      }
      // 转换为 MapHomestay 格式
      let list: MapHomestay[] = data.map(normalizeHomestay);

      // 仅保留已在后端持久化坐标的房源；不再运行时调用高德地理编码 API
      if (usedFallbackWithoutViewport && viewportBounds) {
        list = list.filter(homestay => hasUsableCoordinates(homestay) && isWithinViewportBounds(homestay, viewportBounds));
      } else {
        list = list.filter(homestay => hasUsableCoordinates(homestay));
      }

      homestays.value = list;
      syncActiveHomestayState(list);
      rememberNormalSearchSnapshot();

      // 更新地图标记
      updateMarkers({ fitView });
    } catch (e) {
      if (requestId !== latestSearchRequestId) {
        return;
      }
      console.error('[MapSearch] 加载房源失败:', e);
      searchError.value = '房源加载失败，请稍后重试';
      homestays.value = [];
    } finally {
      if (requestId === latestSearchRequestId) {
        isLoading.value = false;
        if (searchMode.value !== 'normal') {
          pendingViewportSearch = false;
        }
        if (pendingViewportSearch && viewportSearchEnabled.value && searchMode.value === 'normal') {
          pendingViewportSearch = false;
          setTimeout(() => {
            void searchVisibleHomestays();
          }, 0);
        }
      }
    }
  };

  /**
   * 标记防重叠：检测屏幕像素距离，太近的标记做垂直堆叠偏移
   */
  const applyMarkerAntiOverlap = (markerList: any[]) => {
    if (!mapInstance.value || markerList.length < 2) return;

    const AMap = (window as any).AMap;
    if (!AMap) return;

    const minDistance = 52; // 像素阈值（价格标记宽度约 50-60px）
    const stepOffset = 28;  // 每个重叠层向上偏移的像素
    const positions: { marker: any; x: number; y: number; offsetIndex: number }[] = [];

    for (const marker of markerList) {
      const pos = marker.getPosition();
      if (!pos) continue;
      const pixel = mapInstance.value.lngLatToContainer(pos);
      positions.push({ marker, x: pixel.x, y: pixel.y, offsetIndex: 0 });
    }

    // 按价格降序排列，高价标记优先保持原位，低价标记向上偏移
    positions.sort((a, b) => {
      const priceA = (a.marker.getExtData()?.price ?? 0);
      const priceB = (b.marker.getExtData()?.price ?? 0);
      return priceB - priceA;
    });

    for (let i = 0; i < positions.length; i++) {
      for (let j = i + 1; j < positions.length; j++) {
        const dx = positions[i].x - positions[j].x;
        const dy = positions[i].y - positions[j].y;
        const dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < minDistance) {
          positions[j].offsetIndex = Math.max(positions[j].offsetIndex, positions[i].offsetIndex + 1);
        }
      }
    }

    for (const p of positions) {
      if (p.offsetIndex > 0) {
        const offsetY = -40 - p.offsetIndex * stepOffset;
        p.marker.setOffset(new AMap.Pixel(-40, offsetY));
      }
    }
  };

  /**
   * 更新地图标记
   */
  const updateMarkers = (options: { fitView?: boolean } = {}) => {
    const { fitView = true } = options;
    if (!mapInstance.value) return;

    clearMarkers();
    clearClusterMarkers();

    const nextMarkers = homestays.value
      .map((homestay) => createMarker(homestay))
      .filter((marker): marker is NonNullable<typeof marker> => Boolean(marker));

    if (nextMarkers.length > 0) {
      mapInstance.value.add(nextMarkers);
    }

    markers.value = nextMarkers;

    // 应用防重叠
    applyMarkerAntiOverlap(nextMarkers);

    updateSearchContextOverlays({ fitView });

    if (fitView && markers.value.length > 0 && !getSearchAreaContext()) {
      suppressViewportSearch(2);
      mapInstance.value.setFitView(markers.value, false, [60, 60, 60, 60], undefined, true);
    }
  };

  /**
   * 验证图片URL是否安全
   */
  const isValidImageUrl = (url: string): boolean => {
    if (!url) return false;
    // 只允许 http/https 协议，且必须是图片域名或localhost
    try {
      const parsed = new URL(url);
      if (!['http:', 'https:'].includes(parsed.protocol)) return false;
      const trustedHosts = new Set(['localhost', '127.0.0.1', window.location.hostname]);
      const apiBaseUrl = import.meta.env.VITE_API_BASE_URL;
      if (apiBaseUrl) {
        try {
          trustedHosts.add(new URL(apiBaseUrl, window.location.origin).hostname);
        } catch {
          // ignore invalid configured base url
        }
      }

      // 如果是完整URL，必须是可信域名或localhost
      if (!trustedHosts.has(parsed.hostname) && !url.startsWith('https://picsum.photos')) {
        return false;
      }
      return true;
    } catch {
      // 相对路径认为是安全的（会被加上base URL）
      return true;
    }
  };

  /**
   * 创建单个标记
   */
  const createMarker = (homestay: MapHomestay) => {
    if (!mapInstance.value) return;

    const AMap = (window as any).AMap;
    if (!AMap) return;
    if (!hasUsableCoordinates(homestay)) return;

    const markerLat = homestay.lat ?? homestay.latitude!;
    const markerLng = homestay.lng ?? homestay.longitude!;

    // 使用textContent创建DOM元素，避免XSS
    const markerEl = document.createElement('div');
    markerEl.className = `map-price-marker ${hoveredHomestayId.value === homestay.id ? 'active' : ''} ${selectedHomestayId.value === homestay.id ? 'selected' : ''}`;
    markerEl.dataset['id'] = String(homestay.id);

    const priceEl = document.createElement('span');
    priceEl.className = 'price';
    priceEl.textContent = `¥${homestay.price}`;
    markerEl.appendChild(priceEl);

    const marker = new AMap.Marker({
      position: [markerLng, markerLat],
      content: markerEl,
      offset: new AMap.Pixel(-40, -40),
      extData: homestay,
    });

    // 点击事件
    marker.on('click', () => {
      selectHomestay(homestay.id);
    });

    // 悬停事件
    marker.on('mouseover', () => {
      hoverHomestay(homestay.id, { source: 'marker' });
    });

    marker.on('mouseout', () => {
      hoverHomestay(null, { source: 'marker' });
    });

    homestay.marker = marker;
    return marker;
  };

  /**
   * 更新标记样式
   */
  const updateMarkerStyle = (id: number, className: string, add: boolean) => {
    const marker = markers.value.find(m => m.getExtData()?.id === id);
    if (!marker) return;

    const content = marker.getContent();
    // content可能是DOM元素或字符串
    if (content instanceof HTMLElement) {
      if (add) {
        content.classList.add(className);
      } else {
        content.classList.remove(className);
      }
    }
  };

  const syncMarkerStateClasses = () => {
    markers.value.forEach((marker) => {
      const markerId = marker.getExtData()?.id;
      if (!markerId) {
        return;
      }

      updateMarkerStyle(markerId, 'active', hoveredHomestayId.value === markerId);
      updateMarkerStyle(markerId, 'selected', selectedHomestayId.value === markerId);
    });
  };

  /**
   * 清除所有标记
   */
  const clearMarkers = () => {
    if (!mapInstance.value) return;

    if (markers.value.length > 0) {
      mapInstance.value.remove(markers.value);
    }
    markers.value = [];
  };

  const clearClusterMarkers = () => {
    if (!mapInstance.value) return;

    if (clusterMarkers.value.length > 0) {
      mapInstance.value.remove(clusterMarkers.value);
    }
    clusterMarkers.value = [];
  };

  const createClusterMarker = (cluster: MapCluster) => {
    if (!mapInstance.value) return;
    const AMap = (window as any).AMap;
    if (!AMap) return;

    const size = Math.min(56, 36 + cluster.count * 2);
    const el = document.createElement('div');
    el.className = 'map-cluster-marker';
    el.style.cssText = `
      width:${size}px;height:${size}px;
      border-radius:50%;
      background:#ff385c;
      color:#fff;
      display:flex;
      align-items:center;
      justify-content:center;
      font-size:13px;
      font-weight:700;
      box-shadow:0 4px 14px rgba(255,56,92,0.35);
      cursor:pointer;
      transition:transform 0.2s ease;
    `;
    el.textContent = String(cluster.count);

    const marker = new AMap.Marker({
      position: [cluster.longitude, cluster.latitude],
      content: el,
      offset: new AMap.Pixel(-size / 2, -size / 2),
      extData: cluster,
    });

    marker.on('click', () => {
      if (!mapInstance.value) return;
      const currentZoom = mapInstance.value.getZoom() || 12;
      const nextZoom = Math.min(currentZoom + 2, 18);
      suppressViewportSearch(2);
      mapInstance.value.setZoomAndCenter(nextZoom, [cluster.longitude, cluster.latitude], true);
      syncMapViewState();
      useClusterMode.value = false;
      void loadHomestays({ fitView: false });
    });

    return marker;
  };

  const updateClusterMarkers = () => {
    if (!mapInstance.value) return;
    clearClusterMarkers();

    const nextMarkers = clusters.value
      .map((cluster) => createClusterMarker(cluster))
      .filter((marker): marker is NonNullable<typeof marker> => Boolean(marker));

    if (nextMarkers.length > 0) {
      mapInstance.value.add(nextMarkers);
    }
    clusterMarkers.value = nextMarkers;
  };

  /**
   * 选择房源
   */
  const selectHomestay = (id: number) => {
    selectedHomestayId.value = id;
    hoveredHomestayId.value = id;
    syncSearchContext();
    syncMarkerStateClasses();

    const homestay = homestays.value.find(h => h.id === id);
    if (!homestay || !infoWindow.value || !mapInstance.value) return;
    if (!hasUsableCoordinates(homestay)) return;

    const markerLat = homestay.lat ?? homestay.latitude!;
    const markerLng = homestay.lng ?? homestay.longitude!;

    // 创建DOM元素，避免XSS
    const container = document.createElement('div');
    container.className = 'map-info-window';

    const imageDiv = document.createElement('div');
    imageDiv.className = 'info-image';
    const safeImageUrl = isValidImageUrl(homestay.coverImage)
      ? getImageUrl(homestay.coverImage)
      : 'https://picsum.photos/300/200';
    imageDiv.style.backgroundImage = `url('${safeImageUrl}')`;

    const contentDiv = document.createElement('div');
    contentDiv.className = 'info-content';

    const titleEl = document.createElement('h4');
    titleEl.textContent = homestay.title || '';
    contentDiv.appendChild(titleEl);

    const priceEl = document.createElement('p');
    priceEl.className = 'info-price';
    priceEl.textContent = `¥${homestay.price}/晚`;
    contentDiv.appendChild(priceEl);

    if (homestay.distanceKm != null) {
      const distanceEl = document.createElement('p');
      distanceEl.className = 'info-distance';
      distanceEl.textContent = `📍 距搜索中心 ${homestay.distanceKm.toFixed(1)}km`;
      distanceEl.style.cssText = 'font-size:12px;color:#3b82f6;margin:4px 0 0;';
      contentDiv.appendChild(distanceEl);
    }

    if (homestay.rating) {
      const ratingEl = document.createElement('p');
      ratingEl.className = 'info-rating';
      ratingEl.textContent = `⭐ ${homestay.rating}`;
      contentDiv.appendChild(ratingEl);
    }

    const actionDiv = document.createElement('div');
    actionDiv.style.cssText = 'margin-top:10px;';
    const detailBtn = document.createElement('button');
    detailBtn.type = 'button';
    detailBtn.className = 'info-detail-btn';
    detailBtn.textContent = '查看详情';
    detailBtn.onclick = () => {
      if (onViewDetailCallback) {
        onViewDetailCallback(homestay.id);
      }
    };
    actionDiv.appendChild(detailBtn);
    contentDiv.appendChild(actionDiv);

    container.appendChild(imageDiv);
    container.appendChild(contentDiv);

    infoWindow.value.setContent(container);
    if (window.innerWidth > 768) {
      infoWindow.value.open(mapInstance.value, [markerLng, markerLat]);
    }

    // 移动地图中心
    focusHomestayOnMap(homestay, { reason: 'select', force: true });
  };

  /**
   * 获取图片URL
   */
  const getImageUrl = (imageUrl: string): string => {
    if (!imageUrl) return 'https://picsum.photos/300/200';
    if (imageUrl.startsWith('http')) return imageUrl;
    const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081';
    return `${baseUrl}${imageUrl.startsWith('/') ? '' : '/'}${imageUrl}`;
  };

  /**
   * 更新筛选条件并重新搜索
   */
  const updateFilters = async (newFilters: SearchFilters) => {
    filters.value = cloneFilters(newFilters);
    syncSearchContext();

    // 设置地图中心
    if (newFilters.cityCode && mapInstance.value) {
      const center = getCityCenter(newFilters.cityCode);
      if (center) {
        suppressViewportSearch(2);
        mapInstance.value.setCenter([center.lng, center.lat], true);
      }
    }

    await runCurrentSearch({ fitView: true, filters: filters.value });
  };

  const applySearchState = async (
    newFilters: SearchFilters,
    options: {
      viewportOnly?: boolean;
      fitView?: boolean;
      skipCityCenter?: boolean;
      mapView?: MapViewState;
    } = {}
  ) => {
    const {
      viewportOnly = viewportSearchEnabled.value,
      fitView = true,
      skipCityCenter = false,
      mapView: nextMapView,
    } = options;

    viewportSearchEnabled.value = viewportOnly;
    filters.value = cloneFilters(newFilters);
    syncSearchContext();

    if (nextMapView) {
      setMapView(nextMapView);
    } else if (!skipCityCenter && newFilters.cityCode && mapInstance.value) {
      const center = getCityCenter(newFilters.cityCode);
      if (center) {
        suppressViewportSearch(2);
        mapInstance.value.setCenter([center.lng, center.lat], true);
        syncMapViewState();
      }
    }

    await runCurrentSearch({ fitView, filters: filters.value });
  };

  const setViewportSearch = async (enabled: boolean) => {
    viewportSearchEnabled.value = enabled;
    syncSearchContext();
    await runCurrentSearch({ fitView: !enabled, filters: filters.value });
  };

  const retrySearch = async () => {
    await runCurrentSearch({ fitView: !viewportSearchEnabled.value, filters: filters.value });
  };

  /**
   * 悬停房源卡片
   */
  const hoverHomestay = (id: number | null, _options?: { source?: 'card' | 'marker' }) => {
    hoveredHomestayId.value = id;
    syncSearchContext();
    syncMarkerStateClasses();

    if (id === null) {
      return;
    }

    const homestay = homestays.value.find(item => item.id === id);
    if (!homestay) {
      return;
    }

    // hover 时仅高亮标记，不强制移动地图中心
    // focusHomestayOnMap(homestay, {
    //   reason: 'hover',
    //   force: options?.source === 'card',
    // });
  };

  /**
   * 加载地图聚合点
   */
  const loadClusters = async () => {
    if (!mapInstance.value) return;

    const viewportBounds = getViewportBounds();
    if (!viewportBounds) return;

    isLoading.value = true;
    searchError.value = null;

    try {
      const zoom = mapInstance.value.getZoom() || 12;
      const request = {
        ...filters.value,
        ...viewportBounds,
        zoom,
      };

      const response = await getMapClusters(request);
      const payload = response?.data ?? response;
      clusters.value = Array.isArray(payload) ? payload : [];
      clearMarkers();
      updateClusterMarkers();
    } catch (e) {
      console.error('[MapSearch] 加载聚合点失败:', e);
      searchError.value = '加载聚合点失败';
      clusters.value = [];
      clearClusterMarkers();
    } finally {
      isLoading.value = false;
    }
  };

  const setLandmarkCandidate = (landmark: LandmarkCandidate | null) =>
    _setLandmarkCandidate(landmark, {
      clearSearchContextOverlays,
      updateSearchContextOverlays,
    });

  /**
   * 搜索附近房源
   */
  const searchNearby = async (options?: {
    latitude?: number;
    longitude?: number;
    radius?: number;
    source?: 'map-center' | 'user';
    filters?: SearchFilters;
    fitView?: boolean;
  }) => {
    if (!mapInstance.value) return;

    const requestId = ++latestSearchRequestId;

    const fallbackOrigin = nearbySearchOrigin.value
      ?? userLocation.value
      ?? getMapCenterOrigin();
    if (!fallbackOrigin) return;

    const latitude = options?.latitude ?? fallbackOrigin.latitude;
    const longitude = options?.longitude ?? fallbackOrigin.longitude;
    const radius = options?.radius ?? nearbyRadius.value;
    const source = options?.source
      ?? nearbySearchOrigin.value?.source
      ?? userLocation.value?.source
      ?? 'map-center';

    if (searchMode.value === 'normal') {
      rememberNormalSearchSnapshot();
    }

    isLoading.value = true;
    searchError.value = null;
    searchMode.value = 'nearby';
    nearbyRadius.value = radius;
    useClusterMode.value = false;
    activeLandmark.value = null;
    filters.value = cloneFilters(options?.filters ?? filters.value);
    nearbySearchOrigin.value = {
      latitude,
      longitude,
      source,
      accuracy: source === 'user' ? userLocation.value?.accuracy : undefined,
    };
    syncSearchContext();
    updateSearchContextOverlays();

    try {
      const request = {
        ...filters.value,
        latitude,
        longitude,
        radiusKm: radius,
        limit: 50,
        sortBy: 'DISTANCE',
      };

      const response = await getNearbyHomestays(request);

      if (requestId !== latestSearchRequestId) {
        return;
      }

      const list = extractHomestayList(response);

      homestays.value = list.map(normalizeHomestay);
      syncActiveHomestayState(homestays.value);

      updateMarkers({ fitView: options?.fitView ?? true });
    } catch (e) {
      if (requestId !== latestSearchRequestId) {
        return;
      }
      console.error('[MapSearch] 搜索附近房源失败:', e);
      searchError.value = '搜索附近房源失败';
      homestays.value = [];
      syncActiveHomestayState([]);
      updateMarkers({ fitView: options?.fitView ?? true });
    } finally {
      if (requestId === latestSearchRequestId) {
        isLoading.value = false;
      }
    }
  };

  /**
   * 地标周边搜索
   */
  const searchByLandmark = async (
    landmark: { latitude: number; longitude: number; name?: string },
    options?: { radius?: number; filters?: SearchFilters; fitView?: boolean }
  ) => {
    const requestId = ++latestSearchRequestId;

    if (searchMode.value === 'normal') {
      rememberNormalSearchSnapshot();
    }

    isLoading.value = true;
    searchError.value = null;
    nearbyRadius.value = options?.radius ?? nearbyRadius.value;
    setLandmarkCandidate(landmark);
    filters.value = cloneFilters(options?.filters ?? filters.value);
    syncSearchContext();
    updateSearchContextOverlays();

    try {
      const request = {
        ...filters.value,
        latitude: landmark.latitude,
        longitude: landmark.longitude,
        radiusKm: options?.radius ?? nearbyRadius.value,
        limit: 50,
        sortBy: 'DISTANCE',
      };

      const response = await landmarkSearchHomestays(request);

      if (requestId !== latestSearchRequestId) {
        return;
      }

      const list = extractHomestayList(response);

      homestays.value = list.map(normalizeHomestay);
      syncActiveHomestayState(homestays.value);

      updateMarkers({ fitView: options?.fitView ?? true });
    } catch (e) {
      if (requestId !== latestSearchRequestId) {
        return;
      }
      console.error('[MapSearch] 地标搜索失败:', e);
      searchError.value = '地标搜索失败';
      homestays.value = [];
      syncActiveHomestayState([]);
      updateMarkers({ fitView: options?.fitView ?? true });
    } finally {
      if (requestId === latestSearchRequestId) {
        isLoading.value = false;
      }
    }
  };

  /**
   * 销毁地图
   */
  const locateUser = async (options?: {
    recenter?: boolean;
    enableHighAccuracy?: boolean;
    timeout?: number;
  }) => {
    if (!navigator.geolocation) {
      locationError.value = '当前浏览器不支持定位';
      return null;
    }

    isLocating.value = true;
    locationError.value = null;

    try {
      const position = await new Promise<GeolocationPosition>((resolve, reject) => {
        navigator.geolocation.getCurrentPosition(resolve, reject, {
          enableHighAccuracy: options?.enableHighAccuracy ?? true,
          timeout: options?.timeout ?? 10000,
          maximumAge: 0,
        });
      });

      const nextLocation: SearchOrigin = {
        latitude: position.coords.latitude,
        longitude: position.coords.longitude,
        accuracy: position.coords.accuracy,
        source: 'user',
      };

      userLocation.value = nextLocation;
      nearbySearchOrigin.value = nextLocation;
      syncSearchContext();

      if (options?.recenter !== false && mapInstance.value) {
        suppressViewportSearch(2);
        mapInstance.value.setCenter([nextLocation.longitude, nextLocation.latitude]);
        syncMapViewState();
      }

      return nextLocation;
    } catch (error) {
      locationError.value = '定位失败，请检查浏览器权限设置';
      console.error('[MapSearch] 用户定位失败:', error);
      syncSearchContext();
      return null;
    } finally {
      isLocating.value = false;
    }
  };

  const resetSearchMode = async (options?: { reload?: boolean; filters?: SearchFilters; fitView?: boolean }) =>
    _resetSearchMode(options, {
      loadHomestays,
      clearSpecialSearchContext,
      restoreNormalSearchSnapshot,
      syncMapViewState,
    });

  const runCurrentSearch = async (options?: { fitView?: boolean; filters?: SearchFilters }) =>
    _runCurrentSearch(options, {
      searchNearby,
      searchByLandmark,
      loadHomestays,
      applyViewportBounds,
    });

  const setSearchMode = async (mode: SearchMode) =>
    _setSearchMode(mode, {
      searchNearby,
      searchByLandmark,
      resetSearchMode,
      clearSearchContextOverlays,
    });

  const destroyMap = () => {
    if (searchDebounceTimer) {
      clearTimeout(searchDebounceTimer);
    }
    pendingViewportSearch = false;
    if (infoWindow.value) {
      infoWindow.value.close();
    }
    clearSearchContextOverlays();
    clearMarkers();
    clearClusterMarkers();
    if (mapInstance.value) {
      mapInstance.value.destroy();
      mapInstance.value = null;
    }
    isMapReady.value = false;
    clearSpecialSearchContext();
    syncSearchContext();
  };

  // 组件挂载与卸载逻辑由调用方（MapSearch.vue）管理

  return {
    // 状态
    homestays,
    clusters,
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
    filters,
    mapView,
    currentSearchContext,

    // 方法
    initMap,
    loadHomestays,
    loadClusters,
    locateUser,
    searchNearby,
    searchByLandmark,
    setLandmarkCandidate,
    setSearchMode,
    resetSearchMode,
    runCurrentSearch,
    updateFilters,
    applySearchState,
    setViewportSearch,
    setMapView,
    retrySearch,
    selectHomestay,
    hoverHomestay,
    destroyMap,
    getImageUrl,
    setOnViewDetail: (cb: (id: number) => void) => { onViewDetailCallback = cb; },
    rememberNormalSearchSnapshot,
  };
}
