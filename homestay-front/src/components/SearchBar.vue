<template>
  <div class="search-container">
    <div class="search-wrapper">
      <!-- 搜索栏主体 -->
      <div class="search-bar" role="search" aria-label="民宿搜索">
        <!-- 目的地 -->
        <div
          class="search-field"
          :class="{ 'is-active': activeField === 'location' }"
          @click="focusField('location')"
        >
          <span class="field-label">目的地</span>
          <el-cascader
            ref="locationRef"
            v-model="searchParams.selectedRegion"
            :options="regionOptions"
            placeholder="你要去哪里？"
            :props="cascaderProps"
            clearable
            filterable
            :show-all-levels="false"
            @change="handleLocationChange"
            @visible-change="(v: boolean) => onCascaderVisibleChange(v, 'location')"
          />
        </div>

        <div class="field-divider" aria-hidden="true" />

        <!-- 关键词 -->
        <div
          class="search-field"
          :class="{ 'is-active': activeField === 'keyword' }"
          @click="focusField('keyword')"
        >
          <span class="field-label">关键词</span>
          <input
            ref="keywordRef"
            v-model="searchParams.keyword"
            type="text"
            class="field-input"
            placeholder="房源名、位置或特色"
            @focus="focusField('keyword')"
            @keydown.enter.prevent="handleSearch"
          />
        </div>

        <div class="field-divider" aria-hidden="true" />

        <!-- 日期 -->
        <div
          class="search-field search-field--dates"
          :class="{ 'is-active': activeField === 'dates' }"
          @click="focusField('dates')"
        >
          <div class="date-subfield">
            <span class="field-label">入住</span>
            <el-date-picker
              ref="checkInRef"
              v-model="searchParams.checkIn"
              type="date"
              placeholder="添加日期"
              format="M月D日"
              value-format="YYYY-MM-DD"
              :disabled-date="disabledCheckInDate"
              @change="handleCheckInChange"
              @focus="focusField('dates')"
            />
          </div>
          <div class="date-subfield">
            <span class="field-label">退房</span>
            <el-date-picker
              ref="checkOutRef"
              v-model="searchParams.checkOut"
              type="date"
              placeholder="添加日期"
              format="M月D日"
              value-format="YYYY-MM-DD"
              :disabled-date="disabledCheckOutDate"
              @focus="focusField('dates')"
            />
          </div>
        </div>

        <div class="field-divider" aria-hidden="true" />

        <!-- 房客 -->
        <div
          class="search-field search-field--guests"
          :class="{ 'is-active': activeField === 'guests' }"
          @click="toggleGuestPanel"
        >
          <span class="field-label">房客</span>
          <span class="field-value">{{ guestDisplayText }}</span>

          <!-- 房客选择面板 -->
          <transition name="panel">
            <div v-if="showGuestPanel" class="guest-panel" @click.stop>
              <div class="guest-row">
                <div class="guest-info">
                  <span class="guest-label">成人</span>
                  <span class="guest-desc">13 岁及以上</span>
                </div>
                <div class="guest-controls">
                  <button
                    type="button"
                    class="guest-btn"
                    :disabled="guestCounts.adults <= 1"
                    @click="adjustGuest('adults', -1)"
                  >
                    <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                      <path
                        d="M2 6H10"
                        stroke="currentColor"
                        stroke-width="1.5"
                        stroke-linecap="round"
                      />
                    </svg>
                  </button>
                  <span class="guest-count">{{ guestCounts.adults }}</span>
                  <button
                    type="button"
                    class="guest-btn"
                    :disabled="guestCounts.adults >= 20"
                    @click="adjustGuest('adults', 1)"
                  >
                    <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                      <path
                        d="M2 6H10M6 2V10"
                        stroke="currentColor"
                        stroke-width="1.5"
                        stroke-linecap="round"
                      />
                    </svg>
                  </button>
                </div>
              </div>

              <div class="guest-row">
                <div class="guest-info">
                  <span class="guest-label">儿童</span>
                  <span class="guest-desc">2–12 岁</span>
                </div>
                <div class="guest-controls">
                  <button
                    type="button"
                    class="guest-btn"
                    :disabled="guestCounts.children <= 0"
                    @click="adjustGuest('children', -1)"
                  >
                    <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                      <path
                        d="M2 6H10"
                        stroke="currentColor"
                        stroke-width="1.5"
                        stroke-linecap="round"
                      />
                    </svg>
                  </button>
                  <span class="guest-count">{{ guestCounts.children }}</span>
                  <button
                    type="button"
                    class="guest-btn"
                    :disabled="guestCounts.children >= 10"
                    @click="adjustGuest('children', 1)"
                  >
                    <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                      <path
                        d="M2 6H10M6 2V10"
                        stroke="currentColor"
                        stroke-width="1.5"
                        stroke-linecap="round"
                      />
                    </svg>
                  </button>
                </div>
              </div>

              <div class="guest-row">
                <div class="guest-info">
                  <span class="guest-label">婴幼儿</span>
                  <span class="guest-desc">2 岁以下</span>
                </div>
                <div class="guest-controls">
                  <button
                    type="button"
                    class="guest-btn"
                    :disabled="guestCounts.infants <= 0"
                    @click="adjustGuest('infants', -1)"
                  >
                    <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                      <path
                        d="M2 6H10"
                        stroke="currentColor"
                        stroke-width="1.5"
                        stroke-linecap="round"
                      />
                    </svg>
                  </button>
                  <span class="guest-count">{{ guestCounts.infants }}</span>
                  <button
                    type="button"
                    class="guest-btn"
                    :disabled="guestCounts.infants >= 5"
                    @click="adjustGuest('infants', 1)"
                  >
                    <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                      <path
                        d="M2 6H10M6 2V10"
                        stroke="currentColor"
                        stroke-width="1.5"
                        stroke-linecap="round"
                      />
                    </svg>
                  </button>
                </div>
              </div>
            </div>
          </transition>
        </div>

        <!-- 搜索按钮 -->
        <div class="search-action">
          <button
            type="button"
            class="search-button"
            :class="{ 'is-disabled': !canSearch }"
            :disabled="!canSearch || loading"
            :aria-disabled="!canSearch"
            @click="handleSearch"
          >
            <span class="search-icon" aria-hidden="true">
              <svg
                width="20"
                height="20"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2.5"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <circle cx="11" cy="11" r="8" />
                <path d="m21 21-4.35-4.35" />
              </svg>
            </span>
            <span class="search-text">搜索</span>
          </button>
          <div v-if="!canSearch" class="search-tooltip" role="tooltip">
            请输入关键词或选择目的地
          </div>
        </div>
      </div>

      <!-- 搜索建议面板 -->
      <transition name="suggestions">
        <SearchSuggestions v-if="showSuggestions" @select="selectSuggestion" />
      </transition>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from "vue";
