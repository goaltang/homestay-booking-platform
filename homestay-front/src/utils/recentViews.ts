import { computed, ref } from "vue";

const STORAGE_KEY = "homestay_recent_views";
const MAX_ITEMS = 12;

export interface RecentViewItem {
  id: number;
  title: string;
  coverImage?: string;
  price?: string;
  location?: string;
  viewedAt: number;
}

function readStorage(): RecentViewItem[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) return parsed;
    return [];
  } catch {
    return [];
  }
}

// 响应式副本：localStorage 本身不可被 Vue 追踪，增删都经由这里同步
const recentViews = ref<RecentViewItem[]>(readStorage());

export function addRecentView(item: Omit<RecentViewItem, "viewedAt">) {
  const filtered = readStorage().filter((i) => i.id !== item.id);
  filtered.unshift({
    ...item,
    viewedAt: Date.now(),
  });
  const trimmed = filtered.slice(0, MAX_ITEMS);
  recentViews.value = trimmed;
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(trimmed));
  } catch {
    // ignore storage errors
  }
}

/** 响应式的最近浏览列表，用于模板渲染 */
export function useRecentViews() {
  return computed(() => recentViews.value);
}

export function clearRecentViews() {
  recentViews.value = [];
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    // ignore
  }
}
