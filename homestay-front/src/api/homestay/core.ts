import request from "../../utils/request";
import type { Homestay } from "../../types/homestay";

/**
 * 获取所有房源列表
 */
export function getHomestays(params?: {
  page?: number;
  size?: number;
  status?: string;
  type?: string;
}) {
  console.log("获取房源列表，参数:", params);
  return request({
    url: "/api/homestays",
    method: "get",
    params,
  })
    .then((response) => {
      console.log("获取房源列表成功(无版本路径):", response.data);

      if (response.data && typeof response.data === "object" && response.data.success === false) {
        console.warn("后端返回错误响应:", response.data);
        const fallbackResponse = {
          ...response,
          data: response.data.data || [],
        };
        return fallbackResponse;
      }

      return response;
    })
    .catch((error) => {
      console.error("获取房源列表失败:", error);
      throw error;
    });
}

/**
 * 获取房源详情
 */
export function getHomestayById(id: number, referringCriteria?: string) {
  console.log(`开始获取房源详情, ID: ${id}, 搜索条件: ${referringCriteria}`);

  const params: Record<string, any> = {};
  if (referringCriteria) {
    params.referring_criteria = referringCriteria;
  }

  return request({
    url: `/api/homestays/${id}`,
    method: "get",
    params: params,
  })
    .then((response) => {
      console.log(`房源详情获取成功(无版本路径), ID: ${id}, 数据:`, response.data);
      return response;
    })
    .catch((error) => {
      console.error(`房源详情获取失败, ID: ${id}, 错误:`, error);
      throw error;
    });
}

/**
 * 获取房东的房源列表
 */
export function getOwnerHomestays(params?: {
  page?: number;
  size?: number;
  status?: string;
  type?: string;
  groupId?: number;
}) {
  return request({
    url: "/api/homestays/owner",
    method: "get",
    params,
  }).catch((error) => {
    console.error(`获取房东房源列表失败`, error);
    throw error;
  });
}

/**
 * 创建新房源
 */
export function createHomestay(data: Omit<Homestay, "id">) {
  const cleanData: Record<string, any> = { ...data };

  if (cleanData.amenities && Array.isArray(cleanData.amenities)) {
    if (cleanData.amenities.length > 0 && typeof cleanData.amenities[0] === "object") {
      cleanData.amenities = cleanData.amenities
        .map((amenity: any) =>
          typeof amenity === "string" ? amenity : amenity.value || amenity.code || ""
        )
        .filter(Boolean);
    }
  }

  if (typeof cleanData.price === "number") {
    cleanData.price = String(cleanData.price);
  }

  cleanData.maxGuests = Number(cleanData.maxGuests) || 1;
  cleanData.minNights = Number(cleanData.minNights) || 1;

  if (!cleanData.status) {
    cleanData.status = "INACTIVE";
  }

  if (!cleanData.images || !Array.isArray(cleanData.images)) {
    cleanData.images = [];
  }

  Object.keys(cleanData).forEach((key) => {
    if (cleanData[key] === null || cleanData[key] === undefined) {
      delete cleanData[key];
    }
  });

  console.log(`开始创建房源(处理后数据):`, cleanData);

  return request({
    url: "/api/homestays",
    method: "post",
    data: cleanData,
    timeout: 30000,
  })
    .then((response) => {
      console.log(`房源创建成功:`, response.data);

      const newHomestayId = response.data?.id;
      if (
        newHomestayId &&
        cleanData.amenities &&
        cleanData.amenities.length > 0 &&
        (!response.data.amenities || response.data.amenities.length === 0)
      ) {
        console.log(`设施可能未成功保存，尝试使用addAllAmenitiesToHomestay...`);

        return import("../amenities").then(({ addAllAmenitiesToHomestayApi }) => {
          return addAllAmenitiesToHomestayApi(newHomestayId)
            .then(() => {
              console.log(`使用addAllAmenitiesToHomestay添加设施成功`);
              return response;
            })
            .catch((error) => {
              console.error(`使用addAllAmenitiesToHomestay添加设施失败:`, error);
              return response;
            });
        });
      }

      return response;
    })
    .catch((error) => {
      console.error(`房源创建失败:`, error);
      throw error;
    });
}

/**
 * 更新房源信息
 */
