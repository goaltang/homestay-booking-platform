import { ref, computed } from "vue";

// 热门目的地接口
export interface PopularDestination {
  label: string;
  value: string[]; // [provinceCode, cityCode, districtCode?]
}

// 默认热门目的地（当API不可用时的后备）
const defaultPopularDestinations: PopularDestination[] = [
  { label: "北京", value: ["110000", "110100"] },
  { label: "上海", value: ["310000", "310100"] },
  { label: "杭州", value: ["330000", "330100"] },
  { label: "成都", value: ["510000", "510100"] },
  { label: "西安", value: ["610000", "610100"] },
  { label: "厦门", value: ["350000", "350200"] },
  { label: "重庆", value: ["500000", "500100"] },
  { label: "广州", value: ["440000", "440100"] },
];

// localStorage key
const RECENT_SEARCHES_KEY = "homestay_recent_searches";
const MAX_RECENT_SEARCHES = 10;

/**
 * 管理搜索建议（热门目的地 + 最近搜索）
 */
export function useSearchSuggestions() {
  // 热门目的地
  const popularDestinations = ref<PopularDestination[]>(defaultPopularDestinations);
  const loadingPopular = ref(false);

  // 最近搜索
  const recentSearches = ref<PopularDestination[]>([]);

  // 加载本地存储的最近搜索
  const loadRecentSearches = () => {
    try {
      const stored = localStorage.getItem(RECENT_SEARCHES_KEY);
      if (stored) {
        recentSearches.value = JSON.parse(stored);
      }
    } catch (e) {
      console.error("加载最近搜索失败:", e);
    }
  };

  // 保存最近搜索到本地存储
  const saveRecentSearches = () => {
    try {
      localStorage.setItem(RECENT_SEARCHES_KEY, JSON.stringify(recentSearches.value));
    } catch (e) {
      console.error("保存最近搜索失败:", e);
    }
  };

  // 添加到最近搜索
  const addToRecentSearches = (destination: PopularDestination) => {
    // 移除已存在的相同项
    const filtered = recentSearches.value.filter(
      (item) => item.value.join(",") !== destination.value.join(",")
    );

    // 添加到开头
    filtered.unshift(destination);

    // 限制数量
    if (filtered.length > MAX_RECENT_SEARCHES) {
      filtered.pop();
    }

    recentSearches.value = filtered;
    saveRecentSearches();
  };

  // 清除最近搜索
  const clearRecentSearches = () => {
    recentSearches.value = [];
    saveRecentSearches();
  };

  // 删除单条最近搜索
  const removeRecentSearch = (destination: PopularDestination) => {
    recentSearches.value = recentSearches.value.filter(
      (item) => item.value.join(",") !== destination.value.join(",")
    );
    saveRecentSearches();
  };

  // 是否显示"最近搜索"区域
  const showRecentSearches = computed(() => recentSearches.value.length > 0);

  // 是否有热门目的地
  const hasPopularDestinations = computed(() => popularDestinations.value.length > 0);

  // 初始化
  const init = () => {
    loadRecentSearches();
  };

  // 初始化时加载最近搜索
  init();

  return {
    popularDestinations,
    loadingPopular,
    recentSearches,
    showRecentSearches,
    hasPopularDestinations,
    addToRecentSearches,
    clearRecentSearches,
    removeRecentSearch,
  };
}
