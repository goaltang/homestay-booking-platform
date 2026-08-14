<template>
  <div class="map-search-page">
    <div class="top-filter-bar">
      <div class="search-wrapper">
        <el-autocomplete
          v-model="globalSearchKeyword"
          class="smart-search-input"
          clearable
          :loading="isSearchingSuggestions"
          :fetch-suggestions="queryGlobalSuggestions"
          :trigger-on-focus="true"
          :debounce="300"
          value-key="name"
          placeholder="搜索目的地、地标、景点..."
          @select="handleSelectSuggestion"
          @clear="handleClearSearch"
          @keyup.enter="handleSearchEnter"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
          <template #default="{ item }">
            <div class="suggestion-item">
              <el-icon class="suggestion-icon">
                <Location v-if="item.type === 'location' || item.type === 'current'" />
                <LocationInformation v-else />
              </el-icon>
              <div class="suggestion-content">
                <div class="suggestion-name" :class="{'is-current': item.type === 'current'}">{{ item.name }}</div>
                <div v-if="item.secondaryText" class="suggestion-desc">{{ item.secondaryText }}</div>
              </div>
            </div>
          </template>
        </el-autocomplete>
      </div>

      <div class="filter-item">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="入住日期"
          end-placeholder="退房日期"
          value-format="YYYY-MM-DD"
          :disabled-date="disabledDate"
          class="filter-date"
          @change="handleDateChange"
        />
      </div>

      <div class="filter-item region-filter">
        <el-cascader
          v-model="selectedRegion"
          :options="regionOptions"
          :props="{ checkStrictly: true, emitPath: true }"
          placeholder="选择地区"
          clearable
          class="region-cascader"
          @change="handleRegionChange"
        />
      </div>

      <div class="filter-item">
        <el-popover placement="bottom" :width="240" trigger="click">
          <template #reference>
            <el-button class="filter-btn" :class="{'is-active': guestCount}">
              {{ guestCount ? `${guestCount}位房客` : '添加房客' }}
            </el-button>
          </template>
          <div class="popover-content">
            <div class="popover-row">
              <span>入住人数</span>
              <el-input-number v-model="guestCount" :min="1" :max="20" @change="triggerAutoSearch" size="small" />
            </div>
          </div>
        </el-popover>
      </div>

      <div class="filter-item">
        <el-popover placement="bottom" :width="320" trigger="click" @hide="triggerAutoSearch">
          <template #reference>
            <el-button class="filter-btn" :class="{'is-active': minPrice || maxPrice}">
              {{ priceLabel }}
            </el-button>
          </template>
          <div class="popover-content">
            <div class="price-range-inputs">
              <el-input-number v-model="minPrice" :min="0" :max="minPriceUpperBound" placeholder="最低价" :controls="false" />
              <span class="range-sep">-</span>
              <el-input-number v-model="maxPrice" :min="maxPriceLowerBound" :max="99999" placeholder="最高价" :controls="false" />
            </div>
            <div style="margin-top: 12px; text-align: right;">
              <el-button size="small" text @click="minPrice = undefined; maxPrice = undefined;">重置</el-button>
              <el-button size="small" type="primary" @click="triggerAutoSearch">确定</el-button>
            </div>
          </div>
        </el-popover>
      </div>
      
      <el-button v-if="hasActiveFilters" text @click="handleReset" class="clear-btn">清除条件</el-button>

      <div class="top-actions">
        <el-switch v-model="viewportSearchEnabled" active-text="仅看当前视野" @change="handleViewportModeChange" />
        <el-tooltip content="使用聚合模式查看房源分布" placement="bottom">
          <el-button
            :type="useClusterMode ? 'primary' : 'default'"
            size="small"
            :icon="Grid"
            @click="handleToggleCluster"
            class="cluster-btn"
          >
            聚合
          </el-button>
        </el-tooltip>
      </div>

      <el-button class="mobile-filter-btn" @click="isMobileFilterOpen = true">
        <el-icon><Filter /></el-icon>
        筛选
      </el-button>
    </div>

    <div class="main-content">
      <div class="left-list" :class="{'is-mobile-expanded': isMobileListExpanded, 'dragging': isMobileListDragging}">
        <div class="mobile-drawer-handle"
          @touchstart="onHandleTouchStart"
          @touchmove.prevent="onHandleTouchMove"
          @touchend="onHandleTouchEnd">
          <div class="handle-bar"></div>
          <span class="handle-text">{{ homestays.length }} 个房源</span>
        </div>

        <div v-if="isLoading && homestays.length === 0" class="list-wrapper skeleton-wrapper">
          <div class="list-header">
            <span class="count-text skeleton-text" style="width: 120px;"></span>
          </div>
          <div class="list-scroll">
            <div v-for="n in 4" :key="n" class="skeleton-card">
              <div class="skeleton-image"></div>
              <div class="skeleton-body">
                <div class="skeleton-line" style="width: 70%;"></div>
                <div class="skeleton-line" style="width: 50%;"></div>
                <div class="skeleton-line" style="width: 40%;"></div>
              </div>
            </div>
          </div>
        </div>

        <div v-else-if="searchError && homestays.length === 0" class="state-container error-state">
          <el-icon :size="44" color="#f56c6c"><LocationInformation /></el-icon>
          <p>{{ searchError }}</p>
          <el-button type="primary" plain @click="handleRetrySearch">重试</el-button>
        </div>

        <div v-else-if="homestays.length === 0" class="state-container empty-state">
          <el-icon :size="48" color="#ccc"><LocationInformation /></el-icon>
          <p>暂无符合条件的房源</p>
          <p class="hint-text" style="color: #64748b;">请尝试缩放地图或调整筛选条件</p>
          <div class="empty-actions">
            <el-button type="primary" plain size="small" @click="handleReset">清除所有筛选</el-button>
            <el-button size="small" @click="handleExitSpecialSearch">查看全城房源</el-button>
          </div>
        </div>

        <div v-else class="list-wrapper" ref="listRef">
          <div class="list-header">
            <span class="count-text">找到 {{ homestays.length }} 个房源</span>
          </div>
          <div class="list-scroll">
            <MapHomestayCard
              v-for="homestay in homestays"
              :key="homestay.id"
              :homestay="homestay"
              :is-active="selectedHomestayId === homestay.id"
              :is-hovered="hoveredHomestayId === homestay.id"
              @click="handleCardClick"
              @hover="handleCardHover"
              @view-detail="handleViewDetail"
            />
          </div>
        </div>
      </div>

      <div ref="mapContainerRef" class="right-map">
        <div v-if="isMapReady && !mapError" class="map-locate-btn" @click="handleUseCurrentLocation" aria-label="定位到当前位置" role="button" tabindex="0">
          <el-icon><Location /></el-icon>
        </div>

        <div v-if="showMapSearchContextCard" class="map-context-card">
          <div class="map-context-card__body">
            <span class="map-context-card__title">{{ mapSearchContextTitle }}</span>
            <div class="radius-control">
              <span class="radius-label">半径 {{ nearbyRadius }}km</span>
              <el-slider v-model="nearbyRadius" :min="1" :max="20" :step="1" style="width: 120px;" @change="handleRadiusChange" />
            </div>
          </div>
          <el-button size="small" text @click="handleExitSpecialSearch">退出</el-button>
        </div>

        <div v-if="mapError" class="map-overlay error-overlay">
          <el-icon :size="48" color="#f56c6c"><LocationInformation /></el-icon>
          <span>{{ mapError }}</span>
          <el-button type="primary" @click="handleRetryMap">重新加载地图</el-button>
        </div>
        <div v-else-if="!isMapReady" class="map-overlay">
          <el-icon class="is-loading" :size="48"><Loading /></el-icon>
          <span>地图加载中...</span>
        </div>
      </div>
    </div>

    <el-drawer v-model="isMobileFilterOpen" direction="btt" size="70%" :with-header="false" class="mobile-filter-drawer">
      <div class="mobile-filter-content">
        <h3 class="drawer-title">筛选条件</h3>
        <div class="mobile-filter-section">
          <label class="section-label">地区</label>
          <el-cascader
            v-model="selectedRegion"
            :options="regionOptions"
            :props="{ checkStrictly: true, emitPath: true }"
            placeholder="选择地区"
            clearable
            style="width: 100%;"
            @change="handleRegionChange"
          />
        </div>
        <div class="mobile-filter-section">
          <label class="section-label">入住日期</label>
          <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="入住日期" end-placeholder="退房日期" value-format="YYYY-MM-DD" :disabled-date="disabledDate" style="width: 100%;" @change="handleDateChange" />
        </div>
        <div class="mobile-filter-section">
          <label class="section-label">入住人数</label>
          <el-input-number v-model="guestCount" :min="1" :max="20" @change="triggerAutoSearch" style="width: 100%;" />
        </div>
        <div class="mobile-filter-section">
          <label class="section-label">价格区间</label>
          <div class="price-range-inputs">
            <el-input-number v-model="minPrice" :min="0" :max="minPriceUpperBound" placeholder="最低价" @change="triggerAutoSearch" :controls="false" />
            <span class="range-sep">-</span>
            <el-input-number v-model="maxPrice" :min="maxPriceLowerBound" :max="99999" placeholder="最高价" @change="triggerAutoSearch" :controls="false" />
          </div>
        </div>
        <div class="mobile-filter-section switch-section">
          <el-switch v-model="viewportSearchEnabled" active-text="仅看当前视野" @change="handleViewportModeChange" />
          <el-button :type="useClusterMode ? 'primary' : 'default'" size="small" :icon="Grid" @click="handleToggleCluster">聚合</el-button>
        </div>
        <div class="mobile-filter-footer">
          <el-button @click="handleReset">清除条件</el-button>
          <el-button type="primary" @click="isMobileFilterOpen = false">查看 {{ homestays.length }} 个房源</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Grid, Loading, Location, LocationInformation, Search, Filter } from '@element-plus/icons-vue';
