<template>
  <div class="home-page">
    <!-- ===== 首屏：真实房源大图 + 搜索 ===== -->
    <HeroBanner
      :banners="homeBanners"
      :homestay="featuredHomestays[0] || null"
      @banner-click="handleBannerClick"
      @homestay-click="handleHomestayClick"
    >
      <template #search>
        <SearchBar :loading="searchStore.loading" @search="handleSearch" @reset="handleReset" />
      </template>
    </HeroBanner>

    <!-- ===== 公告栏 ===== -->
    <AnnouncementBar />

    <!-- ===== 内容区 ===== -->
    <div class="home-content">
      <!-- 房源类型快捷筛选 -->
      <nav class="section-spaced type-scroll" aria-label="按房源类型浏览">
        <button
          v-for="type in propertyTypes"
          :key="type.code"
          type="button"
          class="type-item"
          :class="{ active: selectedType === type.code }"
          @click="handleTypeClick(type.code)"
        >
          {{ type.name }}
        </button>
      </nav>

      <!-- 最近浏览（无记录时组件自身不渲染） -->
      <RecentViewed class="section-spaced" @click="handleRecentClick" />

      <!-- 精选房源 -->
      <section class="section-spaced">
        <HomestaySection
          title="精选房源"
          subtitle="平台挑出的口碑房源"
          :homestays="featuredHomestays"
          :loading="featuredLoading"
          :error="featuredError"
          empty-text="暂无精选房源"
          error-text="精选房源加载失败"
          :max-display="SECTION_SIZE"
          view-all-route="/homestays"
          :view-all-query="{ featured: 'true' }"
          @homestay-click="handleHomestayClick"
          @view-all="handleViewAll"
          @retry="loadFeatured"
        />
      </section>

      <!-- 推荐 / 热门 -->
      <section class="section-spaced">
        <div class="section-header-tabs">
          <h2 class="section-title">{{ activeTab === "recommended" ? "为你推荐" : "最近热门" }}</h2>
          <div class="tab-bar" role="tablist">
            <button
              v-for="tab in tabs"
              :key="tab.key"
              type="button"
              role="tab"
              class="tab-btn"
              :class="{ active: activeTab === tab.key }"
              :aria-selected="activeTab === tab.key"
              @click="activeTab = tab.key"
            >
              {{ tab.label }}
            </button>
          </div>
        </div>

        <HomestaySection
          :key="activeTab"
          class="tab-panel"
          :homestays="currentTab.homestays"
          :loading="currentTab.loading"
          :error="currentTab.error"
          :empty-text="currentTab.emptyText"
          error-text="房源加载失败"
          :max-display="SECTION_SIZE"
          :show-view-all="false"
          @homestay-click="handleHomestayClick"
          @retry="currentTab.reload"
        />
        <p v-if="activeTab === 'recommended' && !userStore.isAuthenticated" class="login-hint">
          <router-link to="/login">登录</router-link>后，会根据你浏览和收藏过的房源来推荐
        </p>
      </section>

      <!-- 地图找房 -->
      <MapCTA class="section-spaced" />

      <!-- 平台保障 -->
      <TrustSection class="section-spaced" :stats="homeStats" />

      <!-- 成为房东 -->
      <HostCTA />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from "vue";
import { useRouter } from "vue-router";
import { useUserStore } from "@/stores/user";
import { useSearchStore } from "@/stores/search";
import SearchBar from "@/components/SearchBar.vue";
import HeroBanner from "@/components/home/HeroBanner.vue";
import AnnouncementBar from "@/components/home/AnnouncementBar.vue";
import HomestaySection from "@/components/homestay/HomestaySection.vue";
import RecentViewed from "@/components/home/RecentViewed.vue";
import MapCTA from "@/components/home/MapCTA.vue";
import TrustSection from "@/components/home/TrustSection.vue";
import HostCTA from "@/components/home/HostCTA.vue";
import { getActiveHomestays } from "@/api/homestay";
import { getHomestayTypes } from "@/api/homestay/meta";
import { getHomeStats, getHomeBanners, type HomeStats, type Banner } from "@/api/home";
import {
  getRecommendedHomestays,
  getPopularHomestays,
  getMyPersonalizedRecommendations,
} from "@/api/recommendation";
import { addRecentView } from "@/utils/recentViews";
const router = useRouter();
const userStore = useUserStore();
const searchStore = useSearchStore();

// ===== 首页数据 =====
// 接口返回前不展示统计，避免写死的兜底数字被当成真实数据
const homeStats = ref<HomeStats | null>(null);
const homeBanners = ref<Banner[]>([]);

// ===== 房源类型 =====
const propertyTypes = ref<Array<{ code: string; name: string }>>([]);
const selectedType = ref<string | null>(null);