import { ElMessage } from "element-plus";
import { regionData } from "element-china-area-data";
import SearchSuggestions from "./SearchSuggestions.vue";
import { useSearchSuggestions } from "@/composables/useSearchSuggestions";
import { trackSearch } from "@/api/tracking";

const { addToRecentSearches } = useSearchSuggestions();

/* ─── Props & Emits ─── */
interface SearchParamsProps {
  selectedRegion?: string[];
  keyword?: string;
  checkIn?: string | null;
  checkOut?: string | null;
  guestCount?: number;
}

interface Props {
  loading?: boolean;
  initialParams?: SearchParamsProps;
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  initialParams: () => ({}),
});

const emit = defineEmits<{
  search: [params: any];
  reset: [];
}>();

/* ─── Reactive State ─── */
const searchParams = reactive({
  selectedRegion: [] as string[],
  keyword: "",
  checkIn: null as string | null,
  checkOut: null as string | null,
  guestCount: 1,
});

const guestCounts = reactive({
  adults: 1,
  children: 0,
  infants: 0,
});

const activeField = ref<string | null>(null);
const showGuestPanel = ref(false);
const showSuggestions = ref(false);

/* ─── Refs ─── */
const locationRef = ref<any>(null);
const keywordRef = ref<HTMLInputElement | null>(null);
const checkInRef = ref<any>(null);
const checkOutRef = ref<any>(null);

/* ─── Constants ─── */
const regionOptions = regionData;
const cascaderProps = {
  value: "value",
  label: "label",
  children: "children",
  checkStrictly: true,
};

