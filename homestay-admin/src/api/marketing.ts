import request from "@/utils/request";

// ========== 活动管理 ==========

export function getCampaigns(params?: {
  page?: number;
  size?: number;
  name?: string;
  status?: string;
  campaignType?: string;
  subsidyBearer?: string;
  hostId?: number;
}) {
  return request({
    url: "/api/admin/promotions/campaigns",
    method: "get",
    params,
  });
}

export function createCampaign(data: any) {
  return request({
    url: "/api/admin/promotions/campaigns",
    method: "post",
    data,
  });
}

export function updateCampaign(id: number, data: any) {
  return request({
    url: `/api/admin/promotions/campaigns/${id}`,
    method: "put",
    data,
  });
}

export function publishCampaign(id: number) {
  return request({
    url: `/api/admin/promotions/campaigns/${id}/publish`,
    method: "post",
  });
}

export function pauseCampaign(id: number) {
  return request({
    url: `/api/admin/promotions/campaigns/${id}/pause`,
    method: "post",
  });
}

export function deleteCampaign(id: number) {
  return request({
    url: `/api/admin/promotions/campaigns/${id}`,
    method: "delete",
  });
}

// ========== 优惠券模板 ==========

export function getCouponTemplates(params?: {
  page?: number;
  size?: number;
  name?: string;
  status?: string;
  subsidyBearer?: string;
}) {
  return request({
    url: "/api/admin/promotions/templates",
    method: "get",
    params,
  });
}

export function createCouponTemplate(data: any) {
  return request({
    url: "/api/admin/promotions/templates",
    method: "post",
    data,
  });
}

export function updateCouponTemplate(id: number, data: any) {
  return request({
    url: `/api/admin/promotions/templates/${id}`,
    method: "put",
    data,
  });
}

export function deleteCouponTemplate(id: number) {
  return request({
    url: `/api/admin/promotions/templates/${id}`,
    method: "delete",
  });
}

// ========== 统计 ==========

export function getPromotionStatistics(params?: {
  campaignType?: string;
  subsidyBearer?: string;
  startDate?: string;
  endDate?: string;
}) {
  return request({
    url: "/api/admin/promotions/statistics",
    method: "get",
    params,
  });
}

// ========== ROI 分析 ==========

export function getRoiOverview(params?: { startDate?: string; endDate?: string }) {
  return request({
    url: "/api/admin/promotions/roi/overview",
    method: "get",
    params,
  });
}

export function getRoiCampaigns(params?: { startDate?: string; endDate?: string; limit?: number }) {
  return request({
    url: "/api/admin/promotions/roi/campaigns",
    method: "get",
    params,
  });
}
