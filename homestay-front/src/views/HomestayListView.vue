<template>
  <div class="homestay-list-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title">{{ pageTitle }}</h1>
      <div
        v-if="route.query.keyword || route.query.featured || route.query.status"
        class="breadcrumb"
      >
        <el-button link @click="router.push('/')">返回首页</el-button>
      </div>
    </div>

    <!-- 搜索栏 -->
    <SearchBar
      @search="handleSearchBarSearch"
      @reset="handleSearchBarReset"
      :loading="loading"
      :initialParams="searchParams"
    />

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="filter-group">
        <span class="filter-label">价格区间</span>
        <el-input-number
          v-model="minPrice"
          :min="0"
          :step="50"
          placeholder="最低"
          size="small"
          controls-position="right"
          style="width: 90px"
          @change="handleFilterChange"
        />
        <span class="filter-separator">-</span>
        <el-input-number
          v-model="maxPrice"
          :min="0"
          :step="50"
          placeholder="最高"
          size="small"
          controls-position="right"
          style="width: 90px"
          @change="handleFilterChange"
        />
      </div>
      <div class="filter-group">
        <span class="filter-label">设施</span>
        <el-select
          v-model="selectedAmenities"
          multiple
          collapse-tags
          collapse-tags-tooltip
          placeholder="选择设施"
          size="small"
          style="width: 160px"
          @change="handleFilterChange"
        >
          <el-option
            v-for="amenity in amenityOptions"
            :key="amenity.value"
            :label="amenity.label"
            :value="amenity.value"
          />
        </el-select>
      </div>
      <div class="filter-group">
        <span class="filter-label">房源类型</span>
        <el-select
          v-model="selectedType"
          placeholder="全部类型"
          clearable
          size="small"
          style="width: 140px"
          @change="handleFilterChange"
        >
          <el-option label="合住房间" value="合住房间" />
          <el-option label="独立房间" value="独立房间" />
          <el-option label="别墅" value="别墅" />
          <el-option label="酒店式公寓" value="酒店式公寓" />
          <el-option label="客栈房间" value="客栈房间" />
          <el-option label="Loft公寓" value="Loft公寓" />
          <el-option label="整套房子" value="整套房子" />
          <el-option label="整套公寓" value="整套公寓" />
        </el-select>
      </div>
      <div class="filter-group">
        <span class="filter-label">排序</span>
        <el-select
          v-model="currentSort"
          placeholder="排序方式"
          size="small"
          style="width: 140px"
          @change="handleFilterChange"
        >
          <el-option label="最新发布" value="id,desc" />
          <el-option label="价格低到高" value="price,asc" />
          <el-option label="价格高到低" value="price,desc" />
          <el-option label="评分高到低" value="rating,desc" />
        </el-select>
      </div>
      <el-button link size="small" type="primary" @click="handleFilterReset">重置筛选</el-button>
    </div>

    <!-- 已选筛选条件标签 -->
    <div v-if="activeFilterChips.length > 0" class="filter-chips">
      <span class="chips-label">已选条件：</span>
      <div class="chips-list">
        <span
          v-for="chip in activeFilterChips"
          :key="chip.key"
          class="filter-chip"
          @click="chip.onRemove"
        >
          {{ chip.label }}
          <svg
            class="chip-close"
            width="12"
            height="12"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2.5"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M18 6 6 18M6 6l12 12" />
          </svg>
        </span>
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="3" animated />
      <el-skeleton :rows="3" animated />
      <el-skeleton :rows="3" animated />
    </div>

    <!-- 错误状态 -->
    <div v-else-if="searchError" class="error-container">
      <el-result icon="error" title="加载失败" :sub-title="searchError">
        <template #extra>
          <el-button type="primary" @click="loadHomestays">重新加载</el-button>
          <el-button @click="handleSearchBarReset">重置搜索</el-button>
        </template>
      </el-result>
    </div>

    <!-- 空状态 -->
    <div v-else-if="homestays.length === 0" class="empty-container">
      <el-empty description="没有找到符合条件的民宿" />
    </div>

    <!-- 民宿列表 -->
    <div v-else class="homestay-list">
      <el-row :gutter="20">
        <el-col v-for="homestay in homestays" :key="homestay.id" :xs="24" :sm="12" :md="8" :lg="6">
          <HomestayCard :homestay="homestay" :homestay-types="homestayTypes" />
        </el-col>
      </el-row>
    </div>

    <div class="pagination-container">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[12, 24, 36, 48]"
        layout="total, sizes, prev, pager, next"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, watch } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useUserStore } from "@/stores/user";