import { searchAmapPoiSuggestions } from '@/utils/mapService';
import { useMapSearch, type MapHomestay } from '@/composables/useMapSearch';
import { useMapSearchQuerySync, type QuerySyncFormState, type HydratedQueryState } from '@/composables/useMapSearchQuerySync';
import MapHomestayCard from '@/components/homestay/MapHomestayCard.vue';
import { trackClick, trackSearch } from '@/api/tracking';
import { regionData } from 'element-china-area-data';
import { debounce } from 'lodash-es';

const router = useRouter();
const route = useRoute();

const mapContainerRef = ref<HTMLElement | null>(null);
const listRef = ref<HTMLElement | null>(null);

const {
  homestays,
  selectedHomestayId,
  hoveredHomestayId,
  viewportSearchEnabled,
  useClusterMode,
  searchMode,
  nearbyRadius,
  activeLandmark,
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
} = useMapSearch();

const selectedRegion = ref<string[]>([]);
const regionOptions = ref(regionData);
const minPrice = ref<number | undefined>(undefined);
const maxPrice = ref<number | undefined>(undefined);
const guestCount = ref<number | undefined>(undefined);
const checkInDate = ref<string | undefined>(undefined);
const checkOutDate = ref<string | undefined>(undefined);

const globalSearchKeyword = ref('');
const isSearchingSuggestions = ref(false);
const isMobileListExpanded = ref(false);
const isMobileFilterOpen = ref(false);
const isMobileListDragging = ref(false);
const dateRange = ref<[string, string] | null>(null);
const landmarkLat = ref<number | undefined>(undefined);
const landmarkLng = ref<number | undefined>(undefined);
const resolvedLandmark = ref<any>(null);
const isLandmarkDirty = ref(false);
const hasRestoredRouteState = ref(false);
const skipNextRouteReplay = ref(false);

