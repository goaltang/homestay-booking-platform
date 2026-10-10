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

export function addRecentView(item: Omit<RecentViewItem, "viewedAt">) {
  try {
    const list = getRecentViews();
    const filtered = list.filter((i) => i.id !== item.id);
    filtered.unshift({
      ...item,
      viewedAt: Date.now(),
    });
    const trimmed = filtered.slice(0, MAX_ITEMS);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(trimmed));
  } catch {
    // ignore storage errors
  }
}

export function getRecentViews(): RecentViewItem[] {
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

export function clearRecentViews() {
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    // ignore
  }
}

export function getRecentViewIds(): number[] {
  return getRecentViews().map((i) => i.id);
}
