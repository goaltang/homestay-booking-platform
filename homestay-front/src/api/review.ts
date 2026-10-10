import request from "@/utils/request";

// 获取评论列表
export function getReviews(params?: any) {
  return request({
    url: "/api/reviews",
    method: "get",
    params,
  });
}

// 获取评论详情
export function getReviewDetail(id: number) {
  return request({
    url: `/api/reviews/${id}`,
    method: "get",
  });
}

// 创建评论
export function createReview(data: any) {
  console.warn("createReview方法已弃用，请使用submitReview方法");
  return submitReview(data);
}

// 更新评论
export function updateReview(id: number, data: any) {
  return request({
    url: `/api/reviews/${id}`,
    method: "put",
    data,
  });
}

// 删除评论
export function deleteReview(id: number) {
  return request({
    url: `/api/reviews/${id}`,
    method: "delete",
  });
}

/**
 * 获取房源的评价列表
 * @param homestayId 房源ID
 * @param params 分页参数
 */
export function getHomestayReviews(homestayId: number, params?: { page?: number; size?: number }) {
  console.log(`获取房源评价，ID: ${homestayId}，参数:`, params);
  return request({
    url: `/api/reviews/homestay/${homestayId}`,
    method: "get",
    params,
  }).then((response) => {
    console.log(`房源评价获取成功，ID: ${homestayId}`, response.data);
    return response;
  });
}

/**
 * 获取房源评价统计信息
 * @param homestayId 房源ID
 */
export function getHomestayReviewStats(homestayId: number) {
  return request({
    url: `/api/reviews/homestay/${homestayId}/stats`,
    method: "get",
  }).then((response) => {
    console.log(`房源评价统计获取成功，ID: ${homestayId}`, response.data);
    return response;
  });
}

/**
 * 获取用户的评价列表
 * @param params 分页参数
 */
export function getUserReviews(params?: { page?: number; size?: number }) {
  return request({
    url: "/api/reviews/user",
    method: "get",
    params,
  });
}

/**
 * 提交房源评价
 * @param data 评价数据
 */
export function submitReview(data: {
  orderId: number;
  homestayId: number;
  rating: number;
  content: string;
  cleanlinessRating?: number;
  accuracyRating?: number;
  communicationRating?: number;
  locationRating?: number;
  checkInRating?: number;
  valueRating?: number;
  images?: string[];
}) {
  console.log("Submitting review with data:", data);
  return request({
    url: "/api/reviews",
    method: "post",
    data,
  });
}

/**
 * 更新评价图片
 * @param reviewId 评价ID
 * @param images 图片URL列表
 */
export function updateReviewImages(reviewId: number, images: string[]) {
  return request({
    url: `/api/reviews/${reviewId}/images`,
    method: "patch",
    data: { images },
  });
}

// 房东回复评价
export function respondToReview(reviewId: number, response: string) {
  return request({
    url: `/api/reviews/${reviewId}/response`,
    method: "post",
    data: { response },
  });
}

export const replyToReview = respondToReview;

// --- 新增：删除房东回复 ---
/**
 * 删除房东对评价的回复
 * @param reviewId 评价ID
 */
export function deleteReviewResponse(reviewId: number) {
  return request<void>({
    url: `/api/reviews/${reviewId}/response`,
    method: "delete",
  });
}
// --- 结束新增 ---

/**
 * 获取房东的评价列表（带筛选和分页）
 * @param params 筛选和分页参数
 */
export function getHostReviews(params?: {
  homestayId?: number | null;
  rating?: number | null;
  replyStatus?: string | null;
  page?: number; // 0-based
  size?: number;
  sort?: string; // <-- 新增: 允许传递 sort 参数 (e.g., "createTime,desc")
}) {
  // 过滤掉 null 或 undefined 的参数
  const filteredParams = Object.entries(params || {})
    .filter(([_, value]) => value !== null && value !== undefined)
    .reduce((acc, [key, value]) => ({ ...acc, [key]: value }), {});

  return request({
    url: "/api/reviews/host",
    method: "get",
    params: filteredParams,
  });
}

/**
 * 获取房东的评价统计数据
 */
export function getHostReviewStats() {
  return request({
    url: "/api/reviews/host/stats",
    method: "get",
  });
}