const {
  hydrateFiltersFromQuery,
  getMapViewFromQuery,
  syncQueryFromForm,
} = useMapSearchQuerySync({
  router,
  route,
  skipNextRouteReplay,
});

const minPriceUpperBound = computed(() => (maxPrice.value ?? 100000) - 1);
const maxPriceLowerBound = computed(() => (minPrice.value ?? 0) + 1);

const priceLabel = computed(() => {
  if (minPrice.value && maxPrice.value) return `¥${minPrice.value} - ¥${maxPrice.value}`;
  if (minPrice.value) return `¥${minPrice.value} 起`;
  if (maxPrice.value) return `¥${maxPrice.value} 以内`;
  return '价格区间';
});

const hasActiveFilters = computed(() => {
  return !!(globalSearchKeyword.value || dateRange.value || guestCount.value || minPrice.value || maxPrice.value || selectedRegion.value.length);
});

const resolvedLandmarkName = computed(() => (resolvedLandmark.value?.name ?? '').trim());
const currentAppliedLandmarkName = computed(() => activeLandmark.value?.name?.trim() || resolvedLandmarkName.value);
const hasResolvedLandmark = computed(
  () =>
    landmarkLat.value !== undefined
    && landmarkLng.value !== undefined
    && Boolean(resolvedLandmarkName.value)
);

const showMapSearchContextCard = computed(() => {
  return currentSearchContext.value.mode === 'nearby' || currentSearchContext.value.mode === 'landmark';
});

const mapSearchContextTitle = computed(() => {
  if (currentSearchContext.value.mode === 'landmark') {
    return currentAppliedLandmarkName.value
      ? `地标：${currentAppliedLandmarkName.value}`
      : '地标周边搜索';
  }

  if (currentSearchContext.value.mode === 'nearby') {
    return currentSearchContext.value.nearbyOrigin?.source === 'user'
      ? '当前位置附近'
      : '地图中心附近';
  }

  return '';
});

