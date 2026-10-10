<template>
  <div class="search-suggestions">
    <!-- 最近搜索 -->
    <div v-if="showRecentSearches" class="suggestion-section">
      <div class="suggestion-header">
        <span class="suggestion-title">最近搜索</span>
        <button type="button" class="clear-btn" @click="handleClearRecent">清除</button>
      </div>
      <div class="suggestion-items">
        <div
          v-for="item in recentSearches"
          :key="item.label + '-' + item.value.join(',')"
          class="suggestion-item recent-item"
          @click="$emit('select', item)"
        >
          <svg
            class="item-icon recent-icon"
            width="14"
            height="14"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <circle cx="12" cy="12" r="10" />
            <polyline points="12 6 12 12 16 14" />
          </svg>
          <span class="item-label">{{ item.label }}</span>
          <button
            type="button"
            class="remove-btn"
            aria-label="删除此搜索记录"
            @click.stop="handleRemoveRecent(item)"
          >
            <svg
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
          </button>
        </div>
      </div>
    </div>

    <!-- 热门目的地 -->
    <div v-if="hasPopularDestinations" class="suggestion-section">
      <div class="suggestion-title">热门目的地</div>
      <div class="suggestion-items">
        <div
          v-for="suggestion in popularDestinations"
          :key="suggestion.label + '-' + suggestion.value.join(',')"
          class="suggestion-item"
          @click="$emit('select', suggestion)"
        >
          <svg
            class="item-icon hot-icon"
            width="14"
            height="14"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <polygon
              points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"
            />
          </svg>
          <span>{{ suggestion.label }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useSearchSuggestions } from "@/composables/useSearchSuggestions";

const emit = defineEmits<{
  select: [suggestion: { label: string; value: string[] }];
}>();

const {
  popularDestinations,
  recentSearches,
  showRecentSearches,
  hasPopularDestinations,
  clearRecentSearches,
  removeRecentSearch,
} = useSearchSuggestions();

const handleClearRecent = () => {
  clearRecentSearches();
};

const handleRemoveRecent = (item: { label: string; value: string[] }) => {
  removeRecentSearch(item);
};
</script>

<style scoped>
.search-suggestions {
  background: var(--color-surface-elevated);
  border-radius: var(--radius-lg);
  padding: var(--space-5);
  margin-top: var(--space-4);
  box-shadow: var(--shadow-xl);
  border: 1px solid var(--color-neutral-200);
}

.suggestion-section {
  margin-bottom: var(--space-5);
}

.suggestion-section:last-child {
  margin-bottom: 0;
}

.suggestion-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-3);
}

.suggestion-title {
  font-family: var(--font-display);
  font-size: var(--text-h3);
  font-weight: var(--weight-semibold);
  color: var(--color-neutral-900);
}

.suggestion-header .suggestion-title {
  margin-bottom: 0;
}

.clear-btn {
  font-family: var(--font-body);
  font-size: var(--text-body-sm);
  font-weight: var(--weight-medium);
  color: var(--color-neutral-500);
  background: none;
  border: none;
  cursor: pointer;
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-sm);
  transition:
    color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
}

.clear-btn:hover {
  color: var(--color-primary-500);
  background-color: var(--color-primary-50);
}

.suggestion-items {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.suggestion-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  background: var(--color-neutral-50);
  border-radius: var(--radius-full);
  cursor: pointer;
  font-family: var(--font-body);
  font-size: var(--text-body);
  color: var(--color-neutral-900);
  border: 1px solid transparent;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    transform var(--duration-normal) var(--ease-spring);
}

.suggestion-item:hover {
  background: var(--color-primary-500);
  color: white;
  border-color: var(--color-primary-500);
  transform: translateY(-2px);
}

.item-icon {
  flex-shrink: 0;
}

.hot-icon {
  color: var(--color-primary-500);
  transition: color var(--duration-fast) var(--ease-out);
}

.suggestion-item:hover .hot-icon {
  color: white;
}

.recent-item {
  background: var(--color-neutral-100);
}

.recent-icon {
  color: var(--color-neutral-500);
  transition: color var(--duration-fast) var(--ease-out);
}

.suggestion-item:hover .recent-icon {
  color: white;
}

.item-label {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.remove-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  margin-left: var(--space-1);
  border-radius: var(--radius-full);
  border: none;
  background: transparent;
  color: var(--color-neutral-400);
  cursor: pointer;
  opacity: 0;
  transition:
    opacity var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.suggestion-item:hover .remove-btn {
  opacity: 1;
}

.remove-btn:hover {
  background: rgba(255, 255, 255, 0.2);
  color: white;
}

@media (max-width: 480px) {
  .search-suggestions {
    padding: var(--space-4);
  }

  .suggestion-items {
    gap: var(--space-2);
  }

  .suggestion-item {
    padding: var(--space-2) var(--space-3);
  }

  .remove-btn {
    opacity: 1;
  }
}
</style>