import {
  getActiveHomestays,
  getHomestayTypes,
  getHomestayGroups,
  searchHomestays,
  type HomestaySearchRequest,
} from "@/api/homestay";
import { getHomestayAmenities } from "@/api/homestay/meta";
import { getPopularHomestaysPage, getMyPersonalizedRecommendations } from "@/api/recommendation";
import { trackSearch } from "@/api/tracking";
import HomestayCard from "@/components/homestay/HomestayCard.vue";
import SearchBar from "@/components/SearchBar.vue";
import { codeToText } from "element-china-area-data";

// 类型定义
interface Homestay {
  id: number;
  title: string;
  description?: string;
  pricePerNight: number;
  price?: string;
  maxGuests: number;
  bedrooms: number;
  beds: number;
  bathrooms: number;
  amenities: string[];
  images: string[];
  rating?: number;
  reviewCount?: number;
  type: string;
  status: string;
  featured?: boolean;
  propertyType?: string;
  distanceFromCenter?: number;
  latitude: number;
  longitude: number;
  hostName: string;
  hostId: number;
  coverImage?: string;
  provinceCode?: string;
  cityCode?: string;
  districtCode?: string;
  addressDetail?: string;
}

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();

interface SearchBarParams {
  keyword: string;
  selectedRegion: string[];
  checkIn: string | null;
  checkOut: string | null;
  guestCount: number;
}

type RouteQuery = Record<string, unknown>;

const DEFAULT_PAGE_SIZE = 12;

// 筛选相关状态
const minPrice = ref<number | null>(null);
const maxPrice = ref<number | null>(null);
const selectedAmenities = ref<string[]>([]);
const selectedType = ref<string>("");
const currentSort = ref("id,desc");
const amenityOptions = ref<Array<{ value: string; label: string }>>([]);

const getQueryValue = (value: unknown): string | undefined => {
  if (Array.isArray(value)) return value[0] ? String(value[0]) : undefined;
  return value !== undefined && value !== null ? String(value) : undefined;
};

const getPositiveNumber = (value: unknown, fallback: number) => {
  const parsed = Number(getQueryValue(value));
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
};

const cleanQuery = (query: RouteQuery): Record<string, string> => {
  return Object.entries(query).reduce<Record<string, string>>((result, [key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      result[key] = String(value);
    }
    return result;
  }, {});
};

const withPagination = (query: Record<string, unknown>, page: number, size: number) => {
  return cleanQuery({
    ...query,
    page: page > 1 ? page : undefined,
    size: size !== DEFAULT_PAGE_SIZE ? size : undefined,
  });
};

const hasSearchTrigger = (params: SearchBarParams) => {
  return params.keyword.trim().length > 0 || params.selectedRegion.length > 0;
};

const buildSearchQuery = (params: SearchBarParams, page = 1, size = DEFAULT_PAGE_SIZE) => {
  return cleanQuery({
    keyword: params.keyword.trim() || undefined,
    region: params.selectedRegion.length ? params.selectedRegion.join(",") : undefined,
    checkIn: params.checkIn || undefined,
    checkOut: params.checkOut || undefined,
    guestCount: params.guestCount > 1 ? params.guestCount : undefined,
    type: selectedType.value || undefined,
    minPrice:
      minPrice.value !== null && minPrice.value !== undefined ? String(minPrice.value) : undefined,
    maxPrice:
      maxPrice.value !== null && maxPrice.value !== undefined ? String(maxPrice.value) : undefined,
    amenities: selectedAmenities.value.length ? selectedAmenities.value.join(",") : undefined,
    sort: currentSort.value !== "id,desc" ? currentSort.value : undefined,
    search:
      hasSearchTrigger(params) ||
      selectedType.value ||
      minPrice.value ||
      maxPrice.value ||
      selectedAmenities.value.length > 0
        ? "true"
        : undefined,
    page: page > 1 ? page : undefined,
    size: size !== DEFAULT_PAGE_SIZE ? size : undefined,
  });
};

const parseSearchParamsFromRoute = (): SearchBarParams => ({
  keyword: getQueryValue(route.query.keyword) || "",
  selectedRegion: getQueryValue(route.query.region)?.split(",").filter(Boolean) || [],
  checkIn: getQueryValue(route.query.checkIn) || null,
  checkOut: getQueryValue(route.query.checkOut) || null,
  guestCount: getPositiveNumber(route.query.guestCount, 1),
});