const buildFiltersFromForm = () => {
  const filters: Record<string, number | string> = {};

  if (selectedRegion.value.length > 0) {
    const [provinceCode, cityCode, districtCode] = getBackendRegionCodes(selectedRegion.value);
    if (provinceCode) filters.provinceCode = provinceCode;
    if (cityCode) filters.cityCode = cityCode;
    if (districtCode) filters.districtCode = districtCode;
  }

  if (minPrice.value !== undefined) filters.minPrice = minPrice.value;
  if (maxPrice.value !== undefined) filters.maxPrice = maxPrice.value;
  if (guestCount.value !== undefined) filters.minGuests = guestCount.value;
  if (checkInDate.value) filters.checkInDate = checkInDate.value;
  if (checkOutDate.value) filters.checkOutDate = checkOutDate.value;

  return filters;
};

const getBackendRegionCode = (
  code: string | undefined,
  level: 'province' | 'city' | 'district'
) => {
  if (!code) return undefined;

  if (level === 'province' && code.length === 2) {
    return `${code}0000`;
  }

  if (level === 'city' && code.length === 4) {
    return `${code}00`;
  }

  return code;
};

const getBackendRegionCodes = (region: string[]) => {
  return [
    getBackendRegionCode(region[0], 'province'),
    getBackendRegionCode(region[1], 'city'),
    getBackendRegionCode(region[2], 'district'),
  ];
};

const getLandmarkSuggestionCity = () => {
  const [provinceCode, cityCode, districtCode] = getBackendRegionCodes(selectedRegion.value);
  return districtCode || cityCode || provinceCode;
};

const clearResolvedLandmark = () => {
  resolvedLandmark.value = null;
  landmarkLat.value = undefined;
  landmarkLng.value = undefined;
};

const buildQueryState = (): QuerySyncFormState => ({
  searchMode: searchMode.value,
  hasResolvedLandmark: hasResolvedLandmark.value,
  selectedRegion: selectedRegion.value,
  minPrice: minPrice.value,
  maxPrice: maxPrice.value,
  guestCount: guestCount.value,
  checkInDate: checkInDate.value,
  checkOutDate: checkOutDate.value,
  nearbyRadius: nearbyRadius.value,
  landmarkLat: landmarkLat.value,
  landmarkLng: landmarkLng.value,
  resolvedLandmarkName: resolvedLandmarkName.value,
  viewportSearchEnabled: viewportSearchEnabled.value,
  mapView: mapView.value,
  globalSearchKeyword: globalSearchKeyword.value,
});

const applyHydratedState = (state: HydratedQueryState) => {
  selectedRegion.value = state.selectedRegion;
  minPrice.value = state.minPrice;
  maxPrice.value = state.maxPrice;
  guestCount.value = state.guestCount;
  checkInDate.value = state.checkInDate;
  checkOutDate.value = state.checkOutDate;
  dateRange.value = state.dateRange;
  searchMode.value = state.searchMode;
  nearbyRadius.value = state.nearbyRadius;
  landmarkLat.value = state.landmarkLat;
  landmarkLng.value = state.landmarkLng;
  isLandmarkDirty.value = state.isLandmarkDirty;
  viewportSearchEnabled.value = state.viewportSearchEnabled;
  if (state.resolvedLandmark) {
    resolvedLandmark.value = state.resolvedLandmark as any;
  } else {
    resolvedLandmark.value = null;
  }
  globalSearchKeyword.value = state.globalSearchKeyword;
};

const applyRouteStateFromQuery = async () => {
  if (!isMapReady.value) {
    return;
  }

  applyHydratedState(hydrateFiltersFromQuery());
  const restoredMapView = getMapViewFromQuery();
  const nextFilters = buildFiltersFromForm();
  const fitView = !restoredMapView && !viewportSearchEnabled.value;

  if (restoredMapView) {
    setMapView(restoredMapView);
  }

  if (searchMode.value === 'landmark' && hasResolvedLandmark.value) {
    await searchByLandmark(
      {
        latitude: landmarkLat.value!,
        longitude: landmarkLng.value!,
        name: resolvedLandmarkName.value || undefined,
      },
      {
        radius: nearbyRadius.value,
        filters: nextFilters,
        fitView: restoredMapView ? false : fitView,
      }
    );
    return;
  }

  if (searchMode.value === 'nearby') {
    await searchNearby({
      radius: nearbyRadius.value,
      filters: nextFilters,
      fitView: restoredMapView ? false : fitView,
    });
    return;
  }

  await resetSearchMode({ filters: nextFilters });
  await applySearchState(nextFilters, {
    viewportOnly: viewportSearchEnabled.value,
    fitView,
    skipCityCenter: Boolean(restoredMapView),
    mapView: restoredMapView,
  });
};

let dragStartY = 0;
let dragStartTranslateY = 0;
let dragListHeight = 0;

