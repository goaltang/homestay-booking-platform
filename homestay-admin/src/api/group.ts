import request from "../utils/request";
import { normalizePageResponse } from "@/api/response";

export function getGroups(params: {
  page?: number;
  size?: number;
  keyword?: string;
  ownerId?: number;
}) {
  return request({
    url: "/api/admin/groups",
    method: "get",
    params,
  }).then((res) => normalizePageResponse<any>(res));
}

export function getGroupById(id: number) {
  return request({
    url: `/api/admin/groups/${id}`,
    method: "get",
  });
}

export function toggleGroupEnabled(id: number, enabled: boolean) {
  return request({
    url: `/api/admin/groups/${id}/toggle-enabled`,
    method: "put",
    params: { enabled },
  });
}

export function deleteGroup(id: number) {
  return request({
    url: `/api/admin/groups/${id}`,
    method: "delete",
  });
}