/* ─── Computed ─── */
const guestDisplayText = computed(() => {
  const total = guestCounts.adults + guestCounts.children;
  if (total === 1) return "1位房客";
  return `${total}位房客`;
});

const canSearch = computed(() => {
  return (
    searchParams.keyword.trim().length > 0 ||
    searchParams.selectedRegion.length > 0 ||
    searchParams.checkIn !== null ||
    searchParams.checkOut !== null
  );
});

/* ─── Watchers ─── */
watch(
  () => props.initialParams,
  (newParams) => {
    if (!newParams) return;
    if (newParams.selectedRegion !== undefined) {
      searchParams.selectedRegion = [...newParams.selectedRegion];
    }
    if (newParams.keyword !== undefined) {
      searchParams.keyword = newParams.keyword;
    }
    if (newParams.checkIn !== undefined) {
      searchParams.checkIn = newParams.checkIn;
    }
    if (newParams.checkOut !== undefined) {
      searchParams.checkOut = newParams.checkOut;
    }
    if (newParams.guestCount !== undefined) {
      const count = Math.max(1, newParams.guestCount);
      searchParams.guestCount = count;
      guestCounts.adults = count;
      guestCounts.children = 0;
      guestCounts.infants = 0;
    }
  },
  { immediate: true, deep: true }
);

/* ─── Methods ─── */
const focusField = (field: string) => {
  activeField.value = field;
  if (field === "location") {
    showSuggestions.value = true;
    showGuestPanel.value = false;
  } else if (field === "guests") {
    showGuestPanel.value = !showGuestPanel.value;
    showSuggestions.value = false;
  } else {
    showSuggestions.value = false;
    showGuestPanel.value = false;
  }
};

const toggleGuestPanel = () => {
  showGuestPanel.value = !showGuestPanel.value;
  showSuggestions.value = false;
  activeField.value = showGuestPanel.value ? "guests" : null;
};

const onCascaderVisibleChange = (visible: boolean, field: string) => {
  if (visible) {
    activeField.value = field;
    showSuggestions.value = false;
    showGuestPanel.value = false;
  } else {
    activeField.value = null;
  }
};

const handleLocationChange = () => {
  showSuggestions.value = false;
  activeField.value = null;
};

const disabledCheckInDate = (time: Date) => {
  return time.getTime() < Date.now() - 8.64e7;
};

const disabledCheckOutDate = (time: Date) => {
  if (!searchParams.checkIn) return time.getTime() < Date.now();
  return time.getTime() <= new Date(searchParams.checkIn).getTime();
};

const handleCheckInChange = (value: string | null) => {
  if (value && searchParams.checkOut) {
    const checkIn = new Date(value);
    const checkOut = new Date(searchParams.checkOut);
    if (checkOut <= checkIn) {
      const nextDay = new Date(checkIn);
      nextDay.setDate(nextDay.getDate() + 1);
      searchParams.checkOut = nextDay.toISOString().split("T")[0];
    }
  }
};

const adjustGuest = (type: "adults" | "children" | "infants", delta: number) => {
  const next = guestCounts[type] + delta;
  const min = type === "adults" ? 1 : 0;
  const max = type === "adults" ? 20 : type === "children" ? 10 : 5;
  if (next < min || next > max) return;
  guestCounts[type] = next;
  searchParams.guestCount = guestCounts.adults + guestCounts.children;
};

const selectSuggestion = (suggestion: { label: string; value: string[] }) => {
  searchParams.selectedRegion = suggestion.value;
  showSuggestions.value = false;
  addToRecentSearches(suggestion);
};

const handleSearch = () => {
  if (!canSearch.value) return;
  const keyword = searchParams.keyword.trim();
  const selectedRegion = [...searchParams.selectedRegion];

  trackSearch({
    keyword: keyword || undefined,
    cityCode: selectedRegion[1] || selectedRegion[0],
  });

  emit("search", {
    ...searchParams,
    keyword,
    selectedRegion,
  });
};

