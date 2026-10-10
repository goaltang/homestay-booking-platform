import request from "@/utils/request";

export interface Announcement {
  id: number;
  title: string;
  content: string;
  category: string;
  status: string;
  priority: number;
  publisherName?: string;
  publishedAt?: string;
  startTime?: string;
  endTime?: string;
}

export interface AnnouncementQuery {
  page?: number;
  size?: number;
  category?: string;
}

export function getAnnouncements(params: AnnouncementQuery = {}) {
  return request({
    url: "/api/announcements",
    method: "get",
    params: {
      page: params.page ?? 0,
      size: params.size ?? 5,
      category: params.category,
    },
  });
}
