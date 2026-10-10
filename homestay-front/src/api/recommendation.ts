import request from "@/utils/request";

// 推荐请求类型定义
export interface UserRecommendationRequest {
  userId?: number;
  preferredProvinceCode?: string;
  preferredCityCode?: string;
  minPrice?: number;
  maxPrice?: number;
  guestCount?: number;
  preferredPropertyTypes?: string[];
  requiredAmenities?: string[];
  preferredAmenities?: string[];
  checkInDate?: string;
  checkOutDate?: string;
  minRating?: number;
  instantBookingOnly?: boolean;
  recommendationType?:
    "POPULAR" | "PERSONALIZED" | "LOCATION_BASED" | "SIMILAR" | "TRENDING" | "VALUE_FOR_MONEY";
  sortType?: "POPULARITY" | "PRICE_LOW_HIGH" | "PRICE_HIGH_LOW" | "RATING" | "NEWEST" | "DISTANCE";
}

// 分页参数接口
export interface PaginationParams {
  page: number;
  size: number;
}

/**
 * 获取热门民宿
 */
export function getPopularHomestays(limit: number = 6) {
  return request.get(`/api/recommendations/popular?limit=${limit}`);
}

/**
 * 获取热门民宿（分页版本）
 */
export function getPopularHomestaysPage(pagination: PaginationParams) {
  return request.get(
    `/api/recommendations/popular?page=${pagination.page}&size=${pagination.size}`
  );
}

/**
 * 获取推荐民宿
 */
export function getRecommendedHomestays(limit: number = 6) {
  return request.get(`/api/recommendations/recommended?limit=${limit}`);
}

/**
 * 获取推荐民宿（分页版本）
 */
export function getRecommendedHomestaysPage(pagination: PaginationParams) {
  return request.get(
    `/api/recommendations/recommended?page=${pagination.page}&size=${pagination.size}`
  );
}

/**
 * 获取个性化推荐民宿
 */
export function getPersonalizedRecommendations(userId: number, limit: number = 6) {
  return request.get(`/api/recommendations/personalized/${userId}?limit=${limit}`);
}

export function getMyPersonalizedRecommendations(limit: number = 6) {
  return request.get(`/api/recommendations/personalized/me?limit=${limit}`);
}

/**
 * 获取基于位置的推荐民宿
 */
export function getLocationBasedRecommendations(
  provinceCode: string,
  cityCode: string,
  limit: number = 6
) {
  return request.get(
    `/api/recommendations/location?provinceCode=${provinceCode}&cityCode=${cityCode}&limit=${limit}`
  );
}

/**
 * 获取相似民宿推荐
 */
export function getSimilarHomestays(homestayId: number, limit: number = 6) {
  return request.get(`/api/recommendations/similar/${homestayId}?limit=${limit}`);
}

/**
 * 根据用户请求获取推荐
 */
export function getRecommendationsByRequest(requestData: UserRecommendationRequest) {
  return request.post("/api/recommendations/custom", requestData);
}

/**
 * 刷新推荐缓存
 */
export function refreshRecommendationCache() {
  return request.post("/api/recommendations/refresh-cache");
}