const handleReset = () => {
  searchParams.selectedRegion = [];
  searchParams.keyword = "";
  searchParams.checkIn = null;
  searchParams.checkOut = null;
  searchParams.guestCount = 1;
  guestCounts.adults = 1;
  guestCounts.children = 0;
  guestCounts.infants = 0;
  showGuestPanel.value = false;
  showSuggestions.value = false;
  activeField.value = null;
  ElMessage.success("搜索条件已重置");
  emit("reset");
};

/* ─── Click Outside ─── */
const handleClickOutside = (e: MouseEvent) => {
  const target = e.target as HTMLElement;
  const container = document.querySelector(".search-wrapper");
  if (container && !container.contains(target)) {
    showGuestPanel.value = false;
    showSuggestions.value = false;
    activeField.value = null;
  }
};

onMounted(() => {
  document.addEventListener("click", handleClickOutside);
});

onBeforeUnmount(() => {
  document.removeEventListener("click", handleClickOutside);
});

/* ─── Expose ─── */
defineExpose({
  searchParams,
  resetParams: handleReset,
});
</script>

<style scoped>
/* ============================================
   SearchBar — Organic Warmth Design
   基于 Homestay Design System v1.0
   ============================================ */

.search-container {
  position: relative;
  z-index: 10;
  margin-bottom: var(--space-6);
}

.search-wrapper {
  max-width: 900px;
  margin: 0 auto;
}

/* ─── 搜索栏外壳 ─── */
.search-bar {
  display: flex;
  align-items: center;
  gap: 0;
  background: var(--color-surface-elevated);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-lg);
  padding: var(--space-2);
  min-height: 72px;
  transition: box-shadow var(--duration-normal) var(--ease-out);
}

.search-bar:hover {
  box-shadow: var(--shadow-xl);
}

/* ─── 搜索字段 ─── */
.search-field {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  flex: 1;
  min-width: 0;
  padding: var(--space-3) var(--space-5);
  border-radius: var(--radius-xl);
  cursor: pointer;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    transform var(--duration-normal) var(--ease-spring);
  min-height: 56px;
}

.search-field:hover {
  background-color: var(--color-neutral-50);
  transform: translateY(-1px);
}

.search-field.is-active {
  background-color: var(--color-neutral-50);
  box-shadow: var(--shadow-focus);
}

/* 字段标签 */
.field-label {
  font-family: var(--font-body);
  font-size: var(--text-caption);
  font-weight: var(--weight-semibold);
  color: var(--color-neutral-900);
  line-height: var(--leading-tight);
  margin-bottom: var(--space-1);
  letter-spacing: 0.02em;
}

/* 字段值 / 输入框 */
.field-input {
  width: 100%;
  border: none;
  outline: none;
  background: transparent;
  padding: 0;
  margin: 0;
  font-family: var(--font-body);
  font-size: var(--text-body);
  color: var(--color-neutral-700);
  line-height: var(--leading-normal);
}

.field-input::placeholder {
  color: var(--color-neutral-400);
}

.field-value {
  font-family: var(--font-body);
  font-size: var(--text-body);
  color: var(--color-neutral-700);
}

/* ─── 日期字段（双列） ─── */
.search-field--dates {
  flex: 1.4;
  flex-direction: row;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-4);
}

.date-subfield {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

/* ─── 房客字段 ─── */
.search-field--guests {
  flex: 0.8;
  position: relative;
}

/* ─── 分隔线 ─── */
.field-divider {
  width: 1px;
  height: 32px;
  background: var(--color-neutral-200);
  flex-shrink: 0;
}

/* ─── 搜索按钮区域 ─── */
.search-action {
  position: relative;
  padding: var(--space-2);
  flex-shrink: 0;
}

.search-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  width: 52px;
  height: 52px;
  border-radius: var(--radius-full);
  border: none;
  background: var(--color-primary-500);
  color: white;
  cursor: pointer;
  box-shadow: var(--shadow-md);
  transition:
    background-color var(--duration-fast) var(--ease-out),
    transform var(--duration-normal) var(--ease-spring),
    box-shadow var(--duration-fast) var(--ease-out);
}

.search-button:hover:not(:disabled) {
  background: var(--color-primary-600);
  transform: scale(1.06);
  box-shadow: var(--shadow-lg);
}

.search-button:active:not(:disabled) {
  transform: scale(0.96);
  transition-duration: var(--duration-instant);
}