export function updateHomestay(id: number, data: Partial<Homestay>): Promise<any> {
  const cleanData: Record<string, any> = { ...data };

  if (cleanData.amenities && Array.isArray(cleanData.amenities)) {
    cleanData.amenities = cleanData.amenities
      .map((amenity: any) => {
        if (typeof amenity === "string") {
          return amenity;
        } else if (typeof amenity === "object" && amenity !== null) {
          return amenity.value || amenity.code || "";
        }
        return null;
      })
      .filter(Boolean);
  } else {
    cleanData.amenities = [];
  }

  if (typeof cleanData.price === "number") {
    cleanData.price = String(cleanData.price);
  }

  delete cleanData.id;
  delete cleanData.createdAt;
  delete cleanData.updatedAt;
  delete cleanData.ownerInfo;

  return request({
    url: `/api/homestays/${id}`,
    method: "put",
    data: cleanData,
    timeout: 30000,
  })
    .then((response) => ({ success: true, data: response.data }))
    .catch((error) => {
      if (error?.response?.status === 403 && typeof window !== "undefined") {
        setTimeout(() => {
          window.location.href = "/login";
        }, 1500);
      }
      return Promise.reject({ success: false, error });
    });
}

/**
 * 删除房源
 */
export function deleteHomestay(id: number) {
  return request({
    url: `/api/homestays/${id}`,
    method: "delete",
    timeout: 15000,
  })
    .then((response) => {
      console.log(`房源删除成功, ID: ${id}`, response);
      return response;
    })
    .catch((error) => {
      console.error(`删除房源失败, ID: ${id}`, error);
      throw error;
    });
}

/**
 * 更改房源状态：激活
 */
export function activateHomestay(id: number) {
  return request({
    url: `/api/homestays/${id}/activate`,
    method: "patch",
    data: {},
  }).catch((error) => {
    console.error(`激活房源失败, ID: ${id}`, error);
    throw error;
  });
}

/**
 * 更改房源状态：停用
 */
export function deactivateHomestay(id: number) {
  return request({
    url: `/api/homestays/${id}/deactivate`,
    method: "patch",
    data: {},
  }).catch((error) => {
    console.error(`停用房源失败, ID: ${id}`, error);
    throw error;
  });
}

/**
 * 获取已上架房源列表
 */
export function getActiveHomestays(params?: { page?: number; size?: number; featured?: boolean }) {
  console.log("获取已上架房源列表，参数:", params);
  return request({
    url: "/api/homestays/status/ACTIVE",
    method: "get",
    params: params,
  })
    .then((response) => {
      console.log("获取已上架房源列表成功(分页接口):", response.data);
      return response;
    })
    .catch((error) => {
      console.error("获取已上架房源列表失败:", error);
      throw error;
    });
}

/**
 * 根据ID列表批量获取房源
 */
export function getHomestaysByIds(ids: number[]) {
  console.log("根据ID列表获取房源，IDs:", ids);

  return request({
    url: "/api/homestays/by-ids",
    method: "post",
    data: { ids },
  })
    .then((response) => {
      console.log("批量获取房源成功:", response.data);
      return response;
    })
    .catch((error) => {
      console.error("批量获取房源失败，尝试备用API:", error);

      return Promise.all(
        ids.map((id) =>
          request({
            url: `/api/homestays/${id}`,
            method: "get",
          })
            .then((response) => response.data)
            .catch((error) => {
              console.warn(`获取ID为${id}的房源失败:`, error);
              return null;
            })
        )
      ).then((results) => {
        const validResults = results.filter((r) => r !== null);
        console.log("批量获取房源成功(备用方法):", validResults);
        return { data: validResults };
      });
    });
}

/**
 * 验证当前用户是否拥有特定房源的权限
 */
export function checkHomestayOwnership(id: number): Promise<boolean> {
  return request({
    url: `/api/homestays/${id}/ownership`,
    method: "get",
  })
    .then((response) => {
      return response.data?.owned === true;
    })
    .catch((error) => {
      console.error(`检查房源所有权失败:`, error);
      return false;
    });
}

/**
 * 获取房源不可用日期
 */
export function getHomestayUnavailableDates(id: number) {
  return request({
    url: `/api/homestays/${id}/unavailable-dates`,
    method: "get",
  });
}