const onHandleTouchStart = (e: TouchEvent) => {
  isMobileListDragging.value = true;
  dragStartY = e.touches[0].clientY;
  if (listRef.value) {
    dragListHeight = listRef.value.offsetHeight;
  } else {
    dragListHeight = window.innerHeight * 0.7;
  }
  dragStartTranslateY = isMobileListExpanded.value ? 0 : dragListHeight - 60;
};

const onHandleTouchMove = (e: TouchEvent) => {
  if (!isMobileListDragging.value) return;
  const deltaY = e.touches[0].clientY - dragStartY;
  let newTranslateY = dragStartTranslateY + deltaY;
  newTranslateY = Math.max(0, Math.min(newTranslateY, dragListHeight - 60));
  const listEl = document.querySelector('.left-list') as HTMLElement | null;
  if (listEl) {
    listEl.style.transform = `translateY(${newTranslateY}px)`;
  }
};

const onHandleTouchEnd = (e: TouchEvent) => {
  if (!isMobileListDragging.value) return;
  isMobileListDragging.value = false;
  const deltaY = e.changedTouches[0].clientY - dragStartY;
  const listEl = document.querySelector('.left-list') as HTMLElement | null;
  if (listEl) {
    listEl.style.transform = '';
  }
  const threshold = (dragListHeight - 60) * 0.25;
  if (isMobileListExpanded.value) {
    if (deltaY > threshold) {
      isMobileListExpanded.value = false;
    }
  } else {
    if (deltaY < -threshold) {
      isMobileListExpanded.value = true;
    }
  }
};

const triggerAutoSearch = debounce(async () => {
  if (dateRange.value && dateRange.value.length === 2) {
    checkInDate.value = dateRange.value[0];
    checkOutDate.value = dateRange.value[1];
  } else {
    checkInDate.value = undefined;
    checkOutDate.value = undefined;
  }

  const filters = buildFiltersFromForm();

  // 保持当前搜索模式，仅更新筛选条件
  if (searchMode.value === 'landmark' && hasResolvedLandmark.value) {
    await searchByLandmark(
      { latitude: landmarkLat.value!, longitude: landmarkLng.value!, name: resolvedLandmarkName.value || undefined },
      { radius: nearbyRadius.value, filters, fitView: false }
    );
    await syncQueryFromForm(buildQueryState(), 'push', { skipRouteReplay: true });
    return;
  }

  if (searchMode.value === 'nearby') {
    await searchNearby({ radius: nearbyRadius.value, filters, fitView: false });
    await syncQueryFromForm(buildQueryState(), 'push', { skipRouteReplay: true });
    return;
  }

  // normal 模式
  await resetSearchMode({ filters });
  const changed = await syncQueryFromForm(buildQueryState(), 'push');
  if (!changed) {
    await applyRouteStateFromQuery();
  }
}, 500);

const handleDateChange = () => {
  triggerAutoSearch();
};

const handleRegionChange = () => {
  triggerAutoSearch();
};

const queryGlobalSuggestions = async (queryString: string, callback: any) => {
  const keyword = queryString.trim();
  if (!keyword) {
    callback([{ type: 'current', name: '我的当前位置' }]);
    return;
  }
  isSearchingSuggestions.value = true;
  try {
    const city = getLandmarkSuggestionCity();
    const suggestions = await searchAmapPoiSuggestions(keyword, { city, limit: 10 });
    const options = suggestions.map((s: any) => ({
      ...s,
      type: 'landmark',
      secondaryText: [s.district, s.address].filter(Boolean).join(' · ')
    }));
    callback(options);
  } catch (e) {
    callback([]);
  } finally {
    isSearchingSuggestions.value = false;
  }
};

const handleSelectSuggestion = async (item: any) => {
  if (item.type === 'current') {
    globalSearchKeyword.value = '我的当前位置';
    await handleUseCurrentLocation();
    return;
  }
  
  globalSearchKeyword.value = item.name;
  trackSearch({
    keyword: item.name,
    cityCode: getLandmarkSuggestionCity()
  });
  landmarkLat.value = item.latitude;
  landmarkLng.value = item.longitude;
  resolvedLandmark.value = { ...item, name: item.name, secondaryText: item.secondaryText };
  
  useClusterMode.value = false;
  await searchByLandmark(
    { latitude: item.latitude, longitude: item.longitude, name: item.name },
    { radius: nearbyRadius.value, filters: buildFiltersFromForm(), fitView: true }
  );
  await syncQueryFromForm(buildQueryState(), 'push', { skipRouteReplay: true });
};

const handleClearSearch = () => {
  globalSearchKeyword.value = '';
  clearResolvedLandmark();
  searchMode.value = 'normal';
  triggerAutoSearch();
};