const parseFiltersFromRoute = () => {
  minPrice.value = route.query.minPrice ? Number(getQueryValue(route.query.minPrice)) : null;
  maxPrice.value = route.query.maxPrice ? Number(getQueryValue(route.query.maxPrice)) : null;
  selectedAmenities.value = getQueryValue(route.query.amenities)?.split(",").filter(Boolean) || [];
  selectedType.value = getQueryValue(route.query.type) || "";
  const sort = getQueryValue(route.query.sort);
  if (sort) {
    currentSort.value = sort;
  } else {
    const sortBy = getQueryValue(route.query.sortBy) || "id";
    const sortDirection = getQueryValue(route.query.sortDirection) || "desc";
    currentSort.value = `${sortBy},${sortDirection}`;
  }
};

// 已选筛选条件标签（与 search store 的 hasSearchConditions 对齐）
const activeFilterChips = computed(() => {
  const chips: Array<{ key: string; label: string; onRemove: () => void }> = [];

  if (minPrice.value !== null && minPrice.value !== undefined) {
    const max = maxPrice.value;
    const label =
      max !== null && max !== undefined ? `¥${minPrice.value} - ¥${max}` : `¥${minPrice.value} 起`;
    chips.push({
      key: "price",
      label,
      onRemove: () => {
        minPrice.value = null;
        maxPrice.value = null;
        handleFilterChange();
      },
    });
  } else if (maxPrice.value !== null && maxPrice.value !== undefined) {
    chips.push({
      key: "price",
      label: `¥${maxPrice.value} 以下`,
      onRemove: () => {
        maxPrice.value = null;
        handleFilterChange();
      },
    });
  }

  if (selectedType.value) {
    const typeLabel =
      homestayTypes.value.find((t) => (t.code || t.value) === selectedType.value)?.name ||
      selectedType.value;
    chips.push({
      key: "type",
      label: `类型：${typeLabel}`,
      onRemove: () => {
        selectedType.value = "";
        handleFilterChange();
      },
    });
  }

  selectedAmenities.value.forEach((val) => {
    const option = amenityOptions.value.find((o) => o.value === val);
    chips.push({
      key: `amenity-${val}`,
      label: option?.label || val,
      onRemove: () => {
        selectedAmenities.value = selectedAmenities.value.filter((v) => v !== val);
        handleFilterChange();
      },
    });
  });

  if (currentSort.value !== "id,desc") {
    const sortMap: Record<string, string> = {
      "price,asc": "价格低到高",
      "price,desc": "价格高到低",
      "rating,desc": "评分高到低",
    };
    chips.push({
      key: "sort",
      label: `排序：${sortMap[currentSort.value] || currentSort.value}`,
      onRemove: () => {
        currentSort.value = "id,desc";
        handleFilterChange();
      },
    });
  }

  return chips;
});

// 页面标题计算
const pageTitle = computed(() => {
  const type = getQueryValue(route.query.type);
  const featured = getQueryValue(route.query.featured);
  const personalized = getQueryValue(route.query.personalized);
  const status = getQueryValue(route.query.status);
  const keyword = getQueryValue(route.query.keyword);
  const region = getQueryValue(route.query.region);

  if (type === "popular") return "热门民宿";
  if (featured === "true") return "精选房源";
  if (personalized === "true") return "猜你喜欢";
  if (status === "ACTIVE") return "最新上架";
  if (type) {
    const typeLabel = homestayTypes.value.find((t) => (t.code || t.value) === type)?.name || type;
    return `${typeLabel}民宿`;
  }
  if (keyword) return `"${keyword}" 的搜索结果`;
  if (region) {
    // 将地区代码转换为地名
    if (region.includes(",")) {
      // 多级地区：省,市,区
      const regionCodes = region.split(",");
      const regionNames = regionCodes.map((code) => codeToText[code] || code).filter(Boolean);
      return `${regionNames.join("")} 的民宿`;
    } else {
      // 单个地区代码
      const regionName = codeToText[region] || region;
      return `${regionName} 的民宿`;
    }
  }
  return "全部民宿";
});

// 搜索相关数据 - 从 URL 同步
const searchParams = ref<SearchBarParams>(parseSearchParamsFromRoute());

// 分页相关
const currentPage = ref(getPositiveNumber(route.query.page, 1));
const pageSize = ref(getPositiveNumber(route.query.size, DEFAULT_PAGE_SIZE));
const total = ref(0);

