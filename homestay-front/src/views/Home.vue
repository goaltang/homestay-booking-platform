<template>
  <div class="home-page">
    <!-- ===== 首屏：Hero + 搜索框叠加 ===== -->
    <HeroBanner
      :banners="homeBanners"
      :stats="homeStats"
      @banner-click="handleBannerClick"
      @explore="goToHomestays"
    >
      <template #search>
        <div class="search-float">
          <SearchBar :loading="searchStore.loading" @search="handleSearch" @reset="handleReset" />
        </div>
      </template>
    </HeroBanner>

    <!-- ===== 公告栏 ===== -->
    <AnnouncementBar />

    <!-- ===== 内容区 ===== -->
    <div class="home-content">
      <!-- 房源类型快捷筛选 -->
      <div class="section-spaced">
        <div class="type-scroll">
          <div
            v-for="type in propertyTypes"
            :key="type.code"
            class="type-item"
            :class="{ active: selectedType === type.code }"
            @click="handleTypeClick(type.code)"
          >
            <el-icon v-if="type.icon" :size="22">
              <component :is="resolveIcon(type.icon)" />
            </el-icon>
            <span class="type-label">{{ type.name }}</span>
          </div>
        </div>
      </div>

      <!-- 精选房源 -->
      <section class="section-spaced">
        <HomestaySection
          title="精选房源"
          subtitle="为您挑选的最受欢迎的独特住宿"
          :homestays="featuredHomestays"
          :loading="featuredLoading"
          :error="featuredError"
          empty-text="暂无精选房源"
          error-text="加载失败，点击重试"
          :max-display="12"
          view-all-route="/homestays"
          :view-all-query="{ featured: 'true' }"
          @homestay-click="handleHomestayClick"
          @view-all="handleViewAll"
          @retry="loadFeatured"
        />
      </section>

      <!-- Tab 切换：推荐 / 热门 -->
      <section class="section-spaced">
        <div class="section-header-tabs">
          <h2 class="section-title">发现更多</h2>
          <div class="tab-bar">
            <button
              v-for="tab in tabs"
              :key="tab.key"
              class="tab-btn"
              :class="{ active: activeTab === tab.key }"
              @click="activeTab = tab.key"
            >
              {{ tab.label }}
            </button>
          </div>
        </div>

        <!-- 猜你喜欢 -->
        <div v-if="activeTab === 'personalized'" class="tab-panel">
          <div v-if="!userStore.isAuthenticated" class="login-prompt-compact">
            <p>登录后获取更精准的专属推荐</p>
            <el-button type="primary" size="small" @click="router.push('/login')"
              >立即登录</el-button
            >
          </div>
          <HomestaySection
            :homestays="userStore.isAuthenticated ? personalizedHomestays : popularHomestays"
            :loading="userStore.isAuthenticated ? personalizedLoading : popularLoading"
            :error="userStore.isAuthenticated ? personalizedError : popularError"
            :empty-text="
              userStore.isAuthenticated
                ? '暂无个性化推荐，多浏览和收藏房源来获得更精准的推荐'
                : '暂无热门房源'
            "
            error-text="加载失败，点击重试"
            :max-display="12"
            :show-view-all="false"
            view-all-route="/homestays"
            :view-all-query="
              userStore.isAuthenticated ? { personalized: 'true' } : { type: 'popular' }
            "
            @homestay-click="handleHomestayClick"
            @view-all="handleViewAll"
            @retry="userStore.isAuthenticated ? loadPersonalized : loadPopular"
          />
        </div>

        <!-- 推荐 -->
        <div v-else-if="activeTab === 'recommended'" class="tab-panel">
          <HomestaySection
            :homestays="recommendedHomestays"
            :loading="recommendedLoading"
            :error="recommendedError"
            empty-text="暂无推荐民宿"
            error-text="加载推荐失败，点击重试"
            :max-display="12"
            :show-view-all="false"
            view-all-route="/homestays"
            :view-all-query="{ featured: 'true' }"
            @homestay-click="handleHomestayClick"
            @view-all="handleViewAll"
            @retry="loadRecommended"
          />
        </div>

        <!-- 热门 -->
        <div v-else-if="activeTab === 'popular'" class="tab-panel">
          <HomestaySection
            :homestays="popularHomestays"
            :loading="popularLoading"
            :error="popularError"
            empty-text="暂无热门民宿"
            error-text="加载热门失败，点击重试"
            :max-display="12"
            :show-view-all="false"
            view-all-route="/homestays"
            :view-all-query="{ type: 'popular' }"
            @homestay-click="handleHomestayClick"
            @view-all="handleViewAll"
            @retry="loadPopular"
          />
        </div>
      </section>

      <!-- 最近浏览 -->
      <RecentViewed
        v-if="recentList.length > 0"
        @click="handleRecentClick"
        @clear="handleRecentClear"
      />

      <!-- 地图找房 CTA -->
      <MapCTA />

      <!-- 信任背书 -->
      <TrustSection :stats="homeStats" />

      <!-- 成为房东 CTA -->
      <HostCTA />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from "vue";