const handleSearchEnter = async () => {
  const keyword = globalSearchKeyword.value.trim();
  if (!keyword) return;

  if (keyword === '我的当前位置') {
    await handleUseCurrentLocation();
    return;
  }

  // 如果当前输入匹配已解析的地标，仅刷新筛选条件
  if (searchMode.value === 'landmark' && resolvedLandmarkName.value === keyword && hasResolvedLandmark.value) {
    trackSearch({
      keyword,
      cityCode: getLandmarkSuggestionCity()
    });
    await searchByLandmark(
      { latitude: landmarkLat.value!, longitude: landmarkLng.value!, name: keyword },
      { radius: nearbyRadius.value, filters: buildFiltersFromForm(), fitView: true }
    );
    await syncQueryFromForm(buildQueryState(), 'push', { skipRouteReplay: true });
    return;
  }

  // 尝试搜索第一个候选作为兜底
  try {
    const city = getLandmarkSuggestionCity();
    const suggestions = await searchAmapPoiSuggestions(keyword, { city, limit: 1 });
    if (suggestions.length > 0) {
      const s = suggestions[0];
      await handleSelectSuggestion({ ...s, type: 'landmark', secondaryText: [s.district, s.address].filter(Boolean).join(' · ') });
    } else {
      trackSearch({
        keyword,
        cityCode: getLandmarkSuggestionCity()
      });
      ElMessage.warning('未找到匹配地点，请尝试其他关键词');
    }
  } catch (e) {
  }
};

const disabledDate = (time: Date) => {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return time.getTime() < today.getTime();
};

const handleViewportModeChange = async (enabled: boolean) => {
  viewportSearchEnabled.value = enabled;
  const changed = await syncQueryFromForm(buildQueryState(), 'push');
  if (!changed) {
    await applyRouteStateFromQuery();
  }
};

const handleReset = async () => {
  selectedRegion.value = [];
  globalSearchKeyword.value = '';
  dateRange.value = null;
  minPrice.value = undefined;
  maxPrice.value = undefined;
  guestCount.value = undefined;
  checkInDate.value = undefined;
  checkOutDate.value = undefined;
  isLandmarkDirty.value = false;
  clearResolvedLandmark();
  nearbyRadius.value = 5;

  await resetSearchMode({ filters: buildFiltersFromForm() });
  const changed = await syncQueryFromForm(buildQueryState(), 'push');
  if (!changed) {
    await applyRouteStateFromQuery();
  }
};

const handleRetryMap = async () => {
  if (!mapContainerRef.value) return;
  await initMap(mapContainerRef.value);
  hasRestoredRouteState.value = false;
  await applyRouteStateFromQuery();
  hasRestoredRouteState.value = true;
};

const handleRetrySearch = async () => {
  await retrySearch();
};

const handleCardClick = (homestay: MapHomestay) => {
  trackClick(homestay.id);
  selectHomestay(homestay.id);
  // 移动端自动收起列表，露出地图标记
  if (window.innerWidth <= 768) {
    isMobileListExpanded.value = false;
  }
};

const handleViewDetail = (id: number) => {
  trackClick(id);
  void router.push(`/homestays/${id}`);
};

const handleCardHover = (id: number | null) => {
  hoverHomestay(id);
};

const handleRadiusChange = async () => {
  if (searchMode.value === 'normal') {
    return;
  }

  const changed = await syncQueryFromForm(buildQueryState(), 'push');
  if (!changed) {
    await applyRouteStateFromQuery();
  }
};

const handleUseCurrentLocation = async () => {
  const location = await locateUser({ recenter: true });
  if (!location) {
    ElMessage.error(locationError.value || '定位失败，请检查浏览器权限设置');
    globalSearchKeyword.value = '';
    return;
  }

  globalSearchKeyword.value = '我的当前位置';
  rememberNormalSearchSnapshot();
  useClusterMode.value = false;
  await searchNearby({
    latitude: location.latitude,
    longitude: location.longitude,
    radius: nearbyRadius.value,
    source: 'user',
    filters: buildFiltersFromForm(),
    fitView: true,
  });
  await syncQueryFromForm(buildQueryState(), 'push', { skipRouteReplay: true });
};

const handleToggleCluster = async () => {
  useClusterMode.value = !useClusterMode.value;

  if (useClusterMode.value) {
    await resetSearchMode({ filters: buildFiltersFromForm() });
    await loadClusters();
    return;
  }

  await loadHomestays({ fitView: true });
};

const handleExitSpecialSearch = async () => {
  globalSearchKeyword.value = '';
  clearResolvedLandmark();
  await resetSearchMode({ filters: buildFiltersFromForm() });
  const changed = await syncQueryFromForm(buildQueryState(), 'push');
  if (!changed) {
    await applyRouteStateFromQuery();
  }
};

