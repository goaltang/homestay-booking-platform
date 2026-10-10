import request from "../utils/request";

/**
 * 行为埋点 API
 * 所有接口均为 fire-and-forget，不阻塞主流程
 */

/**
 * 记录房源浏览
 */
export function trackView(
  homestayId: number,
  data: { cityCode?: string; type?: string; price?: string | number }
) {
  request.post(`/api/tracking/view/${homestayId}`, data).catch(() => {});
}

/**
 * 记录搜索行为
 */
export function trackSearch(data: { keyword?: string; cityCode?: string; type?: string }) {
  request.post("/api/tracking/search", data).catch(() => {});
}

/**
 * 记录点击行为
 */
export function trackClick(homestayId: number) {
  request.post(`/api/tracking/click/${homestayId}`, {}).catch(() => {});
}