const loadPropertyTypes = async () => {
  try {
    const types = await getHomestayTypes();
    if (Array.isArray(types) && types.length > 0) {
      propertyTypes.value = types.slice(0, 10).map((t: any) => ({
        code: t.code || t.value || t.id,
        name: t.name || t.label,
      }));
    } else {
      setDefaultTypes();
    }
  } catch {
    setDefaultTypes();
  }
};
const setDefaultTypes = () => {
  // 与数据库 homestays.type 实际值对齐
  propertyTypes.value = [
    { code: "合住房间", name: "合住房间" },
    { code: "独立房间", name: "独立房间" },
    { code: "别墅", name: "别墅" },
    { code: "酒店式公寓", name: "酒店公寓" },
    { code: "客栈房间", name: "客栈房间" },
    { code: "Loft公寓", name: "Loft" },
    { code: "整套房子", name: "整套房子" },
    { code: "整套公寓", name: "整套公寓" },
  ];
};

const handleTypeClick = (typeCode: string) => {
  selectedType.value = typeCode;
  router.push({ path: "/homestays", query: { type: typeCode, search: "true" } });
};

// 首页每个房源区块展示的数量（桌面端两排）
const SECTION_SIZE = 8;

// ===== Tab 切换 =====
const tabs = [
  { key: "recommended", label: "为你推荐" },
  { key: "popular", label: "热门" },
] as const;
type TabKey = (typeof tabs)[number]["key"];
const activeTab = ref<TabKey>("recommended");

// ===== 精选房源 =====
const featuredHomestays = ref<any[]>([]);
const featuredLoading = ref(false);
const featuredError = ref(false);
const loadFeatured = async () => {
  featuredLoading.value = true;
  featuredError.value = false;
  try {
    const res = await getActiveHomestays({ page: 0, size: SECTION_SIZE, featured: true });
    featuredHomestays.value = res.data?.data || [];
  } catch {
    featuredError.value = true;
  } finally {
    featuredLoading.value = false;
  }
};

// ===== Tab 内容数据 =====
const personalizedHomestays = ref<any[]>([]);
const personalizedLoading = ref(false);
const personalizedError = ref(false);
const loadPersonalized = async () => {
  if (!userStore.isAuthenticated) return;
  personalizedLoading.value = true;
  personalizedError.value = false;
  try {
    const res = await getMyPersonalizedRecommendations(SECTION_SIZE);
    personalizedHomestays.value = res.data?.content || res.data || [];
  } catch {
    personalizedError.value = true;
  } finally {
    personalizedLoading.value = false;
  }
};

const recommendedHomestays = ref<any[]>([]);
const recommendedLoading = ref(false);
const recommendedError = ref(false);
const loadRecommended = async () => {
  recommendedLoading.value = true;
  recommendedError.value = false;
  try {
    const res = await getRecommendedHomestays(SECTION_SIZE);
    recommendedHomestays.value = res.data || [];
  } catch {
    recommendedError.value = true;
  } finally {
    recommendedLoading.value = false;
  }
};

const popularHomestays = ref<any[]>([]);
const popularLoading = ref(false);
const popularError = ref(false);
const loadPopular = async () => {
  popularLoading.value = true;
  popularError.value = false;
  try {
    const res = await getPopularHomestays(SECTION_SIZE);
    popularHomestays.value = res.data || [];
  } catch {
    popularError.value = true;
  } finally {
    popularLoading.value = false;
  }
};

// “为你推荐”：登录用户用个性化结果，未登录用通用推荐
const currentTab = computed(() => {
  if (activeTab.value === "popular") {
    return {
      homestays: popularHomestays.value,
      loading: popularLoading.value,
      error: popularError.value,
      emptyText: "暂无热门房源",
      reload: loadPopular,
    };
  }
  if (userStore.isAuthenticated) {
    return {
      homestays: personalizedHomestays.value,
      loading: personalizedLoading.value,
      error: personalizedError.value,
      emptyText: "还没有推荐，多浏览或收藏几套房源后再来看看",
      reload: loadPersonalized,
    };
  }
  return {
    homestays: recommendedHomestays.value,
    loading: recommendedLoading.value,
    error: recommendedError.value,
    emptyText: "暂无推荐房源",
    reload: loadRecommended,
  };
});

// 切换标签或登录状态变化时，按需加载对应数据
watch(
  [activeTab, () => userStore.isAuthenticated],
  ([tab, isAuth]) => {
    if (tab === "popular") {
      if (popularHomestays.value.length === 0) loadPopular();
    } else if (isAuth) {
      if (personalizedHomestays.value.length === 0) loadPersonalized();
    } else if (recommendedHomestays.value.length === 0) {
      loadRecommended();
    }
  },
  { immediate: true }
);