import { useRouter } from "vue-router";
import {
  User,
  House,
  School,
  OfficeBuilding,
  CoffeeCup,
  Sunny,
  Moon,
  HomeFilled,
} from "@element-plus/icons-vue";
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
import { getRecentViews } from "@/utils/recentViews";
const router = useRouter();
const userStore = useUserStore();
const searchStore = useSearchStore();

// ===== 首页数据 =====
const homeStats = ref<HomeStats>({
  homestayCount: 10000,
  cityCount: 50,
  positiveRate: 98,
  recentOrders: 0,
  availableToday: 0,
});
const homeBanners = ref<Banner[]>([]);

// ===== 房源类型 =====
const propertyTypes = ref<Array<{ code: string; name: string; icon?: string }>>([]);
const selectedType = ref<string | null>(null);

const iconMap: Record<string, any> = {
  User,
  House,
  School,
  OfficeBuilding,
  CoffeeCup,
  Sunny,
  Moon,
  HomeFilled,
};
const resolveIcon = (iconName: string) => iconMap[iconName] || House;

const loadPropertyTypes = async () => {
  try {
    const types = await getHomestayTypes();
    if (Array.isArray(types) && types.length > 0) {
      propertyTypes.value = types.slice(0, 10).map((t: any) => ({
        code: t.code || t.value || t.id,
        name: t.name || t.label,
        icon: t.icon || "House",
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
    { code: "合住房间", name: "合住房间", icon: "User" },
    { code: "独立房间", name: "独立房间", icon: "House" },
    { code: "别墅", name: "别墅", icon: "School" },
    { code: "酒店式公寓", name: "酒店公寓", icon: "OfficeBuilding" },
    { code: "客栈房间", name: "客栈房间", icon: "CoffeeCup" },
    { code: "Loft公寓", name: "Loft", icon: "Moon" },
    { code: "整套房子", name: "整套房子", icon: "Sunny" },
    { code: "整套公寓", name: "整套公寓", icon: "HomeFilled" },
  ];
};

const handleTypeClick = (typeCode: string) => {
  selectedType.value = typeCode;
  router.push({ path: "/homestays", query: { type: typeCode, search: "true" } });
};

// ===== Tab 切换 =====
const tabs = [
  { key: "personalized", label: "猜你喜欢" },
  { key: "recommended", label: "推荐" },
  { key: "popular", label: "热门" },
];
const activeTab = ref("recommended");

// 登录后自动切换到个性化
watch(
  () => userStore.isAuthenticated,
  (isAuth) => {
    if (isAuth) activeTab.value = "personalized";
  }
);

// ===== 精选房源 =====
const featuredHomestays = ref<any[]>([]);
const featuredLoading = ref(false);
const featuredError = ref(false);
const loadFeatured = async () => {
  featuredLoading.value = true;
  featuredError.value = false;
  try {
    const res = await getActiveHomestays({ page: 0, size: 12, featured: true });
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
    const res = await getMyPersonalizedRecommendations(12);
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
    const res = await getRecommendedHomestays(12);
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
    const res = await getPopularHomestays();
    popularHomestays.value = res.data || [];
  } catch {
    popularError.value = true;
  } finally {
    popularLoading.value = false;
  }
};

// Tab 切换时懒加载
watch(
  activeTab,
  (tab) => {
    if (tab === "personalized") {
      if (userStore.isAuthenticated && personalizedHomestays.value.length === 0) loadPersonalized();
      if (!userStore.isAuthenticated && popularHomestays.value.length === 0) loadPopular();
    }
    if (tab === "recommended" && recommendedHomestays.value.length === 0) loadRecommended();
    if (tab === "popular" && popularHomestays.value.length === 0) loadPopular();
  },
  { immediate: true }
);

// ===== 最近浏览 =====
const recentList = computed(() => getRecentViews());

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

const handleRecentClear = () => {
  // RecentViewed 内部已清空
};

const goToHomestays = () => {
  router.push("/homestays");
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
  loadRecommended();
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

/* 搜索框悬浮在 Hero 底部 */
.search-float {
  max-width: 920px;
  margin: 0 auto;
}

.search-float :deep(.search-container) {
  margin-bottom: 0;
}

.search-float :deep(.search-bar) {
  box-shadow: 0 16px 48px rgba(31, 28, 25, 0.14);
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
  flex-direction: column;
  align-items: center;
  gap: 6px;
  min-width: 72px;
  padding: var(--space-3) var(--space-4);
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
  box-shadow: var(--shadow-sm);
}

.type-item:hover {
  color: var(--color-neutral-900);
  border-color: var(--color-primary-200);
  box-shadow: var(--shadow-md);
  transform: translateY(-1px);
}

.type-item.active {
  color: var(--color-primary-700);
  border-color: var(--color-primary-300);
  background: var(--color-primary-50);
  box-shadow: 0 6px 18px rgba(212, 95, 46, 0.12);
}

.type-label {
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
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

/* 登录提示 */
.login-prompt-compact {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: var(--space-3) var(--space-5);
  margin-bottom: var(--space-4);
  background: var(--color-primary-50);
  border: 1px solid var(--color-primary-200);
  border-radius: var(--radius-md);
  font-size: 14px;
  color: var(--color-primary-700);
}

.login-prompt-compact p {
  margin: 0;
}

/* Tab 面板 */
.tab-panel {
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
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
    min-width: 64px;
    padding: 10px 12px;
  }
}
</style>