.search-button.is-disabled {
  background: var(--color-neutral-300);
  cursor: not-allowed;
}

.search-icon {
  display: flex;
  align-items: center;
  justify-content: center;
}

.search-text {
  display: none;
  font-family: var(--font-body);
  font-size: var(--text-body);
  font-weight: var(--weight-semibold);
}

/* Tooltip */
.search-tooltip {
  position: absolute;
  bottom: calc(100% + var(--space-3));
  left: 50%;
  transform: translateX(-50%);
  padding: var(--space-2) var(--space-4);
  background: var(--color-neutral-800);
  color: white;
  font-size: var(--text-body-sm);
  border-radius: var(--radius-md);
  white-space: nowrap;
  opacity: 0;
  pointer-events: none;
  transition: opacity var(--duration-fast) var(--ease-out);
}

.search-action:hover .search-tooltip {
  opacity: 1;
}

/* ─── 房客选择面板 ─── */
.guest-panel {
  position: absolute;
  top: calc(100% + var(--space-3));
  right: 0;
  width: 320px;
  background: var(--color-surface-elevated);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xl);
  border: 1px solid var(--color-neutral-200);
  padding: var(--space-5);
  z-index: 100;
}

.guest-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--color-neutral-100);
}

.guest-row:last-child {
  border-bottom: none;
}

.guest-info {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.guest-label {
  font-family: var(--font-body);
  font-size: var(--text-body);
  font-weight: var(--weight-semibold);
  color: var(--color-neutral-900);
}

.guest-desc {
  font-family: var(--font-body);
  font-size: var(--text-body-sm);
  color: var(--color-neutral-500);
}

.guest-controls {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.guest-btn {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-full);
  border: 1px solid var(--color-neutral-300);
  background: var(--color-surface);
  color: var(--color-neutral-700);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out),
    transform var(--duration-instant) var(--ease-out);
}

.guest-btn:hover:not(:disabled) {
  border-color: var(--color-primary-500);
  color: var(--color-primary-500);
}

.guest-btn:active:not(:disabled) {
  transform: scale(0.92);
}

.guest-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
  border-color: var(--color-neutral-200);
  color: var(--color-neutral-400);
}

.guest-count {
  font-family: var(--font-body);
  font-size: var(--text-body);
  font-weight: var(--weight-semibold);
  color: var(--color-neutral-900);
  min-width: 24px;
  text-align: center;
}

/* ─── 过渡动画 ─── */
.panel-enter-active,
.panel-leave-active {
  transition:
    opacity var(--duration-normal) var(--ease-out),
    transform var(--duration-slow) var(--ease-spring);
}

.panel-enter-from,
.panel-leave-to {
  opacity: 0;
  transform: scale(0.96) translateY(-8px);
}

.suggestions-enter-active,
.suggestions-leave-active {
  transition:
    opacity var(--duration-normal) var(--ease-out),
    transform var(--duration-slow) var(--ease-spring);
}

.suggestions-enter-from,
.suggestions-leave-to {
  opacity: 0;
  transform: translateY(-12px);
}

/* ─── 响应式：移动端 ─── */
@media (max-width: 768px) {
  .search-bar {
    flex-direction: column;
    align-items: stretch;
    border-radius: var(--radius-lg);
    padding: var(--space-3);
    gap: var(--space-1);
  }

  .search-field {
    padding: var(--space-4) var(--space-5);
    border-radius: var(--radius-md);
    min-height: auto;
  }

  .search-field:first-child {
    border-radius: var(--radius-md) var(--radius-md) 0 0;
  }

  .field-divider {
    display: none;
  }

  .search-field--dates {
    flex-direction: row;
    gap: var(--space-4);
  }

  .search-action {
    padding: var(--space-3) 0 0;
  }

  .search-button {
    width: 100%;
    height: 48px;
    border-radius: var(--radius-md);
  }

  .search-icon {
    display: none;
  }

  .search-text {
    display: inline;
  }

  .guest-panel {
    left: 0;
    right: 0;
    width: auto;
  }
}

/* ─── 深色模式适配（预留） ─── */
@media (prefers-color-scheme: dark) {
  /* 待实现：根据 MASTER.md 定义 dark tokens */
}
</style>
