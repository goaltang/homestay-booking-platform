import request from "../utils/request";

export interface HomeStats {
  homestayCount: number;
  cityCount: number;
  positiveRate: number;
  recentOrders: number;
  availableToday: number;
}

export interface Banner {
  id: number;
  title: string;
  subtitle?: string;
  imageUrl?: string;
  linkUrl?: string;
  bgGradient?: string;
  sortOrder?: number;
  enabled?: boolean;
}

export function getHomeStats() {
  return request({
    url: "/api/home/stats",
    method: "get",
  });
}

export function getHomeBanners() {
  return request({
    url: "/api/home/banners",
    method: "get",
  });
}