const scrollToSelectedCard = () => {
  if (!listRef.value || !selectedHomestayId.value) return;

  const card = listRef.value.querySelector(`[data-id="${selectedHomestayId.value}"]`);
  if (card) {
    card.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }
};

watch(selectedHomestayId, (newId) => {
  if (newId) {
    scrollToSelectedCard();
    // 移动端：当点击地图锚点时，自动弹出一半抽屉展示详情
    if (window.innerWidth <= 768) {
      isMobileListExpanded.value = true;
    }
  }
});

watch(
  () => route.query,
  async () => {
    if (!hasRestoredRouteState.value || !isMapReady.value) {
      return;
    }

    if (skipNextRouteReplay.value) {
      skipNextRouteReplay.value = false;
      return;
    }

    await applyRouteStateFromQuery();
  },
  { deep: true }
);

watch(
  mapView,
  async (nextMapView) => {
    if (!hasRestoredRouteState.value || !isMapReady.value) {
      return;
    }

    if (
      nextMapView.centerLat === undefined
      || nextMapView.centerLng === undefined
      || nextMapView.zoom === undefined
    ) {
      return;
    }

    await syncQueryFromForm(buildQueryState(), 'replace', { skipRouteReplay: true });
  },
  { deep: true }
);

onMounted(async () => {
  setOnViewDetail((id: number) => {
    void router.push(`/homestays/${id}`);
  });

  if (mapContainerRef.value) {
    const restoredMapView = getMapViewFromQuery();
    await initMap(mapContainerRef.value, restoredMapView);
  }

  if (isMapReady.value) {
    await applyRouteStateFromQuery();
    hasRestoredRouteState.value = true;
  }
});

onUnmounted(() => {
  destroyMap();
});
</script>

<style scoped>
.map-search-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 64px);
  overflow: hidden;
  background: #f8fafc;
}

.top-filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 24px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(226, 232, 240, 0.6);
  z-index: 10;
  flex-shrink: 0;
  flex-wrap: wrap;
  box-shadow: 0 4px 20px -8px rgba(0, 0, 0, 0.05);
}

.search-wrapper {
  width: 320px;
}
.smart-search-input {
  width: 100%;
}

.suggestion-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 0;
}
.suggestion-icon {
  font-size: 16px;
  color: #94a3b8;
  background: #f1f5f9;
  padding: 8px;
  border-radius: 50%;
}
.suggestion-content {
  display: flex;
  flex-direction: column;
}
.suggestion-name {
  font-size: 14px;
  color: #334155;
}
.suggestion-name.is-current {
  color: #3b82f6;
  font-weight: 500;
}
.suggestion-desc {
  font-size: 12px;
  color: #64748b;
}

.filter-btn {
  border-radius: 20px;
  padding: 8px 16px;
}
.filter-btn.is-active {
  border-color: #3b82f6;
  color: #3b82f6;
  background: #eff6ff;
}

.popover-content {
  padding: 8px;
}

.popover-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.price-range-inputs {
  display: flex;
  align-items: center;
  gap: 8px;
}
.range-sep {
  color: #94a3b8;
}

.clear-btn {
  color: #64748b;
}

.top-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 16px;
}

.main-content {
  display: flex;
  flex: 1;
  overflow: hidden;
  position: relative;
}

.left-list {
  width: 400px;
  background: #fff;
  border-right: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  z-index: 5;
  transition: transform 0.3s ease;
}

.mobile-drawer-handle {
  display: none;
}

.list-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.list-header {
  padding: 16px 20px;
  border-bottom: 1px solid #f1f5f9;
  font-weight: 500;
  color: #334155;
}

.list-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.right-map {
  flex: 1;
  position: relative;
}

.map-context-card {
  position: absolute;
  top: 16px;
  left: 16px;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(0, 0, 0, 0.05);
  max-width: min(400px, calc(100% - 32px));
}
.map-context-card__body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.map-context-card__title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.radius-control {
  display: flex;
  align-items: center;
  gap: 10px;
}
.radius-label {
  font-size: 12px;
  color: #64748b;
  white-space: nowrap;
}

.map-locate-btn {
  position: absolute;
  bottom: 80px;
  right: 16px;
  z-index: 20;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #334155;
  font-size: 20px;
  transition: all 0.2s ease;
}
.map-locate-btn:active {
  transform: scale(0.95);
  background: #f1f5f9;
}

.region-cascader {
  width: 200px;
}

.mobile-filter-btn {
  display: none;
}

