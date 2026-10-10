import request from "@/utils/request";

export interface Banner {
  id?: number;
  title: string;
  subtitle?: string;
  imageUrl?: string;
  linkUrl?: string;
  bgGradient?: string;
  sortOrder?: number;
  enabled?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export function getBannerList() {
  return request({
    url: "/api/admin/banners",
    method: "get",
  });
}

export function getBannerPage(params: { page?: number; size?: number; keyword?: string }) {
  return request({
    url: "/api/admin/banners/page",
    method: "get",
    params,
  });
}

export function getBannerById(id: number) {
  return request({
    url: `/api/admin/banners/${id}`,
    method: "get",
  });
}

export function createBanner(data: Banner) {
  return request({
    url: "/api/admin/banners",
    method: "post",
    data,
  });
}

export function updateBanner(id: number, data: Banner) {
  return request({
    url: `/api/admin/banners/${id}`,
    method: "put",
    data,
  });
}

export function deleteBanner(id: number) {
  return request({
    url: `/api/admin/banners/${id}`,
    method: "delete",
  });
}

export function toggleBannerEnabled(id: number) {
  return request({
    url: `/api/admin/banners/${id}/toggle`,
    method: "patch",
  });
}