// 加载状态
const loading = ref(false);

// 错误状态
const searchError = ref<string | null>(null);

// 房源列表数据
const homestays = ref<Homestay[]>([]);
let latestLoadRequestId = 0;

// 房源类型数据
const homestayTypes = ref<any[]>([]);

// 分组数据
const groupOptions = ref<any[]>([]);

// 检查是否有搜索条件（与 search store 的 isSearchMode 逻辑对齐）
const hasSearchConditions = () => {
  return (
    getQueryValue(route.query.search) === "true" ||
    Boolean(getQueryValue(route.query.keyword)?.trim()) ||
    Boolean(getQueryValue(route.query.region)) ||
    Boolean(getQueryValue(route.query.checkIn)) ||
    Boolean(getQueryValue(route.query.checkOut)) ||
    Boolean(getQueryValue(route.query.minPrice)) ||
    Boolean(getQueryValue(route.query.maxPrice)) ||
    Boolean(getQueryValue(route.query.amenities)) ||
    Boolean(getQueryValue(route.query.type)) ||
    Boolean(getQueryValue(route.query.minRating)) ||
    Boolean(getQueryValue(route.query.groupId))
  );
};

const buildSearchRequest = (): HomestaySearchRequest & { groupId?: number } => {
  const params = searchParams.value;
  const request: HomestaySearchRequest & { groupId?: number } = {
    keyword: params.keyword.trim() || undefined,
    minPrice: route.query.minPrice ? Number(getQueryValue(route.query.minPrice)) : undefined,
    maxPrice: route.query.maxPrice ? Number(getQueryValue(route.query.maxPrice)) : undefined,
    requiredAmenities: getQueryValue(route.query.amenities)?.split(",").filter(Boolean),
    page: currentPage.value - 1,
    size: pageSize.value,
    sort:
      getQueryValue(route.query.sort) ||
      `${getQueryValue(route.query.sortBy) || "id"},${getQueryValue(route.query.sortDirection) || "desc"}`,
  };

  if (selectedType.value && selectedType.value !== "popular") {
    request.propertyType = selectedType.value;
  }

  const groupId = getQueryValue(route.query.groupId);
  if (groupId) {
    request.groupId = Number(groupId);
  }

  if (params.selectedRegion.length >= 1) {
    request.provinceCode = params.selectedRegion[0];
  }
  if (params.selectedRegion.length >= 2) {
    request.cityCode = params.selectedRegion[1];
  }
  if (params.selectedRegion.length >= 3) {
    request.districtCode = params.selectedRegion[2];
  }
  if (params.checkIn) {
    request.checkInDate = params.checkIn;
  }
  if (params.checkOut) {
    request.checkOutDate = params.checkOut;
  }
  if (params.guestCount > 1) {
    request.minGuests = params.guestCount;
  }

  return request;
};

const getArrayPage = <T,>(items: T[]) => {
  const startIndex = (currentPage.value - 1) * pageSize.value;
  return items.slice(startIndex, startIndex + pageSize.value);
};

const applyResponseData = (data: any, options: { paginateArrayFallback?: boolean } = {}) => {
  const paginateArrayFallback = options.paginateArrayFallback ?? false;

  if (data && typeof data === "object" && Array.isArray(data.content)) {
    homestays.value = data.content;
    total.value = Number(data.totalElements ?? data.total ?? data.content.length);
    return;
  }

  if (data && typeof data === "object" && Array.isArray(data.data)) {
    homestays.value = data.data;
    total.value = Number(data.total ?? data.totalElements ?? data.data.length);
    return;
  }

  if (Array.isArray(data)) {
    homestays.value = paginateArrayFallback ? getArrayPage<Homestay>(data) : data;
    total.value = data.length;
    return;
  }

  homestays.value = [];
  total.value = 0;
};