// ===== 事件处理 =====
const handleHomestayClick = (homestay: any) => {
  if (homestay?.id) {
    addRecentView({
      id: Number(homestay.id),
      title: homestay.title || homestay.name || "未命名房源",
      coverImage: homestay.coverImage,
      price: homestay.price || homestay.pricePerNight,
      location: homestay.provinceText || homestay.cityText || undefined,
    });
    router.push(`/homestays/${homestay.id}`);
  }
};

const handleViewAll = (route: string, query: Record<string, any>) => {
  router.push({ path: route, query });
};

const handleSearch = (params: any) => {
  searchStore.setSearchState(params);
  searchStore.updatePagination(1);
  searchStore.syncToUrl();
};

const handleReset = () => {
  searchStore.resetSearch();
  router.push("/");
};

const handleRecentClick = (item: any) => {
  router.push(`/homestays/${item.id}`);
};

const handleBannerClick = (banner: Banner) => {
  if (banner.linkUrl) {
    if (banner.linkUrl.startsWith("http")) window.open(banner.linkUrl, "_blank");
    else router.push(banner.linkUrl);
  }
};

const loadHomeData = async () => {
  try {
    const [statsRes, bannersRes] = await Promise.all([getHomeStats(), getHomeBanners()]);
    if (statsRes.data?.success && statsRes.data.data) {
      homeStats.value = statsRes.data.data;
    }
    if (bannersRes.data?.success && Array.isArray(bannersRes.data.data)) {
      homeBanners.value = bannersRes.data.data;
    }
  } catch (error) {
    console.error("加载首页数据失败:", error);
  }
};

onMounted(() => {
  loadPropertyTypes();
  loadHomeData();
  loadFeatured();
});
</script>

<style scoped>
.home-page {
  background:
    linear-gradient(
      180deg,
      var(--color-background-warm, #f7f4f0) 0%,
      var(--color-background, #faf9f7) 420px
    ),
    var(--color-background, #faf9f7);
}

/* 内容区 */
.home-content {
  max-width: 1280px;
  margin: 0 auto;
  padding: var(--space-8) var(--space-6) var(--space-16);
}

.section-spaced {
  margin-bottom: 48px;
}

.section-spaced:last-child {
  margin-bottom: 0;
}

/* 房源类型筛选 */
.type-scroll {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 8px;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.type-scroll::-webkit-scrollbar {
  display: none;
}

.type-item {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  font-family: inherit;
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  padding: var(--space-2) var(--space-4);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-normal) var(--ease-out),
    transform var(--duration-normal) var(--ease-out);
  color: var(--color-neutral-600);
  border: 1px solid var(--color-neutral-200);
  background: var(--color-surface-elevated);
}

.type-item:focus-visible,
.tab-btn:focus-visible {
  outline: 2px solid var(--color-primary-500);
  outline-offset: 2px;
}

.type-item:hover {
  color: var(--color-neutral-900);
  border-color: var(--color-primary-200);
}

.type-item.active {
  color: var(--color-primary-700);
  border-color: var(--color-primary-300);
  background: var(--color-primary-50);
}

/* Tab 切换区 */
.section-header-tabs {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 16px;
}

.section-title {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  color: var(--color-neutral-900);
  letter-spacing: 0;
}

.tab-bar {
  display: flex;
  gap: 4px;
  background: var(--color-neutral-100);
  padding: 4px;
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.tab-btn {
  padding: var(--space-2) var(--space-5);
  border: none;
  background: transparent;
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 500;
  color: var(--color-neutral-600);
  cursor: pointer;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
  font-family: inherit;
}

.tab-btn:hover {
  color: var(--color-neutral-900);
}

.tab-btn.active {
  background: var(--color-surface-elevated);
  color: var(--color-primary-700);
  box-shadow: var(--shadow-sm);
  font-weight: 600;
}

/* 未登录时的推荐说明 */
.login-hint {
  margin: var(--space-4) 0 0;
  font-size: var(--text-body-sm);
  color: var(--color-neutral-600);
}

.login-hint a {
  color: var(--color-primary-700);
  font-weight: 600;
}

/* 响应式 */
@media (max-width: 768px) {
  .home-content {
    padding: 20px 16px 40px;
  }

  .section-spaced {
    margin-bottom: 36px;
  }

  .section-title {
    font-size: 22px;
  }

  .section-header-tabs {
    flex-direction: column;
    align-items: flex-start;
  }

  .tab-bar {
    width: 100%;
    overflow-x: auto;
  }

  .tab-btn {
    flex-shrink: 0;
    padding: 8px 16px;
  }

  .type-item {
    padding: var(--space-2) var(--space-3);
  }
}
</style>
