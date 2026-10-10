import request from "../../utils/request";
import type { HomestayGroup, HomestayGroupRequest } from "../../types/homestay";

/**
 * 获取房东的所有分组
 */
export function getHomestayGroups(): Promise<HomestayGroup[]> {
  return request({
    url: "/api/host/groups",
    method: "get",
  })
    .then((response) => {
      return response.data || [];
    })
    .catch((error) => {
      console.error("获取房源分组列表失败:", error);
      return [];
    });
}

/**
 * 获取分组详情
 */
export function getHomestayGroupById(id: number): Promise<HomestayGroup> {
  return request({
    url: `/api/host/groups/${id}`,
    method: "get",
  }).then((response) => {
    return response.data;
  });
}

/**
 * 创建分组
 */
export function createHomestayGroup(data: HomestayGroupRequest) {
  return request({
    url: "/api/host/groups",
    method: "post",
    data,
  }).then((response) => {
    return response.data;
  });
}

/**
 * 更新分组
 */
export function updateHomestayGroup(id: number, data: HomestayGroupRequest) {
  return request({
    url: `/api/host/groups/${id}`,
    method: "put",
    data,
  }).then((response) => {
    return response.data;
  });
}

/**
 * 删除分组
 */
export function deleteHomestayGroup(id: number) {
  return request({
    url: `/api/host/groups/${id}`,
    method: "delete",
  }).then((response) => {
    return response.data;
  });
}

/**
 * 批量分配房源到分组
 */
export function assignHomestaysToGroup(groupId: number, homestayIds: number[]) {
  return request({
    url: `/api/host/groups/${groupId}/assign`,
    method: "post",
    data: { homestayIds },
  }).then((response) => {
    return response.data;
  });
}

/**
 * 批量移除房源分组
 */
export function removeHomestaysFromGroup(homestayIds: number[]) {
  return request({
    url: "/api/host/groups/remove",
    method: "post",
    data: { homestayIds },
  }).then((response) => {
    return response.data;
  });
}

/**
 * 获取分组下的房源列表
 */
export function getHomestaysByGroup(groupId: number, params?: { page?: number; size?: number }) {
  return request({
    url: `/api/host/groups/${groupId}/homestays`,
    method: "get",
    params,
  }).then((response) => {
    return response.data;
  });
}