// 数据加载方法
const loadHomestays = async () => {
  const requestId = ++latestLoadRequestId;
  loading.value = true;
  console.log("loadHomestays 调用 - 路由参数:", route.query);
  console.log("loadHomestays 调用 - 搜索参数:", searchParams.value);
  console.log("是否有搜索条件:", hasSearchConditions());

  searchError.value = null;
  try {
    let response;

    // 优先检查搜索标记或搜索条件
    if (hasSearchConditions()) {
      // 搜索模式：使用搜索API
      const searchRequest = buildSearchRequest();
      console.log("发送搜索请求:", searchRequest);
      response = await searchHomestays(searchRequest);
      console.log("搜索响应:", response);
    } else if (getQueryValue(route.query.type) === "popular") {
      // 加载热门民宿（使用分页API）
      response = await getPopularHomestaysPage({
        page: currentPage.value - 1,
        size: pageSize.value,
      });
    } else if (getQueryValue(route.query.featured) === "true") {
      // 加载推荐民宿（使用分页API）
      response = await getActiveHomestays({
        page: currentPage.value - 1,
        size: pageSize.value,
        featured: true,
      });
    } else if (getQueryValue(route.query.personalized) === "true") {
      // 加载个性化推荐民宿
      if (!userStore.isAuthenticated) {
        applyResponseData([]);
        return;
      }
      // 注意：个性化推荐目前没有分页API，使用简单版本
      response = await getMyPersonalizedRecommendations(
        50 // 获取更多结果用于前端分页
      );
    } else if (getQueryValue(route.query.status) === "ACTIVE") {
      // 加载最新上架 - 使用正确的API函数
      response = await getActiveHomestays({
        page: currentPage.value - 1, // 后端分页从0开始
        size: pageSize.value,
      });
    } else {
      // 默认情况：加载所有活跃房源
      response = await getActiveHomestays({
        page: currentPage.value - 1,
        size: pageSize.value,
      });
    }

    // 后端分页对象优先直接使用；仅数组老接口走前端分页兼容。
    if (requestId !== latestLoadRequestId) {
      return;
    }

    applyResponseData(response.data, {
      paginateArrayFallback:
        hasSearchConditions() || getQueryValue(route.query.personalized) === "true",
    });
    console.log(
      `列表数据处理: 第${currentPage.value}页，共${total.value}条，当前显示${homestays.value.length}条`
    );
  } catch (error: any) {
    console.error("加载民宿数据失败:", error);
    const msg = error?.response?.data?.message || "搜索失败，请检查网络后重试";
    searchError.value = msg;
    homestays.value = [];
    total.value = 0;
  } finally {
    if (requestId === latestLoadRequestId) {
      loading.value = false;
    }
  }
};

// 获取房源类型数据
const loadHomestayTypes = async () => {
  try {
    const response = await getHomestayTypes();
    homestayTypes.value = response || [];
  } catch (error) {
    console.error("获取房源类型失败:", error);
  }
};

// 获取分组数据
const loadGroups = async () => {
  if (!userStore.isLandlord) return;
  try {
    const groups = await getHomestayGroups();
    groupOptions.value = groups.filter((g: any) => g.enabled);
  } catch (error) {
    console.error("获取分组列表失败:", error);
  }
};

// 搜索栏搜索处理
const handleSearchBarSearch = (params: SearchBarParams) => {
  console.log("执行搜索，参数:", params);

  searchParams.value = {
    ...params,
    keyword: params.keyword.trim(),
    selectedRegion: [...params.selectedRegion],
    guestCount: params.guestCount || 1,
  };
  currentPage.value = 1;

  const newQuery = buildSearchQuery(searchParams.value, 1, pageSize.value);
  console.log("设置新的路由参数:", newQuery);
  router.replace({
    path: "/homestays",
    query: newQuery,
  });
};

// 搜索栏重置处理
const handleSearchBarReset = () => {
  console.log("重置搜索");

  // 重置搜索参数
  searchParams.value = {
    keyword: "",
    selectedRegion: [],
    checkIn: null,
    checkOut: null,
    guestCount: 1,
  };

  // 重置筛选条件
  minPrice.value = null;
  maxPrice.value = null;
  selectedAmenities.value = [];
  currentSort.value = "id,desc";

  currentPage.value = 1;
  pageSize.value = DEFAULT_PAGE_SIZE;

  router.replace({ path: "/homestays" });
};

// 筛选条件变化处理
const handleFilterChange = () => {
  currentPage.value = 1;
  const newQuery = buildSearchQuery(searchParams.value, 1, pageSize.value);
  router.replace({
    path: "/homestays",
    query: newQuery,
  });
  // 上报搜索埋点（筛选操作也是搜索意图的信号）
  trackSearch({
    keyword: searchParams.value.keyword.trim() || undefined,
    cityCode:
      searchParams.value.selectedRegion[1] || searchParams.value.selectedRegion[0] || undefined,
  });
};

// 重置筛选条件（保留搜索栏条件）
const handleFilterReset = () => {
  selectedType.value = "";
  minPrice.value = null;
  maxPrice.value = null;
  selectedAmenities.value = [];
  currentSort.value = "id,desc";
  handleFilterChange();
};

