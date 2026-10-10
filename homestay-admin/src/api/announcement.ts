import request from "@/utils/request";

export interface Announcement {
  id?: number;
  title: string;
  content: string;
  category: string;
  status: string;
  priority?: number;
  publisherId?: number;
  publisherName?: string;
  publishedAt?: string;
  startTime?: string;
  endTime?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface AnnouncementQueryParams {
  page?: number;
  size?: number;
  status?: string;
}

export interface ApiResponse<T = any> {
  success: boolean;
  message?: string;
  data?: T;
  total?: number;
  totalPages?: number;
  currentPage?: number;
}

export function getAdminAnnouncementsApi(
  params: AnnouncementQueryParams
): Promise<ApiResponse<Announcement[]>> {
  return request.get("/api/admin/announcements", { params });
}

export function getAdminAnnouncementByIdApi(id: number): Promise<ApiResponse<Announcement>> {
  return request.get(`/api/admin/announcements/${id}`);
}

export function createAnnouncementApi(
  announcement: Announcement,
  publisherId: number,
  publisherName: string,
  ipAddress?: string
): Promise<ApiResponse<Announcement>> {
  return request.post("/api/admin/announcements", announcement, {
    params: { publisherId, publisherName, ipAddress },
  });
}

export function updateAnnouncementApi(
  id: number,
  announcement: Announcement,
  publisherName: string,
  ipAddress?: string
): Promise<ApiResponse<Announcement>> {
  return request.put(`/api/admin/announcements/${id}`, announcement, {
    params: { publisherName, ipAddress },
  });
}

export function deleteAnnouncementApi(
  id: number,
  publisherName: string,
  ipAddress?: string
): Promise<ApiResponse<null>> {
  return request.delete(`/api/admin/announcements/${id}`, {
    params: { publisherName, ipAddress },
  });
}

export function publishAnnouncementApi(
  id: number,
  publisherId: number,
  publisherName: string,
  ipAddress?: string
): Promise<ApiResponse<Announcement>> {
  return request.post(`/api/admin/announcements/${id}/publish`, null, {
    params: { publisherId, publisherName, ipAddress },
  });
}

export function offlineAnnouncementApi(
  id: number,
  publisherName: string,
  ipAddress?: string
): Promise<ApiResponse<Announcement>> {
  return request.post(`/api/admin/announcements/${id}/offline`, null, {
    params: { publisherName, ipAddress },
  });
}

// 用户端接口
export function getPublishedAnnouncementsApi(params: {
  page?: number;
  size?: number;
  category?: string;
}) {
  return request.get("/api/announcements", { params });
}