.mobile-filter-content {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  height: 100%;
}
.drawer-title {
  margin: 0 0 8px 0;
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
}
.mobile-filter-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.section-label {
  font-size: 14px;
  font-weight: 500;
  color: #475569;
}
.switch-section {
  flex-direction: row !important;
  align-items: center;
  justify-content: space-between;
  padding-top: 12px;
  border-top: 1px solid #e2e8f0;
}
.mobile-filter-footer {
  display: flex;
  gap: 12px;
  margin-top: auto;
  padding-top: 16px;
}
.mobile-filter-footer .el-button {
  flex: 1;
}

.state-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #64748b;
  gap: 16px;
}
.empty-actions {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}

/* Skeleton Loading */
.skeleton-wrapper {
  padding: 0;
}
.skeleton-text {
  height: 16px;
  background: linear-gradient(90deg, #f1f5f9 25%, #e2e8f0 50%, #f1f5f9 75%);
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.5s infinite;
  border-radius: 4px;
}
.skeleton-card {
  display: flex;
  gap: 12px;
  padding: 12px;
  background: #fff;
  border-radius: 12px;
}
.skeleton-image {
  width: 100px;
  height: 100px;
  border-radius: 8px;
  background: linear-gradient(90deg, #f1f5f9 25%, #e2e8f0 50%, #f1f5f9 75%);
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.5s infinite;
  flex-shrink: 0;
}
.skeleton-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  justify-content: center;
}
.skeleton-line {
  height: 12px;
  border-radius: 4px;
  background: linear-gradient(90deg, #f1f5f9 25%, #e2e8f0 50%, #f1f5f9 75%);
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.5s infinite;
}
@keyframes skeleton-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.map-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(4px);
  z-index: 100;
}

/* Mobile Responsiveness */
@media (max-width: 768px) {
  .top-filter-bar {
    padding: 8px 12px;
    gap: 8px;
  }
  .search-wrapper {
    width: 100%;
  }
  .filter-item,
  .clear-btn,
  .top-actions {
    display: none;
  }
  .mobile-filter-btn {
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }
  .map-locate-btn {
    bottom: 100px;
  }
  .left-list.dragging {
    transition: none !important;
  }

  .map-context-card {
    top: 10px;
    left: 10px;
    right: 10px;
    max-width: none;
    padding: 8px 12px;
    gap: 8px;
  }
  .map-context-card__title {
    font-size: 13px;
  }
  .radius-control {
    gap: 8px;
  }

  .left-list {
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    width: 100%;
    height: 75vh;
    border-radius: 24px 24px 0 0;
    box-shadow: 0 -8px 30px rgba(0, 0, 0, 0.12);
    transform: translateY(calc(100% - 70px));
    z-index: 30;
    transition: transform 0.4s cubic-bezier(0.16, 1, 0.3, 1);
  }
  
  .left-list.is-mobile-expanded {
    transform: translateY(0);
  }

  .mobile-drawer-handle {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 12px;
    cursor: pointer;
    background: #fff;
    border-radius: 20px 20px 0 0;
    border-bottom: 1px solid #f1f5f9;
  }
  
  .handle-bar {
    width: 40px;
    height: 4px;
    background: #cbd5e1;
    border-radius: 2px;
    margin-bottom: 8px;
  }
  
  .handle-text {
    font-size: 14px;
    font-weight: 600;
    color: #334155;
  }
  
  .list-header {
    display: none;
  }
}
</style>

<style>
.map-price-marker {
  background: #fff;
  padding: 6px 12px;
  border-radius: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  font-size: 13px;
  font-weight: 600;
  color: #222;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  white-space: nowrap;
  border: 2px solid transparent;
}
.map-price-marker:hover,
.map-price-marker.active {
  transform: scale(1.1);
  background: #ff385c;
  color: #fff;
  box-shadow: 0 4px 12px rgba(255, 56, 92, 0.4);
}
.map-price-marker.selected {
  background: #ff385c;
  color: #fff;
  border-color: #ff385c;
  transform: scale(1.15);
}
.map-info-window { width: 240px; font-family: sans-serif; }
.map-info-window .info-image { width: 100%; height: 140px; background-size: cover; border-radius: 8px 8px 0 0; }
.map-info-window .info-content { padding: 12px; }
.map-info-window h4 { margin: 0 0 8px 0; font-size: 14px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.map-info-window .info-price { margin: 0 0 4px 0; font-size: 16px; color: #ff385c; font-weight: 600; }
.map-info-window .info-rating { margin: 0; font-size: 12px; color: #717171; }
.info-detail-btn {
  width: 100%;
  padding: 8px 0;
  background: #ff385c;
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.2s ease;
}
.info-detail-btn:hover {
  background: #e0314f;
}
</style>