// 加载设施选项
const loadAmenities = async () => {
  try {
    const response = await getHomestayAmenities();
    if (response && response.data) {
      if (Array.isArray(response.data)) {
        amenityOptions.value = response.data;
      } else if (response.data.data && Array.isArray(response.data.data)) {
        amenityOptions.value = response.data.data;
      }
    }
  } catch (error) {
    console.error("获取设施列表失败:", error);
    // 使用默认设施选项兜底
    amenityOptions.value = [
      { value: "WIFI", label: "无线网络" },
      { value: "AC", label: "空调" },
      { value: "TV", label: "电视" },
      { value: "KITCHEN", label: "厨房" },
      { value: "WASHER", label: "洗衣机" },
      { value: "PARKING", label: "停车位" },
      { value: "ELEVATOR", label: "电梯" },
      { value: "POOL", label: "游泳池" },
      { value: "GYM", label: "健身房" },
      { value: "BREAKFAST", label: "含早餐" },
    ];
  }
};

// 分页处理
const handleSizeChange = (val: number) => {
  pageSize.value = val;
  currentPage.value = 1;
  router.replace({
    path: "/homestays",
    query: withPagination(route.query, 1, val),
  });
};

const handleCurrentChange = (val: number) => {
  currentPage.value = val;
  console.log("页码变化到:", val);
  router.replace({
    path: "/homestays",
    query: withPagination(route.query, val, pageSize.value),
  });
};

// 初始化路由参数
const initializeFromRoute = () => {
  searchParams.value = parseSearchParamsFromRoute();
  currentPage.value = getPositiveNumber(route.query.page, 1);
  pageSize.value = getPositiveNumber(route.query.size, DEFAULT_PAGE_SIZE);
  parseFiltersFromRoute();
};

// 监听路由变化
watch(
  () => route.fullPath,
  () => {
    console.log("路由参数变化:", route.query);
    initializeFromRoute();
    loadHomestays();
  },
  { immediate: true }
);

// 初始化
onMounted(() => {
  loadHomestayTypes();
  loadGroups();
  loadAmenities();
  console.log("路由查询参数:", route.query);
});
</script>

<style scoped>
.homestay-list-container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.page-title {
  margin: 0;
  font-size: 28px;
  font-weight: 600;
  color: #222;
}

.breadcrumb {
  color: #717171;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  padding: 12px 16px;
  background-color: #f8f9fa;
  border-radius: 8px;
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-label {
  font-size: 14px;
  color: #606266;
  white-space: nowrap;
}

.filter-separator {
  color: #909399;
  font-size: 14px;
}

@media (max-width: 768px) {
  .filter-bar {
    gap: 12px;
  }
  .filter-group {
    flex: 1 1 calc(50% - 12px);
    min-width: 140px;
  }
}

.loading-container {
  margin: 40px 0;
}

.empty-container {
  margin: 60px 0;
  text-align: center;
}

.homestay-list {
  margin-top: 20px;
}

/* HomestayCard 组件已包含所有卡片样式 */

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

.error-container {
  margin: 40px 0;
  padding: 20px;
  background: var(--color-surface-elevated);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
}

/* ─── 已选筛选条件标签 ─── */
.filter-chips {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-bottom: var(--space-5);
  padding: var(--space-3) var(--space-4);
  background: var(--color-neutral-50);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-neutral-200);
}

.chips-label {
  font-family: var(--font-body);
  font-size: var(--text-body-sm);
  font-weight: var(--weight-medium);
  color: var(--color-neutral-600);
  white-space: nowrap;
}

.chips-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.filter-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-1) var(--space-3);
  background: var(--color-primary-50);
  color: var(--color-primary-700);
  border: 1px solid var(--color-primary-200);
  border-radius: var(--radius-full);
  font-family: var(--font-body);
  font-size: var(--text-body-sm);
  font-weight: var(--weight-medium);
  cursor: pointer;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    transform var(--duration-normal) var(--ease-spring);
}

.filter-chip:hover {
  background: var(--color-primary-100);
  border-color: var(--color-primary-300);
  transform: translateY(-1px);
}

.chip-close {
  flex-shrink: 0;
  opacity: 0.7;
  transition: opacity var(--duration-fast) var(--ease-out);
}

.filter-chip:hover .chip-close {
  opacity: 1;
}

@media (max-width: 768px) {
  .filter-chips {
    padding: var(--space-2) var(--space-3);
  }
}
</style>
