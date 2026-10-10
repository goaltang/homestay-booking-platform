import request from "../../utils/request";
import { ElMessage } from "element-plus";
import type { HomestayType, AmenityCategoryOption } from "../../types/homestay";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "";
let homestayTypesCache: Promise<HomestayType[]> | null = null;
let homestayAmenitiesCache: Promise<any> | null = null;

export interface AdministrativeDivisionOption {
  code: string;
  name: string;
  children?: AdministrativeDivisionOption[];
}

/**
 * 获取房源类型列表
 */
export function getHomestayTypes(): Promise<HomestayType[]> {
  if (homestayTypesCache) {
    return homestayTypesCache;
  }

  homestayTypesCache = request({
    url: "/api/homestay-types",
    method: "get",
  })
    .then((response) => {
      if (response && response.data) {
        return response.data as HomestayType[];
      } else {
        console.warn("/api/homestay-types response or response.data is missing.");
        return [];
      }
    })
    .catch((error) => {
      console.error("获取房源类型失败 (API):", error);
      homestayTypesCache = null;
      return [];
    });

  return homestayTypesCache;
}

/**
 * 获取按分类分组的房源类型
 */
export function getHomestayTypesByCategory() {
  return request({
    url: `${API_BASE_URL}/api/homestay-types/by-category`,
    method: "get",
  }).catch((error) => {
    console.error(`获取分组房源类型失败(无版本路径)`, error);
    console.log(`尝试使用v1 API获取分组房源类型`);
    return request({
      url: `${API_BASE_URL}/api/v1/homestay-types/by-category`,
      method: "get",
    });
  });
}

/**
 * 获取所有房源类型分类
 */
export function getHomestayCategories() {
  return request({
    url: `${API_BASE_URL}/api/homestay-types/categories`,
    method: "get",
  }).catch((error) => {
    console.error(`获取房源类型分类失败(无版本路径)`, error);
    console.log(`尝试使用v1 API获取房源类型分类`);
    return request({
      url: `${API_BASE_URL}/api/v1/homestay-types/categories`,
      method: "get",
    });
  });
}

/**
 * 获取房源类型（用于筛选）
 */
export const getHomestayTypesForFilter = () => {
  return request({
    url: "/api/homestay-types/legacy-types",
    method: "get",
  });
};

/**
 * 获取房源设施列表
 */
export function getHomestayAmenities() {
  if (homestayAmenitiesCache) {
    return homestayAmenitiesCache;
  }

  homestayAmenitiesCache = request({
    url: "/api/homestays/amenities",
    method: "get",
  }).catch((error) => {
    console.error(`获取房源设施列表失败(无版本路径)`, error);
    console.log(`尝试使用v1 API获取房源设施列表`);
    return request({
      url: "/api/v1/homestays/amenities",
      method: "get",
    }).catch((fallbackError) => {
      homestayAmenitiesCache = null;
      throw fallbackError;
    });
  });

  return homestayAmenitiesCache;
}

/**
 * 获取按分类分组的可用设施列表
 */
export function getAvailableAmenitiesGrouped(): Promise<AmenityCategoryOption[]> {
  console.log("尝试获取按分类分组的可用设施列表...");
  return request({
    url: "/api/amenities/by-categories",
    method: "get",
    params: { onlyActive: true },
  })
    .then((response) => {
      if (
        response &&
        response.data &&
        response.data.success === true &&
        Array.isArray(response.data.data)
      ) {
        console.log("成功获取按分类分组的可用设施列表:", response.data.data);
        return response.data.data as AmenityCategoryOption[];
      } else {
        console.warn("获取按分类分组的设施列表时返回的数据格式不符合预期:", response?.data);
        return [];
      }
    })
    .catch((error) => {
      console.error("获取按分类分组的设施列表失败:", error);
      ElMessage.error("加载设施选项失败");
      return [];
    });
}

/**
 * 获取省份列表
 */
export function getProvinces() {
  return request({
    url: `${API_BASE_URL}/api/locations/provinces`,
    method: "get",
  }).catch((error) => {
    console.error(`获取省份列表失败(无版本路径)`, error);
    console.log(`尝试使用v1 API获取省份列表`);
    return request({
      url: `${API_BASE_URL}/api/v1/locations/provinces`,
      method: "get",
    });
  });
}

/**
 * 获取城市列表
 */
export function getCities(provinceCode: string) {
  return request({
    url: `${API_BASE_URL}/api/locations/cities`,
    method: "get",
    params: { provinceCode },
  }).catch((error) => {
    console.error(`获取城市列表失败(无版本路径)`, error);
    console.log(`尝试使用v1 API获取城市列表`);
    return request({
      url: `${API_BASE_URL}/api/v1/locations/cities`,
      method: "get",
      params: { provinceCode },
    });
  });
}

/**
 * 获取区县列表
 */
export function getDistricts(cityCode: string) {
  return request({
    url: `${API_BASE_URL}/api/locations/districts`,
    method: "get",
    params: { cityCode },
  }).catch((error) => {
    console.error(`获取区县列表失败(无版本路径)`, error);
    console.log(`尝试使用v1 API获取区县列表`);
    return request({
      url: `${API_BASE_URL}/api/v1/locations/districts`,
      method: "get",
      params: { cityCode },
    });
  });
}

/**
 * 获取省市区树
 */
export function getLocationTree() {
  return request({
    url: `${API_BASE_URL}/api/locations/tree`,
    method: "get",
  }).catch((error) => {
    console.error(`获取省市区树失败(无版本路径)`, error);
    console.log(`尝试使用v1 API获取省市区树`);
    return request({
      url: `${API_BASE_URL}/api/v1/locations/tree`,
      method: "get",
    });
  });
}
